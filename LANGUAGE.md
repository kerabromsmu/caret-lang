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

Number literals start with a digit and may contain at most one decimal point. Malformed number
literals are reported as language errors rather than leaking a Java numeric-conversion exception.

## Comments

`//` introduces a line comment. `#name` is always a name value, including when it appears at the
beginning of a line; `#` is not a comment marker.

## Diagnostics

Lexical, parse, and runtime errors include the one-based line and column of the smallest relevant
source expression. Columns count raw source characters. A tab therefore advances the displayed
column by one, although a leading tab still contributes two spaces to indentation depth.

```text
Error: Line 1, column 7: Unknown name: absent
```

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

A zero-argument function is evaluated when its name is read. Use reflection syntax to refer to the
function itself without invoking it:

```text
factory =
  ^value = 42

factory          // calls factory and produces its exported scope
@factory         // reflects the factory function itself without calling it
```

This rule is not limited to zero-argument functions. `@function` refers to the function binding
without invoking it regardless of the function's arity. Its reflective view includes `kind` and
`remaining`.

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

`print` also has a statement form. The complete remainder of its logical line is parsed as one
expression, so common output does not require grouping:

```text
print add 1 2
print condition & "yes" ! "no"
```

This does not change ordinary application associativity: outside the `print` statement, `f x y`
still means `(f x) y`.

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

functionMeta = @function
functionMeta.kind
functionMeta.remaining
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

## Implementation planned

The following facilities and semantic decisions are planned prerequisites for implementing a
Caret interpreter in Caret. They describe the current design direction, not behavior implemented
by this prototype.

### Source text operations

A Caret lexer needs a small set of text primitives:

```text
textSize text
textAt text index
textSlice text start end
textNumber text
numberText number
```

Text indexes are planned to count Unicode code points rather than UTF-16 code units. An invalid
index or a failed numeric conversion should return `~` instead of throwing for an expected
condition. Exact slice-boundary behavior still needs to be specified before implementation.

### Immutable collections

Token streams, syntax trees, environments, and captured output need immutable sequences and
dictionaries. The planned minimum operations are equivalent to:

```text
seqEmpty
seqAdd sequence value
seqGet sequence index
seqSize sequence

dictEmpty
dictPut dictionary key value
dictGet dictionary key
dictHas dictionary key
dictKeys dictionary
```

Dictionary keys are planned to accept strings and name values, and key iteration should preserve
insertion order. `dictHas` must distinguish an absent key from a present key whose value is `~`.
Collection literal syntax is not required for the initial self-interpreter.

### Unified binary functions and operators

A binary operator and a function taking two parameters are planned to be the same kind of callable
value. Either may be called with prefix notation or placed between its arguments with infix
notation:

```text
add left right = left + right

add 2 3       // prefix call of a named function
2 add 3       // infix call of the same named function

+ 2 3         // prefix call of a symbolic operator
2 + 3         // infix call of the same symbolic operator
```

The parser will distinguish the two forms from the beginning of the expression:

- If the first expression is a value, or a function taking no parameters, and the next expression
  denotes a function taking two parameters, the form is an infix call. The first expression is the
  first argument and the following expression is the second argument.
- If the first expression denotes a function that takes one or more parameters, the form is a
  prefix call of that function. Later binary functions in the argument sequence do not change that
  initial choice.

This is planned behavior and is not implemented by the current parser. The rules for declaring the
precedence and associativity of named and symbolic binary functions still need to be specified.

### Semantics to resolve

The following behavior must be specified before the Caret implementation can be treated as a
conforming interpreter:

- direct and mutual recursion;
- whether closures capture a snapshot or a live scope, including visibility of later definitions;
- duplicate definitions, rebinding, parameter shadowing, and parent-scope lookup;
- exact operand rules for operators and equality, including collections and scopes;
- precedence and associativity for functions used with infix notation;
- division by zero and other non-finite numeric results;
- recognized string escapes and diagnostics for invalid escapes;
- whether `@function` is metadata only or a callable reflective reference; and
- structured propagation of lexical, parse, and runtime diagnostics with source spans.

The self-interpreter may represent successful and failed operations as exported result scopes. Its
CLI adapter can then render a failed result as the normal located `Error:` diagnostic.

### Not required for self-interpretation

The first Caret-written interpreter does not depend on static types, loops, mutation, modules,
lambdas, pattern matching, ownership, reflected invocation, or a compiler backend. Recursion,
immutable collections, tagged exported scopes, and the planned text operations are sufficient.

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
