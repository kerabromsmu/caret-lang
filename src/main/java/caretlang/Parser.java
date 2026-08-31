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

        return parseNonDefinition(line, indent, tokens);
    }

    private void synchronizeDeclaration(int declarationStart, int indent) {
        lineIndex = Math.max(lineIndex, declarationStart + 1);
        while (lineIndex < lines.size() && lines.get(lineIndex).indent() > indent) lineIndex++;
    }

    private Stmt parseLine(LogicalLine line, int indent) {
        return parseLine(line, indent, null);
    }

    private Stmt parseNonDefinition(LogicalLine line, int indent, List<Token> tokens) {
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
                ordinary = new ExprParser(tokens.subList(0, tokens.size() - 1),
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
        return new ExprParser(tokens, end, continuationArguments(baseIndent)).parse();
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

    private record ContractParse(ContractClause clause, int next) {}
    private record ContractNameParse(ContractName name, int next) {}

    private ContractParse contractClause(List<Token> tokens, int start) {
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
            Expr inline = new ExprParser(tokens.subList(start + 1, close), tokens.get(close).span().start()).parse();
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

    private ContractNameParse contractName(List<Token> tokens, int start) {
        Token token = tokens.get(start);
        if (token.kind() != Kind.IDENT) {
            throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                    "Contract clause requires contract names", token.span());
        }
        int current = start + 1;
        ArrayList<ContractName> arguments = new ArrayList<>();
        int arity = LanguageSyntax.contractParameterArity(token.text());
        for (int index = 0; index < arity && current < tokens.size()
                && !tokens.get(current).text().equals(")"); index++) {
            if (tokens.get(current).text().equals("(")) {
                ContractParse grouped = contractClause(tokens, current);
                if (Objects.requireNonNull(grouped).clause().names().size() != 1) {
                    throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                            "A contract parameter must be one contract", grouped.clause().span());
                }
                arguments.add(grouped.clause().names().getFirst());
                current = grouped.next();
            } else {
                ContractNameParse argument = contractName(tokens, current);
                arguments.add(argument.name());
                current = argument.next();
            }
        }
        boolean nullable = false;
        boolean optional = false;
        SourceSpan end = arguments.isEmpty() ? token.span() : arguments.getLast().span();
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
        return new ContractNameParse(new ContractName(token.text(), List.copyOf(arguments), nullable, optional,
                SourceSpan.cover(token.span(), end)), current);
    }

    private static boolean adjacent(Token left, Token right) {
        return adjacent(left.span(), right);
    }

    private static boolean adjacent(SourceSpan left, Token right) {
        return left.end().offset() == right.span().start().offset();
    }

    private SourceSpan functionSpan(List<Token> header, List<Stmt> body) {
        return functionSpan(header, body, header.getLast().span());
    }

    private SourceSpan functionSpan(List<Token> header, List<Stmt> body, SourceSpan emptyBodyEnd) {
        SourceSpan end = body.isEmpty() ? emptyBodyEnd : body.getLast().span();
        return SourceSpan.cover(header.getFirst().span(), end);
    }

    private void requireBindable(List<Token> names) {
        names.forEach(this::requireBindable);
    }

    private void requireBindable(Token token) {
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
            if (t.equals("(") || t.equals("[")) depth++;
            else if (t.equals(")") || t.equals("]")) depth--;
            else if (t.equals("=") && depth == 0) return i;
        }
        return -1;
    }

    private LangException error(LogicalLine line, String code, String message) {
        return new LangException(Diagnostic.Phase.PARSER, code,
                message + "\n  " + line.text(), line.span());
    }

    private static final class ExprParser {
        private final List<Token> tokens;
        private final List<Expr> continuationArguments;
        private int current;

        ExprParser(List<Token> tokens) {
            this(tokens, tokens.isEmpty()
                    ? new SourcePosition(0, 1, 1)
                    : tokens.getLast().span().end(), List.of());
        }

        ExprParser(List<Token> tokens, SourcePosition end) {
            this(tokens, end, List.of());
        }

        ExprParser(List<Token> tokens, SourcePosition end, List<Expr> continuationArguments) {
            this.tokens = new ArrayList<>(tokens);
            this.tokens.add(new Token(Kind.EOF, "", SourceSpan.point(end)));
            this.continuationArguments = List.copyOf(continuationArguments);
        }

        Expr parse() {
            Expr expression = arrow();
            if (!atEnd()) throw error("Unexpected token: " + peek().text());
            return expression;
        }

        private Expr arrow() {
            if (peek().text().equals("[") && arrowClose(current) >= 0) {
                Token open = tokens.get(current++);
                ArrayList<List<Expr>> parameters = new ArrayList<>();
                while (!peek().text().equals("]")) {
                    if (atEnd()) throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ']'");
                    if (match("(")) {
                        ArrayList<Expr> conjunction = new ArrayList<>();
                        while (!peek().text().equals(")")) {
                            if (atEnd()) throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ')'");
                            conjunction.add(contractRequirement());
                        }
                        consume(")", "Expected ')'");
                        if (conjunction.isEmpty()) throw error(Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                                "Arrow parameter requirement cannot be empty");
                        parameters.add(List.copyOf(conjunction));
                    } else {
                        parameters.add(List.of(contractRequirement()));
                    }
                }
                consume("]", "Expected ']'");
                consume("->", "Expected '->' after arrow parameter requirements");
                Expr result;
                ArrayList<Name> effectTerms = new ArrayList<>();
                boolean explicitPure = false;
                if (peek().text().equals("[") && arrowClose(current) >= 0) {
                    result = arrow();
                } else if (match("(")) {
                    ArrayList<Expr> resultRequirements = new ArrayList<>();
                    while (!peek().text().equals(")")) {
                        if (atEnd()) throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ')'");
                        if (peek().kind() == Kind.IDENT && isEffectSpelling(peek().text())) {
                            Token effect = tokens.get(current++);
                            if (effect.text().equals("pure")) explicitPure = true;
                            else effectTerms.add(new Name(effect.text(), effect.span()));
                        } else resultRequirements.add(contractRequirement());
                    }
                    consume(")", "Expected ')'");
                    if (resultRequirements.size() != 1) throw error(Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                            "Arrow contract requires exactly one result contract");
                    result = resultRequirements.getFirst();
                } else {
                    result = contractRequirement();
                }
                return new ArrowContract(List.copyOf(parameters), result, effectTerms, explicitPure,
                        SourceSpan.cover(open.span(), result.span()));
            }
            return lowPrecedenceApplication();
        }

        private boolean isEffectSpelling(String name) {
            return name.equals("pure") || name.equals("Output") || name.equals("StateRead")
                    || name.equals("StateWrite") || name.equals("TestReport");
        }

        /** Returns the matching close only when it is immediately followed by an arrow. */
        private int arrowClose(int start) {
            int depth = 0;
            for (int index = start; index < tokens.size(); index++) {
                String text = tokens.get(index).text();
                if (text.equals("[") || text.equals("(")) depth++;
                else if (text.equals("]") || text.equals(")")) {
                    depth--;
                    if (depth == 0) {
                        return text.equals("]") && index + 1 < tokens.size()
                                && tokens.get(index + 1).text().equals("->") ? index : -1;
                    }
                }
            }
            return -1;
        }

        private Expr contractRequirement() {
            if (peek().text().equals("_")) {
                throw error(Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                        "Unnumbered contract variable is invalid");
            }
            if (peek().kind() != Kind.IDENT) {
                throw error(Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                        "Arrow contract requires contract names");
            }
            Token name = tokens.get(current++);
            Expr requirement = name.text().matches("_[1-9][0-9]*")
                    ? numberedContractVariable(name) : new Name(name.text(), name.span());
            int arity = name.text().startsWith("_") ? 0 : LanguageSyntax.contractParameterArity(name.text());
            for (int index = 0; index < arity; index++) {
                Expr argument = contractRequirement();
                requirement = new Apply(requirement, argument,
                        SourceSpan.cover(requirement.span(), argument.span()));
            }
            SourceSpan end = requirement.span();
            boolean nullable = false;
            boolean optional = false;
            if (peek().text().equals("?") && adjacent(end, peek())) {
                nullable = true;
                end = tokens.get(current++).span();
            }
            if (peek().text().equals("~") && adjacent(end, peek())) {
                optional = true;
                end = tokens.get(current++).span();
            }
            return nullable || optional ? new ContractModifier(requirement, nullable, optional,
                    SourceSpan.cover(requirement.span(), end)) : requirement;
        }

        private Expr numberedContractVariable(Token token) {
            try {
                return new ContractVariable(Integer.parseInt(token.text().substring(1)), token.span());
            } catch (NumberFormatException ignored) {
                throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_HOLE,
                        "Numbered contract variable index is too large", token.span());
            }
        }

        private Expr lowPrecedenceApplication() {
            Expr function = composition();
            if (!match("$")) return function;
            Expr argument = lowPrecedenceApplication();
            return new Apply(function, argument, SourceSpan.cover(function.span(), argument.span()));
        }

        private Expr composition() {
            Expr expression = conditional();
            while (match(">>")) {
                Expr right = conditional();
                expression = new Compose(expression, right,
                        SourceSpan.cover(expression.span(), right.span()));
            }
            return expression;
        }

        private Expr conditional() {
            Expr condition = or();
            if (match("&")) {
                Expr yes = conditionalBranch();
                Expr no = match("!") ? conditional()
                        : new Literal(Value.Missing.INSTANCE, SourceSpan.point(yes.span().end()));
                return new Conditional(condition, yes, no, SourceSpan.cover(condition.span(), no.span()));
            }
            return condition;
        }

        private Expr conditionalBranch() {
            // Parse up to the matching ! at this nesting level.
            return or();
        }

        private Expr or() {
            Expr expr = and();
            while (matchIdent("or")) {
                Expr right = and();
                expr = new Binary("or", expr, right, SourceSpan.cover(expr.span(), right.span()));
            }
            return expr;
        }

        private Expr and() {
            Expr expr = equality();
            while (matchIdent("and")) {
                Expr right = equality();
                expr = new Binary("and", expr, right, SourceSpan.cover(expr.span(), right.span()));
            }
            return expr;
        }

        private Expr equality() {
            Expr expr = comparison();
            while (matchOperators(LanguageSyntax.Precedence.EQUALITY)) {
                String op = previous().text();
                Expr right = comparison();
                expr = new Binary(op, expr, right, SourceSpan.cover(expr.span(), right.span()));
            }
            return expr;
        }

        private Expr comparison() {
            Expr expr = namedInfix();
            while (matchOperators(LanguageSyntax.Precedence.COMPARISON)) {
                String op = previous().text();
                Expr right = namedInfix();
                expr = new Binary(op, expr, right, SourceSpan.cover(expr.span(), right.span()));
            }
            return expr;
        }

        private Expr namedInfix() {
            Expr expr = term(true);
            while (peek().kind() == Kind.IDENT && LanguageSyntax.canBeNamedInfix(peek().text())
                    && canStartAtom(peekNext()) && !nextTokenIsAdjacentContractModifier()) {
                Token function = tokens.get(current++);
                Expr right = term(true);
                expr = new NamedInfix(expr, new Name(function.text(), function.span()), right,
                        SourceSpan.cover(expr.span(), right.span()));
            }
            return expr;
        }

        private Expr term() {
            return term(false);
        }

        private Expr term(boolean namedInfixOperand) {
            Expr expr = factor(namedInfixOperand);
            while (matchOperators(LanguageSyntax.Precedence.ADDITIVE)) {
                String op = previous().text();
                Expr right = factor(namedInfixOperand);
                expr = new Binary(op, expr, right, SourceSpan.cover(expr.span(), right.span()));
            }
            return expr;
        }

        private Expr factor() {
            return factor(false);
        }

        private Expr factor(boolean namedInfixOperand) {
            Expr expr = unary(namedInfixOperand);
            while (matchOperators(LanguageSyntax.Precedence.MULTIPLICATIVE)) {
                String op = previous().text();
                Expr right = unary(namedInfixOperand);
                expr = new Binary(op, expr, right, SourceSpan.cover(expr.span(), right.span()));
            }
            return expr;
        }

        private Expr unary() {
            return unary(false);
        }

        private Expr unary(boolean namedInfixOperand) {
            if (peek().text().equals("-") && !prefixOrReferenceMinus() && match("-")) {
                Token operator = previous();
                Expr operand = unary();
                return new Unary("-", operand, SourceSpan.cover(operator.span(), operand.span()));
            }
            if (matchIdent("not")) {
                Token operator = previous();
                Expr operand = unary();
                return new Unary("not", operand, SourceSpan.cover(operator.span(), operand.span()));
            }
            return application(namedInfixOperand);
        }

        private Expr application(boolean namedInfixOperand) {
            Expr expr = postfix();
            if (namedInfixOperand && expr instanceof Name
                    && peek().kind() == Kind.IDENT
                    && LanguageSyntax.canBeNamedInfix(peek().text()) && canStartAtom(peekNext())
                    && !nextTokenIsAdjacentContractModifier()) {
                Expr middle = postfix();
                Expr last = postfix();
                expr = new AmbiguousCall(expr, middle, last, SourceSpan.cover(expr.span(), last.span()));
            }
            while (canStartAtom(peek())) {
                if (namedInfixOperand && isValueLed(expr)
                        && peek().kind() == Kind.IDENT && LanguageSyntax.canBeNamedInfix(peek().text())
                        && canStartAtom(peekNext()) && !nextTokenIsAdjacentContractModifier()) break;
                Expr argument = postfix();
                expr = new Apply(expr, argument, SourceSpan.cover(expr.span(), argument.span()));
            }
            if (atEnd()) {
                for (Expr argument : continuationArguments) {
                    expr = new Apply(expr, argument, SourceSpan.cover(expr.span(), argument.span()));
                }
            }
            return expr;
        }

        private boolean isValueLed(Expr expression) {
            if (expression instanceof Name) return true;
            return !(expression instanceof Apply) && !(expression instanceof Group);
        }

        private Token peekNext() {
            int next = Math.min(current + 1, tokens.size() - 1);
            return tokens.get(next);
        }

        private boolean nextTokenIsAdjacentContractModifier() {
            Token currentToken = peek();
            Token nextToken = peekNext();
            return (nextToken.text().equals("?") || nextToken.text().equals("~"))
                    && currentToken.span().end().offset() == nextToken.span().start().offset();
        }

        private Expr postfix() {
            Expr expr = primary();
            while (true) {
                if (match(".")) {
                    if (match("@")) {
                        Token marker = previous();
                        Token name = consume(Kind.IDENT, "Expected field name after '.@'");
                        Expr field = new Field(expr, name.text(), false, SourceSpan.cover(expr.span(), name.span()));
                        expr = new Reflect(field, SourceSpan.cover(expr.span(), name.span()));
                        continue;
                    }
                    Token name = consume(Kind.IDENT, "Expected field name after '.'");
                    boolean optional = match("~");
                    SourceSpan end = optional ? previous().span() : name.span();
                    expr = new Field(expr, name.text(), optional, SourceSpan.cover(expr.span(), end));
                    continue;
                }
                if (peek().text().equals(":")
                        && expr.span().end().offset() == peek().span().start().offset()) {
                    match(":");
                    expr = new Dereference(expr, SourceSpan.cover(expr.span(), previous().span()));
                    continue;
                }
                if (peek().text().equals("[")
                        && expr.span().end().offset() == peek().span().start().offset() && match("[")) {
                    if (atEnd()) {
                        throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ']'");
                    }
                    Expr name = lowPrecedenceApplication();
                    consume("]", "Expected ']'");
                    Token close = previous();
                    boolean optional = match("~");
                    SourceSpan end = optional ? previous().span() : close.span();
                    expr = new DynamicField(expr, name, optional, SourceSpan.cover(expr.span(), end));
                    continue;
                }
                if ((peek().text().equals("?") || peek().text().equals("~"))
                        && expr.span().end().offset() == peek().span().start().offset()) {
                    boolean nullable = false;
                    boolean optional = false;
                    SourceSpan modifierEnd = expr.span();
                    if (match("?")) {
                        nullable = true;
                        modifierEnd = previous().span();
                    }
                    if (peek().text().equals("~")
                            && modifierEnd.end().offset() == peek().span().start().offset()) {
                        match("~");
                        optional = true;
                        modifierEnd = previous().span();
                    }
                    if (!nullable && !optional) break;
                    if ((peek().text().equals("?") || peek().text().equals("~"))
                            && modifierEnd.end().offset() == peek().span().start().offset()) {
                        throw error(Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                                "Contract clause modifiers must use canonical form T, T?, T~, or T?~");
                    }
                    expr = new ContractModifier(expr, nullable, optional,
                            SourceSpan.cover(expr.span(), modifierEnd));
                    continue;
                }
                break;
            }
            return expr;
        }

        private Expr primary() {
            if (match("@")) {
                Token operator = previous();
                Expr operand = reflectionPrimary();
                return new Reflect(operand, SourceSpan.cover(operator.span(), operand.span()));
            }
            if (peek().kind() == Kind.SYMBOL
                    && LanguageSyntax.binaryOperatorSpellings().contains(peek().text())) {
                Token operator = tokens.get(current++);
                return new Name(operator.text(), operator.span());
            }
            if (matchKind(Kind.NUMBER)) return numberLiteral(previous());
            if (matchKind(Kind.STRING)) return new Literal(new Value.Str(previous().text()), previous().span());
            if (matchIdent("true")) return new Literal(new Value.Bool(true), previous().span());
            if (matchIdent("false")) return new Literal(new Value.Bool(false), previous().span());
            if (match("?")) return new Literal(Value.Null.INSTANCE, previous().span());
            if (match("~")) return new Literal(Value.Missing.INSTANCE, previous().span());
            if (match("[")) {
                Token open = previous();
                boolean multiline = collectionCloseLine() > open.span().start().line();
                ArrayList<CollectionElement> elements = new ArrayList<>();
                while (!peek().text().equals("]")) {
                    if (atEnd()) throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ']'");
                    if (match("^")) {
                        Token marker = previous();
                        if (peek().kind() != Kind.IDENT) {
                            throw error(Diagnostic.Codes.PARSE_INVALID_SYNTAX,
                                    "Named collection element requires a field name");
                        }
                        Token name = tokens.get(current++);
                        consume("=", "Expected '=' after named collection field");
                        Expr value = multiline ? collectionLineExpression() : lowPrecedenceApplication();
                        SourceSpan span = SourceSpan.cover(marker.span(), value.span());
                        elements.add(new NamedElement(name.text(), value, span));
                        continue;
                    }
                    // In a multiline literal, each top-level physical line is one ordinary
                    // expression. Same-line literals retain eager atom boundaries.
                    Expr value = multiline ? collectionLineExpression()
                            : hasTopLevelOperatorBeforeCollectionEnd()
                            ? lowPrecedenceApplication() : postfix();
                    if (!multiline && value instanceof Group
                            && (peek().text().equals("_")
                            || peek().kind() == Kind.IDENT
                            && peek().text().matches("_[1-9][0-9]*"))) {
                        Expr hole = primary();
                        value = new Apply(value, hole, SourceSpan.cover(value.span(), hole.span()));
                    }
                    elements.add(new PositionalElement(value, value.span()));
                }
                consume("]", "Expected ']'");
                return new CollectionLiteral(List.copyOf(elements),
                        SourceSpan.cover(open.span(), previous().span()));
            }
            if (matchIdent("_")) return new Hole(0, previous().span());
            if (peek().kind() == Kind.IDENT && peek().text().matches("_[1-9][0-9]*")) {
                Token hole = tokens.get(current++);
                final int index;
                try {
                    index = Integer.parseInt(hole.text().substring(1));
                } catch (NumberFormatException ignored) {
                    throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_HOLE,
                            "Numbered hole index is too large", hole.span());
                }
                return new Hole(index, hole.span());
            }
            if (matchKind(Kind.IDENT)) return new Name(previous().text(), previous().span());
            if (match("(")) {
                Token open = previous();
                if (atEnd()) {
                    throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ')'");
                }
                Expr expr = arrow();
                consume(")", "Expected ')'");
                return new Group(expr, SourceSpan.cover(open.span(), previous().span()));
            }
            throw error("Expected expression, found '" + peek().text() + "'");
        }

        private Expr reflectionPrimary() {
            Token token = peek();
            boolean allowed = token.kind() == Kind.IDENT || token.kind() == Kind.NUMBER
                    || token.kind() == Kind.STRING || token.text().equals("true")
                    || token.text().equals("false") || token.text().equals("?")
                    || token.text().equals("~") || token.text().equals("(")
                    || token.text().equals("[");
            if (!allowed) {
                throw error(Diagnostic.Codes.PARSE_INVALID_EXPRESSION,
                        "Expected an identifier, literal, or parenthesized expression after '@'");
            }
            return primary();
        }

        private int collectionCloseLine() {
            int depth = 0;
            for (int index = current; index < tokens.size(); index++) {
                String text = tokens.get(index).text();
                if (text.equals("[") || text.equals("(")) depth++;
                else if (text.equals("]") || text.equals(")")) {
                    if (depth == 0 && text.equals("]")) return tokens.get(index).span().start().line();
                    depth--;
                }
            }
            return openEndedLine();
        }

        private Expr collectionLineExpression() {
            int start = current;
            int depth = 0;
            int end = start;
            for (; end < tokens.size(); end++) {
                Token token = tokens.get(end);
                String text = token.text();
                if (depth == 0) {
                    if (text.equals("]")) break;
                    if (end > start
                            && token.span().start().line() > tokens.get(end - 1).span().end().line()) break;
                }
                if (text.equals("[") || text.equals("(")) depth++;
                else if (text.equals("]") || text.equals(")")) depth--;
            }
            if (end == start) throw error("Expected collection element expression");
            SourcePosition expressionEnd = tokens.get(end - 1).span().end();
            Expr expression = new ExprParser(tokens.subList(start, end), expressionEnd).parse();
            current = end;
            return expression;
        }

        private int openEndedLine() {
            return tokens.getLast().span().end().line();
        }

        private boolean hasTopLevelOperatorBeforeCollectionEnd() {
            int depth = 0;
            for (int index = current; index < tokens.size(); index++) {
                String text = tokens.get(index).text();
                if (text.equals("(") || text.equals("[")) depth++;
                else if (text.equals(")") || text.equals("]")) {
                    if (depth == 0) return false;
                    depth--;
                } else if (depth == 0 && (text.equals("$") || text.equals("&") || text.equals(">>")
                        || LanguageSyntax.binaryOperatorSpellings().contains(text)
                        || text.equals("and") || text.equals("or"))) return true;
            }
            return false;
        }

        private Expr numberLiteral(Token token) {
            final double value;
            try {
                value = Double.parseDouble(token.text());
            } catch (NumberFormatException ignored) {
                throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_NUMBER,
                        "Invalid number literal", token.span());
            }
            if (!Double.isFinite(value)) {
                throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_NUMBER,
                        "Number literal is outside the finite range", token.span());
            }
            return new Literal(new Value.Num(value), token.span());
        }

        private boolean canStartAtom(Token token) {
            if (token.kind() == Kind.NUMBER || token.kind() == Kind.STRING) return true;
            if (token.kind() == Kind.IDENT) {
                return LanguageSyntax.canStartApplicationArgument(token.text());
            }
            return Set.of("(", "[", "?", "~", "@").contains(token.text());
        }

        private boolean prefixOrReferenceMinus() {
            int operands = continuationArguments.size();
            int index = current + 1;
            Token firstOperand = index < tokens.size() ? tokens.get(index) : tokens.getLast();
            while (index < tokens.size() && tokens.get(index).kind() != Kind.EOF
                    && !LanguageSyntax.binaryOperatorSpellings().contains(tokens.get(index).text())) {
                int end = postfixAtomEnd(index);
                if (end == index) break;
                operands++;
                index = end;
            }
            if (operands == 0) return true;
            boolean namedFirstOperand = firstOperand.kind() == Kind.IDENT
                    && !firstOperand.text().equals("_")
                    && !firstOperand.text().matches("_[1-9][0-9]*");
            return operands >= 2 && !namedFirstOperand;
        }

        /** Returns the index after one atom and its postfix accesses, or the input index if absent. */
        private int postfixAtomEnd(int index) {
            if (index >= tokens.size()) return index;
            Token token = tokens.get(index);
            int end;
            if (token.text().equals("(")) {
                int depth = 1;
                end = index + 1;
                while (end < tokens.size() && depth > 0) {
                    String text = tokens.get(end++).text();
                    if (text.equals("(")) depth++;
                    else if (text.equals(")")) depth--;
                }
                if (depth != 0) return index;
            } else if (canStartAtom(token)) {
                end = index + 1;
            } else {
                return index;
            }
            while (end < tokens.size()) {
                if (tokens.get(end).text().equals(".") && end + 1 < tokens.size()) {
                    end += 2;
                    if (end < tokens.size() && tokens.get(end).text().equals("~")) end++;
                } else if (tokens.get(end).text().equals("[")) {
                    int depth = 1;
                    end++;
                    while (end < tokens.size() && depth > 0) {
                        String text = tokens.get(end++).text();
                        if (text.equals("[")) depth++;
                        else if (text.equals("]")) depth--;
                    }
                    if (depth != 0) return index;
                    if (end < tokens.size() && tokens.get(end).text().equals("~")) end++;
                } else {
                    break;
                }
            }
            return end;
        }

        private boolean match(String... texts) {
            for (String text : texts) {
                if (peek().text().equals(text)) { current++; return true; }
            }
            return false;
        }

        private boolean matchOperators(LanguageSyntax.Precedence precedence) {
            if (!LanguageSyntax.operatorsAt(precedence).contains(peek().text())) return false;
            current++;
            return true;
        }

        private boolean matchIdent(String text) {
            if (peek().kind() == Kind.IDENT && peek().text().equals(text)) { current++; return true; }
            return false;
        }

        private boolean matchKind(Kind kind) {
            if (peek().kind() == kind) { current++; return true; }
            return false;
        }

        private Token consume(Kind kind, String message) {
            if (peek().kind() == kind) return tokens.get(current++);
            throw error(codeForExpectedDelimiter(message), message);
        }

        private void consume(String text, String message) {
            if (!match(text)) throw error(codeForExpectedDelimiter(message), message);
        }

        private String codeForExpectedDelimiter(String message) {
            return message.startsWith("Expected ')'") || message.startsWith("Expected ']'")
                    ? Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER
                    : Diagnostic.Codes.PARSE_INVALID_EXPRESSION;
        }

        private Token peek() { return tokens.get(current); }
        private Token previous() { return tokens.get(current - 1); }
        private boolean atEnd() { return peek().kind() == Kind.EOF; }
        private LangException error(String message) {
            return error(Diagnostic.Codes.PARSE_INVALID_EXPRESSION, message);
        }
        private LangException error(String code, String message) {
            return new LangException(Diagnostic.Phase.PARSER, code, message, peek().span());
        }
    }
}
