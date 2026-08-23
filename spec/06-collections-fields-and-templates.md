<a id="collections-fields-and-templates"></a>
# Collections, Fields, and Templates

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

<a id="immutable-collections"></a>
## Immutable collections

The prototype provides immutable sequences and insertion-ordered dictionaries through:

```text
seqEmpty
seqAdd sequence value
seqGet sequence index
seqSize sequence

dictEmpty
dictPut dictionary key value
dictGet dictionary key
dictHas dictionary key
dictKeys dictionary
```

Dictionary keys are strings, and key iteration preserves insertion order. `dictHas` distinguishes
an absent key from a present key whose value is
`~`.
Collection literal syntax is not required for the initial self-interpreter.

<a id="collections-and-lexical-scopes"></a>
## Collections and lexical scopes

Caret has collections and lexical scopes, but no separate first-class `Scope` value. A lexical
scope is an evaluator/compiler mechanism for declaration visibility, name resolution, captures,
shadowing, parent lookup, and lifetime. It is not an ordinary Caret value and cannot itself be
stored, returned, passed, indexed, or reflected.

`Collection` is the ordinary first-class aggregate value model. A non-empty Collection is
structurally either positional:

```caret
[1 2 3]
```

or named:

```caret
[
  ^x = 1
  ^y = 2
]
```

A non-empty Collection cannot mix named Field elements with unnamed positional elements. Exported
bindings in a block are shorthand for constructing the equivalent named Collection:

```caret
value =
  ^x = 1
  ^y = 2
```

is observationally equivalent to:

```caret
value =
  [
    ^x = 1
    ^y = 2
  ]
```

The empty Collection `[]` has no named/positional distinction. It vacuously satisfies ordinary
collection contracts compatible with zero elements, without changing identity or acquiring a
shape. Explicit structural contracts that require actual positions or fields remain unsatisfied.

<a id="collections"></a>
### Collections

<a id="general-collection-contract"></a>
#### General collection contract

`Collection` is the fundamental contract for values containing zero or more elements.

It is the planned language's ordinary first-class aggregate model for both positional collections
and named structured values. There is no additional `Scope` value category for named exports.

More specific collection contracts derive from it.

Conceptually:

```text
Collection
    List
    Array
    Set
    Dictionary
    Queue
    Packed
    ...
```

These relationships need not form a traditional OO hierarchy.

Specific collection properties may be expressed through additional contracts such as:

```text
Ordered
Indexed
Unique
Keyed
FixedSize
Mutable
Contiguous
Packed
Sorted
Persistent
```

A collection type may derive from several such contracts.

For example, conceptually:

```text
Array T
    -> Collection T
    -> Ordered
    -> Indexed

Set T
    -> Collection T
    -> Unique

Packed T
    -> Collection T
    -> Contiguous
    -> Packed
```

---

<a id="parameterized-collection-contracts"></a>
##### Parameterized collection contracts

Collection contracts may be parameterized.

Examples:

```caret
Collection Int
List String
Array Float
Set String
Dictionary String Int
Packed Byte
```

Conceptually:

```caret
Collection element
```

produces a contract requiring every collection element to satisfy `element`.

Thus:

```caret
(Collection Number) values
```

means:

> `values` is a collection whose elements all satisfy `Number`.

Parameterized collection types should use the normal Caret contract/function model rather than requiring a separate generic-type language.

The current prototype implements the first instance of this model as `Sequence T`. Applying the
raw `Sequence` contract to another contract constructs a fresh contract descriptor; applying it to
an ordinary value remains a Boolean raw-sequence membership test. `Sequence T` accepts empty
sequences and sequences whose every element satisfies `T`, supports nesting and ordinary
null/missing modifiers, and reflects `Sequence` as its base and `T` as its requirement. Initial
inference retains the outer `Sequence` constraint while element proof remains a runtime check.

Within a contract clause, a known parameterizable constructor consumes its declared number of
following contract terms. Remaining terms are the existing anonymous conjunction. Thus
`(Sequence Number positive)` requires both `Sequence Number` and `positive`; constructor aliases
retain this metadata. Parenthesized nested terms allow `Sequence (Sequence Number)` without adding
a generic-type grammar.

---

<a id="collection-literals"></a>
#### Collection literals

<a id="universal-collection-syntax"></a>
##### Universal collection syntax

Caret has one collection literal syntax:

```caret
[1 2 3]
```

Square brackets mean:

> construct a collection containing these expressions.

They do **not** specifically mean list or array.

The collection's more specific contract may come from context:

```caret
(List Int) a =
  [1 2 3]

(Array Int) b =
  [1 2 3]

(Set Int) c =
  [1 2 3]

(Packed Int32) d =
  [1 2 3]
```

The same literal syntax is used in every case.

Different contracts may result in different behavior and physical representation.

---

<a id="empty-collection"></a>
##### Empty collection

An empty collection is:

```caret
[]
```

It has no intrinsic named-versus-positional distinction because it contains no elements. It may be
used under every ordinary collection contract whose requirements zero elements vacuously satisfy:

```caret
(Collection Int) a = []
(List Int) b = []
(Set String) c = []
(Dictionary String Int) d = []
(Packed Float32) e = []
```

Contract checking does not mutate the identity of `[]` or turn it into a named or positional empty
value. Explicit structural constraints remain independent: an exact non-empty `template`, or a
future explicit `NonEmpty` contract, still requires its declared elements or fields.

