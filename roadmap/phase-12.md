# Phase 12 — Compiler backend and optimization

[Roadmap index](../PLAN.md)

## Phase 12 — Compiler backend and optimization

Target a later release after v1 and the rules release; no exact version is assigned. Java 21 and
the tree-walking interpreter remain the reference for earlier releases. Backend/ABI support and
differential parity become completion gates here, not prerequisites for v1 or ordinary rules.

### Compiler backend

- Define a lowered typed/effect-checked IR shared with the interpreter. Preserve source maps and
  diagnostic spans through closure conversion, pattern/conditional lowering, cycles, formats, SIMD,
  and rule scheduling.
- Implement Java 21-compatible bytecode behind opaque generated class names, a documented embedding
  facade, and a versioned runtime ABI that rejects incompatible artifacts and requires recompilation.
  Keep the semantic module/interface model backend-independent for future non-JVM and self-hosted
  implementations. Cover all Caret value kinds, module linking, and executable CLI commands.
  Compiled and interpreted programs must share observable
  values, evaluation order, missing/null behavior, errors, effects, environment-relative reflection,
  code visibility, container identity/aliasing, and sandbox authority boundaries.
- Add differential tests that run every conformance example in both modes and compare stdout,
  stderr, exit status, values, and stable diagnostic codes/locations.

### Optimization

- Add constant folding only for proven-pure operations; then dead-code elimination, call
  specialization/inlining, closure/partial specialization, persistent-update elision, tail-call and
  cycle lowering, format specialization, SIMD lowering, and rule dependency indexing.
- Every optimization has an off switch and differential/property tests. It must not expose source
  order for unordered rules, merge null with missing, execute an unselected conditional branch, or
  change effect/contract behavior.
