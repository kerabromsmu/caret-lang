package caretlang;

import java.util.List;
import java.util.Objects;

final class ParameterizedContract implements ContractDescriptor {
    private final ContractDescriptor base;
    private final List<ContractDescriptor> arguments;

    ParameterizedContract(ContractDescriptor base, List<ContractDescriptor> arguments) {
        this.base = Objects.requireNonNull(base);
        this.arguments = List.copyOf(arguments);
        if (this.arguments.size() != base.parameterArity()) {
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
        if (!base.accepts(value)) return false;
        if (base == BuiltinContract.SEQUENCE && value instanceof Value.Seq sequence) {
            ContractDescriptor element = arguments.getFirst();
            return sequence.values().stream().allMatch(element::accepts);
        }
        return false;
    }

    @Override public List<ContractDescriptor> bases() { return List.of(base); }
    @Override public List<String> requirements() {
        return arguments.stream().map(ContractDescriptor::publicName).toList();
    }
}
