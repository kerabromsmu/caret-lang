# Caret Release Roadmap

## Goal and completion criteria

Implement the canonical specification corpus indexed by `LANGUAGE.md` across successive releases,
in dependency order while preserving Caret's
compact syntax, missing/null distinction, lexical scoping, immutable-first semantics, reflection,
and located diagnostics. A feature is complete only when it has:

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

## Release targets and implementation order

Phase numbers remain historical feature identifiers, not release numbers or execution order.
Phases 0–4 provide the implemented compatibility baseline, including contracts and effects,
lambdas, the Collection protocol, exact numeric values and conversion, packed layouts, mutable
containers, scoped lookup, and Java embedding. All implemented behavior belongs to v1.

| Target | Required work | Work excluded from that target |
|---|---|---|
| **1.0** | Implemented baseline; Phase 5 cycles; Phase 9A modules; Phase 9B execution environments and metadata; shared `Result T`; Phase 10A initial sandboxes | Self-interpreter, semantic Code, canonical serialization, environment swaps/reload, restricted container views, rules, SIMD, formats, staging, compiler backend, and remaining Phase 13 work |
| **1.1 provisionally** | Complete Phase 8: contexts, rules, scheduling, chains, rulesets, objects, container dependencies, and `ruleCycle` | Existing advanced-rule deferrals remain deferred; use 2.0 instead only if approved design changes break v1 compatibility |
| **Later releases** | Phase 9C semantic Code; Phase 9D canonical serialization/quines; Phase 10B swaps/reload; Phase 10C richer projections; standalone self-interpreter phase; Phases 6, 7, 11, 12, and remaining Phase 13 | Exact version assignments await later planning |

