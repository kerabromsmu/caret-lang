package caretlang;

// Language diagnostics are process-local and are never Java-serialized.
public final class LangException extends RuntimeException {
    private final Diagnostic diagnostic;
    private final DiagnosticCatalog catalogEntry;

    public LangException(String message) {
        throw new IllegalArgumentException("Free-form language diagnostics are not permitted");
    }

    LangException(String message, SourceSpan span) {
        throw new IllegalArgumentException("Free-form language diagnostics are not permitted");
    }

    LangException(Diagnostic.Phase phase, String code, String message, SourceSpan span) {
        this(new Diagnostic(phase, code, message, span), DiagnosticCatalog.identify(phase, code, message));
    }

    LangException(Diagnostic diagnostic) {
        this(diagnostic, DiagnosticCatalog.identify(
                diagnostic.phase(), diagnostic.code(), diagnostic.message()));
    }

    private LangException(Diagnostic diagnostic, DiagnosticCatalog catalogEntry) {
        super(diagnostic.render());
        this.diagnostic = diagnostic;
        this.catalogEntry = catalogEntry;
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

    DiagnosticCatalog catalogEntry() { return catalogEntry; }

    LangException withSpanIfAbsent(SourceSpan fallback) {
        Diagnostic located = diagnostic.withPrimarySpanIfAbsent(fallback);
        return located == diagnostic ? this : new LangException(located, catalogEntry);
    }
}
