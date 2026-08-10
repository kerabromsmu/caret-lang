package caretlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

final class MarkdownExampleTest {
    @Test
    void publicCaretCodeFencesRemainSyntacticallyValid() throws IOException {
        for (String document : List.of("README.md", "WEB_INTRODUCTION.md")) {
            List<String> examples = caretFences(Files.readString(Path.of(document)));
            assertFalse(examples.isEmpty(), document + " should contain Caret examples");
            for (int i = 0; i < examples.size(); i++) {
                try {
                    new Parser(examples.get(i)).parseProgram();
                } catch (LangException error) {
                    fail(document + " Caret fence " + (i + 1) + " does not parse: " + error.getMessage());
                }
            }
        }
    }

    private List<String> caretFences(String markdown) {
        java.util.ArrayList<String> examples = new java.util.ArrayList<>();
        StringBuilder current = null;
        for (String line : markdown.split("\\R", -1)) {
            if (current == null && line.equals("```caret")) {
                current = new StringBuilder();
            } else if (current != null && line.equals("```")) {
                examples.add(current.toString());
                current = null;
            } else if (current != null) {
                current.append(line).append('\n');
            }
        }
        if (current != null) fail("Unclosed ```caret fence");
        return List.copyOf(examples);
    }
}
