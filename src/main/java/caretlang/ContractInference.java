package caretlang;

import caretlang.Ast.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * First static contract-inference slice. It records guarantees for named functions without
 * replacing the runtime contract checks. Unknown guarantees are generalized by leaving the set
 * empty; a parameter/result flow is retained explicitly rather than being collapsed to Any.
 */
final class ContractInference {
    enum BuiltinEffect { OUTPUT, TEST_REPORT }

    record EffectSummary(Set<BuiltinEffect> effects, boolean unknownDynamicCall) {
        static final EffectSummary PURE = new EffectSummary(Set.of(), false);
        static final EffectSummary UNKNOWN = new EffectSummary(Set.of(), true);

        EffectSummary {
            effects = Set.copyOf(effects);
        }

        boolean isProvenPure() { return effects.isEmpty() && !unknownDynamicCall; }

        EffectSummary plus(EffectSummary other) {
            EnumSet<BuiltinEffect> combined = effects.isEmpty()
                    ? EnumSet.noneOf(BuiltinEffect.class) : EnumSet.copyOf(effects);
            combined.addAll(other.effects);
            return new EffectSummary(combined, unknownDynamicCall || other.unknownDynamicCall);
        }
    }

    private record CallableEffects(int arity, EffectSummary summary) {}

    private static final Map<String, CallableEffects> BUILTIN_EFFECTS = builtinEffects();

    record FunctionContract(List<Set<BuiltinContract>> parameterRequirements,
                            Set<BuiltinContract> resultGuarantees,
                            Integer resultParameter, boolean generalizedResult) {
        FunctionContract {
            parameterRequirements = parameterRequirements.stream().map(Set::copyOf).toList();
            resultGuarantees = Set.copyOf(resultGuarantees);
        }
    }

    private static final class Shape {
        private final EnumSet<BuiltinContract> guarantees;
        private final Integer parameter;
        private boolean generalized;

        private Shape(EnumSet<BuiltinContract> guarantees, Integer parameter, boolean generalized) {
            this.guarantees = guarantees;
            this.parameter = parameter;
            this.generalized = generalized;
        }

        static Shape unknown() { return new Shape(EnumSet.noneOf(BuiltinContract.class), null, false); }
        static Shape generic() { return new Shape(EnumSet.noneOf(BuiltinContract.class), null, true); }
        static Shape concrete(BuiltinContract contract) { return new Shape(EnumSet.of(contract), null, false); }
        static Shape parameter(int index) { return new Shape(EnumSet.noneOf(BuiltinContract.class), index, false); }
        EnumSet<BuiltinContract> guarantees() { return guarantees; }
        Integer parameter() { return parameter; }
        boolean generalized() { return generalized; }
        void resolveWith(Set<BuiltinContract> contracts) {
            guarantees.addAll(contracts);
            generalized = false;
        }
    }

    private final IdentityHashMap<FunctionDef, FunctionContract> contracts = new IdentityHashMap<>();
    private final IdentityHashMap<FunctionDef, EffectSummary> effects = new IdentityHashMap<>();

    static ContractInference analyze(List<Stmt> program) {
        ContractInference inference = new ContractInference();
        inference.analyzeBlock(program, Map.of(), BUILTIN_EFFECTS);
        return inference;
    }

    FunctionContract contract(FunctionDef function) { return contracts.get(function); }
    EffectSummary effects(FunctionDef function) { return effects.get(function); }

    void validateRefinement(FunctionDef function) {
        FunctionContract contract = contracts.get(function);
        EffectSummary effect = effects.get(function);
        if (contract == null || effect == null) throw new IllegalArgumentException("Function was not analyzed");
        if (function.params().size() != 1) invalidRefinement(function, "must take exactly one parameter");
        if (!contract.resultGuarantees().contains(BuiltinContract.BOOLEAN)) {
            invalidRefinement(function, "must guarantee a Boolean result");
        }
        if (!effect.effects().isEmpty()) {
            invalidRefinement(function, "has observable effects " + effect.effects());
        }
        if (effect.unknownDynamicCall()) {
            invalidRefinement(function, "contains a call whose purity cannot be proved");
        }
    }

