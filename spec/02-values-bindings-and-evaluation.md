<a id="values-bindings-and-evaluation"></a>
# Values, Bindings, and Evaluation

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

<a id="values"></a>
## Values

```text
42          number
"text"      string
true false  Boolean
?           null
~           missing
```

Null and missing are separate runtime values.

`type value` returns the public runtime kind name, including `"Null"` for `?` and `"Missing"` for
`~`. Reflection itself produces a `"Dictionary"`; its `kind` field describes the reflected target.

Number literals start with a digit and may contain at most one decimal point. Malformed number
literals are reported as language errors rather than leaking a Java numeric-conversion exception.
Numbers must remain finite. Literals outside the finite range and arithmetic producing a non-finite
result are errors. Division and remainder by zero are errors.

### Immutable semantics and storage ownership

Caret values are observably immutable. An implementation may reuse collection storage only while
an internal ownership fact proves that no alias can observe the older representation. Binding,
passing into an interpreted function, capture by a closure or partial, export, reflection, and
insertion beneath a shared collection conservatively end uniqueness. Unknown ownership always uses
persistent allocation.

Ownership is neither a Caret value nor reflective metadata, and it does not affect equality,
ordering, diagnostics, or effects. The prototype has an internal optimization-disabled mode whose
persistent behavior is authoritative. Its enabled mode currently reuses proven-unique ephemeral
Sequence and Dictionary storage for `seqAdd` and `dictPut`; both modes must produce identical
observable results, including on failures.

Strings recognize `\\`, `\"`, `\n`, `\r`, `\t`, and Unicode code-point escapes written as
`\u{1F642}`. Unknown, incomplete, surrogate, and out-of-range escapes are lexical errors.

<a id="conditional-expression"></a>
## Conditional expression

```text
condition & trueValue ! falseValue
```

Only the selected branch is evaluated.

Without a false branch:

```text
condition & value
```

false produces `~`.

<a id="boolean-operations"></a>
## Boolean operations

```text
a and b
not a
a or b
```

`and` and `or` short-circuit.

<a id="scopes"></a>
## Named Collections and lexical scopes

```text
makeA =
  ^name = "A"
  ^count = 10

a = makeA
print a.name
```

A missing required field is an error:

```text
a.enabled
```

Optional lookup returns `~`:

```text
a.enabled~
```

Exported blocks produce immutable named Collections. An explicit named literal such as
`[^name = "A" ^count = 10]` produces the same value. Lexical scopes remain private evaluator and
resolver mechanisms rather than first-class values.

<a id="dynamic-lookup"></a>
## Dynamic lookup

```text
field = "count"
a[field]~
a["count"]~
```

Dynamic names are strings. The `~` suffix makes a missing binding a normal result instead of an error.

<a id="reflection"></a>
## Reflection

```text
meta = @a
meta.kind
meta.size
meta.names

functionMeta = @function
functionMeta.kind
functionMeta.remaining
functionMeta.signature
functionMeta.variants
```

Current metadata:

- all values: `kind`
- named Collections: `shape = "named"`, `size`, `names`
- function metadata: `kind = "Function"`, visible declaration `name` or `~`, `remaining`,
  language-owned `signature`, and surviving overload `variants`

Every reflection result is a named metadata Collection with runtime kind `Dictionary`. It retains
an opaque reference to its target, which adjacent postfix `:` dereferences:

```text
reference = @function
alias = reference:
directAlias = @function:
```

The metadata dictionary is non-callable; dereferencing restores the exact original function or
value. `:` on any value not produced directly by reflection is a located `NOT_DEREFERENCEABLE`
error. Ordinary aliases preserve dereferenceability, while dictionary updates produce ordinary
dictionaries. Equality and rendering observe only public metadata fields, never the opaque target.
Every reflection result captures its creation environment's dereference authority. Dereference uses
the intersection of that captured authority and the current observer's authority, so retaining,
nesting, aliasing, or re-reflecting metadata can preserve or reduce access but can never amplify it.
This interpreter/compiler context is not exposed as a Caret value.

`@` consumes exactly one identifier or literal, including a Collection literal. Parentheses are
required to reflect a larger expression: `@f x` means `(@f) x`, while `@(f x)` reflects the call
result. Postfix lookup applies to the returned metadata, so `@function.kind` needs no grouping.
Static member reflection is `object.@field`; dynamically named members use
`@(object[fieldName])`.

The callable schema below is implemented for the current named functions, built-ins, prefix
partials, compositions, and closed overload sets. Its immutable descriptors separate effective,
declared, and inferred facts; unknown information is `~`, while known-empty facts are empty
sequences. Reflection exposes no captures, bound partial values, source provenance, native origin,
implementation objects, or authority. Arrow-signature contracts and later callable kinds extend
this same metadata model. Other value kinds gain descriptors with their corresponding features.

<a id="core-semantic-decisions"></a>
## Core semantic decisions

Blocks predeclare their function bindings before executing statements. This supports direct and
mutual recursion. Other bindings are initialized in source order and cannot be read before their
declaration executes.

Initialization checks respect lazy evaluation. A reference to a later binding in an unselected
conditional branch or a short-circuited Boolean right operand does not fail; selecting that path
before the declaration executes produces a located `READ_BEFORE_INITIALIZATION` diagnostic.

Top-level execution commits newly declared bindings only when the submitted program completes.
This is observable in the REPL: after a failed submission such as `x = absent`, a later `x = 1`
submission remains valid. External effects already performed before a failure are not rolled back.

Closures capture their lexical environment. Duplicate definitions and duplicate parameters in one
scope are errors. Parameters and declarations in a function body may shadow outer bindings;
function-body declarations are nested inside the parameter scope so established forms such as
`^name = name` export a parameter under the same name. Parent lookup is lexical.

In the current prototype, equality is recursive and structural for scalar values, named Collections,
sequences, and dictionaries. Scalar
numeric equality therefore has the same result when numbers are nested in data; for example, `-0`
and `0` compare equal both directly and inside a sequence. Encountering a callable anywhere in
either compared structure is a `CALLABLE_EQUALITY` error. Function references compare by the
identity of their referenced callable.

`@function` produces a non-callable metadata dictionary. It suppresses normal implicit invocation
of a nullary binding and exposes `kind` and `remaining`; `@function:` restores the callable, while
bare nullary function names continue to invoke automatically.

Built-in symbolic binary operators are ordinary two-argument callable values. Prefix and infix
forms share the same implementation, arity, partial application, call-depth guard, and errors:

```text
+ 2 3       // 5
2 + 3       // 5
increment = + _ 1
```

Unary negation retains its established parsing for `- name arg`. Use grouping when prefix
subtraction begins with a named operand: `(-) left right`.

Function invocation has an interpreter-owned maximum depth. Both ordinary application and the
implicit invocation of nullary bindings produce a located `CALL_DEPTH_EXCEEDED` diagnostic instead
of exposing JVM stack exhaustion.

The planned static contract system preserves this runtime behavior through the normative operator
matrix below. Concrete numeric representations added later require explicit specialized variants;
they do not silently change these scalar rules.

The self-interpreter may represent successful and failed operations as named result collections. Its
CLI adapter can then render a failed result as the normal located `Error:` diagnostic.

<a id="not-required-for-self-interpretation"></a>
## Not required for self-interpretation

The first Caret-written interpreter does not depend on static types, loops, mutation, modules,
lambdas, pattern matching, ownership, reflected invocation, or a compiler backend. Recursion,
immutable collections, named exported collections, and the planned text operations are sufficient.
