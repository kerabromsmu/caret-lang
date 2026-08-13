package caretlang;

import java.util.List;
import java.util.Objects;

final class UserContract implements ContractDescriptor {
    private String name;
    private final List<ContractDescriptor> bases;

    UserContract(List<ContractDescriptor> bases) {
        this.bases = List.copyOf(bases);
    }

    void nameIfAnonymous(String candidate) {
        if (name == null) name = Objects.requireNonNull(candidate);
    }

    @Override public String publicName() { return name == null ? "<anonymous>" : name; }
    @Override public List<ContractDescriptor> bases() { return bases; }

    @Override public boolean accepts(Value value) {
        if (value instanceof Value.Attributed attributed) {
            return attributed.contracts().stream().anyMatch(this::includes);
        }
        return false;
    }

    private boolean includes(ContractDescriptor candidate) {
        return candidate == this || candidate.bases().stream().anyMatch(this::includes);
    }

    boolean acceptsBuiltinBases(Value value) {
        return bases.stream().allMatch(base -> base instanceof BuiltinContract builtin
                ? builtin.accepts(value)
                : ((UserContract) base).acceptsBuiltinBases(value));
    }
}
