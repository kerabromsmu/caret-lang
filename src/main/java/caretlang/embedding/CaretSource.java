package caretlang.embedding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public record CaretSource(String name, String text, Path path) {
    public CaretSource {
        Objects.requireNonNull(name, "source name");
        Objects.requireNonNull(text, "source text");
        if (name.isBlank()) throw new IllegalArgumentException("source name must not be blank");
    }

    public static CaretSource text(String name, String text) {
        return new CaretSource(name, text, null);
    }

    public static CaretSource path(Path path) throws IOException {
        Path absolute = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        return new CaretSource(absolute.toString(), Files.readString(absolute), absolute);
    }
}
