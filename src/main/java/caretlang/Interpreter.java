package caretlang;

import caretlang.Ast.*;

import java.io.PrintStream;
import java.util.*;

final class Interpreter {
    private final Environment globals = new Environment(null);
    private final PrintStream output;

    Interpreter() {
        this(System.out);
    }

    Interpreter(PrintStream output) {
        this(output, null);
    }

    Interpreter(PrintStream output, TestReporter testReporter) {
        this.output = output;
        installBuiltins();
        if (testReporter != null) installTestBuiltins(testReporter);
    }

    void execute(List<Stmt> program) {
        executeBlock(program, globals);
    }

    Value evalExpression(Expr expression) {
        return eval(expression, globals, null);
    }

    private Value executeBlock(List<Stmt> statements, Environment env) {
        LinkedHashMap<String, Value> exports = new LinkedHashMap<>();
        IdentityHashMap<FunctionDef, Value.FunctionValue> functions = prepareDeclarations(statements, env);
        Value last = Value.Missing.INSTANCE;

        for (Stmt statement : statements) {
            if (statement instanceof Assign(String name, boolean exported, Expr value1, SourceSpan ignored)) {
                Value value = eval(value1, env, null);
                env.define(name, value);
                if (exported) exports.put(name, value);
                last = value;
            } else if (statement instanceof ExprStmt(Expr expression, SourceSpan ignored)) {
                last = eval(expression, env, null);
            } else if (statement instanceof FunctionDef(String name, List<String> params, List<Stmt> body,
                                                        SourceSpan ignored)) {
                last = functions.get((FunctionDef) statement);
            }
        }

        return exports.isEmpty() ? last : new Value.Scope(exports);
    }

