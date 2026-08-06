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

Possible future support:

```caret
f _2 fixed _1
```

Numbered holes reorder or reuse future arguments. Do not implement this unless the task explicitly requests it.

### Reflection

```caret
#name          // a name value
@value         // reflective view
value[#name]~  // safe dynamic lookup
```

Reflection exposes only public or exported bindings.

Known names should retain as much static type information as possible. Runtime-generated names may produce a dynamic value that requires matching or type inspection.

Expected reflection failures such as a missing binding must produce `~` or a structured result, not an exception.

## Implementation rules

* Use Java 21.
* Keep lexer, parser, AST, runtime values, evaluation, and diagnostics separated.
* Add automated tests for every syntax or semantic change.
* Preserve source locations in tokens and AST nodes.
* Diagnostics must include line and column information.
* Do not catch broad exceptions and convert them into vague interpreter errors.
* Do not use reflection from the Java implementation as a substitute for implementing Caret reflection semantics.
* Run the full test suite after changes.
* Update `LANGUAGE.md` whenever observable language behavior changes.
* Include representative Caret programs as integration tests.

## Change discipline

Before substantial implementation:

1. Inspect the existing implementation.
2. State what already works.
3. Identify conflicts between the requested feature and current grammar or semantics.
4. Propose a concrete implementation plan.
5. Implement in small testable stages.
6. Report tests run and remaining limitations.
