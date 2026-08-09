package caretlang;

import caretlang.Ast.Apply;
import caretlang.Ast.Assign;
import caretlang.Ast.Binary;
import caretlang.Ast.Conditional;
import caretlang.Ast.DynamicField;
import caretlang.Ast.Expr;
import caretlang.Ast.ExprStmt;
import caretlang.Ast.Field;
import caretlang.Ast.FunctionDef;
import caretlang.Ast.Group;
import caretlang.Ast.Hole;
import caretlang.Ast.Literal;
import caretlang.Ast.Name;
import caretlang.Ast.Reflect;
import caretlang.Ast.Stmt;
import caretlang.Ast.Unary;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;

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
                    resolveExpr(assign.value(), scope, functionBody, false);
                    Symbol symbol = scope.symbols.get(assign.name());
                    if (symbol == null) throw new IllegalStateException("Assignment was not predeclared");
                    scope.symbols.put(assign.name(), symbol.initializedSymbol());
                }
                case ExprStmt expression -> resolveExpr(expression.expression(), scope, functionBody, false);
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

    private void resolveExpr(Expr expression, Scope scope, boolean functionBody, boolean deferred) {
        switch (expression) {
            case Name name -> resolveName(name, scope, functionBody, deferred);
            case Literal ignored -> { }
            case Hole ignored -> { }
            case Unary unary -> resolveExpr(unary.operand(), scope, functionBody, deferred);
            case Binary binary -> {
                resolveExpr(binary.left(), scope, functionBody, deferred);
                resolveExpr(binary.right(), scope, functionBody,
                        deferred || binary.operator().equals("and") || binary.operator().equals("or"));
            }
            case Conditional conditional -> {
                resolveExpr(conditional.condition(), scope, functionBody, deferred);
                resolveExpr(conditional.whenTrue(), scope, functionBody, true);
                resolveExpr(conditional.whenFalse(), scope, functionBody, true);
            }
            case Apply apply -> {
                resolveExpr(apply.function(), scope, functionBody, deferred);
                resolveExpr(apply.argument(), scope, functionBody, deferred);
            }
            case Field field -> resolveExpr(field.target(), scope, functionBody, deferred);
            case DynamicField field -> {
                resolveExpr(field.target(), scope, functionBody, deferred);
                resolveExpr(field.name(), scope, functionBody, deferred);
            }
            case Reflect reflect -> resolveExpr(reflect.target(), scope, functionBody, deferred);
            case Group group -> resolveExpr(group.expression(), scope, functionBody, deferred);
        }
    }

    private void resolveName(Name name, Scope scope, boolean functionBody, boolean deferred) {
        int depth = 0;
        boolean premature = false;
        for (Scope current = scope; current != null; current = current.parent, depth++) {
            Symbol symbol = current.symbols.get(name.name());
            if (symbol == null) continue;
            // A function body may close over a later outer assignment because invocation happens
            // dynamically. Its own block declarations and parameters must still be initialized.
            if (!symbol.initialized() && !(functionBody && depth >= 2)) {
                if (deferred) {
                    names.put(name, binding(symbol, depth, false));
                    return;
                }
                premature = true;
                continue;
            }
            names.put(name, binding(symbol, depth, functionBody && depth >= 2));
            return;
        }
        if (premature) {
            throw new LangException(Diagnostic.Phase.SEMANTIC,
                    Diagnostic.Codes.READ_BEFORE_INITIALIZATION,
                    "Binding read before initialization: " + name.name(), name.span());
        }
        // Preserve lazy conditional/Boolean behavior: an unresolved name in an unselected branch
        // is harmless. Selected unresolved reads retain the established runtime diagnostic.
    }

    private static Resolution.Binding binding(Symbol symbol, int depth, boolean captured) {
        return new Resolution.Binding(depth, symbol.slot(), symbol.declaration(), captured);
    }

    private void duplicate(String name, SourceSpan span, Symbol original) {
        List<Diagnostic.Related> related = original.declaration() == null ? List.of()
                : List.of(new Diagnostic.Related("First definition of " + name, original.declaration()));
        throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                Diagnostic.Codes.DUPLICATE_DEFINITION, "Duplicate definition: " + name, span, related));
    }
}
