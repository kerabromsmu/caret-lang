package caretlang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class EffectCatalogTest {
    @Test
    void aliasesRetainDescriptorIdentityAndPortableNamesCannotBeReplaced() {
        EffectCatalog catalog = EffectCatalog.standard(false).alias("console", "Output");

        assertSame(EffectCatalog.OUTPUT, catalog.resolve("console").orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> catalog.with("Output", new EffectDescriptor("host-output")));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.alias("unknown", "not-visible"));
    }
}
