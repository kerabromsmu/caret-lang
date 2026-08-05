package caretlang;

import java.util.*;
import java.util.function.Function;

public sealed interface Value permits Value.Num, Value.Str, Value.Bool, Value.Null, Value.Missing,
        Value.Name, Value.Scope, Value.Callable {

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
    }

    non-sealed interface Callable extends Value {
        Value apply(Value argument);
        int remainingArity();
    }

    final class FunctionValue implements Callable {
        private final String name;
        private final List<String> params;
        private final List<Value> bound;
        private final Function<List<Value>, Value> implementation;

        public FunctionValue(String name, List<String> params, Function<List<Value>, Value> implementation) {
            this(name, params, List.of(), implementation);
        }

        private FunctionValue(String name, List<String> params, List<Value> bound,
                              Function<List<Value>, Value> implementation) {
            this.name = name;
            this.params = List.copyOf(params);
            this.bound = List.copyOf(bound);
            this.implementation = implementation;
        }

        public Value invokeZero() {
            if (remainingArity() != 0) throw new LangException("Function still requires arguments: " + name);
            return implementation.apply(bound);
        }

        @Override public Value apply(Value argument) {
            ArrayList<Value> next = new ArrayList<>(bound);
            next.add(argument);
            if (next.size() == params.size()) {
                return implementation.apply(next);
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

        @Override public Value apply(Value argument) {
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
