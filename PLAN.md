# Complete Caret Language Implementation Plan

## Goal and completion criteria

Implement every behavior described by `LANGUAGE.md` in dependency order while preserving Caret's
compact syntax, missing/null distinction, lexical scoping, immutable-first semantics, reflection,
and located diagnostics. “Complete” means each normative language feature has:

- a settled, non-contradictory specification in `LANGUAGE.md`;
- separated lexer/parser/AST/analysis/runtime or backend implementation;
- positive, negative, edge-case, diagnostic, and interaction tests;
- a runnable `.caret` example exercised by the integration suite; and
- matching behavior in the tree-walking interpreter and compiled execution where applicable.

The existing prototype already implements scalar values, `?` and `~`, bindings and functions,
closures, indentation-based bodies, whitespace application, conditionals, Boolean/arithmetic
operators, exported scopes, partial application and numbered holes, static/optional/dynamic lookup,
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
- Assign stable diagnostic phase/code/span data before expanding error messages. All new syntax and
  semantic failures must be testable without matching vague prose.
- Maintain a feature conformance table mapping every normative `LANGUAGE.md` requirement to its
  implementation issue, tests, example, and status. A stage is complete only when its rows pass.

## Phase 0 — Specification and conformance baseline (completed)

- `CONFORMANCE.md` inventories implemented, planned, deferred, and unresolved requirements with
  stable IDs and automated evidence.
- Characterization tests cover the implemented precedence table, safe primitive failures, and
  stable diagnostic phases/codes/locations alongside the existing core suites.
- `LANGUAGE.md` records the resolved infix, multiline, function-reference/result-contract,
  persistent state/object, module, bytes, unordered-contract, and JVM backend decisions.
- Remaining open syntax or observable semantics are explicit `unresolved` conformance rows and may
  not be invented during implementation.

## Phase 1 — Front end, binding semantics, and unified callables

Current status: Phase 1 is in progress. Logical-line construction has moved out of the parser,
definition-header parsing has been consolidated, and the semantic resolver now predeclares block
bindings and records lexical depth/slot metadata consumed by the interpreter. Duplicate and
premature-read diagnostics run in the semantic phase, callable invocation has a common depth guard,
partial-expression rewrites share one exhaustive AST rewriter, and more-indented ungrouped
expressions form nested calls. Trailing lambdas remain deferred to Phase 3; unified callable
syntax/metadata remains the principal unfinished Phase 1 work.

### Layout and expressions

- Extend the structured logical-line engine already used by grouped expressions, dynamic lookups,
  ungrouped multiline argument lists, and indented bodies to lambdas, `data`, `format`, `cycle`,
  rules, and trailing blocks as those constructs are implemented.
- Preserve raw source columns and complete spans through desugaring. Add recovery boundaries so one
  malformed declaration does not erase useful later diagnostics in compiler mode.
- Keep function application tighter than infix operators and make conditional branches lazy.

### Name resolution and closures

- Add a resolver pass that predeclares functions per block for direct and mutual recursion while
  executing non-function bindings in source order.
- Diagnose duplicate declarations and duplicate parameters. Permit parameters/body declarations to
  shadow outer bindings and retain the established `^name = name` export pattern.
- Assign lexical slots/upvalues independently of runtime environments. Verify closure capture and
  eager partial capture against mutation introduced later.
- Complete structural equality for scalar, scope, and collection values; reject callable equality
  with a located diagnostic.

### Unified functions/operators and composition

- Represent built-in operators and user functions through one callable protocol containing arity,
  parameter/result contracts, effects, invocation, partial-application state, and reflection.
- Support prefix symbolic calls (`+ 2 3`), prefix named calls, and infix binary calls (`2 add 3`)
  using the precedence/associativity rules settled in Phase 0.
- Preserve the rule that the expression start selects prefix versus infix interpretation; later
  binary functions in a prefix argument sequence do not reclassify the call.
- Implement `>>` function composition with arity, holes, closure capture, contract/effect metadata,
  and diagnostics for incompatible composition.
- Expand function reflection to parameter descriptors, remaining arity, contracts, effects,
  captures where public, and reification rules without making `@function` itself callable.

## Phase 2 — Types, contracts, effects, and ownership foundation

### Contract and type model

- Parse contracts before bindings, functions, and parameters, including multiple contracts and
  nullable/optional type modifiers (`T?`, `T~`, `T?~`). Represent them explicitly rather than as
  ordinary calls lost after parsing.
- Introduce built-in scalar/value type contracts and a structural type/contract model for scopes,
  collections, data, callables, SIMD values, formats, rules, and cycles as those kinds arrive.
