package caretlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ConformanceMatrixTest {
    private static final Set<String> STATUSES = Set.of("implemented", "planned", "deferred", "unresolved");

    @Test
    void requirementIdsStatusesAndImplementedEvidenceAreValid() throws IOException {
        String markdown = Files.readString(Path.of("CONFORMANCE.md"));
        Set<String> ids = new HashSet<>();
        int requirements = 0;

        for (String line : markdown.lines().toList()) {
            if (!line.matches("\\| [A-Z][A-Z0-9-]*-[0-9]{3} \\|.*")) continue;
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
            }
            requirements++;
        }

        assertTrue(requirements >= 70, "The conformance inventory is unexpectedly incomplete");
    }
}
