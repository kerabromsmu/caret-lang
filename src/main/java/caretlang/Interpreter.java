package caretlang;

import caretlang.Ast.*;

import java.io.PrintStream;
import java.util.*;

final class Interpreter {
    private final Environment builtins = new Environment(null);
    private final Environment globals = new Environment(builtins);
    private final PrintStream output;
    private final java.util.function.BooleanSupplier outputAllowed;
    private final CallableDispatcher calls = new CallableDispatcher();
    private ContractInference inference;
    private final EffectCatalog effectCatalog;
    private final OwnershipTracker ownership;
    private ReflectionContext reflectionContext = ReflectionContext.defining();
    private enum NumericPolicy { BROAD, STRICT }
    private NumericPolicy numericPolicy = NumericPolicy.BROAD;
    private final ArrayList<Diagnostic> warnings = new ArrayList<>();
    private final Set<SourceSpan> staticallyReportedPrecisionLosses = new HashSet<>();
    private List<Stmt> staticallyAnalyzedProgram;
    private boolean pendingValidatedProgram;

    List<Diagnostic> warnings() { return List.copyOf(warnings); }
    private final IdentityHashMap<ContractDescriptor, Map<Integer, ContractDescriptor>> modifiedContracts =
            new IdentityHashMap<>();
    private final Map<String, ContractInference.ExternalCallable> embeddingCallables = new LinkedHashMap<>();

    Interpreter(PrintStream output) {
        this(output, null);
    }

    Interpreter(PrintStream output, TestReporter testReporter) {
        this(output, testReporter, EffectCatalog.standard(testReporter != null));
    }

    Interpreter(PrintStream output, TestReporter testReporter, EffectCatalog effectCatalog) {
        this(output, testReporter, effectCatalog, OwnershipTracker.Mode.ENABLED);
    }

    Interpreter(PrintStream output, TestReporter testReporter, EffectCatalog effectCatalog,
                OwnershipTracker.Mode ownershipMode) {
        this(output, testReporter, effectCatalog, ownershipMode, () -> true);
    }

    Interpreter(PrintStream output, TestReporter testReporter, EffectCatalog effectCatalog,
                OwnershipTracker.Mode ownershipMode, java.util.function.BooleanSupplier outputAllowed) {
        this.output = output;
        this.outputAllowed = Objects.requireNonNull(outputAllowed);
        this.effectCatalog = Objects.requireNonNull(effectCatalog);
        this.ownership = new OwnershipTracker(Objects.requireNonNull(ownershipMode));
        installBuiltins();
        if (testReporter != null) installTestBuiltins(testReporter);
    }

    void reflectionContext(ReflectionContext context) {
        this.reflectionContext = Objects.requireNonNull(context);
    }

    Value execute(List<Stmt> program) {
        warnings.clear();
        Environment.Checkpoint checkpoint = globals.checkpoint();
        try {
            Resolution resolution = Resolver.resolve(program, globals, effectCatalog);
            inference = ContractInference.analyze(program, resolution, embeddingCallables, effectCatalog);
            validateEffectAllowances(program, resolution);
            validateCompositionCompatibility(program, resolution);
            if (!pendingValidatedProgram || program != staticallyAnalyzedProgram)
                analyzeStaticPrecision(program);
            pendingValidatedProgram = false;
            return executeBlock(program, globals, resolution);
        } catch (RuntimeException | Error failure) {
            globals.rollbackTo(checkpoint);
            throw failure;
        }
    }

    void validate(List<Stmt> program) {
        warnings.clear();
        pendingValidatedProgram = false;
        Resolution resolution = Resolver.resolve(program, globals, effectCatalog);
        inference = ContractInference.analyze(program, resolution, embeddingCallables, effectCatalog);
        validateEffectAllowances(program, resolution);
        validateCompositionCompatibility(program, resolution);
        analyzeStaticPrecision(program);
        pendingValidatedProgram = true;
    }

    void defineEmbeddingValue(String name, java.util.function.Supplier<Value> supplier) {
        builtins.defineLazy(name, supplier);
    }

    void resetEmbeddingValue(String name, java.util.function.Supplier<Value> supplier) {
        builtins.resetLazy(name, supplier);
    }

    void defineEmbeddingCallable(String name, int arity,
                                 java.util.function.Function<List<Value>, Value> callback,
                                 List<String> effects) {
        List<String> parameters = java.util.stream.IntStream.range(0, arity)
                .mapToObj(index -> "arg" + (index + 1)).toList();
        builtins.define(name, new Value.FunctionValue(name, parameters,
                (arguments, ignored) -> callback.apply(arguments.stream().map(Value.Argument::value).toList()),
                false, CallableSignature.builtin(parameters, effects)));
        embeddingCallables.put(name, new ContractInference.ExternalCallable(arity, Set.copyOf(effects)));
    }

    Value invokeEmbedding(Value.Callable callable, List<Value> arguments) {
        warnings.clear();
        Environment.Checkpoint checkpoint = globals.checkpoint();
        try {
            Value result = callable;
            SourcePosition position = new SourcePosition(0, 1, 1);
            SourceSpan span = new SourceSpan(position, position);
            if (arguments.isEmpty()) return callable.invokeZero(span);
            for (Value argument : arguments) {
                if (!(result instanceof Value.Callable next)) {
                    throw new IllegalArgumentException("Too many arguments");
                }
                result = invoke(next, new Value.Argument(argument, span), span);
            }
            return result;
        } catch (RuntimeException | Error failure) {
            globals.rollbackTo(checkpoint);
            throw failure;
        }
    }

    <T> T embeddingTransaction(java.util.function.Supplier<T> operation) {
        Environment.Checkpoint checkpoint = globals.checkpoint();
        try {
            return operation.get();
        } catch (RuntimeException | Error failure) {
            globals.rollbackTo(checkpoint);
            throw failure;
        }
    }

    Value.Dictionary topLevelBindings(List<Stmt> program) {
        LinkedHashMap<String, Value> bindings = new LinkedHashMap<>();
        for (Stmt statement : program) {
            String name = switch (statement) {
                case Assign assign -> assign.name();
                case FunctionDef function -> function.name();
                default -> null;
            };
            if (name != null) bindings.put(name, globals.get(name));
        }
        return new Value.Dictionary(bindings);
    }

    String inspect(List<Stmt> program) {
        Resolution resolution = Resolver.resolve(program, globals, effectCatalog);
        inference = ContractInference.analyze(program, resolution, embeddingCallables, effectCatalog);
        validateEffectAllowances(program, resolution);
        validateCompositionCompatibility(program, resolution);
        return InferenceReporter.render(program, inference, resolution);
    }

