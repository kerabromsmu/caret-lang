package caretlang;

import caretlang.Ast.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ParserTest {
    @Test
    void parsesEagerCollectionLiteralsAsElementBoundaries() {
        CollectionLiteral literal = assertInstanceOf(CollectionLiteral.class, expression("[A B]"));
        assertEquals(List.of("A", "B"), literal.elements().stream()
                .map(Name.class::cast).map(Name::name).toList());
        assertTrue(assertInstanceOf(CollectionLiteral.class, expression("[]")).elements().isEmpty());
    }

    @Test
    void parsesBuiltInContractClausesOnBindingsAndParameters() {
        List<Stmt> program = new Parser("""
                (Number) count = 1
                add (Number) left (Number Any) right = left + right
                """).parseProgram();
        Assign binding = assertInstanceOf(Assign.class, program.getFirst());
        assertEquals("Number", binding.contracts().names().getFirst().name());
        assertEquals(1, binding.contracts().span().start().column());
        FunctionDef function = assertInstanceOf(FunctionDef.class, program.get(1));
        assertEquals(List.of("left", "right"), function.params().stream().map(Parameter::name).toList());
        assertEquals(2, function.params().get(1).contracts().names().size());
        assertEquals(5, function.params().getFirst().contracts().span().start().column());
    }

    @Test
    void rejectsMalformedContractClausesAndParsesResultContracts() {
        LangException empty = assertThrows(LangException.class,
                () -> new Parser("() value = 1").parseProgram());
        assertEquals(Diagnostic.Codes.PARSE_INVALID_CONTRACT, empty.diagnostic().code());
        FunctionDef result = assertInstanceOf(FunctionDef.class,
                new Parser("(Number) add left right = left + right").parseProgram().getFirst());
        assertEquals("Number", result.resultContracts().names().getFirst().name());
    }

    @Test
    void constructorDoesNotPreparseLaterHeadersOrReorderDiagnostics() {
        Parser parser = new Parser("print )\n(Number) identity value = value");
        LangException error = assertThrows(LangException.class, parser::parseProgram);
        assertEquals(1, error.span().start().line());
    }

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
    void parsesCompositionAsTheLowestPrecedenceLeftAssociativeOperator() {
        Compose pipeline = assertInstanceOf(Compose.class, expression("first >> second >> third"));
        assertInstanceOf(Compose.class, pipeline.left());
        assertEquals("third", assertInstanceOf(Name.class, pipeline.right()).name());

        Compose conditionalPipeline = assertInstanceOf(Compose.class,
                expression("true & first ! second >> third"));
        assertInstanceOf(Conditional.class, conditionalPipeline.left());

        Apply call = assertInstanceOf(Apply.class, expression("(first >> second) value"));
        Compose grouped = assertInstanceOf(Compose.class,
                assertInstanceOf(Group.class, call.function()).expression());
        assertEquals(2, grouped.span().start().column());
        assertEquals(17, grouped.span().end().column());
    }

    @Test
    void parsesDollarAsRightAssociativeLowestPrecedenceApplication() {
        Apply outer = assertInstanceOf(Apply.class, expression("a $ b $ c"));
        assertEquals("a", assertInstanceOf(Name.class, outer.function()).name());
        Apply nested = assertInstanceOf(Apply.class, outer.argument());
        assertEquals("b", assertInstanceOf(Name.class, nested.function()).name());
        assertEquals("c", assertInstanceOf(Name.class, nested.argument()).name());

        Apply arithmetic = assertInstanceOf(Apply.class, expression("use $ 1 + 2 * 3"));
        assertInstanceOf(Binary.class, arithmetic.argument());

        Apply conditional = assertInstanceOf(Apply.class,
                expression("use $ valid & value ! fallback"));
        assertInstanceOf(Conditional.class, conditional.argument());

        Apply composition = assertInstanceOf(Apply.class, expression("use $ parse >> validate"));
        assertInstanceOf(Compose.class, composition.argument());

        Apply appliedFunction = assertInstanceOf(Apply.class, expression("map values $ transform item"));
        assertInstanceOf(Apply.class, appliedFunction.function());
        assertInstanceOf(Apply.class, appliedFunction.argument());
    }

    @Test
    void printDollarUsesOrdinaryLowPrecedenceApplication() {
        ExprStmt statement = assertInstanceOf(ExprStmt.class,
                new Parser("print $ add 1 2").parseProgram().getFirst());
        Apply print = assertInstanceOf(Apply.class, statement.expression());
        assertEquals("print", assertInstanceOf(Name.class, print.function()).name());
        Apply add = assertInstanceOf(Apply.class, print.argument());
        assertInstanceOf(Apply.class, add.function());
    }

    @Test
    void rejectsDollarWithoutARightOperandAtTheOperator() {
        LangException error = assertThrows(LangException.class,
                () -> new Parser("identity $").parseProgram());
        assertEquals(Diagnostic.Codes.PARSE_INVALID_EXPRESSION, error.diagnostic().code());
        assertEquals(11, error.span().start().column());
    }

    @Test
    void parsesNamedInfixAtItsFixedPrecedence() {
        NamedInfix call = assertInstanceOf(NamedInfix.class, expression("2 combine 3 + 4"));
        assertEquals("combine", assertInstanceOf(Name.class, call.function()).name());
        assertInstanceOf(Binary.class, call.right());

        Binary comparison = assertInstanceOf(Binary.class, expression("2 combine 3 < 10"));
        assertInstanceOf(NamedInfix.class, comparison.left());

        NamedInfix chained = assertInstanceOf(NamedInfix.class, expression("1 combine 2 combine 3"));
        assertInstanceOf(NamedInfix.class, chained.left());
    }

    @Test
    void parsesSymbolicOperatorsAsPrefixCallableValues() {
        for (String operator : List.of("+", "*", "/", "%", "==", "!=", ">", ">=", "<", "<=")) {
            Apply outer = assertInstanceOf(Apply.class, expression(operator + " 6 2"));
            Apply inner = assertInstanceOf(Apply.class, outer.function());
            assertEquals(operator, assertInstanceOf(Name.class, inner.function()).name());
        }

        Apply subtraction = assertInstanceOf(Apply.class, expression("- 6 2"));
        assertEquals("-", assertInstanceOf(Name.class,
                assertInstanceOf(Apply.class, subtraction.function()).function()).name());

        Unary establishedUnary = assertInstanceOf(Unary.class, expression("- identity 5"));
        assertInstanceOf(Apply.class, establishedUnary.operand());
        Apply groupedSubtraction = assertInstanceOf(Apply.class, expression("(-) left right"));
        assertInstanceOf(Group.class,
                assertInstanceOf(Apply.class, groupedSubtraction.function()).function());
    }

    @Test
    void parsesPrefixOperatorsWithMultilineArguments() {
        Assign assignment = assertInstanceOf(Assign.class, new Parser("""
                result = +
                  2
                  3
                """).parseProgram().getFirst());
        Apply call = assertInstanceOf(Apply.class, assignment.value());
        assertInstanceOf(Apply.class, call.function());
        assertEquals(3, call.span().end().line());
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
    void printTreatsANamedInfixExpressionAsItsSingleArgument() {
        ExprStmt statement = assertInstanceOf(ExprStmt.class,
                new Parser("print 2 add 3").parseProgram().getFirst());
        Apply print = assertInstanceOf(Apply.class, statement.expression());
        assertEquals("print", assertInstanceOf(Name.class, print.function()).name());
        assertInstanceOf(NamedInfix.class, print.argument());
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
    void rejectsExcessiveExpressionNesting() {
        String source = "value = " + "(".repeat(20_000) + "1" + ")".repeat(20_000);
        LangException error = assertThrows(LangException.class, () -> new Parser(source).parseProgram());
        assertEquals(DiagnosticCatalog.PARSE_NESTING_DEPTH, error.catalogEntry());
        assertEquals("Maximum expression nesting depth exceeded", error.detail());
        assertEquals("Line 1, column 1: Maximum expression nesting depth exceeded", error.getMessage());
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
                () -> new Parser("value = scope[\"name\"").parseProgram());
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
    void rejectsReservedBindingAndParameterNames() {
        for (String name : List.of("true", "false", "and", "or", "not", "_")) {
            LangException binding = assertThrows(LangException.class,
                    () -> new Parser(name + " = 1").parseProgram());
            assertEquals(Diagnostic.Codes.PARSE_RESERVED_BINDING, binding.diagnostic().code());
        }

        LangException parameter = assertThrows(LangException.class,
                () -> new Parser("identity true = true").parseProgram());
        assertEquals(Diagnostic.Codes.PARSE_RESERVED_BINDING, parameter.diagnostic().code());
        assertEquals(10, parameter.span().start().column());
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
    void parsesIndentedUngroupedArgumentsAsNestedApplications() {
        List<Stmt> program = new Parser("""
                result = add
                  1
                  multiply
                    2
                    3
                """).parseProgram();
        Assign assignment = assertInstanceOf(Assign.class, program.getFirst());
        Apply add = assertInstanceOf(Apply.class, assignment.value());
        assertInstanceOf(Apply.class, add.function());
        Apply multiply = assertInstanceOf(Apply.class, add.argument());
        assertInstanceOf(Apply.class, multiply.function());
        assertEquals(1, assignment.span().start().line());
        assertEquals(5, assignment.span().end().line());
    }

    @Test
    void multilineArgumentsRetainApplicationPrecedence() {
        Assign conditionalAssignment = assertInstanceOf(Assign.class, new Parser("""
                result = true & add
                  1
                  2
                """).parseProgram().getFirst());
        Conditional conditional = assertInstanceOf(Conditional.class, conditionalAssignment.value());
        Apply conditionalCall = assertInstanceOf(Apply.class, conditional.whenTrue());
        assertInstanceOf(Apply.class, conditionalCall.function());

        Assign infixAssignment = assertInstanceOf(Assign.class, new Parser("""
                result = 1 + add
                  2
                  3
                """).parseProgram().getFirst());
        Binary addition = assertInstanceOf(Binary.class, infixAssignment.value());
        Apply rightCall = assertInstanceOf(Apply.class, addition.right());
        assertInstanceOf(Apply.class, rightCall.function());
    }

    @Test
    void blankAndCommentLinesDoNotEndUngroupedContinuation() {
        Assign assignment = assertInstanceOf(Assign.class, new Parser("""
                result = add
                  1

                  // still the same call
                  2
                """).parseProgram().getFirst());
        assertInstanceOf(Apply.class, assignment.value());
        assertEquals(5, assignment.span().end().line());
    }

    @Test
    void emptyDefinitionRightSideTakesPrecedenceOverContinuation() {
        FunctionDef function = assertInstanceOf(FunctionDef.class, new Parser("""
                calculate =
                  value = 1
                  value
                """).parseProgram().getFirst());
        assertEquals(2, function.body().size());
        assertInstanceOf(Assign.class, function.body().getFirst());
    }

    @Test
    void acceptsTabsAsContinuationIndentation() {
        Assign assignment = assertInstanceOf(Assign.class,
                new Parser("result = identity\n\t42").parseProgram().getFirst());
        assertInstanceOf(Apply.class, assignment.value());
        assertEquals(2, assignment.span().end().line());
    }

    @Test
    void rejectsDefinitionsAndInconsistentIndentationInsideContinuation() {
        LangException definition = assertThrows(LangException.class, () -> new Parser("""
                result = add
                  value = 1
                """).parseProgram());
        assertTrue(definition.getMessage().contains("Continuation argument must be an expression"));
        assertEquals(2, definition.span().start().line());

        LangException indentation = assertThrows(LangException.class, () -> new Parser("""
                result = add
                    1
                  2
                """).parseProgram());
        assertTrue(indentation.getMessage().contains("Inconsistent continuation indentation"));
        assertEquals(3, indentation.span().start().line());
    }

    @Test
    void continuationIndentationDoesNotCreateABlock() {
        List<Stmt> program = new Parser("""
                result = source[
                    "field"
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
