package caretlang;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static caretlang.InterpreterTestSupport.*;

final class EagerScopedInterpreterTest {
    @Test
    void eagerMaterializesCaretValuesAndPreservesContainersAndCallables() {
        assertEquals("""
                [ 1 2 ]
                [ 1 2 ]
                []
                true
                """, execute("""
                identity value = value
                source = map identity [1 2]
                result = eager source
                print result
                print source
                print getElement (eager [42 (@source)]) 1
                cell = { (Number) 3 }
                print (getElement (eager [cell]) 0) == cell
                """));
        assertEquals("true\n", execute("""
                (Sequence Number) result = eager [1 2]
                print (Sequence Number result)
                """));
        assertEquals("1\n2\n[ 1 2 ]\n", execute("""
                (Output Number) emit value =
                  print value
                  value
                source = map emit [1 2]
                print eager source
                """));
        Value.Callable callable = new Value.FunctionValue("identity", List.of("value"),
                (args, ignored) -> args.getFirst().value());
        assertSame(callable, EagerRuntime.materialize(callable, null, ReflectionContext.defining()));
    }

    @Test
    void eagerEnumeratesBeforeDepthFirstTraversalAndDropsUneumeratedAccess() {
        class TracedProvider implements Value.Reflective, CollectionRuntime.Provider {
            final List<String> trace;
            final String name;
            final List<Value> values;
            TracedProvider(List<String> trace, String name, List<Value> values) {
                this.trace = trace; this.name = name; this.values = values;
            }
            @Override public Optional<Value> find(String key) { return Optional.empty(); }
            @Override public Map<String, Value> fields() { return Map.of(); }
            @Override public Value getElement(Value key) { return new Value.Num(999); }
            @Override public Value keys() { trace.add(name + ":keys"); return new Value.Seq(List.of(new Value.Num(0))); }
            @Override public Value valueEntries() { trace.add(name + ":values"); return new Value.Seq(values); }
            @Override public Value fieldEntries() { return valueEntries(); }
            @Override public Value size() { return new Value.Num(values.size()); }
            @Override public CollectionRuntime.Facts facts() {
                return new CollectionRuntime.Facts(CollectionRuntime.Guarantee.FALSE,
                        CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.UNKNOWN,
                        CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.FALSE,
                        CollectionRuntime.Guarantee.TRUE);
            }
        }
        List<String> trace = new java.util.ArrayList<>();
        TracedProvider nested = new TracedProvider(trace, "nested", List.of(new Value.Num(7)));
        TracedProvider root = new TracedProvider(trace, "root", List.of(nested));
        Value result = EagerRuntime.materialize(root, new SourceSpan(new SourcePosition(0, 1, 1),
                new SourcePosition(5, 1, 6)), ReflectionContext.defining());
        assertEquals(List.of("root:keys", "root:values", "nested:keys", "nested:values"), trace);
        assertSame(Value.Missing.INSTANCE, ((CollectionRuntime.Provider) result).getElement(new Value.Num(2)));
        assertEquals(new Value.Num(999), root.getElement(new Value.Num(2)));
    }

    @Test
    void eagerVisitsEachLazyEntryBeforeProducingTheNext() {
        List<String> trace = new java.util.ArrayList<>();
        Value.LazySeq outer = new Value.LazySeq(2, index -> {
            trace.add("outer" + index);
            return new Value.LazySeq(1, inner -> {
                trace.add("inner" + index);
                return new Value.Num(index);
            });
        });
        EagerRuntime.materialize(outer, null, ReflectionContext.defining());
        assertEquals(List.of("outer0", "inner0", "outer1", "inner1"), trace);

        trace.clear();
        Value.LazySeq failing = new Value.LazySeq(2, index -> {
            trace.add("outer" + index);
            return new Value.LazySeq(1, inner -> {
                trace.add("inner" + index);
                throw new IllegalStateException("nested failure");
            });
        });
        assertThrows(IllegalStateException.class,
                () -> EagerRuntime.materialize(failing, null, ReflectionContext.defining()));
        assertEquals(List.of("outer0", "inner0"), trace);
    }

