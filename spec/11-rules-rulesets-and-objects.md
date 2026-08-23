<a id="rules-rulesets-and-objects"></a>
# Rules, Rulesets, and Objects

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)


<a id="overview"></a>
## Overview

Caret provides a rule system for defining reactive systems such as:

* games;
* simulations;
* data and stream interpreters;
* protocol processors;
* workflow engines;
* state machines.

Rules execute inside a `ruleCycle`.

A rule does not independently poll or execute globally. The surrounding `ruleCycle` provides:

* object traversal;
* context changes;
* context fronts;
* rule evaluation;
* rule scheduling;
* effect propagation;
* chaining;
* lifecycle and termination.

The components of a rule are summarized by the mnemonic **CATEN**:

```text
C  Context
A  Active state
T  Trigger
E  Effect
N  Name (string-literal ID)
```

All CATEN components are optional.

`rule` is an ordinary unary Caret function:

```text
rule : RuleDefinition -> Rule
```

`RuleDefinition` is a structural contract produced through the ordinary `template` function. Its
optional named Collection fields are `C`, `A`, `T`, `E`, and `N`; those letters are field names,
not clauses, keywords, or parser constructs. `rule definition` uses ordinary application, lookup,
aliases, arity, partial application, contracts, effects, reflection, and staging. The compiler may
recognize the resolved language-owned `rule` callable identity, but never the lexical spelling
alone. There is no `RuleExpression` production or rule-only block-argument grammar.

---

<a id="rules"></a>
## Rules

<a id="basic-definition"></a>
### Basic definition

A rule is a first-class specialized Caret value returned by the ordinary `rule` function.

Example:

```caret
captureEffect state =
  move selectedPiece target
  destroy targetPiece

definition = [
  ^C = gamePlayerTurnContext
  ^A = on
  ^T = captureTrigger
  ^E = captureEffect
  ^N = "capture"
]

capture = rule definition
```

Equivalently, an explicit named Collection may be supplied directly:

```caret
capture = rule [
  ^C = gamePlayerTurnContext
  ^A = on
  ^T = captureTrigger
  ^E = captureEffect
  ^N = "capture"
]
```

The example uses ordinary first-class phase functions rather than hidden unevaluated syntax.
`RuleDefinition` requires general optional-template-member semantics. The final surface spelling
for optional template members, and the exact first-class contracts for deferred `C`, `T`, and `E`
values, remain unresolved dependencies. Implementations must not substitute a rule-specific AST,
lazy-expression wrapper, or parser exception. `C` must retain the persistent/context behavior
below, `T` must remain observable and reevaluable, and `E` executes only when the rule is applied.

The components are:

```text
C  context in which the rule can apply
A  whether the rule is active
T  condition or event that triggers application
E  changes caused by the rule
N  optional string-literal ID
```

The canonical documentation order is CATEN.

---

<a id="context"></a>
### Context

A context has a persistent Boolean state:

```text
up
down
```

A rule may apply only while the persistent context value represented by its `C` field is up. The
exact first-class contract by which `C` preserves or computes a context is unresolved; it is not an
unevaluated source expression captured by the parser.

Contexts may be combined using ordinary Boolean expressions:

```caret
game and playerTurn
combat and not paused
dialog or cutscene
```

Example:

```caret
attack = rule [
  ^C = gamePlayerTurnContext
  ^T = attackTrigger
  ^E = performAttack
]
```

If:

```caret
game and playerTurn
```

is down, `attack` cannot apply.

<a id="context-fronts"></a>
#### Context fronts

Changing a context produces a transient front.

A transition:

```text
down -> up
```

produces:

```caret
rise context
```

A transition:

```text
up -> down
```

produces:

```caret
fall context
```

Examples:

```caret
rise combat
fall dialog
```

Boolean combinations may also have fronts:

```caret
rise (game and playerTurn)
fall (combat or dialog)
```

The distinction between a level and a front is fundamental:

