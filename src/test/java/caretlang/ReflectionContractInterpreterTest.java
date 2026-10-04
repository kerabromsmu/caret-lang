package caretlang;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static caretlang.InterpreterTestSupport.*;
import caretlang.InterpreterTestSupport.ModeExecution;

final class ReflectionContractInterpreterTest {
    @Test
    void reifiesFieldBindingsWithoutReadingContainerContents() {
        assertEquals("FieldBinding\nhealth\nfalse\ntrue\nhealth,name\ntrue\ntrue\ntrue\ntrue\n5\n6\n6\n~\n", execute("""
                cell = { (Number) 5 }
                person = [^health = cell ^name = "Ada"]
                alias = person
                reference = person.@health
                print reference.kind
                print reference.key
                print reference.mutable
                print reference.exported
                print reference.owner.ids
                print reference == alias.@health
                print reference: == (fields person)[0]
                print @cell.kind == "Container"
                print size @cell.contentContracts == 1
                print person.health{}
                print put cell 6
                print person.health{}
                print person.@absent
                """));
    }

    @Test
    void reificationPreservesSharedFieldsAndNestedWithOwners() {
        assertEquals("true\n2\ntrue\ntrue\n~\n", execute("""
                common = field "x" 10
                first = [common]
                second = [common]
                left = first.@x
                right = second.@x
                print left == right
                print size left.owner
                with first
                  print @x == first.@x
                  with second
                    print outer.@x == first.@x
                make =
                  hidden = 1
                  ^shown = 2
                print make.@hidden
                """));
    }

