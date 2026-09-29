# Low-precedence application

[Chapter index](../07-state-containers-and-scoped-lookup.md) · [Language specification index](../../LANGUAGE.md)


<a id="section"></a>
## `$`

<a id="overview-3"></a>
### Overview

Caret uses whitespace for ordinary function application:

```caret
f x
```

Whitespace application binds tightly.

When a complete expression should be evaluated first and then supplied as an argument to the expression on its left, Caret provides `$`.

Example:

```caret
print $ calculate x
```

is equivalent to:

```caret
print (calculate x)
```

`$` is therefore a **low-precedence application operator**.

---

<a id="basic-semantics"></a>
### Basic semantics

The general form is:

```caret
functionExpression $ argumentExpression
```

Semantically:

```text
left $ right
```

means:

```text
left (right)
```

after the right-hand expression has been grouped as a whole.

For example:

```caret
put health $ health{} - damage
```

means:

```caret
put health (health{} - damage)
```

---

<a id="is-syntax-level-application"></a>
### `$` is syntax-level application

`$` is not an ordinary binary function.

Its purpose is to affect parsing and expression grouping.

The parser must therefore interpret:

```caret
f $ expression
```

as low-precedence application before ordinary function dispatch occurs.

Semantically it reduces to ordinary function application after parsing.
It therefore uses the same arity, partial-application and hole behavior, contracts, effects,
call-depth guard, source locations, and call diagnostics as whitespace application. `$` introduces
no runtime callable or independently reflectable operator value.

---

<a id="right-associativity"></a>
## Right associativity

`$` is right-associative.

Therefore:

```caret
a $ b $ c
```

means:

```caret
a $ (b $ c)
```

which is equivalent to:

```caret
a (b c)
```

For example:

```caret
print $ toString $ calculate value
```

means:

```caret
print (toString (calculate value))
```

This permits nested application without repeated parentheses.

---

<a id="low-precedence"></a>
## Low precedence

`$` should bind more weakly than ordinary expressions on its right.

For example:

```caret
print $ a + b * c
```

means:

```caret
print (a + b * c)
```

not:

```caret
(print a) + b * c
```

Likewise:

```caret
put health $ max 0 $ health{} - damage
```

means:

```caret
put health
  (max 0
    (health{} - damage))
```

The practical rule is:

> The right-hand side of `$` extends as far as possible.

---

<a id="and-ordinary-application"></a>
## `$` and ordinary application

Caret therefore has two complementary application forms.

High-precedence application:

```caret
f x
```

Low-precedence application:

```caret
f $ expression
```

For example:

```caret
print toString value
```

uses ordinary arity-directed whitespace application.

By contrast:

```caret
print $ toString value
```

explicitly groups:

```caret
toString value
```

as the argument to `print`.

---

<a id="and-lambdas"></a>
## `$` and lambdas

`$` should bind more weakly than lambda construction.

Therefore:

```caret
map (x -> x * 2) values
```

means:

```caret
map (x -> x * 2) values
```

This allows lambdas to be passed without requiring parentheses in many common cases.

For example:

```caret
filter values $ x -> x > 0
```

instead of:

```caret
filter values (x -> x > 0)
```

Parentheses remain available when a lambda must participate in a more complex surrounding expression.

---

<a id="and-conditionals"></a>
## `$` and conditionals

`$` should bind more weakly than Caret's conditional expression:

```caret
condition & trueValue ! falseValue
```

Therefore:

```caret
print $ valid & value ! fallback
```

means:

```caret
print (valid & value ! fallback)
```

Likewise:

```caret
put result $ condition & a ! b
```

means:

```caret
put result (condition & a ! b)
```

This allows `$` to serve as a general escape from parenthesizing complete conditional expressions.

---

<a id="and-composition"></a>
## `$` and composition

Function composition:

```caret
f >> g
```

binds more tightly than `$`.

Therefore:

```caret
use $ parse >> validate
```

means:

```caret
use (parse >> validate)
```

Likewise:

```caret
map (normalize >> validate) values
```

passes the composed function:

```caret
normalize >> validate
```

as the argument.

---

<a id="and-with"></a>
## `$` and `with`

`$` is particularly useful inside concise `with` blocks.

Example:

```caret
with player
  print $ toString health{}
  put health $ max 0 $ health{} - damage
```

Without `$`, the same expressions would require more grouping:

```caret
with player
  print (toString health{})
  put health (max 0 (health{} - damage))
```

The low-precedence application form keeps the flow of expressions readable.

---

<a id="suggested-precedence"></a>
## Suggested precedence

The exact full precedence table is specified separately, but the relative order should follow approximately:

```text
member / index / container access
    .
    []
    {}

ordinary whitespace application

arithmetic
comparisons
named and symbolic binary operators

function composition
    >>

conditional
    & !

lambda
    ->

low-precedence application
    $

assignment / binding
    =
```

The essential guarantees are:

```text
ordinary application binds tightly

>> binds more tightly than $

conditionals bind more tightly than $

lambdas bind more tightly than $

$ is right-associative
```

---

<a id="implementation-requirements-2"></a>
<a id="implementation-requirements-1"></a>
## Implementation requirements

The initial implementation should support at minimum:

1. Basic `with` blocks:

```caret
with value
  body
```

2. Direct lookup of exported named members.

3. Local bindings shadowing `with` members.

4. `with` members shadowing enclosing lexical bindings.

5. Explicit enclosing-scope access:

```caret
outer.name
```

6. Arbitrarily nested scope traversal:

```caret
outer.outer.name
```

7. Nested `with` blocks.

8. `with` working with heterogeneous collections containing named fields.

9. `with` working with named Collections and ruleset public interfaces.

10. Normal visibility rules inside `with`.

11. No implicit copying or destructuring of members.

12. Reification of members inside a `with` block:

```caret
@field
```

13. `with` as an expression returning its body's result.

14. Low-precedence application:

```caret
f $ expression
```

15. Right-associative `$`.

16. `$` binding below ordinary application.

17. `$` binding below arithmetic and comparison expressions.

18. `$` binding below `>>`.

19. `$` binding below conditional expressions.

20. `$` binding below lambda expressions.

21. `$` reducing semantically to ordinary function application after parsing.

The initial implementation may postpone:

* special optimizations for `with`;
* compile-time flattening of nested `outer` chains;
* advanced IDE visualization of scope resolution;
* alternative low-precedence application operators.

No separate record type, implicit receiver object, or multi-object `with` syntax is required.

---

<a id="design-principle-2"></a>
<a id="design-principle-1"></a>
## Design principle

`with` changes how names are resolved, not what values are.

For:

```caret
with value
  body
```

the exported named members of `value` become directly visible in `body`.

Shadowed enclosing names remain explicitly reachable through:

```caret
outer
outer.outer
...
```

Nested `with` blocks express lookup priority naturally.

`$` complements Caret's whitespace application:

```caret
f x
```

means tightly bound application.

```caret
f $ expression
```

means evaluate the complete right-hand expression and pass its result to the left-hand expression.

Together, `with`, `outer`, and `$` allow Caret code to remain concise without introducing implicit object receivers or excessive grouping parentheses.
