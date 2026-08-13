package caretlang;

import caretlang.Ast.Name;
import caretlang.Ast.ContractClause;
import caretlang.Ast.AmbiguousCall;

import java.util.IdentityHashMap;

final class Resolution {
    enum CallMode { PREFIX, INFIX, DYNAMIC }
    record Binding(int lexicalDepth, int slot, SourceSpan declarationSpan, boolean captured) {}
    record ContractBinding(String name, Binding binding, SourceSpan span) {}

    private final IdentityHashMap<Name, Binding> names;
    private final IdentityHashMap<ContractClause, java.util.List<ContractBinding>> contracts;
    private final IdentityHashMap<AmbiguousCall, CallMode> calls;

    Resolution(IdentityHashMap<Name, Binding> names,
               IdentityHashMap<ContractClause, java.util.List<ContractBinding>> contracts,
               IdentityHashMap<AmbiguousCall, CallMode> calls) {
        this.names = new IdentityHashMap<>(names);
        this.contracts = new IdentityHashMap<>(contracts);
        this.calls = new IdentityHashMap<>(calls);
    }

    Binding binding(Name name) {
        return names.get(name);
    }

    java.util.List<ContractBinding> contracts(ContractClause clause) {
        return clause == null ? java.util.List.of() : contracts.getOrDefault(clause, java.util.List.of());
    }

    CallMode callMode(AmbiguousCall call) { return calls.getOrDefault(call, CallMode.DYNAMIC); }
}
