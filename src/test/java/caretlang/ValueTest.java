package caretlang;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;

final class ValueTest {
    @Test
    void scopesAndDictionariesRejectNullRuntimeValues() {
        LinkedHashMap<String, Value> invalid = new LinkedHashMap<>();
        invalid.put("bad", null);
        assertThrows(NullPointerException.class, () -> new Value.Scope(invalid));
        assertThrows(NullPointerException.class, () -> new Value.Dict(invalid));
        LinkedHashMap<String, Value> nullKey = new LinkedHashMap<>();
        nullKey.put(null, new Value.Num(1));
        assertThrows(NullPointerException.class, () -> new Value.Scope(nullKey));
        assertThrows(NullPointerException.class, () -> new Value.Dict(nullKey));
    }
}
