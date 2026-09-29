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
            Value entryEnumeration = facts.keyed() == CollectionRuntime.Guarantee.TRUE
                    ? provider.fieldEntries() : provider.valueEntries();
            if (ValueSemantics.underlying(entryEnumeration) == Value.Missing.INSTANCE) {
                entryEnumeration = provider.fieldEntries();
            }
            List<Value> sourceEntries = snapshot(entryEnumeration);
            ArrayList<Value.SettledCollection.Entry> entries = new ArrayList<>();
            int index = 0;
            for (Value sourceEntry : sourceEntries) {
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
                index++;
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
