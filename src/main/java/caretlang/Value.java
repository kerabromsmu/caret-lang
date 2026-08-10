package caretlang;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

@SuppressWarnings("NullableProblems")
public sealed interface Value permits Value.Num, Value.Str, Value.Bool, Value.Null, Value.Missing,
        Value.Name, Value.Reflective, Value.Seq, Value.Dict, Value.Callable {

    record Argument(Value value, SourceSpan span) {
        public Argument {
            Objects.requireNonNull(value, "argument value");
            Objects.requireNonNull(span, "argument span");
        }
    }

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

    non-sealed interface Reflective extends Value {
        Optional<Value> find(String name);
        Map<String, Value> fields();
    }

    final class Scope implements Reflective {
        private final LinkedHashMap<String, Value> fields;

        public Scope(Map<String, Value> fields) {
            this.fields = checkedMap(fields);
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
        private sealed interface Node permits Leaf, Branch {
            int size();
            Value get(int index);
            void appendTo(List<Value> output);
        }
        private record Leaf(Value value) implements Node {
            private Leaf { Objects.requireNonNull(value); }
            @Override public int size() { return 1; }
            @Override public Value get(int index) {
                if (index != 0) throw new IndexOutOfBoundsException(index);
                return value;
            }
            @Override public void appendTo(List<Value> output) { output.add(value); }
        }
        private record Branch(Node left, Node right, int size) implements Node {
            private Branch(Node left, Node right) {
                this(Objects.requireNonNull(left), Objects.requireNonNull(right), left.size() + right.size());
            }
            @Override public Value get(int index) {
                return index < left.size() ? left.get(index) : right.get(index - left.size());
            }
            @Override public void appendTo(List<Value> output) {
                left.appendTo(output);
                right.appendTo(output);
            }
        }

        private final List<Node> chunks;
        private final int size;
        private volatile List<Value> materialized;

        public Seq(Collection<? extends Value> values) {
            Objects.requireNonNull(values);
            ArrayList<Node> built = new ArrayList<>();
            for (Value value : values) appendChunk(built, new Leaf(value));
            this.chunks = List.copyOf(built);
            this.size = values.size();
            this.materialized = List.copyOf(values);
        }

        private Seq(List<Node> chunks, int size) {
            this.chunks = List.copyOf(chunks);
            this.size = size;
        }

        public List<Value> values() {
            List<Value> result = materialized;
            if (result != null) return result;
            ArrayList<Value> combined = new ArrayList<>(size);
            for (Node chunk : chunks) chunk.appendTo(combined);
            result = List.copyOf(combined);
            materialized = result;
            return result;
        }

        public Seq appended(Value value) {
            ArrayList<Node> updated = new ArrayList<>(chunks);
            appendChunk(updated, new Leaf(value));
            return new Seq(updated, size + 1);
        }

        public int size() { return size; }

        public Optional<Value> find(int index) {
            if (index < 0 || index >= size) return Optional.empty();
            int remaining = index;
            for (Node chunk : chunks) {
                if (remaining < chunk.size()) return Optional.of(chunk.get(remaining));
                remaining -= chunk.size();
            }
            throw new IllegalStateException("Sequence index was not covered by its chunks");
        }

        private static void appendChunk(ArrayList<Node> chunks, Node added) {
            while (!chunks.isEmpty() && chunks.getLast().size() == added.size()) {
                added = new Branch(chunks.removeLast(), added);
            }
            chunks.add(added);
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
        private record Node(String key, Value value, Node left, Node right, int height) {
            private Node {
                Objects.requireNonNull(key);
                Objects.requireNonNull(value);
            }
        }

        private final Node root;
        private final Seq keys;
        private volatile Map<String, Value> materialized;

        public Dict(Map<String, Value> entries) {
            LinkedHashMap<String, Value> checked = checkedMap(entries);
            Node builtRoot = null;
            Seq builtKeys = new Seq(List.of());
            for (Map.Entry<String, Value> entry : checked.entrySet()) {
                builtRoot = putNode(builtRoot, entry.getKey(), entry.getValue());
                builtKeys = builtKeys.appended(new Name(entry.getKey()));
            }
            this.root = builtRoot;
            this.keys = builtKeys;
            this.materialized = Collections.unmodifiableMap(checked);
        }

        private Dict(Node root, Seq keys) {
            this.root = root;
            this.keys = keys;
        }

        public Map<String, Value> entries() {
            Map<String, Value> result = materialized;
            if (result != null) return result;

            LinkedHashMap<String, Value> combined = new LinkedHashMap<>();
            for (Value key : keys.values()) {
                String name = ((Name) key).value();
                combined.put(name, find(name).orElseThrow());
            }
            result = Collections.unmodifiableMap(combined);
            materialized = result;
            return result;
        }

        public Optional<Value> find(String key) {
            Objects.requireNonNull(key);
            for (Node node = root; node != null; ) {
                int comparison = key.compareTo(node.key());
                if (comparison == 0) return Optional.of(node.value());
                node = comparison < 0 ? node.left() : node.right();
            }
            return Optional.empty();
        }

        public Dict put(String key, Value value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            boolean present = containsKey(key);
            return new Dict(putNode(root, key, value), present ? keys : keys.appended(new Name(key)));
        }

        public boolean containsKey(String key) { return find(key).isPresent(); }
        public int size() { return keys.size(); }

        private static Node putNode(Node node, String key, Value value) {
            if (node == null) return new Node(key, value, null, null, 1);
            int comparison = key.compareTo(node.key());
            if (comparison == 0) return new Node(key, value, node.left(), node.right(), node.height());
            Node updated = comparison < 0
                    ? node(node.key(), node.value(), putNode(node.left(), key, value), node.right())
                    : node(node.key(), node.value(), node.left(), putNode(node.right(), key, value));
            return balance(updated);
        }

        private static Node balance(Node node) {
            int balance = height(node.left()) - height(node.right());
            if (balance > 1) {
                Node left = node.left();
                if (height(left.left()) < height(left.right())) left = rotateLeft(left);
                return rotateRight(node(node.key(), node.value(), left, node.right()));
            }
            if (balance < -1) {
                Node right = node.right();
                if (height(right.right()) < height(right.left())) right = rotateRight(right);
                return rotateLeft(node(node.key(), node.value(), node.left(), right));
            }
            return node;
        }

        private static Node rotateLeft(Node node) {
            Node right = node.right();
            Node moved = node(node.key(), node.value(), node.left(), right.left());
            return node(right.key(), right.value(), moved, right.right());
        }

        private static Node rotateRight(Node node) {
            Node left = node.left();
            Node moved = node(node.key(), node.value(), left.right(), node.right());
            return node(left.key(), left.value(), left.left(), moved);
        }

        private static Node node(String key, Value value, Node left, Node right) {
            return new Node(key, value, left, right, Math.max(height(left), height(right)) + 1);
        }

        private static int height(Node node) { return node == null ? 0 : node.height(); }

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
        Value apply(Argument argument, SourceSpan callSpan);
        int remainingArity();

        default Value invokeZero(SourceSpan callSpan) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                    "Callable still requires arguments", callSpan);
        }
    }

    final class FunctionValue implements Callable {
        private final String name;
        private final List<String> params;
        private final List<Argument> bound;
        private final BiFunction<List<Argument>, SourceSpan, Value> implementation;

        public FunctionValue(String name, List<String> params, Function<List<Value>, Value> implementation) {
            this(name, params, List.of(), (arguments, ignoredSpan) -> implementation.apply(
                    arguments.stream().map(Argument::value).toList()));
        }

        FunctionValue(String name, List<String> params,
                      BiFunction<List<Argument>, SourceSpan, Value> implementation) {
            this(name, params, List.of(), implementation);
        }

        private FunctionValue(String name, List<String> params, List<Argument> bound,
                              BiFunction<List<Argument>, SourceSpan, Value> implementation) {
            this.name = name;
            this.params = List.copyOf(params);
            this.bound = List.copyOf(bound);
            this.implementation = implementation;
        }

        @Override public Value invokeZero(SourceSpan callSpan) {
            if (remainingArity() != 0) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                        "Function still requires arguments: " + name, callSpan);
            }
            return implementation.apply(bound, callSpan);
        }

        @Override public Value apply(Argument argument, SourceSpan callSpan) {
            ArrayList<Argument> next = new ArrayList<>(bound);
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

    final class FunctionReference implements Reflective {
        private final Callable target;

        FunctionReference(Callable target) {
            this.target = Objects.requireNonNull(target);
        }

        @Override public Optional<Value> find(String name) {
            return Optional.ofNullable(fields().get(name));
        }

        @Override public Map<String, Value> fields() {
            return Map.of("kind", new Str("Function"),
                    "remaining", new Num(target.remainingArity()));
        }

        @Override public boolean equals(Object other) {
            return other instanceof FunctionReference reference && target == reference.target;
        }

        @Override public int hashCode() { return System.identityHashCode(target); }

        @Override public String toString() { return "<function-reference " + target + ">"; }
    }

    final class HoleFunction implements Callable {
        private final String display;
        private final int arity;
        private final List<Argument> bound;
        private final Function<List<Argument>, Value> implementation;

        public HoleFunction(String display, int arity, Function<List<Argument>, Value> implementation) {
            this(display, arity, List.of(), implementation);
        }

        private HoleFunction(String display, int arity, List<Argument> bound,
                             Function<List<Argument>, Value> implementation) {
            this.display = display;
            this.arity = arity;
            this.bound = List.copyOf(bound);
            this.implementation = implementation;
        }

        @Override public Value apply(Argument argument, SourceSpan callSpan) {
            ArrayList<Argument> next = new ArrayList<>(bound);
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

    private static LinkedHashMap<String, Value> checkedMap(Map<String, Value> values) {
        Objects.requireNonNull(values, "values");
        LinkedHashMap<String, Value> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "value name"), Objects.requireNonNull(value, "value")));
        return copy;
    }
}
