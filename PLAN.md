# Complete Caret Language Implementation Plan

## Goal and completion criteria

Implement every behavior described by the canonical specification corpus indexed by `LANGUAGE.md`
in dependency order while preserving Caret's
compact syntax, missing/null distinction, lexical scoping, immutable-first semantics, reflection,
and located diagnostics. “Complete” means each normative language feature has:

- a settled, non-contradictory specification in `LANGUAGE.md`;
- separated lexer/parser/AST/analysis/runtime or backend implementation;
- positive, negative, edge-case, diagnostic, and interaction tests;
- a runnable `.caret` example exercised by the integration suite; and
- matching behavior in the tree-walking interpreter and compiled execution where applicable.

The existing prototype already implements scalar values, `?` and `~`, bindings and functions,
closures, indentation-based bodies, whitespace application, conditionals, Boolean/arithmetic
operators, exported named Collections, partial application and numbered holes, static/optional/dynamic lookup,
basic reflection, Unicode text primitives, persistent sequences/dictionaries, Caret-native tests,
located diagnostics (including rendered related spans), guarded callable invocation, and the REPL.
Logical-line construction is owned by the lexer, and an initial semantic-validation pass diagnoses
duplicate declarations and parameters before block execution. These remain the compatibility
baseline.

## Cross-cutting architecture

- Keep source text, tokens/layout, AST, name resolution, contracts/types/effects, lowering, runtime
  values, evaluation, reflection, diagnostics, and CLI/backend orchestration as separate layers.
- Replace ad-hoc runtime-only validation incrementally with a semantic-analysis result attached to
  source-spanned AST nodes. The tree walker consumes the analyzed/lowered form; the compiler backend
  consumes the same form so semantics cannot drift.
- Give every public value kind a language-owned descriptor and reflective view. Never use Java
  reflection as Caret reflection.
