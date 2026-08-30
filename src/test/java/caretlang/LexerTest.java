package caretlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class LexerTest {
    @Test
    void recognizesCompositionAsOneSymbol() {
        List<Lexer.Token> tokens = Lexer.lex("left >> right");
        assertEquals(List.of("left", ">>", "right", ""),
                tokens.stream().map(Lexer.Token::text).toList());
    }

    @Test
    void recognizesLowPrecedenceApplicationAsSyntax() {
        List<Lexer.Token> tokens = Lexer.lex("left $ right", 20, 3, 5);
        assertEquals(List.of("left", "$", "right", ""),
                tokens.stream().map(Lexer.Token::text).toList());
        assertEquals(new SourcePosition(25, 3, 10), tokens.get(1).span().start());
    }

    @Test
    void rejectsRemovedNameLiteralSyntax() {
        LangException error = assertThrows(LangException.class,
                () -> Lexer.lex("#count", 20, 3, 5));
        assertEquals(Diagnostic.Codes.LEX_UNEXPECTED_CHARACTER, error.diagnostic().code());
        assertEquals(new SourcePosition(20, 3, 5), error.span().start());
    }

    @Test
    void preservesLiteralTabsInsideStrings() {
        Lexer.Token string = Lexer.lex("\"left\tright\"").getFirst();
        assertEquals("left\tright", string.text());
    }

    @Test
    void reportsMalformedNumbersAtTheirLocation() {
        LangException error = assertThrows(LangException.class, () -> Lexer.lex("  1.2.3"));
        assertEquals(1, error.span().start().line());
        assertEquals(3, error.span().start().column());
        assertTrue(error.getMessage().contains("Invalid number literal"));
    }

    @Test
    void reportsUnexpectedCharactersAtTheirLocation() {
        LangException error = assertThrows(LangException.class, () -> Lexer.lex("ok #"));
        assertEquals(4, error.span().start().column());
        assertTrue(error.getMessage().contains("Unexpected character: #"));
    }

    @Test
    void reportsUnterminatedStringsAtTheirLocation() {
        LangException error = assertThrows(LangException.class, () -> Lexer.lex("  \"unfinished"));
        assertEquals(3, error.span().start().column());
        assertTrue(error.getMessage().contains("Unterminated string"));
    }

    @Test
    void recognizesDocumentedEscapesIncludingUnicodeCodePoints() {
        Lexer.Token string = Lexer.lex("\"a\\n\\r\\t\\\"\\\\\\u{1F642}\"").getFirst();
        assertEquals("a\n\r\t\"\\🙂", string.text());
    }

    @Test
    void rejectsUnknownAndInvalidUnicodeEscapes() {
        LangException unknown = assertThrows(LangException.class, () -> Lexer.lex("\"\\q\""));
        assertEquals(Diagnostic.Phase.LEXER, unknown.diagnostic().phase());
        assertTrue(unknown.getMessage().contains("Unknown string escape"));

        LangException unicode = assertThrows(LangException.class, () -> Lexer.lex("\"\\u{D800}\""));
        assertTrue(unicode.getMessage().contains("Invalid Unicode code point"));
    }

    @Test
    void tracksTokensAcrossLinesAndContinuesAfterComments() {
        List<Lexer.Token> tokens = Lexer.lex("add (\n  1 // first\n  2\n)", 10, 4, 3);

        Lexer.Token one = tokens.stream().filter(token -> token.text().equals("1")).findFirst().orElseThrow();
        Lexer.Token two = tokens.stream().filter(token -> token.text().equals("2")).findFirst().orElseThrow();
        assertEquals(new SourcePosition(18, 5, 3), one.span().start());
        assertEquals(new SourcePosition(31, 6, 3), two.span().start());
        assertEquals(4, tokens.stream().filter(token -> token.text().equals("(") || token.text().equals(")")
                || token.kind() == Lexer.Kind.NUMBER).count());
    }

    @Test
    void logicalLinesRetainIndentationAcrossBlankAndCommentLines() {
        List<Lexer.LogicalLine> lines = Lexer.logicalLines("""
                call
                  first

                  // comment
                  second
                next
                """);
        assertEquals(List.of(0, 2, 2, 0), lines.stream().map(Lexer.LogicalLine::indent).toList());
        assertEquals(List.of(1, 2, 5, 6), lines.stream().map(Lexer.LogicalLine::number).toList());
    }

    @Test
    void logicalLayoutMappingsEstablishRestoreAndStackBaselines() {
        List<Lexer.LogicalLine> lines = Lexer.logicalLines("outer = \\\\\nfirst\ninner = \\\\\n  nested\n\\*\nsecond\n\\*\nfinal\n");
        assertEquals(List.of(0, 1, 1, 2, 1, 0), lines.stream().map(Lexer.LogicalLine::indent).toList());
        assertEquals(List.of("outer =", "first", "inner =", "nested", "second", "final"),
                lines.stream().map(Lexer.LogicalLine::text).toList());
    }

    @Test
    void layoutMarkersIgnoreCommentsAndStringsAndRejectInvalidPlacement() {
        List<Lexer.LogicalLine> lines = Lexer.logicalLines("text = \"\\\\\"\nvalue = 1 // \\\\\n\\*\n");
        assertEquals(List.of(0, 0), lines.stream().map(Lexer.LogicalLine::indent).toList());

        LangException error = assertThrows(LangException.class, () -> Lexer.logicalLines("call \\\\"));
        assertEquals(Diagnostic.Codes.LEX_INVALID_LAYOUT_MARKER, error.diagnostic().code());
        assertEquals(new SourcePosition(5, 1, 6), error.span().start());
    }
}
