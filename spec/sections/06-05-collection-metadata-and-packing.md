# Collection metadata and packing

[Chapter index](../06-collections-fields-and-templates.md) · [Language specification index](../../LANGUAGE.md)


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

See [Formats and Codecs](../10-formats-and-codecs.md#formats-as-specialized-collections).

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