    @Test
    void eagerCompletesUnknownShapeKeyEnumerationBeforeNestedValues() {
        assertEquals("outer1\nouter2\ninner1\ninner2\n[ 1 ]\n[ 2 ]\n", execute("""
                (Output Number) inner value =
                  print "inner" + value
                  value
                (Output Field) makeNestedField value =
                  print "outer" + value
                  field ("k" + value) (map inner [value])
                result = eager (map makeNestedField [1 2])
                print result["k1"]
                print result["k2"]
                """));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(output));
        LangException failure = assertThrows(LangException.class, () -> interpreter.execute(new Parser("""
                (Output Number) inner value =
                  print "inner" + value
                  1 / (value - 1)
                (Output Field) makeNestedField value =
                  print "outer" + value
                  field ("k" + value) (map inner [value])
                eager (map makeNestedField [1 2])
                """).parseProgram()));
        assertEquals("outer1\nouter2\ninner1\n", output.toString());
        assertEquals(Diagnostic.Phase.RUNTIME, failure.diagnostic().phase());
        assertEquals(Diagnostic.Codes.DIVISION_BY_ZERO, failure.diagnostic().code());
        assertEquals("Line 3, column 7: Division by zero", failure.getMessage());
    }

    @Test
    void eagerRefreshesInferredShapeAfterKeyEnumeration() {
        assertEquals("true\ntrue\n1\n2\n2\n", execute("""
                makeField value = field ("k" + value) value
                settled = eager (map makeField [1 2])
                print isKeyed settled
                print (@settled).keyed
                print settled["k1"]
                print size (fields settled)
                print (map (entry -> entry[1]) settled)[1]
                """));
        assertEquals("false\nfalse\n2\n2\n", execute("""
                settled = eager (map (value -> value) [1 2])
                print isKeyed settled
                print (@settled).keyed
                print size (fields settled)
                print (map (value -> value + 1) settled)[0]
                """));
        assertEquals("true\nfalse\nfalse\n11\n2\n", execute("""
                (Set Number) source = [1 2]
                settled = eager (map (value -> value[0] + 10) source)
                print isKeyed settled
                print hasValues settled
                print (@settled).hasValues
                print settled[11]
                print size (fields settled)
                """));

        class ChangingFacts implements Value.Reflective, CollectionRuntime.Provider {
            boolean enumerated;
            @Override public Optional<Value> find(String name) { return Optional.empty(); }
            @Override public Map<String, Value> fields() { return Map.of(); }
            @Override public Value getElement(Value key) { return Value.Missing.INSTANCE; }
            @Override public Value keys() { enumerated = true; return Value.EmptyCollection.INSTANCE; }
            @Override public Value valueEntries() { return Value.EmptyCollection.INSTANCE; }
            @Override public Value fieldEntries() { return Value.EmptyCollection.INSTANCE; }
            @Override public Value size() { return new Value.Num(0); }
            @Override public CollectionRuntime.Facts facts() {
                return new CollectionRuntime.Facts(enumerated ? CollectionRuntime.Guarantee.TRUE
                        : CollectionRuntime.Guarantee.FALSE,
                        enumerated ? CollectionRuntime.Guarantee.FALSE : CollectionRuntime.Guarantee.TRUE,
                        CollectionRuntime.Guarantee.UNKNOWN, CollectionRuntime.Guarantee.TRUE,
                        CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.TRUE);
            }
        }
        LangException changed = assertThrows(LangException.class,
                () -> EagerRuntime.materialize(new ChangingFacts(), null, ReflectionContext.defining()));
        assertEquals(Diagnostic.Codes.CONTRADICTORY_COLLECTION_GUARANTEES, changed.diagnostic().code());
    }

    @Test
    void eagerRejectsInfiniteAndCyclicCollectionsWithLocatedErrors() {
        Value.Seq cycle = new Value.Seq(List.of());
        cycle.appendOwned(cycle);
        SourceSpan span = new SourceSpan(new SourcePosition(14, 3, 5), new SourcePosition(19, 3, 10));
        LangException cyclic = assertThrows(LangException.class, () -> EagerRuntime.materialize(cycle, span,
                ReflectionContext.defining()));
        assertEquals(Diagnostic.Codes.EAGER_CYCLE, cyclic.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, cyclic.diagnostic().phase());
        assertSame(DiagnosticCatalog.EAGER_CYCLE, cyclic.catalogEntry());
        assertEquals(span, cyclic.span());
        assertEquals("Line 3, column 5: eager encountered cyclic Collection containment", cyclic.getMessage());
        assertEquals(3, cyclic.span().start().line());
        assertEquals(5, cyclic.span().start().column());
        class InfiniteProvider implements Value.Reflective, CollectionRuntime.Provider {
            @Override public Optional<Value> find(String name) { return Optional.empty(); }
            @Override public Map<String, Value> fields() { return Map.of(); }
            @Override public Value getElement(Value key) { return Value.Missing.INSTANCE; }
            @Override public Value keys() { throw new AssertionError("Infinite source must not enumerate"); }
            @Override public Value valueEntries() { throw new AssertionError("Infinite source must not enumerate"); }
            @Override public Value fieldEntries() { throw new AssertionError("Infinite source must not enumerate"); }
            @Override public Value size() { return Value.Missing.INSTANCE; }
            @Override public CollectionRuntime.Facts facts() {
                return new CollectionRuntime.Facts(CollectionRuntime.Guarantee.FALSE,
                        CollectionRuntime.Guarantee.UNKNOWN, CollectionRuntime.Guarantee.UNKNOWN,
                        CollectionRuntime.Guarantee.FALSE, CollectionRuntime.Guarantee.FALSE,
                        CollectionRuntime.Guarantee.TRUE);
            }
        }
        LangException infinite = assertThrows(LangException.class,
                () -> EagerRuntime.materialize(new InfiniteProvider(), span, ReflectionContext.defining()));
        assertEquals(Diagnostic.Codes.EAGER_INFINITE, infinite.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, infinite.diagnostic().phase());
        assertSame(DiagnosticCatalog.EAGER_INFINITE, infinite.catalogEntry());
        assertEquals(span, infinite.span());
        assertEquals("Line 3, column 5: eager cannot materialize a declared-infinite Collection",
                infinite.getMessage());
    }

    @Test
    void eagerMaterializedKeyCollisionsSkipIgnoredValuesAndKeepSharedDag() {
        Value.Dictionary firstKey = Value.Dictionary.reflection(Map.of("id", new Value.Num(1)),
                new Value.Num(1), ReflectionContext.defining());
        Value.Dictionary secondKey = Value.Dictionary.reflection(Map.of("id", new Value.Num(2)),
                new Value.Num(2), ReflectionContext.defining());
        Value.Seq ignoredCycle = new Value.Seq(List.of());
        ignoredCycle.appendOwned(ignoredCycle);
        Value.KeyedCollection source = new Value.KeyedCollection(Value.KeyedCollection.Shape.GENERAL,
                List.of(new Value.KeyedCollection.Entry(firstKey, new Value.Num(10)),
                        new Value.KeyedCollection.Entry(secondKey, ignoredCycle)));
        Value.SettledCollection result = (Value.SettledCollection) EagerRuntime.materialize(source, null,
                ReflectionContext.defining());
        assertEquals(1, result.entries().size());
        assertSame(Value.EmptyCollection.INSTANCE, result.entries().getFirst().key());
        assertEquals(new Value.Num(10), result.entries().getFirst().value());
        assertEquals(2, source.entries().size());

        Value.Seq shared = new Value.Seq(List.of(new Value.Num(5)));
        Value.SettledCollection dag = (Value.SettledCollection) EagerRuntime.materialize(
                new Value.Seq(List.of(shared, shared)), null, ReflectionContext.defining());
        assertSame(dag.entries().get(0).value(), dag.entries().get(1).value());
        Value.Field sharedField = new Value.Field(new Value.Str("x"), shared);
        Value.SettledCollection fieldDag = (Value.SettledCollection) EagerRuntime.materialize(
                new Value.Seq(List.of(sharedField, sharedField)), null, ReflectionContext.defining());
        assertSame(fieldDag.entries().get(0).value(), fieldDag.entries().get(1).value());
    }

    @Test
    void eagerPreservesUnavailableKeysAndAdaptsInvalidatedContracts() {
        class KeylessProvider implements Value.Reflective, CollectionRuntime.Provider {
            @Override public Optional<Value> find(String name) { return Optional.empty(); }
            @Override public Map<String, Value> fields() { return Map.of(); }
            @Override public Value getElement(Value key) { return new Value.Num(99); }
            @Override public Value keys() { return Value.Missing.INSTANCE; }
            @Override public Value valueEntries() { return new Value.Seq(List.of(new Value.Num(1))); }
            @Override public Value fieldEntries() { return valueEntries(); }
            @Override public Value size() { return new Value.Num(1); }
            @Override public CollectionRuntime.Facts facts() {
                return new CollectionRuntime.Facts(CollectionRuntime.Guarantee.FALSE,
                        CollectionRuntime.Guarantee.UNKNOWN, CollectionRuntime.Guarantee.TRUE,
                        CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.FALSE,
                        CollectionRuntime.Guarantee.TRUE);
            }
        }
        KeylessProvider source = new KeylessProvider();
        CollectionRuntime.Provider result = (CollectionRuntime.Provider) EagerRuntime.materialize(source, null,
                ReflectionContext.defining());
        assertSame(Value.Missing.INSTANCE, result.keys());
        assertSame(Value.Missing.INSTANCE, result.getElement(new Value.Num(0)));
        assertEquals(new Value.Num(99), source.getElement(new Value.Num(0)));
        assertEquals(CollectionRuntime.Guarantee.UNKNOWN, result.facts().unique());

        ContractDescriptor numbers = BuiltinContract.SEQUENCE.parameterize(List.of(BuiltinContract.NUMBER));
        Value.Attributed attributed = new Value.Attributed(new Value.Seq(List.of(new Value.Num(1))),
                Set.of(numbers));
        Value.Attributed materialized = assertInstanceOf(Value.Attributed.class,
                EagerRuntime.materialize(attributed, null, ReflectionContext.defining()));
        assertTrue(materialized.contracts().contains(numbers));
        assertTrue(numbers.accepts(materialized));
    }

    @Test
    void withReturnsBodyResultAndOuterTraversesNestedMemberLayers() {
        assertEquals("""
                2
                1
                3
                2
                1
                5
                """, execute("""
                x = 1
                a = [^x = 2 ^z = 3]
                b = [^x = 3]
                result = with a
                  print x
                  print outer.x
                  with b
                    print x
                    print outer.x
                    print outer.outer.x
                  z + 2
                print result
                """));
        assertEquals("2\ntrue\n", execute("""
                cell = { (Number) 2 }
                a = [^cell = cell]
                with a
                  print cell{}
                  print cell == outer.cell
                """));
        assertEquals("2\n", execute("""
                x = 1
                a = [^x = 2]
                with a
                  (Output StateRead StateWrite) getter ignored = x
                  with [^x = 3]
                    print getter 0
                """));
        assertEquals("2\n2\n1\n", execute("""
                calls = { (Number) 0 }
                (StateRead StateWrite) make ignored =
                  put calls (calls{} + 1)
                  [^x = 2]
                with make 0
                  print x
                  print x
                print calls{}
                """));
        assertEquals("7\n", execute("""
                custom receiver key = 7
                record = [^getElement = custom ^target = [^x = 1]]
                with record
                  print target.x
                """));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(new PrintStream(output));
        Value shadowedPrint = interpreter.execute(new Parser("""
                increment value = value + 1
                record = [^print = increment]
                with record
                  print 4
                """).parseProgram());
        assertEquals(new Value.Num(5), shadowedPrint);
        assertEquals("", output.toString());
    }

    @Test
    void withBindsOnlyEnumeratedNamesBeforeBodyAndKeepsMembersLazy() {
        List<String> trace = new java.util.ArrayList<>();
        class NamedProvider implements Value.Reflective, CollectionRuntime.Provider {
            @Override public Optional<Value> find(String name) { return Optional.empty(); }
            @Override public Map<String, Value> fields() { return Map.of(); }
            @Override public Value getElement(Value key) {
                String name = ((Value.Str) key).value();
                trace.add("read:" + name);
                return name.equals("a") ? Value.Missing.INSTANCE : new Value.Num(20);
            }
            @Override public Value keys() {
                trace.add("keys");
                return new Value.LazySeq(3, index -> {
                    trace.add("key:" + index);
                    return new Value.Str(List.of("a", "b", "later").get(index));
                });
            }
            @Override public Value valueEntries() { throw new AssertionError(); }
            @Override public Value fieldEntries() { throw new AssertionError(); }
            @Override public Value size() { return new Value.Num(3); }
            @Override public CollectionRuntime.Facts facts() {
                return new CollectionRuntime.Facts(CollectionRuntime.Guarantee.FALSE,
                        CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.UNKNOWN,
                        CollectionRuntime.Guarantee.TRUE, CollectionRuntime.Guarantee.TRUE,
                        CollectionRuntime.Guarantee.TRUE);
            }
        }
        Interpreter interpreter = new Interpreter(new PrintStream(new ByteArrayOutputStream()));
        interpreter.defineEmbeddingValue("target", NamedProvider::new);
        Value result = interpreter.execute(new Parser("""
                a = 9
                with target
                  a
                  b
                """).parseProgram());
        assertEquals(new Value.Num(20), result);
        assertEquals(List.of("keys", "key:0", "key:1", "read:a", "read:b"), trace);

        trace.clear();
        Interpreter missing = new Interpreter(new PrintStream(new ByteArrayOutputStream()));
        missing.defineEmbeddingValue("target", NamedProvider::new);
        Value shadowed = missing.execute(new Parser("""
                a = 9
                with target
                  a
                """).parseProgram());
        assertSame(Value.Missing.INSTANCE, shadowed);
        assertEquals(List.of("keys", "key:0", "read:a"), trace);

        trace.clear();
        Interpreter absent = new Interpreter(new PrintStream(new ByteArrayOutputStream()));
        absent.defineEmbeddingValue("target", NamedProvider::new);
        Value fallback = absent.execute(new Parser("""
                age = 9
                with target
                  age
                """).parseProgram());
        assertEquals(new Value.Num(9), fallback);
        assertEquals(List.of("keys", "key:0", "key:1", "key:2"), trace);
    }

    @Test
    void withRejectsInvalidTargetsAndOuterCannotBecomeAScopeValue() {
        LangException target = expectDiagnostic("with [1]\n  2", "with target must expose", 1, 6);
        assertEquals(Diagnostic.Codes.EXPECTED_WITH_TARGET, target.diagnostic().code());
        assertEquals(Diagnostic.Phase.RUNTIME, target.diagnostic().phase());
        assertSame(DiagnosticCatalog.EXPECTED_WITH_TARGET, target.catalogEntry());
        assertEquals("Line 1, column 6: with target must expose public named members", target.getMessage());
        record InvalidOuter(String source, int line, int column) {}
        for (InvalidOuter invalid : List.of(new InvalidOuter("value = outer", 1, 9),
                new InvalidOuter("value = @outer", 1, 10),
                new InvalidOuter("value = outer[\"x\"]", 1, 9),
                new InvalidOuter("with [^x = 1]\n  outer.outer.x", 2, 3))) {
            LangException failure = assertThrows(LangException.class, () -> execute(invalid.source()));
            assertEquals(Diagnostic.Codes.INVALID_OUTER_PATH, failure.diagnostic().code());
            assertEquals(Diagnostic.Phase.SEMANTIC, failure.diagnostic().phase());
            assertSame(DiagnosticCatalog.INVALID_OUTER_PATH, failure.catalogEntry());
            assertEquals(invalid.line(), failure.span().start().line());
            assertEquals(invalid.column(), failure.span().start().column());
            assertEquals("Line " + invalid.line() + ", column " + invalid.column()
                    + ": outer is only valid as a lexical member path inside with", failure.getMessage());
        }
        LangException privateName = expectDiagnostic("""
                make ignored =
                  internal = 1
                  ^public = 2
                person = make 0
                with person
                  internal
                """, "Unknown name: internal", 6, 3);
        assertEquals(Diagnostic.Codes.UNKNOWN_NAME, privateName.diagnostic().code());
        LangException local = expectDiagnostic("""
                record = [^x = 2]
                with record
                  x = x
                """, "Binding read before initialization", 3, 7);
        assertEquals(Diagnostic.Codes.READ_BEFORE_INITIALIZATION, local.diagnostic().code());
    }

}
