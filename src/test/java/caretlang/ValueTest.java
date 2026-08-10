package caretlang;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void persistentCollectionsHandleLongUpdateHistoriesWithoutChangingValues() {
        Value.Seq sequence = new Value.Seq(List.of());
        Value.Dict dictionary = new Value.Dict(new LinkedHashMap<>());
        for (int i = 0; i < 10_000; i++) {
            sequence = sequence.appended(new Value.Num(i));
            dictionary = dictionary.put("key" + i, new Value.Num(i));
        }

        assertEquals(10_000, sequence.size());
        assertEquals(new Value.Num(0), sequence.find(0).orElseThrow());
        assertEquals(new Value.Num(9_999), sequence.find(9_999).orElseThrow());
        assertEquals(10_000, dictionary.size());
        assertEquals(new Value.Num(0), dictionary.find("key0").orElseThrow());
        assertEquals(new Value.Num(9_999), dictionary.find("key9999").orElseThrow());
    }
}
