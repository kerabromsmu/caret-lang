package caretlang;

import java.util.List;

record Diagnostic(Phase phase, String code, String message, SourceSpan primarySpan,
                  List<Related> related) {
    enum Phase { LEXER, PARSER, SEMANTIC, RUNTIME, LOWERING, COMPILER }

    static final class Codes {
        private Codes() {}

        static final String LEX_UNEXPECTED_CHARACTER = "LEX_UNEXPECTED_CHARACTER";
        static final String LEX_INVALID_NUMBER = "LEX_INVALID_NUMBER";
        static final String LEX_UNTERMINATED_STRING = "LEX_UNTERMINATED_STRING";
        static final String LEX_INVALID_ESCAPE = "LEX_INVALID_ESCAPE";
        static final String LEX_INVALID_LAYOUT_MARKER = "LEX_INVALID_LAYOUT_MARKER";
        static final String PARSE_INVALID_SYNTAX = "PARSE_INVALID_SYNTAX";
        static final String PARSE_UNEXPECTED_INDENT = "PARSE_UNEXPECTED_INDENT";
        static final String PARSE_INVALID_EXPRESSION = "PARSE_INVALID_EXPRESSION";
        static final String PARSE_UNCLOSED_DELIMITER = "PARSE_UNCLOSED_DELIMITER";
        static final String PARSE_INVALID_HOLE = "PARSE_INVALID_HOLE";
        static final String PARSE_INVALID_NUMBER = "PARSE_INVALID_NUMBER";
        static final String PARSE_RESERVED_BINDING = "PARSE_RESERVED_BINDING";
        static final String PARSE_INVALID_CONTRACT = "PARSE_INVALID_CONTRACT";
        static final String DUPLICATE_DEFINITION = "DUPLICATE_DEFINITION";
        static final String DUPLICATE_PARAMETER = "DUPLICATE_PARAMETER";
        static final String INCONSISTENT_OVERLOAD_ARITY = "INCONSISTENT_OVERLOAD_ARITY";
        static final String NO_APPLICABLE_OVERLOAD = "NO_APPLICABLE_OVERLOAD";
        static final String AMBIGUOUS_OVERLOAD = "AMBIGUOUS_OVERLOAD";
        static final String UNKNOWN_NAME = "UNKNOWN_NAME";
        static final String READ_BEFORE_INITIALIZATION = "READ_BEFORE_INITIALIZATION";
        static final String UNKNOWN_CONTRACT = "UNKNOWN_CONTRACT";
        static final String NOT_A_CONTRACT = "NOT_A_CONTRACT";
        static final String CONTRACT_VIOLATION = "CONTRACT_VIOLATION";
        static final String INCOMPATIBLE_CONTRACTS = "INCOMPATIBLE_CONTRACTS";
        static final String AMBIGUOUS_CONTRACT = "AMBIGUOUS_CONTRACT";
        static final String INVALID_REFINEMENT = "INVALID_REFINEMENT";
        static final String INVALID_CONTRACT_VARIABLE = "INVALID_CONTRACT_VARIABLE";
        static final String AMBIGUOUS_CLAUSE_NAME = "AMBIGUOUS_CLAUSE_NAME";
        static final String UNKNOWN_CLAUSE_NAME = "UNKNOWN_CLAUSE_NAME";
        static final String CONFLICTING_EFFECT_ALLOWANCE = "CONFLICTING_EFFECT_ALLOWANCE";
        static final String INVALID_EFFECT_MODIFIER = "INVALID_EFFECT_MODIFIER";
        static final String EFFECT_AS_CONTRACT_ARGUMENT = "EFFECT_AS_CONTRACT_ARGUMENT";
        static final String EFFECT_CONSTRAINT_REQUIRES_CALLABLE = "EFFECT_CONSTRAINT_REQUIRES_CALLABLE";
        static final String EFFECT_ALLOWANCE_EXCEEDED = "EFFECT_ALLOWANCE_EXCEEDED";
        static final String UNKNOWN_CALL_EFFECTS = "UNKNOWN_CALL_EFFECTS";
        static final String NOT_CALLABLE = "NOT_CALLABLE";
        static final String INVALID_INFIX_ARITY = "INVALID_INFIX_ARITY";
        static final String INVALID_COMPOSITION_LEFT = "INVALID_COMPOSITION_LEFT";
        static final String INVALID_COMPOSITION_RIGHT = "INVALID_COMPOSITION_RIGHT";
        static final String TOO_MANY_ARGUMENTS = "TOO_MANY_ARGUMENTS";
        static final String CALL_DEPTH_EXCEEDED = "CALL_DEPTH_EXCEEDED";
        static final String INVALID_CONDITION = "INVALID_CONDITION";
        static final String EXPECTED_NUMBER = "EXPECTED_NUMBER";
        static final String EXPECTED_STRING = "EXPECTED_STRING";
        static final String EXPECTED_SEQUENCE = "EXPECTED_SEQUENCE";
        static final String EXPECTED_DICTIONARY = "EXPECTED_DICTIONARY";
        static final String INVALID_DICTIONARY_KEY = "INVALID_DICTIONARY_KEY";
        static final String DIVISION_BY_ZERO = "DIVISION_BY_ZERO";
        static final String NON_FINITE_RESULT = "NON_FINITE_RESULT";
        static final String INVALID_FIELD_TARGET = "INVALID_FIELD_TARGET";
        static final String MISSING_FIELD = "MISSING_FIELD";
        static final String INVALID_DYNAMIC_FIELD_NAME = "INVALID_DYNAMIC_FIELD_NAME";
        static final String NOT_DEREFERENCEABLE = "NOT_DEREFERENCEABLE";
        static final String MIXED_COLLECTION_SHAPE = "MIXED_COLLECTION_SHAPE";
        static final String DUPLICATE_FIELD = "DUPLICATE_FIELD";
        static final String CALLABLE_EQUALITY = "CALLABLE_EQUALITY";
        static final String CALLABLE_RENDERING = "CALLABLE_RENDERING";
        static final String MIXED_HOLE_STYLES = "MIXED_HOLE_STYLES";
        static final String INVALID_ASSERTION = "INVALID_ASSERTION";
        static final String INTERNAL_ERROR = "INTERNAL_ERROR";
        static final String UNKNOWN_OPERATOR = "UNKNOWN_OPERATOR";
        static final String RUNTIME_ERROR = "RUNTIME_ERROR";
    }

    record Related(String message, SourceSpan span) {}

    Diagnostic {
        related = List.copyOf(related);
    }

    Diagnostic(Phase phase, String code, String message, SourceSpan primarySpan) {
        this(phase, code, message, primarySpan, List.of());
    }

    Diagnostic withPrimarySpanIfAbsent(SourceSpan fallback) {
        return primarySpan == null
                ? new Diagnostic(phase, code, message, fallback, related)
                : this;
    }

    String render() {
        if (primarySpan == null) return message;
        SourcePosition start = primarySpan.start();
        StringBuilder rendered = new StringBuilder("Line ")
                .append(start.line()).append(", column ").append(start.column()).append(": ").append(message);
        for (Related note : related) {
            SourcePosition relatedStart = note.span().start();
            rendered.append("\n  Note: Line ").append(relatedStart.line())
                    .append(", column ").append(relatedStart.column()).append(": ").append(note.message());
        }
        return rendered.toString();
    }
}
