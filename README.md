# Caret language prototype

A small tree-walking interpreter exploring these ideas:

- indentation-delimited function bodies
- Haskell-style whitespace application: `add 2 3`
- compact conditional expressions: `condition & yes ! no`
- exported scope bindings: `^name = value`
- nullable literal `?` and missing literal `~`
- optional field lookup: `scope.name~`
- arbitrary partial application with holes: `between 0 _ 10`
- eager partial capture and numbered argument holes: `f _2 fixed _1`
- implicit scope return when a function exports bindings
- name literals and safe dynamic lookup: `scope[#name]~`
- lightweight metadata reflection: `@value`
- Unicode code-point text primitives
- persistent sequences and insertion-ordered dictionaries

This is deliberately a language experiment, not a production compiler.

See [`examples/implemented_features.caret`](examples/implemented_features.caret) for a runnable
program demonstrating every feature currently supported by the prototype.

## Run

With Gradle:

```bash
gradle run --args='examples/demo.caret'
```

Or with Java directly:

```bash
mkdir -p out
javac --release 21 -d out $(find src/main/java -name '*.java')
java -cp out caretlang.Main examples/demo.caret
```

Start the REPL:

```bash
java -cp out caretlang.Main
```

Run the automated tests:

```bash
./gradlew test
./test.sh
```

The Gradle task runs the JUnit lexer, parser, interpreter, and CLI tests. `test.sh` remains as a
compatibility smoke test for representative Caret programs, including `examples/demo.caret`.

## Test Caret programs

Run a single Caret test file with the `test` subcommand:

```bash
java -cp out caretlang.Main test examples/testing.caret
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

## Example

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
- Grouped expressions and dynamic lookups may span lines; ungrouped multiline calls and trailing
  blocks remain unspecified.
- Types are dynamic in this first prototype.
- `?` and `~` are distinct runtime values, but nullable/optional type syntax is not yet checked.
- No mutation, resource ownership, modules, bytecode, or optimizer.

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

## Reflection currently implemented

```text
field = #count
print source[field]~
print (@source).kind
print (@source).names
```

`@scope` currently returns basic metadata (`kind`, `size`, `names`). `@function` returns `kind` and remaining arity. This is intentionally small and will need a richer metadata value model.