- Allow a pure unary Boolean function as a value contract. Evaluate compile-time-known contracts;
  retain runtime checks only when proof is unavailable.
- Implement function/result contracts once the Phase 0 syntax is settled. Contract failures identify
  the declaration/call and the failing contract with related spans.

### Effects and purity

- Give every callable an inferred effect set and a declared maximum set. No effect declaration means
  an empty set; `pure` is the explicit spelling of that guarantee.
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
  optimization disabled. This foundation later supports efficient cycles, data updates, SIMD memory,
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

## Phase 4 — General collections, `data`, fields, and state updates

### Collection protocol and literals

- Generalize existing sequences/dictionaries behind the structural `Collection` contract while
  keeping persistent semantics and insertion-ordered dictionary keys.
- Implement heterogeneous collection contract inference and structural equality. Preserve string
  and name values as one dictionary key space and distinguish absent keys from present `~` values.
- Add the collection/data syntax specified in `LANGUAGE.md` without introducing JSON/YAML rules,
  commas, mandatory braces, implicit strings, or special interpolation.

### Data and fields

- Add first-class `data` values containing named and unnamed elements, nested data, computed values,
  conditional fields, and ordinary expressions.
- Implement `^name expression` fields and `field name value` dynamic field creation as first-class
  field values. Preserve insertion order and field identity/descriptor metadata.
- Support static and dynamic access, optional lookup, and exact missing/null/present-`~` behavior.
- Validate statically known data against structural contracts and retain dynamic checks when needed.
- Define conversions/relationships between exported scopes and `data` without making all data an
  executable scope.

### Updates and controlled mutation

- Implement the immutable scope/data update syntax settled in Phase 0, including nested updates and
  shape/contract checking.
- Add mutable bindings or objects only to the degree specified, with explicit aliasing and closure
  capture semantics. Expected missing/update failures return structured results or `~` where the
  language defines them, not Java exceptions.
- Extend reflection with field descriptors, order, mutability, ownership, contracts, nullability,
  optionality, and export status.

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
- Accept named functions, lambdas, partials, structured scopes, and `data` as phase values. Enforce a
  stable state shape and compatible contracts across iterations.
- Infer phase effects while requiring the condition to remain pure. Lower to an internal loop/tail
  recursion without mandatory immutable copying, preserving functional observable semantics.
- Support nesting, collection traversal helpers, format use, and final-state return. Keep `Break` /
  `Continue`, changing shapes, labels, effectful conditions, and automatic parallelism deferred as
  `LANGUAGE.md` allows.

## Phase 6 — SIMD values and lifted execution

- Introduce portable SIMD scalar/lane types and Boolean masks with explicit lane counts/contracts.
- Lift supported pure numeric scalar operations and functions lane-wise. Implement mask selection so
  vector conditionals do not inherit scalar lazy-branch semantics incorrectly.
- Implement explicit `::` SIMD application as a semantic requirement: either produce verified SIMD
  execution or a clear compile-time diagnostic explaining impurity, unsupported operations, lane
  mismatch, or target limitations.
- Support composition, partial application, lambda mapping, and reductions using the same callable
  metadata. Specify reduction order and floating-point reproducibility.
- Add aligned/unaligned load/store lowering internally while exposing ordinary collection/data APIs;
  provide a portable fallback only where it preserves the explicit `::` contract.
- Test multiple lane widths and hardware capability profiles in interpreter/emulation and compiler
  modes; never make program meaning depend on host vector width.

## Phase 7 — First-class bidirectional formats

- Add immutable `Format` values representing bidirectional relations plus explicit structured
  success/failure results. Implement empty formats and ordinary functional construction.
- Implement primitive byte/integer formats, `field format "name"`, constants/signatures, nested
  formats, fixed/prior-field repetition, conditions, choices, constraints, and `>>` composition.
- Decode structured formats into ordinary `data` and encode compatible `data`, resolving earlier
  fields and derivable values consistently in both directions.
- Implement `decode`, `encode`, and pure explicit `codec decode encode format`; distinguish
  representation transformations from logical-value transformations.
- Make expected mismatch, incomplete input, invalid field, and codec failure structured rather than
  exceptional. Preserve offsets/path context in failure values and rendered diagnostics.
- Add format reflection for components, names, contracts, directionality, size information where
  known, and tooling/generator use. Keep transport independent from formats.
- Defer general relational solving, arbitrary inversion, nondeterminism/backtracking, streaming,
  zero-copy, and async transport as permitted, without changing the compositional relation model.

## Phase 8 — Rules, rulesets, objects, and `ruleCycle`

### Contexts and rules

- Add persistent `Context` values, idempotent `raise`/`lower`, and transient `rise`/`fall` fronts over
  Boolean context expressions.
