package caretlang;

import java.util.IdentityHashMap;

/** Conservative runtime uniqueness facts; never part of Caret value semantics or reflection. */
final class OwnershipTracker {
    enum Mode { ENABLED, DISABLED }

    private enum State { UNIQUE, SHARED }

    private final Mode mode;
    private final IdentityHashMap<Value, State> states = new IdentityHashMap<>();
    private int reuseCount;

    OwnershipTracker(Mode mode) { this.mode = mode; }

    <T extends Value> T fresh(T value) {
        if (mode == Mode.ENABLED && reusable(value)) states.put(value, State.UNIQUE);
        return value;
    }

    void share(Value value) {
        value = ValueSemantics.underlying(value);
        if (states.put(value, State.SHARED) == State.SHARED) return;
        if (value instanceof Value.Seq sequence) sequence.values().forEach(this::share);
        if (value instanceof Value.Dictionary dictionary) dictionary.entries().values().forEach(this::share);
        if (value instanceof Value.Callable callable) callable.retainedValues().forEach(this::share);
    }

    Value.Seq append(Value.Seq sequence, Value value) {
        share(value);
        if (mode == Mode.ENABLED && states.get(sequence) == State.UNIQUE) {
            sequence.appendOwned(value);
            reuseCount++;
            return sequence;
        }
        return fresh(sequence.appended(value));
    }

    Value.Dictionary put(Value.Dictionary dictionary, String key, Value value) {
        share(value);
        if (mode == Mode.ENABLED && states.get(dictionary) == State.UNIQUE) {
            dictionary.putOwned(key, value);
            reuseCount++;
            return dictionary;
        }
        return fresh(dictionary.put(key, value));
    }

    int reuseCount() { return reuseCount; }

    private static boolean reusable(Value value) {
        return value instanceof Value.Seq || value instanceof Value.Dictionary;
    }
}
