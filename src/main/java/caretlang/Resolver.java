package caretlang;

import caretlang.Ast.Apply;
import caretlang.Ast.AmbiguousCall;
import caretlang.Ast.ArrowContract;
import caretlang.Ast.Assign;
import caretlang.Ast.Binary;
import caretlang.Ast.Conditional;
import caretlang.Ast.ContractVariable;
import caretlang.Ast.Compose;
import caretlang.Ast.CollectionLiteral;
import caretlang.Ast.ContractModifier;
import caretlang.Ast.DynamicField;
import caretlang.Ast.Dereference;
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

import java.util.ArrayDeque;
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
    private final IdentityHashMap<Ast.ContractClause, Resolution.AnalyzedClause> clauses = new IdentityHashMap<>();
    private final EffectCatalog effectCatalog;
    private final IdentityHashMap<AmbiguousCall, Resolution.CallMode> calls = new IdentityHashMap<>();
    private final IdentityHashMap<Ast.PrintLine, Boolean> builtinPrintLines = new IdentityHashMap<>();
    private final IdentityHashMap<FunctionDef, LinkedHashMap<Integer, Resolution.Upvalue>> upvalues =
            new IdentityHashMap<>();
    private final java.util.Set<ArrowContract> headerArrows =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayDeque<FunctionDef> functions = new ArrayDeque<>();
    private final java.util.Map<SourceSpan, Integer> declarations = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> directAliases = new java.util.HashMap<>();
    private final java.util.Map<Integer, java.util.Set<Integer>> staticContractBases = new java.util.HashMap<>();
    private int nextSymbolId;
    private Integer anyContractSymbol;

    private Resolver(EffectCatalog effectCatalog) { this.effectCatalog = effectCatalog; }

    static Resolution resolve(List<Stmt> program, Environment globals, EffectCatalog effectCatalog) {
        Resolver resolver = new Resolver(effectCatalog);
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
        return new Resolution(resolver.names, resolver.clauses, resolver.calls,
                resolver.builtinPrintLines, resolver.resolvedUpvalues(), resolver.declarations);
    }

    static Resolution resolve(List<Stmt> program, Environment globals) {
        return resolve(program, globals, EffectCatalog.standard(false));
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
                    Resolution.Binding target = aliasTarget(assign.value());
                    if (target != null) directAliases.put(initialized.id(), target.symbolId());
                }
                case ExprStmt expression -> resolveExpr(expression.expression(), scope, functionBody, false);
                case Ast.PrintLine line -> resolvePrintLine(line, scope, functionBody);
                case FunctionDef function -> resolveFunction(function, scope);
            }
        }
        recordStaticContractBases(statements);
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
                if (function && original.declaration() == null && original.callableArity() != null
                        && original.contractState() != ContractState.CONTRACT) {
                    if (!java.util.Objects.equals(original.callableArity(), arity)) {
                        throw new LangException(Diagnostic.Phase.SEMANTIC,
                                Diagnostic.Codes.INCONSISTENT_OVERLOAD_ARITY,
                                "Overload variants must have the same arity: " + name, statement.span());
                    }
                    scope.symbols.put(name, new Symbol(original.slot(), original.id(), null, true, arity,
                            original.contractState(), original.contractParameterArity(),
                            original.refinementEligible(), true));
                    declarations.put(statement.span(), original.id());
                    continue;
                }
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
        Resolution.AnalyzedClause analyzed = clauses.get(clause);
        List<Resolution.ContractBinding> requirements = analyzed == null ? List.of()
                : analyzed.valueRequirements();
        List<String> keys = requirements.stream().filter(binding -> !isAny(binding))
                .filter(binding -> requirements.stream().noneMatch(other -> other != binding
                        && contractBindingImplies(other, binding)
                        && !contractBindingImplies(binding, other)))
                .map(this::contractKey)
                .distinct().sorted(Comparator.naturalOrder()).toList();
        return keys.isEmpty() ? "Any" : String.join("&", keys);
    }

    private boolean contractBindingImplies(Resolution.ContractBinding left,
                                           Resolution.ContractBinding right) {
        if (left.nullable() && !right.nullable() || left.optional() && !right.optional()) return false;
        if (left.arguments().size() != right.arguments().size()) return false;
        for (int i = 0; i < left.arguments().size(); i++) {
            if (!contractBindingImplies(left.arguments().get(i), right.arguments().get(i))) return false;
        }
        if (isAny(right)) return true;
        if (left.binding() == null || right.binding() == null) {
            BuiltinContract l = BuiltinContract.named(left.name()).orElse(null);
            BuiltinContract r = BuiltinContract.named(right.name()).orElse(null);
            return l != null && r != null && ContractRelations.implies(l, r);
        }
        int source = canonicalSymbol(left.binding().symbolId());
        int target = canonicalSymbol(right.binding().symbolId());
        if (source == target) return true;
        java.util.Set<Integer> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<Integer> pending = new java.util.ArrayDeque<>();
        pending.add(source);
        while (!pending.isEmpty()) {
            int current = pending.removeFirst();
            if (!visited.add(current)) continue;
            for (int base : staticContractBases.getOrDefault(current, java.util.Set.of())) {
                if (base == target) return true;
                pending.addLast(base);
            }
        }
        return false;
    }

    private void recordStaticContractBases(List<Stmt> statements) {
        for (Stmt statement : statements) {
            if (!(statement instanceof Assign assign)) continue;
            Expr value = assign.value();
            while (value instanceof Group group) value = group.expression();
            if (!(value instanceof Apply apply) || !(apply.function() instanceof Name constructor)
                    || !constructor.name().equals("contract")) continue;
            Integer derived = declarations.get(assign.span());
            if (derived == null) continue;
            java.util.LinkedHashSet<Integer> bases = new java.util.LinkedHashSet<>();
            collectStaticContractBases(apply.argument(), bases);
            staticContractBases.put(canonicalSymbol(derived), java.util.Set.copyOf(bases));
        }
    }

    private void collectStaticContractBases(Expr expression, java.util.Set<Integer> result) {
        while (expression instanceof Group group) expression = group.expression();
        if (expression instanceof Name name) {
            Resolution.Binding binding = names.get(name);
            if (binding != null) result.add(canonicalSymbol(binding.symbolId()));
        } else if (expression instanceof CollectionLiteral collection) {
            collection.elements().forEach(element -> collectStaticContractBases(element.value(), result));
        }
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

    private Resolution.Binding aliasTarget(Expr expression) {
        while (expression instanceof Group group) expression = group.expression();
        return expression instanceof Name alias ? names.get(alias) : null;
    }

    private void resolvePrintLine(Ast.PrintLine line, Scope scope, boolean functionBody) {
        resolveName(line.target(), scope, functionBody, false);
        Resolution.Binding binding = names.get(line.target());
        boolean builtin = binding != null && binding.declarationSpan() == null;
        builtinPrintLines.put(line, builtin);
        resolveExpr(builtin ? line.builtinArgument() : line.ordinaryCall(), scope, functionBody, false);
    }

    private void resolveFunction(FunctionDef function, Scope enclosing) {
        functions.push(function);
        upvalues.put(function, new LinkedHashMap<>());
        try {
            validateHeaderVariables(function);
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
        } finally {
            functions.pop();
        }
    }

    private void resolverContracts(Ast.ContractClause clause, Scope scope) {
        if (clause == null) return;
        java.util.ArrayList<Resolution.ContractBinding> resolved = new java.util.ArrayList<>();
        java.util.LinkedHashSet<EffectDescriptor> effects = new java.util.LinkedHashSet<>();
        Ast.ContractName pure = null;
        for (int index = 0; index < clause.names().size(); index++) {
            Ast.ContractName name = clause.names().get(index);
            boolean contractName = isKnownContractName(name.name(), scope);
            EffectDescriptor effect = effectCatalog.resolve(name.name()).orElse(null);
            if (name.name().equals("pure")) {
                if (name.nullable() || name.optional()) invalidEffectModifier(name);
                if (!name.arguments().isEmpty()) effectAsContractArgument(name.arguments().getFirst());
                if (!effects.isEmpty()) conflictingAllowance(name, clause.names().getFirst());
                pure = name;
                continue;
            }
            if (effect != null && contractName) {
                throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.AMBIGUOUS_CLAUSE_NAME,
                        "Ambiguous clause name: " + name.name(), name.span());
            }
            if (effect != null) {
                if (name.nullable() || name.optional()) invalidEffectModifier(name);
                if (!name.arguments().isEmpty()) effectAsContractArgument(name.arguments().getFirst());
                if (pure != null) conflictingAllowance(name, pure);
                effects.add(effect);
                continue;
            }
            if (name.inline() != null) {
                resolveExpr(name.inline(), scope, false, false);
                resolved.add(new Resolution.ContractBinding(name.name(), null, List.of(), false, false,
                        name.inline(), name.span()));
                continue;
            }
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
        clauses.put(clause, new Resolution.AnalyzedClause(List.copyOf(resolved),
                pure != null || !effects.isEmpty() ? List.copyOf(effects) : null, clause.span()));
    }

    private boolean isKnownContractName(String name, Scope scope) {
        if (BuiltinContract.named(name).isPresent()) return true;
        for (Scope current = scope; current != null; current = current.parent) {
            Symbol symbol = current.symbols.get(name);
            if (symbol != null) return symbol.contractState() != ContractState.NON_CONTRACT;
        }
        return false;
    }

    private static void invalidEffectModifier(Ast.ContractName name) {
        throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.INVALID_EFFECT_MODIFIER,
                "Effect terms cannot use null or missing modifiers: " + name.name(), name.span());
    }

    private static void effectAsContractArgument(Ast.ContractName name) {
        throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.EFFECT_AS_CONTRACT_ARGUMENT,
                "Effect cannot be used as a contract argument: " + name.name(), name.span());
    }

    private static void conflictingAllowance(Ast.ContractName later, Ast.ContractName earlier) {
        throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                Diagnostic.Codes.CONFLICTING_EFFECT_ALLOWANCE,
                "pure cannot be combined with named effects", later.span(), List.of(
                new Diagnostic.Related("Conflicting allowance starts here", earlier.span()))));
    }

    private Resolution.ContractBinding resolveContract(Ast.ContractName name, Scope scope) {
            if (isContractVariable(name.name())) {
                return new Resolution.ContractBinding(name.name(), null, resolveContractArguments(name, scope),
                        name.nullable(), name.optional(), name.span());
            }
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
        return name.arguments().stream().map(argument -> {
            if (argument.name().equals("pure") || effectCatalog.resolve(argument.name()).isPresent()) {
                effectAsContractArgument(argument);
            }
            return resolveContract(argument, scope);
        }).toList();
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
        if (expression instanceof ArrowContract) return ContractState.CONTRACT;
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
            case ContractVariable ignored -> { }
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
            case Dereference dereference -> resolveExpr(dereference.target(), scope, functionBody, deferred);
            case ContractModifier modifier -> {
                resolveExpr(modifier.target(), scope, functionBody, deferred);
                if (knownContractState(modifier.target(), scope) == ContractState.NON_CONTRACT) {
                    throw new LangException(Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.NOT_A_CONTRACT,
                            "Binding is not a contract: " + modifier.target(), modifier.target().span());
                }
            }
            case Group group -> resolveExpr(group.expression(), scope, functionBody, deferred);
            case Ast.CollectionLiteral collection -> {
                Class<?> shape = null;
                LinkedHashMap<String, SourceSpan> names = new LinkedHashMap<>();
                for (Ast.CollectionElement element : collection.elements()) {
                    Class<?> elementShape = element instanceof Ast.NamedElement
                            || staticallyFieldExpression(element.value())
                            ? Ast.NamedElement.class : Ast.PositionalElement.class;
                    if (shape == null) shape = elementShape;
                    else if (shape != elementShape) {
                        throw new LangException(Diagnostic.Phase.SEMANTIC,
                                Diagnostic.Codes.MIXED_COLLECTION_SHAPE,
                                "A collection cannot mix named and positional elements", element.span());
                    }
                    if (element instanceof Ast.NamedElement named) {
                        SourceSpan original = names.putIfAbsent(named.name(), named.span());
                        if (original != null) {
                            throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                                    Diagnostic.Codes.DUPLICATE_FIELD,
                                    "Duplicate field: " + named.name(), named.span(),
                                    List.of(new Diagnostic.Related(
                                            "First field named " + named.name(), original))));
                        }
                    }
                    resolveExpr(element.value(), scope, functionBody, deferred);
                }
            }
            case ArrowContract arrow -> {
                if (!headerArrows.contains(arrow)) validateContractVariables(arrow);
                arrow.parameters().forEach(parameter -> parameter.forEach(
                        requirement -> resolveExpr(requirement, scope, functionBody, deferred)));
                resolveExpr(arrow.result(), scope, functionBody, deferred);
                if (arrow.explicitPure() && !arrow.effectTerms().isEmpty()) {
                    throw new LangException(Diagnostic.Phase.SEMANTIC,
                            Diagnostic.Codes.CONFLICTING_EFFECT_ALLOWANCE,
                            "pure cannot be combined with named effects", arrow.effectTerms().getFirst().span());
                }
                for (Name effect : arrow.effectTerms()) {
                    if (effectCatalog.resolve(effect.name()).isEmpty()) {
                        throw new LangException(Diagnostic.Phase.SEMANTIC,
                                Diagnostic.Codes.UNKNOWN_CLAUSE_NAME,
                                "Unknown clause name: " + effect.name(), effect.span());
                    }
                }
            }
        }
    }

    private static boolean staticallyFieldExpression(Expr expression) {
        while (expression instanceof Group group) expression = group.expression();
        int arguments = 0;
        while (expression instanceof Apply apply) {
            arguments++;
            expression = apply.function();
        }
        return arguments == 2 && expression instanceof Name name && name.name().equals("field");
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
        if (expression instanceof ArrowContract) return ContractState.CONTRACT;
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

    private void validateContractVariables(ArrowContract arrow) {
        java.util.TreeMap<Integer, List<SourceSpan>> occurrences = new java.util.TreeMap<>();
        AstTraversal.walkPreOrder(arrow, expression -> {
            if (expression instanceof ContractVariable(int index, SourceSpan span)) {
                occurrences.computeIfAbsent(index, ignored -> new ArrayList<>()).add(span);
            }
        });
        if (occurrences.isEmpty()) return;
        int expected = 1;
        for (var entry : occurrences.entrySet()) {
            if (entry.getKey() != expected) {
                throw invalidContractVariable("Contract variable indices must be contiguous from _1",
                        entry.getValue().getFirst(), List.of());
            }
            if (entry.getValue().size() < 2) {
                throw invalidContractVariable("Contract variable must relate at least two arrow positions: _"
                        + entry.getKey(), entry.getValue().getFirst(), List.of());
            }
            expected++;
        }
    }

    private void validateHeaderVariables(FunctionDef function) {
        java.util.TreeMap<Integer, List<SourceSpan>> occurrences = new java.util.TreeMap<>();
        collectHeaderVariables(function.resultContracts(), occurrences);
        function.params().forEach(parameter -> collectHeaderVariables(parameter.contracts(), occurrences));
        if (occurrences.isEmpty()) return;
        int expected = 1;
        for (var entry : occurrences.entrySet()) {
            if (entry.getKey() != expected) {
                throw invalidContractVariable("Contract variable indices must be contiguous from _1",
                        entry.getValue().getFirst(), List.of());
            }
            if (entry.getValue().size() < 2) {
                throw invalidContractVariable("Contract variable must relate at least two header positions: _"
                        + entry.getKey(), entry.getValue().getFirst(), List.of());
            }
            expected++;
        }
        validateHeaderVariableBounds(function);
    }

    private record HeaderVariableBound(BuiltinContract contract, SourceSpan span) {}

    private void validateHeaderVariableBounds(FunctionDef function) {
        java.util.Map<Integer, List<HeaderVariableBound>> bounds = new java.util.TreeMap<>();
        collectHeaderVariableBounds(function.resultContracts(), bounds);
        function.params().forEach(parameter -> collectHeaderVariableBounds(parameter.contracts(), bounds));
        for (var entry : bounds.entrySet()) {
            List<HeaderVariableBound> values = entry.getValue();
            for (int right = 0; right < values.size(); right++) {
                for (int left = 0; left < right; left++) {
                    HeaderVariableBound earlier = values.get(left);
                    HeaderVariableBound later = values.get(right);
                    if (!ContractRelations.implies(earlier.contract(), later.contract())
                            && !ContractRelations.implies(later.contract(), earlier.contract())) {
                        throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                                Diagnostic.Codes.INCOMPATIBLE_CONTRACTS,
                                "Incompatible inferred contracts: " + earlier.contract().publicName()
                                        + " and " + later.contract().publicName(), later.span(),
                                List.of(new Diagnostic.Related("Earlier bound for _" + entry.getKey(),
                                        earlier.span()))));
                    }
                }
            }
        }
    }

    private void collectHeaderVariableBounds(Ast.ContractClause clause,
            java.util.Map<Integer, List<HeaderVariableBound>> bounds) {
        if (clause == null) return;
        List<Integer> variables = clause.names().stream().filter(name -> isContractVariable(name.name()))
                .map(name -> Integer.parseInt(name.name().substring(1))).toList();
        if (variables.isEmpty()) return;
        for (Ast.ContractName name : clause.names()) {
            BuiltinContract.named(name.name()).ifPresent(contract -> variables.forEach(variable -> bounds
                    .computeIfAbsent(variable, ignored -> new ArrayList<>())
                    .add(new HeaderVariableBound(contract, name.span()))));
        }
    }

    private void collectHeaderVariables(Ast.ContractClause clause,
                                        java.util.Map<Integer, List<SourceSpan>> occurrences) {
        if (clause == null) return;
        clause.names().forEach(name -> collectHeaderVariables(name, occurrences));
    }

    private void collectHeaderVariables(Ast.ContractName name,
                                        java.util.Map<Integer, List<SourceSpan>> occurrences) {
        if (isContractVariable(name.name())) addOccurrence(name.name(), name.span(), occurrences);
        name.arguments().forEach(argument -> collectHeaderVariables(argument, occurrences));
        if (name.inline() instanceof ArrowContract arrow) {
            headerArrows.add(arrow);
            AstTraversal.walkPreOrder(arrow, expression -> {
                if (expression instanceof ContractVariable(int index, SourceSpan span)) {
                    occurrences.computeIfAbsent(index, ignored -> new ArrayList<>()).add(span);
                }
            });
        }
    }

    private static void addOccurrence(String spelling, SourceSpan span,
                                      java.util.Map<Integer, List<SourceSpan>> occurrences) {
        try {
            int index = Integer.parseInt(spelling.substring(1));
            occurrences.computeIfAbsent(index, ignored -> new ArrayList<>()).add(span);
        } catch (NumberFormatException tooLarge) {
            throw invalidContractVariable("Contract variable index is too large", span, List.of());
        }
    }

    private static boolean isContractVariable(String name) { return name.matches("_[1-9][0-9]*"); }

    private static LangException invalidContractVariable(String message, SourceSpan span,
                                                          List<Diagnostic.Related> related) {
        return new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                Diagnostic.Codes.INVALID_CONTRACT_VARIABLE, message, span, related));
    }

    private void resolveName(Name name, Scope scope, boolean functionBody, boolean deferred) {
        int depth = 0;
        boolean premature = false;
        for (Scope current = scope; current != null; current = current.parent, depth++) {
            Symbol symbol = current.symbols.get(name.name());
            if (symbol == null) continue;
            // A function body may close over a later outer assignment because invocation happens
            // dynamically. Its own block declarations and parameters must still be initialized.
            if (!symbol.initialized() && symbol.contractState() != ContractState.CONTRACT
                    && !(functionBody && depth >= 2)) {
                if (deferred) {
                    names.put(name, binding(symbol, depth, false));
                    return;
                }
                premature = true;
                continue;
            }
            boolean captured = functionBody && depth >= 2;
            names.put(name, binding(symbol, depth, captured));
            if (captured) recordUpvalue(symbol, depth - 2, name.span());
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

    private void recordUpvalue(Symbol symbol, int lexicalDepth, SourceSpan firstUseSpan) {
        FunctionDef function = functions.peek();
        if (function == null) throw new IllegalStateException("Captured binding outside a function");
        LinkedHashMap<Integer, Resolution.Upvalue> captures = upvalues.get(function);
        captures.computeIfAbsent(symbol.id(), ignored -> new Resolution.Upvalue(captures.size(), symbol.id(),
                lexicalDepth, symbol.slot(), symbol.declaration(), firstUseSpan));
    }

    private IdentityHashMap<FunctionDef, List<Resolution.Upvalue>> resolvedUpvalues() {
        IdentityHashMap<FunctionDef, List<Resolution.Upvalue>> result = new IdentityHashMap<>();
        upvalues.forEach((function, captures) -> result.put(function, List.copyOf(captures.values())));
        return result;
    }

    private void duplicate(String name, SourceSpan span, Symbol original) {
        List<Diagnostic.Related> related = original.declaration() == null ? List.of()
                : List.of(new Diagnostic.Related("First definition of " + name, original.declaration()));
        throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                Diagnostic.Codes.DUPLICATE_DEFINITION, "Duplicate definition: " + name, span, related));
    }
}
