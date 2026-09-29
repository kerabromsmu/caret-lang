# Collection protocol

[Chapter index](../06-collections-fields-and-templates.md) · [Language specification index](../../LANGUAGE.md)


<a id="phase-4-collection-protocol-revision-planned"></a>
<a id="phase-4-collection-protocol-revision-partially-implemented"></a>
## Phase 4 Collection protocol revision (implemented with deferred extensions)

This section records the decisions from issues #55 and #59 and their joint design discussion.
The common `keys`/`values`/`fields`/`size` protocol, Boolean-or-missing guarantee queries,
matching reflection fields, `Natural`, contextual Field/Set/Dictionary/keyless shapes, first-key
settlement, unified access, lazy shape-aware transforms, strict consumers, paired construction,
revised Collection equality, shape-neutral empty facts, containers, scoped lookup,
collection-value `eager`, expected-template completion for named literals, and selected packed
layouts are implemented. Later extensions are identified below. For the subjects covered here,
this revision supersedes earlier target semantics: required dot access, scalar-only dynamic keys,
eager transforms, a distinct non-Collection Field representation, and earlier collection-equality
assumptions. Examples for deferred extensions remain conceptual/planned.

### Scope and custom-provider deferral

Phase 4 includes the common protocol, built-in lazy transforms, collection-value `eager`,
and their contracts, effects, reflection, and diagnostics. Public custom-provider construction,
its registration/constructor syntax, general computations, concurrency/synchronization, resumable
failure handling, callable forms of `eager`, and context-dependent template invocation are deferred.
Phase 4 does include expected-template completion of named Collection literals as specified under
[named fields](06-06-templates-foundations.md#named-fields); that rule does not change ordinary template application into a
constructor.
Built-in laziness must not depend on exposing those deferred APIs.

Public `addElement`, `removeElement`, and `replaceElement`, their construction-selection
interface, and additional immutable-update syntax are also deferred beyond Phase 4, with no later
phase assigned. Their design is retained below. Internal construction and settlement, existing
persistent primitives, and mutable containers with `put` remain in scope.

The implemented Dictionary-order, zip construction, with-binding, and Field-access decisions below
supersede the former open items.

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
[lazy-value rules](../02-values-bindings-and-evaluation.md#planned-lazy-values-and-lexical-contexts):
first access establishes a value in the accessing lexical context; inherited values stay shared;
a fresh invocation can obtain different results. No special context is introduced by `eager`,
reflection, or a handler. A sequential provider must uphold its stronger cross-invocation
stability guarantee.

Providers determine the relationship between enumerated keys and access: the language does not
require enumeration to list every accessible key. Duplicate enumerated keys count once, retaining
their first position. Thus materializing enumerated content need not preserve access to omitted
keys. Provider APIs for custom construction are deferred; built-in protocol support is not.

### Lookup and keys

These implemented expressions use the same access operation:

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
Parser and interaction tests cover Collection literal boundaries, ordinary and numbered holes,
reused holes, lexical shadowing, source order, and fixed-operand effects.

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

The prototype implements this boundary internally for literals and structural collection
constructors. It evaluates literal expressions eagerly, interprets Field parts under the selected
result contract, retains the first equal key, and publishes only the settled value. No builder or
unfinished Collection is exposed as a Caret value.

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

The built-in `zip` and `zipWithKeys` functions now implement this section. Their lazy results use
the same incremental provider foundation as transforms, and Dictionary context selects sorted
homogeneous construction without changing the default general keyed result.

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

The built-in `map`, `filter`, `fold`, `any`, and `all` now implement this section for the built-in
Collection representations. Lazy transform results establish demanded entries incrementally and
retain first output keys; public custom providers and automatic parallel evaluation remain deferred.

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

Implemented Phase 4 `eager value` materializes Collection values. It follows ordinary lexical evaluation;
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
The current callable signature advertises a conservative upper bound containing all current
observable effects, so a function with a narrower declared allowance cannot invoke `eager` even
when a particular supplied value would be pure. Traversal itself uses ordinary provider and
callable operations, so demanded effects are still subject to those call boundaries.

### Collection equality (forcing policy provisional)

The built-in equality operators now implement this policy for eager and lazy Collection providers.
Equality enumerates incrementally so a mismatch does not demand later entries.

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
