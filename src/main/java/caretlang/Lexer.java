package caretlang;

import java.util.ArrayList;
import java.util.List;

final class Lexer {
    enum Kind { NUMBER, STRING, IDENT, NAME, SYMBOL, EOF }
    record Token(Kind kind, String text, SourceSpan span) {}
    record LogicalLine(int indent, String text, int number, int offset, int column, SourceSpan span) {}
    private record PhysicalLine(int start, int end, int number) {}

    static List<Token> lex(String source) {
        return lex(source, 0, 1, 1);
    }

    static List<Token> lex(String source, int baseOffset, int line, int baseColumn) {
        ArrayList<Token> tokens = new ArrayList<>();
        PositionTable positions = new PositionTable(source, baseOffset, line, baseColumn);
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                while (i < source.length() && source.charAt(i) != '\n' && source.charAt(i) != '\r') i++;
                continue;
            }
            if (c == '#') {
                int tokenStart = i;
                int start = ++i;
                if (i >= source.length() || (!Character.isLetter(source.charAt(i)) && source.charAt(i) != '_'))
                    throw error(Diagnostic.Codes.LEX_INVALID_NAME, "Expected a name after '#'",
                            positions, tokenStart, i);
                i++;
                while (i < source.length()) {
                    char d = source.charAt(i);
                    if (!Character.isLetterOrDigit(d) && d != '_') break;
                    i++;
                }
                tokens.add(token(Kind.NAME, source.substring(start, i), positions, tokenStart, i));
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
                            throw error(Diagnostic.Codes.LEX_INVALID_ESCAPE,
                                    "Incomplete string escape", positions, i - 1, i);
                        }
                        char e = source.charAt(i++);
                        switch (e) {
                            case 'n' -> b.append('\n');
                            case 'r' -> b.append('\r');
                            case 't' -> b.append('\t');
                            case '"' -> b.append('"');
                            case '\\' -> b.append('\\');
                            case 'u' -> i = appendUnicodeEscape(source, i, b, positions);
                            default -> throw error(Diagnostic.Codes.LEX_INVALID_ESCAPE,
                                    "Unknown string escape: \\" + e,
                                    positions, i - 2, i);
                        }
                    } else if (d == '\n' || d == '\r') {
                        throw error(Diagnostic.Codes.LEX_UNTERMINATED_STRING,
                                "Unterminated string", positions, tokenStart, i - 1);
                    } else b.append(d);
                }
                if (i >= source.length())
                    throw error(Diagnostic.Codes.LEX_UNTERMINATED_STRING,
                            "Unterminated string", positions, tokenStart, i);
                i++;
                tokens.add(token(Kind.STRING, b.toString(), positions, tokenStart, i));
                continue;
            }
            if (Character.isDigit(c)) {
                int start = i++;
                boolean sawDot = false;
                while (i < source.length() && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) {
                    if (source.charAt(i) == '.' && sawDot)
                        throw error(Diagnostic.Codes.LEX_INVALID_NUMBER,
                                "Invalid number literal", positions, start, i + 1);
                    if (source.charAt(i) == '.') sawDot = true;
                    i++;
                }
                tokens.add(token(Kind.NUMBER, source.substring(start, i), positions, start, i));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < source.length()) {
                    char d = source.charAt(i);
                    if (!Character.isLetterOrDigit(d) && d != '_') break;
                    i++;
                }
                tokens.add(token(Kind.IDENT, source.substring(start, i), positions, start, i));
                continue;
            }
            String two = i + 1 < source.length() ? source.substring(i, i + 2) : "";
            if (List.of("==", "!=", ">=", "<=").contains(two)) {
                tokens.add(token(Kind.SYMBOL, two, positions, i, i + 2));
                i += 2;
                continue;
            }
            if ("()[]@+-*/%^=<>.&!?~".indexOf(c) >= 0) {
                tokens.add(token(Kind.SYMBOL, Character.toString(c), positions, i, i + 1));
                i++;
                continue;
            }
            throw error(Diagnostic.Codes.LEX_UNEXPECTED_CHARACTER,
                    "Unexpected character: " + c, positions, i, i + 1);
        }
        tokens.add(token(Kind.EOF, "", positions, i, i));
        return tokens;
    }

    /** Returns delimiter depth while applying the same string/comment boundaries as tokenization. */
    static int continuationDelimiterDelta(String source) {
        int depth = 0;
        boolean string = false;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (string) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') string = false;
                continue;
            }
            if (c == '"') string = true;
            else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') break;
            else if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
        }
        return depth;
    }

    static List<LogicalLine> logicalLines(String source) {
        List<PhysicalLine> physicalLines = physicalLines(source);
        ArrayList<LogicalLine> result = new ArrayList<>();
        for (int physicalIndex = 0; physicalIndex < physicalLines.size(); physicalIndex++) {
            PhysicalLine first = physicalLines.get(physicalIndex);
            String line = source.substring(first.start(), first.end()).stripTrailing();
            String trimmed = line.stripLeading();
            int leadingCharacters = line.length() - trimmed.length();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue;

            int indent = 0;
            for (int i = 0; i < leadingCharacters; i++) indent += line.charAt(i) == '\t' ? 2 : 1;
            int contentOffset = first.start() + leadingCharacters;
            int depth = continuationDelimiterDelta(trimmed);
            PhysicalLine last = first;
            while (depth > 0 && physicalIndex + 1 < physicalLines.size()) {
                last = physicalLines.get(++physicalIndex);
                depth += continuationDelimiterDelta(source.substring(last.start(), last.end()));
            }

            String lastText = source.substring(last.start(), last.end()).stripTrailing();
            int logicalEnd = last.start() + lastText.length();
            SourcePosition start = new SourcePosition(contentOffset, first.number(), leadingCharacters + 1);
            SourcePosition end = new SourcePosition(logicalEnd, last.number(), lastText.length() + 1);
            result.add(new LogicalLine(indent, source.substring(contentOffset, logicalEnd), first.number(),
                    contentOffset, leadingCharacters + 1, new SourceSpan(start, end)));
        }
        return result;
    }

    private static List<PhysicalLine> physicalLines(String source) {
        ArrayList<PhysicalLine> lines = new ArrayList<>();
        int start = 0;
        int number = 1;
        while (start <= source.length()) {
            int end = start;
            while (end < source.length() && source.charAt(end) != '\n' && source.charAt(end) != '\r') end++;
            int next = end;
            if (next < source.length() && source.charAt(next) == '\r') next++;
            if (next < source.length() && source.charAt(next) == '\n') next++;
            lines.add(new PhysicalLine(start, end, number++));
            if (end >= source.length()) break;
            start = next;
        }
        return lines;
    }

    private static Token token(Kind kind, String text, PositionTable positions, int start, int end) {
        return new Token(kind, text, positions.span(start, end));
    }

    private static LangException error(String code, String message, PositionTable positions, int start, int end) {
        return new LangException(Diagnostic.Phase.LEXER, code, message,
                positions.span(start, end));
    }

    private static int appendUnicodeEscape(String source, int index, StringBuilder output,
                                           PositionTable positions) {
        int escapeStart = index - 2;
        if (index >= source.length() || source.charAt(index) != '{') {
            throw error(Diagnostic.Codes.LEX_INVALID_ESCAPE, "Unicode escape must use \\u{...}", positions,
                    escapeStart, Math.min(index + 1, source.length()));
        }
        int digitsStart = ++index;
        while (index < source.length() && source.charAt(index) != '}') index++;
        if (index >= source.length() || index == digitsStart) {
            throw error(Diagnostic.Codes.LEX_INVALID_ESCAPE, "Invalid Unicode escape", positions,
                    escapeStart, Math.min(index + 1, source.length()));
        }
        String digits = source.substring(digitsStart, index);
        final int codePoint;
        try {
            codePoint = Integer.parseInt(digits, 16);
        } catch (NumberFormatException ignored) {
            throw error(Diagnostic.Codes.LEX_INVALID_ESCAPE, "Invalid Unicode escape", positions,
                    escapeStart, index + 1);
        }
        if (!Character.isValidCodePoint(codePoint) || codePoint >= 0xD800 && codePoint <= 0xDFFF) {
            throw error(Diagnostic.Codes.LEX_INVALID_ESCAPE, "Invalid Unicode code point", positions,
                    escapeStart, index + 1);
        }
        output.appendCodePoint(codePoint);
        return index + 1;
    }

    private static final class PositionTable {
        private final int baseOffset;
        private final int[] lines;
        private final int[] columns;

        private PositionTable(String source, int baseOffset, int initialLine, int initialColumn) {
            this.baseOffset = baseOffset;
            lines = new int[source.length() + 1];
            columns = new int[source.length() + 1];
            int line = initialLine;
            int column = initialColumn;
            for (int i = 0; i <= source.length(); i++) {
                lines[i] = line;
                columns[i] = column;
                if (i == source.length()) break;
                char c = source.charAt(i);
                if (c == '\r') {
                    line++;
                    column = 1;
                } else if (c == '\n') {
                    if (i == 0 || source.charAt(i - 1) != '\r') line++;
                    column = 1;
                } else {
                    column++;
                }
            }
        }

        private SourceSpan span(int start, int end) {
            return new SourceSpan(position(start), position(end));
        }

        private SourcePosition position(int index) {
            return new SourcePosition(baseOffset + index, lines[index], columns[index]);
        }
    }
}
