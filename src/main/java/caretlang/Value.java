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
        private final List<Value> values;

        public Seq(Collection<? extends Value> values) {
            this.values = List.copyOf(values);
        }

        public List<Value> values() {
            return values;
        }

        public Seq appended(Value value) {
            ArrayList<Value> result = new ArrayList<>(values);
            result.add(value);
            return new Seq(result);
        }

        @Override public boolean equals(Object other) {
            return other instanceof Seq sequence && values.equals(sequence.values);
        }

        @Override public int hashCode() { return values.hashCode(); }

        @Override public String toString() {
            StringJoiner joiner = new StringJoiner(", ", "[", "]");
            values.forEach(value -> joiner.add(value.toString()));
            return joiner.toString();
        }
    }

    final class Dict implements Value {
        private final LinkedHashMap<String, Value> entries;

        public Dict(Map<String, Value> entries) {
            this.entries = new LinkedHashMap<>(entries);
        }

        public Map<String, Value> entries() {
            return Collections.unmodifiableMap(entries);
        }

        public Optional<Value> find(String key) {
            return entries.containsKey(key) ? Optional.of(entries.get(key)) : Optional.empty();
        }

        public Dict put(String key, Value value) {
            LinkedHashMap<String, Value> result = new LinkedHashMap<>(entries);
            result.put(key, value);
            return new Dict(result);
        }

        @Override public boolean equals(Object other) {
            return other instanceof Dict dictionary && entries.equals(dictionary.entries);
        }

        @Override public int hashCode() { return entries.hashCode(); }

        @Override public String toString() {
            StringJoiner joiner = new StringJoiner(", ", "#[", "]");
            entries.forEach((key, value) -> joiner.add("#" + key + " = " + value));
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
            if (remainingArity() != 0) throw new LangException("Function still requires arguments: " + name);
            return implementation.apply(bound, null);
        }

        @Override public Value apply(Value argument, SourceSpan callSpan) {
            ArrayList<Value> next = new ArrayList<>(bound);
            next.add(argument);
            if (next.size() == params.size()) {
                return implementation.apply(next, callSpan);
            }
            if (next.size() > params.size()) {
                throw new LangException("Too many arguments for " + name);
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
            if (next.size() > arity) throw new LangException("Too many arguments for partial expression");
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
