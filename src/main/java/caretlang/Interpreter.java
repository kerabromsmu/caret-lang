package caretlang;

import caretlang.Ast.*;

import java.io.PrintStream;
import java.util.*;

final class Interpreter {
    private final Environment globals = new Environment(null);
    private final PrintStream output;
    private final CallableDispatcher calls = new CallableDispatcher();
    private ContractInference inference;
    private final EffectCatalog effectCatalog;
    private final IdentityHashMap<ContractDescriptor, Map<Integer, ContractDescriptor>> modifiedContracts =
            new IdentityHashMap<>();

    Interpreter(PrintStream output) {
        this(output, null);
    }

    Interpreter(PrintStream output, TestReporter testReporter) {
        this.output = output;
        this.effectCatalog = EffectCatalog.standard(testReporter != null);
        installBuiltins();
        if (testReporter != null) installTestBuiltins(testReporter);
    }

    void execute(List<Stmt> program) {
        Environment.Checkpoint checkpoint = globals.checkpoint();
        try {
            Resolution resolution = Resolver.resolve(program, globals, effectCatalog);
            inference = ContractInference.analyze(program, resolution);
            validateEffectAllowances(program, resolution);
            validateCompositionCompatibility(program, resolution);
            executeBlock(program, globals, resolution);
        } catch (RuntimeException | Error failure) {
            globals.rollbackTo(checkpoint);
            throw failure;
        }
    }

