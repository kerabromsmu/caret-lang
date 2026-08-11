package caretlang;

/** Stable host/CLI messages that are not Caret language diagnostics. */
enum HostMessageCatalog {
    FILE_USAGE("HOST-FILE-USAGE", "Usage: caret <file> | caret test <file>"),
    TEST_USAGE("HOST-TEST-USAGE", "Usage: caret test <file>"),
    SOURCE_READ_FAILURE("HOST-SOURCE-READ-FAILURE", "Error: Cannot read Caret source file %s: %s"),
    TEST_READ_FAILURE("HOST-TEST-READ-FAILURE", "Error: Cannot read Caret test file %s: %s"),
    REPL_TERMINAL_REQUIRED("HOST-REPL-TERMINAL-REQUIRED",
            "Error: Caret REPL requires an interactive terminal. Run ./repl.sh from a terminal window."),
    REPL_HISTORY_READ("HOST-REPL-HISTORY-READ", "Warning: Cannot read REPL history at %s; using in-memory history: %s"),
    REPL_HISTORY_WRITE("HOST-REPL-HISTORY-WRITE", "Warning: Cannot write REPL history at %s; using in-memory history: %s");

    private final String id;
    private final String template;

    HostMessageCatalog(String id, String template) {
        this.id = id;
        this.template = template;
    }

    String id() { return id; }
    String format(Object... arguments) { return template.formatted(arguments); }
}
