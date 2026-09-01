package caretlang;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable, environment-relative namespace of visible effect identities. */
final class EffectCatalog {
    static final EffectDescriptor OUTPUT = new EffectDescriptor("Output");
    static final EffectDescriptor STATE_READ = new EffectDescriptor("StateRead");
    static final EffectDescriptor STATE_WRITE = new EffectDescriptor("StateWrite");
    static final EffectDescriptor TEST_REPORT = new EffectDescriptor("TestReport");

    private final Map<String, EffectDescriptor> entries;

    private EffectCatalog(Map<String, EffectDescriptor> entries) { this.entries = Map.copyOf(entries); }

    static EffectCatalog standard(boolean testing) {
        LinkedHashMap<String, EffectDescriptor> entries = new LinkedHashMap<>();
        for (EffectDescriptor effect : List.of(OUTPUT, STATE_READ, STATE_WRITE)) {
            entries.put(effect.canonicalName(), effect);
        }
        if (testing) entries.put(TEST_REPORT.canonicalName(), TEST_REPORT);
        return new EffectCatalog(entries);
    }

    EffectCatalog with(String name, EffectDescriptor effect) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Effect name must not be blank");
        if (effect == null) throw new IllegalArgumentException("Effect descriptor must not be null");
        EffectDescriptor portable = portable(name);
        if (portable != null && portable != effect) {
            throw new IllegalArgumentException("Effect catalog cannot replace portable identity: " + name);
        }
        LinkedHashMap<String, EffectDescriptor> updated = new LinkedHashMap<>(entries);
        EffectDescriptor existing = updated.putIfAbsent(name, effect);
        if (existing != null && existing != effect) {
            throw new IllegalArgumentException("Effect catalog name already denotes another identity: " + name);
        }
        return new EffectCatalog(updated);
    }

    EffectCatalog alias(String alias, String target) {
        EffectDescriptor effect = resolve(target).orElseThrow(() ->
                new IllegalArgumentException("Unknown effect alias target: " + target));
        return with(alias, effect);
    }

    Optional<EffectDescriptor> resolve(String name) { return Optional.ofNullable(entries.get(name)); }
    boolean visible(EffectDescriptor effect) { return entries.get(effect.canonicalName()) == effect; }

    private static EffectDescriptor portable(String name) {
        return switch (name) {
            case "Output" -> OUTPUT;
            case "StateRead" -> STATE_READ;
            case "StateWrite" -> STATE_WRITE;
            default -> null;
        };
    }
}
