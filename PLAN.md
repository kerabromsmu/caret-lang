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
- Pass an explicit execution environment through interpretation, imports, tests, REPL sessions, and
  compiled entry points. Reflection and authority are always relative to that environment.
- Assign stable diagnostic phase/code/span data before expanding error messages. All new syntax and
  semantic failures must be testable without matching vague prose.
- Maintain a feature conformance table mapping every normative `LANGUAGE.md` requirement to its
  implementation issue, tests, example, and status. A stage is complete only when its rows pass.

## Phase 0 — Specification and conformance baseline (completed)

- `CONFORMANCE.md` inventories implemented, planned, deferred, and unresolved requirements with
  stable IDs and automated evidence.
- Characterization tests cover the implemented precedence table, safe primitive failures, and
  stable diagnostic phases/codes/locations alongside the existing core suites.
- The redesigned contract/derivation, functional-dispatch, and universal-collection model is
  reconciled across the public introduction, conformance matrix, and this roadmap.
- Root/module reification, canonical code equivalence, sandbox construction/lifecycle, SIMD
  grouping, general choices, unordered object traversal, and the initial JVM ABI are specified.
- Physical-to-logical layout baseline mappings, stable module-ID declarations, catalog discovery,
  path/ID import resolution, and environment-relative catalog visibility are specified.
- `LANGUAGE.md` records the resolved infix, multiline, function-reference/result-contract,
  persistent state/object, module, bytes, contract, collection, and JVM backend decisions.
- The shared structured-error payload is specified through `ErrorTemplate`, and the parameterized
  `Result` contract defines the public success/failure envelope. Dynamically supplied host
  capabilities remain environment bindings rather than serialized code dependencies.

## Phase 1 — Front end, binding semantics, and unified callables

Current status: Phase 1 is complete. Logical-line construction has moved out of the parser,
definition-header parsing has been consolidated, and the semantic resolver now predeclares block
bindings and records lexical depth/slot metadata consumed by the interpreter. Duplicate and
premature-read diagnostics run in the semantic phase, callable invocation has a common depth guard,
partial-expression rewrites share one exhaustive AST rewriter, and more-indented ungrouped
expressions form nested calls. Potential named prefix/infix calls are parsed neutrally and resolved
from lexical callable facts, with runtime fallback only when arity is genuinely dynamic. Callable
partial arguments use persistent O(1) accumulation, and language-owned value descriptors now
centralize public kinds, basic reflection, structural equality, and stack-safe rendering. Trailing
lambdas remain deferred to Phase 3. Right-associative low-precedence `$` application now lowers to
the ordinary callable path. Language-owned callable signature metadata and its safe reflective
projection are implemented for named functions, built-ins, prefix partials, compositions, and
closed overload sets. Exact-arity arrow contracts now work as named or inline structural
predicates with explicit visible effect allowances. Declaration-wide variables and substitution
through prefix and hole partials are implemented; complete overload-domain proofs remain unfinished.
Derived callable signatures now project repeated and reordered holes, specialize composition
bridges, separate construction effects from invocation bounds, and preserve projected overload
survivor signatures with conservative summaries.

### Layout and expressions

- Extend the structured logical-line engine already used by grouped expressions, dynamic lookups,
  ungrouped multiline argument lists, and indented bodies to lambdas, collection literals, `format`, `cycle`,
  rules, and trailing blocks as those constructs are implemented.
- Preserve the implemented pre-parse layout-mapping stack for terminal `\\` baseline adjustments
  and standalone `\*` restoration lines as later indentation-opening forms are added. Effective
  indentation is computed before parsing while diagnostics retain physical coordinates.
- Preserve raw source columns and complete spans through desugaring. Add recovery boundaries so one
  malformed declaration does not erase useful later diagnostics in compiler mode.
- Keep function application tighter than infix operators and make conditional branches lazy.
- Preserve implemented right-associative, syntax-level `$` below composition and conditionals on
  the ordinary application path; extend precedence coverage when lambdas arrive in Phase 3.

### Name resolution and closures

- Preserve the resolver's implemented block-wide function predeclaration, source-ordered
  non-function initialization, duplicate diagnostics, lexical depths/slots, closure capture, and
  established `^name = name` export pattern as later declaration forms are added.
- Preserve the implemented resolver-owned upvalue/lowering metadata and eager partial-value capture
  as mutation and compiler lowering are introduced later.
- Preserve implemented structural equality for scalars, named Collections, and positional collections and the located
  rejection of callable equality as new value kinds arrive.

