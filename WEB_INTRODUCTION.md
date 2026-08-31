# Caret: concise code, explicit meaning

Caret is an experimental programming language for expressing programs with less routine punctuation
and boilerplate—without making their behavior mysterious. Function calls use whitespace, indentation
defines blocks, and familiar operations remain easy to read.

```caret
add a b =
  a + b

answer = add 20 22
print answer
```

The project explores a simple question: how compact can a programming language become while
remaining predictable, statically analyzable, and pleasant to work with?

## Designed around expressions

In Caret, functions and calls use the same lightweight notation. A function lists its parameters
before `=`, and passing arguments requires no commas or parentheses:

```caret
greet name = "Hello, " + name

message = greet "Ada"
```

Function application binds more tightly than infix operators, so this:

```caret
double x + square y
```

means `(double x) + (square y)`. Parentheses are still available whenever explicit grouping makes an
expression clearer.

Binary functions can also be written between their arguments. Named infix calls are
left-associative and have one fixed precedence between comparison and addition:

```caret
combine left right = left * 10 + right
value = 1 combine 2 combine 3
```

The analyzer uses the callable visible in the expression's lexical scope to distinguish named
prefix and infix forms; unrelated declarations elsewhere in a file cannot change that choice.

Caret also provides right-associative `$` when the entire expression on the right
should become one argument:

```caret
print $ calculate value
```

This is syntax-level low-precedence application, not a new callable operator. It will lower to the
same calls as `print (calculate value)`.

## Indentation defines structure

Multiline function bodies are introduced by indentation rather than braces. The final value in a
body becomes its result:

```caret
totalWithTax subtotal rate =
  tax = subtotal * rate
  subtotal + tax
```

This keeps the common case compact while preserving an obvious visual structure.

For deeply embedded source, `\\` and `\*` layout markers temporarily remap physical
indentation to the same effective logical nesting. They change only layout processing: they do not
create scopes, close blocks, or perform control flow. The mappings stack and are implemented for
the prototype's currently supported indentation-opening headers.

## Lazy decisions

Caret's conditional expression uses `&` for the selected true branch and `!` for the alternative:

```caret
label = score >= 50 & "pass" ! "try again"
```

Only the selected branch is evaluated. The shorter form omits the alternative and produces the
missing value when the condition is false:

```caret
result = ready & calculate input
```

The `and` and `or` operators short-circuit in the same way.

## Null and missing are different

Many languages use one value for two different situations. Caret keeps them separate:

```caret
?  // null: a value is present, but explicitly empty
~  // missing: no value is present
```

That distinction also appears in field access. Required access reports an error when a field does
not exist, while optional access returns `~`:

```caret
person.name
person.phone~
```

Code can therefore state whether absence is expected instead of relying on exceptions for ordinary
lookup failures.

## Small, immutable exported values

A function can return a named structured value by exporting bindings with `^`. Everything else in
the function stays private:

```caret
makePerson name birthYear =
  currentYear = 2026
  ^name = name
  ^age = currentYear - birthYear

ada = makePerson "Ada" 1815
print ada.name
print ada.age
```

The current prototype returns the named `Collection` containing those exported fields, equivalent
to writing an explicit `[...]` named collection. The same recursive equality rules apply when
values are nested, while callable values are deliberately not comparable. Lexical scopes remain
private name-resolution environments rather than first-class values.

Planned `with person` blocks will make public named members available directly inside an expression.
Local declarations take priority, followed by the current `with` members and then enclosing lexical
bindings. Explicit `outer.name` paths recover shadowed names, but `outer` will not be a first-class
or reflectable environment value; exports and sandbox visibility remain unchanged.

## Partial application without ceremony

An underscore marks an argument to be supplied later:

```caret
between low value high =
  value >= low and value <= high

insideTen = between 0 _ 10
print insideTen 7
```

Numbered holes can reorder or reuse future arguments:

```caret
reordered = combine _2 fixed _1
duplicated = pair _1 _1
```

This turns ordinary expressions into reusable functions without requiring separate lambda syntax
for simple cases.

## Reflection belongs to the language

Caret treats reflection as a normal language operation. Field names represented as data are ordinary
strings, while `@` produces a reflective view:

