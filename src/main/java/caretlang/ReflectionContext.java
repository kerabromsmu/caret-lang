package caretlang;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/** Interpreter-owned visibility and authority used while projecting reflective metadata. */
final class ReflectionContext {
    private final boolean callableNames;
    private final boolean inferredFacts;
    private final boolean dereference;
    private final Set<Object> namedDescriptors;

    private ReflectionContext(boolean callableNames, boolean inferredFacts, boolean dereference,
                              Set<Object> namedDescriptors) {
        this.callableNames = callableNames;
        this.inferredFacts = inferredFacts;
        this.dereference = dereference;
        this.namedDescriptors = namedDescriptors;
    }

    static ReflectionContext defining() {
        return new ReflectionContext(true, true, true, null);
    }

    static ReflectionContext restricted(boolean callableNames, boolean inferredFacts, boolean dereference,
                                        Set<?> namedDescriptors) {
        Set<Object> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.addAll(Objects.requireNonNull(namedDescriptors));
        return new ReflectionContext(callableNames, inferredFacts, dereference,
                Collections.unmodifiableSet(identities));
    }

    static ReflectionContext externalModule(boolean callableNames, boolean dereference,
                                            Set<?> namedDescriptors) {
        return restricted(callableNames, false, dereference, namedDescriptors);
    }

    static ReflectionContext sandbox(boolean callableNames, boolean dereference,
                                     Set<?> namedDescriptors) {
        return restricted(callableNames, false, dereference, namedDescriptors);
    }

    ReflectionContext intersect(ReflectionContext other) {
        Objects.requireNonNull(other);
        Set<Object> descriptors;
        if (namedDescriptors == null) descriptors = other.namedDescriptors;
        else if (other.namedDescriptors == null) descriptors = namedDescriptors;
        else {
            Set<Object> intersection = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Object descriptor : namedDescriptors) if (other.namedDescriptors.contains(descriptor)) {
                intersection.add(descriptor);
            }
            descriptors = Collections.unmodifiableSet(intersection);
        }
        return new ReflectionContext(callableNames && other.callableNames,
                inferredFacts && other.inferredFacts, dereference && other.dereference, descriptors);
    }

    boolean callableNames() { return callableNames; }
    boolean inferredFacts() { return inferredFacts; }
    boolean dereference() { return dereference; }
    boolean names(Object descriptor) { return namedDescriptors == null || namedDescriptors.contains(descriptor); }
}
