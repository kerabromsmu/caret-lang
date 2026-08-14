# Caret Doesn’t Look Like One Language — and That’s the Point

If someone asks what Caret is similar to, there is no especially satisfying one-word answer.

Its function syntax can remind you of Haskell. Its approach to polymorphism has something in common with Julia. Its compile-time execution points toward Zig. Its metaprogramming ambitions are reminiscent of Lisp and Racket. Its rule system has relatives in languages such as CLIPS. Its explicit treatment of mutable state has some of the motivations you might associate with Rust.

But Caret is not trying to reproduce any of them.

What makes it interesting is how these ideas start fitting together around a fairly small set of concepts.

Caret is being designed around a recurring principle:

> If something can be represented as an ordinary value, function, contract, collection, or scope, avoid inventing a separate language mechanism for it.

That principle turns out to connect features that are normally found in quite different kinds of languages.

> **Implementation status:** This article compares the complete planned Caret design, not only the
> current Java prototype. Sections marked **Prototype** discuss syntax available today, though
> individual explanatory snippets may omit definitions for names such as `parse` or `validate`.
> Sections and examples marked **Planned** are conceptual syntax specified in `LANGUAGE.md` but not
> yet executable. The conformance matrix is the authoritative feature-status inventory.

## The first impression: a compact functional language — Prototype

Basic Caret code has a functional flavor:

```caret
add a b =
  a + b

result = add 2 3
```

Function application uses whitespace. Partial application is normal:

```caret
addTen = add 10
```

Caret also has arbitrary-position holes:

```caret
between low value high =
  value >= low and value <= high

inside = between 0 _ 10
```

and function composition:

```caret
pipeline =
  parse >> normalize >> validate
```

Low-precedence application uses `$`:

```caret
print $ calculate value
```

instead of:

```caret
print (calculate value)
```

So a developer coming from Haskell will recognize some of the surface vocabulary.

But the resemblance only goes so far.

Caret is not built around the idea that every useful program should remain purely functional. Instead, it tries to make the boundary between pure computation and stateful behavior explicit.

That difference becomes important later.

## Types are contracts, not containers for behavior — Prototype foundation, planned dispatch

One of Caret's more fundamental ideas is that a type is a **contract**.

A contract behaves like a predicate:

```caret
Int value
Number value
Eq value
```

The prototype also accepts proven-pure unary Boolean functions as refinement requirements in
derived contracts and declaration clauses. Contract-specialized overload dispatch remains planned.
These contracts compare by descriptor identity. Aliases preserve equality, while separate
constructions remain unequal even with identical requirements. Their reflective view lists public
base and refinement requirement names rather than exposing interpreter implementation objects.

A value satisfying `Int` may also satisfy broader contracts through derivation:

```text
Int -> Number -> Comparable -> Eq
```

But contracts do not own methods.

Instead of defining equality "inside" a type, Caret defines ordinary functions:

> **Planned example:** Same-name contract-specialized overload sets and their dispatch are not yet
> implemented by the prototype.

```caret
eq (Eq) a (Eq) b =
  ...

eq (Number) a (Number) b =
  ...

eq (Int) a (Int) b =
  ...
```

The most specific applicable function implementation is selected.

This is where Caret starts to resemble Julia more than a traditional object-oriented language.

There is no privileged receiver. Dispatch can depend equally on several arguments.

That makes operations independent of the type hierarchy while still allowing specialized behavior.

Caret then pushes the contract idea further than ordinary nominal types. Contracts are intended to cover concepts that languages often split into several unrelated systems:

```text
type
interface
refinement
capability
structural requirement
```

A template, for example, is simply a structural contract:

> **Planned example:** `template` and the universal structural collection model are not yet
> implemented.

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

`Point` can then be used wherever any other contract can be used.

This creates a rather different type-system philosophy:

> describe what values satisfy; keep behavior in functions.

## Collections are deliberately underspecified — Planned

The planned universal collection model tries to avoid encoding representation decisions into basic
syntax. The prototype currently provides eager sequence literals plus persistent sequence and
dictionary primitives; the contract-selected representations below are conceptual.

The literal:

```caret
[1 2 3]
```

means essentially:

> a collection containing these values.

It does not inherently mean "list."

Context may determine that it is a list, array, set, packed buffer, or some other collection representation:

```caret
(List Int) a =
  [1 2 3]

(Set Int) b =
  [1 2 3]

(Packed Int32) c =
  [1 2 3]
```

The same principle extends to structured data.

Named fields are elements of collections rather than members of a separate object model:

```caret
person =
  [
    ^name "Alice"
    ^age 42
  ]
```

So Caret does not need unrelated literal syntaxes for arrays, records, dictionaries, tuples, and schemas unless their semantics genuinely differ.

