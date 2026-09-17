package caretlang;

import java.util.List;
import java.util.Objects;

final class ParameterizedContract implements ContractDescriptor {
    private final ContractDescriptor base;
    private final List<ContractDescriptor> arguments;

    ParameterizedContract(ContractDescriptor base, List<ContractDescriptor> arguments) {
        this.base = Objects.requireNonNull(base);
        this.arguments = List.copyOf(arguments);
        if (this.arguments.isEmpty() || this.arguments.size() > base.parameterArity()) {
            throw new IllegalArgumentException("Incorrect contract parameter count for " + base.publicName());
        }
    }

    ContractDescriptor base() { return base; }
    List<ContractDescriptor> arguments() { return arguments; }

    @Override public String publicName() {
        return base.publicName() + " " + arguments.stream()
                .map(argument -> argument instanceof ParameterizedContract
                        ? "(" + argument.publicName() + ")" : argument.publicName())
                .reduce((left, right) -> left + " " + right).orElseThrow();
    }

    @Override public boolean accepts(Value value) {
        value = ValueSemantics.underlying(value);
        if (parameterArity() > 0 || !base.accepts(value)) return false;
        if (value instanceof Value.EmptyCollection) return true;
        if (base == BuiltinContract.SEQUENCE && value instanceof Value.Seq sequence) {
            return sequence.values().stream().allMatch(arguments.getFirst()::accepts);
        }
        if (base == BuiltinContract.FIELD && value instanceof Value.Field(String key, Value fieldValue)) {
            return arguments.get(0).accepts(new Value.Str(key)) && arguments.get(1).accepts(fieldValue);
        }
        if (base == BuiltinContract.DICTIONARY && value instanceof Value.Dictionary dictionary) {
            return dictionary.entries().entrySet().stream().allMatch(entry ->
                    arguments.getFirst().accepts(new Value.Str(entry.getKey()))
                            && arguments.get(1).accepts(entry.getValue()));
        }
        return false;
    }

    @Override public boolean test(Value value, SourceSpan span) {
        value = ValueSemantics.underlying(value);
        if (parameterArity() > 0) return false;
        if (!base.test(value, span)) return false;
        if (value instanceof Value.EmptyCollection) return true;
        if (base == BuiltinContract.SEQUENCE && value instanceof Value.Seq sequence) {
            ContractDescriptor element = arguments.getFirst();
            return sequence.values().stream().allMatch(
                    elementValue -> element.acceptsRequirement(elementValue, span));
        }
        if (base == BuiltinContract.FIELD && value instanceof Value.Field(String key1, Value value1)) {
            return arguments.get(0).acceptsRequirement(new Value.Str(key1), span)
                    && arguments.get(1).acceptsRequirement(value1, span);
        }
        if (base == BuiltinContract.DICTIONARY && value instanceof Value.Dictionary dictionary) {
            ContractDescriptor key = arguments.get(0);
            ContractDescriptor element = arguments.get(1);
            return dictionary.entries().entrySet().stream().allMatch(entry ->
                    key.acceptsRequirement(new Value.Str(entry.getKey()), span)
                            && element.acceptsRequirement(entry.getValue(), span));
        }
        return false;
    }

    @Override public int parameterArity() { return base.parameterArity() - arguments.size(); }

    @Override public ContractDescriptor parameterize(List<ContractDescriptor> more) {
        if (more.isEmpty() || more.size() > parameterArity()) {
            throw new IllegalArgumentException("Incorrect contract parameter count for " + publicName());
        }
        java.util.ArrayList<ContractDescriptor> combined = new java.util.ArrayList<>(arguments);
        combined.addAll(more);
        return new ParameterizedContract(base, combined);
    }

    @Override public List<ContractDescriptor> bases() { return List.of(base); }
    @Override public List<String> requirements() {
        return arguments.stream().map(ContractDescriptor::publicName).toList();
    }
}
