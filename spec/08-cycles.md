<a id="cycles"></a>
# Cycles

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)


<a id="overview"></a>
## Overview

A Caret cycle is a generalized iterative state transformation.

Rather than giving `for`, `while`, and similar loops unrelated semantics, Caret models iteration as repeated transformation of a state value.

Conceptually:

```text
initial state
    ↓
condition
    ↓
body
    ↓
prepare next state
    ↺
```

A cycle produces a final value.

It is therefore an expression, not merely a control-flow statement.

Conceptually:

```text
cycle : Init -> Condition -> Body -> Prepare -> Result
```

For a state type `S`:

```text
init      : () -> S
condition : S -> Bool
body      : S -> S
prepare   : S -> S

cycle     : S
```

The cycle repeatedly transforms `S` until `condition` becomes false.

---

<a id="fundamental-semantics"></a>
## Fundamental semantics

Given:

```caret
result = cycle init condition body prepare
```

the semantics are equivalent to:

```text
state = init

while condition(state):
    state = body(state)
    state = prepare(state)

result = state
```

This description is only explanatory.

Caret should not require mutable state internally.

The semantic model is functional:

```text
S -> S -> S -> ...
```

Each stage receives a state and produces the next state.

The final state is the value of the entire `cycle` expression.

---

<a id="initialization"></a>
## Initialization

`init` creates the initial state.

It may be:

* an existing value;
* a function producing a value;
* a collection;
* another expression whose result becomes the initial cycle state.

Example:

```caret
initial =
  [
    ^i = 0
    ^sum = 0
  ]

result =
  cycle initial condition body prepare
```

A nullary initializer may also be used where initialization itself must be deferred:

```caret
result =
  cycle @makeInitialState condition body prepare
```

The exact handling of nullary functions follows the normal Caret function-reference rules.

---

<a id="named-collection-state"></a>
## Named collection state

A particularly important use of `cycle` is iteration over a named Collection.

Example state:

```caret
[
  ^i = 0
  ^sum = 0
]
```

The cycle may transform this state Collection at every step.

Conceptually:

```caret
condition s =
  s.i < 10

body s =
  s
  >> set "sum" (s.sum + s.i)

prepare s =
  s
  >> set "i" (s.i + 1)
```

Then:

```caret
result =
  cycle initial condition body prepare
```

produces a final state equivalent to:

```caret
[
  ^i = 10
  ^sum = 45
]
```

The exact collection/state update functions may be provided by the standard library.

The important semantic rule is that each phase receives the complete current state and returns the complete next state.

<a id="previous-and-next-state-views"></a>
### Previous and next state views

Cycle conditions, bodies, and preparation phases execute with a cycle-state lookup view over the
state Collection in addition to their ordinary lexical parameters. This view is an evaluation and
name-resolution mechanism, not a first-class Scope value.

For every phase, unqualified state-field reads refer to the complete previous state. Name lookup
checks local bindings and parameters first, then previous-state public fields, then the captured
lexical parent. The reserved `next` binding denotes the state currently being constructed.

A body or preparation phase begins with `next` structurally equal to its previous state. An exported
assignment writes the new state:

```caret
^sum = sum + i
```

Unmentioned fields remain present in `next`. A later expression in the same phase can observe an
earlier write explicitly:

```caret
^sum = sum + i
^large = next.sum > 100
```

Reading `sum` in the second assignment still reads the previous state; `next.sum` reads the new
value. Non-exported assignments remain local temporaries and do not become state fields.

Each phase commits atomically. The body transforms `S0` into `S1`; preparation then receives `S1`
as its previous state and transforms it into `S2`. No caller can observe a partially constructed
state.

A phase that performs exported state writes commits `next` as its result. A phase that performs no
exported state writes may instead return an explicit complete state value, preserving the ordinary
`S -> S` functions shown above. Mixing exported state writes with a different explicit state return
is an error rather than an implicit merge.

The condition receives the same previous-state read view but is read-only: it cannot perform
exported state writes or access a changing `next` value. This preserves condition purity.

