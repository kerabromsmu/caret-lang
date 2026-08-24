package caretlang;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared runtime policy for collection representation and canonical named-field order. */
final class CollectionRuntime {
    private CollectionRuntime() {}

    static final Comparator<String> FIELD_ORDER = CollectionRuntime::compareCodePoints;

    static LinkedHashMap<String, Value> canonicalNamedFields(Map<String, Value> fields) {
        ArrayList<Map.Entry<String, Value>> entries = new ArrayList<>(fields.entrySet());
        entries.sort(Map.Entry.comparingByKey(FIELD_ORDER));
        LinkedHashMap<String, Value> result = new LinkedHashMap<>();
        for (Map.Entry<String, Value> entry : entries) result.put(entry.getKey(), entry.getValue());
        return result;
    }

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
