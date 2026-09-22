package caretlang;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public sealed interface Value permits Value.Num, Value.Str, Value.Bool, Value.Null, Value.Missing,
        Value.Field, Value.KeyedCollection, Value.LazyCollection, Value.LazySeq,
        Value.Reflective, Value.Seq, Value.Callable, Value.Attributed {

    record Attributed(Value value, Set<ContractDescriptor> contracts) implements Value {
        public Attributed {
            Objects.requireNonNull(value);
            contracts = Set.copyOf(contracts);
        }
        @Override public @NotNull String toString() { return value.toString(); }
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
        @Override public @NotNull String toString() {
            long asLong = (long) value;
            return value == asLong ? Long.toString(asLong) : Double.toString(value);
        }
    }

    record Str(String value) implements Value {
        public Str { Objects.requireNonNull(value, "string value"); }
        @Override public @NotNull String toString() { return value; }
    }

    record Bool(boolean value) implements Value {
        @Override public @NotNull String toString() { return Boolean.toString(value); }
    }

    enum Null implements Value {
        INSTANCE;
        @Override public String toString() { return "?"; }
    }

    enum Missing implements Value {
        INSTANCE;
        @Override public String toString() { return "~"; }
    }

    record Field(Value key, Value value) implements Value, CollectionRuntime.Provider {
        public Field {
            Objects.requireNonNull(key, "field key");
            Objects.requireNonNull(value, "field value");
        }
        @Override public Value getElement(Value index) {
            return ValueSemantics.underlying(index) instanceof Num(double number)
                    ? number == 0 ? key : number == 1 ? value : Missing.INSTANCE
                    : Missing.INSTANCE;
        }
        @Override public Value keys() { return new Seq(List.of(new Num(0), new Num(1))); }
        @Override public Value valueEntries() { return new Seq(List.of(key, value)); }
        @Override public Value fieldEntries() { return valueEntries(); }
        @Override public Value size() { return new Num(2); }
        @Override public CollectionRuntime.Facts facts() {
            return new CollectionRuntime.Facts(CollectionRuntime.Guarantee.TRUE,
                    CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.UNKNOWN,
                    CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.FALSE,
                    CollectionRuntime.Guarantee.TRUE);
        }
        @Override public @NotNull String toString() { return ValueSemantics.render(this); }
    }

    /** Settled keyed content whose keys need not be Java Strings. */
    final class KeyedCollection implements Value, CollectionRuntime.Provider {
        enum Shape { GENERAL, DICTIONARY, SET }
        record Entry(Value key, Value value) {
            Entry { Objects.requireNonNull(key); Objects.requireNonNull(value); }
        }

        private final Shape shape;
        private final List<Entry> entries;

        KeyedCollection(Shape shape, Collection<Entry> entries) {
            this.shape = Objects.requireNonNull(shape);
            this.entries = List.copyOf(entries);
        }

        Shape shape() { return shape; }
        List<Entry> entries() { return entries; }

        @Override public Value getElement(Value key) {
            for (Entry entry : entries) {
                if (ValueSemantics.equal(entry.key(), key)) {
                    return shape == Shape.SET ? entry.key() : entry.value();
                }
            }
            return Missing.INSTANCE;
        }

        @Override public Value keys() {
            return new Seq(entries.stream().map(Entry::key).toList());
        }

        @Override public Value valueEntries() {
            return shape == Shape.SET ? Missing.INSTANCE
                    : new Seq(entries.stream().map(Entry::value).toList());
        }

        @Override public Value fieldEntries() {
            return new Seq(entries.stream().map(entry -> (Value) new Field(entry.key(),
                    shape == Shape.SET ? Missing.INSTANCE : entry.value())).toList());
        }

        @Override public Value size() { return new Num(entries.size()); }

        @Override public CollectionRuntime.Facts facts() {
            return new CollectionRuntime.Facts(CollectionRuntime.Guarantee.FALSE,
                    CollectionRuntime.Guarantee.TRUE,
                    shape == Shape.SET ? CollectionRuntime.Guarantee.TRUE : CollectionRuntime.Guarantee.UNKNOWN,
                    CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.TRUE,
                    shape == Shape.SET ? CollectionRuntime.Guarantee.FALSE : CollectionRuntime.Guarantee.TRUE);
        }

        @Override public String toString() { return ValueSemantics.render(this); }
    }

    /** The single shape-neutral empty collection literal. */
    enum EmptyCollection implements Reflective {
        INSTANCE;
        @Override public Optional<Value> find(String name) { return Optional.empty(); }
        @Override public Map<String, Value> fields() { return Map.of(); }
        @Override public String toString() { return "[]"; }
    }

    non-sealed interface Reflective extends Value {
        Optional<Value> find(String name);
        Map<String, Value> fields();
    }

    /** Internal lazy Dictionary projection. Its observation context is never a Caret value. */
    final class ProjectedDictionary implements Reflective {
        @FunctionalInterface interface Projection { Map<String, Value> fields(ReflectionContext context); }
        private final Projection projection;
        private final ReflectionContext captured;
        private final Value reflectedTarget;
        private final Object semanticIdentity;

        ProjectedDictionary(Projection projection, ReflectionContext captured, Value reflectedTarget,
                            Object semanticIdentity) {
            this.projection = Objects.requireNonNull(projection);
            this.captured = Objects.requireNonNull(captured);
            this.reflectedTarget = reflectedTarget;
            this.semanticIdentity = semanticIdentity;
        }

        private ReflectionContext effective(ReflectionContext observer) { return captured.intersect(observer); }
        @Override public Optional<Value> find(String name) { return find(name, ReflectionContext.defining()); }
        Optional<Value> find(String name, ReflectionContext observer) {
            return Optional.ofNullable(fields(observer).get(name));
        }
        @Override public Map<String, Value> fields() { return fields(ReflectionContext.defining()); }
        Map<String, Value> fields(ReflectionContext observer) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(projection.fields(effective(observer))));
        }
        Optional<Value> reflectedTarget(ReflectionContext observer) {
            return reflectedTarget != null && effective(observer).dereference()
                    ? Optional.of(reflectedTarget) : Optional.empty();
        }
        Object semanticIdentity() { return semanticIdentity; }
        @Override public String toString() { return ValueSemantics.render(this); }
    }

    final class Seq implements Value, Iterable<Value> {
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

        private List<Node> chunks;
        private int size;
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

        void appendOwned(Value value) {
            ArrayList<Node> updated = new ArrayList<>(chunks);
            appendChunk(updated, new Leaf(value));
            chunks = List.copyOf(updated);
            size++;
            materialized = UNMATERIALIZED;
        }

        public int size() { return size; }

        @Override public @NotNull Iterator<Value> iterator() {
            return new Iterator<>() {
                private final ArrayDeque<Node> pending = initial();
                private Value next = advance();
                private ArrayDeque<Node> initial() {
                    ArrayDeque<Node> nodes = new ArrayDeque<>();
                    for (int i = chunks.size() - 1; i >= 0; i--) nodes.push(chunks.get(i));
                    return nodes;
                }
                private Value advance() {
                    while (!pending.isEmpty()) {
                        Node node = pending.pop();
                        if (node instanceof Leaf(Value value)) return value;
                        Branch branch = (Branch) node;
                        pending.push(branch.right());
                        pending.push(branch.left());
                    }
                    return null;
                }
                @Override public boolean hasNext() { return next != null; }
                @Override public Value next() {
                    if (next == null) throw new NoSuchElementException();
                    Value result = next;
                    next = advance();
                    return result;
                }
            };
        }

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

    /** Language-owned lazy sequential adapter; each position establishes once in this result context. */
    final class LazySeq implements Value, CollectionRuntime.Provider {
        private final int size;
        private final java.util.function.IntFunction<Value> producer;
        private final Value[] established;
        private final RuntimeException[] failures;
        private final boolean[] demanded;
        private final CollectionRuntime.Facts facts;

        LazySeq(int size, java.util.function.IntFunction<Value> producer) {
            this(size, producer, new CollectionRuntime.Facts(CollectionRuntime.Guarantee.TRUE,
                    CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.UNKNOWN,
                    CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.FALSE,
                    CollectionRuntime.Guarantee.TRUE));
        }

        LazySeq(int size, java.util.function.IntFunction<Value> producer, CollectionRuntime.Facts facts) {
            if (size < 0) throw new IllegalArgumentException("negative lazy sequence size");
            this.size = size;
            this.producer = Objects.requireNonNull(producer);
            this.facts = Objects.requireNonNull(facts);
            this.established = new Value[size];
            this.failures = new RuntimeException[size];
            this.demanded = new boolean[size];
        }

        int length() { return size; }
        synchronized Value at(int index) {
            if (index < 0 || index >= size) return Missing.INSTANCE;
            if (!demanded[index]) {
                demanded[index] = true;
                try { established[index] = Objects.requireNonNull(producer.apply(index)); }
                catch (RuntimeException failure) { failures[index] = failure; }
            }
            if (failures[index] != null) throw failures[index];
            return established[index];
        }
        List<Value> materialize() {
            ArrayList<Value> values = new ArrayList<>(size);
            for (int index = 0; index < size; index++) values.add(at(index));
            return List.copyOf(values);
        }
        @Override public Value getElement(Value key) {
            if (!(ValueSemantics.underlying(key) instanceof Num(double number))
                    || number < 0 || number != Math.rint(number) || number > Integer.MAX_VALUE) return Missing.INSTANCE;
            return at((int) number);
        }
        @Override public Value keys() {
            ArrayList<Value> keys = new ArrayList<>(size);
            for (int index = 0; index < size; index++) keys.add(new Num(index));
            return new Seq(keys);
        }
        @Override public Value valueEntries() { return new Seq(materialize()); }
        @Override public Value fieldEntries() { return valueEntries(); }
        @Override public Value size() { return new Num(size); }
        @Override public CollectionRuntime.Facts facts() { return facts; }
        @Override public String toString() { return ValueSemantics.render(this); }
    }

    /** Incremental transform result backed by an ordered stream of keyless values or keyed fields. */
    final class LazyCollection implements Value, CollectionRuntime.Provider {
        enum Shape { INFER, KEYLESS, KEYED, SET }
        record Produced(Value key, Value value, Shape shape) {
            Produced {
                Objects.requireNonNull(value);
                Objects.requireNonNull(shape);
                if (shape == Shape.KEYLESS && key != null) throw new IllegalArgumentException("keyless result has a key");
                if ((shape == Shape.KEYED || shape == Shape.SET) && key == null) {
                    throw new IllegalArgumentException("keyed result has no key");
                }
            }
        }
        @FunctionalInterface interface Producer { Produced next(); }

        private Shape shape;
        private final Producer producer;
        private final CollectionRuntime.Facts initialFacts;
        private final Integer knownSize;
        private final SourceSpan sourceSpan;
        private final boolean dictionarySelectable;
        private boolean dictionarySelected;
        private boolean shapeLocked;
        private final ArrayList<Produced> established = new ArrayList<>();
        private RuntimeException failure;
        private boolean exhausted;

        LazyCollection(Shape shape, Producer producer, CollectionRuntime.Facts facts, Integer knownSize,
                       SourceSpan sourceSpan, boolean dictionarySelectable, boolean dictionarySelected) {
            this.shape = Objects.requireNonNull(shape);
            this.producer = Objects.requireNonNull(producer);
            this.initialFacts = Objects.requireNonNull(facts);
            this.knownSize = knownSize;
            this.sourceSpan = sourceSpan;
            this.dictionarySelectable = dictionarySelectable;
            this.dictionarySelected = dictionarySelected;
        }

        synchronized Optional<Produced> entryAt(int index) {
            if (index < 0) return Optional.empty();
            while (established.size() <= index && !exhausted) establishNext();
            if (failure != null) throw failure;
            return index < established.size() ? Optional.of(established.get(index)) : Optional.empty();
        }

        synchronized List<Produced> materializeEntries() {
            while (!exhausted) establishNext();
            if (failure != null) throw failure;
            return List.copyOf(established);
        }

        Value materializedValue() {
            List<Produced> entries = materializeEntries();
            Shape current = resolvedShape();
            if (current == Shape.KEYLESS || current == Shape.INFER) {
                return new Seq(entries.stream().map(Produced::value).toList());
            }
            ArrayList<KeyedCollection.Entry> keyed = new ArrayList<>(entries.stream()
                    .map(entry -> new KeyedCollection.Entry(entry.key(), entry.value())).toList());
            Value.KeyedCollection.Shape settled;
            if (current == Shape.SET) settled = Value.KeyedCollection.Shape.SET;
            else if (dictionarySelected && homogeneousSortable(keyed)) {
                settled = Value.KeyedCollection.Shape.DICTIONARY;
                keyed.sort((left, right) -> compareKeys(left.key(), right.key()));
            } else settled = Value.KeyedCollection.Shape.GENERAL;
            return new KeyedCollection(settled, keyed);
        }

        private static boolean homogeneousSortable(List<KeyedCollection.Entry> entries) {
            if (entries.isEmpty()) return false;
            Value first = ValueSemantics.underlying(entries.getFirst().key());
            Class<?> kind = first.getClass();
            if (!(first instanceof Num || first instanceof Str || first instanceof Bool || first instanceof Null)) {
                return false;
            }
            return entries.stream().allMatch(entry -> ValueSemantics.underlying(entry.key()).getClass() == kind);
        }

        private static int compareKeys(Value left, Value right) {
            left = ValueSemantics.underlying(left);
            right = ValueSemantics.underlying(right);
            if (left instanceof Num(double a) && right instanceof Num(double b)) return Double.compare(a, b);
            if (left instanceof Str(String a) && right instanceof Str(String b)) {
                return CollectionRuntime.FIELD_ORDER.compare(a, b);
            }
            if (left instanceof Bool(boolean a) && right instanceof Bool(boolean b)) return Boolean.compare(a, b);
            if (left instanceof Null && right instanceof Null) return 0;
            throw new IllegalArgumentException("Non-sortable transformed Dictionary key");
        }

        Shape resolvedShape() {
            synchronized (this) { return shape; }
        }

        synchronized boolean selectDictionary() {
            if (!dictionarySelectable || shapeLocked) return false;
            dictionarySelected = true;
            return true;
        }

        synchronized boolean dictionarySelected() { return dictionarySelected; }
        synchronized void lockShape() { shapeLocked = true; }

        private void establishNext() {
            try {
                Produced produced = producer.next();
                if (produced == null) {
                    exhausted = true;
                    return;
                }
                if (shape == Shape.INFER) shape = produced.shape();
                if (produced.shape() != shape) {
                    throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.MIXED_COLLECTION_SHAPE,
                            "A transformed Collection cannot mix keyed and keyless elements", sourceSpan);
                }
                if (shape != Shape.KEYLESS) {
                    for (Produced existing : established) {
                        if (ValueSemantics.equal(existing.key(), produced.key())) return;
                    }
                }
                established.add(produced);
            } catch (RuntimeException problem) {
                failure = problem;
                exhausted = true;
            }
        }

        @Override public Value getElement(Value key) {
            Shape current = resolvedShape();
            if (current == Shape.INFER) {
                if (entryAt(0).isEmpty()) return Missing.INSTANCE;
                current = resolvedShape();
            }
            if (current == Shape.KEYLESS) {
                if (!(ValueSemantics.underlying(key) instanceof Num(double number))
                        || number < 0 || number != Math.rint(number) || number > Integer.MAX_VALUE) {
                    return Missing.INSTANCE;
                }
                return entryAt((int) number).map(Produced::value).orElse(Missing.INSTANCE);
            }
            int index = 0;
            Optional<Produced> entry;
            while ((entry = entryAt(index++)).isPresent()) {
                if (ValueSemantics.equal(entry.get().key(), key)) {
                    return current == Shape.SET ? entry.get().key() : entry.get().value();
                }
            }
            return Missing.INSTANCE;
        }

        @Override public Value keys() {
            if (resolvedShape() == Shape.KEYLESS && knownSize != null) {
                ArrayList<Value> keys = new ArrayList<>(knownSize);
                for (int index = 0; index < knownSize; index++) keys.add(new Num(index));
                return new Seq(keys);
            }
            List<Produced> entries = materializeEntries();
            if (resolvedShape() == Shape.KEYLESS || resolvedShape() == Shape.INFER) {
                ArrayList<Value> keys = new ArrayList<>(entries.size());
                for (int index = 0; index < entries.size(); index++) keys.add(new Num(index));
                return new Seq(keys);
            }
            return ((CollectionRuntime.Provider) materializedValue()).keys();
        }

        @Override public Value valueEntries() {
            List<Produced> entries = materializeEntries();
            if (resolvedShape() == Shape.SET) return Missing.INSTANCE;
            if (resolvedShape() == Shape.KEYLESS || resolvedShape() == Shape.INFER) {
                return new Seq(entries.stream().map(Produced::value).toList());
            }
            return ((CollectionRuntime.Provider) materializedValue()).valueEntries();
        }

        @Override public Value fieldEntries() {
            List<Produced> entries = materializeEntries();
            if (resolvedShape() == Shape.KEYLESS || resolvedShape() == Shape.INFER) {
                return new Seq(entries.stream().map(Produced::value).toList());
            }
            return ((CollectionRuntime.Provider) materializedValue()).fieldEntries();
        }

        @Override public Value size() {
            return new Num(knownSize != null ? knownSize : materializeEntries().size());
        }

        @Override public CollectionRuntime.Facts facts() {
            Shape current = resolvedShape();
            CollectionRuntime.Guarantee keyed = switch (current) {
                case KEYLESS -> CollectionRuntime.Guarantee.FALSE;
                case KEYED, SET -> CollectionRuntime.Guarantee.TRUE;
                case INFER -> initialFacts.keyed();
            };
            CollectionRuntime.Guarantee hasValues = switch (current) {
                case KEYLESS, KEYED -> CollectionRuntime.Guarantee.TRUE;
                case SET -> CollectionRuntime.Guarantee.FALSE;
                case INFER -> initialFacts.hasValues();
            };
            return new CollectionRuntime.Facts(initialFacts.sequential(), initialFacts.ordered(),
                    initialFacts.unique(), initialFacts.finite(), keyed, hasValues);
        }

        @Override public String toString() { return ValueSemantics.render(this); }
    }

    final class Dictionary implements Reflective {
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

        private Tree root;
        private int size;
        private final Value reflectedTarget;
        private final ReflectionContext reflectionContext;
        private volatile Map<String, Value> materialized;

        public Dictionary(Map<String, Value> entries) {
            LinkedHashMap<String, Value> checked = checkedMap(entries);
            Tree builtRoot = EmptyTree.INSTANCE;
            for (Map.Entry<String, Value> entry : checked.entrySet()) {
                builtRoot = putNode(builtRoot, entry.getKey(), entry.getValue());
            }
            this.root = builtRoot;
            this.size = checked.size();
            this.reflectedTarget = null;
            this.reflectionContext = null;
        }

        private Dictionary(Tree root, int size) {
            this(root, size, null, null);
        }

        private Dictionary(Tree root, int size, Value reflectedTarget, ReflectionContext reflectionContext) {
            this.root = Objects.requireNonNull(root);
            this.size = size;
            this.reflectedTarget = reflectedTarget;
            this.reflectionContext = reflectionContext;
        }

        static Dictionary reflection(Map<String, Value> entries, Value target, ReflectionContext context) {
            Dictionary dictionary = new Dictionary(entries);
            return new Dictionary(dictionary.root, dictionary.size, Objects.requireNonNull(target),
                    Objects.requireNonNull(context));
        }

        Optional<Value> reflectedTarget(ReflectionContext observer) {
            return reflectedTarget != null && reflectionContext.intersect(Objects.requireNonNull(observer)).dereference()
                    ? Optional.of(reflectedTarget) : Optional.empty();
        }

        public Map<String, Value> entries() {
            Map<String, Value> result = materialized;
            if (result != null) return result;

            LinkedHashMap<String, Value> combined = new LinkedHashMap<>();
            appendEntries(root, combined);
            result = Collections.unmodifiableMap(combined);
            materialized = result;
            return result;
        }

        @Override public Map<String, Value> fields() { return entries(); }

        public Optional<Value> find(String key) {
            Objects.requireNonNull(key);
            for (Tree tree = root; tree instanceof Node node; ) {
                int comparison = CollectionRuntime.FIELD_ORDER.compare(key, node.key());
                if (comparison == 0) return Optional.of(node.value());
                tree = comparison < 0 ? node.left() : node.right();
            }
            return Optional.empty();
        }

        public Dictionary put(String key, Value value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            boolean present = containsKey(key);
            return new Dictionary(putNode(root, key, value), present ? size : size + 1);
        }

        void putOwned(String key, Value value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            boolean present = containsKey(key);
            root = putNode(root, key, value);
            if (!present) size++;
            materialized = null;
        }

        public boolean containsKey(String key) { return find(key).isPresent(); }
        public int size() { return size; }

        private static void appendEntries(Tree tree, LinkedHashMap<String, Value> output) {
            if (!(tree instanceof Node node)) return;
            appendEntries(node.left(), output);
            output.put(node.key(), node.value());
            appendEntries(node.right(), output);
        }

        private static Tree putNode(Tree tree, String key, Value value) {
            if (tree == EmptyTree.INSTANCE) {
                return new Node(key, value, EmptyTree.INSTANCE, EmptyTree.INSTANCE, 1);
            }
            Node node = (Node) tree;
            int comparison = CollectionRuntime.FIELD_ORDER.compare(key, node.key());
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
            return other instanceof Dictionary dictionary && entries().equals(dictionary.entries());
        }

        @Override public int hashCode() { return entries().hashCode(); }

        @Override public String toString() {
            return ValueSemantics.render(this);
        }
    }

    non-sealed interface Callable extends Value {
        Value apply(Argument argument, SourceSpan callSpan);
        int remainingArity();
        default CallableSignature signature() {
            return CallableSignature.unknown(java.util.Collections.nCopies(remainingArity(), null));
        }
        default List<CallableSignature> variantSignatures() { return List.of(); }
        default List<Value> retainedValues() { return List.of(); }
        default boolean refinementEligible() { return false; }
        default String publicName() { return "<anonymous>"; }

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
            Value raw = ValueSemantics.underlying(argument.value());
            if (contract.parameterArity() > 0 && raw instanceof ContractValue parameter) {
                return new ContractValue(contract.parameterize(List.of(parameter.descriptor())));
            }
            return new Bool(contract.test(argument.value(), callSpan));
        }

        @Override public int remainingArity() { return 1; }
        @Override public CallableSignature signature() {
            return CallableSignature.builtin(List.of("value"), List.of());
        }
        @Override public Optional<Value> find(String name) { return Optional.ofNullable(fields().get(name)); }
        @Override public Map<String, Value> fields() {
            LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
            fields.put("kind", new Str("Contract"));
            fields.put("id", new Str(contract.publicName()));
            fields.put("bases", new Seq(contract.bases().stream()
                    .map(base -> (Value) new Str(base.publicName())).toList()));
            fields.put("requirements", new Seq(contract.requirements().stream()
                    .map(requirement -> (Value) new Str(requirement)).toList()));
            if (contract instanceof TemplateContract template) fields.putAll(template.reflectionFields());
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
        @Override public CallableSignature signature() { return target.signature(); }
        @Override public List<CallableSignature> variantSignatures() { return target.variantSignatures(); }
        @Override public List<Value> retainedValues() { return target.retainedValues(); }
        @Override public String publicName() { return target.publicName(); }
        @Override public boolean refinementEligible() { return target.refinementEligible(); }
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
        private final CallableSignature signature;

        ComposedFunction(Callable left, Callable right, CallInvoker invoker) {
            this.left = Objects.requireNonNull(left);
            this.right = Objects.requireNonNull(right);
            this.invoker = Objects.requireNonNull(invoker);
            this.signature = CallableSignature.compose(left.signature(), right.signature()).signature();
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

        @Override public CallableSignature signature() {
            return signature;
        }

        @Override public List<Value> retainedValues() { return List.of(left, right); }

        @Override public String toString() {
            return "<composition/" + remainingArity() + ">";
        }
    }

    final class FunctionValue implements Callable {
        private final String name;
        private final List<String> params;
        private final BoundArguments bound;
        private final BiFunction<List<Argument>, SourceSpan, Value> implementation;
        private final boolean refinementEligible;
        private final CallableSignature signature;

        public FunctionValue(String name, List<String> params, Function<List<Value>, Value> implementation) {
            this(name, params, BoundArguments.empty(), valueImplementation(implementation), false,
                    CallableSignature.builtin(params, List.of()));
        }

        FunctionValue(String name, List<String> params,
                      BiFunction<List<Argument>, SourceSpan, Value> implementation) {
            this(name, params, BoundArguments.empty(), implementation, false,
                    CallableSignature.builtin(params, List.of()));
        }

        FunctionValue(String name, List<String> params,
                      BiFunction<List<Argument>, SourceSpan, Value> implementation,
                      boolean refinementEligible, CallableSignature signature) {
            this(name, params, BoundArguments.empty(), implementation, refinementEligible, signature);
        }

        private FunctionValue(String name, List<String> params, BoundArguments bound,
                              BiFunction<List<Argument>, SourceSpan, Value> implementation,
                              boolean refinementEligible, CallableSignature signature) {
            this.name = Objects.requireNonNull(name, "function name");
            this.params = List.copyOf(params);
            this.bound = Objects.requireNonNull(bound);
            this.implementation = Objects.requireNonNull(implementation, "function implementation");
            this.refinementEligible = refinementEligible;
            this.signature = Objects.requireNonNull(signature);
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
            CallableSignature specialized = signature.specializeFirst(argument.value());
            if (next.size() == params.size()) {
                return implementation.apply(next.values(), callSpan);
            }
            if (next.size() > params.size()) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.TOO_MANY_ARGUMENTS,
                        "Too many arguments for " + name, callSpan);
            }
            return new FunctionValue(name, params, next, implementation, refinementEligible, specialized);
        }

        @Override public int remainingArity() {
            return params.size() - bound.size();
        }

        @Override public boolean refinementEligible() {
            return refinementEligible && bound.size() == 0;
        }

        @Override public CallableSignature signature() { return signature; }
        @Override public List<Value> retainedValues() {
            return bound.values().stream().map(Argument::value).toList();
        }

        @Override public String publicName() { return name; }

        @Override public String toString() {
            return "<fn " + name + "/" + remainingArity() + ">";
        }
    }

    final class CallableMetadata {
        private CallableMetadata() {}

        static Value reflection(Callable target, ReflectionContext captured) {
            return projected(captured, context -> fields(target, context), target);
        }

        private static Map<String, Value> fields(Callable target, ReflectionContext context) {
            LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
            fields.put("kind", new Str("Function"));
            fields.put("id", target.publicName().equals("<anonymous>") || !context.callableNames()
                    ? Missing.INSTANCE : new Str(target.publicName()));
            fields.put("remaining", new Num(target.remainingArity()));
            fields.put("signature", signatureValue(target.signature(), context));
            fields.put("variants", new Seq(target.variantSignatures().stream()
                    .map(signature -> signatureValue(signature, context)).toList()));
            return fields;
        }

        private static Value signatureValue(CallableSignature signature, ReflectionContext captured) {
            return projected(captured, context -> Map.of(
                    "parameters", new Seq(java.util.stream.IntStream.range(0, signature.parameters().size())
                            .mapToObj(index -> parameterValue(signature.parameters().get(index), index, context)).toList()),
                    "result", resultValue(signature.result(), context),
                    "effects", effectsValue(signature.effects(), context),
                    "variables", new Seq(signature.variables().stream()
                            .map(variable -> variableValue(variable, context)).toList())), null, "Signature");
        }

        private static Value parameterValue(CallableSignature.Parameter parameter, int position,
                                            ReflectionContext captured) {
            return projected(captured, context -> Map.of(
                    "position", new Num(position),
                    "id", parameter.name() == null ? Missing.INSTANCE : new Str(parameter.name()),
                    "requirements", refs(effective(parameter.requirements(), parameter.declared(), context), context),
                    "declared", nullableRefs(parameter.declared(), context),
                    "inferred", inferredRefs(parameter.inferred(), parameter.declared(), context)), null, "Parameter");
        }

        private static Value resultValue(CallableSignature.Result result, ReflectionContext captured) {
            return projected(captured, context -> Map.of(
                    "guarantees", refs(effective(result.guarantees(), result.declared(), context), context),
                    "declared", nullableRefs(result.declared(), context),
                    "inferred", inferredRefs(result.inferred(), result.declared(), context)), null, "FunctionResult");
        }

        private static Value effectsValue(CallableSignature.Effects effects, ReflectionContext captured) {
            return projected(captured, context -> Map.of(
                    "upperBound", nullableEffects(effective(effects.upperBound(), effects.declared(), context), context),
                    "declared", nullableEffects(effects.declared(), context),
                    "inferred", inferredEffects(effects.inferred(), effects.declared(), context)), null, "FunctionEffects");
        }

        private static Value variableValue(CallableSignature.Variable variable, ReflectionContext captured) {
            return projected(captured, context -> Map.of("index", new Num(variable.index()),
                    "requirements", refs(variable.requirements(), context)), null, "SignatureVariable");
        }

        private static Value refs(List<CallableSignature.ContractTerm> terms, ReflectionContext context) {
            return new Seq(terms.stream().map(term -> termValue(term, context)).toList());
        }
        private static Value nullableRefs(List<CallableSignature.ContractTerm> names, ReflectionContext context) {
            return names == null ? Missing.INSTANCE : refs(names, context);
        }
        private static Value inferredRefs(List<CallableSignature.ContractTerm> inferred,
                                          List<CallableSignature.ContractTerm> declared,
                                          ReflectionContext context) {
            return !context.inferredFacts() && declared != null ? Missing.INSTANCE : nullableRefs(inferred, context);
        }
        private static Value termValue(CallableSignature.ContractTerm term, ReflectionContext captured) {
            return switch (term) {
                case CallableSignature.VariableRef variable -> metadata("VariableRef", captured,
                        Map.of("index", new Num(variable.index())));
                case CallableSignature.NamedRef named -> projected(captured, context -> Map.of(
                        "id", context.names(named.identity()) ? new Str(named.name()) : Missing.INSTANCE),
                        null, named.identity(), "ContractRef");
                case CallableSignature.AppliedRef applied -> metadata("ContractApplication", captured, Map.of(
                        "constructor", termValue(applied.constructor(), captured),
                        "arguments", new Seq(applied.arguments().stream()
                                .map(argument -> termValue(argument, captured)).toList())));
                case CallableSignature.ModifiedRef modified -> metadata("ModifiedContractRef", captured, Map.of(
                        "base", termValue(modified.base(), captured), "nullable", new Bool(modified.nullable()),
                        "optional", new Bool(modified.optional())));
                case CallableSignature.ArrowRef arrow -> metadata("ArrowContractRef", captured, Map.of(
                        "parameters", new Seq(arrow.parameters().stream().map(requirements ->
                                (Value) new Seq(requirements.stream()
                                        .map(requirement -> termValue(requirement, captured)).toList())).toList()),
                        "result", termValue(arrow.result(), captured)));
            };
        }
        private static Value nullableEffects(List<CallableSignature.EffectRef> effects, ReflectionContext context) {
            return effects == null ? Missing.INSTANCE : new Seq(effects.stream().map(effect ->
                    projected(context, observer -> Map.of("id", observer.names(effect.identity())
                            ? new Str(effect.name()) : Missing.INSTANCE), null, effect.identity(), "Effect")).toList());
        }
        private static Value inferredEffects(List<CallableSignature.EffectRef> inferred,
                                             List<CallableSignature.EffectRef> declared,
                                             ReflectionContext context) {
            return !context.inferredFacts() && declared != null ? Missing.INSTANCE : nullableEffects(inferred, context);
        }
        private static <T> List<T> effective(List<T> complete, List<T> declared, ReflectionContext context) {
            return !context.inferredFacts() && declared != null ? declared : complete;
        }
        private static Value metadata(String kind, ReflectionContext captured, Map<String, Value> values) {
            return projected(captured, ignored -> values, null, kind);
        }
        private static Value projected(ReflectionContext captured, ProjectionBody body,
                                       Value target, String kind) {
            return projected(captured, body, target, null, kind);
        }
        private static Value projected(ReflectionContext captured, ProjectionBody body,
                                       Value target, Object semanticIdentity, String kind) {
            return new ProjectedDictionary(context -> {
                LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
                fields.put("kind", new Str(kind));
                fields.putAll(body.fields(context));
                return fields;
            }, captured, target, semanticIdentity);
        }
        private static Value projected(ReflectionContext captured, ProjectionBody body, Value target) {
            return new ProjectedDictionary(body::fields, captured, target, target);
        }
        @FunctionalInterface private interface ProjectionBody {
            Map<String, Value> fields(ReflectionContext context);
        }
    }

    final class HoleFunction implements Callable {
        private final String display;
        private final int arity;
        private final BoundArguments bound;
        private final Function<List<Argument>, Value> implementation;
        private final CallableSignature signature;

        public HoleFunction(String display, int arity, Function<List<Argument>, Value> implementation) {
            this(display, arity, implementation,
                    CallableSignature.unknown(java.util.Collections.nCopies(arity, null)));
        }

        HoleFunction(String display, int arity, Function<List<Argument>, Value> implementation,
                     CallableSignature signature) {
            this(display, arity, BoundArguments.empty(), implementation, signature);
        }

        private HoleFunction(String display, int arity, BoundArguments bound,
                             Function<List<Argument>, Value> implementation, CallableSignature signature) {
            this.display = Objects.requireNonNull(display, "partial display");
            if (arity < 1) throw new IllegalArgumentException("Partial arity must be positive");
            this.arity = arity;
            this.bound = Objects.requireNonNull(bound);
            this.implementation = Objects.requireNonNull(implementation, "partial implementation");
            this.signature = Objects.requireNonNull(signature, "partial signature");
        }

        @Override public Value apply(Argument argument, SourceSpan callSpan) {
            BoundArguments next = bound.appended(argument);
            if (next.size() == arity) return implementation.apply(next.values());
            if (next.size() > arity) {
                throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.TOO_MANY_ARGUMENTS,
                        "Too many arguments for partial expression", callSpan);
            }
            return new HoleFunction(display, arity, next, implementation,
                    signature.specializeFirst(argument.value()));
        }

        @Override public int remainingArity() {
            return arity - bound.size();
        }

        @Override public CallableSignature signature() { return signature; }

        @Override public List<Value> retainedValues() {
            return bound.values().stream().map(Argument::value).toList();
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
