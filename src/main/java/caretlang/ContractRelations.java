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

    static boolean sameInvariantArgument(ContractDescriptor left, ContractDescriptor right) {
        if (left == right) return true;
        if (left instanceof ModifiedContract a && right instanceof ModifiedContract b) {
            return a.nullable() == b.nullable() && a.optional() == b.optional()
                    && sameInvariantArgument(a.base(), b.base());
        }
        if (left instanceof ParameterizedContract a && right instanceof ParameterizedContract b) {
            if (!sameInvariantArgument(a.base(), b.base()) || a.arguments().size() != b.arguments().size()) {
                return false;
            }
            for (int i = 0; i < a.arguments().size(); i++) {
                if (!sameInvariantArgument(a.arguments().get(i), b.arguments().get(i))) return false;
            }
            return true;
        }
        return false;
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
        if (left instanceof BuiltinContract a && right instanceof BuiltinContract b
                && numericFormatImplies(a, b)) return true;

        if (left instanceof ParameterizedContract lp && right instanceof ParameterizedContract rp) {
            if (lp.base() != rp.base() || lp.arguments().size() != rp.arguments().size()) return false;
            if (lp.base() == BuiltinContract.CONTAINER) {
                for (int i = 0; i < lp.arguments().size(); i++) {
                    if (!sameInvariantArgument(lp.arguments().get(i), rp.arguments().get(i))) return false;
                }
                return true;
            }
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

    private static boolean numericFormatImplies(BuiltinContract left, BuiltinContract right) {
        if (left == BuiltinContract.FLOAT && right == BuiltinContract.DOUBLE) return true;
        IntegerFormat source = integerFormat(left);
        if (source == null) return false;
        if (right == BuiltinContract.FLOAT) return source.maximumMagnitude().bitLength() <= 24;
        if (right == BuiltinContract.DOUBLE) return source.maximumMagnitude().bitLength() <= 53;
        IntegerFormat target = integerFormat(right);
        return target != null && source.min().compareTo(target.min()) >= 0
                && source.max().compareTo(target.max()) <= 0;
    }

    private record IntegerFormat(java.math.BigInteger min, java.math.BigInteger max) {
        java.math.BigInteger maximumMagnitude() { return min.abs().max(max.abs()); }
    }

    private static IntegerFormat integerFormat(BuiltinContract contract) {
        int width = switch (contract) {
            case INT8, UINT8 -> 8;
            case INT16, UINT16 -> 16;
            case INT32, UINT32 -> 32;
            case INT64, UINT64 -> 64;
            default -> 0;
        };
        if (width == 0) return null;
        boolean signed = contract == BuiltinContract.INT8 || contract == BuiltinContract.INT16
                || contract == BuiltinContract.INT32 || contract == BuiltinContract.INT64;
        java.math.BigInteger minimum = signed
                ? java.math.BigInteger.ONE.shiftLeft(width - 1).negate() : java.math.BigInteger.ZERO;
        java.math.BigInteger maximum = java.math.BigInteger.ONE.shiftLeft(signed ? width - 1 : width)
                .subtract(java.math.BigInteger.ONE);
        return new IntegerFormat(minimum, maximum);
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
