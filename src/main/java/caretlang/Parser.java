package caretlang;

import caretlang.Ast.*;
import caretlang.Lexer.Kind;
import caretlang.Lexer.LogicalLine;
import caretlang.Lexer.Token;

import java.util.*;

final class Parser {
    /** Analysis-facing parse output. Invalid declarations are omitted from the recovered program. */
    record ParseResult(List<Stmt> statements, List<Diagnostic> diagnostics) {
        ParseResult {
            statements = List.copyOf(statements);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record DefinitionHeader(String name, ContractClause contracts,
                                    List<Parameter> parameters, boolean exported) {}
    private final List<LogicalLine> lines;
    private int lineIndex;

    Parser(String source) {
        this.lines = Lexer.logicalLines(source);
    }

    List<Stmt> parseProgram() {
        try {
            return parseBlock(0);
        } catch (StackOverflowError exhaustedStack) {
            SourceSpan span = lines.isEmpty()
                    ? SourceSpan.point(new SourcePosition(0, 1, 1))
                    : lines.get(Math.min(lineIndex, lines.size() - 1)).span();
            throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_EXPRESSION,
                    "Maximum expression nesting depth exceeded", span);
        }
    }

    /**
     * Parses as many independent declarations as possible. The interpreter intentionally continues
     * to use {@link #parseProgram()}, which reports the first failure.
     */
    ParseResult parseProgramRecovering() {
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        try {
            return new ParseResult(parseBlockRecovering(0, diagnostics), diagnostics);
        } catch (StackOverflowError exhaustedStack) {
            SourceSpan span = lines.isEmpty()
                    ? SourceSpan.point(new SourcePosition(0, 1, 1))
                    : lines.get(Math.min(lineIndex, lines.size() - 1)).span();
            diagnostics.add(new Diagnostic(Diagnostic.Phase.PARSER,
                    Diagnostic.Codes.PARSE_INVALID_EXPRESSION,
                    "Maximum expression nesting depth exceeded", span));
            return new ParseResult(List.of(), diagnostics);
        }
    }

    private List<Stmt> parseBlock(int indent) {
        ArrayList<Stmt> result = new ArrayList<>();
        while (lineIndex < lines.size()) {
            LogicalLine line = lines.get(lineIndex);
            if (line.indent() < indent) break;
            if (line.indent() > indent) {
                throw error(line, Diagnostic.Codes.PARSE_UNEXPECTED_INDENT, "Unexpected indentation");
            }
            result.add(parseLine(line, indent));
        }
        return result;
    }

    private List<Stmt> parseBlockRecovering(int indent, List<Diagnostic> diagnostics) {
        ArrayList<Stmt> result = new ArrayList<>();
        while (lineIndex < lines.size()) {
            LogicalLine line = lines.get(lineIndex);
            if (line.indent() < indent) break;

            int declarationStart = lineIndex;
            try {
                if (line.indent() > indent) {
                    throw error(line, Diagnostic.Codes.PARSE_UNEXPECTED_INDENT, "Unexpected indentation");
                }
                result.add(parseLine(line, indent, diagnostics));
            } catch (LangException failure) {
                if (failure.diagnostic().phase() != Diagnostic.Phase.PARSER) throw failure;
                diagnostics.add(failure.diagnostic());
                synchronizeDeclaration(declarationStart, indent);
            }
        }
        return List.copyOf(result);
    }