### Unified functions/operators and composition

- Extend the shared callable protocol already used by operators, user functions, composition, and
  partials with one language-owned signature scheme containing declared and inferred parameter,
  result, generic-variable, effect, arity, and provenance data. Preserve stronger inferred facts
  locally while projecting explicit declarations as stable cross-module interfaces.
- Specialize signature instances through prefix and hole partials, including conjunctive repeated-
  hole requirements and construction-time fixed-operand effects. Derive composition signatures
  from left parameters, right results, compatibility constraints, and unioned invocation effects.
  Keep construction effects separate from later callable invocation effects.
- Preserve complete variant signatures in overload sets and partials alongside a conservative
  common-result and unioned-effect summary; never collapse alternative parameter domains into one
  conjunction. Retain immutable viable-variant, sparse filled-position, bound-argument, and
  applicability-cache state while exposing common remaining arity.
- Parse right-associative `[requirements] -> result` arrow signatures as first-class structural
  callable contracts without changing collection or lambda parsing. Support exact nullary and
  multi-parameter arity, conjunctive parameter positions, mixed result/effect clauses, nesting, and
  declaration-wide numbered contract variables with source spans.
- Preserve the implemented single analyzed representation of declaration clauses, split between
  conjunctive value requirements and an optional effect allowance with source spans and
  position-specific function, parameter, and assignment meanings.
- Preserve implemented prefix symbolic calls (`+ 2 3`), prefix named calls, and fixed-precedence
  infix binary calls (`2 add 3`), including the expression-start classification rule.
- Attach the settled pure operator signatures to symbolic callable values. Represent `+` as its
  closed numeric/string overload set so partial application, narrowing, composition, and reflection
  use the ordinary callable path; keep truth and conditional laziness as syntax-level evaluation.
- Extend the implemented `>>` function composition with contract/effect metadata when those systems
  arrive. Its arity, holes, partial state, reflection, and incompatible-operand diagnostics already
  use the shared callable path.
- Expand function reflection over the settled nested `Function`/`Signature` schema, including
  remaining parameter, result, effect, generalized-variable, and surviving-overload descriptors.
  Preserve environment-relative lazy inferred-fact and descriptor-name filtering through the
  internal non-amplifying observation-context seam, target/descriptor identity, and the prohibition
  on exposing captures, bound values, provenance, implementation objects, or authority. Keep
  `@function` itself non-callable.

## Phase 2 — Types, contracts, effects, and ownership foundation

Current status: Phase 2 is in progress. Unary user-defined base and derived contracts are
implemented alongside runtime-kind contracts. Multiple bases are passed as one ordinary `[A B]`
collection. Binding, parameter, and result clauses acquire or validate membership at runtime.
Initial constraint inference and
generalized contract variables for named functions are implemented. A minimum effect analysis now
propagates known named-call effects, preserves unknown dynamic calls, and proves refinement
eligibility without exposing effect syntax. Proven unary Boolean callables now participate as
first-class predicate requirements in derived contracts and direct clauses, including through
aliases. Nullable/optional contract modifiers are implemented as first-class, identity-stable
contract unions without collapsing null into missing. The initial parameterized-contract slice is
also implemented as `Sequence T`, with constructor metadata preserved through aliases, recursive
element validation, nesting, modifiers, identity semantics, reflection, and conservative outer-kind
inference. Environment-relative public effect identities, declaration allowances, callable
constraints, and effectful arrow contracts are implemented; complete higher-order propagation and
catalog aliasing remain subsequent Phase 2 slices. Closed same-name overload sets now provide observational applicability, unique
most-specific runtime selection, generic fallbacks, persistent prefix and hole narrowing,
and distinct no-applicable/ambiguous diagnostics. Complete static selection remains planned; the
initial signature/reflection schema is implemented.
The effect pass includes eagerly captured fixed operands in partial expressions, treats
over-application through an unknown returned callable conservatively, and uses resolver-owned
symbol identities rather than source-span equality.

### Contract and type model

- Preserve implemented source-spanned contract construction, derivation, refinement, binding,
  parameter, result, and nullable/optional modifier forms (`T?`, `T~`, `T?~`).
- Preserve implemented validation of inferred callable needs and guarantees against declarations
  without silently strengthening parameter interfaces. Undeclared signature components generalize,
  instantiate freshly per use, and retain substitutions in derived partial callables.
