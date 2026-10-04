# Phase 0 — Specification and conformance baseline (completed)

[Roadmap index](../PLAN.md)

## Phase 0 — Specification and conformance baseline (completed)

- `CONFORMANCE.md` inventories implemented, planned, deferred, and unresolved requirements with
  stable IDs and automated evidence.
- Characterization tests cover the implemented precedence table, safe primitive failures, and
  stable diagnostic phases/codes/locations alongside the existing core suites.
- The redesigned contract/derivation, functional-dispatch, and universal-collection model is
  reconciled across the public introduction, conformance matrix, and this roadmap.
- Root/module reification, canonical code equivalence, sandbox construction/lifecycle, SIMD
  grouping, general choices, unordered object traversal, and the initial JVM ABI are specified.
- Physical-to-logical layout baseline mappings, stable module-ID declarations, catalog discovery,
  path/ID import resolution, and environment-relative catalog visibility are specified.
- `LANGUAGE.md` records the resolved infix, multiline, function-reference/result-contract,
  persistent state/object, module, bytes, contract, collection, and JVM backend decisions.
- The shared structured-error payload is specified through `ErrorTemplate`, and the parameterized
  `Result` contract defines the public success/failure envelope. Dynamically supplied host
  capabilities remain environment bindings rather than serialized code dependencies.
