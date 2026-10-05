package caretlang;

import caretlang.Ast.*;
import caretlang.Lexer.Kind;
import caretlang.Lexer.Token;
import caretlang.Parser.ContractParse;

import java.util.*;

import static caretlang.Parser.adjacent;
import static caretlang.Parser.contractClause;
import static caretlang.Parser.isLambdaParameterPrefix;
import static caretlang.Parser.lambdaParameters;
import static caretlang.Parser.topLevelLambdaArrow;

final class ExpressionParser {
    private final List<Token> tokens;
    private final List<Expr> continuationArguments;
    private int current;

    ExpressionParser(List<Token> tokens) {
        this(tokens, tokens.isEmpty()
                ? new SourcePosition(0, 1, 1)
                : tokens.getLast().span().end(), List.of());
    }

    ExpressionParser(List<Token> tokens, SourcePosition end) {
        this(tokens, end, List.of());
    }

    ExpressionParser(List<Token> tokens, SourcePosition end, List<Expr> continuationArguments) {
        this.tokens = new ArrayList<>(tokens);
        this.tokens.add(new Token(Kind.EOF, "", SourceSpan.point(end)));
        this.continuationArguments = List.copyOf(continuationArguments);
    }

    Expr parse() {
        Expr expression = arrow();
        if (!atEnd()) throw error("Unexpected token: " + peek().text());
        return expression;
    }

    private Expr arrow() {
        int lambdaArrow = topLevelLambdaArrow(tokens.subList(current, tokens.size() - 1));
        if (lambdaArrow >= 0 && isLambdaParameterPrefix(
                tokens.subList(current, current + lambdaArrow), tokens.get(current + lambdaArrow))) {
            int absoluteArrow = current + lambdaArrow;
            Token marker = tokens.get(absoluteArrow);
            List<Parameter> parameters = lambdaParameters(tokens.subList(current, absoluteArrow), marker);
            current = absoluteArrow + 1;
            if (atEnd()) throw error(Diagnostic.Codes.PARSE_INVALID_SYNTAX,
                    "Lambda body must follow '->' or be indented");
            Expr body = arrow();
            return new Lambda(parameters, List.of(new ExprStmt(body, body.span())),
                    SourceSpan.cover(parameters.isEmpty() ? marker.span() : parameters.getFirst().span(), body.span()));
        }
        if (peek().text().equals("[") && arrowClose(current) >= 0) {
            Token open = tokens.get(current++);
            ArrayList<List<Expr>> parameters = new ArrayList<>();
            while (!peek().text().equals("]")) {
                if (atEnd()) throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ']'");
                if (match("(")) {
                    ArrayList<Expr> conjunction = new ArrayList<>();
                    while (!peek().text().equals(")")) {
                        if (atEnd()) throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ')'");
                        conjunction.add(contractRequirement());
                    }
                    consume(")", "Expected ')'");
                    if (conjunction.isEmpty()) throw error(Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                            "Arrow parameter requirement cannot be empty");
                    parameters.add(List.copyOf(conjunction));
                } else {
                    parameters.add(List.of(contractRequirement()));
                }
            }
            consume("]", "Expected ']'");
            consume("->", "Expected '->' after arrow parameter requirements");
            Expr result;
            ArrayList<Name> effectTerms = new ArrayList<>();
            boolean explicitPure = false;
            if (peek().text().equals("[") && arrowClose(current) >= 0) {
                result = arrow();
            } else if (match("(")) {
                ArrayList<Expr> resultRequirements = new ArrayList<>();
                while (!peek().text().equals(")")) {
                    if (atEnd()) throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ')'");
                    if (peek().kind() == Kind.IDENT && isEffectSpelling(peek().text())) {
                        Token effect = tokens.get(current++);
                        if (effect.text().equals("pure")) explicitPure = true;
                        else effectTerms.add(new Name(effect.text(), effect.span()));
                    } else resultRequirements.add(contractRequirement());
                }
                Token close = peek();
                consume(")", "Expected ')'");
                if (resultRequirements.isEmpty()) throw error(Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                        "Arrow contract requires exactly one result contract");
                result = resultRequirements.size() == 1 ? resultRequirements.getFirst()
                        : new ContractTerms(resultRequirements, SourceSpan.cover(
                        resultRequirements.getFirst().span(), close.span()));
            } else {
                ArrayList<Expr> resultTerms = new ArrayList<>();
                do resultTerms.add(contractRequirement());
                while (!atEnd() && (peek().kind() == Kind.IDENT || peek().text().equals("(")));
                result = resultTerms.size() == 1 ? resultTerms.getFirst()
                        : new ContractTerms(resultTerms,
                        SourceSpan.cover(resultTerms.getFirst().span(), resultTerms.getLast().span()));
            }
            return new ArrowContract(List.copyOf(parameters), result, effectTerms, explicitPure,
                    SourceSpan.cover(open.span(), result.span()));
        }
        return lowPrecedenceApplication();
    }

