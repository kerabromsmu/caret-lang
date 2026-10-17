# V1 effort estimates

[Release roadmap](../PLAN.md#release-targets-and-implementation-order) · [V1 scope](../LANGUAGE.md#v1-feature-work)

Estimate date: **2026-10-17** (updated from the 2026-10-16 snapshot).

The remaining v1 work totals **78–153 engineer-days**, or roughly **16–31 full-time working weeks**,
before reserve. This includes basic integer-shift implementation (#53) and reduced direct self-tail
recursion implementation (#57). Completed template sugar (#61) is excluded from remaining work.

## Basis and assumptions

These are planning estimates for one experienced developer familiar with the Java interpreter,
not delivery commitments. Time waiting for design decisions is excluded.

Implementation estimates include focused tests, representative runnable examples, diagnostics,
and canonical/public documentation. The existing Phases 0–4 implementation is the compatibility
baseline and is not counted again. Design discussions may introduce additional scope; the
roadmap and owning specifications remain authoritative for release allocation and behavior.

## Specification work

| Task | Main deliverable | Effort |
|---|---|---:|
| **Integer shifts — [#53](https://github.com/kerabromsmu/caret-lang/issues/53)** | Settle contracts, negative right shifts, shift counts and ordinary callable behavior. Rotations remain deferred. | **1–3 days** |
| **Direct self-tail recursion — [#57](https://github.com/kerabromsmu/caret-lang/issues/57)** | Settle eligible self calls and tail positions, contract checks, depth diagnostics and applicable execution boundaries. | **1–2 days** |
| **Seeded randomness — [#110](https://github.com/kerabromsmu/caret-lang/issues/110)** | Define the small API, seed/output contracts, algorithm, reproducibility, state and effects. | **1–3 days** |
| **File/console I/O — [#109](https://github.com/kerabromsmu/caret-lang/issues/109)** | Define pipelines, nullary inputs, explicit Result handling, EOF, resource cleanup, effects and capabilities. | **3–7 days** |
| **Specification subtotal** | | **6–15 days** |

## Implementation, proof and release work

| Task | Main work and dependencies | Effort |
|---|---|---:|
| **Basic integer shifts — [#53](https://github.com/kerabromsmu/caret-lang/issues/53)** | Implement settled `shl`/`shr` semantics and ordinary callable behavior, including edge cases and diagnostics. Rotations and width-dependent extensions excluded. Requires its specification first. | **2–4 days** |
| **Direct self-tail recursion — [#57](https://github.com/kerabromsmu/caret-lang/issues/57)** | Implement reduced self-tail recursion in ordinary function bodies and conditional result branches, preserving contracts/effects and other calls' existing depth behavior. Requires its specification first. Full language-wide coverage excluded. | **3–6 days** |
| **Multiline REPL — [#81](https://github.com/kerabromsmu/caret-lang/issues/81) / 13A** | Grammar-owned completion detection, shared submission handling, layout mappings, EOF, cancellation and history. Ready independently. | **5–10 days** |
| **Ordinary cycles — Phase 5** | Four-argument callable, `identity`, previous/next lookup, stable state contracts, atomic phase commits, effects and nesting. | **8–15 days** |
| **Path modules — [#114](https://github.com/kerabromsmu/caret-lang/issues/114) / Phase 9A** | Relative-path imports, exports/privacy, canonical-path initialization/cache identity, failed loads and cycle diagnostics. Module IDs/catalog discovery (#115) are post-v1. | **6–12 days** |
| **Execution environments — [#116](https://github.com/kerabromsmu/caret-lang/issues/116) / Phase 9B** | Carry explicit environment context through files, imports, closures, REPL, tests and embedding. Builds on module integration. | **8–15 days** |
| **Root/module metadata, including [#5](https://github.com/kerabromsmu/caret-lang/issues/5) — Phase 9B** | Metadata-only `@root`/`@module`, identity and deterministic visible identifiers. Sandbox visibility testing completes with 10A. | **4–8 days** |
| **Shared `Result T` — Phase 9B** | Parameterized contract, exact success/failure shape and validation, reusing `ErrorTemplate`. Needed by I/O and sandboxes. | **2–4 days** |
| **Nullary callable/composition support — part of #109** | Implement the eventual input-callable design, including storage/passing and composition without accidental execution. Requires the I/O design decision. | **4–8 days** |
| **File/console I/O implementation — remainder of #109** | UTF-8/binary reads, replacement/append, console operations, explicit failures and resource cleanup. Follows 9B and the callable foundation above. | **7–12 days** |
| **Seeded randomness implementation — #110** | Implement the selected minimal generator, deterministic sequences and state/effect behavior. Requires its specification first. | **3–6 days** |
| **Restricted sandboxes — [#117](https://github.com/kerabromsmu/caret-lang/issues/117) / Phase 10A** | Fixed environments, authorized paths, immutable data/mediated calls, effects/reflection, retained-reference invalidation and terminate/unload. Includes threat model, adversarial tests and rejection of shared containers/nesting; their support (#118) is post-v1. | **12–22 days** |
| **Turing-completeness proof/examples — [#80](https://github.com/kerabromsmu/caret-lang/issues/80)** | Choose the proof construction, write supporting Caret programs and explain the argument. Best completed once the required runtime foundation exists. | **3–6 days** |
| **Final v1 integration and release preparation** | Combined-feature regressions, conformance audit, packaging/launcher checks, documentation consistency and release checklist. | **5–10 days** |
| **Implementation/proof/release subtotal** | | **72–138 days** |

The final integration allowance covers combined behavior; ordinary per-feature testing is already
included in each row. #5 is included in root/module metadata rather than estimated again as a
separate feature. The two #109 implementation rows cover distinct work and together account for
the callable foundation and the I/O operations.

## Dependencies and optional scope

The established main sequence remains:

**Cycles → modules → environments/metadata/Results → file/console I/O → sandboxes**

Template sugar is complete. Multiline REPL, basic shifts, direct self-tail recursion and the
specification discussions can proceed independently of
that sequence. Seeded randomness follows its own design work, with environment/sandbox integration
where applicable.

#53 and #57 both require specification and implementation for v1. Basic shifts cost **3–7 days**
including specification (1–3) and implementation (2–4). Reduced direct self-tail recursion costs
**4–8 days** including specification (1–2) and implementation (3–6). Both are included in the tables
and total above; neither adds a prerequisite to the main phase sequence.

[Full language-wide tail calls (#113)](https://github.com/kerabromsmu/caret-lang/issues/113)
remain post-v1. The earlier full-scope estimate of **3–7 days for specification** and **10–25 days
for implementation** is excluded; re-estimate incremental work after #57 rather than adding that
full estimate to the reduced implementation.

Gated computations (#60) are excluded because their v1 inclusion remains undecided. The full
generalized I/O (#99) and randomness (#98) systems, rotations/width-dependent extensions,
self-interpreter, semantic Code/serialization, sandbox swaps/reload and richer projections, rules,
SIMD, formats, staging, compiler backend and remaining broad tooling stay outside this estimate.
Self-interpreter comparison, compiled parity and the generated documentation site are not v1 gates.

## Total and uncertainty

The task ranges sum to **78–153 engineer-days**, before reserve. Carry a
**20–30% reserve** while the I/O, randomness, shift and reduced tail-call designs remain open.
The largest uncertainty is the sandbox boundary and its interaction with modules, closures,
reflection and retained references. Revisit estimates after specification prerequisites are settled.

## Revision history

The 2026-10-16 snapshot totaled **89–173 days** (rounded to 90–175). It included the now-completed
#61 (2–4 days) and full tail-call specification (3–7 days), but excluded #53/#57 implementations.
The earlier #53/#57 revision removed #61, replaced full tail-call specification with reduced specification
(1–2 days), and included basic-shift implementation (2–4 days) and reduced tail-call implementation
(3–6 days): **89–173 − 2–4 − 3–7 + 1–2 + 2–4 + 3–6 = 90–174 days**.
These are corresponding low/high endpoint adjustments, not interval subtraction. The original
basic-shift specification allowance (1–3 days) remains included.

### Reduced modules and sandboxes — 2026-10-17

Replace full modules (10–20 days) with path modules #114 (6–12) and initial sandboxes (20–35)
with restricted sandboxes #117 (12–22). This reduces corresponding low/high endpoints by
12 and 21 days: **90–174 becomes 78–153 engineer-days**, with specification unchanged at 6–15
and implementation/proof/release at 72–138. #53 and reduced #57 implementation remain included.

Execution environments #116 retain their 8–15 day scope, split into context/state ownership
(3–5), propagation through imports/closures/invocation (3–6), and entry-point integration (2–4).
Root/module metadata and shared Result retain separate estimates. These milestones are subdivisions,
not extra allowances.

Module IDs/catalogs #115 and shared-container/nested sandbox support #118 are post-v1, exact release
unassigned. Re-estimate their incremental work after the subsets; the original full ranges are
historical comparisons, not additional estimates. Sandbox estimates remain provisional until
supported boundary interfaces are settled; retain the 20–30% reserve and all isolation guarantees.
