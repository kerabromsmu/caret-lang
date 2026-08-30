<a id="state-containers-and-scoped-lookup"></a>
# State, Containers, and Scoped Lookup

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

<a id="mutability-containers"></a>
## Mutability Containers

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

<a id="mutable-state-and-purity"></a>
### Mutable state and purity

Reading mutable state is observable.

Therefore:

```caret
health{}
```

must not be treated as an ordinary pure value lookup.

For example:

```caret
alive player =
  player.health{} > 0
```

can return different results at different times for the same immutable `player` value.

The function therefore depends on mutable state.

Similarly:

```caret
put player.health 50
```

changes mutable state.

Caret's effect system must distinguish computations that:

```text
do not observe mutable state
read mutable state
modify mutable state
```

The initial built-in effects are named `StateRead` and `StateWrite`.

Conceptually:

```text
container
    pure value access

container{}
    StateRead

put container value
    StateWrite
```

`StateWrite` grants neither an implicit read nor any authority by itself. Code that both reads and
writes declares or infers both effects, and the execution environment must separately supply access
to the particular container.

No mutable read or write may silently appear in a function that is required to be pure.

---

<a id="pure-access-to-a-container-reference"></a>
### Pure access to a container reference

Accessing the container itself does not read its mutable contents.

For example:

```caret
getHealthContainer player =
  player.health
```

may remain pure.

It returns the stable container reference.

By contrast:

```caret
getHealth player =
  player.health{}
```

observes mutable state.

This distinction allows immutable structures containing containers to be passed, compared by identity where appropriate, stored, and composed without automatically making every operation on those structures effectful.

---

<a id="contracts-and-put"></a>
### Contracts and `put`

A container's content contract is enforced whenever its contents change.

For:

```caret
health = { (Int nonnegative) 100 }
```

the following is valid:

```caret
put health 50
```

while:

```caret
put health -1
```

violates the container contract.

The compiler should reject invalid writes statically where possible.

If the new value cannot be proven statically, the ordinary runtime contract mechanism applies.

A container must never silently transition into a value that violates its declared content contract.
Validation happens before replacement. A failed dynamic check produces the ordinary located
contract-violation diagnostic and leaves the previous content unchanged.

After a successful replacement, `put` returns the newly stored value. This makes `put` usable as an
ordinary expression without introducing a second assignment-like result convention.

---

<a id="contract-widening-and-inference"></a>
### Contract widening and inference

When no explicit contract is provided:

```caret
value = { 100 }
```

the compiler may infer a content contract from the initial value.

If broader future contents are intended, the broader contract should be explicit:

```caret
value = { (Number) 100 }
```

This permits:

```caret
put value 3.14
```

provided `Float` satisfies `Number`.

The inferred or explicit content contract is part of the container's stable metadata.

It must not change merely because different values are later stored.

---

<a id="container-identity"></a>
### Container identity

A container has stable identity independent of its current content.

Therefore two containers:

```caret
a = { 100 }
b = { 100 }
```

have equal current contents but are distinct containers.

Updating:

```caret
put a 50
```

does not affect `b`.

By contrast:

```caret
a = { 100 }
b = a
```

makes `a` and `b` refer to the same container.

Then:

```caret
put a 50
```

causes:

```caret
b{}
```

to return `50`.

Container identity is therefore a meaningful runtime property. Ordinary `==` compares containers
by this identity: aliases of the same container compare equal, while independently constructed
containers compare unequal even when their current contents are equal. Comparing current contents
requires explicit reads, such as `a{} == b{}`, and therefore has `StateRead` effects.

Container identity is local to the current runtime environment generation. Persistence or identity
across unloading, reloading, or process restarts requires a separately specified facility.

---

<a id="containers-in-collections"></a>
### Containers in collections

Containers may appear inside ordinary collections:

```caret
values =
  [
    { (Int) 10 }
    { (Int) 20 }
    { (Int) 30 }
  ]
```

The collection itself remains immutable unless it is placed inside another container.

Individual cells may still change:

```caret
put values[1] 25
```

provided indexing returns the container at that position.

This does not replace the collection element structurally.

It changes the contents of the stable container referenced by that element.

---

<a id="containers-and-shared-metadata"></a>
### Containers and shared metadata

A collection of containers may share common metadata.

For example:

```caret
(Collection (Container Int)) counters =
  [
    { (Int) 0 }
    { (Int) 0 }
    { (Int) 0 }
  ]
```

