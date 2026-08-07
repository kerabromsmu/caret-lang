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

Internally, errors retain their phase, a stable diagnostic code, message, primary source span, and
space for related source spans. The CLI renders the primary location in the compact form below.

```text
Error: Line 1, column 7: Unknown name: absent
```

## Bindings and functions

```text
x = 10
add a b = a + b

makePerson name age =
  internal = age + 1
  ^name = name
  ^age = age
```

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
without invoking it regardless of the function's arity. Its reflective view includes `kind` and
`remaining`.

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
- functions: `remaining`

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

This is planned behavior and is not implemented by the current parser. The rules for declaring the
precedence and associativity of named and symbolic binary functions still need to be specified.

### Core semantic decisions

Blocks predeclare their function bindings before executing statements. This supports direct and
mutual recursion. Other bindings are initialized in source order and cannot be read before their
declaration executes.

Closures capture their lexical environment. Duplicate definitions and duplicate parameters in one
scope are errors. Parameters and declarations in a function body may shadow outer bindings;
function-body declarations are nested inside the parameter scope so established forms such as
`^name = name` export a parameter under the same name. Parent lookup is lexical.

Equality is structural for scalar values and exported scopes. Callable values cannot be compared
for equality. Collection equality will be structural when collections are implemented.

`@function` produces metadata and is not a callable reflective reference. Reflected invocation is
a separate planned facility.

The following decisions remain prerequisites for unified binary functions:

- precedence and associativity for named functions used with infix notation; and
- the complete operand/coercion rules for operators once collections and static types exist.

The self-interpreter may represent successful and failed operations as exported result scopes. Its
CLI adapter can then render a failed result as the normal located `Error:` diagnostic.

### Not required for self-interpretation

The first Caret-written interpreter does not depend on static types, loops, mutation, modules,
lambdas, pattern matching, ownership, reflected invocation, or a compiler backend. Recursion,
immutable collections, tagged exported scopes, and the planned text operations are sufficient.

## Contracts

A contract constrains a declaration or parameter. Contracts are written in parentheses immediately before the binding they constrain.

```caret
(Int) count

(Int positive) amount

(pure) normalize text =
  ...
```

Multiple contracts are listed inside the same parentheses:

```caret
(Int positive nonZero) amount
```

This means that all listed contracts must hold.

Conceptually:

```caret
(A B C) x
```

requires:

```caret
A x
B x
C x
```

A contract may carry additional compile-time semantics beyond its Boolean result.

### Contract functions

Any function may be used as a value contract when it:

* takes exactly one argument;
* returns `Bool`;
* is pure.

Example:

```caret
positive x =
  x > 0

(Int positive) amount
```

The compiler may evaluate such contracts at compile time whenever the argument is known and the contract is compile-time evaluable.

If the compiler can prove a contract false, compilation fails.

If the compiler cannot prove a runtime value satisfies a value contract, a runtime contract check may be retained.

Contract functions must not have observable side effects.

### Types as contracts

Type names behave as contracts.

```caret
(Int) value
(String) name
```

Conceptually:

```caret
Int value
String name
```

produce Boolean type-membership tests.

Types additionally provide static type information to the compiler.

Nullable and optional modifiers participate in the same type-contract syntax:

```caret
(String?) value      // may be null
(String~) value      // may be missing
(String?~) value     // may be missing or null
```

### Function contracts

Contracts placed immediately before a function name constrain the function itself.

```caret
(pure) calculate x =
  ...
```

Contracts placed before parameters constrain those parameters:

```caret
(pure) calculate (Int positive) x =
  ...
```

`pure` is a built-in function contract.

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


## Not implemented

- static types and `T?`, `T~`, `T?~`
- cycle primitive and immutable scope transitions
- multiline call arguments or trailing blocks
- lambdas
- mutation and immutable scope-update expressions
- resource ownership and deterministic destruction
- rich reflection and reflected invocation
- modules, imports, compiler backend, bytecode, optimizer
