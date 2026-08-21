package caretlang;

import caretlang.Ast.*;

import java.io.PrintStream;
import java.util.*;

final class Interpreter {
    // Keep the language-owned guard below typical JVM stack limits so diagnostics do not depend on
    // host stack size or whether a StackOverflowError happens first.
    private static final int MAX_CALL_DEPTH = 256;
    private final Environment globals = new Environment(null);
    private final PrintStream output;
    private int callDepth;
    private ContractInference inference;
    private final IdentityHashMap<ContractDescriptor, Map<Integer, ContractDescriptor>> modifiedContracts =
            new IdentityHashMap<>();

    Interpreter(PrintStream output) {
        this(output, null);
    }

    Interpreter(PrintStream output, TestReporter testReporter) {
        this.output = output;
        installBuiltins();
        if (testReporter != null) installTestBuiltins(testReporter);
    }

    void execute(List<Stmt> program) {
        int checkpoint = globals.checkpoint();
        try {
            Resolution resolution = Resolver.resolve(program, globals);
            inference = ContractInference.analyze(program, resolution);
            executeBlock(program, globals, resolution);
        } catch (RuntimeException | Error failure) {
            globals.rollbackTo(checkpoint);
            throw failure;
        }
    }

    private Value executeBlock(List<Stmt> statements, Environment env, Resolution resolution) {
        LinkedHashMap<String, Value> exports = new LinkedHashMap<>();
        IdentityHashMap<FunctionDef, Value.Callable> functions = prepareDeclarations(statements, env, resolution);
        Value last = Value.Missing.INSTANCE;

        for (Stmt statement : statements) {
            if (statement instanceof Assign(String name, boolean exported, ContractClause contracts,
                                            Expr value1, SourceSpan ignored)) {
                Value value = eval(value1, env, null, resolution);
                value = validateContracts(value, value1.span(), contracts, resolution, env, "binding " + name);
                if (value instanceof Value.ContractValue contract
                        && contract.descriptor() instanceof UserContract user) user.nameIfAnonymous(name);
                env.initialize(name, value);
                if (exported) exports.put(name, value);
                last = value;
            } else if (statement instanceof ExprStmt(Expr expression, SourceSpan ignored)) {
                last = eval(expression, env, null, resolution);
            } else if (statement instanceof PrintLine line) {
                last = eval(resolution.usesBuiltinPrint(line)
                        ? new Apply(line.target(), line.builtinArgument(), line.span())
                        : line.ordinaryCall(), env, null, resolution);
            } else if (statement instanceof FunctionDef(String name, ContractClause resultContracts,
                                                        List<Parameter> params, List<Stmt> body,
                                                        SourceSpan ignored)) {
                last = functions.get((FunctionDef) statement);
            }
        }

        return exports.isEmpty() ? last : new Value.Scope(exports);
    }

    private IdentityHashMap<FunctionDef, Value.Callable> prepareDeclarations(List<Stmt> statements,
                                                                                   Environment env,
                                                                                   Resolution resolution) {
        IdentityHashMap<FunctionDef, Value.Callable> functions = new IdentityHashMap<>();
        LinkedHashMap<String, List<FunctionDef>> groups = new LinkedHashMap<>();
        for (Stmt statement : statements) {
            if (statement instanceof Assign assign) declare(env, assign.name(), assign.span());
            else if (statement instanceof FunctionDef function) {
                List<FunctionDef> group = groups.computeIfAbsent(function.name(), ignored -> new ArrayList<>());
                if (group.isEmpty()) declare(env, function.name(), function.span());
                group.add(function);
            }
        }
        for (var entry : groups.entrySet()) {
            ArrayList<OverloadVariant> variants = new ArrayList<>();
            for (FunctionDef function : entry.getValue()) {
                Value.FunctionValue raw = rawFunction(function, env, resolution);
                variants.add(new OverloadVariant(function, raw));
            }
            if (variants.size() == 1) {
                OverloadVariant variant = variants.getFirst();
                FunctionDef function = variant.definition();
                Value.Callable value = function.params().stream().noneMatch(parameter -> parameter.contracts() != null)
                        ? variant.function() : new Value.ContractedCallable(variant.function(), (index, argument) -> {
                            Parameter parameter = function.params().get(index);
                            Value checked = validateContracts(argument.value(), argument.span(),
                                    parameter.contracts(), resolution, env, "parameter " + parameter.name());
                            return new Value.Argument(checked, argument.span());
                        });
                env.initialize(entry.getKey(), value);
                functions.put(function, value);
            } else {
                Value.Callable overload = new OverloadCallable(entry.getKey(), List.copyOf(variants),
                        List.copyOf(variants), Map.of(), Map.of(), env, resolution);
                env.initialize(entry.getKey(), overload);
                for (OverloadVariant variant : variants) functions.put(variant.definition(), overload);
            }
        }
        return functions;
    }

    private Value.FunctionValue rawFunction(FunctionDef function, Environment env, Resolution resolution) {
        List<String> parameterNames = function.params().stream().map(Parameter::name).toList();
        boolean refinementEligible = inference != null && inference.isRefinementEligible(function);
        return new Value.FunctionValue(function.name(), parameterNames, (arguments, ignoredCallSpan) -> {
            Environment parameters = new Environment(env);
            for (int i = 0; i < function.params().size(); i++) {
                parameters.define(function.params().get(i).name(), arguments.get(i).value());
            }
            Value result = executeBlock(function.body(), new Environment(parameters), resolution);
            return validateContracts(result, function.body().getLast().span(),
                    function.resultContracts(), resolution, env, "result of " + function.name());
        }, refinementEligible, CallableSignature.inferred(function, Objects.requireNonNull(inference)));
    }

    private record OverloadVariant(FunctionDef definition, Value.FunctionValue function) {}
    private record ApplicabilityKey(Object requirement, int position) {}
    private record RefinementRequirement(Value.Callable callable, boolean nullable, boolean optional) {}

    private final class OverloadCallable implements Value.Callable {
        private final String name;
        private final List<OverloadVariant> all;
        private final List<OverloadVariant> viable;
        private final Map<Integer, Value.Argument> arguments;
        private final Map<ApplicabilityKey, Boolean> cache;
        private final Environment contractEnvironment;
        private final Resolution resolution;

        private OverloadCallable(String name, List<OverloadVariant> all, List<OverloadVariant> viable,
                                 Map<Integer, Value.Argument> arguments, Map<ApplicabilityKey, Boolean> cache,
                                 Environment contractEnvironment, Resolution resolution) {
            this.name = name;
            this.all = all;
            this.viable = viable;
            this.arguments = arguments;
            this.cache = cache;
            this.contractEnvironment = contractEnvironment;
            this.resolution = resolution;
        }

        @Override public Value apply(Value.Argument argument, SourceSpan callSpan) {
            int position = 0;
            while (arguments.containsKey(position)) position++;
            return bind(position, argument, callSpan);
        }

