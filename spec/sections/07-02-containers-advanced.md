# Container effects and advanced rules

[Chapter index](../07-state-containers-and-scoped-lookup.md) · [Language specification index](../../LANGUAGE.md)


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

The [v1 sandbox subset](../../roadmap/phase-10.md#phase-10a-initial-sandboxes-v1) supports explicitly
shared read/write containers and immutable value snapshots. Read-only views and richer
mediated/virtual replacements below target Phase 10C. All implemented in-environment container
behavior remains part of v1; only its sandbox integration is currently planned.

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
