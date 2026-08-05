package caretlang;

import java.util.List;

final class Ast {
    sealed interface Stmt permits Assign, ExprStmt, FunctionDef {}
    record Assign(String name, boolean exported, Expr value) implements Stmt {}
    record ExprStmt(Expr expression) implements Stmt {}
    record FunctionDef(String name, List<String> params, List<Stmt> body) implements Stmt {}

    sealed interface Expr permits Literal, Name, Unary, Binary, Conditional, Apply, Field, DynamicField, Reflect, Hole {}
    record Literal(Value value) implements Expr {}
    record Name(String name) implements Expr {}
    record Unary(String operator, Expr operand) implements Expr {}
    record Binary(String operator, Expr left, Expr right) implements Expr {}
    record Conditional(Expr condition, Expr whenTrue, Expr whenFalse) implements Expr {}
    record Apply(Expr function, Expr argument) implements Expr {}
    record Field(Expr target, String field, boolean optional) implements Expr {}
    record DynamicField(Expr target, Expr name, boolean optional) implements Expr {}
    record Reflect(Expr target) implements Expr {}
    record Hole() implements Expr {}
}