```caret
fieldName = "name"
print person[fieldName]~
print (@person).kind
print (@person).names
```

Reflection exposes only public or explicitly exported information. Expected failures, such as a
missing dynamic field, can produce `~` instead of an exception. A reflected function is a
non-callable metadata Dictionary: `type (@function)` is `"Dictionary"`, while `@function.kind` is
`"Function"`. Its fields are computed lazily for the observing environment: hidden facts and names
become `~`, known-empty facts remain `[]`, and moving metadata cannot increase visibility. Adjacent
postfix `:` recovers the reflected value or callable only when that observer retains access.

## Values and collections

The current prototype supports finite numbers, Unicode strings, Booleans, null, and missing. It also
provides persistent sequences and canonically ordered Dictionaries. Collection updates
produce new values rather than mutating existing ones, and equality is structural for ordinary data.
The bare `[]` value is a shape-neutral empty Collection accepted by compatible sequence and
dictionary contracts. Named Collection fields traverse and reflect in locale-independent,
case-sensitive Unicode code-point order, while their expressions evaluate in source order. Static
`^name` fields, ordinary `field "name" value` results, and `dictPut` updates share one String-keyed
Dictionary representation and one member protocol.

`toString` is an extensible ordinary overload set. Its standard fallback renders top-level Strings
as raw text, flat positional collections as `[ 1 2 ]`, and named or structurally nested collections
on indented lines. Named keys and nested Strings are quoted and escaped. Contract-specific
specializations participate recursively when a collection is converted.

```caret
person = [
  field "first name" "Alice"
  field "age" 42
]
```

Each top-level line in a multiline collection literal is an ordinary expression, so calls do not
need parentheses merely because they produce collection elements.

```caret
items = seqAdd (seqAdd seqEmpty "first") "second"
settings = dictPut dictEmpty "theme" "dark"

print seqGet items 0
print dictGet settings "theme"
```

The prototype now has a general `Collection` contract, positional and static named `[...]` literals,
and named Collections returned directly by exported blocks. The planned contextual model will let
the same literal describe a list, set, dictionary, packed buffer, or heterogeneous structure while
surrounding contracts select behavior and representation. Dynamic fields will become ordinary
first-class collection elements rather than a separate object or JSON notation.

Caret likewise plans to use contracts as one common model for types, interfaces, refinements, and
capabilities. Contracts form derivation graphs and work as predicates. Behavior remains in ordinary
functions, with the most-specific applicable implementation selected from contract-specialized
definitions.

The prototype includes built-in predicates plus unary user-defined contract construction. Use
`Tag = contract ~` for a nominal base, `Numeric = contract Number` for one base, and
`AB = contract [A B]` for multiple bases. Contract declarations may refer forward within their
block; multiple-base diamonds imply every transitive base, while direct and indirect derivation
cycles are rejected with their declaration locations. Clauses can constrain bindings, parameters, and function
results. An internal analysis also propagates known effects and conservatively rejects unknown
dynamic calls when proving whether a refinement predicate is pure, including effects incurred while
fixed operands are captured into partial applications. Proven unary Boolean functions
are first-class refinement requirements in derived contracts and direct clauses, and retain that
eligibility through aliases. The initial parameterized-contract slice implements `Sequence T` as
ordinary contract application: it validates every sequence element, composes through aliases,
nesting, and null/missing modifiers, and exposes its base and requirement through reflection.
General parameterized contracts and complete static proof remain
planned. Callable reflection now exposes immutable language-owned signature metadata for remaining
parameters, result facts, known invocation effects, and surviving overload variants without exposing
captures, partial values, implementation objects, or authority. Derived metadata specializes
generic prefix and hole partials, conjoins repeated-hole requirements, projects reordered holes,
and carries compatible substitutions and effect unions through composition. The metadata is lazily
filtered through interpreter-owned environment state that is never exposed as a Caret value.
Contract and effect references preserve identity even when their visible name is `~`. Closed
same-name overload sets are implemented: applicability observes existing contract
membership without acquiring it, and the unique most-specific applicable variant wins.

```caret
describe (Any) value = "fallback"
describe (Number) value = "number"

print (describe 42)
```

