<a id="functions-operators-and-lambdas"></a>
# Functions, Operators, and Lambdas

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

<a id="bindings-and-functions"></a>
## Bindings and functions

```text
x = 10
add a b = a + b

makePerson name age =
  internal = age + 1
  ^name = name
  ^age = age
```

The constant/operator spellings `true`, `false`, `and`, `or`, `not`, the planned lexical forms
`with`, `outer`, `root`, and `module`, `_`, and numbered holes such as `_1` are reserved and cannot
be used as binding or parameter names.

<a id="function-application"></a>
## Function application

```text
add 2 3
```

Application is left-associative and has high precedence.

The standard bindings `rule`, `template`, `format`, `cycle`, `contract`, and `sandbox` use this
ordinary application machinery. They are not keywords, declaration forms, literals, macros, DSL
introducers, or parser constructs. Parsing `someName argument` is independent of whether
`someName` has one of those spellings. Normal multiline application, arity, prefix and hole partial
application, aliases, contract-based overload dispatch, callable contracts and effects,
reflection/reification, `#` execution, lexical lookup, and shadowing apply unchanged. No second
invocation mechanism exists for these functions.

An implementation may intrinsically recognize the resolved language-owned callable identity for
static analysis, optimization, staging, or runtime support. It must not infer special behavior from
the lexical identifier. Consequently aliases preserve the callable behavior of non-nullary
constructors, subject to the ordinary nullary-function and callable-reference rules:

```caret
makeRule = rule
makeTemplate = template
makeContract = contract
iterate = cycle
isolate = sandbox
```

Their results may still be specialized semantic values rather than undifferentiated Collections.

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

Expressions inside parentheses and dynamic-lookup brackets may span physical lines. Indentation
inside the delimiters is continuation layout and does not start a function body:

```caret
result = (
  add
    1
    2
)

value = record[
  "field"
]~
```

More-indented ungrouped multiline arguments are implemented. Trailing callable blocks remain
planned until lambda syntax is implemented; their layout rule is specified in the implementation
roadmap below.

`print` also has a statement form. The complete remainder of its logical line is parsed as one
expression, so common output does not require grouping:

```text
print add 1 2
print condition & "yes" ! "no"
```

This whole-line grouping applies only when `print` resolves to the builtin output function. A
lexical binding named `print` shadows the builtin and uses ordinary left-associative application.

This does not change ordinary application associativity: outside the `print` statement, `f x y`
still means `(f x) y`.

<a id="arbitrary-partial-application"></a>
## Arbitrary partial application

```text
between low value high = value >= low and value <= high
inside = between 0 _ 10
inside 7
```

Every `_` introduces a future argument, ordered left to right.

Non-hole parts of a partial expression are evaluated and captured when the partial function is
created.

Numbered holes reorder and reuse future arguments:

```caret
reordered = f _2 fixed _1
duplicated = pair _1 _1
```

The highest hole number determines the resulting arity. Repeated numbers reuse the same argument.
Numbered and unnumbered holes cannot be mixed in one partial expression.

When the callable is an overload set, every supplied argument filters the viable variants for its
known parameter position immediately. This includes fixed operands captured while constructing a
hole partial, even when an earlier parameter remains a hole. The resulting partial callable retains
the common arity, the sparse set of filled positions, every still-viable variant, and the cached
applicability results for those positions. Supplying an argument creates a new immutable narrowed
callable; the earlier partial remains reusable and independent.

Narrowing does not commit early when only one variant remains. Selection and invocation occur only
after every parameter position is filled, so a less-specific fallback remains available until its
own later requirements fail and overload diagnostics remain distinct from ordinary single-function
contract failures. If a supplied value eliminates the last viable variant, the partial application
fails immediately with `NO_APPLICABLE_OVERLOAD` at that argument or application step.

<a id="operators-and-precedence"></a>
## Operators and precedence

From lower to higher precedence:

