package caretlang;

import caretlang.Ast.Apply;
import caretlang.Ast.AmbiguousCall;
import caretlang.Ast.Assign;
import caretlang.Ast.Binary;
import caretlang.Ast.Conditional;
import caretlang.Ast.Compose;
import caretlang.Ast.ContractModifier;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class Resolver {
    private enum ContractState { CONTRACT, NON_CONTRACT, UNKNOWN }
    private record Symbol(int slot, int id, SourceSpan declaration, boolean initialized, Integer callableArity,
                          ContractState contractState, Integer contractParameterArity,
                          Boolean refinementEligible, boolean functionGroup) {
        Symbol initializedSymbol() {
            return new Symbol(slot, id, declaration, true, callableArity, contractState,
                    contractParameterArity, refinementEligible, functionGroup);
        }
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
    private final IdentityHashMap<Ast.PrintLine, Boolean> builtinPrintLines = new IdentityHashMap<>();
    private final java.util.Map<SourceSpan, Integer> declarations = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> directAliases = new java.util.HashMap<>();
    private int nextSymbolId;
    private Integer anyContractSymbol;

    static Resolution resolve(List<Stmt> program, Environment globals) {
        Resolver resolver = new Resolver();
        Scope root = new Scope(null);
        for (Environment.LocalBinding binding : globals.localBindings()) {
            ContractState state = BuiltinContract.named(binding.name()).isPresent()
                    ? ContractState.CONTRACT : ContractState.UNKNOWN;
            int symbolId = resolver.nextSymbolId++;
            root.symbols.put(binding.name(), new Symbol(binding.slot(), symbolId, null, true,
                    binding.callableArity(), state, binding.contractParameterArity(), binding.refinementEligible(), false));
            if (binding.name().equals("Any") && state == ContractState.CONTRACT) {
                resolver.anyContractSymbol = symbolId;
            }
            root.nextSlot = Math.max(root.nextSlot, binding.slot() + 1);
        }
        resolver.resolveBlock(program, root, false);
        return new Resolution(resolver.names, resolver.contracts, resolver.calls,
                resolver.builtinPrintLines, resolver.declarations);
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
                    Integer parameterArity = knownContractParameterArity(assign.value(), scope);
                    Symbol initialized = symbol.initializedSymbol();
                    scope.symbols.put(assign.name(), new Symbol(initialized.slot(), initialized.id(),
                            initialized.declaration(), true, initialized.callableArity(), initialized.contractState(),
                            parameterArity, initialized.refinementEligible(), initialized.functionGroup()));
                    if (assign.value() instanceof Name alias) {
                        Resolution.Binding target = names.get(alias);
                        if (target != null) directAliases.put(initialized.id(), target.symbolId());
                    }
                }
                case ExprStmt expression -> resolveExpr(expression.expression(), scope, functionBody, false);
                case Ast.PrintLine line -> resolvePrintLine(line, scope, functionBody);
                case FunctionDef function -> resolveFunction(function, scope);
            }
        }
        validateOverloadDomains(statements);
    }

    private void predeclare(List<Stmt> statements, Scope scope) {
        for (Stmt statement : statements) {
            String name = switch (statement) {
                case Assign assign -> assign.name();
                case FunctionDef function -> function.name();
                case Ast.PrintLine ignored -> null;
                default -> null;
            };
            if (name == null) continue;
            Symbol original = scope.symbols.get(name);
            boolean function = statement instanceof FunctionDef;
            Integer arity = statement instanceof FunctionDef definition ? definition.params().size() : null;
            if (original != null) {
                if (function && original.functionGroup()) {
                    if (!java.util.Objects.equals(original.callableArity(), arity)) {
                        throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                                Diagnostic.Codes.INCONSISTENT_OVERLOAD_ARITY,
                                "Overload variants must have the same arity: " + name,
                                statement.span(), List.of(new Diagnostic.Related(
                                "First overload variant declared here", original.declaration()))));
                    }
                    declarations.put(statement.span(), original.id());
                    continue;
                }
                duplicate(name, statement.span(), original);
            }
            ContractState state = statement instanceof Assign assign ? contractState(assign.value())
                    : ContractState.UNKNOWN;
            int symbolId = nextSymbolId++;
            scope.symbols.put(name, new Symbol(scope.nextSlot++, symbolId, statement.span(), function, arity, state,
                    null, null, function));
            declarations.put(statement.span(), symbolId);
        }
    }

    private void validateOverloadDomains(List<Stmt> statements) {
        LinkedHashMap<String, List<FunctionDef>> groups = new LinkedHashMap<>();
        for (Stmt statement : statements) {
            if (statement instanceof FunctionDef function) {
                groups.computeIfAbsent(function.name(), ignored -> new ArrayList<>()).add(function);
            }
        }
        for (var entry : groups.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            LinkedHashMap<String, FunctionDef> domains = new LinkedHashMap<>();
            for (FunctionDef function : entry.getValue()) {
                String domain = function.params().stream().map(parameter -> domainKey(parameter.contracts()))
                        .reduce((left, right) -> left + "|" + right).orElse("");
                FunctionDef original = domains.putIfAbsent(domain, function);
                if (original != null) {
                    throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                            Diagnostic.Codes.DUPLICATE_DEFINITION,
                            "Duplicate definition: " + entry.getKey(), function.span(),
                            List.of(new Diagnostic.Related("First overload variant declared here",
                                    original.span()))));
                }
            }
        }
    }

    private String domainKey(Ast.ContractClause clause) {
        if (clause == null) return "Any";
        List<String> keys = contracts.getOrDefault(clause, List.of()).stream()
                .filter(binding -> !isAny(binding)).map(this::contractKey)
                .distinct().sorted(Comparator.naturalOrder()).toList();
        return keys.isEmpty() ? "Any" : String.join("&", keys);
    }

    private boolean isAny(Resolution.ContractBinding binding) {
        return binding.arguments().isEmpty() && !binding.nullable() && !binding.optional()
                && ((binding.binding() == null && binding.name().equals("Any"))
                || (binding.binding() != null && anyContractSymbol != null
                && canonicalSymbol(binding.binding().symbolId()) == anyContractSymbol));
    }

    private String contractKey(Resolution.ContractBinding binding) {
        String identity = binding.binding() == null ? "builtin:" + binding.name()
                : "symbol:" + canonicalSymbol(binding.binding().symbolId());
        String arguments = binding.arguments().stream().map(this::contractKey)
                .reduce((left, right) -> left + "," + right).map(value -> "<" + value + ">").orElse("");
        return identity + arguments + (binding.nullable() ? "?" : "") + (binding.optional() ? "~" : "");
    }

    private int canonicalSymbol(int symbol) {
        HashSet<Integer> seen = new HashSet<>();
        while (seen.add(symbol) && directAliases.containsKey(symbol)) symbol = directAliases.get(symbol);
        return symbol;
    }

    private void resolvePrintLine(Ast.PrintLine line, Scope scope, boolean functionBody) {
        resolveName(line.target(), scope, functionBody, false);
        Resolution.Binding binding = names.get(line.target());
        boolean builtin = binding != null && binding.declarationSpan() == null;
        builtinPrintLines.put(line, builtin);
        resolveExpr(builtin ? line.builtinArgument() : line.ordinaryCall(), scope, functionBody, false);
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
                    new Symbol(parameters.nextSlot++, nextSymbolId++, parameter.span(), true, null,
                            ContractState.UNKNOWN, null, null, false));
        }
        resolveBlock(function.body(), new Scope(parameters), true);
    }

    private void resolverContracts(Ast.ContractClause clause, Scope scope) {
        if (clause == null) return;
        java.util.ArrayList<Resolution.ContractBinding> resolved = new java.util.ArrayList<>();
        for (int index = 0; index < clause.names().size(); index++) {
            Ast.ContractName name = clause.names().get(index);
            Resolution.ContractBinding binding = resolveContract(name, scope);
            if (name.arguments().isEmpty()) {
                Integer arity = knownContractParameterArity(name.name(), scope);
                if (arity != null && arity > 0 && index + arity < clause.names().size()) {
                    java.util.ArrayList<Resolution.ContractBinding> arguments = new java.util.ArrayList<>();
                    for (int argument = 0; argument < arity; argument++) {
                        arguments.add(resolveContract(clause.names().get(++index), scope));
                    }
                    binding = new Resolution.ContractBinding(binding.name(), binding.binding(),
                            List.copyOf(arguments), binding.nullable(), binding.optional(),
                            SourceSpan.cover(binding.span(), arguments.getLast().span()));
                }
            }
            resolved.add(binding);
        }
        contracts.put(clause, List.copyOf(resolved));
    }

    private Resolution.ContractBinding resolveContract(Ast.ContractName name, Scope scope) {
            int depth = 0;
            for (Scope current = scope; current != null; current = current.parent, depth++) {
                Symbol symbol = current.symbols.get(name.name());
                if (symbol != null) {
                    if (symbol.contractState() == ContractState.NON_CONTRACT) {
                        throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.NOT_A_CONTRACT,
                                "Binding is not a contract: " + name.name(), name.span());
                    }
                    return new Resolution.ContractBinding(name.name(),
                            new Resolution.Binding(depth, symbol.slot(), symbol.id(), symbol.declaration(), false,
                                    symbol.refinementEligible()), resolveContractArguments(name, scope),
                            name.nullable(), name.optional(), name.span());
                }
            }
            if (BuiltinContract.named(name.name()).isPresent()) {
                return new Resolution.ContractBinding(name.name(), null, resolveContractArguments(name, scope),
                        name.nullable(), name.optional(), name.span());
            }
            throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.UNKNOWN_CONTRACT,
                    "Unknown contract: " + name.name(), name.span());
    }

    private List<Resolution.ContractBinding> resolveContractArguments(Ast.ContractName name, Scope scope) {
        return name.arguments().stream().map(argument -> resolveContract(argument, scope)).toList();
    }

    private Integer knownContractParameterArity(String name, Scope scope) {
        for (Scope current = scope; current != null; current = current.parent) {
            Symbol symbol = current.symbols.get(name);
            if (symbol != null) return symbol.contractParameterArity();
        }
        return null;
    }

    private Integer knownContractParameterArity(Expr expression, Scope scope) {
        if (expression instanceof Name name) return knownContractParameterArity(name.name(), scope);
        if (expression instanceof Group group) return knownContractParameterArity(group.expression(), scope);
        return null;
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
            case ContractModifier modifier -> {
                resolveExpr(modifier.target(), scope, functionBody, deferred);
                if (knownContractState(modifier.target(), scope) == ContractState.NON_CONTRACT) {
                    throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.NOT_A_CONTRACT,
                            "Binding is not a contract: " + modifier.target(), modifier.target().span());
                }
            }
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

    private ContractState knownContractState(Expr expression, Scope scope) {
        if (expression instanceof ContractModifier) return ContractState.CONTRACT;
        if (expression instanceof Apply apply && apply.function() instanceof Name name
                && name.name().equals("contract")) return ContractState.CONTRACT;
        if (expression instanceof Literal || expression instanceof Ast.CollectionLiteral) {
            return ContractState.NON_CONTRACT;
        }
        if (!(expression instanceof Name name)) return ContractState.UNKNOWN;
        for (Scope current = scope; current != null; current = current.parent) {
            Symbol symbol = current.symbols.get(name.name());
            if (symbol != null) return symbol.contractState();
        }
        return BuiltinContract.named(name.name()).isPresent()
                ? ContractState.CONTRACT : ContractState.UNKNOWN;
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
        return new Resolution.Binding(depth, symbol.slot(), symbol.id(), symbol.declaration(), captured,
                symbol.refinementEligible());
    }

    private void duplicate(String name, SourceSpan span, Symbol original) {
        List<Diagnostic.Related> related = original.declaration() == null ? List.of()
                : List.of(new Diagnostic.Related("First definition of " + name, original.declaration()));
        throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                Diagnostic.Codes.DUPLICATE_DEFINITION, "Duplicate definition: " + name, span, related));
    }
}
