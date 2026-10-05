# Phase 5 — Ordinary cycles

[Roadmap index](../PLAN.md)

<a id="phase-5-self-hosting-foundation-and-ordinary-cycles"></a>
## Phase 5 — Ordinary cycles (v1)

The former Caret-written interpreter milestone is now a
[standalone post-v1 phase](self-interpreter.md). It is not a prerequisite for cycles, modules,
or sandboxes. Existing callable, contract/effect, Collection, and resolver behavior supplies the
cycle foundation.

### `cycle`

- Implement ordinary four-argument `cycle initial condition body prepare`, with a pure unary
  condition and unary body/prepare transformations. Supply ordinary `identity` for an unused body
  or prepare phase; fewer arguments retain ordinary partial-application behavior. Omission
  shorthand remains a future possibility, not a required cycle grammar.
- Accept named functions, lambdas, and partials as phases, with positional or named Collections
  among supported state values. Enforce stable state shape and compatible contracts across iterations.
- Implement the [previous/next state view](../spec/08-cycles.md#previous-and-next-state-views):
  local/parameter names precede previous-state fields, which precede captures; exported writes
  construct `next`, unmentioned fields persist, and each phase commits atomically. Explicit
  whole-state return and exported state writes cannot be mixed into an implicit merge.
- Infer phase effects while requiring the condition to remain pure. Lower to an internal loop/tail
  recursion without mandatory immutable copying, preserving functional observable semantics.
- Support nesting, collection traversal helpers, and final-state return. Add format interactions
  when formats exist; they are not a v1 gate. Keep `Break` / `Continue`, changing shapes, labels,
  effectful conditions, and automatic parallelism deferred.

Require focused callable/state/contract/effect tests and runnable integration examples before
completion. Test old-state persistence, `next` read-after-write, failed transitions, pure conditions,
aliases, shadowing, partials, identity phases, and nested cycles.
