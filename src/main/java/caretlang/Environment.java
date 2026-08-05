package caretlang;

import java.util.HashMap;
import java.util.Map;

final class Environment {
    private final Environment parent;
    private final Map<String, Value> values = new HashMap<>();

    Environment(Environment parent) {
        this.parent = parent;
    }

    void define(String name, Value value) {
        values.put(name, value);
    }

    Value get(String name) {
        if (values.containsKey(name)) return values.get(name);
        if (parent != null) return parent.get(name);
        throw new LangException("Unknown name: " + name);
    }
}
