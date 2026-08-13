package caretlang;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public sealed interface Value permits Value.Num, Value.Str, Value.Bool, Value.Null, Value.Missing,
        Value.Reflective, Value.Seq, Value.Dict, Value.Callable, Value.Attributed {

    record Attributed(Value value, Set<ContractDescriptor> contracts) implements Value {
        public Attributed {
            Objects.requireNonNull(value);
            contracts = Set.copyOf(contracts);
        }
        @SuppressWarnings("NullableProblems")
        @Override public String toString() { return value.toString(); }
    }

    record Argument(Value value, SourceSpan span) {
        public Argument {
            Objects.requireNonNull(value, "argument value");
            Objects.requireNonNull(span, "argument span");
        }
    }

    record Num(double value) implements Value {
        public Num {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Caret numbers must be finite");
        }
        @SuppressWarnings("NullableProblems")
        @Override public String toString() {
            long asLong = (long) value;
            return value == asLong ? Long.toString(asLong) : Double.toString(value);
        }
    }

    record Str(String value) implements Value {
        public Str { Objects.requireNonNull(value, "string value"); }
        @SuppressWarnings("NullableProblems")
        @Override public String toString() { return value; }
    }

    record Bool(boolean value) implements Value {
        @SuppressWarnings("NullableProblems")
        @Override public String toString() { return Boolean.toString(value); }
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
            return ValueSemantics.render(this);
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
        private static final List<Value> UNMATERIALIZED = Collections.unmodifiableList(new ArrayList<>());
        private volatile List<Value> materialized = UNMATERIALIZED;

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
            if (result != UNMATERIALIZED) return result;
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
            return ValueSemantics.render(this);
        }
    }

    final class Dict implements Value {
        private sealed interface Tree permits EmptyTree, Node {}

        private enum EmptyTree implements Tree { INSTANCE }

        private record Node(String key, Value value, Tree left, Tree right, int height) implements Tree {
            private Node {
                Objects.requireNonNull(key);
                Objects.requireNonNull(value);
                Objects.requireNonNull(left);
                Objects.requireNonNull(right);
            }
        }

        private static final Map<String, Value> UNMATERIALIZED =
                Collections.unmodifiableMap(new LinkedHashMap<>());
        private final Tree root;
        private final Seq keys;
        private volatile Map<String, Value> materialized = UNMATERIALIZED;

        public Dict(Map<String, Value> entries) {
            LinkedHashMap<String, Value> checked = checkedMap(entries);
            Tree builtRoot = EmptyTree.INSTANCE;
            Seq builtKeys = new Seq(List.of());
            for (Map.Entry<String, Value> entry : checked.entrySet()) {
                builtRoot = putNode(builtRoot, entry.getKey(), entry.getValue());
                builtKeys = builtKeys.appended(new Str(entry.getKey()));
            }
            this.root = builtRoot;
            this.keys = builtKeys;
            this.materialized = Collections.unmodifiableMap(checked);
        }

        private Dict(Tree root, Seq keys) {
            this.root = Objects.requireNonNull(root);
            this.keys = Objects.requireNonNull(keys);
        }

        public Map<String, Value> entries() {
            Map<String, Value> result = materialized;
            if (result != UNMATERIALIZED) return result;

            LinkedHashMap<String, Value> combined = new LinkedHashMap<>();
            for (Value key : keys.values()) {
                String name = ((Str) key).value();
                combined.put(name, find(name).orElseThrow());
            }
            result = Collections.unmodifiableMap(combined);
            materialized = result;
            return result;
        }

        public Optional<Value> find(String key) {
            Objects.requireNonNull(key);
            for (Tree tree = root; tree instanceof Node node; ) {
                int comparison = key.compareTo(node.key());
                if (comparison == 0) return Optional.of(node.value());
                tree = comparison < 0 ? node.left() : node.right();
            }
            return Optional.empty();
        }

        public Dict put(String key, Value value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            boolean present = containsKey(key);
            return new Dict(putNode(root, key, value), present ? keys : keys.appended(new Str(key)));
        }

        public boolean containsKey(String key) { return find(key).isPresent(); }
        public int size() { return keys.size(); }

        private static Tree putNode(Tree tree, String key, Value value) {
            if (tree == EmptyTree.INSTANCE) {
                return new Node(key, value, EmptyTree.INSTANCE, EmptyTree.INSTANCE, 1);
            }
            Node node = (Node) tree;
            int comparison = key.compareTo(node.key());
            if (comparison == 0) return new Node(key, value, node.left(), node.right(), node.height());
            Node updated = comparison < 0
                    ? node(node.key(), node.value(), putNode(node.left(), key, value), node.right())
                    : node(node.key(), node.value(), node.left(), putNode(node.right(), key, value));
            return balance(updated);
        }

        private static Tree balance(Node node) {
            int balance = height(node.left()) - height(node.right());
            if (balance > 1) {
                Node left = populated(node.left());
                if (height(left.left()) < height(left.right())) left = rotateLeft(left);
                return rotateRight(node(node.key(), node.value(), left, node.right()));
            }
            if (balance < -1) {
                Node right = populated(node.right());
                if (height(right.right()) < height(right.left())) right = rotateRight(right);
                return rotateLeft(node(node.key(), node.value(), node.left(), right));
            }
            return node;
        }

        private static Node rotateLeft(Node node) {
            Node right = populated(node.right());
            Node moved = node(node.key(), node.value(), node.left(), right.left());
            return node(right.key(), right.value(), moved, right.right());
        }

        private static Node rotateRight(Node node) {
            Node left = populated(node.left());
            Node moved = node(node.key(), node.value(), left.right(), node.right());
            return node(left.key(), left.value(), left.left(), moved);
        }

        private static Node node(String key, Value value, Tree left, Tree right) {
            return new Node(key, value, left, right, Math.max(height(left), height(right)) + 1);
        }

        private static int height(Tree tree) {
            return tree instanceof Node node ? node.height() : 0;
        }

        private static Node populated(Tree tree) {
            if (tree instanceof Node node) return node;
            throw new IllegalStateException("Balanced dictionary tree expected a populated child");
        }

        @Override public boolean equals(Object other) {
            return other instanceof Dict dictionary && entries().equals(dictionary.entries());
        }

        @Override public int hashCode() { return entries().hashCode(); }

        @Override public String toString() {
            return ValueSemantics.render(this);
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

    final class ContractValue implements Callable, Reflective {
        private final ContractDescriptor contract;

        ContractValue(ContractDescriptor contract) { this.contract = Objects.requireNonNull(contract); }
        ContractDescriptor descriptor() { return contract; }

        @Override public Value apply(Argument argument, SourceSpan callSpan) {
            return new Bool(contract.accepts(argument.value()));
        }

        @Override public int remainingArity() { return 1; }
        @Override public Optional<Value> find(String name) { return Optional.ofNullable(fields().get(name)); }
        @Override public Map<String, Value> fields() {
            LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
            fields.put("kind", new Str("Contract"));
            fields.put("name", new Str(contract.publicName()));
            fields.put("bases", new Seq(contract.bases().stream()
                    .map(base -> (Value) new Str(base.publicName())).toList()));
            return Collections.unmodifiableMap(fields);
        }
        @Override public String toString() { return "<contract " + contract.publicName() + ">"; }
    }

    final class ContractedCallable implements Callable {
        private final Callable target;
        private final int parameterIndex;
        private final java.util.function.BiFunction<Integer, Argument, Argument> validator;

        ContractedCallable(Callable target, java.util.function.BiFunction<Integer, Argument, Argument> validator) {
            this(target, 0, validator);
        }

        private ContractedCallable(Callable target, int parameterIndex,
                                   java.util.function.BiFunction<Integer, Argument, Argument> validator) {
            this.target = Objects.requireNonNull(target);
            this.parameterIndex = parameterIndex;
            this.validator = Objects.requireNonNull(validator);
        }

        @Override public Value apply(Argument argument, SourceSpan callSpan) {
            argument = validator.apply(parameterIndex, argument);
            int before = target.remainingArity();
            Value result = target.apply(argument, callSpan);
            return before > 1 && result instanceof Callable callable
                    ? new ContractedCallable(callable, parameterIndex + 1, validator) : result;
        }

        @Override public int remainingArity() { return target.remainingArity(); }
        @Override public Value invokeZero(SourceSpan callSpan) { return target.invokeZero(callSpan); }
        @Override public String toString() { return target.toString(); }
    }

    /** Persistent reverse argument chain: O(1) partial application and one materialization at invocation. */
    final class BoundArguments {
        private sealed interface Link permits EmptyLink, Node {}
        private enum EmptyLink implements Link { INSTANCE }
        private record Node(Argument value, Link previous) implements Link {
            private Node {
                Objects.requireNonNull(value);
                Objects.requireNonNull(previous);
            }
        }
        private static final BoundArguments EMPTY = new BoundArguments(EmptyLink.INSTANCE, 0);
        private final Link last;
        private final int size;

        private BoundArguments(Link last, int size) {
            this.last = Objects.requireNonNull(last);
            this.size = size;
        }

        static BoundArguments empty() { return EMPTY; }
        BoundArguments appended(Argument argument) {
            return new BoundArguments(new Node(Objects.requireNonNull(argument), last), size + 1);
        }
        int size() { return size; }
        List<Argument> values() {
            Argument[] ordered = new Argument[size];
            Link link = last;
            for (int i = size - 1; i >= 0; i--) {
                Node node = (Node) link;
                ordered[i] = node.value();
                link = node.previous();
            }
            return List.of(ordered);
        }
    }

    @FunctionalInterface
    interface CallInvoker {
        Value invoke(Callable callable, Argument argument, SourceSpan callSpan);
    }

    final class ComposedFunction implements Callable {
        private final Callable left;
        private final Callable right;
        private final CallInvoker invoker;

        ComposedFunction(Callable left, Callable right, CallInvoker invoker) {
            this.left = Objects.requireNonNull(left);
            this.right = Objects.requireNonNull(right);
            this.invoker = Objects.requireNonNull(invoker);
        }

        @Override public Value apply(Argument argument, SourceSpan callSpan) {
            int before = left.remainingArity();
            Value leftResult = invoker.invoke(left, argument, callSpan);
            if (before > 1) {
                if (!(leftResult instanceof Callable remaining)) {
                    throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                            "Composed callable lost its remaining parameters", callSpan);
                }
                return new ComposedFunction(remaining, right, invoker);
            }
            return invoker.invoke(right, new Argument(leftResult, callSpan), callSpan);
        }

        @Override public int remainingArity() {
            return left.remainingArity();
        }

        @Override public String toString() {
            return "<composition/" + remainingArity() + ">";
        }
    }

    final class FunctionValue implements Callable {
        private final String name;
        private final List<String> params;
        private final BoundArguments bound;
        private final BiFunction<List<Argument>, SourceSpan, Value> implementation;

        public FunctionValue(String name, List<String> params, Function<List<Value>, Value> implementation) {
            this(name, params, BoundArguments.empty(), valueImplementation(implementation));
        }

        FunctionValue(String name, List<String> params,
                      BiFunction<List<Argument>, SourceSpan, Value> implementation) {
            this(name, params, BoundArguments.empty(), implementation);
        }

        private FunctionValue(String name, List<String> params, BoundArguments bound,
                              BiFunction<List<Argument>, SourceSpan, Value> implementation) {
            this.name = Objects.requireNonNull(name, "function name");
            this.params = List.copyOf(params);
            this.bound = Objects.requireNonNull(bound);
            this.implementation = Objects.requireNonNull(implementation, "function implementation");
        }

        private static BiFunction<List<Argument>, SourceSpan, Value> valueImplementation(
                Function<List<Value>, Value> implementation) {
            Objects.requireNonNull(implementation, "function implementation");
            return (arguments, ignoredSpan) -> implementation.apply(
                    arguments.stream().map(Argument::value).toList());
        }

        @Override public Value invokeZero(SourceSpan callSpan) {
            if (remainingArity() != 0) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR,
                        "Function still requires arguments: " + name, callSpan);
            }
            return implementation.apply(bound.values(), callSpan);
        }

        @Override public Value apply(Argument argument, SourceSpan callSpan) {
            BoundArguments next = bound.appended(argument);
            if (next.size() == params.size()) {
                return implementation.apply(next.values(), callSpan);
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
        private final BoundArguments bound;
        private final Function<List<Argument>, Value> implementation;

        public HoleFunction(String display, int arity, Function<List<Argument>, Value> implementation) {
            this(display, arity, BoundArguments.empty(), implementation);
        }

        private HoleFunction(String display, int arity, BoundArguments bound,
                             Function<List<Argument>, Value> implementation) {
            this.display = Objects.requireNonNull(display, "partial display");
            if (arity < 1) throw new IllegalArgumentException("Partial arity must be positive");
            this.arity = arity;
            this.bound = Objects.requireNonNull(bound);
            this.implementation = Objects.requireNonNull(implementation, "partial implementation");
        }

        @Override public Value apply(Argument argument, SourceSpan callSpan) {
            BoundArguments next = bound.appended(argument);
            if (next.size() == arity) return implementation.apply(next.values());
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
