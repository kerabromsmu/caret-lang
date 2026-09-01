package caretlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

final class DiagnosticCoverageTest {
    private static final Pattern TEST = Pattern.compile("([A-Za-z][A-Za-z0-9]*Test)#([A-Za-z][A-Za-z0-9]*)");
    private static final Pattern EXAMPLE = Pattern.compile("examples/errors/[A-Za-z0-9_.-]+\\.caret");
    private record Row(String category, String evidence) {}

    @Test
    void catalogsAndCoverageRowsStayInSync() throws IOException {
        String markdown = Files.readString(Path.of("DIAGNOSTICS.md"));
        String integration = Files.readString(Path.of("test.sh"));
        Map<String, Row> evidence = rows(markdown);
        Set<String> catalogIds = new HashSet<>();
        for (DiagnosticCatalog entry : DiagnosticCatalog.values()) assertTrue(catalogIds.add(entry.id()));
        for (HostMessageCatalog entry : HostMessageCatalog.values()) assertTrue(catalogIds.add(entry.id()));
        assertEquals(catalogIds, evidence.keySet());
        for (DiagnosticCatalog entry : DiagnosticCatalog.values()) {
            assertEquals(entry.category(), evidence.get(entry.id()).category(),
                    "Incorrect category for " + entry.id());
        }
        for (HostMessageCatalog entry : HostMessageCatalog.values()) {
            assertEquals("host", evidence.get(entry.id()).category(),
                    "Incorrect category for " + entry.id());
        }

        evidence.forEach((id, row) -> {
            Matcher example = EXAMPLE.matcher(row.evidence());
            Matcher test = TEST.matcher(row.evidence());
            boolean found = false;
            while (example.find()) {
                found = true;
                Path source = Path.of(example.group());
                Path expected = Path.of(example.group().replace(".caret", ".expected"));
                assertTrue(Files.isRegularFile(source), "Missing fixture for " + id);
                assertTrue(Files.isRegularFile(expected), "Missing golden stderr for " + id);
                assertTrue(integration.contains(source.toString()), "Fixture is not executed for " + id);
                DiagnosticCatalog catalog = Set.of(DiagnosticCatalog.values()).stream()
                        .filter(entry -> entry.id().equals(id)).findFirst().orElseThrow();
                String firstLine;
                try {
                    firstLine = Files.readAllLines(expected).getFirst();
                } catch (IOException error) {
                    throw new AssertionError("Cannot read golden stderr for " + id, error);
                }
                String message = firstLine.replaceFirst("^Error: Line \\d+, column \\d+: ", "");
                assertSame(catalog, DiagnosticCatalog.identify(catalog.phase(), catalog.code(), message),
                        "Golden stderr does not identify " + id);
            }
            while (test.find()) {
                found = true;
                validateTest(id, test.group(1), test.group(2));
            }
            assertTrue(found, "Missing evidence for " + id);
        });
    }

    @Test
    void everyInternalCatalogVariantIsIndividuallyIdentifiable() {
        Map<DiagnosticCatalog, String> messages = Map.of(
                DiagnosticCatalog.PARSE_INVALID_NUMBER, "Invalid number literal",
                DiagnosticCatalog.RUNTIME_DUPLICATE_DEFINITION, "Duplicate definition: value",
                DiagnosticCatalog.RUNTIME_PREMATURE_READ, "Binding read before initialization: value",
                DiagnosticCatalog.TOO_MANY_FUNCTION_ARGUMENTS, "Too many arguments for function",
                DiagnosticCatalog.TOO_MANY_PARTIAL_ARGUMENTS, "Too many arguments for partial expression",
                DiagnosticCatalog.EVALUATION_DEPTH, "Maximum Caret evaluation depth exceeded",
                DiagnosticCatalog.UNKNOWN_UNARY_OPERATOR, "Unknown unary operator: token",
                DiagnosticCatalog.UNKNOWN_BINARY_OPERATOR, "Unknown operator: token",
                DiagnosticCatalog.INTERNAL_INVARIANT, "Internal invariant failure");

        Set<DiagnosticCatalog> internal = Set.of(DiagnosticCatalog.values()).stream()
                .filter(entry -> entry.category().equals(DiagnosticCategory.INTERNAL))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(internal, messages.keySet());
        messages.forEach((entry, message) ->
                assertSame(entry, DiagnosticCatalog.identify(entry.phase(), entry.code(), message), entry.id()));
    }

    private static Map<String, Row> rows(String markdown) {
        Map<String, Row> rows = new HashMap<>();
        markdown.lines().filter(line -> line.startsWith("| ") && !line.startsWith("| Variant")
                && !line.startsWith("|---")).forEach(line -> {
            String[] cells = line.split("\\|", -1);
            assertTrue(cells.length >= 6, "Malformed diagnostic row: " + line);
            assertNull(rows.put(cells[1].trim(), new Row(cells[2].trim(), cells[4].trim())),
                    "Duplicate diagnostic ID");
        });
        return rows;
    }

    private static void validateTest(String id, String className, String methodName) {
        try {
            Class<?> type = Class.forName("caretlang." + className);
            assertTrue(Set.of(type.getDeclaredMethods()).stream()
                    .anyMatch(method -> method.getName().equals(methodName)), "Unknown test for " + id);
        } catch (ClassNotFoundException missing) {
            fail("Unknown test class for " + id + ": " + className);
        }
    }
}
