package caretlang;

import caretlang.Ast.*;
import caretlang.Lexer.Kind;
import caretlang.Lexer.LogicalLine;
import caretlang.Lexer.Token;

import java.util.*;

final class Parser {
    private record DefinitionHeader(String name, List<String> parameters, boolean exported) {}
    private final List<LogicalLine> lines;
    private final Map<String, Integer> declaredArities;
    private int lineIndex;

    Parser(String source) {
        this.lines = Lexer.logicalLines(source);
        this.declaredArities = discoverDeclaredArities(lines);
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

    private Stmt parseLine(LogicalLine line, int indent) {
        lineIndex++;
        List<Token> tokens = Lexer.lex(line.text(), line.offset(), line.number(), line.column());

        // Output is intentionally a statement form: the complete remainder of the
        // line is its expression. This keeps ordinary whitespace application
        // left-associative while allowing the concise `print add 2 3` spelling.
        if (tokens.size() > 2 && tokens.getFirst().kind() == Kind.IDENT
                && tokens.getFirst().text().equals("print")
                && !tokens.get(1).text().equals("=")) {
            Expr expression = parseExpression(tokens.subList(1, tokens.size() - 1),
                    tokens.getLast().span().end(), indent);
            Expr print = new Name("print", tokens.getFirst().span());
            Expr call = new Apply(print, expression, SourceSpan.cover(print.span(), expression.span()));
            return new ExprStmt(call, call.span());
        }

        int eq = topLevelEquals(tokens);
        if (eq >= 0) {
            List<Token> left = tokens.subList(0, eq);
            List<Token> right = tokens.subList(eq + 1, tokens.size() - 1);
            DefinitionHeader header = definitionHeader(left);
            if (header != null && !right.isEmpty()) {
                Expr expression = parseExpression(right, tokens.getLast().span().end(), indent);
                if (header.parameters().isEmpty()) {
                    return new Assign(header.name(), header.exported(), expression,
                            SourceSpan.cover(left.getFirst().span(), expression.span()));
                }
                if (!header.exported()) {
                    ExprStmt expressionStatement = new ExprStmt(expression, expression.span());
                    return new FunctionDef(header.name(), header.parameters(), List.of(expressionStatement),
                            SourceSpan.cover(left.getFirst().span(), expression.span()));
                }
            }
            if (header != null && right.isEmpty() && !header.exported()) {
                if (lineIndex >= lines.size() || lines.get(lineIndex).indent() <= indent) {
                    throw error(line, Diagnostic.Codes.PARSE_INVALID_SYNTAX, "Function body must be indented");
                }
                List<Stmt> body = parseBlock(lines.get(lineIndex).indent());
                return new FunctionDef(header.name(), header.parameters(), body, functionSpan(left, body));
            }

            throw error(line, Diagnostic.Codes.PARSE_INVALID_SYNTAX,
                    "Invalid assignment or function definition");
        }

        Expr expression = parseExpression(tokens.subList(0, tokens.size() - 1),
                tokens.getLast().span().end(), indent);
        return new ExprStmt(expression, expression.span());
    }

    private Expr parseExpression(List<Token> tokens, SourcePosition end, int baseIndent) {
        return new ExprParser(tokens, end, continuationArguments(baseIndent), declaredArities).parse();
    }

    private static Map<String, Integer> discoverDeclaredArities(List<LogicalLine> lines) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (LogicalLine line : lines) {
            List<Token> tokens = Lexer.lex(line.text(), line.offset(), line.number(), line.column());
            int equals = -1;
            int depth = 0;
            for (int i = 0; i < tokens.size() - 1; i++) {
                String spelling = tokens.get(i).text();
                if (spelling.equals("(") || spelling.equals("[")) depth++;
                else if (spelling.equals(")") || spelling.equals("]")) depth--;
                else if (spelling.equals("=") && depth == 0) { equals = i; break; }
            }
            if (equals < 1) continue;
            int start = tokens.getFirst().text().equals("^") ? 1 : 0;
            List<Token> header = tokens.subList(start, equals);
            if (!header.isEmpty() && header.stream().allMatch(token -> token.kind() == Kind.IDENT)) {
                result.putIfAbsent(header.getFirst().text(), header.size() - 1);
            }
        }
        return Map.copyOf(result);
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
        int start = exported ? 1 : 0;
        if (tokens.size() <= start || !tokens.subList(start, tokens.size()).stream()
                .allMatch(token -> token.kind() == Kind.IDENT)) return null;
        List<Token> names = tokens.subList(start, tokens.size());
        requireBindable(names);
        return new DefinitionHeader(names.getFirst().text(),
                names.subList(1, names.size()).stream().map(Token::text).toList(), exported);
    }

