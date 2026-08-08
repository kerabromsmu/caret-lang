package caretlang;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;

final class JLineRepl {
    private static final int HISTORY_LIMIT = 1_000;

    private JLineRepl() {}

    static int run(Interpreter interpreter, PrintStream output, PrintStream error, Path historyFile) {
        try (Terminal terminal = TerminalBuilder.builder().system(true).dumb(false).build()) {
            return run(terminal, interpreter, output, error, historyFile);
        } catch (IOException terminalError) {
            error.println("Error: Caret REPL requires an interactive terminal. "
                    + "Run ./repl.sh from a terminal window.");
            error.flush();
            return 1;
        }
    }

    static int run(Terminal terminal, Interpreter interpreter, PrintStream output, PrintStream error,
                   Path historyFile) {
        CaretHistory history = new CaretHistory();
        LineReader reader = createReader(terminal, history, historyFile, error);

        output.println("Caret prototype REPL. Enter one-line expressions or assignments. "
                + "Use Up/Down for history. Type exit or press Ctrl-D to exit.");
        output.flush();
        try {
            while (true) {
                String line;
                try {
                    line = reader.readLine("> ");
                } catch (UserInterruptException ignored) {
                    continue;
                } catch (EndOfFileException ignored) {
                    break;
                }

                if (line.isBlank()) continue;
                if (line.trim().equals("exit")) break;
                try {
                    interpreter.execute(new Parser(line).parseProgram());
                    output.flush();
                } catch (LangException languageError) {
                    error.println("Error: " + languageError.getMessage());
                    error.flush();
                }
            }
        } finally {
            saveHistory(reader, history, historyFile, error);
        }
        return 0;
    }

    static LineReader createReader(Terminal terminal, CaretHistory history, Path historyFile, PrintStream error) {
        LineReader reader = LineReaderBuilder.builder()
                .appName("caret")
                .terminal(terminal)
                .history(history)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                .option(LineReader.Option.HISTORY_INCREMENTAL, false)
                .variable(LineReader.HISTORY_SIZE, HISTORY_LIMIT)
                .variable(LineReader.HISTORY_FILE_SIZE, HISTORY_LIMIT)
                .build();
        history.attach(reader);
        loadHistory(reader, history, historyFile, error);
        return reader;
    }

    private static void loadHistory(LineReader reader, CaretHistory history, Path historyFile, PrintStream error) {
        reader.setVariable(LineReader.HISTORY_FILE, historyFile);
        try {
            history.load();
        } catch (IOException | UncheckedIOException | IllegalArgumentException historyError) {
            reader.setVariable(LineReader.HISTORY_FILE, null);
            warn(error, "read", historyFile, historyError);
        }
    }

    static void saveHistory(LineReader reader, CaretHistory history, Path historyFile, PrintStream error) {
        if (reader.getVariable(LineReader.HISTORY_FILE) == null) return;
        try {
            history.save();
        } catch (IOException | UncheckedIOException | IllegalArgumentException historyError) {
            warn(error, "write", historyFile, historyError);
        }
    }

    private static void warn(PrintStream error, String operation, Path historyFile, Throwable cause) {
        if (cause instanceof UncheckedIOException unchecked) cause = unchecked.getCause();
        error.println("Warning: Cannot " + operation + " REPL history at " + historyFile
                + "; using in-memory history: " + cause.getMessage());
        error.flush();
    }

    static final class CaretHistory extends DefaultHistory {
        @Override
        public void add(Instant time, String line) {
            if (line.isBlank() || line.trim().equals("exit")) return;
            super.add(time, line);
        }
    }
}