---

<a id="functional-semantics"></a>
## Functional semantics

Cycles are semantically compatible with immutable data.

A cycle does not require mutation of the current state.

Conceptually:

```text
S0
 ↓ body
S1
 ↓ prepare
S2
 ↓ body
S3
 ...
```

The previous state may become unreachable after the next state is produced.

This permits Caret implementations to optimize immutable cycle transformations aggressively.

If the compiler can prove that a previous state is no longer observable, it may:

* reuse storage;
* update values in place internally;
* eliminate intermediate allocations;
* use mutable machine registers or stack slots;
* perform tail-call-like transformations.

Such optimizations must not change the observable immutable semantics.

---

<a id="equivalence-to-tail-recursion"></a>
## Equivalence to tail recursion

A cycle can be expressed as tail recursion.

Conceptually:

```caret
run s =
  condition s &
    run (prepare (body s))
  !
    s
```

Therefore:

```caret
cycle initial condition body prepare
```

is semantically equivalent to repeatedly applying:

```caret
body >> prepare
```

while `condition` holds.

This equivalence is important.

`cycle` is not a separate mutable execution model.

It is a convenient and optimizable representation of a common recursive state transformation.

---

<a id="body-and-prepare-are-separate"></a>
## Body and prepare are separate

`body` and `prepare` deliberately have separate roles.

For a conventional `for`-style loop:

```text
initialize
check
body
increment
check
body
increment
...
```

the mapping is:

```text
init       -> initialization
condition  -> loop condition
body       -> loop body
prepare    -> increment/update before next iteration
```

For example:

```caret
initial =
  [
    ^i = 0
    ^sum = 0
  ]

condition s =
  s.i < 10

body s =
  update s "sum" (s.sum + s.i)

prepare s =
  update s "i" (s.i + 1)

result =
  cycle initial condition body prepare
```

Keeping `body` and `prepare` separate makes conventional iteration easy to express while retaining the general state-transform model.

---

<a id="omitted-prepare-phase"></a>
## Omitted prepare phase

When no separate preparation step is needed, the identity function may be used.

Conceptually:

```caret
cycle initial condition body identity
```

The standard library should provide an identity function.

Caret may later provide shorthand syntax for omitting an identity `prepare` phase, but the fundamental semantics remain:

```text
prepare : S -> S
```

---

<a id="omitted-body-phase"></a>
## Omitted body phase

Similarly, a cycle whose meaningful work occurs entirely in the preparation transformation may use `identity` as its body:

```caret
cycle initial condition identity prepare
```

No special loop form is required.

---

<a id="example-counting"></a>
## Example: counting

Conceptually:

```caret
initial =
  [
    ^i = 0
  ]

condition s =
  s.i < 10

body s =
  s

prepare s =
  update s "i" (s.i + 1)

result =
  cycle initial condition body prepare
```

The final result contains:

```caret
result.i
```

with value:

```text
10
```

---

<a id="example-accumulation"></a>
## Example: accumulation

```caret
initial =
  [
    ^i = 1
    ^total = 1
  ]

condition s =
  s.i <= 10

body s =
  update s "total" (s.total * s.i)

prepare s =
  update s "i" (s.i + 1)

result =
  cycle initial condition body prepare
```

The final cycle state contains both the accumulated result and the final index.

The caller may select the part it needs:

```caret
factorial10 = result.total
```

This is preferable to requiring a separate externally mutable accumulator.

---

<a id="lambdas-with-cycles"></a>
## Lambdas with cycles

Cycle phases may be supplied directly as lambdas.

Example:

```caret
result =
  cycle
    initial
    (s -> s.i < 10)
    (s -> update s "sum" (s.sum + s.i))
    (s -> update s "i" (s.i + 1))
```

All lambda rules apply normally:

* contracts;
* lexical capture;
* purity inference;
* effects;
* partial application;
* ownership.

No separate "loop lambda" syntax is required.

---

<a id="partial-application"></a>
## Partial application

