package caretlang;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public sealed interface Value permits Value.Num, Value.Str, Value.Bool, Value.Null, Value.Missing,
        Value.Field, Value.Reflective, Value.Seq, Value.Callable, Value.Attributed {

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

    record Field(String key, Value value) implements Value {
        public Field {
            Objects.requireNonNull(key, "field key");
            Objects.requireNonNull(value, "field value");
        }
        @Override public @NotNull String toString() { return ValueSemantics.render(this); }
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

        private final Tree root;
        private final int size;
        private final Value reflectedTarget;
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
        }

        private Dictionary(Tree root, int size) {
            this(root, size, null);
        }

        private Dictionary(Tree root, int size, Value reflectedTarget) {
            this.root = Objects.requireNonNull(root);
            this.size = size;
            this.reflectedTarget = reflectedTarget;
        }

        static Dictionary reflection(Map<String, Value> entries, Value target) {
            Dictionary dictionary = new Dictionary(entries);
            return new Dictionary(dictionary.root, dictionary.size, Objects.requireNonNull(target));
        }

        Optional<Value> reflectedTarget() { return Optional.ofNullable(reflectedTarget); }

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
            fields.put("requirements", new Seq(contract.requirements().stream()
                    .map(requirement -> (Value) new Str(requirement)).toList()));
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

        @Override public String publicName() { return name; }

        @Override public String toString() {
            return "<fn " + name + "/" + remainingArity() + ">";
        }
    }

    final class CallableMetadata {
        private CallableMetadata() {}

        static Map<String, Value> fields(Callable target) {
            LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
            fields.put("kind", new Str("Function"));
            fields.put("name", target.publicName().equals("<anonymous>")
                    ? Missing.INSTANCE : new Str(target.publicName()));
            fields.put("remaining", new Num(target.remainingArity()));
            fields.put("signature", signatureValue(target.signature()));
            fields.put("variants", new Seq(target.variantSignatures().stream()
                    .map(CallableMetadata::signatureValue).toList()));
            return Collections.unmodifiableMap(fields);
        }

        private static Value signatureValue(CallableSignature signature) {
            return metadata("Signature", Map.of(
                    "parameters", new Seq(java.util.stream.IntStream.range(0, signature.parameters().size())
                            .mapToObj(index -> parameterValue(signature.parameters().get(index), index)).toList()),
                    "result", resultValue(signature.result()),
                    "effects", effectsValue(signature.effects()),
                    "variables", new Seq(signature.variables().stream().map(CallableMetadata::variableValue).toList())));
        }

        private static Value parameterValue(CallableSignature.Parameter parameter, int position) {
            return metadata("Parameter", Map.of(
                    "position", new Num(position),
                    "name", parameter.name() == null ? Missing.INSTANCE : new Str(parameter.name()),
                    "requirements", refs(parameter.requirements()),
                    "declared", nullableRefs(parameter.declared()),
                    "inferred", nullableRefs(parameter.inferred())));
        }

        private static Value resultValue(CallableSignature.Result result) {
            return metadata("FunctionResult", Map.of("guarantees", refs(result.guarantees()),
                    "declared", nullableRefs(result.declared()), "inferred", nullableRefs(result.inferred())));
        }

        private static Value effectsValue(CallableSignature.Effects effects) {
            return metadata("FunctionEffects", Map.of("upperBound", nullableNames(effects.upperBound(), "Effect"),
                    "declared", nullableNames(effects.declared(), "Effect"),
                    "inferred", nullableNames(effects.inferred(), "Effect")));
        }

        private static Value variableValue(CallableSignature.Variable variable) {
            return metadata("SignatureVariable", Map.of("index", new Num(variable.index()),
                    "requirements", refs(variable.requirements())));
        }

        private static Value refs(List<CallableSignature.ContractTerm> terms) {
            return new Seq(terms.stream().map(CallableMetadata::termValue).toList());
        }
        private static Value nullableRefs(List<CallableSignature.ContractTerm> names) {
            return names == null ? Missing.INSTANCE : refs(names);
        }
        private static Value termValue(CallableSignature.ContractTerm term) {
            return switch (term) {
                case CallableSignature.VariableRef variable -> metadata("VariableRef",
                        Map.of("index", new Num(variable.index())));
                case CallableSignature.NamedRef named -> metadata("ContractRef",
                        Map.of("name", new Str(named.name())));
                case CallableSignature.AppliedRef applied -> metadata("ContractApplication", Map.of(
                        "constructor", termValue(applied.constructor()),
                        "arguments", new Seq(applied.arguments().stream()
                                .map(CallableMetadata::termValue).toList())));
                case CallableSignature.ModifiedRef modified -> metadata("ModifiedContractRef", Map.of(
                        "base", termValue(modified.base()), "nullable", new Bool(modified.nullable()),
                        "optional", new Bool(modified.optional())));
                case CallableSignature.ArrowRef arrow -> metadata("ArrowContractRef", Map.of(
                        "parameters", new Seq(arrow.parameters().stream().map(requirements ->
                                (Value) new Seq(requirements.stream().map(CallableMetadata::termValue).toList())).toList()),
                        "result", termValue(arrow.result())));
            };
        }
        private static Value nullableNames(List<String> names, String kind) {
            return names == null ? Missing.INSTANCE : new Seq(names.stream()
                    .map(name -> metadata(kind, Map.of("name", new Str(name)))).toList());
        }
        private static Value metadata(String kind, Map<String, Value> values) {
            LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
            fields.put("kind", new Str(kind));
            fields.putAll(values);
            return new Dictionary(fields);
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
