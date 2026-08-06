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

                field = #count
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

    private Invocation run(Path program) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int exitCode = Main.run(
                new String[]{program.toString()},
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));
        return new Invocation(exitCode, output.toString(StandardCharsets.UTF_8), error.toString(StandardCharsets.UTF_8));
    }

    private record Invocation(int exitCode, String output, String error) {}
}