    private boolean isEffectSpelling(String name) {
        return name.equals("pure") || name.equals("Output") || name.equals("StateRead")
                || name.equals("StateWrite") || name.equals("TestReport");
    }

    /** Returns the matching close only when it is immediately followed by an arrow. */
    private int arrowClose(int start) {
        int depth = 0;
        for (int index = start; index < tokens.size(); index++) {
            String text = tokens.get(index).text();
            if (text.equals("[") || text.equals("(") || text.equals("{")) depth++;
            else if (text.equals("]") || text.equals(")") || text.equals("}")) {
                depth--;
                if (depth == 0) {
                    return text.equals("]") && index + 1 < tokens.size()
                            && tokens.get(index + 1).text().equals("->") ? index : -1;
                }
            }
        }
        return -1;
    }

    private Expr contractRequirement() {
        if (match("(")) {
            Token open = previous();
            ArrayList<Expr> terms = new ArrayList<>();
            while (!peek().text().equals(")")) {
                if (atEnd()) throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ')'");
                terms.add(contractRequirement());
            }
            Token close = peek();
            consume(")", "Expected ')'");
            if (terms.isEmpty()) throw error(Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                    "A contract parameter must be one contract");
            Expr grouped = terms.size() == 1 ? terms.getFirst()
                    : new ContractTerms(terms, SourceSpan.cover(open.span(), close.span()));
            return new Group(grouped, SourceSpan.cover(open.span(), close.span()));
        }
        if (peek().text().equals("_")) {
            throw error(Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                    "Unnumbered contract variable is invalid");
        }
        if (peek().kind() != Kind.IDENT) {
            throw error(Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                    "Arrow contract requires contract names");
        }
        Token name = tokens.get(current++);
        Expr requirement = name.text().matches("_[1-9][0-9]*")
                ? numberedContractVariable(name) : new Name(name.text(), name.span());
        int arity = name.text().startsWith("_") ? 0 : BuiltinContract.named(name.text())
                .map(ContractDescriptor::parameterArity).orElse(0);
        for (int index = 0; index < arity; index++) {
            Expr argument = contractRequirement();
            requirement = new Apply(requirement, argument,
                    SourceSpan.cover(requirement.span(), argument.span()));
        }
        SourceSpan end = requirement.span();
        boolean nullable = false;
        boolean optional = false;
        if (peek().text().equals("?") && adjacent(end, peek())) {
            nullable = true;
            end = tokens.get(current++).span();
        }
        if (peek().text().equals("~") && adjacent(end, peek())) {
            optional = true;
            end = tokens.get(current++).span();
        }
        return nullable || optional ? new ContractModifier(requirement, nullable, optional,
                SourceSpan.cover(requirement.span(), end)) : requirement;
    }

