package caretlang;

import org.junit.jupiter.api.Test;

import static caretlang.InterpreterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class TemplateSugarInterpreterTest {
    @Test
    void matchesOrdinaryTemplatesForEmptyPositionalNamedAndRepeatedHoles() {
        assertEquals("true\ntrue\ntrue\nfalse\ntrue\nfalse\ntrue\nfalse\n", execute("""
                Empty = <[]>
                print Empty []
                Point = <[(Number) _ (Number) _]>
                Ordinary = template [(Number) _ (Number) _]
                print Point [1 2]
                print Point [1 "two"] == Ordinary [1 "two"]
                print Point [1 2 3]
                Repeated = <[_2 _1 _1]>
                print Repeated [2 1 1]
                print Repeated [2 1 3]
                Named = <[^name = (String) _ ^active = true]>
                print Named [^name = "Ada" ^active = true]
                print Named [^name = "Ada" ^active = false]
                """));
    }

    @Test
    void honorsLocalAndParameterShadowingWithoutRequiringContractResults() {
        assertEquals("2\n42\nContract\n", execute("""
                local =
                  template specimen = seqSize specimen
                  <[1 2]>
                print local
                use ([Collection] -> Number) template = <[1 2]>
                print use (specimen -> 42)
                print type <[]>
                """));
    }

    @Test
    void preservesNestedCollectionStructureAndReusableContractedTemplates() {
        assertEquals("true\nfalse\ntrue\nfalse\n", execute("""
                Person = <[
                  ^name = (String) _
                  ^address = [
                    ^street = (String) _
                    ^city = (String) _
                  ]
                ]>
                print Person [^name = "Ada" ^address = [^street = "Main" ^city = "Oslo"]]
                print Person [^name = "Ada" ^address = [^street = "Main" ^city = 1]]
                Address = <[^street = (String) _ ^city = (String) _]>
                Reusable = <[^name = (String) _ ^address = (Address) _]>
                print Reusable [^name = "Ada" ^address = [^street = "Main" ^city = "Oslo"]]
                print Reusable [^name = "Ada" ^address = []]
                """));
    }

    @Test
    void capturesFixedValuesOnceInOrderAndKeepsHolesInsideTheSpecimen() {
        assertEquals("first\nsecond\ntrue\ntrue\ntrue\nfalse\n", execute("""
                Captured = <[
                  print "first"
                  print "second"
                  _
                ]>
                print Captured ["first" "second" 1]
                print Captured ["first" "second" 2]
                cell = { (Number) 1 }
                Fixed = <[cell{}]>
                put cell 2
                print Fixed [1]
                print Fixed [2]
                """));
    }

    @Test
    void retainsExpectedTemplateDefaultsAndReflection() {
        assertEquals("~\ntrue\nfalse\ntrue\nnamed\n", execute("""
                Contact = <[^name = (String) _ ^phone = (String~) _]>
                (Contact) person = [^name = "Ada"]
                print person.phone
                print Contact person
                print Contact [^name = "Ada"]
                print (seqGet @Contact.elements 1).defaultsMissing
                print @Contact.shape
                """));
    }

    @Test
    void groupsApplicationCompositionAndAdjacentOperatorsWithoutChangingComparisons() {
        assertEquals("true\n[ 1 ]\nfalse\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\n-2\n", execute("""
                print <[_]> [1]
                print (<[_]>) [1]
                print <[]>==<[]>
                print <[]>!=<[]>
                pipeline = <[_]>>> (result -> not result)
                print pipeline []
                print true & <[_]> [1] ! false
                values = [1]
                print values[0]>0
                print values[0]>=1
                print 0<[1][0]
                print size [<[]> <[]>] == 2
                local =
                  template specimen = seqSize specimen
                  - <[1 2]>
                print local
                """));
    }

    @Test
    void preservesMultilineLayoutCommentsStringsAndNestedExplicitSugar() {
        assertEquals("true\ntrue\nContract\ntrue\n", execute("""
                Text = <[
                  "<["
                  "]>"
                ]> // ]> and <[ inside this comment are not delimiters
                print Text ["<[" "]>"]
                Opening = <["["]>
                print Opening ["["]
                Nested = [<[]>]
                print type (seqGet Nested 0)
                print <[]> []
                """));
    }

    @Test
    void propagatesShadowedCallableEffectsAndRejectsDisallowedEffectsBeforeExecution() {
        assertEquals("called\n2\n", execute("""
                (Output Number) local =
                  (Output Number) template specimen =
                    print "called"
                    seqSize specimen
                  <[1 2]>
                print local
                """));
        LangException effect = assertThrows(LangException.class, () -> execute("""
                (pure) local =
                  (Output Number) template specimen =
                    print "called"
                    2
                  <[1 2]>
                local
                """));
        assertEquals(Diagnostic.Codes.EFFECT_ALLOWANCE_EXCEEDED, effect.diagnostic().code());
        assertEquals(Diagnostic.Phase.SEMANTIC, effect.diagnostic().phase());
    }

    @Test
    void retainsTemplateAndHoleFailuresWithLocatedDiagnostics() {
        LangException mixed = assertThrows(LangException.class, () -> execute("Bad = <[_ _1]>"));
        assertEquals(Diagnostic.Codes.MIXED_HOLE_STYLES, mixed.diagnostic().code());
        LangException computed = assertThrows(LangException.class,
                () -> execute("Bad = <[\n  numberText _\n]>"));
        assertEquals(Diagnostic.Codes.TEMPLATE_INVALID_CONSTRUCTOR, computed.diagnostic().code());
        assertEquals(1, computed.span().start().line());
        assertEquals(8, computed.span().start().column());
        LangException callable = assertThrows(LangException.class, () -> execute("Bad = <[@print:]>"));
        assertEquals(Diagnostic.Codes.TEMPLATE_NONCOMPARABLE_FIXED_VALUE, callable.diagnostic().code());
        LangException nestedContract = assertThrows(LangException.class, () -> execute("Bad = <[<[]>]>"));
        assertEquals(Diagnostic.Codes.TEMPLATE_NONCOMPARABLE_FIXED_VALUE,
                nestedContract.diagnostic().code());
    }
}
