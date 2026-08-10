# Implemented language sketch

This file describes the prototype as it currently behaves, not a final language specification.

## Values

```text
42          number
"text"      string
true false  Boolean
?           null
~           missing
#count      name value
```

Null and missing are separate runtime values.

`type value` returns the public runtime kind name used by reflection, including `"Null"` for `?`,
`"Missing"` for `~`, and `"Function"` for a non-callable function reference produced by `@`.

Number literals start with a digit and may contain at most one decimal point. Malformed number
literals are reported as language errors rather than leaking a Java numeric-conversion exception.
Numbers must remain finite. Literals outside the finite range and arithmetic producing a non-finite
result are errors. Division and remainder by zero are errors.

Strings recognize `\\`, `\"`, `\n`, `\r`, `\t`, and Unicode code-point escapes written as
`\u{1F642}`. Unknown, incomplete, surrogate, and out-of-range escapes are lexical errors.

## Comments

`//` introduces a line comment. `#name` is always a name value, including when it appears at the
beginning of a line; `#` is not a comment marker.

## Diagnostics

Lexical, parse, and runtime errors include the one-based line and column of the smallest relevant
source expression. Columns count raw source characters. A tab therefore advances the displayed
column by one, although a leading tab still contributes two spaces to indentation depth.
Built-in argument validation retains individual argument spans, so an invalid operand points to
that operand rather than the complete call.

Internally, errors retain their phase, a stable diagnostic code, message, primary source span, and
related source spans. The CLI renders the primary location in the compact form below and follows it
with located `Note:` lines when a diagnostic has related locations, such as the first declaration
for a duplicate definition.

```text
Error: Line 1, column 7: Unknown name: absent
```

## Test files

The CLI can run one Caret test file with `caret test file.caret`. Test mode adds two assertion
functions without changing the language grammar:

```caret
assert "descriptive name" condition
assertEqual "descriptive name" actual expected
```

`assert` requires a string name and a Boolean condition. `assertEqual` requires a string name and
uses the same structural equality rules as `==`; callable values therefore cannot be compared.
Both functions return `~` after recording their result.

Passing and failing assertions are written to standard output. Failures include the line and column
of the complete assertion call plus expected and actual values. Evaluation continues after an
assertion mismatch and ends with a summary. The process succeeds only when at least one assertion
ran and every assertion passed.

Assertion arguments remain eager. Lexical, parse, and runtime errors abort the file, are written to
standard error as normal located diagnostics, and do not produce a completed summary. Assertions
are test-runner builtins and are not present during ordinary file or REPL execution.

## Bindings and functions

```text
x = 10
add a b = a + b

makePerson name age =
  internal = age + 1
  ^name = name
  ^age = age
```

The constant/operator spellings `true`, `false`, `and`, `or`, `not`, `_`, and numbered holes such
as `_1` are reserved and cannot be used as binding or parameter names.

Indentation defines a multiline function body. If a body contains exported bindings (`^`), calling the function returns an immutable scope containing those exports. Otherwise it returns the final expression or assigned value.

A zero-argument function is evaluated when its name is read. Use reflection syntax to refer to the
function itself without invoking it:

```text
factory =
  ^value = 42

factory          // calls factory and produces its exported scope
@factory         // reflects the factory function itself without calling it
```

This rule is not limited to zero-argument functions. `@function` refers to the function binding
without invoking it regardless of the function's arity. The result is a non-callable function
reference whose reflective fields include `kind` and `remaining`. References to the same function
compare equal by target identity; references to different functions do not.

## Function application

```text
add 2 3
```

Application is left-associative and has high precedence.

```text
f x y
```

means:

```text
(f x) y
```

Parentheses remain available as a grouping escape:

```text
print (add 2 3)
```

Expressions inside parentheses and dynamic-lookup brackets may span physical lines. Indentation
inside the delimiters is continuation layout and does not start a function body:

```caret
result = (
  add
    1
    2
)

value = scope[
  #field
]~
```

More-indented ungrouped multiline arguments are implemented. Trailing callable blocks remain
planned until lambda syntax is implemented; their layout rule is specified in the implementation
roadmap below.

`print` also has a statement form. The complete remainder of its logical line is parsed as one
expression, so common output does not require grouping:

```text
print add 1 2
print condition & "yes" ! "no"
```

This does not change ordinary application associativity: outside the `print` statement, `f x y`
still means `(f x) y`.

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

## Boolean operations

```text
a and b
not a
a or b
```

`and` and `or` short-circuit.

## Arbitrary partial application

```text
between low value high = value >= low and value <= high
inside = between 0 _ 10
inside 7
```

Every `_` introduces a future argument, ordered left to right.

Non-hole parts of a partial expression are evaluated and captured when the partial function is
created.

Numbered holes reorder and reuse future arguments:

```caret
reordered = f _2 fixed _1
duplicated = pair _1 _1
```

The highest hole number determines the resulting arity. Repeated numbers reuse the same argument.
Numbered and unnumbered holes cannot be mixed in one partial expression.

## Scopes

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

Scopes are immutable in this prototype.

## Dynamic lookup

```text
field = #count
a[field]~
a[#count]~
a["count"]~
```

Dynamic names can be name values or strings. The `~` suffix makes a missing binding a normal result instead of an error.

## Reflection

```text
meta = @a
meta.kind
meta.size
meta.names

functionMeta = @function
functionMeta.kind
functionMeta.remaining
```

Current metadata:

- all values: `kind`
- scopes: `size`, `names`
- function references: `kind = "Function"`, `remaining`

`@function` is a reference and reflection mechanism, not an alternate call syntax. Applying the
result is a `NOT_CALLABLE` error. Reflecting an existing function reference returns the same
reference.

The metadata representation is intentionally minimal. A later version should expose iterable field descriptors, parameter descriptors, mutability, ownership, nullability, optionality, and export status.

## Operators and precedence

From lower to higher precedence:

1. conditional `& ... ! ...`
2. `or`
3. `and`
4. equality `== !=`
5. comparison `< <= > >=`
6. addition `+ -`
7. multiplication `* / %`
8. unary `- not @`
9. function application
10. field and dynamic lookup

## Implementation roadmap

The following facilities and semantic decisions are planned prerequisites for implementing a
Caret interpreter in Caret. They describe the current design direction, not behavior implemented
by this prototype.

### Source text operations

The prototype provides the text primitives needed by a Caret lexer:

```text
textSize text
textAt text index
textSlice text start end
textNumber text
numberText number
```

Text indexes count Unicode code points rather than UTF-16 code units. Slices use half-open
`[start, end)` bounds. An invalid index, invalid bounds, or failed numeric conversion returns `~`
instead of throwing for an expected condition.

### Immutable collections

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

Dictionary keys accept strings and name values as the same logical key space, and key iteration
preserves insertion order. `dictHas` distinguishes an absent key from a present key whose value is
`~`.
Collection literal syntax is not required for the initial self-interpreter.

### Unified binary functions and operators

A binary operator and a function taking two parameters are planned to be the same kind of callable
value. Either may be called with prefix notation or placed between its arguments with infix
notation:

```text
add left right = left + right

add 2 3       // prefix call of a named function
2 add 3       // infix call of the same named function

+ 2 3         // prefix call of a symbolic operator
2 + 3         // infix call of the same symbolic operator
```

The parser will distinguish the two forms from the beginning of the expression:

- If the first expression is a value, or a function taking no parameters, and the next expression
  denotes a function taking two parameters, the form is an infix call. The first expression is the
  first argument and the following expression is the second argument.
- If the first expression denotes a function that takes one or more parameters, the form is a
  prefix call of that function. Later binary functions in the argument sequence do not change that
  initial choice.

Named binary functions used in infix position have one fixed precedence level. They are
left-associative, bind less tightly than additive operators, and bind more tightly than comparison
operators. Thus `2 combine 3 + 4` means `2 combine (3 + 4)`, while `2 combine 3 < 10` means
`(2 combine 3) < 10`. Parentheses are required when another grouping is intended.

Built-in symbolic operators retain the precedence table documented above. Custom
symbolic-operator declaration syntax remains an open design decision; the initial unified-callable
implementation makes the existing symbolic operators callable in prefix form but must not invent
new declaration syntax.

This unified prefix/infix behavior is planned and is not implemented by the current parser.

### Ungrouped multiline application

Outside an explicit delimiter, a physical line indented more deeply than a non-definition
expression continues that expression. Each continuation expression is the next whitespace-applied
argument at ordinary application precedence, and dedentation ends the call. Lower-precedence
operators on the initial line remain outside that application: `true & add` followed by indented
`1` and `2` means `true & (add 1 2)`.

```caret
result = add
  1
  multiply
    2
    3
```

is equivalent to `result = add 1 (multiply 2 3)`. Blank and comment-only lines do not end a
continuation. An empty function-definition right side still opens a function body and takes
precedence over continuation parsing.

Sibling continuation arguments use the same indentation. A deeper line continues the immediately
preceding argument; dedenting to an indentation other than an established enclosing level is a
located layout error. A continuation line is an expression and cannot contain a definition.

Once lambdas are implemented, an indented trailing lambda will be the final call argument. A
definition or lambda body is delimited by its own indentation in the ordinary way. This rule is
planned and is not implemented by the current parser.

### Core semantic decisions

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

Equality is recursive and structural for scalar values, exported scopes, and collections. Scalar
numeric equality therefore has the same result when numbers are nested in data; for example, `-0`
and `0` compare equal both directly and inside a sequence. Encountering a callable anywhere in
either compared structure is a `CALLABLE_EQUALITY` error. Function references compare by the
identity of their referenced callable.

`@function` produces a non-callable reflective function reference. It suppresses normal implicit
invocation of a nullary binding and exposes `kind` and `remaining`; bare nullary function names
continue to invoke automatically.

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

The complete operand/coercion rules for operators once static types exist remain a prerequisite for
extending unified binary functions beyond the existing scalar operators.

The self-interpreter may represent successful and failed operations as exported result scopes. Its
CLI adapter can then render a failed result as the normal located `Error:` diagnostic.

### Not required for self-interpretation

The first Caret-written interpreter does not depend on static types, loops, mutation, modules,
lambdas, pattern matching, ownership, reflected invocation, or a compiler backend. Recursion,
immutable collections, tagged exported scopes, and the planned text operations are sufficient.

## Contracts, Type Derivation, and Collections

### Overview

Caret does not distinguish fundamentally between types, interfaces, refinement types, and value contracts.

A type is a **contract**.

A contract behaves as a predicate over values:

```text
Contract : Value -> Boolean
```

A value satisfies a type when it satisfies the corresponding contract.

Contracts may also derive from other contracts. This creates a hierarchy of logical implication:

```text
Int -> Number -> Comparable -> Eq
```

meaning:

```text
Int x => Number x
Number x => Comparable x
Comparable x => Eq x
```

Behavior is not stored inside contracts.

Operations are ordinary Caret functions whose parameter contracts determine which values they accept.

Collections use one common literal syntax:

```caret
[1 2 3]
```

The literal itself does not mean `List`, `Array`, `Set`, or another particular representation.

Its specific collection contract and representation are determined by inference and surrounding constraints.

---

# Contracts

## Contract definition

`contract` constructs a contract.

A base contract with no additional value restriction may be defined as:

```caret
Eq = contract ~
```

Here `~` means that the contract introduces no additional value predicate of its own.

Membership in such a base contract is established through derivation from that contract.

For example:

```caret
Eq = contract ~

Number = contract Eq

Int = contract Number
```

establishes:

```text
Int -> Number -> Eq
```

An `Int` therefore satisfies all three contracts.

---

## Contracts as predicates

Every contract may be used as a Boolean membership predicate:

```caret
Int value
Number value
Eq value
```

