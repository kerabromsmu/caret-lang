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

For deeply embedded source, planned `\\` and `\*` layout markers will temporarily remap physical
indentation to the same effective logical nesting. They change only layout processing: they do not
create scopes, close blocks, or perform control flow. The mappings can stack and are not implemented
by the current prototype.

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

## Small, immutable scopes

A function can return a scope by exporting bindings with `^`. Everything else in the function stays
private:

```caret
makePerson name birthYear =
  currentYear = 2026
  ^name = name
  ^age = currentYear - birthYear

ada = makePerson "Ada" 1815
print ada.name
print ada.age
```

Exported scopes are immutable and compare recursively by their contents. The same equality rules
apply when values are nested, while callable values are deliberately not comparable. Scopes provide
a compact foundation for building and returning named data without exposing implementation details.

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
field = "name"
print person[field]~
print (@person).kind
print (@person).names
```

Reflection exposes only public or explicitly exported information. Expected failures, such as a
missing dynamic field, can produce `~` instead of an exception. A reflected function is a
non-callable reference, and both `type` and its `kind` metadata identify it as `"Function"`.

## Values and collections

The current prototype supports finite numbers, Unicode strings, Booleans, null, and missing. It also
provides persistent sequences and insertion-ordered dictionaries. Collection updates
produce new values rather than mutating existing ones, and equality is structural for ordinary data.

```caret
items = seqAdd (seqAdd seqEmpty "first") "second"
settings = dictPut dictEmpty "theme" "dark"

print seqGet items 0
print dictGet settings "theme"
```

The planned language generalizes these primitives into a single collection model. One `[...]`
literal can describe a list, set, dictionary, packed buffer, or heterogeneous structure; surrounding
contracts select its behavior and representation. Named fields are ordinary first-class collection
elements rather than a separate object or JSON notation.

Caret likewise plans to use contracts as one common model for types, interfaces, refinements, and
capabilities. Contracts form derivation graphs and work as predicates. Behavior remains in ordinary
functions, with the most-specific applicable implementation selected from contract-specialized
definitions.

The prototype includes built-in predicates plus unary user-defined contract construction. Use
`Tag = contract ~` for a nominal base, `Numeric = contract Number` for one base, and
`AB = contract [A B]` for multiple bases. Clauses can constrain bindings, parameters, and function
results. An internal analysis also propagates known effects and conservatively rejects unknown
dynamic calls when proving whether a refinement predicate is pure, including effects incurred while
fixed operands are captured into partial applications. Proven unary Boolean functions
are first-class refinement requirements in derived contracts and direct clauses, and retain that
eligibility through aliases. Parameterized contracts, complete static proof, public effect
declarations, and dispatch remain planned.
Contract equality follows descriptor identity: aliases compare equal, but separate constructions
remain unequal even with identical requirements. Contract reflection exposes public base and
refinement-requirement names without exposing implementation callables.
The prototype infers initial built-in constraints for unannotated named functions and uses
generalized contract variables when parameter or result contracts cannot yet be made concrete;
each call instantiates those variables independently.

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

Caret also plans a standard `ErrorTemplate` carrying a stable code, phase, message, locations,
cause, and subsystem details. Expected operation failures use values of that shape; aborting
compiler and runtime diagnostics share the information model without becoming catchable return
values. A generic `Result` contract uses `ok`, `value`, and `error` fields so format and sandbox
operations share one explicit envelope. Structural templates, parameterized contracts, dispatch,
universal literals, `ErrorTemplate`, and `Result` remain planned; the unary contract and refinement
foundation described above is implemented.

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

The same model anchors sandboxing. `sandbox source environment` accepts an immutable exported scope;
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
supports lexical closures, direct and mutual recursion, partial application, exported scopes,
left-to-right function composition, language-owned reflection, persistent collections,
source-located diagnostics, a REPL, and native test assertions.

Parameterized contracts, structural templates, contract-based dispatch, universal collection
literals, modules, root reification, sandboxing, compile-time execution, separate compilation roots,
lambdas, mutability containers, and a compiler backend remain future work. The prototype exists to
make the language's ideas executable and testable while its larger design evolves.

To explore the implementation, syntax reference, and runnable examples, see the project
[README](README.md), [language specification](LANGUAGE.md), and
[implemented feature tour](examples/implemented_features.caret).
