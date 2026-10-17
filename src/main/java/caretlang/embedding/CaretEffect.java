package caretlang.embedding;

import java.util.Objects;

public record CaretEffect(String name) {
    public static final CaretEffect OUTPUT = new CaretEffect("Output");
    public static final CaretEffect STATE_READ = new CaretEffect("StateRead");
    public static final CaretEffect STATE_WRITE = new CaretEffect("StateWrite");

    public CaretEffect {
        Objects.requireNonNull(name, "effect name");
        if (!name.matches("[A-Z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("Invalid Caret effect name: " + name);
        }
    }
}