    private void validateEffectAllowances(List<Stmt> statements, Resolution resolution) {
        for (Stmt statement : statements) {
            if (!(statement instanceof FunctionDef function)) {
                Expr expression = statementExpression(statement);
                if (expression != null) AstTraversal.walkPreOrder(expression, candidate -> {
                    if (candidate instanceof With with) validateEffectAllowances(with.body(), resolution);
                });
                continue;
            }
            Resolution.AnalyzedClause clause = resolution.clause(function.resultContracts());
            Set<String> allowed = clause == null || clause.effectAllowance() == null
                    ? Set.of() : clause.effectAllowance().stream().map(EffectDescriptor::canonicalName)
                    .collect(java.util.stream.Collectors.toSet());
            ContractInference.EffectSummary actual = inference.effects(function);
            // Unknown higher-order calls are rejected at the dynamic invocation boundary until
            // parameter-effect substitution is available; known effects are checked here.
            Set<String> inferred = actual.symbolicEffects();
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

    private static Expr statementExpression(Stmt statement) {
        return switch (statement) {
            case Assign assign -> assign.value();
            case ExprStmt line -> line.expression();
            case PrintLine line -> line.builtinArgument();
            case FunctionDef ignored -> null;
        };
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
            if (statement instanceof FunctionDef function) {
                Integer symbol = resolution.symbolId(function.span());
                if (symbol != null) signatures.computeIfAbsent(symbol, ignored -> new ArrayList<>())
                        .add(CallableSignature.inferred(function, Objects.requireNonNull(inference), resolution));
                collectFunctionSignatures(function.body(), resolution, signatures);
            } else {
                Expr expression = statementExpression(statement);
                if (expression != null) AstTraversal.walkPreOrder(expression, candidate -> {
                    if (candidate instanceof With with) collectFunctionSignatures(with.body(), resolution, signatures);
                });
            }
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
        if (expression instanceof With with) {
            validateCompositionCompatibility(with.target(), resolution, signatures);
            validateCompositionCompatibility(with.body(), resolution, signatures);
            return;
        }
        for (Expr child : AstTraversal.children(expression)) {
            validateCompositionCompatibility(child, resolution, signatures);
        }
        if (!(expression instanceof Compose(Expr left1, Expr right1, SourceSpan span))) return;
        CallableSignature left = knownSignature(left1, resolution, signatures);
        CallableSignature right = knownSignature(right1, resolution, signatures);
        if (left == null || right == null || right.parameters().size() != 1) return;
        CallableSignature.Composition composition = CallableSignature.compose(left, right);
        if (composition.compatibility() == CallableSignature.Compatibility.INCOMPATIBLE) {
            throw incompatibleComposition(span, left1.span(), right1.span());
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
        return executeBlock(statements, env, resolution, null, null, null);
    }

    private Value executeBlock(List<Stmt> statements, Environment env, Resolution resolution,
                               TemplateContract resultTemplate, BuiltinContract resultFormat,
                               ParameterizedContract resultPacked) {
        LinkedHashMap<String, Value.Field> exports = new LinkedHashMap<>();
        IdentityHashMap<FunctionDef, Value.Callable> functions = prepareDeclarations(statements, env, resolution);
        IdentityHashMap<Assign, UserContract> contractPlaceholders = prepareContractDeclarations(statements, env);
        Value last = Value.Missing.INSTANCE;

        for (Stmt statement : statements) {
            if (statement instanceof Assign(String name, boolean exported, ContractClause contracts,
                                            Expr value1, SourceSpan ignored)) {
                Value value;
                NumericPolicy previousPolicy = numericPolicy;
                numericPolicy = numericPolicy(contracts, env, resolution, previousPolicy);
                try {
                    if (value1 instanceof CollectionLiteral collection
                            && analyzeCollectionHoles(collection).indexes().isEmpty()) {
                        TemplateContract template = expectedTemplate(contracts, env, resolution);
                        if (template == null && !exported && statement == statements.getLast()) {
                            template = resultTemplate;
                        }
                        CollectionShape shape = expectedCollectionShape(contracts, env, resolution);
                        if (shape == CollectionShape.INFER && template != null && template.descriptor().root().named()) {
                            shape = CollectionShape.DICTIONARY;
                        }
                        ParameterizedContract packed = expectedPacked(contracts, env, resolution);
                        if (packed == null && contracts == null && !exported
                                && statement == statements.getLast()) packed = resultPacked;
                        value = packed != null ? evaluatePackedLiteral(collection, env, resolution, packed)
                                : evaluateCollection(collection, env, resolution, shape, template);
                        if (packed != null) value = contextualPackedLiteral(packed, value, collection.span());
                    } else if (ungroup(value1) instanceof Literal(Value.Num number, SourceSpan span)) {
                        value = contextualNumericLiteral(number, span,
                                contracts == null && !exported && statement == statements.getLast()
                                        ? resultFormat : expectedNumericFormat(contracts, env, resolution));
                    } else value = eval(value1, env, null, resolution);
                } finally {
                    numericPolicy = previousPolicy;
                }
                ArrayList<ContractDescriptor> exportedContracts = exported ? new ArrayList<>() : null;
                value = validateContracts(value, value1.span(), contracts, resolution, env,
                        "binding " + name, true, exportedContracts);
                UserContract placeholder = contractPlaceholders.get(statement);
                if (placeholder != null && value instanceof Value.ContractValue contract
                        && contract.descriptor() instanceof UserContract constructed) {
                    placeholder.configureFrom(constructed);
                    placeholder.nameIfAnonymous(name);
                    rejectContractCycle(placeholder, statement.span());
                    value = new Value.ContractValue(placeholder);
                    env.replace(name, value);
                } else {
                    if (value instanceof Value.ContractValue contract
                            && contract.descriptor() instanceof UserContract user) user.nameIfAnonymous(name);
                    if (value instanceof Value.ContractValue contract
                            && contract.descriptor() instanceof TemplateContract template) {
                        template.nameIfAnonymous(name);
                    }
                    if (underlying(value) instanceof Value.LazyCollection collection) collection.lockShape();
                    ownership.share(value);
                    env.initialize(name, value);
                }
                if (exported) {
                    ownership.share(value);
                    exports.put(name, new Value.Field(new Value.Str(name), value, exportedContracts));
                }
                last = value;
            } else if (statement instanceof ExprStmt(Expr expression, SourceSpan ignored)) {
                if (statement == statements.getLast() && resultPacked != null
                        && expression instanceof CollectionLiteral collection
                        && analyzeCollectionHoles(collection).indexes().isEmpty()) {
                    Value value = evaluatePackedLiteral(collection, env, resolution, resultPacked);
                    last = contextualPackedLiteral(resultPacked, value, collection.span());
                } else if (statement == statements.getLast() && resultTemplate != null
                        && expression instanceof CollectionLiteral collection
                        && analyzeCollectionHoles(collection).indexes().isEmpty()) {
                    last = evaluateCollection(collection, env, resolution,
                            resultTemplate.descriptor().root().named()
                                    ? CollectionShape.DICTIONARY : CollectionShape.KEYLESS, resultTemplate);
                } else if (statement == statements.getLast() && resultFormat != null
                        && ungroup(expression) instanceof Literal(Value.Num number, SourceSpan span)) {
                    last = contextualNumericLiteral(number, span, resultFormat);
                } else last = eval(expression, env, null, resolution);
            } else if (statement instanceof PrintLine line) {
                last = eval(usesBuiltinPrint(line, env, resolution)
                        ? new Apply(line.target(), line.builtinArgument(), line.span())
                        : line.ordinaryCall(), env, null, resolution);
            } else if (statement instanceof FunctionDef function) {
                last = functions.get(function);
            }
        }

        if (exports.isEmpty()) return last;
        if (resultTemplate != null && resultTemplate.descriptor().root().named()) {
            SourceSpan block = SourceSpan.cover(statements.getFirst().span(), statements.getLast().span());
            for (CollectionConstructorDescriptor.Element element : resultTemplate.descriptor().root().elements()) {
                if (exports.containsKey(element.name())) continue;
                if (element.defaultsMissing()) {
                    exports.put(element.name(), new Value.Field(new Value.Str(element.name()),
                            Value.Missing.INSTANCE));
                } else {
                    throw new LangException(new Diagnostic(Diagnostic.Phase.RUNTIME,
                            Diagnostic.Codes.CONTRACT_VIOLATION,
                            "Contract violation for exported block: missing field " + element.name(),
                            block, List.of(new Diagnostic.Related("Template field declared here", element.span()))));
                }
            }
        }
        return ownership.fresh(Value.Dictionary.fromFields(exports));
    }

    private IdentityHashMap<Assign, UserContract> prepareContractDeclarations(List<Stmt> statements,
                                                                               Environment env) {
        IdentityHashMap<Assign, UserContract> result = new IdentityHashMap<>();
        for (Stmt statement : statements) {
            if (!(statement instanceof Assign assign) || !isContractConstruction(assign.value())) continue;
            UserContract placeholder = new UserContract(assign.span());
            placeholder.nameIfAnonymous(assign.name());
            env.initialize(assign.name(), new Value.ContractValue(placeholder));
            result.put(assign, placeholder);
        }
        return result;
    }

    private static boolean isContractConstruction(Expr expression) {
        while (expression instanceof Group group) expression = group.expression();
        return expression instanceof Apply apply && apply.function() instanceof Name name
                && name.name().equals("contract");
    }

    private static void rejectContractCycle(UserContract root, SourceSpan span) {
        Set<ContractDescriptor> visiting = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ContractDescriptor> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<UserContract> path = new ArrayList<>();
        if (!findContractCycle(root, visiting, visited, path)) return;
        List<Diagnostic.Related> related = path.stream().map(contract -> new Diagnostic.Related(
                "Contract in derivation cycle: " + contract.publicName(), contract.declarationSpan()))
                .filter(item -> item.span() != null).toList();
        throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                Diagnostic.Codes.CONTRACT_DERIVATION_CYCLE,
                "Contract derivation cycle: " + root.publicName(), span, related));
    }

    private static boolean findContractCycle(ContractDescriptor current, Set<ContractDescriptor> visiting,
                                             Set<ContractDescriptor> visited, List<UserContract> path) {
        if (visiting.contains(current)) return true;
        if (!visited.add(current)) return false;
        visiting.add(current);
        if (current instanceof UserContract user) path.add(user);
        for (ContractDescriptor base : current.bases()) {
            if (findContractCycle(base, visiting, visited, path)) return true;
        }
        visiting.remove(current);
        if (current instanceof UserContract) path.removeLast();
        return false;
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
            Value inheritedBuiltin = env == globals ? env.inheritedValue(entry.getKey()) : null;
            if (existing instanceof Value.Callable callable && !(existing instanceof Value.ContractValue)) {
                variants.add(new OverloadVariant(null, callable));
            } else if (inheritedBuiltin instanceof Value.Callable callable
                    && !(inheritedBuiltin instanceof Value.ContractValue)) {
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
                        }, index -> expectedTemplate(function.params().get(index).contracts(), env, resolution),
                                index -> expectedPacked(function.params().get(index).contracts(), env, resolution),
                                index -> expectedNumericFormat(function.params().get(index).contracts(),
                                        env, resolution),
                                index -> numericPolicy(function.params().get(index).contracts(), env, resolution,
                                        NumericPolicy.BROAD) == NumericPolicy.STRICT);
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
                Value value = arguments.get(i).value();
                ownership.share(value);
                parameters.define(function.params().get(i).name(), value);
            }
            NumericPolicy previousPolicy = numericPolicy;
            numericPolicy = numericPolicy(function.resultContracts(), env, resolution, NumericPolicy.BROAD);
            Value result;
            try {
                result = executeBlock(function.body(), new Environment(parameters), resolution,
                        expectedTemplate(function.resultContracts(), env, resolution),
                        expectedNumericFormat(function.resultContracts(), env, resolution),
                        expectedPacked(function.resultContracts(), env, resolution));
            } finally {
                numericPolicy = previousPolicy;
            }
            return validateContracts(result, function.body().getLast().span(),
                    function.resultContracts(), resolution, env, "result of " + function.name(), false);
        }, refinementEligible, CallableSignature.inferred(function, Objects.requireNonNull(inference), resolution));
    }

    private Value.Callable lambdaFunction(Lambda lambda, Environment env, Resolution resolution) {
        List<String> parameterNames = lambda.params().stream().map(Parameter::name).toList();
        boolean refinementEligible = Objects.requireNonNull(inference).isRefinementEligible(lambda);
        LinkedHashMap<Integer, Environment.BindingReference> captures = new LinkedHashMap<>();
        for (Resolution.Upvalue upvalue : resolution.upvalues(lambda)) {
            captures.put(upvalue.symbolId(), env.referenceAt(upvalue.lexicalDepth(), upvalue.slot()));
        }
        Value.Callable raw = new Value.FunctionValue("<anonymous>", parameterNames, (arguments, ignoredCallSpan) -> {
            Environment parameters = new Environment(env, captures);
            for (int index = 0; index < lambda.params().size(); index++) {
                Value value = arguments.get(index).value();
                ownership.share(value);
                parameters.define(lambda.params().get(index).name(), value);
            }
            return executeBlock(lambda.body(), new Environment(parameters), resolution);
        }, refinementEligible, inference.signature(lambda));
        if (lambda.params().stream().noneMatch(parameter -> parameter.contracts() != null)) return raw;
        return new Value.ContractedCallable(raw, (index, argument) -> {
            Parameter parameter = lambda.params().get(index);
            Value checked = validateContracts(argument.value(), argument.span(), parameter.contracts(),
                    resolution, env, "parameter " + parameter.name());
            return new Value.Argument(checked, argument.span());
        }, index -> expectedTemplate(lambda.params().get(index).contracts(), env, resolution),
                index -> expectedPacked(lambda.params().get(index).contracts(), env, resolution),
                index -> expectedNumericFormat(lambda.params().get(index).contracts(), env, resolution),
                index -> numericPolicy(lambda.params().get(index).contracts(), env, resolution,
                        NumericPolicy.BROAD) == NumericPolicy.STRICT);
    }

    private record OverloadVariant(FunctionDef definition, Value.Callable function) {}
    private record ApplicabilityKey(Object requirement, int position) {}
    private record RefinementRequirement(Value.Callable callable, boolean nullable, boolean optional) {}

    private final class BuiltinOperatorCallable implements Value.Callable {
        private final String operator;
        private final List<Value.Argument> arguments;

        private BuiltinOperatorCallable(String operator) { this(operator, List.of()); }

        private BuiltinOperatorCallable(String operator, List<Value.Argument> arguments) {
            this.operator = operator;
            this.arguments = List.copyOf(arguments);
        }

        @Override public Value apply(Value.Argument argument, SourceSpan callSpan) {
            ArrayList<Value.Argument> next = new ArrayList<>(arguments);
            next.add(argument);
            if (next.size() == 2) return binaryOperation(operator, next, callSpan);
            if (next.size() > 2) throw new LangException(Diagnostic.Phase.RUNTIME,
                    Diagnostic.Codes.TOO_MANY_ARGUMENTS, "Too many arguments for " + operator, callSpan);
            return new BuiltinOperatorCallable(operator, next);
        }

        @Override public int remainingArity() { return 2 - arguments.size(); }

        @Override public String publicName() { return operator; }

        @Override public CallableSignature signature() {
            return CallableSignature.summarize(variantSignatures());
        }

        @Override public List<CallableSignature> variantSignatures() {
            return operatorSignatures(operator).stream().map(signature -> arguments.isEmpty()
                    ? signature : signature.specializeFirst(arguments.getFirst().value())).toList();
        }
        @Override public List<Value> retainedValues() {
            return arguments.stream().map(Value.Argument::value).toList();
        }
    }

    /** Higher-order map exposes the selected transform's invocation bound after partial application. */
    private final class MapCallable implements Value.Callable {
        private final Value.Callable transform;

        private MapCallable() { this(null); }
        private MapCallable(Value.Callable transform) { this.transform = transform; }

        @Override public Value apply(Value.Argument argument, SourceSpan callSpan) {
            if (transform == null) return new MapCallable(unaryMapTransform(argument));
            IndexedCollection source = indexedFields(argument);
            boolean pure = knownPure(transform);
            CollectionRuntime.Facts sourceFacts = source.facts();
            CollectionRuntime.Guarantee sequential = sourceFacts.sequential() == CollectionRuntime.Guarantee.TRUE
                    && pure ? CollectionRuntime.Guarantee.TRUE : CollectionRuntime.Guarantee.UNKNOWN;
            CollectionRuntime.Facts keylessFacts = new CollectionRuntime.Facts(sequential,
                    sourceFacts.ordered(), CollectionRuntime.Guarantee.UNKNOWN, sourceFacts.finite(),
                    CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.TRUE);
            boolean sourceSet = sourceFacts.keyed() == CollectionRuntime.Guarantee.TRUE
                    && sourceFacts.hasValues() == CollectionRuntime.Guarantee.FALSE;
            boolean oneToOne = !sourceSet && definitelyNonField(transform);
            if (oneToOne && source.knownSize() != null) {
                return ownership.fresh(new Value.LazySeq(source.knownSize(), index -> invoke(transform,
                        new Value.Argument(source.at(index).orElseThrow(), argument.span()), callSpan), keylessFacts));
            }
            int[] next = {0};
            Value.LazyCollection.Shape initialShape = sourceSet
                    ? Value.LazyCollection.Shape.SET
                    : oneToOne ? Value.LazyCollection.Shape.KEYLESS : Value.LazyCollection.Shape.INFER;
            CollectionRuntime.Facts facts = new CollectionRuntime.Facts(sequential, sourceFacts.ordered(),
                    sourceSet ? CollectionRuntime.Guarantee.TRUE : CollectionRuntime.Guarantee.UNKNOWN,
                    sourceFacts.finite(), sourceSet ? CollectionRuntime.Guarantee.TRUE
                    : oneToOne ? CollectionRuntime.Guarantee.FALSE : CollectionRuntime.Guarantee.UNKNOWN,
                    sourceSet ? CollectionRuntime.Guarantee.FALSE
                    : oneToOne ? CollectionRuntime.Guarantee.TRUE : CollectionRuntime.Guarantee.UNKNOWN);
            return ownership.fresh(new Value.LazyCollection(initialShape, () -> {
                while (true) {
                    Optional<Value> input = source.at(next[0]++);
                    if (input.isEmpty()) return null;
                    Value mapped = invoke(transform, new Value.Argument(input.get(), argument.span()), callSpan);
                    Value.LazyCollection.Produced produced = transformedEntry(mapped, sourceSet, callSpan);
                    if (produced != null) return produced;
                }
            }, facts, null, callSpan, true, true, oneToOne ? source::hasIndex : null));
        }

        @Override public int remainingArity() { return transform == null ? 2 : 1; }

        @Override public CallableSignature signature() {
            if (transform == null) return CallableSignature.unknown(List.of("transform", "values"));
            CallableSignature.Effects effects = transform.signature().effects();
            return new CallableSignature(
                    List.of(new CallableSignature.Parameter("values", List.of(), null, null)),
                    new CallableSignature.Result(List.of(), null, null),
                    new CallableSignature.Effects(effects.upperBound(), null, effects.inferred()), List.of());
        }

        @Override public String publicName() { return "map"; }
        @Override public List<Value> retainedValues() {
            return transform == null ? List.of() : List.of(transform);
        }
        @Override public String toString() { return "<fn map/" + remainingArity() + ">"; }
    }

    private enum SequenceOperation { FILTER, FOLD, ANY, ALL }

    private final class SequenceOperationCallable implements Value.Callable {
        private static final List<String> ALL_EFFECTS =
                List.of("Output", "StateRead", "StateWrite", "TestReport");
        private final SequenceOperation operation;
        private final List<Value.Argument> arguments;

        private SequenceOperationCallable(SequenceOperation operation) { this(operation, List.of()); }
        private SequenceOperationCallable(SequenceOperation operation, List<Value.Argument> arguments) {
            this.operation = operation;
            this.arguments = List.copyOf(arguments);
        }

        @Override public Value apply(Value.Argument argument, SourceSpan callSpan) {
            ArrayList<Value.Argument> next = new ArrayList<>(arguments);
            next.add(argument);
            if (next.size() == 1) collection(argument);
            if (next.size() < arity()) return new SequenceOperationCallable(operation, next);
            if (next.size() > arity()) throw runtime(Diagnostic.Codes.TOO_MANY_ARGUMENTS,
                    "Too many arguments for " + id(), callSpan);
            return executeSequenceOperation(next, callSpan);
        }

        private Value executeSequenceOperation(List<Value.Argument> supplied, SourceSpan callSpan) {
            IndexedCollection values = indexedFields(supplied.getFirst());
            int callbackIndex = operation == SequenceOperation.FOLD ? 2 : 1;
            int callbackArity = operation == SequenceOperation.FOLD ? 2 : 1;
            Value.Callable callback = collectionCallback(supplied.get(callbackIndex), callbackArity, id(),
                    operation == SequenceOperation.FOLD ? "combine" : "predicate");
            return switch (operation) {
                case FILTER -> {
                    CollectionRuntime.Facts sourceFacts = values.facts();
                    boolean pure = knownPure(callback);
                    CollectionRuntime.Guarantee sequential = sourceFacts.sequential()
                            == CollectionRuntime.Guarantee.TRUE && pure
                            ? CollectionRuntime.Guarantee.TRUE : CollectionRuntime.Guarantee.UNKNOWN;
                    CollectionRuntime.Guarantee finite = sourceFacts.finite() == CollectionRuntime.Guarantee.TRUE
                            ? CollectionRuntime.Guarantee.TRUE : CollectionRuntime.Guarantee.UNKNOWN;
                    CollectionRuntime.Facts facts = new CollectionRuntime.Facts(sequential, sourceFacts.ordered(),
                            sourceFacts.unique(), finite, sourceFacts.keyed(), sourceFacts.hasValues());
                    Value.LazyCollection.Shape shape = sourceFacts.keyed() == CollectionRuntime.Guarantee.FALSE
                            ? Value.LazyCollection.Shape.KEYLESS
                            : sourceFacts.hasValues() == CollectionRuntime.Guarantee.FALSE
                            ? Value.LazyCollection.Shape.SET : sourceFacts.keyed() == CollectionRuntime.Guarantee.TRUE
                            ? Value.LazyCollection.Shape.KEYED : Value.LazyCollection.Shape.INFER;
                    int[] next = {0};
                    Integer knownSize = values.knownSize() != null && values.knownSize() == 0 ? 0 : null;
                    yield ownership.fresh(new Value.LazyCollection(shape, () -> {
                        while (true) {
                            Optional<Value> candidate = values.at(next[0]++);
                            if (candidate.isEmpty()) return null;
                            Value result = invoke(callback, new Value.Argument(candidate.get(),
                                    supplied.getFirst().span()), callSpan);
                            if (predicateResult(result, id(), supplied.get(callbackIndex).span())) {
                                return retainedEntry(candidate.get(), shape, callSpan);
                            }
                        }
                    }, facts, knownSize, callSpan, true,
                            ValueKind.of(supplied.getFirst().value()) == ValueKind.DICTIONARY));
                }
                case FOLD -> {
                    Value accumulator = supplied.get(1).value();
                    for (int index = 0; ; index++) {
                        Optional<Value> next = values.at(index);
                        if (next.isEmpty()) break;
                        Value partial = invoke(callback,
                                new Value.Argument(accumulator, supplied.get(1).span()), callSpan);
                        if (!(underlying(partial) instanceof Value.Callable remaining)) {
                            throw runtime(Diagnostic.Codes.INTERNAL_ERROR,
                                    "fold combine lost its second parameter", callSpan);
                        }
                        accumulator = invoke(remaining,
                                new Value.Argument(next.get(), supplied.getFirst().span()), callSpan);
                    }
                    yield accumulator;
                }
                case ANY -> {
                    boolean matched = false;
                    for (int index = 0; ; index++) {
                        Optional<Value> next = values.at(index);
                        if (next.isEmpty()) break;
                        Value result = invoke(callback, new Value.Argument(next.get(), supplied.getFirst().span()), callSpan);
                        if (predicateResult(result, id(), supplied.get(callbackIndex).span())) { matched = true; break; }
                    }
                    yield new Value.Bool(matched);
                }
                case ALL -> {
                    boolean matched = true;
                    for (int index = 0; ; index++) {
                        Optional<Value> next = values.at(index);
                        if (next.isEmpty()) break;
                        Value result = invoke(callback, new Value.Argument(next.get(), supplied.getFirst().span()), callSpan);
                        if (!predicateResult(result, id(), supplied.get(callbackIndex).span())) { matched = false; break; }
                    }
                    yield new Value.Bool(matched);
                }
            };
        }

        private int arity() { return operation == SequenceOperation.FOLD ? 3 : 2; }
        private String id() { return operation.name().toLowerCase(Locale.ROOT); }
        @Override public int remainingArity() { return arity() - arguments.size(); }
        @Override public String publicName() { return id(); }
        @Override public CallableSignature signature() {
            List<String> parameters = operation == SequenceOperation.FOLD
                    ? List.of("values", "initial", "combine") : List.of("values", "predicate");
            CallableSignature signature = CallableSignature.builtin(parameters, ALL_EFFECTS);
            for (Value.Argument supplied : arguments) signature = signature.specializeFirst(supplied.value());
            return signature;
        }
        @Override public List<Value> retainedValues() {
            return arguments.stream().map(Value.Argument::value).toList();
        }
    }

    private final class ZipCallable implements Value.Callable {
        private final boolean keyed;
        private final List<Value.Argument> arguments;

        private ZipCallable(boolean keyed) { this(keyed, List.of()); }
        private ZipCallable(boolean keyed, List<Value.Argument> arguments) {
            this.keyed = keyed;
            this.arguments = List.copyOf(arguments);
        }

        @Override public Value apply(Value.Argument argument, SourceSpan callSpan) {
            indexedSequence(argument);
            ArrayList<Value.Argument> next = new ArrayList<>(arguments);
            next.add(argument);
            if (next.size() < 2) return new ZipCallable(keyed, next);
            if (next.size() > 2) throw runtime(Diagnostic.Codes.TOO_MANY_ARGUMENTS,
                    "Too many arguments for " + publicName(), callSpan);
            return zip(next.get(0), next.get(1), keyed, callSpan);
        }

        @Override public int remainingArity() { return 2 - arguments.size(); }
        @Override public String publicName() { return keyed ? "zipWithKeys" : "zip"; }
        @Override public CallableSignature signature() {
            CallableSignature.ContractTerm sequence = new CallableSignature.NamedRef(
                    BuiltinContract.SEQUENCE, BuiltinContract.SEQUENCE.publicName());
            CallableSignature.ContractTerm result = new CallableSignature.NamedRef(
                    keyed ? BuiltinContract.COLLECTION : BuiltinContract.SEQUENCE,
                    keyed ? BuiltinContract.COLLECTION.publicName() : BuiltinContract.SEQUENCE.publicName());
            CallableSignature signature = CallableSignature.builtin(keyed
                            ? List.of("keys", "values") : List.of("left", "right"),
                    List.of(List.of(sequence), List.of(sequence)), List.of(result), List.of());
            for (Value.Argument supplied : arguments) signature = signature.specializeFirst(supplied.value());
            return signature;
        }
        @Override public List<Value> retainedValues() {
            return arguments.stream().map(Value.Argument::value).toList();
        }
    }

    private static List<CallableSignature> operatorSignatures(String operator) {
        return switch (operator) {
            case "div" -> List.of(CallableSignature.operator(List.of("Integer", "Integer"), "Integer"));
            case "+" -> List.of(
                    CallableSignature.operator(List.of("Number", "Number"), "Number"),
                    CallableSignature.operator(List.of("String", "String"), "String"),
                    CallableSignature.operator(List.of("String", "Any"), "String"),
                    CallableSignature.operator(List.of("Any", "String"), "String"));
            case "==", "!=" -> List.of(CallableSignature.operator(List.of("Eq", "Eq"), "Boolean"));
            case ">", ">=", "<", "<=" -> List.of(CallableSignature.operator(
                    List.of("Number", "Number"), "Boolean"));
            default -> List.of(CallableSignature.operator(List.of("Number", "Number"), "Number"));
        };
    }

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

        TemplateContract expectedTemplate() {
            int position = 0;
            while (arguments.containsKey(position)) position++;
            TemplateContract selected = null;
            for (OverloadVariant variant : viable) {
                TemplateContract candidate = Interpreter.this.expectedTemplate(
                        variantClause(variant, position), contractEnvironment, resolution);
                if (candidate == null || selected != null && selected != candidate) return null;
                selected = candidate;
            }
            return selected;
        }

        ParameterizedContract expectedPacked() {
            int position = 0;
            while (arguments.containsKey(position)) position++;
            ParameterizedContract selected = null;
            for (OverloadVariant variant : viable) {
                ParameterizedContract candidate = Interpreter.this.expectedPacked(
                        variantClause(variant, position), contractEnvironment, resolution);
                if (candidate == null || selected != null
                        && selected.arguments().getFirst() != candidate.arguments().getFirst()) return null;
                selected = candidate;
            }
            return selected;
        }

        BuiltinContract expectedNumericFormat() {
            int position = 0;
            while (arguments.containsKey(position)) position++;
            BuiltinContract selected = null;
            for (OverloadVariant variant : viable) {
                BuiltinContract candidate = Interpreter.this.expectedNumericFormat(
                        variantClause(variant, position), contractEnvironment, resolution);
                if (candidate == null || selected != null && selected != candidate) return null;
                selected = candidate;
            }
            return selected;
        }

        boolean strictNumeric() {
            int position = 0;
            while (arguments.containsKey(position)) position++;
            int parameter = position;
            return !viable.isEmpty() && viable.stream().allMatch(variant ->
                    Interpreter.this.numericPolicy(variantClause(variant, parameter), contractEnvironment, resolution,
                            NumericPolicy.BROAD) == NumericPolicy.STRICT);
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
        @Override public List<Value> retainedValues() {
            return arguments.values().stream().map(Value.Argument::value).toList();
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
        for (Resolution.ContractBinding binding : valueRequirements(resolution.clause(clause))) {
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
        List<Object> l = valueRequirements(resolution.clause(left)).stream()
                .map(binding -> resolveRequirement(binding, env, resolution)).toList();
        List<Object> r = valueRequirements(resolution.clause(right)).stream()
                .map(binding -> resolveRequirement(binding, env, resolution)).toList();
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
        return validateContracts(value, valueSpan, clause, resolution, contractEnvironment, subject,
                constrainCallableEffects, null);
    }

    private Value validateContracts(Value value, SourceSpan valueSpan, ContractClause clause,
                                   Resolution resolution, Environment contractEnvironment, String subject,
                                   boolean constrainCallableEffects, List<ContractDescriptor> observedContracts) {
        LinkedHashSet<ContractDescriptor> acquired = new LinkedHashSet<>();
        Resolution.AnalyzedClause analyzed = resolution.clause(clause);
        for (Resolution.ContractBinding reference : valueRequirements(analyzed)) {
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
            if (observedContracts != null) observedContracts.add(contract);
            Value underlyingValue = underlying(value);
            if (underlyingValue == Value.Null.INSTANCE && contract.accepts(value)) continue;
            if (underlyingValue == Value.Missing.INSTANCE && contract.accepts(value)) continue;
            ContractDescriptor nominal = contract instanceof ModifiedContract modified
                    ? modified.base() : contract;
            if (underlying(value) instanceof Value.LazyCollection collection
                    && dictionaryRequirement(nominal)) collection.selectDictionary();
            if (nominal instanceof UserContract user && user.canAcquire(value, valueSpan)) {
                acquired.add(nominal);
                continue;
            }
            if (contract.test(value, valueSpan)) continue;
            List<Diagnostic.Related> related = analyzed == null ? List.of()
                    : List.of(new Diagnostic.Related("Required contract: " + contract.publicName(), analyzed.span()));
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

    private boolean dictionaryRequirement(ContractDescriptor descriptor) {
        if (descriptor == BuiltinContract.DICTIONARY) return true;
        return descriptor instanceof ParameterizedContract parameterized
                && parameterized.base() == BuiltinContract.DICTIONARY;
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
        List<String> upper = callable.signature().effects().upperBound() == null ? null
                : callable.signature().effects().upperBound().stream()
                .map(CallableSignature.EffectRef::name).toList();
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

    private static List<Resolution.ContractBinding> valueRequirements(Resolution.AnalyzedClause clause) {
        return clause == null ? List.of() : clause.valueRequirements();
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
            if (holeArgs == null && expr instanceof CollectionLiteral collection) {
                HoleAnalysis collectionHoles = analyzeCollectionHoles(collection);
                if (!collectionHoles.indexes().isEmpty()) {
                    int arity = holeArity(expr, collectionHoles.indexes());
                    CollectionConstructorDescriptor descriptor = collectionConstructor(
                            collection, env, resolution, collectionHoles.indexes());
                    if (descriptor != null) return new CollectionConstructorCallable(descriptor, List.of());
                    Expr captured = captureNonHoleParts(expr, env,
                            collectionHoles.containsHole(), resolution);
                    return new Value.HoleFunction(expr.toString(), arity,
                            supplied -> eval(captured, env, supplied, resolution),
                            holeSignature(captured, arity));
                }
            }
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

    private final class CollectionConstructorCallable implements Value.Callable {
        private final CollectionConstructorDescriptor descriptor;
        private final List<Value.Argument> arguments;

        private CollectionConstructorCallable(CollectionConstructorDescriptor descriptor,
                                              List<Value.Argument> arguments) {
            this.descriptor = descriptor;
            this.arguments = List.copyOf(arguments);
        }

        CollectionConstructorDescriptor descriptor() { return descriptor; }

        @Override public Value apply(Value.Argument argument, SourceSpan callSpan) {
            int parameter = arguments.size();
            argument = validateConstructorArgument(parameter, argument,
                    descriptor.parameterRequirements().get(parameter));
            ArrayList<Value.Argument> next = new ArrayList<>(arguments);
            next.add(argument);
            if (next.size() == descriptor.arity()) return materialize(descriptor.root(), next);
            return new CollectionConstructorCallable(descriptor, next);
        }

        @Override public List<Value> retainedValues() {
            return arguments.stream().map(Value.Argument::value).toList();
        }

        @Override public int remainingArity() { return descriptor.arity() - arguments.size(); }
        @Override public String publicName() { return "<collection-constructor>"; }
        @Override public CallableSignature signature() {
            CallableSignature signature = CallableSignature.collectionConstructor(
                    descriptor.parameterRequirements());
            for (Value.Argument argument : arguments) signature = signature.specializeFirst(argument.value());
            return signature;
        }
        @Override public String toString() {
            return "<collection-constructor/" + remainingArity() + ">";
        }
    }

    private Value.Argument validateConstructorArgument(int parameter, Value.Argument argument,
                                                       List<Object> requirements) {
        LinkedHashSet<ContractDescriptor> acquired = new LinkedHashSet<>();
        for (Object requirement : requirements) {
            boolean accepted;
            if (requirement instanceof ContractDescriptor contract) {
                ContractDescriptor nominal = contract instanceof ModifiedContract modified
                        ? modified.base() : contract;
                if (nominal instanceof UserContract user
                        && user.canAcquire(argument.value(), argument.span())) {
                    acquired.add(nominal);
                    accepted = true;
                } else accepted = contract.accepts(argument.value());
            } else {
                accepted = truth(invoke((Value.Callable) requirement, argument, argument.span()));
            }
            if (!accepted) throw new LangException(Diagnostic.Phase.RUNTIME,
                    Diagnostic.Codes.CONTRACT_VIOLATION,
                    "Contract violation for collection constructor parameter _" + (parameter + 1),
                    argument.span());
        }
        if (acquired.isEmpty()) return argument;
        Value value = argument.value();
        if (value instanceof Value.Attributed(Value raw, Set<ContractDescriptor> existing)) {
            acquired.addAll(existing);
            value = new Value.Attributed(raw, acquired);
        } else value = new Value.Attributed(value, acquired);
        return new Value.Argument(value, argument.span());
    }

    private Value materialize(CollectionConstructorDescriptor.Node node, List<Value.Argument> arguments) {
        if (node instanceof CollectionConstructorDescriptor.FixedNode fixed) return fixed.value();
        if (node instanceof CollectionConstructorDescriptor.HoleNode hole) {
            return arguments.get(hole.parameter()).value();
        }
        CollectionConstructorDescriptor.CollectionNode collection =
                (CollectionConstructorDescriptor.CollectionNode) node;
        if (collection.elements().isEmpty()) return Value.EmptyCollection.INSTANCE;
        if (!collection.named()) return ownership.fresh(new Value.Seq(collection.elements().stream()
                .map(element -> materialize(element.value(), arguments)).toList()));
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        collection.elements().forEach(element -> fields.putIfAbsent(element.name(),
                materialize(element.value(), arguments)));
        return ownership.fresh(new Value.Dictionary(fields));
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
        @Override public List<Value> retainedValues() {
            ArrayList<Value> retained = new ArrayList<>(overload.retainedValues());
            retained.addAll(arguments.stream().map(Value.Argument::value).toList());
            return List.copyOf(retained);
        }
        @Override public String toString() { return "<overload-partial " + display + "/" + remainingArity() + ">"; }
    }

    private Value evalInnerUnchecked(Expr expr, Environment env, Resolution resolution) {
        if (expr instanceof Literal(Value value1, SourceSpan ignored)) return value1;
        if (expr instanceof With with) return evaluateWith(with, env, resolution);
        if (expr instanceof OuterPath path) {
            Value value = readScoped(path, path.name(), env, resolution);
            Value raw = underlying(value);
            return raw instanceof Value.Callable callable && callable.remainingArity() == 0
                    ? invokeZero(callable, expr.span()) : value;
        }
        if (expr instanceof Name nameExpression) {
            Value value = readScoped(nameExpression, nameExpression.name(), env, resolution);
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
                case "-" -> {
                    Value raw = underlying(value);
                    if (!(raw instanceof Value.Num numeric)) {
                        throw runtime(Diagnostic.Codes.EXPECTED_NUMBER, "Expected number, got: " + raw,
                                operand.span());
                    }
                    java.math.BigInteger integer = NumericValues.integral(numeric);
                    yield numeric.exactInteger() == null && numeric.value() == 0.0
                            ? new Value.Num(-numeric.value())
                            : integer != null ? new Value.Num(integer.negate())
                            : finiteNumber(-numeric.value());
                }
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
                Value middle = argumentValue(middleExpression, callable, env, resolution);
                Value partial = invoke(callable,
                        new Value.Argument(middle, middleExpression.span()), expr.span());
                if (!(partial instanceof Value.Callable remaining)) {
                    throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.TOO_MANY_ARGUMENTS,
                            "Callable accepts fewer than two arguments", lastExpression.span());
                }
                Value last = argumentValue(lastExpression, remaining, env, resolution);
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
            Value conversion = groupedContractApplication((Apply) expr, env, resolution);
            if (conversion != null) return conversion;
            Value fn = underlying(evalInner(function, env, resolution));
            if (!(fn instanceof Value.Callable callable)) {
                throw runtime(Diagnostic.Codes.NOT_CALLABLE, "Value is not callable: " + fn);
            }
            Value argument = argumentValue(argument1, callable, env, resolution);
            return invoke(callable, new Value.Argument(argument, argument1.span()), expr.span());
        }
        if (expr instanceof Field(Expr target2, String field, boolean ignoredOptional, SourceSpan ignored)) {
            Value target = evalInner(target2, env, resolution);
            return invokeAccessor(expr, target, target2.span(), new Value.Str(field), expr.span(),
                    env, resolution);
        }
        if (expr instanceof DynamicField(Expr target1, Expr name1, boolean ignoredOptional, SourceSpan ignored)) {
            Value target = evalInner(target1, env, resolution);
            Value key = evalInner(name1, env, resolution);
            return invokeAccessor(expr, target, target1.span(), key, name1.span(), env, resolution);
        }
        if (expr instanceof Reflect(Expr target, SourceSpan ignored)) {
            // Reflection of a name observes the binding itself. In particular,
            // this is the escape hatch for referring to a zero-argument function
            // without triggering the normal implicit invocation on name reads.
            Value targetValue;
            if (target instanceof Field field) {
                Value owner = evalInner(field.target(), env, resolution);
                return reifyField(owner, field.field());
            }
            if (target instanceof Name || target instanceof OuterPath) {
                String name = target instanceof Name lexical ? lexical.name() : ((OuterPath) target).name();
                Resolution.Lookup lookup = resolution.scopedLookup(target);
                if (lookup != null) {
                    for (int depth : lookup.withDepths()) {
                        Environment layer = env.ancestor(depth);
                        if (layer.hasLocal(name) && layer.memberOwner() != null) {
                            return reifyField(layer.memberOwner(), name);
                        }
                    }
                }
                targetValue = readScoped(target, name, env, resolution);
            } else {
                targetValue = evalInner(target, env, resolution);
            }
            return reflect(targetValue);
        }
        if (expr instanceof Dereference(Expr target, SourceSpan ignored)) {
            Value reference = underlying(evalInner(target, env, resolution));
            if (reference instanceof Value.ProjectedDictionary dictionary) {
                Optional<Value> reflected = dictionary.reflectedTarget(reflectionContext);
                if (reflected.isPresent()) return reflected.get();
            }
            if (reference instanceof Value.Dictionary dictionary) {
                Optional<Value> reflected = dictionary.reflectedTarget(reflectionContext);
                if (reflected.isPresent()) return reflected.get();
            }
            throw runtime(Diagnostic.Codes.NOT_DEREFERENCEABLE,
                    "Value is not dereferenceable: " + reference, expr.span());
        }
        if (expr instanceof ContainerRead(Expr target, SourceSpan ignored)) {
            Value value = underlying(evalInner(target, env, resolution));
            if (!(value instanceof Value.Container container)) {
                throw runtime(Diagnostic.Codes.EXPECTED_CONTAINER,
                        "Expected Container, got: " + ValueSemantics.kind(value), target.span());
            }
            return container.current();
        }
        if (expr instanceof ContainerLiteral(ContractClause contracts, Expr initial, SourceSpan ignored)) {
            Value value = evalInner(initial, env, resolution);
            if (contracts == null) {
                List<ContractDescriptor> inferred = inferredContainerContracts(value);
                return new Value.Container(value, inferred, inferred.size() == 1, (candidate, span) -> {
                    for (ContractDescriptor contract : inferred) {
                        if (contract.test(candidate, span)) continue;
                        throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                                "Contract violation for container content: expected " + contract.publicName()
                                        + ", got " + ValueSemantics.kind(candidate), span);
                    }
                    return candidate;
                });
            }
            Value checked = validateContracts(value, initial.span(), contracts, resolution, env,
                    "container content");
            List<ContractDescriptor> descriptors = containerContractDescriptors(contracts, env, resolution);
            boolean single = valueRequirements(resolution.clause(contracts)).size() == 1
                    && descriptors.size() == 1;
            return new Value.Container(checked, descriptors, single, (candidate, span) ->
                    validateContracts(candidate, span, contracts, resolution, env, "container content"));
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
        if (expr instanceof ContractTerms) {
            throw runtime(Diagnostic.Codes.INTERNAL_ERROR,
                    "Unanalyzed arrow contract terms reached evaluation", expr.span());
        }
        if (expr instanceof Group(Expr expression, SourceSpan ignored)) {
            return evalInner(expression, env, resolution);
        }
        if (expr instanceof CollectionLiteral collection) {
            return evaluateCollection(collection, env, resolution);
        }
        if (expr instanceof ArrowContract arrow) {
            ArrowContract analyzed = resolution.arrow(arrow);
            ArrayList<List<ContractDescriptor>> parameterDescriptors = new ArrayList<>();
            for (List<Expr> parameter : analyzed.parameters()) {
                parameterDescriptors.add(parameter.stream()
                        .map(requirement -> arrowRequirement(requirement, env, resolution)).toList());
            }
            ContractDescriptor resultDescriptor = arrowRequirement(analyzed.result(), env, resolution);
            return new Value.ContractValue(new ArrowContractDescriptor(
                    List.copyOf(parameterDescriptors), resultDescriptor, arrow.effectTerms().stream()
                    .map(effect -> effectCatalog.resolve(effect.name()).orElseThrow()).toList()));
        }
        if (expr instanceof Lambda lambda) return lambdaFunction(lambda, env, resolution);
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
        return readScoped(expression, expression.name(), env, resolution);
    }

    private Value readScoped(Expr expression, String name, Environment env, Resolution resolution) {
        Resolution.Lookup lookup = resolution.scopedLookup(expression);
        if (lookup != null) {
            for (int depth : lookup.withDepths()) {
                Environment layer = env.ancestor(depth);
                if (layer.hasLocal(name)) return layer.readLocal(name);
            }
            if (lookup.fallback() != null) return env.getResolved(lookup.fallback());
            return env.ancestor(lookup.fallbackDepth()).get(name);
        }
        if (expression instanceof Name lexical) {
            Resolution.Binding binding = resolution.binding(lexical);
            return binding == null ? env.get(name) : env.getResolved(binding);
        }
        throw new IllegalStateException("Unresolved outer path");
    }

    private Value evaluateWith(With with, Environment env, Resolution resolution) {
        Value target = evalInner(with.target(), env, resolution);
        Value raw = underlying(target);
        CollectionRuntime.Provider provider = CollectionRuntime.provider(raw, reflectionContext).orElse(null);
        if (provider == null || provider.facts().keyed() == CollectionRuntime.Guarantee.FALSE) {
            throw runtime(Diagnostic.Codes.EXPECTED_WITH_TARGET,
                    "with target must expose public named members", with.target().span());
        }
        provider.facts().validate(with.target().span());
        Environment layer = new Environment(env);
        layer.memberOwner(raw);
        LinkedHashSet<String> needed = new LinkedHashSet<>(resolution.withNames(with));
        if (!needed.isEmpty()) {
            Value enumeration = raw instanceof Value.ProjectedDictionary projected
                    ? new Value.Seq(projected.fields(reflectionContext).keySet().stream()
                    .map(name -> (Value) new Value.Str(name)).toList())
                    : raw instanceof Value.LazyCollection ? null : provider.keys();
            int index = 0;
            while (!needed.isEmpty()) {
                int current = index;
                Optional<Value> next = raw instanceof Value.LazyCollection lazy
                        ? lazy.entryAt(current).map(entry -> entry.key() == null
                        ? new Value.Num(current) : entry.key())
                        : enumeratedValue(enumeration, index);
                if (next.isEmpty()) break;
                index++;
                if (!(underlying(next.get()) instanceof Value.Str(String name)) || !needed.remove(name)) continue;
                ReflectionContext observer = reflectionContext;
                layer.defineLazy(name, () -> raw instanceof Value.ProjectedDictionary projected
                        ? projected.find(name, observer).orElse(Value.Missing.INSTANCE)
                        : provider.getElement(new Value.Str(name)));
            }
        }
        return executeBlock(with.body(), new Environment(layer), resolution);
    }

    private boolean usesBuiltinPrint(PrintLine line, Environment env, Resolution resolution) {
        Resolution.Lookup lookup = resolution.scopedLookup(line.target());
        if (lookup != null) {
            for (int depth : lookup.withDepths()) {
                if (env.ancestor(depth).hasLocal("print")) return false;
            }
        }
        return resolution.usesBuiltinPrint(line);
    }

    private Optional<Value> enumeratedValue(Value enumeration, int index) {
        Value raw = underlying(enumeration);
        if (raw == Value.Missing.INSTANCE || raw == Value.EmptyCollection.INSTANCE) return Optional.empty();
        if (raw instanceof Value.Seq sequence) return sequence.find(index);
        if (raw instanceof Value.LazySeq sequence) return index < sequence.length()
                ? Optional.of(sequence.at(index)) : Optional.empty();
        if (raw instanceof Value.LazyCollection collection) return collection.entryAt(index)
                .map(Value.LazyCollection.Produced::value);
        if (raw instanceof Value.SettledCollection collection) return index < collection.entries().size()
                ? Optional.of(collection.entries().get(index).value()) : Optional.empty();
        throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                "Contract violation for with key provider: expected enumerable Collection");
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
                if (left instanceof Value.Str || right instanceof Value.Str) {
                    String leftText = left instanceof Value.Str(String text) ? text
                            : concatenateText(leftArgument, callSpan);
                    String rightText = right instanceof Value.Str(String text) ? text
                            : concatenateText(rightArgument, callSpan);
                    yield new Value.Str(leftText + rightText);
                }
                yield numericBinary(leftArgument, rightArgument, java.math.BigInteger::add,
                        Double::sum, callSpan);
            }
            case "-" -> numericBinary(leftArgument, rightArgument, java.math.BigInteger::subtract,
                    (a, b) -> a - b, callSpan);
            case "*" -> numericBinary(leftArgument, rightArgument, java.math.BigInteger::multiply,
                    (a, b) -> a * b, callSpan);
            case "/" -> {
                Value.Num dividend = numeric(leftArgument);
                Value.Num divisor = numeric(rightArgument);
                if (NumericValues.compare(divisor, new Value.Num(0)) == 0) {
                    throw runtime(Diagnostic.Codes.DIVISION_BY_ZERO, "Division by zero", rightArgument.span());
                }
                java.math.BigInteger a = NumericValues.integral(dividend);
                java.math.BigInteger b = NumericValues.integral(divisor);
                if (a != null && b != null) {
                    java.math.BigInteger[] division = a.divideAndRemainder(b);
                    if (division[1].signum() == 0) yield new Value.Num(division[0]);
                    double rounded = NumericValues.quotientToDouble(a, b);
                    Value.Num result = finiteNumber(rounded, callSpan);
                    if (new java.math.BigDecimal(rounded).multiply(new java.math.BigDecimal(b))
                            .compareTo(new java.math.BigDecimal(a)) != 0) {
                        reportImplicitPrecisionLoss(callSpan);
                    }
                    yield result;
                }
                checkFloatingOperands(dividend, divisor, callSpan);
                yield finiteNumber(dividend.value() / divisor.value(), callSpan);
            }
            case "div" -> {
                Value.Num dividend = numeric(leftArgument);
                Value.Num divisor = numeric(rightArgument);
                java.math.BigInteger a = NumericValues.integral(dividend);
                java.math.BigInteger b = NumericValues.integral(divisor);
                if (a == null || b == null) {
                    throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                            "div operands must satisfy Integer", callSpan);
                }
                if (b.signum() == 0) {
                    throw runtime(Diagnostic.Codes.DIVISION_BY_ZERO, "Division by zero", rightArgument.span());
                }
                yield new Value.Num(a.divide(b));
            }
            case "%" -> {
                Value.Num dividend = numeric(leftArgument);
                Value.Num divisor = numeric(rightArgument);
                if (NumericValues.compare(divisor, new Value.Num(0)) == 0) {
                    throw runtime(Diagnostic.Codes.DIVISION_BY_ZERO, "Division by zero", rightArgument.span());
                }
                java.math.BigInteger a = NumericValues.integral(dividend);
                java.math.BigInteger b = NumericValues.integral(divisor);
                if (a != null && b != null) yield new Value.Num(a.remainder(b));
                checkFloatingOperands(dividend, divisor, callSpan);
                yield finiteNumber(dividend.value() % divisor.value(), callSpan);
            }
            case ">" -> new Value.Bool(NumericValues.compare(numeric(leftArgument), numeric(rightArgument)) > 0);
            case ">=" -> new Value.Bool(NumericValues.compare(numeric(leftArgument), numeric(rightArgument)) >= 0);
            case "<" -> new Value.Bool(NumericValues.compare(numeric(leftArgument), numeric(rightArgument)) < 0);
            case "<=" -> new Value.Bool(NumericValues.compare(numeric(leftArgument), numeric(rightArgument)) <= 0);
            case "==" -> new Value.Bool(ValueSemantics.equal(left, right, reflectionContext));
            case "!=" -> new Value.Bool(!ValueSemantics.equal(left, right, reflectionContext));
            default -> throw runtime(Diagnostic.Codes.UNKNOWN_OPERATOR, "Unknown operator: " + op);
        };
    }

    private String concatenateText(Value.Argument argument, SourceSpan span) {
        Value.Callable renderer = (Value.Callable) globals.get("toString");
        Value rendered = underlying(invoke(renderer, argument, span));
        if (rendered instanceof Value.Str(String text)) return text;
        throw runtime(Diagnostic.Codes.EXPECTED_STRING,
                "toString specialization must return a String", span);
    }

    private Value.Num numeric(Value.Argument argument) {
        Value raw = underlying(argument.value());
        if (raw instanceof Value.Num number) return number;
        throw runtime(Diagnostic.Codes.EXPECTED_NUMBER,
                "Expected number, got: " + argument.value(), argument.span());
    }

    private Value.Num numericBinary(Value.Argument left, Value.Argument right,
                                    java.util.function.BiFunction<java.math.BigInteger, java.math.BigInteger,
                                            java.math.BigInteger> exact,
                                    java.util.function.DoubleBinaryOperator floating, SourceSpan span) {
        Value.Num a = numeric(left);
        Value.Num b = numeric(right);
        java.math.BigInteger ai = NumericValues.integral(a);
        java.math.BigInteger bi = NumericValues.integral(b);
        if (ai != null && bi != null) return new Value.Num(exact.apply(ai, bi));
        checkFloatingOperands(a, b, span);
        return finiteNumber(floating.applyAsDouble(a.value(), b.value()), span);
    }

    private void checkFloatingOperands(Value.Num left, Value.Num right, SourceSpan span) {
        if (!NumericValues.exactlyDouble(left) || !NumericValues.exactlyDouble(right)) {
            reportImplicitPrecisionLoss(span);
        }
    }

    private Value.Num finiteNumber(double value) {
        if (!Double.isFinite(value)) {
            throw runtime(Diagnostic.Codes.NON_FINITE_RESULT, "Numeric result is not finite");
        }
        return new Value.Num(value);
    }

    private Value.Num finiteNumber(double value, SourceSpan span) {
        if (!Double.isFinite(value)) {
            throw runtime(Diagnostic.Codes.NON_FINITE_RESULT, "Numeric result is not finite", span);
        }
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
            builtins.define(contract.publicName(), new Value.ContractValue(contract));
        }
        builtins.define("Int", new Value.ContractValue(BuiltinContract.INTEGER));
        builtins.define("Byte", new Value.ContractValue(BuiltinContract.UINT8));
        builtins.define("Float32", new Value.ContractValue(BuiltinContract.FLOAT));
        builtins.define("Float64", new Value.ContractValue(BuiltinContract.DOUBLE));
        builtins.define("contract", locatedFunction("contract", List.of("bases"), (args, span) -> {
            Value argument = underlying(args.getFirst().value());
            List<ContractDescriptor> bases = new ArrayList<>();
            List<Value.Callable> refinements = new ArrayList<>();
            if (argument instanceof Value.ContractValue contract) {
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
            } else if (argument != Value.Missing.INSTANCE) {
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
        builtins.define("template", locatedFunction("template", List.of("specimen"), (args, span) -> {
            Value specimen = underlying(args.getFirst().value());
            CollectionConstructorDescriptor descriptor;
            if (specimen instanceof CollectionConstructorCallable constructor) {
                if (!constructor.arguments.isEmpty()) {
                    throw runtime(Diagnostic.Codes.TEMPLATE_INVALID_CONSTRUCTOR,
                            "template requires an unbound collection constructor", args.getFirst().span());
                }
                descriptor = constructor.descriptor();
            } else {
                descriptor = concreteTemplateDescriptor(specimen, args.getFirst().span());
                if (descriptor == null) {
                    throw runtime(Diagnostic.Codes.TEMPLATE_INVALID_CONSTRUCTOR,
                            "template requires a Collection or reifiable collection constructor",
                            args.getFirst().span());
                }
            }
            validateTemplateFixedValues(descriptor.root());
            return new Value.ContractValue(new TemplateContract(descriptor,
                    (callable, argument) -> invoke(callable, argument, argument.span())));
        }));
        for (LanguageSyntax.BinaryOperator descriptor : LanguageSyntax.binaryOperators()) {
            String operator = descriptor.spelling();
            builtins.define(operator, new BuiltinOperatorCallable(operator));
        }

        builtins.define("print", new Value.FunctionValue("print", List.of("value"), (args, ignoredSpan) -> {
            if (!outputAllowed.getAsBoolean()) {
                throw runtime(Diagnostic.Codes.INTERNAL_ERROR, "Output is not available in the current environment");
            }
            output.println(ValueSemantics.render(args.getFirst().value(), null, reflectionContext));
            return args.getFirst().value();
        }, false, CallableSignature.builtin(List.of("value"), List.of("Output"))));

        builtins.define("type", new Value.FunctionValue("type", List.of("value"),
                args -> new Value.Str(ValueSemantics.kind(args.getFirst()))));

        builtins.define("toString", locatedFunction("toString", List.of("value"), (args, span) -> {
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
            }, reflectionContext));
        }));

        builtins.define("textSize", locatedFunction("textSize", List.of("text"), (args, ignored) -> {
            String value = text(args.getFirst());
            return new Value.Num(value.codePointCount(0, value.length()));
        }));
        builtins.define("textAt", locatedFunction("textAt", List.of("text", "index"), (args, ignored) -> {
            String value = text(args.get(0));
            OptionalInt index = index(args.get(1));
            int size = value.codePointCount(0, value.length());
            if (index.isEmpty() || index.getAsInt() >= size) return Value.Missing.INSTANCE;
            int offset = value.offsetByCodePoints(0, index.getAsInt());
            return new Value.Str(new String(Character.toChars(value.codePointAt(offset))));
        }));
        builtins.define("textSlice", locatedFunction("textSlice", List.of("text", "start", "end"), (args, ignored) -> {
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
        builtins.define("textNumber", locatedFunction("textNumber", List.of("text"), (args, ignored) -> {
            try {
                double number = Double.parseDouble(text(args.getFirst()));
                return Double.isFinite(number) ? new Value.Num(number) : Value.Missing.INSTANCE;
            } catch (NumberFormatException invalidNumber) {
                return Value.Missing.INSTANCE;
            }
        }));
        builtins.define("numberText", locatedFunction("numberText", List.of("number"), (args, ignored) ->
                new Value.Str(numeric(args.getFirst()).toString())));

        builtins.define("field", locatedFunction("field", List.of("key", "value"), (args, ignored) ->
                new Value.Field(args.getFirst().value(), args.get(1).value())));

        builtins.define("getElement", getElementFunction());

        CallableSignature.ContractTerm containerTerm = new CallableSignature.NamedRef(
                BuiltinContract.CONTAINER, BuiltinContract.CONTAINER.publicName());
        CallableSignature.ContractTerm anyTerm = new CallableSignature.NamedRef(
                BuiltinContract.ANY, BuiltinContract.ANY.publicName());
        CallableSignature putSignature = CallableSignature.builtin(List.of("container", "value"),
                List.of(List.of(containerTerm), List.of(anyTerm)), List.of(anyTerm), List.of("StateWrite"));
        builtins.define("put", new Value.FunctionValue("put", List.of("container", "value"), (args, ignored) -> {
            Value target = underlying(args.getFirst().value());
            if (!(target instanceof Value.Container container)) {
                throw runtime(Diagnostic.Codes.EXPECTED_CONTAINER,
                        "Expected Container, got: " + ValueSemantics.kind(target), args.getFirst().span());
            }
            return container.replace(args.get(1).value(), args.get(1).span());
        }, false, putSignature));

        builtins.define("keys", collectionFunction("keys", BuiltinContract.COLLECTION, true,
                (args, ignored) -> collectionEnumeration(
                        collection(args.getFirst()).keys(), "keys", args.getFirst().span())));
        builtins.define("values", collectionFunction("values", BuiltinContract.COLLECTION, true,
                (args, ignored) -> collectionEnumeration(
                        collection(args.getFirst()).valueEntries(), "values", args.getFirst().span())));
        builtins.define("fields", collectionFunction("fields", BuiltinContract.COLLECTION, false,
                (args, ignored) -> collectionEnumeration(
                        collection(args.getFirst()).fieldEntries(), "fields", args.getFirst().span())));
        builtins.define("size", collectionFunction("size", BuiltinContract.NATURAL, true,
                (args, ignored) -> collectionSize(collection(args.getFirst()), args.getFirst().span())));
        CallableSignature eagerSignature = new CallableSignature(
                List.of(new CallableSignature.Parameter("value", List.of(), null, null)),
                new CallableSignature.Result(List.of(), null, null),
                new CallableSignature.Effects(java.util.stream.Stream
                        .of("Output", "StateRead", "StateWrite", "TestReport")
                        .map(CallableSignature.EffectRef::new).toList(), null, null), List.of());
        builtins.define("eager", new Value.FunctionValue("eager", List.of("value"),
                (args, ignored) -> EagerRuntime.materialize(args.getFirst().value(), args.getFirst().span(),
                        reflectionContext),
                false, eagerSignature));
        builtins.define("isSequential", collectionGuarantee("isSequential",
                CollectionRuntime.Facts::sequential));
        builtins.define("isOrdered", collectionGuarantee("isOrdered", CollectionRuntime.Facts::ordered));
        builtins.define("isUnique", collectionGuarantee("isUnique", CollectionRuntime.Facts::unique));
        builtins.define("isFinite", collectionGuarantee("isFinite", CollectionRuntime.Facts::finite));
        builtins.define("isKeyed", collectionGuarantee("isKeyed", CollectionRuntime.Facts::keyed));
        builtins.define("hasValues", collectionGuarantee("hasValues", CollectionRuntime.Facts::hasValues));

        builtins.define("seqEmpty", function("seqEmpty", List.of(), args -> ownership.fresh(new Value.Seq(List.of()))));
        builtins.define("seqAdd", locatedFunction("seqAdd", List.of("sequence", "value"), (args, ignored) -> {
            Value current = underlying(args.getFirst().value());
            if (current instanceof Value.PackedCollection packed) {
                Value value = acquirePackedValue(packed.elementContract(), args.get(1).value(),
                        args.get(1).span());
                if (packed.layout().rejects(value)
                        || !packed.elementContract().test(value, args.get(1).span())) {
                    throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                            "Packed append rejects value for " + packed.elementContract().publicName(),
                            args.get(1).span());
                }
                ownership.share(value);
                return packed.append(value, args.get(1).span());
            }
            return ownership.append(sequence(args.getFirst()), args.get(1).value());
        }));
        builtins.define("seqGet", locatedFunction("seqGet", List.of("sequence", "index"), (args, ignored) -> {
            Value.Seq values = sequence(args.get(0));
            OptionalInt index = index(args.get(1));
            return index.isPresent() ? values.find(index.getAsInt()).orElse(Value.Missing.INSTANCE)
                    : Value.Missing.INSTANCE;
        }));
        builtins.define("seqSize", locatedFunction("seqSize", List.of("sequence"), (args, ignored) ->
                new Value.Num(sequence(args.getFirst()).size())));
        builtins.define("map", new MapCallable());
        builtins.define("filter", new SequenceOperationCallable(SequenceOperation.FILTER));
        builtins.define("fold", new SequenceOperationCallable(SequenceOperation.FOLD));
        builtins.define("any", new SequenceOperationCallable(SequenceOperation.ANY));
        builtins.define("all", new SequenceOperationCallable(SequenceOperation.ALL));
        builtins.define("zip", new ZipCallable(false));
        builtins.define("zipWithKeys", new ZipCallable(true));

        builtins.define("dictEmpty", function("dictEmpty", List.of(), args ->
                ownership.fresh(new Value.Dictionary(Map.of()))));
        builtins.define("dictPut", locatedFunction("dictPut", List.of("dictionary", "key", "value"), (args, ignored) ->
                ownership.put(dictionary(args.getFirst()), requiredDictionaryKey(args.get(1)), args.get(2).value())));
        builtins.define("dictGet", locatedFunction("dictGet", List.of("dictionary", "key"), (args, ignored) -> {
            String key = dictionaryKey(args.get(1));
            return key == null ? Value.Missing.INSTANCE
                    : dictionary(args.get(0)).find(key).orElse(Value.Missing.INSTANCE);
        }));
        builtins.define("dictHas", locatedFunction("dictHas", List.of("dictionary", "key"), (args, ignored) -> {
            String key = dictionaryKey(args.get(1));
            return new Value.Bool(key != null && dictionary(args.get(0)).containsKey(key));
        }));
        builtins.define("dictKeys", locatedFunction("dictKeys", List.of("dictionary"), (args, ignored) -> ownership.fresh(
                new Value.Seq(dictionary(args.getFirst()).entries().keySet().stream().map(Value.Str::new).toList()))));
    }

    private List<ContractDescriptor> inferredContainerContracts(Value value) {
        if (value instanceof Value.Attributed attributed && !attributed.contracts().isEmpty()) {
            return List.copyOf(attributed.contracts());
        }
        ContractDescriptor inferred = switch (ValueKind.of(value)) {
            case NUMBER -> BuiltinContract.NUMBER;
            case STRING -> BuiltinContract.STRING;
            case BOOLEAN -> BuiltinContract.BOOLEAN;
            case NULL -> BuiltinContract.NULL;
            case MISSING -> BuiltinContract.MISSING;
            case FUNCTION -> BuiltinContract.FUNCTION;
            case FIELD -> BuiltinContract.FIELD;
            case CONTAINER -> BuiltinContract.CONTAINER;
            case SEQUENCE -> BuiltinContract.SEQUENCE;
            case DICTIONARY -> BuiltinContract.DICTIONARY;
            case SET -> BuiltinContract.SET;
            case COLLECTION -> BuiltinContract.COLLECTION;
            case CONTRACT -> BuiltinContract.ANY;
            case REFLECTIVE -> BuiltinContract.ANY;
        };
        return List.of(inferred);
    }

    private List<ContractDescriptor> containerContractDescriptors(ContractClause clause, Environment env,
                                                                  Resolution resolution) {
        ArrayList<ContractDescriptor> descriptors = new ArrayList<>();
        for (Resolution.ContractBinding binding : valueRequirements(resolution.clause(clause))) {
            try {
                descriptors.add(resolveContractDescriptor(binding, env, resolution));
            } catch (LangException error) {
                if (!error.diagnostic().code().equals(Diagnostic.Codes.NOT_A_CONTRACT)) throw error;
            }
        }
        return List.copyOf(descriptors);
    }

    private void installTestBuiltins(TestReporter reporter) {
        builtins.define("assert", new Value.FunctionValue("assert", List.of("name", "condition"),
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
        builtins.define("assertEqual", new Value.FunctionValue("assertEqual", List.of("name", "actual", "expected"),
                (args, span) -> {
                    String name = text(args.get(0));
                    Value actual = args.get(1).value();
                    Value expected = args.get(2).value();
                    reporter.record(name, actual, expected,
                            ValueSemantics.equal(actual, expected, reflectionContext), span);
                    return Value.Missing.INSTANCE;
                }, false, CallableSignature.builtin(
                        List.of("name", "actual", "expected"), List.of("TestReport"))));
    }

    private Value.FunctionValue function(String name, List<String> parameters,
                                         java.util.function.Function<List<Value>, Value> implementation) {
        return new Value.FunctionValue(name, parameters, implementation);
    }

    private CollectionConstructorDescriptor concreteTemplateDescriptor(Value value, SourceSpan span) {
        CollectionConstructorDescriptor.CollectionNode root = concreteTemplateCollection(value, span);
        return root == null ? null : new CollectionConstructorDescriptor(root, List.of());
    }

    private CollectionConstructorDescriptor.CollectionNode concreteTemplateCollection(Value value,
                                                                                       SourceSpan span) {
        value = underlying(value);
        if (value instanceof Value.EmptyCollection) {
            return new CollectionConstructorDescriptor.CollectionNode(false, List.of(), span);
        }
        if (value instanceof Value.Seq sequence) {
            ArrayList<CollectionConstructorDescriptor.Element> elements = new ArrayList<>();
            for (Value member : sequence.values()) elements.add(new CollectionConstructorDescriptor.Element(
                    null, concreteTemplateNode(member, span), span));
            return new CollectionConstructorDescriptor.CollectionNode(false, elements, span);
        }
        Map<String, Value> fields;
        if (value instanceof Value.Dictionary dictionary) fields = dictionary.entries();
        else if (value instanceof Value.ProjectedDictionary dictionary) {
            fields = dictionary.fields(reflectionContext);
        } else return null;
        ArrayList<CollectionConstructorDescriptor.Element> elements = new ArrayList<>();
        fields.forEach((name, member) -> elements.add(new CollectionConstructorDescriptor.Element(
                name, concreteTemplateNode(member, span), span)));
        return new CollectionConstructorDescriptor.CollectionNode(true, elements, span);
    }

    private CollectionConstructorDescriptor.Node concreteTemplateNode(Value value, SourceSpan span) {
        CollectionConstructorDescriptor.CollectionNode nested = concreteTemplateCollection(value, span);
        return nested == null ? new CollectionConstructorDescriptor.FixedNode(value, span) : nested;
    }

    private void validateTemplateFixedValues(CollectionConstructorDescriptor.Node node) {
        if (node instanceof CollectionConstructorDescriptor.FixedNode(Value value, SourceSpan span)) {
            if (!ValueSemantics.equalityEligible(value)) {
                throw runtime(Diagnostic.Codes.TEMPLATE_NONCOMPARABLE_FIXED_VALUE,
                        "Template fixed value does not support equality: "
                                + ValueSemantics.kind(value), span);
            }
        } else if (node instanceof CollectionConstructorDescriptor.CollectionNode collection) {
            collection.elements().forEach(element -> validateTemplateFixedValues(element.value()));
        }
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
        if (raw instanceof Value.EmptyCollection) return ownership.fresh(new Value.Seq(List.of()));
        if (raw instanceof Value.Seq sequence) return sequence;
        if (raw instanceof Value.PackedCollection packed) return ownership.fresh(new Value.Seq(packed.values()));
        if (raw instanceof Value.LazySeq sequence) return ownership.fresh(new Value.Seq(sequence.materialize()));
        if (raw instanceof Value.LazyCollection collection
                && collection.materializedValue() instanceof Value.Seq sequence) return ownership.fresh(sequence);
        if (raw instanceof Value.SettledCollection collection
                && collection.kind() == ValueKind.SEQUENCE
                && collection.facts().sequential() == CollectionRuntime.Guarantee.TRUE
                && collection.keys() != Value.Missing.INSTANCE) {
            return ownership.fresh(new Value.Seq(collection.entries().stream()
                    .map(Value.SettledCollection.Entry::value).toList()));
        }
        throw runtime(Diagnostic.Codes.EXPECTED_SEQUENCE,
                "Expected sequence, got: " + argument.value(), argument.span());
    }

    private record IndexedCollection(java.util.function.IntFunction<Optional<Value>> accessor,
                                     Integer knownSize, CollectionRuntime.Facts facts,
                                     java.util.function.IntPredicate positionExists) {
        private IndexedCollection(java.util.function.IntFunction<Optional<Value>> accessor,
                                  Integer knownSize, CollectionRuntime.Facts facts) {
            this(accessor, knownSize, facts, null);
        }
        private Optional<Value> at(int index) {
            return index < 0 ? Optional.empty() : accessor.apply(index);
        }
        private boolean hasIndex(int index) {
            return index >= 0 && (knownSize != null ? index < knownSize
                    : positionExists != null ? positionExists.test(index) : at(index).isPresent());
        }
    }

    private IndexedCollection indexedSequence(Value.Argument argument) {
        Value raw = underlying(argument.value());
        if (raw instanceof Value.EmptyCollection || raw instanceof Value.Seq || raw instanceof Value.LazySeq) {
            return indexedFields(argument);
        }
        if (raw instanceof Value.PackedCollection packed) {
            return new IndexedCollection(index -> index >= 0 && index < packed.length()
                    ? Optional.of(packed.at(index)) : Optional.empty(),
                    packed.length(), packed.facts());
        }
        if (raw instanceof Value.LazyCollection collection
                && (collection.resolvedShape() == Value.LazyCollection.Shape.KEYLESS
                || collection.resolvedShape() == Value.LazyCollection.Shape.INFER
                || collection.facts().keyed() == CollectionRuntime.Guarantee.FALSE)) {
            return new IndexedCollection(index -> collection.entryAt(index).map(entry -> {
                if (collection.resolvedShape() != Value.LazyCollection.Shape.KEYLESS) {
                    throw runtime(Diagnostic.Codes.EXPECTED_SEQUENCE,
                            "Expected sequence, got a keyed Collection", argument.span());
                }
                return entry.value();
            }), null, collection.facts(), collection::hasIndex);
        }
        if (raw instanceof Value.SettledCollection collection
                && collection.kind() == ValueKind.SEQUENCE
                && collection.facts().sequential() == CollectionRuntime.Guarantee.TRUE) {
            return new IndexedCollection(index -> index < collection.entries().size()
                    ? Optional.of(collection.entries().get(index).value()) : Optional.empty(),
                    collection.entries().size(), collection.facts());
        }
        throw runtime(Diagnostic.Codes.EXPECTED_SEQUENCE,
                "Expected sequence, got: " + argument.value(), argument.span());
    }

    private Value zip(Value.Argument leftArgument, Value.Argument rightArgument,
                      boolean keyed, SourceSpan callSpan) {
        IndexedCollection left = indexedSequence(leftArgument);
        IndexedCollection right = indexedSequence(rightArgument);
        boolean lazy = underlying(leftArgument.value()) instanceof Value.LazySeq
                || underlying(leftArgument.value()) instanceof Value.LazyCollection
                || underlying(rightArgument.value()) instanceof Value.LazySeq
                || underlying(rightArgument.value()) instanceof Value.LazyCollection;
        if (!keyed && !lazy) {
            if (!Objects.equals(left.knownSize(), right.knownSize())) throw zipLengthMismatch(callSpan);
            ArrayList<Value> tuples = new ArrayList<>(left.knownSize());
            for (int index = 0; index < left.knownSize(); index++) {
                tuples.add(new Value.Seq(List.of(left.at(index).orElseThrow(), right.at(index).orElseThrow())));
            }
            return ownership.fresh(new Value.Seq(tuples));
        }

        CollectionRuntime.Guarantee finite = left.facts().finite() == CollectionRuntime.Guarantee.TRUE
                && right.facts().finite() == CollectionRuntime.Guarantee.TRUE
                ? CollectionRuntime.Guarantee.TRUE : CollectionRuntime.Guarantee.UNKNOWN;
        CollectionRuntime.Guarantee ordered = left.facts().ordered() == CollectionRuntime.Guarantee.TRUE
                && right.facts().ordered() == CollectionRuntime.Guarantee.TRUE
                ? CollectionRuntime.Guarantee.TRUE : CollectionRuntime.Guarantee.UNKNOWN;
        CollectionRuntime.Facts facts = keyed
                ? new CollectionRuntime.Facts(CollectionRuntime.Guarantee.FALSE, ordered,
                CollectionRuntime.Guarantee.UNKNOWN, finite, CollectionRuntime.Guarantee.TRUE,
                CollectionRuntime.Guarantee.TRUE)
                : new CollectionRuntime.Facts(CollectionRuntime.Guarantee.TRUE, ordered,
                CollectionRuntime.Guarantee.UNKNOWN, finite, CollectionRuntime.Guarantee.FALSE,
                CollectionRuntime.Guarantee.TRUE);
        Integer knownSize = !keyed && left.knownSize() != null && Objects.equals(left.knownSize(), right.knownSize())
                ? left.knownSize() : null;
        int[] next = {0};
        ArrayList<Value> retainedKeys = new ArrayList<>();
        Value.LazyCollection result = new Value.LazyCollection(keyed
                ? Value.LazyCollection.Shape.KEYED : Value.LazyCollection.Shape.KEYLESS, () -> {
            while (true) {
                int index = next[0]++;
                Optional<Value> leftValue = left.at(index);
                if (leftValue.isEmpty()) {
                    if (right.hasIndex(index)) throw zipLengthMismatch(callSpan);
                    return null;
                }
                if (!keyed) {
                    Optional<Value> rightValue = right.at(index);
                    if (rightValue.isEmpty()) throw zipLengthMismatch(callSpan);
                    return new Value.LazyCollection.Produced(null,
                            new Value.Seq(List.of(leftValue.get(), rightValue.get())),
                            Value.LazyCollection.Shape.KEYLESS);
                }
                Value key = leftValue.get();
                validateZipKey(key, leftArgument.span());
                boolean duplicate = retainedKeys.stream().anyMatch(existing -> ValueSemantics.equal(existing, key));
                if (duplicate) {
                    if (!right.hasIndex(index)) throw zipLengthMismatch(callSpan);
                    continue;
                }
                Optional<Value> rightValue = right.at(index);
                if (rightValue.isEmpty()) throw zipLengthMismatch(callSpan);
                retainedKeys.add(key);
                return new Value.LazyCollection.Produced(key, rightValue.get(),
                        Value.LazyCollection.Shape.KEYED);
            }
        }, facts, knownSize, callSpan, true, false);
        if (!lazy) result.materializeEntries();
        return ownership.fresh(result);
    }

    private void validateZipKey(Value key, SourceSpan span) {
        Value raw = underlying(key);
        if (raw == Value.Missing.INSTANCE) {
            throw runtime(Diagnostic.Codes.INVALID_COLLECTION_KEY,
                    "zipWithKeys key cannot be missing", span);
        }
        if (!ValueSemantics.equalityEligible(raw)) {
            throw runtime(Diagnostic.Codes.INVALID_COLLECTION_KEY,
                    "zipWithKeys key must support equality", span);
        }
    }

    private LangException zipLengthMismatch(SourceSpan span) {
        return runtime(Diagnostic.Codes.ZIP_LENGTH_MISMATCH,
                "zip inputs must have equal lengths", span);
    }

    private IndexedCollection indexedFields(Value.Argument argument) {
        CollectionRuntime.Provider provider = collection(argument);
        Value raw = underlying(argument.value());
        if (raw instanceof Value.EmptyCollection) {
            return new IndexedCollection(ignored -> Optional.empty(), 0, provider.facts());
        }
        if (raw instanceof Value.Seq sequence) {
            return new IndexedCollection(sequence::find, sequence.size(), provider.facts());
        }
        if (raw instanceof Value.PackedCollection packed) {
            return new IndexedCollection(index -> index >= 0 && index < packed.length()
                    ? Optional.of(packed.at(index)) : Optional.empty(),
                    packed.length(), packed.facts());
        }
        if (raw instanceof Value.LazySeq sequence) {
            return new IndexedCollection(index -> index < sequence.length()
                    ? Optional.of(sequence.at(index)) : Optional.empty(), sequence.length(), provider.facts());
        }
        if (raw instanceof Value.Field field) {
            List<Value> values = List.of(field.key(), field.value());
            return indexed(values, provider.facts());
        }
        if (raw instanceof Value.KeyedCollection keyed) {
            List<Value> fields = keyed.entries().stream().map(entry -> (Value) new Value.Field(entry.key(),
                    keyed.shape() == Value.KeyedCollection.Shape.SET
                            ? Value.Missing.INSTANCE : entry.value())).toList();
            return indexed(fields, provider.facts());
        }
        if (raw instanceof Value.Dictionary dictionary) {
            List<Value> fields = dictionary.entries().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(CollectionRuntime.FIELD_ORDER))
                    .map(entry -> (Value) dictionary.fieldBinding(entry.getKey())).toList();
            return indexed(fields, provider.facts());
        }
        if (raw instanceof Value.ProjectedDictionary dictionary) {
            List<Value> fields = dictionary.fields(reflectionContext).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(CollectionRuntime.FIELD_ORDER))
                    .map(entry -> (Value) new Value.Field(new Value.Str(entry.getKey()), entry.getValue())).toList();
            return indexed(fields, provider.facts());
        }
        if (raw instanceof Value.LazyCollection collection) {
            return new IndexedCollection(index -> collection.entryAt(index).map(entry ->
                    collection.resolvedShape() == Value.LazyCollection.Shape.KEYLESS
                            ? entry.value() : new Value.Field(entry.key(),
                            collection.resolvedShape() == Value.LazyCollection.Shape.SET
                                    ? Value.Missing.INSTANCE : entry.value())), null, provider.facts(),
                    collection::hasIndex);
        }
        Value enumeration = provider.fieldEntries();
        if (underlying(enumeration) == raw) {
            throw runtime(Diagnostic.Codes.INTERNAL_ERROR,
                    "Collection provider cannot enumerate its fields", argument.span());
        }
        return indexedFields(new Value.Argument(enumeration, argument.span()));
    }

    private IndexedCollection indexed(List<Value> values, CollectionRuntime.Facts facts) {
        return new IndexedCollection(index -> index < values.size()
                ? Optional.of(values.get(index)) : Optional.empty(), values.size(), facts);
    }

    private boolean knownPure(Value.Callable callback) {
        List<CallableSignature.EffectRef> effects = callback.signature().effects().upperBound();
        return effects != null && effects.isEmpty();
    }

    private boolean definitelyNonField(Value.Callable callback) {
        List<CallableSignature.ContractTerm> results = callback.signature().result().guarantees();
        for (CallableSignature.ContractTerm result : results) {
            while (result instanceof CallableSignature.ModifiedRef modified) result = modified.base();
            if (result instanceof CallableSignature.AppliedRef applied) result = applied.constructor();
            if (result instanceof CallableSignature.NamedRef named
                    && named.identity() instanceof BuiltinContract contract
                    && contract != BuiltinContract.ANY && contract != BuiltinContract.EQ
                    && contract != BuiltinContract.FIELD && contract != BuiltinContract.COLLECTION) return true;
        }
        return false;
    }

    private Value.LazyCollection.Produced transformedEntry(Value mapped, boolean sourceSet, SourceSpan span) {
        Value raw = underlying(mapped);
        if (raw instanceof Value.Field field) {
            Value key = underlying(field.key());
            if (key == Value.Missing.INSTANCE) {
                return underlying(field.value()) == Value.Missing.INSTANCE ? null
                        : new Value.LazyCollection.Produced(null, field.value(),
                        Value.LazyCollection.Shape.KEYLESS);
            }
            if (!ValueSemantics.equalityEligible(key)) {
                throw runtime(Diagnostic.Codes.INVALID_COLLECTION_KEY,
                        "Transformed Collection key must support equality", span);
            }
            Value.LazyCollection.Shape shape = underlying(field.value()) == Value.Missing.INSTANCE
                    ? Value.LazyCollection.Shape.SET : Value.LazyCollection.Shape.KEYED;
            return new Value.LazyCollection.Produced(field.key(), field.value(), shape);
        }
        if (sourceSet) {
            if (raw == Value.Missing.INSTANCE || !ValueSemantics.equalityEligible(raw)) {
                throw runtime(Diagnostic.Codes.INVALID_COLLECTION_KEY,
                        "A transformed Set member must support equality and cannot be missing", span);
            }
            return new Value.LazyCollection.Produced(mapped, Value.Missing.INSTANCE,
                    Value.LazyCollection.Shape.SET);
        }
        return new Value.LazyCollection.Produced(null, mapped, Value.LazyCollection.Shape.KEYLESS);
    }

    private Value.LazyCollection.Produced retainedEntry(Value candidate, Value.LazyCollection.Shape shape,
                                                        SourceSpan span) {
        if (shape == Value.LazyCollection.Shape.KEYLESS || shape == Value.LazyCollection.Shape.INFER) {
            return shape == Value.LazyCollection.Shape.INFER
                    ? transformedEntry(candidate, false, span)
                    : new Value.LazyCollection.Produced(null, candidate, shape);
        }
        Value raw = underlying(candidate);
        if (!(raw instanceof Value.Field field)) {
            throw runtime(Diagnostic.Codes.INTERNAL_ERROR,
                    "A keyed Collection enumerated a non-Field value", span);
        }
        return new Value.LazyCollection.Produced(field.key(), field.value(), shape);
    }

    private CollectionRuntime.Provider collection(Value.Argument argument) {
        CollectionRuntime.Provider provider = CollectionRuntime.provider(argument.value(), reflectionContext).orElseThrow(() ->
                runtime(Diagnostic.Codes.EXPECTED_COLLECTION,
                        "Expected Collection, got: " + argument.value(), argument.span()));
        provider.facts().validate(argument.span());
        return provider;
    }

    private Value collectionEnumeration(Value result, String operation, SourceSpan span) {
        Value raw = underlying(result);
        if (raw == Value.Missing.INSTANCE || CollectionRuntime.isCollection(raw)) return result;
        throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                "Contract violation for " + operation + " provider: expected Collection or missing", span);
    }

    private Value collectionSize(CollectionRuntime.Provider provider, SourceSpan span) {
        Value result = provider.size();
        Value raw = underlying(result);
        if (raw == Value.Missing.INSTANCE || BuiltinContract.NATURAL.accepts(raw)) return result;
        throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                "Contract violation for size provider: expected Natural or missing", span);
    }

    private Value.FunctionValue collectionGuarantee(
            String name, java.util.function.Function<CollectionRuntime.Facts, CollectionRuntime.Guarantee> query) {
        return collectionFunction(name, BuiltinContract.BOOLEAN, true, (arguments, ignored) -> {
            CollectionRuntime.Provider provider = collection(arguments.getFirst());
            return query.apply(provider.facts()).value();
        });
    }

    private Value.FunctionValue collectionFunction(
            String name, BuiltinContract result, boolean optional,
            java.util.function.BiFunction<List<Value.Argument>, SourceSpan, Value> implementation) {
        CallableSignature.ContractTerm collection = new CallableSignature.NamedRef(
                BuiltinContract.COLLECTION, BuiltinContract.COLLECTION.publicName());
        CallableSignature.ContractTerm resultTerm = new CallableSignature.NamedRef(result, result.publicName());
        if (optional) resultTerm = new CallableSignature.ModifiedRef(resultTerm, false, true);
        CallableSignature signature = CallableSignature.builtin(List.of("collection"),
                List.of(List.of(collection)), List.of(resultTerm), List.of());
        return new Value.FunctionValue(name, List.of("collection"), implementation, false, signature);
    }

    private Value.FunctionValue getElementFunction() {
        CallableSignature.ContractTerm collection = new CallableSignature.NamedRef(
                BuiltinContract.COLLECTION, BuiltinContract.COLLECTION.publicName());
        CallableSignature.ContractTerm comparable = new CallableSignature.NamedRef(
                BuiltinContract.EQ, BuiltinContract.EQ.publicName());
        CallableSignature.ContractTerm result = new CallableSignature.ModifiedRef(
                new CallableSignature.NamedRef(BuiltinContract.ANY, BuiltinContract.ANY.publicName()),
                false, true);
        CallableSignature signature = CallableSignature.builtin(List.of("collection", "key"),
                List.of(List.of(collection), List.of(comparable)), List.of(result), List.of());
        return new Value.FunctionValue("getElement", List.of("collection", "key"), (arguments, ignored) -> {
            Value.Argument collectionArgument = arguments.getFirst();
            Value.Argument keyArgument = arguments.get(1);
            Value rawCollection = underlying(collectionArgument.value());
            Value key = underlying(keyArgument.value());
            CollectionRuntime.Provider provider = collection(collectionArgument);
            validateAccessKey(rawCollection, key, keyArgument.span());
            if (rawCollection instanceof Value.ProjectedDictionary projected
                    && key instanceof Value.Str(String name)) {
                return projected.find(name, reflectionContext).orElse(Value.Missing.INSTANCE);
            }
            return provider.getElement(keyArgument.value());
        }, false, signature);
    }

    private void validateAccessKey(Value collection, Value key, SourceSpan span) {
        if (key == Value.Missing.INSTANCE) {
            throw runtime(Diagnostic.Codes.INVALID_COLLECTION_KEY,
                    "Collection access key cannot be missing", span);
        }
        if (!ValueSemantics.equalityEligible(key)) {
            throw runtime(Diagnostic.Codes.INVALID_COLLECTION_KEY,
                    "Collection access key must support equality, got: " + ValueSemantics.kind(key), span);
        }
        Value.SettledCollection settled = collection instanceof Value.SettledCollection snapshot ? snapshot : null;
        if (collection instanceof Value.Seq || collection instanceof Value.PackedCollection
                || collection instanceof Value.LazySeq
                || collection instanceof Value.Field
                || collection instanceof Value.LazyCollection lazy
                && lazy.resolvedShape() == Value.LazyCollection.Shape.KEYLESS
                || settled != null && settled.kind() == ValueKind.SEQUENCE) {
            if (!(key instanceof Value.Num numeric) || NumericValues.integral(numeric) == null) {
                throw runtime(Diagnostic.Codes.INVALID_COLLECTION_KEY,
                        "Sequential Collection key must be an integer, got: " + key, span);
            }
            return;
        }
        if (collection instanceof Value.Dictionary || collection instanceof Value.ProjectedDictionary
                || settled != null && settled.kind() == ValueKind.DICTIONARY) {
            if (!(key instanceof Value.Str)) {
                throw runtime(Diagnostic.Codes.INVALID_COLLECTION_KEY,
                        "Dictionary access key must be a String, got: " + ValueSemantics.kind(key), span);
            }
            return;
        }
        if (collection instanceof Value.KeyedCollection keyed
                && keyed.shape() != Value.KeyedCollection.Shape.GENERAL
                && !keyed.entries().isEmpty()
                && ValueKind.of(key) != ValueKind.of(keyed.entries().getFirst().key())) {
            throw runtime(Diagnostic.Codes.INVALID_COLLECTION_KEY,
                    "Key does not satisfy the Collection access contract", span);
        }
        if (settled != null && settled.kind() == ValueKind.SET && !settled.entries().isEmpty()
                && ValueKind.of(key) != ValueKind.of(settled.entries().getFirst().key())) {
            throw runtime(Diagnostic.Codes.INVALID_COLLECTION_KEY,
                    "Key does not satisfy the Collection access contract", span);
        }
    }

    private Value.Callable unaryMapTransform(Value.Argument argument) {
        Value raw = underlying(argument.value());
        if (raw instanceof Value.Callable callable && callable.remainingArity() == 1) return callable;
        throw runtime(Diagnostic.Codes.INVALID_MAP_TRANSFORM,
                "map transform must be a callable requiring exactly one argument", argument.span());
    }

    private enum CollectionShape { INFER, KEYLESS, DICTIONARY, SET }

    private CollectionShape expectedCollectionShape(ContractClause clause, Environment env,
                                                    Resolution resolution) {
        Resolution.AnalyzedClause analyzed = resolution.clause(clause);
        for (Resolution.ContractBinding reference : valueRequirements(analyzed)) {
            ContractDescriptor descriptor;
            try {
                descriptor = resolveContractDescriptor(reference, env, resolution);
            } catch (LangException ignored) {
                continue;
            }
            if (descriptor instanceof ModifiedContract modified) descriptor = modified.base();
            if (descriptor instanceof ParameterizedContract parameterized) descriptor = parameterized.base();
            if (descriptor == BuiltinContract.SET) return CollectionShape.SET;
            if (descriptor == BuiltinContract.DICTIONARY) return CollectionShape.DICTIONARY;
            if (descriptor == BuiltinContract.SEQUENCE) return CollectionShape.KEYLESS;
        }
        return CollectionShape.INFER;
    }

    private TemplateContract expectedTemplate(ContractClause clause, Environment env, Resolution resolution) {
        TemplateContract selected = null;
        for (Resolution.ContractBinding reference : valueRequirements(resolution.clause(clause))) {
            if (reference.inline() != null || !reference.arguments().isEmpty()) continue;
            Value resolved;
            try {
                resolved = underlying(reference.binding() == null ? globals.get(reference.name())
                        : env.getResolved(reference.binding()));
            } catch (LangException unavailable) {
                return null;
            }
            if (!(resolved instanceof Value.ContractValue contract)) continue;
            ContractDescriptor descriptor = contract.descriptor();
            if (descriptor instanceof ModifiedContract modified) descriptor = modified.base();
            if (!(descriptor instanceof TemplateContract template)) continue;
            if (selected != null && selected != template) return null;
            selected = template;
        }
        return selected;
    }

    private BuiltinContract expectedNumericFormat(ContractClause clause, Environment env,
                                                  Resolution resolution) {
        BuiltinContract selected = null;
        for (Resolution.ContractBinding reference : valueRequirements(resolution.clause(clause))) {
            if (reference.inline() != null || !reference.arguments().isEmpty()) continue;
            if (reference.name().matches("_[1-9][0-9]*")) continue;
            Value resolved = underlying(reference.binding() == null ? globals.get(reference.name())
                    : env.getResolved(reference.binding()));
            if (!(resolved instanceof Value.ContractValue contract)) continue;
            ContractDescriptor descriptor = contract.descriptor();
            if (descriptor != BuiltinContract.FLOAT && descriptor != BuiltinContract.DOUBLE) continue;
            if (selected != null && selected != descriptor) return null;
            selected = (BuiltinContract) descriptor;
        }
        return selected;
    }

    private ParameterizedContract expectedPacked(ContractClause clause, Environment env,
                                                 Resolution resolution) {
        ParameterizedContract selected = null;
        for (Resolution.ContractBinding reference : valueRequirements(resolution.clause(clause))) {
            if (reference.inline() != null) continue;
            Value value;
            try {
                value = underlying(reference.binding() == null ? globals.get(reference.name())
                        : env.getResolved(reference.binding()));
            } catch (LangException unavailable) {
                continue;
            }
            if (!(value instanceof Value.ContractValue contract)) continue;
            ContractDescriptor resolved = contract.descriptor();
            if (!reference.arguments().isEmpty()) {
                if (resolved.parameterArity() != reference.arguments().size()) continue;
                resolved = resolved.parameterize(reference.arguments().stream()
                        .map(argument -> resolveContractDescriptor(argument, env, resolution)).toList());
            }
            if (!(resolved instanceof ParameterizedContract candidate)
                    || candidate.base() != BuiltinContract.PACKED || candidate.parameterArity() != 0) continue;
            if (selected != null && selected.arguments().getFirst() != candidate.arguments().getFirst()) return null;
            selected = candidate;
        }
        return selected;
    }

    private Value contextualPackedLiteral(ParameterizedContract contract, Value source, SourceSpan span) {
        ContractDescriptor element = contract.arguments().getFirst();
        validatePackedElementLayout(element, span);
        Value raw = underlying(source);
        List<Value> values = raw == Value.EmptyCollection.INSTANCE ? List.of()
                : raw instanceof Value.Seq sequence ? sequence.values() : null;
        if (values == null) {
            throw runtime(Diagnostic.Codes.EXPECTED_SEQUENCE,
                    "Expected sequence, got: " + ValueSemantics.kind(raw), span);
        }
        List<Value> acquired = new ArrayList<>(values.size());
        for (Value original : values) {
            Value value = acquirePackedValue(element, original, span);
            if (!element.test(value, span)) {
                throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                        "Contract violation for packed literal element: expected " + element.publicName(), span);
            }
            acquired.add(value);
        }
        return new Value.PackedCollection(element, acquired, span, ownership.optimizationsEnabled());
    }

    private Value acquirePackedValue(ContractDescriptor contract, Value value, SourceSpan span) {
        if (contract instanceof UserContract user) {
            if (user.accepts(value)) return value;
            Value acquired = value;
            for (ContractDescriptor base : user.bases()) {
                acquired = acquirePackedValue(base, acquired, span);
            }
            if (!user.canAcquire(acquired, span)) return acquired;
            java.util.LinkedHashSet<ContractDescriptor> memberships = new java.util.LinkedHashSet<>();
            while (acquired instanceof Value.Attributed(Value nested, Set<ContractDescriptor> contracts)) {
                memberships.addAll(contracts);
                acquired = nested;
            }
            memberships.add(user);
            return new Value.Attributed(acquired, memberships);
        }
        if (contract instanceof TemplateContract template) {
            return acquirePackedNode(template.descriptor().root(), value, span);
        }
        return value;
    }

    private Value acquirePackedNode(CollectionConstructorDescriptor.Node node, Value value, SourceSpan span) {
        if (node instanceof CollectionConstructorDescriptor.HoleNode hole) {
            Value acquired = value;
            for (Object requirement : hole.requirements()) {
                if (requirement instanceof ContractDescriptor contract) {
                    acquired = acquirePackedValue(contract, acquired, span);
                }
            }
            return acquired;
        }
        if (!(node instanceof CollectionConstructorDescriptor.CollectionNode collection)) return value;
        Value raw = underlying(value);
        if (collection.named() && raw instanceof Value.Dictionary dictionary) {
            java.util.LinkedHashMap<String, Value> fields = new java.util.LinkedHashMap<>(dictionary.entries());
            for (CollectionConstructorDescriptor.Element part : collection.elements()) {
                Value member = fields.get(part.name());
                if (member != null) fields.put(part.name(), acquirePackedNode(part.value(), member, span));
            }
            return retainPackedAttributes(value, new Value.Dictionary(fields));
        }
        if (!collection.named() && raw instanceof Value.Seq sequence) {
            List<Value> elements = new ArrayList<>(sequence.values());
            for (int index = 0; index < Math.min(elements.size(), collection.elements().size()); index++) {
                elements.set(index, acquirePackedNode(collection.elements().get(index).value(),
                        elements.get(index), span));
            }
            return retainPackedAttributes(value, new Value.Seq(elements));
        }
        return value;
    }

    private Value retainPackedAttributes(Value original, Value changed) {
        java.util.LinkedHashSet<ContractDescriptor> memberships = new java.util.LinkedHashSet<>();
        while (original instanceof Value.Attributed(Value nested, Set<ContractDescriptor> contracts)) {
            memberships.addAll(contracts);
            original = nested;
        }
        return memberships.isEmpty() ? changed : new Value.Attributed(changed, memberships);
    }

    private NumericPolicy numericPolicy(ContractClause clause, Environment env, Resolution resolution,
                                        NumericPolicy fallback) {
        boolean broad = false;
        for (Resolution.ContractBinding reference : valueRequirements(resolution.clause(clause))) {
            if (reference.inline() != null || !reference.arguments().isEmpty()
                    || reference.name().matches("_[1-9][0-9]*")) continue;
            Value resolved;
            try {
                resolved = underlying(reference.binding() == null ? globals.get(reference.name())
                        : env.getResolved(reference.binding()));
            } catch (LangException unavailable) {
                continue;
            }
            BuiltinContract contract = resolved instanceof Value.ContractValue value
                    && value.descriptor() instanceof BuiltinContract builtin ? builtin : null;
            if (contract == BuiltinContract.NUMBER || contract == BuiltinContract.REAL) broad = true;
            else if (contract == BuiltinContract.INTEGER || contract == BuiltinContract.NATURAL
                    || contract == BuiltinContract.INT8 || contract == BuiltinContract.UINT8
                    || contract == BuiltinContract.INT16 || contract == BuiltinContract.UINT16
                    || contract == BuiltinContract.INT32 || contract == BuiltinContract.UINT32
                    || contract == BuiltinContract.INT64 || contract == BuiltinContract.UINT64
                    || contract == BuiltinContract.FLOAT || contract == BuiltinContract.DOUBLE) {
                return NumericPolicy.STRICT;
            }
        }
        return broad ? NumericPolicy.BROAD : fallback;
    }

    /** Reports source-provable literal division once, before its dynamic evaluation. */
    private void analyzeStaticPrecision(List<Stmt> program) {
        staticallyAnalyzedProgram = null;
        staticallyReportedPrecisionLosses.clear();
        Map<String, BuiltinContract> aliases = new HashMap<>();
        for (Stmt statement : program) {
            if (statement instanceof Assign assign && assign.value() instanceof Name name) {
                BuiltinContract contract = aliases.get(name.name());
                if (contract == null) contract = BuiltinContract.named(name.name()).orElse(null);
                if (contract != null) aliases.put(assign.name(), contract);
            }
        }
        analyzeStaticPrecisionStatements(program, NumericPolicy.BROAD, aliases);
        staticallyAnalyzedProgram = program;
    }

    private void analyzeStaticPrecisionStatements(List<Stmt> statements, NumericPolicy inherited,
                                                  Map<String, BuiltinContract> aliases) {
        for (Stmt statement : statements) {
            if (statement instanceof Assign assign) {
                analyzeStaticPrecisionExpression(assign.value(), staticNumericPolicy(assign.contracts(), inherited,
                        aliases), assign.contracts());
            } else if (statement instanceof FunctionDef function) {
                analyzeStaticPrecisionStatements(function.body(),
                        staticNumericPolicy(function.resultContracts(), NumericPolicy.BROAD, aliases), aliases);
            } else if (statement instanceof ExprStmt expression) {
                analyzeStaticPrecisionExpression(expression.expression(), inherited, null);
            } else if (statement instanceof PrintLine line) {
                analyzeStaticPrecisionExpression(line.builtinArgument(), inherited, null);
            }
        }
    }

    private NumericPolicy staticNumericPolicy(ContractClause clause, NumericPolicy fallback,
                                              Map<String, BuiltinContract> aliases) {
        if (clause == null) return fallback;
        boolean broad = false;
        for (ContractName name : clause.names()) {
            if (name.inline() != null || !name.arguments().isEmpty()) continue;
            BuiltinContract contract = aliases.get(name.name());
            if (contract == null) contract = BuiltinContract.named(name.name()).orElse(null);
            if (contract == BuiltinContract.NUMBER || contract == BuiltinContract.REAL) broad = true;
            else if (contract == BuiltinContract.INTEGER || contract == BuiltinContract.NATURAL
                    || contract == BuiltinContract.INT8 || contract == BuiltinContract.UINT8
                    || contract == BuiltinContract.INT16 || contract == BuiltinContract.UINT16
                    || contract == BuiltinContract.INT32 || contract == BuiltinContract.UINT32
                    || contract == BuiltinContract.INT64 || contract == BuiltinContract.UINT64
                    || contract == BuiltinContract.FLOAT || contract == BuiltinContract.DOUBLE)
                return NumericPolicy.STRICT;
        }
        return broad ? NumericPolicy.BROAD : fallback;
    }

    private void analyzeStaticPrecisionExpression(Expr expression, NumericPolicy policy,
                                                  ContractClause context) {
        if (!(ungroup(expression) instanceof Binary(String operator, Expr leftExpr, Expr rightExpr,
                SourceSpan binarySpan)) || !operator.equals("/")
                || !(ungroup(leftExpr) instanceof Literal(Value.Num left, SourceSpan ignoredLeft))
                || !(ungroup(rightExpr) instanceof Literal(Value.Num right, SourceSpan ignoredRight))) return;
        java.math.BigInteger numerator = NumericValues.integral(left);
        java.math.BigInteger denominator = NumericValues.integral(right);
        if (numerator == null || denominator == null || denominator.signum() == 0
                || numerator.remainder(denominator).signum() == 0) return;
        double rounded = NumericValues.quotientToDouble(numerator, denominator);
        if (!Double.isFinite(rounded) || new java.math.BigDecimal(rounded)
                .multiply(new java.math.BigDecimal(denominator))
                .compareTo(new java.math.BigDecimal(numerator)) == 0) return;
        List<Diagnostic.Related> related = context == null ? List.of()
                : List.of(new Diagnostic.Related("Numeric result requirement", context.span()));
        Diagnostic diagnostic = new Diagnostic(Diagnostic.Phase.SEMANTIC,
                Diagnostic.Codes.IMPLICIT_PRECISION_LOSS,
                "Implicit numeric precision loss", binarySpan, related);
        if (policy == NumericPolicy.STRICT) throw new LangException(diagnostic);
        staticallyReportedPrecisionLosses.add(binarySpan);
        warnings.add(diagnostic);
    }

    private void reportImplicitPrecisionLoss(SourceSpan span) {
        if (staticallyReportedPrecisionLosses.contains(span)) return;
        Diagnostic diagnostic = new Diagnostic(Diagnostic.Phase.RUNTIME,
                Diagnostic.Codes.IMPLICIT_PRECISION_LOSS,
                "Implicit numeric precision loss", span);
        if (numericPolicy == NumericPolicy.STRICT) throw new LangException(diagnostic);
        warnings.add(diagnostic);
    }

    private Value.Num contextualNumericLiteral(Value.Num number, SourceSpan span,
                                               BuiltinContract format) {
        if (format == null || number.literalText() == null) return number;
        java.math.BigDecimal source = new java.math.BigDecimal(number.literalText());
        double rounded = format == BuiltinContract.FLOAT ? source.floatValue() : source.doubleValue();
        if (!Double.isFinite(rounded)) {
            throw runtime(Diagnostic.Codes.NON_FINITE_RESULT,
                    "Numeric result is not finite", span);
        }
        return new Value.Num(rounded);
    }

    private static Expr ungroup(Expr expression) {
        while (expression instanceof Group group) expression = group.expression();
        return expression;
    }

    private Value groupedContractApplication(Apply application, Environment env, Resolution resolution) {
        ArrayList<Expr> arguments = new ArrayList<>();
        Expr head = application;
        while (head instanceof Apply apply) {
            arguments.addFirst(apply.argument());
            head = apply.function();
        }
        if (!(head instanceof Group)) return null;
        Value selected = underlying(evalInner(head, env, resolution));
        if (selected instanceof Value.ContractValue contract) {
            Expr operand = arguments.getFirst();
            for (int index = 1; index < arguments.size(); index++) {
                Expr argument = arguments.get(index);
                operand = new Apply(operand, argument, SourceSpan.cover(operand.span(), argument.span()));
            }
            Value source = eval(operand, env, null, resolution);
            return convertValue(contract.descriptor(), source, operand.span(), env, application.span());
        }
        Value current = selected;
        SourceSpan callSpan = head.span();
        for (Expr argument : arguments) {
            if (!(underlying(current) instanceof Value.Callable callable)) {
                throw runtime(Diagnostic.Codes.NOT_CALLABLE,
                        "Value is not callable: " + current, callSpan);
            }
            Value value = argumentValue(argument, callable, env, resolution);
            callSpan = SourceSpan.cover(callSpan, argument.span());
            current = invoke(callable, new Value.Argument(value, argument.span()), callSpan);
        }
        return current;
    }

    private Value convertValue(ContractDescriptor requested, Value source, SourceSpan operandSpan,
                               Environment env, SourceSpan conversionSpan) {
        return convertValue(requested, source, operandSpan, env, conversionSpan, true);
    }

    private Value convertValue(ContractDescriptor requested, Value source, SourceSpan operandSpan,
                               Environment env, SourceSpan conversionSpan, boolean verifyFinal) {
        ContractDescriptor target = requested instanceof ModifiedContract modified ? modified.base() : requested;
        Value raw = underlying(source);
        if (raw == Value.Null.INSTANCE || raw == Value.Missing.INSTANCE) {
            return verifyFinal ? checkedConversion(requested, source, operandSpan) : source;
        }
        if (verifyFinal && (target instanceof TemplateContract
                && (raw instanceof Value.Seq || raw instanceof Value.Dictionary)
                || target instanceof ParameterizedContract parameterized
                && (parameterized.base() == BuiltinContract.SEQUENCE
                && raw instanceof Value.Seq
                || parameterized.base() == BuiltinContract.PACKED
                && raw instanceof Value.PackedCollection))
                && requested.test(source, operandSpan)) return source;
        Value converted = source;
        if (target instanceof ParameterizedContract parameterized
                && parameterized.base() == BuiltinContract.SEQUENCE
                && parameterized.parameterArity() == 0) {
            converted = convertSequence(parameterized.arguments().getFirst(), source, operandSpan, env,
                    conversionSpan);
        } else if (target instanceof ParameterizedContract parameterized
                && parameterized.base() == BuiltinContract.PACKED
                && parameterized.parameterArity() == 0) {
            ContractDescriptor elementContract = parameterized.arguments().getFirst();
            validatePackedElementLayout(elementContract, operandSpan);
            Value.Seq sequence = (Value.Seq) convertSequence(elementContract, source,
                    operandSpan, env, conversionSpan);
            List<Value> acquired = new ArrayList<>(sequence.values().size());
            for (Value original : sequence.values()) {
                Value element = acquirePackedValue(elementContract, original, operandSpan);
                if (!elementContract.test(element, operandSpan)) {
                    throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                            "Conversion to " + requested.publicName() + " rejected "
                                    + ValueSemantics.kind(element), operandSpan);
                }
                acquired.add(element);
            }
            converted = new Value.PackedCollection(elementContract, acquired, operandSpan,
                    ownership.optimizationsEnabled());
            if (verifyFinal) return converted;
        } else if (target instanceof TemplateContract template) {
            converted = convertTemplateNode(template.descriptor().root(), source, operandSpan, env,
                    conversionSpan);
        } else if (target instanceof BuiltinContract builtin && (builtin == BuiltinContract.FLOAT
                || builtin == BuiltinContract.DOUBLE || builtin == BuiltinContract.INTEGER
                || builtin == BuiltinContract.NATURAL || builtin == BuiltinContract.INT8
                || builtin == BuiltinContract.UINT8 || builtin == BuiltinContract.INT16
                || builtin == BuiltinContract.UINT16 || builtin == BuiltinContract.INT32
                || builtin == BuiltinContract.UINT32 || builtin == BuiltinContract.INT64
                || builtin == BuiltinContract.UINT64)) {
            if (!(raw instanceof Value.Num number)) throw unsupportedConversion(raw, requested, operandSpan);
            java.math.BigDecimal exact = NumericValues.decimal(number);
            if (builtin == BuiltinContract.FLOAT || builtin == BuiltinContract.DOUBLE) {
                double rounded = builtin == BuiltinContract.FLOAT ? exact.floatValue() : exact.doubleValue();
                converted = finiteNumber(rounded, operandSpan);
            } else converted = new Value.Num(exact.toBigInteger());
        } else if (target == BuiltinContract.STRING && !(raw instanceof Value.Str)) {
            Value renderer = underlying(env.get("toString"));
            if (!(renderer instanceof Value.Callable callable)) {
                throw runtime(Diagnostic.Codes.NOT_CALLABLE,
                        "Value is not callable: " + renderer, conversionSpan);
            }
            converted = invoke(callable, new Value.Argument(source, operandSpan), conversionSpan);
        }
        return verifyFinal ? checkedConversion(requested, converted, operandSpan) : converted;
    }

    private Value convertSequence(ContractDescriptor elementContract, Value source,
                                  SourceSpan operandSpan, Environment env, SourceSpan conversionSpan) {
        CollectionRuntime.Provider provider = CollectionRuntime.provider(source, reflectionContext)
                .orElseThrow(() -> unsupportedConversion(underlying(source),
                        BuiltinContract.SEQUENCE, operandSpan));
        CollectionRuntime.Facts facts = provider.facts();
        facts.validate(operandSpan);
        if (facts.keyed() == CollectionRuntime.Guarantee.TRUE) {
            throw runtime(Diagnostic.Codes.EXPECTED_SEQUENCE,
                    "Explicit keys, values, or fields projection required for keyed conversion", operandSpan);
        }
        if (facts.finite() == CollectionRuntime.Guarantee.FALSE) {
            throw runtime(Diagnostic.Codes.EAGER_INFINITE,
                    "Cannot convert a declared-infinite Collection", operandSpan);
        }
        IndexedCollection indexed = indexedSequence(new Value.Argument(source, operandSpan));
        ArrayList<Value> elements = new ArrayList<>();
        for (int index = 0; ; index++) {
            Optional<Value> element = indexed.at(index);
            if (element.isEmpty()) break;
            elements.add(convertValue(elementContract, element.get(), operandSpan, env, conversionSpan, false));
        }
        return new Value.Seq(elements);
    }

    private Value convertTemplateNode(CollectionConstructorDescriptor.Node node, Value source,
                                      SourceSpan operandSpan, Environment env, SourceSpan conversionSpan) {
        Value raw = underlying(source);
        if (node instanceof CollectionConstructorDescriptor.FixedNode) return source;
        if (node instanceof CollectionConstructorDescriptor.HoleNode hole) {
            ContractDescriptor selected = null;
            for (Object requirement : hole.requirements()) {
                if (!(requirement instanceof ContractDescriptor candidate)
                        || !convertibleRequirement(candidate)) continue;
                if (selected != null && selected != candidate) return source;
                selected = candidate;
            }
            return selected == null ? source : convertValue(selected, source, operandSpan, env, conversionSpan, false);
        }
        CollectionConstructorDescriptor.CollectionNode collection =
                (CollectionConstructorDescriptor.CollectionNode) node;
        if (collection.named()) {
            Map<String, Value> fields = raw instanceof Value.Dictionary dictionary ? dictionary.entries()
                    : raw instanceof Value.ProjectedDictionary projected
                    ? projected.fields(reflectionContext) : null;
            if (fields == null || fields.size() != collection.elements().size()) {
                throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                        "Conversion requires the template's exact named fields", operandSpan);
            }
            LinkedHashMap<String, Value> converted = new LinkedHashMap<>();
            for (CollectionConstructorDescriptor.Element element : collection.elements()) {
                Value member = fields.get(element.name());
                if (member == null) {
                    throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                            "Conversion requires the template's exact named fields", operandSpan);
                }
                converted.put(element.name(), convertTemplateNode(element.value(), member,
                        operandSpan, env, conversionSpan));
            }
            return new Value.Dictionary(converted);
        }
        Value.Seq input = (Value.Seq) convertSequence(BuiltinContract.ANY, source,
                operandSpan, env, conversionSpan);
        if (input.size() != collection.elements().size()) {
            throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                    "Conversion requires the template's exact positional shape", operandSpan);
        }
        ArrayList<Value> converted = new ArrayList<>(input.size());
        for (int index = 0; index < input.size(); index++) {
            converted.add(convertTemplateNode(collection.elements().get(index).value(),
                    input.find(index).orElseThrow(),
                    operandSpan, env, conversionSpan));
        }
        return new Value.Seq(converted);
    }

    private static boolean convertibleRequirement(ContractDescriptor target) {
        if (target instanceof ModifiedContract modified) target = modified.base();
        return target == BuiltinContract.STRING || target == BuiltinContract.FLOAT
                || target == BuiltinContract.DOUBLE || target == BuiltinContract.INTEGER
                || target == BuiltinContract.NATURAL || target == BuiltinContract.INT8
                || target == BuiltinContract.UINT8 || target == BuiltinContract.INT16
                || target == BuiltinContract.UINT16 || target == BuiltinContract.INT32
                || target == BuiltinContract.UINT32 || target == BuiltinContract.INT64
                || target == BuiltinContract.UINT64 || target instanceof TemplateContract
                || target instanceof ParameterizedContract parameterized
                && (parameterized.base() == BuiltinContract.SEQUENCE
                || parameterized.base() == BuiltinContract.PACKED);
    }

    private void validatePackedElementLayout(ContractDescriptor target, SourceSpan span) {
        PackedLayout.of(target, span);
    }

    private Value checkedConversion(ContractDescriptor target, Value value, SourceSpan span) {
        if (target.test(value, span)) return value;
        throw runtime(Diagnostic.Codes.CONTRACT_VIOLATION,
                "Conversion to " + target.publicName() + " rejected " + ValueSemantics.kind(value), span);
    }

    private LangException unsupportedConversion(Value source, ContractDescriptor target, SourceSpan span) {
        return runtime(Diagnostic.Codes.UNSUPPORTED_CONVERSION,
                "Cannot convert " + ValueSemantics.kind(source) + " to " + target.publicName(), span);
    }

    private Value evaluateCollection(CollectionLiteral literal, Environment env, Resolution resolution) {
        return evaluateCollection(literal, env, resolution, CollectionShape.INFER,
                (CollectionConstructorDescriptor.CollectionNode) null);
    }

    private Value evaluateCollection(CollectionLiteral literal, Environment env, Resolution resolution,
                                     CollectionShape expected, TemplateContract template) {
        return evaluateCollection(literal, env, resolution, expected,
                template == null ? null : template.descriptor().root());
    }

    private Value evaluateCollection(CollectionLiteral literal, Environment env, Resolution resolution,
                                     CollectionShape expected,
                                     CollectionConstructorDescriptor.CollectionNode shape) {
        return evaluateCollection(literal, env, resolution, expected, shape, null, false);
    }

    private Value evaluatePackedLiteral(CollectionLiteral literal, Environment env,
                                        Resolution resolution, ParameterizedContract packed) {
        ContractDescriptor element = packed.arguments().getFirst();
        CollectionConstructorDescriptor.Node elementShape = element instanceof TemplateContract template
                ? template.descriptor().root()
                : new CollectionConstructorDescriptor.HoleNode(0, List.of(element), literal.span());
        return evaluateCollection(literal, env, resolution, CollectionShape.KEYLESS,
                null, elementShape, true);
    }

    private Value evaluateCollection(CollectionLiteral literal, Environment env, Resolution resolution,
                                     CollectionShape expected,
                                     CollectionConstructorDescriptor.CollectionNode shape,
                                     CollectionConstructorDescriptor.Node positionalElement,
                                     boolean selectNumeric) {
        if (literal.elements().isEmpty() && (shape == null || !shape.named())) {
            return Value.EmptyCollection.INSTANCE;
        }
        ArrayList<Value> values = new ArrayList<>(literal.elements().size());
        for (int index = 0; index < literal.elements().size(); index++) {
            CollectionElement element = literal.elements().get(index);
            CollectionConstructorDescriptor.Node node = positionalElement != null ? positionalElement
                    : expectedNode(shape, element, index);
            CollectionConstructorDescriptor.CollectionNode nested =
                    node instanceof CollectionConstructorDescriptor.CollectionNode collection ? collection : null;
            Value value = element.value() instanceof CollectionLiteral collection && nested != null
                    ? evaluateCollection(collection, env, resolution,
                    nested.named() ? CollectionShape.DICTIONARY : CollectionShape.KEYLESS,
                    nested, null, selectNumeric)
                    : selectNumeric && node instanceof CollectionConstructorDescriptor.HoleNode hole
                    && ungroup(element.value()) instanceof Literal(Value.Num number, SourceSpan span)
                    && packedNumericFormat(hole) != null
                    ? contextualNumericLiteral(number, span, packedNumericFormat(hole))
                    : evalInner(element.value(), env, resolution);
            values.add(element instanceof NamedElement named
                    ? new Value.Field(new Value.Str(named.name()), value) : value);
        }

        if (shape != null && shape.named()) {
            Set<String> present = new HashSet<>();
            for (Value value : values) {
                if (underlying(value) instanceof Value.Field field
                        && underlying(field.key()) instanceof Value.Str(String name)) present.add(name);
            }
            for (CollectionConstructorDescriptor.Element element : shape.elements()) {
                if (present.contains(element.name())) continue;
                if (element.defaultsMissing()) {
                    values.add(new Value.Field(new Value.Str(element.name()), Value.Missing.INSTANCE));
                } else {
                    throw new LangException(new Diagnostic(Diagnostic.Phase.RUNTIME,
                            Diagnostic.Codes.CONTRACT_VIOLATION,
                            "Contract violation for collection literal: missing field " + element.name(),
                            literal.span(), List.of(new Diagnostic.Related(
                            "Template field declared here", element.span()))));
                }
            }
        }

        ArrayList<Value> keyless = new ArrayList<>();
        ArrayList<Value.KeyedCollection.Entry> keyed = new ArrayList<>();
        boolean sawKeyed = false;
        boolean sawKeyless = false;
        boolean sawAssociatedValue = false;
        for (int index = 0; index < values.size(); index++) {
            Value value = ValueSemantics.underlying(values.get(index));
            SourceSpan span = index < literal.elements().size()
                    ? literal.elements().get(index).span() : literal.span();
            if (expected == CollectionShape.SET && !(value instanceof Value.Field)) {
                if (value == Value.Missing.INSTANCE) {
                    throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_DICTIONARY_KEY,
                            "A Set cannot contain missing", span);
                }
                sawKeyed = true;
                addFirst(keyed, value, Value.Missing.INSTANCE);
                continue;
            }
            if (!(value instanceof Value.Field field)) {
                sawKeyless = true;
                keyless.add(values.get(index));
                continue;
            }
            Value key = ValueSemantics.underlying(field.key());
            Value associated = field.value();
            if (key == Value.Missing.INSTANCE) {
                if (ValueSemantics.underlying(associated) != Value.Missing.INSTANCE) {
                    sawKeyless = true;
                    keyless.add(associated);
                }
                continue;
            }
            sawKeyed = true;
            if (ValueSemantics.underlying(associated) != Value.Missing.INSTANCE) sawAssociatedValue = true;
            addFirst(keyed, field.key(), associated);
        }

        if (!sawKeyed && !sawKeyless) return Value.EmptyCollection.INSTANCE;
        if (sawKeyed && sawKeyless) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.MIXED_COLLECTION_SHAPE,
                    "A collection cannot mix keyed and keyless elements", literal.span());
        }
        if (sawKeyless) {
            if (expected == CollectionShape.DICTIONARY || expected == CollectionShape.SET) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.MIXED_COLLECTION_SHAPE,
                        "Collection elements do not satisfy the selected keyed shape", literal.span());
            }
            return ownership.fresh(new Value.Seq(keyless));
        }
        if (expected == CollectionShape.KEYLESS) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.MIXED_COLLECTION_SHAPE,
                    "Keyed fields do not satisfy the selected keyless shape", literal.span());
        }
        if (expected == CollectionShape.SET) {
            if (sawAssociatedValue) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.MIXED_COLLECTION_SHAPE,
                        "A Set cannot contain associated field values", literal.span());
            }
            return ownership.fresh(new Value.KeyedCollection(Value.KeyedCollection.Shape.SET, keyed));
        }
        if (expected == CollectionShape.INFER && !sawAssociatedValue) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.AMBIGUOUS_COLLECTION_SHAPE,
                    "Fields without values require a Set or Dictionary contract", literal.span());
        }

        boolean dictionary = expected == CollectionShape.DICTIONARY || homogeneousSortableKeys(keyed);
        if (expected == CollectionShape.DICTIONARY && !homogeneousSortableKeys(keyed)) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_DICTIONARY_KEY,
                    "Dictionary keys must have one homogeneous sortable type", literal.span());
        }
        if (dictionary) keyed.sort((left, right) -> compareDictionaryKeys(left.key(), right.key()));
        if (dictionary && keyed.stream().allMatch(entry -> ValueSemantics.underlying(entry.key()) instanceof Value.Str)) {
            LinkedHashMap<String, Value.Field> fields = new LinkedHashMap<>();
            for (Value candidate : values) {
                if (ValueSemantics.underlying(candidate) instanceof Value.Field field
                        && ValueSemantics.underlying(field.key()) instanceof Value.Str(String name)) {
                    fields.putIfAbsent(name, field);
                }
            }
            return ownership.fresh(Value.Dictionary.fromFields(fields));
        }
        return ownership.fresh(new Value.KeyedCollection(dictionary
                ? Value.KeyedCollection.Shape.DICTIONARY : Value.KeyedCollection.Shape.GENERAL, keyed));
    }

    private static CollectionConstructorDescriptor.Node expectedNode(
            CollectionConstructorDescriptor.CollectionNode shape, CollectionElement element, int index) {
        if (shape == null) return null;
        if (!shape.named()) return index < shape.elements().size() ? shape.elements().get(index).value() : null;
        if (!(element instanceof NamedElement named)) return null;
        for (CollectionConstructorDescriptor.Element candidate : shape.elements()) {
            if (named.name().equals(candidate.name())) return candidate.value();
        }
        return null;
    }

    private static BuiltinContract packedNumericFormat(CollectionConstructorDescriptor.HoleNode hole) {
        BuiltinContract selected = null;
        for (Object requirement : hole.requirements()) {
            if (requirement != BuiltinContract.FLOAT && requirement != BuiltinContract.DOUBLE) continue;
            if (selected != null && selected != requirement) return null;
            selected = (BuiltinContract) requirement;
        }
        return selected;
    }

    private void addFirst(List<Value.KeyedCollection.Entry> entries, Value key, Value value) {
        for (Value.KeyedCollection.Entry entry : entries) {
            if (ValueSemantics.equal(entry.key(), key, reflectionContext)) return;
        }
        entries.add(new Value.KeyedCollection.Entry(key, value));
    }

    private static boolean homogeneousSortableKeys(List<Value.KeyedCollection.Entry> entries) {
        if (entries.isEmpty()) return true;
        ValueKind kind = ValueKind.of(entries.getFirst().key());
        if (!(kind == ValueKind.NUMBER || kind == ValueKind.STRING || kind == ValueKind.BOOLEAN
                || kind == ValueKind.NULL)) return false;
        return entries.stream().allMatch(entry -> ValueKind.of(entry.key()) == kind);
    }

    private static int compareDictionaryKeys(Value left, Value right) {
        left = ValueSemantics.underlying(left);
        right = ValueSemantics.underlying(right);
        if (left instanceof Value.Num a && right instanceof Value.Num b) return NumericValues.compare(a, b);
        if (left instanceof Value.Str(String a) && right instanceof Value.Str(String b)) {
            return CollectionRuntime.FIELD_ORDER.compare(a, b);
        }
        if (left instanceof Value.Bool(boolean a) && right instanceof Value.Bool(boolean b)) {
            return Boolean.compare(a, b);
        }
        if (left instanceof Value.Null && right instanceof Value.Null) return 0;
        throw new IllegalArgumentException("Non-sortable Dictionary key");
    }

    private Value.Callable collectionCallback(Value.Argument argument, int arity,
                                              String operation, String role) {
        Value raw = underlying(argument.value());
        if (raw instanceof Value.Callable callable && callable.remainingArity() == arity) return callable;
        throw runtime(Diagnostic.Codes.INVALID_COLLECTION_CALLBACK,
                operation + " " + role + " must be a callable requiring exactly "
                        + (arity == 1 ? "one argument" : "two arguments"), argument.span());
    }

    private boolean predicateResult(Value value, String operation, SourceSpan span) {
        Value raw = underlying(value);
        if (raw instanceof Value.Bool(boolean value1)) return value1;
        if (raw instanceof Value.Null || raw instanceof Value.Missing) return false;
        throw runtime(Diagnostic.Codes.INVALID_PREDICATE_RESULT,
                operation + " predicate must return Boolean, null, or missing", span);
    }

    private Value.Dictionary dictionary(Value.Argument argument) {
        Value raw = underlying(argument.value());
        if (raw instanceof Value.EmptyCollection) return ownership.fresh(new Value.Dictionary(Map.of()));
        if (raw instanceof Value.Dictionary dictionary) return dictionary;
        if (raw instanceof Value.ProjectedDictionary dictionary) {
            return ownership.fresh(new Value.Dictionary(dictionary.fields(reflectionContext)));
        }
        throw runtime(Diagnostic.Codes.EXPECTED_DICTIONARY,
                "Expected dictionary, got: " + argument.value(), argument.span());
    }

    private OptionalInt index(Value.Argument argument) {
        int index = NumericValues.nonNegativeInt(argument.value());
        return index < 0 ? OptionalInt.empty() : OptionalInt.of(index);
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

    private Value invokeAccessor(Expr expression, Value target, SourceSpan targetSpan,
                                 Value key, SourceSpan keySpan, Environment env,
                                 Resolution resolution) {
        Resolution.Binding binding = resolution.accessor(expression);
        Value resolved = resolution.scopedLookup(expression) != null
                ? readScoped(expression, "getElement", env, resolution)
                : binding == null ? env.get("getElement") : env.getResolved(binding);
        Value raw = underlying(resolved);
        if (!(raw instanceof Value.Callable callable)) {
            throw runtime(Diagnostic.Codes.NOT_CALLABLE,
                    "Value is not callable: " + resolved, expression.span());
        }
        Value partial = invoke(callable, new Value.Argument(target, targetSpan), expression.span());
        if (!(underlying(partial) instanceof Value.Callable remaining)) {
            throw runtime(Diagnostic.Codes.TOO_MANY_ARGUMENTS,
                    "Callable accepts fewer than two arguments", keySpan);
        }
        return invoke(remaining, new Value.Argument(key, keySpan), expression.span());
    }

    private Value argumentValue(Expr argument, Value.Callable callable,
                                Environment env, Resolution resolution) {
        NumericPolicy previousPolicy = numericPolicy;
        if (callable instanceof Value.ContractedCallable contracted && contracted.strictNumeric()
                || callable instanceof OverloadCallable overload && overload.strictNumeric()) {
            numericPolicy = NumericPolicy.STRICT;
        }
        try {
            if (ungroup(argument) instanceof Literal(Value.Num number, SourceSpan span)) {
                BuiltinContract format = callable instanceof Value.ContractedCallable contracted
                        ? contracted.expectedNumericFormat() : callable instanceof OverloadCallable overload
                        ? overload.expectedNumericFormat() : null;
                return contextualNumericLiteral(number, span, format);
            }
            if (!(argument instanceof CollectionLiteral literal)) return evalInner(argument, env, resolution);
            if (!analyzeCollectionHoles(literal).indexes().isEmpty()) return eval(argument, env, null, resolution);
            TemplateContract template = callable instanceof Value.ContractedCallable contracted
                    ? contracted.expectedTemplate() : callable instanceof OverloadCallable overload
                    ? overload.expectedTemplate() : null;
            ParameterizedContract packed = callable instanceof Value.ContractedCallable contracted
                    ? contracted.expectedPacked() : callable instanceof OverloadCallable overload
                    ? overload.expectedPacked() : null;
            CollectionShape shape = packed != null ? CollectionShape.KEYLESS
                    : template != null && template.descriptor().root().named()
                    ? CollectionShape.DICTIONARY : CollectionShape.INFER;
            Value value = packed != null ? evaluatePackedLiteral(literal, env, resolution, packed)
                    : evaluateCollection(literal, env, resolution, shape, template);
            return packed == null ? value : contextualPackedLiteral(packed, value, literal.span());
        } finally {
            numericPolicy = previousPolicy;
        }
    }

    private Value reflect(Value value) {
        ownership.share(value);
        Value reflected = underlying(value);
        if (reflected instanceof Value.Callable callable && !(reflected instanceof Value.Reflective)) {
            return Value.CallableMetadata.reflection(callable, reflectionContext);
        }
        if (reflected instanceof Value.Container container) {
            return new Value.ProjectedDictionary(
                    context -> ValueSemantics.reflectionFields(container, context),
                    reflectionContext, value, container);
        }
        Map<String, Value> fields = ValueSemantics.reflectionFields(reflected, reflectionContext);
        return Value.Dictionary.reflection(fields, value, reflectionContext);
    }

    private Value reifyField(Value owner, String name) {
        ownership.share(owner);
        Value raw = underlying(owner);
        Value.Field field = raw instanceof Value.Dictionary dictionary ? dictionary.fieldBinding(name)
                : providerField(raw, name);
        if (field == null) return Value.Missing.INSTANCE;
        field.addOwner(raw);
        return new Value.ProjectedDictionary(context -> {
            LinkedHashMap<String, Value> facts = new LinkedHashMap<>();
            facts.put("kind", new Value.Str("FieldBinding"));
            facts.put("key", field.key());
            facts.put("mutable", new Value.Bool(false));
            facts.put("exported", new Value.Bool(true));
            List<ContractDescriptor> visibleContracts = field.contracts().stream().filter(context::names).toList();
            facts.put("contracts", visibleContracts.isEmpty() ? Value.EmptyCollection.INSTANCE
                    : new Value.Seq(visibleContracts.stream().map(contract -> contractReference(contract, context)).toList()));
            facts.put("nullable", field.contracts().isEmpty()
                    || visibleContracts.size() != field.contracts().size() ? Value.Missing.INSTANCE
                    : new Value.Bool(field.contracts().stream().allMatch(contract ->
                    contract instanceof ModifiedContract modified && modified.nullable()
                            || contract == BuiltinContract.ANY || contract == BuiltinContract.NULL)));
            facts.put("optional", field.contracts().isEmpty()
                    || visibleContracts.size() != field.contracts().size() ? Value.Missing.INSTANCE
                    : new Value.Bool(field.contracts().stream().allMatch(contract ->
                    contract instanceof ModifiedContract modified && modified.optional()
                            || contract == BuiltinContract.ANY || contract == BuiltinContract.MISSING)));
            List<Value> owners = field.owners().stream().filter(context::names)
                    .map(candidate -> (Value) new Value.ProjectedDictionary(
                            observer -> ValueSemantics.reflectionFields(candidate, observer),
                            context, candidate, candidate))
                    .toList();
            facts.put("owner", owners.isEmpty() ? Value.Missing.INSTANCE
                    : owners.size() == 1 ? owners.getFirst() : new Value.Seq(owners));
            return facts;
        }, reflectionContext, field, field);
    }

    private Value.Field providerField(Value owner, String name) {
        if (owner instanceof Value.KeyedCollection keyed) return keyed.fieldBinding(name);
        if (owner instanceof Value.SettledCollection settled) return settled.fieldBinding(name);
        if (owner instanceof Value.LazyCollection lazy) return lazy.fieldBinding(name);
        return null;
    }

    private static Value contractReference(ContractDescriptor descriptor, ReflectionContext context) {
        Value.ContractValue contract = new Value.ContractValue(descriptor);
        return Value.Dictionary.reflection(ValueSemantics.reflectionFields(contract, context), contract, context);
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

    private CollectionConstructorDescriptor collectionConstructor(CollectionLiteral literal,
                                                                  Environment env,
                                                                  Resolution resolution,
                                                                  List<Integer> indexes) {
        ConstructorBuild build = new ConstructorBuild(indexes.stream().anyMatch(index -> index > 0));
        CollectionConstructorDescriptor.CollectionNode root = constructorCollection(
                literal, env, resolution, build);
        if (root == null) return null;
        int arity = build.numbered
                ? indexes.stream().mapToInt(Integer::intValue).max().orElseThrow()
                : indexes.size();
        ArrayList<List<Object>> requirements = new ArrayList<>();
        for (int index = 0; index < arity; index++) requirements.add(List.of());
        for (CollectionConstructorDescriptor.HoleNode hole : build.holes) {
            ArrayList<Object> combined = new ArrayList<>(requirements.get(hole.parameter()));
            for (Object requirement : hole.requirements()) {
                if (combined.stream().noneMatch(existing -> existing == requirement)) combined.add(requirement);
            }
            requirements.set(hole.parameter(), List.copyOf(combined));
        }
        return new CollectionConstructorDescriptor(eligibleDefaults(root, requirements, new HashMap<>()),
                requirements);
    }

    private static final class ConstructorBuild {
        private final boolean numbered;
        private int ordinary;
        private final ArrayList<CollectionConstructorDescriptor.HoleNode> holes = new ArrayList<>();
        private ConstructorBuild(boolean numbered) { this.numbered = numbered; }
        private int parameter(Hole hole) { return numbered ? hole.index() - 1 : ordinary++; }
    }

    private CollectionConstructorDescriptor.CollectionNode constructorCollection(CollectionLiteral literal,
                                                                                  Environment env,
                                                                                  Resolution resolution,
                                                                                  ConstructorBuild build) {
        ArrayList<CollectionConstructorDescriptor.Element> elements = new ArrayList<>();
        for (CollectionElement element : literal.elements()) {
            CollectionConstructorDescriptor.Element dynamic = element instanceof PositionalElement
                    ? dynamicConstructorField(element.value(), env, resolution, build) : null;
            if (dynamic != null) {
                elements.add(dynamic);
                continue;
            }
            CollectionConstructorDescriptor.Node node = constructorNode(
                    element.value(), env, resolution, build);
            if (node == null) return null;
            boolean defaultMissing = element instanceof NamedElement
                    && directlyDefaultsMissing(element.value(), node);
            elements.add(new CollectionConstructorDescriptor.Element(
                    element instanceof NamedElement field ? field.name() : null, node, element.span(), defaultMissing));
        }
        boolean named = !elements.isEmpty() && elements.stream().allMatch(element -> element.name() != null);
        boolean positional = elements.stream().noneMatch(element -> element.name() != null);
        if (!named && !positional) return null;
        return new CollectionConstructorDescriptor.CollectionNode(named, elements, literal.span());
    }

    private CollectionConstructorDescriptor.Element dynamicConstructorField(Expr expression, Environment env,
                                                                             Resolution resolution,
                                                                             ConstructorBuild build) {
        Expr raw = expression;
        while (raw instanceof Group group) raw = group.expression();
        if (raw instanceof AmbiguousCall call && call.first() instanceof Name name
                && name.name().equals("field")) {
            return resolvedDynamicConstructorField(call.middle(), call.last(), expression.span(),
                    env, resolution, build);
        }
        if (raw instanceof Apply outer && outer.function() instanceof AmbiguousCall call
                && call.first() instanceof Name name && name.name().equals("field")) {
            Expr contracted = new Apply(call.last(), outer.argument(),
                    SourceSpan.cover(call.last().span(), outer.argument().span()));
            return resolvedDynamicConstructorField(call.middle(), contracted, expression.span(),
                    env, resolution, build);
        }
        if (!(raw instanceof Apply outer) || !(outer.function() instanceof Apply inner)) return null;
        Expr target = inner.function();
        while (target instanceof Group group) target = group.expression();
        if (!(target instanceof Name name) || !name.name().equals("field")) return null;
        return resolvedDynamicConstructorField(inner.argument(), outer.argument(), expression.span(),
                env, resolution, build);
    }

    private CollectionConstructorDescriptor.Element resolvedDynamicConstructorField(
            Expr keyExpression, Expr valueExpression, SourceSpan span, Environment env,
            Resolution resolution, ConstructorBuild build) {
        Value key = underlying(eval(keyExpression, env, null, resolution));
        if (!(key instanceof Value.Str(String fieldName))) {
            throw runtime(Diagnostic.Codes.INVALID_DYNAMIC_FIELD_NAME,
                    "Dynamic field name must be a string, got: " + key, keyExpression.span());
        }
        CollectionConstructorDescriptor.Node value = constructorNode(
                valueExpression, env, resolution, build);
        if (value == null) return null;
        return new CollectionConstructorDescriptor.Element(fieldName, value, span,
                directlyDefaultsMissing(valueExpression, value));
    }

    private CollectionConstructorDescriptor.Node constructorNode(Expr expression, Environment env,
                                                                 Resolution resolution,
                                                                 ConstructorBuild build) {
        Expr unwrapped = expression;
        while (unwrapped instanceof Group group) unwrapped = group.expression();
        if (unwrapped instanceof Hole hole) return constructorHole(hole, List.of(), build);
        if (unwrapped instanceof Apply apply && apply.argument() instanceof Hole hole) {
            List<Object> requirements = constructorRequirements(apply.function(), env, resolution);
            if (requirements != null) return constructorHole(hole, requirements, build);
        }
        if (unwrapped instanceof CollectionLiteral nested) {
            return constructorCollection(nested, env, resolution, build);
        }
        HoleAnalysis nestedHoles = analyzeCollectionHoles(expression);
        if (!nestedHoles.indexes().isEmpty()) return null;
        return new CollectionConstructorDescriptor.FixedNode(
                eval(expression, env, null, resolution), expression.span());
    }

    private CollectionConstructorDescriptor.HoleNode constructorHole(Hole hole, List<Object> requirements,
                                                                      ConstructorBuild build) {
        CollectionConstructorDescriptor.HoleNode node = new CollectionConstructorDescriptor.HoleNode(
                build.parameter(hole), requirements, hole.span());
        build.holes.add(node);
        return node;
    }

    private boolean directlyDefaultsMissing(Expr expression, CollectionConstructorDescriptor.Node node) {
        if (!(node instanceof CollectionConstructorDescriptor.HoleNode)) return false;
        while (expression instanceof Group group) expression = group.expression();
        if (!(expression instanceof Apply apply) || !(apply.argument() instanceof Hole)) return false;
        ArrayList<Expr> terms = new ArrayList<>();
        if (!flattenConstructorRequirements(apply.function(), terms)) return false;
        return terms.stream().anyMatch(term ->
                term instanceof ContractModifier modifier && modifier.optional());
    }

    private CollectionConstructorDescriptor.CollectionNode eligibleDefaults(
            CollectionConstructorDescriptor.CollectionNode node, List<List<Object>> requirements,
            Map<Integer, Boolean> missingAcceptance) {
        ArrayList<CollectionConstructorDescriptor.Element> elements = new ArrayList<>();
        for (CollectionConstructorDescriptor.Element element : node.elements()) {
            CollectionConstructorDescriptor.Node value = element.value();
            if (value instanceof CollectionConstructorDescriptor.CollectionNode nested) {
                value = eligibleDefaults(nested, requirements, missingAcceptance);
            }
            boolean defaults = element.defaultsMissing();
            if (defaults && value instanceof CollectionConstructorDescriptor.HoleNode hole) {
                defaults = missingAcceptance.computeIfAbsent(hole.parameter(), index ->
                        requirementsAcceptMissing(requirements.get(index), hole.span()));
            }
            elements.add(new CollectionConstructorDescriptor.Element(
                    element.name(), value, element.span(), defaults));
        }
        return new CollectionConstructorDescriptor.CollectionNode(node.named(), elements, node.span());
    }

    private boolean requirementsAcceptMissing(List<Object> requirements, SourceSpan span) {
        for (Object requirement : requirements) {
            try {
                if (requirement instanceof ContractDescriptor contract) {
                    if (!contract.accepts(Value.Missing.INSTANCE)) return false;
                } else {
                    Value result = underlying(invoke((Value.Callable) requirement,
                            new Value.Argument(Value.Missing.INSTANCE, span), span));
                    if (!(result instanceof Value.Bool(boolean accepted)) || !accepted) return false;
                }
            } catch (LangException rejectedMissing) {
                return false;
            }
        }
        return true;
    }

    private List<Object> constructorRequirements(Expr expression, Environment env,
                                                 Resolution resolution) {
        ArrayList<Expr> expressions = new ArrayList<>();
        if (!flattenConstructorRequirements(expression, expressions)) return null;
        ArrayList<Object> requirements = new ArrayList<>();
        for (Expr requirement : expressions) {
            Value value = underlying(evalInner(requirement, env, resolution));
            if (value instanceof Value.ContractValue contract) requirements.add(contract.descriptor());
            else if (value instanceof Value.Callable callable && callable.refinementEligible()) {
                requirements.add(callable);
            } else return null;
        }
        return List.copyOf(requirements);
    }

    private boolean flattenConstructorRequirements(Expr expression, List<Expr> result) {
        while (expression instanceof Group group) expression = group.expression();
        if (expression instanceof Apply apply) {
            return flattenConstructorRequirements(apply.function(), result)
                    && flattenConstructorRequirements(apply.argument(), result);
        }
        if (expression instanceof Name || expression instanceof ContractModifier) {
            result.add(expression);
            return true;
        }
        return false;
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
        return analyzeHoles(expr, false);
    }

    private HoleAnalysis analyzeCollectionHoles(Expr expr) {
        return analyzeHoles(expr, true);
    }

    private HoleAnalysis analyzeHoles(Expr expr, boolean traverseCollections) {
        ArrayList<Integer> indexes = new ArrayList<>();
        IdentityHashMap<Expr, Boolean> containsHole = new IdentityHashMap<>();
        markHoles(expr, indexes, containsHole, traverseCollections, true);
        return new HoleAnalysis(List.copyOf(indexes), containsHole);
    }

    private boolean markHoles(Expr expr, List<Integer> indexes,
                              IdentityHashMap<Expr, Boolean> containsHole,
                              boolean traverseCollections, boolean root) {
        if (!root && expr instanceof CollectionLiteral && !traverseCollections) {
            containsHole.put(expr, false);
            return false;
        }
        boolean found = expr instanceof Hole;
        if (expr instanceof Hole hole) indexes.add(hole.index());
        for (Expr child : AstTraversal.children(expr)) {
            found |= markHoles(child, indexes, containsHole, traverseCollections, false);
        }
        containsHole.put(expr, found);
        return found;
    }

    private Expr captureNonHoleParts(Expr expr, Environment env,
                                     IdentityHashMap<Expr, Boolean> containsHole, Resolution resolution) {
        return AstRewriter.rewrite(expr, candidate -> {
            if (containsHole.get(candidate)) return Optional.empty();
            Value captured = evalInner(candidate, env, resolution);
            ownership.share(captured);
            return Optional.of(new Literal(captured, candidate.span()));
        });
    }

    int ownershipReuseCount() { return ownership.reuseCount(); }

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
            return structuralHoleSignature(expression, arity);
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
                    return structuralHoleSignature(expression, arity);
                }
            }
        }
        return specialized.projectParameters(projected);
    }

    private CallableSignature structuralHoleSignature(Expr expression, int arity) {
        ArrayList<CallableSignature.EffectRef> effects = new ArrayList<>();
        boolean[] unknown = {false};
        AstTraversal.walkPreOrder(expression, candidate -> {
            if (!(candidate instanceof Literal literal)
                    || !(underlying(literal.value()) instanceof Value.Callable callable)) return;
            List<CallableSignature.EffectRef> upper = callable.signature().effects().upperBound();
            if (upper == null) unknown[0] = true;
            else for (CallableSignature.EffectRef effect : upper) {
                if (effects.stream().noneMatch(existing -> existing.identity() == effect.identity())) {
                    effects.add(effect);
                }
            }
        });
        List<CallableSignature.Parameter> parameters = Collections.nCopies(arity,
                new CallableSignature.Parameter(null, List.of(), null, null));
        List<CallableSignature.EffectRef> upper = unknown[0] ? null : List.copyOf(effects);
        return new CallableSignature(parameters, new CallableSignature.Result(List.of(), null, null),
                new CallableSignature.Effects(upper, null, upper), List.of());
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
