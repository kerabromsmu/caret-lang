# Next Step workflow

### `next step`

* Read the Caret GitHub project and implement every card currently in `In Progress`.
* If `In Progress` is empty, promote the first `Todo` card in project order to `In Progress` and
  implement it.
* Inspect the relevant implementation and canonical specifications before editing. Resolve
  discoverable facts independently, but ask before inventing unresolved syntax or observable
  semantics.
* Complete the code, automated tests, runnable `.caret` examples, owning `spec/` updates, public
  documentation, and conformance evidence required by each card.
* Run the full baseline suites from the Testing section.
* Only after implementation and required verification succeed, comment on the linked issue with
  completion evidence, close it, and move its project card to `Done`.
* If required work or verification remains incomplete, leave the issue open and do not move the
  card to `Done`; report the blocker and remaining work.

### Repeated `next step`

* An explicit request to repeat `next step` through a named roadmap phase authorizes a durable loop
  over only that phase's project cards, in project order. It does not include unrelated `Todo`
  cards.
* Treat each card's implementation plan as approved when repository evidence determines all
  observable behavior. Pause and ask the project owner before unresolved syntax or semantics,
  conflicting specifications or project state, authority-boundary changes, destructive actions, or
  materially broader scope.
* For each card, complete the ordinary `next step` workflow and full verification, then commit all
  changes produced for that card with a concise card-specific message. Preserve unrelated user
  changes outside the commit. Do not commit incomplete work merely to advance the loop.
* After a successful card commit, comment with completion and test evidence, close the issue, move
  its card to `Done`, and continue with the next card. If verification or acceptance remains
  incomplete, leave the issue open and out of `Done` and pause with the blocker.
* Finish the loop only after every card in the named phase is `Done`, the roadmap and conformance
  corpus record the completed phase, and final verification passes.
* Repetition does not authorize changing `VERSION`, pushing a branch, or creating, reopening, or
  updating a pull request. Those actions retain their separate explicit authorization rules.
