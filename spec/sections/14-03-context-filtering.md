# Context filtering

[Chapter index](../14-staging-compilation-and-compatibility.md) · [Language specification index](../../LANGUAGE.md)


<a id="context-filtering"></a>
## Context filtering

<a id="context-values-rather-than-names"></a>
### Context values rather than names

Compile-time rule filtering should normally compare or inspect actual context values rather than their textual names.

Prefer:

```caret
rule.context contains shared.client
```

over:

```caret
rule.context contains "client"
```

when `shared.client` is the context being selected.

The first form refers to the actual exported context value.

It therefore participates in normal Caret identity, reflection, renaming, and static analysis.

Strings remain appropriate only when an API intentionally operates on names.

---

<a id="complex-context-expressions"></a>
### Complex context expressions

A RuleDefinition `C` field may refer to ordinary first-class context combinations such as:

```caret
client and authenticated
```

or:

```caret
client or server
```

The filtering predicate may use ordinary context-inspection functions to determine whether a rule is relevant.

The simple example:

```caret
rule.context contains shared.client
```

is sufficient when structural containment expresses the desired criterion.

More sophisticated selection may use ordinary predicates such as:

```caret
contextCompatible rule.context targetContext
```

without changing the compile-time mechanism.

For example:

```caret
clientRules =
  # shared.interaction filter $
    rule ->
      contextCompatible rule.context shared.client
```

Context compatibility policy belongs to context/ruleset functions, not to `#`.

---

<a id="filtering-and-dependency-closure"></a>
## Filtering and dependency closure

<a id="filter-selects-rules"></a>
### `filter` selects rules

When filtering a ruleset:

```caret
selected =
  # rules filter predicate
```

`filter` determines which rules are present in the resulting ruleset.

It does not need to manually enumerate every function, contract, format, or helper referenced by those rules.

For example, if a selected rule calls:

```caret
encode LoginRequest request
```

the selected rule retains its semantic references to:

```text
encode
LoginRequest
```

Normal reachability analysis keeps those required definitions.

The programmer should therefore specify:

```text
which rules belong to the resulting ruleset
```

rather than:

```text
every source declaration that must appear in the artifact
```

---

<a id="unselected-rules"></a>
### Unselected rules

A rule removed by compile-time filtering is not part of the resulting runtime ruleset.

If no remaining runtime definition depends on it, it is unreachable and need not be emitted.

Dependencies used only by that rule likewise need not be emitted.

Thus:

```caret
```caret
<a id="shared-import-clientserver-5"></a>
```caret
# shared = import clientServer
```
```

clientRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.client
```

does not imply that the client artifact contains `shared.interaction` in its original complete form.

Only the resulting `clientRules` value and runtime-reachable dependencies matter.

---

<a id="generality"></a>
## Generality

The mechanism is not specific to client/server programs.

Different compilation roots may filter or transform shared code using any compile-time criterion expressible in Caret.

Examples may include:

```text
desktop / browser
CPU / GPU
editor / runtime
production / test
different embedded devices
different protocol roles
different application editions
different rule-system participants
```

For example:

```caret
```caret
<a id="shared-import-platform-rulescaret"></a>
```caret
# shared = import "platform-rules.caret"
```
```

browserRules =
  # shared.rules filter $
    rule ->
      rule.context contains shared.browser
```

The compiler does not need a built-in concept of `browser`, `client`, `server`, or `agent`.

These are ordinary program values interpreted by compile-time Caret code.

---
