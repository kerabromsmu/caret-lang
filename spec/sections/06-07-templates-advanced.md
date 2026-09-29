# Templates: advanced rules

[Chapter index](../06-collections-fields-and-templates.md) · [Language specification index](../../LANGUAGE.md)


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

The prototype implements collection-owned structural constructors for positional and named shapes,
direct nested literals, eagerly captured fixed members, contracted holes, and ordinary or numbered
holes. Repeated numbered holes share one constructor parameter and therefore one supplied value.
Computed-hole members remain ordinary opaque partial callables rather than falsely claiming a
reifiable structural descriptor.

The descriptor is immutable language-owned data and retains source provenance internally for later
template diagnostics. It is not exposed as Java AST or runtime implementation state. The constructor
itself follows ordinary callable equality rules, so direct equality is invalid; reflection retains
ordinary callable target identity and exposes only its public parameter/result contracts, never
fixed captures or descriptor internals. Applying the constructor always produces a complete
Collection and can never materialize a hole value.

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
<a id="implementation-requirements-1"></a>
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

24. Contextual completion of required named fields whose holes directly use `T~` or `T?~`, including
`defaultsMissing` reflection, for structural contracts such as `RuleDefinition`. Test bindings,
known parameters, results, nested literals, exported blocks, dynamic keys, aliases, explicit missing
and null values, nondefaultable omissions, extra fields, wrong values, ambiguous contexts, pure
membership, explicit conversion, evaluation order, and located failures using existing syntax.

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
<a id="design-principle-1"></a>
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

The prototype implements `ErrorTemplate` as the standard recursive structural contract with the
seven fields above. `phase` uses the lowercase language-owned phase names; `location` is `~` or an
exact Collection containing one-based `line`, `column`, `endLine`, and `endColumn`; each `related`
entry contains `message` and `location`. `cause` is `~` or another `ErrorTemplate`, and `details` is
always a Collection. Every field remains present even when its value is missing or empty.

Aborting diagnostics retain the same immutable descriptor and can be projected to this value shape,
but remain `LangException` control flow rather than catchable Caret values. Cause chains contain only
language diagnostic descriptors, have a bounded projection depth, and never retain or reveal Java
exceptions, stack traces, implementation objects, or host serialization identities. Structural
equality and reflection operate solely on the projected Caret Collections.
