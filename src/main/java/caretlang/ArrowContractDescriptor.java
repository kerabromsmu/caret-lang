package caretlang;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Structural, exact-arity contract over language-owned callable signature metadata. */
final class ArrowContractDescriptor implements ContractDescriptor {
    private final List<List<ContractDescriptor>> parameters;
    private final ContractDescriptor result;
    private final List<EffectDescriptor> effects;

    ArrowContractDescriptor(List<List<ContractDescriptor>> parameters, ContractDescriptor result,
                            List<EffectDescriptor> effects) {
        this.parameters = parameters.stream().map(List::copyOf).toList();
        this.result = result;
        this.effects = List.copyOf(effects);
    }

    List<List<ContractDescriptor>> parameters() { return parameters; }
    ContractDescriptor result() { return result; }
    List<EffectDescriptor> effects() { return effects; }

    @Override public String publicName() {
        String left = String.join(" ", parameters.stream().map(ArrowContractDescriptor::parameterName).toList());
        String right = effects.isEmpty() ? result.publicName() : "("
                + String.join(" ", effects.stream().map(EffectDescriptor::canonicalName).toList())
                + " " + result.publicName() + ")";
        return "[" + left + "] -> " + right;
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
            List<CallableSignature.ContractTerm> accepted = signature.parameters().get(index).requirements();
            if (!namesImply(parameters.get(index), accepted)) return false;
        }
        return variablesCompatible(signature);
    }

    private boolean acceptsResultAndEffects(CallableSignature signature) {
        if (signature.effects().upperBound() == null) return false;
        Set<String> allowance = effects.stream().map(EffectDescriptor::canonicalName)
                .collect(java.util.stream.Collectors.toSet());
        if (!allowance.containsAll(signature.effects().upperBound().stream()
                .map(CallableSignature.EffectRef::name).toList())) return false;
        List<CallableSignature.ContractTerm> guarantees = signature.result().guarantees();
        return guarantees.stream().anyMatch(candidate -> nameImplies(candidate.render(), result.publicName()));
    }

    private boolean variablesCompatible(CallableSignature signature) {
        Set<Integer> required = new LinkedHashSet<>();
        parameters.forEach(parameter -> parameter.forEach(contract -> collectVariables(contract, required)));
        collectVariables(result, required);
        if (required.isEmpty()) return true;
        if (signature.variables().size() < required.size()) return false;
        Set<Integer> occurrences = new LinkedHashSet<>();
        signature.parameters().forEach(parameter -> parameter.requirements()
                .forEach(term -> collectVariables(term, occurrences)));
        signature.result().guarantees().forEach(term -> collectVariables(term, occurrences));
        return required.stream().allMatch(index -> occurrences.contains(index - 1));
    }

    private static void collectVariables(ContractDescriptor contract, Set<Integer> indexes) {
        if (contract instanceof ContractVariableDescriptor(int index)) indexes.add(index);
        if (contract instanceof ParameterizedContract parameterized) {
            parameterized.arguments().forEach(argument -> collectVariables(argument, indexes));
        }
        if (contract instanceof ModifiedContract modified) collectVariables(modified.base(), indexes);
    }

    private static void collectVariables(CallableSignature.ContractTerm term, Set<Integer> indexes) {
        switch (term) {
            case CallableSignature.VariableRef variable -> indexes.add(variable.index());
            case CallableSignature.AppliedRef applied -> applied.arguments()
                    .forEach(argument -> collectVariables(argument, indexes));
            case CallableSignature.ModifiedRef modified -> collectVariables(modified.base(), indexes);
            case CallableSignature.ArrowRef arrow -> {
                arrow.parameters().forEach(parameter -> parameter.forEach(value -> collectVariables(value, indexes)));
                collectVariables(arrow.result(), indexes);
            }
            default -> { }
        }
    }

    private static boolean namesImply(List<ContractDescriptor> supplied,
                                      List<CallableSignature.ContractTerm> accepted) {
        if (accepted.isEmpty()) return true;
        return accepted.stream().allMatch(target -> target instanceof CallableSignature.VariableRef
                || supplied.stream().anyMatch(source -> nameImplies(source.publicName(), target.render())));
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
