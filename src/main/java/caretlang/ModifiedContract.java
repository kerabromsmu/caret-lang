package caretlang;

import java.util.List;
import java.util.Objects;

final class ModifiedContract implements ContractDescriptor {
    private final ContractDescriptor base;
    private final boolean nullable;
    private final boolean optional;

    ModifiedContract(ContractDescriptor base, boolean nullable, boolean optional) {
        this.base = Objects.requireNonNull(base);
        this.nullable = nullable;
        this.optional = optional;
        if (!nullable && !optional) throw new IllegalArgumentException("Contract modifier has no modifier");
    }

    ContractDescriptor base() { return base; }
    boolean nullable() { return nullable; }
    boolean optional() { return optional; }

    @Override public String publicName() {
        return base.publicName() + (nullable ? "?" : "") + (optional ? "~" : "");
    }

    @Override public boolean accepts(Value value) {
        value = ValueSemantics.underlying(value);
        if (value == Value.Null.INSTANCE) return nullable || base.accepts(value);
        if (value == Value.Missing.INSTANCE) return optional || base.accepts(value);
        return base.accepts(value);
    }

    @Override public List<ContractDescriptor> bases() { return List.of(base); }
}
