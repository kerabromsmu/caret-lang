package caretlang;

import caretlang.Ast.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ParserTest {
    @Test
    void parsesApplicationMoreTightlyThanAddition() {
        Expr expression = expression("f x + g y");
        Binary addition = assertInstanceOf(Binary.class, expression);
        assertInstanceOf(Apply.class, addition.left());
        assertInstanceOf(Apply.class, addition.right());
        assertEquals(new SourcePosition(0, 1, 1), expression.span().start());
        assertEquals(new SourcePosition(9, 1, 10), expression.span().end());
    }

    @Test
    void printStatementConsumesTheRemainingExpression() {
        ExprStmt statement = assertInstanceOf(ExprStmt.class,
                new Parser("print add 2 3").parseProgram().getFirst());
        Apply print = assertInstanceOf(Apply.class, statement.expression());
        Name name = assertInstanceOf(Name.class, print.function());
        assertEquals("print", name.name());
        Apply add = assertInstanceOf(Apply.class, print.argument());
        assertInstanceOf(Apply.class, add.function());
    }

    @Test
    void retainsGroupingSpan() {
        Group group = assertInstanceOf(Group.class, expression("(add 1 2)"));
        assertInstanceOf(Apply.class, group.expression());
        assertEquals(1, group.span().start().column());
        assertEquals(10, group.span().end().column());
    }

    @Test
    void givesImplicitMissingAZeroWidthSpan() {
        Conditional conditional = assertInstanceOf(Conditional.class, expression("false & 42"));
        Literal missing = assertInstanceOf(Literal.class, conditional.whenFalse());
        assertSame(Value.Missing.INSTANCE, missing.value());
        assertEquals(missing.span().start(), missing.span().end());
        assertEquals(11, missing.span().start().column());
    }

    @Test
    void parsesNameValueAtBeginningOfLine() {
        Literal literal = assertInstanceOf(Literal.class, expression("#count"));
        assertEquals(new Value.Name("count"), literal.value());
    }

    @Test
    void multilineFunctionSpanCoversItsBody() {
        List<Stmt> program = new Parser("make n =\n  hidden = n\n  ^value = hidden\n").parseProgram();
        FunctionDef function = assertInstanceOf(FunctionDef.class, program.getFirst());
        assertEquals(1, function.span().start().line());
        assertEquals(3, function.span().end().line());
        assertEquals(18, function.span().end().column());
    }

    @Test
    void parserErrorsIncludeLineAndColumn() {
        LangException error = assertThrows(LangException.class, () -> new Parser("value = (1 + )").parseProgram());
        assertEquals(1, error.span().start().line());
        assertEquals(14, error.span().start().column());
    }

    @Test
    void rejectsMissingConditionalTrueBranch() {
        LangException error = assertThrows(LangException.class,
                () -> new Parser("value = true & ! false").parseProgram());
        assertEquals(16, error.span().start().column());
        assertTrue(error.getMessage().contains("Expected expression"));
    }

    @Test
    void rejectsUnclosedGrouping() {
        LangException error = assertThrows(LangException.class,
                () -> new Parser("value = (1 + 2").parseProgram());
        assertTrue(error.getMessage().contains("Expected ')'"));
    }

    @Test
    void rejectsMalformedFieldAndDynamicLookups() {
        LangException field = assertThrows(LangException.class,
                () -> new Parser("value = scope.").parseProgram());
        assertTrue(field.getMessage().contains("Expected field name"));

        LangException dynamic = assertThrows(LangException.class,
                () -> new Parser("value = scope[#name").parseProgram());
        assertTrue(dynamic.getMessage().contains("Expected ']'"));
    }

    @Test
    void rejectsMalformedDefinitionsAndIndentation() {
        LangException definition = assertThrows(LangException.class,
                () -> new Parser("add a =").parseProgram());
        assertTrue(definition.getMessage().contains("Function body must be indented"));

        LangException indentation = assertThrows(LangException.class,
                () -> new Parser("  value = 1").parseProgram());
        assertTrue(indentation.getMessage().contains("Unexpected indentation"));
    }

    @Test
    void rejectsMalformedOperatorsReflectionAndExports() {
        LangException operator = assertThrows(LangException.class,
                () -> new Parser("value = true and").parseProgram());
        assertTrue(operator.getMessage().contains("Expected expression"));

        LangException reflection = assertThrows(LangException.class,
                () -> new Parser("print @").parseProgram());
        assertTrue(reflection.getMessage().contains("Expected expression"));

        LangException export = assertThrows(LangException.class,
                () -> new Parser("^ = 1").parseProgram());
        assertTrue(export.getMessage().contains("Invalid assignment or function definition"));
    }

    @Test
    void rejectsNonFiniteNumberLiteralsAsLocatedParserDiagnostics() {
        String huge = "9".repeat(400);
        LangException error = assertThrows(LangException.class,
                () -> new Parser("value = " + huge).parseProgram());
        assertEquals(Diagnostic.Phase.PARSER, error.diagnostic().phase());
        assertEquals(9, error.span().start().column());
        assertTrue(error.getMessage().contains("outside the finite range"));
    }

    @Test
    void parsesMultilineExpressionsInsideExplicitGrouping() {
        List<Stmt> program = new Parser("""
                result = (
                  add
                    1
                    2
                )
                """).parseProgram();
        Assign assignment = assertInstanceOf(Assign.class, program.getFirst());
        Group group = assertInstanceOf(Group.class, assignment.value());
        Apply outer = assertInstanceOf(Apply.class, group.expression());
        assertInstanceOf(Apply.class, outer.function());
        assertEquals(1, assignment.span().start().line());
        assertEquals(5, assignment.span().end().line());
    }

    @Test
    void continuationIndentationDoesNotCreateABlock() {
        List<Stmt> program = new Parser("""
                result = source[
                    #field
                  ]~
                next = 2
                """).parseProgram();
        Assign first = assertInstanceOf(Assign.class, program.getFirst());
        DynamicField field = assertInstanceOf(DynamicField.class, first.value());
        assertTrue(field.optional());
        assertEquals(2, program.size());
    }

    @Test
    void multilineParseErrorsPointAtTheFinalPhysicalLine() {
        LangException error = assertThrows(LangException.class, () -> new Parser("""
                value = (
                  add 1 2
                """).parseProgram());
        assertEquals(3, error.span().start().line());
        assertEquals(1, error.span().start().column());
        assertTrue(error.getMessage().contains("Expected ')'"));
    }

    private Expr expression(String source) {
        ExprStmt statement = assertInstanceOf(ExprStmt.class, new Parser(source).parseProgram().getFirst());
        return statement.expression();
    }
}
