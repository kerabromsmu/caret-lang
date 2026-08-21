package caretlang;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Structural, exact-arity contract over language-owned callable signature metadata. */
final class ArrowContractDescriptor implements ContractDescriptor {
    private final List<List<ContractDescriptor>> parameters;
    private final ContractDescriptor result;
    private final List<String> effects;

    ArrowContractDescriptor(List<List<ContractDescriptor>> parameters, ContractDescriptor result,
                            List<String> effects) {
        this.parameters = parameters.stream().map(List::copyOf).toList();
        this.result = result;
        this.effects = List.copyOf(effects);
    }

    List<List<ContractDescriptor>> parameters() { return parameters; }
    ContractDescriptor result() { return result; }
    List<String> effects() { return effects; }

    @Override public String publicName() {
        String left = String.join(" ", parameters.stream().map(ArrowContractDescriptor::parameterName).toList());
        return "[" + left + "] -> " + result.publicName();
    }

    private static String parameterName(List<ContractDescriptor> requirements) {
        if (requirements.size() == 1) return requirements.getFirst().publicName();
        return "(" + String.join(" ", requirements.stream().map(ContractDescriptor::publicName).toList()) + ")";
    }

    @Override public boolean accepts(Value value) {
        value = ValueSemantics.underlying(value);
        if (!(value instanceof Value.Callable callable)) return false;
        List<CallableSignature> variants = callable.variantSignatures();
        if (variants.isEmpty()) return satisfies(callable.signature());

        // Initial conservative overload proof: one variant must cover the entire requested domain;
        // every variant that also covers it must have a compatible result and effect bound.
        List<CallableSignature> covering = variants.stream().filter(this::acceptsParameters).toList();
        return !covering.isEmpty() && covering.stream().allMatch(this::acceptsResultAndEffects);
    }

    boolean implies(ArrowContractDescriptor required) {
        if (parameters.size() != required.parameters.size()) return false;
        for (int index = 0; index < parameters.size(); index++) {
            // An arrow that accepts the required arrow's domain is the more specific value contract.
            if (!conjunctionImplies(required.parameters.get(index), parameters.get(index))) return false;
        }
        return ContractRelations.implies(result, required.result)
                && new HashSet<>(required.effects).containsAll(effects);
    }

    private boolean satisfies(CallableSignature signature) {
        return acceptsParameters(signature) && acceptsResultAndEffects(signature);
    }

    private boolean acceptsParameters(CallableSignature signature) {
        if (signature.parameters().size() != parameters.size()) return false;
        for (int index = 0; index < parameters.size(); index++) {
            List<String> accepted = signature.parameters().get(index).requirements();
            if (!namesImply(parameters.get(index), accepted)) return false;
        }
        return variablesCompatible(signature);
    }

    private boolean acceptsResultAndEffects(CallableSignature signature) {
        if (signature.effects().upperBound() == null
                || !new HashSet<>(effects).containsAll(signature.effects().upperBound())) return false;
        List<String> guarantees = signature.result().guarantees();
        return guarantees.stream().anyMatch(candidate -> nameImplies(candidate, result.publicName()));
    }

    private boolean variablesCompatible(CallableSignature signature) {
        Set<Integer> required = new LinkedHashSet<>();
        parameters.forEach(parameter -> parameter.forEach(contract -> collectVariables(contract, required)));
        collectVariables(result, required);
        if (required.isEmpty()) return true;
        if (signature.variables().size() < required.size()) return false;
        Set<String> occurrences = new LinkedHashSet<>();
        signature.parameters().forEach(parameter -> occurrences.addAll(parameter.requirements()));
        occurrences.addAll(signature.result().guarantees());
        return required.stream().allMatch(index -> occurrences.contains("_" + index));
    }

    private static void collectVariables(ContractDescriptor contract, Set<Integer> indexes) {
        if (contract instanceof ContractVariableDescriptor(int index)) indexes.add(index);
        if (contract instanceof ParameterizedContract parameterized) {
            parameterized.arguments().forEach(argument -> collectVariables(argument, indexes));
        }
        if (contract instanceof ModifiedContract modified) collectVariables(modified.base(), indexes);
    }

    private static boolean namesImply(List<ContractDescriptor> supplied, List<String> accepted) {
        if (accepted.isEmpty()) return true;
        return accepted.stream().allMatch(target -> supplied.stream()
                .anyMatch(source -> nameImplies(source.publicName(), target)));
    }

    private static boolean conjunctionImplies(List<ContractDescriptor> supplied,
                                              List<ContractDescriptor> required) {
        if (required.isEmpty()) return true;
        return required.stream().allMatch(target -> supplied.stream()
                .anyMatch(source -> ContractRelations.implies(source, target)));
    }

    private static boolean nameImplies(String source, String target) {
        if (source.equals(target) || target.equals("Any")) return true;
        ContractDescriptor left = descriptor(source);
        ContractDescriptor right = descriptor(target);
        return left != null && right != null && ContractRelations.implies(left, right);
    }

    private static ContractDescriptor descriptor(String name) {
        if (name.matches("_[1-9][0-9]*")) {
            return new ContractVariableDescriptor(Integer.parseInt(name.substring(1)));
        }
        return BuiltinContract.named(name).orElse(null);
    }
}

