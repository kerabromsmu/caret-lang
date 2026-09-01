package caretlang;

import caretlang.embedding.*;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.BiFunction;

/** Internal adapter used by the stable {@code caretlang.embedding} facade. */
public final class EmbeddingBridge {
    public record Prepared(Object program) {}

    private final CaretSandbox owner;
    private final AtomicReference<CaretEnvironment> environment;
    private final Consumer<Map<String, CaretCallable>> registrar;
    private final BiFunction<Object, Integer, CaretCallable> callableFactory;
    private final Interpreter interpreter;
    private final Set<String> lazyNames = new LinkedHashSet<>();
    private final IdentityHashMap<CaretCallable, Value.Callable> callableHandles = new IdentityHashMap<>();

    public EmbeddingBridge(CaretSandbox owner, AtomicReference<CaretEnvironment> environment,
                           PrintStream output, Consumer<Map<String, CaretCallable>> registrar,
                           BiFunction<Object, Integer, CaretCallable> callableFactory) {
        this.owner = Objects.requireNonNull(owner);
        this.environment = Objects.requireNonNull(environment);
        this.registrar = Objects.requireNonNull(registrar);
        this.callableFactory = Objects.requireNonNull(callableFactory);
        CaretEnvironment initial = environment.get();
        EffectCatalog effects = EffectCatalog.standard(false);
        for (CaretEffect effect : initial.effects()) {
            if (effects.resolve(effect.name()).isEmpty()) effects = effects.with(effect.name(), new EffectDescriptor(effect.name()));
        }
        interpreter = new Interpreter(output, null, effects, OwnershipTracker.Mode.ENABLED,
                () -> environment.get().printEnabled());
        lazyNames.addAll(initial.values().keySet());
        for (String name : lazyNames) interpreter.defineEmbeddingValue(name, () -> resolveValue(name));
        for (CaretEnvironment.Callback callback : initial.callbacks().values()) installCallback(callback);
        interpreter.defineEmbeddingCallable("registerCallbacks", 1,
                values -> register(values.getFirst()), List.of());
    }

    public Prepared load(String text) {
        List<Ast.Stmt> program = new Parser(text).parseProgram();
        interpreter.validate(program);
        return new Prepared(program);
    }

    @SuppressWarnings("unchecked")
    public CaretValue.CollectionValue execute(Prepared prepared) {
        return interpreter.embeddingTransaction(() -> {
            List<Ast.Stmt> program = (List<Ast.Stmt>) prepared.program();
            interpreter.execute(program);
            return (CaretValue.CollectionValue) external(interpreter.topLevelBindings(program));
        });
    }

    public CaretValue invoke(Object callable, List<CaretValue> arguments) {
        if (!(callable instanceof Value.Callable function)) throw new IllegalArgumentException("Invalid callable handle");
        return interpreter.embeddingTransaction(() ->
                external(interpreter.invokeEmbedding(function, arguments.stream().map(this::internal).toList())));
    }

    public static boolean isCallableHandle(Object handle) { return handle instanceof Value.Callable; }

    public void swapped() {
        for (String name : lazyNames) interpreter.resetEmbeddingValue(name, () -> resolveValue(name));
    }

    public static Optional<CaretDiagnostic> diagnostic(Throwable failure, String sourceName) {
        Diagnostic diagnostic = failure instanceof LangException language ? language.diagnostic() : null;
        return diagnostic == null ? Optional.empty() : Optional.of(externalDiagnostic(diagnostic, sourceName));
    }

    public static boolean isExpectedFailure(Throwable failure) { return failure instanceof LangException; }

    private static CaretDiagnostic externalDiagnostic(Diagnostic diagnostic, String sourceName) {
        CaretDiagnostic.Location location = location(sourceName, diagnostic.primarySpan());
        List<CaretDiagnostic.Note> notes = diagnostic.related().stream().map(note ->
                new CaretDiagnostic.Note(note.message(), location(sourceName, note.span()))).toList();
        return new CaretDiagnostic(diagnostic.code(), CaretDiagnostic.Phase.valueOf(diagnostic.phase().name()),
                diagnostic.message(), location, notes);
    }

    private static CaretDiagnostic.Location location(String sourceName, SourceSpan span) {
        return span == null ? null : new CaretDiagnostic.Location(sourceName, span.start().line(), span.start().column(),
                span.end().line(), span.end().column());
    }

