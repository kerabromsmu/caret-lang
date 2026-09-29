# Phase 5 — Self-hosting foundation and ordinary cycles

[Roadmap index](../PLAN.md)

## Phase 5 — Self-hosting foundation and ordinary cycles

### Caret-written interpreter milestone

- Use the existing Unicode text and persistent collection primitives plus recursion, tagged data,
  closures, and located result values to implement a lexer/parser/evaluator subset in Caret.
- Keep the Caret implementation as a conformance client, not a replacement for Java prematurely.
  Run shared fixtures through Java and Caret implementations and compare values/diagnostics.
- Expand the self-interpreter alongside later features only after each feature's Java semantics are
  stable.

### `cycle`

- Implement ordinary four-argument `cycle initial condition body prepare`, with a pure unary
  condition and unary body/prepare transformations. Supply ordinary `identity` for an unused body
  or prepare phase; fewer arguments retain ordinary partial-application behavior. Omission
  shorthand remains a future possibility, not a required cycle grammar.
- Accept named functions, lambdas, partials, and positional or named Collections as phase values. Enforce a
  stable state shape and compatible contracts across iterations.
- Infer phase effects while requiring the condition to remain pure. Lower to an internal loop/tail
  recursion without mandatory immutable copying, preserving functional observable semantics.
- Support nesting, collection traversal helpers, format use, and final-state return. Keep `Break` /
  `Continue`, changing shapes, labels, effectful conditions, and automatic parallelism deferred as
  `LANGUAGE.md` allows.

