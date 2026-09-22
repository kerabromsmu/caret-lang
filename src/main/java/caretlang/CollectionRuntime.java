package caretlang;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Shared runtime policy for collection representation, protocol access, and canonical field order. */
final class CollectionRuntime {
    private CollectionRuntime() {}

    static final Comparator<String> FIELD_ORDER = CollectionRuntime::compareCodePoints;

    enum Guarantee {
        TRUE, FALSE, UNKNOWN;

        Value value() {
            return switch (this) {
                case TRUE -> new Value.Bool(true);
                case FALSE -> new Value.Bool(false);
                case UNKNOWN -> Value.Missing.INSTANCE;
            };
        }
    }

    record Facts(Guarantee sequential, Guarantee ordered, Guarantee unique, Guarantee finite,
                 Guarantee keyed, Guarantee hasValues) {
        Facts {
            java.util.Objects.requireNonNull(sequential);
            java.util.Objects.requireNonNull(ordered);
            java.util.Objects.requireNonNull(unique);
            java.util.Objects.requireNonNull(finite);
            java.util.Objects.requireNonNull(keyed);
            java.util.Objects.requireNonNull(hasValues);
        }

        void validate(SourceSpan span) {
            if (sequential == Guarantee.TRUE && ordered == Guarantee.FALSE) {
                throw new LangException(Diagnostic.Phase.RUNTIME,
                        Diagnostic.Codes.CONTRADICTORY_COLLECTION_GUARANTEES,
                        "A sequential Collection cannot declare ordered as false", span);
            }
            if (sequential == Guarantee.TRUE && hasValues == Guarantee.FALSE) {
                throw new LangException(Diagnostic.Phase.RUNTIME,
                        Diagnostic.Codes.CONTRADICTORY_COLLECTION_GUARANTEES,
                        "A sequential Collection cannot declare hasValues as false", span);
            }
        }
    }

    /** Internal provider protocol. Public custom-provider construction remains deferred. */
    interface Provider {
        Value getElement(Value key);
        Value keys();
        Value valueEntries();
        Value fieldEntries();
        Value size();
        Facts facts();
    }

    static Optional<Provider> provider(Value input) {
        Value value = ValueSemantics.underlying(input);
        if (value instanceof Provider provider) return Optional.of(provider);
        if (value == Value.EmptyCollection.INSTANCE) return Optional.of(EmptyProvider.INSTANCE);
        if (value instanceof Value.Seq sequence) return Optional.of(new SequenceProvider(sequence));
        if (value instanceof Value.Dictionary dictionary) {
            return Optional.of(new DictionaryProvider(dictionary.entries()));
        }
        if (value instanceof Value.ProjectedDictionary projected) {
            return Optional.of(new DictionaryProvider(projected.fields(ReflectionContext.defining())));
        }
        return Optional.empty();
    }

    static boolean isCollection(Value value) { return provider(value).isPresent(); }

    private enum EmptyProvider implements Provider {
        INSTANCE;

        private static final Facts FACTS = new Facts(Guarantee.UNKNOWN, Guarantee.UNKNOWN,
                Guarantee.TRUE, Guarantee.TRUE, Guarantee.UNKNOWN, Guarantee.UNKNOWN);

        @Override public Value getElement(Value key) { return Value.Missing.INSTANCE; }
        @Override public Value keys() { return Value.EmptyCollection.INSTANCE; }
        @Override public Value valueEntries() { return Value.EmptyCollection.INSTANCE; }
        @Override public Value fieldEntries() { return Value.EmptyCollection.INSTANCE; }
        @Override public Value size() { return new Value.Num(0); }
        @Override public Facts facts() { return FACTS; }
    }

    private record SequenceProvider(Value.Seq sequence) implements Provider {
        private static final Facts FACTS = new Facts(Guarantee.TRUE, Guarantee.TRUE,
                Guarantee.UNKNOWN, Guarantee.TRUE, Guarantee.FALSE, Guarantee.TRUE);

        @Override public Value getElement(Value key) {
            if (!(ValueSemantics.underlying(key) instanceof Value.Num(double number))
                    || number < 0 || number != Math.rint(number) || number > Integer.MAX_VALUE) {
                return Value.Missing.INSTANCE;
            }
            return sequence.find((int) number).orElse(Value.Missing.INSTANCE);
        }

        @Override public Value keys() {
            ArrayList<Value> keys = new ArrayList<>(sequence.size());
            for (int index = 0; index < sequence.size(); index++) keys.add(new Value.Num(index));
            return new Value.Seq(keys);
        }

        @Override public Value valueEntries() { return sequence; }
        @Override public Value fieldEntries() { return sequence; }
        @Override public Value size() { return new Value.Num(sequence.size()); }
        @Override public Facts facts() { return FACTS; }
    }

    private record DictionaryProvider(Map<String, Value> entries) implements Provider {
        private static final Facts FACTS = new Facts(Guarantee.FALSE, Guarantee.TRUE,
                Guarantee.UNKNOWN, Guarantee.TRUE, Guarantee.TRUE, Guarantee.TRUE);

        private DictionaryProvider {
            entries = Map.copyOf(entries);
        }

        @Override public Value getElement(Value key) {
            if (!(ValueSemantics.underlying(key) instanceof Value.Str(String name))) {
                return Value.Missing.INSTANCE;
            }
            return entries.getOrDefault(name, Value.Missing.INSTANCE);
        }

        @Override public Value keys() {
            return new Value.Seq(entries.keySet().stream().sorted(FIELD_ORDER).map(Value.Str::new).toList());
        }

        @Override public Value valueEntries() {
            return new Value.Seq(orderedEntries().stream().map(Map.Entry::getValue).toList());
        }

        @Override public Value fieldEntries() {
            return new Value.Seq(orderedEntries().stream()
                    .map(entry -> (Value) new Value.Field(new Value.Str(entry.getKey()), entry.getValue())).toList());
        }

        @Override public Value size() { return new Value.Num(entries.size()); }
        @Override public Facts facts() { return FACTS; }

        private List<Map.Entry<String, Value>> orderedEntries() {
            LinkedHashMap<String, Value> ordered = new LinkedHashMap<>();
            entries.entrySet().stream().sorted(Map.Entry.comparingByKey(FIELD_ORDER))
                    .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
            return List.copyOf(ordered.entrySet());
        }
    }

    private static int compareCodePoints(String left, String right) {
        var leftPoints = left.codePoints().iterator();
        var rightPoints = right.codePoints().iterator();
        while (leftPoints.hasNext() && rightPoints.hasNext()) {
            int comparison = Integer.compare(leftPoints.nextInt(), rightPoints.nextInt());
            if (comparison != 0) return comparison;
        }
        return Boolean.compare(leftPoints.hasNext(), rightPoints.hasNext());
    }
}
