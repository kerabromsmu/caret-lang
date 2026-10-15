# Caret language specification

This document is the normative index for the Caret language specification. Together with the
linked documents in [`spec/`](spec/), it forms the canonical language specification. A rule is
normative regardless of which document owns it; summaries in other documents defer to the linked
owner when wording differs.

Caret is an experimental concise programming language. Its syntax removes routine punctuation and
boilerplate while retaining clear, predictable, statically analyzable semantics. The current Java
21 tree-walking interpreter implements a growing subset of this specification.

## Status and supporting documents

- [`CONFORMANCE.md`](CONFORMANCE.md) is the authoritative implemented/planned/deferred status matrix
  and points to automated evidence.
- [`PLAN.md`](PLAN.md) owns release targets and dependency-ordered implementation chunks; phase
  identifiers do not determine release numbers or execution order.
- [`README.md`](README.md) describes the current prototype and how to run it.
- [`WEB_INTRODUCTION.md`](WEB_INTRODUCTION.md) is a public-facing introduction, not a normative source.

Unless a section explicitly says otherwise, specification text describes the intended language.
“Implemented” means the current prototype has automated evidence in the conformance matrix;
“planned” means normative but not yet implemented; “deferred” means explicitly postponed by its
owning specification. Release allocation is separate from implementation status: a planned feature
may target a later release, and a requirement spanning several releases remains incomplete until
all its behavior has evidence. A release implements its selected subset, not the whole corpus.

## First-class by default

**Everything in Caret is first-class by default.** New language features should integrate with
ordinary values, functions, contracts, and the established lazy-evaluation model. Language entities
should support binding, passing, returning, and storing as values, and composition where applicable.
Designs should reuse these mechanisms and avoid unnecessary syntax or separate conceptual systems,
so developers can apply a small, consistent set of concepts throughout the language. A special case
is permitted only when it cannot be avoided; its owning specification must explain why.

