package caretlang.embedding;

import java.util.List;
import java.util.Optional;

public final class CaretExecutionResult {
    private final CaretOperationResult<CaretValue.CollectionValue> result;
    private CaretExecutionResult(CaretOperationResult<CaretValue.CollectionValue> result) { this.result = result; }
    static CaretExecutionResult success(CaretValue.CollectionValue value) { return new CaretExecutionResult(CaretOperationResult.success(value)); }
    static CaretExecutionResult failure(List<CaretDiagnostic> diagnostics) { return new CaretExecutionResult(CaretOperationResult.failure(diagnostics)); }
    public CaretOperationResult.Code code() { return result.code(); }
    public Optional<CaretValue.CollectionValue> value() { return result.value(); }
    public List<CaretDiagnostic> diagnostics() { return result.diagnostics(); }
}
