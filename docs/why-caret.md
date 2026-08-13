# When Programs Should Look More Like the Systems They Describe

Most software begins with ideas that are not especially procedural. A game designer thinks in
terms of rules: a capture is allowed under these conditions, and completing it changes the score. A
protocol developer thinks in relationships: these bytes represent this message, and this message
must produce those bytes. A plugin host thinks in permissions and environments: this script may see
the clock and a virtual directory, but not the application's database.

Then implementation begins, and those ideas are translated into lower-level machinery. Rules become
callbacks and priority tables. Relationships become separate readers and writers. Structured values
become class hierarchies and serializer annotations. Permissions become checks scattered through an
API. None of that machinery is inherently wrong, but it can make the code describe the framework
more clearly than it describes the system.

Caret is an experimental language asking whether some of those higher-level ideas can be ordinary,
composable parts of a programming language. Its planned design brings rules, structural contracts,
bidirectional formats, program reification, effects, and restricted execution environments into one
value model. The aim is not to replace established general-purpose languages. A useful outcome would
be narrower: for certain rule-driven, structured, reflective, or controlled systems, the program
might resemble the thing its author is reasoning about.

That is the larger design. The implementation today is much smaller: a Java 21 tree-walking
interpreter with concise functions, lexical closures, partial application, immutable exported
scopes, persistent sequence and dictionary primitives, basic reflection, a REPL, and source-located
diagnostics. Most of the ideas discussed below are specified future work, not production-ready
features. That gap is important because it also identifies the kinds of early users who could most
usefully influence what Caret becomes.

## A small language underneath the larger experiment

Caret starts from compact expression-oriented syntax. Whitespace performs function application,
indentation defines bodies, and a function returns its final value:

```caret
totalWithTax subtotal rate =
  tax = subtotal * rate
  subtotal + tax

answer = totalWithTax 100 0.2
```

Holes provide lightweight partial application:

```caret
between low value high =
  value >= low and value <= high

insideUnit = between 0 _ 1
```

The language also distinguishes null, written `?`, from missing, written `~`. That difference
matters in data-heavy and reflective programs: a field can be present with a null value, present
with a missing value, or absent altogether.

Functions can return first-class scopes by marking only their public bindings with `^`:

```caret
makeCounter initial =
  internal = initial * 2
  ^value = initial
  ^description = "counter"
```

Everything not exported remains private. The same export convention is intended to carry into
modules and rulesets, giving Caret one small vocabulary for constructing public interfaces rather
than a separate visibility system for every abstraction.

These features are useful, but they are not the main reason Caret may be interesting. They are the
compact substrate on which the broader experiments are meant to compose.

## Early adopters: people who already think in rules

Gameplay, simulation, and rule-driven application developers routinely work with behavior that is
conditional, reactive, and only partly ordered. A turn may begin, several independent systems may
respond, one response may enable another, and the whole state must settle before traversal
continues. Encoding that behavior as a collection of update methods often hides the causal model.
Encoding it in a generic event bus can make ordering accidental.

Caret's planned rule model makes the relevant pieces explicit. A rule is a first-class value with
five optional components summarized as **CATEN**:

- `C`: the **C**ontext in which the rule is permitted to apply;
- `A`: whether the rule itself is **A**ctive (can be switchen on and off);
- `T`: the **T**riggering condition or event;
- `E`: the **E**ffect performed when it applies; and
- `N`: its optional string-literal ID (**N**ame).

A game rule might look like this:

```caret
capture = rule
  C game and playerTurn
  A on
  T captureRequested and validCapture
  E
    move selectedPiece target
    destroy targetPiece
  N "capture"
```

Contexts have persistent up/down states. Their transitions create temporary `rise` and `fall`
fronts, which can trigger other rules. A persistent trigger normally fires on its false-to-true
transition rather than on every scan while it remains true. Context and active state are gates: if
a trigger occurs while its context is down, raising the context later does not replay the missed
event.

Ordering is deliberately not inherited from source order. When two rules can apply and no causal
relationship connects them, either may run first. The cycle chooses one, applies its effect,
propagates the resulting state, and reevaluates what is applicable. This matters because the first
effect can make the second rule irrelevant. Caret tooling is intended to warn about potentially
significant unordered interactions; `(unordered)` acknowledges that either order is acceptable
without changing scheduling.

When order matters, it is expressed as causality. Every rule has an implicit context that rises
while its effect runs and falls when it completes. Another rule can trigger on that fall, or the
same relationship can be written with `chain`:

```caret
chain
  rule
    T attackRequested
    E applyDamage

  rule
    E checkForDefeat
```

`chain` is sugar for ordinary rules connected by completion fronts; it is not a second execution
engine. More complex dependencies can form partial orders, leaving independent branches genuinely
unordered.

Rules are packaged in parameterized, private-by-default `ruleset` values. Constructing a ruleset is
inert; `install` adds it to a `ruleCycle`. Each construction has independent runtime state. The
`ruleCycle` owns contexts, objects, traversal, scheduling, propagation to stability, and termination.
Its object traversal order is itself unspecified, so cross-object dependencies must also be stated
through contexts, triggers, or chains.

