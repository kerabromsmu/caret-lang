# Rules

[Chapter index](../11-rules-rulesets-and-objects.md) · [Language specification index](../../LANGUAGE.md)



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

Every constructed RuleDefinition contains all CATEN fields. A directly contextual definition
literal may omit a field whose template requirement directly uses `T~` or `T?~`; construction then
materializes the field with `~` to select the documented default. Established Collections still
require the complete exact shape.

`rule` is an ordinary unary Caret function:

```text
rule : RuleDefinition -> Rule
```

`RuleDefinition` is a structural contract produced through the ordinary `template` function. Its
required named Collection fields with optional values are `C`, `A`, `T`, `E`, and `N`; those letters are field names,
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
`RuleDefinition` uses the general exact-template model: every CATEN field is present, and `~`
selects its default. Because the ordinary unary `rule` callable has a statically known
`RuleDefinition` parameter, a directly supplied literal receives the expected template and
materializes eligible omitted fields as `~`. The exact first-class contracts for deferred `C`, `T`,
and `E` values remain unresolved dependencies. Implementations must not substitute a rule-specific
AST, lazy-expression wrapper, or parser exception. `C` must retain the persistent/context behavior
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
  ^A = ~
  ^T = attackTrigger
  ^E = performAttack
  ^N = ~
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
  ^C = ~
  ^A = off
  ^T = specialTrigger
  ^E = performSpecialAttack
  ^N = ~
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
  ^C = ~
  ^A = ~
  ^T = deathTrigger
  ^E = destroyPlayer
  ^N = ~
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
  ^C = ~
  ^A = ~
  ^T = beginTurnTrigger
  ^E = prepareTurn
  ^N = ~
]

resume = rule [
  ^C = ~
  ^A = ~
  ^T = resumeTrigger
  ^E = resumeGame
  ^N = ~
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
  ^A = ~
  ^T = enemyDeathTrigger
  ^E = ~
  ^N = ~
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
  ^C = ~
  ^A = ~
  ^T = combatEnemyDeathTrigger
  ^E = ~
  ^N = ~
]
```

---

<a id="effect"></a>
### Effect

`E` contains the changes caused by application of the rule.

Example:

```caret
capture = rule [
  ^C = ~
  ^A = ~
  ^T = validCaptureTrigger
  ^E = captureEffect
  ^N = ~
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
  ^C = ~
  ^A = ~
  ^T = validCaptureTrigger
  ^E = capturePiece
  ^N = "capture"
]
```

Assignment does not derive `N` from the binding name. A contextual RuleDefinition literal may omit
`N`, in which case its direct missing-capable template requirement supplies `~`:

```caret
capture = rule [
  ^C = ~
  ^A = ~
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
  ^C = ~
  ^A = ~
  ^T = captureTrigger
  ^E = ~
  ^N = "capture"
]
```

---

<a id="optional-caten-components"></a>
### Optional CATEN components

Every CATEN field is required by the general exact-template contract and exists in the completed
RuleDefinition value. A directly supplied Collection literal is constructed under the known
`RuleDefinition` parameter template, so omission of an eligible direct `T~` or `T?~` field
materializes that field with `~`. Explicitly supplying `~` selects the same default. Null does not
select these defaults.

This is ordinary expected-template literal construction, not special behavior in `rule`. An
already established Collection with an absent CATEN field fails RuleDefinition membership, and the
`rule` callable never mutates or completes it. Aliases that merely accept missing do not enable
literal omission.

Recommended defaults are:

```text
C = ~  -> always up
A = ~  -> initially on
T = ~  -> no autonomous trigger
E = ~  -> no explicit effect
N = ~  -> anonymous/internal identity
```

A rule with `E = ~` still produces its implicit rule context when applied.

A rule with `T = ~` may still participate in mechanisms such as explicit invocation or chaining.

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
  ^C = ~
  ^A = ~
  ^T = captureRequestedTrigger
  ^E = capturePiece
  ^N = ~
]

score = rule [
  ^C = ~
  ^A = ~
  ^T = captureCompletionTrigger
  ^E = addCaptureScore
  ^N = ~
]
```

The implicit context exists even when the rule has no explicit `E`.

---