    private Expr numberedContractVariable(Token token) {
        try {
            return new ContractVariable(Integer.parseInt(token.text().substring(1)), token.span());
        } catch (NumberFormatException ignored) {
            throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_HOLE,
                    "Numbered contract variable index is too large", token.span());
        }
    }

    private Expr lowPrecedenceApplication() {
        Expr function = composition();
        if (!match("$")) return function;
        Expr argument = arrow();
        return new Apply(function, argument, SourceSpan.cover(function.span(), argument.span()));
    }

    private Expr composition() {
        Expr expression = conditional();
        while (match(">>")) {
            Expr right = conditional();
            expression = new Compose(expression, right,
                    SourceSpan.cover(expression.span(), right.span()));
        }
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
        Expr expr = term();
        while (peek().kind() == Kind.IDENT && LanguageSyntax.canBeNamedInfix(peek().text())
                && canStartAtom(peekNext()) && nextTokenIsNotAdjacentContractModifier()) {
            Token function = tokens.get(current++);
            Expr right = term();
            expr = new NamedInfix(expr, new Name(function.text(), function.span()), right,
                    SourceSpan.cover(expr.span(), right.span()));
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (matchOperators(LanguageSyntax.Precedence.ADDITIVE)) {
            String op = previous().text();
            Expr right = factor();
            expr = new Binary(op, expr, right, SourceSpan.cover(expr.span(), right.span()));
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary(true);
        while (matchOperators(LanguageSyntax.Precedence.MULTIPLICATIVE)) {
            String op = previous().text();
            Expr right = unary(true);
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
        if (matchIdent("not")) {
            Token operator = previous();
            Expr operand = unary();
            return new Unary("not", operand, SourceSpan.cover(operator.span(), operand.span()));
        }
        return application(namedInfixOperand);
    }

    private Expr application(boolean namedInfixOperand) {
        Expr expr = postfix();
        if (namedInfixOperand && expr instanceof Name
                && peek().kind() == Kind.IDENT
                && LanguageSyntax.canBeNamedInfix(peek().text()) && canStartAtom(peekNext())
                && nextTokenIsNotAdjacentContractModifier()) {
            Expr middle = postfix();
            Expr last = postfix();
            expr = new AmbiguousCall(expr, middle, last, SourceSpan.cover(expr.span(), last.span()));
        }
        while (canStartAtom(peek())) {
            if (namedInfixOperand && isValueLed(expr)
                    && peek().kind() == Kind.IDENT && LanguageSyntax.canBeNamedInfix(peek().text())
                    && canStartAtom(peekNext()) && nextTokenIsNotAdjacentContractModifier()) break;
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
        if (expression instanceof Name) return true;
        return !(expression instanceof Apply) && !(expression instanceof Group);
    }

    private Token peekNext() {
        int next = Math.min(current + 1, tokens.size() - 1);
        return tokens.get(next);
    }

    private boolean nextTokenIsNotAdjacentContractModifier() {
        Token currentToken = peek();
        Token nextToken = peekNext();
        return !(nextToken.text().equals("?") || nextToken.text().equals("~"))
                || currentToken.span().end().offset() != nextToken.span().start().offset();
    }

    private Expr postfix() {
        Expr expr = primary();
        while (true) {
            if (match(".")) {
                if (match("@")) {
                    Token name = consumeIdentifier("Expected field name after '.@'");
                    if (expr instanceof Name root && root.name().equals("outer")
                            || expr instanceof OuterPath path && path.name() == null) {
                        int hops = expr instanceof OuterPath path ? path.hops() : 1;
                        Expr path = new OuterPath(hops, name.text(), SourceSpan.cover(expr.span(), name.span()));
                        expr = new Reflect(path, SourceSpan.cover(expr.span(), name.span()));
                        continue;
                    }
                    Expr field = new Field(expr, name.text(), false, SourceSpan.cover(expr.span(), name.span()));
                    expr = new Reflect(field, SourceSpan.cover(expr.span(), name.span()));
                    continue;
                }
                Token name = consumeIdentifier("Expected field name after '.'");
                boolean optional = match("~");
                SourceSpan end = optional ? previous().span() : name.span();
                if (expr instanceof Name root && root.name().equals("outer")
                        || expr instanceof OuterPath) {
                    if (optional) throw error(Diagnostic.Codes.PARSE_INVALID_EXPRESSION,
                            "outer paths do not support optional access");
                    int hops = expr instanceof OuterPath path ? path.hops() : 1;
                    if (expr instanceof OuterPath path && path.name() != null) {
                        expr = new Field(expr, name.text(), false, SourceSpan.cover(expr.span(), end));
                    } else {
                        expr = new OuterPath(name.text().equals("outer") ? hops + 1 : hops,
                                name.text().equals("outer") ? null : name.text(),
                                SourceSpan.cover(expr.span(), end));
                    }
                    continue;
                }
                expr = new Field(expr, name.text(), optional, SourceSpan.cover(expr.span(), end));
                continue;
            }
            if (peek().text().equals(":")
                    && expr.span().end().offset() == peek().span().start().offset()) {
                match(":");
                expr = new Dereference(expr, SourceSpan.cover(expr.span(), previous().span()));
                continue;
            }
            if (peek().text().equals("{")
                    && expr.span().end().offset() == peek().span().start().offset()) {
                match("{");
                consume("}", "Expected '}' after container read");
                expr = new ContainerRead(expr, SourceSpan.cover(expr.span(), previous().span()));
                continue;
            }
            if (peek().text().equals("[")
                    && expr.span().end().offset() == peek().span().start().offset() && match("[")) {
                if (atEnd()) {
                    throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ']'");
                }
                Expr name = lowPrecedenceApplication();
                consume("]", "Expected ']'");
                Token close = previous();
                boolean optional = match("~");
                SourceSpan end = optional ? previous().span() : close.span();
                expr = new DynamicField(expr, name, optional, SourceSpan.cover(expr.span(), end));
                continue;
            }
            if ((peek().text().equals("?") || peek().text().equals("~"))
                    && expr.span().end().offset() == peek().span().start().offset()) {
                boolean nullable = false;
                boolean optional = false;
                SourceSpan modifierEnd = expr.span();
                if (match("?")) {
                    nullable = true;
                    modifierEnd = previous().span();
                }
                if (peek().text().equals("~")
                        && modifierEnd.end().offset() == peek().span().start().offset()) {
                    match("~");
                    optional = true;
                    modifierEnd = previous().span();
                }
                if (!nullable && !optional) break;
                if ((peek().text().equals("?") || peek().text().equals("~"))
                        && modifierEnd.end().offset() == peek().span().start().offset()) {
                    throw error(Diagnostic.Codes.PARSE_INVALID_CONTRACT,
                            "Contract clause modifiers must use canonical form T, T?, T~, or T?~");
                }
                expr = new ContractModifier(expr, nullable, optional,
                        SourceSpan.cover(expr.span(), modifierEnd));
                continue;
            }
            break;
        }
        return expr;
    }

    private Expr primary() {
        if (match("@")) {
            Token operator = previous();
            Expr operand = reflectionPrimary();
            return new Reflect(operand, SourceSpan.cover(operator.span(), operand.span()));
        }
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
        if (match("{")) {
            Token open = previous();
            int close = matchingClose(tokens, current - 1, "{", "}");
            if (close < 0) throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected '}'");
            List<Token> contents = tokens.subList(current, close);
            if (contents.isEmpty()) {
                throw error(Diagnostic.Codes.PARSE_INVALID_EXPRESSION,
                        "Container literal requires an initial value");
            }
            ContractClause contracts = null;
            int valueStart = 0;
            if (contents.getFirst().text().equals("(")
                    && matchingClose(contents, 0, "(", ")") < contents.size() - 1) {
                ContractParse clause = Objects.requireNonNull(contractClause(contents, 0));
                contracts = clause.clause();
                valueStart = clause.next();
            }
            if (valueStart >= contents.size()) {
                throw error(Diagnostic.Codes.PARSE_INVALID_EXPRESSION,
                        "Container literal requires an initial value");
            }
            Expr value = new ExpressionParser(contents.subList(valueStart, contents.size()),
                    tokens.get(close).span().start()).parse();
            current = close + 1;
            return new ContainerLiteral(contracts, value, SourceSpan.cover(open.span(), tokens.get(close).span()));
        }
        if (match("[")) {
            Token open = previous();
            boolean multiline = collectionCloseLine() > open.span().start().line();
            ArrayList<CollectionElement> elements = new ArrayList<>();
            while (!peek().text().equals("]")) {
                if (atEnd()) throw error(Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER, "Expected ']'");
                if (match("^")) {
                    Token marker = previous();
                    if (peek().kind() != Kind.IDENT) {
                        throw error(Diagnostic.Codes.PARSE_INVALID_SYNTAX,
                                "Named collection element requires a field name");
                    }
                    Token name = tokens.get(current++);
                    consume("=", "Expected '=' after named collection field");
                    Expr value = multiline ? collectionLineExpression() : lowPrecedenceApplication();
                    SourceSpan span = SourceSpan.cover(marker.span(), value.span());
                    elements.add(new NamedElement(name.text(), value, span));
                    continue;
                }
                // In a multiline literal, each top-level physical line is one ordinary
                // expression. Same-line literals retain eager atom boundaries.
                Expr value = multiline ? collectionLineExpression()
                        : hasTopLevelOperatorBeforeCollectionEnd()
                        ? lowPrecedenceApplication() : postfix();
                if (!multiline && value instanceof Group
                        && (peek().text().equals("_")
                        || peek().kind() == Kind.IDENT
                        && peek().text().matches("_[1-9][0-9]*"))) {
                    Expr hole = primary();
                    value = new Apply(value, hole, SourceSpan.cover(value.span(), hole.span()));
                }
                elements.add(new PositionalElement(value, value.span()));
            }
            consume("]", "Expected ']'");
            return new CollectionLiteral(List.copyOf(elements),
                    SourceSpan.cover(open.span(), previous().span()));
        }
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
            Expr expr = arrow();
            consume(")", "Expected ')'");
            return new Group(expr, SourceSpan.cover(open.span(), previous().span()));
        }
        throw error("Expected expression, found '" + peek().text() + "'");
    }

    private Expr reflectionPrimary() {
        Token token = peek();
        boolean allowed = token.kind() == Kind.IDENT || token.kind() == Kind.NUMBER
                || token.kind() == Kind.STRING || token.text().equals("true")
                || token.text().equals("false") || token.text().equals("?")
                || token.text().equals("~") || token.text().equals("(")
                || token.text().equals("[") || token.text().equals("{");
        if (!allowed) {
            throw error(Diagnostic.Codes.PARSE_INVALID_EXPRESSION,
                    "Expected an identifier, literal, or parenthesized expression after '@'");
        }
        return primary();
    }

    private int collectionCloseLine() {
        int depth = 0;
        for (int index = current; index < tokens.size(); index++) {
            String text = tokens.get(index).text();
            if (text.equals("[") || text.equals("(") || text.equals("{")) depth++;
            else if (text.equals("]") || text.equals(")") || text.equals("}")) {
                if (depth == 0 && text.equals("]")) return tokens.get(index).span().start().line();
                depth--;
            }
        }
        return openEndedLine();
    }

    private Expr collectionLineExpression() {
        int start = current;
        int depth = 0;
        int end = start;
        for (; end < tokens.size(); end++) {
            Token token = tokens.get(end);
            String text = token.text();
            if (depth == 0) {
                if (text.equals("]")) break;
                if (end > start
                        && token.span().start().line() > tokens.get(end - 1).span().end().line()) break;
            }
            if (text.equals("[") || text.equals("(") || text.equals("{")) depth++;
            else if (text.equals("]") || text.equals(")") || text.equals("}")) depth--;
        }
        if (end == start) throw error("Expected collection element expression");
        SourcePosition expressionEnd = tokens.get(end - 1).span().end();
        Expr expression = new ExpressionParser(tokens.subList(start, end), expressionEnd).parse();
        current = end;
        return expression;
    }

    private int openEndedLine() {
        return tokens.getLast().span().end().line();
    }

    private boolean hasTopLevelOperatorBeforeCollectionEnd() {
        int depth = 0;
        for (int index = current; index < tokens.size(); index++) {
            String text = tokens.get(index).text();
            if (text.equals("(") || text.equals("[") || text.equals("{")) depth++;
            else if (text.equals(")") || text.equals("]") || text.equals("}")) {
                if (depth == 0) return false;
                depth--;
            } else if (depth == 0 && (text.equals("$") || text.equals("&") || text.equals(">>")
                    || LanguageSyntax.binaryOperatorSpellings().contains(text)
                    || text.equals("and") || text.equals("or"))) return true;
        }
        return false;
    }

    private Expr numberLiteral(Token token) {
        if (!token.text().contains(".")) {
            try {
                return new Literal(new Value.Num(new java.math.BigInteger(token.text()), token.text()), token.span());
            } catch (NumberFormatException ignored) {
                throw new LangException(Diagnostic.Phase.PARSER, Diagnostic.Codes.PARSE_INVALID_NUMBER,
                        "Invalid number literal", token.span());
            }
        }
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
        return new Literal(new Value.Num(value, token.text()), token.span());
    }

    private boolean canStartAtom(Token token) {
        if (token.kind() == Kind.NUMBER || token.kind() == Kind.STRING) return true;
        if (token.kind() == Kind.IDENT) {
            return LanguageSyntax.canStartApplicationArgument(token.text());
        }
        return Set.of("(", "[", "{", "?", "~", "@").contains(token.text());
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
        } else if (token.text().equals("{") || token.text().equals("[")) {
            String open = token.text();
            String close = open.equals("{") ? "}" : "]";
            int depth = 1;
            end = index + 1;
            while (end < tokens.size() && depth > 0) {
                String text = tokens.get(end++).text();
                if (text.equals(open)) depth++;
                else if (text.equals(close)) depth--;
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
            } else if (tokens.get(end).text().equals("{") && end + 1 < tokens.size()
                    && tokens.get(end + 1).text().equals("}")) {
                end += 2;
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

    private Token consumeIdentifier(String message) {
        if (peek().kind() == Kind.IDENT) return tokens.get(current++);
        throw error(codeForExpectedDelimiter(message), message);
    }

    private void consume(String text, String message) {
        if (!match(text)) throw error(codeForExpectedDelimiter(message), message);
    }

    private String codeForExpectedDelimiter(String message) {
        return message.startsWith("Expected ')'") || message.startsWith("Expected ']'")
                || message.startsWith("Expected '}'")
                ? Diagnostic.Codes.PARSE_UNCLOSED_DELIMITER
                : Diagnostic.Codes.PARSE_INVALID_EXPRESSION;
    }

    private static int matchingClose(List<Token> source, int open, String opening, String closing) {
        int depth = 0;
        for (int index = open; index < source.size(); index++) {
            String text = source.get(index).text();
            if (text.equals(opening)) depth++;
            else if (text.equals(closing) && --depth == 0) return index;
        }
        return -1;
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
