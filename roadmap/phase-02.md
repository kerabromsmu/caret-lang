# Phase 2 — Types, contracts, effects, and ownership foundation

[Roadmap index](../PLAN.md)

## Phase 2 — Types, contracts, effects, and ownership foundation

Current status: Phase 2 is complete. Unary user-defined base and derived contracts are
implemented alongside runtime-kind contracts. Multiple bases are passed as one ordinary `[A B]`
collection. Binding, parameter, and result clauses acquire or validate membership at runtime.
Initial constraint inference and
generalized contract variables for named functions are implemented. The Phase 2 effect analysis
propagates named, aliased, higher-order, closure, composition, partial, overload, and recursive-call
effects, preserves unknown dynamic calls, and proves refinement
eligibility without exposing effect syntax. Proven unary Boolean callables now participate as
first-class predicate requirements in derived contracts and direct clauses, including through
aliases. Nullable/optional contract modifiers are implemented as first-class, identity-stable
contract unions without collapsing null into missing. The initial parameterized-contract slice uses
callable `Sequence T`, `Field K V`, and `Dictionary K V` constructors, with constructor metadata preserved through aliases, recursive
element validation, nesting, modifiers, identity semantics, reflection, and conservative outer-kind
inference. Environment-relative public effect identities, catalog aliases, declaration allowances,
callable constraints, guarded invocation bounds, and effectful arrow contracts are implemented.
Closed same-name overload sets now provide observational applicability, unique
most-specific runtime selection, generic fallbacks, persistent prefix and hole narrowing,
and distinct no-applicable/ambiguous diagnostics. Complete static selection remains planned; the
initial signature/reflection schema is implemented.
The effect pass includes eagerly captured fixed operands in partial expressions, treats
over-application through an unknown returned callable conservatively, and uses resolver-owned
symbol identities rather than source-span equality.
The `caret inspect` command exposes the resulting language-owned signature facts without executing
the source program. Conservative internal ownership tracking and its optimization-disabled
reference mode complete the Phase 2 storage-reuse foundation without changing Caret semantics.

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
- Extend the implemented ordinary contract/function parameterization as later value kinds arrive;
  keep `Collection` unparameterized and mutable `Container T` aligned with Phase 4
  rather than introducing a separate generic-type subsystem.
- Preserve implemented same-named overload sets and static normalization of parameter conjunctions,
  aliases, redundant nominal bases, `Any`, and absence alternatives, then order variants with the
  settled compiler-proven implication relation:
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
- Preserve eligible hole functions with language-owned collection-constructor descriptors retaining
  shape, nesting, fields, fixed captures, hole identities, and hole contracts. Keep ordinary eager
  capture and numbered-hole behavior; never expose Java AST or runtime implementation objects.
- Preserve implemented `template` as an ordinary callable over concrete collections and reifiable collection
  constructors. It produces an ordinary first-class `Contract` and rejects arbitrary callables or
  non-structural partial expressions with a stable located diagnostic.
- Preserve the shared diagnostic/error descriptor from `ErrorTemplate`: stable code, phase,
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

- Immutable value semantics are separate from the implemented internal uniqueness tracker. Ephemeral
  Sequence and Dictionary updates may reuse storage only before binding, parameter passing, capture,
  export, nesting under shared storage, or reflection makes an alias observable.
- Ownership remains an internal optimization. Differential tests establish that enabled execution
  matches the authoritative optimization-disabled persistent behavior. This foundation later supports
  efficient cycles, collection updates, SIMD memory, and compiled execution.
