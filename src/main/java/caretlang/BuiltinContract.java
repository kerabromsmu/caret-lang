package caretlang;

import java.util.Arrays;
import java.util.Optional;

enum BuiltinContract implements ContractDescriptor {
    ANY("Any") { @Override public boolean accepts(Value value) { return true; } },
    NUMBER("Number") { @Override public boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.NUMBER); } },
    STRING("String") { @Override public boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.STRING); } },
    BOOLEAN("Boolean") { @Override public boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.BOOLEAN); } },
    NULL("Null") { @Override public boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.NULL); } },
    MISSING("Missing") { @Override public boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.MISSING); } },
    FUNCTION("Function") { @Override public boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.FUNCTION); } },
    SCOPE("Scope") { @Override public boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.SCOPE); } },
    SEQUENCE("Sequence") { @Override public boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.SEQUENCE); } },
    DICTIONARY("Dictionary") { @Override public boolean accepts(Value value) { return kind(value, ValueSemantics.Descriptor.DICTIONARY); } };

    private final String publicName;
    BuiltinContract(String publicName) { this.publicName = publicName; }
    public String publicName() { return publicName; }
    public abstract boolean accepts(Value value);

    private static boolean kind(Value value, ValueSemantics.Descriptor descriptor) {
        if (value instanceof Value.Attributed attributed) value = attributed.value();
        return ValueSemantics.descriptor(value) == descriptor;
    }

    static Optional<BuiltinContract> named(String name) {
        return Arrays.stream(values()).filter(contract -> contract.publicName.equals(name)).findFirst();
    }
}
