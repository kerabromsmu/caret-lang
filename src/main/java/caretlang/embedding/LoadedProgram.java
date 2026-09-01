package caretlang.embedding;

public final class LoadedProgram {
    final CaretSandbox owner;
    final Object program;
    boolean consumed;

    LoadedProgram(CaretSandbox owner, Object program) {
        this.owner = owner;
        this.program = program;
    }
}
