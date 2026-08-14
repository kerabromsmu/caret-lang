package caretlang;

import caretlang.Ast.Apply;
import caretlang.Ast.AmbiguousCall;
import caretlang.Ast.Assign;
import caretlang.Ast.Binary;
import caretlang.Ast.Conditional;
import caretlang.Ast.Compose;
import caretlang.Ast.DynamicField;
import caretlang.Ast.Expr;
import caretlang.Ast.ExprStmt;
import caretlang.Ast.Field;
import caretlang.Ast.FunctionDef;
import caretlang.Ast.Group;
import caretlang.Ast.Hole;
import caretlang.Ast.Literal;
import caretlang.Ast.Name;
import caretlang.Ast.NamedInfix;
import caretlang.Ast.Reflect;
import caretlang.Ast.Stmt;
import caretlang.Ast.Unary;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;

final class Resolver {
    private enum ContractState { CONTRACT, NON_CONTRACT, UNKNOWN }
    private record Symbol(int slot, SourceSpan declaration, boolean initialized, Integer callableArity,
                          ContractState contractState) {
        Symbol initializedSymbol() { return new Symbol(slot, declaration, true, callableArity, contractState); }
    }

    private static final class Scope {
        private final Scope parent;
        private final LinkedHashMap<String, Symbol> symbols = new LinkedHashMap<>();
        private int nextSlot;

        private Scope(Scope parent) { this.parent = parent; }
    }

    private final IdentityHashMap<Name, Resolution.Binding> names = new IdentityHashMap<>();
    private final IdentityHashMap<Ast.ContractClause, List<Resolution.ContractBinding>> contracts = new IdentityHashMap<>();
    private final IdentityHashMap<AmbiguousCall, Resolution.CallMode> calls = new IdentityHashMap<>();

    static Resolution resolve(List<Stmt> program, Environment globals) {
        Resolver resolver = new Resolver();
        Scope root = new Scope(null);
        for (Environment.LocalBinding binding : globals.localBindings()) {
            ContractState state = BuiltinContract.named(binding.name()).isPresent()
                    ? ContractState.CONTRACT : ContractState.UNKNOWN;
            root.symbols.put(binding.name(), new Symbol(binding.slot(), null, true,
                    binding.callableArity(), state));
            root.nextSlot = Math.max(root.nextSlot, binding.slot() + 1);
        }
        resolver.resolveBlock(program, root, false);
        return new Resolution(resolver.names, resolver.contracts, resolver.calls);
    }

    private void resolveBlock(List<Stmt> statements, Scope scope, boolean functionBody) {
        predeclare(statements, scope);
        for (Stmt statement : statements) {
            switch (statement) {
                case Assign assign -> {
                    resolverContracts(assign.contracts(), scope);
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
            Integer arity = statement instanceof FunctionDef definition ? definition.params().size() : null;
            ContractState state = statement instanceof Assign assign ? contractState(assign.value())
                    : ContractState.UNKNOWN;
            scope.symbols.put(name, new Symbol(scope.nextSlot++, statement.span(), function, arity, state));
        }
    }

    private void resolveFunction(FunctionDef function, Scope enclosing) {
        resolverContracts(function.resultContracts(), enclosing);
        Scope parameters = new Scope(enclosing);
        HashSet<String> seen = new HashSet<>();
        for (Ast.Parameter parameter : function.params()) {
            resolverContracts(parameter.contracts(), enclosing);
            if (!seen.add(parameter.name())) {
                throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.DUPLICATE_PARAMETER,
                        "Duplicate parameter: " + parameter.name(), function.span());
            }
            parameters.symbols.put(parameter.name(),
                    new Symbol(parameters.nextSlot++, parameter.span(), true, null, ContractState.UNKNOWN));
        }
        resolveBlock(function.body(), new Scope(parameters), true);
    }

    private void resolverContracts(Ast.ContractClause clause, Scope scope) {
        if (clause == null) return;
        List<Resolution.ContractBinding> resolved = clause.names().stream().map(name -> {
            int depth = 0;
            for (Scope current = scope; current != null; current = current.parent, depth++) {
                Symbol symbol = current.symbols.get(name.name());
                if (symbol != null) {
                    if (symbol.contractState() == ContractState.NON_CONTRACT) {
                        throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.NOT_A_CONTRACT,
                                "Binding is not a contract: " + name.name(), name.span());
                    }
                    return new Resolution.ContractBinding(name.name(),
                            new Resolution.Binding(depth, symbol.slot(), symbol.declaration(), false), name.span());
                }
            }
            if (BuiltinContract.named(name.name()).isPresent()) {
                return new Resolution.ContractBinding(name.name(), null, name.span());
            }
            throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.UNKNOWN_CONTRACT,
                    "Unknown contract: " + name.name(), name.span());
        }).toList();
        contracts.put(clause, resolved);
    }

    private ContractState contractState(Expr expression) {
        if (expression instanceof Apply apply && apply.function() instanceof Name name
                && name.name().equals("contract")) return ContractState.CONTRACT;
        if (expression instanceof Literal || expression instanceof Ast.CollectionLiteral) {
            return ContractState.NON_CONTRACT;
        }
        return ContractState.UNKNOWN;
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
            case Compose compose -> {
                resolveExpr(compose.left(), scope, functionBody, deferred);
                resolveExpr(compose.right(), scope, functionBody, deferred);
            }
            case NamedInfix infix -> {
                resolveExpr(infix.left(), scope, functionBody, deferred);
                resolveExpr(infix.function(), scope, functionBody, deferred);
                resolveExpr(infix.right(), scope, functionBody, deferred);
            }
            case AmbiguousCall call -> {
                resolveExpr(call.first(), scope, functionBody, deferred);
                resolveExpr(call.middle(), scope, functionBody, deferred);
                resolveExpr(call.last(), scope, functionBody, deferred);
                Integer firstArity = knownArity(call.first(), scope);
                Integer middleArity = knownArity(call.middle(), scope);
                Resolution.CallMode mode = firstArity != null && firstArity > 0
                        ? Resolution.CallMode.PREFIX
                        : firstArity != null && firstArity == 0 && Integer.valueOf(2).equals(middleArity)
                                ? Resolution.CallMode.INFIX : Resolution.CallMode.DYNAMIC;
                calls.put(call, mode);
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
            case Ast.CollectionLiteral collection -> collection.elements().forEach(
                    element -> resolveExpr(element, scope, functionBody, deferred));
        }
    }

    private Integer knownArity(Expr expression, Scope scope) {
        if (!(expression instanceof Name name)) return null;
        for (Scope current = scope; current != null; current = current.parent) {
            Symbol symbol = current.symbols.get(name.name());
            if (symbol != null) return symbol.callableArity();
        }
        return null;
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
