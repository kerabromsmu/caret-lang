<a id="effects-and-callable-signatures"></a>
# Effects and Callable Signatures

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

<a id="purity-and-effects"></a>
## Purity and effects

Every function has an inferred effect set.

The compiler determines this set from:

* operations performed directly by the function;
* functions called by it;
* functions passed into and invoked by it;
* composed and partially applied functions used by it.

A function with an empty effect set is pure.

Effects propagate transitively through function calls.

For example, if:

```caret
(fs) read path =
  ...

parse text =
  ...
```

then:

```caret
load path =
  parse (read path)
```

has inferred effect set:

```text
{ fs }
```

<a id="effects-must-be-declared"></a>
## Effects must be declared

Effects may not appear implicitly in a function declaration.

If a function has no explicit effect contract, its allowed effect set is empty.

Therefore:

```caret
calculate x =
  ...
```

implicitly guarantees that `calculate` is pure.

If an effect is later introduced into its implementation, compilation fails until the declaration is explicitly changed.

For example:

```caret
load path =
  read path
```

is invalid when `read` has the `fs` effect.

The compiler should report the inferred undeclared effect and may suggest:

```caret
(fs) load path =
  read path
```

This rule prevents effects from propagating silently through the call graph.

<a id="effect-contracts"></a>
## Effect contracts

Effect contracts specify the maximum set of effects a function is permitted to have.

```caret
(fs) read path =
  ...

(net) download url =
  ...

(fs net) synchronize source target =
  ...
```

If the compiler infers an effect not included in the declared set, compilation fails.

For example:

```caret
(fs) load path =
  download path
```

is invalid if `download` requires `net`.

Effect contracts are upper bounds, not assertions that an effect necessarily occurs during every invocation.

A function declared:

```caret
(fs) cachedLoad path =
  ...
```

may execute without filesystem access on some or all paths.

<a id="explicit-purity"></a>
## Explicit purity

`pure` is equivalent to an empty allowed effect set.

```caret
(pure) calculate x =
  ...
```

is therefore stricter documentation of the same purity guarantee that an effect-less declaration receives by default.

Explicit `pure` is useful when purity is an intentional API-level guarantee.

It is particularly useful on higher-order function parameters:

```caret
map (pure) transform values =
  ...
```

This requires `transform` to be a pure function.

A unary Boolean function that is itself effectful cannot be used as a contract.

<a id="effect-identities-and-catalogs"></a>
## Effect identities and catalogs

An effect is a language-owned identity describing one category of observable behavior. Effect
identities form a flat set: one effect does not imply another, and repeated names or aliases of the
same identity normalize to one set member. `pure` is reserved declaration syntax for the empty
allowed set, not an effect descriptor.

