# Phase 4 — Universal collections, fields, and mutability containers (completed)

[Roadmap index](../PLAN.md)

## Phase 4 — Universal collections, fields, and mutability containers (completed)

The completed foundation implements `Collection` as the general contract for Sequences and
Dictionaries. Static `^name`, ordinary `field "name" value`, exported blocks, and `dictPut` share
one String-keyed `Dictionary K V` representation; mixed shapes and duplicate keys are diagnosed.
The common provider-backed `keys`/`values`/`fields`/`size` operations, Boolean-or-missing guarantee
queries, matching reflection fields, `Natural`, and shape-neutral empty facts are implemented.
The steps below record the contextual, template, and representation work verified by the Phase 4
completion audit (#79).
The [Phase 4 Collection protocol revision](../spec/sections/06-01-collection-protocol.md#phase-4-collection-protocol-revision-implemented-with-deferred-extensions)
records the newer #55/#59 decisions and takes precedence over legacy implementation targets
for its covered behavior. The common protocol, Field tuple, contextual shape, settlement, lazy
transform, and strict-consumer foundations are implemented; deferred extensions retain separate
requirements and evidence.

### Agreed collection revision and deferrals

- Preserve implemented ordinary keys/values/fields/size access, guarantee queries and reflective
  equivalents, Natural size contracts, shape-neutral empty facts, keyed/keyless and Set/Dictionary
  contextual shapes, Field-contract tuples, and first-key settlement.
- Preserve implemented lazy map/filter, strict fold and short-circuit any/all, shape-changing transforms,
  first-entry duplicate handling, two-input zip tuples and zipWithKeys keyed construction.
- Retain internal construction and settlement without exposing unfinished Collections. Public
  addElement/removeElement/replaceElement, their construction-selection interface, and additional
  immutable-update syntax are deferred beyond Phase 4, with no later phase assigned. Preserve the
  existing persistent primitives; built-in construction does not require a public builder API.
- Apply ordinary lexical lazy-value establishment and inferred provider effects; retain stronger
  sequential guarantees without introducing automatic uniqueness tracking.
- Implement unified dot/bracket/getElement lookup, general equality-comparable keys, invalid-key
  contracts, and ordinary shadowing/partial-application lowering.
- Implement collection-value eager with complete enumeration before depth-first materialization,
  reflection removal, preserved containers/functions and sharing, and cycle/infinite diagnostics.
- Implement the revised contract-sensitive equality, contextual empty Collections, unordered
  multiplicity comparison, and the provisional forcing policy.
- Keep Dictionaries sorted with homogeneous sortable keys; general keyed Collections only require
  equality-comparable keys accepted by their contracts. Add positional Field tuple access while
  retaining the Field contract and existing reflective metadata.
- Analyze the identifiers needing with lookup and bind them against enumerated public keys before
  body execution. Matched members remain lazy; only established absence permits outer fallback.
- Preserve existing structural-template predicates. Add expected-template completion for named
  Collection constructors without changing ordinary `Template value` application from a predicate.
  Defer contextual predicate/constructor calls, general completely deferred computations, custom
  provider construction, concurrency and resumable handlers, and function-wrapping/nullary-invocation
  forms of eager beyond Phase 4. No particular later phase is assigned yet. There is no
  eagerWithRetry feature.
- Use existing failure behavior in Phase 4. The deferred handler design is not a prerequisite for
  built-in lazy Collections. Add the protocol/revision conformance evidence before claiming completion.

### Packed design and separate numeric/conversion prerequisites

The [approved packed design](../spec/sections/06-02-packed-layouts.md#phase-4-packed-layouts-implemented)
and its acceptance matrix clarify #77 and #78. The design and its prerequisite implementations
are complete; #79 audits the resulting evidence. Their delivery order was:

1. **Numeric foundations (#82, implemented):** format-independent Number/Real/Integer/Natural, exact arbitrary-precision
   integers, full signed/unsigned 8/16/32/64-bit domains, finite Float/Double, value-based
   representability, aliases, contextual literals, exact integer arithmetic/comparison, true `/`,
   truncating integer `div` at multiplicative precedence, precision diagnostics, and exact Java
   embedding round trips. This task consumes #77's design and must reconcile Natural with #65's
   protocol work without duplicating or weakening its contract.
2. **Explicit contract conversion (#83, implemented):** depends on #82 and #77. It supplies
   `(Contract) expression`, following-application precedence, identity-based target resolution,
   preserved non-contract grouping, strict declarations/checked holes, built-in conversion and
   recursive structural rules, and exact diagnostics. It selects the representation consumed by #78.
3. **Packed implementation (#78, implemented):** depends on #76, #77, #82, and #83. It supplies selected
   finite positional layouts for fixed numeric formats, one-byte Boolean, and fixed-size templates;
   retains declaration order, excludes nullable/variable-size payloads, materializes explicit lazy
   conversions, rejects incompatible appends, and integrates the common protocol with reference-mode
   parity. Physical layout metadata remains internal.

This order adds prerequisites without renumbering existing Phase 4 cards: #77 → #82 → #83 → #78.
#79's completion audit includes their transitive implementation evidence, runnable examples, and
conformance/diagnostic evidence. Numeric text parsing through the
new syntax, custom conversions (possibly related to future formats), Fractional/Complex, wider
named fixed formats, packed keyed Collections, and bit fields remain deferred.

### Collection protocol and literals

- Generalize existing sequences/dictionaries behind `Collection` and capability contracts while
  keeping persistent semantics. Dictionaries retain sorted homogeneous keys; general keyed
  Collections permit non-sortable keys and do not inherit a Dictionary sorting requirement.
- Complete contextual behavior for `[...]`, including shape-neutral empty values and inferred
  content contracts, without assigning a fixed container meaning to square brackets.
- Make each collection literal a hole-expression boundary: materialize its collection-constructor
  function before passing it to a surrounding call, while retaining nested collection structure in
  the outer constructor descriptor. This lets ordinary `template [..]` application receive the
  constructor without template-specific parsing or evaluation.
- Preserve semantic element contracts independently from physical representation metadata. Share
  metadata at collection level when possible and retain per-element metadata where required.

### Fields and dictionary-like collections

- Retain the `Field` contract and ordinary `field name value` construction while migrating Fields
  to contract-bearing tuples with key/value access at positions zero/one. Preserve existing
  reflective metadata and integrate `Field K V`, exports, static fields, and persistent updates.
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
- Preserve implemented [contextual completion of required fields](../spec/sections/06-06-templates-foundations.md#named-fields)
  as `TEMPLATE-OPTIONAL-001`: every constructed value contains every declared field. In a named
  Collection constructor with one unambiguous expected template, omitted holes directly written
  with `T~` or `T?~` materialize as `~` when their full clauses accept missing. Propagate context
  through annotated bindings, known parameters, declared results, nested literals, dynamic template
  keys, and exported-block shorthand. Aliases, fixed `~`, unconstrained holes, ambiguous overloads,
  predicates, and explicit conversions do not enable insertion. Reflect `defaultsMissing` metadata
  and cover nullability, wrong/extra fields, nesting, evaluation order, and source locations.
- Derive membership as the structural inverse of eligible constructors without invoking them.
  Repeated numbered holes impose candidate equality; numbering changes parameter order but not
  collection shape, and mixed numbered/unnumbered holes remain invalid.
- Validate statically known template membership and retain runtime checks when proof is unavailable.
  Diagnose invalid constructors and non-comparable fixed values with their settled template codes;
  reuse ordinary field, contract, and hole codes for malformed dynamic keys, invalid contracted
  holes, and mixed hole styles. Reconcile legacy duplicate-field diagnostics with the new
  first-entry construction rule. Preserve parser phase for malformed syntax and stable located
  behavioral diagnostics across semantic and runtime discovery.
- Reflect template structure as metadata on `Contract` descriptors. Permit shared metadata and
  packed-layout derivation only when optimized and optimization-disabled behavior is identical.

### Persistent updates and contained mutation

- Preserve existing persistent collection primitives. Additional immutable-update syntax and the
  new public element-operation functions are deferred; they are not Phase 4 completion gates.
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

Phase 4 completion is recorded by #79 after the common protocol, contextual templates, exact
numeric domains, explicit conversion, packed layouts, diagnostics, embedding, examples, and
optimization parity passed the baseline suites. The project cards #64–#78 and prerequisite cards
#82/#83 are Done. Public element-operation APIs, custom providers, computations, resumable handlers,
and callable `eager` forms remain deferred as stated above.
