package caretlang;

import caretlang.Ast.*;
import caretlang.Lexer.Kind;
import caretlang.Lexer.Token;

import java.util.*;

final class Parser {
    private record Line(int indent, String text, int number, int offset, int column, SourceSpan span) {}

    private final List<Line> lines;
    private int lineIndex;

    Parser(String source) {
        this.lines = preprocess(source);
    }

    List<Stmt> parseProgram() {
        return parseBlock(0);
    }

    private List<Stmt> parseBlock(int indent) {
        ArrayList<Stmt> result = new ArrayList<>();
        while (lineIndex < lines.size()) {
            Line line = lines.get(lineIndex);
            if (line.indent < indent) break;
            if (line.indent > indent) {
                throw error(line, "Unexpected indentation");
            }
            result.add(parseLine(line, indent));
        }
        return result;
    }

    private Stmt parseLine(Line line, int indent) {
        lineIndex++;
        List<Token> tokens = Lexer.lex(line.text, line.offset, line.number, line.column);

        // Output is intentionally a statement form: the complete remainder of the
        // line is its expression. This keeps ordinary whitespace application
        // left-associative while allowing the concise `print add 2 3` spelling.
        if (tokens.size() > 2 && tokens.getFirst().kind() == Kind.IDENT
                && tokens.getFirst().text().equals("print")
                && !tokens.get(1).text().equals("=")) {
            Expr expression = new ExprParser(tokens.subList(1, tokens.size() - 1)).parse();
            Expr print = new Name("print", tokens.getFirst().span());
            Expr call = new Apply(print, expression, SourceSpan.cover(print.span(), expression.span()));
            return new ExprStmt(call, call.span());
        }

        int eq = topLevelEquals(tokens);
        if (eq >= 0) {
            List<Token> left = tokens.subList(0, eq);
            List<Token> right = tokens.subList(eq + 1, tokens.size() - 1);
            boolean exported = !left.isEmpty() && left.getFirst().text().equals("^");
            int offset = exported ? 1 : 0;

            if (!exported && left.size() > 1 && left.stream().allMatch(t -> t.kind() == Kind.IDENT) && !right.isEmpty()) {
                String name = left.getFirst().text();
                List<String> params = left.subList(1, left.size()).stream().map(Token::text).toList();
                Expr expression = new ExprParser(right).parse();
                ExprStmt expressionStatement = new ExprStmt(expression, expression.span());
                return new FunctionDef(name, params, List.of(expressionStatement),
                        SourceSpan.cover(left.getFirst().span(), expression.span()));
            }

            if (left.size() == offset + 1 && left.get(offset).kind() == Kind.IDENT && !right.isEmpty()) {
                Expr expression = new ExprParser(right).parse();
                return new Assign(left.get(offset).text(), exported, expression,
                        SourceSpan.cover(left.getFirst().span(), expression.span()));
            }

            if (!exported && !left.isEmpty() && left.stream().allMatch(t -> t.kind() == Kind.IDENT) && right.isEmpty()) {
                String name = left.getFirst().text();
                List<String> params = left.subList(1, left.size()).stream().map(Token::text).toList();
                if (lineIndex >= lines.size() || lines.get(lineIndex).indent <= indent) {
                    throw error(line, "Function body must be indented");
                }
                int childIndent = lines.get(lineIndex).indent;
                List<Stmt> body = parseBlock(childIndent);
                return new FunctionDef(name, params, body, functionSpan(left, body));
            }

            if (left.size() == offset + 1 && left.get(offset).kind() == Kind.IDENT && right.isEmpty()) {
                // Zero-argument function with an indented body.
                if (!exported && lineIndex < lines.size() && lines.get(lineIndex).indent > indent) {
                    String name = left.get(offset).text();
                    int childIndent = lines.get(lineIndex).indent;
                    List<Stmt> body = parseBlock(childIndent);
                    return new FunctionDef(name, List.of(), body, functionSpan(left, body));
                }
            }

            throw error(line, "Invalid assignment or function definition");
        }

        Expr expression = new ExprParser(tokens.subList(0, tokens.size() - 1)).parse();
        return new ExprStmt(expression, expression.span());
    }

    private SourceSpan functionSpan(List<Token> header, List<Stmt> body) {
        SourceSpan start = header.getFirst().span();
        return SourceSpan.cover(start, body.getLast().span());
    }

    private int topLevelEquals(List<Token> tokens) {
        int depth = 0;
        for (int i = 0; i < tokens.size() - 1; i++) {
            String t = tokens.get(i).text();
            if (t.equals("(")) depth++;
            else if (t.equals(")")) depth--;
            else if (t.equals("=") && depth == 0) return i;
        }
        return -1;
    }

