<a id="formats-and-codecs"></a>
# Formats and Codecs

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)


<a id="overview"></a>
## Overview

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

<a id="formats-as-relations"></a>
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

<a id="format-as-a-first-class-value"></a>
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

Format construction uses ordinary Caret functions rather than special grammar for format features.
In particular, `format` is an ordinary nullary function, conceptually:

```text
format : [] -> Format
```

Bare `format` produces an appropriate new empty Format through Caret's normal implicit invocation
of named nullary functions. It is neither an empty-format literal nor the empty Format value itself,
and the parser does not special-case its spelling. Each result follows the ordinary identity and
equality semantics of Format values; this nullary-call rule does not otherwise redefine them.

For example:

```caret
Packet =
  format
  >> constant "PACK"
  >> field u16be "length"
  >> field u8 "type"
  >> field (bytes length) "payload"
```

The first pipeline value is the empty Format returned by that ordinary nullary call.

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

Together with `decode` and `encode`, they are ordinary Caret functions, whether supplied as
library-level bindings or standard format primitives, rather than separate parser constructs.

---

<a id="formats-as-specialized-collections"></a>
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

<a id="format-composition"></a>
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

<a id="primitive-formats"></a>
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

<a id="fields"></a>
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
[
  ^x = 10.0
  ^y = 20.0
]
```

and encodes such a value back into the corresponding representation.

Field names are ordinary strings.

No separate name-literal syntax is required.

---

<a id="references-to-earlier-fields"></a>
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

<a id="constant-representation-elements"></a>
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

<a id="repeated-formats"></a>
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

<a id="conditional-formats"></a>
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

<a id="general-choices-and-format-selection"></a>
## General choices and format selection

Caret has a general choice expression. It is also used by formats to describe alternatives based on
data patterns or discriminators:

```caret
kind ==
  1 & TextMessage
  2 & ImageMessage
  3 & FileMessage
  ! UnknownMessage
```

The selector is evaluated once. Case labels are evaluated and compared from top to bottom using
ordinary equality; only the selected result expression is evaluated. The optional `!` fallback is
unique and must be last. A choice with no matching case and no fallback evaluates to `~`.
Statically recognizable duplicate labels are diagnostics.

The semantic requirement is more important:

* decoding may use representation data to determine which alternative applies;
* encoding may use the logical value to determine which representation and discriminator are required.

Where the relationship is deterministic in both directions, the user should not have to write separate selection logic for encoding and decoding.

For a format, deterministic literal cases may derive the encoded discriminator. A fallback may not
invent a discriminator: it must use one already known from the logical value or produce the
structured format mismatch defined by the eventual format-result model.

Pattern matching in formats should therefore be treated relationally where practical.

---

<a id="format-constraints"></a>
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

<a id="automatic-bidirectionality"></a>
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

<a id="explicit-codecs"></a>
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

<a id="codec-composition"></a>
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

<a id="representation-transformations-versus-logical-transformations"></a>
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

<a id="purity-of-format-definitions"></a>
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

<a id="decode-and-encode-operations"></a>
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

<a id="failure"></a>
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

Each failure payload satisfies the standard `ErrorTemplate`; a format-specific exact template
describes its `details` field. Both operations return `Result ValueContract`, as defined in the
structured-error section. A successful decode or encode places its logical value or representation
in `value`; an expected mismatch or other format failure places the structured error in `error`.

Errors should be capable of carrying useful information such as:

* format component;
* field name;
* representation position;
* expected condition;
* actual value;
* nested error cause.

---

<a id="canonical-representations-and-round-trips"></a>
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

<a id="relationship-to-caret-collections"></a>
## Relationship to Caret collections

Formats decode into ordinary Caret values.

Structured formats should normally produce heterogeneous collections containing ordinary fields.

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
[
  ^length = 128
  ^type = 2
  ^payload = payloadBytes
]
```

The format subsystem must not introduce a separate object model for decoded data.

The same value may therefore:

* be created directly using collection syntax;
* be decoded from a binary format;
* be encoded into another format;
* be passed through ordinary Caret functions;
* satisfy contracts;
* participate in collection operations;
* be inspected through reflection.

---

<a id="formats-are-independent-of-transport"></a>
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

<a id="extensibility"></a>
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

<a id="reflection"></a>
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

<a id="implementation-requirements"></a>
## Implementation requirements

The initial implementation should support at minimum:

1. A first-class immutable `Format` value.
2. The ordinary nullary `format` function returning an empty `Format`.
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

11. Decoding structured formats into ordinary Caret collection values.
12. Encoding ordinary compatible collection values.
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

<a id="design-principle"></a>
## Design principle

The central principle is:

> A Caret format describes the relationship between a logical value and its representation, not separate procedures for reading and writing it.

Where the relationship is structurally reversible, Caret derives both directions from one description.

Where reversal requires algorithms that cannot be inferred, the format explicitly contains both directional functions:

```caret
codec decode encode
```

Complex formats are built from smaller bidirectional relations using ordinary Caret functions, collections, contracts, partial application, and composition.
