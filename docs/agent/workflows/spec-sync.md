# Spec Sync workflow

### `spec sync`

* Compare the canonical `spec/` corpus with implemented behavior, tests, examples, clarified GitHub
  issues, `LANGUAGE.md`, `PLAN.md`, `CONFORMANCE.md`, `DIAGNOSTICS.md`, `README.md`, and
  `WEB_INTRODUCTION.md`.
* Repair factual and documentation drift when intent is unambiguous. Ask before resolving conflicts
  between observable language behaviors; neither current code nor a GitHub card silently overrides
  canonical semantics.
* Update the owning `spec/` document first. Use `LANGUAGE.md` only for navigation, global invariants,
  or shared terminology, and update other documentation and conformance records as applicable.
* Do not change interpreter behavior under this alias.
* Run documentation and corpus checks, plus the full baseline suites when the affected documentation
  is covered by them.
