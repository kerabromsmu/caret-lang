<a id="simd"></a>
# SIMD

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)


SIMD is a language-level execution mechanism rather than a separate intrinsic API.

Ordinary pure numeric functions should be usable on both scalar values and SIMD values whenever their operations can be lifted lane-wise.

<a id="simd-types"></a>
## SIMD types

A native-width SIMD value is written:

```caret
(Simd native Float32) values
```

`Simd` has fixed arity: its first argument is a width selector and its second is the scalar
contract. `native` is a built-in compile-time width selector, not an omitted argument or partial
application. The number of lanes is chosen appropriately for the compilation target.

An explicit lane count may be written:

```caret
(Simd 8 Float32) values
```

This represents exactly eight `Float` lanes.

`Simd` is a capitalized contract constructor and participates in normal contract syntax.

<a id="floating-point-reduction-grouping"></a>
## Floating-point reduction grouping

SIMD floating-point reductions read the active execution-environment grouping option when the
reduction begins. The default is `pairwise`:

```caret
simdOption grouping pairwise
simdOption grouping hardware
```

`pairwise` uses the language-defined pairwise grouping. `hardware` permits target-dependent
grouping and therefore target-dependent floating-point results. The option is inherited by a child
environment at construction, remains current within that environment, and may subsequently be
changed there without changing its parent. Strict left-to-right reduction remains available through
an explicit scalar `fold`; it is not the SIMD default.

<a id="lane-wise-operations"></a>
## Lane-wise operations

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

<a id="simd-boolean-values-and-conditional-selection"></a>
## SIMD Boolean values and conditional selection

A comparison involving SIMD values produces a SIMD Boolean mask.

```caret
positive = values > 0
```

If `values` is:

```caret
Simd native Float32
```

then `positive` is conceptually:

```caret
Simd native Boolean
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

<a id="scalar-functions-lifted-to-simd"></a>
## Scalar functions lifted to SIMD

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

<a id="explicit-simd-application"></a>
## Explicit SIMD application

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

<a id="is-a-requirement-not-merely-a-hint"></a>
## `::` is a requirement, not merely a hint

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

<a id="purity-requirement-for-simd-mapping"></a>
## Purity requirement for SIMD mapping

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

<a id="function-composition-and-partial-application"></a>
## Function composition and partial application

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

<a id="reductions"></a>
## Reductions

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

<a id="memory-and-alignment"></a>
## Memory and alignment

Normal SIMD code should not require explicit aligned-load, unaligned-load, store, or hardware-register operations.

The compiler/runtime is responsible for handling:

* native SIMD width;
* memory alignment;
* vector loading and storing;
* remainder elements;
* target instruction sets such as AVX, AVX2, AVX-512, NEON, or equivalent facilities.

Low-level architecture-specific SIMD facilities may exist separately, but they are not part of the
ordinary `Simd` / `::` programming model.

<a id="portability"></a>
## Portability

Code using:

```caret
Simd native Float32
```

is portable across targets with different native SIMD widths.

Code using an explicit width:

```caret
Simd 8 Float32
```

requests that logical lane width specifically. The compiler may use one or more hardware vector operations to implement it where necessary, or reject it when the target cannot support the required semantics.

