package caretlang.embedding;

import java.util.List;
import java.util.Optional;

public final class CaretLoadResult {
    private final CaretOperationResult<LoadedProgram> result;
    private CaretLoadResult(CaretOperationResult<LoadedProgram> result) { this.result = result; }
    static CaretLoadResult success(LoadedProgram value) { return new CaretLoadResult(CaretOperationResult.success(value)); }
    static CaretLoadResult success(LoadedProgram value, List<CaretDiagnostic> warnings) {
        return new CaretLoadResult(CaretOperationResult.success(value, warnings));
    }
    static CaretLoadResult failure(List<CaretDiagnostic> diagnostics) { return new CaretLoadResult(CaretOperationResult.failure(diagnostics)); }
    static CaretLoadResult failure(List<CaretDiagnostic> diagnostics, List<CaretDiagnostic> warnings) {
        return new CaretLoadResult(CaretOperationResult.failure(diagnostics, warnings));
    }
    public CaretOperationResult.Code code() { return result.code(); }
    public Optional<LoadedProgram> value() { return result.value(); }
    public List<CaretDiagnostic> diagnostics() { return result.diagnostics(); }
    public List<CaretDiagnostic> warnings() { return result.warnings(); }
}
