<a id="values-bindings-and-evaluation"></a>
# Values, Bindings, and Evaluation

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

<a id="values"></a>
## Planned lazy values and lexical contexts

This planned general rule applies to lazy values throughout the language, including Collection
access and reflection. It is not specific to handlers, `eager`, or any one runtime kind.

First access computes or obtains the specific value; it is not fixed before access. Once obtained,
the value stays fixed in that lexical context. Nested contexts using the same inherited established
binding share it. A fresh invocation creating a new lazy access/binding may obtain a different
value, even for the same provider and key. Do not impose permanent Collection-wide memoization or
a special `eager` context. Stronger provider contracts, such as sequential stability, still apply.
The Collection protocol is owned by the
[Phase 4 revision](06-collections-fields-and-templates.md#phase-4-collection-protocol-revision-planned).

### Deferred computations and synchronization

A computation will represent a completely deferred expression/function call. Constructing it
performs none of its runtime work, including argument evaluation; explicit compile-time work
continues to follow staging rules. Ordinary calls still evaluate where values are needed.
The computation syntax, full contract, and remaining capture/staging integration are deferred
beyond Phase 4. Earlier proposals to evaluate arguments at computation construction are superseded.
Arguments exposed for a failed invocation have already been evaluated at invocation time;
reflection does not evaluate them again.

Concurrent first readers of the same shared lazy value use one producer. Dependent readers suspend
until a result is established and resume on completion; independent work continues. Recoverable
retries, when implemented, leave that result unsettled. These are deferred synchronization design
constraints, not a Phase 4 concurrency implementation.

Runtime dependency-cycle detection is deferred with concurrency/synchronization; lazy dependency
cycles may deadlock for now. This permission does not replace the separate planned `eager`
diagnostic for cyclic Collection containment. No universal timeout or cancellation policy is
selected here.

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

### Phase 4 numeric values and arithmetic (planned)

The following approved design extends the current finite-`double` prototype. It is not implemented
by the existing Number tests. Numeric contract membership is owned by
[the contracts specification](04-contracts-inference-and-dispatch.md#phase-4-numeric-contracts-planned).

`Number` prescribes no storage format. Integer values support arbitrary precision, including every
value in the signed and unsigned 64-bit domains. A runtime must not pass exact integers through
`double` when parsing, storing, comparing, rendering, exporting, or invoking host callbacks.
Storage choice is separate from the mathematical value and its public numeric contracts.

Whole-number tokens without a selecting context produce exact integers. Decimal tokens default to
`Double`. An expected concrete numeric format can instead select the literal representation:

<!-- caret-example: planned -->
```caret
count = 123456789012345678901234567890  // exact integer
ratio = 0.1                           // Double
(Float) sample = 0.1                  // directly rounded to binary32
```

Floating-point literal creation rounds to nearest, ties to even, directly from the source literal
to the selected format; do not first round through another floating-point format. Preserve literal
text/source provenance until contextual selection is known. The existing lexical number spelling
is unchanged. NaN and infinities remain unsupported; a result outside the selected finite range is
an error. No fixed width is silently selected for an unconstrained `Integer` or `Natural`.

| Operation | Phase 4 behavior |
| --- | --- |
| Integer `+`, `-`, `*`, unary negation | Exact arbitrary-precision result; no wrap or clamp. |
| Integer `%` | Exact remainder with the dividend's sign, or zero; zero divisor is an error. |
| `/` on integers | Exact integer quotient if divisible; otherwise the mathematical quotient rounds to `Double`. |
| `div` | Two `Integer` operands; exact quotient truncated toward zero; result is `Integer`. |
| Non-integral real arithmetic | `Double` precision, with ordinary floating-point rounding. |
| Numeric equality and ordering | Compare mathematical values accurately across representations, without a lossy common-`double` shortcut. |

Integer membership is value-based: `7.0` is integral, but `7.5` is not. For integer operands and
nonzero `b`, `a == (a div b) * b + (a % b)`. `/` does not become truncating division because its
operands are integers. A required integer result rejects a mathematically fractional quotient
before floating-point rounding can make it appear integral. Integer fractional division rounds
the quotient, rather than overflowing a floating-point conversion of its integer operands first.
An arithmetic result does not inherit an operand's fixed-width constraint; explicit result
requirements validate it. In particular, adding `255` and `1` produces `256`, which fails `UInt8`.

### Precision requirements and warnings (planned)

An implicit conversion that changes a numeric value is a precision loss. In a broad `Number` or
`Real` result context it warns and continues with the rounded value. Under an explicit concrete
numeric result requirement, or an `Integer`/`Natural` requirement, it is an error. Contextual literal
creation and ordinary floating-point arithmetic rounding are permitted by their selected formats;
they do not generate precision warnings. Explicit conversion deliberately authorizes its documented
rounding or truncation and does not generate an implicit-precision warning.

An explicit `Float` result requirement does not select binary32 arithmetic: non-integral arithmetic
still uses `Double`, and the resulting value must satisfy the requirement. A declaration checks an
existing value without converting it. Inferred default `Double` results alone do not turn the
broad-context warning policy into an explicit strict requirement.

Declared function result contracts govern precision policy inside the function. A function
explicitly returning `Number` can warn and return a rounded value; its caller validates that
established value rather than changing the callee's declaration or inspecting its rounding history.
Ordinary inference applies where there is no explicit declaration boundary. Failures to satisfy
the final result contract remain errors even if an earlier conversion emitted only a warning.

Warnings do not excuse overflow, zero division, invalid operand contracts, or unsupported
conversions. See [diagnostic delivery](01-source-layout-and-diagnostics.md#phase-4-numeric-and-conversion-diagnostics-planned).
Future rational `Fractional`, `Complex`, wider named fixed-width formats, and bit fields are not
part of this implementation boundary.

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
meta.ids

functionMeta = @function
functionMeta.kind
functionMeta.remaining
functionMeta.signature
functionMeta.variants
```

Current metadata:

- all values: `kind`
- named Collections: `shape = "named"`, `size`, `ids`
- function metadata: `kind = "Function"`, visible declaration `id` or `~`, `remaining`,
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

The initial operator matrix records this implemented runtime behavior. The explicitly planned
[Phase 4 numeric revision](#phase-4-numeric-values-and-arithmetic-planned) specifies the changes
for exact integers, concrete formats, and `div`; it does not claim current runtime support.

The self-interpreter may represent successful and failed operations as named result collections. Its
CLI adapter can then render a failed result as the normal located `Error:` diagnostic.

<a id="not-required-for-self-interpretation"></a>
## Not required for self-interpretation

The first Caret-written interpreter does not depend on static types, loops, mutation, modules,
lambdas, pattern matching, ownership, reflected invocation, or a compiler backend. Recursion,
immutable collections, named exported collections, and the planned text operations are sufficient.