Conceptually:

```text
Int value -> Boolean
```

A contract may therefore be used anywhere an ordinary pure unary Boolean function is appropriate.

The compiler may determine membership statically when sufficient type information is available.

It does not need to execute a runtime predicate when derivation already proves membership.

---

## Contract composition

`contract` may combine multiple contracts:

```caret
Number =
  contract Eq Comparable Arithmetic
```

This means that every `Number` also satisfies:

```text
Eq
Comparable
Arithmetic
```

Conceptually:

```text
Number x
    =>
Eq x
and Comparable x
and Arithmetic x
```

Multiple derivation is therefore ordinary contract composition.

No separate multiple-inheritance mechanism is required.

For example:

```caret
Integer =
  contract Number Integral

Float =
  contract Number Fractional
```

establishes:

```text
Integer -> Number
Integer -> Integral

Float -> Number
Float -> Fractional
```

and transitively all contracts derived by `Number`.

---

## Type derivation

Type derivation is contract inclusion.

For example:

```caret
Eq = contract ~

Comparable =
  contract Eq

Arithmetic =
  contract Eq

Number =
  contract Comparable Arithmetic

Int =
  contract Number Integral

Float =
  contract Number Fractional
```

This creates a graph rather than requiring a strict inheritance tree.

A contract may derive from any number of other contracts.

There is no object-layout diamond problem because derivation does not copy or embed base objects.

If several derivation paths lead to `Eq`, the resulting value simply satisfies `Eq`.

---

## Refinement predicates

Ordinary pure unary Boolean functions may participate in contracts.

Example:

```caret
positive x =
  x > 0

PositiveInt =
  contract Int positive
```

Conceptually:

```text
PositiveInt x
    =
Int x
and positive x
```

The same constraint may also be written directly on a binding:

```caret
(Int positive) count
```

Named derived contracts are useful when a combination is reused:

```caret
SmallPositiveInt =
  contract Int positive small
```

Thus Caret uses the same mechanism for:

* base types;
* derived types;
* refinement types;
* interfaces/capabilities;
* user-defined validation constraints.

---

## Contracts do not contain operations

A contract does not contain a method table or list of allowed operations.

For example:

```caret
Eq = contract ~
```

does not itself declare `eq`.

Equality is an ordinary function defined separately:

```caret
(Boolean) eq (Eq) a (Eq) b =
  false
```

Specific contracts may provide more specialized implementations:

```caret
(Boolean) eq (Int) a (Int) b =
  primitiveIntEq a b

(Boolean) eq (Float) a (Float) b =
  primitiveFloatEq a b

(Boolean) eq (String) a (String) b =
  primitiveStringEq a b
```

Likewise:

```caret
(Boolean) lt (Comparable) a (Comparable) b =
  ...

(Int) add (Int) a (Int) b =
  ...

(Float) add (Float) a (Float) b =
  ...
```

Operations belong to functions, not types.

---

## Functional polymorphism

Several definitions of the same function may specialize different parameter contracts.

For example:

```caret
(Boolean) eq (Eq) a (Eq) b =
  false

(Boolean) eq (Number) a (Number) b =
  numericEq a b

(Boolean) eq (Int) a (Int) b =
  primitiveIntEq a b
```

When calling:

```caret
eq 10 20
```

the most specific applicable implementation is selected.

Here that is:

```caret
eq (Int) a (Int) b
```

This provides functional polymorphism through contract-based dispatch.

There is no privileged receiver object.

Dispatch may therefore depend on several arguments:

```caret
add (Int) a (Float) b =
  ...

add (Float) a (Int) b =
  ...

add (Float) a (Float) b =
  ...
```

If exactly one most-specific implementation exists, it is used.

If several incomparable implementations are equally applicable, the call is ambiguous and must produce a compile-time diagnostic where determinable.

Function dispatch must not arbitrarily choose between ambiguous implementations.

---

# Collections

## General collection contract

`Collection` is the fundamental contract for values containing zero or more elements.

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

## Parameterized collection contracts

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

---

# Collection literals

## Universal collection syntax

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

## Empty collection

An empty collection is:

```caret
[]
```

Its element contract cannot be inferred from its contents.

It may therefore obtain its type from context:

```caret
(List Int) values = []

(Set String) names = []

(Packed Byte) buffer = []
```

Without sufficient context it may remain a generic empty collection until additional constraints determine its element type.

---

## Heterogeneous collections

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

## Homogeneous collections

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

## Collection expressions

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

---

# Named fields and dictionaries

## Named elements

`^` may construct named elements inside a collection:

```caret
person =
  [
    ^name "Alice"
    ^age 42
    ^active true
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

---

## Dynamic keys

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

## Nested collections

Collections may contain collections:

```caret
people =
  [
    [
      ^name "Alice"
      ^age 32
    ]

    [
      ^name "Bob"
      ^age 41
    ]
  ]
```

or arbitrary heterogeneous nested structures:

```caret
[
  10
  "hello"

  [
    ^x 20
    ^y 30
  ]

  true
]
```

No separate object, record, JSON, or array literal syntax is required.

---

# Collection metadata

## Logical versus physical metadata

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

## Per-element metadata

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

## Shared collection metadata

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

## Contract-homogeneous but representation-heterogeneous collections

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

# Packed collections

## Shared representation

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

## Packed versus homogeneous

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

## Packed structural values

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

## Metadata placement rule

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

# Relationship to formats

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

# Implementation requirements

The initial implementation should support at minimum:

1. `contract` as the fundamental type-definition mechanism.
2. Base/tag contracts:

```caret
Eq = contract ~
```

3. Contract derivation:

```caret
Number = contract Eq Comparable
```

4. Multiple derivation.
5. Contracts usable as membership predicates.
6. Ordinary pure predicates used as refinements.
7. Derived refinement contracts:

```caret
PositiveInt = contract Int positive
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

The initial implementation may postpone:

* sophisticated automatic memory-layout optimization;
* GPU-specific layout attributes;
* structure-of-arrays transformations;
* compressed runtime type tags;
* zero-copy format views;
* advanced generic constraint inference.

These later features must preserve the fundamental distinction between contract membership, element-specific metadata, and shared collection representation.

---

# Design principle

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

### Purity and effects

Every function has an inferred effect set.

The compiler determines this set from:

* operations performed directly by the function;
* functions called by it;
* functions passed into and invoked by it;
* composed and partially applied functions used by it.

A function with an empty effect set is pure.

Effects propagate transitively through function calls.

For example, if:

```caret
(fs) read path =
  ...

parse text =
  ...
```

then:

```caret
load path =
  parse (read path)
```

has inferred effect set:

```text
{ fs }
```

### Effects must be declared

Effects may not appear implicitly in a function declaration.

If a function has no explicit effect contract, its allowed effect set is empty.

Therefore:

```caret
calculate x =
  ...
```

implicitly guarantees that `calculate` is pure.

If an effect is later introduced into its implementation, compilation fails until the declaration is explicitly changed.

For example:

```caret
load path =
  read path
```

is invalid when `read` has the `fs` effect.

The compiler should report the inferred undeclared effect and may suggest:

```caret
(fs) load path =
  read path
```

This rule prevents effects from propagating silently through the call graph.

### Effect contracts

Effect contracts specify the maximum set of effects a function is permitted to have.

```caret
(fs) read path =
  ...

(net) download url =
  ...

(fs net) synchronize source target =
  ...
```

If the compiler infers an effect not included in the declared set, compilation fails.

For example:

```caret
(fs) load path =
  download path
```

is invalid if `download` requires `net`.

Effect contracts are upper bounds, not assertions that an effect necessarily occurs during every invocation.

A function declared:

```caret
(fs) cachedLoad path =
  ...
```

may execute without filesystem access on some or all paths.

### Explicit purity

`pure` is equivalent to an empty allowed effect set.

```caret
(pure) calculate x =
  ...
```

is therefore stricter documentation of the same purity guarantee that an effect-less declaration receives by default.

Explicit `pure` is useful when purity is an intentional API-level guarantee.

It is particularly useful on higher-order function parameters:

```caret
map (pure) transform values =
  ...
```

This requires `transform` to be a pure function.

A unary Boolean function that is itself effectful cannot be used as a contract.

### Effect inference and tooling

Effect inference is mandatory even when an explicit effect contract is present.

The compiler must infer the actual effect set and verify:

```text
actual effects ⊆ declared allowed effects
```

IDE tooling should expose inferred effects directly at function declarations.

For example, the source:

```caret
load path =
  ...
```

may temporarily display an inferred annotation such as:

```text
⟨fs — undeclared⟩ load path =
```

while editing.

For a pure function, tooling may display:

```text
⟨pure⟩ calculate x =
```

Such annotations are IDE presentation only and are not part of Caret source syntax.

The compiler should also provide a way to inspect fully inferred contracts and effects in plain-text environments.

---

## SIMD

SIMD is a language-level execution mechanism rather than a separate intrinsic API.

Ordinary pure numeric functions should be usable on both scalar values and SIMD values whenever their operations can be lifted lane-wise.

### SIMD types

A native-width SIMD value is written:

```caret
(simd Float) values
```

The number of lanes is chosen appropriately for the compilation target.

An explicit lane count may be written:

```caret
(simd 8 Float) values
```

This represents exactly eight `Float` lanes.

`simd` is a type constructor and participates in the normal contract/type syntax.

### Lane-wise operations

Ordinary arithmetic and comparison operators operate lane-wise on SIMD values.

For example:

```caret
a + b
a * b
a > b
```

when applied to SIMD values produce corresponding SIMD results.

A scalar operand is automatically broadcast where the operation is otherwise well-defined:

```caret
values * 0.5
values + offset
values > 10
```

No explicit broadcast operation is required for ordinary scalar-to-SIMD use.

### SIMD Boolean values and conditional selection

A comparison involving SIMD values produces a SIMD Boolean mask.

```caret
positive = values > 0
```

If `values` is:

```caret
simd Float
```

then `positive` is conceptually:

```caret
simd Bool
```

Caret's ordinary conditional expression operates lane-wise when its condition is a SIMD Boolean value:

```caret
positive & values ! 0
```

This selects between `values` and `0` independently for each lane.

For example:

```text
mask   = [true, false, true, false]
values = [8, -2, 4, -7]

mask & values ! 0
```

produces:

```text
[8, 0, 4, 0]
```

No separate blend/select intrinsic is required.

### Scalar functions lifted to SIMD

A pure scalar function may be applied to SIMD values when all operations in the relevant execution path support SIMD semantics.

For example:

```caret
(pure) square x =
  x * x
```

may be used with either:

```caret
square 3.0
```

or:

```caret
square vector
```

when `vector` is a compatible SIMD value.

The compiler may generate scalar or SIMD code according to the argument type.

### Explicit SIMD application

Caret provides:

```caret
collection :: function
```

to request SIMD application of `function` across the elements of `collection`.

Example:

```caret
(pure) adjust x exposure =
  bright = x * exposure
  bright > 1 & 1 ! bright < 0 & 0 ! bright

result = pixels :: adjust _ exposure
```

`adjust _ exposure` creates the unary function applied to SIMD groups of `pixels`.

Conceptually, `::` performs:

1. SIMD-sized grouped processing across as much of the collection as possible;
2. scalar or masked handling of any remaining tail elements.

The programmer does not manually write a remainder loop.

### `::` is a requirement, not merely a hint

Ordinary collection operations may be auto-vectorized by the compiler whenever safe.

For example:

```caret
values map transform
```

may use SIMD without any special source syntax.

By contrast:

```caret
values :: transform
```

explicitly requests SIMD execution.

If the compiler cannot generate valid SIMD code for this operation, it must issue a diagnostic rather than silently falling back to fully scalar execution.