- Preserve implemented arrow-contract satisfaction and implication with exact arity,
  contravariant parameters, covariant results, effect-bound inclusion, and generalized-variable
  compatibility. Unknown
  relationships return false as predicates and retain ordinary boundary contract failures; checks
  never invoke the candidate or acquire nominal membership.
- Preserve the implemented overload proof requiring one variant to cover the complete domain and
  every potentially selectable overlapping variant to satisfy result and effect constraints. Treat
  unknown overlap as possible and do not execute refinements or combine partial domains to prove
  coverage in the initial implementation.
- Preserve the implemented checked logical-inclusion graph with forward references, multiple and
  redundant bases, transitive diamond implication, cycle rejection, and declaration provenance for
  invalid derivation diagnostics.
- Preserve implemented unary contract predicates and proven-pure Boolean refinements while adding
  static membership proofs where possible and retaining runtime checks when proof is unavailable.
- Preserve the implemented initial operator matrix over `Number`, `String`, structural `Eq`, and
  `Boolean?~`, including reflected closed `+` variants and later-context relational inference.
  Preserve String-plus-Any language rendering, numeric-only ordering, Number-only arithmetic
  guarantees, recursive callable rejection in equality, and lazy normalized truth operations.
- Retain closed `+` alternatives across whole-block constraint collection, resolve them from operands
  and context, and report `AMBIGUOUS_CONTRACT` rather than generalizing an operator constraint or
  defaulting to Number. Use `INCOMPATIBLE_CONTRACTS` for statically impossible operands while
  preserving established runtime operand, zero-divisor, non-finite, and callable-equality errors.
- Extend the implemented ordinary contract/function parameterization beyond `Sequence T` as later
  value kinds arrive; keep general `Collection T` and mutable `Container T` aligned with Phase 4
  rather than introducing a separate generic-type subsystem.
- Group same-named function definitions into overload sets. Normalize parameter conjunctions and
  absence alternatives, then order variants with the settled compiler-proven implication relation:
  nominal derivation, verified-refinement identity, constructor-declared variance, and component-wise
  strictness. Select the unique most-specific implementation and diagnose incomparable applicable
  definitions without executing predicates to determine ordering. Runtime applicability checks are
  observational: require existing nominal membership, cache pure structural/refinement checks per
  requirement identity and argument position, preserve original arguments, and never acquire
  membership while considering candidates. Keep single-function contract violations distinct from
  multi-variant no-applicable and ambiguous-overload diagnostics. Narrow overload sets incrementally
  as prefix or hole arguments fill known positions, fail when no variant survives, and defer final
  selection and ambiguity until full arity without repeating cached parameter checks.
- Introduce built-in scalar/value contracts and structural contracts for named Collections,
  callables, SIMD values, formats, rules, and cycles as those kinds arrive. Contract failures identify
  the declaration/call and failing contract with related spans.
- Extend eligible hole functions with language-owned collection-constructor descriptors retaining
  shape, nesting, fields, fixed captures, hole identities, and hole contracts. Keep ordinary eager
  capture and numbered-hole behavior; never expose Java AST or runtime implementation objects.
- Implement `template` as an ordinary callable over concrete collections and reifiable collection
  constructors. It produces an ordinary first-class `Contract` and rejects arbitrary callables or
  non-structural partial expressions with a stable located diagnostic.
- Establish the shared diagnostic/error descriptor from `ErrorTemplate`: stable code, phase,
  message, primary location, related locations, cause, and subsystem details. Aborting diagnostics
  retain control-flow semantics; expected operation failures use the corresponding Caret value.

### Effects and purity

- Give every callable an inferred effect set and a declared maximum set. No effect declaration means
  an empty set; `pure` is the explicit spelling of that guarantee.
- Resolve effects through a separate environment-relative catalog. Standardize `Output` and reserve
  `StateRead`/`StateWrite`; require environment callables such as test, filesystem, or network
  integrations to expose stable effect identities and known upper bounds without granting authority.
- Classify mixed clause terms against both the contract/refinement namespace and effect catalog.
  Reject ambiguous or unknown names, effects with absence modifiers or as parameterized-contract
  arguments, and `pure` combined with a nonempty allowance; make ordering immaterial and normalize
  aliases by descriptor identity.
- Apply effect terms before functions as declared function allowances and before parameters or
  assignments as subset constraints on the callable value's known upper bound. Do not infer an
  implicit purity constraint when a parameter or assignment clause contains no effect terms.