---

<a id="heterogeneous-collections"></a>
##### Heterogeneous collections

Collections may contain values of different types:

```caret
[
  10
  "hello"
  true
  3.14
]
```

The inferred element contract is conceptually a union of the possible element contracts:

```text
Int | String | Boolean | Float
```

Caret must not require the programmer to explicitly use `Any` merely because a collection is heterogeneous.

Individual elements retain enough metadata to determine their actual contracts and representation where required.

---

<a id="homogeneous-collections"></a>
##### Homogeneous collections

A collection such as:

```caret
[1 2 3 4]
```

may infer a common element contract:

```text
Int
```

Conceptually, the collection can carry that information once:

```text
Collection
    element contract: Int

    values:
        1
        2
        3
        4
```

The implementation need not repeat identical type metadata for every element.

---

<a id="collection-expressions"></a>
##### Collection expressions

Elements inside `[]` are ordinary Caret expressions.

Example:

```caret
[
  10
  calculate x
  transform value
]
```

No separate collection-expression language is introduced.

Multi-line literals are allowed:

```caret
[
  first
  second
  calculate third
]
```

Parentheses remain the normal grouping mechanism where expression boundaries would otherwise be ambiguous.
On one line, adjacent simple atoms are separate elements. An unparenthesized top-level operator
extends an element through the closing bracket; use parentheses when another element follows it.

---

<a id="named-fields-and-dictionaries"></a>
#### Named fields and dictionaries

<a id="named-elements"></a>
##### Named elements

A non-empty Collection has exactly one structural shape. A positional Collection contains only
unnamed values, while a named Collection contains only Field values. Static exported fields and
dynamic `field` construction are both named elements. They may be combined with one another, but
neither may be mixed with unnamed positional elements in the same non-empty Collection.

`^` may construct named elements inside a collection:

```caret
person =
  [
    ^name = "Alice"
    ^age = 42
    ^active = true
  ]
```

Conceptually these are first-class field values:

```text
Field("name", "Alice")
Field("age", 42)
Field("active", true)
```

The collection may therefore satisfy a record-like or dictionary-like contract.

Static member access may be used where the field is known:

```caret
person.name
person.age
```

Dynamic lookup uses the same member model:

```caret
person["name"]~
```

A positional Collection does not acquire named fields merely because named Collections support
member access.

The following literal is invalid:

```caret
[
  1
  ^name = "Alice"
  2
]
```

Analysis must report the located diagnostic `MIXED_COLLECTION_SHAPE` at the first element whose
shape conflicts with the earlier elements, retaining the collection literal as context. The same
diagnostic applies whether the Field was produced by `^` or by `field`.

<a id="exported-block-shorthand"></a>
##### Exported-block shorthand

When a function or ordinary block contains exported bindings, its result is the named Collection
formed from those fields. These values are equivalent:

```caret
thing1 =
  ^field1 = 1
  ^field2 = 10

thing2 =
  [
    ^field1 = 1
    ^field2 = 10
  ]
```

For example:

```caret
makeThing x =
  temporary = calculate x
  ^field1 = x
  ^field2 = temporary
```

returns the same value as the explicit named Collection containing `field1` and `field2`.
`temporary` remains a lexical local and is not a Collection element. A body with no exported
bindings retains its ordinary final-expression result. No intermediate Scope object is created.

Equality, reflection, contracts, and member access cannot distinguish exported-block shorthand
from the equivalent explicit named Collection.

```caret
thing1 == thing2 // true
```

Both reflect with the ordinary Collection kind, shape, field names, and field metadata. Lexical
scopes do not appear as reflectable values merely because the source block contains declarations.

---

<a id="dynamic-keys"></a>
##### Dynamic keys

For keys that cannot be expressed as static identifiers, ordinary field construction may be used:

```caret
[
  field "first name" "Alice"
  field "age" 42
]
```

or:

```caret
[
  field key1 value1
  field key2 value2
]
```

A dictionary is therefore still fundamentally a collection.

Conceptually:

```text
Dictionary K V
    -> Collection (Field K V)
    -> Keyed
```

Caret does not require a separate `{ key: value }` literal syntax.

---

<a id="nested-collections"></a>
##### Nested collections

Collections may contain collections:

```caret
people =
  [
    [
      ^name = "Alice"
      ^age = 32
    ]

    [
      ^name = "Bob"
      ^age = 41
    ]
  ]
```

or arbitrary heterogeneous nested structures:

```caret
[
  10
  "hello"

  [
    ^x = 20
    ^y = 30
  ]

  true
]
```

No separate object, record, JSON, or array literal syntax is required.

---

<a id="collection-metadata"></a>
#### Collection metadata

<a id="logical-versus-physical-metadata"></a>
##### Logical versus physical metadata

Caret distinguishes between:

1. **semantic/type metadata** — what contracts a value satisfies;
2. **representation metadata** — how a value is physically laid out.

These are related but not identical.

For example:

```caret
(Collection Number) values
```

guarantees that every element satisfies `Number`.

It does not necessarily imply that every element has the same representation.

The collection may contain:

```caret
[1 2.5 100 3.14]
```

where some values are represented as integers and others as floating-point values.

---

<a id="per-element-metadata"></a>
##### Per-element metadata

A heterogeneous collection may require metadata for each element.

