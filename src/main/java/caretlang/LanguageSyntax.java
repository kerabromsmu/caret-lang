package caretlang;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Central registry for established lexical spellings and binary-operator metadata. */
final class LanguageSyntax {
    enum Precedence { EQUALITY, COMPARISON, ADDITIVE, MULTIPLICATIVE }

    record BinaryOperator(String spelling, Precedence precedence) {}

    private static final List<BinaryOperator> BINARY_OPERATORS = List.of(
            new BinaryOperator("==", Precedence.EQUALITY),
            new BinaryOperator("!=", Precedence.EQUALITY),
            new BinaryOperator(">", Precedence.COMPARISON),
            new BinaryOperator(">=", Precedence.COMPARISON),
            new BinaryOperator("<", Precedence.COMPARISON),
            new BinaryOperator("<=", Precedence.COMPARISON),
            new BinaryOperator("+", Precedence.ADDITIVE),
            new BinaryOperator("-", Precedence.ADDITIVE),
            new BinaryOperator("*", Precedence.MULTIPLICATIVE),
            new BinaryOperator("/", Precedence.MULTIPLICATIVE),
            new BinaryOperator("%", Precedence.MULTIPLICATIVE));

    private static final Map<Precedence, Set<String>> OPERATORS_BY_PRECEDENCE = operatorSets();
    private static final Set<String> BINARY_OPERATOR_SPELLINGS = BINARY_OPERATORS.stream()
            .map(BinaryOperator::spelling).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> RESERVED_WORDS = Set.of(
            "true", "false", "and", "or", "not", "with", "outer", "root", "module");
    private static final Set<String> NON_ARGUMENT_KEYWORDS = Set.of("and", "or", "not");
    private static final Set<String> MULTI_CHARACTER_SYMBOLS = Set.of("==", "!=", ">=", "<=", ">>");
    private static final String SINGLE_CHARACTER_SYMBOLS = "()[]@+-*/%^=<>.&!?~$";

    static int contractParameterArity(String name) {
        return name.equals("Sequence") ? 1 : 0;
    }

    private LanguageSyntax() {}

    static List<BinaryOperator> binaryOperators() { return BINARY_OPERATORS; }
    static Set<String> binaryOperatorSpellings() { return BINARY_OPERATOR_SPELLINGS; }
    static Set<String> operatorsAt(Precedence precedence) { return OPERATORS_BY_PRECEDENCE.get(precedence); }

    static boolean isReservedBinding(String spelling) {
        return RESERVED_WORDS.contains(spelling) || spelling.equals("_")
                || spelling.matches("_[1-9][0-9]*");
    }

    static boolean canStartApplicationArgument(String spelling) {
        return !NON_ARGUMENT_KEYWORDS.contains(spelling);
    }

    static boolean canBeNamedInfix(String spelling) {
        return canStartApplicationArgument(spelling) && !isReservedBinding(spelling);
    }

    static boolean isMultiCharacterSymbol(String spelling) {
        return MULTI_CHARACTER_SYMBOLS.contains(spelling);
    }

    static boolean isSingleCharacterSymbol(char spelling) {
        return SINGLE_CHARACTER_SYMBOLS.indexOf(spelling) >= 0;
    }

    private static Map<Precedence, Set<String>> operatorSets() {
        EnumMap<Precedence, Set<String>> result = new EnumMap<>(Precedence.class);
        for (Precedence precedence : Precedence.values()) result.put(precedence, new LinkedHashSet<>());
        for (BinaryOperator operator : BINARY_OPERATORS) {
            result.get(operator.precedence()).add(operator.spelling());
        }
        result.replaceAll((ignored, spellings) -> Set.copyOf(spellings));
        return Map.copyOf(result);
    }
}
