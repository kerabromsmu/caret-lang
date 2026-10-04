package caretlang;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static caretlang.InterpreterTestSupport.*;

final class ConversionInterpreterTest {
    @Test
    void explicitNumericConversionProducesValuesAndPreservesPredicates() {
        assertEquals("false\n0.10000000149011612\n-3\n255\n5\n", execute("""
                print Float 0.1
                print (Float) 0.1
                print (Integer) (-3.75)
                print (UInt8) 255.9
                add x y = x + y
                print (Int8) add 2 3
                """));
        Interpreter interpreter = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
        interpreter.execute(new Parser("rounded = (Float) 0.1").parseProgram());
        assertTrue(interpreter.warnings().isEmpty());
    }

    @Test
    void conversionTargetsUseRuntimeIdentityAndGroupedCallsRemainOrdinary() {
        assertEquals("true\ntrue\n5\n42\n", execute("""
                Choice = Float
                selected = true & Choice ! Double
                (Float) converted = (selected) 0.1
                print Float converted
                print Float ((selected) 0.1)
                add x y = x + y
                print (add) 2 3
                print (String) 42
                """));
    }

    @Test
    void sequenceConversionSelectsRepresentationAndConvertsEachElement() {
        assertEquals("[ 1 2 ]\ntrue\n", execute("""
                converted = (Sequence Int8) [1.9 2.1]
                print converted
                Small = Sequence Int8
                print Small converted
                """));
        LangException keyed = assertThrows(LangException.class,
                () -> execute("print (Sequence Int8) [^a = 1]"));
        assertEquals(Diagnostic.Codes.EXPECTED_SEQUENCE, keyed.diagnostic().code());
    }

