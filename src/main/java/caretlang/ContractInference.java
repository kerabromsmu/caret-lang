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
    record FunctionContract(List<Set<BuiltinContract>> parameterRequirements,
                            Set<BuiltinContract> resultGuarantees,
                            Integer resultParameter, boolean generalizedResult) {
        FunctionContract {
            parameterRequirements = parameterRequirements.stream().map(Set::copyOf).toList();
            resultGuarantees = Set.copyOf(resultGuarantees);
        }
    }

    private record Shape(EnumSet<BuiltinContract> guarantees, Integer parameter, boolean generalized) {
        static Shape unknown() { return new Shape(EnumSet.noneOf(BuiltinContract.class), null, false); }
        static Shape generic() { return new Shape(EnumSet.noneOf(BuiltinContract.class), null, true); }
        static Shape concrete(BuiltinContract contract) { return new Shape(EnumSet.of(contract), null, false); }
        static Shape parameter(int index) { return new Shape(EnumSet.noneOf(BuiltinContract.class), index, false); }
    }

    private final IdentityHashMap<FunctionDef, FunctionContract> contracts = new IdentityHashMap<>();

    static ContractInference analyze(List<Stmt> program) {
        ContractInference inference = new ContractInference();
        inference.analyzeBlock(program, Map.of());
        return inference;
    }

    FunctionContract contract(FunctionDef function) { return contracts.get(function); }

    private void analyzeBlock(List<Stmt> statements, Map<String, FunctionContract> enclosing) {
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

        for (FunctionDef function : definitions.values()) {
            FunctionContract inferred = visible.get(function.name());
            contracts.put(function, inferred);
            analyzeBlock(function.body(), visible);
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
                constrain(operand, EnumSet.of(BuiltinContract.NUMBER), requirements, unary.span());
                yield Shape.concrete(BuiltinContract.NUMBER);
            }
            case Binary binary -> binary(binary, parameters, locals, requirements, visible);
            case Conditional conditional -> {
                Shape condition = expression(conditional.condition(), parameters, locals, requirements, visible);
                constrain(condition, EnumSet.of(BuiltinContract.BOOLEAN), requirements, conditional.condition().span());
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
            case AmbiguousCall ignored -> Shape.unknown();
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
            case "+", "-", "*", "/", "%", "<", "<=", ">", ">=" -> {
                constrain(left, EnumSet.of(BuiltinContract.NUMBER), requirements, binary.left().span());
                constrain(right, EnumSet.of(BuiltinContract.NUMBER), requirements, binary.right().span());
                yield switch (binary.operator()) {
                    case "<", "<=", ">", ">=" -> Shape.concrete(BuiltinContract.BOOLEAN);
                    default -> Shape.concrete(BuiltinContract.NUMBER);
                };
            }
            case "and", "or" -> {
                constrain(left, EnumSet.of(BuiltinContract.BOOLEAN), requirements, binary.left().span());
                constrain(right, EnumSet.of(BuiltinContract.BOOLEAN), requirements, binary.right().span());
                yield Shape.concrete(BuiltinContract.BOOLEAN);
            }
            case "==", "!=" -> Shape.concrete(BuiltinContract.BOOLEAN);
            default -> Shape.unknown();
        };
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

    private void analyzeOrdinaryBindings(List<Stmt> statements, Map<String, FunctionContract> visible) {
        Map<String, Shape> locals = new HashMap<>();
        for (Stmt statement : statements) {
            if (!(statement instanceof Assign assign)) continue;
            Shape shape = expression(assign.value(), Map.of(), locals, List.of(), visible);
            constrain(shape, clause(assign.contracts()), List.of(), assign.value().span());
            if (shape.generalized() && assign.contracts() == null && !containsHole(assign.value())) {
                throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.AMBIGUOUS_CONTRACT,
                        "Ambiguous contract at use: binding " + assign.name(), assign.value().span());
            }
            locals.put(assign.name(), shape);
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