- Reject invocation of a callable whose effect upper bound is unavailable with
  `UNKNOWN_CALL_EFFECTS`; use the same code when an unavailable bound prevents validation of an
  effect-constrained parameter or assignment. Do not add an effect wildcard or runtime
  after-the-fact enforcement.
- Use distinct stable codes for conflicting `pure`/named allowances, effect absence modifiers,
  effects used as contract arguments, non-callable constrained values, and known bounds or inferred
  effects outside an allowance. Keep behavioral codes stable across semantic and runtime discovery,
  with deterministic primary and related locations.
- Define `StateRead` for explicit container dereference and `StateWrite` for `put`. Accessing or
  sharing a container reference is pure; effects describe observation but do not grant authority.
- Infer effects transitively through calls, higher-order parameters, closures, composition, partials,
  cycles, codecs, and rule effects. Diagnose every inferred effect outside the declared allowance.
- Require contract predicates, format construction/relations, SIMD-mapped functions, cycle
  conditions, and rule `C`/`T` expressions to be pure where specified.
- Add CLI output for inferred contracts/effects so the capability is available without an IDE.

### Ownership and optimization contract

- Define immutable value semantics separately from storage optimization. Add an internal uniqueness
  or ownership analysis that may update state in place only when no observable alias can distinguish
  it.
- Keep ownership an optimization initially; results must match persistent semantics with the
  optimization disabled. This foundation later supports efficient cycles, collection updates, SIMD memory,
  and compiled execution.

## Phase 3 — Lambdas and higher-order programming

- Parse unary/multi-parameter lambdas, contracted parameters, expression bodies, and indented bodies
  with the precedence/extent rules settled in Phase 0.
- Lower lambdas to the same function representation as named functions. Implement lexical capture,
  capture timing, arity, nullary behavior, return values, reflection/reification, and higher-order
  calls.
- Support ordinary partial application and hole-based partial application around lambdas without
  conflating holes with parameter declarations.
- Implement composition and standard higher-order collection functions (`map`, `filter`, `fold`,
  `any`, `all`) using the unified callable/effect model.
- Infer contracts, purity, effects, and later SIMD eligibility exactly as for named functions.
- Complete `LAMBDA-LOWAPP-001`: lambda construction binds above `$`, with parser and runtime
  coverage for ungrouped lambdas used as complete low-precedence arguments.

## Phase 4 — Universal collections, fields, and mutability containers

Current foundation: `Collection` is implemented as the general contract for Sequences and
Dictionaries. Static `^name`, ordinary `field "name" value`, exported blocks, and `dictPut` share
one String-keyed `Dictionary K V` representation; mixed shapes and duplicate keys are diagnosed.
The steps below describe the remaining contextual, template, and representation work.

### Collection protocol and literals

- Generalize existing sequences/dictionaries behind `Collection` and capability contracts while
  keeping persistent semantics and canonical Dictionary field order.
- Complete contextual behavior for `[...]`, including shape-neutral empty values and inferred
  content contracts, without assigning a fixed container meaning to square brackets.
- Make each collection literal a hole-expression boundary: materialize its collection-constructor
  function before passing it to a surrounding call, while retaining nested collection structure in
  the outer constructor descriptor. This lets ordinary `template [..]` application receive the
  constructor without template-specific parsing or evaluation.
- Preserve semantic element contracts independently from physical representation metadata. Share
  metadata at collection level when possible and retain per-element metadata where required.

### Fields and dictionary-like collections

- Preserve the implemented first-class `Field K V`, ordinary `field name value` construction, and
  unified `Dictionary K V` representation shared by exports, static fields, and persistent updates.
- Preserve the implemented positional/named shape diagnostic. Make `[]` shape-neutral and valid
  under every zero-compatible collection contract.
- Support static and dynamic access, optional lookup, and exact missing/null/present-`~` behavior.
- Validate statically known collections against structural contracts and retain dynamic checks when
  needed. Preserve the implemented direct lowering of exported blocks, excluding private bindings.
- Add packed collections only after representation analysis can require uniform layouts. Integrate
  later with SIMD and formats without changing observable contract membership.

### Scoped member lookup

- Implement `with value` as an expression over the common public named-member protocol, beginning
  with named Collections and extending to rulesets, root/module metadata,
  and sandbox projections as those value kinds arrive. Evaluate the target once and preserve member
  identity rather than copying or destructuring fields.
- Resolve names in local-declaration, current-`with`-member, enclosing-lexical order. Keep ordinary
  declaration predeclaration and initialization errors; a same-named member is not a fallback for
  an uninitialized local.
