# Rule cycles and implementation requirements

[Chapter index](../11-rules-rulesets-and-objects.md) · [Language specification index](../../LANGUAGE.md)


<a id="rulecycle"></a>
## `ruleCycle`

<a id="overview-3"></a>
### Overview

`ruleCycle` is the execution environment for rules.

A rule cycle:

1. executes initialization;
2. establishes its objects, contexts, rules, and rulesets;
3. raises its master context;
4. traverses relevant objects and rules;
5. generates implicit contexts and fronts;
6. determines applicable rules;
7. applies one permitted applicable rule at a time;
8. propagates its effects;
9. reevaluates affected rules;
10. continues until stable;
11. advances its traversal;
12. terminates when its master context goes down.

---

<a id="initialization"></a>
### Initialization

A rule cycle contains an `init` part:

```caret
system =
  ruleCycle
    init
      ...
```

Initialization establishes the initial rule-cycle universe.

It may create:

* objects;
* contexts;
* rules;
* ruleset instances;
* data;
* other cycle-local state.

Example:

```caret
game =
  ruleCycle
    init
      player = object
        ^health = 100

      enemy = object
        ^health = 50

      gameOver = rule [
        ^C = ~
        ^A = ~
        ^T = gameOverTrigger
        ^E = endCycle
        ^N = ~
      ]
```

---

<a id="installing-rulesets"></a>
### Installing rulesets

A ruleset may be constructed independently:

```caret
combat = Combat player enemy calculateDamage
```

and installed into the current cycle:

```caret
install combat
```

or commonly:

```caret
install Combat player enemy calculateDamage
```

according to normal Caret application rules.

Installation makes the ruleset's relevant rules and contexts part of the current `ruleCycle`.

A constructed but uninstalled ruleset remains an ordinary value and does not autonomously execute.

---

<a id="template-based-system-construction"></a>
### Template-based system construction

A principal purpose of `ruleCycle` is to assemble systems from reusable rule libraries.

Example:

```caret
game =
  ruleCycle
    init
      board = makeBoard 8 8

      white = Player "White"
      black = Player "Black"

      pieces = makePieces board white black

      install AlternatingTurns white black
      install ChessMovement board pieces
      install CaptureRules pieces
      install ChessVictory white black pieces
```

The application-specific definition may therefore consist mainly of objects, configuration, and instantiated rulesets.

The same mechanism can construct a data interpreter:

```caret
parser =
  ruleCycle
    init
      source = stream bytes

      install Signature pngSignature
      install ChunkReader source PngChunk
      install StopAt "IEND"
```

---

<a id="master-cycle-context"></a>
### Master cycle context

Every `ruleCycle` owns an implicit master context.

At cycle start:

```text
down -> up
```

producing its rise front.

The cycle runs while that context is up.

A rule may terminate the cycle:

```caret
finish = rule [
  ^C = ~
  ^A = ~
  ^T = completedTrigger
  ^E = endCycle
  ^N = ~
]
```

The cycle ends when its master context goes down.

---

<a id="object-traversal"></a>
### Object traversal

A rule cycle implicitly traverses the objects belonging to its runtime universe.

Application code does not normally write this outer traversal explicitly.

When an object is entered, processed, or left, the cycle may implicitly raise and lower object-related contexts.

Conceptually:

```text
object A context rises
    rule propagation
object A context falls

object B context rises
    rule propagation
object B context falls
```

These transitions produce ordinary fronts available to rule triggers.

Objects may also participate in category or state contexts where defined by their contracts or object model.

---

<a id="rule-scheduling"></a>
### Rule scheduling

The observable scheduling model is:

```text
1. Update context/object state.

2. Determine applicable rules.

3. Respect explicit causal ordering relationships.

4. If multiple unordered rules are applicable,
   choose an arbitrary one.

5. Raise the chosen rule's implicit context.

6. Execute its effect.

7. Propagate state and context changes.

8. Lower the rule's implicit context.

9. Propagate the resulting fall front.

10. Reevaluate affected rules.

11. Repeat until no applicable rule remains
    for the current propagation step.
```

The implementation need not literally scan every rule.

It may maintain dependency indexes, queues, or other optimized structures.

The observable result must follow the same scheduling semantics.

---

<a id="no-source-order-guarantee"></a>
### No source-order guarantee

The order in which rules appear in:

* source code;
* a `ruleset`;
* an `init` block;
* an internal collection

does not create a scheduling constraint.

For example:

```caret
firstInSource = rule firstDefinition

secondInSource = rule secondDefinition
```

does not imply:

```text
firstInSource -> secondInSource
```

If order matters, the program must state the relationship explicitly.

---

<a id="propagation-to-stability"></a>
### Propagation to stability

Effects may change:

* object state;
* contexts;
* rule active states;
* object existence;
* installed state;
* values used by triggers.

These changes may make other rules applicable.

The cycle continues applying and propagating rules until the current processing step reaches a state in which no further rule is applicable.

Conceptually:

```text
change
  ↓
rule A
  ↓
change
  ↓
rule B
  ↓
rule C
  ↓
stable
```

Only then does normal traversal advance.

---

<a id="trigger-stability"></a>
### Trigger stability

Repeated evaluation must not repeatedly fire a continuously true trigger.

For:

```caret
rule [
  ^C = ~
  ^A = ~
  ^T = greaterThanTenTrigger
  ^E = effect
  ^N = ~
]
```

application occurs on the relevant transition:

```text
false -> true
```

not on every internal scan while `x > 10` remains true.

The runtime must retain sufficient trigger history to preserve this behavior.

---

<a id="object-creation-and-destruction"></a>
### Object creation and destruction

