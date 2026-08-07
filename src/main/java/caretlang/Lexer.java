package caretlang;

import java.util.ArrayList;
import java.util.List;

final class Lexer {
    enum Kind { NUMBER, STRING, IDENT, NAME, SYMBOL, EOF }
    record Token(Kind kind, String text, SourceSpan span) {}

    static List<Token> lex(String source) {
        return lex(source, 0, 1, 1);
    }

    static List<Token> lex(String source, int baseOffset, int line, int baseColumn) {
        ArrayList<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') break;
            if (c == '#') {
                int tokenStart = i;
                int start = ++i;
                if (i >= source.length() || (!Character.isLetter(source.charAt(i)) && source.charAt(i) != '_'))
                    throw error("Expected a name after '#'", baseOffset, line, baseColumn, tokenStart, i);
                i++;
                while (i < source.length()) {
                    char d = source.charAt(i);
                    if (!Character.isLetterOrDigit(d) && d != '_') break;
                    i++;
                }
                tokens.add(token(Kind.NAME, source.substring(start, i), baseOffset, line, baseColumn, tokenStart, i));
                continue;
            }
            if (c == '"') {
                int tokenStart = i;
                StringBuilder b = new StringBuilder();
                i++;
                while (i < source.length() && source.charAt(i) != '"') {
                    char d = source.charAt(i++);
                    if (d == '\\') {
                        if (i >= source.length()) {
                            throw error("Incomplete string escape", baseOffset, line, baseColumn, i - 1, i);
                        }
                        char e = source.charAt(i++);
                        switch (e) {
                            case 'n' -> b.append('\n');
                            case 'r' -> b.append('\r');
                            case 't' -> b.append('\t');
                            case '"' -> b.append('"');
                            case '\\' -> b.append('\\');
                            case 'u' -> i = appendUnicodeEscape(source, i, b, baseOffset, line, baseColumn);
                            default -> throw error("Unknown string escape: \\" + e,
                                    baseOffset, line, baseColumn, i - 2, i);
                        }
                    } else b.append(d);
                }
                if (i >= source.length())
                    throw error("Unterminated string", baseOffset, line, baseColumn, tokenStart, i);
                i++;
                tokens.add(token(Kind.STRING, b.toString(), baseOffset, line, baseColumn, tokenStart, i));
                continue;
            }
            if (Character.isDigit(c)) {
                int start = i++;
                boolean sawDot = false;
                while (i < source.length() && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) {
                    if (source.charAt(i) == '.' && sawDot)
                        throw error("Invalid number literal", baseOffset, line, baseColumn, start, i + 1);
                    if (source.charAt(i) == '.') sawDot = true;
                    i++;
                }
                tokens.add(token(Kind.NUMBER, source.substring(start, i), baseOffset, line, baseColumn, start, i));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < source.length()) {
                    char d = source.charAt(i);
                    if (!Character.isLetterOrDigit(d) && d != '_') break;
                    i++;
                }
                tokens.add(token(Kind.IDENT, source.substring(start, i), baseOffset, line, baseColumn, start, i));
                continue;
            }
            String two = i + 1 < source.length() ? source.substring(i, i + 2) : "";
            if (List.of("==", "!=", ">=", "<=").contains(two)) {
                tokens.add(token(Kind.SYMBOL, two, baseOffset, line, baseColumn, i, i + 2));
                i += 2;
                continue;
            }
            if ("()[]@+-*/%^=<>.&!?~".indexOf(c) >= 0) {
                tokens.add(token(Kind.SYMBOL, Character.toString(c), baseOffset, line, baseColumn, i, i + 1));
                i++;
                continue;
            }
            throw error("Unexpected character: " + c, baseOffset, line, baseColumn, i, i + 1);
        }
        tokens.add(token(Kind.EOF, "", baseOffset, line, baseColumn, i, i));
        return tokens;
    }

    private static Token token(Kind kind, String text, int baseOffset, int line, int baseColumn,
                               int start, int end) {
        return new Token(kind, text, span(baseOffset, line, baseColumn, start, end));
    }

    private static LangException error(String message, int baseOffset, int line, int baseColumn,
                                       int start, int end) {
        return new LangException(Diagnostic.Phase.LEXER, "INVALID_TOKEN", message,
                span(baseOffset, line, baseColumn, start, end));
    }

    private static int appendUnicodeEscape(String source, int index, StringBuilder output,
                                           int baseOffset, int line, int baseColumn) {
        int escapeStart = index - 2;
        if (index >= source.length() || source.charAt(index) != '{') {
            throw error("Unicode escape must use \\u{...}", baseOffset, line, baseColumn,
                    escapeStart, Math.min(index + 1, source.length()));
        }
        int digitsStart = ++index;
        while (index < source.length() && source.charAt(index) != '}') index++;
        if (index >= source.length() || index == digitsStart) {
            throw error("Invalid Unicode escape", baseOffset, line, baseColumn,
                    escapeStart, Math.min(index + 1, source.length()));
        }
        String digits = source.substring(digitsStart, index);
        final int codePoint;
        try {
            codePoint = Integer.parseInt(digits, 16);
        } catch (NumberFormatException ignored) {
            throw error("Invalid Unicode escape", baseOffset, line, baseColumn,
                    escapeStart, index + 1);
        }
        if (!Character.isValidCodePoint(codePoint) || codePoint >= 0xD800 && codePoint <= 0xDFFF) {
            throw error("Invalid Unicode code point", baseOffset, line, baseColumn,
                    escapeStart, index + 1);
        }
        output.appendCodePoint(codePoint);
        return index + 1;
    }

    private static SourceSpan span(int baseOffset, int line, int baseColumn, int start, int end) {
        return new SourceSpan(
                new SourcePosition(baseOffset + start, line, baseColumn + start),
                new SourcePosition(baseOffset + end, line, baseColumn + end));
    }
}