1. low-precedence application `$`
2. composition `>>`
3. conditional `& ... ! ...`
4. `or`
5. `and`
6. equality `== !=`
7. comparison `< <= > >=`
8. named binary infix functions
9. addition `+ -`
10. multiplication `* / %`
11. unary `- not @`
12. function application
13. field and dynamic lookup

Lambda construction will also bind more tightly than `$` once lambdas are implemented.

The planned compile-time marker `#` is not part of this precedence ladder. In expression position it
opens a compile-time region covering the remainder of the current syntactic expression boundary.
The planned layout markers `\\` and `\*` are also absent from the ladder: unlike `$`, `@`, and `#`,
they are consumed by layout handling before expression parsing and have no expression precedence.
The roles remain separate: `$` groups syntax-level application, `@` reifies a binding or program
entity, `#` changes execution stage, and `\\`/`\*` change only the mapping from physical to logical
indentation.

<a id="unified-binary-functions-and-operators"></a>
## Unified binary functions and operators

A binary operator and a function taking two parameters are the same kind of callable
value. Either may be called with prefix notation or placed between its arguments with infix
notation:

```text
add left right = left + right

add 2 3       // prefix call of a named function
2 add 3       // infix call of the same named function

+ 2 3         // prefix call of a symbolic operator
2 + 3         // infix call of the same symbolic operator
```

The parser preserves a potentially ambiguous named call without consulting declarations elsewhere
in the source. Semantic analysis then attaches lexical callable-arity facts where available, and
evaluation uses the visible runtime binding when static arity is unknown. This prevents a nested or
later declaration from changing how an unrelated expression is interpreted. The choice still
follows the beginning of the expression:

- If the first expression is a value, or a function taking no parameters, and the next expression
  denotes a function taking two parameters, the form is an infix call. The first expression is the
  first argument and the following expression is the second argument.
- If the first expression denotes a function that takes one or more parameters, the form is a
  prefix call of that function. Later binary functions in the argument sequence do not change that
  initial choice.

Named binary functions used in infix position have one fixed precedence level. They are
left-associative, bind less tightly than additive operators, and bind more tightly than comparison
operators. Thus `2 combine 3 + 4` means `2 combine (3 + 4)`, while `2 combine 3 < 10` means
`(2 combine 3) < 10`. Parentheses are required when another grouping is intended.

Built-in symbolic operators retain the precedence table documented above. User-defined symbolic
operators are deliberately unsupported for now; language extensions use named infix functions.
A later language version may add a quoted-symbol declaration resembling:

<!-- caret-example: planned -->
```caret
`#-!` x y = f x y
```

That spelling is only a design direction and is not valid Caret syntax.

Analyzed named infix calls invoke the same callable values as prefix application. A non-callable
infix target or a callable whose remaining arity is not two produces a located runtime diagnostic.

<a id="function-composition"></a>
## Function composition

`left >> right` creates an ordinary callable that applies `left` and passes its result to `right`:

```caret
double value = value * 2
asText = double >> numberText
print asText 5
```

`>>` is left-associative. `$` application binds below it. The left operand may require any positive number
of remaining arguments; the composition retains that arity and supports ordinary partial
application. The right operand must require exactly one remaining argument. Inline partial operands
work normally, as in `add _ 10 >> numberText`.

Both operands are validated when the composition is created. Non-callable operands, a nullary left
operand, or a non-unary right operand produce located runtime diagnostics. Nullary composition is
deferred until Caret has a separate first-class callable-value design; `@function` remains a
non-callable reflective reference. The completed left result is passed as one value even when that
value is itself callable. Composition uses the ordinary invocation path and therefore preserves
call-depth checks and argument locations. Contract and effect propagation will be added with the
planned contract/effect system.

<a id="lambda-functions"></a>
## Lambda Functions

<a id="overview"></a>
### Overview

Lambda expressions create anonymous first-class functions.

The basic syntax is:

```caret
x -> expression
```

Example:

```caret
square = x -> x * x
```

A lambda may have multiple parameters:

```caret
x y -> x + y
```

Equivalent named function:

```caret
add x y =
  x + y
