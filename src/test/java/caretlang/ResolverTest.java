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
    void rejectsResolvedBindingsThatAreNotContracts() {
        LangException error = assertThrows(LangException.class, () -> Resolver.resolve(
                new Parser("value = 1\n(value) other = 2").parseProgram(), new Environment(null)));
        assertEquals(Diagnostic.Phase.SEMANTIC, error.diagnostic().phase());
        assertEquals(Diagnostic.Codes.NOT_A_CONTRACT, error.diagnostic().code());
        assertEquals(2, error.span().start().line());
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
                enclosing captured =
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
    void recordsDeterministicDeduplicatedUpvaluesByStableSymbolIdentity() {
        List<Stmt> program = new Parser("""
                enclosing left =
                  local = 10
                  first right = left + local + right + left
                  second left = first left
                  first
                """).parseProgram();
        Resolution resolution = Resolver.resolve(program, new Environment(null));
        FunctionDef enclosing = assertInstanceOf(FunctionDef.class, program.getFirst());
        FunctionDef first = assertInstanceOf(FunctionDef.class, enclosing.body().get(1));
        FunctionDef second = assertInstanceOf(FunctionDef.class, enclosing.body().get(2));

        List<Resolution.Upvalue> firstCaptures = resolution.upvalues(first);
        assertEquals(2, firstCaptures.size());
        assertEquals(List.of(0, 1), firstCaptures.stream().map(Resolution.Upvalue::index).toList());
        assertEquals(List.of(1, 0), firstCaptures.stream().map(Resolution.Upvalue::lexicalDepth).toList());
        assertEquals(List.of(0, 0), firstCaptures.stream().map(Resolution.Upvalue::slot).toList());
        assertEquals(List.of(1, 2), firstCaptures.stream()
                .map(capture -> capture.declarationSpan().start().line()).toList());
        assertEquals(List.of(3, 3), firstCaptures.stream()
                .map(capture -> capture.firstUseSpan().start().line()).toList());
        assertNotEquals(firstCaptures.get(0).symbolId(), firstCaptures.get(1).symbolId());

        List<Resolution.Upvalue> secondCaptures = resolution.upvalues(second);
        assertEquals(1, secondCaptures.size());
        assertEquals(resolution.symbolId(first.span()), secondCaptures.getFirst().symbolId());
        assertEquals(0, secondCaptures.getFirst().lexicalDepth());
        assertEquals(1, secondCaptures.getFirst().slot());
    }

    @Test
    void recordsRecursiveAndDeepCapturesWithoutPromotingScopesToValues() {
        List<Stmt> program = new Parser("""
                top captured =
                  recursive n = n == 0 & captured ! recursive (n - 1)
                  middle =
                    inner =
                      captured
                    inner
                  recursive
                """).parseProgram();
        Resolution resolution = Resolver.resolve(program, new Environment(null));
        FunctionDef top = assertInstanceOf(FunctionDef.class, program.getFirst());
        FunctionDef recursive = assertInstanceOf(FunctionDef.class, top.body().getFirst());
        FunctionDef middle = assertInstanceOf(FunctionDef.class, top.body().get(1));
        FunctionDef inner = assertInstanceOf(FunctionDef.class, middle.body().getFirst());

        List<Resolution.Upvalue> recursiveCaptures = resolution.upvalues(recursive);
        assertEquals(2, recursiveCaptures.size());
        assertEquals(1, recursiveCaptures.getFirst().lexicalDepth());
        assertEquals(0, recursiveCaptures.getFirst().slot());
        assertEquals(resolution.symbolId(recursive.span()), recursiveCaptures.get(1).symbolId());
        assertEquals(0, recursiveCaptures.get(1).lexicalDepth());

        Resolution.Upvalue deep = assertDoesNotThrow(() -> resolution.upvalues(inner).getFirst());
        assertEquals(3, deep.lexicalDepth());
        assertEquals(recursiveCaptures.getFirst().symbolId(), deep.symbolId());
        assertTrue(resolution.upvalues(top).isEmpty());
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