may store:

```text
element metadata:
    Container Int
```

once at the collection level.

The individual elements need only preserve their separate container identities and current contents.

This follows Caret's normal collection metadata rules.

---

<a id="containers-and-templates"></a>
### Containers and templates

Templates may require container-valued positions.

For example, conceptually:

```caret
Player =
  template [
    ^name = (String) _
    ^health = (Container Int) _
  ]
```

A matching value may be:

```caret
[
  ^name = "Alice"
  ^health = { (Int) 100 }
]
```

The template constrains the field to contain a container whose content contract satisfies `Int`.

The template does not automatically dereference the container.

If a template needs to constrain the current mutable content rather than the container type, that requires an explicit predicate that performs a mutable-state read and therefore participates in the effect system.

Pure structural template matching should not silently read mutable container contents.

---

<a id="containers-and-rulecycle"></a>
### Containers and `ruleCycle`

Containers provide a natural representation for mutable object state inside `ruleCycle`.

Example:

```caret
player =
  [
    ^name = "Alice"
    ^health = { (Int nonnegative) 100 }
    ^score = { (Int) 0 }
  ]
```

A rule may change the health:

```caret
damage = rule
  T hit player
  E
    put player.health
      (player.health{} - hit.damage)
```

The surrounding `player` value remains immutable.

The rule changes only the explicitly mutable health container.

Containers complement rather than replace the persistent previous/next-state model of `cycle` and
`ruleCycle`. Exported next-state writes still form one atomic state transition. Container writes are
separate observable effects on explicitly shared identities and are not silently rolled into or
rolled back with that persistent-state commit.

---

<a id="reactive-dependency-tracking"></a>
### Reactive dependency tracking

A mutable-state read gives the `ruleCycle` a precise dependency.

For example:

```caret
death = rule
  T player.health{} <= 0
  E destroy player
```

depends on:

```caret
player.health
```

When:

```caret
put player.health newHealth
```

changes that container, the rule engine knows that `death` may need reevaluation.

An unrelated container change does not require reevaluating triggers that do not depend on it.

Containers therefore provide a natural unit of reactive dependency tracking.

Conceptually:

```text
container read
    ↓
dependency recorded

put to container
    ↓
dependent rules become candidates for reevaluation
```

This enables efficient rule-cycle execution without treating arbitrary object memory as mutable.
A successful `put` records the changed container and queues rules whose last relevant evaluation
read that identity. It does not require reevaluating rules dependent only on other containers.

---

<a id="context-changes-derived-from-containers"></a>
### Context changes derived from containers

A rule cycle may derive contexts from mutable container values.

For example:

```text
player.health{} > 0
    -> player alive context up

player.health{} <= 0
    -> player alive context down
```

Then:

```caret
put player.health 0
```

may cause:

```text
player.alive:
    up -> down
```

which produces:

```caret
fall player.alive
```

and may trigger another rule.

The exact derived-context declaration mechanism is specified separately, but container updates are a primary source of observable state change inside rule cycles.

---

<a id="containers-and-sandboxes"></a>
### Containers and sandboxes

A container passed into a sandbox is a capability to observe and potentially modify shared mutable state.

The sandbox boundary must therefore preserve access restrictions.

A host may choose to expose:

* the real container;
* a read-only projection;
* a mediated container;
* a copied snapshot;
* a virtual replacement.

For example, a sandbox may be allowed to read:

```caret
settings{}
```

without being given a `put` capability that can modify the host's real settings.

The exact capability interface may be represented through contracts and sandbox projections.

Reflection must not allow sandboxed code to obtain unrestricted mutable access merely because it can inspect a container reference.
Reflective metadata and field reification do not add `StateRead` or `StateWrite` authority. A
sandbox projection may expose identity and metadata, readable contents, writable contents, or a
snapshot only as explicitly selected by its environment.

---

<a id="containers-and-concurrency"></a>
### Containers and concurrency

Containers introduce shared mutable state and therefore require defined concurrency semantics if multiple computations may access them concurrently.

The initial language model does not require a particular concurrency mechanism.

A future implementation may provide:

* atomic containers;
* synchronization contracts;
* transactional updates;
* isolated actor ownership;
* thread-local containers;
* other concurrency policies.

Ordinary containers should not silently promise atomic multi-threaded mutation unless explicitly specified.

The core semantics only require stable identity and sequentially observable replacement of the contained value.