This is one of the more Lisp-like aspects of Caret—not syntactically, but architecturally.

A relatively small collection model is expected to support increasingly sophisticated abstractions built on top of it.

## Mutation exists, but you can see exactly where — Planned

Caret values are immutable by default. The explicit container syntax in this section is specified
but not implemented by the current prototype.

When mutable state is required, it is introduced using an explicit container:

```caret
health = { (Int) 100 }
```

The container has stable identity. Its contents can change.

Reading the mutable value is explicit:

```caret
health{}
```

Writing is explicit:

```caret
put health 80
```

This becomes particularly useful inside otherwise immutable structures:

```caret
player =
  [
    ^name "Alice"
    ^health { (Int) 100 }
  ]
```

`player` remains immutable.

Only `player.health` refers to mutable state.

That makes three operations deliberately different:

```caret
player.health
player.health{}
player.@health
```

They mean:

```text
the container

the current contents of the container

the field itself as a reified program entity
```

This explicitness is partly reminiscent of Rust's concern with making state and identity visible, although Caret's model is quite different from Rust's borrowing system.

More importantly, mutable reads and writes participate in the effect system.

Reading:

```caret
health{}
```

is observably different from reading an immutable value: the same function call may return something different later.

Caret therefore treats mutable-state observation and mutation as effects rather than hiding them behind normal field access.

## Rules are part of the language, not a separate DSL — Planned

The planned language becomes much less familiar when you reach its rule system. Rules, rulesets,
contexts, and `ruleCycle` are not implemented by the current prototype.

A rule has five conceptual parts, summarized as CATEN:

```text
C   Context
A   Active state
T   Trigger
E   Effect
N   Name
```

For example:

```caret
capture = rule
  C game and playerTurn
  T captureRequested
  E
    move selectedPiece target
    destroy targetPiece
```

Rules execute inside a `ruleCycle`.

Contexts have persistent up/down state and transient rise/fall fronts. Rules react to changes and may themselves change contexts, objects, mutable state, or other rules.

Rule definition order deliberately does not imply execution order.

If two rules can execute independently, Caret refuses to manufacture a fake ordering merely because one appears first in a file.

Where order matters, it is expressed causally.

That gives rule systems in Caret some conceptual relatives in production-rule languages such as CLIPS or OPS5.

But there is an important difference.

Rules are not written in a separate rule language.

Their conditions, effects, functions, values, contracts, collections, formats, and state are ordinary Caret.

A ruleset is likewise a first-class reusable value rather than a separate compilation unit:

```caret
Combat attacker target damage =
  ruleset
    ...
```

An ordinary function returning a ruleset acts as a parameterized ruleset template.

Again, the recurring pattern is visible:

> Don't invent a second language if the first one can represent the idea directly.

## Caret's metaprogramming follows the same philosophy — Partly prototype, mostly planned

The prototype implements basic `@value` reflection. Environment-relative `@root`, structured code
reification, imports, lambdas, higher-order collection operations, and compile-time `#` execution in
the following examples are planned.

Code itself can be represented structurally:

```caret
@root.code
```

A program can inspect semantic code rather than merely treating source as text.

Caret also uses `#` for compile-time execution.

For example:

```caret
# shared = import "client-server.caret"
```

means that `shared` exists in the compile-time environment rather than becoming an ordinary runtime binding.

Ordinary Caret can then transform it:

```caret
clientRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.client
```

The interesting part is not client/server programming specifically.

It is that `filter` is still just `filter`.

The language does not need a separate collection of preprocessor directives or a miniature target-selection language.

The programmer is simply running Caret while compiling Caret.

This is where Zig becomes an interesting comparison. Zig demonstrates the usefulness of doing compile-time computation in essentially the same language as runtime computation.

Caret combines that idea with its own reflection model:

```text
@   reify program structure

#   compute during compilation
```

Together, those two mechanisms provide a basis for structural metaprogramming without requiring textual macro substitution.

## Separate targets become an ordinary consequence — Planned

This section describes the planned compiler, module, staging, and reachability model; none of these
facilities is implemented by the current tree-walking prototype. Suppose one source contains both
sides of some client/server interaction.

The client can be one compilation root:

```text
client.caret
```

and the server another:

```text
server.caret
```

Both can import the same source at compile time:

```caret
# shared = import "client-server.caret"
```

The client selects:

```caret
clientRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.client
```

while the server selects:

```caret
serverRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.server
```

Normal dependency analysis then includes functions, formats, contracts, and other definitions actually required by the selected rules.

The whole shared module does not have to appear in either runtime artifact.

And there is nothing intrinsically client/server-specific about the mechanism. A different program could derive separate artifacts for browser and desktop, editor and runtime, different devices, protocol roles, or entirely different criteria.

