package caretlang.embedding;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CaretOperationResult<T> {
    public enum Code { SUCCESS, FAILURE }
    private final Code code;
    private final T value;
    private final List<CaretDiagnostic> diagnostics;
    private final List<CaretDiagnostic> warnings;

    private CaretOperationResult(Code code, T value, List<CaretDiagnostic> diagnostics,
                                 List<CaretDiagnostic> warnings) {
        this.code = code;
        this.value = value;
        this.diagnostics = List.copyOf(diagnostics);
        this.warnings = List.copyOf(warnings);
    }

    public static <T> CaretOperationResult<T> success(T value) {
        return success(value, List.of());
    }
    public static <T> CaretOperationResult<T> success(T value, List<CaretDiagnostic> warnings) {
        return new CaretOperationResult<>(Code.SUCCESS, Objects.requireNonNull(value), List.of(), warnings);
    }
    public static <T> CaretOperationResult<T> failure(List<CaretDiagnostic> diagnostics) {
        return failure(diagnostics, List.of());
    }
    public static <T> CaretOperationResult<T> failure(List<CaretDiagnostic> diagnostics,
                                                       List<CaretDiagnostic> warnings) {
        if (diagnostics.isEmpty()) throw new IllegalArgumentException("failure requires diagnostics");
        return new CaretOperationResult<>(Code.FAILURE, null, diagnostics, warnings);
    }
    public Code code() { return code; }
    public Optional<T> value() { return Optional.ofNullable(value); }
    public List<CaretDiagnostic> diagnostics() { return diagnostics; }
    public List<CaretDiagnostic> warnings() { return warnings; }
}
