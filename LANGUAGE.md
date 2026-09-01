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
- [`PLAN.md`](PLAN.md) is the dependency-ordered implementation roadmap.
- [`README.md`](README.md) describes the current prototype and how to run it.
- [`WEB_INTRODUCTION.md`](WEB_INTRODUCTION.md) is a public-facing introduction, not a normative source.

Unless a section explicitly says otherwise, specification text describes the intended language.
“Implemented” means the current prototype has automated evidence in the conformance matrix;
“planned” means normative but not yet implemented; “deferred” means intentionally outside the
initial implementation target.

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

This invariant does not imply that every language facility is a function. Established syntax such
as `->`, `[...]`, container braces, contract clauses, `#`, `@`, `$`, layout markers, and module-ID
declarations remains syntax where its owning specification says so.

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
- [Collections](spec/06-collections-fields-and-templates.md#collections-and-lexical-scopes)
- [Effects](spec/05-effects-and-callable-signatures.md#purity-and-effects)
- [SIMD](spec/09-simd.md#simd)
- [Formats](spec/10-formats-and-codecs.md)
- [Lambdas](spec/03-functions-operators-and-lambdas.md#lambda-functions)
- [Cycles](spec/08-cycles.md#cycles)
- [Rules and rule cycles](spec/11-rules-rulesets-and-objects.md)
- [Modules and imports](spec/12-modules-reflection-and-code.md#planned-modules-and-compilation)
- [`@root`, code, and quines](spec/12-modules-reflection-and-code.md#root-program-reification-quines-and-sandboxes)
- [Sandboxes](spec/13-sandboxes-and-security.md#sandboxes)
- [Templates](spec/06-collections-fields-and-templates.md#templates)
- [Mutability containers](spec/07-state-containers-and-scoped-lookup.md#mutability-containers)
- [`with`, `outer`, and `$`](spec/07-state-containers-and-scoped-lookup.md#with-outer-and-low-precedence-application)
- [Compile-time execution](spec/14-staging-compilation-and-compatibility.md#compile-time-execution-and-separate-compilation)
- [Compiler compatibility](spec/14-staging-compilation-and-compatibility.md#compiler-target-and-compatibility)

## Deferred specification work

Deferred work remains documented in the feature document that owns the affected semantics. The
current deferred inventory and initial implementation boundary are tracked in
[`CONFORMANCE.md`](CONFORMANCE.md); future implementation sequencing belongs in [`PLAN.md`](PLAN.md).