```caret
combat
```

means `combat` is currently up.

```caret
rise combat
```

means `combat` has just changed from down to up.

```caret
fall combat
```

means `combat` has just changed from up to down.

A front is transient and exists only as part of the corresponding rule-cycle propagation.

---

<a id="changing-contexts"></a>
### Changing contexts

Contexts may be changed by rule effects or other rule-cycle operations:

```caret
raise combat
lower combat
```

`raise` changes a context to up.

`lower` changes a context to down.

Raising an already-up context does not generate another rise front.

Lowering an already-down context does not generate another fall front.

---

<a id="active-state"></a>
### Active state

Every rule has an active state independent of its context.

The active state is:

```text
on
off
```

Example:

```caret
specialAttack = rule [
  ^A = off
  ^T = specialTrigger
  ^E = performSpecialAttack
]
```

An inactive rule cannot apply.

Rules may be activated and deactivated at runtime:

```caret
activate @specialAttack
deactivate @specialAttack
```

Context and active state have different meanings:

```text
Context
    describes whether circumstances permit the rule.

Active state
    describes whether the rule itself is enabled.
```

---

<a id="trigger"></a>
### Trigger

`T` stores the first-class trigger value or function that defines the observable condition or
Boolean combination causing a rule to become applicable. The rule engine reevaluates that value
according to the semantics below; Rule construction does not evaluate the trigger eagerly. The
exact trigger contract remains unresolved and is not hidden parser-retained syntax.

Example:

```caret
death = rule [
  ^T = deathTrigger
  ^E = destroyPlayer
]
```

Normal persistent conditions use transition semantics.

For:

```caret
player.health <= 0
```

the triggering event is normally:

```text
false -> true
```

A continuously true condition does not repeatedly trigger the rule.

A rule therefore becomes applicable when:

```text
C is up
AND
A is on
AND
T triggers
```

<a id="fronts-in-triggers"></a>
#### Fronts in triggers

Context fronts may be used directly:

```caret
beginTurn = rule [
  ^T = beginTurnTrigger
  ^E = prepareTurn
]

resume = rule [
  ^T = resumeTrigger
  ^E = resumeGame
]
```

This is particularly important for chaining rules.

<a id="context-and-active-state-are-gates"></a>
#### Context and active state are gates

`C` and `A` permit application but do not normally generate a delayed trigger.

For example:

```caret
rule [
  ^C = combat
  ^T = enemyDeathTrigger
]
```

If:

```caret
enemy.health <= 0
```

becomes true while `combat` is down, subsequently raising `combat` does not retroactively apply the rule.

If entering combat should itself cause evaluation as an event, it should be expressed explicitly:

```caret
rule [
  ^T = combatEnemyDeathTrigger
]
```

---

<a id="effect"></a>
### Effect

`E` contains the changes caused by application of the rule.

Example:

```caret
capture = rule [
  ^T = validCaptureTrigger
  ^E = captureEffect
]
```

The function or descriptor stored in `E` may use an ordinary Caret block and call ordinary
functions. Constructing the Rule does not execute it.

Like an ordinary `cycle` transformation, the callable or descriptor stored in `E` executes against persistent previous and
next rule-cycle state. Unqualified state reads observe the previous state, `^field = value` writes
the next state, and `next.field` observes writes already made by the current effect. Unmentioned
fields carry forward. The complete next state becomes visible atomically after the selected rule's
effect finishes and before applicability is reevaluated.

Typical operations may include:

```caret
raise context
lower context

activate @rule
deactivate @rule

create object
destroy object

send message
```

The rule system does not require a closed hard-coded set of effect operations.

<a id="effect-inference"></a>
#### Effect inference

Calls made from `E` participate in Caret's ordinary effect system.

An effect involving networking, file access, GUI state, or other externally observable behavior introduces the corresponding inferred effects.

`C` and `T` should normally remain pure because the rule engine may reevaluate them freely.

---

<a id="name"></a>
### Name

