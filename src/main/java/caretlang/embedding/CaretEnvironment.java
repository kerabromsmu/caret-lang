package caretlang.embedding;

import java.util.*;
import java.util.function.Supplier;

public final class CaretEnvironment {
    @FunctionalInterface
    public interface HostCallback { CaretValue invoke(List<CaretValue> arguments) throws Exception; }
    public record Callback(String name, int arity, Set<CaretEffect> effects, HostCallback implementation) {
        public Callback {
            requireName(name);
            if (arity < 0) throw new IllegalArgumentException("arity must be non-negative");
            effects = Set.copyOf(effects);
            Objects.requireNonNull(implementation);
        }
    }

    private final Map<String, Supplier<CaretValue>> values;
    private final Map<String, Callback> callbacks;
    private final Set<CaretEffect> effects;
    private final boolean print;
    private final boolean callbackRegistration;

    private CaretEnvironment(Builder builder) {
        values = Map.copyOf(builder.values);
        callbacks = Map.copyOf(builder.callbacks);
        effects = Set.copyOf(builder.effects);
        print = builder.print;
        callbackRegistration = builder.callbackRegistration;
    }
    public static Builder builder() { return new Builder(); }
    public Map<String, Supplier<CaretValue>> values() { return values; }
    public Map<String, Callback> callbacks() { return callbacks; }
    public Set<CaretEffect> effects() { return effects; }
    public boolean printEnabled() { return print; }
    public boolean callbackRegistrationEnabled() { return callbackRegistration; }

    public static final class Builder {
        private final Map<String, Supplier<CaretValue>> values = new LinkedHashMap<>();
        private final Map<String, Callback> callbacks = new LinkedHashMap<>();
        private final Set<CaretEffect> effects = new LinkedHashSet<>();
        private boolean print;
        private boolean callbackRegistration;

        public Builder value(String name, Supplier<CaretValue> supplier) {
            requireName(name); putUnique(name); values.put(name, Objects.requireNonNull(supplier)); return this;
        }
        public Builder callback(String name, int arity, Set<CaretEffect> effects, HostCallback implementation) {
            putUnique(name); callbacks.put(name, new Callback(name, arity, effects, implementation)); return this;
        }
        public Builder allowEffect(CaretEffect effect) { effects.add(Objects.requireNonNull(effect)); return this; }
        public Builder enablePrint() { print = true; effects.add(CaretEffect.OUTPUT); return this; }
        public Builder enableCallbackRegistration() { callbackRegistration = true; return this; }
        public CaretEnvironment build() {
            for (Callback callback : callbacks.values()) {
                if (!effects.containsAll(callback.effects())) {
                    throw new IllegalStateException("Callback effects are not visible in the environment: " + callback.name());
                }
            }
            return new CaretEnvironment(this);
        }
        private void putUnique(String name) {
            requireName(name);
            if (values.containsKey(name) || callbacks.containsKey(name)
                    || name.equals("registerCallbacks")) throw new IllegalArgumentException("Duplicate or reserved binding: " + name);
        }
    }

    private static void requireName(String name) {
        Objects.requireNonNull(name, "binding name");
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid Caret name: " + name);
    }
}
