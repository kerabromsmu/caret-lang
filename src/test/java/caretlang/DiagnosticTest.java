package caretlang;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

final class DiagnosticTest {
    @Test
    void usesStableCodesAndLocationsAcrossPhases() {
        LangException lexical = assertThrows(LangException.class, () -> Lexer.lex("$"));
        assertDiagnostic(lexical, Diagnostic.Phase.LEXER,
                Diagnostic.Codes.LEX_UNEXPECTED_CHARACTER, 1, 1);

        LangException parse = assertThrows(LangException.class, () -> new Parser("print (").parseProgram());
        assertDiagnostic(parse, Diagnostic.Phase.PARSER,
                Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, 1, 8);

        LangException runtime = assertThrows(LangException.class, () -> execute("print 1 / 0"));
        assertDiagnostic(runtime, Diagnostic.Phase.RUNTIME,
                Diagnostic.Codes.DIVISION_BY_ZERO, 1, 11);

        LangException unknown = assertThrows(LangException.class, () -> execute("print absent"));
        assertDiagnostic(unknown, Diagnostic.Phase.RUNTIME,
                Diagnostic.Codes.UNKNOWN_NAME, 1, 7);
    }

    @Test
    void duplicateDefinitionsIncludeTheOriginalDeclaration() {
        LangException error = assertThrows(LangException.class,
                () -> execute("value = 1\nvalue = 2"));

        assertDiagnostic(error, Diagnostic.Phase.SEMANTIC,
                Diagnostic.Codes.DUPLICATE_DEFINITION, 2, 1);
        assertEquals(1, error.diagnostic().related().size());
        Diagnostic.Related original = error.diagnostic().related().getFirst();
        assertEquals("First definition of value", original.message());
        assertEquals(1, original.span().start().line());
        assertEquals(1, original.span().start().column());
        assertTrue(error.getMessage().contains("Note: Line 1, column 1"));
    }

    private void execute(String source) {
        new Interpreter(new PrintStream(new ByteArrayOutputStream()))
                .execute(new Parser(source).parseProgram());
    }

    private void assertDiagnostic(LangException error, Diagnostic.Phase phase, String code,
                                  int line, int column) {
        assertEquals(phase, error.diagnostic().phase());
        assertEquals(code, error.diagnostic().code());
        assertNotNull(error.span());
        assertEquals(line, error.span().start().line());
        assertEquals(column, error.span().start().column());
    }
}