    private void analyzeBlock(List<Stmt> statements, Map<String, FunctionContract> enclosing,
                              Map<String, CallableEffects> enclosingEffects) {
        LinkedHashMap<String, FunctionDef> definitions = new LinkedHashMap<>();
        for (Stmt statement : statements) {
            if (statement instanceof FunctionDef function) definitions.put(function.name(), function);
        }

        Map<String, FunctionContract> visible = new HashMap<>(enclosing);
        for (FunctionDef function : definitions.values()) visible.put(function.name(), empty(function));

        // Recursive groups are deliberately monomorphic while this fixed point is computed.
        boolean changed;
        int passes = Math.max(1, definitions.size() * 4);
        do {
            changed = false;
            for (FunctionDef function : definitions.values()) {
                FunctionContract inferred = infer(function, visible);
                if (!inferred.equals(visible.get(function.name()))) {
                    visible.put(function.name(), inferred);
                    changed = true;
                }
            }
        } while (changed && --passes > 0);

        Map<String, CallableEffects> visibleEffects = new HashMap<>(enclosingEffects);
        for (FunctionDef function : definitions.values()) {
            visibleEffects.put(function.name(), new CallableEffects(function.params().size(), EffectSummary.PURE));
        }
        boolean effectsChanged;
        int effectPasses = Math.max(1, definitions.size() * 4);
        do {
            effectsChanged = false;
            for (FunctionDef function : definitions.values()) {
                EffectSummary inferred = inferEffects(function.body(), visibleEffects);
                CallableEffects previous = visibleEffects.get(function.name());
                if (!inferred.equals(previous.summary())) {
                    visibleEffects.put(function.name(), new CallableEffects(function.params().size(), inferred));
                    effectsChanged = true;
                }
            }
        } while (effectsChanged && --effectPasses > 0);

        for (FunctionDef function : definitions.values()) {
            FunctionContract inferred = visible.get(function.name());
            contracts.put(function, inferred);
            effects.put(function, visibleEffects.get(function.name()).summary());
            analyzeBlock(function.body(), visible, visibleEffects);
        }
        analyzeOrdinaryBindings(statements, visible);
    }

    private static FunctionContract empty(FunctionDef function) {
        List<Set<BuiltinContract>> parameters = new ArrayList<>();
        for (int i = 0; i < function.params().size(); i++) parameters.add(Set.of());
        return new FunctionContract(parameters, Set.of(), null, false);
    }

    private FunctionContract infer(FunctionDef function, Map<String, FunctionContract> visible) {
        List<EnumSet<BuiltinContract>> requirements = new ArrayList<>();
        Map<String, Integer> parameters = new HashMap<>();
        for (int i = 0; i < function.params().size(); i++) {
            EnumSet<BuiltinContract> explicit = clause(function.params().get(i).contracts());
            requirements.add(explicit);
            parameters.put(function.params().get(i).name(), i);
        }
        Map<String, Shape> locals = new HashMap<>();
        Shape result = Shape.unknown();
        for (Stmt statement : function.body()) {
            result = switch (statement) {
                case Assign assign -> {
                    Shape value = expression(assign.value(), parameters, locals, requirements, visible);
                    constrain(value, clause(assign.contracts()), requirements, assign.span());
                    locals.put(assign.name(), value);
                    yield value;
                }
                case ExprStmt expression -> expression(expression.expression(), parameters, locals,
                        requirements, visible);
                case FunctionDef ignored -> Shape.unknown();
            };
        }
        EnumSet<BuiltinContract> declaredResult = clause(function.resultContracts());
        constrain(result, declaredResult, requirements, function.span());
        EnumSet<BuiltinContract> guarantees = result.guarantees().clone();
        guarantees.addAll(declaredResult);
        boolean generalizedResult = result.generalized();
        return new FunctionContract(new ArrayList<>(requirements), guarantees, result.parameter(), generalizedResult);
    }