```

and lambda:

```caret
add = x y -> x + y
```

Lambda parameters are separated by whitespace, consistently with ordinary Caret function declarations and application.

---

<a id="lambda-bodies"></a>
### Lambda bodies

A lambda may contain a single expression:

```caret
x -> x * 2
```

or a block at a deeper effective logical indentation:

```caret
x ->
  doubled = x * 2
  doubled + 1
```

The result of the final expression is the result of the lambda, following the same rules as an
ordinary function body. Planned layout modifiers may shift the block physically, but do not change
its extent, captures, parameters, or result.

Example:

```caret
normalize =
  text ->
    trimmed = trim text
    lowercase trimmed
```

No braces, commas, or explicit `return` keyword are required.

---

<a id="parameter-contracts"></a>
### Parameter contracts

Lambda parameters use the same contract syntax as named-function parameters.

Example:

```caret
(Int) x -> x * 2
```

Multiple contracts are written in one parenthesized contract clause:

```caret
(Int positive) x -> x * 2
```

Multiple parameters may each have their own contracts:

```caret
(Int) x (Int positive) y ->
  x + y
```

Contracts have exactly the same semantics as on named function parameters.

For example:

```caret
(Int positive) x -> x * 2
```

requires `x` to satisfy both `Int` and `positive`.

The compiler should statically verify contracts wherever possible and retain runtime checks only where necessary according to the normal Caret contract rules.

---

<a id="arity"></a>
### Arity

A lambda's arity is the number of explicitly declared parameters.

```caret
x -> expression
```

has arity 1.

```caret
x y -> expression
```

has arity 2.

```caret
a b c -> expression
```

has arity 3.

Lambda arity participates in Caret's ordinary arity-directed function application and binary-function interpretation.

For example:

```caret
compare = a b -> a.value < b.value
```

creates an ordinary two-argument function and may be used anywhere another binary function can be used.

---

<a id="application"></a>
### Application

Lambda values are called using ordinary whitespace application.

Example:

```caret
double = x -> x * 2

result = double 10
```

A lambda may also be created and immediately applied:

```caret
(x -> x * 2) 10
```

Parentheses are required here to delimit the lambda expression before its argument.

Application is left-associative according to the normal Caret rules.

---

<a id="partial-application"></a>
### Partial application

Multi-parameter lambdas support ordinary partial application.

Given:

```caret
add = x y -> x + y
```

then:

```caret
add 10
```

returns a unary function awaiting `y`.

Example:

```caret
addTen = add 10
result = addTen 5
```

Caret's arbitrary-position hole syntax also works with lambdas and lambda-derived functions.

For example:

```caret
between = low value high ->
  value >= low and value <= high

inside = between 0 _ 10
```

`inside` is a unary function.

---

<a id="lambdas-versus-holes"></a>
### Lambdas versus holes

Caret supports both explicit lambdas and implicit partial application through `_`.

For simple partial application:

```caret
addOne = + _ 1
```

is preferred over unnecessarily verbose lambda syntax:

```caret
addOne = x -> x + 1
```

Both are valid and semantically compatible.

Explicit lambdas are useful when:

* a parameter is used more than once;
* multiple expressions are needed;
* the parameter needs a meaningful local name;
* parameter contracts are needed;
* control flow is required;
* the body cannot be expressed naturally through partial application.

Example:

```caret
distanceSquared = p ->
  p.x * p.x + p.y * p.y
```

A hole `_` always denotes a future argument to an existing application expression. It is not itself a named lambda variable.

---

<a id="closures"></a>
### Closures

A lambda may reference bindings from its lexical environment.

Example:

```caret
makeAdder amount =
  x -> x + amount
