package caretlang;

record SourcePosition(int offset, int line, int column) {
    SourcePosition {
        if (offset < 0) throw new IllegalArgumentException("offset must be non-negative");
        if (line < 1) throw new IllegalArgumentException("line must be one-based");
        if (column < 1) throw new IllegalArgumentException("column must be one-based");
    }
}

record SourceSpan(SourcePosition start, SourcePosition end) {
    SourceSpan {
        if (end.offset() < start.offset()) throw new IllegalArgumentException("span end precedes start");
    }

    static SourceSpan cover(SourceSpan first, SourceSpan last) {
        return new SourceSpan(first.start(), last.end());
    }

    static SourceSpan point(SourcePosition position) {
        return new SourceSpan(position, position);
    }
}
