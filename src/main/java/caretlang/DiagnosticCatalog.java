package caretlang;

import java.util.Arrays;
import java.util.regex.Pattern;

import static caretlang.DiagnosticCategory.INTERNAL;
import static caretlang.DiagnosticCategory.PUBLIC;

/** Stable inventory of the distinct diagnostic messages emitted by the prototype. */
enum DiagnosticCatalog {
    LEX_INCOMPLETE_ESCAPE("LEX-INCOMPLETE-ESCAPE", Diagnostic.Phase.LEXER, Diagnostic.Codes.LEX_INVALID_ESCAPE, "Incomplete string escape", PUBLIC),
    LEX_UNKNOWN_ESCAPE("LEX-UNKNOWN-ESCAPE", Diagnostic.Phase.LEXER, Diagnostic.Codes.LEX_INVALID_ESCAPE, "Unknown string escape: .*", PUBLIC),
    LEX_UNTERMINATED_STRING("LEX-UNTERMINATED-STRING", Diagnostic.Phase.LEXER, Diagnostic.Codes.LEX_UNTERMINATED_STRING, "Unterminated string", PUBLIC),
    LEX_INVALID_NUMBER("LEX-INVALID-NUMBER", Diagnostic.Phase.LEXER, Diagnostic.Codes.LEX_INVALID_NUMBER, "Invalid number literal", PUBLIC),
    LEX_UNEXPECTED_CHARACTER("LEX-UNEXPECTED-CHARACTER", Diagnostic.Phase.LEXER, Diagnostic.Codes.LEX_UNEXPECTED_CHARACTER, "Unexpected character: .*", PUBLIC),
    LEX_UNICODE_FORM("LEX-UNICODE-FORM", Diagnostic.Phase.LEXER, Diagnostic.Codes.LEX_INVALID_ESCAPE, "Unicode escape must use .*", PUBLIC),
    LEX_INVALID_UNICODE("LEX-INVALID-UNICODE", Diagnostic.Phase.LEXER, Diagnostic.Codes.LEX_INVALID_ESCAPE, "Invalid Unicode escape", PUBLIC),
    LEX_INVALID_CODE_POINT("LEX-INVALID-CODE-POINT", Diagnostic.Phase.LEXER, Diagnostic.Codes.LEX_INVALID_ESCAPE, "Invalid Unicode code point", PUBLIC),
    LEX_INVALID_LAYOUT_MARKER("LEX-INVALID-LAYOUT-MARKER", Diagnostic.Phase.LEXER,
            Diagnostic.Codes.LEX_INVALID_LAYOUT_MARKER,
            "Layout baseline marker must follow an indentation-opening header", PUBLIC),