Because `cycle` is an ordinary higher-order function, partial application may be used to define reusable cycle forms.

For example:

```caret
repeatWhile condition body =
  cycle _ condition body identity
```

or:

```caret
iterate prepare =
  cycle _ _ identity prepare
```

The exact reusable abstractions should preferably be library functions rather than additional loop syntax.

---

<a id="state-collection-shape"></a>
## State collection shape

The initial implementation should require a stable state shape across a cycle unless the type system can prove a broader compatible type.

For example, if the initial state is:

```caret
[
  ^i = 0
  ^sum = 0
]
```

then `body` and `prepare` should normally return values exposing compatible fields:

```text
i
sum
```

A transformation that sometimes returns:

```caret
[
  ^i = 1
]
```

and sometimes:

```caret
[
  ^i = 1
  ^sum = 10
  ^error = "..."
]
```

introduces variant state shapes.

Such cycles may eventually be represented using:

* structural unions;
* optional fields;
* pattern matching;
* row-polymorphic types.

The initial implementation may reject incompatible state-shape changes.

---

<a id="contracts"></a>
## Contracts

Cycle phase contracts follow ordinary Caret rules.

For a state contract `State`:

```caret
condition (State) s =
  ...

body (State) s =
  ...

prepare (State) s =
  ...
```

The compiler should infer that the cycle preserves `State` where possible.

Conceptually:

```text
condition : State -> Bool
body      : State -> State
prepare   : State -> State
```

A phase that violates the required state contract causes a compile-time error where statically detectable.

---

<a id="purity-and-effects"></a>
## Purity and effects

`cycle` itself does not imply mutation or effects.

A cycle is pure if:

* initialization is pure;
* `condition` is pure;
* `body` is pure;
* `prepare` is pure.

For example:

```caret
result =
  cycle
    initial
    (s -> s.i < 10)
    updateTotal
    increment
```

is pure if all supplied components are pure.

If a phase has effects, those effects propagate to the cycle expression according to the ordinary effect system.

Example:

```caret
(io) printState s =
  print s
  s
```

Using it as the body causes the cycle to acquire the `io` effect.

The enclosing function must therefore explicitly permit that effect.

Effects must not be hidden merely because they occur inside iteration.

---

<a id="condition-purity"></a>
## Condition purity

The cycle condition must normally be pure.

```text
condition : S -> Bool
```

The compiler must reject a condition whose evaluation introduces an undeclared observable effect.

This avoids behavior where merely checking whether another iteration should occur changes external program state.

If effectful conditions are ever supported, they must be explicitly represented and must participate in normal effect inference.

The initial implementation should require cycle conditions to be pure.

---

<a id="state-ownership"></a>
## State ownership

Cycle state follows normal Caret ownership and lifetime rules.

Conceptually, each iteration consumes the current state and produces the next state:

```text
S0 -> S1 -> S2
```

When a state is uniquely owned and the old version is not subsequently accessible, the compiler may safely reuse its physical storage.

This is particularly important for large:

* collections;
* buffers;
* scopes;
* SIMD data;
* format-processing state.

Functional cycle semantics must therefore not imply mandatory copying.

---

<a id="cycles-over-collections"></a>
## Cycles over collections

Collection iteration can be implemented using `cycle`, although high-level collection operations should remain available.

For example, operations such as:

```caret
map
filter
fold
reduce
```

may internally lower to cycle-like transformations.

Application code should normally prefer these more descriptive operations when they directly express the intent.

Use `cycle` when the iteration requires explicit multi-value state or a more general state machine.

---

<a id="cycles-and-simd"></a>
## Cycles and SIMD

A cycle may be optimized using SIMD when its transformations satisfy normal SIMD requirements.

The presence of `cycle` does not itself request SIMD execution.

Explicit SIMD syntax such as:

```caret
collection :: transform
```

remains the preferred way to require vectorized element-wise execution.

The compiler may nevertheless auto-vectorize suitable pure cycles where safe.

---

<a id="cycles-and-formats"></a>
## Cycles and formats

