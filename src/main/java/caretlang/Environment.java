package caretlang;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class Environment {
    record Checkpoint(int size, List<Value> values, List<Boolean> initialized) {}
    record LocalBinding(String name, int slot, Integer callableArity, Integer contractParameterArity,
                        Boolean refinementEligible) {}
    private static final class Binding {
        private Value value;
        private Supplier<Value> supplier;
        private boolean initialized;

        private Value read() {
            if (!initialized) {
                throw new LangException(Diagnostic.Phase.RUNTIME,
                        Diagnostic.Codes.READ_BEFORE_INITIALIZATION,
                        "Binding read before initialization", null);
            }
            if (supplier != null && value == null) value = java.util.Objects.requireNonNull(supplier.get());
            return value;
        }
    }

    static final class BindingReference {
        private final Binding binding;

        private BindingReference(Binding binding) { this.binding = binding; }

        private Value read() {
            if (!binding.initialized) {
                throw new LangException(Diagnostic.Phase.RUNTIME,
                        Diagnostic.Codes.READ_BEFORE_INITIALIZATION,
                        "Binding read before initialization", null);
            }
            return binding.read();
        }
    }

    private final Environment parent;
    private final Map<String, Binding> values = new LinkedHashMap<>();
    private final List<Binding> slots = new ArrayList<>();
    private final List<String> slotNames = new ArrayList<>();
    private final Map<Integer, BindingReference> captures;

    Environment(Environment parent) {
        this(parent, Map.of());
    }

    Environment(Environment parent, Map<Integer, BindingReference> captures) {
        this.parent = parent;
        this.captures = Map.copyOf(captures);
    }

    void define(String name, Value value) {
        declare(name);
        initialize(name, value);
    }

    void defineLazy(String name, Supplier<Value> supplier) {
        declare(name);
        Binding binding = values.get(name);
        binding.supplier = java.util.Objects.requireNonNull(supplier);
        binding.initialized = true;
    }

    void resetLazy(String name, Supplier<Value> supplier) {
        Binding binding = values.get(name);
        if (binding == null || binding.supplier == null) throw new IllegalArgumentException("Not a lazy binding: " + name);
        binding.supplier = java.util.Objects.requireNonNull(supplier);
        binding.value = null;
    }

    void declare(String name) {
        if (values.containsKey(name)) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.DUPLICATE_DEFINITION,
                    "Duplicate definition: " + name, null);
        }
        values.put(name, new Binding());
        slots.add(values.get(name));
        slotNames.add(name);
    }

    void initialize(String name, Value value) {
        Binding binding = values.get(name);
        if (binding == null) throw new IllegalStateException("Binding was not declared: " + name);
        if (binding.initialized) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.DUPLICATE_DEFINITION,
                    "Duplicate definition: " + name, null);
        }
        binding.value = value;
        binding.initialized = true;
    }

    Value get(String name) {
        Binding binding = values.get(name);
        if (binding != null) {
            if (!binding.initialized) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.READ_BEFORE_INITIALIZATION,
                        "Binding read before initialization: " + name, null);
            }
            return binding.read();
        }
        if (parent != null) return parent.get(name);
        throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.UNKNOWN_NAME,
                "Unknown name: " + name, null);
    }

    Value localValue(String name) {
        Binding binding = values.get(name);
        return binding == null || !binding.initialized ? null : binding.read();
    }

    void replace(String name, Value value) {
        Binding binding = values.get(name);
        if (binding == null || !binding.initialized) throw new IllegalStateException("Binding is not initialized: " + name);
        binding.value = value;
    }

    Value getAt(int lexicalDepth, int slot) {
        Environment environment = this;
        for (int i = 0; i < lexicalDepth; i++) {
            if (environment.parent == null) throw new IllegalStateException("Invalid lexical depth");
            environment = environment.parent;
        }
        if (slot < 0 || slot >= environment.slots.size()) throw new IllegalStateException("Invalid lexical slot");
        Binding binding = environment.slots.get(slot);
        if (!binding.initialized) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.READ_BEFORE_INITIALIZATION,
                    "Binding read before initialization", null);
        }
        return binding.read();
    }

    BindingReference referenceAt(int lexicalDepth, int slot) {
        Environment environment = this;
        for (int i = 0; i < lexicalDepth; i++) {
            if (environment.parent == null) throw new IllegalStateException("Invalid capture depth");
            environment = environment.parent;
        }
        if (slot < 0 || slot >= environment.slots.size()) {
            throw new IllegalStateException("Invalid capture slot");
        }
        return new BindingReference(environment.slots.get(slot));
    }

    Value getResolved(Resolution.Binding binding) {
        if (!binding.captured()) return getAt(binding.lexicalDepth(), binding.slot());
        for (Environment environment = this; environment != null; environment = environment.parent) {
            BindingReference reference = environment.captures.get(binding.symbolId());
            if (reference != null) return reference.read();
        }
        throw new IllegalStateException("Missing captured binding metadata for symbol " + binding.symbolId());
    }

    List<LocalBinding> localBindings() {
        ArrayList<LocalBinding> bindings = new ArrayList<>(slotNames.size());
        for (int slot = 0; slot < slotNames.size(); slot++) {
            Value value = slots.get(slot).initialized ? slots.get(slot).value : null;
            if (value != null) value = ValueSemantics.underlying(value);
            Integer arity = value instanceof Value.Callable callable ? callable.remainingArity() : null;
            Integer contractArity = value instanceof Value.ContractValue contract
                    ? contract.descriptor().parameterArity() : null;
            Boolean refinement = value instanceof Value.Callable callable
                    && !(value instanceof Value.ContractValue) ? callable.refinementEligible() : null;
            bindings.add(new LocalBinding(slotNames.get(slot), slot, arity, contractArity, refinement));
        }
        return List.copyOf(bindings);
    }

    Checkpoint checkpoint() {
        return new Checkpoint(slots.size(), slots.stream().map(binding -> binding.value).toList(),
                slots.stream().map(binding -> binding.initialized).toList());
    }

    void rollbackTo(Checkpoint checkpoint) {
        if (checkpoint.size() < 0 || checkpoint.size() > slots.size()) {
            throw new IllegalArgumentException("Invalid environment checkpoint");
        }
        while (slots.size() > checkpoint.size()) {
            slots.removeLast();
            values.remove(slotNames.removeLast());
        }
        for (int index = 0; index < checkpoint.size(); index++) {
            Binding binding = slots.get(index);
            // Host-lazy resolution belongs to the immutable environment snapshot, not Caret state.
            if (binding.supplier == null) {
                binding.value = checkpoint.values().get(index);
                binding.initialized = checkpoint.initialized().get(index);
            }
        }
    }
}
