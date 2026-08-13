package caretlang;

import java.util.Arrays;
import java.util.Optional;

enum BuiltinContract {
    ANY("Any") { @Override boolean accepts(Value value) { return true; } },
    NUMBER("Number") { @Override boolean accepts(Value value) { return value instanceof Value.Num; } },
    STRING("String") { @Override boolean accepts(Value value) { return value instanceof Value.Str; } },
    BOOLEAN("Boolean") { @Override boolean accepts(Value value) { return value instanceof Value.Bool; } },
    NULL("Null") { @Override boolean accepts(Value value) { return value == Value.Null.INSTANCE; } },
    MISSING("Missing") { @Override boolean accepts(Value value) { return value == Value.Missing.INSTANCE; } },
    FUNCTION("Function") {
        @Override boolean accepts(Value value) {
            return value instanceof Value.Callable && !(value instanceof Value.ContractValue)
                    || value instanceof Value.FunctionReference;
        }
    },
    SCOPE("Scope") { @Override boolean accepts(Value value) { return value instanceof Value.Scope; } },
    SEQUENCE("Sequence") { @Override boolean accepts(Value value) { return value instanceof Value.Seq; } },
    DICTIONARY("Dictionary") { @Override boolean accepts(Value value) { return value instanceof Value.Dict; } };

    private final String publicName;
    BuiltinContract(String publicName) { this.publicName = publicName; }
    String publicName() { return publicName; }
    abstract boolean accepts(Value value);

    static Optional<BuiltinContract> named(String name) {
        return Arrays.stream(values()).filter(contract -> contract.publicName.equals(name)).findFirst();
    }
}
