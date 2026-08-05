# Implemented language sketch

This file describes the prototype as it currently behaves, not a final language specification.

## Values

```text
42          number
"text"      string
true false  Boolean
?           null
~           missing
#count      name value
```

Null and missing are separate runtime values.

## Bindings and functions

```text
x = 10
add a b = a + b

makePerson name age =
  internal = age + 1
  ^name = name
  ^age = age
```

Indentation defines a multiline function body. If a body contains exported bindings (`^`), calling the function returns an immutable scope containing those exports. Otherwise it returns the final expression or assigned value.

A zero-argument function is evaluated when its name is read. This is a provisional rule; a future design needs explicit syntax for referring to the function itself without invoking it.

## Function application

```text
add 2 3
```

Application is left-associative and has high precedence.

```text
f x y
```

means:

```text
(f x) y
```

Parentheses remain available as a grouping escape:

```text
print (add 2 3)
```

## Conditional expression

```text
condition & trueValue ! falseValue
```

Only the selected branch is evaluated.

Without a false branch:

```text
condition & value
```

false produces `~`.

## Boolean operations

```text
a and b
not a
a or b
```

`and` and `or` short-circuit.

## Arbitrary partial application

```text
between low value high = value >= low and value <= high
inside = between 0 _ 10
inside 7
```

Every `_` introduces a future argument, ordered left to right.

Current limitation: non-hole parts of the partial expression are evaluated when the partial function is invoked, not when it is created. Proper eager capture is a planned semantic correction.

## Scopes

```text
makeA =
  ^name = "A"
  ^count = 10

a = makeA
print a.name
```

A missing required field is an error:

```text
a.enabled
```

Optional lookup returns `~`:

```text
a.enabled~
```

Scopes are immutable in this prototype.

## Dynamic lookup

```text
field = #count
a[field]~
a[#count]~
a["count"]~
```

Dynamic names can be name values or strings. The `~` suffix makes a missing binding a normal result instead of an error.

## Reflection

```text
meta = @a
meta.kind
meta.size
meta.names
```

Current metadata:

- all values: `kind`
- scopes: `size`, `names`
- functions: `remaining`

The metadata representation is intentionally minimal. A later version should expose iterable field descriptors, parameter descriptors, mutability, ownership, nullability, optionality, and export status.

## Operators and precedence

From lower to higher precedence:

1. conditional `& ... ! ...`
2. `or`
3. `and`
4. equality `== !=`
5. comparison `< <= > >=`
6. addition `+ -`
7. multiplication `* / %`
8. unary `- not @`
9. function application
10. field and dynamic lookup

## Not implemented

- static types and `T?`, `T~`, `T?~`
- cycle primitive and immutable scope transitions
- multiline call arguments or trailing blocks
- lambdas
- mutation and immutable scope-update expressions
- numbered holes and argument reordering
- resource ownership and deterministic destruction
- rich reflection and reflected invocation
- modules, imports, compiler backend, bytecode, optimizer
