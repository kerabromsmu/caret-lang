<a id="contracts-inference-and-dispatch"></a>
# Contracts, Inference, and Dispatch

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

<a id="contract-foundation-currently-implemented"></a>
## Contract foundation currently implemented

The prototype provides first-class unary contracts matching its existing runtime kinds: `Any`,
`Number`, `String`, `Boolean`, `Null`, `Missing`, `Function`, `Collection`, `Sequence`, and `Dictionary`.
Calling a contract tests membership and returns a Boolean:

```caret
Number 42
String "text"
```

Simple clauses constrain bindings and function parameters dynamically:

```caret
(Number) count = 1
add (Number) left (Number) right = left + right
```

Arguments are checked as they fill parameters, including during partial application. Contracted
initializers are checked before their bindings commit. `type Number` and `(@Number).kind` report
`"Contract"`, and `(@Number).name` reports `"Number"`.

The unary `contract` function constructs nominal contracts. `contract ~` creates a base contract,
`contract A` derives from one contract, and `contract [A B]` derives from several contracts packaged
in one ordinary collection argument. Explicit binding and parameter clauses acquire nominal
membership while checking built-in base constraints; leading function clauses check and attribute
results. Unannotated named functions infer parameter and result contracts from their values,
operations, calls, and surrounding context. When callable contracts remain unresolved, they are
generalized and each external use receives a fresh instantiation. Ordinary non-callable bindings
must instead resolve from their initializer or context. An actual use that still leaves a required
contract variable unresolved is a located compile-time ambiguity error.

Explicit callable declarations remain distinct from implementation inference. An implementation
need must be guaranteed by the declared parameter domain; it cannot silently narrow that public
domain. Inferred results incompatible with an explicit result clause are rejected with a located
`INCOMPATIBLE_CONTRACTS` diagnostic.

The semantic analyzer also computes an initial effect summary for named functions. It propagates
known effects through direct named calls, includes effects from the fixed subexpressions captured
eagerly while constructing partials, and records an
unknown-call marker when dynamic invocation prevents a purity proof. This internal summary can
prove that a prospective refinement is unary, Boolean-returning, and pure. Environment-relative
effect identities, declaration allowances, callable constraints, and effectful arrow contracts are
implemented. Complete higher-order propagation and broader effect tooling remain planned. Proven
predicates are implemented as first-class refinement
requirements in `contract` construction and direct clauses, including through ordinary aliases.
Contract equality is identity-based: aliases of one descriptor compare equal, while every separate
evaluation of `contract` creates an unequal descriptor even when its requirements are identical.
Names and reflective metadata do not participate in equality. Contract reflection exposes `name`,
`bases`, and language-owned refinement
`requirements`.

Nullable and optional contract modifiers are implemented as adjacent postfix contract syntax:

```caret
Number?     // Number or null
Number~     // Number or missing
Number?~    // Number, null, or missing
```

These are first-class unary `Contract` values and may be called, stored, aliased, reflected, or used
in binding, parameter, and result clauses. Whitespace distinguishes modification from application:
`Number?` is one modified contract expression, while `Number ?` calls `Number` with null. Only the
canonical suffix order is accepted; `T~?`, repeated suffixes, and longer combinations are invalid
contract syntax.

Null and missing remain separate union states. `T?` does not accept missing, and `T~` does not
accept null unless `T` itself already accepts that state. Adding a state already accepted by the
base normalizes to the base contract, so `Null? == Null` and `Any?~ == Any`. Other modified
contracts are cached by base identity and admitted states: repeated evaluation over the same base
has stable identity, while modifiers over distinct nominal contracts remain distinct. Reflection
uses the canonical modified name and reports the wrapped contract in `bases`.

Applying another modifier to a grouped modified contract flattens both modifiers onto the original
base before normalization. `(Number?)~` and `Number?~` therefore denote the same descriptor and
reflect the same `Number` base. For an ordinary value, a modified nominal clause checks or acquires
the underlying nominal membership; the union wrapper does not hide existing attributed membership.
Null and missing alternatives satisfy the union without acquiring nominal membership.

