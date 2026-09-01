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
    private Set<String> knownValueNames;
    private Map<String, CallbackSchema> knownCallbacks;
    private Map<String, CaretCallable> pendingCallbacks;

    private CaretSandbox(CaretEnvironment environment, PrintStream output) {
        this.environment = new AtomicReference<>(environment);
        this.output = output;
        rememberSchema(environment);
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
                return CaretLoadResult.failure(List.of(expectedDiagnostic(failure)));
            } catch (Error fatal) {
                closed = true;
                pendingCallbacks = null;
                throw fatal;
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
            } catch (CaretEmbeddingException misuse) {
                pendingCallbacks = null;
                throw misuse;
            } catch (RuntimeException failure) {
                pendingCallbacks = null;
                return CaretExecutionResult.failure(List.of(expectedDiagnostic(failure)));
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
            arguments.forEach(this::requireOwnedCallables);
            pendingCallbacks = null;
            try {
                CaretValue value = bridge.invoke(callable.implementationHandle(), List.copyOf(arguments));
                commitCallbacks();
                return CaretInvocationResult.success(value);
            } catch (CaretEmbeddingException misuse) {
                pendingCallbacks = null;
                throw misuse;
            } catch (RuntimeException failure) {
                pendingCallbacks = null;
                return CaretInvocationResult.failure(List.of(expectedDiagnostic(failure)));
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
            if (loaded) {
                validateLoadedReplacement(replacement);
                bridge.swapped();
                environment.set(replacement);
            } else {
                CaretEnvironment previous = environment.getAndSet(replacement);
                try {
                    EmbeddingBridge replacementBridge = new EmbeddingBridge(
                            this, environment, output, this::stageCallbacks, this::newCallable);
                    bridge = replacementBridge;
                    rememberSchema(replacement);
                } catch (RuntimeException failure) {
                    environment.set(previous);
                    if (!EmbeddingBridge.isExpectedFailure(failure)) throw failure;
                    throw misuse(CaretEmbeddingException.Code.INVALID_ARGUMENT, "Invalid replacement environment");
                }
            }
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

    private void rememberSchema(CaretEnvironment environment) {
        knownValueNames = environment.values().keySet();
        LinkedHashMap<String, CallbackSchema> schemas = new LinkedHashMap<>();
        environment.callbacks().forEach((name, callback) -> schemas.put(name, CallbackSchema.of(callback)));
        knownCallbacks = Collections.unmodifiableMap(schemas);
    }

    private void validateLoadedReplacement(CaretEnvironment replacement) {
        if (!knownValueNames.containsAll(replacement.values().keySet())) {
            throw misuse(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                    "A loaded sandbox cannot introduce new host value names");
        }
        if (!knownCallbacks.keySet().containsAll(replacement.callbacks().keySet())) {
            throw misuse(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                    "A loaded sandbox cannot introduce new host callback names");
        }
        replacement.callbacks().forEach((name, callback) -> {
            if (!knownCallbacks.get(name).equals(CallbackSchema.of(callback))) {
                throw misuse(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                        "A rebound callback must preserve arity and effects: " + name);
            }
        });
    }

    private void requireOwnedCallables(CaretValue value) {
        switch (value) {
            case CaretCallable callable -> {
                if (!callable.isOwnedBy(this)) {
                    throw misuse(CaretEmbeddingException.Code.FOREIGN_HANDLE,
                            "Callable argument belongs to another sandbox");
                }
            }
            case CaretValue.FieldValue field -> requireOwnedCallables(field.value());
            case CaretValue.SequenceValue sequence -> sequence.values().forEach(this::requireOwnedCallables);
            case CaretValue.CollectionValue collection -> collection.fields().values().forEach(this::requireOwnedCallables);
            default -> { }
        }
    }

    private CaretDiagnostic expectedDiagnostic(RuntimeException failure) {
        Optional<CaretDiagnostic> diagnostic = EmbeddingBridge.diagnostic(failure, sourceName);
        if (diagnostic.isPresent()) return diagnostic.get();
        closed = true;
        pendingCallbacks = null;
        throw failure;
    }

    private record CallbackSchema(int arity, Set<CaretEffect> effects) {
        private CallbackSchema { effects = Set.copyOf(effects); }
        private static CallbackSchema of(CaretEnvironment.Callback callback) {
            return new CallbackSchema(callback.arity(), callback.effects());
        }
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
                if (!EmbeddingBridge.isExpectedFailure(failure)) throw failure;
                throw new CaretEmbeddingException(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                        "Invalid initial environment");
            }
        }
    }
}
