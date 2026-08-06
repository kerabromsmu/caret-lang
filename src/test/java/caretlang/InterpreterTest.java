package caretlang;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

final class InterpreterTest {
    @Test
    void characterizesCoreLanguageBehavior() {
        String source = """
                add a b = a + b
                between low value high = value >= low and value <= high

                pair a b =
                  hidden = "private"
                  ^first = a
                  ^second = b

                print (add 2 3)
                print (true & "yes" ! unknown)
                print (false & unknown)
                inside = between 0 _ 10
                print (inside 7)
                value = pair ? ~
                print value.first
                print value.second
                print value.absent~
                print value[#first]~
                print (@value).names
                """;

        assertEquals("5\nyes\n~\ntrue\n?\n~\n~\n?\nfirst,second\n", execute(source));
    }

    @Test
    void reflectionDoesNotExposePrivateLocals() {
        String output = execute("""
                make =
                  private = 1
                  ^public = 2
                value = make
                print value.private~
                print (@value).size
                print (@value).names
                """);
        assertEquals("~\n1\npublic\n", output);
    }

    @Test
    void runtimeErrorsUseTheSmallestFailingExpressionSpan() {
        LangException error = assertThrows(LangException.class, () -> execute("print (1 + missing)"));
        assertEquals(1, error.span().start().line());
        assertEquals(12, error.span().start().column());
        assertTrue(error.getMessage().contains("Unknown name: missing"));
    }

    @Test
    void requiredMissingFieldReportsTheFieldExpression() {
        LangException error = assertThrows(LangException.class, () -> execute("""
                make =
                  ^present = true
                value = make
                print value.absent
                """));
        assertEquals(4, error.span().start().line());
        assertEquals(7, error.span().start().column());
        assertTrue(error.getMessage().contains("Scope has no exported binding: absent"));
    }

    private String execute(String source) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        interpreter.execute(new Parser(source).parseProgram());
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