`N` optionally identifies a rule with a string value under the RuleDefinition contract. The current
model requires a construction-time string literal; it does not accept a bare identifier or an
arbitrary runtime string expression.

Example:

```caret
rule [
  ^N = "capture"
  ^T = validCaptureTrigger
  ^E = capturePiece
]
```

Assignment does not supply `N` implicitly:

```caret
capture = rule [
  ^T = validCaptureTrigger
  ^E = capturePiece
]
```

This Rule has the documented anonymous/internal identity. An ordinary reflective descriptor may
independently expose `capture` as its visible binding or declaration name, but that metadata is not
the CATEN `N` field. The ordinary `rule` function cannot inspect its caller's syntax or assignment
left-hand side.

Binding name and rule identity are conceptually distinct:

```caret
r = rule [
  ^N = "capture"
  ^T = captureTrigger
]
```

---

<a id="optional-caten-components"></a>
### Optional CATEN components

All CATEN fields are optional through the general optional named-template-member semantics. This is
not rule-specific missing-field behavior.

Recommended defaults are:

```text
C omitted  -> always up
A omitted  -> initially on
T omitted  -> no autonomous trigger
E omitted  -> no explicit effect
N omitted  -> anonymous/internal identity
```

A rule without `E` still produces its implicit rule context when applied.

A rule without `T` may still participate in mechanisms such as explicit invocation or chaining.

---

<a id="implicit-rule-context"></a>
### Implicit rule context

Every rule owns an implicit context.

When a rule applies:

```text
raise rule.context
execute E
lower rule.context
```

Therefore every application produces:

```caret
rise @rule.context
fall @rule.context
```

Other rules may respond to those fronts.

Example:

```caret
capture = rule [
  ^T = captureRequestedTrigger
  ^E = capturePiece
]

score = rule [
  ^T = captureCompletionTrigger
  ^E = addCaptureScore
]
```

The implicit context exists even when the rule has no explicit `E`.

---

<a id="rule-ordering"></a>
## Rule ordering

<a id="unordered-rules"></a>
### Unordered rules

Rule definition order does **not** imply execution order.

If several rules are simultaneously applicable and no ordering relationship between them has been specified, the `ruleCycle` may choose any of them.

For example:

```caret
a = rule [
  ^T = eventTrigger
  ^E = effectA
]

b = rule [
  ^T = eventTrigger
  ^E = effectB
]
```

If both become applicable, either sequence is valid:

```text
a
b
```

or:

```text
b
a
```

Caret deliberately provides no guarantee that the chosen order remains the same across:

* executions;
* compiler versions;
* platforms;
* optimization levels;
* runtime implementations.

Source order must never be relied upon as implicit rule priority.

---

<a id="effects-affect-subsequent-scheduling"></a>
### Effects affect subsequent scheduling

Applicable rules are not normally executed as an immutable simultaneous batch.

The scheduler conceptually operates as follows:

```text
determine applicable rules

choose one permitted rule

apply it

propagate its effects

reevaluate affected rules

choose another applicable rule

...
```

Therefore the first selected rule may alter whether another previously applicable rule remains applicable.

Example:

```caret
a = rule [
  ^T = conditionTrigger
  ^E = disableSomething
]

b = rule [
  ^T = enabledConditionTrigger
  ^E = otherEffect
]
```

If both initially become applicable and `a` executes first, its effect may make `b` no longer applicable.

If `b` executes first, both effects may occur.

If that difference matters, the developer must specify ordering.

---

<a id="unordered-rule-diagnostics"></a>
### Unordered-rule diagnostics

Because accidental ordering dependencies can produce difficult bugs, Caret tooling should warn when it detects potentially significant unordered rule application.

A diagnostic may conceptually report:

```text
warning:
rules `a` and `b` may become applicable without a defined order
their effects may be observed in either order
```

Static analysis should report cases it can reasonably identify.

A development or debug runtime may additionally report actual cases where several unordered rules become applicable together.