---

<a id="implementation-freedom"></a>
### Implementation freedom

The semantic model is:

```text
stable container identity
        +
replaceable contained value
```

The implementation is free to represent this using:

* a mutable machine-memory slot;
* an object containing a pointer;
* an atomic reference;
* an indirection table;
* runtime-managed state storage;
* another equivalent mechanism.

Replacing a contained immutable value does not require copying that value's entire object graph.

Persistent collections, structural sharing, ownership analysis, and uniqueness analysis may be used freely.

These optimizations must not change observable container identity or content semantics.

---

<a id="implementation-requirements"></a>
## Implementation requirements

The initial implementation should support at minimum:

1. Container literals:

```caret
{ value }
```

2. Explicit content contracts:

```caret
{ (Int) 100 }
```

3. Contract inference when no explicit content contract is given.

4. Stable container identity.

5. Explicit content reads:

```caret
container{}
```

6. Container updates using:

```caret
put container value
```

7. Contract validation on every `put`.

   Validation precedes replacement; failure leaves the old content unchanged, and success returns
   the newly stored value.

8. Containers stored in immutable fields and collections.

9. Sharing one container between multiple immutable structures.

10. No implicit deep mutability.

11. Explicit distinction between:

```caret
player.health
player.health{}
player.@health
```

12. `object.@field` as field reification.

13. `StateRead` effect inference for mutable-state reads.

14. `StateWrite` effect inference for mutable-state writes.

15. Purity rejection when mutable reads or writes occur in a pure function.

16. Container dependency tracking usable by `ruleCycle`.

17. Runtime reevaluation of dependent rules after `put`.

18. Reflection over containers subject to normal visibility and sandbox restrictions.

The initial implementation may postpone:

* atomic updates;
* transactions;
* concurrency-specific containers;
* revocable mutable capabilities;
* read-only container projections;
* lock-free containers;
* compare-and-swap operations;
* mutable slices;
* distributed shared containers;
* persistence of container identity across process restarts.

These later features must preserve the core contained-mutability model.

---

<a id="design-principle"></a>
## Design principle

Caret uses explicit containers to isolate mutable state inside otherwise immutable structures.

The core syntax is:

```caret
health = { (Int) 100 }

health{}          // read current content

put health 80     // replace current content
```

Containers may be shared normally:

```caret
player =
  ^health = health

healthBar =
  ^health = health
```

No special reference-assignment syntax is required.

Field access remains precise:

```caret
player.health
```

returns the stable container.

```caret
player.health{}
```

reads its current mutable content.

```caret
player.@health
```

reifies the field itself.

The central rule is:

> Structures remain immutable unless mutability is explicitly introduced with `{ ... }`. The container's identity remains stable; only its contained value may be replaced.

This keeps mutation local, visible, shareable, compatible with Caret's effect system, and naturally observable by reactive systems such as `ruleCycle`.

<a id="with-outer-and-low-precedence-application"></a>
## `with`, `outer`, and Low-Precedence Application

<a id="overview-2"></a>
### Overview

Caret provides two related mechanisms for reducing syntactic noise:

* `with` temporarily exposes the named members of a value directly in lexical lookup;
* `$` provides low-precedence function application, reducing the need for parentheses.

These features do not introduce new object or record types.

A value used with `with` may simply be a heterogeneous collection containing named fields.

For example:

```caret
number = 11

record =
  [
    ^name = "one"
    ^number = 10
    ^content = [1 2 3]
  ]

with record
  print name
  print number
  print outer.number
  map print content
```

Inside the `with` block:

```text
name
number
content
```

refer directly to exported named members of `record`.

`outer.number` refers to the `number` binding from the enclosing scope.

---

<a id="with"></a>
## `with`

<a id="basic-syntax"></a>
### Basic syntax

The general form is:

```caret
with value
  body
```

The body is determined by effective logical indentation. The planned `\\` and `\*` layout markers
may shift its physical baseline but do not change `with` name resolution or block semantics.

`with` and `outer` are reserved spellings and cannot be declared as bindings or parameters.

Example:

```caret
with player
  print name
  print health{}
```

The expression supplied to `with` is evaluated once.

Its accessible named members participate directly in name resolution throughout the body.
The target must expose a public named-member interface; otherwise evaluation produces a located
diagnostic. Statically known members should resolve during semantic analysis. When the target's
shape is dynamic, member selection is checked at runtime without weakening ordinary lexical or
visibility rules.