Conceptually:

```text
[
    { type: Int,     value: 10 }
    { type: String,  value: "hello" }
    { type: Boolean, value: true }
]
```

This is a semantic model only.

The runtime is not required to physically store a complete descriptor beside every value.

It may use:

* compact tags;
* descriptor tables;
* separate storage areas;
* compiler-known static information;
* other equivalent representations.

The observable semantics must only preserve sufficient information to recover each element's relevant type and representation.

---

<a id="shared-collection-metadata"></a>
##### Shared collection metadata

When every element shares the same relevant metadata, that metadata may belong to the collection instead of every element.

For example:

```caret
(Collection Int) values =
  [1 2 3 4]
```

may conceptually be represented as:

```text
element contract: Int

values:
    1
    2
    3
    4
```

rather than:

```text
Int 1
Int 2
Int 3
Int 4
```

This sharing is semantically invisible and may be performed automatically.

---

<a id="contract-homogeneous-but-representation-heterogeneous-collections"></a>
##### Contract-homogeneous but representation-heterogeneous collections

A common contract does not necessarily provide enough information to remove all per-element metadata.

For example:

```caret
(Collection Number) values =
  [1 2.5 3 4.5]
```

has a common semantic contract:

```text
Number
```

but its elements may have more specific contracts:

```text
Int
Float
Int
Float
```

and may therefore require different representations.

The collection may store `Number` as shared metadata while retaining enough per-element information to distinguish the concrete numeric forms.

---

<a id="packed-collections"></a>
#### Packed collections

<a id="shared-representation"></a>
##### Shared representation

A packed collection has a uniform statically known element representation.

Example:

```caret
(Packed Byte) bytes =
  [12 48 91 255]
```

The representation may contain only the element data:

```text
0C 30 5B FF
```

with the common element representation stored once as collection metadata.

Individual elements require no separate type or layout descriptor.

---

<a id="packed-versus-homogeneous"></a>
##### Packed versus homogeneous

A homogeneous semantic contract is weaker than a packed representation.

For example:

```caret
(Collection Number) values
```

does not imply packed storage.

Even:

```caret
(Collection Int) values
```

need not necessarily promise a particular physical width or layout if `Int` has implementation-dependent representation.

By contrast:

```caret
(Packed Int32) values
```

requires a concrete uniform representation.

Conceptually:

```text
Packed T => Collection T
```

but:

```text
Collection T !=> Packed T
```

---

<a id="packed-structural-values"></a>
##### Packed structural values

Packed elements need not be primitive scalars.

A structural type may have a shared layout.

For example, conceptually:

```text
Vertex
    position : 3 × Float32
    normal   : 3 × Float32
    uv       : 2 × Float32
```

Then:

```caret
(Packed Vertex) vertices
```

may carry one common descriptor:

```text
stride      32 bytes
position    offset 0
normal      offset 12
uv          offset 24
```

while the collection storage contains only packed vertex records.

This is suitable for:

* GPU buffers;
* SIMD processing;
* native interop;
* audio buffers;
* binary I/O;
* memory mapping;
* network buffers.

---

<a id="metadata-placement-rule"></a>
##### Metadata placement rule

The semantic rule is:

> A collection may provide metadata that applies to every element. An element requires additional metadata only when the collection-level metadata is insufficient to determine that element's relevant type or representation.

Examples:

```caret
[1 2 3]
```

may use one shared `Int` descriptor.

```caret
[1 2.0 3]
```

may share `Number` while retaining information distinguishing `Int` from `Float`.

```caret
[1 "two" true]
```

requires heterogeneous element information.

```caret
(Packed Int32) [1 2 3]
```

has a complete common element layout and needs no per-element representation metadata.

---

<a id="relationship-to-formats"></a>
#### Relationship to formats