    private IdentityHashMap<FunctionDef, Value.FunctionValue> prepareDeclarations(List<Stmt> statements,
                                                                                   Environment env) {
        IdentityHashMap<FunctionDef, Value.FunctionValue> functions = new IdentityHashMap<>();
        LinkedHashMap<String, SourceSpan> declarations = new LinkedHashMap<>();
        for (Stmt statement : statements) {
            if (statement instanceof Assign(String name, boolean ignoredExport, Expr ignoredValue, SourceSpan span)) {
                SourceSpan original = declarations.putIfAbsent(name, span);
                if (original != null) duplicateDefinition(name, span, original);
            } else if (statement instanceof FunctionDef(String name, List<String> params, List<Stmt> ignoredBody,
                                                        SourceSpan span)) {
                SourceSpan original = declarations.putIfAbsent(name, span);
                if (original != null) duplicateDefinition(name, span, original);
                HashSet<String> parameterNames = new HashSet<>();
                for (String parameter : params) {
                    if (!parameterNames.add(parameter)) {
                        throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.DUPLICATE_PARAMETER,
                                "Duplicate parameter: " + parameter, span);
                    }
                }
            }
        }
        for (Stmt statement : statements) {
            if (statement instanceof FunctionDef function) declare(env, function.name(), function.span());
        }
        for (Stmt statement : statements) {
            if (statement instanceof FunctionDef function) {
                Value.FunctionValue value = new Value.FunctionValue(function.name(), function.params(), args -> {
                    Environment parameters = new Environment(env);
                    for (int i = 0; i < function.params().size(); i++) {
                        parameters.define(function.params().get(i), args.get(i));
                    }
                    return executeBlock(function.body(), new Environment(parameters));
                });
                env.initialize(function.name(), value);
                functions.put(function, value);
            }
        }
        return functions;
    }

    private void declare(Environment env, String name, SourceSpan span) {
        try {
            env.declare(name);
        } catch (LangException error) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.DUPLICATE_DEFINITION,
                    error.detail(), span);
        }
    }

    private void duplicateDefinition(String name, SourceSpan span, SourceSpan original) {
        throw new LangException(new Diagnostic(Diagnostic.Phase.RUNTIME,
                Diagnostic.Codes.DUPLICATE_DEFINITION, "Duplicate definition: " + name, span,
                List.of(new Diagnostic.Related("First definition of " + name, original))));
    }

    private Value eval(Expr expr, Environment env, List<Value> holeArgs) {
        if (holeArgs == null) {
            HoleAnalysis analysis = analyzeHoles(expr);
            if (!analysis.indexes().isEmpty()) {
                HoleShape shape = holeShape(expr, analysis.indexes());
                Expr captured = captureNonHoleParts(expr, env, analysis.containsHole());
                return new Value.HoleFunction(expr.toString(), shape.arity(),
                        supplied -> eval(captured, env, supplied));
            }
        }
        Expr resolved = holeArgs == null ? expr : bindHoles(expr, new HoleBinder(holeArgs));
        return evalInner(resolved, env);
    }

    private Value evalInner(Expr expr, Environment env) {
        try {
            return evalInnerUnchecked(expr, env);
        } catch (LangException error) {
            throw error.withSpanIfAbsent(expr.span());
        }
    }

    private Value evalInnerUnchecked(Expr expr, Environment env) {
        if (expr instanceof Literal(Value value1, SourceSpan ignored)) return value1;
        if (expr instanceof Name(String name2, SourceSpan ignored)) {
            Value value = env.get(name2);
            if (value instanceof Value.Callable callable && callable.remainingArity() == 0) {
                return ((Value.FunctionValue) callable).invokeZero();
            }
            return value;
        }
        if (expr instanceof Hole) {
            throw runtime(Diagnostic.Codes.INTERNAL_ERROR, "Internal error: unresolved hole");
        }
        if (expr instanceof Unary(String operator1, Expr operand, SourceSpan ignored)) {
            Value value = evalInner(operand, env);
            return switch (operator1) {
                case "-" -> finiteNumber(-number(value), "Numeric result is not finite");
                case "not" -> new Value.Bool(!truth(value));
                default -> throw runtime(Diagnostic.Codes.UNKNOWN_OPERATOR,
                        "Unknown unary operator: " + operator1);
            };
        }
        if (expr instanceof Binary(String operator, Expr left1, Expr right1, SourceSpan ignored)) {
            if (operator.equals("and")) {
                Value left = evalInner(left1, env);
                return truth(left) ? evalInner(right1, env) : new Value.Bool(false);
            }
            if (operator.equals("or")) {
                Value left = evalInner(left1, env);
                return truth(left) ? new Value.Bool(true) : new Value.Bool(truth(evalInner(right1, env)));
            }
            Value left = evalInner(left1, env);
            Value right = evalInner(right1, env);
            return binary(operator, left, right);
        }
        if (expr instanceof Conditional(Expr condition1, Expr whenTrue, Expr whenFalse, SourceSpan ignored)) {
            Value condition = evalInner(condition1, env);
            return truth(condition)
                    ? evalInner(whenTrue, env)
                    : evalInner(whenFalse, env);
        }
        if (expr instanceof Apply(Expr function, Expr argument1, SourceSpan ignored)) {
            Value fn = evalInner(function, env);
            Value argument = evalInner(argument1, env);
            if (!(fn instanceof Value.Callable callable)) {
                throw runtime(Diagnostic.Codes.NOT_CALLABLE, "Value is not callable: " + fn);
            }
            try {
                return callable.apply(argument, expr.span());
            } catch (StackOverflowError exhaustedStack) {
                throw runtime(Diagnostic.Codes.CALL_DEPTH_EXCEEDED,
                        "Maximum Caret call depth exceeded");
            }
        }
        if (expr instanceof Field(Expr target2, String field, boolean optional1, SourceSpan ignored)) {
            Value target = evalInner(target2, env);
            return field(target, field, optional1);
        }
        if (expr instanceof DynamicField(Expr target1, Expr name1, boolean optional, SourceSpan ignored)) {
            Value target = evalInner(target1, env);
            Value name = evalInner(name1, env);
            String fieldName = switch (name) {
                case Value.Name n -> n.value();
                case Value.Str text -> text.value();
                default -> throw runtime(Diagnostic.Codes.INVALID_DYNAMIC_FIELD_NAME,
                        "Dynamic field name must be a name or string, got: " + name);
            };
            return field(target, fieldName, optional);
        }
        if (expr instanceof Reflect(Expr target, SourceSpan ignored)) {
            // Reflection of a name observes the binding itself. In particular,
            // this is the escape hatch for referring to a zero-argument function
            // without triggering the normal implicit invocation on name reads.
            Value targetValue = target instanceof Name(String name, SourceSpan ignoredNameSpan)
                    ? env.get(name)
                    : evalInner(target, env);
            return reflect(targetValue);
        }
        if (expr instanceof Group(Expr expression, SourceSpan ignored)) {
            return evalInner(expression, env);
        }
        throw runtime(Diagnostic.Codes.INTERNAL_ERROR, "Unknown expression: " + expr);
    }

    private Value binary(String op, Value left, Value right) {
        return switch (op) {
            case "+" -> {
                if (left instanceof Value.Str || right instanceof Value.Str)
                    yield new Value.Str(left.toString() + right);
                yield finiteNumber(number(left) + number(right), "Numeric result is not finite");
            }
            case "-" -> finiteNumber(number(left) - number(right), "Numeric result is not finite");
            case "*" -> finiteNumber(number(left) * number(right), "Numeric result is not finite");
            case "/" -> {
                double divisor = number(right);
                if (divisor == 0.0) {
                    throw runtime(Diagnostic.Codes.DIVISION_BY_ZERO, "Division by zero");
                }
                yield finiteNumber(number(left) / divisor, "Numeric result is not finite");
            }
            case "%" -> {
                double divisor = number(right);
                if (divisor == 0.0) {
                    throw runtime(Diagnostic.Codes.DIVISION_BY_ZERO, "Division by zero");
                }
                yield finiteNumber(number(left) % divisor, "Numeric result is not finite");
            }
            case ">" -> new Value.Bool(number(left) > number(right));
            case ">=" -> new Value.Bool(number(left) >= number(right));
            case "<" -> new Value.Bool(number(left) < number(right));
            case "<=" -> new Value.Bool(number(left) <= number(right));
            case "==" -> new Value.Bool(equalsValue(left, right));
            case "!=" -> new Value.Bool(!equalsValue(left, right));
            default -> throw runtime(Diagnostic.Codes.UNKNOWN_OPERATOR, "Unknown operator: " + op);
        };
    }

    private boolean equalsValue(Value a, Value b) {
        if (a instanceof Value.Callable || b instanceof Value.Callable) {
            throw runtime(Diagnostic.Codes.CALLABLE_EQUALITY,
                    "Callable values cannot be compared for equality");
        }
        if (a instanceof Value.Num(double value1) && b instanceof Value.Num(double value)) return value1 == value;
        return Objects.equals(a, b);
    }

    private double number(Value value) {
        if (value instanceof Value.Num(double value1)) return value1;
        throw runtime(Diagnostic.Codes.EXPECTED_NUMBER, "Expected number, got: " + value);
    }

    private Value.Num finiteNumber(double value, String message) {
        if (!Double.isFinite(value)) throw runtime(Diagnostic.Codes.NON_FINITE_RESULT, message);
        return new Value.Num(value);
    }

    private boolean truth(Value value) {
        if (value instanceof Value.Bool(boolean value1)) return value1;
        if (value == Value.Null.INSTANCE || value == Value.Missing.INSTANCE) return false;
        throw runtime(Diagnostic.Codes.INVALID_CONDITION,
                "Condition must be Boolean, null, or missing; got: " + value);
    }

    private void installBuiltins() {
        globals.define("print", new Value.FunctionValue("print", List.of("value"), args -> {
            output.println(args.getFirst());
            return args.getFirst();
        }));

        globals.define("type", new Value.FunctionValue("type", List.of("value"), args -> new Value.Str(kindOf(args.getFirst()))));

        globals.define("textSize", function("textSize", List.of("text"), args ->
                new Value.Num(text(args.getFirst()).codePointCount(0, text(args.getFirst()).length()))));
        globals.define("textAt", function("textAt", List.of("text", "index"), args -> {
            String value = text(args.get(0));
            OptionalInt index = index(args.get(1));
            int size = value.codePointCount(0, value.length());
            if (index.isEmpty() || index.getAsInt() >= size) return Value.Missing.INSTANCE;
            int offset = value.offsetByCodePoints(0, index.getAsInt());
            return new Value.Str(new String(Character.toChars(value.codePointAt(offset))));
        }));
        globals.define("textSlice", function("textSlice", List.of("text", "start", "end"), args -> {
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
        globals.define("textNumber", function("textNumber", List.of("text"), args -> {
            try {
                double number = Double.parseDouble(text(args.getFirst()));
                return Double.isFinite(number) ? new Value.Num(number) : Value.Missing.INSTANCE;
            } catch (NumberFormatException ignored) {
                return Value.Missing.INSTANCE;
            }
        }));
        globals.define("numberText", function("numberText", List.of("number"), args ->
                new Value.Str(new Value.Num(number(args.getFirst())).toString())));

        globals.define("seqEmpty", function("seqEmpty", List.of(), args -> new Value.Seq(List.of())));
        globals.define("seqAdd", function("seqAdd", List.of("sequence", "value"), args ->
                sequence(args.getFirst()).appended(args.get(1))));
        globals.define("seqGet", function("seqGet", List.of("sequence", "index"), args -> {
            List<Value> values = sequence(args.get(0)).values();
            OptionalInt index = index(args.get(1));
            return index.isPresent() && index.getAsInt() < values.size()
                    ? values.get(index.getAsInt()) : Value.Missing.INSTANCE;
        }));
        globals.define("seqSize", function("seqSize", List.of("sequence"), args ->
                new Value.Num(sequence(args.getFirst()).values().size())));

        globals.define("dictEmpty", function("dictEmpty", List.of(), args -> new Value.Dict(Map.of())));
        globals.define("dictPut", function("dictPut", List.of("dictionary", "key", "value"), args ->
                dictionary(args.getFirst()).put(requiredDictionaryKey(args.get(1)), args.get(2))));
        globals.define("dictGet", function("dictGet", List.of("dictionary", "key"), args -> {
            String key = dictionaryKey(args.get(1));
            return key == null ? Value.Missing.INSTANCE
                    : dictionary(args.get(0)).find(key).orElse(Value.Missing.INSTANCE);
        }));
        globals.define("dictHas", function("dictHas", List.of("dictionary", "key"), args -> {
            String key = dictionaryKey(args.get(1));
            return new Value.Bool(key != null && dictionary(args.get(0)).entries().containsKey(key));
        }));
        globals.define("dictKeys", function("dictKeys", List.of("dictionary"), args -> new Value.Seq(
                dictionary(args.getFirst()).entries().keySet().stream().map(Value.Name::new).toList())));
    }

    private void installTestBuiltins(TestReporter reporter) {
        globals.define("assert", new Value.FunctionValue("assert", List.of("name", "condition"),
                (args, span) -> {
                    String name = text(args.get(0));
                    if (!(args.get(1) instanceof Value.Bool condition)) {
                        throw runtime(Diagnostic.Codes.INVALID_ASSERTION,
                                "Assertion condition must be Boolean, got: " + args.get(1));
                    }
                    reporter.record(name, condition, new Value.Bool(true), condition.value(), span);
                    return Value.Missing.INSTANCE;
                }));
        globals.define("assertEqual", new Value.FunctionValue("assertEqual", List.of("name", "actual", "expected"),
                (args, span) -> {
                    String name = text(args.get(0));
                    Value actual = args.get(1);
                    Value expected = args.get(2);
                    reporter.record(name, actual, expected, equalsValue(actual, expected), span);
                    return Value.Missing.INSTANCE;
                }));
    }

    private Value.FunctionValue function(String name, List<String> parameters,
                                         java.util.function.Function<List<Value>, Value> implementation) {
        return new Value.FunctionValue(name, parameters, implementation);
    }

    private String text(Value value) {
        if (value instanceof Value.Str(String text)) return text;
        throw runtime(Diagnostic.Codes.EXPECTED_STRING, "Expected string, got: " + value);
    }

    private Value.Seq sequence(Value value) {
        if (value instanceof Value.Seq sequence) return sequence;
        throw runtime(Diagnostic.Codes.EXPECTED_SEQUENCE, "Expected sequence, got: " + value);
    }

    private Value.Dict dictionary(Value value) {
        if (value instanceof Value.Dict dictionary) return dictionary;
        throw runtime(Diagnostic.Codes.EXPECTED_DICTIONARY, "Expected dictionary, got: " + value);
    }

    private OptionalInt index(Value value) {
        if (!(value instanceof Value.Num(double number)) || !Double.isFinite(number)
                || number < 0 || number != Math.rint(number) || number > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) number);
    }

    private String dictionaryKey(Value value) {
        return switch (value) {
            case Value.Name name -> name.value();
            case Value.Str text -> text.value();
            default -> null;
        };
    }

    private String requiredDictionaryKey(Value value) {
        String key = dictionaryKey(value);
        if (key == null) {
            throw runtime(Diagnostic.Codes.INVALID_DICTIONARY_KEY,
                    "Dictionary key must be a name or string, got: " + value);
        }
        return key;
    }

    private Value field(Value target, String name, boolean optional) {
        if (!(target instanceof Value.Scope scope)) {
            throw runtime(Diagnostic.Codes.INVALID_FIELD_TARGET,
                    "Field access requires a scope, got: " + target);
        }
        Optional<Value> value = scope.find(name);
        if (value.isPresent()) return value.get();
        if (optional) return Value.Missing.INSTANCE;
        throw runtime(Diagnostic.Codes.MISSING_FIELD, "Scope has no exported binding: " + name);
    }

    private Value reflect(Value value) {
        LinkedHashMap<String, Value> metadata = new LinkedHashMap<>();
        switch (value) {
            case Value.Num ignored -> { }
            case Value.Str ignored -> { }
            case Value.Bool ignored -> { }
            case Value.Name ignored -> { }
            case Value.Null ignored -> { }
            case Value.Missing ignored -> { }
            case Value.Scope scope -> {
                metadata.put("size", new Value.Num(scope.fields().size()));
                metadata.put("names", new Value.Str(String.join(",", scope.fields().keySet())));
            }
            case Value.Seq sequence -> metadata.put("size", new Value.Num(sequence.values().size()));
            case Value.Dict dictionary -> {
                metadata.put("size", new Value.Num(dictionary.entries().size()));
                metadata.put("names", new Value.Str(String.join(",", dictionary.entries().keySet())));
            }
            case Value.Callable callable -> metadata.put("remaining", new Value.Num(callable.remainingArity()));
        }
        metadata.putFirst("kind", new Value.Str(kindOf(value)));
        return new Value.Scope(metadata);
    }

    private String kindOf(Value value) {
        return switch (value) {
            case Value.Num ignored -> "Number";
            case Value.Str ignored -> "String";
            case Value.Bool ignored -> "Boolean";
            case Value.Name ignored -> "Name";
            case Value.Null ignored -> "Null";
            case Value.Missing ignored -> "Missing";
            case Value.Scope ignored -> "Scope";
            case Value.Seq ignored -> "Sequence";
            case Value.Dict ignored -> "Dictionary";
            case Value.Callable ignored -> "Function";
        };
    }

    private record HoleShape(int arity, boolean numbered) {}
    private record HoleAnalysis(List<Integer> indexes, IdentityHashMap<Expr, Boolean> containsHole) {}

    private HoleShape holeShape(Expr expr, List<Integer> indexes) {
        boolean numbered = indexes.stream().anyMatch(index -> index > 0);
        boolean ordinary = indexes.stream().anyMatch(index -> index == 0);
        if (numbered && ordinary) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.MIXED_HOLE_STYLES,
                    "Cannot mix numbered and unnumbered holes", expr.span());
        }
        int arity = numbered ? indexes.stream().mapToInt(Integer::intValue).max().orElseThrow()
                : indexes.size();
        return new HoleShape(arity, numbered);
    }

    private HoleAnalysis analyzeHoles(Expr expr) {
        ArrayList<Integer> indexes = new ArrayList<>();
        IdentityHashMap<Expr, Boolean> containsHole = new IdentityHashMap<>();
        markHoles(expr, indexes, containsHole);
        return new HoleAnalysis(List.copyOf(indexes), containsHole);
    }

    private boolean markHoles(Expr expr, List<Integer> indexes,
                              IdentityHashMap<Expr, Boolean> containsHole) {
        boolean found = switch (expr) {
            case Hole hole -> {
                indexes.add(hole.index());
                yield true;
            }
            case Literal ignored -> false;
            case Name ignored -> false;
            case Unary unary -> markHoles(unary.operand(), indexes, containsHole);
            case Binary binary -> markHoles(binary.left(), indexes, containsHole)
                    | markHoles(binary.right(), indexes, containsHole);
            case Conditional conditional -> markHoles(conditional.condition(), indexes, containsHole)
                    | markHoles(conditional.whenTrue(), indexes, containsHole)
                    | markHoles(conditional.whenFalse(), indexes, containsHole);
            case Apply apply -> markHoles(apply.function(), indexes, containsHole)
                    | markHoles(apply.argument(), indexes, containsHole);
            case Field field -> markHoles(field.target(), indexes, containsHole);
            case DynamicField field -> markHoles(field.target(), indexes, containsHole)
                    | markHoles(field.name(), indexes, containsHole);
            case Reflect reflect -> markHoles(reflect.target(), indexes, containsHole);
            case Group group -> markHoles(group.expression(), indexes, containsHole);
        };
        containsHole.put(expr, found);
        return found;
    }

    private Expr captureNonHoleParts(Expr expr, Environment env,
                                     IdentityHashMap<Expr, Boolean> containsHole) {
        if (!containsHole.get(expr)) return new Literal(evalInner(expr, env), expr.span());
        return switch (expr) {
            case Hole hole -> hole;
            case Literal literal -> literal;
            case Name name -> name;
            case Unary unary -> new Unary(unary.operator(),
                    captureNonHoleParts(unary.operand(), env, containsHole), unary.span());
            case Binary binary -> new Binary(binary.operator(),
                    captureNonHoleParts(binary.left(), env, containsHole),
                    captureNonHoleParts(binary.right(), env, containsHole), binary.span());
            case Conditional conditional -> new Conditional(
                    captureNonHoleParts(conditional.condition(), env, containsHole),
                    captureNonHoleParts(conditional.whenTrue(), env, containsHole),
                    captureNonHoleParts(conditional.whenFalse(), env, containsHole), conditional.span());
            case Apply apply -> new Apply(captureNonHoleParts(apply.function(), env, containsHole),
                    captureNonHoleParts(apply.argument(), env, containsHole), apply.span());
            case Field field -> new Field(captureNonHoleParts(field.target(), env, containsHole),
                    field.field(), field.optional(), field.span());
            case DynamicField field -> new DynamicField(
                    captureNonHoleParts(field.target(), env, containsHole),
                    captureNonHoleParts(field.name(), env, containsHole), field.optional(), field.span());
            case Reflect reflect -> new Reflect(captureNonHoleParts(reflect.target(), env, containsHole),
                    reflect.span());
            case Group group -> new Group(captureNonHoleParts(group.expression(), env, containsHole), group.span());
        };
    }

    private Expr bindHoles(Expr expr, HoleBinder holes) {
        return switch (expr) {
            case Hole hole -> new Literal(hole.index() == 0 ? holes.next() : holes.at(hole.index()), hole.span());
            case Literal literal -> literal;
            case Name name -> name;
            case Unary unary -> new Unary(unary.operator(), bindHoles(unary.operand(), holes), unary.span());
            case Binary binary -> new Binary(binary.operator(), bindHoles(binary.left(), holes),
                    bindHoles(binary.right(), holes), binary.span());
            case Conditional conditional -> new Conditional(bindHoles(conditional.condition(), holes),
                    bindHoles(conditional.whenTrue(), holes), bindHoles(conditional.whenFalse(), holes),
                    conditional.span());
            case Apply apply -> new Apply(bindHoles(apply.function(), holes), bindHoles(apply.argument(), holes),
                    apply.span());
            case Field field -> new Field(bindHoles(field.target(), holes), field.field(), field.optional(),
                    field.span());
            case DynamicField field -> new DynamicField(bindHoles(field.target(), holes),
                    bindHoles(field.name(), holes), field.optional(), field.span());
            case Reflect reflect -> new Reflect(bindHoles(reflect.target(), holes), reflect.span());
            case Group group -> new Group(bindHoles(group.expression(), holes), group.span());
        };
    }

    private static final class HoleBinder {
        private final List<Value> values;
        private int index;
        private HoleBinder(List<Value> values) { this.values = values; }
        Value next() {
            if (index >= values.size()) {
                throw runtime(Diagnostic.Codes.INTERNAL_ERROR,
                        "Not enough arguments for partial expression");
            }
            return values.get(index++);
        }
        Value at(int oneBasedIndex) {
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
}