    private Shape expression(Expr expression, Map<String, Integer> parameters, Map<String, Shape> locals,
                             List<EnumSet<BuiltinContract>> requirements,
                             Map<String, FunctionContract> visible) {
        return switch (expression) {
            case Literal literal -> literal(literal.value());
            case Name name -> parameters.containsKey(name.name()) ? Shape.parameter(parameters.get(name.name()))
                    : locals.getOrDefault(name.name(), Shape.unknown());
            case Group group -> expression(group.expression(), parameters, locals, requirements, visible);
            case Unary unary -> {
                Shape operand = expression(unary.operand(), parameters, locals, requirements, visible);
                yield switch (unary.operator()) {
                    case "-" -> {
                        constrain(operand, EnumSet.of(BuiltinContract.NUMBER), requirements, unary.operand().span());
                        yield Shape.concrete(BuiltinContract.NUMBER);
                    }
                    case "not" -> Shape.concrete(BuiltinContract.BOOLEAN);
                    default -> Shape.unknown();
                };
            }
            case Binary binary -> binary(binary, parameters, locals, requirements, visible);
            case Conditional conditional -> {
                Shape condition = expression(conditional.condition(), parameters, locals, requirements, visible);
                Shape yes = expression(conditional.whenTrue(), parameters, locals, requirements, visible);
                Shape no = expression(conditional.whenFalse(), parameters, locals, requirements, visible);
                if (conditional.condition() instanceof Literal(Value.Bool booleanValue, SourceSpan ignored)) {
                    yield booleanValue.value() ? yes : no;
                }
                EnumSet<BuiltinContract> common = yes.guarantees().clone();
                common.retainAll(no.guarantees());
                Integer flow = yes.parameter() != null && yes.parameter().equals(no.parameter())
                        ? yes.parameter() : null;
                yield new Shape(common, flow, flow == null && common.isEmpty());
            }
            case Apply apply -> application(apply, parameters, locals, requirements, visible);
            case NamedInfix infix -> application(new Apply(new Apply(infix.function(), infix.left(), infix.span()),
                    infix.right(), infix.span()), parameters, locals, requirements, visible);
            case AmbiguousCall call -> ambiguousCall(call, parameters, locals, requirements, visible);
            case Compose ignored -> Shape.unknown();
            case Field ignored -> Shape.unknown();
            case DynamicField ignored -> Shape.unknown();
            case Reflect ignored -> Shape.unknown();
            case Hole ignored -> Shape.unknown();
            case CollectionLiteral ignored -> Shape.concrete(BuiltinContract.SEQUENCE);
        };
    }

    private Shape binary(Binary binary, Map<String, Integer> parameters, Map<String, Shape> locals,
                         List<EnumSet<BuiltinContract>> requirements,
                         Map<String, FunctionContract> visible) {
        Shape left = expression(binary.left(), parameters, locals, requirements, visible);
        Shape right = expression(binary.right(), parameters, locals, requirements, visible);
        return switch (binary.operator()) {
            case "+" -> plus(left, right);
            case "-", "*", "/", "%", "<", "<=", ">", ">=" -> {
                constrain(left, EnumSet.of(BuiltinContract.NUMBER), requirements, binary.left().span());
                constrain(right, EnumSet.of(BuiltinContract.NUMBER), requirements, binary.right().span());
                yield switch (binary.operator()) {
                    case "<", "<=", ">", ">=" -> Shape.concrete(BuiltinContract.BOOLEAN);
                    default -> Shape.concrete(BuiltinContract.NUMBER);
                };
            }
            case "and", "or" -> Shape.concrete(BuiltinContract.BOOLEAN);
            case "==", "!=" -> Shape.concrete(BuiltinContract.BOOLEAN);
            default -> Shape.unknown();
        };
    }