See [Formats and Codecs](10-formats-and-codecs.md#formats-as-specialized-collections).

Contracts and physical representation are separate concepts.

A contract describes:

```text
which values are valid
```

A layout describes:

```text
how values are represented in memory
```

A `Format` describes:

```text
logical value <-> external representation
```

These concepts may cooperate without being collapsed into one abstraction.

For example, a packed collection may use a shared representation compatible with a `Format`, but being a `Packed` collection does not itself make the collection a `Format`.

This separation allows the compiler to optimize memory layout without changing logical contract semantics.

---

<a id="implementation-requirements"></a>
#### Implementation requirements

The initial implementation should support at minimum:

1. `contract` as the fundamental type-definition mechanism.
2. Base/tag contracts:

```caret
Eq = contract ~
```

3. Contract derivation:

```caret
Number = contract [Eq Comparable]
```

4. Multiple derivation.
5. Contracts usable as membership predicates.
6. Ordinary pure predicates used as refinements.
7. Derived refinement contracts:

```caret
PositiveInt = contract [Int positive]
```

8. Separate function definitions for operations.
9. Contract-based function specialization and most-specific dispatch.
10. Ambiguity diagnostics for incomparable applicable function implementations.
11. A general `Collection` contract.
12. Parameterized collection contracts.
13. A universal square-bracket collection literal:

```caret
[1 2 3]
```

14. Empty collection literals:

```caret
[]
```

15. Homogeneous collections.
16. Heterogeneous collections.
17. Nested collections.
18. Named fields with `^`.
19. Dynamic fields through ordinary `field` construction.
20. Dictionary-like collections using field elements.
21. Shared collection-level element metadata.
22. Per-element metadata where required.
23. Distinction between common semantic contract and common physical representation.
24. Packed collections with uniform representation metadata.
25. Compiler/runtime freedom to optimize metadata representation without changing observable semantics.
26. Non-empty Collections that are entirely positional or entirely named.
27. A located `MIXED_COLLECTION_SHAPE` diagnostic for mixing Field and unnamed elements.
28. Named fields constructed interchangeably with `^name = value` or `field key value`.
29. One shape-neutral empty Collection satisfying zero-compatible collection contracts vacuously.
30. Exact structural contracts remaining unsatisfied when their required positions or fields are absent.
31. Exported blocks producing named Collections containing only their exported fields.
32. Observational equivalence between exported-block shorthand and explicit named Collections.
33. Imported module exports using the same named-Collection member model.
34. `with` exposing named Collection fields without converting a Collection into a lexical scope.
35. Lexical scopes remaining non-value compiler/evaluator environments.

The initial implementation may postpone:

* sophisticated automatic memory-layout optimization;
* GPU-specific layout attributes;
* structure-of-arrays transformations;
* compressed runtime type tags;
* zero-copy format views;
* advanced generic constraint inference.

These later features must preserve the fundamental distinction between contract membership, element-specific metadata, and shared collection representation.

---

<a id="design-principle"></a>
#### Design principle

Caret uses one conceptual system for type constraints:

```text
type
interface
refinement
capability
    -> Contract
```

Derivation means logical inclusion:

```text
Derived x => Base x
```

Behavior remains outside the type hierarchy and is expressed through ordinary polymorphic functions.

Collections likewise use one literal form:

```caret
[...]
```

The literal specifies its elements, not its container implementation.

Contracts determine whether the resulting value behaves as a:

```text
List
Array
Set
Dictionary
Packed buffer
heterogeneous collection
...
```

and the runtime stores metadata at the narrowest level necessary:

```text
shared by collection when possible
per element only when necessary
```

This allows the same collection abstraction to range from fully heterogeneous structured data to tightly packed GPU-compatible buffers without introducing separate literal syntaxes or unrelated collection models.

<a id="templates"></a>
## Templates

<a id="overview"></a>
### Overview

A **template** is a contract describing the structure and contents of a collection.

A template is constructed by calling the ordinary `template` function with either a concrete
collection or a reifiable function produced by a collection expression containing:

* holes;
* contracted holes;
* fixed values;
* named fields;
* nested templates or collections.

Example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

`Point` is a contract describing a two-element collection whose elements both satisfy `Float`.

Therefore:

```caret
Point [10.0 20.0]
```

is true, while:

```caret
Point [10.0 "hello"]
```

is false.

Templates use the ordinary Caret contract system.

They do not introduce a separate type system.

`template specimen` is ordinary whitespace application. There is no `template` parser production,
template-only invocation syntax, or spelling-based semantic rule. Inspection of a collection
constructor descriptor is behavior of the resolved language-owned `template` callable and its
contracts. Aliases retain the same ordinary callable behavior.

---

<a id="template-construction"></a>
### Template construction

The general form is:

```caret
template specimen
```

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

Collection literals are hole-expression boundaries: their holes are materialized as a constructor
function before a surrounding application consumes the literal's value. Because this collection
contains holes, it first becomes a function whose parameters fill those holes and whose result is
the completed collection. The ordinary `template` function then derives a contract from that
function's language-owned collection constructor descriptor.

The supported overloads are conceptually:

```text
template : Collection -> Contract
template : CollectionConstructor -> Contract
```

A concrete collection produces a fixed-only exact template. A `CollectionConstructor` is an
ordinary hole function whose retained descriptor is structurally a collection construction.
`template` is not syntax and creates no exception to ordinary application or hole evaluation.

For example, the compact form above is equivalent to:

```caret
PointConstructor =
  [
    (Float) _
    (Float) _
  ]

Point = template PointConstructor
```

Calling `PointConstructor 10.0 20.0` produces `[10.0 20.0]`; calling
`template PointConstructor` instead derives the corresponding membership contract.

Only reifiable hole functions whose expression directly constructs a collection are accepted
initially. Named functions, opaque/native callables, and partial expressions such as `[transform _]`
whose hole is used in a computed element are rejected with a located
`TEMPLATE_INVALID_CONSTRUCTOR` diagnostic. This restriction avoids attempting general function
inversion. The descriptor is language-owned metadata and must never expose Java AST or runtime
implementation objects.

Template membership is the structural inverse of construction: candidate elements occupy hole
positions, captured values compare equal, and fields and nested collections match recursively. The
constructor is not invoked during membership testing.

The resulting value may be used anywhere an ordinary contract may be used.

For example:

```caret
(Point) position
```

or:

```caret
(Collection Point) positions
```

---

<a id="holes"></a>
### Holes

<a id="unconstrained-holes"></a>
#### Unconstrained holes

A plain hole:

```caret
_
```

represents a position whose value may vary freely.

Example:

```caret
Pair =
  template [
    _
    _
  ]
```

matches any two-element collection:

```caret
[1 2]
["a" true]
[person 42]
```

provided the collection has the required shape.

---

<a id="contracted-holes"></a>
#### Contracted holes

Normal Caret contract syntax may constrain a hole:

```caret
(Int) _
```

Example:

```caret
IntegerPair =
  template [
    (Int) _
    (Int) _
  ]
```

matches:

```caret
[10 20]
[-1 42]
```

but not:

```caret
[10 "twenty"]
```

Multiple contracts use the normal Caret contract syntax:

```caret
PositiveIntegerPair =
  template [
    (Int positive) _
    (Int positive) _
  ]
```

No template-specific constraint syntax is required.

<a id="numbered-holes"></a>
#### Numbered holes

Numbered holes retain their ordinary partial-application meaning in collection constructors:

```caret
Diagonal = template [_1 _1]
Swapped = template [_2 _1]
```

Repeated occurrences of the same numbered hole describe the same constructor parameter and
therefore require the corresponding candidate positions to be equal. Numbering and reordering
change the constructor's parameter order, not the collection's structural order. Any contracts on
repeated occurrences must all hold for the shared candidate value.

As with every partial expression, numbered and unnumbered holes may not be mixed.

---

<a id="fixed-values"></a>
### Fixed values

A template element that is not a hole represents a fixed-value requirement.

Example:

```caret
Header =
  template [
    0xCA
    0xFE
    (Int) _
  ]
```

matches:

```caret
[0xCA 0xFE 100]
```

but not:

```caret
[0xCA 0xFF 100]
```

Conceptually, a fixed element:

```caret
42
```

requires:

```text
eq actual 42
```

using the applicable polymorphic equality operation. Non-hole subexpressions are evaluated and
captured eagerly when the collection-producing hole function is created; deriving or testing the
template does not repeat those effects. A fixed template value must support Caret
equality. Template construction fails with a located diagnostic when a fixed value is callable or
otherwise non-comparable; membership testing must not turn such a value into an exceptional
predicate.

Thus a template may combine exact-value requirements and contract requirements.

---

<a id="template-semantics"></a>
### Template semantics

For:

```caret
T =
  template [
    fixed
    (Contract) _
    _
  ]
```

a candidate collection satisfies `T` when:

1. it has the required collection shape;
2. the first element equals `fixed`;
3. the second element satisfies `Contract`;
4. the third element exists but is otherwise unrestricted.

Conceptually:

```text
T value =
    Collection value
    and shape value == templateShape
    and eq value[0] fixed
    and Contract value[1]
```

with no additional value constraint on `value[2]`.

The compiler may perform these checks statically when sufficient information is known.

Runtime validation is required only where the contract cannot be proven statically.

---

<a id="shape"></a>
### Shape

A template describes collection **shape** as well as element constraints.

A non-empty template is structurally either positional or named, following the ordinary Collection
rule. A Collection specimen supplied to `template` cannot mix named Field elements with unnamed
positions; doing so produces
`MIXED_COLLECTION_SHAPE`. There is no separate scope-template model. The specimen `[]` describes
the exact empty shape. Every explicit non-empty template requires all declared positions and all
named fields not explicitly designated optional by the general semantics below.

For a positional collection:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

the shape includes:

```text
element count: 2

position 0:
    Float hole

position 1:
    Float hole
```

Therefore a three-element collection does not satisfy `Point`:

```caret
Point [1.0 2.0 3.0]
```

is false.

Likewise:

```caret
Point [1.0]
```

is false.

For positional templates, element count and position are part of the template contract.

---

<a id="named-fields"></a>
### Named fields

Templates may describe named structured collections using ordinary `^` fields.

Example:

```caret
Person =
  template [
    ^name = (String) _
    ^age = (Int positive) _
    ^active = true
  ]
```

A matching value may be:

```caret
[
  ^name = "Alice"
  ^age = 42
  ^active = true
]
```

The template requires:

```text
name:
    satisfies String

age:
    satisfies Int
    satisfies positive

active:
    equals true
```

Field names are part of the template structure. For the initial exact model, named shape means the
exact set of field names and the field ordering defined by the universal collection model. Missing,
additional, or reordered fields are incompatible whenever that ordering is observable for the
candidate collection.

The general template model also supports members explicitly designated optional by the template
descriptor. A candidate may omit any such member; if present, it must occupy the ordinary named
Collection shape and satisfy the member's fixed-value, hole, or contract requirement. Members not
declared by the template remain incompatible, so optional members do not make a template open.
This capability is required by structural contracts such as `RuleDefinition`. Its final Caret
surface spelling is unresolved; implementations and examples must not invent a feature-specific
spelling or silently treat `T?`, `T~`, or optional lookup syntax as an optional-field declaration.

The template system does not require a separate record-schema syntax.

---

<a id="dynamic-fields"></a>
### Dynamic fields

Templates may use ordinary field values where dynamic keys are required.

For example, where appropriate:

```caret
template [
  field key (String) _
]
```

uses the same first-class field mechanism as ordinary collection construction. The key expression
is evaluated exactly once when the collection or hole function is created. It must produce a valid field name, and
duplicate resolved names are located template-construction diagnostics.

Templates do not introduce another dictionary representation.

---

<a id="template-construction-diagnostics"></a>
### Template construction diagnostics

Templates reuse ordinary language diagnostics when construction fails in an ordinary contract,
field, or hole mechanism. The stable mapping is:

* `TEMPLATE_INVALID_CONSTRUCTOR` when `template` receives a callable that is not an eligible,
  reifiable collection constructor;
* `TEMPLATE_NONCOMPARABLE_FIXED_VALUE` when a captured fixed value does not support Caret equality;
* `INVALID_DYNAMIC_FIELD_NAME` when a dynamic key does not produce a valid field name;
* `DUPLICATE_FIELD` when two elements resolve to the same field name;
* the ordinary contract diagnostic, including `UNKNOWN_CONTRACT`, `NOT_A_CONTRACT`, or
  `PARSE_INVALID_CONTRACT` as appropriate, when a contracted hole has an invalid requirement; and
* `MIXED_HOLE_STYLES` when numbered and unnumbered holes are mixed.

The code describes the failure independently of when it becomes knowable. Malformed syntax retains
phase `PARSER`, a well-formed failure established by analysis uses phase `SEMANTIC`, and a failure
that depends on a dynamically obtained value uses phase `RUNTIME`. Where the same behavioral code
can arise during analysis or evaluation, those phases share that code. The invalid constructor,
fixed value, dynamic key, contract term, or hole is the primary location. For `DUPLICATE_FIELD`, the
later field is primary and the first field with that resolved name is a related location.

---

<a id="nested-templates"></a>
### Nested templates

Templates may contain nested collection structure.

Example:

```caret
Person =
  template [
    ^name = (String) _

    ^position =
      [
        (Float) _
        (Float) _
      ]
  ]
```

Within an eligible collection-constructor descriptor, a bare nested collection contributes
recursively to the outer template shape; it is not a fixed-value equality requirement.
Non-collection expressions that are neither holes nor contracted holes remain fixed-value
requirements.

A matching value is:

```caret
[
  ^name = "Alice"
  ^position = [
    10.0
    20.0
  ]
]
```

The implementation should recursively derive structural constraints from nested collection values.

Where a reusable nested contract is preferable, an explicitly defined template may be used:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

Person =
  template [
    ^name = (String) _
    ^position = (Point) _
  ]
```

Both forms participate in the ordinary contract system.

---

<a id="templates-are-contracts"></a>
### Templates are contracts

The result of `template` is a normal Caret contract.

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

may be used as:

```caret
(Point) p
```

or:

```caret
(Collection Point) points
```

or as part of another contract:

```caret
VisiblePoint =
  contract Point visible
```

Templates therefore participate in:

* contract derivation;
* parameter contracts;
* return contracts;
* collection element contracts;
* polymorphic function dispatch;
* runtime contract checks;
* static contract inference.

No separate "template type" mechanism is required.

---

<a id="template-derivation"></a>
### Template derivation

Templates may participate in ordinary derived contracts.

Example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

NonZeroPoint =
  contract Point nonZero
```

Conceptually:

```text
NonZeroPoint value
    =
Point value
and nonZero value
```

Likewise, a structural template may derive from or be combined with other collection-related contracts.

---

<a id="collections-of-template-shaped-values"></a>
### Collections of template-shaped values

Templates are particularly useful as common metadata for homogeneous structural collections.

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

(Collection Point) points =
  [
    [1.0 2.0]
    [3.0 4.0]
    [5.0 6.0]
  ]
```

Every element has the same template shape.

An implementation may avoid repeating the full structural metadata for every point.

Conceptually:

```text
collection metadata:

    element contract:
        Point

    Point shape:
        two Float positions

values:

    [1.0 2.0]
    [3.0 4.0]
    [5.0 6.0]
```

The template may be stored once as common collection metadata.

---

<a id="templates-and-metadata"></a>
### Templates and metadata

A template may describe more precise structural metadata than a broad element contract.

For example:

```caret
(Collection Number) values
```

only establishes that every element satisfies `Number`.

By contrast:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

establishes:

* collection arity;
* element positions;
* element contracts;
* structural nesting;
* named fields where present;
* fixed values where present.

A collection whose elements all satisfy the same template can therefore share this metadata.

The optimization permission is:

> When every element of a collection has the same template shape, the template may serve as shared element metadata for the entire collection.

The physical metadata representation remains an implementation detail. Sharing must not change
value identity, structural equality, reflection, evaluation order, or any other observable behavior.

---

<a id="templates-and-packed-collections"></a>
### Templates and packed collections

A template may also provide the structural information required for a packed representation.

For example:

```caret
Point =
  template [
    (Float32) _
    (Float32) _
  ]
```

then:

```caret
(Packed Point) points =
  [
    [1.0 2.0]
    [3.0 4.0]
    [5.0 6.0]
  ]
```

may be represented physically as:

```text
Float32 Float32
Float32 Float32
Float32 Float32
```

with the `Point` layout descriptor stored once for the collection.

Conceptually:

```text
element layout:

    position 0:
        Float32
        offset 0

    position 1:
        Float32
        offset 4

stride:
    8 bytes
```

followed by packed values.

The template itself describes logical structure.

`Packed` additionally requires that this structure have a uniform concrete physical representation.

Therefore:

```text
template shape
    !=
packed layout
```

but a sufficiently concrete template may allow a packed layout to be derived.

---

<a id="heterogeneous-template-collections"></a>
### Heterogeneous template collections

A collection may also contain templates themselves.

For example:

```caret
patterns =
  [
    template [1 _ _]
    template [2 _ _]
    template [3 _ _]
  ]
```

These templates share the same structural form:

```text
three positions

position 0:
    fixed value

position 1:
    hole

position 2:
    hole
```

Only the fixed value differs between instances.

The runtime may therefore share the common template metadata across the collection.

Conceptually:

```text
collection metadata:

    element kind:
        Contract

    common template shape:
        [Fixed Hole Hole]

elements:

    fixed value = 1
    fixed value = 2
    fixed value = 3
```

This allows collections of structurally equivalent templates to be represented efficiently.

---

<a id="template-shape-metadata"></a>
### Template shape metadata

Two templates may have the same metadata shape while containing different fixed values.

For example:

```caret
template [1 _ _]
template [2 _ _]
template [100 _ _]
```

share the structural descriptor:

```text
[
    Fixed
    Hole
    Hole
]
```

Similarly:

```caret
template [
  ^opcode = 1
  ^arg = (Int) _
]

template [
  ^opcode = 2
  ^arg = (Int) _
]
```

share:

```text
fields:

opcode:
    fixed-value position

arg:
    Int hole
```

while the fixed `opcode` value differs.

An implementation may factor common template structure into collection-level metadata.

---

<a id="templates-and-ordinary-collection-literals"></a>
### Templates and ordinary collection literals

Square brackets retain exactly one fundamental meaning:

```caret
[...]
```

describes collection construction.

A collection expression containing holes follows the ordinary partial-application rule: it evaluates
to a function whose parameters fill the holes and whose result is the completed collection. A
collection literal owns the holes in its structural expression and materializes that function before
the value is passed to a surrounding call. Thus `consume [1 _]` passes a constructor function to
`consume`; it does not make the whole `consume [1 _]` application partial. This is a general
collection rule, not behavior specific to `template`.

Evaluation proceeds recursively. A nested literal used directly as an element belongs to the
enclosing collection's structural constructor, so its holes contribute to the same reifiable
descriptor. A nested literal used as the operand of an inner call materializes its constructor for
that call first. A collection expression never evaluates to a collection containing hole values and
does not automatically become a template.

Template construction is explicit:

```caret
template [...]
```

This distinction is intentional.

For example:

```caret
[
  (Float) _
  (Float) _
]
```

is an ordinary two-argument function. Supplying two values constructs the completed collection.

Passing that function to the ordinary `template` callable creates a structural contract:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

The language therefore has only ordinary Collection literal grammar; `template` consumes the
resulting value or eligible constructor through ordinary application.

---

<a id="relationship-to-ordinary-holes"></a>
### Relationship to ordinary holes

Templates reuse the normal Caret `_` syntax.

A hole means that some value is intentionally unspecified.

Within a collection-producing hole function passed to `template`:

```caret
template [...]
```

the hole represents a variable position in the matched collection.

A contracted hole:

```caret
(Float) _
```

means:

> this position is variable, but any value occupying it must satisfy `Float`.

A fixed element:

```caret
10
```

means:

> this position is not variable; its value must equal `10`.

No additional placeholder syntax is required.

---

<a id="templates-and-polymorphic-dispatch"></a>
### Templates and polymorphic dispatch

Because templates are contracts, they may specialize function definitions.

Example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

(String) toString (Point) p =
  ...
```

A value satisfying `Point` may therefore select the `Point` specialization of `toString`.

Likewise:

```caret
(Float) distance (Point) a (Point) b =
  ...
```

uses ordinary contract-based function polymorphism.

Template dispatch is not a separate dispatch system.

---

<a id="templates-and-static-checking"></a>
### Templates and static checking

The compiler should statically validate template contracts whenever possible.

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

(Point) p =
  [1.0 2.0]
```

may be proven valid at compile time.

This:

```caret
(Point) p =
  [1.0 "two"]
```

should produce a compile-time error when the compiler knows that `"two"` does not satisfy `Float`.

Likewise:

```caret
(Point) p =
  [1.0 2.0 3.0]
```

may be rejected statically because its shape is incompatible.

When the candidate value is only known dynamically, normal runtime contract checking applies.

---

<a id="exact-shape"></a>
### Exact shape

The initial template model uses exact structural shape.

For positional collections:

```caret
template [
  (Int) _
  (Int) _
]
```

requires exactly two positions.

For named structures, required fields described by the template are part of its required shape;
members explicitly designated optional follow the general rule above.

The initial implementation should treat additional unmatched structural members as incompatible unless another contract explicitly provides open/extensible-template semantics.

A future contract may provide open structural matching where useful, but it should not silently change the meaning of ordinary `template`.

---

<a id="templates-versus-formats"></a>
### Templates versus formats

Templates and formats describe different relationships.

A template describes:

```text
Value -> Boolean
```

by specifying a collection's logical shape.

A format describes:

```text
logical value <-> representation
```

A template may therefore be used as the logical contract of values processed by a format.

For example, a format may decode data satisfying:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

without making `Point` itself a serialization format.

Likewise, a concrete template may provide enough structural information to derive a packed memory layout without becoming a `Format`.

---

<a id="reflection"></a>
### Reflection

Templates are first-class contract values and should be reflectable. Their public kind remains
`Contract`; template shape is metadata on the language-owned contract descriptor rather than a
separate `Template` value kind.

Reflection may expose information such as:

```text
shape
element count
field names
required/optional field membership
hole positions
hole contracts
fixed positions
fixed values
nested templates
derived contracts
```

For example:

```caret
@Point
```

may expose the structure represented by:

```caret
template [
  (Float) _
  (Float) _
]
```

subject to ordinary Caret reflection and sandbox rules.

This enables tooling such as:

* schema viewers;
* editors;
* serializers;
* binary layout tools;
* GPU buffer inspectors;
* pattern editors;
* generated documentation.

---

<a id="implementation-requirements-2"></a>
### Implementation requirements

The initial implementation should support at minimum:

1. Ordinary `template` function application to concrete collections or eligible collection-producing
hole functions:

```caret
template [...]
```

2. Templates as ordinary contracts.

3. Unconstrained holes:

```caret
_
```

4. Contracted holes:

```caret
(Int) _
```

5. Multiple contracts on a hole:

```caret
(Int positive) _
```

6. Numbered-hole reordering and repeated-hole equality constraints.

7. Fixed-value positions.

8. Equality-based checking of fixed values.

9. Exact positional shape matching.

10. Named fields using `^`.

11. Nested collection shapes.

12. Reusable named templates:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

13. Templates usable as binding and parameter contracts.

14. Templates usable as collection element contracts:

```caret
(Collection Point) points
```

15. Templates participating in ordinary contract derivation.

16. Template-based polymorphic function specialization.

17. Static template validation where possible.

18. Runtime validation where static proof is unavailable.

19. Shared template metadata for collections whose elements have a common shape.

20. Compatibility with packed collection layout when all required representations are concrete.

21. Reflection over template structure.

22. Located diagnostics for invalid constructors, fixed values, dynamic field names, duplicate
fields, and malformed contracted holes.

23. Identical observable behavior with shared-template and packed-layout optimizations disabled.

24. General optional named members, including reflection of required/optional membership, for
structural contracts that require them such as `RuleDefinition`; the final declaration spelling is
unresolved.

The initial implementation may postpone:

* open structural templates;
* variable-length positional templates;
* repeated subpatterns;
* template unions;
* destructuring/binding values from matched holes;
* automatic construction from template holes;
* generalized pattern-matching syntax;
* compile-time layout optimization across arbitrary recursive templates.

These later features should preserve the fundamental model that:

```caret
template [...]
```

calls an ordinary function that constructs a contract from a concrete collection or from the
language-owned descriptor of an eligible collection-producing hole function.

---

<a id="design-principle-2"></a>
### Design principle

A Caret template is an explicitly constructed structural contract.

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

means:

> `Point` is the contract for collections of exactly this shape, with two variable positions, each constrained by `Float`.

Within an eligible collection constructor:

```text
_                 unrestricted variable position
(Contract) _      constrained variable position
value             fixed-value requirement
collection        nested structural requirement
^name = ...       named structural member
```

Templates reuse ordinary:

* collection syntax;
* holes;
* contracts;
* fields;
* equality;
* polymorphic functions;
* reflection.

When many values or templates have the same shape, that shape may be stored once as shared collection metadata.

This lets templates serve simultaneously as structural types, reusable schemas, and compact shared metadata without introducing a separate object, record, tuple, or schema type system.

<a id="standard-error-template"></a>
### Standard error template

Caret uses one structural information model for recoverable operation failures and aborting
diagnostics. The planned standard library defines an exact outer error shape equivalent to:

```caret
ErrorShape =
  template [
    ^code = (String) _
    ^phase = (String) _
    ^message = (String) _
    ^location = _
    ^related = (Collection) _
    ^cause = _
    ^details = (Collection) _
  ]

ErrorTemplate =
  contract [ErrorShape validErrorMembers]
```

The standard parameterized result contract has exactly three exported fields:

```caret
Result ValueContract =
  contract [
    (template [
      ^ok = (Boolean) _
      ^value = _
      ^error = _
    ])
    (validResult ValueContract)
  ]
```

A successful result is:

```caret
[
  ^ok = true
  ^value = value
  ^error = ~
]
```

A failed result is:

```caret
[
  ^ok = false
  ^value = ~
  ^error = error
]
```

`validResult` requires a successful `value` to satisfy `ValueContract` and requires a failed
`error` to satisfy `ErrorTemplate`. The `ok` discriminator is authoritative because `~` is a valid
successful value. Results do not flatten or propagate implicitly.

Every field is present. `location` and `cause` contain `~` when unavailable; a present cause must
itself satisfy `ErrorTemplate`. `related` is an empty collection when there are no related
locations. `validErrorMembers` supplies these recursive and source-location constraints until the
corresponding parameterized contracts can express them directly.

`code` is a stable machine-readable name. `phase` identifies the producing subsystem, `message` is
human-readable, and `location` is the primary source or representation location. `details` contains
domain-specific information. Formats, sandboxes, contracts, and other subsystems may define exact
templates for their `details` values, but they do not add fields to the outer error shape.

Expected failures of operations such as decoding or sandbox lifecycle calls return a failed
`Result` whose `error` satisfies `ErrorTemplate`. Lexer, parser, semantic, and aborting runtime
errors retain the same fields in their language-owned diagnostic descriptors and render them
consistently, but remain control-flow events rather than ordinary catchable return values.
Unexpected host exceptions and implementation faults must not be silently reclassified as expected
failures.

`ErrorTemplate` defines failure payloads, while `Result` defines the common enclosing protocol.
Public APIs that must return either success or failure still require a separately specified result
envelope; no union, exception-catching, or propagation syntax is implied here.
