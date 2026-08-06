package caretlang;

public final class LangException extends RuntimeException {
    private final String detail;
    private final SourceSpan span;

    public LangException(String message) {
        this(message, null);
    }

    LangException(String message, SourceSpan span) {
        super(format(message, span));
        this.detail = message;
        this.span = span;
    }

    String detail() {
        return detail;
    }

    SourceSpan span() {
        return span;
    }

    LangException withSpanIfAbsent(SourceSpan fallback) {
        return span == null ? new LangException(detail, fallback) : this;
    }

    private static String format(String message, SourceSpan span) {
        if (span == null) return message;
        SourcePosition start = span.start();
        return "Line " + start.line() + ", column " + start.column() + ": " + message;
    }
}
