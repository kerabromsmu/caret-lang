package caretlang.embedding;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CaretOperationResult<T> {
    public enum Code { SUCCESS, FAILURE }
    private final Code code;
    private final T value;
    private final List<CaretDiagnostic> diagnostics;

    private CaretOperationResult(Code code, T value, List<CaretDiagnostic> diagnostics) {
        this.code = code;
        this.value = value;
        this.diagnostics = List.copyOf(diagnostics);
    }

    public static <T> CaretOperationResult<T> success(T value) {
        return new CaretOperationResult<>(Code.SUCCESS, Objects.requireNonNull(value), List.of());
    }
    public static <T> CaretOperationResult<T> failure(List<CaretDiagnostic> diagnostics) {
        if (diagnostics.isEmpty()) throw new IllegalArgumentException("failure requires diagnostics");
        return new CaretOperationResult<>(Code.FAILURE, null, diagnostics);
    }
    public Code code() { return code; }
    public Optional<T> value() { return Optional.ofNullable(value); }
    public List<CaretDiagnostic> diagnostics() { return diagnostics; }
}
