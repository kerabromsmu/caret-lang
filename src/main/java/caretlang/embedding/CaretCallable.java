package caretlang.embedding;

import java.util.List;

public final class CaretCallable implements CaretValue {
    final CaretSandbox owner;
    final Object callable;
    private final int arity;

    CaretCallable(CaretSandbox owner, Object callable, int arity) {
        this.owner = owner;
        this.callable = callable;
        this.arity = arity;
    }

    public int arity() { return arity; }
    boolean isOwnedBy(CaretSandbox sandbox) { return owner == sandbox; }
    Object implementationHandle() { return callable; }
    public CaretInvocationResult invoke(List<CaretValue> arguments) {
        return owner.invoke(this, arguments);
    }
}