This makes gameplay and simulation developers plausible early design partners. The model is already
detailed enough to test against turn systems, board games, state machines, and small interpreters.
It is not yet implemented, however. Serious use would also need rule tracing, a timeline of context
fronts, conflict visualization, deterministic replay tools, useful non-stabilization diagnostics,
profiling, and likely hot reload. Those are not all commitments in the current roadmap. They are
natural priorities if rule-oriented users find that Caret expresses their systems well.

## Early adopters: DSL and interpreter builders

People building small languages face a recurring choice. They can embed a DSL in a host language
and inherit its syntax and authority, or build a separate parser, evaluator, diagnostics system,
module model, and tooling stack. Caret is exploring a middle ground: a compact host whose own code,
contracts, scopes, and rules are intended to be inspectable as language-owned values.

The `@` operator provides reflection. In the current prototype it exposes modest metadata such as a
value's kind, public names, collection size, or function arity. The planned model goes further with
semantic code reification. `@root` is a metadata-only reference to the root of the current execution
environment, and:

```caret
@root.code
```

is an immutable structural `Code` value describing the complete admitted analyzed code unit for the
visible root module. Imports remain semantic references rather than inlining other module bodies.
The value contains semantic declarations and references, not Java parser objects, original source
text, comments, paths, or source locations. Converting it to text produces canonical Caret. This
makes the canonical quine unusually small:

```caret
print toString @root.code
```

The result need not reproduce the original whitespace or comments. Its parsed semantic structure
must be equivalent to the reflected code. Imported module bodies are not textually inlined, and
canonical names may replace alpha-equivalent private local names.

That model could support interpreters, schema tools, program transformers, generated documentation,
or editors without treating a compiler's private AST as a public API. It also fits educational
environments: a lesson could expose a small language universe, inspect the learner's semantic code,
and gradually add capabilities.

This is a promising area for experimental use because language enthusiasts and DSL authors are
often willing to work with a small runtime while semantics are still changing. The missing pieces
are substantial: modules, full code descriptors, canonical serialization, source-to-code mapping
for tools, parser recovery, an LSP, package management, and a stable embedding API. Source-exact or
comment-preserving reconstruction is explicitly outside the initial model. Early users could help
answer whether Caret should first mature into an embeddable interpreter, a language-workbench
substrate, or a teaching environment.

## Embedded scripts and plugins: a strong fit that needs security work

An application that runs extensions needs more than a scripting syntax. It needs a clear answer to
what extension code can see, what it can call, what survives reload, and whether reflection can
cross the boundary.

Caret's planned sandbox model begins with root substitution:

```caret
plugin = sandbox pluginCode environment
```

The sandbox's `@root` is not the host root with a few names hidden. It is the root of a smaller
Caret universe assembled from the code, libraries, language features, and runtime capabilities the
host admits. The host can expose direct, filtered, or virtual capabilities—for example, a virtual
filesystem or a clock—and absent authority is preferred to ambient access followed by global
permission checks.

Effects and authority remain distinct. A function declared with an `fs` effect says that filesystem
behavior may be observable; the declaration does not grant access to a filesystem. The same code
could receive the real filesystem in a trusted application, a restricted directory in a plugin, or
an in-memory filesystem in a tutorial.

Reflection is intended to obey the same boundary. A callable projected into a sandbox exposes only
its arity, argument contracts, and result contract—not whether it is native, its host captures, its
implementation, or a route back to the host root. Inside the sandbox, the quine prints only the
plugin code visible there, not the supplied environment.

The lifecycle design is also explicit. Reload stops the old generation first and invalidates its
references; it does not silently make saved references point into the new generation. Immutable
values already copied out remain values. A running plugin can atomically swap its immutable
environment snapshot without reloading; mediated names then resolve through the new snapshot, while
revoking independently retained resource references still requires a mediation object.

This is a coherent basis for plugins, application automation, and controlled REPLs, but it is not a
security product today. Sandboxing, reflective membranes, modules, lifecycle APIs, and even the
public success/failure result envelope remain unimplemented. Practical adoption would
need an embedding API, resource limits, adversarial security testing, dependable host
interoperability, and probably process or stronger isolation for hostile code. Capability
revocation, safe FFI, signing and distribution, and operational monitoring might become priorities
if plugin hosts adopt the model; they should not be assumed merely from the specification.

## Protocol and binary-format developers: describe the relation once

Protocol code often duplicates knowledge. A decoder reads a length and then that many bytes. An
encoder retrieves the payload, computes or validates the length, and writes the same fields in the
opposite direction. Validation, offsets, and version choices must remain synchronized across both
implementations.

Caret's planned `Format` is a first-class bidirectional relation between a logical value and a
representation. A packet description might be assembled from ordinary immutable format values:

```caret
Packet =
  format
  >> constant "PACK"
  >> field u16be "length"
  >> field u8 "type"
  >> field (bytes length) "payload"
```

The constant is checked while decoding and emitted while encoding. A prior field can parameterize a
later one. Explicit `decode Packet bytes` and `encode Packet value` operations use the same format.
Where the relation is structurally reversible, both directions are derived from the description.
Where it is not—compression and encryption are obvious examples—a `codec` supplies both pure
directional functions as part of the relation.

