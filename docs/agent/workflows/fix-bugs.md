# Fix Bugs workflow

### `fix bugs`

* Fetch the complete Caret GitHub project using explicit sufficient limits or pagination. Among
  cards whose project `Issue type` is `Bug`, select the first `In Progress` card in project order;
  if none exists, select the first `Todo` card and move it to `In Progress`. Fix only that card.
  If neither column has a Bug card, report that there is no unfinished Bug card and stop.
* Read the linked issue, relevant implementation, and canonical specifications before editing.
  Resolve discoverable facts independently, but ask before unresolved language-design decisions,
  contradictions, or materially broader work.
* Complete the fix and any required automated tests, runnable `.caret` examples, owning `spec/`
  updates, public documentation, and conformance evidence. Make needed test changes before running
  the full baseline suites from the Testing section.
* After implementation and verification succeed, comment on the linked issue with completion and
  test evidence, close it, and move the card to `Done`. If work or verification remains incomplete,
  leave the issue open and card in `In Progress`; report the blocker and remaining work.

### Repeated `fix bugs`

* An explicit request to repeat `fix bugs` authorizes a loop over Bug cards in `In Progress` and
  `Todo` only, using the selection order above, until neither column contains a Bug card.
* Treat each card's implementation plan as confirmed when repository evidence determines the
  behavior. Interrupt the loop and ask the project owner if ambiguity, contradiction, or an
  unresolved question needs their attention. Preserve unrelated user changes and stop if they
  cannot be separated safely.
* For each card, complete its fix and full verification, then commit only that card's changes with
  a concise card-specific message. Do not commit incomplete work to advance the loop. After the
  successful commit, comment with completion and test evidence, close the issue, move the card to
  `Done`, and continue. If verification or acceptance remains incomplete, leave the issue open and
  card in `In Progress`, and stop with the blocker.
* Repetition does not authorize changing `VERSION`, pushing a branch, or creating, reopening, or
  updating a pull request. Those actions retain their separate explicit authorization rules.