- Represent `outer.name` and repeated `outer.outer.name` as analyzed lexical paths across `with`
  layers, never as first-class environment values. Preserve export, reflection, module, root, and
  sandbox authority boundaries, including for member reification.
- Defer lookup specialization, flattened outer chains, and IDE scope visualization until the
  resolver/member protocol is semantically complete.

### Structural templates

- Implement unconstrained and contracted holes, equality-checked fixed values, exact positional and
  named shape, dynamic field names, and recursive nested collection shapes.
- Derive membership as the structural inverse of eligible constructors without invoking them.
  Repeated numbered holes impose candidate equality; numbering changes parameter order but not
  collection shape, and mixed numbered/unnumbered holes remain invalid.
- Validate statically known template membership and retain runtime checks when proof is unavailable.
  Diagnose invalid constructors and non-comparable fixed values with their settled template codes;
  reuse ordinary field, contract, and hole codes for malformed dynamic keys, duplicate fields,
  invalid contracted holes, and mixed hole styles. Preserve parser phase for malformed syntax and
  keep behavioral codes stable across semantic and runtime discovery, with the offending construct
  primary and the first duplicate field related.
- Reflect template structure as metadata on `Contract` descriptors. Permit shared metadata and
  packed-layout derivation only when optimized and optimization-disabled behavior is identical.

### Persistent updates and contained mutation

- Implement immutable collection update syntax, including nested updates and
  shape/contract checking.
- Implement `{ value }` and `{ (Contract...) value }`, postfix `container{}`, and `put container
  value`. Infer stable content contracts, validate initial and replacement values, return the stored
  value from successful `put`, and leave prior content unchanged on failed validation.
- Give containers stable runtime identity and identity-based `==`. Ordinary assignment, fields,
  collections, closures, and function calls share that identity; only explicit dereference observes
  contents, and no surrounding value becomes deeply mutable.
- Implement `object.@field` as field-binding reification distinct from both `object.field` and
  `object.field{}`. Reification exposes no additional read/write authority.
- Extend reflection with field descriptors, order, mutability, ownership, contracts, nullability,
  optionality, export status, and visibility-filtered container identity/content-contract metadata.

## Phase 5 — Self-hosting foundation and ordinary cycles

### Caret-written interpreter milestone

- Use the existing Unicode text and persistent collection primitives plus recursion, tagged data,
  closures, and located result values to implement a lexer/parser/evaluator subset in Caret.
- Keep the Caret implementation as a conformance client, not a replacement for Java prematurely.
  Run shared fixtures through Java and Caret implementations and compare values/diagnostics.
- Expand the self-interpreter alongside later features only after each feature's Java semantics are
  stable.

### `cycle`

- Implement `cycle` as an expression over an initial state, pure unary condition, unary body, and
  unary prepare transformation. Support omitted body/prepare forms exactly as specified.
- Accept named functions, lambdas, partials, and positional or named Collections as phase values. Enforce a
  stable state shape and compatible contracts across iterations.
- Infer phase effects while requiring the condition to remain pure. Lower to an internal loop/tail
  recursion without mandatory immutable copying, preserving functional observable semantics.
- Support nesting, collection traversal helpers, format use, and final-state return. Keep `Break` /
  `Continue`, changing shapes, labels, effectful conditions, and automatic parallelism deferred as
  `LANGUAGE.md` allows.

## Phase 6 — SIMD values and lifted execution

- Introduce fixed-arity `Simd native Scalar` and `Simd lanes Scalar` contracts and Boolean masks.
- Lift supported pure numeric scalar operations and functions lane-wise. Implement mask selection so
  vector conditionals do not inherit scalar lazy-branch semantics incorrectly.
- Implement explicit `::` SIMD application as a semantic requirement: either produce verified SIMD
  execution or a clear compile-time diagnostic explaining impurity, unsupported operations, lane
  mismatch, or target limitations.
- Support composition, partial application, lambda mapping, and reductions using the same callable
  metadata. Implement inherited environment-scoped `simdOption grouping`, defaulting to
  language-defined `pairwise` and allowing target-dependent `hardware`; keep strict left grouping
  available through scalar `fold`.
- Add aligned/unaligned load/store lowering internally while exposing ordinary collection APIs;
  provide a portable fallback only where it preserves the explicit `::` contract.
- Test multiple lane widths and hardware capability profiles in interpreter/emulation and compiler
  modes; never make program meaning depend on host vector width.

