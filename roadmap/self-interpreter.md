# Standalone phase — Caret-written self-interpreter

[Roadmap index](../PLAN.md)

## Target and dependencies

Schedule after v1, separately from ordinary cycles and the rules release. No exact release number
is assigned. This is a conformance client, not a prerequisite for v1 or a replacement for Java.
Use stable Java semantics and the available text, Collection, callable, diagnostic/result, and
module foundations; do not require staging or a compiler backend merely to begin this subset.

## Implementation and acceptance

- Implement a lexer/parser/evaluator subset in Caret using Unicode text operations, persistent
  Collections, recursion, tagged data, closures, and located result values.
- Run shared fixtures through Java and Caret implementations and compare values and diagnostics.
  Explicitly document the supported subset and keep unsupported syntax out of completion claims.
- Expand the self-interpreter alongside later features only after their Java semantics are stable.
- Include a runnable `.caret` entry point and exercise it from the integration suite. Keep Java 21
  and the tree-walking interpreter as the reference until differential conformance justifies change.
