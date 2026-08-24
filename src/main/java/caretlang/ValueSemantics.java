package caretlang;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Language-owned policies for public kinds, reflection, equality, and value rendering. */
final class ValueSemantics {
    private ValueSemantics() {}

    static Value underlying(Value value) {
        while (value instanceof Value.Attributed attributed) value = attributed.value();
        return value;
    }

    static String kind(Value value) { return ValueKind.of(value).publicName(); }

    static Map<String, Value> reflectionFields(Value value) {
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        fields.put("kind", new Value.Str(kind(value)));
        switch (value) {
            case Value.EmptyCollection ignored -> {
                fields.put("shape", new Value.Str("empty"));
                fields.put("size", new Value.Num(0));
            }
            case Value.NamedCollection collection -> {
                fields.put("shape", new Value.Str("named"));
                fields.put("size", new Value.Num(collection.fields().size()));
                fields.put("names", new Value.Str(String.join(",", collection.fields().keySet())));
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
    private record SequenceFrame(java.util.Iterator<Value> values, boolean first) {}
    private record EntriesFrame(java.util.Iterator<Map.Entry<String, Value>> entries,
                                String keyPrefix, boolean first) {}

    static boolean equal(Value left, Value right) {
        ArrayDeque<Pair> pending = new ArrayDeque<>();
        pending.push(new Pair(left, right));
        while (!pending.isEmpty()) {
            Pair pair = pending.pop();
            Value a = pair.left();
            Value b = pair.right();
            if (a instanceof Value.Attributed attributed) a = attributed.value();
            if (b instanceof Value.Attributed attributed) b = attributed.value();
            if (a instanceof Value.ContractValue x && b instanceof Value.ContractValue y) {
                if (x.descriptor() != y.descriptor()) return false;
                continue;
            }
            if (isEmptyCollection(a) && isEmptyCollection(b)) continue;
            if (a instanceof Value.Callable || b instanceof Value.Callable) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CALLABLE_EQUALITY,
                        "Callable values cannot be compared for equality", null);
            }
            if (a instanceof Value.Num(double x) && b instanceof Value.Num(double y)) {
                if (x != y) return false;
            } else if (a instanceof Value.NamedCollection x && b instanceof Value.NamedCollection y) {
                if (!enqueueFields(x.fields(), y.fields(), pending)) return false;
            } else if (a instanceof Value.Seq x && b instanceof Value.Seq y) {
                if (x.size() != y.size()) return false;
                var xs = x.iterator();
                var ys = y.iterator();
                while (xs.hasNext()) pending.push(new Pair(xs.next(), ys.next()));
            } else if (a instanceof Value.Dict x && b instanceof Value.Dict y) {
                if (x.size() != y.size()) return false;
                for (Map.Entry<String, Value> entry : x.orderedEntries()) {
                    Optional<Value> other = y.find(entry.getKey());
                    if (other.isEmpty()) return false;
                    pending.push(new Pair(entry.getValue(), other.get()));
                }
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
                case Value.EmptyCollection ignored -> output.append("[]");
                case Value.NamedCollection collection -> pushMap(collection.fields(), "[", "]", "^", pending);
                case Value.Dict dictionary -> {
                    pending.push("]");
                    pending.push(new EntriesFrame(dictionary.orderedEntries().iterator(), "#", true));
                    pending.push("#[");
                }
                case Value.Seq sequence -> {
                    pending.push("]");
                    pending.push(new SequenceFrame(sequence.iterator(), true));
                    pending.push("[");
                }
                case SequenceFrame frame -> {
                    if (frame.values().hasNext()) {
                        Value value = frame.values().next();
                        pending.push(new SequenceFrame(frame.values(), false));
                        pending.push(value);
                        if (!frame.first()) pending.push(", ");
                    }
                }
                case EntriesFrame frame -> {
                    if (frame.entries().hasNext()) {
                        Map.Entry<String, Value> entry = frame.entries().next();
                        pending.push(new EntriesFrame(frame.entries(), frame.keyPrefix(), false));
                        pending.push(entry.getValue());
                        pending.push(" = ");
                        pending.push(entry.getKey());
                        pending.push(frame.keyPrefix());
                        if (!frame.first()) pending.push(", ");
                    }
                }
                case Value.Attributed attributed -> pending.push(attributed.value());
                default -> output.append(item);
            }
        }
        return output.toString();
    }

    private static boolean isEmptyCollection(Value value) {
        return value instanceof Value.EmptyCollection
                || value instanceof Value.Seq sequence && sequence.size() == 0
                || value instanceof Value.Dict dictionary && dictionary.size() == 0
                || value instanceof Value.NamedCollection collection && collection.fields().isEmpty();
    }

    private static void pushMap(Map<String, Value> values, String open, String close,
                                String keyPrefix, ArrayDeque<Object> pending) {
        pushEntries(values.entrySet(), open, close, keyPrefix, pending);
    }

    private static void pushEntries(Iterable<Map.Entry<String, Value>> values, String open, String close,
                                    String keyPrefix, ArrayDeque<Object> pending) {
        List<Map.Entry<String, Value>> entries = new ArrayList<>();
        values.forEach(entries::add);
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
