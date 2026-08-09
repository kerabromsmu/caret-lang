package caretlang;

import caretlang.Ast.*;

import java.util.Optional;
import java.util.function.Function;

/** Shared exhaustive expression rebuilding for lowering-style transformations. */
final class AstRewriter {
    private AstRewriter() {}

    static Expr rewrite(Expr expression, Function<Expr, Optional<Expr>> replacement) {
        Optional<Expr> replaced = replacement.apply(expression);
        return replaced.orElseGet(() -> switch (expression) {
            case Literal literal -> literal;
            case Name name -> name;
            case Hole hole -> hole;
            case Unary unary -> new Unary(unary.operator(), rewrite(unary.operand(), replacement), unary.span());
            case Binary binary -> new Binary(binary.operator(), rewrite(binary.left(), replacement),
                    rewrite(binary.right(), replacement), binary.span());
            case Conditional conditional -> new Conditional(rewrite(conditional.condition(), replacement),
                    rewrite(conditional.whenTrue(), replacement), rewrite(conditional.whenFalse(), replacement),
                    conditional.span());
            case Apply apply -> new Apply(rewrite(apply.function(), replacement),
                    rewrite(apply.argument(), replacement), apply.span());
            case Field field -> new Field(rewrite(field.target(), replacement), field.field(), field.optional(),
                    field.span());
            case DynamicField field -> new DynamicField(rewrite(field.target(), replacement),
                    rewrite(field.name(), replacement), field.optional(), field.span());
            case Reflect reflect -> new Reflect(rewrite(reflect.target(), replacement), reflect.span());
            case Group group -> new Group(rewrite(group.expression(), replacement), group.span());
        });
    }
}
