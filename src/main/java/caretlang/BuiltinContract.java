package caretlang;

import java.util.Arrays;
import java.util.Optional;

enum BuiltinContract implements ContractDescriptor {
    ANY("Any") { @Override public boolean accepts(Value value) { return true; } },
    NUMBER("Number") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.NUMBER); } },
    STRING("String") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.STRING); } },
    BOOLEAN("Boolean") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.BOOLEAN); } },
    NULL("Null") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.NULL); } },
    MISSING("Missing") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.MISSING); } },
    FUNCTION("Function") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.FUNCTION); } },
    FIELD("Field") {
        @Override public boolean accepts(Value value) { return kind(value, ValueKind.FIELD); }
        @Override public int parameterArity() { return 2; }
    },
    COLLECTION("Collection") {
        @Override public boolean accepts(Value value) {
            value = ValueSemantics.underlying(value);
            return value instanceof Value.Dictionary || value instanceof Value.EmptyCollection
                    || value instanceof Value.Seq;
        }
    },
    SEQUENCE("Sequence") {
        @Override public boolean accepts(Value value) {
            value = ValueSemantics.underlying(value);
            return value instanceof Value.EmptyCollection || ValueKind.of(value) == ValueKind.SEQUENCE;
        }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(COLLECTION); }
        @Override public int parameterArity() { return 1; }
        @Override public ContractDescriptor parameterize(java.util.List<ContractDescriptor> arguments) {
            if (arguments.size() != 1) throw new IllegalArgumentException("Sequence requires one contract argument");
            return new ParameterizedContract(this, arguments);
        }
    },
    DICTIONARY("Dictionary") {
        @Override public boolean accepts(Value value) {
            value = ValueSemantics.underlying(value);
            return value instanceof Value.EmptyCollection || ValueKind.of(value) == ValueKind.DICTIONARY;
        }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(COLLECTION); }
        @Override public int parameterArity() { return 2; }
    };

    private final String publicName;
    BuiltinContract(String publicName) { this.publicName = publicName; }
    public String publicName() { return publicName; }
    public abstract boolean accepts(Value value);

    @Override public ContractDescriptor parameterize(java.util.List<ContractDescriptor> arguments) {
        if (parameterArity() == 0 || arguments.isEmpty() || arguments.size() > parameterArity()) {
            throw new IllegalArgumentException("Incorrect contract parameter count for " + publicName);
        }
        return new ParameterizedContract(this, arguments);
    }

    private static boolean kind(Value value, ValueKind descriptor) {
        value = ValueSemantics.underlying(value);
        return ValueKind.of(value) == descriptor;
    }

    static Optional<BuiltinContract> named(String name) {
        return Arrays.stream(values()).filter(contract -> contract.publicName.equals(name)).findFirst();
    }
}
