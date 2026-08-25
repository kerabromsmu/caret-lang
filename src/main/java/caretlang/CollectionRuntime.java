package caretlang;

import java.util.Comparator;

/** Shared runtime policy for collection representation and canonical named-field order. */
final class CollectionRuntime {
    private CollectionRuntime() {}

    static final Comparator<String> FIELD_ORDER = CollectionRuntime::compareCodePoints;

    private static int compareCodePoints(String left, String right) {
        var leftPoints = left.codePoints().iterator();
        var rightPoints = right.codePoints().iterator();
        while (leftPoints.hasNext() && rightPoints.hasNext()) {
            int comparison = Integer.compare(leftPoints.nextInt(), rightPoints.nextInt());
            if (comparison != 0) return comparison;
        }
        return Boolean.compare(leftPoints.hasNext(), rightPoints.hasNext());
    }
}
