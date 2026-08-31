package caretlang;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ContractInferenceTest {
    @Test
    void distinguishesNeutralEmptySequenceAndUnifiedDictionaryLiterals() {
        List<Ast.Stmt> program = new Parser("""
                empty =
                  []
                sequence =
                  [1]
                staticDictionary =
                  [^name = "Ada"]
                fieldDictionary =
                  [(field "name" "Ada")]
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);
        assertEquals(Set.of(BuiltinContract.SEQUENCE),
                inference.contract((Ast.FunctionDef) program.get(0)).resultGuarantees());
        assertEquals(Set.of(BuiltinContract.SEQUENCE),
                inference.contract((Ast.FunctionDef) program.get(1)).resultGuarantees());
        assertEquals(Set.of(BuiltinContract.DICTIONARY),
                inference.contract((Ast.FunctionDef) program.get(2)).resultGuarantees());
        assertEquals(Set.of(BuiltinContract.DICTIONARY),
                inference.contract((Ast.FunctionDef) program.get(3)).resultGuarantees());
    }

    @Test
    void preservesGenericParameterResultFlowAndInfersNumericConstraints() {
        List<Ast.Stmt> program = new Parser("""
                identity value =
                  value

                increment value =
                  value * 1 + 1
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);
        Ast.FunctionDef identity = (Ast.FunctionDef) program.get(0);
        Ast.FunctionDef increment = (Ast.FunctionDef) program.get(1);
        assertEquals(new ContractInference.FunctionContract(List.of(Set.of()), List.of(Set.of()),
                Set.of(), Set.of(), 0, false),
                inference.contract(identity));
        assertEquals(new ContractInference.FunctionContract(List.of(Set.of(BuiltinContract.NUMBER)),
                List.of(Set.of(BuiltinContract.NUMBER)), Set.of(BuiltinContract.NUMBER),
                Set.of(BuiltinContract.NUMBER), null, false),
                inference.contract(increment));
    }

    @Test
    void joinsAlternativeResultsByCommonGuarantees() {
        List<Ast.Stmt> program = new Parser("""
                choose condition =
                  condition & 1 ! 2

                mixed condition =
                  condition & 1 ! "two"
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);
        Ast.FunctionDef choose = (Ast.FunctionDef) program.get(0);
        Ast.FunctionDef mixed = (Ast.FunctionDef) program.get(1);
        assertEquals(Set.of(), inference.contract(choose).parameterRequirements().getFirst());
        assertEquals(Set.of(BuiltinContract.NUMBER), inference.contract(choose).resultGuarantees());
        assertEquals(Set.of(), inference.contract(mixed).resultGuarantees());
    }

    @Test
    void propagatesContractsThroughNamedCalls() {
        List<Ast.Stmt> program = new Parser("""
                identity value =
                  value

                increment value =
                  identity value * 1
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);
        Ast.FunctionDef increment = (Ast.FunctionDef) program.get(1);
        assertEquals(Set.of(BuiltinContract.NUMBER), inference.contract(increment).parameterRequirements().getFirst());
        assertEquals(Set.of(BuiltinContract.NUMBER), inference.contract(increment).resultGuarantees());
    }

    @Test
    void rejectsImpossibleBuiltInConjunctions() {
        LangException error = assertThrows(LangException.class, () -> ContractInference.analyze(new Parser("""
                impossible (String) value =
                  value * 1
                """).parseProgram()));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, error.diagnostic().code());
        assertEquals(2, error.diagnostic().primarySpan().start().line());
    }

    @Test
    void validatesInferredNeedsAndGuaranteesAgainstExplicitInterfaces() {
        LangException strengthening = assertThrows(LangException.class,
                () -> ContractInference.analyze(new Parser("""
                        numeric (Any) value =
                          value * 2
                        """).parseProgram()));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, strengthening.diagnostic().code());
        assertEquals(2, strengthening.diagnostic().primarySpan().start().line());

        LangException guarantee = assertThrows(LangException.class,
                () -> ContractInference.analyze(new Parser("""
                        (Number) invalid =
                          "text"
                        """).parseProgram()));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, guarantee.diagnostic().code());
        assertEquals(1, guarantee.diagnostic().primarySpan().start().line());

        List<Ast.Stmt> program = new Parser("""
                numeric (Number) value =
                  value * 2
                """).parseProgram();
        ContractInference.FunctionContract contract = ContractInference.analyze(program)
                .contract((Ast.FunctionDef) program.getFirst());
        assertEquals(List.of(Set.of(BuiltinContract.NUMBER)), contract.parameterRequirements());
        assertEquals(List.of(Set.of(BuiltinContract.NUMBER)), contract.inferredParameterRequirements());
        assertEquals(Set.of(BuiltinContract.NUMBER), contract.resultGuarantees());
    }

    @Test
    void preservesDeclaredDomainsWithoutInventingImplementationNeeds() {
        List<Ast.Stmt> program = new Parser("""
                (Number) constant (Number) ignored =
                  1
                """).parseProgram();
        ContractInference.FunctionContract contract = ContractInference.analyze(program)
                .contract((Ast.FunctionDef) program.getFirst());
        assertEquals(List.of(Set.of(BuiltinContract.NUMBER)), contract.parameterRequirements());
        assertEquals(List.of(Set.of()), contract.inferredParameterRequirements());
        assertEquals(Set.of(BuiltinContract.NUMBER), contract.resultGuarantees());
    }

    @Test
    void nullableAndOptionalRequirementsFlowThroughKnownCalls() {
        ContractInference.analyze(new Parser("""
                identity (Number?~) value = value
                nullable = identity ?
                optional = identity ~
                numeric = identity 1
                """).parseProgram());

        LangException error = assertThrows(LangException.class, () -> ContractInference.analyze(new Parser("""
                numeric (Number?) value = value
                invalid = numeric "wrong"
                """).parseProgram()));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, error.diagnostic().code());

        LangException possibleNull = assertThrows(LangException.class,
                () -> ContractInference.analyze(new Parser("""
                        maybe (Number?) value = value
                        invalid = maybe ? * 2
                        """).parseProgram()));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, possibleNull.diagnostic().code());

        for (String modifier : List.of("?", "~", "?~")) {
            LangException unsafeBody = assertThrows(LangException.class,
                    () -> ContractInference.analyze(new Parser("""
                            unsafe (Number%s) value = value * 2
                            """.formatted(modifier)).parseProgram()));
            assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS,
                    unsafeBody.diagnostic().code(), modifier);
        }
    }

    @Test
    void rejectsAnUnconstrainedGenericResultAtAnOrdinaryBindingUse() {
        LangException error = assertThrows(LangException.class, () -> ContractInference.analyze(new Parser("""
                mixed condition =
                  condition & 1 ! "two"

                value = mixed true
                """).parseProgram()));
        assertEquals(Diagnostic.Codes.AMBIGUOUS_CONTRACT, error.diagnostic().code());
        assertEquals(4, error.diagnostic().primarySpan().start().line());
    }

    @Test
    void followsRuntimeStringUnaryAndTruthSemantics() {
        List<Ast.Stmt> program = new Parser("""
                suffix value =
                  value + "!"

                negate value =
                  not value

                choose condition =
                  condition & 1 ! 2

                text = suffix "ok"
                negated = negate true
                chosenNull = choose ?
                chosenMissing = choose ~
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);
        Ast.FunctionDef suffix = (Ast.FunctionDef) program.get(0);
        Ast.FunctionDef negate = (Ast.FunctionDef) program.get(1);
        assertEquals(Set.of(BuiltinContract.STRING), inference.contract(suffix).resultGuarantees());
        assertEquals(Set.of(BuiltinContract.BOOLEAN), inference.contract(negate).resultGuarantees());
    }

    @Test
    void resolvesAnEarlierGenericBindingFromLaterContext() {
        ContractInference.analyze(new Parser("""
                mixed condition =
                  condition & 1 ! "two"

                contextual condition =
                  local = mixed condition
                  local * 1
                """).parseProgram());
    }

    @Test
    void checksKnownCallsInExpressionStatements() {
        LangException error = assertThrows(LangException.class, () -> ContractInference.analyze(new Parser("""
                numeric value =
                  value * 2

                numeric "wrong"
                """).parseProgram()));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, error.diagnostic().code());
    }

    @Test
    void infersPureKnownAndUnknownEffectsTransitively() {
        List<Ast.Stmt> program = new Parser("""
                pureValue value =
                  value * 2

                emit value =
                  print value

                emitThrough value =
                  emit value

                invoke callable value =
                  callable value

                capture value =
                  print _
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);

        assertEquals(ContractInference.EffectSummary.PURE,
                inference.effects((Ast.FunctionDef) program.get(0)));
        assertEquals(Set.of(ContractInference.BuiltinEffect.OUTPUT),
                inference.effects((Ast.FunctionDef) program.get(1)).effects());
        assertEquals(Set.of(ContractInference.BuiltinEffect.OUTPUT),
                inference.effects((Ast.FunctionDef) program.get(2)).effects());
        assertTrue(inference.effects((Ast.FunctionDef) program.get(3)).unknownDynamicCall());
        assertEquals(ContractInference.EffectSummary.PURE,
                inference.effects((Ast.FunctionDef) program.get(4)));
    }

    @Test
    void includesNestedNamedCallsInEnclosingFunctionEffects() {
        List<Ast.Stmt> program = new Parser("""
                enclosing value =
                  nested item =
                    print item
                  nested value
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);
        assertEquals(Set.of(ContractInference.BuiltinEffect.OUTPUT),
                inference.effects((Ast.FunctionDef) program.getFirst()).effects());
    }

    @Test
    void validatesOnlyProvenPureUnaryBooleanRefinements() {
        List<Ast.Stmt> program = new Parser("""
                positive value = value > 0
                wrongArity left right = left == right
                wrongResult value = value + 1
                emitting value = print (value > 0)
                dynamic predicate value = predicate value
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);

        inference.validateRefinement((Ast.FunctionDef) program.getFirst());
        for (int index = 1; index < program.size(); index++) {
            Ast.FunctionDef candidate = (Ast.FunctionDef) program.get(index);
            LangException error = assertThrows(LangException.class,
                    () -> inference.validateRefinement(candidate));
            assertEquals(Diagnostic.Codes.INVALID_REFINEMENT, error.diagnostic().code());
            assertEquals(index + 1, error.diagnostic().primarySpan().start().line());
        }
    }

    @Test
    void lexicalShadowsNeverInheritOuterCallablePurity() {
        List<Ast.Stmt> program = new Parser("""
                known value = value > 0

                parameterShadow known value =
                  known value

                builtinShadow print value =
                  print value

                assignmentShadow value =
                  known = value
                  known value
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);

        for (int index = 1; index < program.size(); index++) {
            Ast.FunctionDef candidate = (Ast.FunctionDef) program.get(index);
            assertTrue(inference.effects(candidate).unknownDynamicCall(), candidate.name());
        }
        LangException error = assertThrows(LangException.class,
                () -> inference.validateRefinement((Ast.FunctionDef) program.get(1)));
        assertEquals(Diagnostic.Codes.INVALID_REFINEMENT, error.diagnostic().code());
    }

    @Test
    void reflectingANullaryFunctionDoesNotInvokeOrAcquireItsEffects() {
        List<Ast.Stmt> program = new Parser("""
                emit =
                  print "effect"

                inspect value =
                  (@emit).remaining == 0

                evaluateThenReflect value =
                  (@(emit)).kind == "Missing"
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);

        Ast.FunctionDef inspect = (Ast.FunctionDef) program.get(1);
        Ast.FunctionDef evaluateThenReflect = (Ast.FunctionDef) program.get(2);
        assertEquals(ContractInference.EffectSummary.PURE, inference.effects(inspect));
        inference.validateRefinement(inspect);
        assertEquals(Set.of(ContractInference.BuiltinEffect.OUTPUT),
                inference.effects(evaluateThenReflect).effects());
    }

    @Test
    void recognizesBuiltinsResolvedThroughTheInterpreterGlobalScope() {
        List<Ast.Stmt> program = new Parser("""
                emit value =
                  print value
                """).parseProgram();
        Environment globals = new Environment(null);
        globals.define("print", new Value.FunctionValue("print", List.of("value"),
                ignored -> Value.Missing.INSTANCE));
        Resolution resolution = Resolver.resolve(program, globals);

        ContractInference inference = ContractInference.analyze(program, resolution);
        assertEquals(Set.of(ContractInference.BuiltinEffect.OUTPUT),
                inference.effects((Ast.FunctionDef) program.getFirst()).effects());
    }

    @Test
    void partialConstructionIncludesEffectsOfEagerHoleFreeSubexpressions() {
        List<Ast.Stmt> program = new Parser("""
                pair left right = left
                buildsPartial value =
                  pair _ (print value)
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);
        assertEquals(Set.of(ContractInference.BuiltinEffect.OUTPUT),
                inference.effects((Ast.FunctionDef) program.get(1)).effects());
    }

    @Test
    void overApplicationOfAKnownCallableAddsUnknownResultCallEffects() {
        List<Ast.Stmt> program = new Parser("""
                identity value = value
                over value = identity value value
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);
        assertTrue(inference.effects((Ast.FunctionDef) program.get(1)).unknownDynamicCall());
    }

    @Test
    void overloadEffectsUnionTransitivelyAndParticipateInRecursiveFixedPoints() {
        List<Ast.Stmt> program = new Parser("""
                visit (Number) value = visit "done"
                visit (String) value = print value
                caller value = visit value

                uncertain (Number) value = dynamic value
                uncertain (String) value = value
                uncertainCaller value = uncertain value

                (Boolean) candidate value = visit value
                """).parseProgram();
        ContractInference inference = ContractInference.analyze(program);

        for (int index : List.of(0, 1, 2, 6)) {
            assertEquals(Set.of(ContractInference.BuiltinEffect.OUTPUT),
                    inference.effects((Ast.FunctionDef) program.get(index)).effects());
        }
        assertTrue(inference.effects((Ast.FunctionDef) program.get(5)).unknownDynamicCall());

        Ast.FunctionDef candidate = (Ast.FunctionDef) program.get(6);
        LangException error = assertThrows(LangException.class, () -> inference.validateRefinement(candidate));
        assertEquals(Diagnostic.Codes.INVALID_REFINEMENT, error.diagnostic().code());
        assertTrue(error.getMessage().contains("observable effects"));
    }
}