    @Test
    void structuralConversionUsesExactShapeAndValidatesRepeatedAndFixedValues() {
        assertEquals("[ 1 2 ]\n[\n  \"age\" = 7\n  \"name\" = \"42\"\n]\n", execute("""
                Pair = template [(Int8) _ (Int8) _]
                Person = template [^name = (String) _ ^age = (Int8) _]
                print (Pair) [1.9 2.8]
                print (Person) [^name = 42 ^age = 7.8]
                """));
        for (String source : List.of("""
                Person = template [^name = (String) _ ^age = (Int8) _]
                print (Person) [^age = 7.8]
                """, """
                Same = template [_1 _1]
                print (Same) [1 2]
                """, """
                Fixed = template [7 (Int8) _]
                print (Fixed) [8 2]
                """)) {
            LangException failure = assertThrows(LangException.class, () -> execute(source));
            assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, failure.diagnostic().code());
        }
    }

    @Test
    void packedConversionSelectsMembershipAndSequenceConversionRemovesIt() {
        assertEquals("[ 1 2 ]\ntrue\ntrue\nfalse\nfalse\n", execute("""
                Small = Packed Int8
                packed = (Small) [1.9 2.8]
                print packed
                print Small packed
                print (Sequence Int8 packed)
                print Small [1 2]
                ordinary = (Sequence Number) packed
                print Small ordinary
                """));
        LangException invalid = assertThrows(LangException.class,
                () -> execute("print (Packed Integer) [1 2]"));
        assertEquals(Diagnostic.Codes.INVALID_PACKED_LAYOUT, invalid.diagnostic().code());
    }

    @Test
    void packedLiteralConstructionKeepsDeclarationCheckingStrict() {
        assertEquals("true\n[ 1 2 ]\n", execute("""
                Small = Packed Int8
                (Small) literal = [1 2]
                print Small literal
                print literal
                """));
        LangException failure = assertThrows(LangException.class,
                () -> execute("(Packed Int8) literal = [1.5]"));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, failure.diagnostic().code());
    }

    @Test
    void numericConversionChecksBoundariesAfterTruncation() {
        assertEquals("-128\n127\n0\n65535\n", execute("""
                print (Int8) (-128.9)
                print (Int8) 127.9
                print (Natural) 0.9
                print (UInt16) 65535.9
                """));
        for (String source : List.of("(Int8) 128", "(Int8) (-129)",
                "(UInt8) (-1)", "(UInt16) 65536", "(Int16) 32768",
                "(UInt32) 4294967296", "(Int32) 2147483648",
                "(UInt64) 18446744073709551616", "(Int64) 9223372036854775808",
                "(Natural) (-1.1)")) {
            LangException failure = assertThrows(LangException.class, () -> execute(source), source);
            assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, failure.diagnostic().code(), source);
        }
        assertEquals("-32768\n32767\n4294967295\n-9223372036854775808\n18446744073709551615\n", execute("""
                print (Int16) (-32768)
                print (Int16) 32767
                print (UInt32) 4294967295
                print (Int64) (-9223372036854775808)
                print (UInt64) 18446744073709551615
                """));
        LangException overflow = assertThrows(LangException.class,
                () -> execute("(Double) " + "9".repeat(400)));
        assertEquals(Diagnostic.Codes.NON_FINITE_RESULT, overflow.diagnostic().code());
    }

    @Test
    void conversionExtentHolesAndNonContractGroupingRemainDistinct() {
        assertEquals("6\n5\nfalse\ntrue\n5\n7\n", execute("""
                add left right = left + right
                print (Int8) add 2 3 + 1
                print (Int8) (add 2 3)
                checked = (Int8) _
                print checked 128
                print checked 5
                print (add) 2 3
                print (Int8) $ add 3 4
                """));
        LangException nonContract = assertThrows(LangException.class,
                () -> execute("print (42) 1"));
        assertEquals(Diagnostic.Codes.NOT_CALLABLE, nonContract.diagnostic().code());
        assertEquals(1, nonContract.span().start().line());
    }

    @Test
    void conversionCoversPostfixAndLambdaOperandsButCheckedNumberedHolesStayPredicates() {
        assertEquals("7\n5\ntrue\nfalse\n", execute("""
                record = [^value = 7.9]
                print (Int8) record.value
                print (Int8) (value -> value + 1) 4
                checked = (Int8) _1
                print checked 7
                print checked 128
                """));
    }

    @Test
    void conversionUsesSelectedToStringAndConsumesLazyInput() {
        assertEquals("value:7\n1\n2\n[ 1 2 ]\n", execute("""
                (String) toString (Number) value = "value:" + numberText value
                print (String) 7
                (Output Number) trace (Number) value =
                  print value
                  value
                source = map trace [1 2]
                packed = (Packed Int8) source
                print packed
                """));
        LangException parsing = assertThrows(LangException.class,
                () -> execute("(Int8) \"7\""));
        assertEquals(Diagnostic.Codes.UNSUPPORTED_CONVERSION, parsing.diagnostic().code());
        assertEquals(1, parsing.span().start().line());
        LangException truthiness = assertThrows(LangException.class,
                () -> execute("(Boolean) 1"));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, truthiness.diagnostic().code());
    }

    @Test
    void recursiveStringConversionRunsSelectedRendererEffectsInOrder() {
        assertEquals("1\n2\n[ \"n:1\" \"n:2\" ]\n", execute("""
                (Output String) toString (Number) value =
                  print value
                  "n:" + numberText value
                converted = (Sequence String) [1 2]
                print converted
                """));
        LangException disallowed = assertThrows(LangException.class, () -> execute("""
                (Output String) toString (Number) value =
                  print value
                  numberText value
                (pure Sequence String) render ignored = (Sequence String) [1]
                render 0
                """));
        assertEquals(Diagnostic.Codes.UNKNOWN_CALL_EFFECTS, disallowed.diagnostic().code());
    }

    @Test
    void keyedConversionRequiresProjectionAndEmptyPackingIsSelected() {
        assertEquals("false\ntrue\n[ 1 2 ]\ntrue\n", execute("""
                Small = Packed Int8
                print Small []
                selected = (Small) []
                print Small selected
                print (Sequence Int8) values [^first = 1 ^second = 2]
                print selected == []
                """));
        LangException keyed = assertThrows(LangException.class,
                () -> execute("(Packed Int8) [^first = 1]"));
        assertEquals(Diagnostic.Codes.EXPECTED_SEQUENCE, keyed.diagnostic().code());
    }

    @Test
    void conversionRejectsDeclaredInfiniteInputBeforeEnumeration() {
        class InfiniteProvider implements Value.Reflective, CollectionRuntime.Provider {
            @Override public Optional<Value> find(String name) { return Optional.empty(); }
            @Override public Map<String, Value> fields() { return Map.of(); }
            @Override public Value getElement(Value key) { throw new AssertionError("Must not enumerate"); }
            @Override public Value keys() { throw new AssertionError("Must not enumerate"); }
            @Override public Value valueEntries() { throw new AssertionError("Must not enumerate"); }
            @Override public Value fieldEntries() { throw new AssertionError("Must not enumerate"); }
            @Override public Value size() { return Value.Missing.INSTANCE; }
            @Override public CollectionRuntime.Facts facts() {
                return new CollectionRuntime.Facts(CollectionRuntime.Guarantee.FALSE,
                        CollectionRuntime.Guarantee.UNKNOWN, CollectionRuntime.Guarantee.UNKNOWN,
                        CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.FALSE,
                        CollectionRuntime.Guarantee.TRUE);
            }
        }
        Interpreter interpreter = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
        interpreter.defineEmbeddingValue("infiniteSource", InfiniteProvider::new);
        LangException failure = assertThrows(LangException.class, () ->
                interpreter.execute(new Parser("(Packed Int8) infiniteSource").parseProgram()));
        assertEquals(Diagnostic.Codes.EAGER_INFINITE, failure.diagnostic().code());
        assertEquals(1, failure.span().start().line());
        assertEquals(15, failure.span().start().column());
        assertEquals("Cannot convert a declared-infinite Collection", failure.diagnostic().message());
        assertSame(DiagnosticCatalog.CONVERSION_INFINITE, failure.catalogEntry());
        assertEquals(Diagnostic.Phase.RUNTIME, failure.diagnostic().phase());
        assertEquals("Line 1, column 15: Cannot convert a declared-infinite Collection", failure.getMessage());
    }

    @Test
    void nestedTemplateAndPackedConversionRetainSelectedElementContract() {
        assertEquals("[\n  [ 1 2 ]\n  [ 3 4 ]\n]\ntrue\ntrue\n[\n  [ 1 2 ]\n  [ 3 4 ]\n]\n", execute("""
                Point = template [(Int8) _ (UInt8) _]
                Points = Packed Point
                packed = (Points) [[1.9 2.1] [3.8 4.4]]
                print packed
                print Points packed
                print (Sequence Point packed)
                print (Sequence (Sequence Number)) packed
                """));
    }

    @Test
    void derivedPackedContractsAcquireNominalElementsAcrossAllOperations() {
        assertEquals("true\ntrue\ntrue\ntrue\ntrue\n", execute("""
                Small = contract Int8
                (Packed Small) values = [1]
                converted = (Packed Small) [2]
                appended = seqAdd values 3
                print Small (getElement values 0)
                print Small (getElement converted 0)
                print Small (getElement appended 1)
                print (Sequence Small appended)
                print Packed Small appended
                """));
        assertEquals("true\ntrue\n", execute("""
                Small = contract Int8
                Point = template [(Small) _]
                (Packed Point) points = [[1]]
                print Small (getElement (getElement points 0) 0)
                print Point (getElement points 0)
                """));
        String refined = """
                positive value = value > 0
                Small = contract [Int8 positive]
                (Packed Small) values = [1]
                extended = seqAdd values 2
                print Small (getElement extended 0)
                print Small (getElement extended 1)
                """;
        assertEquals(executeWithOwnership(refined, OwnershipTracker.Mode.DISABLED),
                executeWithOwnership(refined, OwnershipTracker.Mode.ENABLED));
        assertEquals("true\ntrue\n", execute(refined));
        LangException refinement = assertThrows(LangException.class, () -> execute("""
                positive value = value > 0
                Small = contract [Int8 positive]
                (Packed Small) values = [1]
                seqAdd values (0 - 1)
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, refinement.diagnostic().code());
        for (String expression : List.of("(Packed Small) [1.9]", "seqAdd values 1.9")) {
            LangException failure = assertThrows(LangException.class, () -> execute("""
                    Small = contract Int8
                    (Packed Small) values = [1]
                    """ + expression));
            assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, failure.diagnostic().code());
        }
        LangException conflict = assertThrows(LangException.class, () -> execute("""
                Conflicted = contract [Int8 UInt8]
                (Packed Conflicted) []
                """));
        assertEquals(Diagnostic.Codes.INVALID_PACKED_LAYOUT, conflict.diagnostic().code());
    }

    @Test
    void packedAppendEnumerationEagerAndAliasesPreserveSelectedLayout() {
        assertEquals("true\ntrue\nfalse\n3\n[ 0 1 2 ]\n[ 1 2 3 ]\n2\ntrue\ntrue\ntrue\n", execute("""
                Small = Packed Int8
                source = (Small) [1 2]
                alias = source
                extended = seqAdd source 3
                print Small source
                print Small extended
                print source == extended
                print size extended
                print keys extended
                print values extended
                print seqGet alias 1
                print isFinite extended
                print extended == [1 2 3]
                print Small (eager extended)
                """));
        LangException invalid = assertThrows(LangException.class, () -> execute("""
                Small = Packed Int8
                source = (Small) [1 2]
                seqAdd source 128
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, invalid.diagnostic().code());
        assertEquals(3, invalid.span().start().line());
        assertEquals("Packed append rejects value for Int8", invalid.diagnostic().message());
    }

    @Test
    void packedLayoutsRejectBroadOptionalAndVariableSizeFieldsBeforePacking() {
        for (String target : List.of("Number", "Real", "Integer", "Natural", "String",
                "Int8?", "Int8~", "Sequence Int8", "Dictionary String Int8")) {
            LangException failure = assertThrows(LangException.class,
                    () -> execute("(Packed (" + target + ")) []"), target);
            assertEquals(Diagnostic.Codes.INVALID_PACKED_LAYOUT, failure.diagnostic().code(), target);
        }
        for (String template : List.of("template [(String) _]", "template [_]",
                "template [(Int8?) _]", "template [\"fixed\" (Int8) _]")) {
            LangException failure = assertThrows(LangException.class,
                    () -> execute("Item = " + template + "\n(Packed Item) []"), template);
            assertEquals(Diagnostic.Codes.INVALID_PACKED_LAYOUT, failure.diagnostic().code(), template);
        }
        for (String value : List.of("~", "?")) {
            LangException failure = assertThrows(LangException.class,
                    () -> execute("(Packed Int8) [" + value + "]"), value);
            assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, failure.diagnostic().code(), value);
        }
    }

    @Test
    void packedProtocolAndFailuresMatchWithOwnershipOptimizationDisabled() {
        assertEquals("true\n", execute("""
                narrow = (Packed Int8) [1 2 3]
                wide = (Packed Int32) [1 2 3]
                print narrow == wide
                """));
        String program = """
                Small = Packed Int16
                source = (Small) [1 2]
                extended = seqAdd source 3
                identity value = value
                mapped = map identity extended
                positive value = value > 0
                filtered = filter extended positive
                print source
                print extended
                print keys extended
                print values extended
                print fields extended
                print size extended
                print getElement extended 1
                print extended == [1 2 3]
                print extended == (Packed Int32) [1 2 3]
                print Small extended
                print Small mapped
                print Small filtered
                print Small (eager extended)
                print @extended.size
                print @extended.elementContract.id
                """;
        assertEquals(executeWithOwnership(program, OwnershipTracker.Mode.DISABLED),
                executeWithOwnership(program, OwnershipTracker.Mode.ENABLED));
        for (OwnershipTracker.Mode mode : OwnershipTracker.Mode.values()) {
            Interpreter interpreter = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()),
                    null, EffectCatalog.standard(false), mode);
            Value.PackedCollection selected = assertInstanceOf(Value.PackedCollection.class,
                    interpreter.execute(new Parser("(Packed Int16) values = [1 2]").parseProgram()));
            assertEquals(mode == OwnershipTracker.Mode.ENABLED, selected.usesContiguousPayload());
            assertEquals(4, selected.layout().stride() * selected.length());
        }
        String rejected = """
                Small = Packed Int16
                source = (Small) [1 2]
                seqAdd source 40000
                """;
        for (OwnershipTracker.Mode mode : OwnershipTracker.Mode.values()) {
            Interpreter interpreter = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()),
                    null, EffectCatalog.standard(false), mode);
            LangException failure = assertThrows(LangException.class,
                    () -> interpreter.execute(new Parser(rejected).parseProgram()));
            assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, failure.diagnostic().code());
            assertEquals(3, failure.span().start().line());
        }
    }

    @Test
    void packedContextPropagatesToKnownParametersAndResultsWithoutRepackingValues() {
        assertEquals("true\ntrue\ntrue\n", execute("""
                Small = Packed Int8
                accepts (Small) values = true
                print accepts [1 2]
                (Small) make ignored = [3 4]
                print Small (make 0)
                acceptsDirect (Packed Int8) values = true
                print acceptsDirect [7 8]
                """));
        LangException strict = assertThrows(LangException.class, () -> execute("""
                Small = Packed Int8
                accepts (Small) values = true
                ordinary = [1 2]
                accepts ordinary
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, strict.diagnostic().code());
        LangException establishedResult = assertThrows(LangException.class, () -> execute("""
                Small = Packed Int8
                (Small) makeLocal ignored =
                  result = [5 6]
                  result
                makeLocal 0
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, establishedResult.diagnostic().code());
    }

    @Test
    void packedTemplateLiteralSelectsConcreteFloatFieldBeforeChecking() {
        assertEquals("true\n0.10000000149011612\n", execute("""
                Point = template [(Float) _ (Int8) _]
                (Packed Point) points = [[0.1 2]]
                print (Packed Point points)
                print points[0][0]
                """));
        assertEquals("0.10000000149011612\n", execute("""
                (Packed Float) values = [0.1]
                print values[0]
                """));
    }

    @Test
    void packedNestedTemplatesRetainFixedAndRepeatedSemanticConstraints() {
        assertEquals("true\ntrue\ntrue\n", execute("""
                Item = template [^pair = [(Int8) _ (Boolean) _] ^id = (UInt64) _]
                items = (Packed Item) [[^id = 18446744073709551615 ^pair = [7 true]]]
                print (Packed Item items)
                Fixed = template [7 (Int8) _]
                fixed = (Packed Fixed) [[7 2]]
                print (Packed Fixed fixed)
                Same = template [(Int8) _1 (Int8) _1]
                repeated = (Packed Same) [[3 3]]
                print (Packed Same repeated)
                """));
        for (String source : List.of("""
                Fixed = template [7 (Int8) _]
                (Packed Fixed) [[8 2]]
                """, """
                Same = template [(Int8) _1 (Int8) _1]
                (Packed Same) [[1 2]]
                """)) {
            LangException rejected = assertThrows(LangException.class, () -> execute(source));
            assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, rejected.diagnostic().code());
        }
    }

    @Test
    void packedScalarFormatsCoverEveryWidthThroughConstructionAppendAccessAndReflection() {
        record Case(String format, String low, String high, int width) {}
        for (Case sample : List.of(
                new Case("Int8", "-128", "127", 1),
                new Case("UInt8", "0", "255", 1),
                new Case("Int16", "-32768", "32767", 2),
                new Case("UInt16", "0", "65535", 2),
                new Case("Int32", "-2147483648", "2147483647", 4),
                new Case("UInt32", "0", "4294967295", 4),
                new Case("Int64", "-9223372036854775808", "9223372036854775807", 8),
                new Case("UInt64", "0", "18446744073709551615", 8),
                new Case("Byte", "0", "255", 1),
                new Case("Float32", "1.401298464324817e-45", "3.4028234663852886e38", 4),
                new Case("Float64", "5e-324", "1.7976931348623157e308", 8),
                new Case("Boolean", "false", "true", 1))) {
            Interpreter interpreter = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
            String literal = sample.format().startsWith("Float")
                    ? new java.math.BigDecimal(sample.low()).toPlainString() : sample.low();
            String high = sample.format().startsWith("Float")
                    ? new java.math.BigDecimal(sample.high()).toPlainString() : sample.high();
            String low = literal.startsWith("-") ? "(" + literal + ")" : literal;
            String declaration = "(Packed " + sample.format() + ") packed = ["
                    + low + " " + high + "]";
            Value.PackedCollection packed = assertInstanceOf(Value.PackedCollection.class,
                    interpreter.execute(new Parser(declaration).parseProgram()), sample.format());
            assertEquals(sample.width() * 2, packed.payloadSize(), sample.format());
            assertEquals(packed.at(0), packed.getElement(new Value.Num(0)), sample.format());
            assertEquals(new Value.Num(2), ValueSemantics.reflectionFields(packed).get("size"), sample.format());
            assertTrue(ValueSemantics.equal(packed, new Value.Seq(packed.values())), sample.format());
            String appendValue = sample.format().equals("Boolean") ? "false" : "0";
            Value.PackedCollection appended = assertInstanceOf(Value.PackedCollection.class,
                    interpreter.execute(new Parser("seqAdd packed " + appendValue).parseProgram()),
                    sample.format());
            assertSame(packed.layout(), appended.layout(), sample.format());
            assertEquals(sample.width() * 3, appended.payloadSize(), sample.format());
            assertEquals(packed.values(), appended.values().subList(0, 2), sample.format());
            assertEquals(new Value.Bool(true), interpreter.execute(new Parser(
                    "Packed " + sample.format() + " packed").parseProgram()), sample.format());
        }
    }

    @Test
    void packedIntegerWidthsRejectAdjacentOutOfRangeValuesOnConstructionAndAppend() {
        record Case(String format, String low, String high) {}
        for (Case sample : List.of(
                new Case("Int8", "-128", "127"), new Case("UInt8", "0", "255"),
                new Case("Int16", "-32768", "32767"), new Case("UInt16", "0", "65535"),
                new Case("Int32", "-2147483648", "2147483647"),
                new Case("UInt32", "0", "4294967295"),
                new Case("Int64", "-9223372036854775808", "9223372036854775807"),
                new Case("UInt64", "0", "18446744073709551615"))) {
            for (String adjacent : List.of(
                    new java.math.BigInteger(sample.low()).subtract(java.math.BigInteger.ONE).toString(),
                    new java.math.BigInteger(sample.high()).add(java.math.BigInteger.ONE).toString())) {
                String expression = adjacent.startsWith("-") ? "(" + adjacent + ")" : adjacent;
                LangException construction = assertThrows(LangException.class, () -> execute(
                        "(Packed " + sample.format() + ") [" + expression + "]"), sample.format());
                assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION,
                        construction.diagnostic().code(), sample.format());
                assertEquals(Diagnostic.Phase.RUNTIME, construction.diagnostic().phase());
                assertEquals(1, construction.span().start().line());

                LangException append = assertThrows(LangException.class, () -> execute(
                        "(Packed " + sample.format() + ") packed = [0]\n"
                                + "seqAdd packed " + expression), sample.format());
                assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION,
                        append.diagnostic().code(), sample.format());
                assertEquals(Diagnostic.Phase.RUNTIME, append.diagnostic().phase());
                assertEquals(2, append.span().start().line());
            }
        }
    }

    private String executeWithOwnership(String source, OwnershipTracker.Mode mode) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8),
                null, EffectCatalog.standard(false), mode);
        interpreter.execute(new Parser(source).parseProgram());
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
