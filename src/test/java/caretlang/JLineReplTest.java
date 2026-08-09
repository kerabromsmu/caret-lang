package caretlang;

import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class JLineReplTest {
    @Test
    void failedSubmissionDoesNotPoisonLaterBindingDefinition() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
        PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);
        Iterator<String> lines = List.of("x = missing", "x = 1", "print x", "exit").iterator();

        JLineRepl.runLoop(() -> lines.hasNext() ? lines.next() : null,
                new Interpreter(output), output, error);

        assertEquals("1\n", outputBytes.toString(StandardCharsets.UTF_8));
        assertTrue(errorBytes.toString(StandardCharsets.UTF_8).contains("Unknown name: missing"));
        assertFalse(errorBytes.toString(StandardCharsets.UTF_8).contains("Duplicate definition: x"));
    }
    @TempDir Path temporaryDirectory;

    @Test
    void configuresArrowHistoryWithoutReinterpretingCaretBangOperator() throws Exception {
        try (Fixture fixture = fixture(temporaryDirectory.resolve("history"))) {
            LineReader reader = fixture.reader();
            KeyMap<Binding> keys = reader.getKeyMaps().get(LineReader.MAIN);

            assertNotNull(keys.getBound(KeyMap.key(fixture.terminal(), InfoCmp.Capability.key_up)));
            assertNotNull(keys.getBound(KeyMap.key(fixture.terminal(), InfoCmp.Capability.key_down)));
            assertTrue(reader.isSet(LineReader.Option.DISABLE_EVENT_EXPANSION));
            assertTrue(reader.isSet(LineReader.Option.HISTORY_IGNORE_DUPS));
            assertFalse(reader.isSet(LineReader.Option.HISTORY_INCREMENTAL));
            assertEquals(1_000, reader.getVariable(LineReader.HISTORY_SIZE));
            assertEquals(1_000, reader.getVariable(LineReader.HISTORY_FILE_SIZE));
        }
    }

    @Test
    void historyFiltersCommandsAndPersistsAcrossReaders() throws Exception {
        Path historyFile = temporaryDirectory.resolve("history");
        try (Fixture first = fixture(historyFile)) {
            Instant now = Instant.now();
            first.history().add(now, "print \"first\"");
            first.history().add(now, "print \"second\"");
            first.history().add(now, "print \"second\"");
            first.history().add(now, "   ");
            first.history().add(now, "  exit  ");
            JLineRepl.saveHistory(first.reader(), first.history(), historyFile, first.error());
        }

        String persisted = Files.readString(historyFile);
        assertTrue(persisted.contains("print \"first\""));
        assertEquals(1, occurrences(persisted, "print \"second\""));
        assertFalse(persisted.contains("exit"));

        try (Fixture second = fixture(historyFile)) {
            assertEquals(2, second.history().size());
            assertEquals("print \"first\"", second.history().get(second.history().first()));
            assertEquals("print \"second\"", second.history().get(second.history().last()));
            assertEquals("", second.errorText());
        }
    }

    @Test
    void unreadableHistoryFallsBackToMemoryWithAClearWarning() throws Exception {
        Path directoryInsteadOfFile = temporaryDirectory.resolve("history-directory");
        Files.createDirectory(directoryInsteadOfFile);

        try (Fixture fixture = fixture(directoryInsteadOfFile)) {
            assertNull(fixture.reader().getVariable(LineReader.HISTORY_FILE));
            assertTrue(fixture.errorText().contains("Warning: Cannot read REPL history at "
                    + directoryInsteadOfFile));
            assertTrue(fixture.errorText().contains("using in-memory history"));
        }
    }

    private Fixture fixture(Path historyFile) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);
        Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .type("xterm")
                .streams(new ByteArrayInputStream(new byte[0]), output)
                .build();
        JLineRepl.CaretHistory history = new JLineRepl.CaretHistory();
        LineReader reader = JLineRepl.createReader(terminal, history, historyFile, error);
        return new Fixture(terminal, reader, history, error, errorBytes);
    }

    private int occurrences(String text, String fragment) {
        return (text.length() - text.replace(fragment, "").length()) / fragment.length();
    }

    private record Fixture(Terminal terminal, LineReader reader, JLineRepl.CaretHistory history,
                           PrintStream error, ByteArrayOutputStream errorBytes) implements AutoCloseable {
        String errorText() {
            return errorBytes.toString(StandardCharsets.UTF_8);
        }

        @Override public void close() throws Exception {
            terminal.close();
        }
    }
}