The diagnostic should explain the reason when possible, such as:

* the function has effects;
* an operation has no SIMD implementation;
* an unsupported data layout is required;
* aliasing prevents safe vectorization;
* control flow cannot be represented safely with SIMD semantics.

### Purity requirement for SIMD mapping

Functions used with `::` must normally be pure.

```caret
values :: transform
```

requires that `transform` have an empty inferred effect set.

An effectful function such as:

```caret
(io) transform x =
  print x
  x * 2
```

cannot normally be used through:

```caret
values :: transform
```

because lane-wise execution would make observable effect ordering ambiguous.

Purity is checked using the ordinary Caret effect system; SIMD does not introduce a separate purity mechanism.

### Function composition and partial application

SIMD application composes with ordinary Caret function features.

Partial application:

```caret
pixels :: adjust _ exposure
```

Function composition:

```caret
pipeline = normalize >> clamp >> encode
result = values :: pipeline
```

The resulting composed function is SIMD-compatible only if the entire composition is pure and every relevant operation supports SIMD execution.

### Reductions

Operations that collapse SIMD lanes are ordinary functions rather than special syntax.

Examples include:

```caret
sum values
min values
max values
any mask
all mask
```

The compiler may lower these to efficient SIMD horizontal reductions.

### Memory and alignment

Normal SIMD code should not require explicit aligned-load, unaligned-load, store, or hardware-register operations.

The compiler/runtime is responsible for handling:

* native SIMD width;
* memory alignment;
* vector loading and storing;
* remainder elements;
* target instruction sets such as AVX, AVX2, AVX-512, NEON, or equivalent facilities.

Low-level architecture-specific SIMD facilities may exist separately, but they are not part of the ordinary `simd` / `::` programming model.

### Portability

Code using:

```caret
simd Float
```

is portable across targets with different native SIMD widths.

Code using an explicit width:

```caret
simd 8 Float
```

requests that logical lane width specifically. The compiler may use one or more hardware vector operations to implement it where necessary, or reject it when the target cannot support the required semantics.

## Formats

### Overview

A `Format` describes a bidirectional relation between an in-memory Caret value and an external representation.

Conceptually:

```text
Value ↔ Representation
```

Examples of representations include:

* byte streams;
* files;
* network packets;
* textual formats;
* JSON-like data;
* compressed data;
* encrypted data;
* protocol messages.

A format definition should normally describe both directions at once:

```text
decode : Representation -> Value
encode : Value -> Representation
```

The programmer should not normally write independent encoder and decoder implementations for the same structure.

The compiler/runtime derives both directions from the same `Format` value wherever possible.

---

## Formats as relations

A format is relational rather than inherently directional.

For example:

```text
u16be
```

describes the relation between an integer and its two-byte big-endian representation:

```text
Int ↔ Bytes
```

When applied in the decoding direction:

```text
00 2A -> 42
```

When applied in the encoding direction:

```text
42 -> 00 2A
```

A compound format describes a larger relation assembled from smaller relations.

Caret does not require general Prolog-style search or backtracking for format relations.

A format is expected to support deterministic evaluation when one side of the relation is sufficiently known.

The normal supported directions are:

```text
known Representation -> Value
known Value          -> Representation
```

The format system must not implicitly search arbitrary solution spaces when neither side is sufficiently determined.

---

## `Format` as a first-class value

`Format` is a first-class Caret value.

Formats may be:

* stored in variables;
* passed to functions;
* returned from functions;
* composed;
* partially applied;
* placed in collections;
* inspected through reflection.

Format construction should use ordinary Caret functions rather than special grammar for each format feature.

For example:

```caret
Packet =
  format
  >> constant "PACK"
  >> field u16be "length"
  >> field u8 "type"
  >> field (bytes length) "payload"
```

`format` is the empty format.

Functions such as:

```text
constant
field
array
when
choice
require
codec
```

construct or transform formats.

They should normally be library-level functions or standard format primitives rather than separate parser constructs.

---

## Formats as specialized collections

`Format` satisfies the general Caret `Collection` model.

Conceptually:

```text
Format : Collection FormatElement
```

A format may contain heterogeneous elements such as:

```text
Constant
Field
Sequence
Repeat
Choice
Conditional
Constraint
Codec
```

These elements may have different concrete types but satisfy the common format-element contract.

A format should normally be immutable.

Functions that extend a format return an updated format rather than mutating the original value.

Conceptually:

```text
Format -> Format
```

For example:

```caret
addHeader f =
  f
  >> constant "HEAD"
  >> field u16be "version"
```

Because formats are ordinary immutable values, they can be reused safely:

```caret
Base =
  format
  >> constant "DOC"

Version1 =
  Base
  >> field u8 "flags"

Version2 =
  Base
  >> field u16be "flags"
```

---

## Format composition

Caret's normal `>>` composition operator is also used for format construction and relational composition.

When a function is partially applied so that it accepts a `Format` and returns a `Format`, it can participate directly in a format pipeline.

For example:

```caret
Packet =
  format
  >> constant "PACK"
  >> field u16be "length"
  >> field u8 "type"
```

Conceptually:

```text
format
  -> add constant
  -> add length field
  -> add type field
```

When complete bidirectional relations are composed:

```text
A ↔ B
B ↔ C
```

their composition describes:

```text
A ↔ C
```

The encoding direction follows the relation in one direction and the decoding direction follows it in the opposite direction.

This allows format composition to define both encoder and decoder behavior from one expression.

---

## Primitive formats

Primitive representation formats are themselves `Format` values.

Binary primitive formats consume and produce a first-class immutable `Bytes` value. `Bytes` is
distinct from Unicode text and from a general sequence of numbers: byte indexing counts octets,
while text indexing continues to count Unicode code points. Standard pure conversions provide
explicit interoperability with hexadecimal text, encoded text, and validated integer sequences;
raw bytes are never smuggled through `String`.

Examples may include:

```caret
u8
u16be
u16le
u32be
u32le
i16le
f32le
bytes
utf8
ascii
```

For example:

```caret
field u32be "size"
```

uses `u32be` as a format describing:

```text
Int ↔ four big-endian bytes
```

A compound format may be used anywhere a primitive format can be used.

For example:

```caret
Point =
  format
  >> field f32le "x"
  >> field f32le "y"

Object =
  format
  >> field Point "position"
```

`field` must not distinguish unnecessarily between primitive and compound formats.

---

## Fields

A field relates a named member of an in-memory data structure to a representation described by another format.

Conceptually:

```text
field : Format -> String -> Format -> Format
```

Exact internal argument ordering may follow normal Caret partial-application rules, but this syntax should be supported:

```caret
field u16be "length"
```

When decoding, the format:

1. decodes a value using `u16be`;
2. adds a named field `"length"` to the resulting Caret data value.

When encoding, it:

1. obtains the field `length` from the input data;
2. encodes it using `u16be`.

For example:

```caret
Point =
  format
  >> field f32le "x"
  >> field f32le "y"
```

decodes into a value structurally equivalent to:

```caret
data
  ^x 10.0
  ^y 20.0
```

and encodes such a value back into the corresponding representation.

Field names are ordinary strings.

Do not require `#name` syntax.

---

## References to earlier fields

Later format elements may depend on values decoded or encoded earlier in the same structure.

For example:

```caret
Packet =
  format
  >> field u16be "length"
  >> field (bytes length) "payload"
```

Within the format definition, `length` refers to the logical value of the previously defined field.

In the decoding direction:

1. decode `length`;
2. use it to determine how many bytes constitute `payload`.

In the encoding direction, the same relationship must be respected.

If a field such as `length` can be derived from another value during encoding, the format system should permit the implementation to derive or validate it rather than require duplicated application code.

The precise dependency-resolution rules may be expanded later, but dependencies must be represented as relationships rather than duplicated encode/decode implementations wherever possible.

---

## Constant representation elements

A constant format element represents data that appears in the external representation but normally does not need to appear as a logical in-memory field.

Example:

```caret
PngLike =
  format
  >> constant signature
  >> field u32be "length"
```

In the decoding direction:

```text
constant x
```

consumes representation data and verifies that it equals `x`.

If it does not match, decoding fails.

In the encoding direction, the same element emits `x` automatically.

This is a naturally bidirectional relation:

```text
representation element == x
```

A constant should not create an in-memory field unless explicitly requested.

This is useful for:

* file signatures;
* magic values;
* protocol markers;
* separators;
* fixed headers;
* reserved constants.

---

## Repeated formats

Repeated structures are created by format combinators rather than special looping syntax.

For example:

```caret
array count Item
```

constructs a format representing `count` repetitions of `Item`.

Example:

```caret
Point =
  format
  >> field f32le "x"
  >> field f32le "y"

Polygon =
  format
  >> field u16be "count"
  >> field (array count Point) "points"
```

Decoding produces a collection of decoded `Point` values.

Encoding consumes a collection of `Point` values.

The same format definition controls both directions.

The count may be:

* constant;
* derived from a previous field;
* derived from the value being encoded;
* determined by another format relation.

The implementation should avoid requiring the user to write separate loops for encoding and decoding.

---

## Conditional formats

A format may conditionally include another format.

A combinator conceptually similar to:

```caret
when predicate format
```

constructs a conditional format.

Example:

```caret
Extension =
  format
  >> field u32be "extra"

Packet =
  format
  >> field u8 "flags"
  >> field
       (when (flags has Extended) Extension)
       "extension"
```

The condition should be usable in both directions whenever enough information is available.

In the decoding direction, previously decoded data may determine whether the subformat is present.

In the encoding direction, the logical data may determine whether the corresponding representation is emitted.

The compiler/runtime should derive both directions from the same condition wherever possible.

---

## Choices and pattern matching

Formats may describe alternatives based on data patterns or discriminators.

Conceptually:

```caret
choice selector alternatives
```

For example:

```caret
MessageBody =
  choice kind
    1 TextMessage
    2 ImageMessage
    3 FileMessage
```

The surface syntax for declaring general alternatives remains unresolved and is tracked as
`FORMAT-CHOICE-001` in `CONFORMANCE.md`. It must be specified before general choices are
implemented.

The semantic requirement is more important:

* decoding may use representation data to determine which alternative applies;
* encoding may use the logical value to determine which representation and discriminator are required.

Where the relationship is deterministic in both directions, the user should not have to write separate selection logic for encoding and decoding.

Pattern matching in formats should therefore be treated relationally where practical.

---

## Format constraints

Ordinary Caret contracts may constrain values represented by a format.

Conceptually:

```caret
require contract format
```

returns a constrained format.

Example:

```caret
PositiveInt =
  require positive u32be
```

When decoding:

1. decode an integer;
2. require that `positive` holds.

When encoding:

1. require that the supplied value satisfies `positive`;
2. encode it.

The same pure contract is used in both directions.

This connects format validation directly to Caret's normal contract system.

---

## Automatic bidirectionality

Format components should define both directions automatically whenever their relation contains enough information to do so.

Examples include:

```caret
constant "PNG"
field u16be "length"
array count Entry
require positive u32be
```

The programmer should not write:

```text
encodeConstant
decodeConstant

encodeField
decodeField

encodeArray
decodeArray
```

as separate application-level definitions.

The common format description should generate both behaviors.

---

## Explicit codecs

Not every transformation can be inverted automatically.

For example:

```text
compressed bytes ↔ uncompressed bytes
encrypted bytes  ↔ plaintext
base64 text       ↔ bytes
```

The compiler cannot generally derive a compressor from a decompressor or an encryptor from a decryptor.

Caret therefore supports a format component that explicitly supplies the two directions.

Conceptually:

```caret
codec decode encode format
```

The first function implements representation-to-value transformation.

The second implements value-to-representation transformation.

For example:

