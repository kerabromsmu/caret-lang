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
    void constructsAndEnforcesParameterizedSequenceContracts() {
        assertEquals("true\nfalse\ntrue\ntrue\nSequence Number\n[Sequence]\n[Number]\n", execute("""
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
                print (@Numbers).name
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
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, derived.diagnostic().code());

        LangException direct = assertThrows(LangException.class, () -> execute("""
                positive value = value > 0
                (positive) count = 0
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, direct.diagnostic().code());
    }

    @Test
    void rejectsCallablesThatCannotBeProvedValidAsRefinements() {
        LangException wrongArity = assertThrows(LangException.class, () -> execute("""
                same left right = left == right
                Invalid = contract same
                """));
        assertEquals(Diagnostic.Codes.INVALID_REFINEMENT, wrongArity.diagnostic().code());

        LangException effectful = assertThrows(LangException.class, () -> execute("""
                emitting value = print (value > 0)
                (emitting) count = 1
                """));
        assertEquals(Diagnostic.Codes.INVALID_REFINEMENT, effectful.diagnostic().code());
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
        assertEquals("true\ntrue\ntrue\nfalse\nfalse\nNumber?~\n[Number]\n", execute("""
                (Number?) nullable = ?
                (Number~) optional = ~
                (Number?~) either = ~
                accepts = Number?~
                print accepts ?
                print accepts ~
                print accepts 1
                print Number? "wrong"
                print Number~ ?
                print (@accepts).name
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
        assertEquals("true\ntrue\ntrue\nfalse\ntrue\n[Number]\n", execute("""
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
        assertEquals("[positive]\n", execute("""
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
