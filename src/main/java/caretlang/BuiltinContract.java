package caretlang;

import java.util.Arrays;
import java.util.Optional;

enum BuiltinContract {
    ANY("Any") { @Override boolean accepts(Value value) { return true; } },
    NUMBER("Number") { @Override boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.NUMBER); } },
    STRING("String") { @Override boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.STRING); } },
    BOOLEAN("Boolean") { @Override boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.BOOLEAN); } },
    NULL("Null") { @Override boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.NULL); } },
    MISSING("Missing") { @Override boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.MISSING); } },
    FUNCTION("Function") { @Override boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.FUNCTION); } },
    SCOPE("Scope") { @Override boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.SCOPE); } },
    SEQUENCE("Sequence") { @Override boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.SEQUENCE); } },
    DICTIONARY("Dictionary") { @Override boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.DICTIONARY); } };

    private final String publicName;
    BuiltinContract(String publicName) { this.publicName = publicName; }
    String publicName() { return publicName; }
    abstract boolean accepts(Value value);

    private static boolean kind(Value value, ValueSemantics.Descriptor descriptor) {
        return ValueSemantics.descriptor(value) == descriptor;
    }

    static Optional<BuiltinContract> named(String name) {
        return Arrays.stream(values()).filter(contract -> contract.publicName.equals(name)).findFirst();
    }
}
