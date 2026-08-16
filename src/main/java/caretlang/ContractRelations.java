package caretlang;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class ContractRelations {
    private ContractRelations() {}

    static boolean implies(ContractDescriptor left, ContractDescriptor right) {
        if (left == right || right == BuiltinContract.ANY) return true;

        Absence l = absence(left);
        Absence r = absence(right);
        if (l.nullable && !r.nullable || l.optional && !r.optional) return false;
        if (l.base != left || r.base != right) return implies(l.base, r.base);

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
