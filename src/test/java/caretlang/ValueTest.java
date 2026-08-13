package caretlang;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

final class ValueTest {
    @Test
    void scopesAndDictionariesRejectNullRuntimeValues() {
        LinkedHashMap<String, Value> invalid = new LinkedHashMap<>();
        invalid.put("bad", null);
        assertThrows(NullPointerException.class, () -> new Value.Scope(invalid));
        assertThrows(NullPointerException.class, () -> new Value.Dict(invalid));
        LinkedHashMap<String, Value> nullKey = new LinkedHashMap<>();
        nullKey.put(null, new Value.Num(1));
        assertThrows(NullPointerException.class, () -> new Value.Scope(nullKey));
        assertThrows(NullPointerException.class, () -> new Value.Dict(nullKey));
    }

    @Test
    void primitiveValuesEnforceRuntimeInvariants() {
        assertThrows(IllegalArgumentException.class, () -> new Value.Num(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Value.Num(Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> new Value.Str(null));
    }

    @Test
    void callableValuesRejectInvalidConstructionState() {
        assertThrows(NullPointerException.class,
                () -> new Value.FunctionValue(null, List.of("value"), List::getFirst));
        assertThrows(NullPointerException.class,
                () -> new Value.FunctionValue("identity", List.of("value"),
                        (Function<List<Value>, Value>) null));
        assertThrows(NullPointerException.class,
                () -> new Value.HoleFunction(null, 1, arguments -> arguments.getFirst().value()));
        assertThrows(IllegalArgumentException.class,
                () -> new Value.HoleFunction("invalid", 0, arguments -> Value.Missing.INSTANCE));
        assertThrows(NullPointerException.class,
                () -> new Value.HoleFunction("invalid", 1,
                        null));
    }

    @Test
    void persistentArgumentChainsPreserveOrderAtLargeArity() {
        Value.BoundArguments arguments = Value.BoundArguments.empty();
        for (int i = 0; i < 10_000; i++) {
            arguments = arguments.appended(new Value.Argument(new Value.Num(i),
                    SourceSpan.point(new SourcePosition(i, 1, i + 1))));
        }
        List<Value.Argument> values = arguments.values();
        assertEquals(10_000, values.size());
        assertEquals(new Value.Num(0), values.getFirst().value());
        assertEquals(new Value.Num(9_999), values.getLast().value());
    }

    @Test
    void deeplyNestedValuesCompareAndRenderWithoutUsingTheJavaCallStack() {
        Value left = new Value.Num(1);
        Value right = new Value.Num(1);
        for (int i = 0; i < 20_000; i++) {
            left = new Value.Seq(List.of(left));
            right = new Value.Seq(List.of(right));
        }

        assertTrue(ValueSemantics.equal(left, right));
        String rendered = assertDoesNotThrow(left::toString);
        assertEquals(40_001, rendered.length());
        assertTrue(rendered.startsWith("[[[["));
        assertTrue(rendered.endsWith("]]"));
    }

    @Test
    void iterativeRenderingPreservesCollectionSyntax() {
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        fields.put("items", new Value.Seq(List.of(new Value.Num(1))));
        fields.put("lookup", new Value.Dict(Map.of("answer", new Value.Num(42))));
        Value value = new Value.Scope(fields);

        assertEquals("^{items = [1], lookup = #[#answer = 42]}", value.toString());
    }

    @Test
    void persistentCollectionsHandleLongUpdateHistoriesWithoutChangingValues() {
        Value.Seq sequence = new Value.Seq(List.of());
        Value.Dict dictionary = new Value.Dict(new LinkedHashMap<>());
        for (int i = 0; i < 10_000; i++) {
            sequence = sequence.appended(new Value.Num(i));
            dictionary = dictionary.put("key" + i, new Value.Num(i));
        }

        assertEquals(10_000, sequence.size());
        assertEquals(new Value.Num(0), sequence.find(0).orElseThrow());
        assertEquals(new Value.Num(9_999), sequence.find(9_999).orElseThrow());
        assertEquals(10_000, dictionary.size());
        assertEquals(new Value.Num(0), dictionary.find("key0").orElseThrow());
        assertEquals(new Value.Num(9_999), dictionary.find("key9999").orElseThrow());
    }
}
