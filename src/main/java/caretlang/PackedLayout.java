package caretlang;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One immutable physical descriptor shared by all elements of a packed Collection. */
final class PackedLayout {
    record Metadata(Set<ContractDescriptor> contracts, List<Metadata> children) {
        Metadata {
            contracts = Set.copyOf(contracts);
            children = List.copyOf(children);
        }
        boolean empty() {
            if (!contracts.isEmpty()) return false;
            for (Metadata child : children) if (!child.empty()) return false;
            return true;
        }
    }

    private sealed interface Node permits Scalar, Constant, Structure {
        int width();
        void write(Value value, byte[] bytes, int offset);
        Value read(byte[] bytes, int offset);
    }

    private record Scalar(BuiltinContract format, int width) implements Node {
        @Override public void write(Value value, byte[] bytes, int offset) {
            if (format == BuiltinContract.BOOLEAN) {
                bytes[offset] = (byte) (((Value.Bool) ValueSemantics.underlying(value)).value() ? 1 : 0);
                return;
            }
            Value.Num number = (Value.Num) ValueSemantics.underlying(value);
            long bits;
            if (format == BuiltinContract.FLOAT) bits = Float.floatToRawIntBits((float) number.value());
            else if (format == BuiltinContract.DOUBLE) bits = Double.doubleToRawLongBits(number.value());
            else bits = NumericValues.integral(number).longValue();
            for (int index = 0; index < width; index++) bytes[offset + index] = (byte) (bits >>> (index * 8));
        }

        @Override public Value read(byte[] bytes, int offset) {
            if (format == BuiltinContract.BOOLEAN) return new Value.Bool(bytes[offset] == 1);
            long bits = 0;
            for (int index = 0; index < width; index++) bits |= (bytes[offset + index] & 0xffL) << (index * 8);
            if (format == BuiltinContract.FLOAT) return new Value.Num((double) Float.intBitsToFloat((int) bits));
            if (format == BuiltinContract.DOUBLE) return new Value.Num(Double.longBitsToDouble(bits));
            byte[] bigEndian = new byte[width];
            for (int index = 0; index < width; index++) bigEndian[width - index - 1] = bytes[offset + index];
            boolean unsigned = format == BuiltinContract.UINT8 || format == BuiltinContract.UINT16
                    || format == BuiltinContract.UINT32 || format == BuiltinContract.UINT64;
            return new Value.Num(unsigned ? new BigInteger(1, bigEndian) : new BigInteger(bigEndian));
        }
    }

    private record Constant(Value value) implements Node {
        @Override public int width() { return 0; }
        @Override public void write(Value ignored, byte[] bytes, int offset) {}
        @Override public Value read(byte[] bytes, int offset) { return value; }
    }

    private record Part(String name, Node node) {}

    private record Structure(boolean named, List<Part> parts, int width) implements Node {
        @Override public void write(Value value, byte[] bytes, int offset) {
            value = ValueSemantics.underlying(value);
            int position = offset;
            if (named) {
                Map<String, Value> fields = ((Value.Dictionary) value).entries();
                for (Part part : parts) {
                    part.node().write(fields.get(part.name()), bytes, position);
                    position += part.node().width();
                }
            } else {
                List<Value> values = ((Value.Seq) value).values();
                for (int index = 0; index < parts.size(); index++) {
                    Node node = parts.get(index).node();
                    node.write(values.get(index), bytes, position);
                    position += node.width();
                }
            }
        }

        @Override public Value read(byte[] bytes, int offset) {
            int position = offset;
            if (named) {
                LinkedHashMap<String, Value> fields = new LinkedHashMap<>();
                for (Part part : parts) {
                    fields.put(part.name(), part.node().read(bytes, position));
                    position += part.node().width();
                }
                return new Value.Dictionary(fields);
            }
            ArrayList<Value> values = new ArrayList<>(parts.size());
            for (Part part : parts) {
                values.add(part.node().read(bytes, position));
                position += part.node().width();
            }
            return new Value.Seq(values);
        }
    }

    private final Node element;
    private final int stride;

    private PackedLayout(Node element) {
        this.element = element;
        this.stride = element.width();
    }

    static PackedLayout of(ContractDescriptor contract, SourceSpan span) {
        return new PackedLayout(node(contract, span));
    }

    int stride() { return stride; }
    boolean canEncode(Value value) { return canEncode(element, value); }
    void write(Value value, byte[] bytes, int offset) { element.write(value, bytes, offset); }
    Value read(byte[] bytes, int offset) { return element.read(bytes, offset); }
    Metadata captureMetadata(Value value) { return captureMetadata(element, value); }
    Value restoreMetadata(Value value, Metadata metadata) {
        return metadata.empty() ? value : restoreMetadata(element, value, metadata);
    }

    private static Metadata captureMetadata(Node node, Value value) {
        Set<ContractDescriptor> contracts = value instanceof Value.Attributed attributed
                ? attributed.contracts() : Set.of();
        value = ValueSemantics.underlying(value);
        if (!(node instanceof Structure structure)) return new Metadata(contracts, List.of());
        ArrayList<Metadata> children = new ArrayList<>(structure.parts().size());
        if (structure.named()) {
            Map<String, Value> fields = ((Value.Dictionary) value).entries();
            for (Part part : structure.parts()) children.add(captureMetadata(part.node(), fields.get(part.name())));
        } else {
            List<Value> elements = ((Value.Seq) value).values();
            for (int index = 0; index < structure.parts().size(); index++) {
                children.add(captureMetadata(structure.parts().get(index).node(), elements.get(index)));
            }
        }
        return new Metadata(contracts, children);
    }