        private Value bind(int position, Value.Argument argument, SourceSpan callSpan) {
            if (position < 0 || position >= all.getFirst().definition().params().size()
                    || arguments.containsKey(position)) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                        "Invalid overload argument position", argument.span());
            }
            LinkedHashMap<ApplicabilityKey, Boolean> nextCache = new LinkedHashMap<>(cache);
            ArrayList<OverloadVariant> survivors = new ArrayList<>();
            for (OverloadVariant variant : viable) {
                if (matches(variant.definition().params().get(position).contracts(), argument, position,
                        nextCache, contractEnvironment, resolution)) survivors.add(variant);
            }
            LinkedHashMap<Integer, Value.Argument> nextArguments = new LinkedHashMap<>(arguments);
            nextArguments.put(position, argument);
            boolean complete = nextArguments.size() == all.getFirst().definition().params().size();
            if (survivors.isEmpty()) {
                throw overloadFailure(Diagnostic.Codes.NO_APPLICABLE_OVERLOAD,
                        "No applicable overload: " + name, complete ? callSpan : argument.span(), all);
            }
            if (!complete) {
                return new OverloadCallable(name, all, List.copyOf(survivors), Map.copyOf(nextArguments),
                        Map.copyOf(nextCache), contractEnvironment, resolution);
            }
            List<OverloadVariant> maximal = maximalVariants(survivors, contractEnvironment, resolution);
            if (maximal.size() != 1) {
                throw overloadFailure(Diagnostic.Codes.AMBIGUOUS_OVERLOAD,
                        "Ambiguous overload: " + name, callSpan, maximal);
            }
            Value result = maximal.getFirst().function();
            for (int index = 0; index < nextArguments.size(); index++) {
                result = invoke((Value.Callable) result, nextArguments.get(index), callSpan);
            }
            return result;
        }

        @Override public int remainingArity() {
            return all.getFirst().definition().params().size() - arguments.size();
        }

        @Override public CallableSignature signature() {
            return CallableSignature.summarize(variantSignatures());
        }

        @Override public List<CallableSignature> variantSignatures() {
            return viable.stream().map(variant -> specialize(variant.function().signature())).toList();
        }

        private CallableSignature specialize(CallableSignature signature) {
            ArrayList<CallableSignature.Parameter> parameters = new ArrayList<>();
            for (int index = 0; index < signature.parameters().size(); index++) {
                if (!arguments.containsKey(index)) parameters.add(signature.parameters().get(index));
            }
            return signature.withParameters(parameters);
        }

        @Override public String publicName() { return name; }
        @Override public String toString() { return "<overload " + name + "/" + remainingArity() + ">"; }
    }

    private boolean matches(ContractClause clause, Value.Argument argument, int position,
                            Map<ApplicabilityKey, Boolean> cache, Environment env, Resolution resolution) {
        for (Resolution.ContractBinding binding : resolution.contracts(clause)) {
            Object requirement = resolveRequirement(binding, env, resolution);
            ApplicabilityKey key = new ApplicabilityKey(requirement, position);
            Boolean accepted = cache.get(key);
            if (accepted == null) {
                accepted = requirement instanceof ContractDescriptor contract
                        ? contract.accepts(argument.value())
                        : refinementAccepts((RefinementRequirement) requirement, argument, binding.span());
                cache.put(key, accepted);
            }
            if (!accepted) return false;
        }
        return true;
    }

    private boolean refinementAccepts(RefinementRequirement requirement, Value.Argument argument, SourceSpan span) {
        Value raw = underlying(argument.value());
        if (raw == Value.Null.INSTANCE && requirement.nullable()) return true;
        if (raw == Value.Missing.INSTANCE && requirement.optional()) return true;
        Value result = underlying(invoke(requirement.callable(), argument, span));
        return result instanceof Value.Bool(boolean accepted) && accepted;
    }

    private Object resolveRequirement(Resolution.ContractBinding binding, Environment env,
                                      Resolution resolution) {
        Value resolved = binding.inline() == null
                ? underlying(binding.binding() == null ? globals.get(binding.name())
                : env.getAt(binding.binding().lexicalDepth(), binding.binding().slot()))
                : evalInner(binding.inline(), env, resolution);
        if (resolved instanceof Value.ContractValue contract) {
            ContractDescriptor descriptor = contract.descriptor();
            if (!binding.arguments().isEmpty()) {
                descriptor = descriptor.parameterize(binding.arguments().stream()
                        .map(argument -> (ContractDescriptor) resolveRequirement(argument, env, resolution)).toList());
            }
            return modifiedContract(descriptor, binding.nullable(), binding.optional());
        }
        if (resolved instanceof Value.Callable callable && callable.refinementEligible()) {
            return new RefinementRequirement(callable, binding.nullable(), binding.optional());
        }
        throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_A_CONTRACT,
                "Binding is not a contract: " + binding.name(), binding.span());
    }

    private List<OverloadVariant> maximalVariants(List<OverloadVariant> variants, Environment env,
                                                  Resolution resolution) {
        return variants.stream().filter(candidate -> variants.stream().noneMatch(other -> other != candidate
                && moreSpecific(other, candidate, env, resolution))).toList();
    }

    private boolean moreSpecific(OverloadVariant left, OverloadVariant right, Environment env,
                                 Resolution resolution) {
        boolean strict = false;
        for (int position = 0; position < left.definition().params().size(); position++) {
            ContractClause l = left.definition().params().get(position).contracts();
            ContractClause r = right.definition().params().get(position).contracts();
            boolean lr = clauseImplies(l, r, env, resolution);
            if (!lr) return false;
            strict |= !clauseImplies(r, l, env, resolution);
        }
        return strict;
    }

    private boolean clauseImplies(ContractClause left, ContractClause right, Environment env,
                                  Resolution resolution) {
        List<Object> l = resolution.contracts(left).stream().map(binding -> resolveRequirement(binding, env, resolution)).toList();
        List<Object> r = resolution.contracts(right).stream().map(binding -> resolveRequirement(binding, env, resolution)).toList();
        if (r.isEmpty()) return true;
        if (l.isEmpty()) return false;
        return r.stream().allMatch(required -> l.stream().anyMatch(candidate -> requirementImplies(candidate, required)));
    }

    private boolean requirementImplies(Object left, Object right) {
        if (left == right || right == BuiltinContract.ANY) return true;
        if (left instanceof RefinementRequirement(Value.Callable callable, boolean nullable, boolean optional) && right instanceof RefinementRequirement(
                Value.Callable callable1, boolean nullable1, boolean optional1
        )) {
            return callable == callable1 && (!nullable || nullable1)
                    && (!optional || optional1);
        }
        return left instanceof ContractDescriptor l && right instanceof ContractDescriptor r
                && ContractRelations.implies(l, r);
    }

    private LangException overloadFailure(String code, String message, SourceSpan span,
                                          List<OverloadVariant> variants) {
        List<Diagnostic.Related> related = variants.stream().map(variant -> new Diagnostic.Related(
                "Overload variant declared here", variant.definition().span())).toList();
        return new LangException(new Diagnostic(Diagnostic.Phase.RUNTIME, code, message, span, related));
    }

    private Value validateContracts(Value value, SourceSpan valueSpan, ContractClause clause,
                                   Resolution resolution, Environment contractEnvironment, String subject) {
        LinkedHashSet<ContractDescriptor> acquired = new LinkedHashSet<>();
        for (Resolution.ContractBinding reference : resolution.contracts(clause)) {
            Value resolved = reference.inline() == null
                    ? underlying(reference.binding() == null ? globals.get(reference.name())
                    : contractEnvironment.getAt(reference.binding().lexicalDepth(), reference.binding().slot()))
                    : evalInner(reference.inline(), contractEnvironment, resolution);
            if (!reference.arguments().isEmpty()) {
                if (!(resolved instanceof Value.ContractValue constructor)) {
                    throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_A_CONTRACT,
                            "Binding is not a contract: " + reference.name(), reference.span());
                }
                ContractDescriptor descriptor = constructor.descriptor();
                if (descriptor.parameterArity() != reference.arguments().size()) {
                    throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_A_CONTRACT,
                            "Binding is not a contract: " + reference.name(), reference.span());
                }
                List<ContractDescriptor> arguments = reference.arguments().stream()
                        .map(argument -> resolveContractDescriptor(argument, contractEnvironment, resolution))
                        .toList();
                resolved = new Value.ContractValue(descriptor.parameterize(arguments));
            }
            if (resolved instanceof Value.Callable refinement && !(resolved instanceof Value.ContractValue)) {
                if (!refinement.refinementEligible()) {
                    throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.INVALID_REFINEMENT,
                            "Invalid refinement predicate: " + reference.name()
                                    + " must be unary, Boolean-returning, and pure", reference.span());
                }
                Value underlyingValue = underlying(value);
                if (reference.nullable() && underlyingValue == Value.Null.INSTANCE) continue;
                if (reference.optional() && underlyingValue == Value.Missing.INSTANCE) continue;
                Value result = underlying(invoke(refinement, new Value.Argument(value, valueSpan), reference.span()));
                if (result instanceof Value.Bool(boolean accepted) && accepted) continue;
                List<Diagnostic.Related> related = List.of(
                        new Diagnostic.Related("Required refinement: " + reference.name(), reference.span()));
                throw new LangException(new Diagnostic(Diagnostic.Phase.RUNTIME,
                        Diagnostic.Codes.CONTRACT_VIOLATION,
                        "Contract violation for " + subject + ": refinement " + reference.name()
                                + " rejected " + ValueSemantics.kind(value), valueSpan, related));
            }
            if (!(resolved instanceof Value.ContractValue contractValue)) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_A_CONTRACT,
                        "Binding is not a contract: " + reference.name(), reference.span());
            }
            ContractDescriptor contract = modifiedContract(contractValue.descriptor(),
                    reference.nullable(), reference.optional());
            Value underlyingValue = underlying(value);
            if (underlyingValue == Value.Null.INSTANCE && contract.accepts(value)) continue;
            if (underlyingValue == Value.Missing.INSTANCE && contract.accepts(value)) continue;
            ContractDescriptor nominal = contract instanceof ModifiedContract modified
                    ? modified.base() : contract;
            if (nominal instanceof UserContract user && user.canAcquire(value, valueSpan)) {
                acquired.add(nominal);
                continue;
            }
            if (contract.accepts(value)) continue;
            List<Diagnostic.Related> related = clause == null ? List.of()
                    : List.of(new Diagnostic.Related("Required contract: " + contract.publicName(), clause.span()));
            throw new LangException(new Diagnostic(Diagnostic.Phase.RUNTIME,
                    Diagnostic.Codes.CONTRACT_VIOLATION,
                    "Contract violation for " + subject + ": expected " + contract.publicName()
                            + ", got " + ValueSemantics.kind(value), valueSpan, related));
        }
        if (acquired.isEmpty()) return value;
        if (value instanceof Value.Attributed(Value value1, Set<ContractDescriptor> contracts)) {
            acquired.addAll(contracts);
            return new Value.Attributed(value1, acquired);
        }
        return new Value.Attributed(value, acquired);
    }

    private ContractDescriptor resolveContractDescriptor(Resolution.ContractBinding reference,
                                                         Environment contractEnvironment,
                                                         Resolution resolution) {
        Value resolved = reference.inline() == null
                ? underlying(reference.binding() == null ? globals.get(reference.name())
                : contractEnvironment.getAt(reference.binding().lexicalDepth(), reference.binding().slot()))
                : evalInner(reference.inline(), contractEnvironment, resolution);
        if (!(resolved instanceof Value.ContractValue contract)) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_A_CONTRACT,
                    "Binding is not a contract: " + reference.name(), reference.span());
        }
        ContractDescriptor descriptor = contract.descriptor();
        if (!reference.arguments().isEmpty()) {
            if (descriptor.parameterArity() != reference.arguments().size()) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_A_CONTRACT,
                        "Binding is not a contract: " + reference.name(), reference.span());
            }
            descriptor = descriptor.parameterize(reference.arguments().stream()
                    .map(argument -> resolveContractDescriptor(argument, contractEnvironment, resolution)).toList());
        }
        return modifiedContract(descriptor, reference.nullable(), reference.optional());
    }

    private void declare(Environment env, String name, SourceSpan span) {
        try {
            env.declare(name);
        } catch (LangException error) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.DUPLICATE_DEFINITION,
                    error.detail(), span);
        }
    }

    private Value eval(Expr expr, Environment env, List<Value.Argument> holeArgs, Resolution resolution) {
        try {
            if (holeArgs == null && !(expr instanceof Compose)) {
                HoleAnalysis analysis = analyzeHoles(expr);
                if (!analysis.indexes().isEmpty()) {
                    int arity = holeArity(expr, analysis.indexes());
                    Expr captured = captureNonHoleParts(expr, env, analysis.containsHole(), resolution);
                    Value overloadPartial = overloadHolePartial(captured, arity, env, resolution);
                    if (overloadPartial != null) return overloadPartial;
                    return new Value.HoleFunction(expr.toString(), arity,
                            supplied -> eval(captured, env, supplied, resolution));
                }
            }
            Expr resolved = holeArgs == null ? expr : bindHoles(expr, new HoleBinder(holeArgs));
            return evalInner(resolved, env, resolution);
        } catch (StackOverflowError exhaustedStack) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CALL_DEPTH_EXCEEDED,
                    "Maximum Caret evaluation depth exceeded", expr.span());
        }
    }

    private Value evalInner(Expr expr, Environment env, Resolution resolution) {
        try {
            return evalInnerUnchecked(expr, env, resolution);
        } catch (LangException error) {
            throw error.withSpanIfAbsent(expr.span());
        }
    }

    private Value overloadHolePartial(Expr expression, int arity, Environment env, Resolution resolution) {
        ArrayList<Expr> reversed = new ArrayList<>();
        Expr callee = expression;
        while (callee instanceof Apply apply) {
            reversed.add(apply.argument());
            callee = apply.function();
        }
        Collections.reverse(reversed);
        if (!(callee instanceof Literal(Value value, SourceSpan ignored))
                || !(underlying(value) instanceof OverloadCallable overload)
                || reversed.size() != overload.all.getFirst().definition().params().size()) return null;

        ArrayList<PendingOverloadArgument> pending = new ArrayList<>();
        int[] ordinaryIndex = {0};
        Value state = overload;
        for (int position = 0; position < reversed.size(); position++) {
            Expr argument = reversed.get(position);
            if (argument instanceof Literal(Value fixed, SourceSpan span)) {
                state = ((OverloadCallable) state).bind(position, new Value.Argument(fixed, span), expression.span());
            } else {
                Expr normalized = AstRewriter.rewrite(argument, candidate -> candidate instanceof Hole(
                        int index, SourceSpan span
                )
                        && index == 0
                        ? Optional.of(new Hole(++ordinaryIndex[0], span)) : Optional.empty());
                List<Integer> dependencies = analyzeHoles(normalized).indexes();
                if (dependencies.isEmpty()) return null;
                pending.add(new PendingOverloadArgument(position, normalized,
                        dependencies.stream().mapToInt(Integer::intValue).max().orElseThrow()));
            }
        }
        if (!(state instanceof OverloadCallable narrowed)) return null;
        return new OverloadHoleCallable(expression.toString(), narrowed, List.copyOf(pending),
                List.of(), arity, env, resolution);
    }

    private record PendingOverloadArgument(int position, Expr expression, int readyAfter) {}

    private final class OverloadHoleCallable implements Value.Callable {
        private final String display;
        private final OverloadCallable overload;
        private final List<PendingOverloadArgument> pending;
        private final List<Value.Argument> arguments;
        private final int arity;
        private final Environment environment;
        private final Resolution resolution;

        private OverloadHoleCallable(String display, OverloadCallable overload,
                                     List<PendingOverloadArgument> pending, List<Value.Argument> arguments,
                                     int arity, Environment environment, Resolution resolution) {
            this.display = display;
            this.overload = overload;
            this.pending = pending;
            this.arguments = arguments;
            this.arity = arity;
            this.environment = environment;
            this.resolution = resolution;
        }

        @Override public Value apply(Value.Argument argument, SourceSpan callSpan) {
            ArrayList<Value.Argument> nextArguments = new ArrayList<>(arguments);
            nextArguments.add(argument);
            Value state = overload;
            ArrayList<PendingOverloadArgument> remaining = new ArrayList<>();
            for (PendingOverloadArgument candidate : pending) {
                if (candidate.readyAfter() <= nextArguments.size()) {
                    Value value = eval(candidate.expression(), environment, nextArguments, resolution);
                    state = ((OverloadCallable) state).bind(candidate.position(),
                            new Value.Argument(value, argument.span()), callSpan);
                } else {
                    remaining.add(candidate);
                }
            }
            if (nextArguments.size() == arity) return state;
            return new OverloadHoleCallable(display, (OverloadCallable) state, List.copyOf(remaining),
                    List.copyOf(nextArguments), arity, environment, resolution);
        }

        @Override public int remainingArity() { return arity - arguments.size(); }
        @Override public CallableSignature signature() {
            return CallableSignature.unknown(Collections.nCopies(remainingArity(), null));
        }
        @Override public List<CallableSignature> variantSignatures() { return overload.variantSignatures(); }
        @Override public String toString() { return "<overload-partial " + display + "/" + remainingArity() + ">"; }
    }

    private Value evalInnerUnchecked(Expr expr, Environment env, Resolution resolution) {
        if (expr instanceof Literal(Value value1, SourceSpan ignored)) return value1;
        if (expr instanceof Name nameExpression) {
            Resolution.Binding binding = resolution.binding(nameExpression);
            Value value = binding == null ? env.get(nameExpression.name())
                    : env.getAt(binding.lexicalDepth(), binding.slot());
            Value callableValue = underlying(value);
            if (callableValue instanceof Value.Callable callable && callable.remainingArity() == 0) {
                return invokeZero(callable, expr.span());
            }
            return value;
        }
        if (expr instanceof Hole) {
            throw runtime(Diagnostic.Codes.INTERNAL_ERROR, "Internal error: unresolved hole");
        }
        if (expr instanceof ContractVariable) {
            throw runtime(Diagnostic.Codes.INTERNAL_ERROR, "Internal error: contract variable outside arrow contract");
        }
        if (expr instanceof Unary(String operator1, Expr operand, SourceSpan ignored)) {
            Value value = evalInner(operand, env, resolution);
            return switch (operator1) {
                case "-" -> finiteNumber(-number(value), "Numeric result is not finite");
                case "not" -> new Value.Bool(!truth(value));
                default -> throw runtime(Diagnostic.Codes.UNKNOWN_OPERATOR,
                        "Unknown unary operator: " + operator1);
            };
        }
        if (expr instanceof Binary(String operator, Expr left1, Expr right1, SourceSpan ignored)) {
            if (operator.equals("and")) {
                Value left = evalInner(left1, env, resolution);
                return truth(left) ? evalInner(right1, env, resolution) : new Value.Bool(false);
            }
            if (operator.equals("or")) {
                Value left = evalInner(left1, env, resolution);
                return truth(left) ? new Value.Bool(true) : new Value.Bool(truth(evalInner(right1, env, resolution)));
            }
            Value left = evalInner(left1, env, resolution);
            Value right = evalInner(right1, env, resolution);
            return applyBinaryOperator(operator, left, left1.span(), right, right1.span(), expr.span());
        }
        if (expr instanceof Compose(Expr leftExpression, Expr rightExpression, SourceSpan ignored)) {
            Value left = underlying(compositionOperand(leftExpression, env, resolution));
            if (!(left instanceof Value.Callable leftCallable) || leftCallable.remainingArity() < 1) {
                throw new LangException(Diagnostic.Phase.RUNTIME,
                        Diagnostic.Codes.INVALID_COMPOSITION_LEFT,
                        "Composition left operand must be a callable requiring at least one argument",
                        leftExpression.span());
            }
            Value right = underlying(compositionOperand(rightExpression, env, resolution));
            if (!(right instanceof Value.Callable rightCallable) || rightCallable.remainingArity() != 1) {
                throw new LangException(Diagnostic.Phase.RUNTIME,
                        Diagnostic.Codes.INVALID_COMPOSITION_RIGHT,
                        "Composition right operand must be a callable requiring exactly one argument",
                        rightExpression.span());
            }
            return new Value.ComposedFunction(leftCallable, rightCallable, this::invoke);
        }
        if (expr instanceof NamedInfix(Expr leftExpression, Expr functionExpression,
                                       Expr rightExpression, SourceSpan ignored)) {
            Value left = evalInner(leftExpression, env, resolution);
            Value function = rawValue(functionExpression, env, resolution);
            Value right = evalInner(rightExpression, env, resolution);
            return invokeNamedInfix(left, leftExpression.span(), function, functionExpression,
                    right, rightExpression.span(), expr.span());
        }
        if (expr instanceof AmbiguousCall(Expr firstExpression, Expr middleExpression,
                                          Expr lastExpression, SourceSpan ignored)) {
            Value first = underlying(rawValue(firstExpression, env, resolution));
            Resolution.CallMode mode = resolution.callMode((AmbiguousCall) expr);
            if (mode == Resolution.CallMode.PREFIX
                    || mode == Resolution.CallMode.DYNAMIC
                    && first instanceof Value.Callable callableValue && callableValue.remainingArity() > 0) {
                if (!(first instanceof Value.Callable callable)) {
                    throw runtime(Diagnostic.Codes.NOT_CALLABLE, "Value is not callable: " + first);
                }
                Value middle = evalInner(middleExpression, env, resolution);
                Value partial = invoke(callable,
                        new Value.Argument(middle, middleExpression.span()), expr.span());
                if (!(partial instanceof Value.Callable remaining)) {
                    throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.TOO_MANY_ARGUMENTS,
                            "Callable accepts fewer than two arguments", lastExpression.span());
                }
                Value last = evalInner(lastExpression, env, resolution);
                return invoke(remaining, new Value.Argument(last, lastExpression.span()), expr.span());
            }
            Value left = first instanceof Value.Callable callable ? invokeZero(callable, firstExpression.span()) : first;
            Value function = rawValue(middleExpression, env, resolution);
            Value right = evalInner(lastExpression, env, resolution);
            return invokeNamedInfix(left, firstExpression.span(), function, middleExpression,
                    right, lastExpression.span(), expr.span());
        }
        if (expr instanceof Conditional(Expr condition1, Expr whenTrue, Expr whenFalse, SourceSpan ignored)) {
            Value condition = evalInner(condition1, env, resolution);
            return truth(condition)
                    ? evalInner(whenTrue, env, resolution)
                    : evalInner(whenFalse, env, resolution);
        }
        if (expr instanceof Apply(Expr function, Expr argument1, SourceSpan ignored)) {
            Value fn = underlying(evalInner(function, env, resolution));
            Value argument = evalInner(argument1, env, resolution);
            if (!(fn instanceof Value.Callable callable)) {
                throw runtime(Diagnostic.Codes.NOT_CALLABLE, "Value is not callable: " + fn);
            }
            return invoke(callable, new Value.Argument(argument, argument1.span()), expr.span());
        }
        if (expr instanceof Field(Expr target2, String field, boolean optional1, SourceSpan ignored)) {
            Value target = evalInner(target2, env, resolution);
            return field(target, field, optional1);
        }
        if (expr instanceof DynamicField(Expr target1, Expr name1, boolean optional, SourceSpan ignored)) {
            Value target = evalInner(target1, env, resolution);
            Value name = underlying(evalInner(name1, env, resolution));
            if (!(name instanceof Value.Str(String fieldName))) {
                throw runtime(Diagnostic.Codes.INVALID_DYNAMIC_FIELD_NAME,
                        "Dynamic field name must be a string, got: " + name);
            }
            return field(target, fieldName, optional);
        }
        if (expr instanceof Reflect(Expr target, SourceSpan ignored)) {
            // Reflection of a name observes the binding itself. In particular,
            // this is the escape hatch for referring to a zero-argument function
            // without triggering the normal implicit invocation on name reads.
            Value targetValue;
            if (target instanceof Name nameExpression) {
                Resolution.Binding binding = resolution.binding(nameExpression);
                targetValue = binding == null ? env.get(nameExpression.name())
                        : env.getAt(binding.lexicalDepth(), binding.slot());
            } else {
                targetValue = evalInner(target, env, resolution);
            }
            return reflect(targetValue);
        }
        if (expr instanceof ContractModifier(Expr target, boolean nullable, boolean optional,
                                             SourceSpan ignored)) {
            Value value = underlying(evalInner(target, env, resolution));
            if (!(value instanceof Value.ContractValue contract)) {
                String subject = target instanceof Name name ? name.name() : ValueSemantics.kind(value);
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_A_CONTRACT,
                        "Binding is not a contract: " + subject, target.span());
            }
            return new Value.ContractValue(modifiedContract(contract.descriptor(), nullable, optional));
        }
        if (expr instanceof Group(Expr expression, SourceSpan ignored)) {
            return evalInner(expression, env, resolution);
        }
        if (expr instanceof CollectionLiteral(List<Expr> elements, SourceSpan ignored)) {
            ArrayList<Value> values = new ArrayList<>(elements.size());
            for (Expr element : elements) values.add(evalInner(element, env, resolution));
            return new Value.Seq(values);
        }
        if (expr instanceof ArrowContract(List<List<Expr>> parameters, Expr result, SourceSpan ignored)) {
            ArrayList<List<ContractDescriptor>> parameterDescriptors = new ArrayList<>();
            for (List<Expr> parameter : parameters) {
                parameterDescriptors.add(parameter.stream()
                        .map(requirement -> arrowRequirement(requirement, env, resolution)).toList());
            }
            ContractDescriptor resultDescriptor = arrowRequirement(result, env, resolution);
            return new Value.ContractValue(new ArrowContractDescriptor(
                    List.copyOf(parameterDescriptors), resultDescriptor, List.of()));
        }
        throw runtime(Diagnostic.Codes.INTERNAL_ERROR, "Unknown expression: " + expr);
    }

    private ContractDescriptor arrowRequirement(Expr expression, Environment env, Resolution resolution) {
        if (expression instanceof ContractVariable variable) {
            return new ContractVariableDescriptor(variable.index());
        }
        if (expression instanceof ContractModifier modifier) {
            return modifiedContract(arrowRequirement(modifier.target(), env, resolution),
                    modifier.nullable(), modifier.optional());
        }
        if (expression instanceof Apply apply) {
            ContractDescriptor constructor = arrowRequirement(apply.function(), env, resolution);
            return constructor.parameterize(List.of(arrowRequirement(apply.argument(), env, resolution)));
        }
        Value value = underlying(evalInner(expression, env, resolution));
        if (!(value instanceof Value.ContractValue contract)) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_A_CONTRACT,
                    "Arrow requirement is not a contract", expression.span());
        }
        return contract.descriptor();
    }

    private Value invokeNamedInfix(Value left, SourceSpan leftSpan, Value function,
                                   Expr functionExpression, Value right, SourceSpan rightSpan,
                                   SourceSpan callSpan) {
            String functionName = functionExpression instanceof Name name ? name.name() : function.toString();
            if (!(function instanceof Value.Callable callable)) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_CALLABLE,
                        "Named infix target is not callable: " + functionName,
                        functionExpression.span());
            }
            if (callable.remainingArity() != 2) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_INFIX_ARITY,
                        "Named infix function must take exactly two arguments: " + functionName,
                        functionExpression.span());
            }
            Value partial = invoke(callable, new Value.Argument(left, leftSpan), callSpan);
            return invoke((Value.Callable) partial,
                    new Value.Argument(right, rightSpan), callSpan);
    }

    private Value rawValue(Expr expression, Environment env, Resolution resolution) {
        return expression instanceof Name name ? bindingValue(name, env, resolution)
                : evalInner(expression, env, resolution);
    }

    private Value compositionOperand(Expr expression, Environment env, Resolution resolution) {
        return expression instanceof Name name ? bindingValue(name, env, resolution)
                : eval(expression, env, null, resolution);
    }

    private Value bindingValue(Name expression, Environment env, Resolution resolution) {
        Resolution.Binding binding = resolution.binding(expression);
        return binding == null ? env.get(expression.name())
                : env.getAt(binding.lexicalDepth(), binding.slot());
    }

    private Value invoke(Value.Callable callable, Value.Argument argument, SourceSpan span) {
        return withinCallDepth(span, () -> callable.apply(argument, span));
    }

    private Value invokeZero(Value.Callable callable, SourceSpan span) {
        return withinCallDepth(span, () -> callable.invokeZero(span));
    }

    private Value withinCallDepth(SourceSpan span, java.util.function.Supplier<Value> invocation) {
        if (callDepth >= MAX_CALL_DEPTH) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CALL_DEPTH_EXCEEDED,
                    "Maximum Caret call depth exceeded", span);
        }
        callDepth++;
        try {
            return invocation.get();
        } catch (StackOverflowError exhaustedStack) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CALL_DEPTH_EXCEEDED,
                    "Maximum Caret call depth exceeded", span);
        } finally {
            callDepth--;
        }
    }

    private Value applyBinaryOperator(String operator, Value left, SourceSpan leftSpan,
                                      Value right, SourceSpan rightSpan, SourceSpan span) {
        Value value = globals.get(operator);
        if (!(value instanceof Value.Callable callable)) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                    "Binary operator is not callable: " + operator, span);
        }
        Value partial = invoke(callable, new Value.Argument(left, leftSpan), span);
        if (!(partial instanceof Value.Callable remaining)) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                    "Binary operator did not retain its second parameter: " + operator, span);
        }
        return invoke(remaining, new Value.Argument(right, rightSpan), span);
    }

    private Value binaryOperation(String op, List<Value.Argument> arguments, SourceSpan callSpan) {
        Value.Argument leftArgument = arguments.get(0);
        Value.Argument rightArgument = arguments.get(1);
        Value left = underlying(leftArgument.value());
        Value right = underlying(rightArgument.value());
        return switch (op) {
            case "+" -> {
                if (left instanceof Value.Str || right instanceof Value.Str)
                    yield new Value.Str(left.toString() + right);
                yield finiteNumber(number(leftArgument) + number(rightArgument),
                        "Numeric result is not finite", callSpan);
            }
            case "-" -> finiteNumber(number(leftArgument) - number(rightArgument),
                    "Numeric result is not finite", callSpan);
            case "*" -> finiteNumber(number(leftArgument) * number(rightArgument),
                    "Numeric result is not finite", callSpan);
            case "/" -> {
                double divisor = number(rightArgument);
                if (divisor == 0.0) {
                    throw runtime(Diagnostic.Codes.DIVISION_BY_ZERO, "Division by zero", rightArgument.span());
                }
                yield finiteNumber(number(leftArgument) / divisor, "Numeric result is not finite", callSpan);
            }
            case "%" -> {
                double divisor = number(rightArgument);
                if (divisor == 0.0) {
                    throw runtime(Diagnostic.Codes.DIVISION_BY_ZERO, "Division by zero", rightArgument.span());
                }
                yield finiteNumber(number(leftArgument) % divisor, "Numeric result is not finite", callSpan);
            }
            case ">" -> new Value.Bool(number(leftArgument) > number(rightArgument));
            case ">=" -> new Value.Bool(number(leftArgument) >= number(rightArgument));
            case "<" -> new Value.Bool(number(leftArgument) < number(rightArgument));
            case "<=" -> new Value.Bool(number(leftArgument) <= number(rightArgument));
            case "==" -> new Value.Bool(ValueSemantics.equal(left, right));
            case "!=" -> new Value.Bool(!ValueSemantics.equal(left, right));
            default -> throw runtime(Diagnostic.Codes.UNKNOWN_OPERATOR, "Unknown operator: " + op);
        };
    }

    private double number(Value value) {
        value = underlying(value);
        if (value instanceof Value.Num(double value1)) return value1;
        throw runtime(Diagnostic.Codes.EXPECTED_NUMBER, "Expected number, got: " + value);
    }

    private double number(Value.Argument argument) {
        Value raw = underlying(argument.value());
        if (raw instanceof Value.Num(double value)) return value;
        throw runtime(Diagnostic.Codes.EXPECTED_NUMBER,
                "Expected number, got: " + argument.value(), argument.span());
    }

    private Value.Num finiteNumber(double value, String message) {
        if (!Double.isFinite(value)) throw runtime(Diagnostic.Codes.NON_FINITE_RESULT, message);
        return new Value.Num(value);
    }

    private Value.Num finiteNumber(double value, String message, SourceSpan span) {
        if (!Double.isFinite(value)) throw runtime(Diagnostic.Codes.NON_FINITE_RESULT, message, span);
        return new Value.Num(value);
    }

    private boolean truth(Value value) {
        value = underlying(value);
        if (value instanceof Value.Bool(boolean value1)) return value1;
        if (value == Value.Null.INSTANCE || value == Value.Missing.INSTANCE) return false;
        throw runtime(Diagnostic.Codes.INVALID_CONDITION,
                "Condition must be Boolean, null, or missing; got: " + value);
    }

    private void installBuiltins() {
        for (BuiltinContract contract : BuiltinContract.values()) {
            globals.define(contract.publicName(), new Value.ContractValue(contract));
        }
        globals.define("contract", locatedFunction("contract", List.of("bases"), (args, span) -> {
            Value argument = underlying(args.getFirst().value());
            List<ContractDescriptor> bases = new ArrayList<>();
            List<Value.Callable> refinements = new ArrayList<>();
            if (argument == Value.Missing.INSTANCE) {
            } else if (argument instanceof Value.ContractValue contract) {
                bases.add(contract.descriptor());
            } else if (argument instanceof Value.Callable callable && callable.refinementEligible()) {
                refinements.add(callable);
            } else if (argument instanceof Value.Seq sequence && sequence.size() >= 2) {
                for (Value element : sequence.values()) {
                    element = underlying(element);
                    if (element instanceof Value.ContractValue contract) {
                        bases.add(contract.descriptor());
                    } else if (element instanceof Value.Callable callable && callable.refinementEligible()) {
                        refinements.add(callable);
                    } else {
                        throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                                "Contract violation for contract requirements: expected Contract or verified refinement, got "
                                        + ValueSemantics.kind(element), args.getFirst().span());
                    }
                }
            } else {
                if (argument instanceof Value.Callable) {
                    throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.INVALID_REFINEMENT,
                            "Invalid refinement predicate: callable must be unary, Boolean-returning, and pure",
                            args.getFirst().span());
                }
                throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                        "Contract violation for contract argument: expected missing, Contract, verified refinement, or a collection of requirements",
                        args.getFirst().span());
            }
            return new Value.ContractValue(new UserContract(bases, refinements,
                    (callable, refinementArgument) -> invoke(callable, refinementArgument,
                            refinementArgument.span())));
        }));
        for (LanguageSyntax.BinaryOperator descriptor : LanguageSyntax.binaryOperators()) {
            String operator = descriptor.spelling();
            globals.define(operator, locatedFunction(operator, List.of("left", "right"),
                    (args, span) -> binaryOperation(operator, args, span)));
        }

        globals.define("print", new Value.FunctionValue("print", List.of("value"), (args, ignoredSpan) -> {
            output.println(args.getFirst().value());
            return args.getFirst().value();
        }, false, CallableSignature.builtin(List.of("value"), List.of("Output"))));

        globals.define("type", new Value.FunctionValue("type", List.of("value"),
                args -> new Value.Str(ValueSemantics.kind(args.getFirst()))));

        globals.define("textSize", locatedFunction("textSize", List.of("text"), (args, ignored) -> {
            String value = text(args.getFirst());
            return new Value.Num(value.codePointCount(0, value.length()));
        }));
        globals.define("textAt", locatedFunction("textAt", List.of("text", "index"), (args, ignored) -> {
            String value = text(args.get(0));
            OptionalInt index = index(args.get(1));
            int size = value.codePointCount(0, value.length());
            if (index.isEmpty() || index.getAsInt() >= size) return Value.Missing.INSTANCE;
            int offset = value.offsetByCodePoints(0, index.getAsInt());
            return new Value.Str(new String(Character.toChars(value.codePointAt(offset))));
        }));
        globals.define("textSlice", locatedFunction("textSlice", List.of("text", "start", "end"), (args, ignored) -> {
            String value = text(args.get(0));
            OptionalInt start = index(args.get(1));
            OptionalInt end = index(args.get(2));
            int size = value.codePointCount(0, value.length());
            if (start.isEmpty() || end.isEmpty() || start.getAsInt() > end.getAsInt()
                    || end.getAsInt() > size) return Value.Missing.INSTANCE;
            int from = value.offsetByCodePoints(0, start.getAsInt());
            int to = value.offsetByCodePoints(0, end.getAsInt());
            return new Value.Str(value.substring(from, to));
        }));
        globals.define("textNumber", locatedFunction("textNumber", List.of("text"), (args, ignored) -> {
            try {
                double number = Double.parseDouble(text(args.getFirst()));
                return Double.isFinite(number) ? new Value.Num(number) : Value.Missing.INSTANCE;
            } catch (NumberFormatException invalidNumber) {
                return Value.Missing.INSTANCE;
            }
        }));
        globals.define("numberText", locatedFunction("numberText", List.of("number"), (args, ignored) ->
                new Value.Str(new Value.Num(number(args.getFirst())).toString())));

        globals.define("seqEmpty", function("seqEmpty", List.of(), args -> new Value.Seq(List.of())));
        globals.define("seqAdd", locatedFunction("seqAdd", List.of("sequence", "value"), (args, ignored) ->
                sequence(args.getFirst()).appended(args.get(1).value())));
        globals.define("seqGet", locatedFunction("seqGet", List.of("sequence", "index"), (args, ignored) -> {
            Value.Seq values = sequence(args.get(0));
            OptionalInt index = index(args.get(1));
            return index.isPresent() ? values.find(index.getAsInt()).orElse(Value.Missing.INSTANCE)
                    : Value.Missing.INSTANCE;
        }));
        globals.define("seqSize", locatedFunction("seqSize", List.of("sequence"), (args, ignored) ->
                new Value.Num(sequence(args.getFirst()).size())));

        globals.define("dictEmpty", function("dictEmpty", List.of(), args -> new Value.Dict(Map.of())));
        globals.define("dictPut", locatedFunction("dictPut", List.of("dictionary", "key", "value"), (args, ignored) ->
                dictionary(args.getFirst()).put(requiredDictionaryKey(args.get(1)), args.get(2).value())));
        globals.define("dictGet", locatedFunction("dictGet", List.of("dictionary", "key"), (args, ignored) -> {
            String key = dictionaryKey(args.get(1));
            return key == null ? Value.Missing.INSTANCE
                    : dictionary(args.get(0)).find(key).orElse(Value.Missing.INSTANCE);
        }));
        globals.define("dictHas", locatedFunction("dictHas", List.of("dictionary", "key"), (args, ignored) -> {
            String key = dictionaryKey(args.get(1));
            return new Value.Bool(key != null && dictionary(args.get(0)).containsKey(key));
        }));
        globals.define("dictKeys", locatedFunction("dictKeys", List.of("dictionary"), (args, ignored) -> new Value.Seq(
                dictionary(args.getFirst()).entries().keySet().stream().map(Value.Str::new).toList())));
    }

    private void installTestBuiltins(TestReporter reporter) {
        globals.define("assert", new Value.FunctionValue("assert", List.of("name", "condition"),
                (args, span) -> {
                    String name = text(args.get(0));
                    Value conditionValue = underlying(args.get(1).value());
                    if (!(conditionValue instanceof Value.Bool condition)) {
                        throw runtime(Diagnostic.Codes.INVALID_ASSERTION,
                                "Assertion condition must be Boolean, got: " + args.get(1).value(),
                                args.get(1).span());
                    }
                    reporter.record(name, condition, new Value.Bool(true), condition.value(), span);
                    return Value.Missing.INSTANCE;
                }, false, CallableSignature.builtin(List.of("name", "condition"), List.of("TestReport"))));
        globals.define("assertEqual", new Value.FunctionValue("assertEqual", List.of("name", "actual", "expected"),
                (args, span) -> {
                    String name = text(args.get(0));
                    Value actual = args.get(1).value();
                    Value expected = args.get(2).value();
                    reporter.record(name, actual, expected, ValueSemantics.equal(actual, expected), span);
                    return Value.Missing.INSTANCE;
                }, false, CallableSignature.builtin(
                        List.of("name", "actual", "expected"), List.of("TestReport"))));
    }

    private Value.FunctionValue function(String name, List<String> parameters,
                                         java.util.function.Function<List<Value>, Value> implementation) {
        return new Value.FunctionValue(name, parameters, implementation);
    }

    private Value.FunctionValue locatedFunction(String name, List<String> parameters,
            java.util.function.BiFunction<List<Value.Argument>, SourceSpan, Value> implementation) {
        return new Value.FunctionValue(name, parameters, implementation);
    }

    private String text(Value.Argument argument) {
        Value raw = underlying(argument.value());
        if (raw instanceof Value.Str(String text)) return text;
        throw runtime(Diagnostic.Codes.EXPECTED_STRING,
                "Expected string, got: " + argument.value(), argument.span());
    }

    private Value.Seq sequence(Value.Argument argument) {
        Value raw = underlying(argument.value());
        if (raw instanceof Value.Seq sequence) return sequence;
        throw runtime(Diagnostic.Codes.EXPECTED_SEQUENCE,
                "Expected sequence, got: " + argument.value(), argument.span());
    }

    private Value.Dict dictionary(Value.Argument argument) {
        Value raw = underlying(argument.value());
        if (raw instanceof Value.Dict dictionary) return dictionary;
        throw runtime(Diagnostic.Codes.EXPECTED_DICTIONARY,
                "Expected dictionary, got: " + argument.value(), argument.span());
    }

    private OptionalInt index(Value.Argument argument) {
        Value raw = underlying(argument.value());
        if (!(raw instanceof Value.Num(double number)) || !Double.isFinite(number)
                || number < 0 || number != Math.rint(number) || number > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) number);
    }

    private String dictionaryKey(Value.Argument argument) {
        return underlying(argument.value()) instanceof Value.Str(String text) ? text : null;
    }

    private String requiredDictionaryKey(Value.Argument argument) {
        String key = dictionaryKey(argument);
        if (key == null) {
            throw runtime(Diagnostic.Codes.INVALID_DICTIONARY_KEY,
                    "Dictionary key must be a string, got: " + argument.value(), argument.span());
        }
        return key;
    }

    private Value field(Value target, String name, boolean optional) {
        target = underlying(target);
        if (!(target instanceof Value.Reflective reflective)) {
            throw runtime(Diagnostic.Codes.INVALID_FIELD_TARGET,
                    "Field access requires a scope, got: " + target);
        }
        Optional<Value> value = reflective.find(name);
        if (value.isPresent()) return value.get();
        if (optional) return Value.Missing.INSTANCE;
        if (target instanceof Value.Scope) {
            throw runtime(Diagnostic.Codes.MISSING_FIELD, "Scope has no exported binding: " + name);
        }
        throw runtime(Diagnostic.Codes.MISSING_FIELD, "Reflected value has no field: " + name);
    }

    private Value reflect(Value value) {
        value = underlying(value);
        if (value instanceof Value.FunctionReference reference) return reference;
        if (value instanceof Value.ContractValue contract) return contract;
        if (value instanceof Value.Callable callable) return new Value.FunctionReference(callable);
        return new Value.Scope(ValueSemantics.reflectionFields(value));
    }

    private ContractDescriptor modifiedContract(ContractDescriptor base, boolean nullable, boolean optional) {
        if (base instanceof ModifiedContract modified) {
            nullable |= modified.nullable();
            optional |= modified.optional();
            base = modified.base();
        }
        if (!nullable && !optional) return base;
        boolean needsNull = nullable && !base.accepts(Value.Null.INSTANCE);
        boolean needsMissing = optional && !base.accepts(Value.Missing.INSTANCE);
        if (!needsNull && !needsMissing) return base;
        int key = (needsNull ? 1 : 0) | (needsMissing ? 2 : 0);
        ContractDescriptor normalizedBase = base;
        return modifiedContracts.computeIfAbsent(normalizedBase, ignored -> new HashMap<>())
                .computeIfAbsent(key,
                        ignored -> new ModifiedContract(normalizedBase, needsNull, needsMissing));
    }

    private static Value underlying(Value value) {
        return ValueSemantics.underlying(value);
    }

    private record HoleAnalysis(List<Integer> indexes, IdentityHashMap<Expr, Boolean> containsHole) {}

    private int holeArity(Expr expr, List<Integer> indexes) {
        boolean numbered = indexes.stream().anyMatch(index -> index > 0);
        boolean ordinary = indexes.stream().anyMatch(index -> index == 0);
        if (numbered && ordinary) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.MIXED_HOLE_STYLES,
                    "Cannot mix numbered and unnumbered holes", expr.span());
        }
        return numbered ? indexes.stream().mapToInt(Integer::intValue).max().orElseThrow()
                : indexes.size();
    }

    private HoleAnalysis analyzeHoles(Expr expr) {
        ArrayList<Integer> indexes = new ArrayList<>();
        IdentityHashMap<Expr, Boolean> containsHole = new IdentityHashMap<>();
        markHoles(expr, indexes, containsHole);
        return new HoleAnalysis(List.copyOf(indexes), containsHole);
    }

    private boolean markHoles(Expr expr, List<Integer> indexes,
                              IdentityHashMap<Expr, Boolean> containsHole) {
        boolean found = expr instanceof Hole;
        if (expr instanceof Hole hole) indexes.add(hole.index());
        for (Expr child : AstTraversal.children(expr)) {
            found |= markHoles(child, indexes, containsHole);
        }
        containsHole.put(expr, found);
        return found;
    }

    private Expr captureNonHoleParts(Expr expr, Environment env,
                                     IdentityHashMap<Expr, Boolean> containsHole, Resolution resolution) {
        return AstRewriter.rewrite(expr, candidate -> !containsHole.get(candidate)
                ? Optional.of(new Literal(evalInner(candidate, env, resolution), candidate.span()))
                : Optional.empty());
    }

    private Expr bindHoles(Expr expr, HoleBinder holes) {
        return AstRewriter.rewrite(expr, candidate -> candidate instanceof Hole hole
                ? Optional.of(holes.literal(hole.index()))
                : Optional.empty());
    }

    private static final class HoleBinder {
        private final List<Value.Argument> values;
        private int index;
        private HoleBinder(List<Value.Argument> values) { this.values = values; }
        Literal literal(int oneBasedIndex) {
            Value.Argument argument = oneBasedIndex == 0 ? next() : at(oneBasedIndex);
            return new Literal(argument.value(), argument.span());
        }
        Value.Argument next() {
            if (index >= values.size()) {
                throw runtime(Diagnostic.Codes.INTERNAL_ERROR,
                        "Not enough arguments for partial expression");
            }
            return values.get(index++);
        }
        Value.Argument at(int oneBasedIndex) {
            if (oneBasedIndex < 1 || oneBasedIndex > values.size()) {
                throw runtime(Diagnostic.Codes.INTERNAL_ERROR,
                        "Not enough arguments for numbered partial expression");
            }
            return values.get(oneBasedIndex - 1);
        }
    }

    private static LangException runtime(String code, String message) {
        return new LangException(Diagnostic.Phase.RUNTIME, code, message, null);
    }

    private static LangException runtime(String code, String message, SourceSpan span) {
        return new LangException(Diagnostic.Phase.RUNTIME, code, message, span);
    }
}