---

<a id="named-members"></a>
### Named members

`with` operates on values that expose named members.

For example:

```caret
person =
  [
    ^name = "Alice"
    ^age = 42
  ]

with person
  print name
  print age
```

There is no separate `Record` or `Scope` type required. A named Collection is the normal
first-class structured value. `with` temporarily exposes its fields to lexical name resolution;
the Collection does not become a lexical scope object.

Likewise, `with` may operate on:

* exported or otherwise constructed named Collections;
* rulesets through their public named interface;
* `@root`;
* sandbox roots;
* other values exposing named members.

Example:

```caret
with @root
  print code
```

subject to normal visibility and sandbox restrictions.

For `@root` and other metadata-only references, `with` exposes only the names already present on
the reference's public metadata interface. It does not turn metadata into binding authority or a
capability invocation path.

---

<a id="export-visibility"></a>
### Export visibility

Only members visible through the value's normal public interface participate in `with`.

For a ruleset:

```caret
system =
  ruleset
    privateState = 10
    ^publicState = 20
```

then:

```caret
with system
  print publicState
```

is valid.

The private binding:

```caret
privateState
```

does not become visible merely because `with` is used.

`with` must preserve normal Caret visibility rules.

---

<a id="name-resolution"></a>
## Name resolution

<a id="lookup-order"></a>
### Lookup order

Inside a `with` block, unqualified names are resolved in the following order:

```text
1. local bindings declared in the current lexical block
2. named members exposed by the current `with` value
3. named members from enclosing `with` layers, innermost first
4. enclosing lexical bindings according to ordinary parent lookup
```

For example:

```caret
number = 11

record =
  [
    ^number = 10
  ]

with record
  print number
```

prints:

```text
10
```

because the exposed `record.number` shadows the enclosing `number`.

---

<a id="local-bindings-inside-with"></a>
### Local bindings inside `with`

A local binding declared inside the block has higher precedence than a member supplied by `with`.
As in ordinary blocks, declarations are resolved for the whole block: reading such a local before
its initialization reports `READ_BEFORE_INITIALIZATION` rather than falling back to a same-named
`with` member.

Example:

```caret
number = 11

record =
  [
    ^number = 10
  ]

with record
  number = 20

  print number
  print outer.number
```

Here:

```caret
number
```

refers to the local value `20`.

`outer.number` explicitly traverses the next lexical-resolution layer. In this example that is the
enclosing `with` layer or lexical environment according to the nesting already specified.

If direct access to the original structured value remains available, its member can still be accessed explicitly:

```caret
record.number
```

---

<a id="outer"></a>
## `outer`

<a id="enclosing-scope-access"></a>
### Enclosing scope access

Inside a `with` block, `outer` refers to the immediately enclosing lexical environment.

Example:

```caret
number = 11

record =
  [
    ^number = 10
  ]

with record
  print number
  print outer.number
```

produces:

```text
10
11
```

`outer` is a reserved, resolver-owned lexical path used for explicit lookup. It does not
materialize the enclosing environment as a first-class scope: bare `outer` cannot be stored,
passed, called, dynamically indexed, or reflected. Only member traversal such as `outer.name` and
`outer.outer.name` is valid. This prevents `with` from exposing private lexical bindings through
reflection or dynamic lookup.

---

<a id="nested-with"></a>
### Nested `with`

`with` blocks may be nested.

Example:

```caret
x = 1

a =
  [
    ^x = 2
  ]

b =
  [
    ^x = 3
  ]

with a
  with b
    print x
    print outer.x
    print outer.outer.x
```

produces:

```text
3
2
1
```

The lexical-resolution layers are conceptually:

```text
inner with b
    ↓ outer
with a
    ↓ outer
enclosing lexical scope
```

---

<a id="outerouter"></a>
### `outer.outer`

`outer` may be followed repeatedly:

```caret
outer.outer.name
outer.outer.outer.value
```

Each `outer` moves one level outward through the lexical-resolution layers.

Each step crosses one enclosing `with` lookup layer. After the outermost `with`, lookup continues
in the ordinary enclosing lexical scope. Normal initialization and visibility rules still apply;
`outer` grants no authority and cannot bypass module, export, root, or sandbox boundaries.

This provides explicit access to shadowed bindings without introducing multi-object `with` syntax.

Caret should prefer nested `with` blocks over constructs such as:

