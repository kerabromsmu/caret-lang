package caretlang;

import caretlang.Ast.*;

import java.util.Optional;
import java.util.function.Function;

/** Shared exhaustive expression rebuilding for lowering-style transformations. */
final class AstRewriter {
    private AstRewriter() {}

    static Expr rewrite(Expr expression, Function<Expr, Optional<Expr>> replacement) {
        Optional<Expr> replaced = replacement.apply(expression);
        return replaced.orElseGet(() -> AstTraversal.rebuild(expression, AstTraversal.children(expression).stream()
                .map(child -> rewrite(child, replacement)).toList()));
    }
}
