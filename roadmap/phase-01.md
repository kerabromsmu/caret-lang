# Phase 1 — Front end, binding semantics, and unified callables

[Roadmap index](../PLAN.md)

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
lambdas and right-associative low-precedence `$` application now lower to
the ordinary callable path. Language-owned callable signature metadata and its safe reflective
projection are implemented for named functions, built-ins, prefix partials, compositions, and
closed overload sets. Exact-arity arrow contracts now work as named or inline structural
predicates with explicit visible effect allowances. Declaration-wide variables and substitution
through prefix and hole partials are implemented; complete overload-domain proofs remain unfinished.
Derived callable signatures now project repeated and reordered holes, specialize composition
bridges, separate construction effects from invocation bounds, and preserve projected overload
survivor signatures with conservative summaries.

### Layout and expressions

- Preserve the structured logical-line engine for grouped expressions, dynamic lookups, multiline
  arguments, lambdas, collection literals, and indented bodies. Future `format`, `cycle`, `rule`, and
  `sandbox` calls use these ordinary application/layout rules without parser-specific headers.
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
