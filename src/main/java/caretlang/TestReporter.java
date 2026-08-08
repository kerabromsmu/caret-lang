package caretlang;

import java.io.PrintStream;

final class TestReporter {
    private final PrintStream output;
    private int passed;
    private int failed;

    TestReporter(PrintStream output) {
        this.output = output;
    }

    void record(String name, Value actual, Value expected, boolean successful, SourceSpan span) {
        if (successful) {
            passed++;
            output.println("PASS: " + name);
            return;
        }

        failed++;
        SourcePosition start = span.start();
        output.println("FAIL: " + name + " (Line " + start.line() + ", column " + start.column() + ")");
        output.println("  expected: " + expected);
        output.println("  actual: " + actual);
    }

    boolean finish() {
        int total = passed + failed;
        if (total == 0) output.println("No tests found.");
        String testLabel = total == 1 ? " test" : " tests";
        output.println("Summary: " + total + testLabel + ", " + passed + " passed, " + failed + " failed");
        return total > 0 && failed == 0;
    }
}
