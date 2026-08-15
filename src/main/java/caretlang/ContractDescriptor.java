package caretlang;

import java.util.List;

interface ContractDescriptor {
    String publicName();
    boolean accepts(Value value);
    default List<ContractDescriptor> bases() { return List.of(); }
    default List<String> requirements() { return List.of(); }
    default int parameterArity() { return 0; }
    default ContractDescriptor parameterize(List<ContractDescriptor> arguments) {
        throw new IllegalStateException("Contract is not parameterizable: " + publicName());
    }
}
