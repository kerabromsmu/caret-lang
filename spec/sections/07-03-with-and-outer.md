# With and outer

[Chapter index](../07-state-containers-and-scoped-lookup.md) · [Language specification index](../../LANGUAGE.md)


<a id="with-outer-and-low-precedence-application"></a>
## `with`, `outer`, and Low-Precedence Application

<a id="overview-2"></a>
<a id="overview-1"></a>
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

The body is determined by effective logical indentation. The implemented `\\` and `\*` layout markers
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
diagnostic. Analysis identifies the names requiring member lookup. Runtime binding against
enumerated public keys completes before body execution, as specified in
[implemented with binding](07-01-containers-core.md#implemented-with-binding-over-lazy-collections), without weakening ordinary
lexical or visibility rules.

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
