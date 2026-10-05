package caretlang;

import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static caretlang.InterpreterTestSupport.*;
import caretlang.InterpreterTestSupport.ModeExecution;

final class NumericTemplateInterpreterTest {
    @Test
    void exactIntegerDomainsArithmeticAndContextualFloatAreValueBased() {
        assertEquals("18446744073709551615\ntrue\nfalse\nfalse\ntrue\n2\n-2\n2.5\n",
                execute("""
                        maximum = 18446744073709551615
                        print maximum
                        print UInt64 maximum
                        print UInt64 (maximum + 1)
                        print Float 0.1
                        (Float) selected = 0.1
                        print Float selected
                        print 5 div 2
                        print (-7) div 3
                        print 5 / 2
                        """));
    }

    @Test
    void broadDivisionReportsPrecisionWarningAndStrictResultRejectsLoss() {
        Interpreter interpreter = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
        interpreter.execute(new Parser("(Number) ratio = 1 / 3").parseProgram());
        assertEquals(1, interpreter.warnings().size());
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, interpreter.warnings().getFirst().code());
        assertEquals(1, interpreter.warnings().getFirst().primarySpan().start().line());

        LangException strict = assertThrows(LangException.class,
                () -> execute("(Double) ratio = 1 / 3"));
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, strict.diagnostic().code());
        LangException result = assertThrows(LangException.class, () -> execute("""
                (Double) divide ignored = 1 / 3
                divide 0
                """));
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, result.diagnostic().code());
        LangException parameter = assertThrows(LangException.class, () -> execute("""
                identity (Double) value = value
                identity (1 / 3)
                """));
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, parameter.diagnostic().code());
    }

    @Test
    void mixedNumericOperationsReportLossBeforePromotingExactIntegers() {
        for (String operator : List.of("+", "-", "*", "/", "%")) {
            for (String expression : List.of("large " + operator + " 0.5", "0.5 " + operator + " large")) {
                for (String broad : List.of("Number", "Real")) {
                    Interpreter interpreter = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
                    interpreter.execute(new Parser("large = 9007199254740993\n(" + broad
                            + ") result = " + expression).parseProgram());
                    assertEquals(1, interpreter.warnings().size(), expression);
                    Diagnostic warning = interpreter.warnings().getFirst();
                    assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, warning.code());
                    assertEquals(Diagnostic.Phase.RUNTIME, warning.phase());
                    assertEquals(2, warning.primarySpan().start().line());
                    assertEquals(broad.equals("Number") ? 19 : 17, warning.primarySpan().start().column());
                }
                LangException error = assertThrows(LangException.class, () -> execute(
                        "large = 9007199254740993\n(Double) result = " + expression));
                assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, error.diagnostic().code());
                assertEquals(Diagnostic.Phase.RUNTIME, error.diagnostic().phase());
                assertEquals(2, error.span().start().line());
                assertEquals(19, error.span().start().column());
            }
        }
    }

    @Test
    void mixedPrecisionPolicyRespectsParameterAndDeclaredResultBoundaries() {
        LangException parameter = assertThrows(LangException.class, () -> execute("""
                large = 9007199254740993
                identity (Double) value = value
                identity (large + 0.5)
                """));
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, parameter.diagnostic().code());
        assertEquals(3, parameter.span().start().line());
        LangException result = assertThrows(LangException.class, () -> execute("""
                (Double) calculate value = value + 0.5
                calculate 9007199254740993
                """));
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, result.diagnostic().code());
        assertEquals(1, result.span().start().line());
        Interpreter broad = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
        broad.execute(new Parser("""
                (Number) calculate value = value + 0.5
                result = calculate 9007199254740993
                """).parseProgram());
        assertEquals(1, broad.warnings().size());
    }

    @Test
    void exactPromotionAndExplicitConversionDoNotWarnAboutFloatingRounding() {
        Interpreter interpreter = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
        interpreter.execute(new Parser("""
                exact = 9007199254740992
                (Double) rounded = exact + 0.5
                large = 9007199254740993
                converted = (Double) large
                (Double) deliberate = converted + 0.5
                (Integer) sum = large + 1
                (Double) ordinary = 0.1 + 0.2
                """).parseProgram());
        assertTrue(interpreter.warnings().isEmpty());
    }

    @Test
    void dynamicPrecisionLossRetainsRuntimePhaseAndExactLocation() {
        String source = """
                one = 1
                three = 3
                (Number) ratio = one / three
                """;
        Interpreter interpreter = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
        List<Ast.Stmt> program = new Parser(source).parseProgram();
        interpreter.validate(program);
        assertTrue(interpreter.warnings().isEmpty());
        interpreter.execute(program);
        assertEquals(1, interpreter.warnings().size());
        Diagnostic warning = interpreter.warnings().getFirst();
        assertEquals(Diagnostic.Phase.RUNTIME, warning.phase());
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, warning.code());
        assertSame(DiagnosticCatalog.IMPLICIT_PRECISION_LOSS,
                DiagnosticCatalog.identify(warning.phase(), warning.code(), warning.message()));
        assertEquals(3, warning.primarySpan().start().line());
        assertEquals(18, warning.primarySpan().start().column());
        assertEquals("Line 3, column 18: Implicit numeric precision loss", warning.render());

        LangException strict = assertThrows(LangException.class,
                () -> execute(source.replace("(Number)", "(Double)")));
        assertSame(DiagnosticCatalog.IMPLICIT_PRECISION_LOSS, strict.catalogEntry());
        assertEquals(Diagnostic.Phase.RUNTIME, strict.diagnostic().phase());
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, strict.diagnostic().code());
        assertEquals(warning.render(), strict.getMessage());
    }

    @Test
    void literalPrecisionLossIsReportedAtAnalysisOnce() {
        Interpreter interpreter = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
        List<Ast.Stmt> program = new Parser("(Number) ratio = 1 / 3").parseProgram();
        interpreter.validate(program);
        assertEquals(1, interpreter.warnings().size());
        Diagnostic warning = interpreter.warnings().getFirst();
        assertEquals(Diagnostic.Phase.SEMANTIC, warning.phase());
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, warning.code());
        assertEquals(1, warning.primarySpan().start().line());
        assertEquals(18, warning.primarySpan().start().column());
        assertSame(DiagnosticCatalog.STATIC_IMPLICIT_PRECISION_LOSS,
                DiagnosticCatalog.identify(warning.phase(), warning.code(), warning.message()));
        assertEquals("Line 1, column 18: Implicit numeric precision loss\n"
                + "  Note: Line 1, column 1: Numeric result requirement", warning.render());
        interpreter.execute(program);
        assertTrue(interpreter.warnings().isEmpty());

        List<Ast.Stmt> repeatable = new Parser("print 1 / 3").parseProgram();
        interpreter.execute(repeatable);
        assertEquals(1, interpreter.warnings().size());
        interpreter.execute(repeatable);
        assertEquals(1, interpreter.warnings().size());
    }

    @Test
    void numericPolicyFollowsContractAliasesAndNarrowedOverloads() {
        Interpreter broad = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
        broad.execute(new Parser("MyNumber = Number\n(MyNumber) ratio = 1 / 3").parseProgram());
        assertEquals(1, broad.warnings().size());
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, broad.warnings().getFirst().code());

        LangException overload = assertThrows(LangException.class, () -> execute("""
                choose (Int8) selector (Double) value = value
                choose (String) selector (Double) value = value
                choose 1 (1 / 3)
                """));
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, overload.diagnostic().code());
        assertEquals(3, overload.diagnostic().primarySpan().start().line());
    }

    @Test
    void modifiedAndDerivedNumericRequirementsKeepStrictPolicyAcrossAliases() {
        for (String modifier : List.of("?", "~", "?~")) {
            for (String declarations : List.of("", "Alias = Double" + modifier + "\n",
                    "First = Double" + modifier + "\nAlias = First\n",
                    "Base = contract Double\nAlias = Base" + modifier + "\n")) {
                String requirement = declarations.isEmpty() ? "Double" + modifier : "Alias";
                for (boolean literal : List.of(true, false)) {
                    String prefix = declarations + (literal ? "" : "one = 1\nthree = 3\n");
                    String expression = literal ? "1 / 3" : "one / three";
                    String binding = "(" + requirement + ") result = " + expression;
                    LangException failure = assertThrows(LangException.class,
                            () -> execute(prefix + binding), prefix + binding);
                    assertEquals(literal ? Diagnostic.Phase.SEMANTIC : Diagnostic.Phase.RUNTIME,
                            failure.diagnostic().phase());
                    assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, failure.diagnostic().code());
                    assertEquals(1 + prefix.lines().count(), failure.diagnostic().primarySpan().start().line());
                    assertEquals(binding.indexOf(expression) + 1, failure.diagnostic().primarySpan().start().column());
                }
            }
        }
        for (String base : List.of("Double", "Integer", "Natural", "Float", "Int8", "[Number Double]")) {
            LangException failure = assertThrows(LangException.class,
                    () -> execute("Derived = contract " + base + "\n(Derived) result = 1 / 3"));
            assertEquals(Diagnostic.Phase.SEMANTIC, failure.diagnostic().phase());
            assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, failure.diagnostic().code());
            assertEquals(2, failure.diagnostic().primarySpan().start().line());
            assertEquals(20, failure.diagnostic().primarySpan().start().column());
        }
    }

    @Test
    void aliasedNumericPolicyAppliesAtParameterAndFunctionResultBoundaries() {
        for (String declaration : List.of("Alias = Double?~", "Base = contract Double\nAlias = Base?~")) {
            String prefix = declaration + "\none = 1\nthree = 3\n";
            for (String argument : List.of("one / three", "1 / 3")) {
                LangException parameter = assertThrows(LangException.class, () -> execute(prefix
                        + "identity (Alias) value = value\nidentity (" + argument + ")"));
                assertEquals(Diagnostic.Phase.RUNTIME, parameter.diagnostic().phase());
                assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, parameter.diagnostic().code());
                assertEquals(prefix.lines().count() + 2, parameter.diagnostic().primarySpan().start().line());
                assertEquals(11, parameter.diagnostic().primarySpan().start().column());
            }
            for (boolean literal : List.of(true, false)) {
                String body = literal ? "1 / 3" : "one / three";
                LangException result = assertThrows(LangException.class, () -> execute(prefix
                        + "(Alias) calculate ignored = " + body + "\ncalculate 0"));
                assertEquals(literal ? Diagnostic.Phase.SEMANTIC : Diagnostic.Phase.RUNTIME,
                        result.diagnostic().phase());
                assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, result.diagnostic().code());
                assertEquals(prefix.lines().count() + 1, result.diagnostic().primarySpan().start().line());
                assertEquals(29, result.diagnostic().primarySpan().start().column());
            }
        }
    }

    @Test
    void dynamicDerivedRequirementsDoNotLetStaticWarningsBypassStrictChecks() {
        LangException failure = assertThrows(LangException.class, () -> execute("""
                (Boolean) positive value = value > 0
                Derived = contract [Double positive]
                (Derived) result = 1 / 3
                """));
        assertEquals(Diagnostic.Phase.RUNTIME, failure.diagnostic().phase());
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, failure.diagnostic().code());
        assertEquals(3, failure.diagnostic().primarySpan().start().line());
        assertEquals(20, failure.diagnostic().primarySpan().start().column());
    }

    @Test
    void modifiedPoliciesPreserveAbsenceBroadResultsConversionsAndLexicalShadowing() {
        assertEquals("?\n~\n?\n~\ntrue\n", execute("""
                Alias = Double?~
                (Alias) presentNull = ?
                (Alias) missing = ~
                identity (Alias) value = value
                (Alias) absent ignored = ~
                print presentNull
                print missing
                print identity ?
                print absent 0
                (Alias) converted = (Double) 9007199254740993
                print converted == 9007199254740992
                """));
        for (String base : List.of("Number", "Real")) {
            Interpreter interpreter = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
            interpreter.execute(new Parser("Alias = " + base + "?~\n(Alias) ratio = 1 / 3").parseProgram());
            assertEquals(1, interpreter.warnings().size());
            assertEquals(Diagnostic.Phase.SEMANTIC, interpreter.warnings().getFirst().phase());
            interpreter.execute(new Parser("""
                    StrictAlias = Double?~
                    (Alias) calculate ignored =
                      one = 1
                      three = 3
                      one / three
                    (StrictAlias) result = calculate 0
                    """).parseProgram());
            assertEquals(1, interpreter.warnings().size());
            assertEquals(Diagnostic.Phase.RUNTIME, interpreter.warnings().getFirst().phase());
        }
        Interpreter shadowing = new Interpreter(new PrintStream(java.io.OutputStream.nullOutputStream()));
        shadowing.execute(new Parser("""
                Alias = Double?~
                calculate ignored =
                  Alias = Number?~
                  (Alias) ratio = 1 / 3
                  ratio
                calculate 0
                """).parseProgram());
        assertEquals(1, shadowing.warnings().size());
        assertEquals(Diagnostic.Phase.SEMANTIC, shadowing.warnings().getFirst().phase());
    }

    @Test
    void knownNumericParameterAndResultContextsSelectLiteralFormats() {
        assertEquals("true\ntrue\ntrue\n", execute("""
                identity (Float) value = value
                (Float) make ignored = 0.1
                (Float) grouped = (0.1)
                print Float (identity 0.1)
                print Float (make 0)
                print Float grouped
                """));
    }

    @Test
    void integerDivisionRetainsOperatorPrecedenceAliasesAndHoles() {
        assertEquals("5\n-2\n-2\n2\n3\n5\n", execute("""
                print 10 div 3 + 2
                print (-7) div 3
                print 7 div (-3)
                print div 7 3
                quotient = div
                print quotient 10 3
                halves = div _ 2
                print halves 10
                """));
        LangException zero = assertThrows(LangException.class, () -> execute("print 3 div 0"));
        assertEquals(Diagnostic.Codes.DIVISION_BY_ZERO, zero.diagnostic().code());
    }

    @Test
    void exactRemainderAndTrueDivisionCoverSignsAndSignedZero() {
        assertEquals("-1\n1\n2\n-2\n-3.5\n2\ntrue\ntrue\n", execute("""
                print (-7) % 3
                print 7 % (-3)
                print (-7) div (-3)
                print 7 div (-3)
                print (-7) / 2
                print 6 / 3
                print (-0.0) == 0
                print Float (-0.0)
                """));
    }

    @Test
    void allConcreteIntegerFormatsRespectExactAdjacentBoundaries() {
        StringBuilder program = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        for (int width : List.of(8, 16, 32, 64)) {
            java.math.BigInteger signedMin = java.math.BigInteger.ONE.shiftLeft(width - 1).negate();
            java.math.BigInteger signedMax = java.math.BigInteger.ONE.shiftLeft(width - 1)
                    .subtract(java.math.BigInteger.ONE);
            java.math.BigInteger unsignedMax = java.math.BigInteger.ONE.shiftLeft(width)
                    .subtract(java.math.BigInteger.ONE);
            String signed = "Int" + width;
            String unsigned = "UInt" + width;
            program.append("print ").append(signed).append(" (").append(signedMin).append(")\n")
                    .append("print ").append(signed).append(' ').append(signedMax).append('\n')
                    .append("print ").append(signed).append(" 0\n")
                    .append("print ").append(signed).append(" (-1)\n")
                    .append("print ").append(signed).append(" (").append(signedMin.subtract(java.math.BigInteger.ONE))
                    .append(")\n")
                    .append("print ").append(signed).append(' ').append(signedMax.add(java.math.BigInteger.ONE))
                    .append('\n')
                    .append("print ").append(unsigned).append(" 0\n")
                    .append("print ").append(unsigned).append(' ').append(unsignedMax).append('\n')
                    .append("print ").append(unsigned).append(" (-1)\n")
                    .append("print ").append(unsigned).append(' ').append(unsignedMax.add(java.math.BigInteger.ONE))
                    .append('\n');
            expected.append("true\ntrue\ntrue\ntrue\nfalse\nfalse\ntrue\ntrue\nfalse\nfalse\n");
        }
        assertEquals(expected.toString(), execute(program.toString()));
    }

    @Test
    void floatingRepresentabilityOverlapsExactIntegerDomains() {
        assertEquals("true\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\n", execute("""
                print Float 16777216
                print Float 16777217
                print Double 9007199254740992
                print Double 9007199254740993
                print Integer 7.0
                print Integer 7.5
                print Int == Integer
                """));
    }

    @Test
    void contextualFloatRoundsFromSourceDigitsIncludingTiesAndSubnormals() {
        // 2^-149 = 5^149 / 10^149, so this decimal is exact.
        String subnormal = new java.math.BigDecimal(java.math.BigInteger.valueOf(5).pow(149), 149)
                .toPlainString();
        String floatMaximum = new java.math.BigDecimal(Float.MAX_VALUE).toPlainString();
        String doubleMaximum = new java.math.BigDecimal(Double.MAX_VALUE).toBigIntegerExact().toString();
        assertEquals("true\ntrue\ntrue\ntrue\ntrue\n", execute("""
                (Float) tied = 1.000000059604644775390625
                (Float) above = 1.000000059604644775390626
                (Float) tiny = %s
                (Float) largestFloat = %s
                (Double) largestDouble = %s
                print tied == 1
                print above > 1
                print tiny > 0
                print Float largestFloat
                print Double largestDouble
                """.formatted(subnormal, floatMaximum, doubleMaximum)));
        LangException overflow = assertThrows(LangException.class, () -> execute("""
                (Float) overflow = 10000000000000000000000000000000000000000.0
                """));
        assertEquals(Diagnostic.Codes.NON_FINITE_RESULT, overflow.diagnostic().code());
    }

    @Test
    void contextualDoubleRoundsWholeNumberSourceAndAliasesKeepStrictPolicy() {
        assertEquals("true\ntrue\n", execute("""
                MyDouble = Double
                (MyDouble) rounded = 9007199254740993
                print rounded == 9007199254740992
                print Double rounded
                """));
        LangException strict = assertThrows(LangException.class, () -> execute("""
                MyDouble = Double
                (MyDouble) ratio = 1 / 3
                """));
        assertEquals(Diagnostic.Codes.IMPLICIT_PRECISION_LOSS, strict.diagnostic().code());
        assertEquals(2, strict.diagnostic().primarySpan().start().line());
        LangException invalid = assertThrows(LangException.class, () -> execute("print 1.5 div 1"));
        assertEquals(Diagnostic.Codes.INCOMPATIBLE_CONTRACTS, invalid.diagnostic().code());
    }

    @Test
    void contextualNamedTemplateBindingCompletesOnlyDirectMissingModifiers() {
        assertEquals("~\ntrue\ntrue\nfalse\n", execute("""
                Person = template [^name = (String) _ ^phone = (String~) _ ^alias = (String?~) _]
                (Person) person = [^name = "Ada"]
                print person.phone
                print Person person
                print (seqGet @Person.elements 1).defaultsMissing
                print (seqGet @Person.elements 0).defaultsMissing
                """));

        LangException absent = assertThrows(LangException.class, () -> execute("""
                OptionalText = String~
                Person = template [^name = (String) _ ^phone = (OptionalText) _]
                (Person) person = [^name = "Ada"]
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, absent.diagnostic().code());
    }

    @Test
    void expectedTemplatesCompleteKnownArgumentsAndNestedLiterals() {
        assertEquals("~\n~\ntrue\n", execute("""
                Person = template [^name = (String) _ ^phone = (String~) _]
                identity (Person) value = value
                print (identity [^name = "Ada"]).phone
                Nested = template [^person = [^name = (String) _ ^phone = (String~) _]]
                (Nested) nested = [^person = [^name = "Bo"]]
                print nested.person.phone
                print (seqGet (seqGet @Nested.elements 0).elements 1).defaultsMissing
                """));
    }

    @Test
    void declaredTemplateResultsCompleteLiteralsAndExportedBlocks() {
        assertEquals("~\n~\n", execute("""
                Person = template [^name = (String) _ ^phone = (String~) _]
                (Person) make (String) name = [^name = name]
                (Person) export (String) name =
                  ^name = name
                print (make "Ada").phone
                print (export "Bo").phone
                """));
    }

    @Test
    void dynamicTemplateKeysAndNullableOptionalTermsCompleteWithoutChangingPredicates() {
        assertEquals("~\ntrue\nfalse\ntrue\ntrue\nfalse\n", execute("""
                key = "phone"
                Phone = template [
                  field key (String?~) _
                ]
                (Phone) person = []
                print person.phone
                print Phone person
                print Phone []
                print Phone [^phone = ?]
                print (seqGet @Phone.elements 0).defaultsMissing
                Rejected = template [^value = (String~ Number) _]
                print (seqGet @Rejected.elements 0).defaultsMissing
                """));
    }

    @Test
    void templateCompletionPreservesExplicitValuesAndExistingCollections() {
        assertEquals("second\nfirst\n?\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n", execute("""
                Person = template [^name = (String) _ ^phone = (String?~) _]
                (Output Any) mark label value =
                  print label
                  value
                (Person) person = [^phone = (mark "second" ?) ^name = (mark "first" "Ada")]
                print person.phone
                print Person person
                print Person [^phone = ? ^name = "Ada"]
                (Person) omitted = [^name = "Ada"]
                print omitted.phone == ~
                print size (fields omitted) == 2
                print omitted == [^name = "Ada" ^phone = ~]
                existing = [^name = "Ada"]
                print Person existing
                """));
    }

    @Test
    void templateCompletionRejectsNondefaultableAndWrongShapesWithLocations() {
        LangException required = assertThrows(LangException.class, () -> execute("""
                Person = template [^name = (String) _ ^phone = (String~) _]
                (Person) person = []
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, required.diagnostic().code());
        assertEquals(2, required.diagnostic().primarySpan().start().line());
        assertEquals(1, required.diagnostic().related().size());
        assertEquals(1, required.diagnostic().related().getFirst().span().start().line());

        LangException wrong = assertThrows(LangException.class, () -> execute("""
                Person = template [^name = (String) _ ^phone = (String~) _]
                (Person) person = [^name = 1]
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, wrong.diagnostic().code());

        LangException extra = assertThrows(LangException.class, () -> execute("""
                Person = template [^name = (String) _ ^phone = (String~) _]
                (Person) person = [^name = "Ada" ^extra = 1]
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, extra.diagnostic().code());

        LangException fixed = assertThrows(LangException.class, () -> execute("""
                Fixed = template [^name = (String) _ ^phone = ~]
                (Fixed) person = [^name = "Ada"]
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, fixed.diagnostic().code());

        LangException hole = assertThrows(LangException.class, () -> execute("""
                Hole = template [^name = (String) _ ^phone = _]
                (Hole) person = [^name = "Ada"]
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, hole.diagnostic().code());
    }

    @Test
    void competingTemplateOverloadsCannotSelectByCompletingAnArgument() {
        LangException failure = assertThrows(LangException.class, () -> execute("""
                A = template [^name = (String) _ ^a = (String~) _]
                B = template [^name = (String) _ ^b = (String~) _]
                choose (A) value = "a"
                choose (B) value = "b"
                print choose [^name = "Ada"]
                """));
        assertEquals(Diagnostic.Codes.NO_APPLICABLE_OVERLOAD, failure.diagnostic().code());
    }

    @Test
    void templateCompletionMetadataIsIndependentOfStorageReuse() {
        String program = """
                Person = template [^name = (String) _ ^phone = (String~) _]
                (Person) ada = [^name = "Ada"]
                print ada == [^name = "Ada" ^phone = ~]
                print (seqGet @Person.elements 1).defaultsMissing
                print Person ada
                """;
        ModeExecution enabled = execute(program, OwnershipTracker.Mode.ENABLED);
        ModeExecution disabled = execute(program, OwnershipTracker.Mode.DISABLED);
        assertEquals("true\ntrue\ntrue\n", enabled.output());
        assertEquals(enabled.output(), disabled.output());
    }

    @Test
    void sharedNumberedHoleRequirementsCanDisableAVisibleDefault() {
        assertEquals("false\n", execute("""
                Shared = template [^optional = (String~) _1 ^required = (String) _1]
                print (seqGet @Shared.elements 0).defaultsMissing
                """));
        LangException absent = assertThrows(LangException.class, () -> execute("""
                Shared = template [^optional = (String~) _1 ^required = (String) _1]
                (Shared) value = [^required = "Ada"]
                """));
        assertEquals(Diagnostic.Codes.CONTRACT_VIOLATION, absent.diagnostic().code());
        assertEquals(2, absent.diagnostic().primarySpan().start().line());
    }

    @Test
    void duplicateLiteralFieldsRetainFirstEntryBeforeTemplateValidation() {
        assertEquals("Ada\n~\ntrue\n", execute("""
                Person = template [^name = (String) _ ^phone = (String~) _]
                (Person) person = [^name = "Ada" ^name = 17]
                print person.name
                print person.phone
                print Person person
                """));
    }
}
