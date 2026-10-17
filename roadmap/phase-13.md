# Phase 13 — Tooling, standard library, and release hardening

[Roadmap index](../PLAN.md)

## Phase 13 — Tooling, standard library, and release hardening

Phase 13A/#81 multiline submission and the separately tracked #80 proof/example work target v1;
the remaining Phase 13B work is post-v1.
Existing launchers, releases, inspection, testing,
embedding, and REPL behavior remain part of the compatibility baseline. V1 still requires complete
tests, runnable feature examples, and accurate documentation for its selected feature set.
The ordinary `identity` callable belongs to Phase 5, and the shared `Result T` contract belongs
to Phase 9B; neither waits for a broad standard-library release. Do not make a compiler backend,
self-interpreter, or generated documentation site a v1 release prerequisite.

The separately scheduled [file/console I/O subset (#109)](v1-io.md) also targets v1 after Phase 9B,
with sandbox integration coordinated with Phase 10A. It does not wait for Phase 13B or completion
of the post-v1 generalized I/O design (#99); its bounded API must be specified before implementation.

Other reviewed v1 work includes [basic seeded generation (#110)](../spec/05-effects-and-callable-signatures.md#planned-v1-seeded-random-generation),
[integer-shift specification and implementation (#53)](../spec/02-values-bindings-and-evaluation.md#planned-v1-integer-shifts),
and [direct self-tail recursion specification and implementation (#57)](../spec/03-functions-operators-and-lambdas.md#planned-v1-tail-calls).
These do not wait for the broad 13B standard-library milestone. Keep their card-level open questions
unresolved until their design discussions; #110 does not complete the broader randomness system #98.

<a id="v1-proof-and-example-work"></a>
### V1 proof and example work (#80)

[Card #80](https://github.com/kerabromsmu/caret-lang/issues/80) calls for one or more Caret programs
that can be used as a proof of Turing completeness. Track this as v1 proof/example work rather than
a new runtime facility or the standalone self-interpreter milestone. The card does not yet select
the proof construction; this allocation supplies no new language guarantee or implementation evidence.

<a id="phase-13a-multiline-repl-submission-v1"></a>
### Phase 13A — Multiline REPL submission (v1, #81)

- Follow the [planned submission rules](../spec/01-source-layout-and-diagnostics.md#planned-multiline-repl-submissions)
  using lexer/layout/parser-owned completion information for both interactive JLine and plain input.
- Execute complete one-line input immediately; accumulate incomplete-but-continuable input without
  evaluation, and report invalid input. Complete grouped constructs only when the full expression
  is complete; close indentation bodies on a valid blank line or effective dedent, retaining the
  next line for a separate submission.
- Preserve effective indentation mappings. Interactive submission waits for restoration; EOF may
  execute complete source with an active mapping under existing file semantics. Diagnose incomplete
  or invalid EOF input without executing a truncated prefix.
- Add continuation prompts, whole-submission Ctrl-C cancellation, correct `exit` handling, physical
  source locations, and multiline history entries while preserving prior bindings and existing
  runtime failure/effect semantics.
- Test both input paths, nested functions/lambdas/groups/Collections, trailing continuations,
  layout stacks, EOF, recovery, paste/history recall, and probe-time absence of effects. Require
  actual REPL integration evidence and a runnable example of the supported multiline source forms.
- This chunk needs no new language expressions, modules, compiler, sandbox/tutorial modes, or
  deferred computations. It does not complete Phase 13B or claim an implementation before evidence.

<a id="phase-13b-broader-tooling-and-release-hardening-post-v1"></a>
### Phase 13B — Broader tooling and release hardening (post-v1)

- Build standard-library modules for identity, collection transforms/reductions, field manipulation,
  common contracts, formats/codecs, cycle helpers, and reusable rulesets using ordinary Caret where
  feasible.
- Expose formatter/parser services, semantic queries, inferred contracts/effects, reflection
  descriptors, module navigation, and unordered-rule warnings for editor integration.
- Extend the REPL beyond the 13A submission foundation for modules, type/contract/effect inspection, compiled-mode
  parity, environment-root inspection, sandboxed sessions, and structured display of collections,
  formats, SIMD, rules, code metadata, and diagnostics.
- Add fuzz/property tests for lexer/parser layout, Unicode, numeric finiteness, persistent
  collections, encode/decode round trips, optimizer equivalence, and scheduler stability.
- Establish performance suites for parsing, closures/partials, collection updates, cycles, SIMD,
  formats, rule propagation, module compilation, code serialization, sandbox startup, and REPL latency.
- After all planned language features and their conformance requirements are complete, publish the
  canonical `LANGUAGE.md` and `spec/` corpus as a developer-learning site. Use MkDocs Material with
  persistent left-pane navigation, search, breadcrumbs, and previous/next links;
  distinguish implemented, planned, deferred, and unresolved material from `CONFORMANCE.md`.
- Generate a coverage manifest mapping every normative specification section to the site, and fail
  strict builds on uncovered sections, duplicate anchors, broken links, missing navigation entries,
  orphan pages, contradictory status, or non-reproducible output. Generated pages are build artifacts,
  not an independently edited specification.
- Author a shared-source “Learn Caret in Y Minutes” tutorial using only implemented behavior and
  runnable examples. Produce both a site page and an upstream-compatible Markdown/YAML contribution,
  with an explicit checklist for metadata, license, formatting, highlighting, links, and submission.
- Release each selected language milestone only after its applicable conformance suite passes on
  supported platforms. Establish and version the compiled runtime ABI when Phase 12 exists; the
  current release numbering follows the release policy in `PLAN.md`.
