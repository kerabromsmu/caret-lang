package caretlang;

import caretlang.Ast.*;
import caretlang.Lexer.Kind;
import caretlang.Lexer.Token;

import java.util.*;

final class Parser {
    private record Line(int indent, String text, int number) {}

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
        List<Token> tokens = Lexer.lex(line.text);

        int eq = topLevelEquals(tokens);
        if (eq >= 0) {
            List<Token> left = tokens.subList(0, eq);
            List<Token> right = tokens.subList(eq + 1, tokens.size() - 1);
            boolean exported = !left.isEmpty() && left.getFirst().text().equals("^");
            int offset = exported ? 1 : 0;

            if (!exported && left.size() > 1 && left.stream().allMatch(t -> t.kind() == Kind.IDENT) && !right.isEmpty()) {
                String name = left.getFirst().text();
                List<String> params = left.subList(1, left.size()).stream().map(Token::text).toList();
                Expr expression = new ExprParser(right, line.number).parse();
                return new FunctionDef(name, params, List.of(new ExprStmt(expression)));
            }

            if (left.size() == offset + 1 && left.get(offset).kind() == Kind.IDENT && !right.isEmpty()) {
                return new Assign(left.get(offset).text(), exported, new ExprParser(right, line.number).parse());
            }

            if (!exported && !left.isEmpty() && left.stream().allMatch(t -> t.kind() == Kind.IDENT) && right.isEmpty()) {
                String name = left.getFirst().text();
                List<String> params = left.subList(1, left.size()).stream().map(Token::text).toList();
                if (lineIndex >= lines.size() || lines.get(lineIndex).indent <= indent) {
                    throw error(line, "Function body must be indented");
                }
                int childIndent = lines.get(lineIndex).indent;
                List<Stmt> body = parseBlock(childIndent);
                return new FunctionDef(name, params, body);
            }

            if (left.size() == offset + 1 && left.get(offset).kind() == Kind.IDENT && right.isEmpty()) {
                // Zero-argument function with an indented body.
                if (!exported && lineIndex < lines.size() && lines.get(lineIndex).indent > indent) {
                    String name = left.get(offset).text();
                    int childIndent = lines.get(lineIndex).indent;
                    return new FunctionDef(name, List.of(), parseBlock(childIndent));
                }
            }

            throw error(line, "Invalid assignment or function definition");
        }

        return new ExprStmt(new ExprParser(tokens.subList(0, tokens.size() - 1), line.number).parse());
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
        String[] raw = source.replace("\t", "  ").split("\\R", -1);
        for (int i = 0; i < raw.length; i++) {
            String line = raw[i].stripTrailing();
            String trimmed = line.stripLeading();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int indent = line.length() - trimmed.length();
            result.add(new Line(indent, trimmed, i + 1));
        }
        return result;
    }

    private LangException error(Line line, String message) {
        return new LangException("Line " + line.number + ": " + message + "\n  " + line.text);
    }

    private static final class ExprParser {
        private final List<Token> tokens;
        private final int line;
        private int current;

        ExprParser(List<Token> tokens, int line) {
            this.tokens = new ArrayList<>(tokens);
            this.tokens.add(new Token(Kind.EOF, ""));
            this.line = line;
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
                Expr no = match("!") ? conditional() : new Literal(Value.Missing.INSTANCE);
                return new Conditional(condition, yes, no);
            }
            return condition;
        }

        private Expr conditionalBranch() {
            // Parse up to the matching ! at this nesting level.
            return or();
        }

        private Expr or() {
            Expr expr = and();
            while (matchIdent("or")) expr = new Binary("or", expr, and());
            return expr;
        }

        private Expr and() {
            Expr expr = equality();
            while (matchIdent("and")) expr = new Binary("and", expr, equality());
            return expr;
        }

        private Expr equality() {
            Expr expr = comparison();
            while (match("==", "!=")) {
                String op = previous().text();
                expr = new Binary(op, expr, comparison());
            }
            return expr;
        }

        private Expr comparison() {
            Expr expr = term();
            while (match(">", ">=", "<", "<=")) {
                String op = previous().text();
                expr = new Binary(op, expr, term());
            }
            return expr;
        }

        private Expr term() {
            Expr expr = factor();
            while (match("+", "-")) {
                String op = previous().text();
                expr = new Binary(op, expr, factor());
            }
            return expr;
        }

        private Expr factor() {
            Expr expr = unary();
            while (match("*", "/", "%")) {
                String op = previous().text();
                expr = new Binary(op, expr, unary());
            }
            return expr;
        }

        private Expr unary() {
            if (match("-")) return new Unary("-", unary());
            if (match("@")) return new Reflect(unary());
            if (matchIdent("not")) return new Unary("not", unary());
            return application();
        }

        private Expr application() {
            Expr expr = postfix();
            while (canStartAtom(peek())) {
                expr = new Apply(expr, postfix());
            }
            return expr;
        }

        private Expr postfix() {
            Expr expr = primary();
            while (true) {
                if (match(".")) {
                    Token name = consume(Kind.IDENT, "Expected field name after '.'");
                    boolean optional = match("~");
                    expr = new Field(expr, name.text(), optional);
                    continue;
                }
                if (match("[")) {
                    Expr name = conditional();
                    consume("]", "Expected ']'");
                    boolean optional = match("~");
                    expr = new DynamicField(expr, name, optional);
                    continue;
                }
                break;
            }
            return expr;
        }

        private Expr primary() {
            if (matchKind(Kind.NUMBER)) return new Literal(new Value.Num(Double.parseDouble(previous().text())));
            if (matchKind(Kind.STRING)) return new Literal(new Value.Str(previous().text()));
            if (matchKind(Kind.NAME)) return new Literal(new Value.Name(previous().text()));
            if (matchIdent("true")) return new Literal(new Value.Bool(true));
            if (matchIdent("false")) return new Literal(new Value.Bool(false));
            if (match("?")) return new Literal(Value.Null.INSTANCE);
            if (match("~")) return new Literal(Value.Missing.INSTANCE);
            if (matchIdent("_")) return new Hole();
            if (matchKind(Kind.IDENT)) return new Name(previous().text());
            if (match("(")) {
                Expr expr = conditional();
                consume(")", "Expected ')'");
                return expr;
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
        private LangException error(String message) { return new LangException("Line " + line + ": " + message); }
    }
}
