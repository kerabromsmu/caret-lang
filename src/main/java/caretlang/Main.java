package caretlang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public final class Main {
    public static void main(String[] args) throws IOException {
        Interpreter interpreter = new Interpreter();
        if (args.length > 0) {
            String source = Files.readString(Path.of(args[0]));
            try {
                interpreter.execute(new Parser(source).parseProgram());
            } catch (LangException e) {
                System.err.println("Error: " + e.getMessage());
                System.exit(1);
            }
            return;
        }

        System.out.println("Caret prototype REPL. Enter one-line expressions or assignments. Ctrl-D to exit.");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine();
            if (line.isBlank()) continue;
            try {
                interpreter.execute(new Parser(line).parseProgram());
            } catch (LangException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}
