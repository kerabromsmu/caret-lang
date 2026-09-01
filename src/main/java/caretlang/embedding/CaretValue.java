package caretlang.embedding;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public sealed interface CaretValue permits CaretValue.NumberValue, CaretValue.TextValue,
        CaretValue.BooleanValue, CaretValue.NullValue, CaretValue.MissingValue, CaretValue.FieldValue,
        CaretValue.SequenceValue, CaretValue.CollectionValue, CaretCallable {

    record NumberValue(double value) implements CaretValue {
        public NumberValue {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Caret numbers must be finite");
        }
    }
    record TextValue(String value) implements CaretValue {
        public TextValue { Objects.requireNonNull(value); }
    }
    record BooleanValue(boolean value) implements CaretValue {}
    enum NullValue implements CaretValue { INSTANCE }
    enum MissingValue implements CaretValue { INSTANCE }
    record FieldValue(String name, CaretValue value) implements CaretValue {
        public FieldValue { Objects.requireNonNull(name); Objects.requireNonNull(value); }
    }
    record SequenceValue(List<CaretValue> values) implements CaretValue {
        public SequenceValue { values = List.copyOf(values); }
    }
    record CollectionValue(Map<String, CaretValue> fields) implements CaretValue {
        public CollectionValue {
            Objects.requireNonNull(fields);
            java.util.LinkedHashMap<String, CaretValue> checked = new java.util.LinkedHashMap<>();
            fields.forEach((name, value) -> checked.put(Objects.requireNonNull(name), Objects.requireNonNull(value)));
            fields = java.util.Collections.unmodifiableMap(checked);
        }
        public Optional<CaretValue> find(String name) { return Optional.ofNullable(fields.get(name)); }
    }

    static NumberValue number(double value) { return new NumberValue(value); }
    static TextValue text(String value) { return new TextValue(value); }
    static BooleanValue bool(boolean value) { return new BooleanValue(value); }
    static NullValue nullValue() { return NullValue.INSTANCE; }
    static MissingValue missing() { return MissingValue.INSTANCE; }
    static SequenceValue sequence(List<CaretValue> values) { return new SequenceValue(values); }
    static CollectionValue collection(Map<String, CaretValue> fields) { return new CollectionValue(fields); }
}