Formats are deliberately separate from transport. Reading a file or receiving a packet has an
effect; decoding bytes remains a pure relationship. They are also separate from in-memory layout:
a format describes an external representation, while a packed collection describes physical
storage and a contract describes valid logical values.

This audience becomes plausible after the collection, contract, byte, and format layers exist.
Before real protocol work, Caret would need precise nested failure reporting, more primitive
formats, versioning libraries, incremental and streaming decoding, fuzzing support, zero-copy views
where safe, and interoperability with existing network and storage stacks. Streaming, zero-copy,
and asynchronous transport are explicitly postponable in the current design, so early protocol
users would have real influence over their priority.

## Structures without another object hierarchy

Several of these domains need to describe shapes: a point, an error, a message, a vertex, or a
collection of records. Caret plans to make contracts the basis of its type system. A contract is a
pure predicate over values, and derivation means logical inclusion:

```caret
Number = contract Comparable Arithmetic
Int = contract [Number Integral]
```

An `Int` therefore satisfies `Number` and its other base contracts. This is a graph, not an
object-layout hierarchy. Contracts contain no methods. Behavior lives in ordinary functions, and
multiple definitions can specialize several parameter contracts. A call selects the unique most
specific applicable definition; incomparable matches are an ambiguity, not an invitation to choose
by source order.

Templates build exact structural contracts from collections. A collection expression containing
holes is itself a function whose parameters complete the collection. Passing that reifiable
constructor to `template` turns its structure into a contract:

```caret
PointConstructor =
  [
    (Float) _
    (Float) _
  ]

Point = template PointConstructor
```

Candidate values fill the hole positions; fixed elements must compare equal; named fields and
nested collections contribute to exact shape. Numbered holes can express repeated positions, so
`template [_1 _1]` requires the two candidate elements to be equal. This is structural inversion of
a restricted, language-owned constructor description—not an attempt to invert arbitrary functions.

The common collection syntax extends the same idea. `[...]` does not permanently mean list, array,
dictionary, or packed buffer. Contracts and context determine the collection behavior. A
heterogeneous collection retains enough metadata to distinguish its elements. When metadata is
common, it can be stored once at collection level. `Packed Int32` goes further by requiring a
uniform concrete representation, and templates can describe packed structural records such as
vertices with one shared layout descriptor.

This unified model may interest data-oriented and language-design programmers early, even before it
is fast. Graphics, GPU, audio, and scientific programmers are later adopters: they need dependable
layout and alignment guarantees, profiling, native interoperability, device APIs, and evidence that
abstraction does not cost throughput. Caret's planned SIMD model is relevant here—pure scalar
functions can lift lane-wise, while `collection :: function` requires vectorized execution or a
diagnostic rather than silently becoming scalar. But without a compiler backend, packed runtime,
and hardware integration, it remains a direction rather than a reason to migrate workloads.

## Workflow and automation users: possible, but later

Rules, explicit effects, persistent state transitions, and sandboxed capabilities also suggest
workflow and automation systems. Caret could describe dependencies causally rather than through
incidental task order, give scripts only the services they need, and make effectful boundaries
visible in function contracts.

There is a limit to the analogy. The specified `ruleCycle` is an in-process reactive execution
model, not a durable workflow service. It does not currently promise persistence across crashes,
distributed scheduling, retries, compensation, leases, audit storage, or exactly-once external
effects. Those facilities should not be inferred from words such as “rule” and “cycle.” Workflow
users become a credible audience only if the ecosystem grows durable state, operational tooling,
connectors, and well-defined recovery semantics. If they do adopt Caret, those needs could steer a
future runtime; until then, the language is better understood as a way to explore workflow logic
than as workflow infrastructure.

## What participation means at this stage

Caret does not need every audience at once. In fact, trying to satisfy game engines, protocol
stacks, plugin hosts, teaching tools, and numerical computing simultaneously would likely blur the
experiment. The useful question is which group first finds that the model removes enough accidental
machinery to justify helping build the missing pieces.

Today, that participation means evaluating semantics as much as writing programs. Are CATEN rules
and explicit causal ordering easier to inspect than the alternatives? Does one contract model work
for refinements, capabilities, and structural templates without becoming obscure? Can reified code
remain useful while respecting sandbox authority? Do bidirectional formats produce better designs
than paired codecs in realistic protocols? These questions benefit from examples, counterexamples,
small prototypes, and tooling experiments long before Caret is ready for deployment.

The individual ingredients have precedents in other languages and systems. Caret's interesting bet
is about composition: contracts without method ownership, functions dispatched by those contracts,
collections ranging from heterogeneous data to packed storage, formats relating values to bytes,
rules reacting inside an explicit cycle, effects describing behavior without granting authority,
and reflection bounded by the current environment.

Caret may ultimately be valuable not because it offers a better way to write every program, but
because for certain programs the source code can look substantially more like the system the
programmer is actually trying to describe. Finding those programs—and the programmers who care
enough to shape the unfinished language around them—is the experiment now.
