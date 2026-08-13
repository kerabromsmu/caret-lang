package caretlang;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Language-owned policies for public kinds, reflection, equality, and value rendering. */
final class ValueSemantics {
    enum Descriptor {
        NUMBER("Number"), STRING("String"), BOOLEAN("Boolean"), NULL("Null"), MISSING("Missing"),
        SCOPE("Scope"), SEQUENCE("Sequence"), DICTIONARY("Dictionary"), FUNCTION("Function"),
        CONTRACT("Contract"), REFLECTIVE("Reflective");

        private final String publicName;
        Descriptor(String publicName) { this.publicName = publicName; }
        String publicName() { return publicName; }
    }

    private ValueSemantics() {}

    static Value underlying(Value value) {
        while (value instanceof Value.Attributed attributed) value = attributed.value();
        return value;
    }

    static Descriptor descriptor(Value value) {
        Objects.requireNonNull(value);
        value = underlying(value);
        return switch (value) {
            case Value.Num ignored -> Descriptor.NUMBER;
            case Value.Str ignored -> Descriptor.STRING;
            case Value.Bool ignored -> Descriptor.BOOLEAN;
            case Value.Null ignored -> Descriptor.NULL;
            case Value.Missing ignored -> Descriptor.MISSING;
            case Value.Scope ignored -> Descriptor.SCOPE;
            case Value.Seq ignored -> Descriptor.SEQUENCE;
            case Value.Dict ignored -> Descriptor.DICTIONARY;
            case Value.FunctionReference ignored -> Descriptor.FUNCTION;
            case Value.ContractValue ignored -> Descriptor.CONTRACT;
            case Value.Attributed attributed -> descriptor(attributed.value());
            case Value.Reflective ignored -> Descriptor.REFLECTIVE;
            case Value.Callable ignored -> Descriptor.FUNCTION;
        };
    }

    static String kind(Value value) { return descriptor(value).publicName(); }

    static Map<String, Value> reflectionFields(Value value) {
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        fields.put("kind", new Value.Str(kind(value)));
        switch (value) {
            case Value.Scope scope -> {
                fields.put("size", new Value.Num(scope.fields().size()));
                fields.put("names", new Value.Str(String.join(",", scope.fields().keySet())));
            }
            case Value.Seq sequence -> fields.put("size", new Value.Num(sequence.size()));
            case Value.Dict dictionary -> {
                fields.put("size", new Value.Num(dictionary.size()));
                fields.put("names", new Value.Str(String.join(",", dictionary.entries().keySet())));
            }
            case Value.Reflective reflective -> fields.putAll(reflective.fields());
            default -> { }
        }
        return fields;
    }

    private record Pair(Value left, Value right) {}

    static boolean equal(Value left, Value right) {
        ArrayDeque<Pair> pending = new ArrayDeque<>();
        pending.push(new Pair(left, right));
        while (!pending.isEmpty()) {
            Pair pair = pending.pop();
            Value a = pair.left();
            Value b = pair.right();
            if (a instanceof Value.Attributed attributed) a = attributed.value();
            if (b instanceof Value.Attributed attributed) b = attributed.value();
            if (a instanceof Value.Callable || b instanceof Value.Callable) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CALLABLE_EQUALITY,
                        "Callable values cannot be compared for equality", null);
            }
            if (a instanceof Value.Num(double x) && b instanceof Value.Num(double y)) {
                if (x != y) return false;
            } else if (a instanceof Value.Scope x && b instanceof Value.Scope y) {
                if (!enqueueFields(x.fields(), y.fields(), pending)) return false;
            } else if (a instanceof Value.Seq x && b instanceof Value.Seq y) {
                List<Value> xs = x.values();
                List<Value> ys = y.values();
                if (xs.size() != ys.size()) return false;
                for (int i = xs.size() - 1; i >= 0; i--) pending.push(new Pair(xs.get(i), ys.get(i)));
            } else if (a instanceof Value.Dict x && b instanceof Value.Dict y) {
                if (!enqueueFields(x.entries(), y.entries(), pending)) return false;
            } else if (!Objects.equals(a, b)) {
                return false;
            }
        }
        return true;
    }

    static String render(Value root) {
        StringBuilder output = new StringBuilder();
        ArrayDeque<Object> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Object item = pending.pop();
            switch (item) {
                case String text -> output.append(text);
                case Value.Scope scope -> pushMap(scope.fields(), "^{", "}", "", pending);
                case Value.Dict dictionary -> pushMap(dictionary.entries(), "#[", "]", "#", pending);
                case Value.Seq sequence -> {
                    List<Value> values = sequence.values();
                    pending.push("]");
                    for (int i = values.size() - 1; i >= 0; i--) {
                        pending.push(values.get(i));
                        if (i > 0) pending.push(", ");
                    }
                    pending.push("[");
                }
                case Value.Attributed attributed -> pending.push(attributed.value());
                default -> output.append(item);
            }
        }
        return output.toString();
    }

    private static void pushMap(Map<String, Value> values, String open, String close,
                                String keyPrefix, ArrayDeque<Object> pending) {
        List<Map.Entry<String, Value>> entries = new ArrayList<>(values.entrySet());
        pending.push(close);
        for (int i = entries.size() - 1; i >= 0; i--) {
            Map.Entry<String, Value> entry = entries.get(i);
            pending.push(entry.getValue());
            pending.push(" = ");
            pending.push(entry.getKey());
            pending.push(keyPrefix);
            if (i > 0) pending.push(", ");
        }
        pending.push(open);
    }

    private static boolean enqueueFields(Map<String, Value> left, Map<String, Value> right,
                                         ArrayDeque<Pair> pending) {
        if (!left.keySet().equals(right.keySet())) return false;
        for (String key : left.keySet()) pending.push(new Pair(left.get(key), right.get(key)));
        return true;
    }
}
