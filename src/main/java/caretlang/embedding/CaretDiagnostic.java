package caretlang.embedding;

import java.util.List;
import java.util.Objects;

public record CaretDiagnostic(String code, Phase phase, String message, Location location, List<Note> notes) {
    public enum Phase { LEXER, PARSER, SEMANTIC, RUNTIME, LOWERING, COMPILER }
    public record Location(String sourceName, int startLine, int startColumn, int endLine, int endColumn) {}
    public record Note(String message, Location location) {}

    public CaretDiagnostic {
        Objects.requireNonNull(code);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(message);
        notes = List.copyOf(notes);
    }
}
