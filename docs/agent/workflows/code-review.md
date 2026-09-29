# Code Review workflow

### `code review`

* Review committed branch changes from the merge base with current `origin/main`, together with
  staged and unstaged worktree changes.
* Prioritize correctness bugs, uncaught edge cases, code duplication, technical debt, unnecessary
  complexity, maintenance risk, specification drift, and missing tests.
* For every finding, search existing repository issues and project cards for the same problem.
  Reuse a matching issue and card instead of creating duplicates; add an existing issue to the
  project if it has no card. Otherwise, create one GitHub issue per finding with the affected
  location, impact, and remediation guidance, and add it to the Caret GitHub project. Set each
  finding's project `Issue type` to `Bug`; set new cards to `Todo` and preserve the status of
  existing cards.
* Report findings first in severity order, with precise file and line references and concise
  remediation guidance and links to their GitHub issues. If no findings exist, say so and identify
  residual testing or coverage risks; do not create cards for those risks alone.
* This workflow authorizes issue and project changes only to track findings as described above.
  Do not edit code or move cards to `In Progress` or `Done` unless the user explicitly requests fixes.