    private static Value restoreMetadata(Node node, Value value, Metadata metadata) {
        if (node instanceof Structure structure && !metadata.children().isEmpty()) {
            if (structure.named()) {
                Map<String, Value> original = ((Value.Dictionary) value).entries();
                LinkedHashMap<String, Value> restored = new LinkedHashMap<>();
                for (int index = 0; index < structure.parts().size(); index++) {
                    Part part = structure.parts().get(index);
                    restored.put(part.name(), restoreMetadata(part.node(), original.get(part.name()),
                            metadata.children().get(index)));
                }
                value = new Value.Dictionary(restored);
            } else {
                List<Value> original = ((Value.Seq) value).values();
                ArrayList<Value> restored = new ArrayList<>(original.size());
                for (int index = 0; index < structure.parts().size(); index++) {
                    restored.add(restoreMetadata(structure.parts().get(index).node(), original.get(index),
                            metadata.children().get(index)));
                }
                value = new Value.Seq(restored);
            }
        }
        return metadata.contracts().isEmpty() ? value : new Value.Attributed(value, metadata.contracts());
    }

    private static boolean canEncode(Node node, Value value) {
        value = ValueSemantics.underlying(value);
        if (node instanceof Constant) return true;
        if (node instanceof Scalar scalar) return scalar.format().accepts(value);
        Structure structure = (Structure) node;
        if (structure.named()) {
            if (!(value instanceof Value.Dictionary dictionary)) return false;
            Map<String, Value> fields = dictionary.entries();
            if (fields.size() != structure.parts().size()) return false;
            for (Part part : structure.parts()) {
                Value member = fields.get(part.name());
                if (member == null || !canEncode(part.node(), member)) return false;
            }
            return true;
        }
        if (!(value instanceof Value.Seq sequence) || sequence.size() != structure.parts().size()) return false;
        for (int index = 0; index < structure.parts().size(); index++) {
            if (!canEncode(structure.parts().get(index).node(), sequence.values().get(index))) return false;
        }
        return true;
    }

    private static Node node(ContractDescriptor contract, SourceSpan span) {
        if (contract instanceof BuiltinContract builtin) return scalar(builtin, span);
        if (contract instanceof TemplateContract template) return node(template.descriptor().root(), span);
        throw invalid("No fixed packed layout for " + contract.publicName(), span);
    }

    private static Node scalar(BuiltinContract format, SourceSpan span) {
        return switch (format) {
            case INT8, UINT8, BOOLEAN -> new Scalar(format, 1);
            case INT16, UINT16 -> new Scalar(format, 2);
            case INT32, UINT32, FLOAT -> new Scalar(format, 4);
            case INT64, UINT64, DOUBLE -> new Scalar(format, 8);
            default -> throw invalid("No fixed packed layout for " + format.publicName(), span);
        };
    }

    private static Node node(CollectionConstructorDescriptor.Node descriptor, SourceSpan span) {
        if (descriptor instanceof CollectionConstructorDescriptor.FixedNode fixed) {
            Value value = ValueSemantics.underlying(fixed.value());
            if (!(value instanceof Value.Num || value instanceof Value.Bool)) {
                throw invalid("No fixed packed layout for template constant", span);
            }
            return new Constant(value);
        }
        if (descriptor instanceof CollectionConstructorDescriptor.HoleNode hole) {
            BuiltinContract selected = null;
            for (Object requirement : hole.requirements()) {
                if (!(requirement instanceof ContractDescriptor contract)) continue;
                if (contract instanceof ModifiedContract modified
                        && (modified.nullable() || modified.optional())) {
                    throw invalid("Packed fields cannot be nullable or optional", span);
                }
                if (!(contract instanceof BuiltinContract builtin)) continue;
                if (!(builtin == BuiltinContract.INT8 || builtin == BuiltinContract.UINT8
                        || builtin == BuiltinContract.INT16 || builtin == BuiltinContract.UINT16
                        || builtin == BuiltinContract.INT32 || builtin == BuiltinContract.UINT32
                        || builtin == BuiltinContract.INT64 || builtin == BuiltinContract.UINT64
                        || builtin == BuiltinContract.FLOAT || builtin == BuiltinContract.DOUBLE
                        || builtin == BuiltinContract.BOOLEAN)) continue;
                if (selected != null && selected != builtin) {
                    throw invalid("Conflicting concrete packed formats", span);
                }
                selected = builtin;
            }
            if (selected == null) throw invalid("Packed field requires one concrete format", span);
            return scalar(selected, span);
        }
        CollectionConstructorDescriptor.CollectionNode collection =
                (CollectionConstructorDescriptor.CollectionNode) descriptor;
        ArrayList<Part> parts = new ArrayList<>(collection.elements().size());
        int width = 0;
        for (CollectionConstructorDescriptor.Element field : collection.elements()) {
            if (field.defaultsMissing()) throw invalid("Packed fields cannot default to missing", span);
            Node nested = node(field.value(), span);
            parts.add(new Part(field.name(), nested));
            try {
                width = Math.addExact(width, nested.width());
            } catch (ArithmeticException overflow) {
                throw invalid("Packed payload too large", span);
            }
        }
        return new Structure(collection.named(), List.copyOf(parts), width);
    }

    private static LangException invalid(String message, SourceSpan span) {
        return new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.INVALID_PACKED_LAYOUT,
                message, span);
    }
}
