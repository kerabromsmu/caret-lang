package caretlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpecificationCorpusTest {
    private static final Pattern LINK = Pattern.compile("(?<!!)\\[[^]]*]\\(([^ )]+)(?: \\\"[^\\\"]*\\\")?\\)");
    private static final Pattern ANCHOR = Pattern.compile("<a id=\\\"([^\\\"]+)\\\"></a>");

    @Test
    void canonicalSpecificationCorpusHasCompleteNavigationAndValidMarkdownLinks() throws IOException {
        Path index = Path.of("LANGUAGE.md");
        String indexText = Files.readString(index);
        List<Path> documents = new ArrayList<>();
        documents.add(index);
        for (int number = 1; number <= 14; number++) {
            String prefix = "spec/%02d-".formatted(number);
            List<Path> matches;
            try (var paths = Files.list(Path.of("spec"))) {
                matches = paths.filter(path -> path.toString().replace('\\', '/').startsWith(prefix)).toList();
            }
            assertEquals(1, matches.size(), "Expected one canonical document with prefix " + prefix);
            Path document = matches.getFirst();
            assertTrue(indexText.contains(document.toString().replace('\\', '/')),
                    "Specification index does not link " + document);
            documents.add(document);
        }

        for (Path document : documents) validateDocument(document);
    }

    private static void validateDocument(Path document) throws IOException {
        String markdown = Files.readString(document);
        assertEquals(0, markdown.lines().filter(line -> line.startsWith("```")).count() % 2,
                "Unbalanced fenced block in " + document);
        assertEquals(1, headingsOutsideFences(markdown, "# "), "Expected exactly one H1 in " + document);

        Set<String> anchors = new HashSet<>();
        Matcher declared = ANCHOR.matcher(markdown);
        while (declared.find()) assertTrue(anchors.add(declared.group(1)),
                "Duplicate explicit anchor in " + document + ": " + declared.group(1));

        Matcher links = LINK.matcher(markdown);
        while (links.find()) {
            String target = links.group(1);
            if (target.startsWith("http://") || target.startsWith("https://") || target.startsWith("mailto:")) continue;
            String[] parts = target.split("#", 2);
            Path parent = document.getParent() == null ? Path.of(".") : document.getParent();
            Path linked = parts[0].isEmpty() ? document : parent.resolve(parts[0]).normalize();
            assertTrue(Files.exists(linked), "Broken link in " + document + ": " + target);
            if (parts.length == 2) {
                String linkedText = Files.readString(linked);
                assertTrue(linkedText.contains("<a id=\"" + parts[1] + "\"></a>")
                                || generatedHeadingAnchorExists(linkedText, parts[1]),
                        "Broken fragment in " + document + ": " + target);
            }
        }
    }

    private static long headingsOutsideFences(String markdown, String prefix) {
        boolean fenced = false;
        long count = 0;
        for (String line : markdown.split("\\R", -1)) {
            if (line.startsWith("```")) fenced = !fenced;
            else if (!fenced && line.startsWith(prefix) && !line.startsWith(prefix + "#")) count++;
        }
        return count;
    }

    private static boolean generatedHeadingAnchorExists(String markdown, String expected) {
        return markdown.lines().filter(line -> line.startsWith("#"))
                .map(line -> line.replaceFirst("^#+\\s+", "").replace("`", "").toLowerCase()
                        .replaceAll("[^a-z0-9 _-]", "").trim().replaceAll("\\s+", "-")
                        .replaceAll("-+", "-"))
                .anyMatch(expected::equals);
    }
}