    private static List<Line> preprocess(String source) {
        ArrayList<Line> result = new ArrayList<>();
        int lineStart = 0;
        int lineNumber = 1;
        while (lineStart <= source.length()) {
            int lineEnd = lineStart;
            while (lineEnd < source.length() && source.charAt(lineEnd) != '\n' && source.charAt(lineEnd) != '\r') {
                lineEnd++;
            }
            String line = source.substring(lineStart, lineEnd).stripTrailing();
            String trimmed = line.stripLeading();
            int leadingCharacters = line.length() - trimmed.length();
            if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
                int indent = 0;
                for (int i = 0; i < leadingCharacters; i++) {
                    indent += line.charAt(i) == '\t' ? 2 : 1;
                }
                int contentOffset = lineStart + leadingCharacters;
                SourcePosition start = new SourcePosition(contentOffset, lineNumber, leadingCharacters + 1);
                SourcePosition end = new SourcePosition(contentOffset + trimmed.length(), lineNumber,
                        leadingCharacters + trimmed.length() + 1);
                result.add(new Line(indent, trimmed, lineNumber, contentOffset, leadingCharacters + 1,
                        new SourceSpan(start, end)));
            }

            if (lineEnd >= source.length()) break;
            if (source.charAt(lineEnd) == '\r' && lineEnd + 1 < source.length()
                    && source.charAt(lineEnd + 1) == '\n') lineEnd++;
            lineStart = lineEnd + 1;
            lineNumber++;
        }
        return result;
    }

    private LangException error(Line line, String message) {
        return new LangException(message + "\n  " + line.text, line.span);
    }

    private static final class ExprParser {
        private final List<Token> tokens;
        private int current;

        ExprParser(List<Token> tokens) {
            this.tokens = new ArrayList<>(tokens);
            SourcePosition end = tokens.isEmpty()
                    ? new SourcePosition(0, 1, 1)
                    : tokens.getLast().span().end();
            this.tokens.add(new Token(Kind.EOF, "", SourceSpan.point(end)));
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
            while (match("==", "!=")) {
                String op = previous().text();
                Expr right = comparison();
                expr = new Binary(op, expr, right, SourceSpan.cover(expr.span(), right.span()));
            }
            return expr;
        }

        private Expr comparison() {
            Expr expr = term();
            while (match(">", ">=", "<", "<=")) {
                String op = previous().text();
                Expr right = term();
                expr = new Binary(op, expr, right, SourceSpan.cover(expr.span(), right.span()));
            }
            return expr;
        }

        private Expr term() {
            Expr expr = factor();
            while (match("+", "-")) {
                String op = previous().text();
                Expr right = factor();
                expr = new Binary(op, expr, right, SourceSpan.cover(expr.span(), right.span()));
            }
            return expr;
        }

        private Expr factor() {
            Expr expr = unary();
            while (match("*", "/", "%")) {
                String op = previous().text();
                Expr right = unary();
                expr = new Binary(op, expr, right, SourceSpan.cover(expr.span(), right.span()));
            }
            return expr;
        }

        private Expr unary() {
            if (match("-")) {
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
            return application();
        }

        private Expr application() {
            Expr expr = postfix();
            while (canStartAtom(peek())) {
                Expr argument = postfix();
                expr = new Apply(expr, argument, SourceSpan.cover(expr.span(), argument.span()));
            }
            return expr;
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
            if (matchKind(Kind.NUMBER)) return new Literal(new Value.Num(Double.parseDouble(previous().text())), previous().span());
            if (matchKind(Kind.STRING)) return new Literal(new Value.Str(previous().text()), previous().span());
            if (matchKind(Kind.NAME)) return new Literal(new Value.Name(previous().text()), previous().span());
            if (matchIdent("true")) return new Literal(new Value.Bool(true), previous().span());
            if (matchIdent("false")) return new Literal(new Value.Bool(false), previous().span());
            if (match("?")) return new Literal(Value.Null.INSTANCE, previous().span());
            if (match("~")) return new Literal(Value.Missing.INSTANCE, previous().span());
            if (matchIdent("_")) return new Hole(previous().span());
            if (matchKind(Kind.IDENT)) return new Name(previous().text(), previous().span());
            if (match("(")) {
                Token open = previous();
                Expr expr = conditional();
                consume(")", "Expected ')'");
                return new Group(expr, SourceSpan.cover(open.span(), previous().span()));
            }
            throw error("Expected expression, found '" + peek().text() + "'");
        }

        private boolean canStartAtom(Token token) {
            if (token.kind() == Kind.NUMBER || token.kind() == Kind.STRING || token.kind() == Kind.NAME) return true;
            if (token.kind() == Kind.IDENT) {
                return !Set.of("and", "or", "not").contains(token.text());
            }
            return Set.of("(", "?", "~").contains(token.text());
        }

        private boolean match(String... texts) {
            for (String text : texts) {
                if (peek().text().equals(text)) { current++; return true; }
            }
            return false;
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
            throw error(message);
        }

        private void consume(String text, String message) {
            if (!match(text)) throw error(message);
        }

        private Token peek() { return tokens.get(current); }
        private Token previous() { return tokens.get(current - 1); }
        private boolean atEnd() { return peek().kind() == Kind.EOF; }
        private LangException error(String message) { return new LangException(message, peek().span()); }
    }
}