```caret
gzip format =
  codec gunzip gzip format
```

or:

```caret
encrypted key format =
  codec (decrypt key) (encrypt key) format
```

These functions construct new formats.

They are not special external encoding/decoding procedures attached after format construction.

They are components of the format relation itself.

---

## Codec composition

Explicit codecs compose with ordinary declarative formats.

For example:

```caret
Payload =
  format
  >> field u32be "id"
  >> field utf8 "text"

CompressedPayload =
  gzip Payload
```

Conceptually, the relationship is:

```text
Caret Payload
      ↕ Payload format
uncompressed representation
      ↕ gzip codec
compressed representation
```

Encoding follows:

```text
Caret value
 -> Payload representation
 -> compression
 -> compressed representation
```

Decoding follows:

```text
compressed representation
 -> decompression
 -> Payload representation
 -> Caret value
```

The complete encoder and decoder are derived from the composed relation.

---

## Representation transformations versus logical transformations

A codec may alter either the physical representation or the logical value.

Representation example:

```text
plain bytes ↔ compressed bytes
```

Logical-value example:

```text
stored integer ↔ floating-point temperature
```

For example:

```caret
Temperature =
  codec
    (x -> x / 100.0)
    (x -> round (x * 100))
    i16le
```

The external representation is a signed integer.

The logical Caret value is a floating-point temperature.

Both kinds of transformations use the same relational format machinery.

Libraries may provide more descriptive wrapper functions for common purposes, but they need not require separate compiler concepts.

---

## Purity of format definitions

A `Format` describes data relationships and should normally be pure.

Format construction functions should therefore normally be pure.

Encoder and decoder functions supplied to `codec` must normally be pure.

For example:

```caret
gzip format =
  codec gunzip gzip format
```

requires `gunzip` and `gzip` to satisfy the purity requirement.

Reading a file, receiving network data, or writing to a socket is not part of the format relation itself.

For example:

```caret
(fs) raw = read file
value = decode Packet raw
```

and:

```caret
raw = encode Packet value
(fs) write file raw
```

`decode` and `encode` remain pure even though acquiring or storing the representation is effectful.

This separation must be preserved.

---

## Decode and encode operations

The standard library should expose explicit directional operations:

```caret
decode Format representation
encode Format value
```

These are ordinary functions.

For a bidirectional format:

```caret
decoded = decode Packet bytes
encoded = encode Packet packet
```

Both operations use the same `Packet` value.

Do not require separately declared `PacketDecoder` and `PacketEncoder` objects.

A future relational application syntax may permit direction to be inferred from which side is known, but explicit `decode` and `encode` functions must remain available and unambiguous.

---

## Failure

Decoding may fail because:

* a signature or constant does not match;
* input ends prematurely;
* a field representation is invalid;
* a contract fails;
* no conditional/pattern alternative matches;
* a codec rejects the representation.

Encoding may also fail because:

* a required field is missing;
* a field has an invalid value;
* a contract fails;
* the value cannot be represented by the selected primitive format;
* no encoding alternative matches;
* a codec rejects the logical value.

These failures should be represented explicitly rather than relying on exceptions for expected format mismatch.

The concrete exported result/error shape remains unresolved and is tracked as
`FORMAT-FAILURE-001` in `CONFORMANCE.md`. It must be specified before public `decode` and `encode`
are implemented.

Errors should be capable of carrying useful information such as:

* format component;
* field name;
* representation position;
* expected condition;
* actual value;
* nested error cause.

---

## Canonical representations and round trips

A bidirectional format does not necessarily imply that every raw representation round-trips byte-for-byte.

For example:

```text
"00123" -> 123 -> "123"
```

may be valid if the encoder emits a canonical representation.

The preferred semantic guarantee is normally:

```text
decode (encode value) == value
```

for every valid logical value.

The opposite:

```text
encode (decode representation) == representation
```

is required only for formats that explicitly promise representation-preserving round trips.

Formats may therefore normalize representations.

---

## Relationship to Caret `data`

Formats decode into ordinary Caret values.

Structured formats should normally produce `data` collections containing ordinary fields.

For example:

```caret
Packet =
  format
  >> field u16be "length"
  >> field u8 "type"
  >> field (bytes length) "payload"
```

may decode to:

```caret
data
  ^length 128
  ^type 2
  ^payload payloadBytes
```

The format subsystem must not introduce a separate object model for decoded data.

The same value may therefore:

* be created directly using `data`;
* be decoded from a binary format;
* be encoded into another format;
* be passed through ordinary Caret functions;
* satisfy contracts;
* participate in collection operations;
* be inspected through reflection.

---

## Formats are independent of transport

A format describes representation, not where that representation comes from.

The same format may be used with:

```text
file
network connection
memory buffer
HTTP body
database blob
IPC message
```

Transport effects belong to transport functions.

For example:

```caret
(net) raw = receive connection
message = decode MessageFormat raw
```

The format itself remains pure.

This allows the same `Format` to be reused across files, REST clients, servers, protocols, tests, and in-memory transformations.

---

## Extensibility

Most format functionality should be implementable as ordinary Caret functions.

A library should be able to introduce new combinators such as:

```caret
checksum
padding
aligned
gzip
encrypted
terminated
versioned
optional
bounded
```

without adding new grammar to the language.

For example:

```caret
gzip format =
  codec gunzip gzip format
```

A user-defined format constructor should have the same compositional status as a standard-library format constructor.

Do not hard-code individual file formats, protocol fields, compression algorithms, or serialization systems into the Caret parser.

---

## Reflection

Formats are first-class values and should be reflectable.

Reflection may expose information such as:

```text
format elements
field names
nested formats
primitive representations
contracts
constants
choices
codecs
decode capability
encode capability
```

Reflection must not violate private bindings or other normal Caret visibility rules.

Format reflection should make it possible to build tooling such as:

* format inspectors;
* binary viewers;
* protocol debuggers;
* generated documentation;
* editors;
* test-data generators;
* schema converters.

---

## Implementation requirements

The initial implementation should support at minimum:

1. A first-class immutable `Format` value.
2. An empty `format`.
3. Format composition using ordinary functions and `>>`.
4. Primitive formats for common integer and byte representations.
5. Named fields using ordinary string names:

```caret
field u16be "length"
```

6. Constant/signature elements.
7. Nested compound formats.
8. Repeated formats with a fixed or previously decoded count.
9. Contract validation through a format combinator.
10. Explicit:

```caret
decode Format representation
encode Format value
```

11. Decoding structured formats into ordinary Caret `data` values.
12. Encoding ordinary compatible `data` values.
13. Pure explicit bidirectional codecs:

```caret
codec decode encode format
```

14. Composition of codecs with structural formats.
15. Explicit format mismatch/failure values rather than expected-case exceptions.

The initial implementation may postpone:

* general relational solving;
* automatic inversion of arbitrary Caret functions;
* nondeterministic relations;
* backtracking;
* sophisticated pattern-derived discriminators;
* streaming incremental decoding;
* zero-copy decoding;
* asynchronous transport integration.

These later capabilities should not require changing the fundamental model that a `Format` is a first-class bidirectional relation assembled compositionally from smaller relations.

---

## Design principle

The central principle is:

> A Caret format describes the relationship between a logical value and its representation, not separate procedures for reading and writing it.

Where the relationship is structurally reversible, Caret derives both directions from one description.

Where reversal requires algorithms that cannot be inferred, the format explicitly contains both directional functions:

```caret
codec decode encode
```

Complex formats are built from smaller bidirectional relations using ordinary Caret functions, collections, contracts, partial application, and composition.

## Lambda Functions

### Overview

Lambda expressions create anonymous first-class functions.

The basic syntax is:

```caret
x -> expression
```

Example:

```caret
square = x -> x * x
```

A lambda may have multiple parameters:

```caret
x y -> x + y
```

Equivalent named function:

```caret
add x y =
  x + y
```

and lambda:

```caret
add = x y -> x + y
```

Lambda parameters are separated by whitespace, consistently with ordinary Caret function declarations and application.

---

## Lambda bodies

A lambda may contain a single expression:

```caret
x -> x * 2
```

or an indented block:

```caret
x ->
  doubled = x * 2
  doubled + 1
```

The result of the final expression is the result of the lambda, following the same rules as an ordinary function body.

Example:

```caret
normalize =
  text ->
    trimmed = trim text
    lowercase trimmed
```

No braces, commas, or explicit `return` keyword are required.

---

## Parameter contracts

Lambda parameters use the same contract syntax as named-function parameters.

Example:

```caret
(Int) x -> x * 2
```

Multiple contracts are written in one parenthesized contract clause:

```caret
(Int positive) x -> x * 2
```

Multiple parameters may each have their own contracts:

```caret
(Int) x (Int positive) y ->
  x + y
```

Contracts have exactly the same semantics as on named function parameters.

For example:

```caret
(Int positive) x -> x * 2
```

requires `x` to satisfy both `Int` and `positive`.

The compiler should statically verify contracts wherever possible and retain runtime checks only where necessary according to the normal Caret contract rules.

---

## Arity

A lambda's arity is the number of explicitly declared parameters.

```caret
x -> expression
```

has arity 1.

```caret
x y -> expression
```

has arity 2.

```caret
a b c -> expression
```

has arity 3.

Lambda arity participates in Caret's ordinary arity-directed function application and binary-function interpretation.

For example:

```caret
compare = a b -> a.value < b.value
```

creates an ordinary two-argument function and may be used anywhere another binary function can be used.

---

## Application

Lambda values are called using ordinary whitespace application.

Example:

```caret
double = x -> x * 2

result = double 10
```

A lambda may also be created and immediately applied:

```caret
(x -> x * 2) 10
```

Parentheses are required here to delimit the lambda expression before its argument.

Application is left-associative according to the normal Caret rules.

---

## Partial application

Multi-parameter lambdas support ordinary partial application.

Given:

```caret
add = x y -> x + y
```

then:

```caret
add 10
```

returns a unary function awaiting `y`.

Example:

```caret
addTen = add 10
result = addTen 5
```

Caret's arbitrary-position hole syntax also works with lambdas and lambda-derived functions.

For example:

```caret
between = low value high ->
  value >= low and value <= high

inside = between 0 _ 10
```

`inside` is a unary function.

---

## Lambdas versus holes

Caret supports both explicit lambdas and implicit partial application through `_`.

For simple partial application:

```caret
addOne = + _ 1
```

is preferred over unnecessarily verbose lambda syntax:

```caret
addOne = x -> x + 1
```

Both are valid and semantically compatible.

Explicit lambdas are useful when:

* a parameter is used more than once;
* multiple expressions are needed;
* the parameter needs a meaningful local name;
* parameter contracts are needed;
* control flow is required;
* the body cannot be expressed naturally through partial application.

Example:

```caret
distanceSquared = p ->
  p.x * p.x + p.y * p.y
```

A hole `_` always denotes a future argument to an existing application expression. It is not itself a named lambda variable.

---

## Closures

A lambda may reference bindings from its lexical environment.

Example:

```caret
makeAdder amount =
  x -> x + amount
```

Then:

```caret
addFive = makeAdder 5
addFive 10
```

produces:

```text
15
```

The lambda captures `amount`.

Captured values follow Caret's normal ownership, mutability, and lifetime rules.

A closure must not provide a way to access a value after its ownership or lifetime has ended.

The compiler may copy, borrow, share, or move captured values according to the applicable ownership rules.

---

## Capture timing

Captured expressions are evaluated according to normal lexical evaluation semantics when the closure is created.

For example:

```caret
amount = calculateAmount source
f = x -> x + amount
```

the lambda captures the resulting `amount`; it does not implicitly call `calculateAmount` again whenever `f` is invoked.

This is consistent with arbitrary partial application:

