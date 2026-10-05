package caretlang;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static caretlang.InterpreterTestSupport.*;

final class CoreCallableInterpreterTest {
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
                print (@value).ids
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
                applyInfix left (pure) operation right = left operation right
                applyPrefix (pure) operation left right = operation left right
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
                print (@value).ids
                """);
        assertEquals("~\n1\npublic\n", output);
    }

    @Test
    void typeAndReflectionUseTheSameRuntimeKindNames() {
        assertEquals("Null\nMissing\nNumber\nDictionary\nFunction\n", execute("""
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
    void callableReflectionExposesLanguageOwnedSignaturesAndSpecializesPrefixPartials() {
        assertEquals("""
                add
                2
                Parameter
                0
                left
                Number
                Number
                FunctionResult
                Number
                FunctionEffects
                0
                add
                1
                right
                0
                """, execute("""
                (Number) add (Number) left (Number) right =
                  left + right
                addOne = add 1

                signature = (@add).signature
                first = seqGet signature.parameters 0
                firstRequirement = seqGet first.requirements 0
                resultGuarantee = seqGet signature.result.guarantees 0
                print (@add).id
                print (@add).remaining
                print first.kind
                print first.position
                print first.id
                print firstRequirement.id
                print (seqGet first.declared 0).id
                print signature.result.kind
                print resultGuarantee.id
                print signature.effects.kind
                print seqSize signature.effects.upperBound
                print (@addOne).id
                print (@addOne).remaining
                print (seqGet (@addOne).signature.parameters 0).id
                print seqSize (@addOne).variants
                """));
    }

    @Test
    void callableReflectionProjectsLazilyWithoutAmplifyingVisibilityOrAuthority() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        interpreter.execute(new Parser("""
                (Number) transform (Number) value = value + 1
                metadata = @transform
                """).parseProgram());

        interpreter.reflectionContext(ReflectionContext.externalModule(false, false, Set.of()));
        interpreter.execute(new Parser("""
                parameter = seqGet metadata.signature.parameters 0
                print metadata.id
                print (seqGet parameter.requirements 0).id
                print parameter.inferred
                print (seqGet metadata.signature.result.guarantees 0).id
                print metadata.signature.result.inferred
                print seqSize metadata.signature.effects.upperBound
                """).parseProgram());

        interpreter.reflectionContext(ReflectionContext.sandbox(false, false, Set.of()));
        interpreter.execute(new Parser("sandboxMetadata = @transform").parseProgram());

        interpreter.reflectionContext(ReflectionContext.defining());
        interpreter.execute(new Parser("""
                definingParameter = seqGet metadata.signature.parameters 0
                print metadata.id
                print seqSize definingParameter.inferred
                print parameter.inferred
                print sandboxMetadata.id
                print (seqGet sandboxMetadata.signature.parameters 0).inferred
                """).parseProgram());

        assertEquals("~\n~\n~\n~\n~\n0\ntransform\n0\n~\n~\n~\n",
                bytes.toString(StandardCharsets.UTF_8));

