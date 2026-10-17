# Container integration and core rules

[Chapter index](../07-state-containers-and-scoped-lookup.md) · [Language specification index](../../LANGUAGE.md)


## Phase 4 integration boundary

The [Collection protocol revision](06-01-collection-protocol.md#phase-4-collection-protocol-revision-implemented-with-deferred-extensions)
defines unpublished construction and settlement. Constructing code can inspect and edit an
unfinished Collection, but outside code cannot access it until settlement. This is distinct from
a stable-identity mutable container. Settled Collection updates produce new values. `eager`
preserves container references and never reads or freezes their contents.

The deferred `addElement`, `removeElement`, and `replaceElement` names have distinct receiver/result
contracts: construction edits return the affected Field (the old Field for replacement) or missing;
settled updates return an immutable Collection and preserve the original. The
[Collection specification](06-01-collection-protocol.md#element-operations-during-construction-and-after-settlement)
owns their selection and no-change rules. Sequential construction does not expose numeric
indices until settlement; its first-equal-value selection rule is explicitly provisional.
These public operations, their construction-selection interface, and additional immutable-update
syntax are outside Phase 4, with no later phase assigned. Internal construction/settlement and
existing persistent primitives remain available; this deferral does not postpone containers or `put`.

### Implemented with binding over lazy Collections

During compilation or interpreter analysis, identify unqualified names in the block that need
lookup through `with`. Exclude names resolved to local declarations and explicit `outer` paths.
Keep declaration predeclaration and initialization checks; a local binding that is not initialized
does not fall back to a member.

On execution, evaluate the target once and establish all required name bindings before executing
the body. Match the identified names against the target's enumerated public keys; do not create
bindings for every unrelated key. Enumeration can stop once all required names match. Otherwise
it must finish to establish absence before unmatched names resolve to enclosing bindings.
An infinite enumeration may therefore prevent body execution when a required name cannot be found.
Enumeration effects occur before the body and follow ordinary effect contracts.

A matched member is a lazy binding to the target's member; its value is obtained when needed,
without copying fields or widening authority. A member whose value is `~` still shadows an outer
binding. Do not probe `getElement` and interpret missing as absence: existence for lexical lookup
comes from enumerated keys. A provider-accessible but unlisted key remains available through
explicit access, but does not introduce a name into `with`.

For example, conceptually a target that enumerates only `"name"` but also permits explicit lookup
of `"age"` does not shadow an outer `age` binding. Conversely, enumerating `"age"` shadows it
even when that member's value is missing. `outer.age` remains the explicit enclosing path.

These implemented binding rules refine the runtime selection of dynamic members described below;
the complete target shape need not be statically known. Resolver-only, export, and sandbox
restrictions on `with`/`outer` remain authoritative. Custom provider construction is deferred; these lookup rules also guide
the implementation of built-in lazy Collections.

<a id="mutability-containers"></a>
## Mutability Containers

The Java 21 prototype implements `{ value }`, `{ (Contract...) value }`, `Container T`,
postfix `container{}`, and ordinary `put container value`. The inferred or explicit content
requirements remain fixed for the life of the container; replacement validates before mutation.
`Container T` matches a cell with one equivalent invariant content descriptor. A conjunction or
additional refinement remains enforced on writes but cannot be widened to one of its components.
Aliases, captures, calls, and immutable Collections share its stable identity. `==` compares that
identity, while observing the content requires an explicit read. Reads and writes contribute
`StateRead` and `StateWrite` to callable inference and pure-function checks. Field-binding
reification and container content-contract metadata are implemented for current Collection kinds;
rules and sandbox projections remain later work. Java embedding
does not export mutable container values through its immutable `CaretValue` carrier.

<a id="overview"></a>
### Overview

Caret values are immutable by default.

When a program needs a piece of state that changes over time, Caret uses an explicit **mutability container**.

A container has stable identity, but the value stored inside it may be replaced.

Example:

```caret
health = { (Int) 100 }
```

`health` is a container.

Its identity does not change, but its current content may change from:

```text
100
```

to:

```text
80
```

or another value satisfying the container's content contract.

This provides **contained mutability**:

```text
immutable structure
       ↓
stable container
       ↓
replaceable value
```

The surrounding object does not need to become mutable merely because one of its fields changes over time.

---

<a id="container-literal"></a>
### Container literal

A mutability container is constructed using braces:

```caret
{ value }
```

Example:

```caret
health = { 100 }
```

The compiler may infer the content contract from the initial value.

An explicit content contract may be provided inside the braces:

```caret
health = { (Int) 100 }
```

The contract applies to the contents of the container, not to the container itself.

Therefore every future value placed into `health` must satisfy `Int`.

For example:

```caret
put health 75
```

is valid.

```caret
put health "dead"
```

is invalid.

Multiple contracts may be used normally:

```caret
health = { (Int nonnegative) 100 }
```

The initial value and every future value must satisfy all specified contracts.

---

<a id="container-type"></a>
### Container type

Conceptually:

```text
Container T
```

means:

> a stable container whose current content satisfies `T`.

For example:

```caret
health = { (Int) 100 }
```

has a contract conceptually equivalent to:

```text
Container Int
```

The exact parameterized contract syntax for referring directly to container types follows the normal Caret contract system.

---

<a id="containers-are-values"></a>
### Containers are values

A container is itself an ordinary first-class Caret value.

It may be:

* assigned to bindings;
* stored in collections;
* stored in fields;
* passed to functions;
* returned from functions;
* shared between several immutable structures;
* reified;
* inspected through reflection where permitted.

Assigning a container does not copy its current content into a new container.

It copies or shares the reference to the same stable container identity.

For example:

```caret
health = { (Int) 100 }

player =
  ^health = health

healthBar =
  ^health = health

enemyAI =
  ^targetHealth = health
```

All three objects refer to the same container:

```text
health ───────────────┐
player.health ────────┼──> { 100 }
healthBar.health ─────┤
enemyAI.targetHealth ─┘
```

If:

```caret
put health 75
```

is executed, then:

```caret
player.health{}
healthBar.health{}
enemyAI.targetHealth{}
```

all observe:

```text
75
```

The enclosing `player`, `healthBar`, and `enemyAI` values remain immutable.

---

<a id="reading-container-contents"></a>
### Reading container contents

Reading the current content of a container is explicit.

The syntax is:

```caret
container{}
```

Example:

```caret
health{}
```

returns the current content of `health`.

If:

```caret
health = { (Int) 100 }
```

then:

```caret
health{}
```

initially evaluates to:

```text
100
```

Reading through a field works the same way:

```caret
player.health{}
```

The distinction is:

```caret
player.health
```

returns the container value.

```caret
player.health{}
```

returns the current value stored inside that container.

This distinction is intentional.

Caret does not implicitly dereference mutable containers during ordinary field access.

---

<a id="updating-container-contents"></a>
### Updating container contents

The standard operation for replacing container contents is:

```caret
put container value
```

Example:

```caret
put health 80
```

changes the current contents of `health` to `80`.

For a field:

```caret
put player.health 80
```

changes the contents of the container stored in `player.health`.

It does not replace the `health` field of `player`.

The immutable relationship:

```text
player.health -> container
```

remains unchanged.

Only:

```text
container current content
```

changes.

---

<a id="updating-based-on-the-previous-value"></a>
### Updating based on the previous value

The current value may be read, transformed, and written back:

```caret
damage player amount =
  put player.health
    (player.health{} - amount)
```

Conceptually:

```text
old = player.health{}
new = old - amount
put player.health new
```

Ordinary functions and partial application may be used normally.

Caret does not require special increment, decrement, or field-mutation syntax.

---

<a id="field-reification"></a>
### Field reification

Ordinary field access and field reification are distinct.

```caret
player.health
```

evaluates the field and returns the value stored in that field.

If the field contains a container, this returns the container.

To reify the field itself:

```caret
player.@health
```

This produces a reference to the `health` field binding rather than evaluating it normally.

The general distinction is:

```text
object.field
    evaluate/access the field

object.@field
    reify/reference the field itself
```

This is independent of mutability.

For a container field:

```caret
player.health
```

is the container.

```caret
player.health{}
```

is the current content.

```caret
player.@health
```

is the reified field.

These are three distinct values and must not be conflated.

In the implemented named-Collection subset, `object.@field` is a projected, language-owned
`FieldBinding` metadata reference. Its `key`, `mutable`, `exported`, `contracts`, `nullable`,
`optional`, and `owner` facts describe the binding without reading a container's content.
`mutable` is false for an immutable field even when its value is a mutable container. A
contracted exported field exposes its declared contract references; an unconstrained field uses
`~` for unknown nullability and optionality. The opaque reference's adjacent `:` recovers the
same `Field` tuple when dereference authority permits it. Ordinary `@fieldTuple` reflection keeps
its existing `key` and `value` fields.

Field identity follows the actual Field instance. Reusing that Field in another Collection
preserves identity; constructing a new Field from the same key and value does not. The `owner`
fact is `~` until an owner is known, one metadata reference for one visible owner, or a Collection
of references for multiple visible owners. Owner discovery occurs when that Field is reified
through an owner. Observation may add another known owner but cannot disclose owners hidden from
the observing environment. Field order belongs to each owner's public enumeration metadata,
not to the Field. Ownership here means semantic containment, not the interpreter's private
storage-reuse/uniqueness tracker.

`@container` exposes stable identity and visible `contentContracts` references, never current
content or host storage. A container's content changes only through `put`; metadata inspection
needs neither `StateRead` nor `StateWrite`. Root/module/sandbox projection kinds remain planned.
Reifying a field through a lazy keyed Collection demands only the entries needed to find its key;
it uses that provider's ordinary effects and caches the established Field. In the current
interpreter, functions that may perform provider lookup receive a conservative upper bound of
all effects visible in their environment. This bound does not mean reification itself reads
container content or grants those effects as authority.

---

<a id="is-not-required-for-sharing-containers"></a>
### `@` is not required for sharing containers

Because containers are ordinary first-class values, sharing a container does not require reference-assignment syntax.

For example:

```caret
health = { 100 }

player =
  ^health = health
```

is sufficient.

The field receives the container itself.

It is not necessary to write:

```caret
^health = @health
```

because `@health` would refer to the binding `health`, not to the container value stored in that binding.

Likewise, no special operator such as `@=` is required for container sharing.

Ordinary value assignment already has the intended semantics.

---

<a id="contained-mutability"></a>
### Contained mutability

A mutable container does not make surrounding structures mutable.

Example:

```caret
player =
  [
    ^name = "Alice"
    ^health = { (Int) 100 }
    ^score = { (Int) 0 }
  ]
```

The structure of `player` is immutable.

The following relationships remain fixed:

```text
player.name   -> "Alice"
player.health -> health container
player.score  -> score container
```

Only the contents of explicitly introduced containers may change.

This makes mutation boundaries visible in the value definition.

Compare:

```caret
^name = "Alice"
```

with:

```caret
^health = { (Int) 100 }
```

The first is immutable data.

The second explicitly introduces mutable state.

---

<a id="no-implicit-deep-mutability"></a>
### No implicit deep mutability

A container makes only its own content replaceable.

It does not recursively make nested values mutable.

For example:

```caret
inventory =
  {
    (Collection Item)
    [sword potion]
  }
```

The container is mutable.

The collection:

```caret
[sword potion]
```

is still an ordinary immutable collection.

Adding an item means replacing the container contents with another collection:

```caret
put inventory
  (inventory{} add key)
```

Conceptually:

```text
same container identity

old content:
    [sword potion]

new content:
    [sword potion key]
```

The runtime may use structural sharing or other optimizations to avoid unnecessary copying.

---

<a id="nested-mutability"></a>
### Nested mutability

Mutability may be introduced at any structural level.

For example:

```caret
player =
  [
    ^name = "Alice"

    ^stats = [
      ^health = { (Int) 100 }
      ^strength = 15
    ]
  ]
```

Here:

```caret
player.stats.health{}
```

reads mutable state.

But:

```caret
player.stats.strength
```

is immutable.

Putting a container around a larger structure has different semantics:

```caret
player =
  [
    ^stats = {
      [
        ^health = 100
        ^strength = 15
      ]
    }
  ]
```

Here the entire `stats` value may be replaced:

```caret
put player.stats newStats
```

but its internal `health` and `strength` fields are not independently mutable unless they themselves contain containers.

---
