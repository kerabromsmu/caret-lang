package caretlang;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public sealed interface Value permits Value.Num, Value.Str, Value.Bool, Value.Null, Value.Missing,
        Value.Name, Value.Scope, Value.Seq, Value.Dict, Value.Callable {

    record Num(double value) implements Value {
        @Override public String toString() {
            long asLong = (long) value;
            return value == asLong ? Long.toString(asLong) : Double.toString(value);
        }
    }

    record Str(String value) implements Value {
        @Override public String toString() { return value; }
    }

    record Bool(boolean value) implements Value {
        @Override public String toString() { return Boolean.toString(value); }
    }

    record Name(String value) implements Value {
        @Override public String toString() { return "#" + value; }
    }

    enum Null implements Value {
        INSTANCE;
        @Override public String toString() { return "?"; }
    }

    enum Missing implements Value {
        INSTANCE;
        @Override public String toString() { return "~"; }
    }

    final class Scope implements Value {
        private final LinkedHashMap<String, Value> fields;

        public Scope(Map<String, Value> fields) {
            this.fields = new LinkedHashMap<>(fields);
        }

        public Optional<Value> find(String name) {
            return Optional.ofNullable(fields.get(name));
        }

        public Map<String, Value> fields() {
            return Collections.unmodifiableMap(fields);
        }

        @Override public String toString() {
            StringJoiner joiner = new StringJoiner(", ", "^{", "}");
            fields.forEach((k, v) -> joiner.add(k + " = " + v));
            return joiner.toString();
        }

        @Override public boolean equals(Object other) {
            return other instanceof Scope scope && fields.equals(scope.fields);
        }

        @Override public int hashCode() {
            return fields.hashCode();
        }
    }

    final class Seq implements Value {
        private final Seq parent;
        private final Value appended;
        private volatile List<Value> materialized;

        public Seq(Collection<? extends Value> values) {
            this.parent = null;
            this.appended = null;
            this.materialized = List.copyOf(values);
        }

        private Seq(Seq parent, Value appended) {
            this.parent = Objects.requireNonNull(parent);
            this.appended = Objects.requireNonNull(appended);
        }

        public List<Value> values() {
            List<Value> result = materialized;
            if (result != null) return result;

            ArrayDeque<Value> suffix = new ArrayDeque<>();
            Seq cursor = this;
            while (cursor.materialized == null) {
                suffix.push(cursor.appended);
                cursor = cursor.parent;
            }
            ArrayList<Value> combined = new ArrayList<>(cursor.materialized);
            while (!suffix.isEmpty()) combined.add(suffix.pop());
            result = List.copyOf(combined);
            materialized = result;
            return result;
        }

        public Seq appended(Value value) {
            return new Seq(this, value);
        }

        @Override public boolean equals(Object other) {
            return other instanceof Seq sequence && values().equals(sequence.values());
        }

        @Override public int hashCode() { return values().hashCode(); }

        @Override public String toString() {
            StringJoiner joiner = new StringJoiner(", ", "[", "]");
            values().forEach(value -> joiner.add(value.toString()));
            return joiner.toString();
        }
    }

    final class Dict implements Value {
        private final Dict parent;
        private final String addedKey;
        private final Value addedValue;
        private volatile Map<String, Value> materialized;

        public Dict(Map<String, Value> entries) {
            this.parent = null;
            this.addedKey = null;
            this.addedValue = null;
            this.materialized = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }

        private Dict(Dict parent, String key, Value value) {
            this.parent = Objects.requireNonNull(parent);
            this.addedKey = Objects.requireNonNull(key);
            this.addedValue = Objects.requireNonNull(value);
        }

        public Map<String, Value> entries() {
            Map<String, Value> result = materialized;
            if (result != null) return result;

            ArrayDeque<Dict> suffix = new ArrayDeque<>();
            Dict cursor = this;
            while (cursor.materialized == null) {
                suffix.push(cursor);
                cursor = cursor.parent;
            }
            LinkedHashMap<String, Value> combined = new LinkedHashMap<>(cursor.materialized);
            while (!suffix.isEmpty()) {
                Dict update = suffix.pop();
                combined.put(update.addedKey, update.addedValue);
            }
            result = Collections.unmodifiableMap(combined);
            materialized = result;
            return result;
        }

        public Optional<Value> find(String key) {
            Map<String, Value> values = entries();
            return values.containsKey(key) ? Optional.of(values.get(key)) : Optional.empty();
        }

        public Dict put(String key, Value value) {
            return new Dict(this, key, value);
        }

        @Override public boolean equals(Object other) {
            return other instanceof Dict dictionary && entries().equals(dictionary.entries());
        }

        @Override public int hashCode() { return entries().hashCode(); }

        @Override public String toString() {
            StringJoiner joiner = new StringJoiner(", ", "#[", "]");
            entries().forEach((key, value) -> joiner.add("#" + key + " = " + value));
            return joiner.toString();
        }
    }

    non-sealed interface Callable extends Value {
        Value apply(Value argument, SourceSpan callSpan);
        int remainingArity();
    }

    final class FunctionValue implements Callable {
        private final String name;
        private final List<String> params;
        private final List<Value> bound;
        private final BiFunction<List<Value>, SourceSpan, Value> implementation;

        public FunctionValue(String name, List<String> params, Function<List<Value>, Value> implementation) {
            this(name, params, List.of(), (arguments, ignoredSpan) -> implementation.apply(arguments));
        }

        FunctionValue(String name, List<String> params,
                      BiFunction<List<Value>, SourceSpan, Value> implementation) {
            this(name, params, List.of(), implementation);
        }

        private FunctionValue(String name, List<String> params, List<Value> bound,
                              BiFunction<List<Value>, SourceSpan, Value> implementation) {
            this.name = name;
            this.params = List.copyOf(params);
            this.bound = List.copyOf(bound);
            this.implementation = implementation;
        }

        public Value invokeZero() {
            if (remainingArity() != 0) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                        "Function still requires arguments: " + name, null);
            }
            return implementation.apply(bound, null);
        }

        @Override public Value apply(Value argument, SourceSpan callSpan) {
            ArrayList<Value> next = new ArrayList<>(bound);
            next.add(argument);
            if (next.size() == params.size()) {
                return implementation.apply(next, callSpan);
            }
            if (next.size() > params.size()) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.TOO_MANY_ARGUMENTS,
                        "Too many arguments for " + name, callSpan);
            }
            return new FunctionValue(name, params, next, implementation);
        }

        @Override public int remainingArity() {
            return params.size() - bound.size();
        }

        @Override public String toString() {
            return "<fn " + name + "/" + remainingArity() + ">";
        }
    }

    final class HoleFunction implements Callable {
        private final String display;
        private final int arity;
        private final List<Value> bound;
        private final Function<List<Value>, Value> implementation;

        public HoleFunction(String display, int arity, Function<List<Value>, Value> implementation) {
            this(display, arity, List.of(), implementation);
        }

        private HoleFunction(String display, int arity, List<Value> bound,
                             Function<List<Value>, Value> implementation) {
            this.display = display;
            this.arity = arity;
            this.bound = List.copyOf(bound);
            this.implementation = implementation;
        }

        @Override public Value apply(Value argument, SourceSpan callSpan) {
            ArrayList<Value> next = new ArrayList<>(bound);
            next.add(argument);
            if (next.size() == arity) return implementation.apply(next);
            if (next.size() > arity) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.TOO_MANY_ARGUMENTS,
                        "Too many arguments for partial expression", callSpan);
            }
            return new HoleFunction(display, arity, next, implementation);
        }

        @Override public int remainingArity() {
            return arity - bound.size();
        }

        @Override public String toString() {
            return "<partial " + display + "/" + remainingArity() + ">";
        }
    }
}
