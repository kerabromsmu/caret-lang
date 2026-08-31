package caretlang;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class ContractRelations {
    private ContractRelations() {}

    static boolean implies(ContractDescriptor left, ContractDescriptor right) {
        if (left == right || right == BuiltinContract.ANY) return true;
        if (left instanceof ArrowContractDescriptor arrowLeft
                && right instanceof ArrowContractDescriptor arrowRight) {
            return arrowLeft.implies(arrowRight);
        }
        if (left instanceof TemplateContract templateLeft && right instanceof TemplateContract templateRight) {
            return templateLeft.implies(templateRight);
        }
        if (left instanceof ContractVariableDescriptor leftVariable
                || right instanceof ContractVariableDescriptor) return left.equals(right);

        Absence l = absence(left);
        Absence r = absence(right);
        if (!baseImpliesDomain(l.base, r)) return false;
        if (l.nullable && !acceptsNull(r)) return false;
        return !l.optional || acceptsMissing(r);
    }

    private static boolean baseImpliesDomain(ContractDescriptor left, Absence right) {
        if (left == BuiltinContract.NULL && right.nullable) return true;
        if (left == BuiltinContract.MISSING && right.optional) return true;
        return rawImplies(left, right.base);
    }

    private static boolean acceptsNull(Absence domain) {
        return domain.nullable || rawImplies(BuiltinContract.NULL, domain.base);
    }

    private static boolean acceptsMissing(Absence domain) {
        return domain.optional || rawImplies(BuiltinContract.MISSING, domain.base);
    }

    private static boolean rawImplies(ContractDescriptor left, ContractDescriptor right) {
        if (left == right || right == BuiltinContract.ANY) return true;

        if (left instanceof ParameterizedContract lp && right instanceof ParameterizedContract rp) {
            if (lp.base() != rp.base() || lp.arguments().size() != rp.arguments().size()) return false;
            for (int i = 0; i < lp.arguments().size(); i++) {
                if (!implies(lp.arguments().get(i), rp.arguments().get(i))) return false;
            }
            return true;
        }

        Set<ContractDescriptor> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<ContractDescriptor> pending = new ArrayDeque<>();
        pending.add(left);
        while (!pending.isEmpty()) {
            ContractDescriptor current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current == right) return true;
            if (current instanceof ModifiedContract && implies(current, right)) return true;
            current.bases().forEach(pending::addLast);
        }
        return false;
    }

    private static Absence absence(ContractDescriptor descriptor) {
        boolean nullable = false;
        boolean optional = false;
        while (descriptor instanceof ModifiedContract modified) {
            nullable |= modified.nullable();
            optional |= modified.optional();
            descriptor = modified.base();
        }
        return new Absence(descriptor, nullable, optional);
    }

    private record Absence(ContractDescriptor base, boolean nullable, boolean optional) {}
}
