package caretlang;

import java.util.Objects;

/** One authoritative classification of runtime values into public Caret kinds. */
enum ValueKind {
    NUMBER("Number"), STRING("String"), BOOLEAN("Boolean"), NULL("Null"), MISSING("Missing"),
    COLLECTION("Collection"), SEQUENCE("Sequence"), DICTIONARY("Dictionary"), FUNCTION("Function"),
    CONTRACT("Contract"), REFLECTIVE("Reflective");

    private final String publicName;
    ValueKind(String publicName) { this.publicName = publicName; }
    String publicName() { return publicName; }

    static ValueKind of(Value input) {
        Objects.requireNonNull(input);
        Value value = ValueSemantics.underlying(input);
        return switch (value) {
            case Value.Num ignored -> NUMBER;
            case Value.Str ignored -> STRING;
            case Value.Bool ignored -> BOOLEAN;
            case Value.Null ignored -> NULL;
            case Value.Missing ignored -> MISSING;
            case Value.NamedCollection ignored -> COLLECTION;
            case Value.EmptyCollection ignored -> COLLECTION;
            case Value.Seq ignored -> SEQUENCE;
            case Value.Dict ignored -> DICTIONARY;
            case Value.FunctionReference ignored -> FUNCTION;
            case Value.ContractValue ignored -> CONTRACT;
            case Value.Attributed attributed -> of(attributed.value());
            case Value.Reflective ignored -> REFLECTIVE;
            case Value.Callable ignored -> FUNCTION;
        };
    }
}