## Phase 7 — First-class bidirectional formats

- Add immutable `Format` values representing bidirectional relations. `decode` and `encode` return
  `Result`, with expected failures carrying an `ErrorTemplate` payload. Implement empty formats and
  ordinary functional construction.
- Implement primitive byte/integer formats, `field format "name"`, constants/signatures, nested
  formats, fixed/prior-field repetition, conditions, general `selector ==` choices, constraints,
  and `>>` composition.
- Decode structured formats into ordinary collections and encode compatible collections, resolving earlier
  fields and derivable values consistently in both directions.
- Implement `decode`, `encode`, and pure explicit `codec decode encode format`; distinguish
  representation transformations from logical-value transformations.
- Make expected mismatch, incomplete input, invalid field, and codec failure structured rather than
  exceptional. Preserve offsets/path context in an exact format-details template inside the shared
  error payload and in rendered diagnostics.
- Add format reflection for components, names, contracts, directionality, size information where
  known, and tooling/generator use. Keep transport independent from formats.
- Defer general relational solving, arbitrary inversion, nondeterminism/backtracking, streaming,
  zero-copy, and async transport as permitted, without changing the compositional relation model.

## Phase 8 — Rules, rulesets, objects, and `ruleCycle`

### Contexts and rules

- Add persistent `Context` values, idempotent `raise`/`lower`, and transient `rise`/`fall` fronts over
  Boolean context expressions.
- Add first-class `Rule` values with unique optional CATEN clauses, defaults, inferred or explicit
  string-literal IDs for `N`, runtime active state, edge-trigger history, effect block, and implicit
  application context.
- Implement gate semantics: `C` and `A` permit application but never replay a trigger missed while
  gated. Require `C` and `T` purity; propagate ordinary effects from `E`.
- Implement `activate`/`deactivate`, implicit context rise/effect/fall, reevaluation after each effect,
  and protection/diagnostics for non-stabilizing propagation.

### Ordering and chains

- Schedule one applicable rule at a time, propagate its effects, and reevaluate. Use no observable
  source-order priority; a stable internal order may make tests reproducible but is not contractual.
- Represent causal dependencies through fronts and partial orders. Lower `chain` to ordinary rules
  joined by `fall @previous.context`; combine an explicit later trigger at the same completion front.
- Warn conservatively about significant simultaneously applicable unordered rules. `(unordered)`
  suppresses only the warning and never changes scheduling.

### Rulesets and cycles

- Add first-class private-by-default `RuleSet` values with `^` exports. Ordinary functions and holes
  provide templates and partial application; every construction owns independent state.
- Implement nested rulesets and explicit idempotent `install`; an uninstalled ruleset is inert.
- Add `ruleCycle init`, its master `cycle` context, registration, propagation to stability, traversal,
  and termination by lowering the master context.
- Record container identities observed by rule `C`/`T` evaluation. After a successful `put`, enqueue
  only dependent rules for reevaluation; keep this live dependency mechanism separate from atomic
  previous/next persistent-state commits.
- Introduce first-class objects with stable identity, exported state, cycle membership, and implicit
  traversal contexts. Define deterministic deferred lifecycle semantics for `create`/`destroy` to
  prevent reentrant traversal.
- Extend reflection across rules, contexts, rulesets, cycle state, dependencies, and public object
  interfaces without exposing private bindings.

## Phase 9 — Modules, execution roots, and program reification

### Modules

- Parse an optional top-level `moduleId = module` declaration into a namespace separate from lexical
  bindings. Shallowly discover project declarations below each compilation-root directory, merge
  environment-supplied IDs, and diagnose malformed, duplicate, colliding, and unresolved IDs with
  all relevant source locations.
- Implement both relative `String` path imports and catalog-resolved `ModuleId` imports, explicit
  exports, private bindings, initialization order, and duplicate/cyclic import diagnostics. Use the
  resolved canonical source path—not the logical ID—as the evaluation-cache and cycle-detection key,
  so path and ID imports of the same source share one module value per environment generation.
- Carry the visible module catalog through normal execution, staging, reflection, and sandboxes;
  never inject IDs as runtime globals or expose catalog entries outside the current environment.
- Compile/cache modules independently using a versioned semantic interface containing public names,
  types/contracts, effects, formats, and reflection descriptors.

### Execution roots and code values

- Give file execution, imports, tests, and REPL sessions an explicit execution-environment object
  containing the visible root, code snapshot, libraries, language capabilities, and runtime
  capabilities. Do not derive Caret authority from process-global Java state.