    private Stmt parseLine(LogicalLine line, int indent, List<Diagnostic> diagnostics) {
        lineIndex++;
        List<Token> tokens = Lexer.lex(line.text(), line.offset(), line.number(), line.column());

        int eq = topLevelEquals(tokens);
        if (eq >= 0) {
            List<Token> left = tokens.subList(0, eq);
            List<Token> right = tokens.subList(eq + 1, tokens.size() - 1);
            DefinitionHeader header = definitionHeader(left);
            if (header != null && !right.isEmpty()) {
                Expr expression = parseExpression(right, tokens.getLast().span().end(), indent);
                if (header.parameters().isEmpty()) {
                    return new Assign(header.name(), header.exported(), header.contracts(), expression,
                            SourceSpan.cover(left.getFirst().span(), expression.span()));
                }
                if (!header.exported()) {
                    ExprStmt expressionStatement = new ExprStmt(expression, expression.span());
                    return new FunctionDef(header.name(), header.contracts(), header.parameters(),
                            List.of(expressionStatement), SourceSpan.cover(left.getFirst().span(), expression.span()));
                }
            }
            if (header != null && right.isEmpty() && !header.exported()) {
                if (lineIndex >= lines.size() || lines.get(lineIndex).indent() <= indent) {
                    throw error(line, Diagnostic.Codes.PARSE_INVALID_SYNTAX, "Function body must be indented");
                }
                List<Stmt> body = diagnostics == null
                        ? parseBlock(lines.get(lineIndex).indent())
                        : parseBlockRecovering(lines.get(lineIndex).indent(), diagnostics);
                return new FunctionDef(header.name(), header.contracts(), header.parameters(), body,
                        functionSpan(left, body, line.span()));
            }
            throw error(line, Diagnostic.Codes.PARSE_INVALID_SYNTAX,
                    "Invalid assignment or function definition");
        }

        return parseNonDefinition(indent, tokens);
    }

    private void synchronizeDeclaration(int declarationStart, int indent) {
        lineIndex = Math.max(lineIndex, declarationStart + 1);
        while (lineIndex < lines.size() && lines.get(lineIndex).indent() > indent) lineIndex++;
    }

    private Stmt parseLine(LogicalLine line, int indent) {
        return parseLine(line, indent, null);
    }

    private Stmt parseNonDefinition(int indent, List<Token> tokens) {
        // Output is intentionally a statement form only when the line is not a definition.
        // This preserves the concise `print add 2 3` spelling without preventing `print`
        // from being shadowed as an ordinary binding or function name.
        if (tokens.size() > 2 && tokens.getFirst().kind() == Kind.IDENT
                && tokens.getFirst().text().equals("print") && !tokens.get(1).text().equals("$")) {
            Expr expression = parseExpression(tokens.subList(1, tokens.size() - 1),
                    tokens.getLast().span().end(), indent);
            Name print = new Name("print", tokens.getFirst().span());
            Expr call = new Apply(print, expression, SourceSpan.cover(print.span(), expression.span()));
            Expr ordinary;
            try {
                ordinary = new ExpressionParser(tokens.subList(0, tokens.size() - 1),
                        tokens.getLast().span().end()).parse();
            } catch (LangException unavailableOrdinaryParse) {
                // Delimited multiline syntax may only become complete through the continuation
                // consumed by the builtin argument parse. It cannot denote an ordinary shadowed
                // call in the current grammar, so retain the builtin-shaped fallback.
                ordinary = call;
            }
            return new PrintLine(print, expression, ordinary, call.span());
        }

        Expr expression = parseExpression(tokens.subList(0, tokens.size() - 1),
                tokens.getLast().span().end(), indent);
        return new ExprStmt(expression, expression.span());
    }