    PARSE_NESTING_DEPTH("PARSE-NESTING-DEPTH", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_EXPRESSION, "Maximum expression nesting depth exceeded", PUBLIC),
    PARSE_UNEXPECTED_INDENT("PARSE-UNEXPECTED-INDENT", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_UNEXPECTED_INDENT, "Unexpected indentation.*", PUBLIC),
    PARSE_INCONSISTENT_INDENT("PARSE-INCONSISTENT-INDENT", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_UNEXPECTED_INDENT, "Inconsistent continuation indentation.*", PUBLIC),
    PARSE_FUNCTION_BODY("PARSE-FUNCTION-BODY", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_SYNTAX, "Function body must be indented.*", PUBLIC),
    PARSE_INVALID_DEFINITION("PARSE-INVALID-DEFINITION", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_SYNTAX, "Invalid assignment or function definition.*", PUBLIC),
    PARSE_CONTINUATION_DEFINITION("PARSE-CONTINUATION-DEFINITION", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_SYNTAX, "Continuation argument must be an expression.*", PUBLIC),
    PARSE_RESERVED_BINDING("PARSE-RESERVED-BINDING", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_RESERVED_BINDING, "Reserved spelling cannot be used as a binding name: .*", PUBLIC),
    PARSE_INVALID_CONTRACT("PARSE-INVALID-CONTRACT", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_CONTRACT, ".*[Cc]ontract.*", PUBLIC),
    PARSE_UNCLOSED_DELIMITER("PARSE-UNCLOSED-DELIMITER", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected .*", PUBLIC),
    PARSE_INVALID_HOLE("PARSE-INVALID-HOLE", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_HOLE, "Numbered hole index is too large", PUBLIC),
    PARSE_INVALID_NUMBER("PARSE-INVALID-NUMBER", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_NUMBER, "Invalid number literal", INTERNAL),
    PARSE_NONFINITE_NUMBER("PARSE-NONFINITE-NUMBER", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_NUMBER, "Number literal is outside the finite range", PUBLIC),
    PARSE_EXPECTED_EXPRESSION("PARSE-EXPECTED-EXPRESSION", Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_EXPRESSION, ".*", PUBLIC),

    DUPLICATE_PARAMETER("SEMANTIC-DUPLICATE-PARAMETER", Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.DUPLICATE_PARAMETER, "Duplicate parameter: .*", PUBLIC),
    DUPLICATE_DEFINITION("SEMANTIC-DUPLICATE-DEFINITION", Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.DUPLICATE_DEFINITION, "Duplicate definition: .*", PUBLIC),
    PREMATURE_READ("SEMANTIC-PREMATURE-READ", Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.READ_BEFORE_INITIALIZATION, "Binding read before initialization: .*", PUBLIC),
    UNKNOWN_CONTRACT("SEMANTIC-UNKNOWN-CONTRACT", Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.UNKNOWN_CONTRACT, "Unknown contract: .*", PUBLIC),
    NOT_A_CONTRACT("SEMANTIC-NOT-A-CONTRACT", Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.NOT_A_CONTRACT, "Binding is not a contract: .*", PUBLIC),
    RUNTIME_NOT_A_CONTRACT("RUNTIME-NOT-A-CONTRACT", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_A_CONTRACT, "Binding is not a contract: .*", PUBLIC),
    INCOMPATIBLE_CONTRACTS("SEMANTIC-INCOMPATIBLE-CONTRACTS", Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, "Incompatible inferred contracts: .*", PUBLIC),
    INCOMPATIBLE_COMPOSITION("SEMANTIC-INCOMPATIBLE-COMPOSITION", Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, "Composition result cannot satisfy the right callable parameter", PUBLIC),
    AMBIGUOUS_CONTRACT("SEMANTIC-AMBIGUOUS-CONTRACT", Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.AMBIGUOUS_CONTRACT, "Ambiguous contract at use: .*", PUBLIC),
    INVALID_REFINEMENT("SEMANTIC-INVALID-REFINEMENT", Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.INVALID_REFINEMENT, "Invalid refinement predicate: .*", PUBLIC),
    INVALID_CONTRACT_VARIABLE("SEMANTIC-INVALID-CONTRACT-VARIABLE", Diagnostic.Phase.SEMANTIC,
            Diagnostic.Codes.INVALID_CONTRACT_VARIABLE,
            "Contract variable .*", PUBLIC),
    CONTRACT_DERIVATION_CYCLE("SEMANTIC-CONTRACT-DERIVATION-CYCLE", Diagnostic.Phase.SEMANTIC,
            Diagnostic.Codes.CONTRACT_DERIVATION_CYCLE, "Contract derivation cycle: .*", PUBLIC),
    AMBIGUOUS_CLAUSE_NAME("SEMANTIC-AMBIGUOUS-CLAUSE-NAME", Diagnostic.Phase.SEMANTIC,
            Diagnostic.Codes.AMBIGUOUS_CLAUSE_NAME, "Ambiguous clause name: .*", PUBLIC),
    UNKNOWN_CLAUSE_NAME("SEMANTIC-UNKNOWN-CLAUSE-NAME", Diagnostic.Phase.SEMANTIC,
            Diagnostic.Codes.UNKNOWN_CLAUSE_NAME, "Unknown clause name: .*", PUBLIC),
    CONFLICTING_EFFECT_ALLOWANCE("SEMANTIC-CONFLICTING-EFFECT-ALLOWANCE", Diagnostic.Phase.SEMANTIC,
            Diagnostic.Codes.CONFLICTING_EFFECT_ALLOWANCE, "pure cannot be combined with named effects", PUBLIC),
    INVALID_EFFECT_MODIFIER("SEMANTIC-INVALID-EFFECT-MODIFIER", Diagnostic.Phase.SEMANTIC,
            Diagnostic.Codes.INVALID_EFFECT_MODIFIER, "Effect terms cannot use null or missing modifiers: .*", PUBLIC),
    EFFECT_AS_CONTRACT_ARGUMENT("SEMANTIC-EFFECT-AS-CONTRACT-ARGUMENT", Diagnostic.Phase.SEMANTIC,
            Diagnostic.Codes.EFFECT_AS_CONTRACT_ARGUMENT, "Effect cannot be used as a contract argument: .*", PUBLIC),
    SEMANTIC_EFFECT_ALLOWANCE_EXCEEDED("SEMANTIC-EFFECT-ALLOWANCE-EXCEEDED", Diagnostic.Phase.SEMANTIC,
            Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, "Function effect allowance exceeded: .*", PUBLIC),
    SEMANTIC_UNKNOWN_CALL_EFFECTS("SEMANTIC-UNKNOWN-CALL-EFFECTS", Diagnostic.Phase.SEMANTIC,
            Diagnostic.Codes.UNKNOWN_CALL_EFFECTS, "Callable invocation has no known effect upper bound", PUBLIC),
    INCONSISTENT_OVERLOAD_ARITY("SEMANTIC-INCONSISTENT-OVERLOAD-ARITY", Diagnostic.Phase.SEMANTIC, Diagnostic.Codes.INCONSISTENT_OVERLOAD_ARITY, "Overload variants must have the same arity: .*", PUBLIC),
    MIXED_COLLECTION_SHAPE("SEMANTIC-MIXED-COLLECTION-SHAPE", Diagnostic.Phase.SEMANTIC,
            Diagnostic.Codes.MIXED_COLLECTION_SHAPE,
            "A collection cannot mix named and positional elements", PUBLIC),
    DUPLICATE_FIELD("SEMANTIC-DUPLICATE-FIELD", Diagnostic.Phase.SEMANTIC,
            Diagnostic.Codes.DUPLICATE_FIELD, "Duplicate field: .*", PUBLIC),

    RUNTIME_DUPLICATE_DEFINITION("RUNTIME-DUPLICATE-DEFINITION", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.DUPLICATE_DEFINITION, "Duplicate definition: .*", INTERNAL),
    RUNTIME_PREMATURE_READ("RUNTIME-PREMATURE-READ", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.READ_BEFORE_INITIALIZATION, "Binding read before initialization.*", INTERNAL),
    UNKNOWN_NAME("RUNTIME-UNKNOWN-NAME", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.UNKNOWN_NAME, "Unknown name: .*", PUBLIC),
    NOT_CALLABLE("RUNTIME-NOT-CALLABLE", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_CALLABLE, "Value is not callable: .*", PUBLIC),
    INFIX_NOT_CALLABLE("RUNTIME-INFIX-NOT-CALLABLE", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NOT_CALLABLE, "Named infix target is not callable: .*", PUBLIC),
    INVALID_INFIX_ARITY("RUNTIME-INVALID-INFIX-ARITY", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_INFIX_ARITY, "Named infix function must take exactly two arguments: .*", PUBLIC),
    INVALID_COMPOSITION_LEFT("RUNTIME-INVALID-COMPOSITION-LEFT", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_COMPOSITION_LEFT, "Composition left operand must be a callable requiring at least one argument", PUBLIC),
    INVALID_COMPOSITION_RIGHT("RUNTIME-INVALID-COMPOSITION-RIGHT", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_COMPOSITION_RIGHT, "Composition right operand must be a callable requiring exactly one argument", PUBLIC),
    INVALID_MAP_TRANSFORM("RUNTIME-INVALID-MAP-TRANSFORM", Diagnostic.Phase.RUNTIME,
            Diagnostic.Codes.INVALID_MAP_TRANSFORM,
            "map transform must be a callable requiring exactly one argument", PUBLIC),
    AMBIGUOUS_CALL_ARITY("RUNTIME-AMBIGUOUS-CALL-ARITY", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.TOO_MANY_ARGUMENTS, "Callable accepts fewer than two arguments", PUBLIC),
    TOO_MANY_PARTIAL_ARGUMENTS("INTERNAL-TOO-MANY-PARTIAL-ARGUMENTS", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.TOO_MANY_ARGUMENTS, "Too many arguments for partial expression", INTERNAL),
    TOO_MANY_FUNCTION_ARGUMENTS("INTERNAL-TOO-MANY-FUNCTION-ARGUMENTS", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.TOO_MANY_ARGUMENTS, "Too many arguments for .*", INTERNAL),
    EVALUATION_DEPTH("RUNTIME-EVALUATION-DEPTH", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CALL_DEPTH_EXCEEDED, "Maximum Caret evaluation depth exceeded", INTERNAL),
    CALL_DEPTH("RUNTIME-CALL-DEPTH", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CALL_DEPTH_EXCEEDED, "Maximum Caret call depth exceeded", PUBLIC),
    INVALID_CONDITION("RUNTIME-INVALID-CONDITION", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_CONDITION, "Condition must be Boolean, null, or missing; got: .*", PUBLIC),
    EXPECTED_NUMBER("RUNTIME-EXPECTED-NUMBER", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.EXPECTED_NUMBER, "Expected number, got: .*", PUBLIC),
    EXPECTED_STRING("RUNTIME-EXPECTED-STRING", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.EXPECTED_STRING, "Expected string, got: .*", PUBLIC),
    EXPECTED_SEQUENCE("RUNTIME-EXPECTED-SEQUENCE", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.EXPECTED_SEQUENCE, "Expected sequence, got: .*", PUBLIC),
    EXPECTED_DICTIONARY("RUNTIME-EXPECTED-DICTIONARY", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.EXPECTED_DICTIONARY, "Expected dictionary, got: .*", PUBLIC),
    INVALID_DICTIONARY_KEY("RUNTIME-INVALID-DICTIONARY-KEY", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_DICTIONARY_KEY, "Dictionary key must be a string, got: .*", PUBLIC),
    DIVISION_BY_ZERO("RUNTIME-DIVISION-BY-ZERO", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.DIVISION_BY_ZERO, "Division by zero", PUBLIC),
    NONFINITE_RESULT("RUNTIME-NONFINITE-RESULT", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NON_FINITE_RESULT, "Numeric result is not finite", PUBLIC),
    INVALID_FIELD_TARGET("RUNTIME-INVALID-FIELD-TARGET", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_FIELD_TARGET, "Field access requires a named collection or reflective value, got: .*", PUBLIC),
    MISSING_COLLECTION_FIELD("RUNTIME-MISSING-COLLECTION-FIELD", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.MISSING_FIELD, "Collection has no field: .*", PUBLIC),
    MISSING_REFLECTED_FIELD("RUNTIME-MISSING-REFLECTED-FIELD", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.MISSING_FIELD, "Reflected value has no field: .*", PUBLIC),
    INVALID_DYNAMIC_FIELD("RUNTIME-INVALID-DYNAMIC-FIELD", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_DYNAMIC_FIELD_NAME, "Dynamic field name must be a string, got: .*", PUBLIC),
    RUNTIME_MIXED_COLLECTION_SHAPE("RUNTIME-MIXED-COLLECTION-SHAPE", Diagnostic.Phase.RUNTIME,
            Diagnostic.Codes.MIXED_COLLECTION_SHAPE,
            "A collection cannot mix Field values and positional elements", PUBLIC),
    RUNTIME_DUPLICATE_FIELD("RUNTIME-DUPLICATE-FIELD", Diagnostic.Phase.RUNTIME,
            Diagnostic.Codes.DUPLICATE_FIELD, "Duplicate field: .*", PUBLIC),
    CALLABLE_EQUALITY("RUNTIME-CALLABLE-EQUALITY", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CALLABLE_EQUALITY, "Callable values cannot be compared for equality", PUBLIC),
    CALLABLE_RENDERING("RUNTIME-CALLABLE-RENDERING", Diagnostic.Phase.RUNTIME,
            Diagnostic.Codes.CALLABLE_RENDERING,
            "Callable values do not have a standard textual representation", PUBLIC),
    MIXED_HOLES("RUNTIME-MIXED-HOLES", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.MIXED_HOLE_STYLES, "Cannot mix numbered and unnumbered holes", PUBLIC),
    INVALID_ASSERTION("RUNTIME-INVALID-ASSERTION", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_ASSERTION, "Assertion condition must be Boolean, got: .*", PUBLIC),
    CONTRACT_VIOLATION("RUNTIME-CONTRACT-VIOLATION", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CONTRACT_VIOLATION, "Contract violation for .*", PUBLIC),
    EFFECT_CONSTRAINT_REQUIRES_CALLABLE("RUNTIME-EFFECT-CONSTRAINT-REQUIRES-CALLABLE", Diagnostic.Phase.RUNTIME,
            Diagnostic.Codes.EFFECT_CONSTRAINT_REQUIRES_CALLABLE, "Effect constraint requires a callable value", PUBLIC),
    EFFECT_ALLOWANCE_EXCEEDED("RUNTIME-EFFECT-ALLOWANCE-EXCEEDED", Diagnostic.Phase.RUNTIME,
            Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, "Callable effect allowance exceeded: .*", PUBLIC),
    UNKNOWN_CALL_EFFECTS("RUNTIME-UNKNOWN-CALL-EFFECTS", Diagnostic.Phase.RUNTIME,
            Diagnostic.Codes.UNKNOWN_CALL_EFFECTS, "Callable invocation has no known effect upper bound", PUBLIC),
    NOT_DEREFERENCEABLE("RUNTIME-NOT-DEREFERENCEABLE", Diagnostic.Phase.RUNTIME,
            Diagnostic.Codes.NOT_DEREFERENCEABLE, "Value is not dereferenceable: .*", PUBLIC),
    NO_APPLICABLE_OVERLOAD("RUNTIME-NO-APPLICABLE-OVERLOAD", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.NO_APPLICABLE_OVERLOAD, "No applicable overload: .*", PUBLIC),
    AMBIGUOUS_OVERLOAD("RUNTIME-AMBIGUOUS-OVERLOAD", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.AMBIGUOUS_OVERLOAD, "Ambiguous overload: .*", PUBLIC),
    UNKNOWN_UNARY_OPERATOR("INTERNAL-UNKNOWN-UNARY-OPERATOR", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.UNKNOWN_OPERATOR, "Unknown unary operator: .*", INTERNAL),
    UNKNOWN_BINARY_OPERATOR("INTERNAL-UNKNOWN-BINARY-OPERATOR", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.UNKNOWN_OPERATOR, "Unknown operator: .*", INTERNAL),
    INTERNAL_INVARIANT("INTERNAL-INVARIANT", Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INTERNAL_ERROR, ".*", INTERNAL);

    private final String id;
    private final Diagnostic.Phase phase;
    private final String code;
    private final Pattern message;
    private final String category;

    DiagnosticCatalog(String id, Diagnostic.Phase phase, String code, String messageRegex, String category) {
        this.id = id;
        this.phase = phase;
        this.code = code;
        this.message = Pattern.compile(messageRegex, Pattern.DOTALL);
        this.category = category;
    }

    String id() { return id; }
    Diagnostic.Phase phase() { return phase; }
    String code() { return code; }
    String category() { return category; }

    static DiagnosticCatalog identify(Diagnostic.Phase phase, String code, String message) {
        return Arrays.stream(values())
                .filter(entry -> entry.phase == phase && entry.code.equals(code)
                        && entry.message.matcher(message).matches())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Uncatalogued diagnostic: " + phase + "/" + code + ": " + message));
    }
}
