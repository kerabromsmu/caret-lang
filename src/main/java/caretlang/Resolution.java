package caretlang;

import caretlang.Ast.Name;
import caretlang.Ast.ContractClause;

import java.util.IdentityHashMap;

final class Resolution {
    record Binding(int lexicalDepth, int slot, SourceSpan declarationSpan, boolean captured) {}

    private final IdentityHashMap<Name, Binding> names;
    private final IdentityHashMap<ContractClause, java.util.List<BuiltinContract>> contracts;

    Resolution(IdentityHashMap<Name, Binding> names,
               IdentityHashMap<ContractClause, java.util.List<BuiltinContract>> contracts) {
        this.names = new IdentityHashMap<>(names);
        this.contracts = new IdentityHashMap<>(contracts);
    }

    Binding binding(Name name) {
        return names.get(name);
    }

    java.util.List<BuiltinContract> contracts(ContractClause clause) {
        return clause == null ? java.util.List.of() : contracts.getOrDefault(clause, java.util.List.of());
    }
}
