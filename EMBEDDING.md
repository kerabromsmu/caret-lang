# Embed Caret in Java

Caret provides a Java 21 API for running one Caret script inside a host-controlled
`caretlang.embedding.CaretSandbox`. The API exposes language-owned values and diagnostics without
exposing interpreter, parser, AST, lexical-scope, or Java-reflection implementation objects.

## Get the SDK

Download `caret-java-sdk-<version>.zip` from the
[Caret releases page](https://github.com/kerabromsmu/caret-lang/releases) and extract it. The SDK
contains the embedding library and its runtime dependencies under `lib/`, this guide, generated API
documentation, and the Java and Caret sources for a runnable example.

Java 21 is required. No Gradle installation or Caret repository checkout is needed.

To use the downloaded JARs from a Gradle project, copy them into the project's `libs/` directory:

```kotlin
dependencies {
    implementation(files("libs/caret-embedding-<version>.jar"))
    implementation(files("libs/jline-3.30.0.jar"))
}
```

Registry publication is not available yet, so there are no supported Maven coordinates.

## Run the packaged example

From the extracted SDK directory on Linux or macOS:

```bash
javac -cp "lib/*" -d classes examples/EmbeddingExample.java
java -cp "classes:lib/*" caretlang.examples.EmbeddingExample examples/embedding.caret
```

On Windows, use `;` as the runtime classpath separator:

```powershell
javac -cp "lib/*" -d classes examples/EmbeddingExample.java
java -cp "classes;lib/*" caretlang.examples.EmbeddingExample examples/embedding.caret
```

Both commands print:

```text
Hello, Java
```

## Load and execute Caret

The smallest host supplies an explicit environment and output destination, loads one source, and
consumes the returned program handle exactly once:

```java
import caretlang.embedding.*;

CaretEnvironment environment = CaretEnvironment.builder().build();

try (CaretSandbox sandbox = CaretSandbox.builder()
        .environment(environment)
        .output(System.out)
        .build()) {
    CaretLoadResult loaded = sandbox.load(CaretSource.text("hello.caret", """
            ^message = "Hello from Caret"
            """));
    if (loaded.code() == CaretOperationResult.Code.FAILURE) {
        loaded.diagnostics().forEach(System.err::println);
        return;
    }

    CaretExecutionResult executed = sandbox.execute(loaded.value().orElseThrow());
    if (executed.code() == CaretOperationResult.Code.FAILURE) {
        executed.diagnostics().forEach(System.err::println);
        return;
    }

    CaretValue message = executed.value().orElseThrow().find("message").orElseThrow();
    System.out.println(((CaretValue.TextValue) message).value());
}
```

Use `CaretSource.path(path)` to read a UTF-8 source file or `CaretSource.text(name, source)` for
source already owned by the host. A sandbox accepts one source and one execution attempt. Create a
new sandbox for another script.

## Exchange values and call functions

`CaretValue` is a sealed public model for finite numbers, text, Booleans, null, missing, fields,
Sequences, named Collections, and callables. Convenience factories include `number`, `text`,
`bool`, `nullValue`, `missing`, `sequence`, and `collection`; construct a `FieldValue` directly.
Null and missing remain distinct.

A successful execution returns a named `CollectionValue` containing every binding in the script's
top lexical layer. Java can invoke a returned `CaretCallable` directly or through `sandbox.invoke`:

```java
CaretCallable function = (CaretCallable) result.find("transform").orElseThrow();
CaretInvocationResult invocation = function.invoke(List.of(CaretValue.number(21)));
CaretValue answer = invocation.value().orElseThrow();
```

Nested lexical locals are not included, and the embedding API provides no arbitrary lexical lookup.
Ordinary immutable `CaretValue` data can be passed to another sandbox. Program and callable handles
remain owned by the sandbox that created them.

## Supply host values and callbacks

`CaretEnvironment` is immutable. Environment values are evaluated lazily at most once per
environment snapshot and remain cached even when the Caret operation that first reads them fails.
Callbacks have fixed arity and explicit observable effects:

```java
CaretEnvironment environment = CaretEnvironment.builder()
        .value("hostName", () -> CaretValue.text("example-host"))
        .callback("twice", 1, Set.of(), arguments -> {
            double value = ((CaretValue.NumberValue) arguments.getFirst()).value();
            return CaretValue.number(value * 2);
        })
        .enablePrint()
        .build();
```

An effect declaration describes behavior but grants no authority. Every callback effect must also
be allowed by the environment. `enablePrint()` explicitly exposes host-routed output and allows the
standard `Output` effect. Output goes only to the `PrintStream` supplied to the sandbox builder.
An environment swap cannot redirect that destination, and output already written by a failed Caret
operation is not rolled back.

`enableCallbackRegistration()` lets the script export selected Caret functions through its
`registerCallbacks` environment binding. Retrieve the committed immutable registry with
`sandbox.registeredCallbacks()`. The last registration in a successful execution or invocation
atomically replaces the complete registry; a failed operation preserves the previous snapshot.

`sandbox.swapEnvironment(replacement)` atomically replaces host values and callbacks for later
calls, discards the previous value cache, and preserves sandbox and callable identity. Existing
returned Caret callables resolve the replacement environment lazily. Removed bindings fail instead
of retaining their previous authority. A replacement callback with an existing name must preserve
its arity.

## Handle failures

Caret lexer, parser, semantic, authority, callback, and runtime failures return an operation result
whose `diagnostics()` contain a stable code, phase, message, source location, and related notes:

```java
if (result.code() == CaretOperationResult.Code.FAILURE) {
    for (CaretDiagnostic diagnostic : result.diagnostics()) {
        CaretDiagnostic.Location at = diagnostic.location();
        if (at == null) {
            System.err.printf("%s: %s%n", diagnostic.code(), diagnostic.message());
        } else {
            System.err.printf("%s:%d:%d: %s%n",
                    at.sourceName(), at.startLine(), at.startColumn(), diagnostic.message());
        }
    }
}
```

Invalid Java-side lifecycle or argument use throws `CaretEmbeddingException` with a stable code.
Current sandbox operations emit `ALREADY_LOADED`, `HANDLE_CONSUMED`, `BUSY`, `CLOSED`,
`FOREIGN_HANDLE`, `INVALID_ARGUMENT`, and `INVALID_ARITY`. The public `STALE_HANDLE` member is not
emitted by the current single-script sandbox.
An ordinary Java callback exception becomes a sanitized Caret runtime diagnostic; its Java cause
does not become a Caret value. A fatal JVM `Error` escapes and invalidates the sandbox.

## Lifecycle and concurrency

- Close every sandbox, preferably with try-with-resources. Closing is idempotent and invalidates its
  program and callable handles.
- A sandbox is deliberately not thread-safe. Concurrent or re-entrant use fails with
  `CaretEmbeddingException.Code.BUSY` rather than corrupting state.
- Independent sandboxes are isolated and may execute concurrently.
- Failed execution and invocation attempts roll back Caret mutations and callback-registry changes;
  completed host callbacks, output, and resolved host-value cache entries remain observable.
- Immutable public values may cross between sandboxes. Loaded-program and callable handles may not.

See the generated Javadocs under `docs/javadoc/`, the complete
[`EmbeddingExample.java`](https://github.com/kerabromsmu/caret-lang/blob/main/src/main/java/caretlang/examples/EmbeddingExample.java),
and the canonical
[Java embedding sandbox specification](https://github.com/kerabromsmu/caret-lang/blob/main/spec/13-sandboxes-and-security.md#java-embedding)
for the full contract and security boundary.
