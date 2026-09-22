package caretlang;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

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
            case Value.Field field -> {
                fields.put("key", field.key());
                fields.put("value", field.value());
            }
            case Value.KeyedCollection collection -> {
                fields.put("shape", new Value.Str(collection.shape() == Value.KeyedCollection.Shape.SET
                        ? "set" : "keyed"));
                fields.put("size", new Value.Num(collection.entries().size()));
            }
            case Value.EmptyCollection ignored -> {
                fields.put("shape", new Value.Str("empty"));
                fields.put("size", new Value.Num(0));
            }
            case Value.Dictionary dictionary -> {
                fields.put("shape", new Value.Str("named"));
                fields.put("size", new Value.Num(dictionary.size()));
                fields.put("ids", new Value.Str(String.join(",", dictionary.entries().keySet())));
            }
            case Value.ProjectedDictionary dictionary -> {
                Map<String, Value> projected = dictionary.fields(context);
                fields.put("shape", new Value.Str("named"));
                fields.put("size", new Value.Num(projected.size()));
                fields.put("ids", new Value.Str(String.join(",", projected.keySet())));
            }
            case Value.Seq sequence -> fields.put("size", new Value.Num(sequence.size()));
            case Value.LazySeq sequence -> fields.put("size", new Value.Num(sequence.length()));
            case Value.LazyCollection collection -> fields.put("size", collection.size());
            case Value.Reflective reflective -> fields.putAll(reflective instanceof Value.ProjectedDictionary projected
                    ? projected.fields(context) : reflective.fields());
            default -> { }
        }
        CollectionRuntime.provider(value).ifPresent(provider -> {
            CollectionRuntime.Facts facts = provider.facts();
            facts.validate(null);
            fields.put("sequential", facts.sequential().value());
            fields.put("ordered", facts.ordered().value());
            fields.put("unique", facts.unique().value());
            fields.put("finite", facts.finite().value());
            fields.put("keyed", facts.keyed().value());
            fields.put("hasValues", facts.hasValues().value());
            fields.put("size", provider.size());
        });
        return fields;
    }

    private record CollectionEntry(Value key, Value value) {}
    private record CollectionView(CollectionRuntime.Facts facts,
                                  IntFunction<Optional<CollectionEntry>> entryAt) {}
    private record EqualityShape(CollectionRuntime.Facts facts, boolean neutral) {}
    private record RenderValue(Value value, int indent, boolean quoteStrings) {}
    private record RenderNested(Value value, int indent, Function<Value, String> renderer) {}

    static boolean equal(Value left, Value right) {
        return equal(left, right, ReflectionContext.defining());
    }

    static boolean equal(Value left, Value right, ReflectionContext context) {
        Value a = underlying(left);
        Value b = underlying(right);
        if (a instanceof Value.ContractValue x && b instanceof Value.ContractValue y) {
            return x.descriptor() == y.descriptor();
        }
        if (a instanceof Value.ProjectedDictionary x && x.semanticIdentity() != null
                || b instanceof Value.ProjectedDictionary y && y.semanticIdentity() != null) {
            return a instanceof Value.ProjectedDictionary x
                    && b instanceof Value.ProjectedDictionary y
                    && x.semanticIdentity() == y.semanticIdentity();
        }
        if (a instanceof Value.Field x || b instanceof Value.Field y) {
            return a instanceof Value.Field x && b instanceof Value.Field y
                    && equal(x.key(), y.key(), context) && equal(x.value(), y.value(), context);
        }
        if (a instanceof Value.Callable || b instanceof Value.Callable) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CALLABLE_EQUALITY,
                    "Callable values cannot be compared for equality", null);
        }
        Optional<CollectionRuntime.Provider> leftProvider = CollectionRuntime.provider(a);
        Optional<CollectionRuntime.Provider> rightProvider = CollectionRuntime.provider(b);
        if (leftProvider.isPresent() || rightProvider.isPresent()) {
            return leftProvider.isPresent() && rightProvider.isPresent()
                    && equalCollections(a, leftProvider.get(), b, rightProvider.get(), context);
        }
        if (a instanceof Value.Num(double x) && b instanceof Value.Num(double y)) return x == y;
        return Objects.equals(a, b);
    }

    private static boolean equalCollections(Value left, CollectionRuntime.Provider leftProvider,
                                            Value right, CollectionRuntime.Provider rightProvider,
                                            ReflectionContext context) {
        CollectionRuntime.Facts leftFacts = leftProvider.facts();
        CollectionRuntime.Facts rightFacts = rightProvider.facts();
        leftFacts.validate(null);
        rightFacts.validate(null);
        if (leftFacts.finite() == CollectionRuntime.Guarantee.FALSE
                || rightFacts.finite() == CollectionRuntime.Guarantee.FALSE) return false;
        boolean leftNeutral = left == Value.EmptyCollection.INSTANCE;
        boolean rightNeutral = right == Value.EmptyCollection.INSTANCE;
        if (leftNeutral || rightNeutral) {
            CollectionView leftView = collectionView(left, leftProvider, context);
            CollectionView rightView = collectionView(right, rightProvider, context);
            return leftView.entryAt().apply(0).isEmpty() && rightView.entryAt().apply(0).isEmpty();
        }
        if (leftFacts.ordered() == CollectionRuntime.Guarantee.UNKNOWN
                || rightFacts.ordered() == CollectionRuntime.Guarantee.UNKNOWN) return false;

        EqualityShape leftShape = equalityShape(left, leftFacts);
        EqualityShape rightShape = equalityShape(right, rightFacts);
        leftFacts = leftShape.facts();
        rightFacts = rightShape.facts();
        leftNeutral = leftShape.neutral();
        rightNeutral = rightShape.neutral();
        if (!leftNeutral && !rightNeutral) {
            if (leftFacts.ordered() != rightFacts.ordered()) return false;
            if (leftFacts.keyed() == CollectionRuntime.Guarantee.UNKNOWN
                    || rightFacts.keyed() == CollectionRuntime.Guarantee.UNKNOWN
                    || leftFacts.keyed() != rightFacts.keyed()) return false;
            if (leftFacts.keyed() == CollectionRuntime.Guarantee.TRUE
                    && (leftFacts.hasValues() == CollectionRuntime.Guarantee.UNKNOWN
                    || rightFacts.hasValues() == CollectionRuntime.Guarantee.UNKNOWN
                    || leftFacts.hasValues() != rightFacts.hasValues())) return false;
        }

        CollectionView leftView = collectionView(left, leftProvider, context);
        CollectionView rightView = collectionView(right, rightProvider, context);
        if (leftNeutral || rightNeutral) {
            return leftView.entryAt().apply(0).isEmpty() && rightView.entryAt().apply(0).isEmpty();
        }
        if (leftFacts.ordered() == CollectionRuntime.Guarantee.TRUE) {
            return equalOrdered(leftView, rightView, context);
        }
        return leftFacts.keyed() == CollectionRuntime.Guarantee.TRUE
                ? equalUnorderedKeyed(leftView, rightView, context)
                : equalUnorderedValues(leftView, rightView, context);
    }

    private static EqualityShape equalityShape(Value value, CollectionRuntime.Facts facts) {
        if (value == Value.EmptyCollection.INSTANCE) return new EqualityShape(facts, true);
        if (facts.keyed() != CollectionRuntime.Guarantee.UNKNOWN
                || !(value instanceof Value.LazyCollection collection)) {
            return new EqualityShape(facts, false);
        }
        boolean empty = collection.entryAt(0).isEmpty();
        return new EqualityShape(collection.facts(), empty);
    }

    private static boolean equalOrdered(CollectionView left, CollectionView right,
                                        ReflectionContext context) {
        for (int index = 0; ; index++) {
            Optional<CollectionEntry> leftEntry = left.entryAt().apply(index);
            Optional<CollectionEntry> rightEntry = right.entryAt().apply(index);
            if (leftEntry.isEmpty() || rightEntry.isEmpty()) return leftEntry.isEmpty() && rightEntry.isEmpty();
            if (left.facts().keyed() == CollectionRuntime.Guarantee.TRUE
                    && !equal(leftEntry.get().key(), rightEntry.get().key(), context)) return false;
            if (left.facts().hasValues() != CollectionRuntime.Guarantee.FALSE
                    && !equal(leftEntry.get().value(), rightEntry.get().value(), context)) return false;
        }
    }

    private static boolean equalUnorderedKeyed(CollectionView left, CollectionView right,
                                               ReflectionContext context) {
        ArrayList<CollectionEntry> rightEntries = new ArrayList<>();
        ArrayList<Boolean> matched = new ArrayList<>();
        int rightIndex = 0;
        for (int leftIndex = 0; ; leftIndex++) {
            Optional<CollectionEntry> candidate = left.entryAt().apply(leftIndex);
            if (candidate.isEmpty()) break;
            int match = matchingEntry(candidate.get().key(), rightEntries, matched, context);
            while (match < 0) {
                Optional<CollectionEntry> added = right.entryAt().apply(rightIndex++);
                if (added.isEmpty()) return false;
                rightEntries.add(added.get());
                matched.add(false);
                match = matchingEntry(candidate.get().key(), rightEntries, matched, context);
            }
            matched.set(match, true);
            if (left.facts().hasValues() != CollectionRuntime.Guarantee.FALSE
                    && !equal(candidate.get().value(), rightEntries.get(match).value(), context)) return false;
        }
        while (right.entryAt().apply(rightIndex++).isPresent()) return false;
        return matched.stream().allMatch(Boolean::booleanValue);
    }

    private static int matchingEntry(Value key, List<CollectionEntry> candidates, List<Boolean> matched,
                                     ReflectionContext context) {
        for (int index = 0; index < candidates.size(); index++) {
            if (!matched.get(index) && equal(key, candidates.get(index).key(), context)) return index;
        }
        return -1;
    }

    private static boolean equalUnorderedValues(CollectionView left, CollectionView right,
                                                ReflectionContext context) {
        ArrayList<CollectionEntry> rightEntries = new ArrayList<>();
        ArrayList<Boolean> matched = new ArrayList<>();
        int rightIndex = 0;
        for (int leftIndex = 0; ; leftIndex++) {
            Optional<CollectionEntry> candidate = left.entryAt().apply(leftIndex);
            if (candidate.isEmpty()) break;
            int match = matchingValue(candidate.get().value(), rightEntries, matched, context);
            while (match < 0) {
                Optional<CollectionEntry> added = right.entryAt().apply(rightIndex++);
                if (added.isEmpty()) return false;
                rightEntries.add(added.get());
                matched.add(false);
                match = matchingValue(candidate.get().value(), rightEntries, matched, context);
            }
            matched.set(match, true);
        }
        while (right.entryAt().apply(rightIndex++).isPresent()) return false;
        return matched.stream().allMatch(Boolean::booleanValue);
    }

    private static int matchingValue(Value value, List<CollectionEntry> candidates, List<Boolean> matched,
                                     ReflectionContext context) {
        for (int index = 0; index < candidates.size(); index++) {
            if (!matched.get(index) && equal(value, candidates.get(index).value(), context)) return index;
        }
        return -1;
    }

    private static CollectionView collectionView(Value value, CollectionRuntime.Provider provider,
                                                 ReflectionContext context) {
        CollectionRuntime.Facts facts = provider.facts();
        if (value == Value.EmptyCollection.INSTANCE) {
            return new CollectionView(facts, ignored -> Optional.empty());
        }
        if (value instanceof Value.Seq sequence) {
            return new CollectionView(facts, index -> sequence.find(index)
                    .map(element -> new CollectionEntry(null, element)));
        }
        if (value instanceof Value.LazySeq sequence) {
            return new CollectionView(facts, index -> index >= 0 && index < sequence.length()
                    ? Optional.of(new CollectionEntry(null, sequence.at(index))) : Optional.empty());
        }
        if (value instanceof Value.LazyCollection collection) {
            return new CollectionView(facts, index -> collection.entryAt(index)
                    .map(entry -> new CollectionEntry(entry.key(), entry.value())));
        }
        if (value instanceof Value.KeyedCollection collection) {
            return new CollectionView(facts, index -> index >= 0 && index < collection.entries().size()
                    ? Optional.of(new CollectionEntry(collection.entries().get(index).key(),
                    collection.entries().get(index).value())) : Optional.empty());
        }
        if (value instanceof Value.Dictionary dictionary) {
            List<Map.Entry<String, Value>> entries = List.copyOf(dictionary.entries().entrySet());
            return dictionaryView(facts, entries);
        }
        if (value instanceof Value.ProjectedDictionary dictionary) {
            List<Map.Entry<String, Value>> entries = List.copyOf(dictionary.fields(context).entrySet());
            return dictionaryView(facts, entries);
        }
        Value fields = underlying(provider.fieldEntries());
        return new CollectionView(facts, index -> sequenceValue(fields, index).map(element -> {
            if (facts.keyed() != CollectionRuntime.Guarantee.TRUE) return new CollectionEntry(null, element);
            if (!(underlying(element) instanceof Value.Field field)) {
                throw new IllegalStateException("Keyed Collection provider returned a non-Field entry");
            }
            return new CollectionEntry(field.key(), field.value());
        }));
    }

    private static CollectionView dictionaryView(CollectionRuntime.Facts facts,
                                                  List<Map.Entry<String, Value>> entries) {
        return new CollectionView(facts, index -> index >= 0 && index < entries.size()
                ? Optional.of(new CollectionEntry(new Value.Str(entries.get(index).getKey()),
                entries.get(index).getValue())) : Optional.empty());
    }

    private static Optional<Value> sequenceValue(Value sequence, int index) {
        if (index < 0) return Optional.empty();
        if (sequence == Value.EmptyCollection.INSTANCE) return Optional.empty();
        if (sequence instanceof Value.Seq values) return values.find(index);
        if (sequence instanceof Value.LazySeq values) return index < values.length()
                ? Optional.of(values.at(index)) : Optional.empty();
        throw new IllegalStateException("Collection provider enumeration is not a Sequence");
    }

    static boolean equalityEligible(Value root) {
        ArrayDeque<Value> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Value value = underlying(pending.pop());
            if (value instanceof Value.Callable) return false;
            if (value instanceof Value.Field field) {
                pending.push(field.key());
                pending.push(field.value());
            }
            else if (value instanceof Value.KeyedCollection collection) {
                collection.entries().forEach(entry -> {
                    pending.push(entry.key());
                    pending.push(entry.value());
                });
            }
            else if (value instanceof Value.Dictionary dictionary) {
                dictionary.entries().values().forEach(pending::push);
            } else if (value instanceof Value.ProjectedDictionary dictionary) {
                dictionary.fields(ReflectionContext.defining()).values().forEach(pending::push);
            } else if (value instanceof Value.Seq sequence) {
                sequence.values().forEach(pending::push);
            } else if (value instanceof Value.LazySeq sequence) {
                sequence.materialize().forEach(pending::push);
            } else if (value instanceof Value.LazyCollection collection) {
                pending.push(collection.materializedValue());
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
                    if (ValueSemantics.underlying(field.key()) instanceof Value.Str(String key)) {
                        pending.push(quoted(key) + " = ");
                    } else {
                        pending.push(" ");
                        pending.push(new RenderValue(field.key(), indent, true));
                        pending.push("field ");
                    }
                }
                case RenderValue(Value.LazySeq sequence, int indent, boolean quote) ->
                        pending.push(new RenderValue(new Value.Seq(sequence.materialize()), indent, quote));
                case RenderValue(Value.LazyCollection collection, int indent, boolean quote) ->
                        pending.push(new RenderValue(collection.materializedValue(), indent, quote));
                case RenderValue(Value.KeyedCollection collection, int indent, boolean ignoredQuote) -> {
                    if (collection.entries().isEmpty()) {
                        output.append("[]");
                        continue;
                    }
                    pending.push("\n" + spaces(indent) + "]");
                    for (int index = collection.entries().size() - 1; index >= 0; index--) {
                        Value.KeyedCollection.Entry entry = collection.entries().get(index);
                        if (index + 1 < collection.entries().size()) pending.push("\n");
                        if (collection.shape() == Value.KeyedCollection.Shape.SET) {
                            pending.push(new RenderValue(entry.key(), indent + 2, true));
                        } else {
                            pending.push(new RenderValue(new Value.Field(entry.key(), entry.value()),
                                    indent + 2, true));
                        }
                        pending.push(spaces(indent + 2));
                    }
                    pending.push("[\n");
                }
                case RenderValue(Value value, int ignoredIndent, boolean ignoredQuote) -> output.append(value);
                default -> throw new IllegalStateException("Unknown render task: " + item);
            }
        }
        return output.toString();
    }

    private static boolean isCollection(Value value) {
        value = underlying(value);
        return value instanceof Value.EmptyCollection || value instanceof Value.Dictionary
                || value instanceof Value.Seq || value instanceof Value.LazySeq
                || value instanceof Value.LazyCollection || value instanceof Value.KeyedCollection;
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

}