    private Expr parseExpression(List<Token> tokens, SourcePosition end, int baseIndent) {
        if (!tokens.isEmpty() && tokens.getFirst().kind() == Kind.IDENT
                && tokens.getFirst().text().equals("with")) {
            if (tokens.size() == 1) {
                throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_EXPRESSION,
                        "with requires a target value", tokens.getFirst().span());
            }
            if (lineIndex >= lines.size() || lines.get(lineIndex).indent() <= baseIndent) {
                throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_SYNTAX,
                        "with body must be indented", tokens.getFirst().span());
            }
            Expr target = new ExpressionParser(tokens.subList(1, tokens.size()), end).parse();
            List<Stmt> body = parseBlock(lines.get(lineIndex).indent());
            return new With(target, body, SourceSpan.cover(tokens.getFirst().span(), body.getLast().span()));
        }
        int lambdaArrow = lastTopLevelLambdaArrow(tokens);
        if (lambdaArrow == tokens.size() - 1) {
            if (lineIndex >= lines.size() || lines.get(lineIndex).indent() <= baseIndent) {
                throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_SYNTAX,
                        "Lambda body must follow '->' or be indented", tokens.get(lambdaArrow).span());
            }
            List<Stmt> body = parseBlock(lines.get(lineIndex).indent());
            return blockLambdaExpression(tokens, body);
        }
        return new ExpressionParser(tokens, end, continuationArguments(baseIndent)).parse();
    }

    private static Expr blockLambdaExpression(List<Token> tokens, List<Stmt> body) {
        int arrow = tokens.size() - 1;
        Token marker = tokens.get(arrow);
        List<Token> prefix = tokens.subList(0, arrow);
        if (isLambdaParameterPrefix(prefix, marker)) {
            List<Parameter> parameters = lambdaParameters(prefix, marker);
            return new Lambda(parameters, body, SourceSpan.cover(
                    parameters.isEmpty() ? marker.span() : parameters.getFirst().span(), body.getLast().span()));
        }
        int dollar = topLevelDollar(prefix);
        if (dollar >= 0) {
            if (dollar == 0) return invalidLambdaHeader(prefix, marker);
            Expr function = new ExpressionParser(prefix.subList(0, dollar), prefix.get(dollar).span().start()).parse();
            Expr argument = blockLambdaExpression(tokens.subList(dollar + 1, tokens.size()), body);
            return new Apply(function, argument, SourceSpan.cover(function.span(), argument.span()));
        }
        int nestedArrow = topLevelLambdaArrow(prefix);
        if (nestedArrow >= 0 && isLambdaParameterPrefix(prefix.subList(0, nestedArrow),
                prefix.get(nestedArrow))) {
            List<Parameter> parameters = lambdaParameters(prefix.subList(0, nestedArrow), prefix.get(nestedArrow));
            Expr nested = blockLambdaExpression(tokens.subList(nestedArrow + 1, tokens.size()), body);
            ExprStmt nestedBody = new ExprStmt(nested, nested.span());
            return new Lambda(parameters, List.of(nestedBody), SourceSpan.cover(
                    parameters.isEmpty() ? prefix.get(nestedArrow).span() : parameters.getFirst().span(),
                    nested.span()));
        }
        return invalidLambdaHeader(prefix, marker);
    }

    private static Expr invalidLambdaHeader(List<Token> prefix, Token marker) {
        lambdaParameters(prefix, marker);
        throw new AssertionError("Invalid lambda header unexpectedly parsed");
    }

    private static int topLevelDollar(List<Token> tokens) {
        int depth = 0;
        for (int index = 0; index < tokens.size(); index++) {
            String text = tokens.get(index).text();
            if (text.equals("(") || text.equals("[") || text.equals("{")) depth++;
            else if (text.equals(")") || text.equals("]") || text.equals("}")) depth--;
            else if (text.equals("$") && depth == 0) return index;
        }
        return -1;
    }

    static int topLevelLambdaArrow(List<Token> tokens) {
        int depth = 0;
        for (int index = 0; index < tokens.size(); index++) {
            String text = tokens.get(index).text();
            if (text.equals("(") || text.equals("[") || text.equals("{")) depth++;
            else if (text.equals(")") || text.equals("]") || text.equals("}")) depth--;
            else if (text.equals("->") && depth == 0) return index;
        }
        return -1;
    }

    private static int lastTopLevelLambdaArrow(List<Token> tokens) {
        int depth = 0;
        int result = -1;
        for (int index = 0; index < tokens.size(); index++) {
            String text = tokens.get(index).text();
            if (text.equals("(") || text.equals("[") || text.equals("{")) depth++;
            else if (text.equals(")") || text.equals("]") || text.equals("}")) depth--;
            else if (text.equals("->") && depth == 0) result = index;
        }
        return result;
    }

    static List<Parameter> lambdaParameters(List<Token> tokens, Token arrow) {
        ArrayList<Parameter> parameters = new ArrayList<>();
        int current = 0;
        while (current < tokens.size()) {
            ContractParse clause = contractClause(tokens, current);
            ContractClause contracts = clause == null ? null : clause.clause();
            if (clause != null) current = clause.next();
            if (contracts != null && contracts.names().stream().anyMatch(Parser::containsHoleContractName)) {
                throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                        "Lambda parameter contracts cannot contain expression holes", contracts.span());
            }
            if (current >= tokens.size() || tokens.get(current).kind() != Kind.IDENT) {
                Token problem = current < tokens.size() ? tokens.get(current) : arrow;
                throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_SYNTAX,
                        "Lambda parameters must be binding names", problem.span());
            }
            Token parameter = tokens.get(current++);
            requireBindable(parameter);
            parameters.add(new Parameter(parameter.text(), contracts,
                    contracts == null ? parameter.span() : SourceSpan.cover(contracts.span(), parameter.span())));
        }
        return List.copyOf(parameters);
    }

    private static boolean containsHoleContractName(ContractName name) {
        return name.name().equals("_") || name.arguments().stream().anyMatch(Parser::containsHoleContractName);
    }

    static boolean isLambdaParameterPrefix(List<Token> tokens, Token arrow) {
        try {
            lambdaParameters(tokens, arrow);
            return true;
        } catch (LangException ignored) {
            return false;
        }
    }

    private List<Expr> continuationArguments(int baseIndent) {
        if (lineIndex >= lines.size() || lines.get(lineIndex).indent() <= baseIndent) return List.of();
        int continuationIndent = lines.get(lineIndex).indent();
        ArrayList<Expr> arguments = new ArrayList<>();
        while (lineIndex < lines.size() && lines.get(lineIndex).indent() > baseIndent) {
            LogicalLine line = lines.get(lineIndex);
            if (line.indent() != continuationIndent) {
                throw error(line, Diagnostic.Codes.PARSE_UNEXPECTED_INDENT,
                        "Inconsistent continuation indentation");
            }
            arguments.add(parseContinuationArgument(line));
        }
        return List.copyOf(arguments);
    }

    private Expr parseContinuationArgument(LogicalLine line) {
        lineIndex++;
        List<Token> tokens = Lexer.lex(line.text(), line.offset(), line.number(), line.column());
        if (topLevelEquals(tokens) >= 0) {
            throw error(line, Diagnostic.Codes.PARSE_INVALID_SYNTAX,
                    "Continuation argument must be an expression");
        }
        return parseExpression(tokens.subList(0, tokens.size() - 1),
                tokens.getLast().span().end(), line.indent());
    }

    private DefinitionHeader definitionHeader(List<Token> tokens) {
        boolean exported = !tokens.isEmpty() && tokens.getFirst().text().equals("^");
        int current = exported ? 1 : 0;
        ContractParse leading = contractClause(tokens, current);
        if (leading != null) current = leading.next();
        if (current >= tokens.size() || tokens.get(current).kind() != Kind.IDENT) return null;
        Token name = tokens.get(current++);
        requireBindable(name);
        ArrayList<Parameter> parameters = new ArrayList<>();
        while (current < tokens.size()) {
            ContractParse clause = contractClause(tokens, current);
            ContractClause contracts = clause == null ? null : clause.clause();
            if (clause != null) current = clause.next();
            if (current >= tokens.size() || tokens.get(current).kind() != Kind.IDENT) return null;
            Token parameter = tokens.get(current++);
            requireBindable(parameter);
            parameters.add(new Parameter(parameter.text(), contracts,
                    contracts == null ? parameter.span() : SourceSpan.cover(contracts.span(), parameter.span())));
        }
        return new DefinitionHeader(name.text(), leading == null ? null : leading.clause(),
                List.copyOf(parameters), exported);
    }

    record ContractParse(ContractClause clause, int next) {}
    private record ContractNameParse(ContractName name, int next) {}

    static ContractParse contractClause(List<Token> tokens, int start) {
        if (start >= tokens.size() || !tokens.get(start).text().equals("(")) return null;
        if (start + 1 < tokens.size() && tokens.get(start + 1).text().equals("[")) {
            int depth = 1;
            int close = start + 1;
            for (; close < tokens.size(); close++) {
                if (tokens.get(close).text().equals("(")) depth++;
                else if (tokens.get(close).text().equals(")") && --depth == 0) break;
            }
            if (close >= tokens.size()) {
                throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                        "Expected ')' after arrow contract clause", tokens.get(start).span());
            }
            Expr inline = new ExpressionParser(tokens.subList(start + 1, close), tokens.get(close).span().start()).parse();
            if (!(inline instanceof ArrowContract)) {
                throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                        "Contract clause requires an arrow contract", inline.span());
            }
            SourceSpan span = SourceSpan.cover(tokens.get(start).span(), tokens.get(close).span());
            return new ContractParse(new ContractClause(List.of(new ContractName(
                    "<arrow>", List.of(), false, false, inline, inline.span())), span), close + 1);
        }
        ArrayList<ContractName> names = new ArrayList<>();
        int current = start + 1;
        while (current < tokens.size() && !tokens.get(current).text().equals(")")) {
            ContractNameParse parsed = contractName(tokens, current);
            names.add(parsed.name());
            current = parsed.next();
        }
        if (current >= tokens.size() || names.isEmpty()) {
            SourceSpan span = tokens.get(start).span();
            throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                    names.isEmpty() ? "Contract clause cannot be empty" : "Expected ')' after contract clause", span);
        }
        Token close = tokens.get(current++);
        return new ContractParse(new ContractClause(List.copyOf(names),
                SourceSpan.cover(tokens.get(start).span(), close.span())), current);
    }

    private static ContractNameParse contractName(List<Token> tokens, int start) {
        Token token = tokens.get(start);
        if (token.text().equals("(")) {
            ContractParse grouped = Objects.requireNonNull(contractClause(tokens, start));
            return modifiedContractName(new ContractName("<group>", grouped.clause().names(),
                    false, false, grouped.clause().span()), tokens, grouped.next());
        }
        if (token.kind() != Kind.IDENT) {
            throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                    "Contract clause requires contract names", token.span());
        }
        return modifiedContractName(new ContractName(token.text(), token.span()), tokens, start + 1);
    }

    private static ContractNameParse modifiedContractName(ContractName base, List<Token> tokens, int start) {
        int current = start;
        boolean nullable = false;
        boolean optional = false;
        SourceSpan end = base.span();
        if (current < tokens.size() && adjacent(end, tokens.get(current))
                && tokens.get(current).text().equals("?")) {
            nullable = true;
            end = tokens.get(current++).span();
        }
        if (current < tokens.size() && adjacent(end, tokens.get(current))
                && tokens.get(current).text().equals("~")) {
            optional = true;
            end = tokens.get(current++).span();
        }
        if (current < tokens.size() && adjacent(end, tokens.get(current))
                && (tokens.get(current).text().equals("?") || tokens.get(current).text().equals("~"))) {
            throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                    "Contract clause modifiers must use canonical form T, T?, T~, or T?~", tokens.get(current).span());
        }
        return new ContractNameParse(new ContractName(base.name(), base.arguments(), nullable, optional,
                base.inline(), SourceSpan.cover(base.span(), end)), current);
    }

    static boolean adjacent(SourceSpan left, Token right) {
        return left.end().offset() == right.span().start().offset();
    }

    private SourceSpan functionSpan(List<Token> header, List<Stmt> body, SourceSpan emptyBodyEnd) {
        SourceSpan end = body.isEmpty() ? emptyBodyEnd : body.getLast().span();
        return SourceSpan.cover(header.getFirst().span(), end);
    }

    private static void requireBindable(Token token) {
        String name = token.text();
        if (LanguageSyntax.isReservedBinding(name)) {
            throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_RESERVED_BINDING,
                    "Reserved spelling cannot be used as a binding name: " + name, token.span());
        }
    }

    private int topLevelEquals(List<Token> tokens) {
        int depth = 0;
        for (int i = 0; i < tokens.size() - 1; i++) {
            String t = tokens.get(i).text();
            if (t.equals("(") || t.equals("[") || t.equals("{")) depth++;
            else if (t.equals(")") || t.equals("]") || t.equals("}")) depth--;
            else if (t.equals("=") && depth == 0) return i;
        }
        return -1;
    }

    private LangException error(LogicalLine line, String code, String message) {
        return new LangException(Diagnostic.Phase.PARSER, code,
                message + "\n  " + line.text(), line.span());
    }

}