The v1 implementation order is **5 → 9A → 9B → 10A**. The shared
[`Result T` contract](spec/sections/06-07-templates-advanced.md#standard-error-template) is a
Phase 9B prerequisite for sandboxes, independent of formats. Cycles need the existing callable,
contract/effect, Collection, and resolver foundations; they do not need a Caret-written interpreter.
Modules and environments provide sandbox import visibility and generation-local state. SIMD,
formats, rules, staging, and a compiler backend are not prerequisites for this v1 subset.

| Chunk | Main implementation work | Complexity |
|---|---|---|
| [5 — Cycles](roadmap/phase-05.md) | Four-argument callable, stable state contracts/shapes, previous/next lookup, atomic phase commits, effects, and nesting | Moderate |
| [9A — Modules](roadmap/phase-09.md#phase-9a-modules-and-imports-v1) | Path/ModuleId resolution, catalog discovery, exports, initialization, cycle diagnostics, and per-generation evaluation cache | Moderate to high |
| [9B — Environments and metadata](roadmap/phase-09.md#phase-9b-execution-environments-metadata-and-results-v1) | Execution context across closures/imports/REPL/tests, metadata-only roots/modules, and shared results | Moderate to high |
| [10A — Initial sandboxes](roadmap/phase-10.md#phase-10a-initial-sandboxes-v1) | Fixed environments, projected calls and shared containers, restricted imports/effects/reflection, nested authority, termination/unloading | High |

GitHub card assignment is pending a separate review. Additional v1 cards must have settled semantics,
fit the core language, and avoid introducing a dependency on a postponed subsystem. Existing planned
or deferred work is not promoted into v1 merely because it is described in a completed phase file.

## Release policy and completion

- Preserve the historical mapping of `0.1.x`–`0.4.x` to Phases 1–4. Future minor releases may complete
  an explicitly defined phase chunk or release milestone; their numbers need not match phase IDs.
- Increment `UPDATE` by one for other releases; increment `MINOR` by one and reset `UPDATE` for an
  agreed completed milestone. Increment `MAJOR` and reset both lower components only with explicit
  owner authorization at release preparation. Defining the v1 target does not update `VERSION`.
- A release completes its selected feature set, not the entire intended-language specification.
  Preserve permanent conformance IDs and implemented evidence. Mixed requirements spanning later
  work remain planned with partial evidence until the complete requirement is implemented.
- V1 requires the Java interpreter conformance and runnable-example evidence for the selected
  subset. Self-interpreter comparison, compiled parity, and the full generated documentation site
  become gates when those later facilities exist, not prerequisites for v1.

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
  implementation issue, tests, example, and status. A chunk is complete only when its allocated
  behavior has passing evidence; it must not claim completion of a mixed later requirement.


## Phase index (historical identifiers)

- <a id="phase-0-specification-and-conformance-baseline-completed"></a> [Phase 0 — Specification and conformance baseline (completed)](roadmap/phase-00.md#phase-0-specification-and-conformance-baseline-completed)
- <a id="phase-1-front-end-binding-semantics-and-unified-callables"></a> [Phase 1 — Front end, binding semantics, and unified callables](roadmap/phase-01.md#phase-1-front-end-binding-semantics-and-unified-callables)
- <a id="phase-2-types-contracts-effects-and-ownership-foundation"></a> [Phase 2 — Types, contracts, effects, and ownership foundation](roadmap/phase-02.md#phase-2-types-contracts-effects-and-ownership-foundation)
- <a id="phase-3-lambdas-and-higher-order-programming-completed"></a> [Phase 3 — Lambdas and higher-order programming (completed)](roadmap/phase-03.md#phase-3-lambdas-and-higher-order-programming-completed)
- <a id="phase-4-universal-collections-fields-and-mutability-containers-completed"></a> [Phase 4 — Universal collections, fields, and mutability containers (completed)](roadmap/phase-04.md#phase-4-universal-collections-fields-and-mutability-containers-completed)
- <a id="phase-5-self-hosting-foundation-and-ordinary-cycles"></a> [Phase 5 — Ordinary cycles (v1)](roadmap/phase-05.md#phase-5-self-hosting-foundation-and-ordinary-cycles)
- <a id="phase-6-simd-values-and-lifted-execution"></a> [Phase 6 — SIMD values and lifted execution](roadmap/phase-06.md#phase-6-simd-values-and-lifted-execution)
- <a id="phase-7-first-class-bidirectional-formats"></a> [Phase 7 — First-class bidirectional formats](roadmap/phase-07.md#phase-7-first-class-bidirectional-formats)
- <a id="phase-8-rules-rulesets-objects-and-rulecycle"></a> [Phase 8 — Rules, rulesets, objects, and `ruleCycle`](roadmap/phase-08.md#phase-8-rules-rulesets-objects-and-rulecycle)
- <a id="phase-9-modules-execution-roots-and-program-reification"></a> [Phase 9 — Modules, execution roots, and program reification](roadmap/phase-09.md#phase-9-modules-execution-roots-and-program-reification)
- <a id="phase-10-sandboxes-and-capability-isolation"></a> [Phase 10 — Sandboxes and capability isolation](roadmap/phase-10.md#phase-10-sandboxes-and-capability-isolation)
- <a id="phase-11-compile-time-execution-and-separate-compilation"></a> [Phase 11 — Compile-time execution and separate compilation](roadmap/phase-11.md#phase-11-compile-time-execution-and-separate-compilation)
- <a id="phase-12-compiler-backend-and-optimization"></a> [Phase 12 — Compiler backend and optimization](roadmap/phase-12.md#phase-12-compiler-backend-and-optimization)
- <a id="phase-13-tooling-standard-library-and-release-hardening"></a> [Phase 13 — Tooling, standard library, and release hardening](roadmap/phase-13.md#phase-13-tooling-standard-library-and-release-hardening)
- [Standalone phase — Caret-written self-interpreter (post-v1)](roadmap/self-interpreter.md)

Phases 9 and 10 contain separately scheduled chunks. After v1, prioritize Phase 8; its design
milestone must settle the first-class C/T/E contracts and tracked container-read/purity interaction
before rule implementation. The implemented contextual-template foundation already satisfies its
template prerequisite. Advanced module/sandbox work and self-hosting are separate later tracks.
Staging retains module/environment prerequisites and its unresolved compiler-environment design;
Code-based sandbox construction follows semantic Code support. Later phases add integration with
each feature only when that feature is available.

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
- V1 module tests cover exports/privacy, catalog collisions, path/ID identity, initialization,
  failed loads, import cycles, and environment-local caches. V1 cycle tests cover previous/next
  state, atomic commits, stable shape/contracts, purity, effects, and ordinary callable behavior.
- Sandbox stages require a documented threat model and adversarial tests for hidden-name lookup,
  reflective traversal, imports, effects, retained references, shared containers, termination,
  and nested authority. Add semantic-code visibility and interpreter/compiler parity when available.
- Compile-time stages test runtime-dependency rejection, effect/capability enforcement, module/code
  visibility, boundary representability, independent roots, and exact post-transformation reachability.
- Every release checks existing documentation/navigation/conformance and executable examples.
  The later documentation-site release also builds MkDocs with strict warnings, checks specification
  coverage, and verifies clean reproducible regeneration.
- Stage completion requires `./gradlew test`, `./test.sh`, all examples, differential tests available
  at that stage, and `git diff --check` to pass.

## Recommended next implementation step

Phase 4 is complete, including mutable containers, state effects, field reification,
`with`/`outer`, Collection-value `eager`, contextual templates, numeric conversion, and packed
layouts. The next dependency-ordered work is [Phase 5](roadmap/phase-05.md): ordinary four-argument
`cycle`, including its state lookup and commit semantics. Continue with 9A, 9B, and 10A for v1.
The [self-interpreter](roadmap/self-interpreter.md), explicitly deferred Collection APIs, callable
`eager`, and advanced module/sandbox/compiler work remain outside this target unless the separate
GitHub card review explicitly revises it.

## Explicit assumptions and allowed deferrals

- Java 21 and the tree-walking interpreter remain the reference implementation until differential
  conformance proves the compiler backend.
- Static analysis may be introduced incrementally, but runtime behavior must not claim a feature is
  implemented until its required static guarantees exist.
- The roadmap retains the whole normative specification across releases; v1 includes only its
  selected subset. Items explicitly marked by the specification as postponable remain deferred:
  advanced capture/ownership optimization, general format relation
  solving and streaming, flexible cycle state/`Break`/`Continue`, parallelism, numeric rule
  priorities, distributed/transactional rule cycles, dynamic ruleset unloading, atomic or
  transactional container operations, concurrency policies, revocable/read-only projections,
  cross-process container identity, debugger visualization, and formal rule conflict analysis.
- Deferred items still receive extension points and conformance notes so their later addition does
  not change the core value, effect, format, cycle, or scheduling models.
- The standard compiler-environment interface must be settled before Phase 11 begins. Source-text
  macros, cross-target whole-program optimization, automatic multi-root
  orchestration, compile-time networking, and advanced cache invalidation remain deferred.
