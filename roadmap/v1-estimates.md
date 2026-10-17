# V1 effort estimates

[Release roadmap](../PLAN.md#release-targets-and-implementation-order) · [V1 scope](../LANGUAGE.md#v1-feature-work)

Estimate date: **2026-10-16**.

This is the original planning snapshot, prepared before template sugar (#61) was implemented.
That feature has since completed and is no longer remaining work; its **2–4 day** estimate and the
original totals are retained here as historical estimates. See the
[current roadmap](../PLAN.md#recommended-next-implementation-step) for implementation status.

## Basis and assumptions

At the time of this snapshot, the remaining v1 work was estimated at roughly **90–175 engineer-days**,
or **18–35 full-time working weeks**. These are planning estimates for one experienced developer
familiar with the Java interpreter, not delivery commitments. Time waiting for design decisions
is excluded.

Implementation estimates include focused tests, representative runnable examples, diagnostics,
and canonical/public documentation. The existing Phases 0–4 implementation is the compatibility
baseline and is not counted again. Design discussions may introduce additional scope; the
roadmap and owning specifications remain authoritative for release allocation and behavior.

## Specification work

| Task | Main deliverable | Effort |
|---|---|---:|
| **Integer shifts — [#53](https://github.com/kerabromsmu/caret-lang/issues/53)** | Settle contracts, negative right shifts, shift counts and ordinary callable behavior. Rotations remain deferred. | **1–3 days** |
| **Tail calls — [#57](https://github.com/kerabromsmu/caret-lang/issues/57)** | Settle guarantees, tail positions, callable coverage, contract checks, depth diagnostics and execution boundaries. | **3–7 days** |
| **Seeded randomness — [#110](https://github.com/kerabromsmu/caret-lang/issues/110)** | Define the small API, seed/output contracts, algorithm, reproducibility, state and effects. | **1–3 days** |
| **File/console I/O — [#109](https://github.com/kerabromsmu/caret-lang/issues/109)** | Define pipelines, nullary inputs, explicit Result handling, EOF, resource cleanup, effects and capabilities. | **3–7 days** |
| **Specification subtotal** | | **8–20 days** |

## Implementation, proof and release work

| Task | Main work and dependencies | Effort |
|---|---|---:|
| **Template sugar — [#61](https://github.com/kerabromsmu/caret-lang/issues/61)** | Lexer/parser support preserving ordinary template application, shadowing, holes and source locations. Independent of the main sequence; completed since this snapshot. | **2–4 days** |
| **Multiline REPL — [#81](https://github.com/kerabromsmu/caret-lang/issues/81) / 13A** | Grammar-owned completion detection, shared submission handling, layout mappings, EOF, cancellation and history. Ready independently. | **5–10 days** |
| **Ordinary cycles — Phase 5** | Four-argument callable, `identity`, previous/next lookup, stable state contracts, atomic phase commits, effects and nesting. | **8–15 days** |
| **Modules — Phase 9A** | Module declarations, catalogs, path/ID imports, exports/privacy, initialization, caching and cycle diagnostics. | **10–20 days** |
| **Execution environments — Phase 9B** | Carry explicit environment context through files, imports, closures, REPL, tests and embedding. Builds on module integration. | **8–15 days** |
| **Root/module metadata, including [#5](https://github.com/kerabromsmu/caret-lang/issues/5) — Phase 9B** | Metadata-only `@root`/`@module`, identity and deterministic visible identifiers. Sandbox visibility testing completes with 10A. | **4–8 days** |
| **Shared `Result T` — Phase 9B** | Parameterized contract, exact success/failure shape and validation, reusing `ErrorTemplate`. Needed by I/O and sandboxes. | **2–4 days** |
| **Nullary callable/composition support — part of #109** | Implement the eventual input-callable design, including storage/passing and composition without accidental execution. Requires the I/O design decision. | **4–8 days** |
| **File/console I/O implementation — remainder of #109** | UTF-8/binary reads, replacement/append, console operations, explicit failures and resource cleanup. Follows 9B and the callable foundation above. | **7–12 days** |
| **Seeded randomness implementation — #110** | Implement the selected minimal generator, deterministic sequences and state/effect behavior. Requires its specification first. | **3–6 days** |
| **Initial sandboxes — Phase 10A** | Fixed environments, restricted imports, capability mediation, shared containers, nested authority, retained-reference invalidation and terminate/unload. Includes threat-model and adversarial testing. | **20–35 days** |
| **Turing-completeness proof/examples — [#80](https://github.com/kerabromsmu/caret-lang/issues/80)** | Choose the proof construction, write supporting Caret programs and explain the argument. Best completed once the required runtime foundation exists. | **3–6 days** |
| **Final v1 integration and release preparation** | Combined-feature regressions, conformance audit, packaging/launcher checks, documentation consistency and release checklist. | **5–10 days** |
| **Implementation/proof/release subtotal** | | **81–153 days** |

The final integration allowance covers combined behavior; ordinary per-feature testing is already
included in each row. #5 is included in root/module metadata rather than estimated again as a
separate feature. The two #109 implementation rows cover distinct work and together account for
the callable foundation and the I/O operations.

## Dependencies and optional scope

The established main sequence remains:

**Cycles → modules → environments/metadata/Results → file/console I/O → sandboxes**

Template sugar, multiline REPL and the specification discussions can proceed independently of
that sequence. Seeded randomness follows its own design work, with environment/sandbox integration
where applicable.

#53 and #57 currently require specification work for v1. If their runtime implementations are
subsequently included, tentatively add **2–4 days for basic shifts** and **10–25 days for tail calls**,
subject to the chosen guarantees. These optional implementation estimates are excluded from the
subtotals and total above.

Gated computations (#60) are excluded because their v1 inclusion remains undecided. The full
generalized I/O (#99) and randomness (#98) systems, rotations/width-dependent extensions,
self-interpreter, semantic Code/serialization, sandbox swaps/reload and richer projections, rules,
SIMD, formats, staging, compiler backend and remaining broad tooling stay outside this estimate.
Self-interpreter comparison, compiled parity and the generated documentation site are not v1 gates.

## Total and uncertainty

The task ranges sum to **89–173 engineer-days**, rounded to **90–175 days** for planning. Carry a
**20–30% reserve** while the I/O, randomness and tail-call designs remain open. The largest
uncertainty is the sandbox boundary and its interaction with modules, closures, reflection and
retained references. Revisit the estimates after the specification prerequisites are settled.
