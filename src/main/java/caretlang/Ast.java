package caretlang;

import java.util.List;

final class Ast {
    sealed interface Stmt permits Assign, ExprStmt, PrintLine, FunctionDef {
        SourceSpan span();
    }
    record ContractClause(List<ContractName> names, SourceSpan span) {}
    record ContractName(String name, boolean nullable, boolean optional, SourceSpan span) {
        ContractName(String name, SourceSpan span) { this(name, false, false, span); }
    }
    record Parameter(String name, ContractClause contracts, SourceSpan span) {}
    record Assign(String name, boolean exported, ContractClause contracts, Expr value, SourceSpan span) implements Stmt {}
    record ExprStmt(Expr expression, SourceSpan span) implements Stmt {}
    /** A leading print line retains both parses until resolution determines whether print is shadowed. */
    record PrintLine(Name target, Expr builtinArgument, Expr ordinaryCall, SourceSpan span) implements Stmt {}
    record FunctionDef(String name, ContractClause resultContracts, List<Parameter> params,
                       List<Stmt> body, SourceSpan span) implements Stmt {}

    sealed interface Expr permits Literal, Name, Unary, Binary, Compose, NamedInfix, AmbiguousCall, Conditional, Apply, Field, DynamicField, Reflect, ContractModifier, Hole, Group, CollectionLiteral {
        SourceSpan span();
    }
    record Literal(Value value, SourceSpan span) implements Expr {}
    record Name(String name, SourceSpan span) implements Expr {}
    record Unary(String operator, Expr operand, SourceSpan span) implements Expr {}
    record Binary(String operator, Expr left, Expr right, SourceSpan span) implements Expr {}
    record Compose(Expr left, Expr right, SourceSpan span) implements Expr {}
    record NamedInfix(Expr left, Expr function, Expr right, SourceSpan span) implements Expr {}
    /** A three-part whitespace sequence whose prefix/infix interpretation depends on runtime arity. */
    record AmbiguousCall(Expr first, Expr middle, Expr last, SourceSpan span) implements Expr {}
    record Conditional(Expr condition, Expr whenTrue, Expr whenFalse, SourceSpan span) implements Expr {}
    record Apply(Expr function, Expr argument, SourceSpan span) implements Expr {}
    record Field(Expr target, String field, boolean optional, SourceSpan span) implements Expr {}
    record DynamicField(Expr target, Expr name, boolean optional, SourceSpan span) implements Expr {}
    record Reflect(Expr target, SourceSpan span) implements Expr {}
    record ContractModifier(Expr target, boolean nullable, boolean optional, SourceSpan span) implements Expr {}
    /** index is zero for an ordinary left-to-right hole, otherwise one-based. */
    record Hole(int index, SourceSpan span) implements Expr {}
    record Group(Expr expression, SourceSpan span) implements Expr {}
    record CollectionLiteral(List<Expr> elements, SourceSpan span) implements Expr {}
}