```

Then:

```caret
addFive = makeAdder 5
addFive 10
```

produces:

```text
15
```

The lambda captures `amount`.

Captured values follow Caret's normal ownership, mutability, and lifetime rules.

A closure must not provide a way to access a value after its ownership or lifetime has ended.

The compiler may copy, borrow, share, or move captured values according to the applicable ownership rules.

---

<a id="capture-timing"></a>
### Capture timing

Captured expressions are evaluated according to normal lexical evaluation semantics when the closure is created.

For example:

```caret
amount = calculateAmount source
f = x -> x + amount
```

the lambda captures the resulting `amount`; it does not implicitly call `calculateAmount` again whenever `f` is invoked.

This is consistent with arbitrary partial application:

```caret
f = calculate _ expensiveExpression
```

where supplied expressions are evaluated when the partial function is constructed unless explicitly represented as another function.

---

<a id="purity-and-effects"></a>
### Purity and effects

Lambda effects are inferred exactly like effects of named functions.

Example:

```caret
square = x -> x * x
```

has an empty inferred effect set and is pure.

An effectful lambda:

```caret
writer = x ->
  writeFile path x
```

inherits the filesystem effect of `writeFile`.

Effects propagate through:

* direct calls;
* captured functions;
* higher-order calls;
* composition;
* partial application.

A lambda passed to a parameter requiring purity must have an empty inferred effect set.

For example:

```caret
map (pure) transform values =
  ...
```

accepts:

```caret
map (x -> x * 2) values
```

but rejects an effectful lambda.

Explicit function-value contracts use ordinary binding or parameter boundaries. For example:

```caret
([Int] -> (pure Int)) double = x -> x * 2
```

requires the assigned lambda to accept `Int`, return `Int`, and be pure. A lambda passed directly to
a higher-order parameter is checked against that parameter's arrow-signature contract in the same
way. Mixed clauses are not a separate general expression-ascription syntax.

Purity must always be verified from the lambda body; the contract is a requirement, not merely an annotation.

---

<a id="lambda-return-values"></a>
### Lambda return values

A lambda returns the value produced by its body.

Single-expression example:

```caret
x -> x * 2
```

Block example:

```caret
x ->
  a = x * 2
  b = a + 1
  b
```

returns `b`.

Lambda result guarantees are expressed as part of an arrow-signature contract on a binding or
surrounding higher-order parameter. Do not introduce a separate lambda-specific return-type syntax.

---

<a id="nullary-lambdas"></a>
### Nullary lambdas

Caret may represent a zero-argument anonymous function as:

```caret
-> expression
```

Example:

```caret
action =
  ->
    calculateSomething
```

A nullary lambda is a function value.

Creating the lambda does not execute its body.

This differs from referring to a named zero-argument function by its ordinary name, where Caret's normal nullary-function evaluation rules may invoke the function.

The lambda literal itself is already an explicit function value and therefore does not require `@`.

For example:

```caret
action = -> currentTime
```

stores a function.

Invoking `action` follows the normal rules for a nullary function.

The exact invocation syntax for a stored nullary function should remain consistent with the general nullary-function rules.

---

<a id="reification"></a>
### Reification

A lambda is already a function value.

It does not require `@` in order to be passed to another function:

```caret
map (x -> x * 2) values
```

`@` remains the general binding-reference/reification operator and is primarily needed when referring to an existing binding without applying its normal evaluation behavior.

For example:

```caret
@namedFunction
```

reifies the binding `namedFunction`.

Do not redefine `@` as lambda syntax.

---

<a id="higher-order-functions"></a>
### Higher-order functions

Lambdas are ordinary function values and may be:

* passed as arguments;
* returned from functions;
* stored in collections;
* stored in fields;
* composed;
* partially applied;
* reflected;
* constrained by function contracts.

Example:

```caret
apply transform value =
  transform value

result = apply (x -> x * 2) 10
```

A function may return a lambda:

```caret
multiplier factor =
  x -> x * factor
```

A lambda may return another lambda:

```caret
x -> y -> x + y
```

This is equivalent in behavior to a curried two-stage function.

It is distinct in structure from:

```caret
x y -> x + y
```

which is a single lambda with arity 2.

Both may support equivalent partial use where appropriate, but reflection must preserve their actual structure.

---

<a id="function-composition-2"></a>
### Function composition

Lambda values participate in ordinary `>>` composition.

Example:

```caret
process =
  (x -> x * 2)
  >> normalize
  >> validate
