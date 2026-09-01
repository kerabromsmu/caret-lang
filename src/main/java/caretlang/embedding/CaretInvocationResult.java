package caretlang.embedding;

import java.util.List;
import java.util.Optional;

public final class CaretInvocationResult {
    private final CaretOperationResult<CaretValue> result;
    private CaretInvocationResult(CaretOperationResult<CaretValue> result) { this.result = result; }
    static CaretInvocationResult success(CaretValue value) { return new CaretInvocationResult(CaretOperationResult.success(value)); }
    static CaretInvocationResult failure(List<CaretDiagnostic> diagnostics) { return new CaretInvocationResult(CaretOperationResult.failure(diagnostics)); }
    public CaretOperationResult.Code code() { return result.code(); }
    public Optional<CaretValue> value() { return result.value(); }
    public List<CaretDiagnostic> diagnostics() { return result.diagnostics(); }
}
