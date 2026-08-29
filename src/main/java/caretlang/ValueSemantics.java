package caretlang;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Language-owned policies for public kinds, reflection, equality, and value rendering. */
final class ValueSemantics {
    private ValueSemantics() {}

    static Value underlying(Value value) {
        while (value instanceof Value.Attributed attributed) value = attributed.value();
        return value;
    }

    static String kind(Value value) { return ValueKind.of(value).publicName(); }

    static Map<String, Value> reflectionFields(Value value) {
        return reflectionFields(value, ReflectionContext.defining());
    }

    static Map<String, Value> reflectionFields(Value value, ReflectionContext context) {
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        fields.put("kind", new Value.Str(kind(value)));
        switch (value) {
            case Value.EmptyCollection ignored -> {
                fields.put("shape", new Value.Str("empty"));
                fields.put("size", new Value.Num(0));
            }
            case Value.Dictionary dictionary -> {
                fields.put("shape", new Value.Str("named"));
                fields.put("size", new Value.Num(dictionary.size()));
                fields.put("names", new Value.Str(String.join(",", dictionary.entries().keySet())));
            }
            case Value.ProjectedDictionary dictionary -> {
                Map<String, Value> projected = dictionary.fields(context);
                fields.put("shape", new Value.Str("named"));
                fields.put("size", new Value.Num(projected.size()));
                fields.put("names", new Value.Str(String.join(",", projected.keySet())));
            }
            case Value.Seq sequence -> fields.put("size", new Value.Num(sequence.size()));
            case Value.Reflective reflective -> fields.putAll(reflective instanceof Value.ProjectedDictionary projected
                    ? projected.fields(context) : reflective.fields());
            default -> { }
        }
        return fields;
    }

    private record Pair(Value left, Value right) {}
    private record RenderValue(Value value, int indent, boolean quoteStrings) {}
    private record RenderNested(Value value, int indent, Function<Value, String> renderer) {}

    static boolean equal(Value left, Value right) {
        return equal(left, right, ReflectionContext.defining());
    }

