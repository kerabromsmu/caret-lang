# Caret Language Project Instructions

## Project goal

Caret is an experimental concise programming language. Its syntax should remove routine punctuation and boilerplate while retaining clear, predictable, statically analyzable semantics.

The current implementation is a Java 21 tree-walking interpreter.

Read `README.md` and `LANGUAGE.md` before making architectural or syntactic changes.

## Core design principles

1. Common operations should have compact syntax.
2. Whitespace performs function application.
3. Indentation defines blocks. Parentheses and braces may remain available for explicit grouping.
4. Missing and null are different states.
5. Dynamic operations should not throw exceptions for expected conditions such as missing fields.
6. Reflection should be a normal language feature rather than a separate cumbersome API.
7. Returned scopes expose only explicitly exported bindings.
8. Immutable and functional programming should be supported without making mutable programming impossible.
9. Do not add Java-like ceremony to the Caret language syntax.
10. Do not silently invent syntax when the specification is unresolved. Document the issue and propose alternatives.
11. Reflection is relative to the execution environment visible to the current code and must not
    cross sandbox visibility or authority boundaries.
12. Effect declarations describe observable behavior but do not grant authority. A child sandbox
    must not amplify the authority available to its parent.
13. `@root` and `@module` are metadata-only references, not hidden scope objects or capability
    invocation paths.
14. Code visibility and binding authority are distinct: the initial module-code reflection model
    exposes complete semantic code for a visible module without exposing its private bindings.
15. Planned `with` changes lexical lookup only; it must not copy members or widen their visibility
    or authority. Planned `outer.name` is a resolver-owned lexical path, never a first-class scope
    exposing enclosing private bindings.
16. Planned `#` changes execution stage but grants no effects or authority. Compile-time visibility,
    runtime inclusion, and artifact reachability are distinct; staging must preserve module export,
    reflection, sandbox, and capability boundaries.
17. Expression-form `#` is a compile-time remainder marker, not a unary precedence operator. Its
    region extends to the nearest enclosing expression boundary, and no later operator resumes
    runtime execution; binding-form `# name = expression` stages the complete binding.

## Established syntax

### Function definitions and calls

```caret
add a b =
  a + b

result = add 2 3
```

Function application binds more tightly than infix operators.

```caret
f x + g y
```

means:

```caret
(f x) + (g y)
```

### Conditional expressions

```caret
condition & trueValue ! falseValue
```

Only the selected branch is evaluated.

```caret
condition & expression
```

is equivalent to:

```caret
condition & expression ! ~
```

### Exported scope bindings

```caret
makePerson name age =
  internal = calculateSomething age

  ^name = name
  ^age = age
```

Only bindings marked with `^` belong to the returned scope and its reflective interface.

### Null and missing

```caret
?       // null
~       // missing

Text?   // present but nullable
Text~   // optional; may be missing
Text?~  // missing, null, or a Text value
```

Missing and present-null must remain distinguishable.

### Scope access

```caret
person.name     // statically guaranteed access
person.phone~   // optional access; returns ~ when absent
```

### Partial application

```caret
between low value high =
  value >= low and value <= high

inside = between 0 _ 10
inside 5
```

Each `_` introduces an unfilled argument, ordered from left to right.

Numbered holes reorder or reuse future arguments:

```caret
f _2 fixed _1
pair _1 _1
```

The highest numbered hole determines the resulting arity. Numbered and unnumbered holes may not be
mixed in one partial expression.

### Reflection

```caret
@value         // reflective view
value["name"]~ // safe dynamic lookup
```

Reflection exposes only public or exported bindings.

Known names should retain as much static type information as possible. Runtime-generated names may produce a dynamic value that requires matching or type inspection.

Expected reflection failures such as a missing binding must produce `~` or a structured result, not an exception.

### Planned root/module reflection and sandbox isolation

The planned `@root` form denotes the root of the current Caret execution environment, not an
unconditional process-global root. In a sandbox it must resolve to the substituted sandbox root.
Program/code metadata must expose only code and references visible in that environment.

`@root` and `@module` are reserved metadata-only reflective primaries. The planned sandbox form is
`sandbox source environment`, with atomic environment exposure and explicit lifecycle functions.
Follow the settled metadata, code-equality, serialization, lifecycle, and reference-invalidation
rules in `LANGUAGE.md`; do not invent the remaining structured result forms or serialization for
host capabilities without portable identities. Reflection must not reveal host roots, private
captures, native implementation details, code outside the visible module environment, or unexposed
capabilities.

### Scoped lookup and low-precedence application

```caret
with value
  print member
  print outer.member

print $ toString $ calculate value
```

Planned `with` and `outer` are reserved. Local declarations shadow public members of the current `with`
target, which shadow enclosing lexical bindings. `outer` is valid only as an explicit lexical
member path and cannot be stored, reflected, dynamically indexed, or used to bypass export or
sandbox visibility. `$` is right-associative syntax-level application below `>>`, conditionals,
lambdas, and ordinary expressions; it lowers to the ordinary callable path.

## Implementation rules

* Use Java 21.
* Keep lexer, parser, AST, runtime values, evaluation, and diagnostics separated.
* Add automated tests for every syntax or semantic change.
* Preserve source locations in tokens and AST nodes.
* Diagnostics must include line and column information.
* Do not catch broad exceptions and convert them into vague interpreter errors.
* Do not use reflection from the Java implementation as a substitute for implementing Caret reflection semantics.
* Represent program reification with language-owned descriptors; never expose Java AST/runtime
  objects directly as Caret code metadata.
* Treat sandbox and capability changes as security-sensitive. Document the threat model and add
  adversarial interpreter/compiler tests for name lookup, reflection, imports, effects, retained
  references, and nested sandboxes before marking the feature implemented.
* Invoke Gradle either directly as `./gradlew <arguments...>` or, when a login shell is required,
  as `/bin/bash -lc './gradlew "$@"' bash <arguments...>`. Do not embed Gradle arguments,
  environment assignments, command chains, or substitutions inside the `bash -lc` program string;
  those command shapes do not match the project's persistent Gradle-only approval rules.
* Run the full test suite after changes.
* Update `LANGUAGE.md` whenever observable language behavior changes.
* Update `WEB_INTRODUCTION.md` whenever a language feature is added or altered so the public-facing
  description and examples remain accurate.
* Include representative Caret programs as integration tests.
* For every newly implemented language feature, add or extend a runnable `.caret` example that
  demonstrates the feature, and exercise that example from the integration test suite.
* `LANGUAGE.md` remains the canonical language specification. After the Phase 13 documentation
  generator exists, do not hand-edit generated site pages; update their canonical inputs and
  regenerate them instead.
* The final documentation release must provide a MkDocs Material site split into approachable
  Markdown pages with left-pane navigation, plus a shared-source “Learn Caret in Y Minutes” entry.
  Published examples must be executable or explicitly labeled conceptual/planned.

## Change discipline

Before substantial implementation:

1. Inspect the existing implementation.
2. State what already works.
3. Identify conflicts between the requested feature and current grammar or semantics.
4. Propose a concrete implementation plan.
5. Implement in small testable stages.
6. Report tests run and remaining limitations.
