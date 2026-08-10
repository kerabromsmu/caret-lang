package caretlang;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public final class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            String userHome = System.getProperty("user.home");
            Path historyFile = userHome == null
                    ? Path.of(".caret_history")
                    : Path.of(userHome, ".caret_history");
            int exitCode = JLineRepl.run(new Interpreter(System.out), System.out, System.err, historyFile);
            if (exitCode != 0) System.exit(exitCode);
            return;
        }
        int exitCode = run(args, System.in, System.out, System.err);
        if (exitCode != 0) System.exit(exitCode);
    }

    static int run(String[] args, InputStream input, PrintStream output, PrintStream error) {
        if (args.length > 0 && args[0].equals("test")) {
            if (args.length != 2) {
                error.println("Usage: caret test <file>");
                return 1;
            }
            return runFile(Path.of(args[1]), output, error, true);
        }

        if (args.length > 1) {
            error.println("Usage: caret <file> | caret test <file>");
            return 1;
        }

        if (args.length == 1) {
            return runFile(Path.of(args[0]), output, error, false);
        }

        Interpreter interpreter = new Interpreter(output);
        output.println("Caret prototype REPL. Enter one-line expressions or assignments. Type exit or press Ctrl-D to exit.");
        Scanner scanner = new Scanner(input);
        JLineRepl.runLoop(() -> {
            output.print("> ");
            output.flush();
            return scanner.hasNextLine() ? scanner.nextLine() : null;
        }, interpreter, output, error);
        return 0;
    }

    private static int runFile(Path program, PrintStream output, PrintStream error, boolean testMode) {
        TestReporter reporter = testMode ? new TestReporter(output) : null;
        Interpreter interpreter = new Interpreter(output, reporter);
        final String source;
        try {
            source = Files.readString(program);
        } catch (IOException fileError) {
            String kind = testMode ? "test file " : "source file ";
            error.println("Error: Cannot read Caret " + kind + program + ": " + fileError.getMessage());
            return 1;
        }
        try {
            interpreter.execute(new Parser(source).parseProgram());
        } catch (LangException e) {
            error.println("Error: " + e.getMessage());
            return 1;
        }
        return reporter == null || reporter.finish() ? 0 : 1;
    }
}