Streaming or incremental format processing may eventually use cycles internally.

For example, a decoder may repeatedly transform a state containing:

```text
input position
decoded values
current format element
remaining input
```

until the format is complete.

The format system should not require application programmers to manually write such cycles for ordinary decoding.

`cycle` provides a general implementation mechanism rather than replacing declarative formats.

---

<a id="early-termination"></a>
## Early termination

A future version of Caret may support explicit cycle-control values such as:

```text
Continue S
Break S
```

Conceptually:

```text
body : S -> Continue S | Break S
```

A `Break` result terminates the cycle and returns its contained state.

A `Continue` result proceeds normally.

This is preferable to implementing `break` through:

* exceptions;
* hidden mutation;
* non-local jumps.

The initial implementation may postpone `Break` and `Continue`.

Until then, early termination should be represented through the cycle condition or explicit state.

For example:

```caret
[
  ^done = false
  ^state = ...
]
```

with:

```caret
condition s =
  not s.done
```

---

<a id="nested-cycles"></a>
## Nested cycles

A cycle is an expression and may therefore be used inside another cycle.

Example conceptually:

```caret
outsideResult =
  cycle outsideInitial outsideCondition
    (outside ->
      innerResult =
        cycle innerInitial innerCondition innerBody innerPrepare

      combine outside innerResult)
    outsidePrepare
```

No special nesting syntax is required.

Each cycle has its own state value.

---

<a id="relationship-to-conventional-loops"></a>
## Relationship to conventional loops

Common imperative loop forms can be expressed through `cycle`.

A conventional:

```text
for initialization; condition; increment
    body
```

corresponds to:

```text
cycle initialization condition body increment
```

A conventional:

```text
while condition
    body
```

corresponds conceptually to:

```text
cycle initial condition body identity
```

A repeated state machine corresponds to:

```text
cycle initial notFinished transition identity
```

Therefore separate `for`, `while`, and `do` constructs are not required for the core language.

Libraries may provide convenience abstractions where useful.

---

<a id="implementation-model"></a>
## Implementation model

The compiler should initially lower:

```caret
cycle initial condition body prepare
```

to behavior equivalent to tail-recursive execution:

```text
state = initial

loop:
    if not condition(state):
        return state

    state = body(state)
    state = prepare(state)
    goto loop
```

This imperative pseudocode is an implementation strategy only.

The observable language semantics remain immutable state transformation.

The compiler is free to implement the cycle using:

* a machine-level loop;
* tail-call elimination;
* mutable local variables;
* registers;
* in-place storage reuse;
* specialized collection iteration.

No intermediate state copies are required unless observable semantics demand them.

---

<a id="implementation-requirements"></a>
## Implementation requirements

The initial implementation should support at minimum:

1. `cycle` as an expression that returns its final state.
2. An initial state value.
3. A pure unary Boolean condition.
4. A unary body transformation.
5. A unary preparation transformation.
6. Lambda expressions as phase arguments.
7. Named functions as phase arguments.
8. Positional or named Collections as cycle state.
9. Effect inference through all cycle phases.
10. Contract checking of state transformations.
11. Efficient lowering without mandatory immutable copying.
12. Stable state shape across iterations.

The initial implementation may postpone:

* `Break` / `Continue` values;
* changing structural state types during iteration;
* effectful conditions;
* automatic parallel cycles;
* explicit loop labels;
* generalized nondeterministic relational cycles.

---

<a id="design-principle"></a>
## Design principle

A Caret cycle is not fundamentally a mutable loop.

It is repeated application of state transformations:

```text
S
 -> body
 -> prepare
 -> S
```

controlled by:

```text
S -> Bool
```

and producing the final state as its result.

This provides conventional iteration while remaining compatible with:

* immutable data;
* first-class scopes;
* contracts;
* effect inference;
* lambdas;
* partial application;
* ownership optimization;
* tail recursion;
* SIMD optimization.

The core model should remain small enough that more specialized iteration constructs can be implemented as ordinary Caret functions rather than additional language syntax.