```caret
with a b c
```

because nesting makes lookup precedence visible and deterministic.

---

<a id="with-does-not-copy-fields"></a>
## `with` does not copy fields

`with` changes name resolution only.

It does not destructure or copy the value.

For:

```caret
with player
  print health{}
```

the name:

```caret
health
```

refers to the actual exported member of `player`.

This matters for containers.

Example:

```caret
player =
  [
    ^health = { (Int) 100 }
  ]

with player
  put health 75
```

changes the same container accessible as:

```caret
player.health
```

Afterward:

```caret
player.health{}
```

returns:

```text
75
```

No local copy of the container was created.

---

<a id="field-reification-inside-with"></a>
## Field reification inside `with`

Normal reification rules apply to names introduced through `with`.

Outside:

```caret
player.@health
```

reifies the `health` field of `player`.

Inside:

```caret
with player
  @health
```

reifies the same field.

Conceptually:

```caret
player.@health
```

and:

```caret
with player
  @health
```

refer to the same binding.

This preserves the normal meaning of `@`:

> reify the binding resolved at this position.

---

<a id="with-as-an-expression"></a>
## `with` as an expression

`with` is an expression.

Its result is the result of its body according to normal Caret block semantics.

Example:

```caret
distanceSquared point =
  with point
    x * x + y * y
```

or:

```caret
fullName person =
  with person
    firstName + " " + lastName
```

The `with` block does not require an explicit `return`.

---

<a id="with-and-contained-mutability"></a>
## `with` and contained mutability

`with` is particularly useful with immutable structures containing mutable containers.

Example:

```caret
damage player amount =
  with player
    put health $ health{} - amount
```

Here:

```caret
health
```

is the container stored in `player.health`.

```caret
health{}
```

reads its mutable content.

```caret
put health ...
```

changes its content.

The surrounding `player` value remains immutable.

---

<a id="section"></a>
## `$`

<a id="overview-3"></a>
### Overview

Caret uses whitespace for ordinary function application:

```caret
f x
```

Whitespace application binds tightly.

When a complete expression should be evaluated first and then supplied as an argument to the expression on its left, Caret provides `$`.

Example:

```caret
print $ calculate x
```

is equivalent to:

```caret
print (calculate x)
```

`$` is therefore a **low-precedence application operator**.

---

<a id="basic-semantics"></a>
### Basic semantics

The general form is:

```caret
functionExpression $ argumentExpression
```

Semantically:

```text
left $ right
```

means:

```text
left (right)
```

after the right-hand expression has been grouped as a whole.

For example:

```caret
put health $ health{} - damage
```

means:

```caret
put health (health{} - damage)
```

---

<a id="is-syntax-level-application"></a>
### `$` is syntax-level application

`$` is not an ordinary binary function.

Its purpose is to affect parsing and expression grouping.

The parser must therefore interpret:

```caret
f $ expression
```

as low-precedence application before ordinary function dispatch occurs.

Semantically it reduces to ordinary function application after parsing.
It therefore uses the same arity, partial-application and hole behavior, contracts, effects,
call-depth guard, source locations, and call diagnostics as whitespace application. `$` introduces
no runtime callable or independently reflectable operator value.

---

<a id="right-associativity"></a>
## Right associativity

`$` is right-associative.

Therefore:

```caret
a $ b $ c
```

means:

```caret
a $ (b $ c)
```

which is equivalent to:

```caret
a (b c)
```

For example:

```caret
print $ toString $ calculate value
```

means:

```caret
print (toString (calculate value))
```

This permits nested application without repeated parentheses.

---

<a id="low-precedence"></a>
## Low precedence

`$` should bind more weakly than ordinary expressions on its right.

For example:

```caret
print $ a + b * c
```

means:

```caret
print (a + b * c)
```

not:

```caret
(print a) + b * c
```

Likewise:

```caret
put health $ max 0 $ health{} - damage
```

means:

```caret
put health
  (max 0
    (health{} - damage))
```

The practical rule is:

> The right-hand side of `$` extends as far as possible.

---

<a id="and-ordinary-application"></a>
## `$` and ordinary application

Caret therefore has two complementary application forms.

High-precedence application:

```caret
f x
```

Low-precedence application:

```caret
f $ expression
```

For example:

```caret
print toString value
```

uses ordinary arity-directed whitespace application.

By contrast:

```caret
print $ toString value
```

explicitly groups:

```caret
toString value
```

