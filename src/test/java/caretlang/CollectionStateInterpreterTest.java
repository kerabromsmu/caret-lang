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
import caretlang.InterpreterTestSupport.ModeExecution;

final class CollectionStateInterpreterTest {
    @Test
    void emptyCollectionsAreShapeNeutralAndNamedFieldsHaveCanonicalOrder() {
        assertEquals("Collection\nempty\n0\ntrue\ntrue\na,with,z\n1\ntrue\n", execute("""
                empty = []
                first = [^z = 2 ^with = 1 ^a = 3]
                second = [^a = 3 ^z = 2 ^with = 1]
                print (@empty).kind
                print (@empty).shape
                print (@empty).size
                print Sequence empty
                print Dictionary empty
                print (@first).ids
                print first.with
                print first == second
                """));
    }

    @Test
    void commonCollectionProtocolEnumeratesValuesAndProjectsGuarantees() {
        assertEquals("""
                true
                false
                false
                [ 0 1 2 ]
                [ 1 ~ 3 ]
                [ 1 ~ 3 ]
                3
                true
                true
                ~
                true
                false
                true
                [ "a" "missing" "z" ]
                [ 1 ~ 3 ]
                [ "a" = 1 "missing" = ~ "z" = 3 ]
                3
                false
                true
                ~
                true
                true
                true
                []
                []
                []
                0
                ~
                ~
                true
                true
                ~
                ~
                true
                true
                true
                3
                [ "a" "missing" "z" ]
                local
                Collection
                Natural
                true
                0
                """, execute("""
                positional = [1 ~ 3]
                named = [^z = 3 ^a = 1 ^missing = ~]
                empty = []

                print Natural 0
                print Natural (-1)
                print Natural 1.5

                print keys positional
                print values positional
                print fields positional
                print size positional
                print isSequential positional
                print isOrdered positional
                print isUnique positional
                print isFinite positional
                print isKeyed positional
                print hasValues positional

                print keys named
                print values named
                print fields named
                print size named
                print isSequential named
                print isOrdered named
                print isUnique named
                print isFinite named
                print isKeyed named
                print hasValues named

                print keys empty
                print values empty
                print fields empty
                print size empty
                print isSequential empty
                print isOrdered empty
                print isUnique empty
                print isFinite empty
                print isKeyed empty
                print hasValues empty

                print (@positional).sequential
                print (@named).ordered
                print (@empty).finite
                print (@named).size

                namedKeys = keys _
                print namedKeys named

                locallyShadow collection =
                  keys value = "local"
                  keys collection
                print locallyShadow named

                sizeParameter = seqGet (@size).signature.parameters 0
                print (seqGet sizeParameter.requirements 0).id
                sizeResult = seqGet (@size).signature.result.guarantees 0
                print sizeResult.base.id
                print sizeResult.optional
                print seqSize (@size).signature.effects.upperBound
                """));
    }

    @Test
    void internalCollectionProvidersRejectContradictoryGuaranteesWithoutReadingContent() {
        class TestProvider implements Value.Reflective, CollectionRuntime.Provider {
            int reads;
            private final CollectionRuntime.Facts facts;
            TestProvider(CollectionRuntime.Facts facts) { this.facts = facts; }
            @Override public Value getElement(Value key) { reads++; return Value.Missing.INSTANCE; }
            @Override public Value keys() { reads++; return Value.EmptyCollection.INSTANCE; }
            @Override public Value valueEntries() { reads++; return Value.EmptyCollection.INSTANCE; }
            @Override public Value fieldEntries() { reads++; return Value.EmptyCollection.INSTANCE; }
            @Override public Value size() { reads++; return Value.Missing.INSTANCE; }
            @Override public CollectionRuntime.Facts facts() { return facts; }
            @Override public Optional<Value> find(String name) { return Optional.empty(); }
            @Override public Map<String, Value> fields() { return Map.of(); }
        }

        TestProvider provider = new TestProvider(new CollectionRuntime.Facts(
                CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.FALSE,
                CollectionRuntime.Guarantee.UNKNOWN, CollectionRuntime.Guarantee.UNKNOWN,
                CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.TRUE));
        SourceSpan span = SourceSpan.point(new SourcePosition(8, 2, 4));
        LangException error = assertThrows(LangException.class, () ->
                CollectionRuntime.provider(provider, ReflectionContext.defining()).orElseThrow().facts().validate(span));
        assertEquals(Diagnostic.Codes.CONTRADICTORY_COLLECTION_GUARANTEES, error.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, error.diagnostic().phase());
        assertEquals(span, error.diagnostic().primarySpan());
        assertEquals(0, provider.reads);
        assertEquals(ValueKind.COLLECTION, ValueKind.of(provider));
        assertTrue(BuiltinContract.COLLECTION.accepts(provider));

        TestProvider valid = new TestProvider(new CollectionRuntime.Facts(
                CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.UNKNOWN,
                CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.UNKNOWN,
                CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.TRUE));
        assertSame(Value.EmptyCollection.INSTANCE,
                CollectionRuntime.provider(valid, ReflectionContext.defining()).orElseThrow().keys());
        assertEquals(1, valid.reads);
        Map<String, Value> reflected = ValueSemantics.reflectionFields(valid);
        assertSame(Value.Missing.INSTANCE, reflected.get("size"));
        assertEquals(new Value.Bool(false), reflected.get("sequential"));
        assertSame(Value.Missing.INSTANCE, reflected.get("ordered"));
        assertEquals(2, valid.reads);
    }

