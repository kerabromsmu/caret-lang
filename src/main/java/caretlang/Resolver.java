package caretlang;

import caretlang.Ast.*;

import java.util.*;

final class Resolver {
    private record Symbol(int slot, SourceSpan declaration, boolean initialized) {
        Symbol initializedSymbol() { return new Symbol(slot, declaration, true); }
    }

    private static final class Scope {
        private final Scope parent;
        private final LinkedHashMap<String, Symbol> symbols = new LinkedHashMap<>();
        private int nextSlot;

        private Scope(Scope parent) { this.parent = parent; }
    }

    private final IdentityHashMap<Name, Resolution.Binding> names = new IdentityHashMap<>();

    static Resolution resolve(List<Stmt> program, Environment globals) {
        Resolver resolver = new Resolver();
        Scope root = new Scope(null);
        for (Environment.LocalBinding binding : globals.localBindings()) {
            root.symbols.put(binding.name(), new Symbol(binding.slot(), null, true));
            root.nextSlot = Math.max(root.nextSlot, binding.slot() + 1);
        }
        resolver.resolveBlock(program, root, false);
        return new Resolution(resolver.names);
    }

    private void resolveBlock(List<Stmt> statements, Scope scope, boolean functionBody) {
        predeclare(statements, scope);
        for (Stmt statement : statements) {
            switch (statement) {
                case Assign assign -> {
                    resolveExpr(assign.value(), scope, functionBody);
                    scope.symbols.compute(assign.name(), (ignored, symbol) -> symbol.initializedSymbol());
                }
                case ExprStmt expression -> resolveExpr(expression.expression(), scope, functionBody);
                case FunctionDef function -> resolveFunction(function, scope);
            }
        }
    }

    private void predeclare(List<Stmt> statements, Scope scope) {
        for (Stmt statement : statements) {
            String name = switch (statement) {
                case Assign assign -> assign.name();
                case FunctionDef function -> function.name();
                default -> null;
            };
            if (name == null) continue;
            Symbol original = scope.symbols.get(name);
            if (original != null) duplicate(name, statement.span(), original);
            boolean function = statement instanceof FunctionDef;
            scope.symbols.put(name, new Symbol(scope.nextSlot++, statement.span(), function));
        }
    }

    private void resolveFunction(FunctionDef function, Scope enclosing) {
        Scope parameters = new Scope(enclosing);
        HashSet<String> seen = new HashSet<>();
        for (String parameter : function.params()) {
            if (!seen.add(parameter)) {
                throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.DUPLICATE_PARAMETER,
                        "Duplicate parameter: " + parameter, function.span());
            }
            parameters.symbols.put(parameter,
                    new Symbol(parameters.nextSlot++, function.span(), true));
        }
        resolveBlock(function.body(), new Scope(parameters), true);
    }

    private void resolveExpr(Expr expression, Scope scope, boolean functionBody) {
        switch (expression) {
            case Name name -> resolveName(name, scope, functionBody);
            case Literal ignored -> { }
            case Hole ignored -> { }
            case Unary unary -> resolveExpr(unary.operand(), scope, functionBody);
            case Binary binary -> {
                resolveExpr(binary.left(), scope, functionBody);
                resolveExpr(binary.right(), scope, functionBody);
            }
            case Conditional conditional -> {
                resolveExpr(conditional.condition(), scope, functionBody);
                resolveExpr(conditional.whenTrue(), scope, functionBody);
                resolveExpr(conditional.whenFalse(), scope, functionBody);
            }
            case Apply apply -> {
                resolveExpr(apply.function(), scope, functionBody);
                resolveExpr(apply.argument(), scope, functionBody);
            }
            case Field field -> resolveExpr(field.target(), scope, functionBody);
            case DynamicField field -> {
                resolveExpr(field.target(), scope, functionBody);
                resolveExpr(field.name(), scope, functionBody);
            }
            case Reflect reflect -> resolveExpr(reflect.target(), scope, functionBody);
            case Group group -> resolveExpr(group.expression(), scope, functionBody);
        }
    }

    private void resolveName(Name name, Scope scope, boolean functionBody) {
        int depth = 0;
        Symbol premature = null;
        for (Scope current = scope; current != null; current = current.parent, depth++) {
            Symbol symbol = current.symbols.get(name.name());
            if (symbol == null) continue;
            // A function body may close over a later outer assignment because invocation happens
            // dynamically. Its own block declarations and parameters must still be initialized.
            if (!symbol.initialized() && !(functionBody && depth >= 2)) {
                premature = symbol;
                continue;
            }
            names.put(name, new Resolution.Binding(depth, symbol.slot(), symbol.declaration(),
                    functionBody && depth >= 2));
            return;
        }
        if (premature != null) {
            throw new LangException(Diagnostic.Phase.SEMANTIC,
                    Diagnostic.Codes.READ_BEFORE_INITIALIZATION,
                    "Binding read before initialization: " + name.name(), name.span());
        }
        // Preserve lazy conditional/Boolean behavior: an unresolved name in an unselected branch
        // is harmless. Selected unresolved reads retain the established runtime diagnostic.
    }

    private void duplicate(String name, SourceSpan span, Symbol original) {
        List<Diagnostic.Related> related = original.declaration() == null ? List.of()
                : List.of(new Diagnostic.Related("First definition of " + name, original.declaration()));
        throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                Diagnostic.Codes.DUPLICATE_DEFINITION, "Duplicate definition: " + name, span, related));
    }
}
