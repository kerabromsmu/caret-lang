# Established Caret syntax for agent tasks

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

### Exported collection bindings

```caret
makePerson name age =
  internal = calculateSomething age

  ^name = name
  ^age = age
```

Only bindings marked with `^` belong to the returned named Collection and its reflective interface.
The block form is shorthand for an explicit named `[...]` Collection; other bindings remain lexical
locals. The prototype returns named Collections directly and has no first-class `Scope` runtime kind.

### Null and missing

```caret
?       // null
~       // missing

Text?   // present but nullable
Text~   // optional; may be missing
Text?~  // missing, null, or a Text value
```

Missing and present-null must remain distinguishable.

### Named collection access

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

Follow the release chunks in `PLAN.md`: v1 includes metadata-only root/module identity and visible
binding information, path-based sandboxes with fixed environments, terminate/unload, shared
read/write containers, and immutable snapshots. Semantic Code/serialization, Code input,
swaps/reload, and read-only/virtual container views are later features. The implemented Java
embedding environment-swap API remains available. Do not infer whole-phase completion from v1.

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

`with` and `outer` are implemented reserved spellings. Local declarations shadow public members
of the current `with` target, which shadow enclosing lexical bindings. `outer` is valid only as an
explicit lexical member path and cannot be stored, reflected, dynamically indexed, or used to
bypass export or sandbox visibility. `$` is right-associative syntax-level application below `>>`,
conditionals, lambdas, and ordinary expressions; it lowers to the ordinary callable path.
