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
                Value.FunctionValue fn = new Value.FunctionValue(name, params, args -> {
                    Environment local = new Environment(env);
                    for (int i = 0; i < params.size(); i++) local.define(params.get(i), args.get(i));
                    return executeBlock(body, local);
                });
                env.define(name, fn);
                last = fn;
            }
        }

        return exports.isEmpty() ? last : new Value.Scope(exports);
    }

    private Value eval(Expr expr, Environment env, List<Value> holeArgs) {
        if (containsHole(expr) && holeArgs == null) {
            int holes = countHoles(expr);
            return new Value.HoleFunction(expr.toString(), holes, supplied -> eval(expr, env, supplied));
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
                case "-" -> new Value.Num(-number(value));
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
                yield new Value.Num(number(left) + number(right));
            }
            case "-" -> new Value.Num(number(left) - number(right));
            case "*" -> new Value.Num(number(left) * number(right));
            case "/" -> new Value.Num(number(left) / number(right));
            case "%" -> new Value.Num(number(left) % number(right));
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
        if (a instanceof Value.Num(double value1) && b instanceof Value.Num(double value)) return value1 == value;
        return Objects.equals(a, b);
    }

    private double number(Value value) {
        if (value instanceof Value.Num(double value1)) return value1;
        throw new LangException("Expected number, got: " + value);
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
                case Value.Callable ignored -> "Function";
            };
            return new Value.Str(name);
        }));
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
            case Value.Callable callable -> {
                kind = "Function";
                metadata.put("remaining", new Value.Num(callable.remainingArity()));
            }
        }
        metadata.putFirst("kind", new Value.Str(kind));
        return new Value.Scope(metadata);
    }

    private boolean containsHole(Expr expr) { return countHoles(expr) > 0; }

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
            case Hole hole -> new Literal(holes.next(), hole.span());
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
    }
}