    private Shape plus(Shape left, Shape right) {
        if (left.guarantees().contains(BuiltinContract.STRING)
                || right.guarantees().contains(BuiltinContract.STRING)) {
            return Shape.concrete(BuiltinContract.STRING);
        }
        if (left.guarantees().contains(BuiltinContract.NUMBER)
                && right.guarantees().contains(BuiltinContract.NUMBER)) {
            return Shape.concrete(BuiltinContract.NUMBER);
        }
        // `+` is relational: an unresolved operand may be Number or String. Do not invent a
        // numeric constraint; later relational inference can specialize this further.
        return Shape.unknown();
    }

    private Shape ambiguousCall(AmbiguousCall call, Map<String, Integer> parameters,
                                Map<String, Shape> locals, List<EnumSet<BuiltinContract>> requirements,
                                Map<String, FunctionContract> visible) {
        if (call.first() instanceof Name first && visible.containsKey(first.name())
                && !visible.get(first.name()).parameterRequirements().isEmpty()) {
            return application(new Apply(new Apply(call.first(), call.middle(), call.span()),
                    call.last(), call.span()), parameters, locals, requirements, visible);
        }
        if (call.middle() instanceof Name middle && visible.containsKey(middle.name())
                && visible.get(middle.name()).parameterRequirements().size() == 2) {
            return application(new Apply(new Apply(call.middle(), call.first(), call.span()),
                    call.last(), call.span()), parameters, locals, requirements, visible);
        }
        expression(call.first(), parameters, locals, requirements, visible);
        expression(call.middle(), parameters, locals, requirements, visible);
        expression(call.last(), parameters, locals, requirements, visible);
        return Shape.unknown();
    }

