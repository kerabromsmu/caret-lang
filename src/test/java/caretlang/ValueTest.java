package caretlang;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

final class ValueTest {
    private static final class ProviderValue implements Value.Reflective, CollectionRuntime.Provider {
        private final CollectionRuntime.Facts facts;
        private final Value entries;
        private final Value accessible;
        private int enumerations;

        private ProviderValue(CollectionRuntime.Facts facts, Value entries) {
            this(facts, entries, Value.Missing.INSTANCE);
        }

        private ProviderValue(CollectionRuntime.Facts facts, Value entries, Value accessible) {
            this.facts = facts;
            this.entries = entries;
            this.accessible = accessible;
        }

        @Override public Value getElement(Value key) { return accessible; }
        @Override public Value keys() { throw new AssertionError("equality must enumerate fields"); }
        @Override public Value valueEntries() { throw new AssertionError("equality must enumerate fields"); }
        @Override public Value fieldEntries() { enumerations++; return entries; }
        @Override public Value size() { return Value.Missing.INSTANCE; }
        @Override public CollectionRuntime.Facts facts() { return facts; }
        @Override public Optional<Value> find(String name) { return Optional.empty(); }
        @Override public Map<String, Value> fields() { return Map.of(); }
    }

    @Test
    void namedCollectionsAndDictionariesRejectNullRuntimeValues() {
        LinkedHashMap<String, Value> invalid = new LinkedHashMap<>();
        invalid.put("bad", null);
        assertThrows(NullPointerException.class, () -> new Value.Dictionary(invalid));
        LinkedHashMap<String, Value> nullKey = new LinkedHashMap<>();
        nullKey.put(null, new Value.Num(1));
        assertThrows(NullPointerException.class, () -> new Value.Dictionary(nullKey));
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
        for (int i = 0; i < 500; i++) {
            left = new Value.Seq(List.of(left));
            right = new Value.Seq(List.of(right));
        }

        assertTrue(ValueSemantics.equal(left, right));
        String rendered = assertDoesNotThrow(left::toString);
        assertTrue(rendered.startsWith("[\n  ["));
        assertTrue(rendered.endsWith("]\n]"));
    }

    @Test
    void collectionEqualityHonorsGuaranteesMultiplicityAndEnumeratedContent() {
        CollectionRuntime.Facts unorderedValues = new CollectionRuntime.Facts(
                CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.FALSE,
                CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.TRUE,
                CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.TRUE);
        ProviderValue leftValues = new ProviderValue(unorderedValues,
                new Value.Seq(List.of(new Value.Num(1), new Value.Num(2), new Value.Num(1))));
        ProviderValue reorderedValues = new ProviderValue(unorderedValues,
                new Value.Seq(List.of(new Value.Num(2), new Value.Num(1), new Value.Num(1))));
        ProviderValue wrongMultiplicity = new ProviderValue(unorderedValues,
                new Value.Seq(List.of(new Value.Num(2), new Value.Num(2), new Value.Num(1))));
        assertTrue(ValueSemantics.equal(leftValues, reorderedValues));
        assertFalse(ValueSemantics.equal(leftValues, wrongMultiplicity));

        CollectionRuntime.Facts unorderedKeyed = new CollectionRuntime.Facts(
                CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.FALSE,
                CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.TRUE,
                CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.TRUE);
        Value nestedKey = new Value.Seq(List.of(new Value.Num(1)));
        ProviderValue leftKeyed = new ProviderValue(unorderedKeyed, new Value.Seq(List.of(
                new Value.Field(nestedKey, new Value.Str("nested")),
                new Value.Field(new Value.Str("plain"), new Value.Num(2)))));
        ProviderValue rightKeyed = new ProviderValue(unorderedKeyed, new Value.Seq(List.of(
                new Value.Field(new Value.Str("plain"), new Value.Num(2)),
                new Value.Field(new Value.Seq(List.of(new Value.Num(1))), new Value.Str("nested")))));
        assertTrue(ValueSemantics.equal(leftKeyed, rightKeyed));

        ProviderValue unenumeratedLeft = new ProviderValue(unorderedKeyed, Value.EmptyCollection.INSTANCE,
                new Value.Num(1));
        ProviderValue unenumeratedRight = new ProviderValue(unorderedKeyed, Value.EmptyCollection.INSTANCE,
                new Value.Num(2));
        assertTrue(ValueSemantics.equal(unenumeratedLeft, unenumeratedRight));
    }

    @Test
    void collectionEqualityRejectsUnknownOrderAndKnownInfiniteInputsWithoutEnumeration() {
        ProviderValue unknownOrder = new ProviderValue(new CollectionRuntime.Facts(
                CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.UNKNOWN,
                CollectionRuntime.Guarantee.UNKNOWN, CollectionRuntime.Guarantee.TRUE,
                CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.TRUE),
                new Value.Seq(List.of(new Value.Num(1))));
        assertFalse(ValueSemantics.equal(unknownOrder, unknownOrder));
        assertEquals(0, unknownOrder.enumerations);

        ProviderValue infinite = new ProviderValue(new CollectionRuntime.Facts(
                CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.TRUE,
                CollectionRuntime.Guarantee.UNKNOWN, CollectionRuntime.Guarantee.FALSE,
                CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.TRUE),
                new Value.Seq(List.of(new Value.Num(1))));
        assertFalse(ValueSemantics.equal(infinite, infinite));
        assertEquals(0, infinite.enumerations);
    }

    @Test
    void iterativeRenderingPreservesCollectionSyntax() {
        LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
        fields.put("items", new Value.Seq(List.of(new Value.Num(1))));
        fields.put("lookup", new Value.Dictionary(Map.of("answer", new Value.Num(42))));
        Value value = new Value.Dictionary(fields);

        assertEquals("""
                [
                  "items" = [ 1 ]
                  "lookup" = [
                    "answer" = 42
                  ]
                ]""",
                value.toString());
    }

    @Test
    void persistentCollectionsHandleLongUpdateHistoriesWithoutChangingValues() {
        Value.Seq sequence = new Value.Seq(List.of());
        Value.Dictionary dictionary = new Value.Dictionary(new LinkedHashMap<>());
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
