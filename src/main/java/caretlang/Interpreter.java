package caretlang;

import caretlang.Ast.*;

import java.io.PrintStream;
import java.util.*;

final class Interpreter {
    private static final int MAX_CALL_DEPTH = 1_000;
    private final Environment globals = new Environment(null);
    private final PrintStream output;
    private int callDepth;

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
        int checkpoint = globals.checkpoint();
        try {
            Resolution resolution = Resolver.resolve(program, globals);
            executeBlock(program, globals, resolution);
        } catch (RuntimeException | Error failure) {
            globals.rollbackTo(checkpoint);
            throw failure;
        }
    }

    Value evalExpression(Expr expression) {
        Resolution resolution = Resolver.resolve(List.of(new ExprStmt(expression, expression.span())), globals);
        return eval(expression, globals, null, resolution);
    }

    private Value executeBlock(List<Stmt> statements, Environment env, Resolution resolution) {
        LinkedHashMap<String, Value> exports = new LinkedHashMap<>();
        IdentityHashMap<FunctionDef, Value.FunctionValue> functions = prepareDeclarations(statements, env, resolution);
        Value last = Value.Missing.INSTANCE;

        for (Stmt statement : statements) {
            if (statement instanceof Assign(String name, boolean exported, Expr value1, SourceSpan ignored)) {
                Value value = eval(value1, env, null, resolution);
                env.initialize(name, value);
                if (exported) exports.put(name, value);
                last = value;
            } else if (statement instanceof ExprStmt(Expr expression, SourceSpan ignored)) {
                last = eval(expression, env, null, resolution);
            } else if (statement instanceof FunctionDef(String name, List<String> params, List<Stmt> body,
                                                        SourceSpan ignored)) {
                last = functions.get((FunctionDef) statement);
            }
        }

        return exports.isEmpty() ? last : new Value.Scope(exports);
    }

    private IdentityHashMap<FunctionDef, Value.FunctionValue> prepareDeclarations(List<Stmt> statements,
                                                                                   Environment env,
                                                                                   Resolution resolution) {
        IdentityHashMap<FunctionDef, Value.FunctionValue> functions = new IdentityHashMap<>();
        for (Stmt statement : statements) {
            if (statement instanceof Assign assign) declare(env, assign.name(), assign.span());
            else if (statement instanceof FunctionDef function) declare(env, function.name(), function.span());
        }
        for (Stmt statement : statements) {
            if (statement instanceof FunctionDef function) {
                Value.FunctionValue value = new Value.FunctionValue(function.name(), function.params(), args -> {
                    Environment parameters = new Environment(env);
                    for (int i = 0; i < function.params().size(); i++) {
                        parameters.define(function.params().get(i), args.get(i));
                    }
                    return executeBlock(function.body(), new Environment(parameters), resolution);
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

    private Value eval(Expr expr, Environment env, List<Value> holeArgs, Resolution resolution) {
        try {
            if (holeArgs == null) {
                HoleAnalysis analysis = analyzeHoles(expr);
                if (!analysis.indexes().isEmpty()) {
                    HoleShape shape = holeShape(expr, analysis.indexes());
                    Expr captured = captureNonHoleParts(expr, env, analysis.containsHole(), resolution);
                    return new Value.HoleFunction(expr.toString(), shape.arity(),
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

    private Value evalInnerUnchecked(Expr expr, Environment env, Resolution resolution) {
        if (expr instanceof Literal(Value value1, SourceSpan ignored)) return value1;
        if (expr instanceof Name nameExpression) {
            Resolution.Binding binding = resolution.binding(nameExpression);
            Value value = binding == null ? env.get(nameExpression.name())
                    : env.getAt(binding.lexicalDepth(), binding.slot());
            if (value instanceof Value.Callable callable && callable.remainingArity() == 0) {
                return invokeZero(callable, expr.span());
            }
            return value;
        }
        if (expr instanceof Hole) {
            throw runtime(Diagnostic.Codes.INTERNAL_ERROR, "Internal error: unresolved hole");
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
            return binary(operator, left, right);
        }
        if (expr instanceof Conditional(Expr condition1, Expr whenTrue, Expr whenFalse, SourceSpan ignored)) {
            Value condition = evalInner(condition1, env, resolution);
            return truth(condition)
                    ? evalInner(whenTrue, env, resolution)
                    : evalInner(whenFalse, env, resolution);
        }
        if (expr instanceof Apply(Expr function, Expr argument1, SourceSpan ignored)) {
            Value fn = evalInner(function, env, resolution);
            Value argument = evalInner(argument1, env, resolution);
            if (!(fn instanceof Value.Callable callable)) {
                throw runtime(Diagnostic.Codes.NOT_CALLABLE, "Value is not callable: " + fn);
            }
            return invoke(callable, argument, expr.span());
        }
        if (expr instanceof Field(Expr target2, String field, boolean optional1, SourceSpan ignored)) {
            Value target = evalInner(target2, env, resolution);
            return field(target, field, optional1);
        }
        if (expr instanceof DynamicField(Expr target1, Expr name1, boolean optional, SourceSpan ignored)) {
            Value target = evalInner(target1, env, resolution);
            Value name = evalInner(name1, env, resolution);
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
        if (expr instanceof Group(Expr expression, SourceSpan ignored)) {
            return evalInner(expression, env, resolution);
        }
        throw runtime(Diagnostic.Codes.INTERNAL_ERROR, "Unknown expression: " + expr);
    }

    private Value invoke(Value.Callable callable, Value argument, SourceSpan span) {
        return withinCallDepth(span, () -> callable.apply(argument, span));
    }

    private Value invokeZero(Value.Callable callable, SourceSpan span) {
        if (!(callable instanceof Value.FunctionValue function)) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                    "Unsupported zero-argument callable", span);
        }
        return withinCallDepth(span, function::invokeZero);
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
                                     IdentityHashMap<Expr, Boolean> containsHole, Resolution resolution) {
        return AstRewriter.rewrite(expr, candidate -> !containsHole.get(candidate)
                ? Optional.of(new Literal(evalInner(candidate, env, resolution), candidate.span()))
                : Optional.empty());
    }

    private Expr bindHoles(Expr expr, HoleBinder holes) {
        return AstRewriter.rewrite(expr, candidate -> candidate instanceof Hole(int index, SourceSpan span)
                ? Optional.of(new Literal(index == 0 ? holes.next() : holes.at(index), span))
                : Optional.empty());
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
