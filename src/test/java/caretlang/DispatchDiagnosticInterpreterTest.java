package caretlang;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static caretlang.InterpreterTestSupport.*;

final class DispatchDiagnosticInterpreterTest {
    @Test
    void evaluatesGroupedMultilineCallsAndLookups() {
        assertEquals("6\n42\n", execute("""
                add a b = a + b
                result = (
                  add
                    1
                    (add 2 3)
                )
                print result

                make =
                  ^answer = 42
                scope = make
                print scope[
                  "answer"
                ]~
                """));
    }

    @Test
    void evaluatesUngroupedMultilineCalls() {
        assertEquals("7\n9\n3\n6\n", execute("""
                add a b = a + b
                multiply a b = a * b
                result = add
                  1
                  multiply
                    2
                    3
                print result
                print add
                  4
                  5
                conditional = true & add
                  1
                  2
                print conditional
                infix = 1 + add
                  2
                  3
                print infix
                """));
    }

    @Test
    void testAssertionsCollectFailuresAndReturnMissing() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        TestReporter reporter = new TestReporter(output);
        Interpreter interpreter = new Interpreter(output, reporter);

        interpreter.execute(new Parser("""
                print assert "true condition" true
                assert "false condition" false
                assertEqual "null differs from missing" ? ~
                assertEqual "structural sequence equality" (seqAdd seqEmpty 1) (seqAdd seqEmpty 1)
                """).parseProgram());
        assertFalse(reporter.finish());

        assertEquals("""
                PASS: true condition
                ~
                FAIL: false condition (Line 2, column 1)
                  expected: true
                  actual: false
                FAIL: null differs from missing (Line 3, column 1)
                  expected: ~
                  actual: ?
                PASS: structural sequence equality
                Summary: 4 tests, 2 passed, 2 failed
                """, bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void testAssertionsValidateTheirArgumentsWithLocatedErrors() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TestReporter reporter = new TestReporter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        Interpreter interpreter = new Interpreter(
                new PrintStream(bytes, true, StandardCharsets.UTF_8), reporter);

        LangException condition = assertThrows(LangException.class, () -> interpreter.execute(
                new Parser("assert \"boolean required\" 1").parseProgram()));
        assertEquals(1, condition.span().start().line());
        assertEquals(27, condition.span().start().column());
        assertTrue(condition.getMessage().contains("Assertion condition must be Boolean"));
    }

    @Test
    void contractEqualityUsesDescriptorIdentityNotEquivalentRequirements() {
        assertEquals("true\nfalse\ntrue\nfalse\n", execute("""
                First = contract ~
                Second = contract ~
                Alias = First
                BaseA = contract ~
                BaseB = contract ~
                SameRequirementsOne = contract [BaseA BaseB]
                SameRequirementsTwo = contract [BaseA BaseB]
                print First == First
                print First == Second
                print First == Alias
                print SameRequirementsOne == SameRequirementsTwo
                """));
    }

