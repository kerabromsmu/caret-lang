package caretlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static caretlang.InterpreterTestSupport.*;
import caretlang.InterpreterTestSupport.ModeExecution;
import caretlang.InterpreterTestSupport.ModeFailure;

final class TransformInterpreterTest {
    @Test
    void mapTransformsSequencesInOrderThroughOrdinaryCallableForms() {
        assertEquals("[]\n[ 2 ]\n[ 2 4 6 ]\n[ 4 6 ]\n[ 4 8 ]\n[ ? ~ 3 ]\n", execute("""
                double value = value * 2
                add left right = left + right
                stringify value = numberText value
                print map double []
                print map double [1]
                print map double [1 2 3]
                print map (add 3) [1 3]
                print map (double >> double) [1 2]
                identity value = value
                print map identity [? ~ 3]
                """));
    }

    @Test
    void mapHandlesLargeAndNestedSequencesWithoutMutation() {
        String values = java.util.stream.IntStream.range(0, 10_000)
                .mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(" "));
        assertEquals("10000\n[\n  1\n  [ 2 ]\n]\n[\n  1\n  [ 2 ]\n]\n", execute("""
                identity value = value
                source = [%s]
                mapped = map identity source
                print seqSize mapped
                nested = [1 [2]]
                print nested
                print map identity nested
                """.formatted(values)));
    }

    @Test
    void mapRejectsInvalidInputsAndRetainsLocatedElementFailures() {
        LangException transform = expectDiagnostic("map 1 [2]", "exactly one argument", 1, 5);
        assertEquals(Diagnostic.Codes.INVALID_MAP_TRANSFORM, transform.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, transform.diagnostic().phase());
        assertDiagnostic("add left right = left\nmap add [1]", "exactly one argument", 2, 5);

        LangException element = expectDiagnostic("mapped = map numberText [1 \"bad\"]\nprint mapped",
                "Expected number", 1, 25);
        assertEquals(Diagnostic.Codes.EXPECTED_NUMBER, element.diagnostic().code());

        LangException effects = expectDiagnostic("(pure) mapper = map", "known effect upper bound", 1, 17);
        assertEquals(Diagnostic.Codes.UNKNOWN_CALL_EFFECTS, effects.diagnostic().code());
    }

    @Test
    void lazyMapEstablishesDemandedValuesOncePerResultAndKeepsSizePure() {
        assertEquals("""
                made
                2
                1
                10
                10
                2
                20
                3
                30
                3
                30
                """, execute("""
                (Output Number) traced (Number) value =
                  print value
                  value * 10
                mapped = map traced [1 2]
                alias = mapped
                print "made"
                print size mapped
                print mapped[0]
                print alias[0]
                print mapped[1]

                (Output Collection) make ignored = map traced [3]
                first = make 0
                second = make 0
                print first[0]
                print second[0]
                """));
    }

    @Test
    void ownershipOptimizationMatchesPersistentReferenceSemantics() {
        List<String> programs = List.of("""
                        print (seqAdd (seqAdd (seqEmpty) 1) 2)
                        """, """
                        source = [1]
                        updated = seqAdd source 2
                        print source
                        print updated
                        """, """
                        source = dictPut (dictEmpty) "a" 1
                        updated = dictPut source "b" 2
                        print source
                        print updated
                        print @source
                        """);

        for (String program : programs) {
            ModeExecution enabled = execute(program, OwnershipTracker.Mode.ENABLED);
            ModeExecution disabled = execute(program, OwnershipTracker.Mode.DISABLED);
            assertEquals(disabled.output(), enabled.output());
        }
        assertTrue(execute(programs.getFirst(), OwnershipTracker.Mode.ENABLED).reuseCount() > 0);
        assertEquals(0, execute(programs.getFirst(), OwnershipTracker.Mode.DISABLED).reuseCount());
        assertEquals(0, execute(programs.get(1), OwnershipTracker.Mode.ENABLED).reuseCount(),
                "a bound collection must conservatively use persistent allocation");
    }

    @Test
    void capturedAndReflectedCollectionsAreNeverTreatedAsUniqueStorage() {
        ModeExecution captured = execute("""
                source = [1]
                appendLater value = seqAdd source value
                print appendLater 2
                print source
                """, OwnershipTracker.Mode.ENABLED);
        ModeExecution reflected = execute("""
                source = [^a = 1]
                metadata = @source
                print (dictPut source "b" 2)
                print source
                """, OwnershipTracker.Mode.ENABLED);
        ModeExecution partial = execute("""
                source = [1]
                append = seqAdd source _
                print append 2
                print source
                """, OwnershipTracker.Mode.ENABLED);

        assertEquals(0, captured.reuseCount());
        assertEquals(0, reflected.reuseCount());
        assertEquals(0, partial.reuseCount());
    }

    @Test
    void ownershipOptimizationDoesNotChangeFailureOrRollbackBehavior() {
        ModeFailure enabled = executeFailure(OwnershipTracker.Mode.ENABLED);
        ModeFailure disabled = executeFailure(OwnershipTracker.Mode.DISABLED);

        assertEquals(disabled.output(), enabled.output());
        assertEquals(disabled.code(), enabled.code());
        assertEquals(disabled.line(), enabled.line());
        assertTrue(enabled.reuseCount() > 0);
    }

    @Test
    void lambdasShareOrdinaryCallableExecutionCaptureAndReflection() {
        assertEquals("""
                6
                7
                9
                42
                5
                8
                Function
                ~
                2
                12
                """, execute("""
                add = x y -> x + y
                print add 2 4

                makeAdder amount = x -> x + amount
                addThree = makeAdder 3
                print addThree 4

                nested = x -> y -> x + y
                print (nested 4) 5

                answer = -> 42
                print answer

                functions = [(x -> x + 1)]
                print (seqGet functions 0) 4

                holder = [^transform = (x -> x * 2)]
                print holder.transform 4

                print (@add).kind
                print (@add).id
                print (@add).remaining

                factory value =
                  create value = x -> x + value
                  create 2
                addTen = factory 10
                print addTen 10
                """));
    }

    @Test
    void lambdaParametersUseOrdinaryContractAndDuplicateDiagnostics() {
        assertEquals("4\n", execute("double = (Number) x -> x * 2\nprint double 2\n"));
        assertDiagnostic("bad = x x -> x\n", "Duplicate parameter: x", 1, 9);
        assertDiagnostic("double = (Number) x -> x * 2\nprint double \"no\"\n",
                "Contract violation for parameter x", 2, 14);
    }

    @Test
    void lambdaPartialsPreserveHoleOrderingCapturesAndReflection() {
        assertEquals("""
                13
                7
                7
                8
                10
                15
                1
                right
                Number
                """, execute("""
                add = left right -> left + right
                addTen = add 10
                print addTen 3

                before = add _ 5
                print before 2

                subtract = left right -> left - right
                reverse = subtract _2 _1
                print reverse 3 10

                duplicate = (left right -> left + right) _1 _1
                print duplicate 4

                factory amount = value extra -> value + extra + amount
                returned = factory 5
                fixed = returned _ 3
                print fixed 2

                capturedBase = 10
                captured = (left right -> left + right + capturedBase) _ 2
                print captured 3

                contracted = (Number) left (Number) right -> left + right
                partial = contracted 2
                print (@partial).remaining
                print (seqGet (@partial).signature.parameters 0).id
                print (seqGet (seqGet (@partial).signature.parameters 0).declared 0).id
                """));

        LangException mixed = assertThrows(LangException.class, () -> execute("""
                add = left right -> left + right
                invalid = add _ _1
                """));
        assertEquals(Diagnostic.Codes.MIXED_HOLE_STYLES, mixed.diagnostic().code());
    }

    @Test
    void lambdaSignaturesInferContractsEffectsAndGenericRelationships() {
        assertEquals("""
                6
                hello
                0
                1
                Output
                """, execute("""
                ([Number] -> Number) double = value -> value * 2
                ([_1] -> _1) same = value -> value

                noisy = value ->
                  print value
                  value

                print double 3
                print same "hello"
                print (seqSize (@double).signature.effects.upperBound)
                print (seqSize (@noisy).signature.effects.upperBound)
                print (seqGet (@noisy).signature.effects.upperBound 0).id
                """));

        LangException effectful = assertThrows(LangException.class, () -> execute("""
                ([Number] -> Number) bad = value ->
                  print value
                  value
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, effectful.diagnostic().code());

        LangException unknown = assertThrows(LangException.class, () -> execute("""
                make transform = value -> transform value
                ([Number] -> Number) bad = make print
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, unknown.diagnostic().code());
    }

    @Test
    void higherOrderSequenceOperationsPreserveOrderFoldsAndShortCircuit() {
        assertEquals("""
                [ 2 4 ]
                10
                10
                false
                true
                true
                true
                [ true ]
                true
                false
                true
                false
                """, execute("""
                numbers = [1 2 3 4]
                even value = value % 2 == 0
                print filter numbers even
                print fold numbers 0 (acc value -> acc + value)
                print fold [] 10 (acc value -> acc + value)
                print any [] (value -> true)
                print all [] (value -> false)
                print any numbers (value -> value == 3)
                print all numbers (value -> value > 0)

                mixed = [true ? ~ false]
                print filter mixed (value -> value)
                print any mixed (value -> value)
                print all mixed (value -> value)

                print any [true 0] (value -> value == true & true ! 1 / 0 > 0)
                print all [false 0] (value -> value == false & false ! 1 / 0 > 0)
                """));
    }

    @Test
    void higherOrderSequenceOperationsValidateCallbacksResultsAndElements() {
        LangException arity = assertThrows(LangException.class,
                () -> execute("filter [1] (left right -> true)"));
        assertEquals(Diagnostic.Codes.INVALID_COLLECTION_CALLBACK, arity.diagnostic().code());

        LangException result = assertThrows(LangException.class,
                () -> execute("any [1] (value -> \"yes\")"));
        assertEquals(Diagnostic.Codes.INVALID_PREDICATE_RESULT, result.diagnostic().code());

        LangException contract = assertThrows(LangException.class,
                () -> execute("selected = filter [1 ?] ((Number) value -> true)\nprint selected"));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, contract.diagnostic().code());

        assertEquals("1\n2\n[ 1 2 ]\n", execute("""
                (Output Boolean) emit value =
                  print value
                  true
                (Output) emitting values = filter values emit
                print emitting [1 2]
                """));
        LangException undeclared = assertThrows(LangException.class, () -> execute("""
                (Output Boolean) emit value =
                  print value
                  true
                invalid values = filter values emit
                """));
        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, undeclared.diagnostic().code());
    }

    @Test
    void lazyTransformsUseFieldsAcrossCollectionShapesAndRetainFirstKeys() {
        assertEquals("""
                built
                1
                2
                2
                3
                4
                [ 2 4 ]
                [
                  "b" = 2
                  "c" = 3
                ]
                [
                  "same" = 10
                ]
                5
                """, execute("""
                (Output Boolean) tracedEven value =
                  print value
                  value % 2 == 0
                selected = filter [1 2 3 4] tracedEven
                print "built"
                print selected[0]
                print selected

                keepLarge pair = pair[1] > 1
                print filter [^a = 1 ^b = 2 ^c = 3] keepLarge

                collide pair = field "same" (pair[1] * 10)
                print map collide [^a = 1 ^b = 2]

                sumField accumulator pair = accumulator + pair[1]
                print fold [^left = 2 ^right = 3] 0 sumField
                """));
    }

    @Test
    void zipPairsExactlyTwoSequencesAndSupportsOrdinaryCallableForms() {
        assertEquals("""
                []
                [
                  [ 1 "a" ]
                  [ 2 "b" ]
                ]
                false
                Collection
                [
                  [ 2 ]
                  [ 1 ]
                ]
                false
                [ "a" "b" ]
                [
                  [ 1 2 ]
                ]
                [
                  [ 4 3 ]
                ]
                2
                """, execute("""
                print zip [] []
                print zip [1 2] ["a" "b"]
                print Field (zip [1] [2])[0]

                general = zipWithKeys [[2] [1]] ["b" "a"]
                print type general
                print keys general
                candidate = zipWithKeys ["b" "a"] [2 1]
                print (Dictionary String Number candidate)
                (Dictionary String Number) sorted = zipWithKeys ["b" "a"] [2 1]
                print keys sorted

                pair = zip [1]
                print pair [2]
                swapped = zip _2 _1
                print swapped [3] [4]
                print (@zip).remaining
                """));
    }

    @Test
    void zipWithKeysKeepsAlignmentWithoutDemandingIgnoredDuplicateValues() {
        assertEquals("""
                10
                [
                  "same" = 10
                ]
                """, execute("""
                (Output Number) trace value =
                  print value
                  value
                lazyValues = map trace [10 20]
                paired = zipWithKeys ["same" "same"] lazyValues
                print paired
                """));

        assertEquals("""
                2
                1
                [ field 1 10 field 2 20 ]
                """, execute("""
                (Output) makeField value =
                  print value
                  field value (value * 10)
                source = map makeField [2 1]
                paired = zipWithKeys (keys source) (values source)
                print fields paired
                """));
    }

    @Test
    void zipRejectsUnequalLengthsInvalidShapesAndMixedDictionaryKeys() {
        LangException eager = expectDiagnostic("zip [1] []", "equal lengths", 1, 1);
        assertEquals(Diagnostic.Codes.ZIP_LENGTH_MISMATCH, eager.diagnostic().code());

        LangException lazy = assertThrows(LangException.class, () -> execute("""
                source = filter [1] (value -> true)
                paired = zip source []
                print paired
                """));
        assertEquals(Diagnostic.Codes.ZIP_LENGTH_MISMATCH, lazy.diagnostic().code());

        LangException shape = assertThrows(LangException.class,
                () -> execute("zip [1] [^value = 2]"));
        assertEquals(Diagnostic.Codes.EXPECTED_SEQUENCE, shape.diagnostic().code());

        LangException dictionary = assertThrows(LangException.class,
                () -> execute("(Dictionary Any Any) result = zipWithKeys [\"a\" 1] [1 2]"));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, dictionary.diagnostic().code());
    }

    @Test
    void higherOrderAliasesAndPrefixPartialsPreserveCallbackEffects() {
        assertEquals("1\n[ 1 ]\n2\n[ true ]\n3\n[ 3 ]\n", execute("""
                (Output Boolean) emit value =
                  print value
                  true
                select = filter
                (Output) throughAlias values = select values emit
                print throughAlias [1]

                mapped = map emit
                (Output) throughPartial values = mapped values
                print throughPartial [2]

                selected = filter _ emit
                (Output) throughHole values = selected values
                print throughHole [3]

                (Output) combine accumulator value =
                  print value
                  accumulator
                reduce = fold
                exists = any
                every = all
                transform = map
                (Output) throughFold values = reduce values 0 combine
                (Output) throughAny values = exists values emit
                (Output) throughAll values = every values emit
                (Output) throughMap values = transform emit values
                """));

        LangException alias = assertThrows(LangException.class, () -> execute("""
                (Output Boolean) emit value =
                  print value
                  true
                select = filter
                invalid values = select values emit
                """));
        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, alias.diagnostic().code());

        LangException partial = assertThrows(LangException.class, () -> execute("""
                (Output Boolean) emit value =
                  print value
                  true
                mapped = map emit
                invalid values = mapped values
                """));
        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, partial.diagnostic().code());

        LangException hole = assertThrows(LangException.class, () -> execute("""
                (Output Boolean) emit value =
                  print value
                  true
                selected = filter _ emit
                invalid values = selected values
                """));
        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, hole.diagnostic().code());
    }

    @Test
    void parameterizedContractAliasesWorkInsideArrowContracts() {
        assertEquals("1\n[] -> Sequence Number\n[] -> Sequence Number\n[] -> Sequence (Sequence Number)\n[] -> Field String Number\n",
                execute("""
                Seq = Sequence
                Pair = Field
                (Number) unary (Sequence Number) values = seqGet values 0
                accepts ([Seq Number] -> Number) transform = transform [1]
                print accepts unary

                SequenceResult = [] -> Seq Number
                GroupedResult = [] -> (Seq Number)
                NestedResult = [] -> Seq (Seq Number)
                FieldResult = [] -> Pair String Number
                print (@SequenceResult).id
                print (@GroupedResult).id
                print (@NestedResult).id
                print (@FieldResult).id
                """));

        LangException wrongArity = assertThrows(LangException.class, () -> execute("""
                Seq = Sequence
                binary left right = left
                accepts ([Seq Number] -> Number) transform = transform [1]
                accepts binary
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, wrongArity.diagnostic().code());
    }

    @Test
    void multilineLambdasRemainCompleteRightOperandsOfDollar() {
        assertEquals("""
                8
                9
                7
                [ 2 3 ]
                """, execute("""
                identity value = value
                functions = seqAdd [] $ (Number) value ->
                  value * 2
                print (seqGet functions 0) 4

                chained = identity $ seqAdd [] $ left right ->
                  left + right
                print (seqGet chained 0) 4 5

                nested = first -> second ->
                  first + second
                print (nested 3) 4

                print filter [1 2 3] $ value ->
                  value > 1
                """));
    }

}
