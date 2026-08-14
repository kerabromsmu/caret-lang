package caretlang;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

final class UserContract implements ContractDescriptor {
    private String name;
    private final List<ContractDescriptor> bases;
    private final List<Value.Callable> refinements;
    private final BiFunction<Value.Callable, Value.Argument, Value> refinementInvoker;

    UserContract(List<ContractDescriptor> bases) {
        this(bases, List.of(), (callable, argument) -> callable.apply(argument, argument.span()));
    }

    UserContract(List<ContractDescriptor> bases, List<Value.Callable> refinements,
                 BiFunction<Value.Callable, Value.Argument, Value> refinementInvoker) {
        this.bases = List.copyOf(bases);
        this.refinements = List.copyOf(refinements);
        this.refinementInvoker = Objects.requireNonNull(refinementInvoker);
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

    private boolean acceptsRequirements(Value value, SourceSpan span) {
        return bases.stream().allMatch(base -> base instanceof BuiltinContract builtin
                ? builtin.accepts(value)
                : ((UserContract) base).canAcquire(value, span));
    }

    boolean canAcquire(Value value, SourceSpan span) {
        if (!acceptsRequirements(value, span)) return false;
        for (Value.Callable refinement : refinements) {
            Value result = refinementInvoker.apply(refinement, new Value.Argument(value, span));
            while (result instanceof Value.Attributed attributed) result = attributed.value();
            if (!(result instanceof Value.Bool(boolean accepted)) || !accepted) return false;
        }
        return true;
    }
}
