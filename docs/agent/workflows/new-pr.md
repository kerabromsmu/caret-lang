# New Pr workflow

### `new PR`

* Create, reopen, or update a pull request only when the current user request explicitly invokes
  `new PR` or directly asks for that pull-request action. An implementation request, approved plan,
  commit, push, completed phase, or earlier PR discussion does not independently authorize PR
  activity.
* Require a cleanly understood change set and preserve unrelated user changes. Fetch current
  `main`, inspect the complete diff, and run the full baseline suites before creating or updating
  the pull request.
* Before selecting the version transition, inspect `PLAN.md`, the complete GitHub project, every
  card relevant to the current roadmap phase, and the current version documentation. Increment
  `MINOR` and reset `UPDATE` only when those sources consistently establish that the phase is
  complete; otherwise increment `UPDATE`. If roadmap text, project status, or completion evidence
  conflicts, ask the project owner before changing `VERSION` or creating or updating the pull
  request. Never increment `MAJOR` without explicit project-owner authorization. Validate the
  transition with the repository script.
* If currently on `main`, create a concise feature branch derived from the changes.
* Commit the intended pending changes with a concise summary, push the branch, and create a
  ready-for-review pull request targeting `main`.
* Use a clear title and a concise body covering behavior, tests, documentation, linked issues, and
  the version transition. If the branch already has an open pull request to `main`, update it rather
  than creating a duplicate.
* Never merge the pull request, force-push, or include unrelated changes.