Objects in a rule cycle are persistent values with stable logical identities. An object version is
an immutable named Collection constructed with ordinary exported bindings:

```caret
player = object
  ^health = 100
  ^name = "Ada"
```

The cycle state stores the current version under an exported state field. Replacing that field with
a newly constructed version preserves the object's logical identity; the previous version remains
unchanged for any code that still holds it. Objects outside a cycle remain ordinary inert values and
do not acquire autonomous behavior.

Effects may create objects:

```caret
create bullet
```

or destroy them:

```caret
destroy enemy
```

Created objects become part of the rule-cycle universe.

Destroyed objects cease to participate after destruction becomes effective.

Creation adds a new logical identity to the next persistent cycle state. Destruction removes that
identity from the next state. Both changes commit at the effect boundary and alter traversal only at
the next deterministic traversal boundary; neither operation reenters traversal while an object is
being processed.

Rule-cycle object traversal order is deliberately unspecified and is not observable language
behavior. Each traversal operates on a stable membership snapshot and visits every identity in that
snapshot exactly once. Creation and destruction take effect at the next documented traversal
boundary. Code requiring cross-object order must express it through contexts, triggers, or chains.
An implementation may use a stable internal order, but programs and tests must not depend on it.

The initial implementation should avoid uncontrolled traversal reentrancy when an object is created during another object's propagation.

---

<a id="dynamic-rule-state"></a>
### Dynamic rule state

Rule effects may change rule active states:

```caret
activate @specialRule
deactivate @tutorialRule
```

Such changes participate in normal propagation.

The active state is runtime state, not merely a compile-time annotation.

---

<a id="cycle-termination"></a>
### Cycle termination

The rule cycle runs while its master context remains up.

A normal termination operation is:

```caret
lower cycle
```

Once the cycle context falls, no new ordinary traversal iteration should begin.

The runtime may finish the currently required deterministic cleanup or propagation before returning.

---

<a id="relationship-to-ordinary-cycle"></a>
### Relationship to ordinary `cycle`

Ordinary:

```caret
cycle initial condition body prepare
```

explicitly provides state transformations.

`ruleCycle` derives them from:

* objects;
* contexts;
* installed rules;
* installed rulesets;
* CATEN semantics;
* the rule scheduler.

Conceptually:

```text
condition:
    cycle context is up

body:
    process objects and contexts
    schedule applicable rules
    propagate rule effects to stability

prepare:
    advance traversal
```

`ruleCycle` may internally reuse ordinary cycle machinery, but its reactive scheduling semantics are defined separately.

---

<a id="implementation-requirements"></a>
## Implementation requirements

The initial implementation should support at minimum:

1. A first-class `Rule` value.
2. The ordinary unary `rule : RuleDefinition -> Rule` callable and required CATEN fields with optional values in
the general template-derived `RuleDefinition` contract:

```text
C Context
A Active
T Trigger
E Effect
N Name (string-literal ID)
```

Every field must be present; `~` selects its documented default. Ordinary optional value contracts
provide this behavior without new template syntax. Exact first-class C/T/E contracts remain unresolved.

3. Persistent up/down contexts.
4. Boolean context combinations.
5. `rise` and `fall` fronts.
6. Runtime rule active states.
7. Edge-based trigger behavior.
8. Implicit contexts for rule application.
9. Rule chaining through rule-context fronts.
10. `chain` sugar.
11. Explicitly unordered rule execution when no dependency defines order.
12. No implicit source-order priority.
13. Reevaluation after each selected rule's effects.
14. Warning diagnostics for potentially significant unordered rule interactions.
15. `(unordered)` as explicit acknowledgement/suppression of those diagnostics.
16. Explicit ordering through rule dependencies and chains.
17. A first-class `RuleSet`.
18. Ruleset parameters through ordinary Caret functions.
19. Partial ruleset application using `_`.
20. `^` exports for rules and other ruleset members.
21. Independent ruleset instances.
22. `install ruleset`.
23. `ruleCycle` initialization.
24. An implicit master cycle context.
25. Object traversal and implicit object-related context changes.
26. Rule propagation until stable.
27. Object creation and destruction.
28. Runtime rule activation/deactivation.
29. Cycle termination by lowering its master context.
30. Ordinary Caret effect inference through rule effects.

The initial implementation may postpone:

* numeric rule priorities;
* parallel execution;
* transactional batches;
* distributed rule cycles;
* optimized dependency graphs;
* dynamic ruleset unloading;
* debugger visualization;
* formal conflict analysis.

These later features must preserve the principle that rule order is constrained only where the program explicitly specifies a dependency.

---

<a id="design-principle"></a>
## Design principle

A `ruleCycle` establishes a reactive universe of objects, contexts, rules, and rulesets.

A rule becomes applicable when:

```text
C is up
AND
A is on
AND
T triggers
```

Application causes:

```text
rule context rises
E executes
rule context falls
```

Those effects and fronts may make additional rules applicable.

When several rules are applicable:

```text
explicit dependency
    -> constrains their order

no dependency
    -> order is deliberately arbitrary
```

The runtime applies one permitted rule, propagates its effects, reevaluates the system, and continues until the current propagation reaches stability.

The developer may explicitly acknowledge harmless unordered behavior with:

```caret
(unordered)
```

and should express required ordering through causal relationships such as rule-context dependencies or `chain`.

Rulesets package reusable parameterized behavior.

`^` defines their public interface.

The `ruleCycle` `init` block assembles those reusable rule libraries with concrete objects and configuration, allowing systems such as games, interpreters, simulations, and workflows to be built primarily by composition rather than explicit control flow.
