package caretlang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/** Exact structural collection contract derived from a language-owned constructor descriptor. */
final class TemplateContract implements ContractDescriptor {
    private String name;
    private final CollectionConstructorDescriptor descriptor;
    private final BiFunction<Value.Callable, Value.Argument, Value> refinementInvoker;

    TemplateContract(CollectionConstructorDescriptor descriptor,
                     BiFunction<Value.Callable, Value.Argument, Value> refinementInvoker) {
        this.descriptor = descriptor;
        this.refinementInvoker = refinementInvoker;
    }

    void nameIfAnonymous(String candidate) { if (name == null) name = candidate; }
    CollectionConstructorDescriptor descriptor() { return descriptor; }
    @Override public String publicName() { return name == null ? "<template>" : name; }
    @Override public List<ContractDescriptor> bases() { return List.of(BuiltinContract.COLLECTION); }
    @Override public List<String> requirements() { return List.of("exact collection shape"); }

    @Override public boolean accepts(Value value) {
        return matches(descriptor.root(), ValueSemantics.underlying(value), new HashMap<>());
    }

    private boolean matches(CollectionConstructorDescriptor.Node node, Value value,
                            Map<Integer, Value> repeated) {
        value = ValueSemantics.underlying(value);
        if (node instanceof CollectionConstructorDescriptor.FixedNode fixed) {
            return ValueSemantics.equal(fixed.value(), value);
        }
        if (node instanceof CollectionConstructorDescriptor.HoleNode hole) {
            Value prior = repeated.putIfAbsent(hole.parameter(), value);
            if (prior != null && !ValueSemantics.equal(prior, value)) return false;
            for (Object requirement : hole.requirements()) {
                if (requirement instanceof ContractDescriptor contract) {
                    if (!contract.accepts(value)) return false;
                } else {
                    Value result = ValueSemantics.underlying(refinementInvoker.apply(
                            (Value.Callable) requirement, new Value.Argument(value, hole.span())));
                    if (!(result instanceof Value.Bool(boolean accepted)) || !accepted) return false;
                }
            }
            return true;
        }
        CollectionConstructorDescriptor.CollectionNode collection =
                (CollectionConstructorDescriptor.CollectionNode) node;
        if (collection.named()) {
            if (!(value instanceof Value.Dictionary dictionary)
                    && !(value instanceof Value.ProjectedDictionary)) return false;
            Map<String, Value> fields = value instanceof Value.Dictionary dictionary
                    ? dictionary.entries() : ((Value.ProjectedDictionary) value).fields(ReflectionContext.defining());
            if (fields.size() != collection.elements().size()) return false;
            for (CollectionConstructorDescriptor.Element element : collection.elements()) {
                Value member = fields.get(element.name());
                if (member == null || !matches(element.value(), member, repeated)) return false;
            }
            return true;
        }
        List<Value> values;
        if (value instanceof Value.EmptyCollection) values = List.of();
        else if (value instanceof Value.Seq sequence) values = sequence.values();
        else return false;
        if (values.size() != collection.elements().size()) return false;
        for (int index = 0; index < values.size(); index++) {
            if (!matches(collection.elements().get(index).value(), values.get(index), repeated)) return false;
        }
        return true;
    }

    boolean implies(TemplateContract required) {
        return nodeImplies(descriptor.root(), required.descriptor.root(),
                java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private boolean nodeImplies(CollectionConstructorDescriptor.Node left,
                                CollectionConstructorDescriptor.Node right, Set<Object> visiting) {
        if (left == right) return true;
        if (right instanceof CollectionConstructorDescriptor.HoleNode target) {
            if (left instanceof CollectionConstructorDescriptor.HoleNode source) {
                return target.requirements().stream().allMatch(required -> source.requirements().stream()
                        .anyMatch(candidate -> requirementImplies(candidate, required)));
            }
            if (left instanceof CollectionConstructorDescriptor.FixedNode fixed) {
                return target.requirements().stream().allMatch(requirement ->
                        requirement instanceof ContractDescriptor contract && contract.accepts(fixed.value()));
            }
            return target.requirements().isEmpty();
        }
        if (right instanceof CollectionConstructorDescriptor.FixedNode target) {
            return left instanceof CollectionConstructorDescriptor.FixedNode source
                    && ValueSemantics.equal(source.value(), target.value());
        }
        if (!(left instanceof CollectionConstructorDescriptor.CollectionNode source)
                || !(right instanceof CollectionConstructorDescriptor.CollectionNode target)
                || source.named() != target.named() || source.elements().size() != target.elements().size()) return false;
        for (int index = 0; index < source.elements().size(); index++) {
            CollectionConstructorDescriptor.Element a = source.elements().get(index);
            CollectionConstructorDescriptor.Element b = target.elements().get(index);
            if (!java.util.Objects.equals(a.name(), b.name()) || !nodeImplies(a.value(), b.value(), visiting)) {
                return false;
            }
        }
        return true;
    }

    private boolean requirementImplies(Object left, Object right) {
        if (left == right) return true;
        return left instanceof ContractDescriptor a && right instanceof ContractDescriptor b
                && ContractRelations.implies(a, b);
    }

    Map<String, Value> reflectionFields() {
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        fields.put("shape", new Value.Str(descriptor.root().named() ? "named" : "positional"));
        fields.put("size", new Value.Num(descriptor.root().elements().size()));
        fields.put("elements", new Value.Seq(descriptor.root().elements().stream()
                .map(this::elementMetadata).toList()));
        return fields;
    }

    private Value elementMetadata(CollectionConstructorDescriptor.Element element) {
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        fields.put("name", element.name() == null ? Value.Missing.INSTANCE : new Value.Str(element.name()));
        fields.put("constraint", new Value.Str(switch (element.value()) {
            case CollectionConstructorDescriptor.CollectionNode ignored -> "collection";
            case CollectionConstructorDescriptor.FixedNode ignored -> "fixed";
            case CollectionConstructorDescriptor.HoleNode ignored -> "hole";
        }));
        if (element.value() instanceof CollectionConstructorDescriptor.HoleNode hole) {
            fields.put("parameter", new Value.Num(hole.parameter()));
            fields.put("requirements", new Value.Seq(hole.requirements().stream().map(requirement ->
                    (Value) new Value.Str(requirement instanceof ContractDescriptor contract
                            ? contract.publicName() : ((Value.Callable) requirement).publicName())).toList()));
        }
        return new Value.Dictionary(fields);
    }
}
