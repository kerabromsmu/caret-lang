# Packed layouts

[Chapter index](../06-collections-fields-and-templates.md) · [Language specification index](../../LANGUAGE.md)


## Phase 4 packed layouts (implemented)

This design governs the packed-storage implementation delivered by #78 after numeric foundations
(#82) and explicit conversion (#83). It takes precedence over less specific packed illustrations
later in this document. SIMD execution, formats, and native buffer APIs remain later work.

### Supported layouts and membership

`Packed T` is an ordinary contract constructor describing a finite positional sequence with a
selected, uniform element layout. It derives the ordinary Sequence/Collection capabilities;
indices are consecutive from zero. Membership requires selected layout, not just values that
could fit it. For example, an ordinary nonempty `[1 2]` does not satisfy `Packed Int8` until
contextual construction or explicit conversion selects that representation. Shape-neutral `[]`
retains its contextual-empty exception; a selected packed empty retains `T` and its layout.

Supported scalar layouts are `Int8`/`UInt8` (one byte), `Int16`/`UInt16` (two), `Int32`/`UInt32`
(four), `Int64`/`UInt64` (eight), `Float` (four), `Double` (eight), and `Boolean` (one byte, 0 or 1).
The [numeric contracts](../04-contracts-inference-and-dispatch.md#phase-4-numeric-contracts-implemented)
own their ranges, membership, and aliases. `Byte` selects UInt8, `Float32` selects Float, and
`Float64` selects Double. `Int` means format-independent Integer and supplies no packed layout.

Support exact fixed-size structural templates whose scalar positions recursively have
unambiguous concrete formats. This includes fixed members, nested positional/named templates,
and checked holes. Fixed-value and repeated-hole constraints still apply. Derivation/refinement
must preserve the selected layout and ordinary semantic validation; broad or conflicting layout
requirements are not resolved by sampling values or guessing a width. Preserve concrete-format
requirements in structural descriptors even though several numeric contracts may accept a value.

A derived nominal contract with one fixed base layout selects that layout, including when it
appears in a structural template hole. Contextual packed literals, explicit packed conversion,
and packed append validate the base and refinements, then acquire the derived contract for each
accepted element before storage. Nested template holes acquire their own derived contracts.
Decoded elements retain those semantic memberships. A broad base with no fixed layout, or
different concrete layouts among bases or hole requirements, is invalid even for an empty packed
collection. Packed append does not round or truncate a value to make it fit a derived contract.

Positional fields follow index order. Named fields follow the template's declaration order,
recursively, independently of sorted Dictionary enumeration. Equivalent named semantic shapes
can consequently have different physical layouts. A template constructed from a concrete
Collection without declaration-order provenance uses that Collection's enumeration order.

Element payloads occupy a contiguous block with shared internal layout metadata. Semantic
contracts must survive packing and element access independently of physical storage. Do not
expose host objects, addresses, buffers, offsets, stride, alignment, or byte order through Caret
reflection. Public metadata exposes the selected semantic contract as `@packed.elementContract`
through the ordinary contract metadata projection when the observer can name that contract; the
field is absent otherwise. A template is not thereby an external serialization `Format`.

The representation decision has three distinct inputs. A template descriptor owns the logical
positions, names, fixed values, refinements, and `defaultsMissing` flags. A concrete numeric
contract owns a format and its representable value domain. An internal packed-layout descriptor
records the contiguous physical arrangement selected from those inputs. Equal layouts may share
one internal descriptor even when their templates differ semantically; membership and reflection
must continue to use each value's semantic contract. Conversely, equal semantic field sets can
have distinct named physical orders when their template declaration orders differ. The selected
layout is retained with the Collection so `Packed T` membership does not infer storage from
currently enumerated values.

Nullable/optional elements and fields, missing/null payloads, variable-size fields, String payloads,
arbitrary-precision Integer payloads without a concrete format, references, packed Sets, packed
dictionaries, and bit fields are excluded. Do not reserve a valid numeric bit pattern as a missing
sentinel. A uniform fixed layout cannot be inferred from `Number`, `Real`, `Integer`, or `Natural`
alone, including for an empty value. Ordinary Collections retain all their broader capabilities.

### Construction, conversion, and operations

Contextual literal construction selects the requested layout and validates values. A declaration
does not silently convert an already-established ordinary sequence into packed storage.
Explicit `(Packed T) source` conversion recursively converts elements to `T`, using the
[built-in conversion rules](../04-contracts-inference-and-dispatch.md#phase-4-explicit-contract-conversion-implemented).
After conversion, every element must satisfy its structural and scalar requirements. Incompatible
shape, unsupported layout, and out-of-range values are located errors; do not expose a partial
packed result. A fixed-width append is validation, not an implicit explicit-conversion request.

```caret
(Packed Int8) small = [1 2]
converted = (Packed Int8) [1.9 2.1]  // explicitly truncates elements to [1 2]
ordinary = (Sequence Number) small  // explicitly selects ordinary sequence storage
extended = seqAdd ordinary 300
// seqAdd small 300                 // error: incompatible Int8 element

Point = template [(Float) _ (Float) _]
(Packed Point) points = [[1.0 2.0] [3.0 4.0]]
```

Direct positional conversion accepts keyless input and preserves enumeration order. A keyed
source first needs an explicit `keys`, `values`, or `fields` projection; conversion never discards
keys implicitly. Completely consume a lazy source before exposing a packed result, propagating
all demanded effects. Reject a declared-infinite source; unknown finiteness can require an
enumeration that never terminates. Conversion does not invoke `eager`'s reflection-removal rule
as a hidden coercion or bypass ordinary lexical lazy establishment.

Successful persistent append preserves `Packed T` and the selected layout; incompatible values
are errors. Preserve every existing alias and the source on success or failure. An explicit
ordinary-sequence conversion permits later operations under a wider element domain. No new
builder or public element-update API is added by this design.

Integrate `keys`, `values`, `fields`, `size`, guarantees, numeric access, templates, and ordinary
reflection. Packed sequences are finite, ordered, sequential, keyless, and have values. Value
uniqueness follows established facts rather than packed storage alone. Equality follows the
Collection revision: physical layout/order of record storage alone cannot change logical value
equality. Selected-layout membership is preserved with storage optimizations disabled; automatic
storage optimization must not add or remove observable contract membership.

`map` and `filter` keep their lazy behavior and do not silently repack results. Repacking uses
explicit conversion. `eager` preserves compatible selected contracts and materializes according to
its existing specified traversal; it does not infer new packed contracts merely from uniform
values. Optimized and optimization-disabled execution must agree on values, membership, effects,
reflection, equality, diagnostics, and persistent updates.

### Packed and prerequisite acceptance matrix

This matrix records required implementation evidence. Exact expected outputs and diagnostic
phase/code/physical line/column appear in the cited tests and runnable fixtures.

| Area | Required acceptance cases |
| --- | --- |
| Each signed width 8/16/32/64 | Minimum, maximum, zero, −1; min−1/max+1 rejection; exact literals, conversion, append, extraction, and reflection. |
| Each unsigned width 8/16/32/64 | Zero, maximum, −1/max+1 rejection; full UInt64 values without intermediate double rounding. |
| Abstract numeric domains | Arbitrary-precision Integer/Natural arithmetic; aliases; overlapping representability membership; broad domains rejected as packed layouts. |
| Float/Double | Exact/non-exact representability, ties-to-even conversion, contextual literal selection, small/subnormal values, finite boundaries, overflow, and signed-zero consistency. |
| Numeric operations | Exact cross-format equality/order; true division, div, remainder, all sign combinations, zero divisors, operator precedence, aliases, holes, and large quotients. |
| Precision diagnostics | Static and dynamic implicit loss; broad warning versus strict error; explicit conversion and ordinary rounding without warnings; declared function boundaries. |
| Conversion grammar | Predicate versus conversion, computed/aliased targets, non-contract grouping, precedence, Collection boundaries, lambdas/$/postfix access, checked and repeated numbered holes. |
| Conversion values | Integer truncation before range checks; unsupported text/truthiness conversion; String overloads/effects; strict declarations; recursive structural conversion and shape/fixed-value failures. |
| Boolean/layout | One-byte Boolean; nested and mixed fixed-format records; declaration order distinct from Dictionary enumeration; concrete-template fallback order. |
| Invalid layouts | Nullable/optional fields even with present data; missing/null, broad/unconstrained/conflicting formats, variable-size/reference fields, and unsupported keyed outer shapes. |
| Construction | Contextual empty, shaped packed empty, selected-layout predicate versus ordinary sequence, eager/lazy inputs, known-infinite rejection, effects, and explicit keyed projections. |
| Persistence/protocol | Compatible append and failed append; aliases, ownership reuse, keys/values/fields/size, access, guarantees, equality, templates, eager, and lazy transforms. |
| Metadata/parity | Preserved semantic contracts, no physical or host metadata exposure, shared layouts, and all observable behavior with optimizations enabled/disabled. |
| Embedding | Exact integer input/output/callback round trips; retained double carrier; separate nonfatal warnings for load, execute, and invoke. |

Numeric, conversion, and packed implementation cards must each add representative runnable
`.caret` examples with golden output and `test.sh` execution. Negative fixtures or focused Java
tests cover every diagnostic; parser/resolver/inference/runtime tests cover static-versus-dynamic
discovery and locations. Run the full baseline suites, corpus/navigation and conformance checks,
example-coverage script, and `git diff --check` before claiming implementation completion.

The delivery order was numeric values, formats, arithmetic, warnings, and embedding carriers
(#82); value-producing conversion syntax and recursive conversion (#83); then contiguous selected
packed storage and protocol integration (#78). Optimized execution uses a contiguous payload and
shared internal descriptor; optimization-disabled execution retains a reference representation
with the same observable behavior.