    @Test
    void invalidRefinementsAreRejectedBeforeProgramEffects() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        LangException error = assertThrows(LangException.class, () -> interpreter.execute(new Parser("""
                print "must not happen"
                invalid value = value + 1
                unused (invalid) value = value
                """).parseProgram()));
        assertEquals(Diagnostic.Phase.SEMANTIC, error.diagnostic().phase());
        assertEquals(Diagnostic.Codes.INVALID_REFINEMENT, error.diagnostic().code());
        assertEquals("", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void contractReflectionIncludesLanguageOwnedRequirementNames() {
        assertEquals("[ \"positive\" ]\n", execute("""
                positive value = value > 0
                Positive = contract positive
                print (@Positive).requirements
                """));
    }

    @Test
    void dispatchesClosedOverloadSetsToTheUniqueMostSpecificVariant() {
        assertEquals("number\ntext\nfallback\n", execute("""
                describe (Any) value = "fallback"
                describe (Number) value = "number"
                describe (String) value = "text"

                print (describe 42)
                print (describe "hello")
                print (describe true)
                """));
    }

    @Test
    void overloadApplicabilityCachesSharedRefinementsPerArgumentPosition() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(output, true, StandardCharsets.UTF_8));
        List<Value> checks = new ArrayList<>();
        // The host probe records test instrumentation only; Caret observes a pure Boolean predicate.
        interpreter.defineEmbeddingCallable("probe", 1, arguments -> {
            Value value = arguments.getFirst();
            checks.add(value);
            return new Value.Bool(!(value instanceof Value.Bool));
        }, List.of());
        interpreter.execute(new Parser("""
                (Boolean) accepted value = probe value
                choose (accepted) first (Number) second = "number"
                choose (accepted) first (String) second = "string"
                choose (Any) first (Any) second = "fallback"
                print choose 1 2
                print choose 1 "text"
                print choose true 2

                pair (accepted) first (accepted Number) second = "number-pair"
                pair (accepted) first (accepted String) second = "string-pair"
                print pair 1 1
                """).parseProgram());
        assertEquals("number\nstring\nfallback\nnumber-pair\n", output.toString(StandardCharsets.UTF_8));
        assertEquals(List.of(new Value.Num(1), new Value.Num(1), new Value.Bool(true),
                new Value.Num(1), new Value.Num(1)), checks);
    }

    @Test
    void overloadApplicabilityObservesNominalMembershipWithoutAcquiringIt() {
        assertEquals("fallback\nnominal\nchild\n", execute("""
                Base = contract Number
                Child = contract Base

                classify (Any) value = "fallback"
                classify (Base) value = "nominal"
                classify (Child) value = "child"

                (Base) taggedBase = 1
                (Child) taggedChild = 1
                print (classify 1)
                print (classify taggedBase)
                print (classify taggedChild)
                """));
    }

    @Test
    void overloadsNarrowThroughPrefixApplicationAndReportDistinctFailures() {
        assertEquals("number\n", execute("""
                combine (Any) left (Any) right = "fallback"
                combine (Number) left (Number) right = "number"
                withNumber = combine 1
                print (withNumber 2)
                """));

        LangException noMatch = expectDiagnostic("""
                select (String) value = value
                select (Boolean) value = value
                print (select 1)
                """, "No applicable overload: select", 3, 8);
        assertEquals(Diagnostic.Codes.NO_APPLICABLE_OVERLOAD, noMatch.diagnostic().code());
        assertFalse(noMatch.diagnostic().related().isEmpty());

        LangException ambiguous = expectDiagnostic("""
                choose (Number) left (Any) right = "left"
                choose (Any) left (Number) right = "right"
                print (choose 1 2)
                """, "Ambiguous overload: choose", 3, 8);
        assertEquals(Diagnostic.Codes.AMBIGUOUS_OVERLOAD, ambiguous.diagnostic().code());
        assertEquals(2, ambiguous.diagnostic().related().size());
    }

    @Test
    void overloadHolePartialsNarrowSparsePositionsAndRemainReusable() {
        assertEquals("number-text\nnumber-text\ntext-number\n", execute("""
                route (Number) left (String) right = "number-text"
                route (String) left (Number) right = "text-number"

                numberFirst = route (_ + 0) "fixed"
                textFirst = route _ 1
                print (numberFirst 1)
                print (numberFirst 2)
                print (textFirst "fixed")
                """));

        LangException eliminated = expectDiagnostic("""
                route (Number) left (String) right = "number-text"
                route (String) left (Number) right = "text-number"
                impossible = route (_ + 0) true
                """, "No applicable overload: route", 3, 28);
        assertEquals(Diagnostic.Codes.NO_APPLICABLE_OVERLOAD, eliminated.diagnostic().code());

        LangException supplied = expectDiagnostic("""
                route (Number) left (String) right = "number-text"
                route (String) left (Number) right = "text-number"
                reordered = route _2 _1
                reordered true
                """, "No applicable overload: route", 4, 11);
        assertEquals(Diagnostic.Codes.NO_APPLICABLE_OVERLOAD, supplied.diagnostic().code());
    }

    @Test
    void overloadsSupportParameterizedAbsenceRefinementAndInfixRequirements() {
        assertEquals("numbers\ntexts\nfallback\nmaybe-number\nmaybe-number\nfallback\npositive\nfallback\nmaybe-positive\n3\nab\n", execute("""
                kind (Any) value = "fallback"
                kind (Sequence Number) value = "numbers"
                kind (Sequence String) value = "texts"
                print (kind [1 2])
                print (kind ["a" "b"])
                print (kind [true])

                maybe (Any) value = "fallback"
                maybe (Number?) value = "maybe-number"
                print (maybe ?)
                print (maybe 1)
                print (maybe ~)

                positive value = value > 0
                sign (Any) value = "fallback"
                sign (positive) value = "positive"
                print (sign 1)
                print (sign (-1))

                maybeSign (Any) value = "fallback"
                maybeSign (positive?) value = "maybe-positive"
                print (maybeSign ?)

                merge (Number) left (Number) right = left + right
                merge (String) left (String) right = left + right
                print (1 merge 2)
                print ("a" merge "b")
                """));
    }

    @Test
    void overloadSpecificityUsesNormalizedStaticContractDomains() {
        assertEquals("both\nleft\nfallback\n", execute("""
                Root = contract ~
                Left = contract Root
                Right = contract Root
                Both = contract [Left Right Root]

                select (Any) value = "fallback"
                select (Left) value = "left"
                select (Both) value = "both"

                (Both) both = 1
                (Left) left = 1
                print select both
                print select left
                print select 1
                """));

        LangException redundant = expectDiagnostic("""
                Root = contract ~
                Child = contract Root
                choose (Child Root) value = value
                choose (Child) value = value
                """, "Duplicate definition: choose", 4, 1);
        assertEquals(Diagnostic.Codes.DUPLICATE_DEFINITION, redundant.diagnostic().code());

        LangException anyFallback = expectDiagnostic("""
                choose value = value
                choose (Any) value = value
                """, "Duplicate definition: choose", 2, 1);
        assertEquals(Diagnostic.Codes.DUPLICATE_DEFINITION, anyFallback.diagnostic().code());
    }

    @Test
    void collectionLiteralsOwnReifiableStructuralHoles() {
        assertEquals("capture\n[ \"capture\" 1 ]\n[ \"capture\" 3 ]\n[ 2 1 1 ]\n[\n  [ 4 ]\n  5\n]\n[\n  \"fixed\" = 7\n  \"name\" = 6\n]\n1\nNumber\ntrue\n[ 8 ]\n", execute("""
                identity value = value
                constructor = [
                  print "capture"
                  _
                ]
                print constructor 1
                print constructor 3

                repeated = [_2 _1 _1]
                print repeated 1 2

                nested = [[_] _]
                print nested 4 5

                named = [^name = _ ^fixed = 7]
                print named 6

                numeric = [(Number) _]
                print @numeric.remaining
                print (seqGet (seqGet @numeric.signature.parameters 0).requirements 0).id

                Tagged = contract Number
                taggedConstructor = [(Tagged) _]
                tagged = taggedConstructor 7
                print Tagged (seqGet tagged 0)

                passedThrough = identity [_]
                print passedThrough 8
                """));

        LangException violation = assertThrows(LangException.class,
                () -> execute("numeric = [(Number) _]\nnumeric \"wrong\""));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, violation.diagnostic().code());

        LangException mixed = assertThrows(LangException.class,
                () -> execute("invalid = [_ _1]"));
        assertEquals(Diagnostic.Codes.MIXED_HOLE_STYLES, mixed.diagnostic().code());
    }

    @Test
    void structuralTemplatesAreOrdinaryExactCollectionContracts() {
        assertEquals("true\nfalse\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\ntrue\ntrue\npoint\ncollection\ntrue\npositional\n2\nhole\nname\n~\n", execute("""
                Point = template [(Number) _ (Number) _]
                PointAlias = Point
                print Point [1 2]
                print Point [1 "two"]
                print Point [1 2 3]

                Diagonal = template [_1 _1]
                print Diagonal [3 3]
                print Diagonal [3 4]

                Named = template [^name = (String) _ ^active = true]
                print Named [^name = "Caret" ^active = true]
                print Named [^name = "Caret" ^active = false]

                Fixed = template [1 [2 3]]
                print Fixed [1 [2 3]]
                print PointAlias [4 5]

                dynamicName = "score"
                Dynamic = template [
                  field dynamicName (Number) _
                ]
                print Dynamic [^score = 9]

                describe (Collection) value = "collection"
                describe (Point) value = "point"
                print describe [6 7]
                print describe [6 "seven"]

                NullablePoint = Point?
                print NullablePoint ?
                print (@Point).shape
                print (@Point).size
                print (seqGet (@Point).elements 0).constraint
                print (seqGet (@Named).elements 0).id
                print (seqGet (@Named).elements 0).name~
                """));

        LangException opaque = assertThrows(LangException.class,
                () -> execute("""
                        invalid = template [
                          numberText _
                        ]
                        """));
        assertEquals(Diagnostic.Codes.TEMPLATE_INVALID_CONSTRUCTOR, opaque.diagnostic().code());

        LangException callableFixed = assertThrows(LangException.class,
                () -> execute("invalid = template [@print:]"));
        assertEquals(Diagnostic.Codes.TEMPLATE_NONCOMPARABLE_FIXED_VALUE,
                callableFixed.diagnostic().code());

        LangException dynamicKey = assertThrows(LangException.class, () -> execute("""
                invalid = template [
                  field 1 _
                ]
                """));
        assertEquals(Diagnostic.Codes.INVALID_DYNAMIC_FIELD_NAME, dynamicKey.diagnostic().code());
    }

    @Test
    void explicitNullAndMissingVariantsOutrankModifiedContractAlternatives() {
        assertEquals("null\nnullable\nmissing\noptional\nnull\nmissing\n", execute("""
                nullable (Number?) value = "nullable"
                nullable (Null) value = "null"
                print (nullable ?)
                print (nullable 1)

                optional (Number~) value = "optional"
                optional (Missing) value = "missing"
                print (optional ~)
                print (optional 1)

                either (Number?~) value = "number"
                either (Null) value = "null"
                either (Missing) value = "missing"
                print (either ?)
                print (either ~)
                """));
    }

    @Test
    void rejectsInvalidOverloadDeclarationsBeforeProgramEffects() {
        ByteArrayOutputStream arityBytes = new ByteArrayOutputStream();
        Interpreter arityInterpreter = new Interpreter(new PrintStream(arityBytes, true, StandardCharsets.UTF_8));
        LangException arity = assertThrows(LangException.class, () -> arityInterpreter.execute(new Parser("""
                print "must not happen"
                action (Number) value = value
                action (Number) left (Number) right = left
                """).parseProgram()));
        assertEquals(Diagnostic.Codes.INCONSISTENT_OVERLOAD_ARITY, arity.diagnostic().code());
        assertEquals(3, arity.span().start().line());
        assertEquals(1, arity.diagnostic().related().size());
        assertEquals("", arityBytes.toString(StandardCharsets.UTF_8));

        LangException duplicate = expectDiagnostic("""
                action (Number Any Number) value = value
                action (Number) value = value
                """, "Duplicate definition: action", 2, 1);
        assertEquals(Diagnostic.Codes.DUPLICATE_DEFINITION, duplicate.diagnostic().code());
        assertEquals(1, duplicate.diagnostic().related().size());

        LangException aliasDuplicate = expectDiagnostic("""
                Base = contract Number
                Alias = (Base)
                NestedAlias = ((Alias))
                action (Base) value = value
                action (NestedAlias) value = value
                """, "Duplicate definition: action", 5, 1);
        assertEquals(Diagnostic.Codes.DUPLICATE_DEFINITION, aliasDuplicate.diagnostic().code());
    }

    @Test
    void standardToStringIsExtensibleAndDispatchesRecursivelyInsideCollections() {
        assertEquals("plain\nnumber:7\n[ number:1 number:2 ]\n[\n  special\n]\n", execute("""
                (String) toString (Number) value = "number:" + numberText value
                Special = contract Dictionary
                (Special) special = [^value = 1]
                (String) toString (Special) value = "special"

                print toString "plain"
                print toString 7
                print toString [1 2]
                print toString [special]
                """));
    }

    @Test
    void standardLibraryCallablesCanBeExtendedByContractSpecificVariants() {
        assertEquals("custom\n", execute("""
                (String) numberText (String) value = "custom"
                print numberText "anything"
                """));
    }

    @Test
    void toStringRejectsUnsupportedCallablesAndNonStringSpecializationResults() {
        LangException callable = expectDiagnostic("print toString print", "Callable values do not have", 1, 7);
        assertSame(DiagnosticCatalog.CALLABLE_RENDERING, callable.catalogEntry());
        assertEquals(Diagnostic.Phase.RUNTIME, callable.diagnostic().phase());
        assertEquals(Diagnostic.Codes.CALLABLE_RENDERING, callable.diagnostic().code());
        assertEquals("Line 1, column 7: Callable values do not have a standard textual representation",
                callable.getMessage());
        LangException result = expectDiagnostic("""
                (String) toString (Number) value = 1
                print toString 2
                """, "String", 1, 1);
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, result.diagnostic().code());
    }

    @Test
    void logicalIndentationMappingsExecuteLikeOrdinaryIndentedBlocks() {
        assertEquals("3\n", execute("main = \\\\\nfirst = 1\n^result = first + 2\n\\*\nprint main.result\n"));
    }

}
