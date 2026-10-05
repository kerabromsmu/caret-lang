# Caret language conformance matrix

This matrix tracks the normative requirements in the [language specification index](LANGUAGE.md)
and its linked canonical feature documents. A feature is
`implemented` only when the current Java prototype supports it and the row names automated test and
runnable-example evidence. `planned` behavior is specified but unavailable, `deferred` behavior is
explicitly postponed by the specification, and `unresolved` behavior still needs a language-design
decision.

Requirement IDs are permanent. Rows may change status, evidence, or wording, but an ID must not be
reused for another behavior.

## Release allocation and partial requirements

[`PLAN.md`](PLAN.md#release-targets-and-implementation-order) owns release allocation, independently
of the statuses here. V1 includes all implemented rows plus #61 template sugar, #81/Phase 13A
multiline REPL submission, ordinary cycles, Phase 9A modules,
Phase 9B environments/metadata and shared `Result T`, and Phase 10A initial sandboxes. The complete
rules/rulesets/objects/`ruleCycle` phase targets the next release, provisionally 1.1. Advanced
module/sandbox work and the remaining Phase 13B and other assigned phases target later releases.
#60 gated computations are to be specified soon in a later discussion, with v1 inclusion undecided;
#57 tail calls remain an early v1 candidate awaiting design. Neither is yet a v1 completion gate.

Existing IDs, statuses, and evidence remain intact. A row combining v1 and later behavior must
remain planned with partial evidence until the whole requirement is implemented. In particular,
metadata plus `.code`, path plus Code sandbox construction, termination/unloading plus reload,
all container projection modes, and effect propagation through future facilities cannot be marked
fully implemented by completing only their v1 portion. Chunk completion audits must identify their
covered behavior explicitly and may add focused requirements without reusing an existing ID.

## Phase 4 completion record

The Phase 4 completion audit (#79) verifies the implemented Collection protocol, contextual
templates, exact numeric domains, explicit conversion, packed layouts, Java embedding carriers,
diagnostics, and optimized/reference parity against the canonical specifications. All Phase 4
implementation cards #64–#78, predecessor design cards #55/#59, and prerequisite cards #63, #82,
and #83 are closed and Done in the Caret project. The linked rows cite executable Java tests and
`.caret` examples; `test.sh` exercises
their golden output and error fixtures. Public element-operation APIs, custom providers,
computations, resumable handlers, callable `eager`, and later compiler/sandbox features remain
deferred or planned in their own rows.


## Requirement groups

- [Core](conformance/core.md) — 55 requirements
- [Callables](conformance/callables.md) — 33 requirements
- [Contracts](conformance/contracts.md) — 35 requirements
- [Collections](conformance/collections.md) — 46 requirements
- [Numeric Formats](conformance/numeric-formats.md) — 26 requirements
- [Cycles Rules](conformance/cycles-rules.md) — 28 requirements
- [Modules Security](conformance/modules-security.md) — 30 requirements
- [Staging Tools](conformance/staging-tools.md) — 22 requirements
