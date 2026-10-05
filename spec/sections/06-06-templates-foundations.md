# Templates: foundations

[Chapter index](../06-collections-fields-and-templates.md) · [Language specification index](../../LANGUAGE.md)


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

`template specimen` is ordinary whitespace application. The `template` binding is not a parser
keyword or spelling-based semantic rule. The planned notation below lowers to that same ordinary
application rather than introducing special construction or invocation semantics. Inspection of a collection
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
`template` remains an ordinary binding, not a keyword, and creates no exception to ordinary
application or hole evaluation. Planned sugar does not change this callable model.

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

<a id="planned-template-sugar"></a>
### Template sugar (planned for v1, #61)

The planned `<[ … ]>` notation is sugar for the ordinary application `template [ … ]`, grouped
as one expression. Its contents are always a Collection literal, including an empty literal;
it does not enclose an arbitrary specimen expression. The ordinary callable form still accepts
concrete Collections or eligible constructors supplied through bindings.

The emitted `template` reference uses normal lexical lookup. A local binding or parameter named
`template` shadows the standard callable for this notation exactly as it does for a written
`template [ … ]` application. Sugar does not force the built-in identity, guarantee a Contract
under shadowing, add a scope, or change argument evaluation, effects, or runtime kinds.

This example is planned notation; named-field contracts retain their established spelling:

<!-- caret-example: planned -->
```caret
Form = <[
  ^formType = 10
  ^name = (String) _
  ^data = (Collection) _
]>
```

It denotes the same application as `template [^formType = 10 ^name = (String) _ ^data = (Collection) _]`.
Collection literals remain hole-expression boundaries. Contracted holes, numbered/repeated holes,
fixed captures, missing defaults, and named/positional structure retain their existing rules.
The enclosing sugar must not turn the specimen's holes into parameters of an outer template call.

Nested ordinary `[...]` literals remain Collection structure; they are not separately constructed
template Contracts. Within an eligible constructor, that structure contributes recursively to
the enclosing template as specified below:

<!-- caret-example: planned -->
```caret
Person = <[
  ^name = (String) _
  ^address = [
    ^street = (String) _
    ^city = (String) _
  ]
]>
```

An explicit nested template expression produces an ordinary Contract. To constrain a field using
that Contract, use the existing contracted-hole form rather than placing the Contract in a fixed
field value, where ordinary fixed-value comparability requirements still apply:

<!-- caret-example: planned -->
```caret
Address = <[^street = (String) _ ^city = (String) _]>
Person = <[^name = (String) _ ^address = (Address) _]>
```

Ordinary Collection layout, application, and expression grouping apply inside and around the
notation. Diagnostics retain physical source locations and cover the original delimiters.
Implementation acceptance requires sugar/call equivalence, local shadowing, empty/positional/named
specimens, nested Collections and contracted templates, fixed capture order, all existing hole
rules, multiline layout, and located malformed/unclosed-delimiter diagnostics. This notation is
planned; the current interpreter still requires the ordinary `template` callable form.

<a id="template-constructor-eligibility"></a>
### Constructor eligibility and implemented behavior

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

The prototype implements this exact structural membership model for concrete Collections and
reifiable collection constructors. Positional and named shape, comparable fixed values,
unconstrained and contracted holes, repeated numbered-hole equality, direct nesting, and dynamic
field keys participate in membership. Dynamic keys and fixed expressions are evaluated once during
constructor creation. Template contracts compose with aliases, null/missing modifiers,
parameterized collection contracts, conservative implication, overload dispatch, and ordinary
contract reflection. Every field remains required during membership. The implemented
`TEMPLATE-OPTIONAL-001` rule adds contextual literal completion without weakening that exact
membership rule.

Reflection retains kind `Contract` and adds language-owned `shape`, `size`, and `elements` metadata.
Element metadata identifies its public `id`, constraint kind, zero-based repeated-hole parameter,
and public requirement identifiers without exposing captures, source spans, Java objects, or executable
descriptor internals. Phase 4 adds a Boolean `defaultsMissing` element field so construction
behavior remains observable without exposing source syntax or executable internals.

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

Every field declared by a template must be present in a matching Collection. Implemented Phase 4 contextual
construction may create that present field from an omission in a named Collection literal. A field
is eligible only when its hole clause contains a directly written `T~` or `T?~` term and the full
conjunction accepts missing, including constraints contributed by other occurrences of a numbered
hole. The constructed field receives value `~`; `T?~` additionally permits
an explicitly supplied null but never changes the omission default to null.

The direct suffix is construction metadata, not an accepted-set inference. An alias whose resolved
contract accepts missing permits an explicitly supplied `~`, but does not make omission defaultable.
A fixed `~` element, an unconstrained hole, or a clause whose remaining requirements reject missing
does not enable omission. No separate optional-member syntax is introduced.

For example, using existing template syntax:

```caret
Person = template [
  ^name = (String) _
  ^phone = (String~) _
  ^nickname = (String?~) _
]

(Person) ada = [^name = "Ada"]
```

The annotated initializer supplies one unambiguous expected template, so `ada` contains explicit
`phone = ~` and `nickname = ~` fields. Both fields participate normally in enumeration, access,
reflection, equality, and subsequent membership. A numeric or null `phone` does not satisfy
`String~`; an explicit null `nickname` does satisfy `String?~`.

Expected-template context propagates through an annotated binding initializer, an argument to a
statically known template-constrained parameter, a declared function result, a recursively expected
nested literal, and exported-block shorthand. Dynamic template keys are resolved once when the
template is created and supply the corresponding expected field names. Context is available only
when analysis identifies one template shape unambiguously. Competing overload/template shapes do
not insert fields to decide their own selection.

Only Collection constructors receive this context. Ordinary template application remains a pure
Boolean membership predicate. Explicit conversion requires
the source's established exact shape even when its operand is written as a literal:

```caret
candidate = [^name = "Ada"]

Person candidate             // false
(Person) candidate           // contract violation
(Person) [^name = "Ada"]     // contract violation: explicit conversion
```

The candidate is not mutated and no field is synthesized. Undeclared fields remain incompatible.
For an omitted required or nondefaultable field, report `CONTRACT_VIOLATION` at the literal and
retain the template field declaration as related context. Explicit field initializers keep ordinary
evaluate-once and source-order behavior; inserting `~` evaluates no expression, and the completed
Dictionary retains its ordinary canonical field ordering.

Reflected element metadata sets `defaultsMissing` to true exactly for an eligible named field hole
and false otherwise. Contract membership and implication compare accepted values and exact shape;
they ignore this construction-only flag. `RuleDefinition` uses the same general mechanism for
directly supplied definition literals once that contract is defined. The template descriptor stores
logical element shape and constraints independently of physical Collection layout; contextual
completion creates ordinary Dictionary fields and does not imply a packed representation.

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
(Sequence Number) values
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
