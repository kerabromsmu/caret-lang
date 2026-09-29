# Phase 8 — Rules, rulesets, objects, and `ruleCycle`

[Roadmap index](../PLAN.md)

## Phase 8 — Rules, rulesets, objects, and `ruleCycle`

### Contexts and rules

- Before rule construction, settle `RULE-PHASE-CONTRACT-001`:
  [RuleDefinition needs first-class C/T/E contracts](../spec/sections/11-01-rules.md#basic-definition).
  Require the Phase 4 `TEMPLATE-OPTIONAL-001` evidence for contextual CATEN literal completion and
  exact membership of already established values.
  Unresolved phase contracts must not be replaced by rule-specific ASTs, parser exceptions,
  or hidden lazy-expression wrappers.
- Add persistent `Context` values, idempotent `raise`/`lower`, and transient `rise`/`fall` fronts over
  Boolean context expressions.
- Implement ordinary unary `rule : RuleDefinition -> Rule`, consuming a named Collection validated
  by the general template-derived contract. All CATEN fields are present; explicit missing values
  select the documented defaults. Preserve explicit string-literal IDs for `N`, anonymous identity
  for `N = ~`, runtime active state, edge-trigger history, deferred effect execution, and implicit
  application context. Assignment names do not supply `N`.
- Implement gate semantics: `C` and `A` permit application but never replay a trigger missed while
  gated. Require `C` and `T` purity; propagate ordinary effects from `E`.
- Implement `activate`/`deactivate`, implicit context rise/effect/fall, reevaluation after each effect,
  and protection/diagnostics for non-stabilizing propagation.

### Ordering and chains

- Schedule one applicable rule at a time, propagate its effects, and reevaluate. Use no observable
  source-order priority; a stable internal order may make tests reproducible but is not contractual.
- Represent causal dependencies through fronts and partial orders. Lower `chain` to ordinary rules
  joined by `fall @previous.context`; combine an explicit later trigger at the same completion front.
- Warn conservatively about significant simultaneously applicable unordered rules. `(unordered)`
  suppresses only the warning and never changes scheduling.

### Rulesets and cycles

- Add first-class private-by-default `RuleSet` values with `^` exports. Ordinary functions and holes
  provide templates and partial application; every construction owns independent state.
- Implement nested rulesets and explicit idempotent `install`; an uninstalled ruleset is inert.
- Add `ruleCycle init`, its master `cycle` context, registration, propagation to stability, traversal,
  and termination by lowering the master context.
- Record container identities observed by rule `C`/`T` evaluation. After a successful `put`, enqueue
  only dependent rules for reevaluation; keep this live dependency mechanism separate from atomic
  previous/next persistent-state commits.
- Introduce first-class objects with stable identity, exported state, cycle membership, and implicit
  traversal contexts. Define deterministic deferred lifecycle semantics for `create`/`destroy` to
  prevent reentrant traversal.
- Extend reflection across rules, contexts, rulesets, cycle state, dependencies, and public object
  interfaces without exposing private bindings.

