package caretlang;

import java.util.Arrays;
import java.util.Optional;

enum BuiltinContract implements ContractDescriptor {
    ANY("Any") { @Override public boolean accepts(Value value) { return true; } },
    NUMBER("Number") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.NUMBER); } },
    REAL("Real") {
        @Override public boolean accepts(Value value) { return NUMBER.accepts(value); }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(NUMBER); }
    },
    INTEGER("Integer") {
        @Override public boolean accepts(Value value) {
            return numeric(value, number -> NumericValues.integral(number) != null);
        }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(REAL); }
    },
    NATURAL("Natural") {
        @Override public boolean accepts(Value value) {
            return numeric(value, number -> NumericValues.integral(number) != null
                    && NumericValues.integral(number).signum() >= 0);
        }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(INTEGER); }
    },
    INT8("Int8") { @Override public boolean accepts(Value value) { return fitsInteger(value, 8, true); }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(INTEGER); } },
    UINT8("UInt8") { @Override public boolean accepts(Value value) { return fitsInteger(value, 8, false); }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(NATURAL); } },
    INT16("Int16") { @Override public boolean accepts(Value value) { return fitsInteger(value, 16, true); }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(INTEGER); } },
    UINT16("UInt16") { @Override public boolean accepts(Value value) { return fitsInteger(value, 16, false); }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(NATURAL); } },
    INT32("Int32") { @Override public boolean accepts(Value value) { return fitsInteger(value, 32, true); }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(INTEGER); } },
    UINT32("UInt32") { @Override public boolean accepts(Value value) { return fitsInteger(value, 32, false); }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(NATURAL); } },
    INT64("Int64") { @Override public boolean accepts(Value value) { return fitsInteger(value, 64, true); }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(INTEGER); } },
    UINT64("UInt64") { @Override public boolean accepts(Value value) { return fitsInteger(value, 64, false); }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(NATURAL); } },
    FLOAT("Float") { @Override public boolean accepts(Value value) {
            return numeric(value, NumericValues::exactlyFloat);
        }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(REAL); } },
    DOUBLE("Double") { @Override public boolean accepts(Value value) {
            return numeric(value, NumericValues::exactlyDouble);
        }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(REAL); } },
    STRING("String") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.STRING); } },
    BOOLEAN("Boolean") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.BOOLEAN); } },
    EQ("Eq") { @Override public boolean accepts(Value value) { return ValueSemantics.equalityEligible(value); } },
    ERROR_TEMPLATE("ErrorTemplate") { @Override public boolean accepts(Value value) { return ErrorValues.isError(value); } },
    NULL("Null") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.NULL); } },
    MISSING("Missing") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.MISSING); } },
    CONTAINER("Container") {
        @Override public boolean accepts(Value value) { return kind(value, ValueKind.CONTAINER); }
        @Override public int parameterArity() { return 1; }
    },
    FUNCTION("Function") { @Override public boolean accepts(Value value) { return kind(value, ValueKind.FUNCTION); } },
    FIELD("Field") {
        @Override public boolean accepts(Value value) { return kind(value, ValueKind.FIELD); }
        @Override public int parameterArity() { return 2; }
    },
    COLLECTION("Collection") {
        @Override public boolean accepts(Value value) {
            return CollectionRuntime.isCollection(value);
        }
    },
    SEQUENCE("Sequence") {
        @Override public boolean accepts(Value value) {
            value = ValueSemantics.underlying(value);
            if (value instanceof Value.LazyCollection collection) value = collection.materializedValue();
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
            if (value instanceof Value.LazyCollection collection) value = collection.materializedValue();
            return value instanceof Value.EmptyCollection || ValueKind.of(value) == ValueKind.DICTIONARY;
        }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(COLLECTION); }
        @Override public int parameterArity() { return 2; }
    },
    SET("Set") {
        @Override public boolean accepts(Value value) {
            value = ValueSemantics.underlying(value);
            if (value instanceof Value.LazyCollection collection) value = collection.materializedValue();
            return value instanceof Value.EmptyCollection
                    || value instanceof Value.KeyedCollection collection
                    && collection.shape() == Value.KeyedCollection.Shape.SET
                    || value instanceof Value.SettledCollection collection
                    && collection.kind() == ValueKind.SET;
        }
        @Override public java.util.List<ContractDescriptor> bases() { return java.util.List.of(COLLECTION); }
        @Override public int parameterArity() { return 1; }
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

    private static boolean numeric(Value value, java.util.function.Predicate<Value.Num> test) {
        value = ValueSemantics.underlying(value);
        return value instanceof Value.Num number && test.test(number);
    }

    private static boolean fitsInteger(Value value, int width, boolean signed) {
        return numeric(value, number -> NumericValues.fitsInteger(number, width, signed));
    }

    static Optional<BuiltinContract> named(String name) {
        return switch (name) {
            case "Int" -> Optional.of(INTEGER);
            case "Byte" -> Optional.of(UINT8);
            case "Float32" -> Optional.of(FLOAT);
            case "Float64" -> Optional.of(DOUBLE);
            default -> Arrays.stream(values()).filter(contract -> contract.publicName.equals(name)).findFirst();
        };
    }
}
