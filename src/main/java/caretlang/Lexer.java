package caretlang;

import java.util.ArrayList;
import java.util.List;

final class Lexer {
    enum Kind { NUMBER, STRING, IDENT, NAME, SYMBOL, EOF }
    record Token(Kind kind, String text) {}

    static List<Token> lex(String source) {
        ArrayList<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') break;
            if (c == '#') {
                int start = ++i;
                if (i >= source.length() || (!Character.isLetter(source.charAt(i)) && source.charAt(i) != '_'))
                    throw new LangException("Expected a name after '#'");
                i++;
                while (i < source.length()) {
                    char d = source.charAt(i);
                    if (!Character.isLetterOrDigit(d) && d != '_') break;
                    i++;
                }
                tokens.add(new Token(Kind.NAME, source.substring(start, i)));
                continue;
            }
            if (c == '"') {
                StringBuilder b = new StringBuilder();
                i++;
                while (i < source.length() && source.charAt(i) != '"') {
                    char d = source.charAt(i++);
                    if (d == '\\' && i < source.length()) {
                        char e = source.charAt(i++);
                        b.append(switch (e) {
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            case '"' -> '"';
                            case '\\' -> '\\';
                            default -> e;
                        });
                    } else b.append(d);
                }
                if (i >= source.length()) throw new LangException("Unterminated string");
                i++;
                tokens.add(new Token(Kind.STRING, b.toString()));
                continue;
            }
            if (Character.isDigit(c)) {
                int start = i++;
                while (i < source.length() && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) i++;
                tokens.add(new Token(Kind.NUMBER, source.substring(start, i)));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < source.length()) {
                    char d = source.charAt(i);
                    if (!Character.isLetterOrDigit(d) && d != '_') break;
                    i++;
                }
                tokens.add(new Token(Kind.IDENT, source.substring(start, i)));
                continue;
            }
            String two = i + 1 < source.length() ? source.substring(i, i + 2) : "";
            if (List.of("==", "!=", ">=", "<=").contains(two)) {
                tokens.add(new Token(Kind.SYMBOL, two));
                i += 2;
                continue;
            }
            if ("()[]@+-*/%^=<>.&!?~".indexOf(c) >= 0) {
                tokens.add(new Token(Kind.SYMBOL, Character.toString(c)));
                i++;
                continue;
            }
            throw new LangException("Unexpected character: " + c);
        }
        tokens.add(new Token(Kind.EOF, ""));
        return tokens;
    }
}
