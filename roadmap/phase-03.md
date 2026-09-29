# Phase 3 — Lambdas and higher-order programming (completed)

[Roadmap index](../PLAN.md)

## Phase 3 — Lambdas and higher-order programming (completed)

Current status: Phase 3 is complete. Lambdas share the ordinary callable representation, lexical
capture metadata, contracts, effects, partial application, composition, reflection, and guarded
higher-order execution. Sequence map/filter/fold/any/all and lambda precedence above `$` have full
runtime and corpus evidence. Proven-pure unary Boolean lambdas share named-predicate refinement
eligibility through direct construction, assignments, lexical captures, aliases, retained metadata,
and direct declaration clauses.

- Preserve implemented `CONTRACT-LAMBDA-REFINE-001` callable parity, including contract/alias,
  rejection, diagnostic, retained-submission, reflection, and runnable integration evidence.
- Parse unary/multi-parameter lambdas, contracted parameters, expression bodies, and indented bodies
  with the precedence/extent rules settled in Phase 0.
- Lower lambdas to the same function representation as named functions. Implement lexical capture,
  capture timing, arity, nullary behavior, return values, reflection/reification, and higher-order
  calls.
- Support ordinary partial application and hole-based partial application around lambdas without
  conflating holes with parameter declarations.
- Infer contracts, purity, effects, and later SIMD eligibility exactly as for named functions.
- Implement composition and standard higher-order collection functions (`map`, `filter`, `fold`,
  `any`, `all`) using the unified callable/effect model.
- Complete `LAMBDA-LOWAPP-001`: lambda construction binds above `$`, with parser and runtime
  coverage for ungrouped lambdas used as complete low-precedence arguments.

