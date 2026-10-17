# Rule ordering and chaining

[Chapter index](../11-rules-rulesets-and-objects.md) · [Language specification index](../../LANGUAGE.md)


<a id="rule-ordering"></a>
## Rule ordering

<a id="unordered-rules"></a>
### Unordered rules

Rule definition order does **not** imply execution order.

If several rules are simultaneously applicable and no ordering relationship between them has been specified, the `ruleCycle` may choose any of them.

For example:

```caret
a = rule [
  ^C = ~
  ^A = ~
  ^T = eventTrigger
  ^E = effectA
  ^N = ~
]

b = rule [
  ^C = ~
  ^A = ~
  ^T = eventTrigger
  ^E = effectB
  ^N = ~
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
  ^C = ~
  ^A = ~
  ^T = conditionTrigger
  ^E = disableSomething
  ^N = ~
]

b = rule [
  ^C = ~
  ^A = ~
  ^T = enabledConditionTrigger
  ^E = otherEffect
  ^N = ~
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
  ^C = ~
  ^A = ~
  ^T = eventTrigger
  ^E = updateAmbientEffect
  ^N = ~
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
  ^C = ~
  ^A = ~
  ^T = attackTrigger
  ^E = applyDamage
  ^N = ~
]

death = rule [
  ^C = ~
  ^A = ~
  ^T = damageCompletionTrigger
  ^E = checkDeath
  ^N = ~
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
  ^C = ~
  ^A = ~
  ^T = startTrigger
  ^E = firstEffect
  ^N = ~
]

second = rule [
  ^C = ~
  ^A = ~
  ^T = firstCompletionTrigger
  ^E = secondEffect
  ^N = ~
]

third = rule [
  ^C = ~
  ^A = ~
  ^T = secondCompletionTrigger
  ^E = thirdEffect
  ^N = ~
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
    ^C = ~
    ^A = ~
    ^T = startTrigger
    ^E = firstEffect
    ^N = ~
  ]
  rule [
    ^C = ~
    ^A = ~
    ^T = ~
    ^E = secondEffect
    ^N = ~
  ]
  rule [
    ^C = ~
    ^A = ~
    ^T = ~
    ^E = thirdEffect
    ^N = ~
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
    ^C = ~
    ^A = ~
    ^T = startTrigger
    ^E = first
    ^N = ~
  ]
  rule [
    ^C = ~
    ^A = ~
    ^T = readyTrigger
    ^E = second
    ^N = ~
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