- Preserve the [ordinary-callable construction model](spec/03-functions-operators-and-lambdas.md#function-application)
  for `contract`, `template`, `format`, `rule`, `cycle`, and `sandbox`. Lookup, aliases, shadowing,
  arity, partial application, dispatch, effects, reflection, and staging use ordinary function rules.
  Specialized analysis and lowering recognize resolved language-owned callable identities, never
  lexical spellings; these bindings introduce no feature-specific application or declaration grammar.
- Pass an explicit execution environment through interpretation, imports, tests, REPL sessions, and
  compiled entry points. Reflection and authority are always relative to that environment.
- Assign stable diagnostic phase/code/span data before expanding error messages. All new syntax and
  semantic failures must be testable without matching vague prose.
- Maintain a feature conformance table mapping every normative `LANGUAGE.md` requirement to its
  implementation issue, tests, example, and status. A stage is complete only when its rows pass.


## Phase index

- <a id="phase-0-specification-and-conformance-baseline-completed"></a> [Phase 0 — Specification and conformance baseline (completed)](roadmap/phase-00.md#phase-0-specification-and-conformance-baseline-completed)
- <a id="phase-1-front-end-binding-semantics-and-unified-callables"></a> [Phase 1 — Front end, binding semantics, and unified callables](roadmap/phase-01.md#phase-1-front-end-binding-semantics-and-unified-callables)
- <a id="phase-2-types-contracts-effects-and-ownership-foundation"></a> [Phase 2 — Types, contracts, effects, and ownership foundation](roadmap/phase-02.md#phase-2-types-contracts-effects-and-ownership-foundation)
- <a id="phase-3-lambdas-and-higher-order-programming-completed"></a> [Phase 3 — Lambdas and higher-order programming (completed)](roadmap/phase-03.md#phase-3-lambdas-and-higher-order-programming-completed)
- <a id="phase-4-universal-collections-fields-and-mutability-containers-completed"></a> [Phase 4 — Universal collections, fields, and mutability containers (completed)](roadmap/phase-04.md#phase-4-universal-collections-fields-and-mutability-containers-completed)
- <a id="phase-5-self-hosting-foundation-and-ordinary-cycles"></a> [Phase 5 — Self-hosting foundation and ordinary cycles](roadmap/phase-05.md#phase-5-self-hosting-foundation-and-ordinary-cycles)
- <a id="phase-6-simd-values-and-lifted-execution"></a> [Phase 6 — SIMD values and lifted execution](roadmap/phase-06.md#phase-6-simd-values-and-lifted-execution)
- <a id="phase-7-first-class-bidirectional-formats"></a> [Phase 7 — First-class bidirectional formats](roadmap/phase-07.md#phase-7-first-class-bidirectional-formats)
- <a id="phase-8-rules-rulesets-objects-and-rulecycle"></a> [Phase 8 — Rules, rulesets, objects, and `ruleCycle`](roadmap/phase-08.md#phase-8-rules-rulesets-objects-and-rulecycle)
- <a id="phase-9-modules-execution-roots-and-program-reification"></a> [Phase 9 — Modules, execution roots, and program reification](roadmap/phase-09.md#phase-9-modules-execution-roots-and-program-reification)
- <a id="phase-10-sandboxes-and-capability-isolation"></a> [Phase 10 — Sandboxes and capability isolation](roadmap/phase-10.md#phase-10-sandboxes-and-capability-isolation)
- <a id="phase-11-compile-time-execution-and-separate-compilation"></a> [Phase 11 — Compile-time execution and separate compilation](roadmap/phase-11.md#phase-11-compile-time-execution-and-separate-compilation)
- <a id="phase-12-compiler-backend-and-optimization"></a> [Phase 12 — Compiler backend and optimization](roadmap/phase-12.md#phase-12-compiler-backend-and-optimization)
- <a id="phase-13-tooling-standard-library-and-release-hardening"></a> [Phase 13 — Tooling, standard library, and release hardening](roadmap/phase-13.md#phase-13-tooling-standard-library-and-release-hardening)

## Testing and acceptance gates for every phase

- Unit tests cover lexer/layout, parser/AST spans, resolver/analysis, runtime values, evaluation,
  reflection, diagnostics, and backend lowering independently.
- Integration tests execute at least one representative `.caret` program per newly implemented
  feature through `./run.sh`; compiler phases run the same program through compiled execution.
- Negative tests assert stable diagnostic phase/code and exact one-based line/column, including
  related spans when two declarations/contracts/rules conflict.
- Interaction tests combine each new feature with null/missing, exports, lookup, reflection,
  closures, partials, contracts/effects, collections, and modules as relevant.
- Ordinary constructor-call tests cover aliases, local shadowing, arity, prefix/numbered-hole
  partials, dispatch, effects, and reflection; extend them to staging when available. Verify
  recognition by resolved identity, four-argument cycle/identity phases, normal nullary format
  invocation/reference behavior, and Collection-based rule construction after its design blockers
  are resolved.
- Low-precedence application tests cover right associativity and its boundary with whitespace calls,
  every infix tier, composition, conditionals, holes, multiline layout, and later lambdas.
- Scoped-lookup tests cover one-time target evaluation, local/member/enclosing shadowing,
  initialization errors, nested `outer` paths, member identity and reification, invalid/dynamic
  targets, and adversarial export/root/module/sandbox visibility.
- Container tests cover parsing/spans, independent and aliased identity, identity equality, explicit
  reads, successful and rejected writes, unchanged contents after failure, nested storage, absence
  of deep mutation, effect propagation, field reification, and selective rule reevaluation.
- Sandbox stages require adversarial tests for hidden-name lookup, reflective traversal, code
  visibility, imports, capability retention, nested authority, and interpreter/compiler parity.
- Compile-time stages test runtime-dependency rejection, effect/capability enforcement, module/code
  visibility, boundary representability, independent roots, and exact post-transformation reachability.
- Documentation release gates run every published Caret example, build MkDocs with strict warnings,
  check links/navigation/specification coverage, and verify clean reproducible regeneration.
- Stage completion requires `./gradlew test`, `./test.sh`, all examples, differential tests available
  at that stage, and `git diff --check` to pass.

## Recommended next implementation step

Low-precedence application, runtime user-contract derivation, generalized contract inference, the
minimum purity/effect analysis, proven-predicate refinements, and nullable/optional contract unions
are complete. Initial parameterized contracts are complete through callable `Sequence T`,
`Field K V`, and `Dictionary K V` constructors. The shared
callable-signature scheme and safe callable reflection are now implemented for the current callable
kinds. Exact-arity higher-order arrow contracts are now parsed and analyzed over that metadata,
including inline clauses, variance checks, declaration-wide variables, explicit effects, and runnable examples.
The environment-relative effect catalog and mixed-clause analysis enforce public declaration
allowances and callable-value constraints; unknown higher-order invocation rejection, catalog
aliases, and Phase 2 higher-order effect propagation are complete. Callable signatures, reflection,
explicit higher-order arrow contracts, and the initial static operator matrix are settled.
Mixed-clause and callable-effect diagnostic codes and attribution are also settled; no conformance
item in Phases 1, 2, or 3 remains formally unresolved. The lambda-refinement correction, Phase 4
common Collection protocol, Field tuples, contextual shapes, general keyed Collections, internal
settlement, unified dot/bracket/`getElement` access, lexical lazy establishment, generalized lazy
transforms, strict Collection consumers, paired `zip`/`zipWithKeys` construction, and revised
Collection equality are complete; implement mutable containers and state effects next.
`with`/`outer` wait for the public named-member protocol
rather than introducing a separate exported Scope value model.

## Explicit assumptions and allowed deferrals

- Java 21 and the tree-walking interpreter remain the reference implementation until differential
  conformance proves the compiler backend.
- Static analysis may be introduced incrementally, but runtime behavior must not claim a feature is
  implemented until its required static guarantees exist.
- The plan includes all normative initial requirements. Items explicitly marked by `LANGUAGE.md` as
  postponable remain deferred: advanced capture/ownership optimization, general format relation
  solving and streaming, flexible cycle state/`Break`/`Continue`, parallelism, numeric rule
  priorities, distributed/transactional rule cycles, dynamic ruleset unloading, atomic or
  transactional container operations, concurrency policies, revocable/read-only projections,
  cross-process container identity, debugger visualization, and formal rule conflict analysis.
- Deferred items still receive extension points and conformance notes so their later addition does
  not change the core value, effect, format, cycle, or scheduling models.
- The standard compiler-environment interface must be settled before Phase 11 begins. Source-text
  macros, cross-target whole-program optimization, automatic multi-root
  orchestration, compile-time networking, and advanced cache invalidation remain deferred.