```caret
f = calculate _ expensiveExpression
```

where supplied expressions are evaluated when the partial function is constructed unless explicitly represented as another function.

---

## Purity and effects

Lambda effects are inferred exactly like effects of named functions.

Example:

```caret
square = x -> x * x
```

has an empty inferred effect set and is pure.

An effectful lambda:

```caret
writer = x ->
  writeFile path x
```

inherits the filesystem effect of `writeFile`.

Effects propagate through:

* direct calls;
* captured functions;
* higher-order calls;
* composition;
* partial application.

A lambda passed to a parameter requiring purity must have an empty inferred effect set.

For example:

```caret
map (pure) transform values =
  ...
```

accepts:

```caret
map (x -> x * 2) values
```

but rejects an effectful lambda.

Explicit function-value contracts may also be applied using the normal Caret contract mechanism where needed.

Conceptually:

```caret
(pure) (x -> x * 2)
```

requires the resulting lambda value to satisfy `pure`.

Purity must always be verified from the lambda body; the contract is a requirement, not merely an annotation.

---

## Lambda return values

A lambda returns the value produced by its body.

Single-expression example:

```caret
x -> x * 2
```

Block example:

```caret
x ->
  a = x * 2
  b = a + 1
  b
```

returns `b`.

Return-type or return-value contracts should follow the general Caret function-result contract mechanism once that syntax is finalized.

Do not introduce a separate lambda-specific return-type syntax.

---

## Nullary lambdas

Caret may represent a zero-argument anonymous function as:

```caret
-> expression
```

Example:

```caret
action =
  ->
    calculateSomething
```

A nullary lambda is a function value.

Creating the lambda does not execute its body.

This differs from referring to a named zero-argument function by its ordinary name, where Caret's normal nullary-function evaluation rules may invoke the function.

The lambda literal itself is already an explicit function value and therefore does not require `@`.

For example:

```caret
action = -> currentTime
```

stores a function.

Invoking `action` follows the normal rules for a nullary function.

The exact invocation syntax for a stored nullary function should remain consistent with the general nullary-function rules.

---

## Reification

A lambda is already a function value.

It does not require `@` in order to be passed to another function:

```caret
map (x -> x * 2) values
```

`@` remains the general binding-reference/reification operator and is primarily needed when referring to an existing binding without applying its normal evaluation behavior.

For example:

```caret
@namedFunction
```

reifies the binding `namedFunction`.

Do not redefine `@` as lambda syntax.

---

## Higher-order functions

Lambdas are ordinary function values and may be:

* passed as arguments;
* returned from functions;
* stored in collections;
* stored in fields;
* composed;
* partially applied;
* reflected;
* constrained by function contracts.

Example:

```caret
apply transform value =
  transform value

result = apply (x -> x * 2) 10
```

A function may return a lambda:

```caret
multiplier factor =
  x -> x * factor
```

A lambda may return another lambda:

```caret
x -> y -> x + y
```

This is equivalent in behavior to a curried two-stage function.

It is distinct in structure from:

```caret
x y -> x + y
```

which is a single lambda with arity 2.

Both may support equivalent partial use where appropriate, but reflection must preserve their actual structure.

---

## Function composition

Lambda values participate in ordinary `>>` composition.

Example:

```caret
process =
  (x -> x * 2)
  >> normalize
  >> validate
```

or:

```caret
double = x -> x * 2
process = double >> normalize
```

Composition preserves inferred contracts and effects according to the normal Caret composition rules.

A composition is pure only if every participating function is pure.

---

## Lambdas in collection operations

Lambdas may be used directly with collection functions.

Examples:

```caret
numbers map (x -> x * 2)
```

```caret
numbers filter (x -> x > 0)
```

```caret
people map (person -> person.name)
```

Because a pure unary Boolean function is a valid Caret contract, a suitable lambda may also represent a runtime predicate.

For example:

```caret
positive = (Int) x -> x > 0
```

is a pure unary Boolean function and therefore satisfies the requirements for use as a contract predicate.

Where a contract must be referenced repeatedly or participate in static reasoning, assigning it a stable name is preferred.

---

## Lambdas and SIMD

Pure lambdas may participate in SIMD application when their operations are vectorizable.

Example:

```caret
values :: (x -> x * x + 1)
```

The compiler should infer that the lambda is pure and determine whether its operations support SIMD execution.

An effectful lambda cannot normally be used with `::`.

Example:

```caret
values :: (x ->
  print x
  x * 2)
```

must fail if `print` introduces an observable effect.

SIMD support does not require separate lambda syntax.

---

## Lambdas in data definitions

Because `data` blocks contain ordinary Caret expressions, lambda values may be stored directly in data structures.

Example:

```caret
operations =
  data
    ^double (x -> x * 2)
    ^positive (x -> x > 0)
```

The resulting fields contain ordinary function values.

Likewise, a lambda may calculate a field value through immediate application or higher-order functions.

No special data-lambda syntax is required.

---

## Parsing and precedence

`->` introduces a lambda and has low precedence.

The expression:

```caret
x -> x + 1
```

must parse as:

```text
x -> (x + 1)
```

not:

```text
(x -> x) + 1
```

Multiple parameters immediately preceding `->` belong to the same lambda:

```caret
x y z -> expression
```

Parameter contracts bind to the immediately following parameter:

```caret
(Int) x (String) y -> expression
```

When a lambda appears as an argument inside a larger expression, parentheses should be required wherever its extent would otherwise be ambiguous:

```caret
map (x -> x * 2) values
```

rather than relying on context-sensitive parsing.

An indented lambda body extends through the lambda's indentation block.

---

## Implementation requirements

The initial implementation should support at minimum:

1. Unary lambdas:

```caret
x -> expression
```

2. Multi-parameter lambdas:

```caret
x y -> expression
```

3. Parameter contracts:

```caret
(Int positive) x -> expression
```

4. Indented lambda bodies:

```caret
x ->
  expression
  expression
```

5. Lexical captures.
6. Ordinary function application of lambda values.
7. Partial application.
8. Interaction with `_` hole-based partial application.
9. Lambda effect and purity inference.
10. Passing lambdas to higher-order functions.
11. Returning lambdas from functions.
12. Function composition using `>>`.
13. Reflection/reification compatibility.
14. SIMD eligibility for suitable pure lambdas.

The initial implementation may postpone:

* sophisticated capture optimization;
* static totality checking;
* explicit capture lists;
* ownership-polymorphic closures;
* specialized allocation-free closure representations.

These implementation optimizations must not alter the semantic rule that a lambda is an ordinary first-class Caret function value.

---

## Design principle

Lambda syntax should remain a minimal anonymous form of ordinary Caret function syntax.

Named function:

```caret
add x y =
  x + y
```

Anonymous equivalent:

```caret
x y -> x + y
```

Caret should not create a separate semantic category for lambdas.

They use the same:

* application rules;
* contracts;
* arity;
* partial application;
* purity/effect inference;
* composition;
* ownership rules;
* reflection;
* SIMD rules

as named functions.

## Cycles

### Overview

A Caret cycle is a generalized iterative state transformation.

Rather than giving `for`, `while`, and similar loops unrelated semantics, Caret models iteration as repeated transformation of a state value.

Conceptually:

```text
initial state
    ↓
condition
    ↓
body
    ↓
prepare next state
    ↺
```

A cycle produces a final value.

It is therefore an expression, not merely a control-flow statement.

Conceptually:

```text
cycle : Init -> Condition -> Body -> Prepare -> Result
```

For a state type `S`:

```text
init      : () -> S
condition : S -> Bool
body      : S -> S
prepare   : S -> S

cycle     : S
```

The cycle repeatedly transforms `S` until `condition` becomes false.

---

## Fundamental semantics

Given:

```caret
result = cycle init condition body prepare
```

the semantics are equivalent to:

```text
state = init

while condition(state):
    state = body(state)
    state = prepare(state)

result = state
```

This description is only explanatory.

Caret should not require mutable state internally.

The semantic model is functional:

```text
S -> S -> S -> ...
```

Each stage receives a state and produces the next state.

The final state is the value of the entire `cycle` expression.

---

## Initialization

`init` creates the initial state.

It may be:

* an existing value;
* a function producing a value;
* a `data` structure;
* a returned scope;
* another expression whose result becomes the initial cycle state.

Example:

```caret
initial =
  data
    ^i 0
    ^sum 0

result =
  cycle initial condition body prepare
```

A nullary initializer may also be used where initialization itself must be deferred:

```caret
result =
  cycle @makeInitialState condition body prepare
```

The exact handling of nullary functions follows the normal Caret function-reference rules.

---

## State as a scope

A particularly important use of `cycle` is iteration over a structured scope.

Example state:

```caret
data
  ^i 0
  ^sum 0
```

The cycle may transform this scope at every step.

Conceptually:

```caret
condition s =
  s.i < 10

body s =
  s
  >> set "sum" (s.sum + s.i)

prepare s =
  s
  >> set "i" (s.i + 1)
```

Then:

```caret
result =
  cycle initial condition body prepare
```

produces a final state equivalent to:

```caret
data
  ^i 10
  ^sum 45
```

The exact collection/scope update functions may be provided by the standard library.

The important semantic rule is that each phase receives the complete current state and returns the complete next state.

### Previous and next state views

Cycle conditions, bodies, and preparation phases execute with a cycle-state view in addition to
their ordinary lexical parameters.

For every phase, unqualified state-field reads refer to the complete previous state. Name lookup
checks local bindings and parameters first, then previous-state public fields, then the captured
lexical parent. The reserved `next` binding denotes the state currently being constructed.

A body or preparation phase begins with `next` structurally equal to its previous state. An exported
assignment writes the new state:

```caret
^sum = sum + i
```

Unmentioned fields remain present in `next`. A later expression in the same phase can observe an
earlier write explicitly:

```caret
^sum = sum + i
^large = next.sum > 100
```

Reading `sum` in the second assignment still reads the previous state; `next.sum` reads the new
value. Non-exported assignments remain local temporaries and do not become state fields.

Each phase commits atomically. The body transforms `S0` into `S1`; preparation then receives `S1`
as its previous state and transforms it into `S2`. No caller can observe a partially constructed
state.

A phase that performs exported state writes commits `next` as its result. A phase that performs no
exported state writes may instead return an explicit complete state value, preserving the ordinary
`S -> S` functions shown above. Mixing exported state writes with a different explicit state return
is an error rather than an implicit merge.

The condition receives the same previous-state read view but is read-only: it cannot perform
exported state writes or access a changing `next` value. This preserves condition purity.

---

## Functional semantics

Cycles are semantically compatible with immutable data.

A cycle does not require mutation of the current state.

Conceptually:

```text
S0
 ↓ body
S1
 ↓ prepare
S2
 ↓ body
S3
 ...
```

The previous state may become unreachable after the next state is produced.

This permits Caret implementations to optimize immutable cycle transformations aggressively.

If the compiler can prove that a previous state is no longer observable, it may:

* reuse storage;
* update values in place internally;
* eliminate intermediate allocations;
* use mutable machine registers or stack slots;
* perform tail-call-like transformations.

Such optimizations must not change the observable immutable semantics.

---

## Equivalence to tail recursion

A cycle can be expressed as tail recursion.

Conceptually:

```caret
run s =
  condition s &
    run (prepare (body s))
  !
    s
```

Therefore:

```caret
cycle initial condition body prepare
```

is semantically equivalent to repeatedly applying:

```caret
body >> prepare
```

while `condition` holds.

This equivalence is important.

`cycle` is not a separate mutable execution model.

It is a convenient and optimizable representation of a common recursive state transformation.

---

## Body and prepare are separate

`body` and `prepare` deliberately have separate roles.