as the argument to `print`.

---

<a id="and-lambdas"></a>
## `$` and lambdas

`$` should bind more weakly than lambda construction.

Therefore:

```caret
map (x -> x * 2) values
```

means:

```caret
map (x -> x * 2) values
```

This allows lambdas to be passed without requiring parentheses in many common cases.

For example:

```caret
filter values $ x -> x > 0
```

instead of:

```caret
filter values (x -> x > 0)
```

Parentheses remain available when a lambda must participate in a more complex surrounding expression.

---

<a id="and-conditionals"></a>
## `$` and conditionals

`$` should bind more weakly than Caret's conditional expression:

```caret
condition & trueValue ! falseValue
```

Therefore:

```caret
print $ valid & value ! fallback
```

means:

```caret
print (valid & value ! fallback)
```

Likewise:

```caret
put result $ condition & a ! b
```

means:

```caret
put result (condition & a ! b)
```

This allows `$` to serve as a general escape from parenthesizing complete conditional expressions.

---

<a id="and-composition"></a>
## `$` and composition

Function composition:

```caret
f >> g
```

binds more tightly than `$`.

Therefore:

```caret
use $ parse >> validate
```

means:

```caret
use (parse >> validate)
```

Likewise:

```caret
map (normalize >> validate) values
```

passes the composed function:

```caret
normalize >> validate
```

as the argument.

---

<a id="and-with"></a>
## `$` and `with`

`$` is particularly useful inside concise `with` blocks.

Example:

```caret
with player
  print $ toString health{}
  put health $ max 0 $ health{} - damage
```

Without `$`, the same expressions would require more grouping:

```caret
with player
  print (toString health{})
  put health (max 0 (health{} - damage))
```

The low-precedence application form keeps the flow of expressions readable.

---

<a id="suggested-precedence"></a>
## Suggested precedence

The exact full precedence table is specified separately, but the relative order should follow approximately:

```text
member / index / container access
    .
    []
    {}

ordinary whitespace application

arithmetic
comparisons
named and symbolic binary operators

function composition
    >>

conditional
    & !

lambda
    ->

low-precedence application
    $

assignment / binding
    =
```

The essential guarantees are:

```text
ordinary application binds tightly

>> binds more tightly than $

conditionals bind more tightly than $

lambdas bind more tightly than $

$ is right-associative
```

---

<a id="implementation-requirements-2"></a>
## Implementation requirements

The initial implementation should support at minimum:

1. Basic `with` blocks:

```caret
with value
  body
```

2. Direct lookup of exported named members.

3. Local bindings shadowing `with` members.

4. `with` members shadowing enclosing lexical bindings.

5. Explicit enclosing-scope access:

```caret
outer.name
```

6. Arbitrarily nested scope traversal:

```caret
outer.outer.name
```

7. Nested `with` blocks.

8. `with` working with heterogeneous collections containing named fields.

9. `with` working with named Collections and ruleset public interfaces.

10. Normal visibility rules inside `with`.

11. No implicit copying or destructuring of members.

12. Reification of members inside a `with` block:

```caret
@field
```

13. `with` as an expression returning its body's result.

14. Low-precedence application:

```caret
f $ expression
```

15. Right-associative `$`.

16. `$` binding below ordinary application.

17. `$` binding below arithmetic and comparison expressions.

18. `$` binding below `>>`.

19. `$` binding below conditional expressions.

20. `$` binding below lambda expressions.

21. `$` reducing semantically to ordinary function application after parsing.

The initial implementation may postpone:

* special optimizations for `with`;
* compile-time flattening of nested `outer` chains;
* advanced IDE visualization of scope resolution;
* alternative low-precedence application operators.

No separate record type, implicit receiver object, or multi-object `with` syntax is required.

---

<a id="design-principle-2"></a>
## Design principle

`with` changes how names are resolved, not what values are.

For:

```caret
with value
  body
```

the exported named members of `value` become directly visible in `body`.

Shadowed enclosing names remain explicitly reachable through:

```caret
outer
outer.outer
...
```

Nested `with` blocks express lookup priority naturally.

`$` complements Caret's whitespace application:

```caret
f x
```

means tightly bound application.

```caret
f $ expression
```

means evaluate the complete right-hand expression and pass its result to the left-hand expression.

Together, `with`, `outer`, and `$` allow Caret code to remain concise without introducing implicit object receivers or excessive grouping parentheses.
