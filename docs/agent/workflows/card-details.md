# Card Details workflow

### `card details`

* Fetch the complete project through explicit sufficient limits or pagination; never rely on a
  GitHub CLI default page size. Report the count and identities of all discovered `Underspecified`
  cards before proposing changes.
* Inspect every card in `Underspecified`, its linked issue, dependencies, relevant repository state,
  and canonical specifications. Also inspect relevant cards in every other column for dependencies,
  overlap, conflicts, superseding work, and already-settled design decisions; do not change those
  cards unless they are themselves being clarified under this workflow.
* Resolve discoverable facts independently. Ask targeted questions only for product or
  language-design ambiguity that the repository cannot settle.
* Replace vague titles and bodies with implementation-specific goals, required behavior,
  constraints, dependencies, acceptance criteria, tests, documentation requirements, and explicit
  exclusions.
* Move fully clarified cards to `Todo`. Leave cards with unresolved decisions in `Underspecified`.
* Do not implement the cards under this alias.

