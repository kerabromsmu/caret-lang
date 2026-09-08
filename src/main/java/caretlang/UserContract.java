package caretlang;

import java.util.List;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.Objects;
import java.util.function.BiFunction;

final class UserContract implements ContractDescriptor {
    private String name;
    private List<ContractDescriptor> bases;
    private List<Value.Callable> refinements;
    private BiFunction<Value.Callable, Value.Argument, Value> refinementInvoker;
    private final SourceSpan declarationSpan;
    private boolean configured;

    UserContract(List<ContractDescriptor> bases) {
        this(bases, List.of(), (callable, argument) -> callable.apply(argument, argument.span()));
    }

    UserContract(List<ContractDescriptor> bases, List<Value.Callable> refinements,
                 BiFunction<Value.Callable, Value.Argument, Value> refinementInvoker) {
        this(bases, refinements, refinementInvoker, null, true);
    }

    UserContract(SourceSpan declarationSpan) {
        this(List.of(), List.of(), (callable, argument) -> callable.apply(argument, argument.span()),
                declarationSpan, false);
    }

    private UserContract(List<ContractDescriptor> bases, List<Value.Callable> refinements,
                         BiFunction<Value.Callable, Value.Argument, Value> refinementInvoker,
                         SourceSpan declarationSpan, boolean configured) {
        this.bases = List.copyOf(bases);
        this.refinements = List.copyOf(refinements);
        this.refinementInvoker = Objects.requireNonNull(refinementInvoker);
        this.declarationSpan = declarationSpan;
        this.configured = configured;
    }

    void configureFrom(UserContract source) {
        if (configured) throw new IllegalStateException("Contract descriptor is already configured");
        bases = source.bases;
        refinements = source.refinements;
        refinementInvoker = source.refinementInvoker;
        configured = true;
    }

    SourceSpan declarationSpan() { return declarationSpan; }

    void nameIfAnonymous(String candidate) {
        if (name == null) name = Objects.requireNonNull(candidate);
    }

    @Override public String publicName() { return name == null ? "<anonymous>" : name; }
    @Override public List<ContractDescriptor> bases() { return bases; }
    @Override public List<String> requirements() {
        return refinements.stream().map(Value.Callable::publicName).toList();
    }

    @Override public boolean accepts(Value value) {
        if (value instanceof Value.Attributed attributed) {
            return attributed.contracts().stream().anyMatch(this::includes);
        }
        return false;
    }

    @Override public boolean acceptsRequirement(Value value, SourceSpan span) {
        return accepts(value) || canAcquire(value, span);
    }

    private boolean includes(ContractDescriptor candidate) {
        Set<ContractDescriptor> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<ContractDescriptor> pending = new ArrayDeque<>();
        pending.push(candidate);
        while (!pending.isEmpty()) {
            ContractDescriptor current = pending.pop();
            if (!visited.add(current)) continue;
            if (current == this) return true;
            current.bases().forEach(pending::push);
        }
        return false;
    }

    boolean canAcquire(Value value, SourceSpan span) {
        Set<ContractDescriptor> visitedContracts = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Value.Callable> visitedRefinements = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<ContractDescriptor> pending = new ArrayDeque<>();
        pending.push(this);
        while (!pending.isEmpty()) {
            ContractDescriptor current = pending.pop();
            if (!visitedContracts.add(current)) continue;
            if (current instanceof BuiltinContract builtin) {
                if (!builtin.accepts(value)) return false;
                continue;
            }
            if (current instanceof ModifiedContract modified) {
                Value underlying = ValueSemantics.underlying(value);
                if ((underlying == Value.Null.INSTANCE && modified.nullable())
                        || (underlying == Value.Missing.INSTANCE && modified.optional())) continue;
                pending.push(modified.base());
                continue;
            }
            if (!(current instanceof UserContract user)) {
                if (!current.accepts(value)) return false;
                continue;
            }
            user.bases.forEach(pending::push);
            for (Value.Callable refinement : user.refinements) {
                if (!visitedRefinements.add(refinement)) continue;
                Value result = refinementInvoker.apply(refinement, new Value.Argument(value, span));
                while (result instanceof Value.Attributed attributed) result = attributed.value();
                if (!(result instanceof Value.Bool(boolean accepted)) || !accepted) return false;
            }
        }
        return true;
    }
}