This is a warning, not an error.

Unordered application is a legitimate and intentional design technique.

---

<a id="explicit-acknowledgement-of-unordered-execution"></a>
### Explicit acknowledgement of unordered execution

A developer may explicitly state that arbitrary ordering is acceptable.

The `unordered` contract marks such intent:

```caret
(unordered) ambientEffect = rule [
  ^T = eventTrigger
  ^E = updateAmbientEffect
]
```

A ruleset may similarly declare that unordered interactions among its relevant rules are intentional:

```caret
(unordered) AmbientRules =
  ruleset
    ...
```

The annotation suppresses applicable unordered-order diagnostics.

It does **not** change scheduling behavior.

```caret
(unordered)
```

means:

> Arbitrary ordering is semantically acceptable here.

It does not mean that the runtime must randomize execution order.

`unordered` is a built-in declaration contract, not a second annotation system. Like `pure`, it
has compiler-recognized semantic behavior beyond an ordinary Boolean predicate. It is valid on a
rule or ruleset declaration and invalid on unrelated values.

---

<a id="enforcing-order"></a>
### Enforcing order

When execution order matters, it must be represented explicitly.

The preferred mechanism is a causal relationship between rules.

For example:

```caret
damage = rule [
  ^T = attackTrigger
  ^E = applyDamage
]

death = rule [
  ^T = damageCompletionTrigger
  ^E = checkDeath
]
```

`death` cannot precede completion of `damage`.

This is a semantic dependency rather than a source-order convention.

---

<a id="rule-chaining"></a>
## Rule chaining

<a id="explicit-chain"></a>
### Explicit chain

A sequence of rules may be defined explicitly through rule contexts:

```caret
first = rule [
  ^T = startTrigger
  ^E = firstEffect
]

second = rule [
  ^T = firstCompletionTrigger
  ^E = secondEffect
]

third = rule [
  ^T = secondCompletionTrigger
  ^E = thirdEffect
]
```

This imposes:

```text
first
  ↓
second
  ↓
third
```

---

<a id="chain-sugar"></a>
### `chain` sugar

Caret should provide concise sugar for this common pattern:

```caret
chain [
  rule [
    ^T = startTrigger
    ^E = firstEffect
  ]
  rule [
    ^E = secondEffect
  ]
  rule [
    ^E = thirdEffect
  ]
]
```

This is equivalent to connecting each subsequent rule to:

```caret
fall @previous.context
```

The Collection supplied to `chain` contains ordinary Rule values. The chain therefore compiles to
ordinary rules and ordinary contexts; it does not license CATEN clause parsing.

It does not introduce a separate execution mechanism.

---

<a id="explicit-trigger-in-a-chain"></a>
### Explicit trigger in a chain

A chained rule may additionally specify a trigger:

```caret
chain [
  rule [
    ^T = startTrigger
    ^E = first
  ]
  rule [
    ^T = readyTrigger
    ^E = second
  ]
]
```

The effective trigger of the second rule is conceptually:

```caret
fall @previous.context and ready
```

Thus `ready` must hold at the completion front of the previous rule.

If the desired meaning is instead:

> first must have completed, then wait however long necessary for `ready`

that should be represented using a persistent context rather than ordinary chain-front semantics.

---

<a id="partial-ordering"></a>
### Partial ordering

Rule dependencies may form a partial order rather than a single sequence.

Conceptually:

```text
       A
      / \
     B   C
      \ /
       D
```

`B` and `C` have no ordering relationship and may therefore execute in arbitrary order.

Both are constrained to occur after `A`.

`D` is constrained by both branches.

This is intentional.

Caret should constrain only those rule relationships explicitly expressed by the program.

Independent branches remain unordered.

Numeric priorities or implicit source-order priorities are not required for the core rule model.

---

<a id="rulesets"></a>
## Rulesets

<a id="overview-2"></a>
### Overview

