package caretlang.embedding;

import caretlang.EmbeddingBridge;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class CaretSandbox implements AutoCloseable {
    private final AtomicReference<CaretEnvironment> environment;
    private EmbeddingBridge bridge;
    private final PrintStream output;
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean closed;
    private boolean loaded;
    private String sourceName = "<embedded>";
    private Map<String, CaretCallable> callbacks = Map.of();
    private final Map<String, Integer> callbackArities = new HashMap<>();
    private Map<String, CaretCallable> pendingCallbacks;

    private CaretSandbox(CaretEnvironment environment, PrintStream output) {
        this.environment = new AtomicReference<>(environment);
        this.output = output;
        environment.callbacks().forEach((name, callback) -> callbackArities.put(name, callback.arity()));
        bridge = new EmbeddingBridge(this, this.environment, output, this::stageCallbacks, this::newCallable);
    }

    public static Builder builder() { return new Builder(); }

    public CaretLoadResult load(CaretSource source) {
        enter();
        try {
            if (source == null) throw misuse(CaretEmbeddingException.Code.INVALID_ARGUMENT, "source must not be null");
            if (loaded) throw misuse(CaretEmbeddingException.Code.ALREADY_LOADED, "A sandbox accepts exactly one source");
            loaded = true;
            sourceName = source.name();
            try {
                return CaretLoadResult.success(new LoadedProgram(this, bridge.load(source.text())));
            } catch (RuntimeException failure) {
                return CaretLoadResult.failure(List.of(EmbeddingBridge.diagnostic(failure, sourceName)));
            }
        } finally {
            leave();
        }
    }

    public CaretExecutionResult execute(LoadedProgram handle) {
        enter();
        try {
            requireHandle(handle);
            if (handle.consumed) throw misuse(CaretEmbeddingException.Code.HANDLE_CONSUMED, "Loaded program was already executed");
            handle.consumed = true;
            pendingCallbacks = null;
            try {
                CaretValue.CollectionValue value = bridge.execute((EmbeddingBridge.Prepared) handle.program);
                commitCallbacks();
                return CaretExecutionResult.success(value);
            } catch (RuntimeException failure) {
                pendingCallbacks = null;
                return CaretExecutionResult.failure(List.of(EmbeddingBridge.diagnostic(failure, sourceName)));
            } catch (Error fatal) {
                closed = true;
                pendingCallbacks = null;
                throw fatal;
            }
        } finally {
            leave();
        }
    }

    public CaretInvocationResult invoke(CaretCallable callable, List<CaretValue> arguments) {
        enter();
        try {
            if (callable == null || arguments == null || arguments.stream().anyMatch(Objects::isNull)) {
                throw misuse(CaretEmbeddingException.Code.INVALID_ARGUMENT, "callable and arguments must not be null");
            }
            if (!callable.isOwnedBy(this)) throw misuse(CaretEmbeddingException.Code.FOREIGN_HANDLE,
                    "Callable belongs to another sandbox");
            if (!EmbeddingBridge.isCallableHandle(callable.implementationHandle())) {
                throw misuse(CaretEmbeddingException.Code.INVALID_ARGUMENT, "Invalid callable handle");
            }
            if (arguments.size() != callable.arity()) {
                throw misuse(CaretEmbeddingException.Code.INVALID_ARITY,
                        "Expected " + callable.arity() + " arguments, got " + arguments.size());
            }
            pendingCallbacks = null;
            try {
                CaretValue value = bridge.invoke(callable.implementationHandle(), List.copyOf(arguments));
                commitCallbacks();
                return CaretInvocationResult.success(value);
            } catch (RuntimeException failure) {
                pendingCallbacks = null;
                return CaretInvocationResult.failure(List.of(EmbeddingBridge.diagnostic(failure, sourceName)));
            } catch (Error fatal) {
                closed = true;
                pendingCallbacks = null;
                throw fatal;
            }
        } finally {
            leave();
        }
    }

    public void swapEnvironment(CaretEnvironment replacement) {
        enter();
        try {
            if (replacement == null) throw misuse(CaretEmbeddingException.Code.INVALID_ARGUMENT, "environment must not be null");
            for (var entry : replacement.callbacks().entrySet()) {
                Integer knownArity = callbackArities.get(entry.getKey());
                if (knownArity != null && knownArity != entry.getValue().arity()) {
                    throw misuse(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                            "A rebound callback must preserve arity: " + entry.getKey());
                }
            }
            CaretEnvironment previous = environment.getAndSet(replacement);
            try {
                if (loaded) bridge.swapped();
                else bridge = new EmbeddingBridge(this, environment, output, this::stageCallbacks, this::newCallable);
            } catch (RuntimeException failure) {
                environment.set(previous);
                throw misuse(CaretEmbeddingException.Code.INVALID_ARGUMENT, "Invalid replacement environment");
            }
            replacement.callbacks().forEach((name, callback) -> callbackArities.putIfAbsent(name, callback.arity()));
        } finally {
            leave();
        }
    }

    public Map<String, CaretCallable> registeredCallbacks() {
        enter();
        try { return callbacks; }
        finally { leave(); }
    }

    @Override public void close() {
        if (closed) return;
        if (!busy.compareAndSet(false, true)) throw misuse(CaretEmbeddingException.Code.BUSY, "Sandbox is busy");
        try { closed = true; callbacks = Map.of(); pendingCallbacks = null; }
        finally { busy.set(false); }
    }

    private void stageCallbacks(Map<String, CaretCallable> replacement) {
        pendingCallbacks = Collections.unmodifiableMap(new LinkedHashMap<>(replacement));
    }
    private CaretCallable newCallable(Object implementation, int arity) {
        return new CaretCallable(this, implementation, arity);
    }
    private void commitCallbacks() {
        if (pendingCallbacks != null) callbacks = pendingCallbacks;
        pendingCallbacks = null;
    }
    private void requireHandle(LoadedProgram handle) {
        if (handle == null) throw misuse(CaretEmbeddingException.Code.INVALID_ARGUMENT, "handle must not be null");
        if (handle.owner != this) throw misuse(CaretEmbeddingException.Code.FOREIGN_HANDLE, "Handle belongs to another sandbox");
    }
    private void enter() {
        if (closed) throw misuse(CaretEmbeddingException.Code.CLOSED, "Sandbox is closed");
        if (!busy.compareAndSet(false, true)) throw misuse(CaretEmbeddingException.Code.BUSY, "Sandbox is busy or re-entered");
        if (closed) { busy.set(false); throw misuse(CaretEmbeddingException.Code.CLOSED, "Sandbox is closed"); }
    }
    private void leave() { busy.set(false); }
    private static CaretEmbeddingException misuse(CaretEmbeddingException.Code code, String message) {
        return new CaretEmbeddingException(code, message);
    }

    public static final class Builder {
        private CaretEnvironment environment;
        private PrintStream output;
        public Builder environment(CaretEnvironment environment) { this.environment = Objects.requireNonNull(environment); return this; }
        public Builder output(PrintStream output) { this.output = Objects.requireNonNull(output); return this; }
        public CaretSandbox build() {
            if (environment == null) throw new IllegalStateException("environment is required");
            if (output == null) throw new IllegalStateException("output is required");
            try {
                return new CaretSandbox(environment, output);
            } catch (RuntimeException failure) {
                throw new CaretEmbeddingException(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                        "Invalid initial environment");
            }
        }
    }
}
