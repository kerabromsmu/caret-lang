package caretlang;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static caretlang.InterpreterTestSupport.*;

final class ContractInterpreterTest {
    @Test
    void constructsUnaryBaseAndMultiplyDerivedContracts() {
        assertEquals("false\nfalse\nAB\n[ \"Tag\" \"Numeric\" ]\n[ 1 \"two\" true ]\n", execute("""
                Tag = contract ~
                Numeric = contract Number
                AB = contract [Tag Numeric]
                print Tag "anything"
                print Numeric "not a number"
                print (@AB).id
                print (@AB).bases
                print [1 "two" true]
                """));
    }

    @Test
    void contractDerivationSupportsForwardDiamondsAndRejectsCycles() {
        assertEquals("true\ntrue\ntrue\n", execute("""
                Diamond = contract [Left Right Root]
                Right = contract Root
                Left = contract Root
                Root = contract ~

                (Diamond) value = 1
                print Diamond value
                print Left value
                print Root value
                """));

        LangException direct = assertThrows(LangException.class,
                () -> execute("Self = contract Self"));
        assertEquals(Diagnostic.Codes.CONTRACT_DERIVATION_CYCLE, direct.diagnostic().code());
        assertEquals(1, direct.diagnostic().primarySpan().start().line());
        assertFalse(direct.diagnostic().related().isEmpty());

        LangException indirect = assertThrows(LangException.class, () -> execute("""
                First = contract Second
                Second = contract Third
                Third = contract First
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_DERIVATION_CYCLE, indirect.diagnostic().code());
        assertEquals(3, indirect.diagnostic().primarySpan().start().line());
        assertEquals(3, indirect.diagnostic().related().size());
    }

    @Test
    void operatorsUseLanguageRenderingTruthAndStructuralEqualityPolicies() {
        assertEquals("value:7\n[ value:1 value:2 ]!\ntrue\ntrue\ntrue\n", execute("""
                (String) toString (Number) value = "value:" + numberText value
                print ("" + 7)
                print ([1 2] + "!")
                print (not ?)
                print (~ or true)
                print (? & (1 / 0) ! false) == false
                """));

        LangException nestedCallable = assertThrows(LangException.class,
                () -> execute("print [print] == [print]"));
        assertEquals(Diagnostic.Codes.CALLABLE_EQUALITY, nestedCallable.diagnostic().code());

        LangException zero = assertThrows(LangException.class, () -> execute("print 1 / 0"));
        assertEquals(Diagnostic.Codes.DIVISION_BY_ZERO, zero.diagnostic().code());
        assertEquals(1, zero.diagnostic().primarySpan().start().line());

        String huge = "9".repeat(200);
        assertEquals(new java.math.BigInteger(huge).multiply(new java.math.BigInteger(huge)) + "\n",
                execute("print " + huge + " * " + huge));
        LangException nonFinite = assertThrows(LangException.class,
                () -> execute("print " + "9".repeat(400) + " / 7"));
        assertEquals(Diagnostic.Codes.NON_FINITE_RESULT, nonFinite.diagnostic().code());
    }

    @Test
    void constructsAndEnforcesParameterizedSequenceContracts() {
        assertEquals("true\nfalse\ntrue\ntrue\nSequence Number\n[ \"Sequence\" ]\n[ \"Number\" ]\n", execute("""
                Numbers = Sequence Number
                Alias = Numbers
                SequenceConstructor = Sequence
                Nested = Sequence (Sequence Number)
                (Sequence Number) direct = [1 2 3]
                (Alias) aliased = []
                (SequenceConstructor Number) throughConstructorAlias = [4 5]
                (Nested) nested = [[1] [] [2 3]]
                print Numbers direct
                print Numbers [1 "two"]
                print Nested nested
                print Numbers == Alias
                print (@Numbers).id
                print (@Numbers).bases
                print (@Numbers).requirements
                """));

        LangException element = assertThrows(LangException.class,
                () -> execute("(Sequence Number) values = [1 \"two\"]"));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, element.diagnostic().code());

        LangException nested = assertThrows(LangException.class,
                () -> execute("(Sequence (Sequence Number)) values = [[1] [\"two\"]]"));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, nested.diagnostic().code());
    }

    @Test
    void contractConstructorsAreCurriedFirstClassFunctionsWithRawPredicateFallbacks() {
        assertEquals("true\nfalse\ntrue\ntrue\ntrue\nfalse\nfalse\n", execute("""
                (Boolean) positive value = Number value & value > 0
                SequenceConstructor = Sequence
                PositiveNumbers = SequenceConstructor (contract [Number positive])
                FieldConstructor = Field
                TextNumberField = FieldConstructor String Number
                DictionaryConstructor = Dictionary
                TextNumberDictionary = DictionaryConstructor String Number
                print PositiveNumbers [1 2 3]
                print PositiveNumbers [1 (0 - 2) 3]
                print TextNumberField (field "age" 42)
                print TextNumberDictionary [(field "age" 42)]
                print Sequence [1 "two"]
                print Sequence 1
                print Collection Number
                """));

        LangException outerRefinement = assertThrows(LangException.class, () -> execute("""
                (Boolean) positive value = Number value & value > 0
                (Sequence Number positive) values = [1 2 3]
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, outerRefinement.diagnostic().code());
    }

    @Test
    void parameterizedContractsHaveFreshIdentityAndComposeWithAbsenceModifiers() {
        assertEquals("false\ntrue\ntrue\ntrue\nfalse\n", execute("""
                First = Sequence Number
                Second = Sequence Number
                Alias = First
                MaybeNumbers = (Sequence Number)?~
                print First == Second
                print First == Alias
                print MaybeNumbers [1 2]
                print MaybeNumbers ?
                print MaybeNumbers "not a sequence"
                """));
    }

    @Test
    void rejectsInvalidParameterizedContractArgumentsAndCandidatesWithLocatedDiagnostics() {
        LangException argument = expectDiagnostic("""
                dynamic = false & Number ! 1
                (Sequence dynamic) values = []
                """, "Binding is not a contract: dynamic", 2, 11);
        assertEquals(Diagnostic.Codes.NOT_A_CONTRACT, argument.diagnostic().code());

        LangException candidate = expectDiagnostic("""
                Numbers = Sequence Number
                (Numbers) values = "not a sequence"
                """, "expected Sequence Number", 2, 20);
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, candidate.diagnostic().code());
    }