For a conventional `for`-style loop:

```text
initialize
check
body
increment
check
body
increment
...
```

the mapping is:

```text
init       -> initialization
condition  -> loop condition
body       -> loop body
prepare    -> increment/update before next iteration
```

For example:

```caret
initial =
  data
    ^i 0
    ^sum 0

condition s =
  s.i < 10

body s =
  update s "sum" (s.sum + s.i)

prepare s =
  update s "i" (s.i + 1)

result =
  cycle initial condition body prepare
```

Keeping `body` and `prepare` separate makes conventional iteration easy to express while retaining the general state-transform model.

---

## Omitted prepare phase

When no separate preparation step is needed, the identity function may be used.

Conceptually:

```caret
cycle initial condition body identity
```

The standard library should provide an identity function.

Caret may later provide shorthand syntax for omitting an identity `prepare` phase, but the fundamental semantics remain:

```text
prepare : S -> S
```

---

## Omitted body phase

Similarly, a cycle whose meaningful work occurs entirely in the preparation transformation may use `identity` as its body:

```caret
cycle initial condition identity prepare
```

No special loop form is required.

---

## Example: counting

Conceptually:

```caret
initial =
  data
    ^i 0

condition s =
  s.i < 10

body s =
  s

prepare s =
  update s "i" (s.i + 1)

result =
  cycle initial condition body prepare
```

The final result contains:

```caret
result.i
```

with value:

```text
10
```

---

## Example: accumulation

```caret
initial =
  data
    ^i 1
    ^total 1

condition s =
  s.i <= 10

body s =
  update s "total" (s.total * s.i)

prepare s =
  update s "i" (s.i + 1)

result =
  cycle initial condition body prepare
```

The final cycle state contains both the accumulated result and the final index.

The caller may select the part it needs:

```caret
factorial10 = result.total
```

This is preferable to requiring a separate externally mutable accumulator.

---

## Lambdas with cycles

Cycle phases may be supplied directly as lambdas.

Example:

```caret
result =
  cycle
    initial
    (s -> s.i < 10)
    (s -> update s "sum" (s.sum + s.i))
    (s -> update s "i" (s.i + 1))
```

All lambda rules apply normally:

* contracts;
* lexical capture;
* purity inference;
* effects;
* partial application;
* ownership.

No separate "loop lambda" syntax is required.

---

## Partial application

Because `cycle` is an ordinary higher-order function, partial application may be used to define reusable cycle forms.

For example:

```caret
repeatWhile condition body =
  cycle _ condition body identity
```

or:

```caret
iterate prepare =
  cycle _ _ identity prepare
```

The exact reusable abstractions should preferably be library functions rather than additional loop syntax.

---

## Scope shape

The initial implementation should require a stable state shape across a cycle unless the type system can prove a broader compatible type.

For example, if the initial state is:

```caret
data
  ^i 0
  ^sum 0
```

then `body` and `prepare` should normally return values exposing compatible fields:

```text
i
sum
```

A transformation that sometimes returns:

```caret
data
  ^i 1
```

and sometimes:

```caret
data
  ^i 1
  ^sum 10
  ^error "..."
```

introduces variant state shapes.

Such cycles may eventually be represented using:

* structural unions;
* optional fields;
* pattern matching;
* row-polymorphic types.

The initial implementation may reject incompatible state-shape changes.

---

## Contracts

Cycle phase contracts follow ordinary Caret rules.

For a state contract `State`:

```caret
condition (State) s =
  ...

body (State) s =
  ...

prepare (State) s =
  ...
```

The compiler should infer that the cycle preserves `State` where possible.

Conceptually:

```text
condition : State -> Bool
body      : State -> State
prepare   : State -> State
```

A phase that violates the required state contract causes a compile-time error where statically detectable.

---

## Purity and effects

`cycle` itself does not imply mutation or effects.

A cycle is pure if:

* initialization is pure;
* `condition` is pure;
* `body` is pure;
* `prepare` is pure.

For example:

```caret
result =
  cycle
    initial
    (s -> s.i < 10)
    updateTotal
    increment
```

is pure if all supplied components are pure.

If a phase has effects, those effects propagate to the cycle expression according to the ordinary effect system.

Example:

```caret
(io) printState s =
  print s
  s
```

Using it as the body causes the cycle to acquire the `io` effect.

The enclosing function must therefore explicitly permit that effect.

Effects must not be hidden merely because they occur inside iteration.

---

## Condition purity

The cycle condition must normally be pure.

```text
condition : S -> Bool
```

The compiler must reject a condition whose evaluation introduces an undeclared observable effect.

This avoids behavior where merely checking whether another iteration should occur changes external program state.

If effectful conditions are ever supported, they must be explicitly represented and must participate in normal effect inference.

The initial implementation should require cycle conditions to be pure.

---

## State ownership

Cycle state follows normal Caret ownership and lifetime rules.

Conceptually, each iteration consumes the current state and produces the next state:

```text
S0 -> S1 -> S2
```

When a state is uniquely owned and the old version is not subsequently accessible, the compiler may safely reuse its physical storage.

This is particularly important for large:

* collections;
* buffers;
* scopes;
* SIMD data;
* format-processing state.

Functional cycle semantics must therefore not imply mandatory copying.

---

## Cycles over collections

Collection iteration can be implemented using `cycle`, although high-level collection operations should remain available.

For example, operations such as:

```caret
map
filter
fold
reduce
```

may internally lower to cycle-like transformations.

Application code should normally prefer these more descriptive operations when they directly express the intent.

Use `cycle` when the iteration requires explicit multi-value state or a more general state machine.

---

## Cycles and SIMD

A cycle may be optimized using SIMD when its transformations satisfy normal SIMD requirements.

The presence of `cycle` does not itself request SIMD execution.

Explicit SIMD syntax such as:

```caret
collection :: transform
```

remains the preferred way to require vectorized element-wise execution.

The compiler may nevertheless auto-vectorize suitable pure cycles where safe.

---

## Cycles and formats

Streaming or incremental format processing may eventually use cycles internally.

For example, a decoder may repeatedly transform a state containing:

```text
input position
decoded values
current format element
remaining input
```

until the format is complete.

The format system should not require application programmers to manually write such cycles for ordinary decoding.

`cycle` provides a general implementation mechanism rather than replacing declarative formats.

---

## Early termination

A future version of Caret may support explicit cycle-control values such as:

```text
Continue S
Break S
```

Conceptually:

```text
body : S -> Continue S | Break S
```

A `Break` result terminates the cycle and returns its contained state.

A `Continue` result proceeds normally.

This is preferable to implementing `break` through:

* exceptions;
* hidden mutation;
* non-local jumps.

The initial implementation may postpone `Break` and `Continue`.

Until then, early termination should be represented through the cycle condition or explicit state.

For example:

```caret
data
  ^done false
  ^state ...
```

with:

```caret
condition s =
  not s.done
```

---

## Nested cycles

A cycle is an expression and may therefore be used inside another cycle.

Example conceptually:

```caret
outerResult =
  cycle outerInitial outerCondition
    (outer ->
      innerResult =
        cycle innerInitial innerCondition innerBody innerPrepare

      combine outer innerResult)
    outerPrepare
```

No special nesting syntax is required.

Each cycle has its own state value.

---

## Relationship to conventional loops

Common imperative loop forms can be expressed through `cycle`.

A conventional:

```text
for initialization; condition; increment
    body
```

corresponds to:

```text
cycle initialization condition body increment
```

A conventional:

```text
while condition
    body
```

corresponds conceptually to:

```text
cycle initial condition body identity
```

A repeated state machine corresponds to:

```text
cycle initial notFinished transition identity
```

Therefore separate `for`, `while`, and `do` constructs are not required for the core language.

Libraries may provide convenience abstractions where useful.

---

## Implementation model

The compiler should initially lower:

```caret
cycle initial condition body prepare
```

to behavior equivalent to tail-recursive execution:

```text
state = initial

loop:
    if not condition(state):
        return state

    state = body(state)
    state = prepare(state)
    goto loop
```

This imperative pseudocode is an implementation strategy only.

The observable language semantics remain immutable state transformation.

The compiler is free to implement the cycle using:

* a machine-level loop;
* tail-call elimination;
* mutable local variables;
* registers;
* in-place storage reuse;
* specialized collection iteration.

No intermediate state copies are required unless observable semantics demand them.

---

## Implementation requirements

The initial implementation should support at minimum:

1. `cycle` as an expression that returns its final state.
2. An initial state value.
3. A pure unary Boolean condition.
4. A unary body transformation.
5. A unary preparation transformation.
6. Lambda expressions as phase arguments.
7. Named functions as phase arguments.
8. Structured `data` or scope values as cycle state.
9. Effect inference through all cycle phases.
10. Contract checking of state transformations.
11. Efficient lowering without mandatory immutable copying.
12. Stable state shape across iterations.

The initial implementation may postpone:

* `Break` / `Continue` values;
* changing structural state types during iteration;
* effectful conditions;
* automatic parallel cycles;
* explicit loop labels;
* generalized nondeterministic relational cycles.

---

## Design principle

A Caret cycle is not fundamentally a mutable loop.

It is repeated application of state transformations:

```text
S
 -> body
 -> prepare
 -> S
```

controlled by:

```text
S -> Bool
```

and producing the final state as its result.

This provides conventional iteration while remaining compatible with:

* immutable data;
* first-class scopes;
* contracts;
* effect inference;
* lambdas;
* partial application;
* ownership optimization;
* tail recursion;
* SIMD optimization.

The core model should remain small enough that more specialized iteration constructs can be implemented as ordinary Caret functions rather than additional language syntax.

## Rules, Rulesets, and Rule Cycles

### Overview

Caret provides a rule system for defining reactive systems such as:

* games;
* simulations;
* data and stream interpreters;
* protocol processors;
* workflow engines;
* state machines.

Rules execute inside a `ruleCycle`.

A rule does not independently poll or execute globally. The surrounding `ruleCycle` provides:

* object traversal;
* context changes;
* context fronts;
* rule evaluation;
* rule scheduling;
* effect propagation;
* chaining;
* lifecycle and termination.

The components of a rule are summarized by the mnemonic **CATEN**:

```text
C  Context
A  Active state
T  Trigger
E  Effect
N  Name
```

All CATEN components are optional.

---

# Rules

## Basic definition

A rule is a first-class Caret value.

Example:

```caret
capture = rule
  C game and playerTurn
  A on
  T captureRequested and validCapture
  E
    move selectedPiece target
    destroy targetPiece
  N capture
```

The components are:

```text
C  context in which the rule can apply
A  whether the rule is active
T  condition or event that triggers application
E  changes caused by the rule
N  optional identity
```

The canonical documentation order is CATEN.

---

## Context

A context has a persistent Boolean state:

```text
up
down
```

A rule may apply only while its `C` expression is up.

Contexts may be combined using ordinary Boolean expressions:

```caret
game and playerTurn
combat and not paused
dialog or cutscene
```

Example:

```caret
attack = rule
  C game and playerTurn
  T attackRequested
  E performAttack
```

If:

```caret
game and playerTurn
```

is down, `attack` cannot apply.

### Context fronts

Changing a context produces a transient front.

A transition:

```text
down -> up
```

produces:

```caret
rise context
```

A transition:

```text
up -> down
```

produces:

```caret
fall context
```

Examples:

```caret
rise combat
fall dialog
```

Boolean combinations may also have fronts:

```caret
rise (game and playerTurn)
fall (combat or dialog)
```

The distinction between a level and a front is fundamental:

```caret
combat
```

means `combat` is currently up.

```caret
rise combat
```

means `combat` has just changed from down to up.

```caret
fall combat
```

means `combat` has just changed from up to down.