    private Value resolveValue(String name) {
        var supplier = environment.get().values().get(name);
        if (supplier == null) throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                "Host binding is not available in the current environment: " + name, null);
        try {
            return internal(supplier.get());
        } catch (Error error) {
            throw error;
        } catch (Exception exception) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                    "Host value provider failed", null);
        }
    }

    private void installCallback(CaretEnvironment.Callback declaration) {
        interpreter.defineEmbeddingCallable(declaration.name(), declaration.arity(), values -> {
            CaretEnvironment.Callback current = environment.get().callbacks().get(declaration.name());
            if (current == null) throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                    "Host callback is not available in the current environment: " + declaration.name(), null);
            try {
                return internal(current.implementation().invoke(values.stream().map(this::external).toList()));
            } catch (Error error) {
                throw error;
            } catch (Exception exception) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                        "Host callback failed: " + declaration.name(), null);
            }
        }, declaration.effects().stream().map(CaretEffect::name).toList());
    }

    private Value register(Value value) {
        if (!environment.get().callbackRegistrationEnabled()) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                    "Callback registration is not available in the current environment", null);
        }
        value = ValueSemantics.underlying(value);
        if (!(value instanceof Value.Dictionary dictionary)) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.EXPECTED_DICTIONARY,
                    "registerCallbacks requires a named Collection", null);
        }
        LinkedHashMap<String, CaretCallable> callbacks = new LinkedHashMap<>();
        for (var entry : dictionary.entries().entrySet()) {
            Value candidate = ValueSemantics.underlying(entry.getValue());
            if (!(candidate instanceof Value.Callable callable)) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_CALLABLE,
                        "Registered callback is not callable: " + entry.getKey(), null);
            }
            callbacks.put(entry.getKey(), callable(callable));
        }
        registrar.accept(callbacks);
        return value;
    }

    private CaretValue external(Value original) {
        Value value = ValueSemantics.underlying(original);
        return switch (value) {
            case Value.Num number -> new CaretValue.NumberValue(number.value());
            case Value.Str text -> new CaretValue.TextValue(text.value());
            case Value.Bool bool -> new CaretValue.BooleanValue(bool.value());
            case Value.Null ignored -> CaretValue.NullValue.INSTANCE;
            case Value.Missing ignored -> CaretValue.MissingValue.INSTANCE;
            case Value.Field field -> new CaretValue.FieldValue(field.key(), external(field.value()));
            case Value.Seq sequence -> new CaretValue.SequenceValue(sequence.values().stream().map(this::external).toList());
            case Value.Dictionary dictionary -> collection(dictionary.entries());
            case Value.EmptyCollection ignored -> new CaretValue.CollectionValue(Map.of());
            case Value.ProjectedDictionary dictionary -> collection(dictionary.fields());
            case Value.Callable callable -> callable(callable);
            case Value.Reflective reflective -> collection(reflective.fields());
            case Value.Attributed ignored -> throw new IllegalStateException("Attributed value was not unwrapped");
        };
    }

    private CaretValue.CollectionValue collection(Map<String, Value> fields) {
        LinkedHashMap<String, CaretValue> result = new LinkedHashMap<>();
        fields.forEach((name, value) -> result.put(name, external(value)));
        return new CaretValue.CollectionValue(result);
    }

    private Value internal(CaretValue value) {
        Objects.requireNonNull(value, "Caret value");
        return switch (value) {
            case CaretValue.NumberValue number -> new Value.Num(number.value());
            case CaretValue.TextValue text -> new Value.Str(text.value());
            case CaretValue.BooleanValue bool -> new Value.Bool(bool.value());
            case CaretValue.NullValue ignored -> Value.Null.INSTANCE;
            case CaretValue.MissingValue ignored -> Value.Missing.INSTANCE;
            case CaretValue.FieldValue field -> new Value.Field(field.name(), internal(field.value()));
            case CaretValue.SequenceValue sequence -> new Value.Seq(sequence.values().stream().map(this::internal).toList());
            case CaretValue.CollectionValue collection -> {
                LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
                collection.fields().forEach((name, nested) -> fields.put(name, internal(nested)));
                yield new Value.Dictionary(fields);
            }
            case CaretCallable callable -> {
                Value.Callable internal = callableHandles.get(callable);
                if (internal == null) {
                    throw new IllegalArgumentException("Foreign callable");
                }
                yield internal;
            }
        };
    }

    private CaretCallable callable(Value.Callable value) {
        CaretCallable handle = callableFactory.apply(value, value.remainingArity());
        callableHandles.put(handle, value);
        return handle;
    }
}