    @Test
    void collectionContractIncludesCurrentRepresentationsAndScopeIsRemoved() {
        assertEquals("true\ntrue\ntrue\ntrue\n", execute("""
                named = [^value = 1]
                (Collection) positional = [1 2]
                print Collection named
                print Collection positional
                print Collection dictEmpty
                print Sequence positional
                """));
        assertDiagnostic("print Scope", "Unknown name: Scope", 1, 7);
    }

    @Test
    void rejectsIncompatibleShapesAndRetainsFirstDuplicateEntry() {
        LangException mixed = assertThrows(LangException.class,
                () -> execute("value = [1 ^name = 2]"));
        assertEquals(Diagnostic.Codes.MIXED_COLLECTION_SHAPE, mixed.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, mixed.diagnostic().phase());
        assertEquals("1\n1\n", execute("""
                static = [^name = 1 ^name = 2]
                dynamic = [(field "name" 1) (field "name" 2)]
                print static.name
                print dynamic.name
                """));
    }

    @Test
    void unifiesStaticExportedDynamicAndUpdatedDictionaryFields() {
        assertEquals("true\ntrue\ntrue\nDictionary\nAda\nAda\ntrue\n[ \"age\" \"name\" ]\ntrue\ntrue\n",
                execute("""
                        exported =
                          ^name = "Ada"
                          ^age = 42
                        literal = [^age = 42 ^name = "Ada"]
                        fieldAlias = field
                        fields = [
                          fieldAlias "name" "Ada"
                          fieldAlias "age" 42
                        ]
                        updated = dictPut (dictPut dictEmpty "name" "Ada") "age" 42
                        print exported == literal
                        print literal == fields
                        print fields == updated
                        print type exported
                        print dictGet literal "name"
                        print updated.name
                        print dictHas exported "age"
                        print dictKeys updated
                        print (Dictionary String Any exported)
                        NamedCollection = Dictionary String
                        AnyNamedCollection = NamedCollection Any
                        (AnyNamedCollection) accepted = fields
                        print accepted == exported
                        """));
    }

    @Test
    void fieldCollectionsSupportContextualShapesMissingPartsAndGeneralKeys() {
        LangException mixed = assertThrows(LangException.class,
                () -> execute("makeField = field\nvalue = [(makeField \"name\" 1) 2]"));
        assertEquals(Diagnostic.Codes.MIXED_COLLECTION_SHAPE, mixed.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, mixed.diagnostic().phase());

        assertEquals("""
                true
                true
                [ 0 1 ]
                [ "name" 2 ]
                name
                2
                false
                [ 1 2 3 ]
                [ 3 1 2 ]
                ~
                true
                true
                [ 1 2 ]
                [
                  [ 1 ]
                  [ 2 ]
                ]
                1
                true
                """, execute("""
                item = field "name" 2
                pair = ["name" 2]
                print Field String Number item
                print Collection item
                print keys item
                print values item
                print (@item).key
                print (@item).value
                print Field pair
                keyless = [(field ~ 1) 2 (field ~ 3) (field ~ ~)]
                print keyless
                (Set Number) members = [3 1 3 (field 2 ~)]
                print keys members
                print values members
                print Set members
                (Dictionary String Any) stored = [(field "present" 1) (field "missing" ~)]
                print dictHas stored "missing"
                (Dictionary Number String) ordered = [(field 2 "b") (field 1 "a")]
                print keys ordered
                general = [(field [1] "a") (field [2] "b")]
                print keys general
                duplicate = [(field "same" 1) (field "same" 2)]
                print duplicate.same
                print field 1 2 == field 1 2
                """));

        assertDiagnostic("value = [(field 1 ~)]",
                "Fields without values require a Set or Dictionary contract", 1, 9);
        assertDiagnostic("(Dictionary Any Any) value = [(field 1 1) (field \"two\" 2)]",
                "Dictionary keys must have one homogeneous sortable type", 1, 30);
    }

