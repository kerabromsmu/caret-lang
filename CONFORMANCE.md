# Caret language conformance matrix

This matrix tracks the normative requirements in the [language specification index](LANGUAGE.md)
and its linked canonical feature documents. A feature is
`implemented` only when the current Java prototype supports it and the row names automated test and
runnable-example evidence. `planned` behavior is specified but unavailable, `deferred` behavior is
explicitly postponed by the specification, and `unresolved` behavior still needs a language-design
decision.

Requirement IDs are permanent. Rows may change status, evidence, or wording, but an ID must not be
reused for another behavior.

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
- [Contracts](conformance/contracts.md) — 34 requirements
- [Collections](conformance/collections.md) — 46 requirements
- [Numeric Formats](conformance/numeric-formats.md) — 26 requirements
- [Cycles Rules](conformance/cycles-rules.md) — 28 requirements
- [Modules Security](conformance/modules-security.md) — 30 requirements
- [Staging Tools](conformance/staging-tools.md) — 21 requirements