The initial portable standard effect is `Output`, used by `print`. `StateRead` and `StateWrite` are
reserved portable identities for the [container model](07-state-containers-and-scoped-lookup.md#mutability-containers).
Other domains are supplied by
the active execution environment through an explicit effect catalog. For example, an environment
may expose `fs` and `net` for filesystem and network integrations, and the prototype test
environment exposes `TestReport`. These illustrative catalog names do not imply that the
corresponding capability exists in every environment.

Effect names occupy an environment-relative namespace separate from ordinary values, contracts,
module IDs, and lexical bindings. An ordinary binding cannot create or shadow an effect identity.
The environment catalog may provide aliases, but every alias retains the descriptor identity of its
target. A catalog entry that collides with a portable standard name is invalid unless it denotes
that exact standard identity. Ordinary Caret source may reference visible effect names but cannot
manufacture new identities in the initial language.

Catalog visibility follows the current module, root, staging, and sandbox environment. Visibility
permits a declaration to describe behavior; it does not provide a callable implementation,
capability, permission, or authority. Declaring `fs` cannot make a filesystem binding appear,
expand a sandbox projection, or turn an unavailable operation into an authorized one. Failure of an
operation for lack of authority remains its ordinary structured operation failure.

<a id="mixed-declaration-clauses"></a>
## Mixed declaration clauses

A parenthesized declaration clause may contain both value requirements and an effect allowance.
Each term is resolved independently against the visible contract/refinement namespace and the
environment-relative effect catalog. A contract or verified refinement contributes a conjunctive
value requirement; an effect identity contributes to one effect allowance. Source order does not
change this classification or its meaning. Duplicate contract requirements normalize under the
ordinary contract rules, and repeated effect names or aliases normalize by effect-descriptor
identity.

If a name resolves in both namespaces, analysis reports the located semantic diagnostic
`AMBIGUOUS_CLAUSE_NAME`; neither namespace takes precedence. If it resolves in neither namespace,
analysis reports `UNKNOWN_CLAUSE_NAME`. `pure` is the reserved spelling of an empty effect allowance,
so combining it with any named effect is invalid. Nullable and optional modifiers apply only to
contract terms; applying `?` or `~` to `pure` or an effect name is invalid. A parameterized contract
constructor consumes only its contract arguments: an effect term cannot be interpreted as one of
those arguments. These errors are reported at the offending term while retaining the clause span
for context.

The stable diagnostic taxonomy is:

* `CONFLICTING_EFFECT_ALLOWANCE` when `pure` is combined with one or more named effects;
* `INVALID_EFFECT_MODIFIER` when `?` or `~` is applied to `pure` or an effect term;
* `EFFECT_AS_CONTRACT_ARGUMENT` when an effect is supplied where a parameterized contract expects
  a contract argument;
* `EFFECT_CONSTRAINT_REQUIRES_CALLABLE` when an effect-constrained parameter or assignment receives
  a non-callable value;
* `EFFECT_ALLOWANCE_EXCEEDED` when a known callable upper bound or an inferred function effect set
  is not a subset of its declared allowance; and
* `UNKNOWN_CALL_EFFECTS` when the value is callable but no invocation-effect upper bound is
  available, whether discovered while checking an effect-constrained value or while invoking it.

The code describes the failure independently of when it becomes knowable. A statically established
failure has phase `SEMANTIC`; the same failure discovered only at a dynamic value boundary has phase
`RUNTIME` and retains the same code.

Locations are deterministic. For an invalid clause term, that term is primary and the enclosing
clause is retained as context. In a `pure` conflict, the later conflicting term is primary and the
earlier term is a related location. For a parameter or assignment constraint, the supplied or
assigned value is primary and the constraining clause is related. For an inferred function effect
outside its allowance, the operation that introduces the disallowed effect is primary and the
function declaration's allowance is related. Diagnostic details preserve the unexpected and
allowed effect descriptor identities; rendering exposes an effect name only when that name is
visible in the current environment.

The position of the clause determines what its two parts constrain:

* Before a named function, value requirements constrain the function result and the effect
  allowance is the function's declared maximum effect set. For example, `(pure Int) calculate ...`
  declares a pure function whose result satisfies `Int`.
* Before a parameter, value requirements constrain the parameter value. When effect terms are
  present, that value must also be callable and have a known effect upper bound that is a subset of
  the stated allowance. Thus `(pure) transform` accepts only a callable with an empty bound, while
  `(fs) transform` also accepts a pure callable.
* Before an assignment, value requirements constrain the assigned value. When effect terms are
  present, the assigned value must likewise be callable with a known upper bound contained in the
  allowance. The allowance does not constrain evaluation of the initializer and does not describe
  the callable's eventual result.

Omitting effect terms from a parameter or assignment clause imposes no callable-effect constraint;
it does not implicitly require the value to be pure. A later invocation still requires a known
effect upper bound under the ordinary `UNKNOWN_CALL_EFFECTS` rule. By contrast, omitting an effect
declaration from a named function continues to declare the function pure as specified above.

The analyzed form of a clause therefore has separate `valueRequirements` and optional
`effectAllowance` components while preserving term and clause source spans. The current syntax AST
may retain unresolved clause terms until semantic analysis performs namespace-aware classification;
it must not encode a silent contract-first or effect-first precedence. Callable result-contract
metadata is independent: an assignment such as `(pure Int) callback = value` constrains `callback`
itself to satisfy `Int`, not the result of calling it.

<a id="callable-signature-metadata"></a>
## Callable signature metadata

Every callable has a language-owned signature scheme shared by semantic analysis, interpreted and
compiled invocation, derived callables, and later reflection. A scheme records the ordered
parameter requirements, result guarantees, quantified contract variables and their relationships,
declared effect allowance, inferred actual effects, remaining arity, and source provenance. This is
semantic metadata, not a Java runtime object exposed to Caret.

Declarations and inference remain distinct. A parameter declaration is a precondition promised by
the callable interface. The implementation's inferred parameter needs must be satisfied by that
declaration; analysis must reject an implementation that needs a stricter parameter instead of
silently strengthening the declared interface. An inferred result must imply every declared result
requirement, and inferred effects must be a subset of the declared allowance.

Inside the defining module, analysis retains stronger inferred result guarantees and a narrower
inferred effect set for checking, optimization, and diagnostics. Across a module boundary, an
explicit declaration is the stable interface: consumers may rely on its result guarantees and
declared effect allowance, but not on stronger implementation facts. When a component has no
explicit parameter or result declaration, its generalized inferred component is its interface.
Omitting a function effect declaration instead supplies the explicit empty allowance described
above; inference must validate against it. Parameter requirements are not weakened or strengthened
at either boundary.

Unresolved contract variables are universally quantified after the complete recursive definition
group is analyzed. Each external use instantiates them freshly, while ordinary aliases preserve the
scheme and all parameter/result relationships. Once a callable use is partially applied, that
instance retains its substitutions and is not generalized again.

<a id="arrow-signature-contracts"></a>
### Arrow-signature contracts

Implementation status: the prototype implements right-associative, exact-arity arrow contracts,
inline or named clause use, structural predicate checking, contravariant parameters,
covariant results, explicit visible effect allowances, and standalone contiguous numbered variables.
Variables shared across an enclosing declaration header, complete substitution through derived
callables, and the full conservative overlap proof for overloads remain planned.

A callable signature is an ordinary first-class structural contract written with a bracketed
parameter-requirement list and a right-associative arrow:

```caret
[Int] -> Int
[Int Text] -> (fs Boolean)
[] -> Text
```

The bracketed left side determines exact remaining arity. Each element is one parameter position;
a parenthesized element contains a conjunction of requirements for that one position. The right
side is either one result requirement or a parenthesized mixed result/effect clause. It must always
contain at least one result requirement; use `Any` explicitly for an unconstrained result. An
omitted effect allowance means pure, exactly as for a named function. Existing mixed-clause rules
apply to an explicit allowance such as `(fs Boolean)` or `(Boolean fs)`.

The arrow is recognized as a signature contract only when its direct left operand is a bracketed
list whose elements are parsed as contract requirements. Elsewhere `[...]` remains an ordinary
collection, and an identifier or contracted-parameter header before `->` remains a lambda.
Signature arrows are right-associative, so callable results may be described recursively;
parentheses disambiguate a nested signature where necessary. Constructing or testing an arrow
contract is pure and never invokes the candidate callable.

Arrow signatures may be named like other contract values:

```caret
IntTransform = [Int] -> Int
```

They participate in ordinary declaration clauses:

```caret
apply ([Int] -> Int) transform (Int) value =
  transform value

([Int] -> Int) double = x -> x * 2
```

The outer clause constrains the callable value. A non-arrow value clause such as `(Int) callback`
continues to constrain `callback` itself, not the result of invoking it. Nullable and optional
modifiers apply to the complete grouped arrow contract under the ordinary modifier rules.

Numbered holes have a separate contextual role as contract variables inside declaration headers
and arrow-signature contracts. `_1`, `_2`, and later indices denote universally quantified contract
variables; repeated occurrences preserve the same parameter/result relationship. Within a named
function declaration, their scope is the complete result and parameter header. Within a lambda,
their scope is all parameter clauses and the inferred result of that lambda. Otherwise the nearest
standalone arrow-signature expression owns them. They do not escape into later declarations or the
enclosing lexical block.

For example:

```caret
(Sequence _2) map ([_1] -> _2) transform (Sequence _1) values =
  ...
```

generalizes one input-element contract and one output-element contract, then instantiates both
freshly at every use of `map`. Variables may appear as ordinary constructor arguments and within
conjunctions such as `(_1 Number)`. All indices from `_1` through the highest used index must occur;
their first occurrence order need not match numeric order. An unnumbered `_` is invalid in contract-
variable context, and ordinary expression holes retain their existing partial-application meaning.

A standalone generic arrow contract quantifies its own variables. A candidate then satisfies it
only when the candidate scheme is at least as general; a monomorphic `Int -> Int` callable does not
satisfy `[_1] -> _1`. Within a generalized enclosing header, the shared variables are instantiated
for that enclosing callable use, so a concrete `Int -> Text` transform may satisfy the instantiated
`[_1] -> _2` parameter of one `map` call.

<a id="callable-contract-satisfaction-and-implication"></a>
### Callable-contract satisfaction and implication

A candidate callable satisfies an arrow-signature contract only when its language-owned metadata
proves all of the following:

1. its remaining arity exactly matches the parameter list;
2. each required parameter domain implies the candidate's corresponding accepted domain;
3. the candidate's result guarantee implies the required result;
4. its known effect upper bound is a subset of the signature allowance; and
5. its generalized-variable relationships can be instantiated without breaking those conditions.

Parameter checking is therefore contravariant, while result checking is covariant. An unavailable
effect bound, unknown contract relationship, or insufficiently general variable scheme does not
satisfy the contract. Used as an ordinary predicate, the arrow contract returns `false`; used at a
binding, parameter, or result boundary, it produces that boundary's ordinary located contract
failure. Checking is observational and structural: it does not call refinements, invoke the
candidate, or acquire nominal membership.

An overload set satisfies an arrow contract only when at least one surviving variant provably
accepts the complete required parameter domain. In addition, every variant that may be selected for
any permitted argument tuple must have a compatible result and effect bound. Unknown overlap is
treated as possible overlap. The initial proof is conservative and does not combine several
partial variant domains to claim coverage; a single variant, normally a generic fallback, must
cover the whole required domain.

Implication between two arrow contracts uses the same exact-arity, contravariant-parameter,
covariant-result, variable-instantiation, and effect-subset rules. This participates in ordinary
constraint normalization and overload specificity without executing predicates. Each evaluation
of an arrow-signature expression still constructs a fresh contract identity under the normal
contract-equality rule; structurally equivalent descriptors may mutually imply one another without
being equal, while aliases preserve identity.

<a id="callable-reflection-schema"></a>
### Callable reflection schema

Reflecting a callable produces an immutable, non-callable `Function` reference with a fixed public
shape:

```text
kind        "Function"
name        String or ~
remaining   Number
signature   Signature
variants    Sequence Signature
```

`name` is the original declaration name when that name is visible in the current environment;
otherwise it is `~`. An alias does not rename the target in reflection. A direct prefix partial of
a named function or overload retains that visible declaration name, while a hole-expression
partial, composition, lambda, or other anonymous derived callable reports `~`. `remaining` is the
number of parameters in the callable's current public interface. `variants` is empty for an
ordinary callable and contains the surviving exact variant signatures for an overload set or
overload partial.

The nested `Signature` has these fields:

```text
kind        "Signature"
parameters  Sequence Parameter
result      FunctionResult
effects     FunctionEffects
variables   Sequence SignatureVariable
```

`parameters` contains only parameters still accepted by this callable, in application order. Each
`Parameter` has `kind = "Parameter"`, a zero-based `position` in that current list, `name` or `~`,
effective `requirements`, explicit `declared` requirements or `~`, and visible `inferred`
requirements or `~`. A derived hole parameter has no declaration of its own, so `declared` is `~`
even when its effective requirements were synthesized from declared target positions.

`FunctionResult` has `kind = "FunctionResult"`, effective `guarantees`, explicit `declared`
guarantees or `~`, and visible `inferred` guarantees or `~`. `FunctionEffects` has
`kind = "FunctionEffects"`, the currently usable `upperBound`, an explicit `declared` allowance or
`~` for a derived callable, and visible `inferred` effects or `~`. A known empty sequence means no
requirements or effects as appropriate; in particular, `upperBound = []` means proven pure.
`upperBound = ~` means that no invocation bound is available and therefore remains subject to
`UNKNOWN_CALL_EFFECTS`.

Requirement sequences contain immutable, non-callable `ContractRef` metadata rather than the live
callable contract binding. Effect sequences likewise contain non-callable `Effect` descriptors.
Both preserve their underlying language-owned descriptor identity and expose `name` only when that
name is visible; a hidden name is `~`. Reflection therefore describes a hidden identity when it is
part of a visible signature without granting access to its private binding, predicate invocation,
catalog entry, implementation, or authority.

Generalized-variable occurrences use `VariableRef` values containing a zero-based `index` into
`variables`. Each `SignatureVariable` definition contains that `index` and its normalized
requirements. Indices are assigned by first occurrence in the signature. They are local references,
not globally meaningful contract identities; canonical numbering makes structurally equivalent
signature schemes compare equal modulo variable renaming.

Every field above is present. `~` denotes information unavailable in the current reflective
environment; fields are not dynamically omitted. Inside the defining module, inferred fields expose
the stronger facts retained by analysis. Across a module boundary they are `~` when an explicit
declaration is the public interface. When an inferred component is itself the undeclared public
interface, that component remains visible. The effective requirement, guarantee, and upper-bound
fields always contain exactly the facts on which code in the current environment may rely.

An overload's top-level `signature` is its conservative summary. Its parameter requirements are
`~` while several alternative domains survive; applicability consumers inspect `variants` rather
than treating those alternatives as a conjunction. Common result guarantees and the unioned effect
bound remain available. When narrowing leaves one variant, the summary is that specialized exact
signature, while `variants` continues to identify the surviving overload variant.

Function references compare by target callable identity as already specified. Signature,
parameter, result, effect-summary, and variable metadata compare structurally after canonical
variable numbering. `ContractRef` and `Effect` values compare by their underlying descriptor
identity. Reflecting the same reference again returns the same view; a newly narrowed partial is a
new callable identity with a new immutable view.

Callable reflection never exposes capture names or values, bound partial values, implementation
kind, source spans, native origin, Java objects, or capability handles. Authorized semantic-code
reflection is the separate mechanism for inspecting code and still obeys module, sandbox, and
authority visibility.

<a id="partial-and-composed-signatures"></a>
### Partial and composed signatures

Supplying an ordinary prefix argument validates that parameter, specializes the current signature
variables, removes the filled parameter, and preserves the specialized result and invocation-effect
metadata. A hole partial orders its public parameters by hole order. Every occurrence of one
repeated numbered hole denotes the same future argument, so its public requirement is the
normalized conjunction of the requirements at all target positions.

Fixed operands in a hole partial are evaluated and checked when the partial is constructed. Effects
from evaluating the callable expression, a composition operand, or a fixed operand belong to that
construction expression. They are not repeated in the resulting callable's effect bound. The
resulting callable describes only effects that may occur when its remaining parameters are supplied
and its body or selected overload is invoked.

For `left >> right`, the derived signature retains the parameters of `left`, the result guarantee
of `right`, and the descriptor-identity union of both invocation-effect bounds. Analysis unifies
the left result with the right parameter where possible. A statically proven incompatibility is a
located semantic error at the composition; an undecidable relationship retains the ordinary right
parameter check at invocation. An unavailable operand effect bound makes the composed invocation
bound unavailable. The composition may remain a value, but completing its invocation then follows
the ordinary `UNKNOWN_CALL_EFFECTS` rule.

<a id="overload-signature-summaries"></a>
### Overload signature summaries

An overload set and every narrowed overload partial preserve the complete signature of each
surviving variant. Parameter alternatives are never collapsed into one conjunctive requirement,
because that would describe a different accepted domain. The generic summary exposes their common
remaining arity, only result guarantees implied by every survivor, and the descriptor-identity
union of every survivor's invocation-effect upper bound.

Supplying an argument specializes variables and removes inapplicable variants, so the common result
and effect summary may become narrower. At full arity, the selected variant supplies its exact
signature and validates its result normally. If any surviving variant lacks an effect upper bound,
the generic overload bound is unavailable until narrowing removes every such variant or selection
chooses a variant with a known bound.

Every callable has language-owned metadata containing either an upper bound on the effects it may
perform or an explicit unavailable-bound state. Host callables and environment-provided operations
must supply a known bound at their boundary. A dynamically obtained or conservatively derived
callable may be invoked only when its bound is known and is a subset of the caller's declared
allowance. If no bound is available, analysis reports the located semantic diagnostic
`UNKNOWN_CALL_EFFECTS`; there is no wildcard declaration and no attempt to permit the action first
and inspect its effects afterward.

<a id="effect-inference-and-tooling"></a>
## Effect inference and tooling

Effect inference is mandatory even when an explicit effect contract is present.

The compiler must infer the actual effect set and verify:

```text
actual effects ⊆ declared allowed effects
```

An omitted declaration and explicit `pure` both provide the empty allowed set. The inferred set is
computed independently and never enlarged merely because a broader allowance was written. Catalog
aliases are compared by descriptor identity during this subset check.

IDE tooling should expose inferred effects directly at function declarations.

For example, the source:

```caret
load path =
  ...
```

may temporarily display an inferred annotation such as:

```text
⟨fs — undeclared⟩ load path =
```

while editing.

For a pure function, tooling may display:

```text
⟨pure⟩ calculate x =
```

Such annotations are IDE presentation only and are not part of Caret source syntax.

The compiler should also provide a way to inspect fully inferred contracts and effects in plain-text environments.

---
