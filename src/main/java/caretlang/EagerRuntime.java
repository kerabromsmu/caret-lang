package caretlang;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Enumeration-first, depth-first materialization of language Collection values. */
final class EagerRuntime {
    private final SourceSpan span;
    private final ReflectionContext observer;
    private final IdentityHashMap<Value, Value> completed = new IdentityHashMap<>();
    private final IdentityHashMap<Value, Boolean> active = new IdentityHashMap<>();

    private EagerRuntime(SourceSpan span, ReflectionContext observer) {
        this.span = span;
        this.observer = observer;
    }

    static Value materialize(Value value, SourceSpan span, ReflectionContext observer) {
        return new EagerRuntime(span, observer).visit(value);
    }

    private Value visit(Value value) {
        if (value instanceof Value.Attributed(Value nested, java.util.Set<ContractDescriptor> contracts)) {
            Value previous = completed.get(value);
            if (previous != null) return previous;
            Value changed = visit(nested);
            var retained = new java.util.LinkedHashSet<ContractDescriptor>();
            for (ContractDescriptor contract : contracts) {
                if (contract.accepts(changed)) retained.add(contract);
            }
            Value result = retained.isEmpty() ? changed : new Value.Attributed(changed, retained);
            completed.put(value, result);
            return result;
        }
        if (value instanceof Value.Container || value instanceof Value.Callable) return value;
        if (value instanceof Value.PackedCollection) return value;
        if (value instanceof Value.ProjectedDictionary projection && projection.isReflection()
                || value instanceof Value.Dictionary dictionary && dictionary.isReflection()) {
            return Value.EmptyCollection.INSTANCE;
        }
        if (value instanceof Value.Field field) {
            Value previous = completed.get(value);
            if (previous != null) return previous;
            if (active.put(value, true) != null) throw cycle();
            try {
                Value result = new Value.Field(visit(field.key()), visit(field.value()));
                completed.put(value, result);
                return result;
            }
            finally { active.remove(value); }
        }
        CollectionRuntime.Provider provider = CollectionRuntime.provider(value, observer).orElse(null);
        if (provider == null) return value;
        if (active.containsKey(value)) throw cycle();
        Value previous = completed.get(value);
        if (previous != null) return previous;
        CollectionRuntime.Facts facts = provider.facts();
        facts.validate(span);
        if (facts.finite() == CollectionRuntime.Guarantee.FALSE) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.EAGER_INFINITE,
                    "eager cannot materialize a declared-infinite Collection", span);
        }
        active.put(value, true);
        try {
            Value keyEnumeration = provider.keys();
            boolean keysAvailable = ValueSemantics.underlying(keyEnumeration) != Value.Missing.INSTANCE;
            List<Value> enumeratedKeys = keysAvailable ? snapshot(keyEnumeration) : List.of();
            Value raw = ValueSemantics.underlying(value);
            boolean incremental = keysAvailable && (raw instanceof Value.LazySeq
                    || raw instanceof Value.LazyCollection);
            List<Value> sourceEntries = incremental ? List.of() : snapshotEntries(provider, facts);
            ArrayList<Value.SettledCollection.Entry> entries = new ArrayList<>();
            for (int index = 0; ; index++) {
                Value sourceEntry = incremental ? lazyEntry(raw, facts, index)
                        : index < sourceEntries.size() ? sourceEntries.get(index) : null;
                if (sourceEntry == null) break;
                Value key;
                Value entryValue;
                if (facts.keyed() == CollectionRuntime.Guarantee.TRUE) {
                    if (!(ValueSemantics.underlying(sourceEntry) instanceof Value.Field field)) {
                        throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CONTRACT_VIOLATION,
                                "Contract violation for eager provider: expected Field entry", span);
                    }
                    key = visit(field.key());
                    boolean duplicate = entries.stream().anyMatch(entry -> ValueSemantics.equal(entry.key(), key));
                    if (duplicate) continue;
                    entryValue = facts.hasValues() == CollectionRuntime.Guarantee.FALSE
                            ? Value.Missing.INSTANCE : visit(field.value());
                } else {
                    key = keysAvailable && index < enumeratedKeys.size()
                            ? visit(enumeratedKeys.get(index)) : Value.Missing.INSTANCE;
                    entryValue = visit(sourceEntry);
                }
                entries.add(new Value.SettledCollection.Entry(key, entryValue));
            }
            CollectionRuntime.Guarantee unique = facts.keyed() == CollectionRuntime.Guarantee.TRUE
                    && facts.hasValues() == CollectionRuntime.Guarantee.FALSE
                    ? CollectionRuntime.Guarantee.TRUE : CollectionRuntime.Guarantee.UNKNOWN;
            CollectionRuntime.Facts resultFacts = new CollectionRuntime.Facts(facts.sequential(),
                    facts.ordered(), unique, CollectionRuntime.Guarantee.TRUE,
                    facts.keyed(), facts.hasValues());
            Value result = new Value.SettledCollection(entries, resultFacts, keysAvailable, ValueKind.of(value));
            completed.put(value, result);
            return result;
        } finally { active.remove(value); }
    }

    private List<Value> snapshotEntries(CollectionRuntime.Provider provider, CollectionRuntime.Facts facts) {
        Value enumeration = facts.keyed() == CollectionRuntime.Guarantee.TRUE
                ? provider.fieldEntries() : provider.valueEntries();
        if (ValueSemantics.underlying(enumeration) == Value.Missing.INSTANCE) {
            enumeration = provider.fieldEntries();
        }
        return snapshot(enumeration);
    }

    private Value lazyEntry(Value raw, CollectionRuntime.Facts facts, int index) {
        if (raw instanceof Value.LazySeq sequence) {
            return index < sequence.length() ? sequence.at(index) : null;
        }
        Value.LazyCollection collection = (Value.LazyCollection) raw;
        return collection.entryAt(index).map(entry -> {
            if (facts.keyed() != CollectionRuntime.Guarantee.TRUE) return entry.value();
            return (Value) new Value.Field(entry.key(), facts.hasValues() == CollectionRuntime.Guarantee.FALSE
                    ? Value.Missing.INSTANCE : entry.value());
        }).orElse(null);
    }

    private List<Value> snapshot(Value enumeration) {
        Value raw = ValueSemantics.underlying(enumeration);
        if (raw == Value.EmptyCollection.INSTANCE) return List.of();
        if (raw instanceof Value.Seq sequence) return List.copyOf(sequence.values());
        if (raw instanceof Value.LazySeq sequence) return sequence.materialize();
        throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CONTRACT_VIOLATION,
                "Contract violation for eager provider: expected enumerable Sequence", span);
    }

    private LangException cycle() {
        return new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.EAGER_CYCLE,
                "eager encountered cyclic Collection containment", span);
    }
}
