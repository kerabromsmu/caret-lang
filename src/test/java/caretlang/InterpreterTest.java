package caretlang;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class InterpreterTest {
    @Test
    void constructsUnaryBaseAndMultiplyDerivedContracts() {
        assertEquals("false\nfalse\nAB\n[Tag, Numeric]\n[1, two, true]\n", execute("""
                Tag = contract ~
                Numeric = contract Number
                AB = contract [Tag Numeric]
                print Tag "anything"
                print Numeric "not a number"
                print (@AB).name
                print (@AB).bases
                print [1 "two" true]
                """));
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
        assertEquals("[7, 5, yes, [a, b]]\n", execute("""
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
                print Function (@identity)
                print Scope make
                print Sequence seqEmpty
                print Dictionary dictEmpty
                print Any Number
                print type Number
                print (@Number).name
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
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, binding.diagnostic().code());
        assertEquals(18, binding.span().start().column());
        assertEquals(1, binding.diagnostic().related().size());

        LangException partial = assertThrows(LangException.class,
                () -> execute("add (Number) left right = left\npartial = add \"wrong\""));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, partial.diagnostic().code());
        assertEquals(15, partial.span().start().column());
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

    @Test
    void characterizesCoreLanguageBehavior() {
        String source = """
                add a b = a + b
                between low value high = value >= low and value <= high

                pair a b =
                  hidden = "private"
                  ^first = a
                  ^second = b

                print (add 2 3)
                print (true & "yes" ! unknown)
                print (false & unknown)
                inside = between 0 _ 10
                print (inside 7)
                value = pair ? ~
                print value.first
                print value.second
                print value.absent~
                print value["first"]~
                print (@value).names
                """;

        assertEquals("5\nyes\n~\ntrue\n?\n~\n~\n?\nfirst,second\n", execute(source));
    }

    @Test
    void callsNamedBinaryFunctionsInFixedPrecedenceInfixForm() {
        assertEquals("9\n10\n123\n7\n8\n", execute("""
                combine left right = left * 10 + right
                add left right = left + right
                left = 4
                operation = add
                print 2 add 3 + 4
                print 2 add 3 < 10 & 10 ! 0
                print 1 combine 2 combine 3
                print left operation 3
                addFive = 5 add _
                print addFive 3
                """));
    }

    @Test
    void resolvesCallableParametersAsPrefixOrInfixFromRuntimeArity() {
        assertEquals("5\n5\n", execute("""
                add left right = left + right
                applyInfix left operation right = left operation right
                applyPrefix operation left right = operation left right
                print applyInfix 2 add 3
                print applyPrefix add 2 3
                """));
    }

    @Test
    void composesCallablesWithArityPartialsChainsAndReflection() {
        assertEquals("10\n15\n5\nFunction\n2\n", execute("""
                add left right = left + right
                double value = value * 2
                increment value = value + 1
                returnAdd ignored = add

                addThenDouble = add >> double
                print addThenDouble 2 3
                addTenThenText = add _ 10 >> numberText
                print addTenThenText 5
                pipeline = double >> increment >> numberText
                print pipeline 2
                callableResult = returnAdd >> type
                print callableResult 0
                print (@addThenDouble).remaining
                """));
    }

    @Test
    void compositionRejectsInvalidOperandsWithLocatedDiagnostics() {
        LangException left = assertThrows(LangException.class, () -> execute("""
                identity value = value
                value = 1
                pipeline = value >> identity
                """));
        assertEquals(Diagnostic.Codes.INVALID_COMPOSITION_LEFT, left.diagnostic().code());
        assertEquals(12, left.span().start().column());

        LangException right = assertThrows(LangException.class, () -> execute("""
                identity value = value
                add left right = left + right
                pipeline = identity >> add
                """));
        assertEquals(Diagnostic.Codes.INVALID_COMPOSITION_RIGHT, right.diagnostic().code());
        assertEquals(24, right.span().start().column());

        LangException nullary = assertThrows(LangException.class, () -> execute("""
                zero = 0
                identity value = value
                pipeline = zero >> identity
                """));
        assertEquals(Diagnostic.Codes.INVALID_COMPOSITION_LEFT, nullary.diagnostic().code());
        assertEquals(12, nullary.span().start().column());
    }

    @Test
    void compositionUsesTheOrdinaryCallDepthGuard() {
        String source = "identity value = value\npipeline = identity"
                + " >> identity".repeat(300) + "\nprint pipeline 1\n";
        LangException error = assertThrows(LangException.class, () -> execute(source));
        assertEquals(Diagnostic.Codes.CALL_DEPTH_EXCEEDED, error.diagnostic().code());
        assertEquals(DiagnosticCatalog.CALL_DEPTH, error.catalogEntry());
        assertEquals("Maximum Caret call depth exceeded", error.detail());
    }

    @Test
    void namedInfixCallsRequireCallableBinaryTargets() {
        LangException arity = assertThrows(LangException.class, () -> execute("""
                unary value = value
                print 1 unary 2
                """));
        assertEquals(Diagnostic.Codes.INVALID_INFIX_ARITY, arity.diagnostic().code());
        assertEquals(9, arity.span().start().column());

        LangException target = assertThrows(LangException.class, () -> execute("""
                value = 1
                print 1 value 2
                """));
        assertEquals(Diagnostic.Codes.NOT_CALLABLE, target.diagnostic().code());
        assertEquals(9, target.span().start().column());
    }

    @Test
    void reflectionDoesNotExposePrivateLocals() {
        String output = execute("""
                make =
                  private = 1
                  ^public = 2
                value = make
                print value.private~
                print (@value).size
                print (@value).names
                """);
        assertEquals("~\n1\npublic\n", output);
    }

    @Test
    void typeAndReflectionUseTheSameRuntimeKindNames() {
        assertEquals("Null\nMissing\nNumber\nFunction\nFunction\n", execute("""
                identity value = value
                reference = @identity
                print type ?
                print type ~
                print type 1
                print type reference
                print reference.kind
                """));
    }

    @Test
    void excessiveRecursionProducesALocatedLanguageDiagnostic() {
        LangException error = assertThrows(LangException.class, () -> execute("""
                recurse n = n == 0 & 0 ! recurse (n - 1)
                print recurse 100000
                """));
        assertEquals(Diagnostic.Codes.CALL_DEPTH_EXCEEDED, error.diagnostic().code());
        assertNotNull(error.span());
    }

    @Test
    void excessiveImplicitZeroArgumentRecursionProducesALanguageDiagnostic() {
        LangException error = assertThrows(LangException.class, () -> execute("""
                loop =
                  loop
                print loop
                """));
        assertEquals(Diagnostic.Codes.CALL_DEPTH_EXCEEDED, error.diagnostic().code());
        assertNotNull(error.span());
    }

    @Test
    void reflectionRefersToFunctionsWithoutInvokingThem() {
        assertEquals("Function\n0\nFunction\n1\n", execute("""
                zero =
                  ^called = true
                identity value = value

                print (@zero).kind
                print (@zero).remaining
                print (@identity).kind
                print (@identity).remaining
                """));
    }

    @Test
    void functionReferencesAreReflectiveNonCallableAndUseTargetIdentity() {
        assertEquals("Function\n1\ntrue\nfalse\nFunction\n2\ntrue\n~\n", execute("""
                identity value = value
                other value = value
                reference = @identity
                sameReference = @identity
                reflectedAgain = @reference
                operatorReference = @+

                print reference.kind
                print reference.remaining
                print reference == sameReference
                print reference == @other
                print operatorReference.kind
                print operatorReference.remaining
                print reflectedAgain == reference
                print reference.absent~
                """));

        LangException error = assertThrows(LangException.class, () -> execute("""
                identity value = value
                reference = @identity
                print reference 1
                """));
        assertEquals(Diagnostic.Codes.NOT_CALLABLE, error.diagnostic().code());
        assertTrue(error.getMessage().contains("Value is not callable"));
    }

    @Test
    void symbolicOperatorsSharePrefixInfixAndPartialBehavior() {
        assertEquals("5\n5\n6\n2\n3\n1\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\n6\n5\n-5\n", execute("""
                identity value = value
                increment = + _ 1
                subtract = (-)
                print + 2 3
                print 2 + 3
                print * 2 3
                print / 6 3
                print % 7 4
                print - 7 6
                print == 2 2
                print != 2 3
                print < 2 3
                print <= 3 3
                print > 3 2
                print >= 3 3
                print increment 5
                print subtract 7 2
                print - identity 5
                """));
    }

    @Test
    void holesBecomeFutureArgumentsInLeftToRightOrder() {
        assertEquals("123\nno\n", execute("""
                digits a b c = a * 100 + b * 10 + c
                rearranged = digits _ 2 _
                print rearranged 1 3
                choose = false & _ ! _
                print choose "yes" "no"
                """));
    }

    @Test
    void partialApplicationCapturesFixedOperandsEagerly() {
        assertEquals("captured\ncaptured\n", execute("""
                announce value = print value
                first left right = left
                partial = first (announce "captured") _
                print partial "ignored"
                """));
    }

    @Test
    void numberedHolesReorderAndReuseArguments() {
        assertEquals("321\nxx\n", execute("""
                digits a b c = a * 100 + b * 10 + c
                reordered = digits _2 2 _1
                print reordered 1 3
                join left right = left + right
                duplicate = join _1 _1
                print duplicate "x"
                """));
    }

    @Test
    void rejectsMixedNumberedAndOrdinaryHoles() {
        assertDiagnostic("partial = pair _ _1", "Cannot mix numbered and unnumbered holes", 1, 11);
    }

    @Test
    void conditionalEvaluatesOnlyTheSelectedBranch() {
        assertEquals("yes\nno\n", execute("""
                print true & "yes" ! absent
                print false & absent ! "no"
                """));
    }

    @Test
    void lazyBranchesDeferForwardInitializationChecksUntilSelected() {
        assertEquals("1\nfalse\n", execute("""
                print true & 1 ! later
                print false and later
                later = 2
                """));

        LangException selected = assertThrows(LangException.class, () -> execute("""
                print false & 1 ! later
                later = 2
                """));
        assertEquals(Diagnostic.Codes.READ_BEFORE_INITIALIZATION, selected.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, selected.diagnostic().phase());
    }

    @Test
    void optionalMissingFieldsAreValuesButInvalidOperationsAreDiagnostics() {
        assertEquals("~\n", execute("""
                make =
                  ^present = true
                value = make
                print value.absent~
                """));

        assertDiagnostic("print (1).absent~", "Field access requires a scope", 1, 7);
        assertDiagnostic("print 1 + true", "Expected number", 1, 11);
        assertDiagnostic("print 1 2", "Value is not callable", 1, 7);
        assertDiagnostic("print 1 & true ! false", "Condition must be Boolean", 1, 7);
    }

    @Test
    void dynamicLookupRequiresAString() {
        LangException error = assertThrows(LangException.class, () -> execute("""
                make =
                  ^value = 1
                scope = make
                print scope[42]~
                """));
        assertEquals(4, error.span().start().line());
        assertEquals(7, error.span().start().column());
        assertTrue(error.getMessage().contains("Dynamic field name must be a string"));
    }

    @Test
    void runtimeErrorsUseTheSmallestFailingExpressionSpan() {
        LangException error = assertThrows(LangException.class, () -> execute("print (1 + missing)"));
        assertEquals(1, error.span().start().line());
        assertEquals(12, error.span().start().column());
        assertTrue(error.getMessage().contains("Unknown name: missing"));
    }

    @Test
    void requiredMissingFieldReportsTheFieldExpression() {
        LangException error = assertThrows(LangException.class, () -> execute("""
                make =
                  ^present = true
                value = make
                print value.absent
                """));
        assertEquals(4, error.span().start().line());
        assertEquals(7, error.span().start().column());
        assertTrue(error.getMessage().contains("Scope has no exported binding: absent"));
    }

    @Test
    void supportsDirectAndMutualRecursionThroughBlockPredeclaration() {
        assertEquals("120\ntrue\n", execute("""
                factorial n = n == 0 & 1 ! n * factorial (n - 1)
                even n = n == 0 & true ! odd (n - 1)
                odd n = n == 0 & false ! even (n - 1)
                print factorial 5
                print even 8
                """));
    }

    @Test
    void rejectsDuplicateDefinitionsAndParameters() {
        LangException duplicate = expectDiagnostic("value = 1\nvalue = 2", "Duplicate definition: value", 2, 1);
        assertEquals(Diagnostic.Phase.SEMANTIC, duplicate.diagnostic().phase());
        assertTrue(duplicate.getMessage().contains("Note: Line 1, column 1"));
        assertDiagnostic("same value value = value", "Duplicate parameter: value", 1, 1);
    }

    @Test
    void reportsReadsBeforeSequentialDeclarations() {
        LangException error = expectDiagnostic("first = second\nsecond = 2",
                "Binding read before initialization: second", 1, 9);
        assertEquals(Diagnostic.Phase.SEMANTIC, error.diagnostic().phase());
        assertEquals(Diagnostic.Codes.READ_BEFORE_INITIALIZATION, error.diagnostic().code());
    }

    @Test
    void rejectsInvalidNumericResultsAndCallableEquality() {
        assertDiagnostic("print 1 / 0", "Division by zero", 1, 11);
        assertDiagnostic("print 1 % 0", "Division by zero", 1, 11);
        assertDiagnostic("identity x = x\nprint identity == identity",
                "Callable values cannot be compared for equality", 2, 7);
    }

    @Test
    void comparesExportedScopesStructurally() {
        assertEquals("true\n", execute("""
                make value =
                  ^value = value
                print make 1 == make 1
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
    void providesPersistentSequencesAndInsertionOrderedDictionaries() {
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
                print (@complete).names
                """));
    }

    @Test
    void persistentCollectionsKeepOlderValuesAndDictionaryReplacementOrder() {
        assertEquals("[1]\n[1, 2]\n[first, second]\n22\n", execute("""
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

    private String execute(String source) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        interpreter.execute(new Parser(source).parseProgram());
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private void assertDiagnostic(String source, String detail, int line, int column) {
        LangException error = assertThrows(LangException.class, () -> execute(source));
        assertDiagnosticDetails(error, detail, line, column);
    }

    private LangException expectDiagnostic(String source, String detail, int line, int column) {
        LangException error = assertThrows(LangException.class, () -> execute(source));
        assertDiagnosticDetails(error, detail, line, column);
        return error;
    }

    private void assertDiagnosticDetails(LangException error, String detail, int line, int column) {
        assertEquals(line, error.span().start().line());
        assertEquals(column, error.span().start().column());
        assertTrue(error.getMessage().contains(detail));
    }
}
