package caretlang;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ErrorValuesTest {
    private static SourceSpan span(int line, int column, int endColumn) {
        return new SourceSpan(new SourcePosition(0, line, column),
                new SourcePosition(endColumn - column, line, endColumn));
    }

    @Test
    void diagnosticsAndExpectedFailuresShareTheExactErrorTemplateShape() {
        Diagnostic cause = new Diagnostic(Diagnostic.Phase.LEXER,
                Diagnostic.Codes.LEX_INVALID_ESCAPE, "Invalid escape", span(1, 4, 6));
        Diagnostic diagnostic = new Diagnostic(Diagnostic.Phase.RUNTIME,
                Diagnostic.Codes.CONTRACT_VIOLATION, "Rejected value", span(3, 7, 12),
                List.of(new Diagnostic.Related("Required here", span(2, 2, 5))), cause,
                new Value.Dictionary(Map.of("contract", new Value.Str("Positive"))));

        Value error = diagnostic.errorValue();
        assertTrue(BuiltinContract.ERROR_TEMPLATE.accepts(error));
        Value.Dictionary fields = assertInstanceOf(Value.Dictionary.class, error);
        assertEquals(List.of("cause", "code", "details", "location", "message", "phase", "related"),
                List.copyOf(fields.entries().keySet()));
        assertEquals(new Value.Str("runtime"), fields.entries().get("phase"));
        assertTrue(BuiltinContract.ERROR_TEMPLATE.accepts(fields.entries().get("cause")));
        assertEquals(error, diagnostic.errorValue());
        assertTrue(diagnostic.render().contains("Caused by: Line 1, column 4: Invalid escape"));

        for (Diagnostic.Phase phase : Diagnostic.Phase.values()) {
            assertTrue(BuiltinContract.ERROR_TEMPLATE.accepts(new Diagnostic(
                    phase, "TEST", "message", null).errorValue()), phase.name());
        }
    }

    @Test
    void errorTemplateRejectsMalformedRecursiveAndOptionalMembers() {
        Value valid = new Diagnostic(Diagnostic.Phase.PARSER,
                Diagnostic.Codes.PARSE_INVALID_SYNTAX, "bad", null).errorValue();
        Value.Dictionary fields = (Value.Dictionary) valid;

        java.util.LinkedHashMap<String, Value> badPhase = new java.util.LinkedHashMap<>(fields.entries());
        badPhase.put("phase", new Value.Str("host"));
        assertFalse(BuiltinContract.ERROR_TEMPLATE.accepts(new Value.Dictionary(badPhase)));

        java.util.LinkedHashMap<String, Value> missingField = new java.util.LinkedHashMap<>(fields.entries());
        missingField.remove("details");
        assertFalse(BuiltinContract.ERROR_TEMPLATE.accepts(new Value.Dictionary(missingField)));

        java.util.LinkedHashMap<String, Value> badCause = new java.util.LinkedHashMap<>(fields.entries());
        badCause.put("cause", new Value.Str("java.lang.Exception"));
        assertFalse(BuiltinContract.ERROR_TEMPLATE.accepts(new Value.Dictionary(badCause)));
    }
}