The [function model](spec/03-functions-operators-and-lambdas.md#function-application),
[first-class contracts](spec/04-contracts-inference-and-dispatch.md#contract-foundation-currently-implemented),
and [lazy-value rules](spec/02-values-bindings-and-evaluation.md#planned-lazy-values-and-lexical-contexts)
provide the foundation. Optional syntax sugar may provide compact notation while preserving the
underlying semantics. Evaluation demand, effect ordering, visibility, and authority continue to
follow their owning specifications; this principle does not make every entity callable or every
operation lazy, or grant additional authority.

Explicitly specified exceptions include lexical scopes and resolver-only paths such as `outer.name`,
which remain internal name-resolution mechanisms to preserve lexical privacy and visibility
boundaries. Implementation limitations, such as the deferred first-class nullary callable-reference
design, remain identified as limitations rather than becoming permanent exceptions to this
principle. Adding this design rule does not itself implement deferred behavior.

## Core invariants

1. Whitespace performs function application and indentation defines blocks.
2. Missing (`~`) and present null (`?`) are distinct states.
3. Expected dynamic failures return missing or structured results where specified rather than
   leaking host exceptions.
4. Lexical scopes are name-resolution environments, not first-class values. Exported blocks produce
   named Collections containing only explicitly exported bindings.
5. Reflection uses language-owned descriptors and is relative to the visible execution environment.
6. Effect declarations describe observable behavior but grant no authority.
7. Sandbox, module, reflection, staging, and capability boundaries never amplify authority.
8. Immutable and functional programming are supported without prohibiting explicit contained
   mutability.
9. Specialized semantic values do not require specialized construction syntax. `contract`,
   `template`, `format`, `rule`, `cycle`, and `sandbox` are ordinary Caret callable bindings. Their
   lookup, application, arity, partial application, aliases, contracts, effects, reflection, and
   staging follow the ordinary function model; specialized behavior belongs to the resolved
   language-owned callable identity and to the values it consumes or produces, not to its lexical
   spelling.
10. A contextual named Collection literal governed by one unambiguous structural template still
    produces every declared field. An omitted field whose hole directly uses `T~` or `T?~` is
    materialized with value `~` when the full field clause accepts missing; template membership and
    explicit conversion never synthesize fields in an already established Collection.

This invariant does not imply that every language facility is a function. Established syntax such
as `->`, `[...]`, container braces, contract clauses, `#`, `@`, `$`, layout markers, and module-ID
declarations remains syntax where its owning specification says so.

## V1 feature work

The [release roadmap](PLAN.md#release-targets-and-implementation-order) owns sequencing and release
completion. Alongside the implemented baseline and the planned cycle/module/environment/sandbox
phases, v1 includes the eight labeled cards below at their recorded scope. Allocation is separate
from design readiness and implementation status; open questions remain open.

| Card | V1 scope and owning documentation |
|---|---|
| [#5 — Visible identifiers](https://github.com/kerabromsmu/caret-lang/issues/5) | [Environment-relative root identifier metadata](spec/12-modules-reflection-and-code.md#planned-v1-visible-identifiers), including visible imported bindings; semantic Code remains later work. |
| [#53 — Integer shifts](https://github.com/kerabromsmu/caret-lang/issues/53) | [Basic `shl`/`shr` design](spec/02-values-bindings-and-evaluation.md#planned-v1-integer-shifts); contracts and shift semantics remain unresolved, with rotations/width-dependent extensions outside this v1 portion. |
| [#57 — Tail calls](https://github.com/kerabromsmu/caret-lang/issues/57) | [Language-wide tail-call specification work](spec/03-functions-operators-and-lambdas.md#planned-v1-tail-calls); guarantees, tail positions, coverage, and diagnostics remain to be designed. |
| [#61 — Template sugar](https://github.com/kerabromsmu/caret-lang/issues/61) | [Planned `<[ … ]>` notation](spec/sections/06-06-templates-foundations.md#planned-template-sugar), preserving the approved ordinary lexical application semantics. |
| [#80 — Turing-completeness proof](https://github.com/kerabromsmu/caret-lang/issues/80) | [Proof/example work](roadmap/phase-13.md#v1-proof-and-example-work): one or more Caret programs supporting the requested proof; no proof construction or new runtime guarantee is specified here. |
| [#81 — Multiline REPL](https://github.com/kerabromsmu/caret-lang/issues/81) | [Planned submission rules](spec/01-source-layout-and-diagnostics.md#planned-multiline-repl-submissions) shared by interactive and plain input. |
| [#109 — File/console I/O](https://github.com/kerabromsmu/caret-lang/issues/109) | [Bounded synchronous I/O subset](spec/05-effects-and-callable-signatures.md#planned-v1-file-console-io) of #99; public API and remaining semantics must be specified before implementation. |
| [#110 — Seeded generation](https://github.com/kerabromsmu/caret-lang/issues/110) | [Basic seeded pseudorandom generation](spec/05-effects-and-callable-signatures.md#planned-v1-seeded-random-generation), compatible with #98; API, algorithm, state, effects, and contracts remain unresolved. |

These entries record the cards' current detail without adding syntax, APIs, or semantic decisions.
The full I/O and randomness designs (#99/#98) remain later work, and #60 gated computations retain
undecided release placement. Existing specification/design cards remain specification work, and
#80 remains a proof/example deliverable.

## Specification documents

Read the documents in this order for a linear introduction, or follow their cross-links by feature.

1. [Source, Layout, and Diagnostics](spec/01-source-layout-and-diagnostics.md)
2. [Values, Bindings, and Evaluation](spec/02-values-bindings-and-evaluation.md)
3. [Functions, Operators, and Lambdas](spec/03-functions-operators-and-lambdas.md)
4. [Contracts, Inference, and Dispatch](spec/04-contracts-inference-and-dispatch.md)
5. [Effects and Callable Signatures](spec/05-effects-and-callable-signatures.md)
6. [Collections, Fields, and Templates](spec/06-collections-fields-and-templates.md)
7. [State, Containers, and Scoped Lookup](spec/07-state-containers-and-scoped-lookup.md)
8. [Cycles](spec/08-cycles.md)
9. [SIMD](spec/09-simd.md)
10. [Formats and Codecs](spec/10-formats-and-codecs.md)
11. [Rules, Rulesets, and Objects](spec/11-rules-rulesets-and-objects.md)
12. [Modules, Reflection, and Code](spec/12-modules-reflection-and-code.md)
13. [Sandboxes and Security](spec/13-sandboxes-and-security.md)
14. [Staging, Compilation, and Compatibility](spec/14-staging-compilation-and-compatibility.md)

The [section migration manifest](spec/SECTION_MANIFEST.md) records where every section of the former
monolithic specification moved.

## Normative ownership of cross-cutting rules

- Collection shapes, fields, structural templates, and collection metadata are owned by the
  [collections specification](spec/06-collections-fields-and-templates.md).
- Callable effect bounds, purity, and effect catalogs are owned by the
  [effects specification](spec/05-effects-and-callable-signatures.md).
- Container identity, reads, writes, and scoped member lookup are owned by the
  [state specification](spec/07-state-containers-and-scoped-lookup.md).
- Module identity, import visibility, and semantic code values are owned by the
  [modules specification](spec/12-modules-reflection-and-code.md).
- Capability projection and authority boundaries are owned by the
  [sandbox specification](spec/13-sandboxes-and-security.md), including the implemented
  [Java embedding boundary](spec/13-sandboxes-and-security.md#java-embedding).

## Legacy major-section links

These links replace the major anchors of the former monolithic document:

- [Values](spec/02-values-bindings-and-evaluation.md#values)
- [Functions and application](spec/03-functions-operators-and-lambdas.md#bindings-and-functions)
- [Contracts](spec/04-contracts-inference-and-dispatch.md#contract-foundation-currently-implemented)
- [Collections](spec/sections/06-03-collection-baseline.md#collections-and-lexical-scopes)
- [Effects](spec/05-effects-and-callable-signatures.md#purity-and-effects)
- [SIMD](spec/09-simd.md#simd)
- [Formats](spec/10-formats-and-codecs.md)
- [Lambdas](spec/03-functions-operators-and-lambdas.md#lambda-functions)
- [Cycles](spec/08-cycles.md#cycles)
- [Rules and rule cycles](spec/11-rules-rulesets-and-objects.md)
- [Modules and imports](spec/12-modules-reflection-and-code.md#planned-modules-and-compilation)
- [`@root`, code, and quines](spec/12-modules-reflection-and-code.md#root-program-reification-quines-and-sandboxes)
- [Sandboxes](spec/13-sandboxes-and-security.md#sandboxes)
- [Templates](spec/sections/06-06-templates-foundations.md#templates)
- [Mutability containers](spec/sections/07-01-containers-core.md#mutability-containers)
- [`with`, `outer`, and `$`](spec/sections/07-03-with-and-outer.md#with-outer-and-low-precedence-application)
- [Compile-time execution](spec/sections/14-01-compile-time-execution.md#compile-time-execution-and-separate-compilation)
- [Compiler compatibility](spec/sections/14-05-compatibility.md#compiler-target-and-compatibility)

## Approved Phase 4 numeric and packed additions

The [numeric revision](spec/02-values-bindings-and-evaluation.md#phase-4-numeric-values-and-arithmetic-implemented),
[numeric contracts and explicit conversion](spec/04-contracts-inference-and-dispatch.md#phase-4-numeric-contracts-implemented),
[conversion syntax and div](spec/03-functions-operators-and-lambdas.md#phase-4-conversion-syntax-and-div-implemented),
and [packed layouts](spec/sections/06-02-packed-layouts.md#phase-4-packed-layouts-implemented)
record the approved #77/#78 design and its numeric (#82) and conversion (#83) prerequisites. Numeric
domains, literals, arithmetic, warnings, explicit conversion, and contiguous packed layouts are
implemented. Parenthesized contract application produces a converted value.
Declarations and directly contracted holes remain checks. The shared
[acceptance matrix](spec/sections/06-02-packed-layouts.md#packed-and-prerequisite-acceptance-matrix)
records the required implementation evidence. `@packed.elementContract` exposes only a visible
semantic contract; physical layouts remain private.

## Deferred specification work

The [Phase 4 Collection protocol revision](spec/sections/06-01-collection-protocol.md#phase-4-collection-protocol-revision-implemented-with-deferred-extensions)
records the joint #55/#59 decisions and their explicit precedence over affected legacy target behavior.
Its common enumeration, size, guarantee, reflection, `Natural`, contextual Field/Set/Dictionary
shape, settlement, unified access, lazy transforms, strict consumers, paired construction, revised equality,
and empty-Collection foundation
is implemented; the remaining integration
points are planned. General
[lazy-value evaluation](spec/02-values-bindings-and-evaluation.md#planned-lazy-values-and-lexical-contexts)
belongs to the evaluation specification; deferred
[resumable failure handling](spec/05-effects-and-callable-signatures.md#deferred-failure-handling-and-computations)
belongs to the effects specification. Those sections distinguish Phase 4 work from later designs;
none changes the prototype's implementation status by documentation alone.

Deferred work remains documented in the feature document that owns the affected semantics. The
current implementation and deferred inventory are tracked in [`CONFORMANCE.md`](CONFORMANCE.md);
release boundaries and future implementation sequencing belong in [`PLAN.md`](PLAN.md).
