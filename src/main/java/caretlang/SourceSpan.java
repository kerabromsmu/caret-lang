package caretlang;

public record SourceSpan(SourcePosition start, SourcePosition end) {
    public SourceSpan {
        if (end.offset() < start.offset()) throw new IllegalArgumentException("span end precedes start");
    }

    static SourceSpan cover(SourceSpan first, SourceSpan last) {
        return new SourceSpan(first.start(), last.end());
    }

    static SourceSpan point(SourcePosition position) {
        return new SourceSpan(position, position);
    }
}
