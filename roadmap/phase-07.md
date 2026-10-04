# Phase 7 — First-class bidirectional formats

[Roadmap index](../PLAN.md)

## Phase 7 — First-class bidirectional formats

- Add immutable `Format` values representing bidirectional relations. `decode` and `encode` return
  `Result`, with expected failures carrying an `ErrorTemplate` payload. Implement ordinary nullary
  `format : [] -> Format`; bare `format` invokes it under normal nullary rules to produce an empty
  Format. Format constructors and transformations use ordinary callable application.
- Implement primitive byte/integer formats, `field format "name"`, constants/signatures, nested
  formats, fixed/prior-field repetition, conditions, general `selector ==` choices, constraints,
  and `>>` composition.
- Decode structured formats into ordinary collections and encode compatible collections, resolving earlier
  fields and derivable values consistently in both directions.
- Implement `decode`, `encode`, and pure explicit `codec decode encode format`; distinguish
  representation transformations from logical-value transformations.
- Make expected mismatch, incomplete input, invalid field, and codec failure structured rather than
  exceptional. Preserve offsets/path context in an exact format-details template inside the shared
  error payload and in rendered diagnostics.
- Add format reflection for components, names, contracts, directionality, size information where
  known, and tooling/generator use. Keep transport independent from formats.
- Defer general relational solving, arbitrary inversion, nondeterminism/backtracking, streaming,
  zero-copy, and async transport as permitted, without changing the compositional relation model.
