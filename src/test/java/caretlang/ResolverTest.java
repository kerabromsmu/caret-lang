package caretlang;

import caretlang.Ast.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ResolverTest {
    @Test
    void resolvesBuiltInContractsAndRejectsUnknownAnnotations() {
        List<Stmt> program = new Parser("identity (Any) value = value").parseProgram();
        FunctionDef function = assertInstanceOf(FunctionDef.class, program.getFirst());
        Resolution resolution = Resolver.resolve(program, new Environment(null));
        assertEquals(List.of("Any"), resolution.contracts(function.params().getFirst().contracts())
                .stream().map(Resolution.ContractBinding::name).toList());

        LangException unknown = assertThrows(LangException.class, () -> Resolver.resolve(
                new Parser("identity (Unknown) value = value").parseProgram(), new Environment(null)));
        assertEquals(Diagnostic.Phase.SEMANTIC, unknown.diagnostic().phase());
        assertEquals(Diagnostic.Codes.UNKNOWN_CONTRACT, unknown.diagnostic().code());
        assertEquals(11, unknown.span().start().column());
    }

    @Test
    void resolvesParametersAndPredeclaredMutualFunctionsToLexicalSlots() {
        List<Stmt> program = new Parser("""
                even n = n == 0 & true ! odd (n - 1)
                odd n = n == 0 & false ! even (n - 1)
                """).parseProgram();
        Resolution resolution = Resolver.resolve(program, new Environment(null));

        FunctionDef even = assertInstanceOf(FunctionDef.class, program.getFirst());
        Conditional body = assertInstanceOf(Conditional.class,
                assertInstanceOf(ExprStmt.class, even.body().getFirst()).expression());
        Name parameter = assertInstanceOf(Name.class,
                assertInstanceOf(Binary.class, body.condition()).left());
        Name mutualCall = assertInstanceOf(Name.class,
                assertInstanceOf(Apply.class, body.whenFalse()).function());

        Resolution.Binding parameterBinding = resolution.binding(parameter);
        assertEquals(1, parameterBinding.lexicalDepth());
        assertEquals(0, parameterBinding.slot());
        assertFalse(parameterBinding.captured());
        Resolution.Binding mutualBinding = resolution.binding(mutualCall);
        assertEquals(2, mutualBinding.lexicalDepth());
        assertEquals(1, mutualBinding.slot());
        assertTrue(mutualBinding.captured());
        assertEquals(2, mutualBinding.declarationSpan().start().line());
    }

    @Test
    void initializerCanReadAnOuterBindingBeforeItsOwnBindingBecomesVisible() {
        List<Stmt> program = new Parser("""
                make value =
                  ^value = value
                """).parseProgram();
        Resolution resolution = Resolver.resolve(program, new Environment(null));
        FunctionDef make = assertInstanceOf(FunctionDef.class, program.getFirst());
        Assign export = assertInstanceOf(Assign.class, make.body().getFirst());
        Name parameter = assertInstanceOf(Name.class, export.value());

        Resolution.Binding binding = resolution.binding(parameter);
        assertEquals(1, binding.lexicalDepth());
        assertEquals(0, binding.slot());
        assertFalse(binding.captured());
    }

    @Test
    void nestedFunctionsRecordCapturedLexicalDepth() {
        List<Stmt> program = new Parser("""
                outer captured =
                  inner argument = captured + argument
                  inner
                """).parseProgram();
        Resolution resolution = Resolver.resolve(program, new Environment(null));
        FunctionDef outer = assertInstanceOf(FunctionDef.class, program.getFirst());
        FunctionDef inner = assertInstanceOf(FunctionDef.class, outer.body().getFirst());
        Binary sum = assertInstanceOf(Binary.class,
                assertInstanceOf(ExprStmt.class, inner.body().getFirst()).expression());

        Resolution.Binding captured = resolution.binding(assertInstanceOf(Name.class, sum.left()));
        assertEquals(3, captured.lexicalDepth());
        assertEquals(0, captured.slot());
        assertTrue(captured.captured());
        Resolution.Binding argument = resolution.binding(assertInstanceOf(Name.class, sum.right()));
        assertEquals(1, argument.lexicalDepth());
        assertEquals(0, argument.slot());
        assertFalse(argument.captured());
    }

    @Test
    void rejectsPrematureReadsButLeavesUnknownNamesForLazyRuntimeEvaluation() {
        LangException premature = assertThrows(LangException.class, () -> Resolver.resolve(
                new Parser("first = second\nsecond = 2").parseProgram(), new Environment(null)));
        assertEquals(Diagnostic.Phase.SEMANTIC, premature.diagnostic().phase());
        assertEquals(Diagnostic.Codes.READ_BEFORE_INITIALIZATION, premature.diagnostic().code());

        List<Stmt> lazy = new Parser("true & 1 ! absent").parseProgram();
        Resolution resolution = Resolver.resolve(lazy, new Environment(null));
        Conditional conditional = assertInstanceOf(Conditional.class,
                assertInstanceOf(ExprStmt.class, lazy.getFirst()).expression());
        assertNull(resolution.binding(assertInstanceOf(Name.class, conditional.whenFalse())));
    }
}
