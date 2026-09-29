# Collection semantics

[Chapter index](../06-collections-fields-and-templates.md) · [Language specification index](../../LANGUAGE.md)


<a id="collections"></a>
### Collections

<a id="general-collection-contract"></a>
#### General collection contract

`Collection` is the fundamental contract for values containing zero or more elements.

It is the ordinary first-class aggregate model for both positional collections and named structured
values. The prototype implements the general unary contract across Dictionaries and Sequences.
There is no additional `Scope` or named-Collection runtime category for exports.

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
    -> Collection
    -> Ordered
    -> Indexed

Set T
    -> Collection
    -> Unique

Packed T
    -> Collection
    -> Contiguous
    -> Packed
```

---

<a id="parameterized-collection-contracts"></a>
##### Parameterized collection contracts

Concrete collection contracts may be parameterized. The common `Collection` contract itself has no
contract parameters: it is the ordinary unary predicate for membership in any collection kind.

Examples:

```caret
List String
Array Float
Set String
Dictionary String Int
Packed Byte
```

Parameterized collection types should use the normal Caret contract/function model rather than requiring a separate generic-type language.

The current prototype implements this callable-constructor model for `Sequence T`, `Field K V`, and
`Dictionary K V`. Applying a raw constructor to a contract returns a contract; constructors with
several parameters curry one contract at a time. Applying the same raw constructor to a non-contract
value instead performs its raw-kind membership test. Constructors are ordinary first-class Caret
callables: aliases preserve both behavior and remaining constructor arity. `Collection` remains an
unparameterized contract predicate.
Resolver-owned constructor arity also applies inside arrow-contract parameter and result requirements,
so an alias such as `Seq = Sequence` retains the meaning of `[Seq Number] -> Number` without parser
special-casing the alias spelling.

`Sequence T` accepts empty sequences and sequences whose every element satisfies `T`, supports
derived element contracts, nesting, ordinary null/missing modifiers, and reflection of its base and
requirement. Combine element requirements before applying the constructor:

```caret
positive value = Number value & value > 0
PositiveNumbers = Sequence (contract [Number positive])
```

Initial inference retains the outer `Sequence` constraint while element proof remains a runtime check.

Within a contract clause, a known parameterizable constructor consumes its declared number of
following contract terms. This association uses resolved binding metadata rather than constructor
spellings in the grammar. Remaining terms are the existing anonymous conjunction. Thus
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
Each top-level physical line in a multiline literal is one ordinary expression. On one line,
adjacent simple atoms are separate elements. An unparenthesized top-level operator extends an
element through the closing bracket; use parentheses when another element follows it.

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

When a function or ordinary block contains exported bindings, its result is the Dictionary
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

returns the same value as the explicit Dictionary containing `field1` and `field2`.
`temporary` remains a lexical local and is not a Collection element. A body with no exported
bindings retains its ordinary final-expression result. No intermediate Scope object is created.

Equality, reflection, contracts, and member access cannot distinguish exported-block shorthand
from the equivalent explicit Dictionary.

```caret
thing1 == thing2 // true
```

Both reflect with the `Dictionary` kind, named shape, field names, and field metadata. Lexical
scopes do not appear as reflectable values merely because the source block contains declarations.
Field-binding reification uses the same public field interface in either form; semantic owner
references are described in the [state specification](07-01-containers-core.md#field-reification).

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
