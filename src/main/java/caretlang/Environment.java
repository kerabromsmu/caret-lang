package caretlang;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class Environment {
    record LocalBinding(String name, int slot) {}
    private static final class Binding {
        private Value value;
        private boolean initialized;
    }

    private final Environment parent;
    private final Map<String, Binding> values = new LinkedHashMap<>();
    private final List<Binding> slots = new ArrayList<>();

    Environment(Environment parent) {
        this.parent = parent;
    }

    void define(String name, Value value) {
        declare(name);
        initialize(name, value);
    }

    void declare(String name) {
        if (values.containsKey(name)) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.DUPLICATE_DEFINITION,
                    "Duplicate definition: " + name, null);
        }
        values.put(name, new Binding());
        slots.add(values.get(name));
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
            return binding.value;
        }
        if (parent != null) return parent.get(name);
        throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.UNKNOWN_NAME,
                "Unknown name: " + name, null);
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
        return binding.value;
    }

    List<LocalBinding> localBindings() {
        ArrayList<LocalBinding> bindings = new ArrayList<>(values.size());
        int slot = 0;
        for (String name : values.keySet()) bindings.add(new LocalBinding(name, slot++));
        return List.copyOf(bindings);
    }
}
