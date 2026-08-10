package caretlang;

public record SourcePosition(int offset, int line, int column) {
    public SourcePosition {
        if (offset < 0) throw new IllegalArgumentException("offset must be non-negative");
        if (line < 1) throw new IllegalArgumentException("line must be one-based");
        if (column < 1) throw new IllegalArgumentException("column must be one-based");
    }
}