    static boolean equal(Value left, Value right, ReflectionContext context) {
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
            if (a instanceof Value.ProjectedDictionary x && x.semanticIdentity() != null
                    || b instanceof Value.ProjectedDictionary y && y.semanticIdentity() != null) {
                if (!(a instanceof Value.ProjectedDictionary x)
                        || !(b instanceof Value.ProjectedDictionary y)
                        || x.semanticIdentity() != y.semanticIdentity()) return false;
                continue;
            }
            if (a instanceof Value.Field(String key, Value value) && b instanceof Value.Field(
                    String key1, Value value1
            )) {
                if (!key.equals(key1)) return false;
                pending.push(new Pair(value, value1));
                continue;
            }
            if (isEmptyCollection(a) && isEmptyCollection(b)) continue;
            if (a instanceof Value.Callable || b instanceof Value.Callable) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CALLABLE_EQUALITY,
                        "Callable values cannot be compared for equality", null);
            }
            if (a instanceof Value.Num(double x) && b instanceof Value.Num(double y)) {
                if (x != y) return false;
            } else if (a instanceof Value.ProjectedDictionary x && b instanceof Value.ProjectedDictionary y) {
                if (!enqueueFields(x.fields(context), y.fields(context), pending)) return false;
            } else if (a instanceof Value.ProjectedDictionary x && b instanceof Value.Dictionary y) {
                if (!enqueueFields(x.fields(context), y.entries(), pending)) return false;
            } else if (a instanceof Value.Dictionary x && b instanceof Value.ProjectedDictionary y) {
                if (!enqueueFields(x.entries(), y.fields(context), pending)) return false;
            } else if (a instanceof Value.Dictionary x && b instanceof Value.Dictionary y) {
                if (!enqueueFields(x.entries(), y.entries(), pending)) return false;
            } else if (a instanceof Value.Seq x && b instanceof Value.Seq y) {
                if (x.size() != y.size()) return false;
                var xs = x.iterator();
                var ys = y.iterator();
                while (xs.hasNext()) pending.push(new Pair(xs.next(), ys.next()));
            } else if (!Objects.equals(a, b)) {
                return false;
            }
        }
        return true;
    }

    static String render(Value root) {
        return render(root, null, ReflectionContext.defining());
    }

    static String render(Value root, Function<Value, String> nestedRenderer) {
        return render(root, nestedRenderer, ReflectionContext.defining());
    }

    static String render(Value root, Function<Value, String> nestedRenderer, ReflectionContext context) {
        StringBuilder output = new StringBuilder();
        ArrayDeque<Object> pending = new ArrayDeque<>();
        pending.push(new RenderValue(root, 0, false));
        while (!pending.isEmpty()) {
            Object item = pending.pop();
            switch (item) {
                case String text -> output.append(text);
                case RenderNested(Value value, int indent, Function<Value, String> renderer) -> {
                    Value raw = underlying(value);
                    String rendered = raw instanceof Value.Str(String value1) ? quoted(value1) : renderer.apply(value);
                    output.append(indentFollowingLines(rendered, indent));
                }
                case RenderValue(Value.Attributed attributed, int indent, boolean quoteStrings) ->
                        pending.push(new RenderValue(attributed.value(), indent, quoteStrings));
                case RenderValue(Value.EmptyCollection ignored, int ignoredIndent, boolean ignoredQuote) ->
                        output.append("[]");
                case RenderValue(Value.Str string, int ignoredIndent, boolean quoteStrings) ->
                        output.append(quoteStrings ? quoted(string.value()) : string.value());
                case RenderValue(Value.Dictionary dictionary, int indent, boolean ignoredQuote) -> {
                    if (dictionary.size() == 0) {
                        output.append("[]");
                        continue;
                    }
                    List<Map.Entry<String, Value>> entries = List.copyOf(dictionary.entries().entrySet());
                    pending.push("\n" + spaces(indent) + "]");
                    for (int index = entries.size() - 1; index >= 0; index--) {
                        Map.Entry<String, Value> entry = entries.get(index);
                        if (index + 1 < entries.size()) pending.push("\n");
                        pending.push(nestedRenderer == null
                                ? new RenderValue(entry.getValue(), indent + 2, true)
                                : new RenderNested(entry.getValue(), indent + 2, nestedRenderer));
                        pending.push(quoted(entry.getKey()) + " = ");
                        pending.push(spaces(indent + 2));
                    }
                    pending.push("[\n");
                }
                case RenderValue(Value.ProjectedDictionary dictionary, int indent, boolean ignoredQuote) -> {
                    List<Map.Entry<String, Value>> entries = List.copyOf(dictionary.fields(context).entrySet());
                    pending.push("\n" + spaces(indent) + "]");
                    for (int index = entries.size() - 1; index >= 0; index--) {
                        Map.Entry<String, Value> entry = entries.get(index);
                        if (index + 1 < entries.size()) pending.push("\n");
                        pending.push(nestedRenderer == null
                                ? new RenderValue(entry.getValue(), indent + 2, true)
                                : new RenderNested(entry.getValue(), indent + 2, nestedRenderer));
                        pending.push(quoted(entry.getKey()) + " = ");
                        pending.push(spaces(indent + 2));
                    }
                    pending.push("[\n");
                }
                case RenderValue(Value.Seq sequence, int indent, boolean ignoredQuote) -> {
                    if (sequence.size() == 0) {
                        output.append("[]");
                        continue;
                    }
                    List<Value> values = sequence.values();
                    boolean multiline = values.stream().anyMatch(ValueSemantics::isCollection);
                    if (!multiline) {
                        pending.push(" ]");
                        for (int index = values.size() - 1; index >= 0; index--) {
                            pending.push(nestedRenderer == null
                                    ? new RenderValue(values.get(index), indent, true)
                                    : new RenderNested(values.get(index), indent, nestedRenderer));
                            if (index > 0) pending.push(" ");
                        }
                        pending.push("[ ");
                        continue;
                    }
                    pending.push("\n" + spaces(indent) + "]");
                    for (int index = values.size() - 1; index >= 0; index--) {
                        if (index + 1 < values.size()) pending.push("\n");
                        pending.push(nestedRenderer == null
                                ? new RenderValue(values.get(index), indent + 2, true)
                                : new RenderNested(values.get(index), indent + 2, nestedRenderer));
                        pending.push(spaces(indent + 2));
                    }
                    pending.push("[\n");
                }
                case RenderValue(Value.Field field, int indent, boolean ignoredQuote) -> {
                    pending.push(new RenderValue(field.value(), indent, true));
                    pending.push(quoted(field.key()) + " = ");
                }
                case RenderValue(Value value, int ignoredIndent, boolean ignoredQuote) -> output.append(value);
                default -> throw new IllegalStateException("Unknown render task: " + item);
            }
        }
        return output.toString();
    }

    private static boolean isCollection(Value value) {
        value = underlying(value);
        return value instanceof Value.EmptyCollection || value instanceof Value.Dictionary || value instanceof Value.Seq;
    }

    private static String spaces(int count) { return " ".repeat(count); }

    private static String indentFollowingLines(String text, int indent) {
        return text.replace("\n", "\n" + spaces(indent));
    }

    private static String quoted(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> result.appendCodePoint(codePoint);
            }
        });
        return result.append('"').toString();
    }

    private static boolean isEmptyCollection(Value value) {
        return value instanceof Value.EmptyCollection
                || value instanceof Value.Seq sequence && sequence.size() == 0
                || value instanceof Value.Dictionary dictionary && dictionary.size() == 0;
    }

    private static boolean enqueueFields(Map<String, Value> left, Map<String, Value> right,
                                         ArrayDeque<Pair> pending) {
        if (!left.keySet().equals(right.keySet())) return false;
        for (String key : left.keySet()) pending.push(new Pair(left.get(key), right.get(key)));
        return true;
    }
}