- Implement reserved metadata-only `@root` and `@module` reflective primaries. Make them equal only
  in the root module, keep their descriptors non-callable, and preserve ordinary export authority
  even though visible module code includes private semantic declarations.
- Reify analyzed source as language-owned `Code`/`CodeElement` values with stable public descriptors
  for bindings, functions, parameters, contracts, expressions, imports, and later constructs.
- Implement canonical code serialization with alpha-normalized private names, proven-safe ordering,
  logical import paths, portable language dependencies, and no source metadata. Keep dynamically
  supplied environment implementations out of canonical code and require compatible bindings when
  re-executing it. Require parse/serialize/parse structural equivalence and canonical quine/module
  fixtures.

## Phase 10 — Sandboxes and capability isolation

- Implement `sandbox source environment` for module paths and semantic `Code`, returning `Result
  Sandbox` with a stable sandbox handle and an immutable exported environment snapshot. Keep normal
  imports semantically distinct and evaluated module caches private to each generation.
- Implement atomic, validate-then-install `swapEnv`, current-environment lookup at every boundary
  operation, mediated named capability references, and bounded authority inheritance. Preserve the
  running generation and state; failed swaps retain the previous snapshot.
- Implement effectful `terminate`, `unload`, and stop-first `reload`. Discard resumable state,
  invalidate all old-generation references without rebinding, preserve copied immutable values,
  and leave a failed reload unloaded but retryable from retained configuration.
- Support direct, filtered, and virtual capabilities. Treat effect declarations as descriptions,
  never authority grants, and report unavailable authority through `Result` and `ErrorTemplate`.
  Apply the same boundary result envelope to construction, lifecycle, swaps, and exported calls.
- Make effect-catalog visibility environment-relative and independent from callable/capability
  projection. Nested sandboxes cannot introduce effect identities or implementations hidden by
  their parent without explicit outer-host injection.
- Project containers explicitly as shared read/write identities, read-only views, mediated values,
  snapshots, or virtual replacements. Reflection and field reification must not upgrade the chosen
  projection or recover hidden host mutation authority.
- Project references crossing the boundary through a reflective membrane that cannot expose host
  roots, private captures, native implementation state, hidden code, or other capabilities.
- Support nested sandboxes with child authority bounded by parent authority unless the outer host
  explicitly injects an additional capability.
- Define and test a threat model covering name lookup, reflection, code metadata, imports, effects,
  retained references, nested environments, and interpreter/compiler parity. Keep revocation,
  quotas, OS/process isolation, and advanced information-flow enforcement deferred.

## Phase 11 — Compile-time execution and separate compilation

- Parse binding-form `#` separately and represent expression-form `#` as an explicit staged region
  covering the remainder of its nearest expression boundary. Preserve its complete source span;
  parentheses and other explicit delimiters bound smaller regions, while later operators never
  return to runtime execution.
- Resolve names across stages, reject runtime-only dependencies from compile-time regions, and
  preserve stable diagnostics. Treat redundant nested `#` as valid and ensure split conditionals
  stage both branch values while retaining ordinary laziness for wholly staged conditionals.
- Execute ordinary Caret functions in an explicit compile-time environment using the reference
  evaluator. Enforce inferred effects against the capabilities actually supplied by that environment;
  staging and reflection must never recover omitted host or sandbox authority.
- Apply normal module identity, exports, initialization, and environment-local caching to
  compile-time imports. Track modules and external compile-time inputs as semantic build dependencies
  without automatically including their runtime bodies.
- Lower stage-boundary results into runtime IR: embed portable immutable values, retain semantic
  references required by reifiable executable/code values, and diagnose compiler-only or
  non-portable capability values.
- Compile each requested source root independently. After staging, compute semantic reachability from
  its resulting runtime root, omit discarded definitions, and retain the full dependency closure of
  selected rules, formats, contracts, and helpers.
- Test pure/effectful staging, invalid cross-stage dependencies, import visibility, target-specific
  rule filtering, shared dependency retention, discarded dependency removal, reproducible dependency
  manifests, and adversarial authority/reflection boundaries.

## Phase 12 — Compiler backend and optimization

### Compiler backend

- Define a lowered typed/effect-checked IR shared with the interpreter. Preserve source maps and
  diagnostic spans through closure conversion, pattern/conditional lowering, cycles, formats, SIMD,
  and rule scheduling.