        LangException denied = assertThrows(LangException.class, () -> interpreter.execute(new Parser("""
                callable = sandboxMetadata:
                """).parseProgram()));
        assertEquals(Diagnostic.Codes.NOT_DEREFERENCEABLE, denied.diagnostic().code());
    }

    @Test
    void collectionProtocolUsesCurrentObserverForRetainedCallableMetadata() {
        Value.Callable callable = new Value.FunctionValue("transform", List.of("value"),
                (args, ignored) -> args.getFirst().value());
        ReflectionContext defining = ReflectionContext.defining();
        ReflectionContext hidden = ReflectionContext.externalModule(false, false, Set.of());
        Value.ProjectedDictionary retained = (Value.ProjectedDictionary)
                Value.CallableMetadata.reflection(callable, defining);
        CollectionRuntime.Provider visible = CollectionRuntime.provider(retained, defining).orElseThrow();
        CollectionRuntime.Provider restricted = CollectionRuntime.provider(retained, hidden).orElseThrow();
        assertEquals(new Value.Str("transform"), visible.getElement(new Value.Str("id")));
        assertEquals(Value.Missing.INSTANCE, restricted.getElement(new Value.Str("id")));
        assertTrue(((Value.Seq) restricted.keys()).values().contains(new Value.Str("id")));
        assertTrue(((Value.Seq) restricted.fieldEntries()).values().stream().anyMatch(field ->
                field instanceof Value.Field entry && entry.key().equals(new Value.Str("id"))
                        && entry.value() == Value.Missing.INSTANCE));
        assertFalse(((Value.Seq) restricted.valueEntries()).values().contains(new Value.Str("transform")));
        assertEquals(new Value.Num(((Value.Seq) restricted.keys()).size()), restricted.size());
        assertEquals(new Value.Num(retained.fields(hidden).size()), restricted.size());
        assertEquals(restricted.size(), ValueSemantics.reflectionFields(retained, hidden).get("size"));

        Value.ProjectedDictionary capturedHidden = (Value.ProjectedDictionary)
                Value.CallableMetadata.reflection(callable, hidden);
        assertEquals(Value.Missing.INSTANCE, CollectionRuntime.provider(capturedHidden, defining)
                .orElseThrow().getElement(new Value.Str("id")));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        interpreter.execute(new Parser("(Number) transform (Number) value = value + 1\nmetadata = @transform")
                .parseProgram());
        interpreter.reflectionContext(hidden);
        interpreter.execute(new Parser("""
                print getElement metadata "id"
                print (size metadata) == (size (keys metadata))
                print (size metadata) == (size (fields metadata))
                with metadata
                  print id
                """).parseProgram());
        assertEquals("~\ntrue\ntrue\n~\n", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void callableReflectionSeparatesStableDeclarationsFromStrongerLocalInference() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        interpreter.execute(new Parser("""
                (Collection) makeSequence =
                  [1]
                metadata = @makeSequence
                print seqSize metadata.signature.result.guarantees
                print (seqGet metadata.signature.result.inferred 0).id
                """).parseProgram());

        interpreter.reflectionContext(ReflectionContext.externalModule(false, false, Set.of()));
        interpreter.execute(new Parser("""
                print seqSize metadata.signature.result.guarantees
                print (seqGet metadata.signature.result.guarantees 0).id
                print metadata.signature.result.inferred
                """).parseProgram());

        assertEquals("2\nSequence\n1\n~\n~\n", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void ordinaryReflectionTargetsCannotAmplifyDereferenceAuthorityAcrossContexts() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));

        interpreter.reflectionContext(ReflectionContext.sandbox(false, false, Set.of()));
        interpreter.execute(new Parser("""
                primitiveMetadata = @42
                sequenceMetadata = @[1 2]
                dictionaryMetadata = @[^answer = 42]
                (Number) typed = 42
                attributedMetadata = @typed
                retainedSequence = seqAdd seqEmpty primitiveMetadata
                retainedDictionary = dictPut dictEmpty "metadata" primitiveMetadata
                reflectedMetadata = @primitiveMetadata
                updatedMetadata = dictPut primitiveMetadata "extra" 1
                """).parseProgram());

        interpreter.reflectionContext(ReflectionContext.externalModule(false, false, Set.of()));
        interpreter.execute(new Parser("externalMetadata = @42").parseProgram());

        interpreter.reflectionContext(ReflectionContext.defining());
        assertDereferenceDenied(interpreter, "leaked = primitiveMetadata:");
        assertDereferenceDenied(interpreter, "leaked = sequenceMetadata:");
        assertDereferenceDenied(interpreter, "leaked = dictionaryMetadata:");
        assertDereferenceDenied(interpreter, "leaked = attributedMetadata:");
        assertDereferenceDenied(interpreter, "leaked = (seqGet retainedSequence 0):");
        assertDereferenceDenied(interpreter, "leaked = retainedDictionary.metadata:");
        assertDereferenceDenied(interpreter, "leaked = reflectedMetadata:");
        assertDereferenceDenied(interpreter, "leaked = updatedMetadata:");
        assertDereferenceDenied(interpreter, "leaked = externalMetadata:");

        interpreter.execute(new Parser("definingMetadata = @typed").parseProgram());
        interpreter.reflectionContext(ReflectionContext.sandbox(false, false, Set.of()));
        assertDereferenceDenied(interpreter, "leaked = definingMetadata:");

        interpreter.reflectionContext(ReflectionContext.defining());
        interpreter.execute(new Parser("""
                restored = definingMetadata:
                print restored
                print restored == typed
                """).parseProgram());
        assertEquals("42\ntrue\n", bytes.toString(StandardCharsets.UTF_8));
    }

    private static void assertDereferenceDenied(Interpreter interpreter, String source) {
        LangException denied = assertThrows(LangException.class,
                () -> interpreter.execute(new Parser(source).parseProgram()));
        assertEquals(Diagnostic.Codes.NOT_DEREFERENCEABLE, denied.diagnostic().code());
    }

    @Test
    void hiddenCallableAndDescriptorMetadataRetainLanguageOwnedIdentity() {
        Object firstIdentity = new Object();
        Object secondIdentity = new Object();
        CallableSignature firstSignature = signatureWithRequirement(firstIdentity);
        CallableSignature secondSignature = signatureWithRequirement(secondIdentity);
        Value.Callable first = new Value.FunctionValue("first", List.of("value"),
                (values, span) -> values.getFirst().value(),
                false, firstSignature);
        Value.Callable second = new Value.FunctionValue("second", List.of("value"),
                (values, span) -> values.getFirst().value(),
                false, secondSignature);
        ReflectionContext hidden = ReflectionContext.restricted(false, false, false, Set.of());

        Value firstMetadata = Value.CallableMetadata.reflection(first, hidden);
        Value aliasMetadata = Value.CallableMetadata.reflection(first, hidden);
        Value secondMetadata = Value.CallableMetadata.reflection(second, hidden);
        assertTrue(ValueSemantics.equal(firstMetadata, aliasMetadata, ReflectionContext.defining()));
        assertFalse(ValueSemantics.equal(firstMetadata, secondMetadata, ReflectionContext.defining()));

        Value firstRef = reflectedRequirement(firstMetadata, ReflectionContext.defining());
        Value secondRef = reflectedRequirement(secondMetadata, ReflectionContext.defining());
        assertEquals(Value.Missing.INSTANCE, ((Value.ProjectedDictionary) firstRef)
                .find("id", ReflectionContext.defining()).orElseThrow());
        assertTrue(((Value.ProjectedDictionary) firstRef)
                .find("name", ReflectionContext.defining()).isEmpty());
        assertFalse(ValueSemantics.equal(firstRef, secondRef, ReflectionContext.defining()));
    }

    private static CallableSignature signatureWithRequirement(Object identity) {
        CallableSignature.ContractTerm requirement = new CallableSignature.NamedRef(identity, "Private");
        return new CallableSignature(List.of(new CallableSignature.Parameter("value", List.of(requirement),
                List.of(requirement), List.of(requirement))),
                new CallableSignature.Result(List.of(requirement), List.of(requirement), List.of(requirement)),
                new CallableSignature.Effects(List.of(), List.of(), List.of()), List.of());
    }

    private static Value reflectedRequirement(Value metadata, ReflectionContext context) {
        Value signature = ((Value.ProjectedDictionary) metadata).find("signature", context).orElseThrow();
        Value parameters = ((Value.ProjectedDictionary) signature).find("parameters", context).orElseThrow();
        Value parameter = ((Value.Seq) parameters).values().getFirst();
        Value requirements = ((Value.ProjectedDictionary) parameter).find("requirements", context).orElseThrow();
        return ((Value.Seq) requirements).values().getFirst();
    }

    @Test
    void overloadAndCompositionReflectionUseSafeConservativeSignatureViews() {
        assertEquals("2\n2\nOutput\n1\n~\n1\n", execute("""
                (Number) show (Number) value (Number) suffix =
                  value
                (Output String) show (String) value (String) suffix =
                  print value
                stringify value = numberText value
                pipeline = stringify >> print

                meta = @show
                print seqSize meta.variants
                print seqSize meta.signature.parameters
                print (seqGet meta.signature.effects.upperBound 0).id
                narrowed = show 1
                print seqSize (@narrowed).variants
                print (@pipeline).id
                print seqSize (@pipeline).signature.effects.upperBound
                """));
    }

    @Test
    void derivedSignaturesProjectRepeatedAndReorderedHoles() {
        assertEquals("2\nNumber\nString\nright\nleft\n1\n0\n", execute("""
                (Number) combine (Number) left (String) right = 0
                repeated = combine _1 _1
                reordered = combine _2 _1

                repeatedParameter = seqGet (@repeated).signature.parameters 0
                print seqSize repeatedParameter.requirements
                print (seqGet repeatedParameter.requirements 0).id
                print (seqGet repeatedParameter.requirements 1).id
                print (seqGet (@reordered).signature.parameters 0).id
                print (seqGet (@reordered).signature.parameters 1).id
                print seqSize (@repeated).signature.parameters
                print seqSize (@repeated).signature.variables
                """));
    }

    @Test
    void compositionsSpecializeBridgeVariablesAndRejectOnlyProvenConflicts() {
        assertEquals("String\nString\n0\n5\n", execute("""
                identity value = value
                (String) text (String) value = value
                pipeline = identity >> text
                print (seqGet (seqGet (@pipeline).signature.parameters 0).requirements 0).id
                print (seqGet (@pipeline).signature.result.guarantees 0).id
                print seqSize (@pipeline).signature.variables

                dynamic dictionary key = dictionary[key]~
                unknownPipeline = dynamic _ "value" >> text
                print (unknownPipeline (dictPut dictEmpty "value" "5"))
                """));

        LangException incompatible = assertThrows(LangException.class, () -> execute("""
                (Number) number (Number) value = value
                (String) text (String) value = value
                pipeline = number >> text
                """));
        assertEquals(Diagnostic.Phase.SEMANTIC, incompatible.diagnostic().phase());
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, incompatible.diagnostic().code());
        assertEquals(3, incompatible.span().start().line());
        assertEquals(2, incompatible.diagnostic().related().size());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(output, true, StandardCharsets.UTF_8));
        assertThrows(LangException.class, () -> interpreter.execute(new Parser("""
                print "must not run"
                (Number) number (Number) value = value
                (String) text (String) value = value
                pipeline = number >> text
                """).parseProgram()));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void overloadHolePartialsExposeProjectedSurvivorSignatures() {
        assertEquals("2\n1\n2\n2\nNumber\nString\n1\nString\n", execute("""
                route (Number) left (String) right = "number-text"
                route (String) left (Number) right = "text-number"
                repeated = route _1 _1
                numberFirst = route 1
                metadata = @repeated
                firstVariant = seqGet metadata.variants 0
                print seqSize metadata.variants
                print seqSize metadata.signature.parameters
                print seqSize (seqGet firstVariant.parameters 0).requirements
                print seqSize (seqGet (seqGet metadata.variants 1).parameters 0).requirements
                print (seqGet (seqGet firstVariant.parameters 0).requirements 0).id
                print (seqGet (seqGet firstVariant.parameters 0).requirements 1).id
                print seqSize (@numberFirst).variants
                print (seqGet (seqGet (seqGet (@numberFirst).variants 0).parameters 0).requirements 0).id
                """));
    }

    @Test
    void callableReflectionPreservesGeneralizedParameterResultRelationships() {
        assertEquals("VariableRef\n0\nVariableRef\n0\n1\n", execute("""
                identity value = value
                signature = (@identity).signature
                parameterVariable = seqGet (seqGet signature.parameters 0).requirements 0
                resultVariable = seqGet signature.result.guarantees 0
                print parameterVariable.kind
                print parameterVariable.index
                print resultVariable.kind
                print resultVariable.index
                print seqSize signature.variables
                """));
    }

    @Test
    void functionMetadataIsStructuralAndNonCallable() {
        assertEquals("Function\n1\ntrue\nfalse\nFunction\n2\nfalse\n~\n", execute("""
                identity value = value
                other value = value
                reference = @identity
                sameReference = @identity
                reflectedAgain = @reference
                operatorReference = @(+)

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
    void dereferencesReflectionDictionariesForFunctionsValuesAndMembers() {
        assertEquals("Dictionary\nFunction\n5\n7\nNumber\n\"field\" = 9\n9\nSequence\n", execute("""
                add left right = left + right
                metadata = @add
                alias = metadata:
                value = 7
                valueMetadata = @value
                object = [^field = 9]
                fieldName = "field"

                print type metadata
                print @add.kind
                print alias 2 3
                print valueMetadata:
                print (@42).kind
                print object.@field:
                print @(object[fieldName]):
                print @[1 2].kind
                """));

        LangException ordinary = assertThrows(LangException.class, () -> execute("value = 1\nprint value:\n"));
        assertEquals(Diagnostic.Codes.NOT_DEREFERENCEABLE, ordinary.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, ordinary.diagnostic().phase());
        assertSame(DiagnosticCatalog.NOT_DEREFERENCEABLE, ordinary.catalogEntry());
        assertEquals("Line 2, column 7: Value is not dereferenceable: 1", ordinary.getMessage());
        assertEquals(7, ordinary.span().start().column());

        LangException reflectedFunctionApplied = assertThrows(LangException.class,
                () -> execute("identity value = value\nprint @identity 1\n"));
        assertEquals(Diagnostic.Codes.NOT_CALLABLE, reflectedFunctionApplied.diagnostic().code());
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
        assertEquals("captured\n0\ncaptured\n", execute("""
                (Output) announce value = print value
                first left right = left
                partial = first (announce "captured") _
                print seqSize (@partial).signature.effects.upperBound
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
    void booleanOperatorsShortCircuitAndNormalizeNullableOperands() {
        assertEquals("false\ntrue\nand\ntrue\nor\ntrue\nfalse\nfalse\nnull\ntrue\nmissing\ntrue\n",
                execute("""
                (Output Boolean) right label =
                  print label
                  true
                print false and absent
                print true or absent
                print true and right "and"
                print false or right "or"
                print ? and absent
                print ~ and absent
                print ? or right "null"
                print ~ or right "missing"
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
        assertEquals("~\n~\n", execute("""
                make =
                  ^present = true
                value = make
                print value.absent~
                print value.absent
                """));

        assertDiagnostic("print (1).absent~", "Expected Collection, got: 1", 1, 7);
        assertDiagnostic("print 1 + true", "Expected number", 1, 11);
        assertDiagnostic("print 1 2", "Value is not callable", 1, 7);
        LangException invalidCondition = assertThrows(LangException.class,
                () -> execute("print 1 & true ! false"));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, invalidCondition.diagnostic().code());
        assertEquals(1, invalidCondition.diagnostic().primarySpan().start().line());
    }

    @Test
    void dictionaryLookupRejectsKeysOutsideItsAccessContract() {
        LangException error = assertThrows(LangException.class, () -> execute("""
                make =
                  ^value = 1
                scope = make
                print scope[42]~
                """));
        assertEquals(4, error.span().start().line());
        assertEquals(13, error.span().start().column());
        assertEquals(Diagnostic.Codes.INVALID_COLLECTION_KEY, error.diagnostic().code());
        assertTrue(error.getMessage().contains("Dictionary access key must be a String"));
    }

    @Test
    void runtimeErrorsUseTheSmallestFailingExpressionSpan() {
        LangException error = assertThrows(LangException.class, () -> execute("print (1 + missing)"));
        assertEquals(1, error.span().start().line());
        assertEquals(12, error.span().start().column());
        assertTrue(error.getMessage().contains("Unknown name: missing"));
    }

    @Test
    void requiredAndOptionalMissingFieldSpellingsReturnMissing() {
        assertEquals("~\n~\n", execute("""
                make =
                  ^present = true
                value = make
                print value.absent
                print value.absent~
                """));
    }

    @Test
    void unifiedCollectionAccessSupportsSugarContractsCompositeKeysAndPartials() {
        assertEquals("""
                1
                1
                1
                1
                1
                1
                ~
                ~
                true
                ?
                ?
                member
                nested
                null-key
                ~
                key
                9
                ~
                ~
                20
                1
                1
                1
                1
                2
                1
                0
                """, execute("""
                record = [^present = 1 ^storedMissing = ~ ^storedNull = ?]
                print record.present
                print record.present~
                print record["present"]
                print record["present"]~
                print getElement record "present"
                lookup = getElement
                print lookup record "present"
                print record.absent
                print record.storedMissing
                print dictHas record "storedMissing"
                print record.storedNull
                print getElement record "storedNull"

                (Set String) members = ["member"]
                print members["member"]
                general = [(field [1] "nested") (field ? "null-key")]
                print general[[1]]
                print general[?]
                print general[[2]]

                item = field "key" 9
                print item[0]
                print item[1]
                print item[2]
                print [10 20][-1]
                print [10 20][1]

                byKey = record[_]
                byCollection = _["present"]
                reordered = _2[_1]
                literalBoundary = [1 2][_]
                print byKey "present"
                print byCollection record
                print reordered "present" record
                print literalBoundary 0
                print (@getElement).remaining
                print (@byKey).remaining
                print seqSize (@getElement).signature.effects.upperBound
                """));

        assertEquals("shadow\nshadow\nshadow\n", execute("""
                custom collection key = "shadow"
                getElement = custom
                record = [^present = 1]
                print record.present
                print record["present"]
                print getElement record "present"
                """));

        assertEquals("true\n", execute("""
                sameAccess left right = left == right
                getElement = sameAccess
                same = _1[_1]
                print same 4
                """));

        assertEquals("99\n", execute("""
                (Number) getElement (Sequence Number) collection (Number) key = 99
                print [1][0]
                """));

        assertEquals("fixed\n1\n", execute("""
                (Output Any) mark value =
                  print "fixed"
                  value
                record = [^present = 1]
                selected = (mark record)[_]
                print selected "present"
                """));

        assertEquals("lookup\n7\n", execute("""
                (Output Number) noisyGet collection key =
                  print "lookup"
                  7
                getElement = noisyGet
                (Output Number) access collection = collection.key
                print access []
                """));

        assertDiagnostic("print [1][0.5]", "Sequential Collection key must be an integer, got: 0.5", 1, 11);
        assertDiagnostic("print [1][~]", "Collection access key cannot be missing", 1, 11);
        assertDiagnostic("identity value = value\nprint [1][identity]",
                "Collection access key must support equality, got: Function", 2, 11);
    }

    @Test
    void eagerSnapshotsKeepTheirAccessKeyContracts() {
        LangException sequence = expectDiagnostic("values = eager [10 20]\nprint values[0.5]",
                "Sequential Collection key must be an integer", 2, 14);
        assertEquals(Diagnostic.Codes.INVALID_COLLECTION_KEY, sequence.diagnostic().code());
        LangException dictionary = expectDiagnostic("record = eager [^name = \"Ada\"]\nprint record[1]",
                "Dictionary access key must be a String", 2, 14);
        assertEquals(Diagnostic.Codes.INVALID_COLLECTION_KEY, dictionary.diagnostic().code());
        LangException set = expectDiagnostic("(Set String) members = [\"member\"]\nprint (eager members)[1]",
                "Key does not satisfy the Collection access contract", 2, 23);
        assertEquals(Diagnostic.Codes.INVALID_COLLECTION_KEY, set.diagnostic().code());
        assertEquals("~\n~\n~\n", execute("""
                values = eager [10 20]
                print values[2]
                record = eager [^name = "Ada"]
                print record["absent"]
                general = eager [(field [1] "nested")]
                print general[2]
                """));
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
    void comparesNamedCollectionsStructurally() {
        assertEquals("true\n", execute("""
                make value =
                  ^value = value
                print make 1 == make 1
                """));
    }

    @Test
    void exportedBlocksAndNamedLiteralsAreEquivalentCollections() {
        assertEquals("true\nDictionary\nnamed\n2\nage,name\nAda\n~\n", execute("""
                exported =
                  ^name = "Ada"
                  ^age = 42
                literal = [^name = "Ada" ^age = 42]
                print exported == literal
                print (@literal).kind
                print (@literal).shape
                print (@literal).size
                print (@literal).ids
                print literal.name
                print literal.absent~
                """));
    }

}