- Add first-class `Rule` values with unique optional CATEN clauses, defaults, inferred/explicit name,
  runtime active state, edge-trigger history, effect block, and implicit application context.
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
- Introduce first-class objects with stable identity, exported state, cycle membership, and implicit
  traversal contexts. Define deterministic deferred lifecycle semantics for `create`/`destroy` to
  prevent reentrant traversal.
- Extend reflection across rules, contexts, rulesets, cycle state, dependencies, and public object
  interfaces without exposing private bindings.

## Phase 9 — Modules, separate compilation, backend, and optimization

### Modules

- After Phase 0 settles syntax, implement module identity, file resolution, imports, explicit exports,
  private bindings, initialization order, duplicate/cyclic import diagnostics, and module-level
  contract/effect interfaces.
- Compile/cache modules independently using a versioned semantic interface containing public names,
  types/contracts, effects, formats, and reflection descriptors.

### Compiler backend

- Define a lowered typed/effect-checked IR shared with the interpreter. Preserve source maps and
  diagnostic spans through closure conversion, pattern/conditional lowering, cycles, formats, SIMD,
  and rule scheduling.
- Implement bytecode or the target chosen in Phase 0, a runtime ABI for all Caret value kinds, module
  linking, and executable CLI commands. Compiled and interpreted programs must share observable
  values, evaluation order, missing/null behavior, errors, effects, and reflection.
- Add differential tests that run every conformance example in both modes and compare stdout,
  stderr, exit status, values, and stable diagnostic codes/locations.

### Optimization

- Add constant folding only for proven-pure operations; then dead-code elimination, call
  specialization/inlining, closure/partial specialization, persistent-update elision, tail-call and
  cycle lowering, format specialization, SIMD lowering, and rule dependency indexing.
- Every optimization has an off switch and differential/property tests. It must not expose source
  order for unordered rules, merge null with missing, execute an unselected conditional branch, or
  change effect/contract behavior.

## Phase 10 — Tooling, standard library, and release hardening

- Build standard-library modules for identity, collection transforms/reductions, data manipulation,
  common contracts, formats/codecs, cycle helpers, and reusable rulesets using ordinary Caret where
  feasible.
- Expose formatter/parser services, semantic queries, inferred contracts/effects, reflection
  descriptors, module navigation, and unordered-rule warnings for editor integration.
- Extend the REPL for multiline constructs, modules, type/contract/effect inspection, compiled-mode
  parity, and structured display of data, formats, SIMD, rules, and diagnostics.
- Add fuzz/property tests for lexer/parser layout, Unicode, numeric finiteness, persistent
  collections, encode/decode round trips, optimizer equivalence, and scheduler stability.
- Establish performance suites for parsing, closures/partials, data updates, cycles, SIMD, formats,
  rule propagation, module compilation, startup, and REPL latency.
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
  closures, partials, contracts/effects, collections/data, and modules as relevant.
- Stage completion requires `./gradlew test`, `./test.sh`, all examples, differential tests available
  at that stage, and `git diff --check` to pass.

## Recommended next implementation step

Continue Phase 1 with fixed-precedence named infix calls (`CORE-INFIX-001`). Unified symbolic
callables and non-callable function references are now implemented by `CORE-INFIX-002` and
`CORE-CALL-004`.

1. Resolve the grammar boundary that distinguishes a named infix call from whitespace application,
   while retaining the specified precedence between comparison and addition.
2. Parse and evaluate named binary infix calls through the existing callable application path.
3. Require exactly the documented callable behavior and produce located diagnostics for invalid
   infix targets or arity.
4. Add precedence, associativity, partial-application interaction, multiline, and failure tests.
5. Extend the runnable example, native Caret test, language reference, and conformance evidence.
6. Then implement `>>` composition (`CORE-COMP-001`) on the shared callable representation.

## Explicit assumptions and allowed deferrals

- Java 21 and the tree-walking interpreter remain the reference implementation until differential
  conformance proves the compiler backend.
- Static analysis may be introduced incrementally, but runtime behavior must not claim a feature is
  implemented until its required static guarantees exist.
- The plan includes all normative initial requirements. Items explicitly marked by `LANGUAGE.md` as
  postponable remain deferred: advanced capture/ownership optimization, general format relation
  solving and streaming, flexible cycle state/`Break`/`Continue`, parallelism, numeric rule
  priorities, distributed/transactional rule cycles, dynamic ruleset unloading, debugger
  visualization, and formal rule conflict analysis.
- Deferred items still receive extension points and conformance notes so their later addition does
  not change the core value, effect, format, cycle, or scheduling models.