    @Test
    void internalFieldAndCollectionSettlementPreservePersistentValues() {
        Value.Field field = new Value.Field(new Value.Str("key"), Value.Missing.INSTANCE);
        CollectionRuntime.Provider provider = CollectionRuntime.provider(field, ReflectionContext.defining()).orElseThrow();
        assertEquals(new Value.Str("key"), provider.getElement(new Value.Num(0)));
        assertSame(Value.Missing.INSTANCE, provider.getElement(new Value.Num(1)));
        assertSame(Value.Missing.INSTANCE, provider.getElement(new Value.Num(2)));

        assertEquals("[ 1 2 ]\n[ 1 2 3 ]\n", execute("""
                original = [1 2]
                updated = seqAdd original 3
                print original
                print updated
                """));
    }

    @Test
    void providesUnicodeCodePointTextPrimitives() {
        assertEquals("2\n🙂\na\n~\n42.5\n~\n42.5\n", execute("""
                text = "🙂a"
                print textSize text
                print textAt text 0
                print textSlice text 1 2
                print textAt text 2
                print textNumber "42.5"
                print textNumber "nope"
                print numberText 42.5
                """));
    }

    @Test
    void providesPersistentSequencesAndCanonicallyOrderedDictionaries() {
        assertEquals("0\n2\n1\n~\nfalse\ntrue\n~\n1\nfirst,missing\n", execute("""
                empty = seqEmpty
                values = seqAdd (seqAdd empty 1) ~
                print seqSize empty
                print seqSize values
                print seqGet values 0
                print seqGet values 5

                base = dictEmpty
                withFirst = dictPut base "first" 1
                complete = dictPut withFirst "missing" ~
                print dictHas base "first"
                print dictHas complete "missing"
                print dictGet complete "missing"
                print dictGet complete "first"
                print (@complete).ids
                """));
    }

    @Test
    void persistentCollectionsKeepOlderValuesAndDictionaryReplacementOrder() {
        assertEquals("[ 1 ]\n[ 1 2 ]\n[ \"first\" \"second\" ]\n22\n", execute("""
                first = seqAdd seqEmpty 1
                second = seqAdd first 2
                print first
                print second
                dictionary = dictPut (dictPut (dictPut dictEmpty "first" 1) "second" 2) "first" 22
                print dictKeys dictionary
                print dictGet dictionary "first"
                """));
    }

    @Test
    void collectionEqualityIsStructural() {
        assertEquals("true\ntrue\ntrue\ntrue\n", execute("""
                left = seqAdd seqEmpty 1
                right = seqAdd seqEmpty 1
                print left == right
                first = dictPut dictEmpty "value" left
                second = dictPut dictEmpty "value" right
                print first == second
                print (-0 == 0)
                print seqAdd seqEmpty (-0) == seqAdd seqEmpty 0
                """));

        for (String container : List.of(
                "seqAdd seqEmpty identity",
                "dictPut dictEmpty \"callable\" identity",
                "make identity")) {
            assertDiagnostic("""
                    identity value = value
                    make value =
                      ^nested = value
                    print %s == %s
                    """.formatted(container, container),
                    "Callable values cannot be compared for equality", 4, 7);
        }
    }

    @Test
    void revisedCollectionEqualityUsesShapeOrderAndIncrementalLazyDemand() {
        assertEquals("""
                true
                false
                true
                1
                90
                false
                """, execute("""
                (Sequence Number) broad = [1 2]
                (Sequence Natural) narrow = [1 2]
                print broad == narrow

                (Set Number) sourceSet = [1]
                (Dictionary String Number) sourceDictionary = [(field "value" 1)]
                emptySet = filter sourceSet (value -> false)
                emptyDictionary = filter sourceDictionary (value -> false)
                print emptySet == emptyDictionary
                print [] == emptySet

                (Output Number) traceLeft value =
                  print value
                  value
                (Output Number) traceRight value =
                  print (value * 10)
                  value
                left = map traceLeft [1 2]
                right = map traceRight [9 2]
                print left == right
                """));
    }

    @Test
    void containersPreserveIdentityContractsAndExplicitReadWriteBehavior() {
        assertEquals("""
                Container
                true
                true
                false
                true
                false
                1
                2
                2
                3
                3
                3
                4
                4
                ?
                5
                """, execute("""
                inferred = { 1 }
                alias = inferred
                explicit = { (Number) 10 }
                print type inferred
                print Container inferred
                print (Container Number inferred)
                print (Container Any inferred)
                print inferred == alias
                print inferred == explicit
                print inferred{}
                print put alias 2
                print inferred{}

                holder = [^cell = inferred]
                print put holder.cell 3
                print holder.cell{}

                (StateRead Number) read (Container Number) cell = cell{}
                (StateWrite Number) write (Container Number) cell (Number) value = put cell value
                print read inferred
                print write inferred 4
                print inferred{}

                nullable = { (Number?) ? }
                optional = { (Number~) ~ }
                print nullable{}
                print put optional 5
                """));
    }