    private SourceSpan functionSpan(List<Token> header, List<Stmt> body) {
        SourceSpan start = header.getFirst().span();
        return SourceSpan.cover(start, body.getLast().span());
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
        private final Map<String, Integer> declaredArities;
        private int current;

        ExprParser(List<Token> tokens) {
            this(tokens, tokens.isEmpty()
                    ? new SourcePosition(0, 1, 1)
                    : tokens.getLast().span().end(), List.of(), Map.of());
        }

        ExprParser(List<Token> tokens, SourcePosition end) {
            this(tokens, end, List.of(), Map.of());
        }

        ExprParser(List<Token> tokens, SourcePosition end, List<Expr> continuationArguments,
                   Map<String, Integer> declaredArities) {
            this.tokens = new ArrayList<>(tokens);
            this.tokens.add(new Token(Kind.EOF, "", SourceSpan.point(end)));
            this.continuationArguments = List.copyOf(continuationArguments);
            this.declaredArities = declaredArities;
        }

        Expr parse() {
            Expr expression = conditional();
            if (!atEnd()) throw error("Unexpected token: " + peek().text());
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
                    && canStartAtom(peekNext())) {
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
            if (match("@")) {
                Token operator = previous();
                Expr operand = unary();
                return new Reflect(operand, SourceSpan.cover(operator.span(), operand.span()));
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
            if (namedInfixOperand && expr instanceof Name name
                    && isUnknownCallable(name.name()) && peek().kind() == Kind.IDENT
                    && LanguageSyntax.canBeNamedInfix(peek().text()) && canStartAtom(peekNext())) {
                Expr middle = postfix();
                Expr last = postfix();
                expr = new AmbiguousCall(expr, middle, last, SourceSpan.cover(expr.span(), last.span()));
            }
            while (canStartAtom(peek())) {
                if (namedInfixOperand && isValueLed(expr)
                        && (!(expr instanceof Name) || isDeclaredInfixCandidate(peek()))
                        && peek().kind() == Kind.IDENT && LanguageSyntax.canBeNamedInfix(peek().text())
                        && canStartAtom(peekNext())) break;
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
            if (expression instanceof Name name) {
                return isUnknownCallable(name.name());
            }
            return !(expression instanceof Apply) && !(expression instanceof Group);
        }

        private boolean isUnknownCallable(String name) {
            return !LanguageSyntax.isBuiltinCallable(name) && declaredArities.getOrDefault(name, 0) <= 0;
        }

        private boolean isDeclaredInfixCandidate(Token token) {
            return token.kind() == Kind.IDENT && LanguageSyntax.canBeNamedInfix(token.text())
                    && declaredArities.containsKey(token.text());
        }

        private Token peekNext() {
            int next = Math.min(current + 1, tokens.size() - 1);
            return tokens.get(next);
        }

        private Expr postfix() {
            Expr expr = primary();
            while (true) {
                if (match(".")) {
                    Token name = consume(Kind.IDENT, "Expected field name after '.'");
                    boolean optional = match("~");
                    SourceSpan end = optional ? previous().span() : name.span();
                    expr = new Field(expr, name.text(), optional, SourceSpan.cover(expr.span(), end));
                    continue;
                }
                if (match("[")) {
                    if (atEnd()) {
                        throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ']'");
                    }
                    Expr name = conditional();
                    consume("]", "Expected ']'");
                    Token close = previous();
                    boolean optional = match("~");
                    SourceSpan end = optional ? previous().span() : close.span();
                    expr = new DynamicField(expr, name, optional, SourceSpan.cover(expr.span(), end));
                    continue;
                }
                break;
            }
            return expr;
        }

        private Expr primary() {
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
                Expr expr = conditional();
                consume(")", "Expected ')'");
                return new Group(expr, SourceSpan.cover(open.span(), previous().span()));
            }
            throw error("Expected expression, found '" + peek().text() + "'");
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
            return Set.of("(", "?", "~").contains(token.text());
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