    @Test
    void fieldAndContainerMetadataRespectVisibilityAndDeclaredContracts() {
        assertEquals("true\nfalse\nNumber?\nNumber\n", execute("""
                make =
                  ^(Number?) nullable = ?
                ref = make.@nullable
                print ref.nullable
                print ref.optional
                print (seqGet ref.contracts 0).id
                cell = { (Number) 2 }
                print (seqGet @cell.contentContracts 0).id
                """));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        interpreter.execute(new Parser("object = [^public = 1]\nreference = object.@public").parseProgram());
        interpreter.reflectionContext(ReflectionContext.restricted(false, false, false, Set.of()));
        interpreter.execute(new Parser("print reference.owner").parseProgram());
        LangException denied = assertThrows(LangException.class,
                () -> interpreter.execute(new Parser("print reference:").parseProgram()));
        assertEquals(Diagnostic.Codes.NOT_DEREFERENCEABLE, denied.diagnostic().code());
        assertEquals("~\n", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void lazyMemberReificationUsesEstablishedProviderEntriesAndRemainsPureForContainers() {
        assertEquals("FieldBinding\ntrue\ntrue\nContainer\nFieldBinding\n", execute("""
                source = [^x = 1 ^y = 2]
                lazy = map (entry -> entry) source
                reference = lazy.@x
                print reference.kind
                print reference == lazy.@x
                with lazy
                  print @x == reference
                cell = { (Number) 3 }
                holder = [^cell = cell]
                (pure Dictionary) inspect (Container Number) target = @target
                print (inspect cell).kind
                (Output StateRead StateWrite Dictionary) describe (Dictionary) target = target.@cell
                print (describe holder).kind
                """));

        LangException unknownProviderEffects = assertThrows(LangException.class, () -> execute("""
                holder = [^cell = { (Number) 1 }]
                (pure Dictionary) describe (Dictionary) target = target.@cell
                print describe holder
                """));
        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, unknownProviderEffects.diagnostic().code());
        assertEquals(Diagnostic.Phase.SEMANTIC, unknownProviderEffects.diagnostic().phase());
        assertEquals(2, unknownProviderEffects.span().start().line());
        assertEquals(1, unknownProviderEffects.span().start().column());
    }

    @Test
    void fieldOwnerMetadataIsIndependentOfStorageReuse() {
        String program = """
                first = [^x = 1]
                second = dictPut first "y" 2
                left = first.@x
                right = second.@x
                print left == right
                print size left.owner
                print first.x
                print second.y
                """;
        ModeExecution enabled = execute(program, OwnershipTracker.Mode.ENABLED);
        ModeExecution disabled = execute(program, OwnershipTracker.Mode.DISABLED);
        assertEquals("true\n2\n1\n2\n", enabled.output());
        assertEquals(enabled.output(), disabled.output());
    }
    @Test
    void arrowContractsInspectCallableSignaturesWithoutInvokingCandidates() {
        assertEquals("true\nfalse\ntrue\nfalse\ntrue\n", execute("""
                (Number) double (Number) value = value + value
                (String) stringify (Any) value = numberText 1
                NumberTransform = [Number] -> Number
                BroadTransform = [Any] -> Number
                LooseResult = [Number] -> Any
                PairTransform = [Number Number] -> Number
                print NumberTransform double
                print BroadTransform double
                print LooseResult double
                print PairTransform double
                print NumberTransform == NumberTransform
                """));

        assertEquals("true\ntrue\n", execute("""
                (Number) double (Number) value = value + value
                MaybeTransform = ([Number] -> Number)?~
                print MaybeTransform double
                print MaybeTransform ?
                """));
    }

    @Test
    void arrowContractsWorkInNamedDeclarationClausesAndRejectMismatches() {
        assertEquals("true\n", execute("""
                NumberTransform = [Number] -> Number
                (Number) double (Number) value = value + value
                (NumberTransform) transform = double
                print NumberTransform transform
                """));

        LangException mismatch = assertThrows(LangException.class, () -> execute("""
                NumberTransform = [Number] -> Number
                (String) stringify (Any) value = numberText 1
                (NumberTransform) transform = stringify
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, mismatch.diagnostic().code());

        assertEquals("6\n", execute("""
                (Number) double (Number) value = value + value
                (Number) apply ([Number] -> Number) transform (Number) value = transform value
                print apply double 3
                """));
    }

    @Test
    void arrowContractsRequireWholeOverloadCoverageAndSafeOverlappingVariants() {
        assertEquals("true\nfalse\nfalse\nfalse\ntrue\nfalse\n", execute("""
                NumberTransform = [Number] -> Number
                AnyTransform = [Any] -> Any

                (Number) safe (Any) value = 1
                (String) safe (String) value = "text"

                (Number) unsafe (Any) value = 1
                (String) unsafe (Number) value = "text"

                (Number) partial (Number) value = value
                (String) partial (String) value = value

                explode value = value / 0 > 0
                Positive = contract [Number explode]
                (Number) uncertain (Any) value = 1
                (String) uncertain (Positive) value = "text"

                (Number) safeEffects (Any) value = 1
                (Output String) safeEffects (String) value =
                  print value
                  "text"

                (Number) unsafeEffects (Any) value = 1
                (Output Number) unsafeEffects (Number) value =
                  print value
                  value

                print NumberTransform safe
                print NumberTransform unsafe
                print AnyTransform partial
                print NumberTransform uncertain
                print NumberTransform safeEffects
                print NumberTransform unsafeEffects
                """));
    }

    @Test
    void effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced() {
        assertEquals("3\n3\ntrue\n2\n", execute("""
                (Output Number) noisy (Number) value =
                  print value
                  value
                identity value = value
                (pure) copy = identity
                Signature = [Number] -> (Output Number)
                print noisy 3
                print (Signature noisy)
                print copy 2
                """));

        LangException exceeded = assertThrows(LangException.class, () -> execute("""
                (Output Number) noisy (Number) value =
                  print value
                  value
                use (pure) callback = callback 1
                use noisy
                """));
        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, exceeded.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, exceeded.diagnostic().phase());
        assertEquals(5, exceeded.span().start().line());
        assertEquals(5, exceeded.span().start().column());
        assertEquals(Diagnostic.Codes.CONFLICTING_EFFECT_ALLOWANCE,
                assertThrows(LangException.class, () -> execute("(pure Output) value = 1"))
                        .diagnostic().code());
        assertEquals(Diagnostic.Codes.INVALID_EFFECT_MODIFIER,
                assertThrows(LangException.class, () -> execute("(Output?) value = 1"))
                        .diagnostic().code());
        assertEquals(Diagnostic.Codes.EFFECT_AS_CONTRACT_ARGUMENT,
                assertThrows(LangException.class, () -> execute("(Sequence Output) value = []"))
                        .diagnostic().code());
        assertEquals(Diagnostic.Codes.EFFECT_CONSTRAINT_REQUIRES_CALLABLE,
                assertThrows(LangException.class, () -> execute("(pure) value = 1"))
                        .diagnostic().code());
        assertEquals(Diagnostic.Codes.UNKNOWN_CLAUSE_NAME,
                assertThrows(LangException.class,
                        () -> execute("check = [Number] -> (TestReport Number)"))
                        .diagnostic().code());
        assertEquals(Diagnostic.Codes.AMBIGUOUS_CLAUSE_NAME,
                assertThrows(LangException.class, () -> execute("""
                        Output = contract
                        (Output) value = 1
                        """)).diagnostic().code());
    }

    @Test
    void assignmentEffectConstraintsApplyToCallableRatherThanInitializerOrResult() {
        assertEquals("initialized\n[ \"result\" ]\n", execute("""
                (Output) make ignored =
                  print "initialized"
                  value -> [value]
                (pure) callback = make 0
                print callback "result"
                """));
        LangException failure = expectDiagnostic("""
                (Output) noisy value = print value
                (pure) callback = noisy
                """, "Callable effect allowance exceeded: Output", 2, 19);
        assertSame(DiagnosticCatalog.EFFECT_ALLOWANCE_EXCEEDED, failure.catalogEntry());
        assertEquals(Diagnostic.Phase.RUNTIME, failure.diagnostic().phase());
        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, failure.diagnostic().code());
        assertEquals("Line 2, column 19: Callable effect allowance exceeded: Output\n"
                + "  Note: Line 2, column 1: Effect constraint declared here", failure.getMessage());
    }

    @Test
    void effectCatalogAliasesPreserveIdentityAndRemainSeparateFromBindings() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        EffectCatalog catalog = EffectCatalog.standard(false).alias("console", "Output");
        Interpreter interpreter = new Interpreter(
                new PrintStream(bytes, true, StandardCharsets.UTF_8), null, catalog);
        interpreter.execute(new Parser("""
                console = 42
                (console Number) announce (Number) value =
                  print value
                  value
                print announce 7
                """).parseProgram());
        assertEquals("7\n7\n", bytes.toString(StandardCharsets.UTF_8));

        Interpreter isolated = new Interpreter(new PrintStream(new ByteArrayOutputStream()), null, catalog);
        LangException unavailable = assertThrows(LangException.class, () -> isolated.execute(new Parser("""
                (console) permitted value = value
                print console
                """).parseProgram()));
        assertEquals(Diagnostic.Codes.UNKNOWN_NAME, unavailable.diagnostic().code());
    }

    @Test
    void invocationRejectsUnavailableEffectBoundsBeforeExecutingTheCallable() {
        LangException error = expectDiagnostic("""
                invoke callback value = callback value
                invoke print "not printed"
                """, "no known effect upper bound", 2, 1);
        assertEquals(Diagnostic.Codes.UNKNOWN_CALL_EFFECTS, error.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, error.diagnostic().phase());

        LangException mixed = expectDiagnostic("""
                invokeBoth (pure) known unknown value = known (unknown value)
                identity value = value
                invokeBoth identity identity 1
                """, "no known effect upper bound", 3, 1);
        assertEquals(Diagnostic.Codes.UNKNOWN_CALL_EFFECTS, mixed.diagnostic().code());
    }

    @Test
    void inferredAllowanceViolationsAbortBeforeAnyProgramEffects() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        LangException error = assertThrows(LangException.class, () -> interpreter.execute(new Parser("""
                (pure) invalid value = print value
                print "must not execute"
                """).parseProgram()));

        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, error.diagnostic().code());
        assertEquals("", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void callableMetadataUsesAnalyzedValueRequirementsInsteadOfRawEffectTerms() {
        assertEquals("1\nNumber\n1\nOutput\n1\nNumber\n0\n", execute("""
                (Output Number) noisy (Number) value =
                  print value
                  value
                (Number pure) quiet (Number) value = value

                noisySignature = (@noisy).signature
                quietSignature = (@quiet).signature
                print seqSize noisySignature.result.declared
                print (seqGet noisySignature.result.declared 0).id
                print seqSize noisySignature.effects.declared
                print (seqGet noisySignature.effects.declared 0).id
                print seqSize quietSignature.result.declared
                print (seqGet quietSignature.result.declared 0).id
                print seqSize quietSignature.effects.declared
                """));
    }

    @Test
    void arrowContractVariablesAreContiguousAndRequireGenericRelationships() {
        assertEquals("true\nfalse\n", execute("""
                identity value = value
                (Number) double (Number) value = value + value
                GenericIdentity = [_1] -> _1
                print GenericIdentity identity
                print GenericIdentity double
                """));

        LangException skipped = assertThrows(LangException.class,
                () -> execute("Transform = [_2] -> _2"));
        assertEquals(Diagnostic.Codes.INVALID_CONTRACT_VARIABLE, skipped.diagnostic().code());
        assertEquals(Diagnostic.Phase.SEMANTIC, skipped.diagnostic().phase());
        assertSame(DiagnosticCatalog.INVALID_CONTRACT_VARIABLE, skipped.catalogEntry());
        assertEquals("Line 1, column 14: Contract variable indices must be contiguous from _1",
                skipped.getMessage());
    }

    @Test
    void declarationHeaderVariablesShareSchemesAndSpecializeFreshPrefixPartials() {
        assertEquals("1\n0\n0\nNumber\nString\nNumber\n0\n0\n1\ntrue\ntrue\n", execute("""
                (_1) choose (_1) left (_1) right = left
                alias = choose
                numberChoice = choose 1
                textChoice = choose "left"
                holeChoice = choose _ 2

                scheme = (@choose).signature
                parameterVariable = seqGet (seqGet scheme.parameters 0).requirements 0
                resultVariable = seqGet scheme.result.guarantees 0
                print seqSize scheme.variables
                print parameterVariable.index
                print resultVariable.index
                print (seqGet (seqGet (@numberChoice).signature.parameters 0).requirements 0).id
                print (seqGet (seqGet (@textChoice).signature.parameters 0).requirements 0).id
                print (seqGet (seqGet (@holeChoice).signature.parameters 0).requirements 0).id
                print seqSize (@numberChoice).signature.variables
                print seqSize (@holeChoice).signature.variables
                print seqSize (@alias).signature.variables
                print numberChoice 2 == 1
                print holeChoice 1 == 1
                """));
    }

    @Test
    void declarationVariablesIncludeNestedArrowsAndRejectUnrelatedOccurrences() {
        assertEquals("5\n", execute("""
                identity value = value
                (_1) applyGeneric ([_1] -> _1) transform (_1) value = transform value
                print applyGeneric identity 5
                """));

        LangException single = assertThrows(LangException.class,
                () -> execute("(_1) consume value = value"));
        assertEquals(Diagnostic.Codes.INVALID_CONTRACT_VARIABLE, single.diagnostic().code());
        assertEquals(2, single.span().start().column());

        LangException standalone = assertThrows(LangException.class,
                () -> execute("GenericConsumer = [_1] -> Boolean"));
        assertEquals(Diagnostic.Codes.INVALID_CONTRACT_VARIABLE, standalone.diagnostic().code());

        LangException incompatible = assertThrows(LangException.class,
                () -> execute("(_1) choose (Number _1) left (String _1) right = left"));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, incompatible.diagnostic().code());
        assertEquals(1, incompatible.diagnostic().related().size());
    }

}