Inside a clause, the same suffixes may modify a verified refinement requirement. An admitted null
or missing value satisfies that requirement without invoking the predicate; ordinary values still
run the predicate. Initial inference propagates built-in null/missing alternatives through named
calls and retains runtime checks where user-contract or refinement membership is not statically
decidable. An explicitly nullable or optional parameter cannot be used directly by an operation
that rejects one of its declared alternatives; without flow-sensitive narrowing, that contradiction
is a semantic incompatible-contract diagnostic.

A modifier target known not to be a contract is rejected during semantic analysis before execution.
When the target's contract status depends on runtime evaluation, failure is instead a located runtime
`NOT_A_CONTRACT` diagnostic. It never reports Java AST or implementation details.

The general unary `Collection` contract and the initial `Sequence T` parameterized-contract form are
implemented as described in the collection section below. General parameterization, complete static
inference/proof, and contextual collection representation selection remain planned.

In the current prototype, physical indentation directly defines a multiline function body. The
planned [layout modifiers](01-source-layout-and-diagnostics.md#planned-layout-baseline-modifiers)
will first translate physical indentation into effective
logical indentation; the ordinary block rules will then consume that logical indentation. If a
body contains exported bindings (`^`), calling the function returns the immutable named `Collection`
specified in the [collections document](06-collections-fields-and-templates.md#collections-and-lexical-scopes).
It is observationally equivalent to the explicit named literal containing those exports. Otherwise
the prototype returns the final expression or assigned value.

A zero-argument function is evaluated when its name is read. Use reflection syntax to refer to the
function itself without invoking it:

```text
factory =
  ^value = 42

factory          // calls factory and produces its named Collection
@factory         // reflects the factory function itself without calling it
```

This rule is not limited to zero-argument functions. `@function` refers to the function binding
without invoking it regardless of the function's arity. The result is a non-callable metadata
Dictionary whose fields include `kind` and `remaining`; `@function:` restores the callable.
Metadata dictionaries compare structurally by their public fields.

<a id="contracts-type-derivation-and-collections"></a>
## Contracts, Type Derivation, and Collections

<a id="overview"></a>
### Overview

Caret does not distinguish fundamentally between types, interfaces, refinement types, and value contracts.

A type is a **contract**.

A contract behaves as a predicate over values:

```text
Contract : Value -> Boolean
```

A value satisfies a type when it satisfies the corresponding contract.

Contracts may also derive from other contracts. This creates a hierarchy of logical implication:

```text
Int -> Number -> Comparable -> Eq
```

meaning:

```text
Int x => Number x
Number x => Comparable x
Comparable x => Eq x
```

Behavior is not stored inside contracts.

Operations are ordinary Caret functions whose parameter contracts determine which values they accept.

Collections use one common literal syntax:

```caret
[1 2 3]
```

The literal itself does not mean `List`, `Array`, `Set`, or another particular representation.

Its specific collection contract and representation are determined by inference and surrounding constraints.

---

<a id="contracts"></a>
### Contracts

<a id="contract-definition"></a>
#### Contract definition

`contract` is an ordinary unary Caret function that constructs a contract. It has no declaration
grammar or parser-level construction form. Its one argument is `~`, one base contract, one
predicate, or one ordinary Collection of requirements. Consequently:

```caret
Number = contract [Eq Comparable Arithmetic]
```

is an ordinary assignment whose right-hand side calls `contract` once with a single Collection.
Static knowledge of nominal contract construction attaches to the resolved language-owned
`contract` callable identity, never merely to an identifier spelled `contract`.

A base contract with no additional value restriction may be defined as:

```caret
Eq = contract ~
```

Here `~` means that the contract introduces no additional value predicate of its own.

Membership in such a base contract is established through derivation from that contract.

For example:

```caret
Eq = contract ~

Number = contract Eq

Int = contract Number
```

establishes:

```text
Int -> Number -> Eq
```

An `Int` therefore satisfies all three contracts.

---

<a id="contracts-as-predicates"></a>
#### Contracts as predicates

Every contract may be used as a Boolean membership predicate:

```caret
Int value
Number value
Eq value
```

Conceptually:

```text
Int value -> Boolean
```

A contract may therefore be used anywhere an ordinary pure unary Boolean function is appropriate.

The compiler may determine membership statically when sufficient type information is available.

It does not need to execute a runtime predicate when derivation already proves membership.

---

<a id="contract-inference-and-nominal-ascription"></a>
#### Contract inference and nominal ascription

An explicit contract clause confirms membership already carried by a value. Otherwise it attempts
to establish the named nominal membership by checking every inherited contract and refinement. A
successful check produces an attributed value with that membership; existing aliases remain
unchanged. A failed statically decidable check is a compile-time error, while an undecidable check
is retained for runtime and produces the same located contract-violation diagnostic on failure.
Contract membership participates in checking and dispatch but not structural equality or hashing.
Explicit clauses and equivalent ascription boundaries are the only operations that acquire nominal
membership. Merely testing a contract as a predicate, considering an overload candidate, or proving
that a value satisfies the nominal contract's bases and refinements does not attribute the value or
make that nominal identity observable.

Inference preserves relationships created by value flow. For example, `identity value = value` has
one shared contract variable for its parameter and result. Several simultaneous requirements form
an anonymous conjunction: requiring both `A` and `B` does not create or grant membership in a
nominal `AB`. Alternative branches retain only the contract guarantees common to every branch;
anonymous union contracts are not inferred.

Constraints are collected across the complete lexical block before ambiguity is diagnosed, so a
later use may resolve an earlier intermediate binding. Inference follows runtime operator semantics:
`not`, conditionals, `and`, and `or` accept the established Boolean/null/missing truth values, while
`+` may be numeric addition or string concatenation. When that relational choice cannot yet be
proved, inference retains an unresolved constraint instead of incorrectly assuming `Number`.

Unannotated named functions generalize unresolved contract variables after their complete recursive
definition group has been analyzed. Recursive uses inside that group are monomorphic; distinct
external uses instantiate the generalized variables independently. Polymorphic recursion requires
explicit contracts. An instantiation that remains ambiguous when a concrete contract is required
is a compile-time error at the use site, rather than at the generic declaration.

<a id="built-in-operator-contracts-and-coercions"></a>
#### Built-in operator contracts and coercions

Built-in symbolic operators are pure ordinary callables whose signatures participate in the same
partial application, overload narrowing, composition, and reflection rules as named functions.
Unary syntax and lazy control syntax retain their dedicated parsing, but use the same contract
facts during analysis.

The initial scalar matrix is:

| Form | Accepted operands | Result guarantee |
| --- | --- | --- |
| unary `-` | `Number` | `Number` |
| binary `-`, `*`, `/`, `%` | `Number`, `Number` | `Number` |
| `<`, `<=`, `>`, `>=` | `Number`, `Number` | `Boolean` |
| `==`, `!=` | `Eq`, `Eq` | `Boolean` |
| `not` | `Boolean?~` | `Boolean` |
| `and`, `or` | `Boolean?~`, `Boolean?~` when evaluated | `Boolean` |

There is no implicit numeric parsing, Boolean-to-number conversion, or null/missing propagation.
The generic numeric variants guarantee only `Number`. A value may carry a narrower nominal numeric
membership such as `Int`, but an arithmetic result does not automatically acquire or preserve that
identity. A narrower guarantee requires an explicit checked result boundary or a future specialized
operator variant.

`+` is a closed overload set with these variants:

```text
[Number Number] -> Number
[String String] -> String
[String Any]    -> String
[Any String]    -> String
```

The exact `String`, `String` variant is more specific than both broad concatenation variants and
therefore prevents an ambiguity for two strings. If either operand is a string, the other operand
is converted with Caret's deterministic, stack-safe, language-owned value rendering; Java
`toString`, host identity text, and native implementation details are never used. Two non-string
operands must satisfy the numeric variant.

Ordering remains numeric in the initial matrix. String ordering and ordering for user contracts use
named functions until the standard environment explicitly introduces additional closed symbolic-
operator variants. Merely satisfying a user-defined contract named `Comparable` does not inject an
implementation into `<`.

Standard callable bindings are extensible overload sets unless a future declaration mechanism
seals them. Same-name Caret definitions add contract-specific variants, and ordinary most-specific dispatch selects them
both for direct conversion and recursive collection conversion. Every specialization must return a
String when extending `toString`. Module callables use the same binding model once imports are
implemented; no sealing syntax is implemented yet.

<a id="structural-equality-capability"></a>
##### Structural equality capability

`Eq` in the operator matrix is the standard structural capability descriptor used by the built-in
equality operators. Scalar values, null, missing, contract values by descriptor identity,
structurally comparable metadata dictionaries, and language-owned metadata descriptors
satisfy it. Immutable collections satisfy `Eq` only when every recursively reachable
member does. Planned containers satisfy it by stable container identity, without reading their
contents.

A live callable does not satisfy `Eq`, and neither does a structure containing one. A statically
known violation and a violation discovered during recursive runtime comparison use
`CALLABLE_EQUALITY`, preserving the existing diagnostic rather than converting it into ordinary
inequality. Two Eq values with unrelated concrete kinds are nevertheless valid operands and compare
false. Attributed nominal membership does not otherwise affect structural equality.

The standard `Eq` descriptor is part of the execution environment used to type the built-in
operators. A lexical contract binding that shadows its public name does not rewrite those operator
signatures or manufacture standard Eq membership.

<a id="truth-operations-and-evaluation"></a>
##### Truth operations and evaluation

`Boolean?~` is exactly the established truth domain: Boolean, null, or missing. Null and missing are
falsey. `not`, `and`, and `or` normalize their results to `Boolean`; they do not preserve null or
missing. `and` evaluates its right operand only when the left is true, and `or` evaluates it only
when the left is falsey. Conditional conditions use the same domain, evaluate only the selected
branch, and retain the ordinary common-guarantee join for their result.

Other eager binary operators evaluate operands from left to right before dispatch. All variants in
this initial matrix have an empty effect set.

<a id="operator-constraint-inference-and-failures"></a>
##### Operator constraint inference and failures

An occurrence of `+` retains its closed numeric/string alternatives while constraints are collected
across the complete lexical block. A known string operand selects a concatenation alternative; two
known Number operands select numeric addition. Expected result contracts and later uses may resolve
an earlier choice. Analysis neither defaults an unresolved choice to Number nor generalizes a
hidden `supports +` constraint. If several alternatives remain at the end of analysis,
`AMBIGUOUS_CONTRACT` identifies the operator and relevant operand constraints.

Statically known operands that match no variant produce `INCOMPATIBLE_CONTRACTS` at the smallest
incompatible operand or operator span, with related constraint locations where useful. Dynamically
obtained values retain the existing located runtime operand diagnostics. Division or remainder by
a provably zero divisor and a provably non-finite arithmetic result may be rejected statically;
otherwise `DIVISION_BY_ZERO` and `NON_FINITE_RESULT` remain the runtime diagnostics at their
established locations.

Future concrete numeric contracts such as fixed-width integers and floats must add explicit
operator variants specifying accepted pairs, result contracts, overflow, division, and conversion
rules before those combinations are implemented. This initial matrix defines no implicit widening,
signedness conversion, or mixed-representation promotion.

The prototype implements this initial operator matrix, including four reflected closed `+`
variants, language-owned recursive rendering for concatenation, recursive `Eq` eligibility,
normalized lazy truth results, and relational `+` results retained through direct named-function
calls and later ordinary-binding constraints. More general relational propagation through nested
compositions remains conservative rather than selecting a numeric default.

<a id="contract-declaration-and-identity"></a>
#### Contract binding and identity

Bindings whose values are contracts are predeclared throughout their lexical block where the
ordinary declaration rules require it, so their bases may use forward references. Direct and
indirect contract-derivation cycles are compile-time errors.
The prototype implements this as a checked identity graph: multiple and redundant bases are
accepted, diamond derivation retains transitive implication, and a rejected cycle reports the
participating declaration locations. Graph construction never evaluates refinement predicates.
`contract` always takes exactly one ordinary argument: `~`, one contract or predicate, or one
collection of requirements.

Contract equality is descriptor identity, never structural, nominal-name, or requirement-list
equivalence. Every evaluation of contract construction creates a fresh identity, so separately
constructed contracts remain unequal even when they contain the same bases, refinements, or
parameterization arguments. Assigning, returning, or otherwise passing an existing contract value
preserves its identity. Contract values are comparable by this identity even though ordinary
callable values are not.

<a id="contract-implication-and-constraint-normalization"></a>
#### Contract implication and constraint normalization

Static contract implication is a conservative, compiler-proven partial order. `A` implies `B` only
when the compiler can prove that every value satisfying `A` also satisfies `B`. Unknown
relationships are incomparable; implication analysis never executes refinement predicates or
samples runtime values. `A` is strictly more specific than `B` when `A` implies `B` and `B` does not
imply `A`.

Descriptor identity implies itself, and aliases preserve that identity. Every contract implies
`Any`. A nominal contract implies each declared base transitively and each of its declared
refinement requirements. The reverse does not hold: satisfying the same bases and predicates does
not grant or imply the nominal descriptor. A verified refinement implies only the same callable
identity, including aliases of that callable; different predicates remain incomparable even when
their implementations appear logically equivalent.

Several requirements on one parameter form an anonymous conjunction. A conjunction implies each
of its requirements, and one normalized conjunction implies another only when every requirement of
the latter is implied by the former under the rules above. Normalization removes duplicate
identities and requirements already implied by a stricter retained requirement. An unconstrained
parameter and an explicit `Any` requirement are the same generic fallback and cannot distinguish
two overload variants. A statically empty constraint is an invalid declaration rather than a
dispatch variant that wins by accepting no values.

The prototype normalizes overload declaration domains by canonical binding identity and the
statically declared nominal derivation graph. It removes `Any`, duplicates, and base requirements
already implied by a stricter term before comparing declarations; this includes forward multiple-
base diamonds without executing refinements. Runtime specificity uses the same conservative
implication rules and keeps incomparable variants unordered.

Null and missing alternatives are normalized separately from the conjunction for ordinary present
values. Their implication follows accepted-set inclusion: `T` implies both `T?` and `T~`; each of
those implies `T?~`; and `T?` and `T~` are incomparable. Base implication must also hold, so `Int?`
implies `Number?` when `Int` implies `Number`. For a conjunction, null is admitted only when every
requirement admits null, and missing is admitted only when every requirement admits missing. This
separate normalization also detects when the ordinary-value conjunction is empty but a shared null
or missing alternative remains valid.

Parameterized-contract constructors declare the variance of each parameter as language-owned
metadata. Implication is considered only between applications of the same constructor, or through
an independently declared base relationship. Immutable `Sequence` has a covariant element
parameter, so `Sequence Int` implies `Sequence Number` when `Int` implies `Number`. Mutable
`Container` parameters are invariant by default: `Container Int` and `Container Number` do not
imply one another unless their argument descriptors are identical. No constructor is assumed
covariant merely because its current implementation appears read-only.

The prototype's static implication foundation implements descriptor identity, transitive nominal
derivation, null/missing accepted-set inclusion, and covariance for the implemented immutable
`Sequence` constructor. Parameter conjunction ordering uses those proofs, normalizes duplicate and
`Any` requirements, and keeps null and missing alternatives distinct. Unknown relationships remain
incomparable. Mutable `Container` variance remains tied to that later value-kind implementation.

---

<a id="contract-composition"></a>
#### Contract composition

`contract` may combine multiple contracts:

```caret
Number =
  contract [Eq Comparable Arithmetic]
```

This means that every `Number` also satisfies:

```text
Eq
Comparable
Arithmetic
```

Conceptually:

```text
Number x
    =>
Eq x
and Comparable x
and Arithmetic x
```

Multiple derivation is therefore ordinary contract composition.

No separate multiple-inheritance mechanism is required.

For example:

```caret
Integer =
  contract [Number Integral]

Float =
  contract [Number Fractional]
```

establishes:

```text
Integer -> Number
Integer -> Integral

Float -> Number
Float -> Fractional
```

and transitively all contracts derived by `Number`.

---

<a id="type-derivation"></a>
### Type derivation

Type derivation is contract inclusion.

For example:

```caret
Eq = contract ~

Comparable =
  contract Eq

Arithmetic =
  contract Eq

Number =
  contract [Comparable Arithmetic]

Int =
  contract [Number Integral]

Float =
  contract [Number Fractional]
```

This creates a graph rather than requiring a strict inheritance tree.

A contract may derive from any number of other contracts.

There is no object-layout diamond problem because derivation does not copy or embed base objects.

If several derivation paths lead to `Eq`, the resulting value simply satisfies `Eq`.

---

<a id="refinement-predicates"></a>
#### Refinement predicates

Ordinary pure unary Boolean functions may participate in contracts.

Example:

```caret
positive x =
  x > 0

PositiveInt =
  contract [Int positive]
```

Conceptually:

```text
PositiveInt x
    =
Int x
and positive x
```

The same constraint may also be written directly on a binding:

```caret
(Int positive) count
```

Named derived contracts are useful when a combination is reused:

```caret
SmallPositiveInt =
  contract [Int positive small]
```

Thus Caret uses the same mechanism for:

* base types;
* derived types;
* refinement types;
* interfaces/capabilities;
* user-defined validation constraints.

---

<a id="contracts-do-not-contain-operations"></a>
#### Contracts do not contain operations

A contract does not contain a method table or list of allowed operations.

For example:

```caret
Eq = contract ~
```

does not itself declare `eq`.

Equality is an ordinary function defined separately:

```caret
(Boolean) eq (Eq) a (Eq) b =
  false
```

Specific contracts may provide more specialized implementations:

```caret
(Boolean) eq (Int) a (Int) b =
  primitiveIntEq a b

(Boolean) eq (Float) a (Float) b =
  primitiveFloatEq a b

(Boolean) eq (String) a (String) b =
  primitiveStringEq a b
```

Likewise:

```caret
(Boolean) lt (Comparable) a (Comparable) b =
  ...

(Int) add (Int) a (Int) b =
  ...

(Float) add (Float) a (Float) b =
  ...
```

Operations belong to functions, not types.

---

<a id="functional-polymorphism"></a>
#### Functional polymorphism

Several definitions of the same function may specialize different parameter contracts.

For example:

```caret
(Boolean) eq (Eq) a (Eq) b =
  false

(Boolean) eq (Number) a (Number) b =
  numericEq a b

(Boolean) eq (Int) a (Int) b =
  primitiveIntEq a b
```

When calling:

```caret
eq 10 20
```

the most specific applicable implementation is selected.

Here that is:

```caret
eq (Int) a (Int) b
```

This provides functional polymorphism through contract-based dispatch.

There is no privileged receiver object.

Dispatch may therefore depend on several arguments:

```caret
add (Int) a (Float) b =
  ...

add (Float) a (Int) b =
  ...

add (Float) a (Float) b =
  ...
```

If exactly one most-specific implementation exists, it is used.

If several incomparable implementations are equally applicable, the call is ambiguous and must produce a compile-time diagnostic where determinable.

Function dispatch must not arbitrarily choose between ambiguous implementations.

All definitions with the same name in one lexical block form one closed overload set. Every variant
must have the same arity, and every pair must differ in the normalized requirements of at least one
parameter; result contracts alone cannot distinguish variants. A generic variant may be the
least-specific fallback. Variant ordering uses the contract-implication relation above: one variant
is more specific when its normalized constraint implies the other variant's constraint on every
parameter and the reverse implication fails on at least one parameter. Requirements that are
unknown or incomparable never receive a source-order tie-break.

The compiler selects a uniquely most-specific variant when it can prove one. Otherwise the closed
set is dispatched at runtime using the arguments' actual memberships. No applicable variant and
several incomparable applicable variants are distinct located runtime errors. Runtime-loaded code
may supply values and its own overload sets, but it cannot add variants to an existing lexical set.

Different arities in one lexical overload set are rejected with the located semantic diagnostic
`INCONSISTENT_OVERLOAD_ARITY`; the later declaration is primary and the first variant is related.
If two variants have identical normalized parameter domains, including through contract aliases or
redundant `Any` and duplicate conjunction terms, the later declaration retains the ordinary
`DUPLICATE_DEFINITION` diagnostic and the original variant is related. Result contracts do not make
otherwise identical parameter domains distinct. Both declaration failures occur before program
effects execute.

<a id="overload-applicability"></a>
#### Overload applicability

Applicability testing is observational and never acquires nominal membership. A nominal parameter
requirement is applicable only when the argument already carries that descriptor, or a nominal
descriptor that implies it through derivation. Passing the nominal contract's bases and refinements
is insufficient by itself. An explicit nullable or optional alternative may nevertheless accept
null or missing without attributing the nominal identity.

Built-in, structural, parameterized, and verified-refinement requirements may be tested at runtime
when static information cannot decide them. These checks receive the original argument and never
replace it with an attributed value. A false membership or refinement result removes that candidate.
A diagnostic raised while executing a verified pure refinement aborts the call with its ordinary
diagnostic; it is not converted into a false result or a no-applicable-overload outcome.

Within one call, the dispatcher caches each dynamic result by requirement identity and argument
position. Equivalent uses of that same requirement on that same argument therefore execute at most
once even when several variants share it. Distinct descriptor or refinement identities are distinct
checks. The deterministic evaluation order is argument positions from left to right, then each
parameter's normalized requirements in source-stable order. Variant declaration order may determine
when an otherwise necessary check is scheduled, but never breaks an applicability or specificity
tie.

The selected variant receives the original arguments. Dispatch contributes no nominal attribution;
membership established before the call remains available normally. When a multi-variant set has no
applicable implementation, the diagnostic is `NO_APPLICABLE_OVERLOAD`. When several applicable
maximal variants remain incomparable, it is `AMBIGUOUS_OVERLOAD`. Both diagnostics use the complete
call span as their primary location and include the relevant variant declarations as related
locations.

A name with only one function definition does not perform overload selection. Its parameter
boundary retains the ordinary `CONTRACT_VIOLATION` diagnostic and existing attribution behavior.
Introducing overload support must not silently change diagnostics for ordinary single functions.

<a id="partial-overload-sets"></a>
#### Partial overload sets

Every overload variant has the same declared arity. Ordinary prefix application narrows the set as
each argument fills the next parameter. Hole-based partial application uses the hole layout to map
eager fixed operands and later supplied values to their parameter positions, so later fixed
positions may be checked before earlier holes are filled. Applicability uses the same observational,
per-requirement cache described above and never acquires membership.

The narrowed state is persistent and immutable: it contains the original overload-set identity,
the surviving variants, original bound arguments and source spans, filled parameter positions, and
cached applicability results. Applying the same earlier partial along two paths produces independent
states. A fixed operand's cached checks may be reused for the lifetime of every state derived from
that partial; checks involving a newly supplied value belong only to that derived branch.

No surviving variant is invoked before full arity, even when only one remains. At full arity the
dispatcher selects the unique most-specific surviving variant. Several incomparable maximal
variants produce `AMBIGUOUS_OVERLOAD`; no survivor produces `NO_APPLICABLE_OVERLOAD`. An early
no-survivor diagnostic uses the smallest supplied argument or application step that eliminated the
last candidate as its primary span. A final no-match uses the completed call span. Both retain
related variant declaration locations.

Applicability checks performed during narrowing satisfy the selected overload's parameter boundary
and are not repeated at invocation. The selected implementation receives the original arguments,
and its result contract is validated normally. Overload partials remain ordinary callable values:
their remaining arity supports prefix/infix classification and composition, while contract/effect
metadata follows the variant-preserving signature and conservative summary rules below until
selection completes.

---
