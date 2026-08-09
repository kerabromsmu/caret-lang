package caretlang;

import java.util.LinkedHashMap;
import java.util.Map;

final class Environment {
    private static final class Binding {
        private Value value;
        private boolean initialized;
    }

    private final Environment parent;
    private final Map<String, Binding> values = new LinkedHashMap<>();

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
}
