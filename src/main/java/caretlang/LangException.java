package caretlang;

public final class LangException extends RuntimeException {
    private final Diagnostic diagnostic;

    public LangException(String message) {
        this(new Diagnostic(Diagnostic.Phase.RUNTIME, "RUNTIME_ERROR", message, null));
    }

    LangException(String message, SourceSpan span) {
        this(new Diagnostic(Diagnostic.Phase.RUNTIME, "RUNTIME_ERROR", message, span));
    }

    LangException(Diagnostic.Phase phase, String code, String message, SourceSpan span) {
        this(new Diagnostic(phase, code, message, span));
    }

    LangException(Diagnostic diagnostic) {
        super(diagnostic.render());
        this.diagnostic = diagnostic;
    }

    String detail() {
        return diagnostic.message();
    }

    SourceSpan span() {
        return diagnostic.primarySpan();
    }

    Diagnostic diagnostic() {
        return diagnostic;
    }

    LangException withSpanIfAbsent(SourceSpan fallback) {
        Diagnostic located = diagnostic.withPrimarySpanIfAbsent(fallback);
        return located == diagnostic ? this : new LangException(located);
    }
}
