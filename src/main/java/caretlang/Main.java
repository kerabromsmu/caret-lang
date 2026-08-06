package caretlang;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public final class Main {
    public static void main(String[] args) throws IOException {
        int exitCode = run(args, System.in, System.out, System.err);
        if (exitCode != 0) System.exit(exitCode);
    }

    static int run(String[] args, InputStream input, PrintStream output, PrintStream error) throws IOException {
        Interpreter interpreter = new Interpreter(output);
        if (args.length > 0) {
            String source = Files.readString(Path.of(args[0]));
            try {
                interpreter.execute(new Parser(source).parseProgram());
            } catch (LangException e) {
                error.println("Error: " + e.getMessage());
                return 1;
            }
            return 0;
        }

        output.println("Caret prototype REPL. Enter one-line expressions or assignments. Ctrl-D to exit.");
        Scanner scanner = new Scanner(input);
        while (true) {
            output.print("> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine();
            if (line.isBlank()) continue;
            try {
                interpreter.execute(new Parser(line).parseProgram());
            } catch (LangException e) {
                error.println("Error: " + e.getMessage());
            }
        }
        return 0;
    }
}
