package caretlang;

/** A quantified relationship marker owned by one arrow-contract expression. */
record ContractVariableDescriptor(int index) implements ContractDescriptor {
    @Override public String publicName() { return "_" + index; }
    @Override public boolean accepts(Value value) { return false; }
}