    @Test
    void acceptsUserDefinedContractsInClausesAndRejectsInvalidConstructorArguments() {
        assertEquals("3\n", execute("""
                Numeric = contract Number
                (Numeric) add (Numeric) left (Numeric) right = left + right
                print add 1 2
                """));

        LangException invalid = assertThrows(LangException.class,
                () -> execute("Bad = contract [Number 1]"));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, invalid.diagnostic().code());
    }

    @Test
    void appliesPurePredicateRefinementsInDerivedContractsAndDirectClauses() {
        assertEquals("3\ntrue\n", execute("""
                (Boolean) positive value = value > 0
                predicate = positive
                PositiveNumber = contract [Number predicate]
                (PositiveNumber) count = 3
                (Number positive) identity (Number positive) value = value
                print identity count
                print PositiveNumber count
                """));

        LangException derived = assertThrows(LangException.class, () -> execute("""
                positive value = value > 0
                PositiveNumber = contract [Number positive]
                (PositiveNumber) count = -1
                """));
        assertEquals(Diagnostic.Phase.RUNTIME, derived.diagnostic().phase());
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, derived.diagnostic().code());
        assertEquals(3, derived.diagnostic().primarySpan().start().line());
        assertEquals(26, derived.diagnostic().primarySpan().start().column());

        LangException direct = assertThrows(LangException.class, () -> execute("""
                positive value = value > 0
                (positive) count = 0
                """));
        assertEquals(Diagnostic.Phase.RUNTIME, direct.diagnostic().phase());
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, direct.diagnostic().code());
        assertEquals(2, direct.diagnostic().primarySpan().start().line());
        assertEquals(20, direct.diagnostic().primarySpan().start().column());

        LangException result = assertThrows(LangException.class, () -> execute("""
                positive value = value > 0
                (Number positive) invalidResult value = -1
                print invalidResult 1
                """));
        assertEquals(Diagnostic.Phase.RUNTIME, result.diagnostic().phase());
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, result.diagnostic().code());
        assertEquals(2, result.diagnostic().primarySpan().start().line());
        assertEquals(41, result.diagnostic().primarySpan().start().column());
    }

    @Test
    void lambdaRefinementsWorkInContractsClausesCapturesAndAliases() {
        assertEquals("3\n4\ntrue\ntrue\n", execute("""
                minimum = 0
                positive = (Number) value -> value > minimum
                alias = positive

                DirectPositive = contract [Number (value -> value > minimum)]
                AliasedPositive = contract [Number alias]
                (DirectPositive) direct = 3
                (AliasedPositive) aliased = 4
                (Number alias) keep (Number alias) value = value

                print keep direct
                print keep aliased
                print DirectPositive direct
                print AliasedPositive aliased
                """));

        LangException rejected = assertThrows(LangException.class, () -> execute("""
                positive = value -> value > 0
                (positive) count = 0
                """));
        assertEquals(Diagnostic.Phase.RUNTIME, rejected.diagnostic().phase());
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, rejected.diagnostic().code());
        assertEquals(2, rejected.diagnostic().primarySpan().start().line());
        assertEquals(20, rejected.diagnostic().primarySpan().start().column());
    }

    @Test
    void rejectsInvalidLambdaRefinementsBeforeProgramEffects() {
        record Invalid(String lambda, String reason) {}
        List<Invalid> invalid = List.of(
                new Invalid("-> true", "must take exactly one parameter"),
                new Invalid("left right -> left == right", "must take exactly one parameter"),
                new Invalid("value -> value + 1", "must guarantee a Boolean result"),
                new Invalid("value -> value > 0 & true ! ?", "must guarantee a Boolean result"),
                new Invalid("value ->\n  print value\n  value > 0", "has observable effects"),
                new Invalid("value -> dynamic value == true", "purity cannot be proved"));

        for (Invalid candidate : invalid) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Interpreter interpreter = new Interpreter(
                    new PrintStream(bytes, true, StandardCharsets.UTF_8));
            String source = "candidate = " + candidate.lambda() + "\n"
                    + "print \"must not happen\"\n"
                    + "(candidate) value = 1\n";

            LangException error = assertThrows(LangException.class,
                    () -> interpreter.execute(new Parser(source).parseProgram()), source);
            assertEquals(Diagnostic.Phase.SEMANTIC, error.diagnostic().phase(), source);
            assertEquals(Diagnostic.Codes.INVALID_REFINEMENT, error.diagnostic().code(), source);
            assertEquals(1, error.diagnostic().primarySpan().start().line(), source);
            assertEquals(13, error.diagnostic().primarySpan().start().column(), source);
            assertTrue(error.getMessage().contains(candidate.reason()), error::getMessage);
            assertEquals("", bytes.toString(StandardCharsets.UTF_8), source);
        }
    }

    @Test
    void retainedLambdaRefinementMetadataSurvivesLaterSubmissionsAndReflection() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        interpreter.execute(new Parser("""
                minimum = 0
                predicate = value -> value > minimum
                alias = predicate
                """).parseProgram());

        interpreter.execute(new Parser("""
                (alias) count = 2
                print count
                print (@alias).id
                print (seqGet (@alias).signature.result.guarantees 0).id
                print seqSize (@alias).signature.effects.upperBound
                """).parseProgram());

        assertEquals("2\n~\nBoolean\n0\n", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsCallablesThatCannotBeProvedValidAsRefinements() {
        LangException wrongArity = assertThrows(LangException.class, () -> execute("""
                same left right = left == right
                Invalid = contract same
                """));
        assertEquals(Diagnostic.Phase.SEMANTIC, wrongArity.diagnostic().phase());
        assertEquals(Diagnostic.Codes.INVALID_REFINEMENT, wrongArity.diagnostic().code());
        assertEquals(2, wrongArity.diagnostic().primarySpan().start().line());
        assertEquals(20, wrongArity.diagnostic().primarySpan().start().column());

        LangException effectful = assertThrows(LangException.class, () -> execute("""
                emitting value = print (value > 0)
                (emitting) count = 1
                """));
        assertEquals(Diagnostic.Phase.SEMANTIC, effectful.diagnostic().phase());
        assertEquals(Diagnostic.Codes.INVALID_REFINEMENT, effectful.diagnostic().code());
        assertEquals(1, effectful.diagnostic().primarySpan().start().line());
        assertEquals(1, effectful.diagnostic().primarySpan().start().column());
    }

    @Test
    void rejectsInvalidRefinementAliasesBeforeAnyEffects() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        LangException error = assertThrows(LangException.class, () -> interpreter.execute(new Parser("""
                invalid value = value + 1
                alias = invalid
                print "must not happen"
                unused (alias) value = value
                """).parseProgram()));
        assertEquals(Diagnostic.Phase.SEMANTIC, error.diagnostic().phase());
        assertEquals(Diagnostic.Codes.INVALID_REFINEMENT, error.diagnostic().code());
        assertEquals(1, error.diagnostic().primarySpan().start().line());
        assertEquals(1, error.diagnostic().primarySpan().start().column());
        assertEquals("", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void retainedCallableMetadataRejectsInvalidRefinementsInLaterSubmissions() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        interpreter.execute(new Parser("invalid value = value + 1").parseProgram());

        LangException error = assertThrows(LangException.class, () -> interpreter.execute(new Parser("""
                print "must not happen"
                unused (invalid) value = value
                """).parseProgram()));
        assertEquals(Diagnostic.Phase.SEMANTIC, error.diagnostic().phase());
        assertEquals(Diagnostic.Codes.INVALID_REFINEMENT, error.diagnostic().code());
        assertEquals(2, error.diagnostic().primarySpan().start().line());
        assertEquals(9, error.diagnostic().primarySpan().start().column());
        assertEquals("", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void lexicalPrintShadowUsesOrdinaryApplication() {
        assertEquals("3\n", execute("""
                run value =
                  print left right = left + right
                  builtin = @print
                  sum = print 1 2
                  ^result = sum
                value = run ~
                print value.result
                """));
    }

    @Test
    void nominalAttributionIsTransparentToExistingPrimitiveOperations() {
        assertEquals("second\ntrue\nvalue\n", execute("""
                Index = contract Number
                Key = contract String
                Flag = contract Boolean
                (Index) index = 1
                (Key) key = "name"
                (Flag) condition = true
                values = ["first" "second"]
                dictionary = dictPut dictEmpty key "value"
                print seqGet values index
                print dictHas dictionary key
                print condition & dictGet dictionary key ! "wrong"
                """));
    }

    @Test
    void evaluatesUnambiguousExpressionsInsideCollectionLiterals() {
        assertEquals("[\n  7\n  5\n  \"yes\"\n  [ \"a\" \"b\" ]\n]\n", execute("""
                add left right = left + right
                print [(1 + 2 * 3) (add 2 3) (true & "yes" ! "no") ["a" "b"]]
                """));
    }

    @Test
    void evaluatesLowPrecedenceApplicationThroughTheOrdinaryCallPath() {
        assertEquals("7\n7\n5\ntrue\n", execute("""
                add left right = left + right
                identity value = value
                apply function value = function value
                print $ add 1 $ 2 * 3
                print (add 1 (2 * 3))
                print $ identity $ add 2 3
                addOne = add _ $ 1
                print addOne 4 == 5
                """));
    }

    @Test
    void providesBuiltInContractPredicatesAndReflection() {
        assertEquals("true\nfalse\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nContract\nNumber\n", execute("""
                identity value = value
                make =
                  ^value = 1
                print Number 1
                print Number "one"
                print String "one"
                print Boolean true
                print Null ?
                print Missing ~
                print Function identity
                print Function (@identity:)
                print Collection make
                print Sequence seqEmpty
                print Dictionary dictEmpty
                print Any Number
                print type Number
                print (@Number).id
                """));
    }

    @Test
    void enforcesBindingAndParameterContractsAtTheirBoundaries() {
        assertEquals("3\n", execute("""
                (Number) initial = 1
                add (Number) left (Number) right = left + right
                addOne = add 1
                print addOne 2
                """));

        LangException binding = assertThrows(LangException.class,
                () -> execute("(Number) value = \"wrong\""));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, binding.diagnostic().code());
        assertEquals(18, binding.span().start().column());
        assertEquals(Diagnostic.Phase.SEMANTIC, binding.diagnostic().phase());

        LangException partial = assertThrows(LangException.class,
                () -> execute("add (Number) left right = left\npartial = add \"wrong\""));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, partial.diagnostic().code());
        assertEquals(15, partial.span().start().column());
    }

    @Test
    void nullableAndOptionalContractsPreserveNullAndMissingAsDistinctStates() {
        assertEquals("true\ntrue\ntrue\nfalse\nfalse\nNumber?~\n[ \"Number\" ]\n", execute("""
                (Number?) nullable = ?
                (Number~) optional = ~
                (Number?~) either = ~
                accepts = Number?~
                print accepts ?
                print accepts ~
                print accepts 1
                print Number? "wrong"
                print Number~ ?
                print (@accepts).id
                print (@accepts).bases
                """));

        LangException nullableRejectsMissing = assertThrows(LangException.class,
                () -> execute("(Number?) value = ~"));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS,
                nullableRejectsMissing.diagnostic().code());
        LangException optionalRejectsNull = assertThrows(LangException.class,
                () -> execute("(Number~) value = ?"));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS,
                optionalRejectsNull.diagnostic().code());
    }

    @Test
    void modifiedContractsAreFirstClassNormalizedAndIdentityStable() {
        assertEquals("true\ntrue\ntrue\nfalse\ntrue\n[ \"Number\" ]\n", execute("""
                First = contract Number
                Second = contract Number
                FirstNullable = First?
                FirstNullableAlias = First?
                print FirstNullable == FirstNullableAlias
                print Null? == Null
                print Any?~ == Any
                print First? == Second?
                print (Number?)~ == Number?~
                print (@((Number?)~)).bases
                """));

        LangException error = assertThrows(LangException.class, () -> execute("value = 1?"));
        assertEquals(Diagnostic.Phase.SEMANTIC, error.diagnostic().phase());
        assertEquals(Diagnostic.Codes.NOT_A_CONTRACT, error.diagnostic().code());
    }

    @Test
    void modifiedNominalContractsPreserveAndAcquireMembership() {
        assertEquals("true\ntrue\ntrue\ntrue\n", execute("""
                Numeric = contract Number
                (Numeric) attributed = 1
                print Numeric? attributed

                (Numeric?) acquired = 2
                print Numeric acquired

                MaybeNumeric = Numeric?
                (MaybeNumeric) aliased = 3
                print Numeric aliased

                Derived = contract Numeric?
                (Derived) derived = 4
                print Numeric derived
                """));
    }

    @Test
    void modifiedClausesValidateRequirementsBeforeAcceptingAbsence() {
        LangException error = assertThrows(LangException.class, () -> execute("""
                identity value = value
                notContract = identity 1
                (notContract?) accepted = ?
                """));
        assertEquals(Diagnostic.Phase.RUNTIME, error.diagnostic().phase());
        assertEquals(Diagnostic.Codes.NOT_A_CONTRACT, error.diagnostic().code());
        assertTrue(error.getMessage().contains("Binding is not a contract: notContract"));
    }

    @Test
    void dynamicModifierFailuresUseRuntimeDiagnosticsWithoutLeakingAstText() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        LangException error = assertThrows(LangException.class, () -> interpreter.execute(new Parser("""
                print "effect happened"
                identity value = value
                notContract = identity 1
                modified = notContract?
                """).parseProgram()));
        assertEquals("effect happened\n", bytes.toString(StandardCharsets.UTF_8));
        assertEquals(Diagnostic.Phase.RUNTIME, error.diagnostic().phase());
        assertEquals(Diagnostic.Codes.NOT_A_CONTRACT, error.diagnostic().code());
        assertTrue(error.getMessage().contains("Binding is not a contract: notContract"));
        assertFalse(error.getMessage().contains("Name["));
    }

    @Test
    void modifiedRefinementClausesDoNotInvokePredicatesForAdmittedAbsence() {
        assertEquals("?\n~\n2\n", execute("""
                positive value = value > 0
                keep (positive?) value = value
                maybe (positive~) value = value
                print keep ?
                print maybe ~
                print keep 2
                """));
    }

    @Test
    void contractedFunctionsReturnCallableValuesWithoutRetainingParameterValidation() {
        assertEquals("true\n5\n", execute("""
                returnContract (Any) value = Number
                returnAdd (Any) value = + _ value
                predicate = returnContract 1
                addTwo = returnAdd 2
                print predicate 2
                print addTwo 3
                """));
    }

    @Test
    void nestedDeclarationsDoNotChangeUnrelatedPrefixOrInfixInterpretation() {
        assertEquals("3\n", execute("""
                maker =
                  one a b = a
                  0

                one = 1
                add a b = a + b
                two = 2
                print one add two
                """));
    }

}