    @Test
    void containerParameterizedMembershipPreservesInvariantNestedContentContracts() {
        assertEquals("true\nfalse\n[ 3 4 ]\nfalse\nfalse\n", execute("""
                holder = { (Sequence Number) [1 2] }
                print (Container (Sequence Number) holder)
                print (Container (Sequence Any) holder)
                print put holder [3 4]

                (Boolean) positive (Number) value = value > 0
                guarded = { (Number positive) 1 }
                both = { (Number Natural) 1 }
                print (Container Number guarded)
                print (Container Number both)
                """));
    }

    @Test
    void failedContainerWritesLeavePriorContentAndPureFunctionsRejectStateEffects() {
        LangException initial = expectDiagnostic("cell = { (Number) \"bad\" }",
                "Contract violation for container content", 1, 19);
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, initial.diagnostic().code());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        interpreter.execute(new Parser("cell = { (Number) 1 }").parseProgram());
        LangException failure = assertThrows(LangException.class,
                () -> interpreter.execute(new Parser("put cell \"bad\"").parseProgram()));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, failure.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, failure.diagnostic().phase());
        assertEquals(1, failure.span().start().line());
        assertEquals(10, failure.span().start().column());
        interpreter.execute(new Parser("print cell{}").parseProgram());
        assertEquals("1\n", bytes.toString(StandardCharsets.UTF_8));

        LangException read = assertThrows(LangException.class, () -> execute("""
                cell = { 1 }
                (pure Number) invalid (Container Number) value = value{}
                """));
        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, read.diagnostic().code());
        assertTrue(read.getMessage().contains("StateRead"));

        LangException write = assertThrows(LangException.class, () -> execute("""
                cell = { 1 }
                (pure Number) invalid (Container Number) value = put value 2
                """));
        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, write.diagnostic().code());
        assertTrue(write.getMessage().contains("StateWrite"));
    }

    @Test
    void containerEffectsPropagateThroughAliasesPartialsCompositionAndHigherOrderCalls() {
        assertEquals("""
                1
                2
                3
                [ 3 ]
                Container
                """, execute("""
                cell = { (Number) 1 }
                (StateRead Number) read (Container Number) value = value{}
                (StateWrite Number) store (Container Number) value (Number) next = put value next
                reader = read
                writer = store cell _
                composed = reader >> (value -> value + 1)
                print reader cell
                print composed cell
                print writer 3
                print map reader [cell]
                (pure Collection) inspect (Container Number) value = @value
                print (inspect cell).kind
                """));

        LangException higherOrder = assertThrows(LangException.class, () -> execute("""
                cell = { 1 }
                read value = value{}
                (pure Sequence Number) invalid = map read [cell]
                """));
        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, higherOrder.diagnostic().code());
        assertTrue(higherOrder.getMessage().contains("StateRead"));
    }

    @Test
    void containersShareThroughClosuresNestedCollectionsAndCallsWithoutDeepMutation() {
        assertEquals("""
                true
                true
                8
                8
                8
                9
                """, execute("""
                cell = { (Number) 7 }
                wrap value = [value]
                capture value = (ignored -> value)
                nested = [(wrap cell)]
                get = capture cell
                print (seqGet (seqGet nested 0) 0) == cell
                print get 0 == cell
                print put (get 0) 8
                print (seqGet (seqGet nested 0) 0){}
                print cell{}
                replacement = seqAdd (seqGet nested 0) { (Number) 9 }
                print (seqGet replacement 1){}
                """));
    }

    @Test
    void containerIdentityAndPersistentSurroundingsMatchOptimizationDisabledExecution() {
        String program = """
                cell = { (Number) 1 }
                first = [cell]
                second = seqAdd first { (Number) 9 }
                print first == second
                print (seqGet first 0) == cell
                print (seqGet second 0) == cell
                print put (seqGet first 0) 2
                print (seqGet second 0){}
                print (seqGet second 1){}
                """;
        ModeExecution enabled = execute(program, OwnershipTracker.Mode.ENABLED);
        ModeExecution disabled = execute(program, OwnershipTracker.Mode.DISABLED);
        assertEquals(disabled.output(), enabled.output());
        assertEquals("false\ntrue\ntrue\n2\n2\n9\n", enabled.output());
    }

    @Test
    void containerReadsAndWritesRejectNonContainersAtLocatedOperands() {
        LangException read = expectDiagnostic("value = 1\nprint value{}",
                "Expected Container, got: Number", 2, 7);
        assertEquals(Diagnostic.Codes.EXPECTED_CONTAINER, read.diagnostic().code());
        LangException write = expectDiagnostic("put 1 2",
                "Expected Container, got: Number", 1, 5);
        assertEquals(Diagnostic.Codes.EXPECTED_CONTAINER, write.diagnostic().code());
    }

}
