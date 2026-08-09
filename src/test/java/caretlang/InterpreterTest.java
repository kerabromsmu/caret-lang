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
    void typeAndReflectionUseTheSameRuntimeKindNames() {
        assertEquals("Null\nMissing\nNumber\n", execute("""
                print type ?
                print type ~
                print type 1
                """));
    }

    @Test
    void excessiveRecursionProducesALocatedLanguageDiagnostic() {
        LangException error = assertThrows(LangException.class, () -> execute("""
                recurse n = n == 0 & 0 ! recurse (n - 1)
                print recurse 100000
                """));
        assertEquals(Diagnostic.Codes.CALL_DEPTH_EXCEEDED, error.diagnostic().code());
        assertNotNull(error.span());
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
    void partialApplicationCapturesFixedOperandsEagerly() {
        assertEquals("captured\ncaptured\n", execute("""
                announce value = print value
                first left right = left
                partial = first (announce "captured") _
                print partial "ignored"
                """));
    }

    @Test
    void numberedHolesReorderAndReuseArguments() {
        assertEquals("321\nxx\n", execute("""
                digits a b c = a * 100 + b * 10 + c
                reordered = digits _2 2 _1
                print reordered 1 3
                join left right = left + right
                duplicate = join _1 _1
                print duplicate "x"
                """));
    }

    @Test
    void rejectsMixedNumberedAndOrdinaryHoles() {
        assertDiagnostic("partial = pair _ _1", "Cannot mix numbered and unnumbered holes", 1, 11);
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

    @Test
    void supportsDirectAndMutualRecursionThroughBlockPredeclaration() {
        assertEquals("120\ntrue\n", execute("""
                factorial n = n == 0 & 1 ! n * factorial (n - 1)
                even n = n == 0 & true ! odd (n - 1)
                odd n = n == 0 & false ! even (n - 1)
                print factorial 5
                print even 8
                """));
    }

    @Test
    void rejectsDuplicateDefinitionsAndParameters() {
        assertDiagnostic("value = 1\nvalue = 2", "Duplicate definition: value", 2, 1);
        assertDiagnostic("same value value = value", "Duplicate parameter: value", 1, 1);
    }

    @Test
    void reportsReadsBeforeSequentialDeclarations() {
        assertDiagnostic("first = second\nsecond = 2", "Unknown name: second", 1, 9);
    }

    @Test
    void rejectsInvalidNumericResultsAndCallableEquality() {
        assertDiagnostic("print 1 / 0", "Division by zero", 1, 7);
        assertDiagnostic("print 1 % 0", "Division by zero", 1, 7);
        assertDiagnostic("identity x = x\nprint identity == identity",
                "Callable values cannot be compared for equality", 2, 7);
    }

    @Test
    void comparesExportedScopesStructurally() {
        assertEquals("true\n", execute("""
                make value =
                  ^value = value
                print make 1 == make 1
                """));
    }

    @Test
    void providesUnicodeCodePointTextPrimitives() {
        assertEquals("2\n🙂\na\n~\n42.5\n~\n42.5\n", execute("""
                text = "🙂a"
                print textSize text
                print textAt text 0
                print textSlice text 1 2
                print textAt text 2
                print textNumber "42.5"
                print textNumber "nope"
                print numberText 42.5
                """));
    }

    @Test
    void providesPersistentSequencesAndInsertionOrderedDictionaries() {
        assertEquals("0\n2\n1\n~\nfalse\ntrue\n~\n1\nfirst,missing\n", execute("""
                empty = seqEmpty
                values = seqAdd (seqAdd empty 1) ~
                print seqSize empty
                print seqSize values
                print seqGet values 0
                print seqGet values 5

                base = dictEmpty
                withFirst = dictPut base #first 1
                complete = dictPut withFirst "missing" ~
                print dictHas base #first
                print dictHas complete "missing"
                print dictGet complete #missing
                print dictGet complete #first
                print (@complete).names
                """));
    }

    @Test
    void persistentCollectionsKeepOlderValuesAndDictionaryReplacementOrder() {
        assertEquals("[1]\n[1, 2]\n[#first, #second]\n22\n", execute("""
                first = seqAdd seqEmpty 1
                second = seqAdd first 2
                print first
                print second
                dictionary = dictPut (dictPut (dictPut dictEmpty #first 1) #second 2) #first 22
                print dictKeys dictionary
                print dictGet dictionary #first
                """));
    }

    @Test
    void collectionEqualityIsStructural() {
        assertEquals("true\ntrue\n", execute("""
                left = seqAdd seqEmpty 1
                right = seqAdd seqEmpty 1
                print left == right
                first = dictPut dictEmpty #value left
                second = dictPut dictEmpty "value" right
                print first == second
                """));
    }

    @Test
    void evaluatesGroupedMultilineCallsAndLookups() {
        assertEquals("6\n42\n", execute("""
                add a b = a + b
                result = (
                  add
                    1
                    (add 2 3)
                )
                print result

                make =
                  ^answer = 42
                scope = make
                print scope[
                  #answer
                ]~
                """));
    }

    @Test
    void testAssertionsCollectFailuresAndReturnMissing() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        TestReporter reporter = new TestReporter(output);
        Interpreter interpreter = new Interpreter(output, reporter);

        interpreter.execute(new Parser("""
                print assert "true condition" true
                assert "false condition" false
                assertEqual "null differs from missing" ? ~
                assertEqual "structural sequence equality" (seqAdd seqEmpty 1) (seqAdd seqEmpty 1)
                """).parseProgram());
        assertFalse(reporter.finish());

        assertEquals("""
                PASS: true condition
                ~
                FAIL: false condition (Line 2, column 1)
                  expected: true
                  actual: false
                FAIL: null differs from missing (Line 3, column 1)
                  expected: ~
                  actual: ?
                PASS: structural sequence equality
                Summary: 4 tests, 2 passed, 2 failed
                """, bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void testAssertionsValidateTheirArgumentsWithLocatedErrors() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TestReporter reporter = new TestReporter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        Interpreter interpreter = new Interpreter(new PrintStream(bytes), reporter);

        LangException condition = assertThrows(LangException.class, () -> interpreter.execute(
                new Parser("assert \"boolean required\" 1").parseProgram()));
        assertEquals(1, condition.span().start().line());
        assertEquals(1, condition.span().start().column());
        assertTrue(condition.getMessage().contains("Assertion condition must be Boolean"));
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
