package caretlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

final class ConformanceMatrixTest {
    private static final Set<String> STATUSES = Set.of("implemented", "planned", "deferred", "unresolved");
    private static final Pattern TEST_REFERENCE = Pattern.compile("([A-Za-z][A-Za-z0-9]*Test)#([A-Za-z][A-Za-z0-9]*)");
    private static final Pattern EXAMPLE_REFERENCE = Pattern.compile("examples/[A-Za-z0-9_.-]+\\.caret");

    @Test
    void requirementIdsStatusesAndImplementedEvidenceAreValid() throws IOException {
        String markdown = Files.readString(Path.of("CONFORMANCE.md"));
        String integrationScript = Files.readString(Path.of("test.sh"));
        Set<String> ids = new HashSet<>();
        int requirements = 0;

        for (String line : markdown.lines().toList()) {
            if (!line.matches("\\| [A-Z][A-Z0-9-]*-[0-9]{3} +\\|.*")) continue;
            String[] cells = line.split("\\|", -1);
            assertTrue(cells.length >= 7, "Malformed conformance row: " + line);
            String id = cells[1].trim();
            String status = cells[3].trim();
            String tests = cells[4].trim();
            String example = cells[5].trim();

            assertTrue(ids.add(id), "Duplicate conformance ID: " + id);
            assertTrue(STATUSES.contains(status), "Invalid status for " + id + ": " + status);
            if (status.equals("implemented")) {
                assertNotEquals("`—`", tests, "Implemented requirement lacks test evidence: " + id);
                assertNotEquals("`—`", example, "Implemented requirement lacks runnable example: " + id);
                validateTestReferences(id, tests);
                validateExampleReferences(id, example, integrationScript);
            }
            requirements++;
        }

        assertTrue(requirements >= 70, "The conformance inventory is unexpectedly incomplete");
    }

    private void validateTestReferences(String id, String evidence) {
        Matcher references = TEST_REFERENCE.matcher(evidence);
        assertTrue(references.find(), "Implemented requirement has no machine-checkable test reference: " + id);
        do {
            String className = references.group(1);
            String methodName = references.group(2);
            final Class<?> testClass;
            try {
                testClass = Class.forName("caretlang." + className);
            } catch (ClassNotFoundException missingClass) {
                fail("Unknown test class for " + id + ": " + className);
                return;
            }
            assertTrue(Set.of(testClass.getDeclaredMethods()).stream()
                            .anyMatch(method -> method.getName().equals(methodName)),
                    "Unknown test method for " + id + ": " + className + "#" + methodName);
        } while (references.find());
    }

    private void validateExampleReferences(String id, String evidence, String integrationScript) {
        Matcher references = EXAMPLE_REFERENCE.matcher(evidence);
        assertTrue(references.find(), "Implemented requirement has no example path: " + id);
        do {
            String path = references.group();
            assertTrue(Files.isRegularFile(Path.of(path)), "Missing example for " + id + ": " + path);
            assertTrue(integrationScript.contains(path), "Example is not exercised by test.sh for " + id + ": " + path);
        } while (references.find());
    }
}
