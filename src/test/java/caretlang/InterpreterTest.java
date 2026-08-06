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
    void reflectionRefersToFunctionsWithoutInvokingThem() {
        assertEquals("Function\n0\nFunction\n1\n", execute("""
                zero =
                  ^called = true
                identity value = value

                print (@zero).kind
                print (@zero).remaining
                print (@identity).kind
                print (@identity).remaining
                """));
    }

    @Test
    void holesBecomeFutureArgumentsInLeftToRightOrder() {
        assertEquals("123\nno\n", execute("""
                digits a b c = a * 100 + b * 10 + c
                rearranged = digits _ 2 _
                print rearranged 1 3
                choose = false & _ ! _
                print choose "yes" "no"
                """));
    }

    @Test
    void conditionalEvaluatesOnlyTheSelectedBranch() {
        assertEquals("yes\nno\n", execute("""
                print true & "yes" ! absent
                print false & absent ! "no"
                """));
    }

    @Test
    void optionalMissingFieldsAreValuesButInvalidOperationsAreDiagnostics() {
        assertEquals("~\n", execute("""
                make =
                  ^present = true
                value = make
                print value.absent~
                """));

        assertDiagnostic("print (1).absent~", "Field access requires a scope", 1, 7);
        assertDiagnostic("print 1 + true", "Expected number", 1, 7);
        assertDiagnostic("print 1 2", "Value is not callable", 1, 7);
        assertDiagnostic("print 1 & true ! false", "Condition must be Boolean", 1, 7);
    }

    @Test
    void dynamicLookupRequiresANameOrString() {
        LangException error = assertThrows(LangException.class, () -> execute("""
                make =
                  ^value = 1
                scope = make
                print scope[42]~
                """));
        assertEquals(4, error.span().start().line());
        assertEquals(7, error.span().start().column());
        assertTrue(error.getMessage().contains("Dynamic field name must be a name or string"));
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

    private void assertDiagnostic(String source, String detail, int line, int column) {
        LangException error = assertThrows(LangException.class, () -> execute(source));
        assertEquals(line, error.span().start().line());
        assertEquals(column, error.span().start().column());
        assertTrue(error.getMessage().contains(detail));
    }
}
