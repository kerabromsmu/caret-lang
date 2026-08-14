# Caret language prototype

Caret is an experimental concise programming language implemented as a Java 21 tree-walking
interpreter. The current prototype supports:

- finite numbers, strings, Booleans, null (`?`), and missing (`~`);
- indentation-delimited functions, lexical closures, direct and mutual recursion;
- whitespace application (`add 2 3`) with application binding more tightly than infix operators;
- fixed-precedence named binary infix calls (`2 add 3`) through the ordinary callable model;
- left-to-right function composition (`parse >> validate`) with partial application;
- lazy conditionals (`condition & yes ! no`) and short-circuiting `and`/`or`;
- exported immutable scopes, required/optional field access, and dynamic lookup;
- arbitrary partial application with ordinary and numbered holes;
- basic language-owned reflection through `@value`;
- Unicode code-point text operations;
- persistent sequences and insertion-ordered dictionaries with structural equality; and
- first-class built-in and user-defined derived contracts, predicate membership calls, and
  contract-checked bindings, parameters, and function results.

This is deliberately a language experiment, not a production compiler. [LANGUAGE.md](LANGUAGE.md)
describes both the implemented language sketch and the larger planned language. Features described
there as planned are not necessarily available in this prototype. [PLAN.md](PLAN.md) gives the
implementation roadmap, and [CONFORMANCE.md](CONFORMANCE.md) maps specification requirements to
their implementation status and automated evidence. [DIAGNOSTICS.md](DIAGNOSTICS.md) inventories
every current diagnostic message variant and its exact fixture or focused test evidence.

The planned language uses one contract system for types, interfaces, refinements, and capabilities.
Contracts form derivation graphs and act as predicates, while ordinary functions provide behavior
through contract-based multiple dispatch. Collections likewise have one universal `[...]` literal:
surrounding contracts determine whether a value is a list, set, dictionary, packed buffer, or another
representation. Named fields are first-class collection elements. These facilities are design
targets, not features of the current prototype. A collection expression containing holes will be an
ordinary function whose parameters complete that collection. Passing such a reifiable constructor,
or a concrete fixed collection, to the planned `template` function creates an exact structural
contract. The same mechanism defines a standard structured error payload, while a generic
three-field `Result` contract supplies the planned public success/failure envelope.

Explicit mutability is planned through stable-identity containers rather than mutable bindings or
deeply mutable objects. `{ (Int) 100 }` constructs a container, `container{}` reads its current
content, and `put container value` performs a contract-checked replacement. Containers can be
shared through otherwise immutable fields and collections; reads and writes participate in the
planned effect system. This syntax is specified but not implemented by the current prototype.

Right-associative `$` supplies application below composition, conditionals, and ordinary expressions
(`print $ calculate value`). Planned `with value` expressions will make a value's public named
members available for lexical lookup without copying them, while resolver-only paths such as
`outer.name` recover shadowed enclosing names without exposing lexical environments as values.
`with` and `outer` are specified but not implemented by the current prototype.

The specification also plans environment-relative reflection through `@root`. A program will be
able to inspect a visibility-filtered, structured representation of its code and serialize that code
to canonical Caret syntax. Sandboxes will substitute a smaller visible root and expose only selected
libraries and capabilities; effect declarations will not grant authority. The concrete root metadata,
serialization rules, and sandbox syntax remain open design work and are not implemented.

Compile-time execution is planned through `#` rather than a separate macro language. `# name =
expression` creates a compile-time-only binding, while `name = # expression` incorporates a staged
result into the runtime program. Different source roots may stage the same shared modules differently
and produce separate artifacts; runtime inclusion is determined by reachability after staging. `#`
stages the remainder of its current expression boundary rather than participating in ordinary
operator precedence. The standard compiler environment remains unresolved, and no staging support
is implemented yet.

After the language and conformance roadmap is complete, the project plans to generate a searchable
MkDocs Material learning site from `LANGUAGE.md`, split into approachable Markdown pages with a
left-hand table of contents. The same release-hardening work will produce a runnable, implemented-only
“Learn Caret in Y Minutes” tutorial and an upstream-ready contribution artifact.

See [`examples/implemented_features.caret`](examples/implemented_features.caret) for a runnable
program demonstrating every feature currently supported by the prototype.
[`examples/contracts.caret`](examples/contracts.caret) demonstrates built-in and user-defined
contracts and derivation.

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

`assert` requires a Boolean condition. `assertEqual` compares values using Caret's recursive
structural equality; callable values cannot be compared even when nested in data. Assertion
mismatches are collected, reported with their source locations, and
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
- Built-in and user-defined derived contracts can check bindings, parameters, and results
  dynamically. Initial named-function constraint inference and the internal purity analysis needed
  to validate future refinement predicates are implemented;
  parameterized contracts, refinements, dispatch, complete static proof,
  nullable/optional type checking, and the public effect system are not implemented.
- Universal collection literals, contract-selected representations, first-class fields, formats,
  lambdas, cycles, SIMD, rules,
  rulesets, and rule cycles are not implemented.
- Mutability containers and immutable scope-update syntax are specified but not implemented. There
  is no object model, module system, compiler backend, bytecode, or optimizer.
- Reflection is intentionally limited to basic kind, size/name, and function-arity metadata.
- Environment-relative metadata-only `@root`/`@module`, semantic code reification, canonical
  quines, and `sandbox source environment` execution are specified but not implemented.
- Compile-time `#` execution, compile-time imports, independent compilation roots, staged
  reachability, and target-specific artifacts are specified but not implemented.

## Diagnostics

Lexer, parser, and runtime errors report one-based line and column locations. Source spans use raw
character columns; a tab counts as one source character in diagnostics while leading tabs retain the
prototype's two-space indentation width. Built-in type errors identify the invalid argument rather
than only the enclosing call.

For example:

```text
Error: Line 1, column 7: Unknown name: absent
```

Line comments start with `//`. Field names represented as data use ordinary strings; Caret has no
separate name-literal syntax.

## Built-ins

The ordinary runtime provides:

- `print value` and `type value`;
- `Any`, `Number`, `String`, `Boolean`, `Null`, `Missing`, `Function`, `Scope`, `Sequence`, and
  `Dictionary` as first-class unary contracts;
- `textSize`, `textAt`, `textSlice`, `textNumber`, and `numberText`;
- `seqEmpty`, `seqAdd`, `seqGet`, and `seqSize`; and
- `dictEmpty`, `dictPut`, `dictGet`, `dictHas`, and `dictKeys`.

Invalid text indexes, sequence indexes, slices, and numeric text conversions return `~`. Dictionary
keys are strings; `dictHas` distinguishes an absent key from a present key whose
value is `~`.

## Reflection currently implemented

```text
field = "count"
print source[field]~
print (@source).kind
print (@source).names
```

`@scope`, `@sequence`, and `@dictionary` expose basic metadata such as `kind`, `size`, and, where
applicable, `names`. `@function` returns a non-callable function reference exposing `kind` and
remaining arity. Both `type (@function)` and `(@function).kind` report `"Function"`. References
compare by target identity. Reflection exposes only public or exported
bindings and does not invoke a reflected function.
