# Caret language prototype

Caret is an experimental concise programming language implemented as a Java 21 tree-walking
interpreter. The current prototype supports:

- finite numbers, strings, Booleans, null (`?`), missing (`~`), and name values (`#name`);
- indentation-delimited functions, lexical closures, direct and mutual recursion;
- whitespace application (`add 2 3`) with application binding more tightly than infix operators;
- lazy conditionals (`condition & yes ! no`) and short-circuiting `and`/`or`;
- exported immutable scopes, required/optional field access, and dynamic lookup;
- arbitrary partial application with ordinary and numbered holes;
- basic language-owned reflection through `@value`;
- Unicode code-point text operations; and
- persistent sequences and insertion-ordered dictionaries with structural equality.

This is deliberately a language experiment, not a production compiler. [LANGUAGE.md](LANGUAGE.md)
describes both the implemented language sketch and the larger planned language. Features described
there as planned are not necessarily available in this prototype. [PLAN.md](PLAN.md) gives the
implementation roadmap, and [CONFORMANCE.md](CONFORMANCE.md) maps specification requirements to
their implementation status and automated evidence.

See [`examples/implemented_features.caret`](examples/implemented_features.caret) for a runnable
program demonstrating every feature currently supported by the prototype.

## Requirements

- Java 21
- A POSIX-compatible shell for the provided launchers

The Gradle wrapper downloads the required Gradle distribution and dependencies on first use.

## Run a program

The project launcher builds the distribution and runs a Caret source file:

```bash
./run.sh examples/implemented_features.caret
```

With no arguments, it runs `examples/demo.caret`:

```bash
./run.sh
```

With Gradle:

```bash
./gradlew run --args='examples/demo.caret'
```

Language errors are written to standard error and return a nonzero process status.
Missing or unreadable source files are reported as ordinary CLI errors without Java stack traces.

## REPL

Start the REPL:

```bash
./repl.sh
```

This launcher builds the application and then replaces itself with the interpreter, so there is no
Gradle progress display around the interactive session. Up/Down browse previously entered lines,
including commands saved across sessions in `~/.caret_history`. Consecutive duplicates, blank lines,
and `exit` are not saved, and history is limited to 1,000 entries.

Enter one-line expressions or assignments and type `exit` (or press Ctrl-D) to leave. Ctrl-C cancels
the current input and opens a fresh prompt. Bindings remain available for the rest of the session.
The REPL does not yet accept multiline function definitions or other multiline input.

Do not launch the interactive REPL with `./gradlew run`: Gradle forwards ordinary input but does not
give the Java child process ownership of the terminal, so terminal editing and arrow keys cannot
work. Gradle's `run --args='path.caret'` form remains available for non-interactive file execution.

## Tests

Run the JUnit suite and compatibility/integration suite:

```bash
./gradlew test
./test.sh
```

The Gradle task runs lexer, parser, interpreter, REPL, and CLI tests. `test.sh` executes
representative Caret programs, checks their output, runs the Caret-native suites, and verifies a
failure diagnostic.

### Caret-native test files

Run a single Caret test file with the `test` subcommand:

```bash
./run.sh test examples/testing.caret
```

[`examples/implemented_features_test.caret`](examples/implemented_features_test.caret) is the
comprehensive Caret-native suite for behavior implemented by the prototype.

Test files use ordinary function-call syntax:

```caret
assert "addition succeeds" (add 2 3 == 5)
assertEqual "addition result" (add 2 3) 5
```

`assert` requires a Boolean condition. `assertEqual` compares values using Caret's normal
structural equality. Assertion mismatches are collected, reported with their source locations, and
produce a nonzero exit status after the summary. A test file with no assertions also fails.

Assertion arguments are evaluated eagerly. A lexer, parser, or runtime error while evaluating a
test aborts the file immediately; isolated test bodies and expected-error assertions are not yet
supported. The assertion functions are available only through the `test` subcommand.

## Language example

```text
add a b = a + b

between low value high =
  value >= low and value <= high

inside = between 0 _ 10
print (inside 7)

makeA n =
  hidden = n * 2
  ^name = "A"
  ^count = hidden

makeB =
  ^name = "B"
  ^enabled = true

source = true & makeA 5 ! makeB
print source.name
print source.count~
print source.enabled~
```

`print` consumes the remainder of its logical line as one expression, so `print add 2 3` prints
the result of `add 2 3`. Parenthesized output remains valid.

## Current limitations

- A function definition must start at the beginning of a logical line.
- Grouped expressions, dynamic lookups, and more-indented ungrouped call arguments may span lines.
  Trailing callable blocks remain unavailable until lambda syntax is implemented.
- Values are dynamically checked; contracts, static types, nullable/optional type checking, and
  effect inference are not implemented.
- General collection/data literals, first-class fields, formats, lambdas, cycles, SIMD, rules,
  rulesets, and rule cycles are not implemented.
- There is no mutation, immutable scope-update syntax, object model, module system, compiler
  backend, bytecode, or optimizer.
- Reflection is intentionally limited to basic kind, size/name, and function-arity metadata.

## Diagnostics

Lexer, parser, and runtime errors report one-based line and column locations. Source spans use raw
character columns; a tab counts as one source character in diagnostics while leading tabs retain the
prototype's two-space indentation width.

For example:

```text
Error: Line 1, column 7: Unknown name: absent
```

Line comments start with `//`. A leading `#` is not a comment marker: `#count` is a name value even
when it appears at the beginning of a line.

## Built-ins

The ordinary runtime provides:

- `print value` and `type value`;
- `textSize`, `textAt`, `textSlice`, `textNumber`, and `numberText`;
- `seqEmpty`, `seqAdd`, `seqGet`, and `seqSize`; and
- `dictEmpty`, `dictPut`, `dictGet`, `dictHas`, and `dictKeys`.

Invalid text indexes, sequence indexes, slices, and numeric text conversions return `~`. Dictionary
keys may be strings or name values; `dictHas` distinguishes an absent key from a present key whose
value is `~`.

## Reflection currently implemented

```text
field = #count
print source[field]~
print (@source).kind
print (@source).names
```

`@scope`, `@sequence`, and `@dictionary` expose basic metadata such as `kind`, `size`, and, where
applicable, `names`. `@function` exposes `kind` and remaining arity. Reflection exposes only public
or exported bindings and does not invoke a reflected function.