The distinction between targets is expressed using normal Caret values and computation rather than compiler-specific conditional syntax.

## Formats follow the same pattern — Planned

Caret's planned `Format` system applies the same compositional approach to external representation.
Formats, codecs, bytes, and bidirectional encoding/decoding are not implemented by the prototype.

A format describes a relationship:

```text
logical value <-> representation
```

For example:

```caret
Packet =
  format
  >> constant "PACK"
  >> field u16be "length"
  >> field u8 "type"
  >> field (bytes length) "payload"
```

The intention is that the same structure can drive both encoding and decoding.

Where a transformation cannot reasonably be inverted automatically, both directions are supplied explicitly:

```caret
gzip format =
  codec gunzip gzip format
```

Formats themselves remain first-class immutable values and are constructed through ordinary functions.

Once more, what might normally become a special schema language is represented largely through normal Caret composition.

## Caret in the language landscape

The table compares other languages with the complete planned Caret design. Individual similarities
therefore include facilities beyond the current prototype.

No single existing language is especially close to Caret. Different languages resemble different parts of it:

| Language / family                          | Similarity to Caret                                                                                                                 | Where Caret differs                                                                                                                                               |
| ------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Haskell**                                | Whitespace application, `$`, composition, partial application, higher-order functions, immutable values, strong attention to purity | Caret explicitly incorporates mutable containers, runtime effects, reflection and reactive rules rather than centering the language on pure functional evaluation |
| **Julia**                                  | Multiple dispatch on ordinary functions; behavior is not owned by receiver classes                                                  | Caret generalizes types into predicate-like contracts that can also express refinements, capabilities and structural requirements                                 |
| **Racket / Scheme / Lisp**                 | Small composable core, code manipulation, metaprogramming, tendency to construct abstractions from the language itself              | Caret does not rely on Lisp-style syntactic homoiconicity or make macros its primary abstraction mechanism; its code representation is semantic structured data   |
| **Zig**                                    | Compile-time computation uses the normal language rather than a separate preprocessor                                               | Caret combines staging with reified semantic program structures and uses compile-time execution for transformations such as filtering imported rulesets           |
| **CLIPS / OPS5 / production-rule systems** | Conditions, reactive rule execution, rule applicability and propagation                                                             | Rules in Caret are first-class values embedded in a general-purpose language, sharing its functions, contracts, effects, collections and reflection               |
| **Rust**                                   | Mutation boundaries and capabilities are intended to remain explicit; implementation may exploit ownership information              | Caret uses explicit stable mutability containers inside otherwise immutable structures rather than Rust's variable/borrow model                                   |
| **Nix**                                    | Immutable definitions, evaluation during construction, deriving different outputs from shared definitions                           | Caret is a runtime general-purpose language; compilation is only one domain in which its ordinary evaluation model is reused                                      |
| **Erlang / Elixir**                        | Message-oriented independent participants and distributed-system concerns                                                           | Caret's emerging distributed model grows out of rules, contexts and separate compilation rather than an actor/process abstraction                                 |
| **Datalog / Prolog**                       | Declarative relationships and rule-oriented reasoning                                                                               | Caret deliberately avoids making general logical search, unification or backtracking its normal execution model                                                   |
| **Schema / DSL systems**                   | Formats, structural descriptions and rules can describe domains normally handled by dedicated DSLs                                  | Caret tries to represent these through ordinary first-class values, contracts and functions instead of introducing an unrelated language for each domain          |

These comparisons are useful individually, but none describes Caret as a whole.

The more interesting characteristic is that mechanisms which normally live in separate language subsystems repeatedly collapse into the same Caret concepts.

## The more useful way to think about Caret

Caret is probably better understood not as a collection of borrowed language features but as an attempt to make several programming domains share the same concepts.

Instead of adding:

```text
a type system
a schema system
a macro language
a rule language
a serialization DSL
a build DSL
a special mutable object system
```

Caret repeatedly asks:

> Can this be expressed through contracts, values, collections, functions, scopes, rules, reflection, and execution stages that already exist?

That produces some unusual connections.

A template is a contract.

A ruleset is a value.

A format is a composable value.

Mutable state lives in an explicit container.

Code is structured data.

Compile-time transformation uses ordinary functions.

Different compilation targets can be different transformations of common code.

This does not automatically make the language simple. In fact, one of Caret's main design risks is that interactions between these general mechanisms could become harder to understand than a set of narrower specialized features.

But it gives the project a coherent direction.

The most interesting question about Caret is therefore probably not:

> Which existing language does it resemble?

It is:

> How far can a general-purpose language go if rules, types, data, code generation, serialization, state, and compilation are treated as variations of a relatively small set of composable ideas?

Caret is currently an experiment in finding out.
