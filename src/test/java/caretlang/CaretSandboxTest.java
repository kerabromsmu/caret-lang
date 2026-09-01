package caretlang;

import caretlang.embedding.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CaretSandboxTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsExecutesRegistersAndInvokesOneScript() {
        try (CaretSandbox sandbox = sandbox(CaretEnvironment.builder()
                .enableCallbackRegistration().build())) {
            CaretLoadResult loaded = sandbox.load(CaretSource.text("callbacks.caret", """
                    double value = value * 2
                    registered = registerCallbacks [^double = double]
                    answer = 21
                    """));
            assertEquals(CaretOperationResult.Code.SUCCESS, loaded.code());

            LoadedProgram program = loaded.value().orElseThrow();
            CaretExecutionResult executed = sandbox.execute(program);
            assertEquals(CaretOperationResult.Code.SUCCESS, executed.code());
            assertEquals(new CaretValue.NumberValue(21), executed.value().orElseThrow().find("answer").orElseThrow());

            CaretCallable callback = sandbox.registeredCallbacks().get("double");
            assertEquals(new CaretValue.NumberValue(42), callback.invoke(List.of(CaretValue.number(21)))
                    .value().orElseThrow());
            assertEquals(new CaretValue.NumberValue(10), sandbox.invoke(callback, List.of(CaretValue.number(5)))
                    .value().orElseThrow());

            CaretEmbeddingException repeated = assertThrows(CaretEmbeddingException.class,
                    () -> sandbox.execute(program));
            assertEquals(CaretEmbeddingException.Code.HANDLE_CONSUMED, repeated.code());
        }
    }

    @Test
    void invalidSourceIsAResultAndStillConsumesTheLoadSlot() {
        try (CaretSandbox sandbox = sandbox(CaretEnvironment.builder().build())) {
            CaretLoadResult result = sandbox.load(CaretSource.text("bad.caret", "value ="));
            assertEquals(CaretOperationResult.Code.FAILURE, result.code());
            assertFalse(result.diagnostics().isEmpty());
            assertEquals("bad.caret", result.diagnostics().getFirst().location().sourceName());

            CaretEmbeddingException repeated = assertThrows(CaretEmbeddingException.class,
                    () -> sandbox.load(CaretSource.text("good.caret", "value = 1")));
            assertEquals(CaretEmbeddingException.Code.ALREADY_LOADED, repeated.code());
        }
    }

    @Test
    void returnedCallablesRemainUsableAndResolveSwappedValues() {
        AtomicInteger firstReads = new AtomicInteger();
        CaretEnvironment first = CaretEnvironment.builder()
                .value("hostValue", () -> { firstReads.incrementAndGet(); return CaretValue.number(3); }).build();
        try (CaretSandbox sandbox = sandbox(first)) {
            LoadedProgram program = sandbox.load(CaretSource.text("lazy.caret", "read ignored = hostValue + hostValue"))
                    .value().orElseThrow();
            CaretCallable read = (CaretCallable) sandbox.execute(program).value().orElseThrow().find("read").orElseThrow();
            assertEquals(CaretValue.number(6), sandbox.invoke(read, List.of(CaretValue.missing())).value().orElseThrow());
            assertEquals(CaretValue.number(6), sandbox.invoke(read, List.of(CaretValue.missing())).value().orElseThrow());
            assertEquals(1, firstReads.get());

            AtomicInteger secondReads = new AtomicInteger();
            sandbox.swapEnvironment(CaretEnvironment.builder()
                    .value("hostValue", () -> { secondReads.incrementAndGet(); return CaretValue.number(5); }).build());
            assertEquals(CaretValue.number(10), read.invoke(List.of(CaretValue.missing())).value().orElseThrow());
            assertEquals(1, secondReads.get());

            sandbox.swapEnvironment(CaretEnvironment.builder().build());
            CaretInvocationResult unavailable = read.invoke(List.of(CaretValue.missing()));
            assertEquals(CaretOperationResult.Code.FAILURE, unavailable.code());

            sandbox.swapEnvironment(CaretEnvironment.builder().value("hostValue", () -> CaretValue.number(7)).build());
            assertEquals(CaretValue.number(14), read.invoke(List.of(CaretValue.missing())).value().orElseThrow());
        }
    }

    @Test
    void hostCallbacksAndOutputFollowBoundaryFailureRules() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CaretEnvironment environment = CaretEnvironment.builder().enablePrint()
                .callback("explode", 1, Set.of(), values -> { throw new Exception("secret"); })
                .build();
        try (CaretSandbox sandbox = CaretSandbox.builder().environment(environment)
                .output(new PrintStream(bytes, true, StandardCharsets.UTF_8)).build()) {
            LoadedProgram program = sandbox.load(CaretSource.text("failure.caret", """
                    print "before"
                    result = explode 1
                    """)).value().orElseThrow();
            CaretExecutionResult result = sandbox.execute(program);
            assertEquals(CaretOperationResult.Code.FAILURE, result.code());
            CaretDiagnostic diagnostic = result.diagnostics().getFirst();
            assertEquals("INTERNAL_ERROR", diagnostic.code());
            assertEquals(CaretDiagnostic.Phase.RUNTIME, diagnostic.phase());
            assertEquals("Host callback failed: explode", diagnostic.message());
            assertEquals("failure.caret", diagnostic.location().sourceName());
            assertEquals(2, diagnostic.location().startLine());
            assertEquals(10, diagnostic.location().startColumn());
            assertTrue(diagnostic.notes().isEmpty());
            assertFalse(diagnostic.toString().contains("secret"));
            assertFalse(diagnostic.toString().contains("Exception"));
            assertFalse(diagnostic.toString().contains("java."));
            assertEquals("before\n", bytes.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void publicValuesRoundTripWithoutCollapsingNullOrMissing() {
        try (CaretSandbox sandbox = sandbox(CaretEnvironment.builder().build())) {
            LoadedProgram program = sandbox.load(CaretSource.text("values.caret", "identity value = value"))
                    .value().orElseThrow();
            CaretCallable identity = (CaretCallable) sandbox.execute(program).value().orElseThrow()
                    .find("identity").orElseThrow();
            List<CaretValue> values = List.of(
                    CaretValue.number(12.5),
                    CaretValue.text("Hej 🌍"),
                    CaretValue.bool(true),
                    CaretValue.nullValue(),
                    CaretValue.missing(),
                    new CaretValue.FieldValue("field", CaretValue.text("value")),
                    CaretValue.sequence(List.of(CaretValue.number(1), CaretValue.nullValue(), CaretValue.missing())),
                    CaretValue.collection(Map.of("name", CaretValue.text("Caret"), "none", CaretValue.nullValue())));

            for (CaretValue value : values) {
                assertEquals(value, identity.invoke(List.of(value)).value().orElseThrow());
            }
            assertNotEquals(CaretValue.nullValue(), CaretValue.missing());
            CaretCallable returned = (CaretCallable) identity.invoke(List.of(identity)).value().orElseThrow();
            assertEquals(identity.arity(), returned.arity());
            assertEquals(CaretValue.text("callable"), returned.invoke(List.of(CaretValue.text("callable")))
                    .value().orElseThrow());
        }
    }

    @Test
    void executionReturnsTopLevelBindingsWithoutExposingNestedLexicalLocals() {
        try (CaretSandbox sandbox = sandbox(CaretEnvironment.builder().build())) {
            LoadedProgram program = sandbox.load(CaretSource.text("visibility.caret", """
                    make value =
                      hidden = value
                      ^visible = hidden

                    top = 7
                    """)).value().orElseThrow();
            CaretValue.CollectionValue result = sandbox.execute(program).value().orElseThrow();
            assertEquals(Set.of("make", "top"), result.fields().keySet());
            assertEquals(CaretValue.number(7), result.find("top").orElseThrow());
            assertTrue(result.find("hidden").isEmpty());
            assertTrue(result.find("visible").isEmpty());
        }
    }

    @Test
    void immutableValuesArePortableButCallableHandlesRemainSandboxOwned() {
        try (CaretSandbox first = sandbox(CaretEnvironment.builder().build());
             CaretSandbox second = sandbox(CaretEnvironment.builder().build())) {
            CaretCallable firstIdentity = identity(first, "first.caret");
            CaretCallable secondIdentity = identity(second, "second.caret");
            CaretValue fromFirst = firstIdentity.invoke(List.of(CaretValue.collection(Map.of(
                    "number", CaretValue.number(2),
                    "absence", CaretValue.missing())))).value().orElseThrow();

            assertEquals(fromFirst, secondIdentity.invoke(List.of(fromFirst)).value().orElseThrow());
            assertEmbeddingCode(CaretEmbeddingException.Code.FOREIGN_HANDLE,
                    () -> secondIdentity.invoke(List.of(firstIdentity)));
            assertEmbeddingCode(CaretEmbeddingException.Code.FOREIGN_HANDLE,
                    () -> secondIdentity.invoke(List.of(CaretValue.sequence(List.of(firstIdentity)))));
            assertEmbeddingCode(CaretEmbeddingException.Code.FOREIGN_HANDLE,
                    () -> secondIdentity.invoke(List.of(CaretValue.collection(Map.of("nested", firstIdentity)))));
            assertEmbeddingCode(CaretEmbeddingException.Code.FOREIGN_HANDLE,
                    () -> secondIdentity.invoke(List.of(new CaretValue.FieldValue("nested", firstIdentity))));
            assertEmbeddingCode(CaretEmbeddingException.Code.FOREIGN_HANDLE,
                    () -> second.invoke(firstIdentity, List.of(CaretValue.missing())));
        }
    }

    @Test
    void hostValueCacheSurvivesRollbackAndIsDiscardedByEnvironmentSwap() {
        AtomicInteger firstReads = new AtomicInteger();
        CaretEnvironment first = CaretEnvironment.builder()
                .value("hostValue", () -> {
                    firstReads.incrementAndGet();
                    return CaretValue.number(3);
                }).build();
        try (CaretSandbox sandbox = sandbox(first)) {
            LoadedProgram program = sandbox.load(CaretSource.text("cache.caret", """
                    read shouldFail =
                      captured = hostValue
                      shouldFail & (1 / 0) ! captured
                    """)).value().orElseThrow();
            CaretCallable read = (CaretCallable) sandbox.execute(program).value().orElseThrow()
                    .find("read").orElseThrow();

            assertEquals(CaretOperationResult.Code.FAILURE,
                    read.invoke(List.of(CaretValue.bool(true))).code());
            assertEquals(CaretValue.number(3),
                    read.invoke(List.of(CaretValue.bool(false))).value().orElseThrow());
            assertEquals(1, firstReads.get());

            AtomicInteger secondReads = new AtomicInteger();
            sandbox.swapEnvironment(CaretEnvironment.builder()
                    .value("hostValue", () -> {
                        secondReads.incrementAndGet();
                        return CaretValue.number(5);
                    }).build());
            assertEquals(CaretValue.number(5),
                    read.invoke(List.of(CaretValue.bool(false))).value().orElseThrow());
            assertEquals(1, secondReads.get());
        }
    }

    @Test
    void failedHostValueResolutionIsCachedUntilEnvironmentSwap() {
        AtomicInteger firstReads = new AtomicInteger();
        CaretEnvironment first = CaretEnvironment.builder()
                .value("hostValue", () -> {
                    firstReads.incrementAndGet();
                    throw new IllegalStateException("first secret");
                }).build();
        try (CaretSandbox sandbox = sandbox(first)) {
            LoadedProgram program = sandbox.load(CaretSource.text("failed-cache.caret",
                    "read ignored = hostValue")).value().orElseThrow();
            CaretCallable read = (CaretCallable) sandbox.execute(program).value().orElseThrow()
                    .find("read").orElseThrow();

            assertEquals(CaretOperationResult.Code.FAILURE,
                    read.invoke(List.of(CaretValue.missing())).code());
            assertEquals(CaretOperationResult.Code.FAILURE,
                    read.invoke(List.of(CaretValue.missing())).code());
            assertEquals(1, firstReads.get());

            AtomicInteger secondReads = new AtomicInteger();
            sandbox.swapEnvironment(CaretEnvironment.builder()
                    .value("hostValue", () -> {
                        secondReads.incrementAndGet();
                        throw new IllegalStateException("second secret");
                    }).build());
            assertEquals(CaretOperationResult.Code.FAILURE,
                    read.invoke(List.of(CaretValue.missing())).code());
            assertEquals(CaretOperationResult.Code.FAILURE,
                    read.invoke(List.of(CaretValue.missing())).code());
            assertEquals(1, secondReads.get());
        }
    }

    @Test
    void foreignCallablesFromHostValuesAndCallbacksAreCodedMisuseWithoutInvalidation() {
        try (CaretSandbox owner = sandbox(CaretEnvironment.builder().build())) {
            CaretCallable foreign = identity(owner, "owner.caret");

            CaretEnvironment environment = CaretEnvironment.builder()
                    .value("hostValue", () -> CaretValue.collection(Map.of(
                            "nested", CaretValue.sequence(List.of(new CaretValue.FieldValue("callable", foreign))))))
                    .callback("hostCallback", 1, Set.of(), ignored -> CaretValue.collection(Map.of(
                            "nested", CaretValue.sequence(List.of(new CaretValue.FieldValue("callable", foreign))))))
                    .build();
            try (CaretSandbox sandbox = sandbox(environment)) {
                LoadedProgram program = sandbox.load(CaretSource.text("foreign-results.caret", """
                        readValue ignored = hostValue
                        callHost ignored = hostCallback ignored
                        safe value = value
                        """)).value().orElseThrow();
                CaretValue.CollectionValue exports = sandbox.execute(program).value().orElseThrow();
                CaretCallable readValue = (CaretCallable) exports.find("readValue").orElseThrow();
                CaretCallable callHost = (CaretCallable) exports.find("callHost").orElseThrow();
                CaretCallable safe = (CaretCallable) exports.find("safe").orElseThrow();

                assertEmbeddingCode(CaretEmbeddingException.Code.FOREIGN_HANDLE,
                        () -> readValue.invoke(List.of(CaretValue.missing())));
                assertEmbeddingCode(CaretEmbeddingException.Code.FOREIGN_HANDLE,
                        () -> callHost.invoke(List.of(CaretValue.missing())));
                assertEquals(CaretValue.number(3),
                        safe.invoke(List.of(CaretValue.number(3))).value().orElseThrow());
            }
        }
    }

    @Test
    void diagnosticsPreserveStablePhaseLocationsAndRelatedNotes() {
        try (CaretSandbox parserSandbox = sandbox(CaretEnvironment.builder().build())) {
            CaretDiagnostic parser = parserSandbox.load(CaretSource.text("parser.caret", "value ="))
                    .diagnostics().getFirst();
            assertEquals(CaretDiagnostic.Phase.PARSER, parser.phase());
            assertEquals("parser.caret", parser.location().sourceName());
            assertEquals(1, parser.location().startLine());
            assertTrue(parser.location().startColumn() > 0);
        }

        try (CaretSandbox semanticSandbox = sandbox(CaretEnvironment.builder().build())) {
            CaretDiagnostic semantic = semanticSandbox.load(CaretSource.text("semantic.caret", """
                    value = 1
                    value = 2
                    """)).diagnostics().getFirst();
            assertEquals("DUPLICATE_DEFINITION", semantic.code());
            assertEquals(CaretDiagnostic.Phase.SEMANTIC, semantic.phase());
            assertEquals(2, semantic.location().startLine());
            assertEquals(1, semantic.location().startColumn());
            assertFalse(semantic.notes().isEmpty());
            assertEquals(1, semantic.notes().getFirst().location().startLine());
        }

        try (CaretSandbox runtimeSandbox = sandbox(CaretEnvironment.builder().build())) {
            LoadedProgram program = runtimeSandbox.load(CaretSource.text("runtime.caret", "value = 1 / 0"))
                    .value().orElseThrow();
            CaretDiagnostic runtime = runtimeSandbox.execute(program).diagnostics().getFirst();
            assertEquals("DIVISION_BY_ZERO", runtime.code());
            assertEquals(CaretDiagnostic.Phase.RUNTIME, runtime.phase());
            assertEquals("runtime.caret", runtime.location().sourceName());
            assertEquals(1, runtime.location().startLine());
            assertTrue(runtime.location().startColumn() > 0);
        }
    }

    @Test
    void invalidHostUseHasStableExceptionCodes() {
        CaretSandbox first = sandbox(CaretEnvironment.builder().build());
        CaretSandbox second = sandbox(CaretEnvironment.builder().build());
        try {
            assertEmbeddingCode(CaretEmbeddingException.Code.INVALID_ARGUMENT, () -> first.load(null));
            LoadedProgram program = first.load(CaretSource.text("host-use.caret", "identity value = value"))
                    .value().orElseThrow();
            assertEmbeddingCode(CaretEmbeddingException.Code.FOREIGN_HANDLE, () -> second.execute(program));
            CaretCallable identity = (CaretCallable) first.execute(program).value().orElseThrow()
                    .find("identity").orElseThrow();
            assertEmbeddingCode(CaretEmbeddingException.Code.INVALID_ARITY,
                    () -> identity.invoke(List.of()));
            assertEmbeddingCode(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                    () -> first.invoke(identity, null));
            assertEmbeddingCode(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                    () -> first.invoke(identity, Arrays.asList((CaretValue) null)));
            assertEmbeddingCode(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                    () -> first.swapEnvironment(null));
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void environmentValidationAndArityPreservingReplacementAreTransactional() {
        assertThrows(IllegalArgumentException.class, () -> CaretEnvironment.builder().value("not-valid", CaretValue::missing));
        assertThrows(IllegalArgumentException.class, () -> CaretEnvironment.builder()
                .value("duplicate", CaretValue::missing).value("duplicate", CaretValue::missing));
        assertThrows(IllegalArgumentException.class, () -> CaretEnvironment.builder()
                .callback("registerCallbacks", 1, Set.of(), List::getFirst));

        CaretEnvironment initial = CaretEnvironment.builder()
                .callback("host", 1, Set.of(), values -> CaretValue.text("first")).build();
        try (CaretSandbox sandbox = sandbox(initial)) {
            LoadedProgram program = sandbox.load(CaretSource.text("swap.caret", "call value = host value"))
                    .value().orElseThrow();
            CaretCallable call = (CaretCallable) sandbox.execute(program).value().orElseThrow()
                    .find("call").orElseThrow();
            assertEquals(CaretValue.text("first"), call.invoke(List.of(CaretValue.missing())).value().orElseThrow());

            CaretEnvironment invalid = CaretEnvironment.builder()
                    .callback("host", 2, Set.of(), values -> CaretValue.text("invalid")).build();
            assertEmbeddingCode(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                    () -> sandbox.swapEnvironment(invalid));
            assertEquals(CaretValue.text("first"), call.invoke(List.of(CaretValue.missing())).value().orElseThrow());

            CaretEnvironment changedEffects = CaretEnvironment.builder().allowEffect(CaretEffect.STATE_WRITE)
                    .callback("host", 1, Set.of(CaretEffect.STATE_WRITE), values -> CaretValue.text("effectful"))
                    .build();
            assertEmbeddingCode(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                    () -> sandbox.swapEnvironment(changedEffects));
            assertEquals(CaretValue.text("first"), call.invoke(List.of(CaretValue.missing())).value().orElseThrow());

            CaretEnvironment newName = CaretEnvironment.builder()
                    .callback("host", 1, Set.of(), values -> CaretValue.text("second"))
                    .value("late", CaretValue::missing).build();
            assertEmbeddingCode(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                    () -> sandbox.swapEnvironment(newName));
            assertEquals(CaretValue.text("first"), call.invoke(List.of(CaretValue.missing())).value().orElseThrow());

            CaretEnvironment newCallback = CaretEnvironment.builder()
                    .callback("host", 1, Set.of(), values -> CaretValue.text("second"))
                    .callback("late", 1, Set.of(), List::getFirst).build();
            assertEmbeddingCode(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                    () -> sandbox.swapEnvironment(newCallback));

            CaretEnvironment replacement = CaretEnvironment.builder()
                    .callback("host", 1, Set.of(), values -> CaretValue.text("second")).build();
            sandbox.swapEnvironment(replacement);
            assertEquals(CaretValue.text("second"), call.invoke(List.of(CaretValue.missing())).value().orElseThrow());
        }
    }

    @Test
    void effectfulCallbacksCannotBeReboundAsPure() {
        CaretEnvironment effectful = CaretEnvironment.builder().allowEffect(CaretEffect.STATE_WRITE)
                .callback("host", 1, Set.of(CaretEffect.STATE_WRITE), List::getFirst).build();
        try (CaretSandbox sandbox = sandbox(effectful)) {
            sandbox.load(CaretSource.text("effect-schema.caret", "value = 1")).value().orElseThrow();
            CaretEnvironment pure = CaretEnvironment.builder()
                    .callback("host", 1, Set.of(), List::getFirst).build();
            assertEmbeddingCode(CaretEmbeddingException.Code.INVALID_ARGUMENT,
                    () -> sandbox.swapEnvironment(pure));
        }
    }

    @Test
    void replacementBeforeLoadMayChangeTheCompleteBindingSchema() {
        try (CaretSandbox sandbox = sandbox(CaretEnvironment.builder().value("old", CaretValue::missing).build())) {
            CaretEnvironment replacement = CaretEnvironment.builder()
                    .value("newValue", () -> CaretValue.number(4))
                    .callback("newCallback", 1, Set.of(), List::getFirst)
                    .build();
            sandbox.swapEnvironment(replacement);
            LoadedProgram program = sandbox.load(CaretSource.text("before-load.caret", """
                    ^value = newValue
                    ^called = newCallback 5
                    """)).value().orElseThrow();
            CaretValue.CollectionValue result = sandbox.execute(program).value().orElseThrow();
            assertEquals(CaretValue.number(4), result.find("value").orElseThrow());
            assertEquals(CaretValue.number(5), result.find("called").orElseThrow());
        }
    }

    @Test
    void unexpectedRuntimeFailuresAreNotMislabeledAsCaretDiagnostics() {
        assertTrue(EmbeddingBridge.diagnostic(new IllegalStateException("implementation defect"), "source.caret")
                .isEmpty());
    }

    @Test
    void sourcePathReadsUtf8AndPreservesNormalizedIdentity() throws Exception {
        Path sourcePath = temporaryDirectory.resolve("nested").resolve("..").resolve("unicode.caret");
        Files.writeString(temporaryDirectory.resolve("unicode.caret"), "^message = \"Hej 🌍\"");
        CaretSource source = CaretSource.path(sourcePath);
        assertEquals(sourcePath.toAbsolutePath().normalize(), source.path());
        assertEquals(source.path().toString(), source.name());
        assertEquals("^message = \"Hej 🌍\"", source.text());
        try (CaretSandbox sandbox = sandbox(CaretEnvironment.builder().build())) {
            CaretValue message = sandbox.execute(sandbox.load(source).value().orElseThrow()).value().orElseThrow()
                    .find("message").orElseThrow();
            assertEquals(CaretValue.text("Hej 🌍"), message);
        }
    }

    @Test
    void sameSandboxReentryFailsFast() {
        AtomicInteger code = new AtomicInteger(-1);
        CaretSandbox[] holder = new CaretSandbox[1];
        CaretEnvironment environment = CaretEnvironment.builder()
                .callback("reenter", 1, Set.of(), values -> {
                    try { holder[0].registeredCallbacks(); }
                    catch (CaretEmbeddingException failure) { code.set(failure.code().ordinal()); }
                    return values.getFirst();
                }).build();
        try (CaretSandbox sandbox = sandbox(environment)) {
            holder[0] = sandbox;
            LoadedProgram program = sandbox.load(CaretSource.text("reentry.caret", "value = reenter 1"))
                    .value().orElseThrow();
            assertEquals(CaretOperationResult.Code.SUCCESS, sandbox.execute(program).code());
            assertEquals(CaretEmbeddingException.Code.BUSY.ordinal(), code.get());
        }
    }

    @Test
    void runnableEmbeddingExampleIsExercised() throws Exception {
        try (CaretSandbox sandbox = sandbox(CaretEnvironment.builder().enableCallbackRegistration().build())) {
            LoadedProgram program = sandbox.load(CaretSource.path(Path.of("examples/embedding.caret")))
                    .value().orElseThrow();
            assertEquals(CaretOperationResult.Code.SUCCESS, sandbox.execute(program).code());
            CaretInvocationResult result = sandbox.registeredCallbacks().get("greet")
                    .invoke(List.of(CaretValue.text("Java")));
            assertEquals(CaretValue.text("Hello, Java"), result.value().orElseThrow());
        }
    }

    @Test
    void callbackRegistryReplacementRollsBackWithFailedInvocation() {
        try (CaretSandbox sandbox = sandbox(CaretEnvironment.builder().enableCallbackRegistration().build())) {
            LoadedProgram program = sandbox.load(CaretSource.text("registry.caret", """
                    first value = value
                    replace shouldFail =
                      registered = registerCallbacks [^replace = replace]
                      shouldFail & (1 / 0) ! 0
                    initial = registerCallbacks [^first = first ^replace = replace]
                    """)).value().orElseThrow();
            assertEquals(CaretOperationResult.Code.SUCCESS, sandbox.execute(program).code());
            Map<String, CaretCallable> before = sandbox.registeredCallbacks();
            CaretInvocationResult failed = before.get("replace").invoke(List.of(CaretValue.bool(true)));
            assertEquals(CaretOperationResult.Code.FAILURE, failed.code());
            assertEquals(before.keySet(), sandbox.registeredCallbacks().keySet());
        }
    }

    @Test
    void closedAndForeignHandlesAreHostExceptions() {
        CaretSandbox first = sandbox(CaretEnvironment.builder().build());
        CaretSandbox second = sandbox(CaretEnvironment.builder().build());
        LoadedProgram program = first.load(CaretSource.text("one.caret", "identity value = value"))
                .value().orElseThrow();
        CaretCallable callable = (CaretCallable) first.execute(program).value().orElseThrow()
                .find("identity").orElseThrow();
        CaretEmbeddingException foreign = assertThrows(CaretEmbeddingException.class,
                () -> second.invoke(callable, List.of(CaretValue.number(1))));
        assertEquals(CaretEmbeddingException.Code.FOREIGN_HANDLE, foreign.code());
        first.close();
        CaretEmbeddingException closed = assertThrows(CaretEmbeddingException.class,
                () -> callable.invoke(List.of(CaretValue.number(1))));
        assertEquals(CaretEmbeddingException.Code.CLOSED, closed.code());
        first.close();
        second.close();
        second.close();
    }

    @Test
    void overlappingUseOfOneSandboxFailsFast() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CaretEnvironment environment = CaretEnvironment.builder()
                .callback("wait", 1, Set.of(), values -> {
                    entered.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new Exception("release timeout");
                    return values.getFirst();
                }).build();
        try (CaretSandbox sandbox = sandbox(environment);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            LoadedProgram program = sandbox.load(CaretSource.text("overlap.caret", "value = wait 1"))
                    .value().orElseThrow();
            var execution = executor.submit(() -> sandbox.execute(program));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEmbeddingCode(CaretEmbeddingException.Code.BUSY, sandbox::registeredCallbacks);
            release.countDown();
            assertEquals(CaretOperationResult.Code.SUCCESS, execution.get().code());
        }
    }

    @Test
    void independentSandboxesCanExecuteConcurrently() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CaretEnvironment environment = CaretEnvironment.builder()
                .callback("waitForPeer", 1, Set.of(), values -> {
                    entered.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new Exception("peer timeout");
                    return values.getFirst();
                }).build();
        try (CaretSandbox first = sandbox(environment); CaretSandbox second = sandbox(environment);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            LoadedProgram firstProgram = first.load(CaretSource.text("first.caret", "value = waitForPeer 1"))
                    .value().orElseThrow();
            LoadedProgram secondProgram = second.load(CaretSource.text("second.caret", "value = waitForPeer 2"))
                    .value().orElseThrow();
            var firstResult = executor.submit(() -> first.execute(firstProgram));
            var secondResult = executor.submit(() -> second.execute(secondProgram));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(CaretOperationResult.Code.SUCCESS, firstResult.get().code());
            assertEquals(CaretOperationResult.Code.SUCCESS, secondResult.get().code());
        }
    }

    @Test
    void fatalHostErrorInvalidatesSandbox() {
        CaretEnvironment environment = CaretEnvironment.builder()
                .callback("fatal", 1, Set.of(), values -> { throw new AssertionError("fatal"); }).build();
        try (CaretSandbox sandbox = sandbox(environment)) {
            LoadedProgram program = sandbox.load(CaretSource.text("fatal.caret", "value = fatal 1"))
                    .value().orElseThrow();
            assertThrows(AssertionError.class, () -> sandbox.execute(program));
            CaretEmbeddingException closed = assertThrows(CaretEmbeddingException.class,
                    sandbox::registeredCallbacks);
            assertEquals(CaretEmbeddingException.Code.CLOSED, closed.code());
        }
    }

    @Test
    void callbackEffectsMustBeVisibleButDoNotGrantCapabilities() {
        assertThrows(IllegalStateException.class, () -> CaretEnvironment.builder()
                .callback("write", 1, Set.of(CaretEffect.STATE_WRITE), List::getFirst).build());
        CaretEnvironment environment = CaretEnvironment.builder().allowEffect(CaretEffect.STATE_WRITE)
                .callback("write", 1, Set.of(CaretEffect.STATE_WRITE), List::getFirst).build();
        assertFalse(environment.printEnabled());
        try (CaretSandbox sandbox = sandbox(environment)) {
            CaretLoadResult rejected = sandbox.load(CaretSource.text("effects.caret", "use value = write value"));
            assertEquals(CaretOperationResult.Code.FAILURE, rejected.code());
            assertEquals("EFFECT_ALLOWANCE_EXCEEDED", rejected.diagnostics().getFirst().code());
        }
    }

    private static CaretSandbox sandbox(CaretEnvironment environment) {
        return CaretSandbox.builder().environment(environment)
                .output(new PrintStream(new ByteArrayOutputStream())).build();
    }

    private static CaretCallable identity(CaretSandbox sandbox, String sourceName) {
        LoadedProgram program = sandbox.load(CaretSource.text(sourceName, "identity value = value"))
                .value().orElseThrow();
        return (CaretCallable) sandbox.execute(program).value().orElseThrow().find("identity").orElseThrow();
    }

    private static void assertEmbeddingCode(CaretEmbeddingException.Code expected, Runnable operation) {
        CaretEmbeddingException failure = assertThrows(CaretEmbeddingException.class, operation::run);
        assertEquals(expected, failure.code());
    }
}
