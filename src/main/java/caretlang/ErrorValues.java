package caretlang;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Language-owned ErrorTemplate value conversion and validation. */
final class ErrorValues {
    private static final List<String> ERROR_FIELDS = List.of(
            "cause", "code", "details", "location", "message", "phase", "related");
    private ErrorValues() {}

    static Value fromDiagnostic(Diagnostic diagnostic) {
        return error(diagnostic, 0);
    }

    private static Value error(Diagnostic diagnostic, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Diagnostic cause chain is too deep");
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        fields.put("code", new Value.Str(diagnostic.code()));
        fields.put("phase", new Value.Str(diagnostic.phase().name().toLowerCase(java.util.Locale.ROOT)));
        fields.put("message", new Value.Str(diagnostic.message()));
        fields.put("location", diagnostic.primarySpan() == null
                ? Value.Missing.INSTANCE : location(diagnostic.primarySpan()));
        fields.put("related", new Value.Seq(diagnostic.related().stream()
                .map(ErrorValues::related).toList()));
        fields.put("cause", diagnostic.cause() == null
                ? Value.Missing.INSTANCE : error(diagnostic.cause(), depth + 1));
        fields.put("details", diagnostic.details());
        return new Value.Dictionary(fields);
    }

    private static Value location(SourceSpan span) {
        return new Value.Dictionary(Map.of(
                "line", new Value.Num(span.start().line()),
                "column", new Value.Num(span.start().column()),
                "endLine", new Value.Num(span.end().line()),
                "endColumn", new Value.Num(span.end().column())));
    }

    private static Value related(Diagnostic.Related related) {
        return new Value.Dictionary(Map.of(
                "message", new Value.Str(related.message()),
                "location", location(related.span())));
    }

    static boolean isError(Value candidate) {
        return isError(candidate, 0);
    }

    private static boolean isError(Value candidate, int depth) {
        if (depth > 64) return false;
        candidate = ValueSemantics.underlying(candidate);
        if (!(candidate instanceof Value.Dictionary error)
                || !List.copyOf(error.entries().keySet()).equals(ERROR_FIELDS)) return false;
        if (!(raw(error, "code") instanceof Value.Str)
                || !(raw(error, "phase") instanceof Value.Str phase)
                || !(raw(error, "message") instanceof Value.Str)
                || !validPhase(phase.value())
                || !validLocation(raw(error, "location"))
                || !validRelated(raw(error, "related"))
                || !isCollection(raw(error, "details"))) return false;
        Value cause = raw(error, "cause");
        return cause == Value.Missing.INSTANCE || isError(cause, depth + 1);
    }

    private static Value raw(Value.Dictionary dictionary, String name) {
        return ValueSemantics.underlying(dictionary.entries().get(name));
    }

    private static boolean validPhase(String phase) {
        try {
            Diagnostic.Phase.valueOf(phase.toUpperCase(java.util.Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean validLocation(Value value) {
        if (value == Value.Missing.INSTANCE) return true;
        if (!(value instanceof Value.Dictionary location)) return false;
        return List.copyOf(location.entries().keySet()).equals(
                List.of("column", "endColumn", "endLine", "line"))
                && location.entries().values().stream()
                .map(ValueSemantics::underlying).allMatch(Value.Num.class::isInstance);
    }

    private static boolean validRelated(Value value) {
        if (value instanceof Value.EmptyCollection) return true;
        if (!(value instanceof Value.Seq related)) return false;
        return related.values().stream().map(ValueSemantics::underlying).allMatch(item ->
                item instanceof Value.Dictionary note
                        && List.copyOf(note.entries().keySet()).equals(List.of("location", "message"))
                        && ValueSemantics.underlying(note.entries().get("message")) instanceof Value.Str
                        && validLocation(ValueSemantics.underlying(note.entries().get("location"))));
    }

    private static boolean isCollection(Value value) {
        return value instanceof Value.EmptyCollection || value instanceof Value.Seq
                || value instanceof Value.Dictionary || value instanceof Value.ProjectedDictionary;
    }
}
