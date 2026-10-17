# Rulesets

[Chapter index](../11-rules-rulesets-and-objects.md) · [Language specification index](../../LANGUAGE.md)


<a id="rulesets"></a>
## Rulesets

<a id="overview-2"></a>
<a id="overview-1"></a>
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
      ^C = ~
      ^A = ~
      ^T = attackerRequestTrigger
      ^E = prepareAttackerEffect
      ^N = ~
    ]

    ^attack = rule [
      ^C = ~
      ^A = ~
      ^T = prepareCompletionTrigger
      ^E = applyTargetDamageEffect
      ^N = ~
    ]

    cleanup = rule [
      ^C = ~
      ^A = ~
      ^T = attackCompletionTrigger
      ^E = finishAttackerEffect
      ^N = ~
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
      ^C = ~
      ^A = ~
      ^T = endTurnTrigger
      ^E = advancePlayerEffect
      ^N = ~
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
      ^C = ~
      ^A = ~
      ^T = validationTrigger
      ^E = validateMovement
      ^N = ~
    ]

    update = rule [
      ^C = ~
      ^A = ~
      ^T = updateTrigger
      ^E = updateMovement
      ^N = ~
    ]

    ^completed = rule [
      ^C = ~
      ^A = ~
      ^T = completionTrigger
      ^E = ~
      ^N = ~
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
