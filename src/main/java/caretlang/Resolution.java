package caretlang;

import caretlang.Ast.Name;

import java.util.IdentityHashMap;

final class Resolution {
    record Binding(int lexicalDepth, int slot, SourceSpan declarationSpan, boolean captured) {}

    private final IdentityHashMap<Name, Binding> names;

    Resolution(IdentityHashMap<Name, Binding> names) {
        this.names = new IdentityHashMap<>(names);
    }

    Binding binding(Name name) {
        return names.get(name);
    }
}
