package caretlang;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LanguageConformanceTest {
    @Test
    void characterizesTheCompleteImplementedPrecedenceTable() {
        assertEquals("7\n4\ntrue\ntrue\nyes\n-5\n7\nyes\n", execute("""
                first a b = a
                identity value = value

                print 1 + 2 * 3
                print first 1 2 + 3
                print 1 < 2 == true and false or true
                print false or true and true
                print false or true & "yes" ! "no"
                print - identity 5
                print $ 1 + 2 * 3
                print $ false & "no" ! "yes"
                """));
    }

    @Test
    void characterizesSafePrimitiveFailuresAndMissingDictionaryValues() {
        assertEquals("~\n~\n~\ntrue\n~\nfalse\n", execute("""
                print textAt "a" 2
                print textSlice "abc" 2 1
                print textNumber "not-a-number"
                values = dictPut dictEmpty "present" ~
                print dictHas values "present"
                print dictGet values "present"
                print dictHas values "absent"
                """));
    }

    private String execute(String source) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new Interpreter(new PrintStream(bytes, true, StandardCharsets.UTF_8))
                .execute(new Parser(source).parseProgram());
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
