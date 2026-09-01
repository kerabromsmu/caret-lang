package caretlang.examples;

import caretlang.embedding.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class EmbeddingExample {
    private EmbeddingExample() {}

    public static void main(String[] arguments) throws IOException {
        Path sourcePath = arguments.length == 0 ? Path.of("examples/embedding.caret") : Path.of(arguments[0]);
        CaretEnvironment environment = CaretEnvironment.builder().enableCallbackRegistration().build();
        try (CaretSandbox sandbox = CaretSandbox.builder().environment(environment).output(System.out).build()) {
            CaretLoadResult loaded = sandbox.load(CaretSource.path(sourcePath));
            if (loaded.code() == CaretOperationResult.Code.FAILURE) {
                loaded.diagnostics().forEach(diagnostic -> System.err.println(diagnostic.message()));
                return;
            }
            CaretExecutionResult executed = sandbox.execute(loaded.value().orElseThrow());
            if (executed.code() == CaretOperationResult.Code.FAILURE) {
                executed.diagnostics().forEach(diagnostic -> System.err.println(diagnostic.message()));
                return;
            }
            CaretCallable greet = sandbox.registeredCallbacks().get("greet");
            CaretInvocationResult greeting = greet.invoke(List.of(CaretValue.text("Java")));
            System.out.println(((CaretValue.TextValue) greeting.value().orElseThrow()).value());
        }
    }
}
