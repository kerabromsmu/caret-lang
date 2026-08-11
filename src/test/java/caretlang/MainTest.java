package caretlang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class MainTest {
    @TempDir Path temporaryDirectory;

    @Test
    void fileModeReturnsSuccessAndWritesProgramOutput() throws Exception {
        Path program = temporaryDirectory.resolve("success.caret");
        Files.writeString(program, "print (1 + 2)\n");
        Invocation invocation = run(program);

        assertEquals(0, invocation.exitCode());
        assertEquals("3\n", invocation.output());
        assertEquals("", invocation.error());
    }

    @Test
    void fileModeReturnsFailureAndWritesLocatedDiagnostic() throws Exception {
        Path program = temporaryDirectory.resolve("failure.caret");
        Files.writeString(program, "print absent\n");
        Invocation invocation = run(program);

        assertEquals(1, invocation.exitCode());
        assertEquals("", invocation.output());
        assertTrue(invocation.error().contains("Error: Line 1, column 7: Unknown name: absent"));
    }

    @Test
    void stabilizationProgramRunsEndToEnd() throws Exception {
        Path program = temporaryDirectory.resolve("stabilization.caret");
        Files.writeString(program, """
                add a b =
                  a + b

                max a b =
                  a > b & a ! b

                between low value high =
                  value >= low and value <= high

                inside = between 0 _ 10

                makeA n =
                  hidden = n * 2
                  ^name = "A"
                  ^count = hidden

                makeB =
                  ^name = "B"
                  ^enabled = true

                source = true & makeA 5 ! makeB

                print add 2 3
                print max 4 7
                print inside 5
                print source.name
                print source.count~
                print source.enabled~

                field = "count"
                print source[field]~
                print @source
                """);

        Invocation invocation = run(program);

        assertEquals(0, invocation.exitCode());
        assertEquals("""
                5
                7
                true
                A
                10
                ~
                10
                ^{kind = Scope, size = 2, names = name,count}
                """, invocation.output());
        assertEquals("", invocation.error());
    }

    @Test
    void parseFailuresAreLocatedAndDoNotLeakAStackTrace() throws Exception {
        Path program = temporaryDirectory.resolve("parse-failure.caret");
        Files.writeString(program, "value = (1 + )\n");
        Invocation invocation = run(program);

        assertEquals(1, invocation.exitCode());
        assertTrue(invocation.error().contains("Error: Line 1, column 14:"));
        assertFalse(invocation.error().contains("Exception in thread"));
        assertFalse(invocation.error().contains("caretlang.Parser"));
    }

    @Test
    void fileSystemFailuresAreReportedWithoutAStackTrace() {
        Path missing = temporaryDirectory.resolve("missing.caret");
        Invocation invocation = run(missing);

        assertEquals(1, invocation.exitCode());
        assertTrue(invocation.error().contains("Cannot read Caret source file"));
        assertTrue(invocation.error().contains(missing.toString()));
        assertFalse(invocation.error().contains("Exception in thread"));
    }

    @Test
    void rejectsExtraFileModeArguments() {
        Invocation invocation = run("one.caret", "two.caret");
        assertEquals(1, invocation.exitCode());
        assertEquals("Usage: caret <file> | caret test <file>\n", invocation.error());
    }

    @Test
    void hostCatalogMessagesHaveExactOutput() {
        assertEquals("Usage: caret test <file>\n", run("test").error());
        assertEquals("Error: Cannot read Caret test file sample.caret: unavailable",
                HostMessageCatalog.TEST_READ_FAILURE.format(Path.of("sample.caret"), "unavailable"));
        assertEquals("Error: Caret REPL requires an interactive terminal. "
                        + "Run ./repl.sh from a terminal window.",
                HostMessageCatalog.REPL_TERMINAL_REQUIRED.format());
        assertEquals("Warning: Cannot write REPL history at history; using in-memory history: denied",
                HostMessageCatalog.REPL_HISTORY_WRITE.format(Path.of("history"), "denied"));
    }

    @Test
    void testModeRunsOneFileAndCollectsAssertionFailures() throws Exception {
        Path program = temporaryDirectory.resolve("mixed-tests.caret");
        Files.writeString(program, """
                assert "addition" (1 + 2 == 3)
                assertEqual "wrong total" (2 + 2) 5
                assert "still runs" true
                """);

        Invocation invocation = run("test", program.toString());

        assertEquals(1, invocation.exitCode());
        assertEquals("""
                PASS: addition
                FAIL: wrong total (Line 2, column 1)
                  expected: 5
                  actual: 4
                PASS: still runs
                Summary: 3 tests, 2 passed, 1 failed
                """, invocation.output());
        assertEquals("", invocation.error());
    }

    @Test
    void testModeSucceedsOnlyWhenAtLeastOneAssertionPasses() throws Exception {
        Path passing = temporaryDirectory.resolve("passing-tests.caret");
        Files.writeString(passing, "assertEqual \"answer\" 42 42\n");
        Invocation success = run("test", passing.toString());
        assertEquals(0, success.exitCode());
        assertEquals("PASS: answer\nSummary: 1 test, 1 passed, 0 failed\n", success.output());

        Path empty = temporaryDirectory.resolve("empty-tests.caret");
        Files.writeString(empty, "value = 42\n");
        Invocation noTests = run("test", empty.toString());
        assertEquals(1, noTests.exitCode());
        assertEquals("No tests found.\nSummary: 0 tests, 0 passed, 0 failed\n", noTests.output());
    }

    @Test
    void testModeAbortsOnEvaluationErrorsWithoutPrintingACompletedSummary() throws Exception {
        Path program = temporaryDirectory.resolve("aborting-tests.caret");
        Files.writeString(program, """
                assert "first" true
                assertEqual "error" (1 / 0) 0
                assert "unreached" true
                """);

        Invocation invocation = run("test", program.toString());

        assertEquals(1, invocation.exitCode());
        assertEquals("PASS: first\n", invocation.output());
        assertTrue(invocation.error().contains("Error: Line 2, column 26: Division by zero"));
        assertFalse(invocation.output().contains("Summary:"));
    }

    @Test
    void assertionsAreAvailableOnlyInTestMode() throws Exception {
        Path program = temporaryDirectory.resolve("ordinary.caret");
        Files.writeString(program, "assert \"not ordinary\" true\n");

        Invocation invocation = run(program);

        assertEquals(1, invocation.exitCode());
        assertTrue(invocation.error().contains("Unknown name: assert"));
    }

    @Test
    void replKeepsStateAndStopsAtExitCommand() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[0],
                new ByteArrayInputStream("value = 40\nprint value + 2\n  exit  \nprint absent\n"
                        .getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));

        assertEquals(0, exitCode);
        assertEquals("""
                Caret prototype REPL. Enter one-line expressions or assignments. \
                Type exit or press Ctrl-D to exit.
                > > 42
                >\s""", output.toString(StandardCharsets.UTF_8));
        assertEquals("", error.toString(StandardCharsets.UTF_8));
    }

    private Invocation run(Path program) {
        return run(program.toString());
    }

    private Invocation run(String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int exitCode = Main.run(
                args,
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));
        return new Invocation(exitCode, output.toString(StandardCharsets.UTF_8), error.toString(StandardCharsets.UTF_8));
    }

    private record Invocation(int exitCode, String output, String error) {}
}
