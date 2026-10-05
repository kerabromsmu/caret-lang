package caretlang;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

final class InterpreterTestSupport {
    private InterpreterTestSupport() {}
    record ModeExecution(String output, int reuseCount) {}
    record ModeFailure(String output, String code, int line, int reuseCount) {}

    static ModeExecution execute(String source, OwnershipTracker.Mode mode) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8), null,
                EffectCatalog.standard(false), mode);
        interpreter.execute(new Parser(source).parseProgram());
        return new ModeExecution(bytes.toString(StandardCharsets.UTF_8), interpreter.ownershipReuseCount());
    }

    static ModeFailure executeFailure(OwnershipTracker.Mode mode) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8), null,
                EffectCatalog.standard(false), mode);
        interpreter.execute(new Parser("base = [1]\n").parseProgram());
        LangException failure = assertThrows(LangException.class, () -> interpreter.execute(new Parser("""
                temporary = seqAdd (seqAdd (seqEmpty) 2) 3
                print (1 / 0)
                """).parseProgram()));
        interpreter.execute(new Parser("print base\n").parseProgram());
        return new ModeFailure(bytes.toString(StandardCharsets.UTF_8), failure.diagnostic().code(),
                failure.span().start().line(), interpreter.ownershipReuseCount());
    }

    static String execute(String source) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        interpreter.execute(new Parser(source).parseProgram());
        return bytes.toString(StandardCharsets.UTF_8);
    }

    static void assertDiagnostic(String source, String detail, int line, int column) {
        LangException error = assertThrows(LangException.class, () -> execute(source));
        assertDiagnosticDetails(error, detail, line, column);
    }

    static LangException expectDiagnostic(String source, String detail, int line, int column) {
        LangException error = assertThrows(LangException.class, () -> execute(source));
        assertDiagnosticDetails(error, detail, line, column);
        return error;
    }

    static void assertDiagnosticDetails(LangException error, String detail, int line, int column) {
        assertEquals(line, error.span().start().line());
        assertEquals(column, error.span().start().column());
        assertTrue(error.getMessage().contains(detail));
    }
}
