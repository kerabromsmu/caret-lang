package caretlang;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

final class OwnershipTrackerTest {
    @Test
    void randomizedUniqueUpdatesMatchOptimizationDisabledValues() {
        Random random = new Random(0xCA7E7L);
        OwnershipTracker enabled = new OwnershipTracker(OwnershipTracker.Mode.ENABLED);
        OwnershipTracker disabled = new OwnershipTracker(OwnershipTracker.Mode.DISABLED);
        Value.Seq optimizedSequence = enabled.fresh(new Value.Seq(List.of()));
        Value.Seq referenceSequence = disabled.fresh(new Value.Seq(List.of()));
        Value.Dictionary optimizedDictionary = enabled.fresh(new Value.Dictionary(Map.of()));
        Value.Dictionary referenceDictionary = disabled.fresh(new Value.Dictionary(Map.of()));

        for (int operation = 0; operation < 1_000; operation++) {
            Value value = new Value.Num(random.nextInt(100));
            if (random.nextBoolean()) {
                optimizedSequence = enabled.append(optimizedSequence, value);
                referenceSequence = disabled.append(referenceSequence, value);
                assertEquals(referenceSequence, optimizedSequence);
            } else {
                String key = "k" + random.nextInt(40);
                optimizedDictionary = enabled.put(optimizedDictionary, key, value);
                referenceDictionary = disabled.put(referenceDictionary, key, value);
                assertEquals(referenceDictionary, optimizedDictionary);
            }
        }

        assertTrue(enabled.reuseCount() > 0);
        assertEquals(0, disabled.reuseCount());
    }

    @Test
    void sharingRecursivelyProtectsNestedAndOlderValues() {
        OwnershipTracker ownership = new OwnershipTracker(OwnershipTracker.Mode.ENABLED);
        Value.Seq child = ownership.fresh(new Value.Seq(List.of(new Value.Num(1))));
        Value.Dictionary parent = ownership.fresh(new Value.Dictionary(Map.of("child", child)));
        ownership.share(parent);

        Value.Seq updated = ownership.append(child, new Value.Num(2));

        assertNotSame(child, updated);
        assertEquals(new Value.Seq(List.of(new Value.Num(1))), child);
        assertEquals(child, parent.find("child").orElseThrow());
        assertEquals(0, ownership.reuseCount());
    }
}