- Implement Java 21-compatible bytecode behind opaque generated class names, a documented embedding
  facade, and a versioned runtime ABI that rejects incompatible artifacts and requires recompilation.
  Keep the semantic module/interface model backend-independent for future non-JVM and self-hosted
  implementations. Cover all Caret value kinds, module linking, and executable CLI commands.
  Compiled and interpreted programs must share observable
  values, evaluation order, missing/null behavior, errors, effects, environment-relative reflection,
  code visibility, container identity/aliasing, and sandbox authority boundaries.
- Add differential tests that run every conformance example in both modes and compare stdout,
  stderr, exit status, values, and stable diagnostic codes/locations.

### Optimization

- Add constant folding only for proven-pure operations; then dead-code elimination, call
  specialization/inlining, closure/partial specialization, persistent-update elision, tail-call and
  cycle lowering, format specialization, SIMD lowering, and rule dependency indexing.
- Every optimization has an off switch and differential/property tests. It must not expose source
  order for unordered rules, merge null with missing, execute an unselected conditional branch, or
  change effect/contract behavior.

## Phase 13 — Tooling, standard library, and release hardening

- Build standard-library modules for identity, collection transforms/reductions, field manipulation,
  common contracts, formats/codecs, cycle helpers, and reusable rulesets using ordinary Caret where
  feasible.
- Expose formatter/parser services, semantic queries, inferred contracts/effects, reflection
  descriptors, module navigation, and unordered-rule warnings for editor integration.
- Extend the REPL for multiline constructs, modules, type/contract/effect inspection, compiled-mode
  parity, environment-root inspection, sandboxed sessions, and structured display of collections,
  formats, SIMD, rules, code metadata, and diagnostics.
- Add fuzz/property tests for lexer/parser layout, Unicode, numeric finiteness, persistent
  collections, encode/decode round trips, optimizer equivalence, and scheduler stability.
- Establish performance suites for parsing, closures/partials, collection updates, cycles, SIMD,
  formats, rule propagation, module compilation, code serialization, sandbox startup, and REPL latency.
- After all planned language features and their conformance requirements are complete, publish the
  canonical `LANGUAGE.md` and `spec/` corpus as a developer-learning site. Use MkDocs Material with
  persistent left-pane navigation, search, breadcrumbs, and previous/next links;
  distinguish implemented, planned, deferred, and unresolved material from `CONFORMANCE.md`.
- Generate a coverage manifest mapping every normative specification section to the site, and fail
  strict builds on uncovered sections, duplicate anchors, broken links, missing navigation entries,
  orphan pages, contradictory status, or non-reproducible output. Generated pages are build artifacts,
  not an independently edited specification.
- Author a shared-source “Learn Caret in Y Minutes” tutorial using only implemented behavior and
  runnable examples. Produce both a site page and an upstream-compatible Markdown/YAML contribution,
  with an explicit checklist for metadata, license, formatting, highlighting, links, and submission.
- Version the language and runtime ABI only after the complete conformance suite passes on supported
  platforms.

## Testing and acceptance gates for every phase

- Unit tests cover lexer/layout, parser/AST spans, resolver/analysis, runtime values, evaluation,
  reflection, diagnostics, and backend lowering independently.
- Integration tests execute at least one representative `.caret` program per newly implemented
  feature through `./run.sh`; compiler phases run the same program through compiled execution.
- Negative tests assert stable diagnostic phase/code and exact one-based line/column, including
  related spans when two declarations/contracts/rules conflict.
- Interaction tests combine each new feature with null/missing, exports, lookup, reflection,
  closures, partials, contracts/effects, collections, and modules as relevant.
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
are complete. Initial parameterized contracts are also complete through `Sequence T`. The shared
callable-signature scheme and safe callable reflection are now implemented for the current callable
kinds. Exact-arity higher-order arrow contracts are now parsed and analyzed over that metadata,
including inline clauses, variance checks, declaration-wide variables, explicit effects, and runnable examples.
The environment-relative effect catalog and mixed-clause analysis now enforce public declaration
allowances and callable-value constraints. Next complete unknown higher-order invocation rejection,
catalog aliases, and complete higher-order effect propagation. Callable signatures, reflection, explicit higher-order arrow contracts,
and the initial static operator matrix are settled. Mixed-clause and callable-effect diagnostic
codes and attribution are also settled; no conformance item in Phases 1 or 2 remains formally
unresolved. `with`/`outer` wait for the Phase 4 public named-member protocol rather
than introducing a separate exported Scope value model.

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
