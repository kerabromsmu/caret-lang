package caretlang;

import java.util.List;
import java.util.Objects;

/** Language-owned, immutable description of a collection-producing hole expression. */
final class CollectionConstructorDescriptor {
    sealed interface Node permits CollectionNode, FixedNode, HoleNode {}

    record CollectionNode(boolean named, List<Element> elements, SourceSpan span) implements Node {
        CollectionNode { elements = List.copyOf(elements); }
    }

    record Element(String name, Node value, SourceSpan span) {
        Element { Objects.requireNonNull(value); }
    }

    record FixedNode(Value value, SourceSpan span) implements Node {
        FixedNode { Objects.requireNonNull(value); }
    }

    record HoleNode(int parameter, List<Object> requirements, SourceSpan span) implements Node {
        HoleNode { requirements = List.copyOf(requirements); }
    }

    private final CollectionNode root;
    private final List<List<Object>> parameterRequirements;

    CollectionConstructorDescriptor(CollectionNode root, List<List<Object>> parameterRequirements) {
        this.root = Objects.requireNonNull(root);
        this.parameterRequirements = parameterRequirements.stream().map(List::copyOf).toList();
    }

    CollectionNode root() { return root; }
    int arity() { return parameterRequirements.size(); }
    List<List<Object>> parameterRequirements() { return parameterRequirements; }
}