    private void validateEffectAllowances(List<Stmt> statements, Resolution resolution) {
        for (Stmt statement : statements) {
            if (!(statement instanceof FunctionDef function)) continue;
            Resolution.AnalyzedClause clause = resolution.clause(function.resultContracts());
            Set<String> allowed = clause == null || clause.effectAllowance() == null
                    ? Set.of() : clause.effectAllowance().stream().map(EffectDescriptor::canonicalName)
                    .collect(java.util.stream.Collectors.toSet());
            ContractInference.EffectSummary actual = inference.effects(function);
            // Unknown higher-order calls are rejected at the dynamic invocation boundary until
            // parameter-effect substitution is available; known effects are checked here.
            Set<String> inferred = actual.effects().stream().map(effect -> switch (effect) {
                case OUTPUT -> "Output";
                case TEST_REPORT -> "TestReport";
            }).collect(java.util.stream.Collectors.toSet());
            if (!allowed.containsAll(inferred)) {
                Set<String> unexpected = new LinkedHashSet<>(inferred);
                unexpected.removeAll(allowed);
                throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                        Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED,
                        "Function effect allowance exceeded: " + String.join(", ", unexpected),
                        function.span(), clause == null ? List.of() : List.of(new Diagnostic.Related(
                        "Declared effect allowance", clause.span()))));
            }
            validateEffectAllowances(function.body(), resolution);
        }
    }

    private void validateCompositionCompatibility(List<Stmt> statements, Resolution resolution) {
        HashMap<Integer, List<CallableSignature>> variants = new HashMap<>();
        collectFunctionSignatures(statements, resolution, variants);
        HashMap<Integer, CallableSignature> signatures = new HashMap<>();
        variants.forEach((symbol, values) -> signatures.put(symbol, CallableSignature.summarize(values)));
        validateCompositionCompatibility(statements, resolution, signatures);
    }

    private void collectFunctionSignatures(List<Stmt> statements, Resolution resolution,
                                           Map<Integer, List<CallableSignature>> signatures) {
        for (Stmt statement : statements) {
            if (!(statement instanceof FunctionDef function)) continue;
            Integer symbol = resolution.symbolId(function.span());
            if (symbol != null) signatures.computeIfAbsent(symbol, ignored -> new ArrayList<>())
                    .add(CallableSignature.inferred(function, Objects.requireNonNull(inference), resolution));
            collectFunctionSignatures(function.body(), resolution, signatures);
        }
    }

    private void validateCompositionCompatibility(List<Stmt> statements, Resolution resolution,
                                                  Map<Integer, CallableSignature> signatures) {
        for (Stmt statement : statements) {
            switch (statement) {
                case Assign assign -> validateCompositionCompatibility(assign.value(), resolution, signatures);
                case ExprStmt expression -> validateCompositionCompatibility(expression.expression(), resolution, signatures);
                case PrintLine line -> validateCompositionCompatibility(line.ordinaryCall(), resolution, signatures);
                case FunctionDef function -> validateCompositionCompatibility(function.body(), resolution, signatures);
            }
        }
    }

    private void validateCompositionCompatibility(Expr expression, Resolution resolution,
                                                  Map<Integer, CallableSignature> signatures) {
        for (Expr child : AstTraversal.children(expression)) {
            validateCompositionCompatibility(child, resolution, signatures);
        }
        if (!(expression instanceof Compose compose)) return;
        CallableSignature left = knownSignature(compose.left(), resolution, signatures);
        CallableSignature right = knownSignature(compose.right(), resolution, signatures);
        if (left == null || right == null || right.parameters().size() != 1) return;
        CallableSignature.Composition composition = CallableSignature.compose(left, right);
        if (composition.compatibility() == CallableSignature.Compatibility.INCOMPATIBLE) {
            throw incompatibleComposition(compose.span(), compose.left().span(), compose.right().span());
        }
    }

    private CallableSignature knownSignature(Expr expression, Resolution resolution,
                                             Map<Integer, CallableSignature> signatures) {
        if (expression instanceof Group group) return knownSignature(group.expression(), resolution, signatures);
        if (expression instanceof Name name) {
            Resolution.Binding binding = resolution.binding(name);
            return binding == null ? null : signatures.get(binding.symbolId());
        }
        if (expression instanceof Compose compose) {
            CallableSignature left = knownSignature(compose.left(), resolution, signatures);
            CallableSignature right = knownSignature(compose.right(), resolution, signatures);
            return left == null || right == null ? null : CallableSignature.compose(left, right).signature();
        }
        return null;
    }

    private static LangException incompatibleComposition(SourceSpan span, SourceSpan left, SourceSpan right) {
        return new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                Diagnostic.Codes.INCOMPATIBLE_CONTRACTS,
                "Composition result cannot satisfy the right callable parameter",
                span, List.of(
                new Diagnostic.Related("Left composition operand", left),
                new Diagnostic.Related("Right composition operand", right))));
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

        return exports.isEmpty() ? last : new Value.Dictionary(exports);
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
                if (group.isEmpty() && !(env.localValue(function.name()) instanceof Value.Callable)) {
                    declare(env, function.name(), function.span());
                }
                group.add(function);
            }
        }
        for (var entry : groups.entrySet()) {
            ArrayList<OverloadVariant> variants = new ArrayList<>();
            Value existing = env.localValue(entry.getKey());
            if (existing instanceof Value.Callable callable && !(existing instanceof Value.ContractValue)) {
                variants.add(new OverloadVariant(null, callable));
            }
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
                if (existing == null) env.initialize(entry.getKey(), value);
                else env.replace(entry.getKey(), value);
                functions.put(function, value);
            } else {
                Value.Callable overload = new OverloadCallable(entry.getKey(), List.copyOf(variants),
                        List.copyOf(variants), Map.of(), Map.of(), env, resolution);
                if (existing == null) env.initialize(entry.getKey(), overload);
                else env.replace(entry.getKey(), overload);
                for (OverloadVariant variant : variants) {
                    if (variant.definition() != null) functions.put(variant.definition(), overload);
                }
            }
        }
        return functions;
    }

    private Value.FunctionValue rawFunction(FunctionDef function, Environment env, Resolution resolution) {
        List<String> parameterNames = function.params().stream().map(Parameter::name).toList();
        boolean refinementEligible = inference != null && inference.isRefinementEligible(function);
        LinkedHashMap<Integer, Environment.BindingReference> captures = new LinkedHashMap<>();
        for (Resolution.Upvalue upvalue : resolution.upvalues(function)) {
            captures.put(upvalue.symbolId(), env.referenceAt(upvalue.lexicalDepth(), upvalue.slot()));
        }
        return new Value.FunctionValue(function.name(), parameterNames, (arguments, ignoredCallSpan) -> {
            Environment parameters = new Environment(env, captures);
            for (int i = 0; i < function.params().size(); i++) {
                parameters.define(function.params().get(i).name(), arguments.get(i).value());
            }
            Value result = executeBlock(function.body(), new Environment(parameters), resolution);
            return validateContracts(result, function.body().getLast().span(),
                    function.resultContracts(), resolution, env, "result of " + function.name(), false);
        }, refinementEligible, CallableSignature.inferred(function, Objects.requireNonNull(inference), resolution));
    }

    private record OverloadVariant(FunctionDef definition, Value.Callable function) {}
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
            if (position < 0 || position >= variantArity(all.getFirst())
                    || arguments.containsKey(position)) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                        "Invalid overload argument position", argument.span());
            }
            LinkedHashMap<ApplicabilityKey, Boolean> nextCache = new LinkedHashMap<>(cache);
            ArrayList<OverloadVariant> survivors = new ArrayList<>();
            for (OverloadVariant variant : viable) {
                if (matches(variantClause(variant, position), argument, position,
                        nextCache, contractEnvironment, resolution)) survivors.add(variant);
            }
            LinkedHashMap<Integer, Value.Argument> nextArguments = new LinkedHashMap<>(arguments);
            nextArguments.put(position, argument);
            boolean complete = nextArguments.size() == variantArity(all.getFirst());
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
            if (name.equals("toString") && !(underlying(result) instanceof Value.Str)) {
                throw runtime(Diagnostic.Codes.EXPECTED_STRING,
                        "toString specialization must return a String", callSpan);
            }
            return result;
        }

        @Override public int remainingArity() {
            return variantArity(all.getFirst()) - arguments.size();
        }

        @Override public CallableSignature signature() {
            return CallableSignature.summarize(variantSignatures());
        }

        @Override public List<CallableSignature> variantSignatures() {
            return fullVariantSignatures().stream().map(this::removeBoundParameters).toList();
        }

        private List<CallableSignature> fullVariantSignatures() {
            return viable.stream().map(variant -> {
                CallableSignature signature = variant.function().signature();
                for (Map.Entry<Integer, Value.Argument> argument : arguments.entrySet()) {
                    signature = signature.specializeParameter(argument.getKey(), argument.getValue().value());
                }
                return signature;
            }).toList();
        }

        private CallableSignature removeBoundParameters(CallableSignature signature) {
            ArrayList<List<Integer>> positions = new ArrayList<>();
            for (int index = 0; index < signature.parameters().size(); index++) {
                if (!arguments.containsKey(index)) positions.add(List.of(index));
            }
            return signature.projectParameters(positions);
        }

        @Override public String publicName() { return name; }
        @Override public String toString() { return "<overload " + name + "/" + remainingArity() + ">"; }
    }

    private int variantArity(OverloadVariant variant) {
        return variant.definition() == null ? variant.function().remainingArity() : variant.definition().params().size();
    }

    private ContractClause variantClause(OverloadVariant variant, int position) {
        return variant.definition() == null ? null : variant.definition().params().get(position).contracts();
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
                : env.getResolved(binding.binding()))
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
        for (int position = 0; position < variantArity(left); position++) {
            ContractClause l = variantClause(left, position);
            ContractClause r = variantClause(right, position);
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
        List<Diagnostic.Related> related = variants.stream().filter(variant -> variant.definition() != null)
                .map(variant -> new Diagnostic.Related(
                        "Overload variant declared here", variant.definition().span())).toList();
        return new LangException(new Diagnostic(Diagnostic.Phase.RUNTIME, code, message, span, related));
    }

    private Value validateContracts(Value value, SourceSpan valueSpan, ContractClause clause,
                                   Resolution resolution, Environment contractEnvironment, String subject) {
        return validateContracts(value, valueSpan, clause, resolution, contractEnvironment, subject, true);
    }

    private Value validateContracts(Value value, SourceSpan valueSpan, ContractClause clause,
                                   Resolution resolution, Environment contractEnvironment, String subject,
                                   boolean constrainCallableEffects) {
        LinkedHashSet<ContractDescriptor> acquired = new LinkedHashSet<>();
        for (Resolution.ContractBinding reference : resolution.contracts(clause)) {
            if (isContractVariable(reference.name())) continue;
            Value resolved = reference.inline() == null
                    ? underlying(reference.binding() == null ? globals.get(reference.name())
                    : contractEnvironment.getResolved(reference.binding()))
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
        if (constrainCallableEffects) validateEffectConstraint(value, valueSpan, clause, resolution);
        if (acquired.isEmpty()) return value;
        if (value instanceof Value.Attributed(Value value1, Set<ContractDescriptor> contracts)) {
            acquired.addAll(contracts);
            return new Value.Attributed(value1, acquired);
        }
        return new Value.Attributed(value, acquired);
    }

    private void validateEffectConstraint(Value value, SourceSpan valueSpan, ContractClause clause,
                                          Resolution resolution) {
        Resolution.AnalyzedClause analyzed = resolution.clause(clause);
        if (analyzed == null || analyzed.effectAllowance() == null) return;
        Value candidate = underlying(value);
        if (!(candidate instanceof Value.Callable callable)) {
            throw new LangException(new Diagnostic(Diagnostic.Phase.RUNTIME,
                    Diagnostic.Codes.EFFECT_CONSTRAINT_REQUIRES_CALLABLE,
                    "Effect constraint requires a callable value", valueSpan,
                    List.of(new Diagnostic.Related("Effect constraint declared here", analyzed.span()))));
        }
        List<String> upper = callable.signature().effects().upperBound();
        if (upper == null) {
            throw new LangException(new Diagnostic(Diagnostic.Phase.RUNTIME,
                    Diagnostic.Codes.UNKNOWN_CALL_EFFECTS,
                    "Callable invocation has no known effect upper bound", valueSpan,
                    List.of(new Diagnostic.Related("Effect constraint declared here", analyzed.span()))));
        }
        Set<String> allowed = analyzed.effectAllowance().stream().map(EffectDescriptor::canonicalName)
                .collect(java.util.stream.Collectors.toSet());
        if (!allowed.containsAll(upper)) {
            LinkedHashSet<String> unexpected = new LinkedHashSet<>(upper);
            unexpected.removeAll(allowed);
            throw new LangException(new Diagnostic(Diagnostic.Phase.RUNTIME,
                    Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED,
                    "Callable effect allowance exceeded: " + String.join(", ", unexpected), valueSpan,
                    List.of(new Diagnostic.Related("Effect constraint declared here", analyzed.span()))));
        }
    }

    private ContractDescriptor resolveContractDescriptor(Resolution.ContractBinding reference,
                                                         Environment contractEnvironment,
                                                         Resolution resolution) {
        if (isContractVariable(reference.name())) return BuiltinContract.ANY;
        Value resolved = reference.inline() == null
                ? underlying(reference.binding() == null ? globals.get(reference.name())
                : contractEnvironment.getResolved(reference.binding()))
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
                            supplied -> eval(captured, env, supplied, resolution), holeSignature(captured, arity));
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
            return CallableSignature.summarize(variantSignatures());
        }
        @Override public List<CallableSignature> variantSignatures() {
            return overload.fullVariantSignatures().stream().map(signature ->
                    projectHoleSignature(signature, pending, arity, arguments.size())).toList();
        }
        @Override public String toString() { return "<overload-partial " + display + "/" + remainingArity() + ">"; }
    }

    private Value evalInnerUnchecked(Expr expr, Environment env, Resolution resolution) {
        if (expr instanceof Literal(Value value1, SourceSpan ignored)) return value1;
        if (expr instanceof Name nameExpression) {
            Resolution.Binding binding = resolution.binding(nameExpression);
            Value value = binding == null ? env.get(nameExpression.name()) : env.getResolved(binding);
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
            CallableSignature.Composition composition = CallableSignature.compose(
                    leftCallable.signature(), rightCallable.signature());
            if (composition.compatibility() == CallableSignature.Compatibility.INCOMPATIBLE) {
                throw incompatibleComposition(expr.span(), leftExpression.span(), rightExpression.span());
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
                targetValue = binding == null ? env.get(nameExpression.name()) : env.getResolved(binding);
            } else {
                targetValue = evalInner(target, env, resolution);
            }
            return reflect(targetValue);
        }
        if (expr instanceof Dereference(Expr target, SourceSpan ignored)) {
            Value reference = underlying(evalInner(target, env, resolution));
            if (reference instanceof Value.Dictionary dictionary) {
                Optional<Value> reflected = dictionary.reflectedTarget();
                if (reflected.isPresent()) return reflected.get();
            }
            throw runtime(Diagnostic.Codes.NOT_DEREFERENCEABLE,
                    "Value is not dereferenceable: " + reference, expr.span());
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
        if (expr instanceof CollectionLiteral(List<CollectionElement> elements, SourceSpan ignored)) {
            if (elements.isEmpty()) return Value.EmptyCollection.INSTANCE;
            ArrayList<Value> values = new ArrayList<>(elements.size());
            for (CollectionElement element : elements) {
                Value value = evalInner(element.value(), env, resolution);
                values.add(element instanceof NamedElement named
                        ? new Value.Field(named.name(), value) : value);
            }
            boolean fields = values.stream().allMatch(Value.Field.class::isInstance);
            boolean ordinary = values.stream().noneMatch(Value.Field.class::isInstance);
            if (!fields && !ordinary) {
                int mixed = 0;
                boolean firstIsField = values.getFirst() instanceof Value.Field;
                while ((values.get(mixed) instanceof Value.Field) == firstIsField) mixed++;
                throw new LangException(Diagnostic.Phase.RUNTIME,
                        Diagnostic.Codes.MIXED_COLLECTION_SHAPE,
                        "A collection cannot mix Field values and positional elements",
                        elements.get(mixed).span());
            }
            if (ordinary) return new Value.Seq(values);
            LinkedHashMap<String, Value> dictionary = new LinkedHashMap<>();
            LinkedHashMap<String, SourceSpan> locations = new LinkedHashMap<>();
            for (int i = 0; i < values.size(); i++) {
                Value.Field field = (Value.Field) values.get(i);
                SourceSpan first = locations.putIfAbsent(field.key(), elements.get(i).span());
                if (first != null) {
                    throw new LangException(new Diagnostic(Diagnostic.Phase.RUNTIME,
                            Diagnostic.Codes.DUPLICATE_FIELD,
                            "Duplicate field: " + field.key(), elements.get(i).span(),
                            List.of(new Diagnostic.Related(
                                    "First field named " + field.key(), first))));
                }
                dictionary.put(field.key(), field.value());
            }
            return new Value.Dictionary(dictionary);
        }
        if (expr instanceof ArrowContract(List<List<Expr>> parameters, Expr result, List<Name> effectTerms,
                                          boolean explicitPure, SourceSpan ignored)) {
            ArrayList<List<ContractDescriptor>> parameterDescriptors = new ArrayList<>();
            for (List<Expr> parameter : parameters) {
                parameterDescriptors.add(parameter.stream()
                        .map(requirement -> arrowRequirement(requirement, env, resolution)).toList());
            }
            ContractDescriptor resultDescriptor = arrowRequirement(result, env, resolution);
            return new Value.ContractValue(new ArrowContractDescriptor(
                    List.copyOf(parameterDescriptors), resultDescriptor, effectTerms.stream()
                    .map(effect -> effectCatalog.resolve(effect.name()).orElseThrow()).toList()));
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
        return binding == null ? env.get(expression.name()) : env.getResolved(binding);
    }

    private Value invoke(Value.Callable callable, Value.Argument argument, SourceSpan span) {
        return calls.invoke(callable, argument, span);
    }

    private Value invokeZero(Value.Callable callable, SourceSpan span) {
        return calls.invokeZero(callable, span);
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

        globals.define("toString", locatedFunction("toString", List.of("value"), (args, span) -> {
            Value value = underlying(args.getFirst().value());
            if (value instanceof Value.Callable) {
                throw runtime(Diagnostic.Codes.CALLABLE_RENDERING,
                        "Callable values do not have a standard textual representation", span);
            }
            return new Value.Str(ValueSemantics.render(value, nested -> {
                Value callable = globals.get("toString");
                Value rendered = underlying(invoke((Value.Callable) callable,
                        new Value.Argument(nested, span), span));
                if (!(rendered instanceof Value.Str(String value1))) {
                    throw runtime(Diagnostic.Codes.EXPECTED_STRING,
                            "toString specialization must return a String", span);
                }
                return value1;
            }));
        }));

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

        globals.define("field", locatedFunction("field", List.of("key", "value"), (args, ignored) ->
                new Value.Field(requiredDictionaryKey(args.getFirst()), args.get(1).value())));

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
        List<String> mapParameters = List.of("transform", "values");
        globals.define("map", new Value.FunctionValue("map", mapParameters, (args, span) -> {
            Value.Callable transform = unaryMapTransform(args.getFirst());
            Value.Seq values = sequence(args.get(1));
            ArrayList<Value> mapped = new ArrayList<>(values.size());
            for (Value value : values) {
                mapped.add(invoke(transform, new Value.Argument(value, args.get(1).span()), span));
            }
            return new Value.Seq(mapped);
        }, false, CallableSignature.unknown(mapParameters)));

        globals.define("dictEmpty", function("dictEmpty", List.of(), args -> new Value.Dictionary(Map.of())));
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
        if (raw instanceof Value.EmptyCollection) return new Value.Seq(List.of());
        if (raw instanceof Value.Seq sequence) return sequence;
        throw runtime(Diagnostic.Codes.EXPECTED_SEQUENCE,
                "Expected sequence, got: " + argument.value(), argument.span());
    }

    private Value.Callable unaryMapTransform(Value.Argument argument) {
        Value raw = underlying(argument.value());
        if (raw instanceof Value.Callable callable && callable.remainingArity() == 1) return callable;
        throw runtime(Diagnostic.Codes.INVALID_MAP_TRANSFORM,
                "map transform must be a callable requiring exactly one argument", argument.span());
    }

    private Value.Dictionary dictionary(Value.Argument argument) {
        Value raw = underlying(argument.value());
        if (raw instanceof Value.EmptyCollection) return new Value.Dictionary(Map.of());
        if (raw instanceof Value.Dictionary dictionary) return dictionary;
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
                    "Field access requires a named collection or reflective value, got: " + target);
        }
        Optional<Value> value = reflective.find(name);
        if (value.isPresent()) return value.get();
        if (optional) return Value.Missing.INSTANCE;
        if (target instanceof Value.Dictionary || target instanceof Value.EmptyCollection) {
            throw runtime(Diagnostic.Codes.MISSING_FIELD, "Collection has no field: " + name);
        }
        throw runtime(Diagnostic.Codes.MISSING_FIELD, "Reflected value has no field: " + name);
    }

    private Value reflect(Value value) {
        Value reflected = underlying(value);
        Map<String, Value> fields = reflected instanceof Value.Callable callable
                && !(reflected instanceof Value.Reflective)
                ? Value.CallableMetadata.fields(callable)
                : ValueSemantics.reflectionFields(reflected);
        return Value.Dictionary.reflection(fields, value);
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

    private CallableSignature holeSignature(Expr expression, int arity) {
        ArrayList<Expr> arguments = new ArrayList<>();
        Expr target = expression;
        while (target instanceof Apply apply) {
            arguments.addFirst(apply.argument());
            target = apply.function();
        }
        if (!(target instanceof Literal literal) || !(underlying(literal.value()) instanceof Value.Callable callable)) {
            return CallableSignature.unknown(Collections.nCopies(arity, null));
        }
        CallableSignature specialized = callable.signature();
        for (int index = 0; index < arguments.size() && index < specialized.parameters().size(); index++) {
            if (arguments.get(index) instanceof Literal fixed) {
                specialized = specialized.specializeParameter(index, fixed.value());
            }
        }
        ArrayList<List<Integer>> projected = emptyPositionProjection(arity);
        int ordinaryIndex = 0;
        for (int argumentIndex = 0; argumentIndex < arguments.size(); argumentIndex++) {
            Expr argument = arguments.get(argumentIndex);
            if (argumentIndex >= specialized.parameters().size()) break;
            if (argument instanceof Hole hole) {
                int index = hole.index() == 0 ? ordinaryIndex++ : hole.index() - 1;
                ArrayList<Integer> positions = new ArrayList<>(projected.get(index));
                positions.add(argumentIndex);
                projected.set(index, List.copyOf(positions));
            } else {
                if (!(argument instanceof Literal)) {
                    return CallableSignature.unknown(Collections.nCopies(arity, null));
                }
            }
        }
        return specialized.projectParameters(projected);
    }

    private CallableSignature projectHoleSignature(CallableSignature signature,
                                                    List<PendingOverloadArgument> pending,
                                                    int totalArity, int supplied) {
        ArrayList<List<Integer>> projected = emptyPositionProjection(totalArity - supplied);
        for (PendingOverloadArgument argument : pending) {
            if (!(argument.expression() instanceof Hole hole) || hole.index() == 0) continue;
            int publicIndex = hole.index() - 1 - supplied;
            if (publicIndex < 0 || publicIndex >= projected.size()) continue;
            ArrayList<Integer> positions = new ArrayList<>(projected.get(publicIndex));
            positions.add(argument.position());
            projected.set(publicIndex, List.copyOf(positions));
        }
        return signature.projectParameters(projected);
    }

    private static ArrayList<List<Integer>> emptyPositionProjection(int arity) {
        ArrayList<List<Integer>> result = new ArrayList<>();
        for (int index = 0; index < arity; index++) result.add(List.of());
        return result;
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

    private static boolean isContractVariable(String name) { return name.matches("_[1-9][0-9]*"); }
}
