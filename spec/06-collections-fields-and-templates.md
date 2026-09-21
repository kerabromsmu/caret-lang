<a id="collections-fields-and-templates"></a>
# Collections, Fields, and Templates

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

## Phase 4 Collection protocol revision (planned)

This section records the decisions from issues #55 and #59 and their joint design discussion.
It specifies planned behavior, not current interpreter support. For the subjects covered here it
supersedes the earlier target semantics below: required dot access, scalar-only dynamic keys,
eager transforms, a distinct non-Collection Field representation, and earlier collection-equality
assumptions. Existing tests and the implemented baseline remain valid descriptions of the current
prototype until their corresponding implementation changes land. All examples in this section
are conceptual/planned.

### Scope and custom-provider deferral

Phase 4 includes the common protocol, built-in lazy transforms, collection-value `eager`,
and their contracts, effects, reflection, and diagnostics. Public custom-provider construction,
its registration/constructor syntax, general computations, concurrency/synchronization, resumable
failure handling, callable forms of `eager`, and context-dependent template invocation are deferred.
Phase 4 does include expected-template completion of named Collection literals as specified under
[named fields](#named-fields); that rule does not change ordinary template application into a
constructor.
Built-in laziness must not depend on exposing those deferred APIs.

Public `addElement`, `removeElement`, and `replaceElement`, their construction-selection
interface, and additional immutable-update syntax are also deferred beyond Phase 4, with no later
phase assigned. Their design is retained below. Internal construction and settlement, existing
persistent primitives, and mutable containers with `put` remain in scope.

The Dictionary-order, zip construction, with-binding, and Field-access decisions below supersede
the former open items. Their implementation remains planned.

Custom construction has a semantic model: a provider supplies access, enumeration, size, and
guarantees; constructing code can edit unpublished content until settlement. The public mechanism
for supplying/registering that provider, its constructor syntax, and its complete interface remain
deferred beyond Phase 4, with no later phase assigned yet. Built-in constructors and transforms
can implement the protocol internally without exposing that mechanism. Do not invent a public
custom-provider API from conceptual constructor examples.

### Protocol operations and guarantees

The common operations are ordinary callables, with provider-specific contracts and effects:

| Operation | Meaning |
|---|---|
| `getElement collection key` | Obtain the element for a valid key; absent keys return `~`. |
| `keys collection` | Sequential enumeration of unique keys, or `~` if key access is unsupported. |
| `values collection` | Sequential value enumeration, or `~` for a key-only Set. |
| `fields collection` | Sequential enumeration of Field tuples for keyed Collections, plain values for keyless Collections. |
| `size collection` | `Natural~`; the provider may compute it and perform declared effects. |

The deferred protocol extension includes `addElement`, `removeElement`, and `replaceElement`, whose
construction and persistent-update contracts are retained under
[element operations](#element-operations-during-construction-and-after-settlement).

`Natural` is a contract for non-negative integer Numbers, including zero. Unknown size is `~`;
a known-infinite Collection returns `~` from `size`. No purity or constant-time requirement is
imposed on the size provider.

| Callable query | Reflection member | Result |
|---|---|---|
| `isSequential collection` | `@collection.sequential` | `Boolean~` |
| `isOrdered collection` | `@collection.ordered` | `Boolean~` |
| `isUnique collection` | `@collection.unique` | `Boolean~` |
| `isFinite collection` | `@collection.finite` | `Boolean~` |
| `isKeyed collection` | `@collection.keyed` | `Boolean~` |
| `hasValues collection` | `@collection.hasValues` | `Boolean~` |
| `size collection` | `@collection.size` | `Natural~` |

Queries and their reflective projections describe the same protocol facts. Size reflection uses
the same provider operation and effects, not a separate hidden calculation. Ordinary lazy-access
and visibility rules apply. For guarantees, `true` means guaranteed, `false` means known not to
hold, and `~` means unknown. Detectably contradictory declarations are contract errors without
forcing elements: for example, sequential true and ordered false.

Keyed/keyless and, for keyed Collections, Set/dictionary shape are determined before settlement;
they must not be inferred by forcing a lazy Collection. Shape-neutral `[]` is the exception:
`isKeyed` and `hasValues` are `~` until context selects shape. Its `keys`, `values`, and
`fields` return `[]`; size is zero, finite and unique are true. Ordering and sequentiality
come from the selected contract. These size/finiteness/uniqueness facts also hold for shaped
empty Collections.

Sequential guarantees numeric access to all elements at consecutive keys starting at zero and
the same values in the same order on every enumeration, including across separate invocations.
Sequential Collections may be lazy: deferred computation does not weaken that guarantee.
Ordered is separate: it guarantees stable entry order without requiring stable values or
consecutive numeric keys. An ordered keyed Collection can use arbitrary keys.

Keyless means no explicit user-defined keys; it does not prohibit positional access. A keyless,
non-sequential Collection may expose numeric enumeration positions if they are valid access keys.
Without such access, `keys` returns `~`. Sequential providers may additionally implement
nonnumeric aliases, but aliases are neither supplied by default nor included in key enumeration.

Keys are always unique. The optional unique guarantee concerns element values. Lazy providers'
guarantees are trusted; ordinary access does not automatically scan or remember all previous
values to validate uniqueness, sequentiality, or finiteness. Developers may explicitly use a
uniqueness checker or other validation when they do not trust the provider; its concrete public
API remains unspecified. Detected violations of declared guarantees are errors. Trust does not
justify returning internally contradictory declarations.

### Enumeration and lexical evaluation

Enumeration is part of the Collection protocol, not available only through metadata. Its results
are ordinary, possibly lazy sequential Collections; `keys` additionally guarantees unique values.
Where both exist, key and value enumerations align by entry order; keyed `fields` describes
the corresponding key/value pairs. Set fields have missing associated values. For keyless
Collections, `fields` yields values without synthetic index tuples.

A returned enumeration is individually sequential and stable. Separate calls in separate
invocations can obtain different enumeration Collections from a general lazy provider. Both
key enumeration and element access obey the general
[lazy-value rules](02-values-bindings-and-evaluation.md#planned-lazy-values-and-lexical-contexts):
first access establishes a value in the accessing lexical context; inherited values stay shared;
a fresh invocation can obtain different results. No special context is introduced by `eager`,
reflection, or a handler. A sequential provider must uphold its stronger cross-invocation
stability guarantee.

Providers determine the relationship between enumerated keys and access: the language does not
require enumeration to list every accessible key. Duplicate enumerated keys count once, retaining
their first position. Thus materializing enumerated content need not preserve access to omitted
keys. Provider APIs for custom construction are deferred; built-in protocol support is not.

### Lookup and keys

These planned expressions use the same access operation:

```caret
collection.name
collection.name~
collection["name"]
collection["name"]~
getElement collection "name"
```

Dot access is sugar for a String key, not a distinct guaranteed-presence operation. Optional
spellings remain accepted equivalents. Bracket access accepts every key allowed by the access
contract, including composite keys; the former String/Number/Boolean restriction is removed.
The sugar follows ordinary lexical resolution of `getElement`, including local shadowing.

Valid absent keys return `~` without recording an operation failure. Invalid keys violate the
access contract: wrong key types, fractional sequence indices, and `~` as a key are errors.
A valid integer outside a sequence's range is absent. Null `?` is a permitted key if its
contract allows it. Keys must support equality but need not be sortable. Key equality may force
lazy values or perform effects, which lookup and duplicate detection must account for.

Dictionaries impose an additional restriction: keys have one homogeneous type with a defined
ordering, and enumeration uses sorted key order. Mixed Dictionary key types are rejected rather
than assigned an implicit cross-type ordering. Existing String Dictionary order remains
locale-independent Unicode code-point order. General keyed Collections do not require sortable
or homogeneous keys; their access contracts still determine permitted keys. First-key retention
chooses the retained entry during construction, not a replacement for Dictionary sorted enumeration.

Holes lower through ordinary partial application: `collection[_]` corresponds to
`getElement collection _`, and `_[key]` awaits a Collection. Ordinary fixed-operand evaluation,
hole ordering and numbering, and the prohibition on mixed numbered/unnumbered holes apply.
This syntax decision requires parser/interaction tests, especially at Collection literal hole
boundaries; it is not an implemented extension yet.

### Fields, tuples, Sets, and contextual shape

A tuple is a positional Collection following a template, not a separate tuple runtime kind.
`field key value` constructs a two-position tuple carrying the `Field` contract.
That contract distinguishes fields from ordinary pairs. Field tuples support ordinary positional
access: `fieldValue[0]` obtains the key and `fieldValue[1]` obtains the value. Existing named
reflective metadata is retained under the usual visibility rules. More-than-two-position Fields
are deferred. Migrating the current distinct Field representation must preserve that contract,
field recognition and reflection while implementing the revised tuple/access semantics.

Field interpretation uses the result Collection contract:

| Field content | Contribution |
|---|---|
| `(Field) [key value]`, both present | A keyed entry with its associated value. |
| `(Field) [~ value]`, value present | A keyless element. |
| `(Field) [key ~]`, key present | A dictionary entry storing missing under a dictionary contract; a key-only member under a Set contract. |
| `(Field) [~ ~]` | No entry; no evidence toward shape inference. |

Plain values and Fields with absent keys may mix in a keyless result. Incompatible keyed/keyless
entry shapes are errors; simply mixing Field and non-Field runtime forms is not itself an error.
All-omitted results without a selecting contract produce shape-neutral `[]`.

A Set has unique keys with no associated values. `keys set` enumerates its members;
`fields set` enumerates `(Field) [key ~]` tuples; `values set` is `~`. Lookup returns the
stored member or `~` for absence. A Set cannot contain missing as an actual member/key.
Set versus dictionary is selected by the expected or inferred contract. All fields lacking
values may establish a Set only when contracts establish that fact without traversing lazy output;
otherwise require an explicit contract. This never conflates a dictionary entry storing `~`
with an absent entry.

### Construction and settlement

Constructing code may read, add, remove, and replace elements before the Collection settles.
Outside constructing code there is no exposed Collection until settlement. Guarantees are available
during construction. Settlement fixes structure and access mechanism without forcing all deferred
values; afterward analogous updates produce new immutable Collections, not changes to the original.
This replaces the earlier per-element-only monotonic construction proposal for the unpublished
construction phase. It does not introduce deep mutation or a public builder syntax.

Adding a repeated key refers to the existing entry: its new value is ignored and its original
position is retained. This is distinct from an explicit replacement operation in unpublished
construction. A value ignored because its key is already present need not be forced; effects
already performed to produce an eager value cannot be undone.

### Element operations during construction and after settlement

These ordinary functions and their construction-selection interface are deferred beyond Phase 4;
additional immutable-update syntax is deferred too. No later phase is assigned. The retained design
uses different contracts for unsettled construction and settled Collections. It does not expose
unfinished Collections to outside code or define the deferred
public custom-provider/builder API. The contract distinguishes the construction receiver from a
settled receiver; concrete surface types for unpublished construction remain to be specified
with that API.

| Call | Selection or input | Successful result during construction |
|---|---|---|
| `addElement collection element` | Plain value for keyless content; Field for keyed content. | The added Field. |
| `removeElement collection keyOrValue` | By key when key access is supported; otherwise the first equal value in enumeration order. | The removed Field. |
| `replaceElement collection keyOrValue replacement` | Same selection as removal; replacement uses the Collection's element form. | The old Field, not the replacement. |

For keyless content, a returned Field represents the element as `[~ value]`. Dictionary inputs
use `field key value`; Set inputs use `field key ~`. Ordinary Field shape interpretation and
Collection contracts apply. Replacement can change a keyed entry's key by supplying a new Field.

During construction, the operation edits the unpublished Collection and returns `Field~`.
The following no-change cases return `~` and leave the Collection unchanged:

- Adding a key already present; retain the existing entry under the first-key rule.
- Removing or replacing a valid key or value with no matching entry.
- Replacing an entry with a Field whose key collides with another existing entry. Do not remove
  the selected entry or overwrite the other one.

Invalid keys and detected contract violations remain errors, not missing results. In keyless
by-value selection, removal and replacement affect only the first equal occurrence, not all
duplicates. Equality and any demanded lazy access follow their ordinary contracts/effects.

For a settled Collection, the same function names have persistent-update contracts returning
the resulting immutable Collection, rather than a Field. They preserve the original Collection.
An absent selection or key collision leaves the content unchanged under the same rules.
There is no implicit mutable container or hidden replacement of the caller's binding.

### Sequential construction indices (provisional)

The construction-selection interface below is deferred with the element-operation functions.
It is not a Phase 4 public API requirement.

Unpublished sequential construction maintains element order but does not expose numeric
positional keys. Settlement assigns consecutive numeric indices starting at zero. The declared
sequential guarantee describes the settled Collection; it does not require positional access
to unfinished construction.

Removal and replacement during that keyless construction therefore select by the first equal
value. For example, removing `a` from the conceptual ordered contents `[a b a]` leaves `[b a]`;
replacing it with `c` gives `[c b a]`. There are no temporary element-reference selectors.
This rule is provisional: reconsider precise selection among equal values during implementation
if necessary, documenting any change rather than silently selecting a different behavior.
Settled Collections with positional key access use the by-key operation contract.

### Zip construction

Phase 4 provides two ordinary functions, each taking exactly two input sequences:

- `zip left right` produces a keyless sequence of two-element positional tuples, pairing input
  positions. These tuples are not automatically Fields.
- `zipWithKeys keys values` produces a keyed Collection, using its first input as keys and its
  second as associated values. Its entries follow the Field construction rules.

Different lengths are contract errors when discovered; do not silently truncate or pad.
For `zipWithKeys`, duplicate-key positions still consume positions for alignment but do not
force ignored values; the first entry is retained. Construction from defined data is eager;
if either source is lazy the result is lazy, retaining already-computed data.

`zipWithKeys` defaults to a general keyed Collection. An expected Dictionary contract selects
Dictionary construction with homogeneous sortable keys and sorted enumeration. General keyed
construction permits equality-comparable keys without requiring sorting. Neither function is a
public custom-provider registration mechanism; zip with more than two inputs is deferred.

When mapping only the values of an existing Collection while retaining its keys, the paired
inputs derive from one shared enumeration of that source's fields, ensuring alignment.
The value transformation stays lazy. General paired construction permits independently supplied
sequences; their alignment is the developer's responsibility. This value-only mapping uses
`zipWithKeys` with the shared source enumeration.

### Lazy transforms and consumers

`map transform collection` and `filter collection predicate` always produce lazy Collections,
even for eager inputs. Creating them does not run the transform/predicate. Their deferred effects
come from the source and function contracts and propagate through access, enumeration, equality,
rendering, and materialization as those operations demand work. Proven-pure cases may use only
optimizations preserving observable behavior.

All transforms/consumers use the elements exposed by `fields`: bare values for keyless inputs,
Field tuples for keyed inputs, including Sets. `fold` passes accumulator then element.
`fold` remains a strict ordered traversal returning its final accumulator; `any` and `all`
demand values immediately as needed and retain their established short-circuit/predicate rules.

`filter` preserves retained keys for keyed inputs. Positional keyless filtering compacts indices
from zero and preserves traversal order. Key enumeration of a filtered result can demand source
values and invoke predicates to determine membership, acquiring their effects.

`map` result shape is selected from returned fields/values and expected or inferred contracts:
Field results with keys produce keyed Collections, absent-key Fields or plain results can produce
keyless Collections. Mapping keyed input to plain values is allowed; mapping keyless input to
Fields is allowed. Set map defaults to Set for member results, but Fields with associated values
can select dictionary shape, and an expected keyless contract or absent-key Field can select
keyless shape. Key and value contracts follow the transform rather than being fixed to the source.

A keyed transform may change keys. Enumerating output keys therefore may invoke the transform
and establish its returned pair, including any eager value computation or effects within that
pair. This does not make ordinary map construction eager. Repeated output keys follow first-entry
semantics. Key-only enumeration is not a promise that a key-changing transform avoids all value work.

Empty map results use the transform's declared/inferred contract or expected context; unresolved
shape requires an explicit contract. An established all-omitted result without a selecting
contract remains the shape-neutral empty exception. Never force a lazy result just to infer shape.

Guarantee propagation must be sound:

- Map/filter retain sequentiality only when stable values and order are guaranteed; numeric indexing
  alone is insufficient. An effectful transform or predicate does not automatically preserve it.
- Map preserves value uniqueness only when proven to do so. Filter preserves uniqueness.
- Finite inputs give finite outputs. Filter of infinite or unknown input has unknown finiteness.
- A one-output-per-input keyless map preserves cardinality/finiteness. Key-changing maps can collapse
  keys, and Fields can omit entries; do not propagate infinite size or cardinality through those
  cases without proof. Infinite keyed map has unknown finiteness by default.
- One-output-per-input keyless map preserves known size. Filter/keyed map otherwise report unknown
  size unless a more precise result is established. Empty source size is zero. Providers may
  compute size through their ordinary protocol operation.

Default Collection text conversion recursively calls ordinary `toString` on elements, honoring
their overloads. It may demand lazy values and acquires their and selected overloads' effects.
There is no mandatory preview limit; developers can overload conversion.

### Collection materialization with eager

Phase 4 `eager value` materializes Collection values. It follows ordinary lexical evaluation;
there is no special traversal-wide cache/context overriding fresh versus inherited bindings.

1. Reject a Collection declared infinite immediately with a located error. For unknown finiteness,
   attempt enumeration, which may never finish.
2. Complete key enumeration before materializing any entry. If keys are unavailable, complete
   value enumeration instead. Enumeration itself may demand computations needed to produce its
   results, such as key-changing maps and filtering.
3. Process entries in enumeration order, depth-first. Materialize a key immediately before its
   retained value, completing nested content before advancing to the next entry.
4. If materialized keys collide, retain the first entry and do not force the later ignored value.
   Retain enumerated dictionary keys whose value settles to `~`.
5. Produce a separate fully defined Collection of that enumerated content, without retaining its
   provider. Omitted but provider-accessible keys are absent from this result. The original lazy
   Collection remains usable through its provider.

Do not add positional access or sequentiality to a result merely because storage uses an array.
A keyless source with unavailable keys remains keyless with unavailable keys. Derive compatible
result contracts when materialization transforms content; do not falsely retain uniqueness or
element contracts invalidated by those transformations.

Reflection references become `[]` without traversing their metadata, including nested references
and references in composite keys. For example, conceptual `eager [42 @person]` yields
`[42 []]` and leaves the source unchanged. Mutable container references remain the same containers:
do not dereference, freeze, or traverse their contents. Stored callables remain unchanged.
Phase 4 also returns directly supplied callables unchanged; later callable behavior is deferred.
Already-defined scalar values, including null and missing, remain unchanged.

Preserve shared acyclic nested Collections where ordinary lexical rules identify shared values.
Cyclic Collection containment is a located `eager` error, distinct from deferred synchronization
cycle detection. Do not traverse container contents or reflected metadata to discover such cycles.

Phase 4 uses existing failure behavior, not the deferred resumable-handler design.
There is no `eagerWithRetry` and no automatic cross-field failed-computation cache.
Recovery policy belongs to general failure handlers when that later facility exists.

### Collection equality (forcing policy provisional)

The choice to force lazy content during equality is provisional and must be revisited if edge
cases require it, especially effects/order, failures, infinite content, and sandbox-sensitive
reflection. Until revised:

- Contracts must permit a match; differing contracts alone do not prohibit equality. Set and
  dictionary shapes are unequal, even when all dictionary values are missing.
- Ordered and unordered Collections compare unequal. Unknown ordering gives false by default;
  developers may define custom comparison functions.
- Compare declared ordered Collections in order. For unordered keyed Collections compare by key;
  unordered keyless Collections compare values with multiplicities, ignoring enumeration order.
- If either Collection is known infinite, return false without enumeration, even for self-comparison.
  Equality is intentionally non-reflexive there; identity shortcuts must not override this rule.
  Unknown finiteness may be traversed and may not terminate.
- Compare enumerated content only; provider-accessible but unenumerated keys do not participate.
  Demand corresponding values left before right, reuse established values under lexical rules,
  and stop at the first mismatch. Propagate effects of demanded computations/comparisons.
- Shape-neutral `[]` adopts the compared Collection's matching contract. This is contextual
  adaptation, not one fixed shaped value equal to every empty Collection; distinct shaped empty
  Sets and dictionaries remain unequal.

No built-in comparison operation for ignoring contract/order differences is required.
General comparison strategies may be written by developers.

### Deferred template construction and callable eager

Existing structural templates remain contracts/predicates in Phase 4. Later template calls may
also construct instances by filling holes. Boolean result context selects predicate use;
Collection or compatible template result context selects construction. Independently inferred
contracts and arity may disambiguate; inference must not circularly assume an interpretation to
justify it. Otherwise emit a located ambiguity error asking for an explicit expression contract.
Two arguments to a two-hole template can select construction over unary predicate use.
Zero-hole templates construct in Collection context and test membership when applied to one value;
ambiguous bare uses require context.

Constructor partial application follows ordinary/numbered-hole rules. Defined inputs construct
eagerly; explicitly deferred inputs construct lazily. Ordinary calls evaluate values where needed;
a stored function remains a function. The completely deferred computation syntax is postponed.
Membership can rely on declared/inferred producer contracts when they establish the answer;
only necessary checks execute deferred computation and acquire its effects.

Later `eager` on a non-nullary callable returns a same-arity callable applying `eager` to its
completed result, preserving partial application and recursively handling callable results.
Wrapper creation is pure; invocation carries original and materialization effects.
A directly supplied nullary computation is executed and its result materialized. Stored function
values remain untouched. These forms are expressly not Phase 4 behavior.

### Required future implementation evidence

Add parser/resolver/runtime and runnable integration coverage for protocol reflection, key errors
and shadowed access sugar, hole boundaries, Field shape selection, Sets and keyless access,
lexical lazy sharing, effect propagation, duplicate suppression, paired alignment, contextual empty
values, equality order/infinite cases, eager key/value traversal, reflection removal, unchanged
containers/functions, alias sharing, containment cycles, and all located failures. Update the
diagnostic inventory with exact codes and locations when implementation chooses them.
Do not claim the new protocol implemented based on tests of legacy Sequence/Dictionary behavior.

## Phase 4 packed layouts (planned)

This approved design resolves the packed-storage decisions for issues #77 and #78. Exact numeric
foundations (#82) and explicit contract conversion (#83) are separate prerequisite implementation tasks.
Nothing in this section claims existing packed runtime support. It takes precedence over less
specific packed illustrations later in this document. Packed storage remains a Phase 4 completion
gate; SIMD execution, formats, and native buffer APIs remain later work.

### Supported layouts and membership

`Packed T` is an ordinary contract constructor describing a finite positional sequence with a
selected, uniform element layout. It derives the ordinary Sequence/Collection capabilities;
indices are consecutive from zero. Membership requires selected layout, not just values that
could fit it. For example, an ordinary nonempty `[1 2]` does not satisfy `Packed Int8` until
contextual construction or explicit conversion selects that representation. Shape-neutral `[]`
retains its contextual-empty exception; a selected packed empty retains `T` and its layout.

Supported scalar layouts are `Int8`/`UInt8` (one byte), `Int16`/`UInt16` (two), `Int32`/`UInt32`
(four), `Int64`/`UInt64` (eight), `Float` (four), `Double` (eight), and `Boolean` (one byte, 0 or 1).
The [numeric contracts](04-contracts-inference-and-dispatch.md#phase-4-numeric-contracts-planned)
own their ranges, membership, and aliases. `Byte` selects UInt8, `Float32` selects Float, and
`Float64` selects Double. `Int` means format-independent Integer and supplies no packed layout.

Support exact fixed-size structural templates whose scalar positions recursively have
unambiguous concrete formats. This includes fixed members, nested positional/named templates,
and checked holes. Fixed-value and repeated-hole constraints still apply. Derivation/refinement
must preserve the selected layout and ordinary semantic validation; broad or conflicting layout
requirements are not resolved by sampling values or guessing a width. Preserve concrete-format
requirements in structural descriptors even though several numeric contracts may accept a value.

Positional fields follow index order. Named fields follow the template's declaration order,
recursively, independently of sorted Dictionary enumeration. Equivalent named semantic shapes
can consequently have different physical layouts. A template constructed from a concrete
Collection without declaration-order provenance uses that Collection's enumeration order.

Element payloads occupy a contiguous block with shared internal layout metadata. Semantic
contracts must survive packing and element access independently of physical storage. Do not
expose host objects, addresses, buffers, offsets, stride, alignment, or byte order through Caret
reflection. Public metadata exposes selected semantic contracts through the ordinary reflective
interface; a template is not thereby an external serialization `Format`.

Nullable/optional elements and fields, missing/null payloads, variable-size fields, String payloads,
arbitrary-precision Integer payloads without a concrete format, references, packed Sets, packed
dictionaries, and bit fields are excluded. Do not reserve a valid numeric bit pattern as a missing
sentinel. A uniform fixed layout cannot be inferred from `Number`, `Real`, `Integer`, or `Natural`
alone, including for an empty value. Ordinary Collections retain all their broader capabilities.

### Construction, conversion, and operations

Contextual literal construction selects the requested layout and validates values. A declaration
does not silently convert an already-established ordinary sequence into packed storage.
Explicit `(Packed T) source` conversion recursively converts elements to `T`, using the
[built-in conversion rules](04-contracts-inference-and-dispatch.md#phase-4-explicit-contract-conversion-planned).
After conversion, every element must satisfy its structural and scalar requirements. Incompatible
shape, unsupported layout, and out-of-range values are located errors; do not expose a partial
packed result. A fixed-width append is validation, not an implicit explicit-conversion request.

<!-- caret-example: planned -->
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

This is required future evidence, not a list of existing passing tests. Supply exact expected
outputs and diagnostic phase/code/physical line/column when each implementation lands.

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
shorthand and dynamic first-class Field construction. Context-selected representations remain planned.

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

<a id="collection-metadata"></a>
#### Collection metadata

<a id="logical-versus-physical-metadata"></a>
##### Logical versus physical metadata

Caret distinguishes between:

1. **semantic/type metadata** — what contracts a value satisfies;
2. **representation metadata** — how a value is physically laid out.

These are related but not identical.

For example:

```caret
(Sequence Number) values
```

guarantees that every element satisfies `Number`.

It does not necessarily imply that every element has the same representation.

The collection may contain:

```caret
[1 2.5 100 3.14]
```

where some values are represented as integers and others as floating-point values.

---

<a id="per-element-metadata"></a>
##### Per-element metadata

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

<a id="shared-collection-metadata"></a>
##### Shared collection metadata

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

<a id="contract-homogeneous-but-representation-heterogeneous-collections"></a>
##### Contract-homogeneous but representation-heterogeneous collections

A common contract does not necessarily provide enough information to remove all per-element metadata.

For example:

```caret
(Sequence Number) values =
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

<a id="packed-collections"></a>
#### Packed collections

<a id="shared-representation"></a>
##### Shared representation

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

<a id="packed-versus-homogeneous"></a>
##### Packed versus homogeneous

A homogeneous semantic contract is weaker than a packed representation.

For example:

```caret
(Sequence Number) values
```

does not imply packed storage.

Even:

```caret
(Collection Int) values
```

does not promise a physical width or layout: `Int` aliases the format-independent `Integer`.

By contrast:

```caret
(Packed Int32) values
```

requires a concrete uniform representation.

Conceptually:

```text
Packed T => Collection
```

but:

```text
Collection !=> Packed T
```

---

<a id="packed-structural-values"></a>
##### Packed structural values

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

<a id="metadata-placement-rule"></a>
##### Metadata placement rule

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

<a id="relationship-to-formats"></a>
#### Relationship to formats

See [Formats and Codecs](10-formats-and-codecs.md#formats-as-specialized-collections).

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

<a id="implementation-requirements"></a>
#### Implementation requirements

The initial implementation should support at minimum:

1. `contract` as the fundamental type-definition mechanism.
2. Base/tag contracts:

```caret
Marker = contract ~
```

3. Contract derivation:

```caret
Readable = contract Marker
```

4. Multiple derivation:

```caret
Writable = contract Marker
ReadWrite = contract [Readable Writable]
```

5. Contracts usable as membership predicates.
6. Ordinary pure predicates used as refinements.
7. Derived refinement contracts:

```caret
PositiveNumber = contract [Number positive]
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
26. Non-empty Collections that are entirely positional or entirely named.
27. A located `MIXED_COLLECTION_SHAPE` diagnostic for mixing Field and unnamed elements.
28. Named fields constructed interchangeably with `^name = value` or `field key value`.
29. One shape-neutral empty Collection satisfying zero-compatible collection contracts vacuously.
30. Exact structural contracts remaining unsatisfied when their required positions or fields are absent.
31. Exported blocks producing named Collections containing only their exported fields.
32. Observational equivalence between exported-block shorthand and explicit named Collections.
33. Imported module exports using the same named-Collection member model.
34. `with` exposing named Collection fields without converting a Collection into a lexical scope.
35. Lexical scopes remaining non-value compiler/evaluator environments.

The initial implementation may postpone:

* sophisticated automatic memory-layout optimization;
* GPU-specific layout attributes;
* structure-of-arrays transformations;
* compressed runtime type tags;
* zero-copy format views;
* advanced generic constraint inference.

These later features must preserve the fundamental distinction between contract membership, element-specific metadata, and shared collection representation.

---

<a id="design-principle"></a>
#### Design principle

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

<a id="templates"></a>
## Templates

<a id="overview"></a>
### Overview

A **template** is a contract describing the structure and contents of a collection.

A template is constructed by calling the ordinary `template` function with either a concrete
collection or a reifiable function produced by a collection expression containing:

* holes;
* contracted holes;
* fixed values;
* named fields;
* nested templates or collections.

Example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

`Point` is a contract describing a two-element collection whose elements both satisfy `Float`.

Therefore:

```caret
Point [10.0 20.0]
```

is true, while:

```caret
Point [10.0 "hello"]
```

is false.

Templates use the ordinary Caret contract system.

They do not introduce a separate type system.

`template specimen` is ordinary whitespace application. There is no `template` parser production,
template-only invocation syntax, or spelling-based semantic rule. Inspection of a collection
constructor descriptor is behavior of the resolved language-owned `template` callable and its
contracts. Aliases retain the same ordinary callable behavior.

---

<a id="template-construction"></a>
### Template construction

The general form is:

```caret
template specimen
```

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

Collection literals are hole-expression boundaries: their holes are materialized as a constructor
function before a surrounding application consumes the literal's value. Because this collection
contains holes, it first becomes a function whose parameters fill those holes and whose result is
the completed collection. The ordinary `template` function then derives a contract from that
function's language-owned collection constructor descriptor.

The supported overloads are conceptually:

```text
template : Collection -> Contract
template : CollectionConstructor -> Contract
```

A concrete collection produces a fixed-only exact template. A `CollectionConstructor` is an
ordinary hole function whose retained descriptor is structurally a collection construction.
`template` is not syntax and creates no exception to ordinary application or hole evaluation.

For example, the compact form above is equivalent to:

```caret
PointConstructor =
  [
    (Float) _
    (Float) _
  ]

Point = template PointConstructor
```

Calling `PointConstructor 10.0 20.0` produces `[10.0 20.0]`; calling
`template PointConstructor` instead derives the corresponding membership contract.

Only reifiable hole functions whose expression directly constructs a collection are accepted
initially. Named functions, opaque/native callables, and partial expressions such as `[transform _]`
whose hole is used in a computed element are rejected with a located
`TEMPLATE_INVALID_CONSTRUCTOR` diagnostic. This restriction avoids attempting general function
inversion. The descriptor is language-owned metadata and must never expose Java AST or runtime
implementation objects.

Template membership is the structural inverse of construction: candidate elements occupy hole
positions, captured values compare equal, and fields and nested collections match recursively. The
constructor is not invoked during membership testing.

The resulting value may be used anywhere an ordinary contract may be used.

The prototype implements this exact structural membership model for concrete Collections and
reifiable collection constructors. Positional and named shape, comparable fixed values,
unconstrained and contracted holes, repeated numbered-hole equality, direct nesting, and dynamic
field keys participate in membership. Dynamic keys and fixed expressions are evaluated once during
constructor creation. Template contracts compose with aliases, null/missing modifiers,
parameterized collection contracts, conservative implication, overload dispatch, and ordinary
contract reflection. Every field remains required during membership. The planned
`TEMPLATE-OPTIONAL-001` rule adds contextual literal completion without weakening that exact
membership rule.

Reflection retains kind `Contract` and adds language-owned `shape`, `size`, and `elements` metadata.
Element metadata identifies its public `id`, constraint kind, zero-based repeated-hole parameter,
and public requirement identifiers without exposing captures, source spans, Java objects, or executable
descriptor internals. Phase 4 adds a Boolean `defaultsMissing` element field so construction
behavior remains observable without exposing source syntax or executable internals.

For example:

```caret
(Point) position
```

or:

```caret
(Collection Point) positions
```

---

<a id="holes"></a>
### Holes

<a id="unconstrained-holes"></a>
#### Unconstrained holes

A plain hole:

```caret
_
```

represents a position whose value may vary freely.

Example:

```caret
Pair =
  template [
    _
    _
  ]
```

matches any two-element collection:

```caret
[1 2]
["a" true]
[person 42]
```

provided the collection has the required shape.

---

<a id="contracted-holes"></a>
#### Contracted holes

Normal Caret contract syntax may constrain a hole:

```caret
(Int) _
```

Example:

```caret
IntegerPair =
  template [
    (Int) _
    (Int) _
  ]
```

matches:

```caret
[10 20]
[-1 42]
```

but not:

```caret
[10 "twenty"]
```

Multiple contracts use the normal Caret contract syntax:

```caret
PositiveIntegerPair =
  template [
    (Int positive) _
    (Int positive) _
  ]
```

No template-specific constraint syntax is required.

<a id="numbered-holes"></a>
#### Numbered holes

Numbered holes retain their ordinary partial-application meaning in collection constructors:

```caret
Diagonal = template [_1 _1]
Swapped = template [_2 _1]
```

Repeated occurrences of the same numbered hole describe the same constructor parameter and
therefore require the corresponding candidate positions to be equal. Numbering and reordering
change the constructor's parameter order, not the collection's structural order. Any contracts on
repeated occurrences must all hold for the shared candidate value.

As with every partial expression, numbered and unnumbered holes may not be mixed.

---

<a id="fixed-values"></a>
### Fixed values

A template element that is not a hole represents a fixed-value requirement.

Example:

```caret
Header =
  template [
    0xCA
    0xFE
    (Int) _
  ]
```

matches:

```caret
[0xCA 0xFE 100]
```

but not:

```caret
[0xCA 0xFF 100]
```

Conceptually, a fixed element:

```caret
42
```

requires:

```text
eq actual 42
```

using the applicable polymorphic equality operation. Non-hole subexpressions are evaluated and
captured eagerly when the collection-producing hole function is created; deriving or testing the
template does not repeat those effects. A fixed template value must support Caret
equality. Template construction fails with a located diagnostic when a fixed value is callable or
otherwise non-comparable; membership testing must not turn such a value into an exceptional
predicate.

Thus a template may combine exact-value requirements and contract requirements.

---

<a id="template-semantics"></a>
### Template semantics

For:

```caret
T =
  template [
    fixed
    (Contract) _
    _
  ]
```

a candidate collection satisfies `T` when:

1. it has the required collection shape;
2. the first element equals `fixed`;
3. the second element satisfies `Contract`;
4. the third element exists but is otherwise unrestricted.

Conceptually:

```text
T value =
    Collection value
    and shape value == templateShape
    and eq value[0] fixed
    and Contract value[1]
```

with no additional value constraint on `value[2]`.

The compiler may perform these checks statically when sufficient information is known.

Runtime validation is required only where the contract cannot be proven statically.

---

<a id="shape"></a>
### Shape

A template describes collection **shape** as well as element constraints.

A non-empty template is structurally either positional or named, following the ordinary Collection
rule. A Collection specimen supplied to `template` cannot mix named Field elements with unnamed
positions; doing so produces
`MIXED_COLLECTION_SHAPE`. There is no separate scope-template model. The specimen `[]` describes
the exact empty shape. Every explicit non-empty template requires all declared positions and all
named fields not explicitly designated optional by the general semantics below.

For a positional collection:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

the shape includes:

```text
element count: 2

position 0:
    Float hole

position 1:
    Float hole
```

Therefore a three-element collection does not satisfy `Point`:

```caret
Point [1.0 2.0 3.0]
```

is false.

Likewise:

```caret
Point [1.0]
```

is false.

For positional templates, element count and position are part of the template contract.

---

<a id="named-fields"></a>
### Named fields

Templates may describe named structured collections using ordinary `^` fields.

Example:

```caret
Person =
  template [
    ^name = (String) _
    ^age = (Int positive) _
    ^active = true
  ]
```

A matching value may be:

```caret
[
  ^name = "Alice"
  ^age = 42
  ^active = true
]
```

The template requires:

```text
name:
    satisfies String

age:
    satisfies Int
    satisfies positive

active:
    equals true
```

Field names are part of the template structure. For the initial exact model, named shape means the
exact set of field names and the field ordering defined by the universal collection model. Missing,
additional, or reordered fields are incompatible whenever that ordering is observable for the
candidate collection.

Every field declared by a template must be present in a matching Collection. Phase 4 contextual
construction may create that present field from an omission in a named Collection literal. A field
is eligible only when its hole clause contains a directly written `T~` or `T?~` term and the full
conjunction accepts missing. The constructed field receives value `~`; `T?~` additionally permits
an explicitly supplied null but never changes the omission default to null.

The direct suffix is construction metadata, not an accepted-set inference. An alias whose resolved
contract accepts missing permits an explicitly supplied `~`, but does not make omission defaultable.
A fixed `~` element, an unconstrained hole, or a clause whose remaining requirements reject missing
does not enable omission. No separate optional-member syntax is introduced.

For example, using existing template syntax:

```caret
Person = template [
  ^name = (String) _
  ^phone = (String~) _
  ^nickname = (String?~) _
]

(Person) ada = [^name = "Ada"]
```

The annotated initializer supplies one unambiguous expected template, so `ada` contains explicit
`phone = ~` and `nickname = ~` fields. Both fields participate normally in enumeration, access,
reflection, equality, and subsequent membership. A numeric or null `phone` does not satisfy
`String~`; an explicit null `nickname` does satisfy `String?~`.

Expected-template context propagates through an annotated binding initializer, an argument to a
statically known template-constrained parameter, a declared function result, a recursively expected
nested literal, and exported-block shorthand. Dynamic template keys are resolved once when the
template is created and supply the corresponding expected field names. Context is available only
when analysis identifies one template shape unambiguously. Competing overload/template shapes do
not insert fields to decide their own selection.

Only Collection constructors receive this context. Ordinary template application remains a pure
Boolean membership predicate, and explicit conversion requires the source's established exact
shape even when its operand is written as a literal:

<!-- caret-example: planned -->
```caret
candidate = [^name = "Ada"]

Person candidate             // false
(Person) candidate           // contract violation
(Person) [^name = "Ada"]     // contract violation: explicit conversion
```

The candidate is not mutated and no field is synthesized. Undeclared fields remain incompatible.
For an omitted required or nondefaultable field, report `CONTRACT_VIOLATION` at the literal and
retain the template field declaration as related context. Explicit field initializers keep ordinary
evaluate-once and source-order behavior; inserting `~` evaluates no expression, and the completed
Dictionary retains its ordinary canonical field ordering.

Reflected element metadata sets `defaultsMissing` to true exactly for an eligible named field hole
and false otherwise. Contract membership and implication compare accepted values and exact shape;
they ignore this construction-only flag. `RuleDefinition` uses the same general mechanism for
directly supplied definition literals.

The template system does not require a separate record-schema syntax.

---

<a id="dynamic-fields"></a>
### Dynamic fields

Templates may use ordinary field values where dynamic keys are required.

For example, where appropriate:

```caret
template [
  field key (String) _
]
```

uses the same first-class field mechanism as ordinary collection construction. The key expression
is evaluated exactly once when the collection or hole function is created. It must produce a valid field name, and
duplicate resolved names are located template-construction diagnostics.

Templates do not introduce another dictionary representation.

---

<a id="template-construction-diagnostics"></a>
### Template construction diagnostics

Templates reuse ordinary language diagnostics when construction fails in an ordinary contract,
field, or hole mechanism. The stable mapping is:

* `TEMPLATE_INVALID_CONSTRUCTOR` when `template` receives a callable that is not an eligible,
  reifiable collection constructor;
* `TEMPLATE_NONCOMPARABLE_FIXED_VALUE` when a captured fixed value does not support Caret equality;
* `INVALID_DYNAMIC_FIELD_NAME` when a dynamic key does not produce a valid field name;
* `DUPLICATE_FIELD` when two elements resolve to the same field name;
* the ordinary contract diagnostic, including `UNKNOWN_CONTRACT`, `NOT_A_CONTRACT`, or
  `PARSE_INVALID_CONTRACT` as appropriate, when a contracted hole has an invalid requirement; and
* `MIXED_HOLE_STYLES` when numbered and unnumbered holes are mixed.

The code describes the failure independently of when it becomes knowable. Malformed syntax retains
phase `PARSER`, a well-formed failure established by analysis uses phase `SEMANTIC`, and a failure
that depends on a dynamically obtained value uses phase `RUNTIME`. Where the same behavioral code
can arise during analysis or evaluation, those phases share that code. The invalid constructor,
fixed value, dynamic key, contract term, or hole is the primary location. For `DUPLICATE_FIELD`, the
later field is primary and the first field with that resolved name is a related location.

---

<a id="nested-templates"></a>
### Nested templates

Templates may contain nested collection structure.

Example:

```caret
Person =
  template [
    ^name = (String) _

    ^position =
      [
        (Float) _
        (Float) _
      ]
  ]
```

Within an eligible collection-constructor descriptor, a bare nested collection contributes
recursively to the outer template shape; it is not a fixed-value equality requirement.
Non-collection expressions that are neither holes nor contracted holes remain fixed-value
requirements.

A matching value is:

```caret
[
  ^name = "Alice"
  ^position = [
    10.0
    20.0
  ]
]
```

The implementation should recursively derive structural constraints from nested collection values.

Where a reusable nested contract is preferable, an explicitly defined template may be used:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

Person =
  template [
    ^name = (String) _
    ^position = (Point) _
  ]
```

Both forms participate in the ordinary contract system.

---

<a id="templates-are-contracts"></a>
### Templates are contracts

The result of `template` is a normal Caret contract.

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

may be used as:

```caret
(Point) p
```

or:

```caret
(Collection Point) points
```

or as part of another contract:

```caret
VisiblePoint =
  contract Point visible
```

Templates therefore participate in:

* contract derivation;
* parameter contracts;
* return contracts;
* collection element contracts;
* polymorphic function dispatch;
* runtime contract checks;
* static contract inference.

No separate "template type" mechanism is required.

---

<a id="template-derivation"></a>
### Template derivation

Templates may participate in ordinary derived contracts.

Example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

NonZeroPoint =
  contract Point nonZero
```

Conceptually:

```text
NonZeroPoint value
    =
Point value
and nonZero value
```

Likewise, a structural template may derive from or be combined with other collection-related contracts.

---

<a id="collections-of-template-shaped-values"></a>
### Collections of template-shaped values

Templates are particularly useful as common metadata for homogeneous structural collections.

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

(Collection Point) points =
  [
    [1.0 2.0]
    [3.0 4.0]
    [5.0 6.0]
  ]
```

Every element has the same template shape.

An implementation may avoid repeating the full structural metadata for every point.

Conceptually:

```text
collection metadata:

    element contract:
        Point

    Point shape:
        two Float positions

values:

    [1.0 2.0]
    [3.0 4.0]
    [5.0 6.0]
```

The template may be stored once as common collection metadata.

---

<a id="templates-and-metadata"></a>
### Templates and metadata

A template may describe more precise structural metadata than a broad element contract.

For example:

```caret
(Sequence Number) values
```

only establishes that every element satisfies `Number`.

By contrast:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

establishes:

* collection arity;
* element positions;
* element contracts;
* structural nesting;
* named fields where present;
* fixed values where present.

A collection whose elements all satisfy the same template can therefore share this metadata.

The optimization permission is:

> When every element of a collection has the same template shape, the template may serve as shared element metadata for the entire collection.

The physical metadata representation remains an implementation detail. Sharing must not change
value identity, structural equality, reflection, evaluation order, or any other observable behavior.

---

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
