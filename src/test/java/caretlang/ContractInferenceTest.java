package caretlang;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ContractInferenceTest {
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
        assertEquals(new ContractInference.FunctionContract(List.of(Set.of()), Set.of(), 0, false),
                inference.contract(identity));
        assertEquals(new ContractInference.FunctionContract(List.of(Set.of(BuiltinContract.NUMBER)),
                Set.of(BuiltinContract.NUMBER), null, false), inference.contract(increment));
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
}
