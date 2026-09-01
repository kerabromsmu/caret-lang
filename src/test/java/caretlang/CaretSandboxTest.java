package caretlang;

import caretlang.embedding.*;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CaretSandboxTest {
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
            assertEquals("Host callback failed: explode", result.diagnostics().getFirst().message());
            assertEquals("before\n", bytes.toString(StandardCharsets.UTF_8));
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
        second.close();
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
}
