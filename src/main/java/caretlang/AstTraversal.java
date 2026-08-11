package caretlang;

import caretlang.Ast.*;

import java.util.List;
import java.util.function.Consumer;

/** Shared read-only traversal over expression children in source evaluation order. */
final class AstTraversal {
    private AstTraversal() {}

    static List<Expr> children(Expr expression) {
        return switch (expression) {
            case Literal ignored -> List.of();
            case Name ignored -> List.of();
            case Hole ignored -> List.of();
            case Unary unary -> List.of(unary.operand());
            case Binary binary -> List.of(binary.left(), binary.right());
            case Compose compose -> List.of(compose.left(), compose.right());
            case NamedInfix infix -> List.of(infix.left(), infix.function(), infix.right());
            case AmbiguousCall call -> List.of(call.first(), call.middle(), call.last());
            case Conditional conditional -> List.of(
                    conditional.condition(), conditional.whenTrue(), conditional.whenFalse());
            case Apply apply -> List.of(apply.function(), apply.argument());
            case Field field -> List.of(field.target());
            case DynamicField field -> List.of(field.target(), field.name());
            case Reflect reflect -> List.of(reflect.target());
            case Group group -> List.of(group.expression());
        };
    }

    static void walkPreOrder(Expr expression, Consumer<Expr> visitor) {
        visitor.accept(expression);
        for (Expr child : children(expression)) walkPreOrder(child, visitor);
    }

    static Expr rebuild(Expr expression, List<Expr> children) {
        if (children.size() != children(expression).size()) {
            throw new IllegalArgumentException("Incorrect child count for " + expression.getClass().getSimpleName());
        }
        return switch (expression) {
            case Literal literal -> literal;
            case Name name -> name;
            case Hole hole -> hole;
            case Unary unary -> new Unary(unary.operator(), children.get(0), unary.span());
            case Binary binary -> new Binary(binary.operator(), children.get(0), children.get(1), binary.span());
            case Compose compose -> new Compose(children.get(0), children.get(1), compose.span());
            case NamedInfix infix -> new NamedInfix(children.get(0), children.get(1),
                    children.get(2), infix.span());
            case AmbiguousCall call -> new AmbiguousCall(children.get(0), children.get(1),
                    children.get(2), call.span());
            case Conditional conditional -> new Conditional(
                    children.get(0), children.get(1), children.get(2), conditional.span());
            case Apply apply -> new Apply(children.get(0), children.get(1), apply.span());
            case Field field -> new Field(children.getFirst(), field.field(), field.optional(), field.span());
            case DynamicField field -> new DynamicField(
                    children.get(0), children.get(1), field.optional(), field.span());
            case Reflect reflect -> new Reflect(children.getFirst(), reflect.span());
            case Group group -> new Group(children.getFirst(), group.span());
        };
    }
}