Overloads retain generic fallbacks and narrow through prefix, infix, and direct hole partials.
No-match and incomparable-match calls have distinct located diagnostics; a single function keeps
the ordinary parameter contract behavior.
Arrow contracts accept an overload only when one variant covers the complete requested domain and
every variant that might also be selected has compatible results and effects. Proven-disjoint
variants do not interfere; unknown overlap remains conservatively possible.
Contract equality follows descriptor identity: aliases compare equal, but separate constructions
remain unequal even with identical requirements. Contract reflection exposes public base and
refinement-requirement names without exposing implementation callables.
The prototype infers initial built-in constraints for unannotated named functions and uses
generalized contract variables when parameter or result contracts cannot yet be made concrete;
each call instantiates those variables independently. Explicit callable declarations remain stable
interfaces: implementation inference may retain stronger local result facts, but it cannot silently
narrow a declared parameter domain, and external observation does not expose those stronger facts.

The prototype can state a pure higher-order callable contract directly. A bracketed parameter
list followed by `->` describes exact arity, result guarantees, and an optional maximum effect set:

```caret
[Number] -> Number

(Number) apply ([Number] -> Number) transform (Number) value =
  transform value
```

Explicit effect allowances and declaration-wide variables are also supported:

```caret
[Number String] -> (Output Boolean)

(_2) applyGeneric ([_1] -> _2) transform (_1) value =
  transform value
```

Named declarations use the same mixed-clause model. In `(Output Number) noisy ...`, `Number`
constrains the result while `Output` is the callable's effect allowance. The analyzer classifies the
clause once, so callable reflection reports `Number` only as a result requirement and `Output` only
as an effect; source order does not change that meaning.

The prototype implements the runtime `map transform values` operation for Sequences and current
named, partial, and composed callable values. Declaration-wide variable schemes retain their
substitutions through prefix and hole partials; lambdas and precise transform-effect propagation
remain planned.

Numbered contract variables relate the callable parameter to surrounding parameters and results.
Compatibility is substitution-safe: parameters are contravariant, results covariant, and effects
must remain within the stated allowance. This arrow form is a first-class contract, distinct from a
lambda because its left side is a bracketed requirement list. Exact arity, pure effect bounds,
contravariant parameters, covariant results, inline clauses, and standalone variables are
implemented, including explicit allowances and variables shared across a complete declaration header.

The planned static operator model preserves the prototype's compact behavior without adding hidden
numeric promotion. Arithmetic and ordering initially operate on finite `Number` values. `+` is a
closed overload set: it adds two numbers or concatenates when either operand is a string, rendering
the other value through Caret's own deterministic formatter. Equality uses a recursive structural
`Eq` capability and continues to reject live callables even when nested. Boolean operations retain
the existing Boolean/null/missing truth domain and lazy evaluation. An unresolved numeric-versus-
string `+` is a compile-time ambiguity rather than silently defaulting to Number. This complete
static matrix is specified but not implemented by the current inference pass.

Contracts also have first-class null/missing unions. `Number?` accepts numbers or null, `Number~`
accepts numbers or missing, and `Number?~` accepts all three while keeping null and missing
observably distinct. The modified contracts remain unary predicates, work in clauses and aliases,
and expose canonical names and their wrapped base through reflection.

In the planned collection model, an expression such as `[fixed _]` is an ordinary function whose
parameter fills the hole and whose result is the completed collection. Passing that reifiable
constructor—or a concrete fixed collection—to the ordinary `template` function derives an exact
structural contract. Contracted holes constrain variable positions, ordinary values require
equality, and fields or nested collections contribute recursively. Templates remain ordinary
`Contract` values, so they can constrain parameters, collection elements, and dispatch without
introducing a separate record or schema type system.

Structural-template construction preserves the ordinary diagnostics for invalid field names,
duplicate fields, contract requirements, and mixed hole styles. Only failures unique to template
derivation—an ineligible constructor or a non-comparable captured value—use template-specific
codes, with stable locations in both static and dynamic discovery.