    private Shape application(Apply application, Map<String, Integer> parameters, Map<String, Shape> locals,
                              List<EnumSet<BuiltinContract>> requirements,
                              Map<String, FunctionContract> visible) {
        ArrayList<Expr> arguments = new ArrayList<>();
        Expr target = application;
        while (target instanceof Apply apply) {
            arguments.addFirst(apply.argument());
            target = apply.function();
        }
        if (!(target instanceof Name name) || !visible.containsKey(name.name())) {
            for (Expr argument : arguments) expression(argument, parameters, locals, requirements, visible);
            return Shape.unknown();
        }
        FunctionContract called = visible.get(name.name());
        ArrayList<Shape> shapes = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            Shape shape = expression(arguments.get(i), parameters, locals, requirements, visible);
            shapes.add(shape);
            if (i < called.parameterRequirements().size()) {
                constrain(shape, called.parameterRequirements().get(i), requirements, arguments.get(i).span());
            }
        }
        if (arguments.size() != called.parameterRequirements().size()) return Shape.unknown();
        if (called.resultParameter() != null && called.resultParameter() < shapes.size()) {
            return shapes.get(called.resultParameter());
        }
        return called.generalizedResult() ? Shape.generic()
                : new Shape(enumSet(called.resultGuarantees()), null, false);
    }

    private static Shape literal(Value value) {
        return switch (ValueSemantics.descriptor(value)) {
            case NUMBER -> Shape.concrete(BuiltinContract.NUMBER);
            case STRING -> Shape.concrete(BuiltinContract.STRING);
            case BOOLEAN -> Shape.concrete(BuiltinContract.BOOLEAN);
            case NULL -> Shape.concrete(BuiltinContract.NULL);
            case MISSING -> Shape.concrete(BuiltinContract.MISSING);
            case FUNCTION -> Shape.concrete(BuiltinContract.FUNCTION);
            case SCOPE -> Shape.concrete(BuiltinContract.SCOPE);
            case SEQUENCE -> Shape.concrete(BuiltinContract.SEQUENCE);
            case DICTIONARY -> Shape.concrete(BuiltinContract.DICTIONARY);
            default -> Shape.unknown();
        };
    }

    private static EnumSet<BuiltinContract> clause(ContractClause clause) {
        EnumSet<BuiltinContract> result = EnumSet.noneOf(BuiltinContract.class);
        if (clause != null) {
            for (ContractName name : clause.names()) BuiltinContract.named(name.name())
                    .filter(contract -> contract != BuiltinContract.ANY).ifPresent(result::add);
        }
        return result;
    }

    private static void constrain(Shape shape, Set<BuiltinContract> constraints,
                                  List<EnumSet<BuiltinContract>> requirements, SourceSpan span) {
        if (constraints.isEmpty()) return;
        if (shape.parameter() != null) {
            EnumSet<BuiltinContract> target = requirements.get(shape.parameter());
            target.addAll(constraints);
            rejectDisjoint(target, span);
            return;
        }
        if (shape.generalized()) {
            shape.resolveWith(constraints);
            rejectDisjoint(shape.guarantees(), span);
            return;
        }
        if (!shape.guarantees().isEmpty() && constraints.stream().noneMatch(shape.guarantees()::contains)) {
            throw conflict(span, shape.guarantees(), constraints);
        }
    }

    private static void rejectDisjoint(Set<BuiltinContract> contracts, SourceSpan span) {
        if (contracts.size() > 1) throw conflict(span, contracts, contracts);
    }

    private static LangException conflict(SourceSpan span, Set<BuiltinContract> actual,
                                          Set<BuiltinContract> required) {
        return new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.INCOMPATIBLE_CONTRACTS,
                "Incompatible inferred contracts: " + names(actual) + " and " + names(required), span);
    }

    private static String names(Set<BuiltinContract> contracts) {
        return contracts.stream().map(BuiltinContract::publicName).sorted().toList().toString();
    }

    private static EnumSet<BuiltinContract> enumSet(Set<BuiltinContract> values) {
        return values.isEmpty() ? EnumSet.noneOf(BuiltinContract.class) : EnumSet.copyOf(values);
    }

    private static Map<String, CallableEffects> builtinEffects() {
        Map<String, CallableEffects> result = new HashMap<>();
        for (BuiltinContract contract : BuiltinContract.values()) {
            result.put(contract.publicName(), new CallableEffects(1, EffectSummary.PURE));
        }
        result.put("contract", new CallableEffects(1, EffectSummary.PURE));
        for (LanguageSyntax.BinaryOperator operator : LanguageSyntax.binaryOperators()) {
            result.put(operator.spelling(), new CallableEffects(2, EffectSummary.PURE));
        }
        result.put("print", new CallableEffects(1,
                new EffectSummary(Set.of(BuiltinEffect.OUTPUT), false)));
        result.put("type", new CallableEffects(1, EffectSummary.PURE));
        result.put("textSize", new CallableEffects(1, EffectSummary.PURE));
        result.put("textAt", new CallableEffects(2, EffectSummary.PURE));
        result.put("textSlice", new CallableEffects(3, EffectSummary.PURE));
        result.put("textNumber", new CallableEffects(1, EffectSummary.PURE));
        result.put("numberText", new CallableEffects(1, EffectSummary.PURE));
        result.put("seqEmpty", new CallableEffects(0, EffectSummary.PURE));
        result.put("seqAdd", new CallableEffects(2, EffectSummary.PURE));
        result.put("seqGet", new CallableEffects(2, EffectSummary.PURE));
        result.put("seqSize", new CallableEffects(1, EffectSummary.PURE));
        result.put("dictEmpty", new CallableEffects(0, EffectSummary.PURE));
        result.put("dictPut", new CallableEffects(3, EffectSummary.PURE));
        result.put("dictGet", new CallableEffects(2, EffectSummary.PURE));
        result.put("dictHas", new CallableEffects(2, EffectSummary.PURE));
        result.put("dictKeys", new CallableEffects(1, EffectSummary.PURE));
        result.put("assert", new CallableEffects(2,
                new EffectSummary(Set.of(BuiltinEffect.TEST_REPORT), false)));
        result.put("assertEqual", new CallableEffects(3,
                new EffectSummary(Set.of(BuiltinEffect.TEST_REPORT), false)));
        return Map.copyOf(result);
    }

    private EffectSummary inferEffects(List<Stmt> statements, Map<String, CallableEffects> enclosing) {
        Map<String, CallableEffects> visible = new HashMap<>(enclosing);
        List<FunctionDef> nested = statements.stream().filter(FunctionDef.class::isInstance)
                .map(FunctionDef.class::cast).toList();
        for (FunctionDef function : nested) {
            visible.put(function.name(), new CallableEffects(function.params().size(), EffectSummary.PURE));
        }
        boolean changed;
        int passes = Math.max(1, nested.size() * 4);
        do {
            changed = false;
            for (FunctionDef function : nested) {
                EffectSummary inferred = inferEffects(function.body(), visible);
                CallableEffects previous = visible.get(function.name());
                if (!inferred.equals(previous.summary())) {
                    visible.put(function.name(), new CallableEffects(function.params().size(), inferred));
                    changed = true;
                }
            }
        } while (changed && --passes > 0);

        EffectSummary result = EffectSummary.PURE;
        for (Stmt statement : statements) {
            result = result.plus(switch (statement) {
                case Assign assign -> expressionEffects(assign.value(), visible);
                case ExprStmt expression -> expressionEffects(expression.expression(), visible);
                case FunctionDef ignored -> EffectSummary.PURE;
            });
        }
        return result;
    }

    private EffectSummary expressionEffects(Expr expression, Map<String, CallableEffects> visible) {
        return switch (expression) {
            case Literal ignored -> EffectSummary.PURE;
            case Hole ignored -> EffectSummary.PURE;
            case Name name -> {
                CallableEffects callable = visible.get(name.name());
                yield callable != null && callable.arity() == 0 ? callable.summary() : EffectSummary.PURE;
            }
            case Group group -> expressionEffects(group.expression(), visible);
            case Unary unary -> expressionEffects(unary.operand(), visible);
            case Binary binary -> expressionEffects(binary.left(), visible)
                    .plus(expressionEffects(binary.right(), visible));
            case Conditional conditional -> expressionEffects(conditional.condition(), visible)
                    .plus(expressionEffects(conditional.whenTrue(), visible))
                    .plus(expressionEffects(conditional.whenFalse(), visible));
            case Apply apply -> applicationEffects(apply, visible);
            case NamedInfix infix -> applicationEffects(new Apply(
                    new Apply(infix.function(), infix.left(), infix.span()), infix.right(), infix.span()), visible);
            case AmbiguousCall call -> ambiguousCallEffects(call, visible);
            case Compose compose -> expressionEffects(compose.left(), visible)
                    .plus(expressionEffects(compose.right(), visible));
            case Field field -> expressionEffects(field.target(), visible);
            case DynamicField field -> expressionEffects(field.target(), visible)
                    .plus(expressionEffects(field.name(), visible));
            case Reflect reflect -> expressionEffects(reflect.target(), visible);
            case CollectionLiteral collection -> collection.elements().stream()
                    .map(element -> expressionEffects(element, visible))
                    .reduce(EffectSummary.PURE, EffectSummary::plus);
        };
    }

    private EffectSummary applicationEffects(Apply application, Map<String, CallableEffects> visible) {
        ArrayList<Expr> arguments = new ArrayList<>();
        Expr target = application;
        while (target instanceof Apply apply) {
            arguments.addFirst(apply.argument());
            target = apply.function();
        }
        EffectSummary result = EffectSummary.PURE;
        for (Expr argument : arguments) result = result.plus(expressionEffects(argument, visible));
        if (arguments.stream().anyMatch(ContractInference::containsHole)) return result;
        if (target instanceof Name name) {
            CallableEffects callable = visible.get(name.name());
            if (callable == null) return result.plus(EffectSummary.UNKNOWN);
            return arguments.size() >= callable.arity() ? result.plus(callable.summary()) : result;
        }
        return result.plus(expressionEffects(target, visible)).plus(EffectSummary.UNKNOWN);
    }

    private EffectSummary ambiguousCallEffects(AmbiguousCall call, Map<String, CallableEffects> visible) {
        if (call.first() instanceof Name first && visible.containsKey(first.name())
                && visible.get(first.name()).arity() > 0) {
            return applicationEffects(new Apply(new Apply(call.first(), call.middle(), call.span()),
                    call.last(), call.span()), visible);
        }
        if (call.middle() instanceof Name middle && visible.containsKey(middle.name())
                && visible.get(middle.name()).arity() == 2) {
            return applicationEffects(new Apply(new Apply(call.middle(), call.first(), call.span()),
                    call.last(), call.span()), visible);
        }
        return expressionEffects(call.first(), visible).plus(expressionEffects(call.middle(), visible))
                .plus(expressionEffects(call.last(), visible)).plus(EffectSummary.UNKNOWN);
    }

    private static void invalidRefinement(FunctionDef function, String reason) {
        throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.INVALID_REFINEMENT,
                "Invalid refinement predicate: " + function.name() + " " + reason, function.span());
    }

    private void analyzeOrdinaryBindings(List<Stmt> statements, Map<String, FunctionContract> visible) {
        Map<String, Shape> locals = new HashMap<>();
        List<Map.Entry<Assign, Shape>> pending = new ArrayList<>();
        for (Stmt statement : statements) {
            if (statement instanceof Assign assign) {
                Shape shape = expression(assign.value(), Map.of(), locals, List.of(), visible);
                constrain(shape, clause(assign.contracts()), List.of(), assign.value().span());
                locals.put(assign.name(), shape);
                pending.add(Map.entry(assign, shape));
            } else if (statement instanceof ExprStmt expression) {
                expression(expression.expression(), Map.of(), locals, List.of(), visible);
            }
        }
        for (Map.Entry<Assign, Shape> entry : pending) {
            Assign assign = entry.getKey();
            if (entry.getValue().generalized() && assign.contracts() == null && !containsHole(assign.value())) {
                throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.AMBIGUOUS_CONTRACT,
                        "Ambiguous contract at use: binding " + assign.name(), assign.value().span());
            }
        }
    }

    private static boolean containsHole(Expr expression) {
        return switch (expression) {
            case Hole ignored -> true;
            case Unary unary -> containsHole(unary.operand());
            case Binary binary -> containsHole(binary.left()) || containsHole(binary.right());
            case Compose compose -> containsHole(compose.left()) || containsHole(compose.right());
            case NamedInfix infix -> containsHole(infix.left()) || containsHole(infix.function())
                    || containsHole(infix.right());
            case AmbiguousCall call -> containsHole(call.first()) || containsHole(call.middle())
                    || containsHole(call.last());
            case Conditional conditional -> containsHole(conditional.condition())
                    || containsHole(conditional.whenTrue()) || containsHole(conditional.whenFalse());
            case Apply apply -> containsHole(apply.function()) || containsHole(apply.argument());
            case Field field -> containsHole(field.target());
            case DynamicField field -> containsHole(field.target()) || containsHole(field.name());
            case Reflect reflect -> containsHole(reflect.target());
            case Group group -> containsHole(group.expression());
            case CollectionLiteral collection -> collection.elements().stream().anyMatch(ContractInference::containsHole);
            case Literal ignored -> false;
            case Name ignored -> false;
        };
    }
}
