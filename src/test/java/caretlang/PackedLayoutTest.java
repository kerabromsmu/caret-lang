package caretlang;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class PackedLayoutTest {
    private static final SourceSpan SPAN = new SourceSpan(
            new SourcePosition(0, 1, 1), new SourcePosition(0, 1, 1));

    @Test
    void everyConcreteScalarUsesItsFixedWidthAndRoundTripsExactBoundaries() {
        record Case(BuiltinContract format, int width, Value first, Value second) {}
        for (Case sample : List.of(
                new Case(BuiltinContract.INT8, 1, number(-128), number(127)),
                new Case(BuiltinContract.UINT8, 1, number(0), number(255)),
                new Case(BuiltinContract.INT16, 2, number(-32768), number(32767)),
                new Case(BuiltinContract.UINT16, 2, number(0), number(65535)),
                new Case(BuiltinContract.INT32, 4, number(-2147483648L), number(2147483647L)),
                new Case(BuiltinContract.UINT32, 4, number(0), number(4294967295L)),
                new Case(BuiltinContract.INT64, 8, number("-9223372036854775808"),
                        number("9223372036854775807")),
                new Case(BuiltinContract.UINT64, 8, number(0),
                        number("18446744073709551615")),
                new Case(BuiltinContract.FLOAT, 4, new Value.Num(Float.MIN_VALUE),
                        new Value.Num(Float.MAX_VALUE)),
                new Case(BuiltinContract.DOUBLE, 8, new Value.Num(Double.MIN_VALUE),
                        new Value.Num(Double.MAX_VALUE)),
                new Case(BuiltinContract.BOOLEAN, 1, new Value.Bool(false), new Value.Bool(true)))) {
            Value.PackedCollection packed = new Value.PackedCollection(sample.format(),
                    List.of(sample.first(), sample.second()), SPAN);
            assertEquals(sample.width(), packed.layout().stride(), sample.format().publicName());
            assertEquals(sample.width() * 2, packed.payloadSize(), sample.format().publicName());
            assertEquals(sample.first(), packed.at(0), sample.format().publicName());
            assertEquals(sample.second(), packed.at(1), sample.format().publicName());
            assertEquals(2, packed.length());
        }
    }

    @Test
    void directIntegerWriteRejectsNonintegralValue() {
        PackedLayout layout = PackedLayout.of(BuiltinContract.INT8, SPAN);
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> layout.write(new Value.Num(1.5), new byte[layout.stride()], 0));
        assertEquals("Packed integer must be integral", invalid.getMessage());
    }

    @Test
    void appendCopiesPayloadAndPreservesItsDescriptor() {
        Value.PackedCollection first = new Value.PackedCollection(BuiltinContract.UINT64,
                List.of(number("18446744073709551615")), SPAN);
        Value.PackedCollection second = first.append(number(0));
        assertSame(first.layout(), second.layout());
        assertEquals(8, first.payloadSize());
        assertEquals(16, second.payloadSize());
        assertEquals(List.of(number("18446744073709551615")), first.values());
        assertEquals(List.of(number("18446744073709551615"), number(0)), second.values());
        byte[] unsignedBytes = new byte[8];
        first.layout().write(number("18446744073709551615"), unsignedBytes, 0);
        assertArrayEquals(new byte[]{-1, -1, -1, -1, -1, -1, -1, -1}, unsignedBytes);
        byte[] booleanBytes = new byte[2];
        PackedLayout booleanLayout = PackedLayout.of(BuiltinContract.BOOLEAN, SPAN);
        booleanLayout.write(new Value.Bool(false), booleanBytes, 0);
        booleanLayout.write(new Value.Bool(true), booleanBytes, 1);
        assertArrayEquals(new byte[]{0, 1}, booleanBytes);
    }

    @Test
    void namedTemplateUsesDeclarationOrderRatherThanDictionaryEnumeration() {
        CollectionConstructorDescriptor.HoleNode left = new CollectionConstructorDescriptor.HoleNode(
                1, List.of(BuiltinContract.UINT8), SPAN);
        CollectionConstructorDescriptor.HoleNode right = new CollectionConstructorDescriptor.HoleNode(
                2, List.of(BuiltinContract.BOOLEAN), SPAN);
        CollectionConstructorDescriptor.CollectionNode root = new CollectionConstructorDescriptor.CollectionNode(
                true, List.of(new CollectionConstructorDescriptor.Element("z", left, SPAN),
                        new CollectionConstructorDescriptor.Element("a", right, SPAN)), SPAN);
        TemplateContract template = new TemplateContract(
                new CollectionConstructorDescriptor(root, List.of(List.of(), List.of())),
                (callable, argument) -> { throw new AssertionError("No refinement expected"); });
        PackedLayout layout = PackedLayout.of(template, SPAN);
        byte[] payload = new byte[layout.stride()];
        layout.write(new Value.Dictionary(Map.of("a", new Value.Bool(true), "z", number(42))), payload, 0);
        assertArrayEquals(new byte[]{42, 1}, payload);
        assertEquals(new Value.Dictionary(Map.of("a", new Value.Bool(true), "z", number(42))).entries(),
                ((Value.Dictionary) layout.read(payload, 0)).entries());
        CollectionConstructorDescriptor.CollectionNode reversed = new CollectionConstructorDescriptor.CollectionNode(
                true, List.of(new CollectionConstructorDescriptor.Element("a", right, SPAN),
                        new CollectionConstructorDescriptor.Element("z", left, SPAN)), SPAN);
        PackedLayout reversedLayout = PackedLayout.of(new TemplateContract(
                new CollectionConstructorDescriptor(reversed, List.of(List.of(), List.of())),
                (callable, argument) -> { throw new AssertionError("No refinement expected"); }), SPAN);
        byte[] reversedPayload = new byte[reversedLayout.stride()];
        reversedLayout.write(new Value.Dictionary(Map.of("a", new Value.Bool(true), "z", number(42))),
                reversedPayload, 0);
        assertArrayEquals(new byte[]{1, 42}, reversedPayload);
        assertEquals(((Value.Dictionary) layout.read(payload, 0)).entries(),
                ((Value.Dictionary) reversedLayout.read(reversedPayload, 0)).entries());
    }

    @Test
    void reflectionContainsSemanticFactsWithoutPhysicalLayoutDetails() {
        Value.PackedCollection packed = new Value.PackedCollection(BuiltinContract.INT16,
                List.of(number(1), number(2)), SPAN);
        Map<String, Value> reflected = ValueSemantics.reflectionFields(packed);
        assertEquals(number(2), reflected.get("size"));
        Value.Dictionary contract = assertInstanceOf(Value.Dictionary.class, reflected.get("elementContract"));
        assertEquals(new Value.Str("Int16"), contract.entries().get("id"));
        assertFalse(ValueSemantics.reflectionFields(packed,
                ReflectionContext.restricted(false, false, false, java.util.Set.of()))
                .containsKey("elementContract"));
        for (String forbidden : List.of("buffer", "payload", "stride", "offset", "alignment",
                "byteOrder", "address", "layout")) {
            assertFalse(reflected.containsKey(forbidden), forbidden);
        }
    }

    @Test
    void packedAccessRetainsAcquiredElementAndNestedFieldContracts() {
        UserContract marker = new UserContract(List.of(BuiltinContract.INT8));
        Value tagged = new Value.Attributed(number(7), Set.of(marker));
        Value.PackedCollection scalars = new Value.PackedCollection(BuiltinContract.INT8,
                List.of(tagged), SPAN);
        assertTrue(marker.accepts(scalars.at(0)));
        assertTrue(marker.accepts(scalars.append(tagged).at(1)));

        CollectionConstructorDescriptor.HoleNode field = new CollectionConstructorDescriptor.HoleNode(
                1, List.of(BuiltinContract.INT8), SPAN);
        CollectionConstructorDescriptor.CollectionNode root = new CollectionConstructorDescriptor.CollectionNode(
                true, List.of(new CollectionConstructorDescriptor.Element("x", field, SPAN)), SPAN);
        TemplateContract template = new TemplateContract(
                new CollectionConstructorDescriptor(root, List.of(List.of())),
                (callable, argument) -> { throw new AssertionError("No refinement expected"); });
        Value.PackedCollection records = new Value.PackedCollection(template,
                List.of(new Value.Dictionary(Map.of("x", tagged))), SPAN);
        Value.Dictionary decoded = assertInstanceOf(Value.Dictionary.class, records.at(0));
        assertTrue(marker.accepts(decoded.entries().get("x")));
        assertTrue(ValueSemantics.equal(new Value.Dictionary(Map.of("x", number(7))), decoded));
    }

    @Test
    void broadAndNullableLayoutsFailBeforeReadingAnyPayload() {
        LangException broad = assertThrows(LangException.class,
                () -> PackedLayout.of(BuiltinContract.INTEGER, SPAN));
        assertEquals(Diagnostic.Codes.INVALID_PACKED_LAYOUT, broad.diagnostic().code());
        CollectionConstructorDescriptor.HoleNode nullable = new CollectionConstructorDescriptor.HoleNode(
                1, List.of(new ModifiedContract(BuiltinContract.INT8, true, false)), SPAN);
        CollectionConstructorDescriptor.CollectionNode root = new CollectionConstructorDescriptor.CollectionNode(
                false, List.of(new CollectionConstructorDescriptor.Element(null, nullable, SPAN)), SPAN);
        TemplateContract template = new TemplateContract(
                new CollectionConstructorDescriptor(root, List.of(List.of())),
                (callable, argument) -> { throw new AssertionError("No refinement expected"); });
        LangException rejected = assertThrows(LangException.class, () -> PackedLayout.of(template, SPAN));
        assertEquals("Packed fields cannot be nullable or optional", rejected.diagnostic().message());
        LangException invalidPayload = assertThrows(LangException.class,
                () -> new Value.PackedCollection(BuiltinContract.INT8,
                        List.of(Value.Missing.INSTANCE), SPAN));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, invalidPayload.diagnostic().code());
        assertEquals("Packed value does not match its fixed layout", invalidPayload.diagnostic().message());
    }

    private static Value.Num number(long value) { return new Value.Num(value); }
    private static Value.Num number(String value) { return new Value.Num(new BigInteger(value)); }
}
