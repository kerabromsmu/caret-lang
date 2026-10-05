# Phase 13 — Tooling, standard library, and release hardening

[Roadmap index](../PLAN.md)

## Phase 13 — Tooling, standard library, and release hardening

The remaining work in this phase is post-v1. Existing launchers, releases, inspection, testing,
embedding, and REPL behavior remain part of the compatibility baseline. V1 still requires complete
tests, runnable feature examples, and accurate documentation for its selected feature set.
The ordinary `identity` callable belongs to Phase 5, and the shared `Result T` contract belongs
to Phase 9B; neither waits for a broad standard-library release. Do not make a compiler backend,
self-interpreter, or generated documentation site a v1 release prerequisite.

- Build standard-library modules for identity, collection transforms/reductions, field manipulation,
  common contracts, formats/codecs, cycle helpers, and reusable rulesets using ordinary Caret where
  feasible.
- Expose formatter/parser services, semantic queries, inferred contracts/effects, reflection
  descriptors, module navigation, and unordered-rule warnings for editor integration.
- Extend the REPL for multiline constructs, modules, type/contract/effect inspection, compiled-mode
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