Caret also plans a standard `ErrorTemplate` carrying a stable code, phase, message, locations,
cause, and subsystem details. Expected operation failures use values of that shape; aborting
compiler and runtime diagnostics share the information model without becoming catchable return
values. A generic `Result` contract uses `ok`, `value`, and `error` fields so format and sandbox
operations share one explicit envelope. Structural templates, general parameterized contracts
beyond `Sequence T`, universal literals, `ErrorTemplate`, and `Result` remain planned;
the unary contract, refinement, and initial sequence-parameterization foundation described above
is implemented.

The planned effect system likewise assigns distinct stable codes to malformed mixed
contract/effect clauses, non-callable effect constraints, unavailable callable effect bounds, and
effects outside an allowance. A failure keeps the same behavioral code whether static analysis or
a dynamic boundary discovers it, while its phase and source locations record where it was found.

## Contained mutability

Caret values remain immutable by default. Planned mutability is introduced only through an explicit
stable-identity container:

```text
health = { (Int) 100 }
player =
  ^health = health

print player.health{}  // read the shared current value
put health 80          // replace it after checking the Int contract
```

`player.health` returns the container itself, while `player.health{}` reads its contents and
`player.@health` reifies the field binding. Sharing the container does not make `player` mutable and
does not require special reference-assignment syntax. Container identity uses ordinary equality;
comparing current contents requires explicit reads.

The planned effect system names content observation `StateRead` and replacement `StateWrite`.
Passing or inspecting the container reference remains pure, and declaring an effect never grants
authority over a container. Rule cycles can track explicit reads as reactive dependencies, while
sandboxes may expose a real container, a restricted projection, or an immutable snapshot. These
features are specified future work and are not available in the prototype.

## Environment-relative reflection

Caret plans to let programs reflect on the environment visible to them through metadata-only
`@root`, and on the current module through `@module`. A root module can detect that relationship
with `@module == @root`. Complete semantic module code will be represented as immutable structured
Caret values and convertible to canonical, implementation-independent Caret source, making program
reification and canonical quines possible.

Planned source modules may declare stable logical IDs such as `clientServer = module`. An import may
use either a relative source path or a `ModuleId` resolved through the current environment's flat
module catalog. Module IDs do not become lexical bindings, canonical source paths still identify
module evaluation, and a sandbox sees only the catalog entries explicitly supplied to it.

The same model anchors sandboxing. `sandbox source environment` accepts an immutable named Collection;
the host can atomically replace that complete snapshot with `swapEnv` without restarting the plugin.
Reflection respects that boundary, and canonical sandbox code excludes exposed host implementations.
Declaring an effect describes an action without granting permission to perform it. Reloading, unlike
an environment swap, starts a fresh generation: immutable values already obtained remain values,
while all old-generation references become invalid. These features are specified future work and
are not available in the prototype.

Caret also plans compile-time execution using ordinary Caret code. A `#`-prefixed binding exists only
during compilation, while a `#`-prefixed initializer computes a value or program structure to carry
into the runtime program. Independent client, server, test, or platform roots can transform shared
modules differently; semantic reachability after staging retains required dependencies without
emitting everything inspected at compile time. Staging uses an explicit capability-bounded compiler
environment and is not implemented by the current interpreter.

## An evolving language experiment

Caret is currently a Java 21 tree-walking interpreter, not a production compiler. It already
supports lexical closures, direct and mutual recursion, partial application, named Collections,
left-to-right function composition, language-owned reflection, persistent collections,
source-located diagnostics, a REPL, and native test assertions. Execution remains fail-fast, while
compiler-oriented parsing can recover at declaration boundaries and collect independent failures
without losing physical spans from valid later declarations.
Closure analysis records deterministic, source-spanned upvalues by stable binding identity; runtime
closures use those same internal descriptors without exposing captures or lexical environments
through reflection.

General parameterized contracts, structural templates, contextual collection representations,
modules, root reification, sandboxing,
compile-time execution, separate compilation roots,
lambdas, mutability containers, and a compiler backend remain future work. The prototype exists to
make the language's ideas executable and testable while its larger design evolves.

To explore the implementation, syntax reference, and runnable examples, see the project
[README](README.md), [language specification](LANGUAGE.md), and
[implemented feature tour](examples/features/implemented_features.caret).
