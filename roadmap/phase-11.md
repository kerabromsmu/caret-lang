# Phase 11 — Compile-time execution and separate compilation

[Roadmap index](../PLAN.md)

## Phase 11 — Compile-time execution and separate compilation

Target a later release after v1 and the rules release; no exact version is assigned. Retain
module/execution-environment prerequisites and settle the standard compiler-environment interface
before implementation. Add format/rule/Code integrations when their owning features exist; none
makes staging a prerequisite for the v1 runtime.

- Parse binding-form `#` separately and represent expression-form `#` as an explicit staged region
  covering the remainder of its nearest expression boundary. Preserve its complete source span;
  parentheses and other explicit delimiters bound smaller regions, while later operators never
  return to runtime execution.
- Resolve names across stages, reject runtime-only dependencies from compile-time regions, and
  preserve stable diagnostics. Treat redundant nested `#` as valid and ensure split conditionals
  stage both branch values while retaining ordinary laziness for wholly staged conditionals.
- Execute ordinary Caret functions in an explicit compile-time environment using the reference
  evaluator. Enforce inferred effects against the capabilities actually supplied by that environment;
  staging and reflection must never recover omitted host or sandbox authority.
- Apply normal module identity, exports, initialization, and environment-local caching to
  compile-time imports. Track modules and external compile-time inputs as semantic build dependencies
  without automatically including their runtime bodies.
- Lower stage-boundary results into runtime IR: embed portable immutable values, retain semantic
  references required by reifiable executable/code values, and diagnose compiler-only or
  non-portable capability values.
- Compile each requested source root independently. After staging, compute semantic reachability from
  its resulting runtime root, omit discarded definitions, and retain the full dependency closure of
  selected rules, formats, contracts, and helpers.
- Test pure/effectful staging, invalid cross-stage dependencies, import visibility, target-specific
  rule filtering, shared dependency retention, discarded dependency removal, reproducible dependency
  manifests, and adversarial authority/reflection boundaries.
