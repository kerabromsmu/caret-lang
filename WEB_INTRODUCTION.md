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

## Indentation defines structure

Multiline function bodies are introduced by indentation rather than braces. The final value in a
body becomes its result:

```caret
totalWithTax subtotal rate =
  tax = subtotal * rate
  subtotal + tax
```

This keeps the common case compact while preserving an obvious visual structure.

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

Caret treats reflection as a normal language operation. A name value begins with `#`, while `@`
produces a reflective view:

```caret
field = #name
print person[field]~
print (@person).kind
print (@person).names
```

Reflection exposes only public or explicitly exported information. Expected failures, such as a
missing dynamic field, can produce `~` instead of an exception. A reflected function is a
non-callable reference, and both `type` and its `kind` metadata identify it as `"Function"`.

## Values and collections

The current prototype supports finite numbers, Unicode strings, Booleans, null, missing, and name
values. It also provides persistent sequences and insertion-ordered dictionaries. Collection updates
produce new values rather than mutating existing ones, and equality is structural for ordinary data.

```caret
items = seqAdd (seqAdd seqEmpty "first") "second"
settings = dictPut dictEmpty #theme "dark"

print seqGet items 0
print dictGet settings #theme
```

The planned language generalizes these primitives into a single collection model. One `[...]`
literal can describe a list, set, dictionary, packed buffer, or heterogeneous structure; surrounding
contracts select its behavior and representation. Named fields are ordinary first-class collection
elements rather than a separate object or JSON notation.

Caret likewise plans to use contracts as one common model for types, interfaces, refinements, and
capabilities. Contracts form derivation graphs and work as predicates. Behavior remains in ordinary
functions, with the most-specific applicable implementation selected from contract-specialized
definitions. None of this contract, dispatch, or literal syntax is implemented by the current
prototype yet.

## Environment-relative reflection

Caret plans to let programs reflect on the environment visible to them through metadata-only
`@root`, and on the current module through `@module`. A root module can detect that relationship
with `@module == @root`. Complete semantic module code will be represented as immutable structured
Caret values and convertible to canonical, implementation-independent Caret source, making program
reification and canonical quines possible.

The same model anchors sandboxing. `sandbox source environment` creates a plugin environment whose
exposed names can be changed atomically by its host. Reflection respects that boundary, and
declaring an effect describes an action without granting permission to perform it. Reloading starts
a fresh generation: immutable values already obtained remain values, while all old-generation
references become invalid. These features are specified future work and are not available in the
prototype.

## An evolving language experiment

Caret is currently a Java 21 tree-walking interpreter, not a production compiler. It already
supports lexical closures, direct and mutual recursion, partial application, exported scopes,
language-owned reflection, persistent collections, source-located diagnostics, a REPL, and native
test assertions.

Contracts, contract-based dispatch, universal collection literals, modules, root reification,
sandboxing, lambdas, mutation, and a compiler backend
remain future work. The prototype exists to make the language's ideas executable and testable while
its larger design evolves.

To explore the implementation, syntax reference, and runnable examples, see the project
[README](README.md), [language specification](LANGUAGE.md), and
[implemented feature tour](examples/implemented_features.caret).