A `RuleSet` is a first-class reusable rule-system value. It has a lexical implementation
environment containing rules and supporting declarations, plus a public named interface formed by
its `^` exports. The private lexical environment is not a first-class Scope or Collection value.

Rulesets may contain:

* rules;
* contexts;
* helper functions;
* data;
* configuration;
* nested rulesets;
* private implementation state.

Example:

```caret
Combat attacker target damage =
  ruleset
    prepare = rule [
      ^T = attackerRequestTrigger
      ^E = prepareAttackerEffect
    ]

    ^attack = rule [
      ^T = prepareCompletionTrigger
      ^E = applyTargetDamageEffect
    ]

    cleanup = rule [
      ^T = attackCompletionTrigger
      ^E = finishAttackerEffect
    ]
```

---

<a id="ruleset-templates"></a>
### Ruleset templates

Caret does not require a separate template language for rulesets.

An ordinary function returning a `RuleSet` acts as a template:

```caret
Combat attacker target damage =
  ruleset
    ...
```

Its ordinary Caret parameters are the ruleset holes.

Ruleset parameters may include:

* objects;
* contexts;
* functions;
* rules;
* rulesets;
* collections;
* predicates;
* formats;
* configuration values;
* effect functions.

Normal contracts may constrain them.

Normal partial application also applies:

```caret
standardCombat =
  Combat _ _ standardDamage
```

The remaining `_` positions are supplied when the template is instantiated.

---

<a id="ruleset-encapsulation"></a>
### Ruleset encapsulation

Members of a ruleset are private by default.

`^` exposes a member through the ruleset's public interface.

Example:

```caret
TurnSystem players =
  ruleset
    index = 0
    internalState = context down

    ^turn = context down

    ^next = rule [
      ^T = endTurnTrigger
      ^E = advancePlayerEffect
    ]
```

External code may access:

```caret
turnSystem.turn
turnSystem.next
```

but cannot access private bindings such as:

```caret
turnSystem.index
turnSystem.internalState
```

This uses the normal Caret meaning of `^`.

Rulesets do not introduce another visibility system.

---

<a id="exported-rules"></a>
### Exported rules

Rules are exported in exactly the same way:

```caret
Movement board pieces =
  ruleset
    validate = rule [
      ^T = validationTrigger
      ^E = validateMovement
    ]

    update = rule [
      ^T = updateTrigger
      ^E = updateMovement
    ]

    ^completed = rule [
      ^T = completionTrigger
    ]
```

External users may refer to:

```caret
movement.completed
@movement.completed
@movement.completed.context
```

Private internal rules remain inaccessible.

Exported rules and contexts provide stable integration points between ruleset libraries.

---

<a id="ruleset-instances"></a>
### Ruleset instances

Every ruleset construction creates an independent instance.

For example:

```caret
playerCombat = Combat player enemy damage
enemyCombat = Combat enemy player enemyDamage
```

must create independent runtime state for:

* active states;
* rule contexts;
* private contexts;
* private instance state;
* instance-local rules.

The ruleset definition may be shared, but runtime state belongs to each instance.

---

<a id="nested-rulesets"></a>
### Nested rulesets

Rulesets may build larger systems from smaller rulesets:

```caret
TurnBasedCombat players world damage =
  ruleset
    install TurnRules players
    install TargetSelection world
    install DamageRules world damage
    install DeathRules world
```

This allows reusable libraries to be assembled hierarchically.

---

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
        ^T = gameOverTrigger
        ^E = endCycle
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
  ^T = completedTrigger
  ^E = endCycle
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
  ^T = greaterThanTenTrigger
  ^E = effect
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
2. The ordinary unary `rule : RuleDefinition -> Rule` callable and optional CATEN named fields in
the general template-derived `RuleDefinition` contract:

```text
C Context
A Active
T Trigger
E Effect
N Name (string-literal ID)
```

The general optional-template-member capability is required for this initial rule model even
though its final surface declaration spelling remains unresolved.

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
