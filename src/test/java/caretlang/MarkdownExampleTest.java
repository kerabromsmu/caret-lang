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
            assertFencesParse(document, Files.readString(Path.of(document)));
        }

        String language = Files.readString(Path.of("LANGUAGE.md"));
        assertFencesParse("LANGUAGE.md implemented prototype",
                language.substring(0, language.indexOf("# Planned language specification")));

        String comparison = Files.readString(Path.of("docs/language-comparison.md"));
        assertFencesParse("docs/language-comparison.md prototype sections",
                comparison.substring(0, comparison.indexOf("> **Planned example:**")));
    }

    private void assertFencesParse(String document, String markdown) {
        List<String> examples = caretFences(markdown);
        assertFalse(examples.isEmpty(), document + " should contain Caret examples");
        for (int i = 0; i < examples.size(); i++) {
            try {
                new Parser(examples.get(i)).parseProgram();
            } catch (LangException error) {
                fail(document + " Caret fence " + (i + 1) + " does not parse: " + error.getMessage());
            }
        }
    }

    private List<String> caretFences(String markdown) {
        java.util.ArrayList<String> examples = new java.util.ArrayList<>();
        StringBuilder current = null;
        boolean inCaretFence = false;
        boolean plannedExample = false;
        for (String line : markdown.split("\\R", -1)) {
            if (!inCaretFence && line.equals("<!-- caret-example: planned -->")) {
                plannedExample = true;
            } else if (!inCaretFence && line.equals("```caret")) {
                inCaretFence = true;
                if (!plannedExample) current = new StringBuilder();
            } else if (!inCaretFence && plannedExample && !line.isBlank()) {
                plannedExample = false;
            } else if (inCaretFence && line.equals("```")) {
                if (current != null) examples.add(current.toString());
                current = null;
                inCaretFence = false;
                plannedExample = false;
            } else if (inCaretFence && current != null) {
                current.append(line).append('\n');
            }
        }
        if (inCaretFence) fail("Unclosed ```caret fence");
        return List.copyOf(examples);
    }
}
