package caretlang;

import java.util.List;

record Diagnostic(Phase phase, String code, String message, SourceSpan primarySpan,
                  List<Related> related) {
    enum Phase { LEXER, PARSER, RUNTIME }

    record Related(String message, SourceSpan span) {}

    Diagnostic {
        related = List.copyOf(related);
    }

    Diagnostic(Phase phase, String code, String message, SourceSpan primarySpan) {
        this(phase, code, message, primarySpan, List.of());
    }

    Diagnostic withPrimarySpanIfAbsent(SourceSpan fallback) {
        return primarySpan == null
                ? new Diagnostic(phase, code, message, fallback, related)
                : this;
    }

    String render() {
        if (primarySpan == null) return message;
        SourcePosition start = primarySpan.start();
        return "Line " + start.line() + ", column " + start.column() + ": " + message;
    }
}
