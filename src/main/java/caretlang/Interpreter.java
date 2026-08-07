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
        this.output = output;
        installBuiltins();
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
        HashSet<String> declarations = new HashSet<>();
        for (Stmt statement : statements) {
            if (statement instanceof Assign(String name, boolean ignoredExport, Expr ignoredValue, SourceSpan span)) {
                if (!declarations.add(name)) duplicateDefinition(name, span);
            } else if (statement instanceof FunctionDef(String name, List<String> params, List<Stmt> ignoredBody,
                                                        SourceSpan span)) {
                if (!declarations.add(name)) duplicateDefinition(name, span);
                HashSet<String> parameterNames = new HashSet<>();
                for (String parameter : params) {
                    if (!parameterNames.add(parameter)) {
                        throw new LangException(Diagnostic.Phase.RUNTIME, "DUPLICATE_PARAMETER",
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
            throw new LangException(Diagnostic.Phase.RUNTIME, "DUPLICATE_DEFINITION",
                    error.detail(), span);
        }
    }

    private void duplicateDefinition(String name, SourceSpan span) {
        throw new LangException(Diagnostic.Phase.RUNTIME, "DUPLICATE_DEFINITION",
                "Duplicate definition: " + name, span);
    }

    private Value eval(Expr expr, Environment env, List<Value> holeArgs) {
        if (containsHole(expr) && holeArgs == null) {
            HoleShape shape = holeShape(expr);
            Expr captured = captureNonHoleParts(expr, env);
            return new Value.HoleFunction(expr.toString(), shape.arity(),
                    supplied -> eval(captured, env, supplied));
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
            throw new LangException("Internal error: unresolved hole");
        }
        if (expr instanceof Unary(String operator1, Expr operand, SourceSpan ignored)) {
            Value value = evalInner(operand, env);
            return switch (operator1) {
                case "-" -> finiteNumber(-number(value), "Numeric result is not finite");
                case "not" -> new Value.Bool(!truth(value));
                default -> throw new LangException("Unknown unary operator: " + operator1);
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
                throw new LangException("Value is not callable: " + fn);
            }
            return callable.apply(argument);
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
                default -> throw new LangException("Dynamic field name must be a name or string, got: " + name);
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
        throw new LangException("Unknown expression: " + expr);
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
                if (divisor == 0.0) throw new LangException("Division by zero");
                yield finiteNumber(number(left) / divisor, "Numeric result is not finite");
            }
            case "%" -> {
                double divisor = number(right);
                if (divisor == 0.0) throw new LangException("Division by zero");
                yield finiteNumber(number(left) % divisor, "Numeric result is not finite");
            }
            case ">" -> new Value.Bool(number(left) > number(right));
            case ">=" -> new Value.Bool(number(left) >= number(right));
            case "<" -> new Value.Bool(number(left) < number(right));
            case "<=" -> new Value.Bool(number(left) <= number(right));
            case "==" -> new Value.Bool(equalsValue(left, right));
            case "!=" -> new Value.Bool(!equalsValue(left, right));
            default -> throw new LangException("Unknown operator: " + op);
        };
    }

    private boolean equalsValue(Value a, Value b) {
        if (a instanceof Value.Callable || b instanceof Value.Callable) {
            throw new LangException("Callable values cannot be compared for equality");
        }
        if (a instanceof Value.Num(double value1) && b instanceof Value.Num(double value)) return value1 == value;
        return Objects.equals(a, b);
    }

    private double number(Value value) {
        if (value instanceof Value.Num(double value1)) return value1;
        throw new LangException("Expected number, got: " + value);
    }

    private Value.Num finiteNumber(double value, String message) {
        if (!Double.isFinite(value)) throw new LangException(message);
        return new Value.Num(value);
    }

    private boolean truth(Value value) {
        if (value instanceof Value.Bool(boolean value1)) return value1;
        if (value == Value.Null.INSTANCE || value == Value.Missing.INSTANCE) return false;
        throw new LangException("Condition must be Boolean, null, or missing; got: " + value);
    }

    private void installBuiltins() {
        globals.define("print", new Value.FunctionValue("print", List.of("value"), args -> {
            output.println(args.getFirst());
            return args.getFirst();
        }));

        globals.define("type", new Value.FunctionValue("type", List.of("value"), args -> {
            Value v = args.getFirst();
            String name = switch (v) {
                case Value.Num ignored -> "Number";
                case Value.Str ignored -> "String";
                case Value.Bool ignored -> "Boolean";
                case Value.Name ignored -> "Name";
                case Value.Null ignored -> "Nullable";
                case Value.Missing ignored -> "Missing";
                case Value.Scope ignored -> "Scope";
                case Value.Seq ignored -> "Sequence";
                case Value.Dict ignored -> "Dictionary";
                case Value.Callable ignored -> "Function";
            };
            return new Value.Str(name);
        }));

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
                sequence(args.get(0)).appended(args.get(1))));
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
                dictionary(args.get(0)).put(requiredDictionaryKey(args.get(1)), args.get(2))));
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

    private Value.FunctionValue function(String name, List<String> parameters,
                                         java.util.function.Function<List<Value>, Value> implementation) {
        return new Value.FunctionValue(name, parameters, implementation);
    }

    private String text(Value value) {
        if (value instanceof Value.Str(String text)) return text;
        throw new LangException("Expected string, got: " + value);
    }

    private Value.Seq sequence(Value value) {
        if (value instanceof Value.Seq sequence) return sequence;
        throw new LangException("Expected sequence, got: " + value);
    }

    private Value.Dict dictionary(Value value) {
        if (value instanceof Value.Dict dictionary) return dictionary;
        throw new LangException("Expected dictionary, got: " + value);
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
        if (key == null) throw new LangException("Dictionary key must be a name or string, got: " + value);
        return key;
    }

    private Value field(Value target, String name, boolean optional) {
        if (!(target instanceof Value.Scope scope)) {
            throw new LangException("Field access requires a scope, got: " + target);
        }
        Optional<Value> value = scope.find(name);
        if (value.isPresent()) return value.get();
        if (optional) return Value.Missing.INSTANCE;
        throw new LangException("Scope has no exported binding: " + name);
    }

    private Value reflect(Value value) {
        LinkedHashMap<String, Value> metadata = new LinkedHashMap<>();
        String kind;
        switch (value) {
            case Value.Num ignored -> kind = "Number";
            case Value.Str ignored -> kind = "String";
            case Value.Bool ignored -> kind = "Boolean";
            case Value.Name ignored -> kind = "Name";
            case Value.Null ignored -> kind = "Null";
            case Value.Missing ignored -> kind = "Missing";
            case Value.Scope scope -> {
                kind = "Scope";
                metadata.put("size", new Value.Num(scope.fields().size()));
                metadata.put("names", new Value.Str(String.join(",", scope.fields().keySet())));
            }
            case Value.Seq sequence -> {
                kind = "Sequence";
                metadata.put("size", new Value.Num(sequence.values().size()));
            }
            case Value.Dict dictionary -> {
                kind = "Dictionary";
                metadata.put("size", new Value.Num(dictionary.entries().size()));
                metadata.put("names", new Value.Str(String.join(",", dictionary.entries().keySet())));
            }
            case Value.Callable callable -> {
                kind = "Function";
                metadata.put("remaining", new Value.Num(callable.remainingArity()));
            }
        }
        metadata.putFirst("kind", new Value.Str(kind));
        return new Value.Scope(metadata);
    }

    private boolean containsHole(Expr expr) { return countHoles(expr) > 0; }

    private record HoleShape(int arity, boolean numbered) {}

    private HoleShape holeShape(Expr expr) {
        ArrayList<Integer> indexes = new ArrayList<>();
        collectHoles(expr, indexes);
        boolean numbered = indexes.stream().anyMatch(index -> index > 0);
        boolean ordinary = indexes.stream().anyMatch(index -> index == 0);
        if (numbered && ordinary) {
            throw new LangException("Cannot mix numbered and unnumbered holes", expr.span());
        }
        int arity = numbered ? indexes.stream().mapToInt(Integer::intValue).max().orElseThrow()
                : indexes.size();
        return new HoleShape(arity, numbered);
    }

    private void collectHoles(Expr expr, List<Integer> indexes) {
        switch (expr) {
            case Hole hole -> indexes.add(hole.index());
            case Literal ignored -> { }
            case Name ignored -> { }
            case Unary unary -> collectHoles(unary.operand(), indexes);
            case Binary binary -> { collectHoles(binary.left(), indexes); collectHoles(binary.right(), indexes); }
            case Conditional conditional -> {
                collectHoles(conditional.condition(), indexes);
                collectHoles(conditional.whenTrue(), indexes);
                collectHoles(conditional.whenFalse(), indexes);
            }
            case Apply apply -> { collectHoles(apply.function(), indexes); collectHoles(apply.argument(), indexes); }
            case Field field -> collectHoles(field.target(), indexes);
            case DynamicField field -> { collectHoles(field.target(), indexes); collectHoles(field.name(), indexes); }
            case Reflect reflect -> collectHoles(reflect.target(), indexes);
            case Group group -> collectHoles(group.expression(), indexes);
        }
    }

    private Expr captureNonHoleParts(Expr expr, Environment env) {
        if (!containsHole(expr)) return new Literal(evalInner(expr, env), expr.span());
        return switch (expr) {
            case Hole hole -> hole;
            case Literal literal -> literal;
            case Name name -> name;
            case Unary unary -> new Unary(unary.operator(), captureNonHoleParts(unary.operand(), env), unary.span());
            case Binary binary -> new Binary(binary.operator(), captureNonHoleParts(binary.left(), env),
                    captureNonHoleParts(binary.right(), env), binary.span());
            case Conditional conditional -> new Conditional(captureNonHoleParts(conditional.condition(), env),
                    captureNonHoleParts(conditional.whenTrue(), env),
                    captureNonHoleParts(conditional.whenFalse(), env), conditional.span());
            case Apply apply -> new Apply(captureNonHoleParts(apply.function(), env),
                    captureNonHoleParts(apply.argument(), env), apply.span());
            case Field field -> new Field(captureNonHoleParts(field.target(), env), field.field(), field.optional(),
                    field.span());
            case DynamicField field -> new DynamicField(captureNonHoleParts(field.target(), env),
                    captureNonHoleParts(field.name(), env), field.optional(), field.span());
            case Reflect reflect -> new Reflect(captureNonHoleParts(reflect.target(), env), reflect.span());
            case Group group -> new Group(captureNonHoleParts(group.expression(), env), group.span());
        };
    }

    private int countHoles(Expr expr) {
        return switch (expr) {
            case Hole ignored -> 1;
            case Literal ignored -> 0;
            case Name ignored -> 0;
            case Unary u -> countHoles(u.operand());
            case Binary b -> countHoles(b.left()) + countHoles(b.right());
            case Conditional c -> countHoles(c.condition()) + countHoles(c.whenTrue()) + countHoles(c.whenFalse());
            case Apply a -> countHoles(a.function()) + countHoles(a.argument());
            case Field f -> countHoles(f.target());
            case DynamicField f -> countHoles(f.target()) + countHoles(f.name());
            case Reflect r -> countHoles(r.target());
            case Group g -> countHoles(g.expression());
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
            if (index >= values.size()) throw new LangException("Not enough arguments for partial expression");
            return values.get(index++);
        }
        Value at(int oneBasedIndex) {
            if (oneBasedIndex < 1 || oneBasedIndex > values.size()) {
                throw new LangException("Not enough arguments for numbered partial expression");
            }
            return values.get(oneBasedIndex - 1);
        }
    }
}