A front is transient and exists only as part of the corresponding rule-cycle propagation.

---

## Changing contexts

Contexts may be changed by rule effects or other rule-cycle operations:

```caret
raise combat
lower combat
```

`raise` changes a context to up.

`lower` changes a context to down.

Raising an already-up context does not generate another rise front.

Lowering an already-down context does not generate another fall front.

---

## Active state

Every rule has an active state independent of its context.

The active state is:

```text
on
off
```

Example:

```caret
specialAttack = rule
  A off
  T specialRequested
  E performSpecialAttack
```

An inactive rule cannot apply.

Rules may be activated and deactivated at runtime:

```caret
activate @specialAttack
deactivate @specialAttack
```

Context and active state have different meanings:

```text
Context
    describes whether circumstances permit the rule.

Active state
    describes whether the rule itself is enabled.
```

---

## Trigger

`T` defines the condition or Boolean combination of conditions that causes a rule to become applicable.

Example:

```caret
death = rule
  T player.health <= 0
  E destroy player
```

Normal persistent conditions use transition semantics.

For:

```caret
player.health <= 0
```

the triggering event is normally:

```text
false -> true
```

A continuously true condition does not repeatedly trigger the rule.

A rule therefore becomes applicable when:

```text
C is up
AND
A is on
AND
T triggers
```

### Fronts in triggers

Context fronts may be used directly:

```caret
beginTurn = rule
  T rise playerTurn
  E prepareTurn

resume = rule
  T fall dialog
  E resumeGame
```

This is particularly important for chaining rules.

### Context and active state are gates

`C` and `A` permit application but do not normally generate a delayed trigger.

For example:

```caret
rule
  C combat
  T enemy.health <= 0
```

If:

```caret
enemy.health <= 0
```

becomes true while `combat` is down, subsequently raising `combat` does not retroactively apply the rule.

If entering combat should itself cause evaluation as an event, it should be expressed explicitly:

```caret
rule
  T rise combat and enemy.health <= 0
```

---

## Effect

`E` contains the changes caused by application of the rule.

Example:

```caret
capture = rule
  T validCapture
  E
    move selectedPiece target
    destroy capturedPiece
    addScore currentPlayer captureValue
```

`E` is an ordinary Caret block.

It may call ordinary functions.

Like an ordinary `cycle` transformation, an `E` block executes against persistent previous and
next rule-cycle state. Unqualified state reads observe the previous state, `^field = value` writes
the next state, and `next.field` observes writes already made by the current effect. Unmentioned
fields carry forward. The complete next state becomes visible atomically after the selected rule's
effect finishes and before applicability is reevaluated.

Typical operations may include:

```caret
raise context
lower context

activate @rule
deactivate @rule

create object
destroy object

send message
```

The rule system does not require a closed hard-coded set of effect operations.

### Effect inference

Calls made from `E` participate in Caret's ordinary effect system.

An effect involving networking, file access, GUI state, or other externally observable behavior introduces the corresponding inferred effects.

`C` and `T` should normally remain pure because the rule engine may reevaluate them freely.

---

## Name

`N` optionally identifies a rule.

Example:

```caret
rule
  N capture
  T validCapture
  E capturePiece
```

When a rule is assigned directly:

```caret
capture = rule
  T validCapture
  E capturePiece
```

the implementation should normally infer:

```text
N capture
```

unless another explicit identity is supplied.

Binding name and rule identity are conceptually distinct:

```caret
r = rule
  N capture
  ...
```

---

## Optional CATEN components

All CATEN components are optional.

Recommended defaults are:

```text
C omitted  -> always up
A omitted  -> initially on
T omitted  -> no autonomous trigger
E omitted  -> no explicit effect
N omitted  -> anonymous/internal identity
```

A rule without `E` still produces its implicit rule context when applied.

A rule without `T` may still participate in mechanisms such as explicit invocation or chaining.

---

## Implicit rule context

Every rule owns an implicit context.

When a rule applies:

```text
raise rule.context
execute E
lower rule.context
```

Therefore every application produces:

```caret
rise @rule.context
fall @rule.context
```

Other rules may respond to those fronts.

Example:

```caret
capture = rule
  T captureRequested
  E capturePiece

score = rule
  T fall @capture.context
  E addScore currentPlayer captureValue
```

The implicit context exists even when the rule has no explicit `E`.

---

# Rule ordering

## Unordered rules

Rule definition order does **not** imply execution order.

If several rules are simultaneously applicable and no ordering relationship between them has been specified, the `ruleCycle` may choose any of them.

For example:

```caret
a = rule
  T event
  E effectA

b = rule
  T event
  E effectB
```

If both become applicable, either sequence is valid:

```text
a
b
```

or:

```text
b
a
```

Caret deliberately provides no guarantee that the chosen order remains the same across:

* executions;
* compiler versions;
* platforms;
* optimization levels;
* runtime implementations.

Source order must never be relied upon as implicit rule priority.

---

## Effects affect subsequent scheduling

Applicable rules are not normally executed as an immutable simultaneous batch.

The scheduler conceptually operates as follows:

```text
determine applicable rules

choose one permitted rule

apply it

propagate its effects

reevaluate affected rules

choose another applicable rule

...
```

Therefore the first selected rule may alter whether another previously applicable rule remains applicable.

Example:

```caret
a = rule
  T condition
  E disableSomething

b = rule
  T condition and somethingEnabled
  E otherEffect
```

If both initially become applicable and `a` executes first, its effect may make `b` no longer applicable.

If `b` executes first, both effects may occur.

If that difference matters, the developer must specify ordering.

---

## Unordered-rule diagnostics

Because accidental ordering dependencies can produce difficult bugs, Caret tooling should warn when it detects potentially significant unordered rule application.

A diagnostic may conceptually report:

```text
warning:
rules `a` and `b` may become applicable without a defined order
their effects may be observed in either order
```

Static analysis should report cases it can reasonably identify.

A development or debug runtime may additionally report actual cases where several unordered rules become applicable together.

This is a warning, not an error.

Unordered application is a legitimate and intentional design technique.

---

## Explicit acknowledgement of unordered execution

A developer may explicitly state that arbitrary ordering is acceptable.

The `unordered` contract marks such intent:

```caret
(unordered) ambientEffect = rule
  T event
  E updateAmbientEffect
```

A ruleset may similarly declare that unordered interactions among its relevant rules are intentional:

```caret
(unordered) AmbientRules =
  ruleset
    ...
```

The annotation suppresses applicable unordered-order diagnostics.

It does **not** change scheduling behavior.

```caret
(unordered)
```

means:

> Arbitrary ordering is semantically acceptable here.

It does not mean that the runtime must randomize execution order.

`unordered` is a built-in declaration contract, not a second annotation system. Like `pure`, it
has compiler-recognized semantic behavior beyond an ordinary Boolean predicate. It is valid on a
rule or ruleset declaration and invalid on unrelated values.

---

## Enforcing order

When execution order matters, it must be represented explicitly.

The preferred mechanism is a causal relationship between rules.

For example:

```caret
damage = rule
  T attack
  E applyDamage

death = rule
  T fall @damage.context
  E checkDeath
```

`death` cannot precede completion of `damage`.

This is a semantic dependency rather than a source-order convention.

---

# Rule chaining

## Explicit chain

A sequence of rules may be defined explicitly through rule contexts:

```caret
first = rule
  T start
  E firstEffect

second = rule
  T fall @first.context
  E secondEffect

third = rule
  T fall @second.context
  E thirdEffect
```

This imposes:

```text
first
  ↓
second
  ↓
third
```

---

## `chain` sugar

Caret should provide concise sugar for this common pattern:

```caret
chain
  rule
    T start
    E firstEffect

  rule
    E secondEffect

  rule
    E thirdEffect
```

This is equivalent to connecting each subsequent rule to:

```caret
fall @previous.context
```

The chain therefore compiles to ordinary rules and ordinary contexts.

It does not introduce a separate execution mechanism.

---

## Explicit trigger in a chain

A chained rule may additionally specify a trigger:

```caret
chain
  rule
    T start
    E first

  rule
    T ready
    E second
```

The effective trigger of the second rule is conceptually:

```caret
fall @previous.context and ready
```

Thus `ready` must hold at the completion front of the previous rule.

If the desired meaning is instead:

> first must have completed, then wait however long necessary for `ready`

that should be represented using a persistent context rather than ordinary chain-front semantics.

---

## Partial ordering

Rule dependencies may form a partial order rather than a single sequence.

Conceptually:

```text
       A
      / \
     B   C
      \ /
       D
```

`B` and `C` have no ordering relationship and may therefore execute in arbitrary order.

Both are constrained to occur after `A`.

`D` is constrained by both branches.

This is intentional.

Caret should constrain only those rule relationships explicitly expressed by the program.

Independent branches remain unordered.

Numeric priorities or implicit source-order priorities are not required for the core rule model.

---

# Rulesets

## Overview

A `RuleSet` is a first-class reusable scope containing rules and supporting definitions.

Rulesets may contain:

* rules;
* contexts;
* helper functions;
* data;
* configuration;
* nested rulesets;
* private implementation state.

Example:

```caret
Combat attacker target damage =
  ruleset
    prepare = rule
      T attackRequested attacker
      E prepareAttack attacker

    ^attack = rule
      T fall @prepare.context
      E damage target (damage attacker target)

    cleanup = rule
      T fall @attack.context
      E finishAttack attacker
```

---

## Ruleset templates

Caret does not require a separate template language for rulesets.

An ordinary function returning a `RuleSet` acts as a template:

```caret
Combat attacker target damage =
  ruleset
    ...
```

Its ordinary Caret parameters are the ruleset holes.

Ruleset parameters may include:

* objects;
* contexts;
* functions;
* rules;
* rulesets;
* collections;
* predicates;
* formats;
* configuration values;
* effect functions.

Normal contracts may constrain them.

Normal partial application also applies:

```caret
standardCombat =
  Combat _ _ standardDamage
```

The remaining `_` positions are supplied when the template is instantiated.

---

## Ruleset encapsulation

Members of a ruleset are private by default.

`^` exposes a member through the ruleset's public interface.

Example:

```caret
TurnSystem players =
  ruleset
    index = 0
    internalState = context down

    ^turn = context down

    ^next = rule
      T endTurn
      E advancePlayer players
```

External code may access:

```caret
turnSystem.turn
turnSystem.next
```

but cannot access private bindings such as:

```caret
turnSystem.index
turnSystem.internalState
```

This uses the normal Caret meaning of `^`.

Rulesets do not introduce another visibility system.

---

## Exported rules

Rules are exported in exactly the same way:

```caret
Movement board pieces =
  ruleset
    validate = rule
      ...

    update = rule
      ...

    ^completed = rule
      ...
```

External users may refer to:

```caret
movement.completed
@movement.completed
@movement.completed.context
```

Private internal rules remain inaccessible.

Exported rules and contexts provide stable integration points between ruleset libraries.

---

## Ruleset instances

Every ruleset construction creates an independent instance.

For example:

```caret
playerCombat = Combat player enemy damage
enemyCombat = Combat enemy player enemyDamage
```

must create independent runtime state for:

* active states;
* rule contexts;
* private contexts;
* private instance state;
* instance-local rules.

The ruleset definition may be shared, but runtime state belongs to each instance.

---

## Nested rulesets

Rulesets may build larger systems from smaller rulesets:

```caret
TurnBasedCombat players world damage =
  ruleset
    install TurnRules players
    install TargetSelection world
    install DamageRules world damage
    install DeathRules world
```

This allows reusable libraries to be assembled hierarchically.

---

# `ruleCycle`

## Overview

`ruleCycle` is the execution environment for rules.

A rule cycle:

1. executes initialization;
2. establishes its objects, contexts, rules, and rulesets;
3. raises its master context;
4. traverses relevant objects and rules;
5. generates implicit contexts and fronts;
6. determines applicable rules;
7. applies one permitted applicable rule at a time;
8. propagates its effects;
9. reevaluates affected rules;
10. continues until stable;
11. advances its traversal;
12. terminates when its master context goes down.

---

## Initialization

A rule cycle contains an `init` part:

```caret
system =
  ruleCycle
    init
      ...
```

Initialization establishes the initial rule-cycle universe.

It may create:

* objects;
* contexts;
* rules;
* ruleset instances;
* data;
* other cycle-local state.

Example:

```caret
game =
  ruleCycle
    init
      player = object
        ^health 100

      enemy = object
        ^health 50

      gameOver = rule
        T player.health <= 0
        E lower cycle
```

---

## Installing rulesets

A ruleset may be constructed independently:

```caret
combat = Combat player enemy calculateDamage
```

and installed into the current cycle:

```caret
install combat
```

or commonly:

```caret
install Combat player enemy calculateDamage
```

according to normal Caret application rules.

Installation makes the ruleset's relevant rules and contexts part of the current `ruleCycle`.

A constructed but uninstalled ruleset remains an ordinary value and does not autonomously execute.

---

## Template-based system construction

A principal purpose of `ruleCycle` is to assemble systems from reusable rule libraries.

Example:

```caret
game =
  ruleCycle
    init
      board = makeBoard 8 8

      white = Player "White"
      black = Player "Black"

      pieces = makePieces board white black

      install AlternatingTurns white black
      install ChessMovement board pieces
      install CaptureRules pieces
      install ChessVictory white black pieces
```

The application-specific definition may therefore consist mainly of objects, configuration, and instantiated rulesets.

The same mechanism can construct a data interpreter:

```caret
parser =
  ruleCycle
    init
      source = stream bytes

      install Signature pngSignature
      install ChunkReader source PngChunk
      install StopAt "IEND"
```

---

## Master cycle context

Every `ruleCycle` owns an implicit master context.

At cycle start:

```text
down -> up
```

producing its rise front.

The cycle runs while that context is up.

A rule may terminate the cycle:

```caret
finish = rule
  T completed
  E lower cycle
```

The cycle ends when its master context goes down.

---

## Object traversal

A rule cycle implicitly traverses the objects belonging to its runtime universe.

Application code does not normally write this outer traversal explicitly.

When an object is entered, processed, or left, the cycle may implicitly raise and lower object-related contexts.

Conceptually:

```text
object A context rises
    rule propagation
object A context falls

object B context rises
    rule propagation
object B context falls
```

These transitions produce ordinary fronts available to rule triggers.

Objects may also participate in category or state contexts where defined by their contracts or object model.

---

## Rule scheduling

The observable scheduling model is:

```text
1. Update context/object state.

2. Determine applicable rules.

3. Respect explicit causal ordering relationships.

4. If multiple unordered rules are applicable,
   choose an arbitrary one.

5. Raise the chosen rule's implicit context.

6. Execute its effect.

7. Propagate state and context changes.

8. Lower the rule's implicit context.

9. Propagate the resulting fall front.

10. Reevaluate affected rules.

11. Repeat until no applicable rule remains
    for the current propagation step.
```

The implementation need not literally scan every rule.

It may maintain dependency indexes, queues, or other optimized structures.

The observable result must follow the same scheduling semantics.

---

## No source-order guarantee

The order in which rules appear in:

* source code;
* a `ruleset`;
* an `init` block;
* an internal collection

does not create a scheduling constraint.

For example:

```caret
firstInSource = rule
  ...

secondInSource = rule
  ...
```

does not imply:

```text
firstInSource -> secondInSource
```

If order matters, the program must state the relationship explicitly.

---

## Propagation to stability

Effects may change:

* object state;
* contexts;
* rule active states;
* object existence;
* installed state;
* values used by triggers.

These changes may make other rules applicable.

The cycle continues applying and propagating rules until the current processing step reaches a state in which no further rule is applicable.

Conceptually:

```text
change
  ↓
rule A
  ↓
change
  ↓
rule B
  ↓
rule C
  ↓
stable
```

Only then does normal traversal advance.

---

## Trigger stability

Repeated evaluation must not repeatedly fire a continuously true trigger.

For:

```caret
rule
  T x > 10
  E ...
```

application occurs on the relevant transition:

```text
false -> true
```

not on every internal scan while `x > 10` remains true.

The runtime must retain sufficient trigger history to preserve this behavior.

---

## Object creation and destruction

Objects in a rule cycle are persistent values with stable logical identities. An object version is
an immutable public scope constructed with ordinary exported bindings:

```caret
player = object
  ^health = 100
  ^name = "Ada"
```

The cycle state stores the current version under an exported state field. Replacing that field with
a newly constructed version preserves the object's logical identity; the previous version remains
unchanged for any code that still holds it. Objects outside a cycle remain ordinary inert values and
do not acquire autonomous behavior.

Effects may create objects:

```caret
create bullet
```

or destroy them:

```caret
destroy enemy
```

Created objects become part of the rule-cycle universe.

Destroyed objects cease to participate after destruction becomes effective.

Creation adds a new logical identity to the next persistent cycle state. Destruction removes that
identity from the next state. Both changes commit at the effect boundary and alter traversal only at
the next deterministic traversal boundary; neither operation reenters traversal while an object is
being processed.

The implementation must provide deterministic lifecycle behavior even though rule scheduling itself may intentionally be unordered.

The initial implementation should avoid uncontrolled traversal reentrancy when an object is created during another object's propagation.

---

## Dynamic rule state

Rule effects may change rule active states:

```caret
activate @specialRule
deactivate @tutorialRule
```

Such changes participate in normal propagation.

The active state is runtime state, not merely a compile-time annotation.

---

## Cycle termination

The rule cycle runs while its master context remains up.

A normal termination operation is:

```caret
lower cycle
```

Once the cycle context falls, no new ordinary traversal iteration should begin.

The runtime may finish the currently required deterministic cleanup or propagation before returning.

---

## Relationship to ordinary `cycle`

Ordinary:

```caret
cycle initial condition body prepare
```

explicitly provides state transformations.

`ruleCycle` derives them from:

* objects;
* contexts;
* installed rules;
* installed rulesets;
* CATEN semantics;
* the rule scheduler.

Conceptually:

```text
condition:
    cycle context is up

body:
    process objects and contexts
    schedule applicable rules
    propagate rule effects to stability

prepare:
    advance traversal
```

`ruleCycle` may internally reuse ordinary cycle machinery, but its reactive scheduling semantics are defined separately.

---

# Implementation requirements

The initial implementation should support at minimum:

1. A first-class `Rule` value.
2. Optional CATEN clauses:

```text
C Context
A Active
T Trigger
E Effect
N Name
```

3. Persistent up/down contexts.
4. Boolean context combinations.
5. `rise` and `fall` fronts.
6. Runtime rule active states.
7. Edge-based trigger behavior.
8. Implicit contexts for rule application.
9. Rule chaining through rule-context fronts.
10. `chain` sugar.
11. Explicitly unordered rule execution when no dependency defines order.
12. No implicit source-order priority.
13. Reevaluation after each selected rule's effects.
14. Warning diagnostics for potentially significant unordered rule interactions.
15. `(unordered)` as explicit acknowledgement/suppression of those diagnostics.
16. Explicit ordering through rule dependencies and chains.
17. A first-class `RuleSet`.
18. Ruleset parameters through ordinary Caret functions.
19. Partial ruleset application using `_`.
20. `^` exports for rules and other ruleset members.
21. Independent ruleset instances.
22. `install ruleset`.
23. `ruleCycle` initialization.
24. An implicit master cycle context.
25. Object traversal and implicit object-related context changes.
26. Rule propagation until stable.
27. Object creation and destruction.
28. Runtime rule activation/deactivation.
29. Cycle termination by lowering its master context.
30. Ordinary Caret effect inference through rule effects.

The initial implementation may postpone:

* numeric rule priorities;
* parallel execution;
* transactional batches;
* distributed rule cycles;
* optimized dependency graphs;
* dynamic ruleset unloading;
* debugger visualization;
* formal conflict analysis.

These later features must preserve the principle that rule order is constrained only where the program explicitly specifies a dependency.

---

# Design principle

A `ruleCycle` establishes a reactive universe of objects, contexts, rules, and rulesets.

A rule becomes applicable when:

```text
C is up
AND
A is on
AND
T triggers
```

Application causes:

```text
rule context rises
E executes
rule context falls
```

Those effects and fronts may make additional rules applicable.

When several rules are applicable:

```text
explicit dependency
    -> constrains their order

no dependency
    -> order is deliberately arbitrary
```

The runtime applies one permitted rule, propagates its effects, reevaluates the system, and continues until the current propagation reaches stability.

The developer may explicitly acknowledge harmless unordered behavior with:

```caret
(unordered)
```

and should express required ordering through causal relationships such as rule-context dependencies or `chain`.

Rulesets package reusable parameterized behavior.

`^` defines their public interface.

The `ruleCycle` `init` block assembles those reusable rule libraries with concrete objects and configuration, allowing systems such as games, interpreters, simulations, and workflows to be built primarily by composition rather than explicit control flow.

## Planned modules and compilation

### Import expressions

A module is a Caret source file evaluated through an ordinary import expression:

```caret
math = import "lib/math.caret"
```

The path is resolved relative to the importing source file after normalizing `.` and `..`. The
initial implementation requires the explicit file name and does not search a global package path.
Successful module evaluation is cached by canonical source path for the lifetime of the program.
Every importer receives the same immutable module scope containing only top-level `^` exports.
Private bindings remain inaccessible through lookup and reflection.

An import cycle is a located module diagnostic that reports the import chain. A module that fails to
load or evaluate is not cached as successful. Importing the same canonical module again does not
repeat its initialization effects.

### Compiler target and compatibility

The first compiler backend targets Java 21-compatible JVM class files. It can package a program and
its Caret modules as a runnable or library JAR while using a versioned Caret runtime ABI.

The Java tree-walking interpreter remains the reference implementation until differential tests
establish parity. Interpreted and compiled execution must agree on:

* values and structural equality;
* evaluation and effect order;
* missing versus null;
* exported visibility and reflection;
* contracts and effects;
* stable diagnostic codes and source locations;
* standard output and standard error; and
* process exit status.

The exact JVM class naming, public embedding ABI, and cross-version binary compatibility policy
remain open design decisions. They do not change Caret source semantics.

## Open specification decisions

The following decisions remain unresolved and must be settled in `LANGUAGE.md` before their
dependent implementation begins:

* syntax for declaring new symbolic operators beyond the existing built-in symbols;
* concrete SIMD type/lane spelling and floating-point reduction-order guarantees;
* surface syntax for general format choices and pattern-derived discriminators;
* the concrete exported shape of structured format success/failure values;
* whether rule-cycle object traversal order is observable or deliberately unspecified; and
* JVM class naming, embedding ABI, and binary compatibility across Caret versions.

These are tracked as `unresolved` requirements in `CONFORMANCE.md`. No implementation may silently
choose syntax or observable semantics for them.


## Not implemented

- unified prefix/infix callable operators and composition
- ungrouped multiline call arguments and trailing lambdas
- contracts, static types, effect inference, and ownership analysis
- general collection/data syntax, first-class fields, and persistent updates
- lambdas and higher-order standard collection operations
- cycles and transactional previous/next state views
- SIMD values and required vectorized application
- bytes, formats, codecs, and structured format failures
- contexts, rules, rulesets, persistent cycle objects, and rule cycles
- modules, imports, JVM compiler backend, runtime ABI, and optimizer
