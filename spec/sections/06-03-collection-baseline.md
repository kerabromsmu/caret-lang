# Collection baseline and lexical scopes

[Chapter index](../06-collections-fields-and-templates.md) · [Language specification index](../../LANGUAGE.md)


<a id="immutable-collections"></a>
## Immutable collections (implemented baseline)

The prototype provides String-keyed Dictionaries through exported blocks, explicit named literals,
ordinary `field key value` construction, and persistent updates. It also provides immutable sequences through:

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

Dictionary keys are currently strings, and key iteration uses locale-independent, case-sensitive
Unicode code-point order. `dictHas` distinguishes
an absent key from a present key whose value is
`~`.
Positional and String-keyed Dictionary literal syntax is implemented, including static `^name`
shorthand and dynamic first-class Field construction. Initial context-selected Sequence, Set,
Dictionary, and Packed representations are implemented where requirements are unambiguous;
further representations remain planned.

### Higher-order Sequence operations

The standard operations are `map transform values`, `filter values predicate`,
`fold values initial combine`, `any values predicate`, and `all values predicate`. `map` preserves
its established callable-first order; the others are collection-first and can read naturally in
named-infix form. `filter` retains source order. `fold` is a strict scalar left fold that calls
`combine accumulator element`. Empty results are respectively `[]`, the supplied initial value,
`false`, and `true` for filter, fold, any, and all.

Predicates may return Boolean, null, or missing. Null and missing count as false; other result kinds
produce a located `INVALID_PREDICATE_RESULT`. All elements are passed unchanged. `any` stops after
its first true result and `all` after its first false result. Callbacks use ordinary callable arity,
contract, call-depth, and effect checks. The operations accept named functions, lambdas, partials,
and compositions, return persistent values, and do not traverse Dictionaries.

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

Dictionary values evaluate field expressions in source order, then store, traverse, render, and
reflect fields in locale-independent, case-sensitive Unicode code-point lexicographic order.
Declaration and update order therefore do not affect equality. `^name = value` is shorthand for the
ordinary Field whose String key is `"name"`; reserved binding spellings remain valid static keys
because fields are members, not lexical bindings.

The standard `toString` representation preserves collection shape. Empty collections render as
`[]`; flat positional collections render inline as `[ 1 2 3 ]`. Named collections render one
canonically ordered, quoted key per indented line using `"key" = value`. A positional collection
containing another collection also uses indented multiline form. Nested Strings use canonical
Caret quoting and escapes, while a top-level String converts to its raw text. Rendering traverses
collections without consuming the Java call stack and never exposes host implementation text.

Collection conversion recursively uses the active polymorphic `toString` overload set, allowing a
contract-specific specialization to control the representation of a contained value.
