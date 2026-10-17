# Fuzz Testing workflow

### `fuzz testing`

* Run an on-demand, bounded, reproducible campaign. By default, focus on committed branch changes
  from the merge base with current `origin/main`, together with staged and unstaged changes and
  relevant examples. Extra text may narrow or extend that scope. Record the exact baseline and
  reviewed commits and any worktree changes needed to reproduce the tested state.
* Read the relevant canonical specifications, implementation, tests, and examples before selecting
  generators and checks. Build or extend reusable fuzz tooling when needed, using the existing
  JUnit setup and deterministic seeded generation where practical. Do not assume a campaign
  harness already exists or claim that adding this workflow implements one.

### Campaign cards

* Use [#112](https://github.com/kerabromsmu/caret-lang/issues/112) for the initial template-sugar
  lexer/parser campaign and follow its requirements and acceptance criteria.
* Create a new issue-backed Caret project card for every subsequent campaign execution. Search
  existing repository issues and the complete project, using sufficient limits or pagination,
  for the requirement descriptions that apply. Keep earlier campaign issues and cards intact.
* When reusing requirements, the new issue body must contain **only links to the original
  requirement issue or issues**, without copied details or additional prose. Put run-specific
  scope, configuration, evidence, and results in comments. Reuse the original requirement sources
  rather than building a chain of references through earlier execution-only cards.
* When requirements differ, give the new campaign issue its complete requirements and acceptance
  criteria, including workload, checks, limits, preservation, and reporting. Link related campaigns
  where useful; do not silently treat a reduced workload as meeting another card's acceptance.
* Set each new campaign card's project `Issue type` to `Tests` and its initial status to `Todo`.
  Move the selected execution card to `In Progress` when work begins. After required execution,
  reporting, and verification finish, comment with evidence, close the issue, and move its card to
  `Done`. If work or verification remains incomplete, leave it open and in progress and report
  what remains. Confirmed, separately tracked defects do not by themselves prevent campaign
  completion; unmet acceptance criteria do. Do not reopen or move earlier campaign cards for a rerun.

### Execution and preservation

* Generate valid programs, mutate copies of existing feature examples and error fixtures, and
  enumerate short combinations exhaustively within documented bounds. For #112, exercise at least
  10,000 generated valid programs and 50,000 mutations, plus its bounded delimiter/operator
  enumeration. Document the enumeration alphabet and maximum length. Mutations may remain valid
  or become invalid; do not require every mutation to be rejected.
* **Keep canonical examples and golden outputs unchanged.** Retain byte-for-byte original source
  and golden-output copies alongside separately stored mutations. Record each mutation's original
  path, source hash, seed, and transformation. Verify preservation after the run. Do not overwrite
  or discard originals when generating or minimizing mutations.
* Set and record reproducible seeds and explicit input-size, nesting, execution-time, and resource
  limits. Bound execution of generated programs and minimize without allowing hangs or resource
  exhaustion to stall the campaign. Terminate and record individual timed-out cases; report the
  campaign as incomplete if limits or interruptions prevent its required workload or verification.
* Check unexpected Java exceptions, hangs, invalid diagnostic source locations, and strict/recovering
  parser inconsistencies. Check valid generated syntax against its grammar expectations; classify
  generator defects separately from implementation defects. A syntactically valid program may
  still legitimately produce a semantic diagnostic. For mutations, distinguish expected language
  diagnostics from host failures or violated invariants.
* Compare equivalent programs where the specification supplies an oracle. For template sugar,
  compare ordinary template application while preserving lexical shadowing, holes, effects, and
  the distinction from parenthesized contract conversion. Do not invent unresolved semantics.
* Minimize failures while retaining the original example, initial mutation, and minimized
  reproduction with their provenance. Replay against the recorded merge base to distinguish
  regressions from existing defects. Preserve actionable failures as focused regression tests
  or corpus reproductions; do not weaken assertions merely to obtain a passing suite.

### Findings, authorization, and completion

* For each confirmed defect, search existing repository issues and project cards. Reuse a matching
  issue and card, adding the issue to the project if necessary; otherwise create one issue per
  defect with precise locations, impact, reproduction, and remediation guidance. Set its project
  `Issue type` to `Bug`; set new defect cards to `Todo` and preserve existing defect-card statuses.
  Do not create defect cards for coverage risks alone.
* This alias authorizes fuzz tooling, corpus artifacts, tests, campaign issue/project lifecycle
  changes, and defect tracking as described above. Interpreter or specification behavior fixes
  remain with `fix bugs`; ask before unresolved language-design decisions. This alias does not
  authorize `VERSION` changes, commits, pushes, releases, or pull-request changes.
* After modifying tooling or permanent tests, run both baseline suites from `AGENTS.md` and
  `git diff --check`. Treat failed or unavailable verification as incomplete and report it.
* Persist a run report and link it from the execution issue's comments. Report findings first,
  followed by exact commits and worktree state, replay commands, seeds, case counts, enumeration
  bounds, resource limits, retained artifact locations, baseline comparisons, verification results,
  remaining coverage gaps, GitHub state changes, and campaign/defect links. Distinguish completed
  from incomplete campaigns. Passing a bounded campaign is not exhaustive language coverage.
