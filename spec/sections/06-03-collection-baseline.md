# Collection baseline and lexical scopes

[Chapter index](../06-collections-fields-and-templates.md) · [Language specification index](../../LANGUAGE.md)


<a id="immutable-collections"></a>
## Immutable collections (implemented baseline)

The prototype provides immutable keyless and keyed Collections, including contract-selected
Dictionaries with homogeneous sortable keys. Exported blocks and static named literals produce
String-keyed Dictionaries; ordinary `field key value` construction also supports other keys under
the [Collection protocol](06-01-collection-protocol.md#fields-tuples-sets-and-contextual-shape).
The existing sequence and String-keyed Dictionary helpers remain available:

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

Dictionary enumeration uses sorted key order; String keys use locale-independent, case-sensitive
Unicode code-point order. General keyed Collections permit equality-comparable keys without
requiring homogeneous types or sorting. The legacy `dictPut`/`dictGet`/`dictHas`/`dictKeys` helpers
operate on String-keyed Dictionaries; `dictHas` distinguishes an absent key from a present `~`.
The common protocol supplies access and enumeration for the broader Collection model.
Positional and keyed literal syntax is implemented, including static `^name` shorthand and
first-class Field construction. Context-selected Sequence, Set, Dictionary, and Packed
representations are implemented where requirements are unambiguous; further representations remain
planned.

<a id="higher-order-sequence-operations"></a>
### Higher-order Collection operations

The standard operations are `map transform values`, `filter values predicate`,
`fold values initial combine`, `any values predicate`, and `all values predicate`. `map` preserves
its established callable-first order; the others are collection-first and can read naturally in
named-infix form. `map` and `filter` produce lazy Collections without invoking their callbacks at
construction. `filter` retains source traversal order and keys for keyed inputs, and compacts
keyless positional indices. `fold` is a strict ordered left fold that calls
`combine accumulator element`. Empty keyless filtering produces `[]`; empty `fold`, `any`, and
`all` return the supplied initial value, `false`, and `true`, respectively. Result shape and
guarantees follow the [lazy transform rules](06-01-collection-protocol.md#lazy-transforms-and-consumers).

Predicates may return Boolean, null, or missing. Null and missing count as false; other result kinds
produce a located `INVALID_PREDICATE_RESULT`. Callbacks receive the elements exposed by `fields`:
bare values for keyless inputs and Field tuples for keyed inputs, including Dictionaries and Sets.
`any` stops after its first true result and `all` after its first false result. Callbacks use
ordinary callable arity, contract, call-depth, and effect checks. The operations accept named
functions, lambdas, partials, and compositions. Lazy results establish demanded entries according
to the existing evaluation rules; strict consumers traverse Dictionary fields in sorted key order.

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

Collection literals evaluate field expressions in source order and retain the first equal key.
Dictionaries traverse, render, and reflect fields in sorted key order; for String keys this is
locale-independent, case-sensitive Unicode code-point lexicographic order. General keyed
Collections retain production order. These ordering rules also govern equality.
`^name = value` is shorthand for the ordinary Field whose String key is `"name"`; reserved binding
spellings remain valid static keys because fields are members, not lexical bindings.

The standard `toString` representation preserves collection shape. Empty collections render as
`[]`; flat positional collections render inline as `[ 1 2 3 ]`. String-keyed Dictionaries render
one sorted, quoted key per indented line using `"key" = value`. Fields with non-String keys render
as `field key value`, preserving their Collection's enumeration order. A positional collection
containing another collection also uses indented multiline form. Nested Strings use canonical Caret quoting and
escapes, while a top-level String converts to its raw text. Rendering traverses collections without
consuming the Java call stack and never exposes host implementation text.

Collection conversion recursively uses the active polymorphic `toString` overload set, allowing a
contract-specific specialization to control the representation of a contained value.