```

or:

```caret
double = x -> x * 2
process = double >> normalize
```

Composition preserves inferred contracts and effects according to the normal Caret composition rules.

A composition is pure only if every participating function is pure.

---

<a id="lambdas-in-collection-operations"></a>
### Lambdas in collection operations

Lambdas may be used directly with collection functions.

Examples:

```caret
numbers map (x -> x * 2)
```

```caret
numbers filter (x -> x > 0)
```

```caret
people map (person -> person.name)
```

Because a pure unary Boolean function is a valid Caret contract, a suitable lambda may also represent a runtime predicate.

For example:

```caret
positive = (Int) x -> x > 0
```

is a pure unary Boolean function and therefore satisfies the requirements for use as a contract predicate.

Where a contract must be referenced repeatedly or participate in static reasoning, assigning it a stable name is preferred.

---

<a id="lambdas-and-simd"></a>
### Lambdas and SIMD

Pure lambdas may participate in SIMD application when their operations are vectorizable.

Example:

```caret
values :: (x -> x * x + 1)
```

The compiler should infer that the lambda is pure and determine whether its operations support SIMD execution.

An effectful lambda cannot normally be used with `::`.

Example:

```caret
values :: (x ->
  print x
  x * 2)
```

must fail if `print` introduces an observable effect.

SIMD support does not require separate lambda syntax.

---

<a id="lambdas-in-collection-definitions"></a>
### Lambdas in collection definitions

Because collections contain ordinary Caret expressions, lambda values may be stored directly in data structures.

Example:

```caret
operations =
  [
    ^double = (x -> x * 2)
    ^positive = (x -> x > 0)
  ]
```

The resulting fields contain ordinary function values.

Likewise, a lambda may calculate a field value through immediate application or higher-order functions.

---

<a id="parsing-and-precedence"></a>
### Parsing and precedence

`->` introduces a lambda and has low precedence.

The expression:

```caret
x -> x + 1
```

must parse as:

```text
x -> (x + 1)
```

not:

```text
(x -> x) + 1
```

Multiple parameters immediately preceding `->` belong to the same lambda:

```caret
x y z -> expression
```

Parameter contracts bind to the immediately following parameter:

```caret
(Int) x (String) y -> expression
```

When a lambda appears as an argument inside a larger expression, parentheses should be required wherever its extent would otherwise be ambiguous:

```caret
map (x -> x * 2) values
```

rather than relying on context-sensitive parsing.

An indented lambda body extends through its effective logical indentation block after any active
layout mapping has been applied.

---

<a id="implementation-requirements"></a>
### Implementation requirements

The initial implementation should support at minimum:

1. Unary lambdas:

```caret
x -> expression
```

2. Multi-parameter lambdas:

```caret
x y -> expression
```

3. Parameter contracts:

```caret
(Int positive) x -> expression
```

4. Indented lambda bodies:

```caret
x ->
  expression
  expression
```

5. Lexical captures.
6. Ordinary function application of lambda values.
7. Partial application.
8. Interaction with `_` hole-based partial application.
9. Lambda effect and purity inference.
10. Passing lambdas to higher-order functions.
11. Returning lambdas from functions.
12. Function composition using `>>`.
13. Reflection/reification compatibility.
14. SIMD eligibility for suitable pure lambdas.

The initial implementation may postpone:

* sophisticated capture optimization;
* static totality checking;
* explicit capture lists;
* ownership-polymorphic closures;
* specialized allocation-free closure representations.

These implementation optimizations must not alter the semantic rule that a lambda is an ordinary first-class Caret function value.

---

<a id="design-principle"></a>
### Design principle

Lambda syntax should remain a minimal anonymous form of ordinary Caret function syntax.

Named function:

```caret
add x y =
  x + y
```

Anonymous equivalent:

```caret
x y -> x + y
```

Caret should not create a separate semantic category for lambdas.

They use the same:

* application rules;
* contracts;
* arity;
* partial application;
* purity/effect inference;
* composition;
* ownership rules;
* reflection;
* SIMD rules

as named functions.
