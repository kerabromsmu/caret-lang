# Caret language specification

The opening section describes the prototype as it currently behaves. Later sections describe the
planned language and are explicitly separated from implemented behavior.

# Implemented prototype

## Values

```text
42          number
"text"      string
true false  Boolean
?           null
~           missing
```

Null and missing are separate runtime values.

`type value` returns the public runtime kind name used by reflection, including `"Null"` for `?`,
`"Missing"` for `~`, and `"Function"` for a non-callable function reference produced by `@`.

Number literals start with a digit and may contain at most one decimal point. Malformed number
literals are reported as language errors rather than leaking a Java numeric-conversion exception.
Numbers must remain finite. Literals outside the finite range and arithmetic producing a non-finite
result are errors. Division and remainder by zero are errors.

Strings recognize `\\`, `\"`, `\n`, `\r`, `\t`, and Unicode code-point escapes written as
`\u{1F642}`. Unknown, incomplete, surrogate, and out-of-range escapes are lexical errors.

## Comments

`//` introduces a line comment. Field names and other identifiers represented as data use ordinary
strings rather than a separate name-literal syntax.

## Diagnostics

Lexical, parse, and runtime errors include the one-based line and column of the smallest relevant
source expression. Columns count raw source characters. A tab therefore advances the displayed
column by one, although a leading tab still contributes two spaces to indentation depth.
The planned layout-baseline modifiers do not change these coordinates: diagnostics continue to use
physical source lines and columns even when effective logical indentation differs.
Built-in argument validation retains individual argument spans, so an invalid operand points to
that operand rather than the complete call.

Internally, diagnostics retain their phase, a stable diagnostic code, message, primary source span,
and related source spans. Planned causes and subsystem-specific detail payloads belong to the
`ErrorTemplate` model described below. A diagnostic that
aborts lexing, parsing, analysis, or evaluation is not thereby an ordinary catchable Caret value.
The CLI renders the primary location in the compact form below and follows it
with located `Note:` lines when a diagnostic has related locations, such as the first declaration
for a duplicate definition.

```text
Error: Line 1, column 7: Unknown name: absent
```

## Test files

The CLI can run one Caret test file with `caret test file.caret`. Test mode adds two assertion
functions without changing the language grammar:

```caret
assert "descriptive name" condition
assertEqual "descriptive name" actual expected
```

`assert` requires a string name and a Boolean condition. `assertEqual` requires a string name and
uses the same structural equality rules as `==`; callable values therefore cannot be compared.
Both functions return `~` after recording their result.

Passing and failing assertions are written to standard output. Failures include the line and column
of the complete assertion call plus expected and actual values. Evaluation continues after an
assertion mismatch and ends with a summary. The process succeeds only when at least one assertion
ran and every assertion passed.

Assertion arguments remain eager. Lexical, parse, and runtime errors abort the file, are written to
standard error as normal located diagnostics, and do not produce a completed summary. Assertions
are test-runner builtins and are not present during ordinary file or REPL execution.

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

### Contract foundation currently implemented

The prototype provides first-class unary contracts matching its existing runtime kinds: `Any`,
`Number`, `String`, `Boolean`, `Null`, `Missing`, `Function`, `Scope`, `Sequence`, and `Dictionary`.
Calling a contract tests membership and returns a Boolean:

```caret
Number 42
String "text"
```

Simple clauses constrain bindings and function parameters dynamically:

```caret
(Number) count = 1
add (Number) left (Number) right = left + right
```

Arguments are checked as they fill parameters, including during partial application. Contracted
initializers are checked before their bindings commit. `type Number` and `(@Number).kind` report
`"Contract"`, and `(@Number).name` reports `"Number"`.

The unary `contract` function constructs nominal contracts. `contract ~` creates a base contract,
`contract A` derives from one contract, and `contract [A B]` derives from several contracts packaged
in one ordinary collection argument. Explicit binding and parameter clauses acquire nominal
membership while checking built-in base constraints; leading function clauses check and attribute
results. Unannotated named functions infer parameter and result contracts from their values,
operations, calls, and surrounding context. When callable contracts remain unresolved, they are
generalized and each external use receives a fresh instantiation. Ordinary non-callable bindings
must instead resolve from their initializer or context. An actual use that still leaves a required
contract variable unresolved is a located compile-time ambiguity error.

The semantic analyzer also computes an initial effect summary for named functions. It propagates
known effects through direct named calls, includes effects from the fixed subexpressions captured
eagerly while constructing partials, and records an
unknown-call marker when dynamic invocation prevents a purity proof. This internal summary can
prove that a prospective refinement is unary, Boolean-returning, and pure. Effect declarations,
ordinary-function enforcement, and effect reflection/tooling remain planned; the internal output
marker for `print` is not public syntax. Proven predicates are implemented as first-class refinement
requirements in `contract` construction and direct clauses, including through ordinary aliases.
Contract equality is identity-based: aliases of one descriptor compare equal, while every separate
evaluation of `contract` creates an unequal descriptor even when its requirements are identical.
Names and reflective metadata do not participate in equality. Contract reflection exposes `name`,
`bases`, and language-owned refinement
`requirements`.

Parameterized contracts, nullable/optional modifiers, overload dispatch, complete
static inference/proof, the public effect system, and the full universal-collection model remain
planned below.

In the current prototype, physical indentation directly defines a multiline function body. The
planned layout modifiers described below will first translate physical indentation into effective
logical indentation; the ordinary block rules will then consume that logical indentation. If a
body contains exported bindings (`^`), calling the function returns an immutable scope containing
those exports. Otherwise it returns the final expression or assigned value.

A zero-argument function is evaluated when its name is read. Use reflection syntax to refer to the
function itself without invoking it:

```text
factory =
  ^value = 42

factory          // calls factory and produces its exported scope
@factory         // reflects the factory function itself without calling it
```

This rule is not limited to zero-argument functions. `@function` refers to the function binding
without invoking it regardless of the function's arity. The result is a non-callable function
reference whose reflective fields include `kind` and `remaining`. References to the same function
compare equal by target identity; references to different functions do not.

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

Expressions inside parentheses and dynamic-lookup brackets may span physical lines. Indentation
inside the delimiters is continuation layout and does not start a function body:

```caret
result = (
  add
    1
    2
)

value = scope[
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

Non-hole parts of a partial expression are evaluated and captured when the partial function is
created.

Numbered holes reorder and reuse future arguments:

```caret
reordered = f _2 fixed _1
duplicated = pair _1 _1
```

The highest hole number determines the resulting arity. Repeated numbers reuse the same argument.
Numbered and unnumbered holes cannot be mixed in one partial expression.

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
field = "count"
a[field]~
a["count"]~
```

Dynamic names are strings. The `~` suffix makes a missing binding a normal result instead of an error.

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
- function references: `kind = "Function"`, `remaining`

`@function` is a reference and reflection mechanism, not an alternate call syntax. Applying the
result is a `NOT_CALLABLE` error. Reflecting an existing function reference returns the same
reference.

The metadata representation is intentionally minimal. A later version should expose iterable field descriptors, parameter descriptors, mutability, ownership, nullability, optionality, and export status.

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

## Implementation roadmap

The following facilities and semantic decisions are planned prerequisites for implementing a
Caret interpreter in Caret. They describe the current design direction, not behavior implemented
by this prototype.

### Source text operations

The prototype provides the text primitives needed by a Caret lexer:

```text
textSize text
textAt text index
textSlice text start end
textNumber text
numberText number
```

Text indexes count Unicode code points rather than UTF-16 code units. Slices use half-open
`[start, end)` bounds. An invalid index, invalid bounds, or failed numeric conversion returns `~`
instead of throwing for an expected condition.

### Immutable collections

The prototype provides immutable sequences and insertion-ordered dictionaries through:

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

Dictionary keys are strings, and key iteration preserves insertion order. `dictHas` distinguishes
an absent key from a present key whose value is
`~`.
Collection literal syntax is not required for the initial self-interpreter.

### Unified binary functions and operators

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

### Function composition

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

### Ungrouped multiline application

In the current prototype, outside an explicit delimiter, a physical line indented more deeply than
a non-definition expression continues that expression. With layout modifiers, this rule instead
compares effective logical indentation after the physical-to-logical mapping. Each continuation
expression is the next whitespace-applied argument at ordinary application precedence, and logical
dedentation ends the call. Lower-precedence
operators on the initial line remain outside that application: `true & add` followed by indented
`1` and `2` means `true & (add 1 2)`.

```caret
result = add
  1
  multiply
    2
    3
```

is equivalent to `result = add 1 (multiply 2 3)`. Blank and comment-only lines do not end a
continuation. An empty function-definition right side still opens a function body and takes
precedence over continuation parsing.

Sibling continuation arguments use the same effective indentation. A logically deeper line
continues the immediately preceding argument; dedenting to a logical indentation other than an
established enclosing level is a located layout error. A continuation line is an expression and
cannot contain a definition.

More-indented application is implemented by the current parser. Once lambdas are implemented, an
indented trailing lambda will be the final call argument; its body will be delimited by its own
effective logical indentation in the ordinary way.

### Planned layout baseline modifiers

Caret normally derives logical block structure from physical indentation. The planned layout tokens
`\\` and `\*` allow a region to occupy fewer physical source columns without changing its logical
nesting. They are layout syntax only: neither token is an expression, operator, function, value,
scope, binding, effect, or runtime operation.

The relevant distinction is:

```text
physical indentation
    columns occupied by source text in the file

effective logical indentation
    indentation supplied to Caret's ordinary layout parser
```

Layout handling computes effective logical indentation before ordinary indentation and expression
parsing. All normal block, continuation, visibility, and evaluation rules then apply unchanged.

#### `\\` adjusts the physical baseline

`\\` is written as the final layout token on a construct that opens an indentation-defined region.
It pushes the current physical-to-logical mapping and activates an adjusted mapping for the
following region:

<!-- caret-example: planned -->
```caret
with import clientServer \\
connect url
send request
\*
nextOperation
```

This has the same logical structure as:

<!-- caret-example: planned -->
```caret
with import clientServer
  connect url
  send request
nextOperation
```

The first nonblank, non-comment body line after `\\` establishes the adjusted physical baseline.
That baseline maps to one logical child indentation level beneath the opening construct. Further
physical indentation is interpreted relative to that baseline, preserving sibling and nested
relationships. The adjustment is structural; it does not subtract a fixed number of source-space
columns, depend on formatter width, or assign numeric meaning to individual backslash characters.
Forms such as `\statement` or longer runs of backslashes are not graduated indentation controls.

The marker changes only the baseline mapping. It does not open an additional block, close a block,
create a semantic scope, change lexical visibility, alter evaluation order, or change the meaning
of any declaration or expression.

Relative indentation continues normally inside the adjusted region:

<!-- caret-example: planned -->
```caret
with import clientServer \\
response &
  process response
!
  reportFailure
\*
```

This is logically equivalent to:

<!-- caret-example: planned -->
```caret
with import clientServer
  response &
    process response
  !
    reportFailure
```

Physical dedentation may close nested logical constructs according to the active mapping, but it
does not restore the previous mapping. A `\\` adjustment remains active until a `\*` restoration or
EOF. The layout processor must not infer the end of the adjustment merely because later source
appears physically dedented; physical indentation is precisely the dimension being remapped.

Consequently, an explicit restoration is unnecessary when the adjusted mapping may remain active
through the end of the file:

<!-- caret-example: planned -->
```caret
with import clientServer \\
connect url
send request
```

#### `\*` restores the previous mapping

`\*` is normally written alone at the physical indentation appropriate to the adjusted region. It
contributes no logical statement or indentation event. When a previous mapping exists, it pops the
current mapping and restores that previous mapping before processing the next significant source
line:

<!-- caret-example: planned -->
```caret
main =
  with import clientServer \\
connect url
send request
\*
  finish
```

`\*` does not mean end `with`, end function, end lambda, end scope, semantic dedent, return, or any
other control operation. After restoration, the physical indentation of following lines is
interpreted through the restored mapping.

The markers are state modifiers rather than paired delimiters. An unmatched `\*` is a deterministic
no-op. An active `\\` at EOF is valid, and EOF silently discards every remaining mapping. Tooling may
warn about suspicious redundant markers, but such warnings do not alter program semantics.

#### Stacking and nested indentation

Mappings form a stack, so adjusted regions compose without acquiring block semantics:

<!-- caret-example: planned -->
```caret
with outerModule \\
outerCall

with innerModule \\
innerCall
\*

anotherOuterCall
\*
```

The first `\*` restores the outer adjusted mapping; the second restores the original mapping. Each
new `\\` anchors its first significant body line one logical child level below its own opening
construct, even when that construct is already inside an adjusted region.

Conceptually, every significant source line follows this pipeline:

```text
physical indentation
    + active structural layout mapping
    = effective logical indentation
    -> ordinary Caret layout and expression parsing
```

There is no parallel expression parser for adjusted regions.

#### Placement, strings, and comments

The layout/lexer layer recognizes the exact `\\` and `\*` tokens before ordinary expression parsing.
`\\` is valid as the terminal layout token of a header that permits or requires a following
indentation-defined region, including `with`, function bodies, lambdas, conditionals, and other
such constructs. A token in a syntactically impossible position may produce a located malformed-
layout diagnostic. `\*` is a standalone restoration line; its lack of an active mapping is not an
error.

Marker spellings inside strings retain ordinary string-escape semantics and never affect layout:

<!-- caret-example: planned -->
```caret
text = "text\\text"
```

Comments likewise cannot activate, restore, or otherwise change a layout mapping. Blank and
comment-only lines do not establish the physical baseline awaited after `\\`.

#### Interaction with `with` and other indentation-defined forms

`with` is the primary motivating form:

<!-- caret-example: planned -->
```caret
with import clientServer
  connect url
  send request
```

and:

<!-- caret-example: planned -->
```caret
with import clientServer \\
connect url
send request
\*
```

have identical lexical name resolution, visibility, `outer` behavior, field reification, effects,
evaluation, and result. If `connect` is exported by the imported module, it resolves identically in
both bodies.

Function bodies, ungrouped multiline application, continuation indentation, indented or trailing
lambdas, and nested conditionals all consume effective logical indentation after the same mapping
step. A source line physically at column zero may therefore remain logically nested while an
adjustment is active. Once `\*` restores the original mapping, following lines again derive their
logical indentation from that mapping. None of these constructs receives special parsing rules for
adjusted regions.

For example, an ordinary function body and a multiline application may use the same transformation:

<!-- caret-example: planned -->
```caret
main = \\
initialize
run
\*

result = combine \\
first
second
\*
```

Likewise, a lambda body may be shifted without changing the lambda or its captures:

<!-- caret-example: planned -->
```caret
normalize = text -> \\
trimmed = trim text
lowercase trimmed
\*
```

#### Diagnostics and formatting

Diagnostics continue to report existing physical source line and column positions. Tooling may
display effective logical indentation separately, but it must not substitute logical positions for
physical diagnostic locations. Neither an active mapping at EOF nor an unmatched restoration is a
syntax error.

A formatter must preserve the semantic effect of layout mappings. It may retain the explicit
adjusted layout, or on an explicit normalization request rewrite the region using equivalent
conventional indentation. It must not silently remove `\\` or `\*` while leaving physically shifted
source unchanged. Moving or reindenting surrounding source must update the physical-to-logical
mapping as necessary to preserve the same logical program. Formatter policy is not runtime
semantics.

#### Layout-modifier implementation requirements

The initial implementation must:

1. recognize `\\` outside strings and comments as a layout-baseline modifier;
2. recognize `\*` outside strings and comments as layout restoration;
3. retain physical indentation separately from effective logical indentation;
4. apply the active mapping before ordinary indentation parsing;
5. preserve relative sibling and nested indentation while a mapping is active;
6. maintain nested mappings as a stack;
7. make `\*` restore one previous mapping when available;
8. make redundant or unmatched `\*` a deterministic no-op;
9. permit an active `\\` through EOF;
10. discard every remaining mapping at EOF without error;
11. keep diagnostic line and column locations physical;
12. leave runtime semantics, scopes, visibility, contracts, effects, and evaluation unchanged;
13. use effective indentation uniformly for function bodies, `with`, lambdas, multiline
    application, conditionals, and every other indentation-defined construct; and
14. never interpret marker spellings inside strings or comments as layout syntax.

#### Design principle

Caret's indentation determines logical structure, but logical indentation need not always occupy
the same physical source columns. `\\` temporarily shifts the physical indentation baseline while
preserving logical nesting, and `\*` restores the previous baseline. The adjusted mapping continues
until explicitly restored or EOF; ordinary physical dedentation cannot end it because physical
indentation is what the modifier changes. After effective logical indentation is calculated, all
normal Caret parsing and semantic rules apply unchanged.

### Core semantic decisions

Blocks predeclare their function bindings before executing statements. This supports direct and
mutual recursion. Other bindings are initialized in source order and cannot be read before their
declaration executes.

Initialization checks respect lazy evaluation. A reference to a later binding in an unselected
conditional branch or a short-circuited Boolean right operand does not fail; selecting that path
before the declaration executes produces a located `READ_BEFORE_INITIALIZATION` diagnostic.

Top-level execution commits newly declared bindings only when the submitted program completes.
This is observable in the REPL: after a failed submission such as `x = absent`, a later `x = 1`
submission remains valid. External effects already performed before a failure are not rolled back.

Closures capture their lexical environment. Duplicate definitions and duplicate parameters in one
scope are errors. Parameters and declarations in a function body may shadow outer bindings;
function-body declarations are nested inside the parameter scope so established forms such as
`^name = name` export a parameter under the same name. Parent lookup is lexical.

Equality is recursive and structural for scalar values, exported scopes, and collections. Scalar
numeric equality therefore has the same result when numbers are nested in data; for example, `-0`
and `0` compare equal both directly and inside a sequence. Encountering a callable anywhere in
either compared structure is a `CALLABLE_EQUALITY` error. Function references compare by the
identity of their referenced callable.

`@function` produces a non-callable reflective function reference. It suppresses normal implicit
invocation of a nullary binding and exposes `kind` and `remaining`; bare nullary function names
continue to invoke automatically.

Built-in symbolic binary operators are ordinary two-argument callable values. Prefix and infix
forms share the same implementation, arity, partial application, call-depth guard, and errors:

```text
+ 2 3       // 5
2 + 3       // 5
increment = + _ 1
```

Unary negation retains its established parsing for `- name arg`. Use grouping when prefix
subtraction begins with a named operand: `(-) left right`.

Function invocation has an interpreter-owned maximum depth. Both ordinary application and the
implicit invocation of nullary bindings produce a located `CALL_DEPTH_EXCEEDED` diagnostic instead
of exposing JVM stack exhaustion.

The complete operand/coercion rules for operators once static types exist remain a prerequisite for
extending unified binary functions beyond the existing scalar operators.

The self-interpreter may represent successful and failed operations as exported result scopes. Its
CLI adapter can then render a failed result as the normal located `Error:` diagnostic.

# Planned language specification

Everything below this heading is canonical design work unless an individual section explicitly
states that its behavior is already implemented by the prototype above.

### Not required for self-interpretation

The first Caret-written interpreter does not depend on static types, loops, mutation, modules,
lambdas, pattern matching, ownership, reflected invocation, or a compiler backend. Recursion,
immutable collections, tagged exported scopes, and the planned text operations are sufficient.

## Contracts, Type Derivation, and Collections

### Overview

Caret does not distinguish fundamentally between types, interfaces, refinement types, and value contracts.

A type is a **contract**.

A contract behaves as a predicate over values:

```text
Contract : Value -> Boolean
```

A value satisfies a type when it satisfies the corresponding contract.

Contracts may also derive from other contracts. This creates a hierarchy of logical implication:

```text
Int -> Number -> Comparable -> Eq
```

meaning:

```text
Int x => Number x
Number x => Comparable x
Comparable x => Eq x
```

Behavior is not stored inside contracts.

Operations are ordinary Caret functions whose parameter contracts determine which values they accept.

Collections use one common literal syntax:

```caret
[1 2 3]
```

The literal itself does not mean `List`, `Array`, `Set`, or another particular representation.

Its specific collection contract and representation are determined by inference and surrounding constraints.

---

# Contracts

## Contract definition

`contract` constructs a contract.

A base contract with no additional value restriction may be defined as:

```caret
Eq = contract ~
```

Here `~` means that the contract introduces no additional value predicate of its own.

Membership in such a base contract is established through derivation from that contract.

For example:

```caret
Eq = contract ~

Number = contract Eq

Int = contract Number
```

establishes:

```text
Int -> Number -> Eq
```

An `Int` therefore satisfies all three contracts.

---

## Contracts as predicates

Every contract may be used as a Boolean membership predicate:

```caret
Int value
Number value
Eq value
```

Conceptually:

```text
Int value -> Boolean
```

A contract may therefore be used anywhere an ordinary pure unary Boolean function is appropriate.

The compiler may determine membership statically when sufficient type information is available.

It does not need to execute a runtime predicate when derivation already proves membership.

---

## Contract inference and nominal ascription

An explicit contract clause confirms membership already carried by a value. Otherwise it attempts
to establish the named nominal membership by checking every inherited contract and refinement. A
successful check produces an attributed value with that membership; existing aliases remain
unchanged. A failed statically decidable check is a compile-time error, while an undecidable check
is retained for runtime and produces the same located contract-violation diagnostic on failure.
Contract membership participates in checking and dispatch but not structural equality or hashing.

Inference preserves relationships created by value flow. For example, `identity value = value` has
one shared contract variable for its parameter and result. Several simultaneous requirements form
an anonymous conjunction: requiring both `A` and `B` does not create or grant membership in a
nominal `AB`. Alternative branches retain only the contract guarantees common to every branch;
anonymous union contracts are not inferred.

Constraints are collected across the complete lexical block before ambiguity is diagnosed, so a
later use may resolve an earlier intermediate binding. Inference follows runtime operator semantics:
`not`, conditionals, `and`, and `or` accept the established Boolean/null/missing truth values, while
`+` may be numeric addition or string concatenation. When that relational choice cannot yet be
proved, inference retains an unresolved constraint instead of incorrectly assuming `Number`.

Unannotated named functions generalize unresolved contract variables after their complete recursive
definition group has been analyzed. Recursive uses inside that group are monomorphic; distinct
external uses instantiate the generalized variables independently. Polymorphic recursion requires
explicit contracts. An instantiation that remains ambiguous when a concrete contract is required
is a compile-time error at the use site, rather than at the generic declaration.

Contract declarations are predeclared throughout their lexical block, so their bases may use
forward references. Direct and indirect contract-derivation cycles are compile-time errors.
`contract` always takes exactly one ordinary argument: `~`, one contract or predicate, or one
collection of requirements.

Contract equality is descriptor identity, never structural, nominal-name, or requirement-list
equivalence. Every evaluation of contract construction creates a fresh identity, so separately
constructed contracts remain unequal even when they contain the same bases, refinements, or
parameterization arguments. Assigning, returning, or otherwise passing an existing contract value
preserves its identity. Contract values are comparable by this identity even though ordinary
callable values are not.

---

## Contract composition

`contract` may combine multiple contracts:

```caret
Number =
  contract [Eq Comparable Arithmetic]
```

This means that every `Number` also satisfies:

```text
Eq
Comparable
Arithmetic
```

Conceptually:

```text
Number x
    =>
Eq x
and Comparable x
and Arithmetic x
```

Multiple derivation is therefore ordinary contract composition.

No separate multiple-inheritance mechanism is required.

For example:

```caret
Integer =
  contract [Number Integral]

Float =
  contract [Number Fractional]
```

establishes:

```text
Integer -> Number
Integer -> Integral

Float -> Number
Float -> Fractional
```

and transitively all contracts derived by `Number`.

---

## Type derivation

Type derivation is contract inclusion.

For example:

```caret
Eq = contract ~

Comparable =
  contract Eq

Arithmetic =
  contract Eq

Number =
  contract [Comparable Arithmetic]

Int =
  contract [Number Integral]

Float =
  contract [Number Fractional]
```

This creates a graph rather than requiring a strict inheritance tree.

A contract may derive from any number of other contracts.

There is no object-layout diamond problem because derivation does not copy or embed base objects.

If several derivation paths lead to `Eq`, the resulting value simply satisfies `Eq`.

---

## Refinement predicates

Ordinary pure unary Boolean functions may participate in contracts.

Example:

```caret
positive x =
  x > 0

PositiveInt =
  contract [Int positive]
```

Conceptually:

```text
PositiveInt x
    =
Int x
and positive x
```

The same constraint may also be written directly on a binding:

```caret
(Int positive) count
```

Named derived contracts are useful when a combination is reused:

```caret
SmallPositiveInt =
  contract [Int positive small]
```

Thus Caret uses the same mechanism for:

* base types;
* derived types;
* refinement types;
* interfaces/capabilities;
* user-defined validation constraints.

---

## Contracts do not contain operations

A contract does not contain a method table or list of allowed operations.

For example:

```caret
Eq = contract ~
```

does not itself declare `eq`.

Equality is an ordinary function defined separately:

```caret
(Boolean) eq (Eq) a (Eq) b =
  false
```

Specific contracts may provide more specialized implementations:

```caret
(Boolean) eq (Int) a (Int) b =
  primitiveIntEq a b

(Boolean) eq (Float) a (Float) b =
  primitiveFloatEq a b

(Boolean) eq (String) a (String) b =
  primitiveStringEq a b
```

Likewise:

```caret
(Boolean) lt (Comparable) a (Comparable) b =
  ...

(Int) add (Int) a (Int) b =
  ...

(Float) add (Float) a (Float) b =
  ...
```

Operations belong to functions, not types.

---

## Functional polymorphism

Several definitions of the same function may specialize different parameter contracts.

For example:

```caret
(Boolean) eq (Eq) a (Eq) b =
  false

(Boolean) eq (Number) a (Number) b =
  numericEq a b

(Boolean) eq (Int) a (Int) b =
  primitiveIntEq a b
```

When calling:

```caret
eq 10 20
```

the most specific applicable implementation is selected.

Here that is:

```caret
eq (Int) a (Int) b
```

This provides functional polymorphism through contract-based dispatch.

There is no privileged receiver object.

Dispatch may therefore depend on several arguments:

```caret
add (Int) a (Float) b =
  ...

add (Float) a (Int) b =
  ...

add (Float) a (Float) b =
  ...
```

If exactly one most-specific implementation exists, it is used.

If several incomparable implementations are equally applicable, the call is ambiguous and must produce a compile-time diagnostic where determinable.

Function dispatch must not arbitrarily choose between ambiguous implementations.

All definitions with the same name in one lexical block form one closed overload set. Every variant
must have the same arity, and every pair must differ in the normalized requirements of at least one
parameter; result contracts alone cannot distinguish variants. A generic variant may be the
least-specific fallback. One variant is more specific when it is at least as restrictive on every
parameter and strictly more restrictive on at least one.

The compiler selects a uniquely most-specific variant when it can prove one. Otherwise the closed
set is dispatched at runtime using the arguments' actual memberships. No applicable variant and
several incomparable applicable variants are distinct located runtime errors. Runtime-loaded code
may supply values and its own overload sets, but it cannot add variants to an existing lexical set.

---

# Collections

## General collection contract

`Collection` is the fundamental contract for values containing zero or more elements.

More specific collection contracts derive from it.

Conceptually:

```text
Collection
    List
    Array
    Set
    Dictionary
    Queue
    Packed
    ...
```

These relationships need not form a traditional OO hierarchy.

Specific collection properties may be expressed through additional contracts such as:

```text
Ordered
Indexed
Unique
Keyed
FixedSize
Mutable
Contiguous
Packed
Sorted
Persistent
```

A collection type may derive from several such contracts.

For example, conceptually:

```text
Array T
    -> Collection T
    -> Ordered
    -> Indexed

Set T
    -> Collection T
    -> Unique

Packed T
    -> Collection T
    -> Contiguous
    -> Packed
```

---

## Parameterized collection contracts

Collection contracts may be parameterized.

Examples:

```caret
Collection Int
List String
Array Float
Set String
Dictionary String Int
Packed Byte
```

Conceptually:

```caret
Collection element
```

produces a contract requiring every collection element to satisfy `element`.

Thus:

```caret
(Collection Number) values
```

means:

> `values` is a collection whose elements all satisfy `Number`.

Parameterized collection types should use the normal Caret contract/function model rather than requiring a separate generic-type language.

---

# Collection literals

## Universal collection syntax

Caret has one collection literal syntax:

```caret
[1 2 3]
```

Square brackets mean:

> construct a collection containing these expressions.

They do **not** specifically mean list or array.

The collection's more specific contract may come from context:

```caret
(List Int) a =
  [1 2 3]

(Array Int) b =
  [1 2 3]

(Set Int) c =
  [1 2 3]

(Packed Int32) d =
  [1 2 3]
```

The same literal syntax is used in every case.

Different contracts may result in different behavior and physical representation.

---

## Empty collection

An empty collection is:

```caret
[]
```

Its element contract cannot be inferred from its contents.

It may therefore obtain its type from context:

```caret
(List Int) values = []

(Set String) names = []

(Packed Byte) buffer = []
```

Without sufficient context it may remain a generic empty collection until additional constraints determine its element type.

---

## Heterogeneous collections

Collections may contain values of different types:

```caret
[
  10
  "hello"
  true
  3.14
]
```

The inferred element contract is conceptually a union of the possible element contracts:

```text
Int | String | Boolean | Float
```

Caret must not require the programmer to explicitly use `Any` merely because a collection is heterogeneous.

Individual elements retain enough metadata to determine their actual contracts and representation where required.

---

## Homogeneous collections

A collection such as:

```caret
[1 2 3 4]
```

may infer a common element contract:

```text
Int
```

Conceptually, the collection can carry that information once:

```text
Collection
    element contract: Int

    values:
        1
        2
        3
        4
```

The implementation need not repeat identical type metadata for every element.

---

## Collection expressions

Elements inside `[]` are ordinary Caret expressions.

Example:

```caret
[
  10
  calculate x
  transform value
]
```

No separate collection-expression language is introduced.

Multi-line literals are allowed:

```caret
[
  first
  second
  calculate third
]
```

Parentheses remain the normal grouping mechanism where expression boundaries would otherwise be ambiguous.
On one line, adjacent simple atoms are separate elements. An unparenthesized top-level operator
extends an element through the closing bracket; use parentheses when another element follows it.

---

# Named fields and dictionaries

## Named elements

`^` may construct named elements inside a collection:

```caret
person =
  [
    ^name = "Alice"
    ^age = 42
    ^active = true
  ]
```

Conceptually these are first-class field values:

```text
Field("name", "Alice")
Field("age", 42)
Field("active", true)
```

The collection may therefore satisfy a record-like or dictionary-like contract.

Static member access may be used where the field is known:

```caret
person.name
person.age
```

---

## Dynamic keys

For keys that cannot be expressed as static identifiers, ordinary field construction may be used:

```caret
[
  field "first name" "Alice"
  field "age" 42
]
```

or:

```caret
[
  field key1 value1
  field key2 value2
]
```

A dictionary is therefore still fundamentally a collection.

Conceptually:

```text
Dictionary K V
    -> Collection (Field K V)
    -> Keyed
```

Caret does not require a separate `{ key: value }` literal syntax.

---

## Nested collections

Collections may contain collections:

```caret
people =
  [
    [
      ^name = "Alice"
      ^age = 32
    ]

    [
      ^name = "Bob"
      ^age = 41
    ]
  ]
```

or arbitrary heterogeneous nested structures:

```caret
[
  10
  "hello"

  [
    ^x = 20
    ^y = 30
  ]

  true
]
```

No separate object, record, JSON, or array literal syntax is required.

---

# Collection metadata

## Logical versus physical metadata

Caret distinguishes between:

1. **semantic/type metadata** — what contracts a value satisfies;
2. **representation metadata** — how a value is physically laid out.

These are related but not identical.

For example:

```caret
(Collection Number) values
```

guarantees that every element satisfies `Number`.

It does not necessarily imply that every element has the same representation.

The collection may contain:

```caret
[1 2.5 100 3.14]
```

where some values are represented as integers and others as floating-point values.

---

## Per-element metadata

A heterogeneous collection may require metadata for each element.

Conceptually:

```text
[
    { type: Int,     value: 10 }
    { type: String,  value: "hello" }
    { type: Boolean, value: true }
]
```

This is a semantic model only.

The runtime is not required to physically store a complete descriptor beside every value.

It may use:

* compact tags;
* descriptor tables;
* separate storage areas;
* compiler-known static information;
* other equivalent representations.

The observable semantics must only preserve sufficient information to recover each element's relevant type and representation.

---

## Shared collection metadata

When every element shares the same relevant metadata, that metadata may belong to the collection instead of every element.

For example:

```caret
(Collection Int) values =
  [1 2 3 4]
```

may conceptually be represented as:

```text
element contract: Int

values:
    1
    2
    3
    4
```

rather than:

```text
Int 1
Int 2
Int 3
Int 4
```

This sharing is semantically invisible and may be performed automatically.

---

## Contract-homogeneous but representation-heterogeneous collections

A common contract does not necessarily provide enough information to remove all per-element metadata.

For example:

```caret
(Collection Number) values =
  [1 2.5 3 4.5]
```

has a common semantic contract:

```text
Number
```

but its elements may have more specific contracts:

```text
Int
Float
Int
Float
```

and may therefore require different representations.

The collection may store `Number` as shared metadata while retaining enough per-element information to distinguish the concrete numeric forms.

---

# Packed collections

## Shared representation

A packed collection has a uniform statically known element representation.

Example:

```caret
(Packed Byte) bytes =
  [12 48 91 255]
```

The representation may contain only the element data:

```text
0C 30 5B FF
```

with the common element representation stored once as collection metadata.

Individual elements require no separate type or layout descriptor.

---

## Packed versus homogeneous

A homogeneous semantic contract is weaker than a packed representation.

For example:

```caret
(Collection Number) values
```

does not imply packed storage.

Even:

```caret
(Collection Int) values
```

need not necessarily promise a particular physical width or layout if `Int` has implementation-dependent representation.

By contrast:

```caret
(Packed Int32) values
```

requires a concrete uniform representation.

Conceptually:

```text
Packed T => Collection T
```

but:

```text
Collection T !=> Packed T
```

---

## Packed structural values

Packed elements need not be primitive scalars.

A structural type may have a shared layout.

For example, conceptually:

```text
Vertex
    position : 3 × Float32
    normal   : 3 × Float32
    uv       : 2 × Float32
```

Then:

```caret
(Packed Vertex) vertices
```

may carry one common descriptor:

```text
stride      32 bytes
position    offset 0
normal      offset 12
uv          offset 24
```

while the collection storage contains only packed vertex records.

This is suitable for:

* GPU buffers;
* SIMD processing;
* native interop;
* audio buffers;
* binary I/O;
* memory mapping;
* network buffers.

---

## Metadata placement rule

The semantic rule is:

> A collection may provide metadata that applies to every element. An element requires additional metadata only when the collection-level metadata is insufficient to determine that element's relevant type or representation.

Examples:

```caret
[1 2 3]
```

may use one shared `Int` descriptor.

```caret
[1 2.0 3]
```

may share `Number` while retaining information distinguishing `Int` from `Float`.

```caret
[1 "two" true]
```

requires heterogeneous element information.

```caret
(Packed Int32) [1 2 3]
```

has a complete common element layout and needs no per-element representation metadata.

---

# Relationship to formats

Contracts and physical representation are separate concepts.

A contract describes:

```text
which values are valid
```

A layout describes:

```text
how values are represented in memory
```

A `Format` describes:

```text
logical value <-> external representation
```

These concepts may cooperate without being collapsed into one abstraction.

For example, a packed collection may use a shared representation compatible with a `Format`, but being a `Packed` collection does not itself make the collection a `Format`.

This separation allows the compiler to optimize memory layout without changing logical contract semantics.

---

# Implementation requirements

The initial implementation should support at minimum:

1. `contract` as the fundamental type-definition mechanism.
2. Base/tag contracts:

```caret
Eq = contract ~
```

3. Contract derivation:

```caret
Number = contract [Eq Comparable]
```

4. Multiple derivation.
5. Contracts usable as membership predicates.
6. Ordinary pure predicates used as refinements.
7. Derived refinement contracts:

```caret
PositiveInt = contract [Int positive]
```

8. Separate function definitions for operations.
9. Contract-based function specialization and most-specific dispatch.
10. Ambiguity diagnostics for incomparable applicable function implementations.
11. A general `Collection` contract.
12. Parameterized collection contracts.
13. A universal square-bracket collection literal:

```caret
[1 2 3]
```

14. Empty collection literals:

```caret
[]
```

15. Homogeneous collections.
16. Heterogeneous collections.
17. Nested collections.
18. Named fields with `^`.
19. Dynamic fields through ordinary `field` construction.
20. Dictionary-like collections using field elements.
21. Shared collection-level element metadata.
22. Per-element metadata where required.
23. Distinction between common semantic contract and common physical representation.
24. Packed collections with uniform representation metadata.
25. Compiler/runtime freedom to optimize metadata representation without changing observable semantics.

The initial implementation may postpone:

* sophisticated automatic memory-layout optimization;
* GPU-specific layout attributes;
* structure-of-arrays transformations;
* compressed runtime type tags;
* zero-copy format views;
* advanced generic constraint inference.

These later features must preserve the fundamental distinction between contract membership, element-specific metadata, and shared collection representation.

---

# Design principle

Caret uses one conceptual system for type constraints:

```text
type
interface
refinement
capability
    -> Contract
```

Derivation means logical inclusion:

```text
Derived x => Base x
```

Behavior remains outside the type hierarchy and is expressed through ordinary polymorphic functions.

Collections likewise use one literal form:

```caret
[...]
```

The literal specifies its elements, not its container implementation.

Contracts determine whether the resulting value behaves as a:

```text
List
Array
Set
Dictionary
Packed buffer
heterogeneous collection
...
```

and the runtime stores metadata at the narrowest level necessary:

```text
shared by collection when possible
per element only when necessary
```

This allows the same collection abstraction to range from fully heterogeneous structured data to tightly packed GPU-compatible buffers without introducing separate literal syntaxes or unrelated collection models.

### Purity and effects

Every function has an inferred effect set.

The compiler determines this set from:

* operations performed directly by the function;
* functions called by it;
* functions passed into and invoked by it;
* composed and partially applied functions used by it.

A function with an empty effect set is pure.

Effects propagate transitively through function calls.

For example, if:

```caret
(fs) read path =
  ...

parse text =
  ...
```

then:

```caret
load path =
  parse (read path)
```

has inferred effect set:

```text
{ fs }
```

### Effects must be declared

Effects may not appear implicitly in a function declaration.

If a function has no explicit effect contract, its allowed effect set is empty.

Therefore:

```caret
calculate x =
  ...
```

implicitly guarantees that `calculate` is pure.

If an effect is later introduced into its implementation, compilation fails until the declaration is explicitly changed.

For example:

```caret
load path =
  read path
```

is invalid when `read` has the `fs` effect.

The compiler should report the inferred undeclared effect and may suggest:

```caret
(fs) load path =
  read path
```

This rule prevents effects from propagating silently through the call graph.

### Effect contracts

Effect contracts specify the maximum set of effects a function is permitted to have.

```caret
(fs) read path =
  ...

(net) download url =
  ...

(fs net) synchronize source target =
  ...
```

If the compiler infers an effect not included in the declared set, compilation fails.

For example:

```caret
(fs) load path =
  download path
```

is invalid if `download` requires `net`.

Effect contracts are upper bounds, not assertions that an effect necessarily occurs during every invocation.

A function declared:

```caret
(fs) cachedLoad path =
  ...
```

may execute without filesystem access on some or all paths.

### Explicit purity

`pure` is equivalent to an empty allowed effect set.

```caret
(pure) calculate x =
  ...
```

is therefore stricter documentation of the same purity guarantee that an effect-less declaration receives by default.

Explicit `pure` is useful when purity is an intentional API-level guarantee.

It is particularly useful on higher-order function parameters:

```caret
map (pure) transform values =
  ...
```

This requires `transform` to be a pure function.

A unary Boolean function that is itself effectful cannot be used as a contract.

### Effect inference and tooling

Effect inference is mandatory even when an explicit effect contract is present.

The compiler must infer the actual effect set and verify:

```text
actual effects ⊆ declared allowed effects
```

IDE tooling should expose inferred effects directly at function declarations.

For example, the source:

```caret
load path =
  ...
```

may temporarily display an inferred annotation such as:

```text
⟨fs — undeclared⟩ load path =
```

while editing.

For a pure function, tooling may display:

```text
⟨pure⟩ calculate x =
```

Such annotations are IDE presentation only and are not part of Caret source syntax.

The compiler should also provide a way to inspect fully inferred contracts and effects in plain-text environments.

---

## SIMD

SIMD is a language-level execution mechanism rather than a separate intrinsic API.

Ordinary pure numeric functions should be usable on both scalar values and SIMD values whenever their operations can be lifted lane-wise.

### SIMD types

A native-width SIMD value is written:

```caret
(Simd native Float32) values
```

`Simd` has fixed arity: its first argument is a width selector and its second is the scalar
contract. `native` is a built-in compile-time width selector, not an omitted argument or partial
application. The number of lanes is chosen appropriately for the compilation target.

An explicit lane count may be written:

```caret
(Simd 8 Float32) values
```

This represents exactly eight `Float` lanes.

`Simd` is a capitalized contract constructor and participates in normal contract syntax.

### Floating-point reduction grouping

SIMD floating-point reductions read the active execution-environment grouping option when the
reduction begins. The default is `pairwise`:

```caret
simdOption grouping pairwise
simdOption grouping hardware
```

`pairwise` uses the language-defined pairwise grouping. `hardware` permits target-dependent
grouping and therefore target-dependent floating-point results. The option is inherited by a child
environment at construction, remains current within that environment, and may subsequently be
changed there without changing its parent. Strict left-to-right reduction remains available through
an explicit scalar `fold`; it is not the SIMD default.

### Lane-wise operations

Ordinary arithmetic and comparison operators operate lane-wise on SIMD values.

For example:

```caret
a + b
a * b
a > b
```

when applied to SIMD values produce corresponding SIMD results.

A scalar operand is automatically broadcast where the operation is otherwise well-defined:

```caret
values * 0.5
values + offset
values > 10
```

No explicit broadcast operation is required for ordinary scalar-to-SIMD use.

### SIMD Boolean values and conditional selection

A comparison involving SIMD values produces a SIMD Boolean mask.

```caret
positive = values > 0
```

If `values` is:

```caret
Simd native Float32
```

then `positive` is conceptually:

```caret
Simd native Boolean
```

Caret's ordinary conditional expression operates lane-wise when its condition is a SIMD Boolean value:

```caret
positive & values ! 0
```

This selects between `values` and `0` independently for each lane.

For example:

```text
mask   = [true, false, true, false]
values = [8, -2, 4, -7]

mask & values ! 0
```

produces:

```text
[8, 0, 4, 0]
```

No separate blend/select intrinsic is required.

### Scalar functions lifted to SIMD

A pure scalar function may be applied to SIMD values when all operations in the relevant execution path support SIMD semantics.

For example:

```caret
(pure) square x =
  x * x
```

may be used with either:

```caret
square 3.0
```

or:

```caret
square vector
```

when `vector` is a compatible SIMD value.

The compiler may generate scalar or SIMD code according to the argument type.

### Explicit SIMD application

Caret provides:

```caret
collection :: function
```

to request SIMD application of `function` across the elements of `collection`.

Example:

```caret
(pure) adjust x exposure =
  bright = x * exposure
  bright > 1 & 1 ! bright < 0 & 0 ! bright

result = pixels :: adjust _ exposure
```

`adjust _ exposure` creates the unary function applied to SIMD groups of `pixels`.

Conceptually, `::` performs:

1. SIMD-sized grouped processing across as much of the collection as possible;
2. scalar or masked handling of any remaining tail elements.

The programmer does not manually write a remainder loop.

### `::` is a requirement, not merely a hint

Ordinary collection operations may be auto-vectorized by the compiler whenever safe.

For example:

```caret
values map transform
```

may use SIMD without any special source syntax.

By contrast:

```caret
values :: transform
```

explicitly requests SIMD execution.

If the compiler cannot generate valid SIMD code for this operation, it must issue a diagnostic rather than silently falling back to fully scalar execution.

The diagnostic should explain the reason when possible, such as:

* the function has effects;
* an operation has no SIMD implementation;
* an unsupported data layout is required;
* aliasing prevents safe vectorization;
* control flow cannot be represented safely with SIMD semantics.

### Purity requirement for SIMD mapping

Functions used with `::` must normally be pure.

```caret
values :: transform
```

requires that `transform` have an empty inferred effect set.

An effectful function such as:

```caret
(io) transform x =
  print x
  x * 2
```

cannot normally be used through:

```caret
values :: transform
```

because lane-wise execution would make observable effect ordering ambiguous.

Purity is checked using the ordinary Caret effect system; SIMD does not introduce a separate purity mechanism.

### Function composition and partial application

SIMD application composes with ordinary Caret function features.

Partial application:

```caret
pixels :: adjust _ exposure
```

Function composition:

```caret
pipeline = normalize >> clamp >> encode
result = values :: pipeline
```

The resulting composed function is SIMD-compatible only if the entire composition is pure and every relevant operation supports SIMD execution.

### Reductions

Operations that collapse SIMD lanes are ordinary functions rather than special syntax.

Examples include:

```caret
sum values
min values
max values
any mask
all mask
```

The compiler may lower these to efficient SIMD horizontal reductions.

### Memory and alignment

Normal SIMD code should not require explicit aligned-load, unaligned-load, store, or hardware-register operations.

The compiler/runtime is responsible for handling:

* native SIMD width;
* memory alignment;
* vector loading and storing;
* remainder elements;
* target instruction sets such as AVX, AVX2, AVX-512, NEON, or equivalent facilities.

Low-level architecture-specific SIMD facilities may exist separately, but they are not part of the
ordinary `Simd` / `::` programming model.

### Portability

Code using:

```caret
Simd native Float32
```

is portable across targets with different native SIMD widths.

Code using an explicit width:

```caret
Simd 8 Float32
```

requests that logical lane width specifically. The compiler may use one or more hardware vector operations to implement it where necessary, or reject it when the target cannot support the required semantics.

## Formats

### Overview

A `Format` describes a bidirectional relation between an in-memory Caret value and an external representation.

Conceptually:

```text
Value ↔ Representation
```

Examples of representations include:

* byte streams;
* files;
* network packets;
* textual formats;
* JSON-like data;
* compressed data;
* encrypted data;
* protocol messages.

A format definition should normally describe both directions at once:

```text
decode : Representation -> Value
encode : Value -> Representation
```

The programmer should not normally write independent encoder and decoder implementations for the same structure.

The compiler/runtime derives both directions from the same `Format` value wherever possible.

---

## Formats as relations

A format is relational rather than inherently directional.

For example:

```text
u16be
```

describes the relation between an integer and its two-byte big-endian representation:

```text
Int ↔ Bytes
```

When applied in the decoding direction:

```text
00 2A -> 42
```

When applied in the encoding direction:

```text
42 -> 00 2A
```

A compound format describes a larger relation assembled from smaller relations.

Caret does not require general Prolog-style search or backtracking for format relations.

A format is expected to support deterministic evaluation when one side of the relation is sufficiently known.

The normal supported directions are:

```text
known Representation -> Value
known Value          -> Representation
```

The format system must not implicitly search arbitrary solution spaces when neither side is sufficiently determined.

---

## `Format` as a first-class value

`Format` is a first-class Caret value.

Formats may be:

* stored in variables;
* passed to functions;
* returned from functions;
* composed;
* partially applied;
* placed in collections;
* inspected through reflection.

Format construction should use ordinary Caret functions rather than special grammar for each format feature.

For example:

```caret
Packet =
  format
  >> constant "PACK"
  >> field u16be "length"
  >> field u8 "type"
  >> field (bytes length) "payload"
```

`format` is the empty format.

Functions such as:

```text
constant
field
array
when
choice
require
codec
```

construct or transform formats.

They should normally be library-level functions or standard format primitives rather than separate parser constructs.

---

## Formats as specialized collections

`Format` satisfies the general Caret `Collection` model.

Conceptually:

```text
Format : Collection FormatElement
```

A format may contain heterogeneous elements such as:

```text
Constant
Field
Sequence
Repeat
Choice
Conditional
Constraint
Codec
```

These elements may have different concrete types but satisfy the common format-element contract.

A format should normally be immutable.

Functions that extend a format return an updated format rather than mutating the original value.

Conceptually:

```text
Format -> Format
```

For example:

```caret
addHeader f =
  f
  >> constant "HEAD"
  >> field u16be "version"
```

Because formats are ordinary immutable values, they can be reused safely:

```caret
Base =
  format
  >> constant "DOC"

Version1 =
  Base
  >> field u8 "flags"

Version2 =
  Base
  >> field u16be "flags"
```

---

## Format composition

Caret's normal `>>` composition operator is also used for format construction and relational composition.

When a function is partially applied so that it accepts a `Format` and returns a `Format`, it can participate directly in a format pipeline.

For example:

```caret
Packet =
  format
  >> constant "PACK"
  >> field u16be "length"
  >> field u8 "type"
```

Conceptually:

```text
format
  -> add constant
  -> add length field
  -> add type field
```

When complete bidirectional relations are composed:

```text
A ↔ B
B ↔ C
```

their composition describes:

```text
A ↔ C
```

The encoding direction follows the relation in one direction and the decoding direction follows it in the opposite direction.

This allows format composition to define both encoder and decoder behavior from one expression.

---

## Primitive formats

Primitive representation formats are themselves `Format` values.

Binary primitive formats consume and produce a first-class immutable `Bytes` value. `Bytes` is
distinct from Unicode text and from a general sequence of numbers: byte indexing counts octets,
while text indexing continues to count Unicode code points. Standard pure conversions provide
explicit interoperability with hexadecimal text, encoded text, and validated integer sequences;
raw bytes are never smuggled through `String`.

Examples may include:

```caret
u8
u16be
u16le
u32be
u32le
i16le
f32le
bytes
utf8
ascii
```

For example:

```caret
field u32be "size"
```

uses `u32be` as a format describing:

```text
Int ↔ four big-endian bytes
```

A compound format may be used anywhere a primitive format can be used.

For example:

```caret
Point =
  format
  >> field f32le "x"
  >> field f32le "y"

Object =
  format
  >> field Point "position"
```

`field` must not distinguish unnecessarily between primitive and compound formats.

---

## Fields

A field relates a named member of an in-memory data structure to a representation described by another format.

Conceptually:

```text
field : Format -> String -> Format -> Format
```

Exact internal argument ordering may follow normal Caret partial-application rules, but this syntax should be supported:

```caret
field u16be "length"
```

When decoding, the format:

1. decodes a value using `u16be`;
2. adds a named field `"length"` to the resulting Caret data value.

When encoding, it:

1. obtains the field `length` from the input data;
2. encodes it using `u16be`.

For example:

```caret
Point =
  format
  >> field f32le "x"
  >> field f32le "y"
```

decodes into a value structurally equivalent to:

```caret
data
  ^x = 10.0
  ^y = 20.0
```

and encodes such a value back into the corresponding representation.

Field names are ordinary strings.

No separate name-literal syntax is required.

---

## References to earlier fields

Later format elements may depend on values decoded or encoded earlier in the same structure.

For example:

```caret
Packet =
  format
  >> field u16be "length"
  >> field (bytes length) "payload"
```

Within the format definition, `length` refers to the logical value of the previously defined field.

In the decoding direction:

1. decode `length`;
2. use it to determine how many bytes constitute `payload`.

In the encoding direction, the same relationship must be respected.

If a field such as `length` can be derived from another value during encoding, the format system should permit the implementation to derive or validate it rather than require duplicated application code.

The precise dependency-resolution rules may be expanded later, but dependencies must be represented as relationships rather than duplicated encode/decode implementations wherever possible.

---

## Constant representation elements

A constant format element represents data that appears in the external representation but normally does not need to appear as a logical in-memory field.

Example:

```caret
PngLike =
  format
  >> constant signature
  >> field u32be "length"
```

In the decoding direction:

```text
constant x
```

consumes representation data and verifies that it equals `x`.

If it does not match, decoding fails.

In the encoding direction, the same element emits `x` automatically.

This is a naturally bidirectional relation:

```text
representation element == x
```

A constant should not create an in-memory field unless explicitly requested.

This is useful for:

* file signatures;
* magic values;
* protocol markers;
* separators;
* fixed headers;
* reserved constants.

---

## Repeated formats

Repeated structures are created by format combinators rather than special looping syntax.

For example:

```caret
array count Item
```

constructs a format representing `count` repetitions of `Item`.

Example:

```caret
Point =
  format
  >> field f32le "x"
  >> field f32le "y"

Polygon =
  format
  >> field u16be "count"
  >> field (array count Point) "points"
```

Decoding produces a collection of decoded `Point` values.

Encoding consumes a collection of `Point` values.

The same format definition controls both directions.

The count may be:

* constant;
* derived from a previous field;
* derived from the value being encoded;
* determined by another format relation.

The implementation should avoid requiring the user to write separate loops for encoding and decoding.

---

## Conditional formats

A format may conditionally include another format.

A combinator conceptually similar to:

```caret
when predicate format
```

constructs a conditional format.

Example:

```caret
Extension =
  format
  >> field u32be "extra"

Packet =
  format
  >> field u8 "flags"
  >> field
       (when (flags has Extended) Extension)
       "extension"
```

The condition should be usable in both directions whenever enough information is available.

In the decoding direction, previously decoded data may determine whether the subformat is present.

In the encoding direction, the logical data may determine whether the corresponding representation is emitted.

The compiler/runtime should derive both directions from the same condition wherever possible.

---

## General choices and format selection

Caret has a general choice expression. It is also used by formats to describe alternatives based on
data patterns or discriminators:

```caret
kind ==
  1 & TextMessage
  2 & ImageMessage
  3 & FileMessage
  ! UnknownMessage
```

The selector is evaluated once. Case labels are evaluated and compared from top to bottom using
ordinary equality; only the selected result expression is evaluated. The optional `!` fallback is
unique and must be last. A choice with no matching case and no fallback evaluates to `~`.
Statically recognizable duplicate labels are diagnostics.

The semantic requirement is more important:

* decoding may use representation data to determine which alternative applies;
* encoding may use the logical value to determine which representation and discriminator are required.

Where the relationship is deterministic in both directions, the user should not have to write separate selection logic for encoding and decoding.

For a format, deterministic literal cases may derive the encoded discriminator. A fallback may not
invent a discriminator: it must use one already known from the logical value or produce the
structured format mismatch defined by the eventual format-result model.

Pattern matching in formats should therefore be treated relationally where practical.

---

## Format constraints

Ordinary Caret contracts may constrain values represented by a format.

Conceptually:

```caret
require contract format
```

returns a constrained format.

Example:

```caret
PositiveInt =
  require positive u32be
```

When decoding:

1. decode an integer;
2. require that `positive` holds.

When encoding:

1. require that the supplied value satisfies `positive`;
2. encode it.

The same pure contract is used in both directions.

This connects format validation directly to Caret's normal contract system.

---

## Automatic bidirectionality

Format components should define both directions automatically whenever their relation contains enough information to do so.

Examples include:

```caret
constant "PNG"
field u16be "length"
array count Entry
require positive u32be
```

The programmer should not write:

```text
encodeConstant
decodeConstant

encodeField
decodeField

encodeArray
decodeArray
```

as separate application-level definitions.

The common format description should generate both behaviors.

---

## Explicit codecs

Not every transformation can be inverted automatically.

For example:

```text
compressed bytes ↔ uncompressed bytes
encrypted bytes  ↔ plaintext
base64 text       ↔ bytes
```

The compiler cannot generally derive a compressor from a decompressor or an encryptor from a decryptor.

Caret therefore supports a format component that explicitly supplies the two directions.

Conceptually:

```caret
codec decode encode format
```

The first function implements representation-to-value transformation.

The second implements value-to-representation transformation.

For example:

```caret
gzip format =
  codec gunzip gzip format
```

or:

```caret
encrypted key format =
  codec (decrypt key) (encrypt key) format
```

These functions construct new formats.

They are not special external encoding/decoding procedures attached after format construction.

They are components of the format relation itself.

---

## Codec composition

Explicit codecs compose with ordinary declarative formats.

For example:

```caret
Payload =
  format
  >> field u32be "id"
  >> field utf8 "text"

CompressedPayload =
  gzip Payload
```

Conceptually, the relationship is:

```text
Caret Payload
      ↕ Payload format
uncompressed representation
      ↕ gzip codec
compressed representation
```

Encoding follows:

```text
Caret value
 -> Payload representation
 -> compression
 -> compressed representation
```

Decoding follows:

```text
compressed representation
 -> decompression
 -> Payload representation
 -> Caret value
```

The complete encoder and decoder are derived from the composed relation.

---

## Representation transformations versus logical transformations

A codec may alter either the physical representation or the logical value.

Representation example:

```text
plain bytes ↔ compressed bytes
```

Logical-value example:

```text
stored integer ↔ floating-point temperature
```

For example:

```caret
Temperature =
  codec
    (x -> x / 100.0)
    (x -> round (x * 100))
    i16le
```

The external representation is a signed integer.

The logical Caret value is a floating-point temperature.

Both kinds of transformations use the same relational format machinery.

Libraries may provide more descriptive wrapper functions for common purposes, but they need not require separate compiler concepts.

---

## Purity of format definitions

A `Format` describes data relationships and should normally be pure.

Format construction functions should therefore normally be pure.

Encoder and decoder functions supplied to `codec` must normally be pure.

For example:

```caret
gzip format =
  codec gunzip gzip format
```

requires `gunzip` and `gzip` to satisfy the purity requirement.

Reading a file, receiving network data, or writing to a socket is not part of the format relation itself.

For example:

```caret
(fs) raw = read file
value = decode Packet raw
```

and:

```caret
raw = encode Packet value
(fs) write file raw
```

`decode` and `encode` remain pure even though acquiring or storing the representation is effectful.

This separation must be preserved.

---

## Decode and encode operations

The standard library should expose explicit directional operations:

```caret
decode Format representation
encode Format value
```

These are ordinary functions.

For a bidirectional format:

```caret
decoded = decode Packet bytes
encoded = encode Packet packet
```

Both operations use the same `Packet` value.

Do not require separately declared `PacketDecoder` and `PacketEncoder` objects.

A future relational application syntax may permit direction to be inferred from which side is known, but explicit `decode` and `encode` functions must remain available and unambiguous.

---

## Failure

Decoding may fail because:

* a signature or constant does not match;
* input ends prematurely;
* a field representation is invalid;
* a contract fails;
* no conditional/pattern alternative matches;
* a codec rejects the representation.

Encoding may also fail because:

* a required field is missing;
* a field has an invalid value;
* a contract fails;
* the value cannot be represented by the selected primitive format;
* no encoding alternative matches;
* a codec rejects the logical value.

These failures should be represented explicitly rather than relying on exceptions for expected format mismatch.

Each failure payload satisfies the standard `ErrorTemplate`; a format-specific exact template
describes its `details` field. Both operations return `Result ValueContract`, as defined in the
structured-error section. A successful decode or encode places its logical value or representation
in `value`; an expected mismatch or other format failure places the structured error in `error`.

Errors should be capable of carrying useful information such as:

* format component;
* field name;
* representation position;
* expected condition;
* actual value;
* nested error cause.

---

## Canonical representations and round trips

A bidirectional format does not necessarily imply that every raw representation round-trips byte-for-byte.

For example:

```text
"00123" -> 123 -> "123"
```

may be valid if the encoder emits a canonical representation.

The preferred semantic guarantee is normally:

```text
decode (encode value) == value
```

for every valid logical value.

The opposite:

```text
encode (decode representation) == representation
```

is required only for formats that explicitly promise representation-preserving round trips.

Formats may therefore normalize representations.

---

## Relationship to Caret `data`

Formats decode into ordinary Caret values.

Structured formats should normally produce `data` collections containing ordinary fields.

For example:

```caret
Packet =
  format
  >> field u16be "length"
  >> field u8 "type"
  >> field (bytes length) "payload"
```

may decode to:

```caret
data
  ^length = 128
  ^type = 2
  ^payload = payloadBytes
```

The format subsystem must not introduce a separate object model for decoded data.

The same value may therefore:

* be created directly using `data`;
* be decoded from a binary format;
* be encoded into another format;
* be passed through ordinary Caret functions;
* satisfy contracts;
* participate in collection operations;
* be inspected through reflection.

---

## Formats are independent of transport

A format describes representation, not where that representation comes from.

The same format may be used with:

```text
file
network connection
memory buffer
HTTP body
database blob
IPC message
```

Transport effects belong to transport functions.

For example:

```caret
(net) raw = receive connection
message = decode MessageFormat raw
```

The format itself remains pure.

This allows the same `Format` to be reused across files, REST clients, servers, protocols, tests, and in-memory transformations.

---

## Extensibility

Most format functionality should be implementable as ordinary Caret functions.

A library should be able to introduce new combinators such as:

```caret
checksum
padding
aligned
gzip
encrypted
terminated
versioned
optional
bounded
```

without adding new grammar to the language.

For example:

```caret
gzip format =
  codec gunzip gzip format
```

A user-defined format constructor should have the same compositional status as a standard-library format constructor.

Do not hard-code individual file formats, protocol fields, compression algorithms, or serialization systems into the Caret parser.

---

## Reflection

Formats are first-class values and should be reflectable.

Reflection may expose information such as:

```text
format elements
field names
nested formats
primitive representations
contracts
constants
choices
codecs
decode capability
encode capability
```

Reflection must not violate private bindings or other normal Caret visibility rules.

Format reflection should make it possible to build tooling such as:

* format inspectors;
* binary viewers;
* protocol debuggers;
* generated documentation;
* editors;
* test-data generators;
* schema converters.

---

## Implementation requirements

The initial implementation should support at minimum:

1. A first-class immutable `Format` value.
2. An empty `format`.
3. Format composition using ordinary functions and `>>`.
4. Primitive formats for common integer and byte representations.
5. Named fields using ordinary string names:

```caret
field u16be "length"
```

6. Constant/signature elements.
7. Nested compound formats.
8. Repeated formats with a fixed or previously decoded count.
9. Contract validation through a format combinator.
10. Explicit:

```caret
decode Format representation
encode Format value
```

11. Decoding structured formats into ordinary Caret `data` values.
12. Encoding ordinary compatible `data` values.
13. Pure explicit bidirectional codecs:

```caret
codec decode encode format
```

14. Composition of codecs with structural formats.
15. Explicit format mismatch/failure values rather than expected-case exceptions.

The initial implementation may postpone:

* general relational solving;
* automatic inversion of arbitrary Caret functions;
* nondeterministic relations;
* backtracking;
* sophisticated pattern-derived discriminators;
* streaming incremental decoding;
* zero-copy decoding;
* asynchronous transport integration.

These later capabilities should not require changing the fundamental model that a `Format` is a first-class bidirectional relation assembled compositionally from smaller relations.

---

## Design principle

The central principle is:

> A Caret format describes the relationship between a logical value and its representation, not separate procedures for reading and writing it.

Where the relationship is structurally reversible, Caret derives both directions from one description.

Where reversal requires algorithms that cannot be inferred, the format explicitly contains both directional functions:

```caret
codec decode encode
```

Complex formats are built from smaller bidirectional relations using ordinary Caret functions, collections, contracts, partial application, and composition.

## Lambda Functions

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

## Lambda bodies

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

## Parameter contracts

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

## Arity

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

## Application

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

## Partial application

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

## Lambdas versus holes

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

## Closures

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

## Capture timing

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

## Purity and effects

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

Explicit function-value contracts may also be applied using the normal Caret contract mechanism where needed.

Conceptually:

```caret
(pure) (x -> x * 2)
```

requires the resulting lambda value to satisfy `pure`.

Purity must always be verified from the lambda body; the contract is a requirement, not merely an annotation.

---

## Lambda return values

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

Return-type or return-value contracts should follow the general Caret function-result contract mechanism once that syntax is finalized.

Do not introduce a separate lambda-specific return-type syntax.

---

## Nullary lambdas

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

## Reification

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

## Higher-order functions

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

## Function composition

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

## Lambdas in collection operations

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

## Lambdas and SIMD

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

## Lambdas in data definitions

Because `data` blocks contain ordinary Caret expressions, lambda values may be stored directly in data structures.

Example:

```caret
operations =
  data
    ^double = (x -> x * 2)
    ^positive = (x -> x > 0)
```

The resulting fields contain ordinary function values.

Likewise, a lambda may calculate a field value through immediate application or higher-order functions.

No special data-lambda syntax is required.

---

## Parsing and precedence

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

## Implementation requirements

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

## Design principle

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

## Cycles

### Overview

A Caret cycle is a generalized iterative state transformation.

Rather than giving `for`, `while`, and similar loops unrelated semantics, Caret models iteration as repeated transformation of a state value.

Conceptually:

```text
initial state
    ↓
condition
    ↓
body
    ↓
prepare next state
    ↺
```

A cycle produces a final value.

It is therefore an expression, not merely a control-flow statement.

Conceptually:

```text
cycle : Init -> Condition -> Body -> Prepare -> Result
```

For a state type `S`:

```text
init      : () -> S
condition : S -> Bool
body      : S -> S
prepare   : S -> S

cycle     : S
```

The cycle repeatedly transforms `S` until `condition` becomes false.

---

## Fundamental semantics

Given:

```caret
result = cycle init condition body prepare
```

the semantics are equivalent to:

```text
state = init

while condition(state):
    state = body(state)
    state = prepare(state)

result = state
```

This description is only explanatory.

Caret should not require mutable state internally.

The semantic model is functional:

```text
S -> S -> S -> ...
```

Each stage receives a state and produces the next state.

The final state is the value of the entire `cycle` expression.

---

## Initialization

`init` creates the initial state.

It may be:

* an existing value;
* a function producing a value;
* a `data` structure;
* a returned scope;
* another expression whose result becomes the initial cycle state.

Example:

```caret
initial =
  data
    ^i = 0
    ^sum = 0

result =
  cycle initial condition body prepare
```

A nullary initializer may also be used where initialization itself must be deferred:

```caret
result =
  cycle @makeInitialState condition body prepare
```

The exact handling of nullary functions follows the normal Caret function-reference rules.

---

## State as a scope

A particularly important use of `cycle` is iteration over a structured scope.

Example state:

```caret
data
  ^i = 0
  ^sum = 0
```

The cycle may transform this scope at every step.

Conceptually:

```caret
condition s =
  s.i < 10

body s =
  s
  >> set "sum" (s.sum + s.i)

prepare s =
  s
  >> set "i" (s.i + 1)
```

Then:

```caret
result =
  cycle initial condition body prepare
```

produces a final state equivalent to:

```caret
data
  ^i = 10
  ^sum = 45
```

The exact collection/scope update functions may be provided by the standard library.

The important semantic rule is that each phase receives the complete current state and returns the complete next state.

### Previous and next state views

Cycle conditions, bodies, and preparation phases execute with a cycle-state view in addition to
their ordinary lexical parameters.

For every phase, unqualified state-field reads refer to the complete previous state. Name lookup
checks local bindings and parameters first, then previous-state public fields, then the captured
lexical parent. The reserved `next` binding denotes the state currently being constructed.

A body or preparation phase begins with `next` structurally equal to its previous state. An exported
assignment writes the new state:

```caret
^sum = sum + i
```

Unmentioned fields remain present in `next`. A later expression in the same phase can observe an
earlier write explicitly:

```caret
^sum = sum + i
^large = next.sum > 100
```

Reading `sum` in the second assignment still reads the previous state; `next.sum` reads the new
value. Non-exported assignments remain local temporaries and do not become state fields.

Each phase commits atomically. The body transforms `S0` into `S1`; preparation then receives `S1`
as its previous state and transforms it into `S2`. No caller can observe a partially constructed
state.

A phase that performs exported state writes commits `next` as its result. A phase that performs no
exported state writes may instead return an explicit complete state value, preserving the ordinary
`S -> S` functions shown above. Mixing exported state writes with a different explicit state return
is an error rather than an implicit merge.

The condition receives the same previous-state read view but is read-only: it cannot perform
exported state writes or access a changing `next` value. This preserves condition purity.

---

## Functional semantics

Cycles are semantically compatible with immutable data.

A cycle does not require mutation of the current state.

Conceptually:

```text
S0
 ↓ body
S1
 ↓ prepare
S2
 ↓ body
S3
 ...
```

The previous state may become unreachable after the next state is produced.

This permits Caret implementations to optimize immutable cycle transformations aggressively.

If the compiler can prove that a previous state is no longer observable, it may:

* reuse storage;
* update values in place internally;
* eliminate intermediate allocations;
* use mutable machine registers or stack slots;
* perform tail-call-like transformations.

Such optimizations must not change the observable immutable semantics.

---

## Equivalence to tail recursion

A cycle can be expressed as tail recursion.

Conceptually:

```caret
run s =
  condition s &
    run (prepare (body s))
  !
    s
```

Therefore:

```caret
cycle initial condition body prepare
```

is semantically equivalent to repeatedly applying:

```caret
body >> prepare
```

while `condition` holds.

This equivalence is important.

`cycle` is not a separate mutable execution model.

It is a convenient and optimizable representation of a common recursive state transformation.

---

## Body and prepare are separate

`body` and `prepare` deliberately have separate roles.

For a conventional `for`-style loop:

```text
initialize
check
body
increment
check
body
increment
...
```

the mapping is:

```text
init       -> initialization
condition  -> loop condition
body       -> loop body
prepare    -> increment/update before next iteration
```

For example:

```caret
initial =
  data
    ^i = 0
    ^sum = 0

condition s =
  s.i < 10

body s =
  update s "sum" (s.sum + s.i)

prepare s =
  update s "i" (s.i + 1)

result =
  cycle initial condition body prepare
```

Keeping `body` and `prepare` separate makes conventional iteration easy to express while retaining the general state-transform model.

---

## Omitted prepare phase

When no separate preparation step is needed, the identity function may be used.

Conceptually:

```caret
cycle initial condition body identity
```

The standard library should provide an identity function.

Caret may later provide shorthand syntax for omitting an identity `prepare` phase, but the fundamental semantics remain:

```text
prepare : S -> S
```

---

## Omitted body phase

Similarly, a cycle whose meaningful work occurs entirely in the preparation transformation may use `identity` as its body:

```caret
cycle initial condition identity prepare
```

No special loop form is required.

---

## Example: counting

Conceptually:

```caret
initial =
  data
    ^i = 0

condition s =
  s.i < 10

body s =
  s

prepare s =
  update s "i" (s.i + 1)

result =
  cycle initial condition body prepare
```

The final result contains:

```caret
result.i
```

with value:

```text
10
```

---

## Example: accumulation

```caret
initial =
  data
    ^i = 1
    ^total = 1

condition s =
  s.i <= 10

body s =
  update s "total" (s.total * s.i)

prepare s =
  update s "i" (s.i + 1)

result =
  cycle initial condition body prepare
```

The final cycle state contains both the accumulated result and the final index.

The caller may select the part it needs:

```caret
factorial10 = result.total
```

This is preferable to requiring a separate externally mutable accumulator.

---

## Lambdas with cycles

Cycle phases may be supplied directly as lambdas.

Example:

```caret
result =
  cycle
    initial
    (s -> s.i < 10)
    (s -> update s "sum" (s.sum + s.i))
    (s -> update s "i" (s.i + 1))
```

All lambda rules apply normally:

* contracts;
* lexical capture;
* purity inference;
* effects;
* partial application;
* ownership.

No separate "loop lambda" syntax is required.

---

## Partial application

Because `cycle` is an ordinary higher-order function, partial application may be used to define reusable cycle forms.

For example:

```caret
repeatWhile condition body =
  cycle _ condition body identity
```

or:

```caret
iterate prepare =
  cycle _ _ identity prepare
```

The exact reusable abstractions should preferably be library functions rather than additional loop syntax.

---

## Scope shape

The initial implementation should require a stable state shape across a cycle unless the type system can prove a broader compatible type.

For example, if the initial state is:

```caret
data
  ^i = 0
  ^sum = 0
```

then `body` and `prepare` should normally return values exposing compatible fields:

```text
i
sum
```

A transformation that sometimes returns:

```caret
data
  ^i = 1
```

and sometimes:

```caret
data
  ^i = 1
  ^sum = 10
  ^error = "..."
```

introduces variant state shapes.

Such cycles may eventually be represented using:

* structural unions;
* optional fields;
* pattern matching;
* row-polymorphic types.

The initial implementation may reject incompatible state-shape changes.

---

## Contracts

Cycle phase contracts follow ordinary Caret rules.

For a state contract `State`:

```caret
condition (State) s =
  ...

body (State) s =
  ...

prepare (State) s =
  ...
```

The compiler should infer that the cycle preserves `State` where possible.

Conceptually:

```text
condition : State -> Bool
body      : State -> State
prepare   : State -> State
```

A phase that violates the required state contract causes a compile-time error where statically detectable.

---

## Purity and effects

`cycle` itself does not imply mutation or effects.

A cycle is pure if:

* initialization is pure;
* `condition` is pure;
* `body` is pure;
* `prepare` is pure.

For example:

```caret
result =
  cycle
    initial
    (s -> s.i < 10)
    updateTotal
    increment
```

is pure if all supplied components are pure.

If a phase has effects, those effects propagate to the cycle expression according to the ordinary effect system.

Example:

```caret
(io) printState s =
  print s
  s
```

Using it as the body causes the cycle to acquire the `io` effect.

The enclosing function must therefore explicitly permit that effect.

Effects must not be hidden merely because they occur inside iteration.

---

## Condition purity

The cycle condition must normally be pure.

```text
condition : S -> Bool
```

The compiler must reject a condition whose evaluation introduces an undeclared observable effect.

This avoids behavior where merely checking whether another iteration should occur changes external program state.

If effectful conditions are ever supported, they must be explicitly represented and must participate in normal effect inference.

The initial implementation should require cycle conditions to be pure.

---

## State ownership

Cycle state follows normal Caret ownership and lifetime rules.

Conceptually, each iteration consumes the current state and produces the next state:

```text
S0 -> S1 -> S2
```

When a state is uniquely owned and the old version is not subsequently accessible, the compiler may safely reuse its physical storage.

This is particularly important for large:

* collections;
* buffers;
* scopes;
* SIMD data;
* format-processing state.

Functional cycle semantics must therefore not imply mandatory copying.

---

## Cycles over collections

Collection iteration can be implemented using `cycle`, although high-level collection operations should remain available.

For example, operations such as:

```caret
map
filter
fold
reduce
```

may internally lower to cycle-like transformations.

Application code should normally prefer these more descriptive operations when they directly express the intent.

Use `cycle` when the iteration requires explicit multi-value state or a more general state machine.

---

## Cycles and SIMD

A cycle may be optimized using SIMD when its transformations satisfy normal SIMD requirements.

The presence of `cycle` does not itself request SIMD execution.

Explicit SIMD syntax such as:

```caret
collection :: transform
```

remains the preferred way to require vectorized element-wise execution.

The compiler may nevertheless auto-vectorize suitable pure cycles where safe.

---

## Cycles and formats

Streaming or incremental format processing may eventually use cycles internally.

For example, a decoder may repeatedly transform a state containing:

```text
input position
decoded values
current format element
remaining input
```

until the format is complete.

The format system should not require application programmers to manually write such cycles for ordinary decoding.

`cycle` provides a general implementation mechanism rather than replacing declarative formats.

---

## Early termination

A future version of Caret may support explicit cycle-control values such as:

```text
Continue S
Break S
```

Conceptually:

```text
body : S -> Continue S | Break S
```

A `Break` result terminates the cycle and returns its contained state.

A `Continue` result proceeds normally.

This is preferable to implementing `break` through:

* exceptions;
* hidden mutation;
* non-local jumps.

The initial implementation may postpone `Break` and `Continue`.

Until then, early termination should be represented through the cycle condition or explicit state.

For example:

```caret
data
  ^done = false
  ^state = ...
```

with:

```caret
condition s =
  not s.done
```

---

## Nested cycles

A cycle is an expression and may therefore be used inside another cycle.

Example conceptually:

```caret
outerResult =
  cycle outerInitial outerCondition
    (outer ->
      innerResult =
        cycle innerInitial innerCondition innerBody innerPrepare

      combine outer innerResult)
    outerPrepare
```

No special nesting syntax is required.

Each cycle has its own state value.

---

## Relationship to conventional loops

Common imperative loop forms can be expressed through `cycle`.

A conventional:

```text
for initialization; condition; increment
    body
```

corresponds to:

```text
cycle initialization condition body increment
```

A conventional:

```text
while condition
    body
```

corresponds conceptually to:

```text
cycle initial condition body identity
```

A repeated state machine corresponds to:

```text
cycle initial notFinished transition identity
```

Therefore separate `for`, `while`, and `do` constructs are not required for the core language.

Libraries may provide convenience abstractions where useful.

---

## Implementation model

The compiler should initially lower:

```caret
cycle initial condition body prepare
```

to behavior equivalent to tail-recursive execution:

```text
state = initial

loop:
    if not condition(state):
        return state

    state = body(state)
    state = prepare(state)
    goto loop
```

This imperative pseudocode is an implementation strategy only.

The observable language semantics remain immutable state transformation.

The compiler is free to implement the cycle using:

* a machine-level loop;
* tail-call elimination;
* mutable local variables;
* registers;
* in-place storage reuse;
* specialized collection iteration.

No intermediate state copies are required unless observable semantics demand them.

---

## Implementation requirements

The initial implementation should support at minimum:

1. `cycle` as an expression that returns its final state.
2. An initial state value.
3. A pure unary Boolean condition.
4. A unary body transformation.
5. A unary preparation transformation.
6. Lambda expressions as phase arguments.
7. Named functions as phase arguments.
8. Structured `data` or scope values as cycle state.
9. Effect inference through all cycle phases.
10. Contract checking of state transformations.
11. Efficient lowering without mandatory immutable copying.
12. Stable state shape across iterations.

The initial implementation may postpone:

* `Break` / `Continue` values;
* changing structural state types during iteration;
* effectful conditions;
* automatic parallel cycles;
* explicit loop labels;
* generalized nondeterministic relational cycles.

---

## Design principle

A Caret cycle is not fundamentally a mutable loop.

It is repeated application of state transformations:

```text
S
 -> body
 -> prepare
 -> S
```

controlled by:

```text
S -> Bool
```

and producing the final state as its result.

This provides conventional iteration while remaining compatible with:

* immutable data;
* first-class scopes;
* contracts;
* effect inference;
* lambdas;
* partial application;
* ownership optimization;
* tail recursion;
* SIMD optimization.

The core model should remain small enough that more specialized iteration constructs can be implemented as ordinary Caret functions rather than additional language syntax.

## Rules, Rulesets, and Rule Cycles

### Overview

Caret provides a rule system for defining reactive systems such as:

* games;
* simulations;
* data and stream interpreters;
* protocol processors;
* workflow engines;
* state machines.

Rules execute inside a `ruleCycle`.

A rule does not independently poll or execute globally. The surrounding `ruleCycle` provides:

* object traversal;
* context changes;
* context fronts;
* rule evaluation;
* rule scheduling;
* effect propagation;
* chaining;
* lifecycle and termination.

The components of a rule are summarized by the mnemonic **CATEN**:

```text
C  Context
A  Active state
T  Trigger
E  Effect
N  Name (string-literal ID)
```

All CATEN components are optional.

---

# Rules

## Basic definition

A rule is a first-class Caret value.

Example:

```caret
capture = rule
  C game and playerTurn
  A on
  T captureRequested and validCapture
  E
    move selectedPiece target
    destroy targetPiece
  N "capture"
```

The components are:

```text
C  context in which the rule can apply
A  whether the rule is active
T  condition or event that triggers application
E  changes caused by the rule
N  optional string-literal ID
```

The canonical documentation order is CATEN.

---

## Context

A context has a persistent Boolean state:

```text
up
down
```

A rule may apply only while its `C` expression is up.

Contexts may be combined using ordinary Boolean expressions:

```caret
game and playerTurn
combat and not paused
dialog or cutscene
```

Example:

```caret
attack = rule
  C game and playerTurn
  T attackRequested
  E performAttack
```

If:

```caret
game and playerTurn
```

is down, `attack` cannot apply.

### Context fronts

Changing a context produces a transient front.

A transition:

```text
down -> up
```

produces:

```caret
rise context
```

A transition:

```text
up -> down
```

produces:

```caret
fall context
```

Examples:

```caret
rise combat
fall dialog
```

Boolean combinations may also have fronts:

```caret
rise (game and playerTurn)
fall (combat or dialog)
```

The distinction between a level and a front is fundamental:

```caret
combat
```

means `combat` is currently up.

```caret
rise combat
```

means `combat` has just changed from down to up.

```caret
fall combat
```

means `combat` has just changed from up to down.

A front is transient and exists only as part of the corresponding rule-cycle propagation.

---

## Changing contexts

Contexts may be changed by rule effects or other rule-cycle operations:

```caret
raise combat
lower combat
```

`raise` changes a context to up.

`lower` changes a context to down.

Raising an already-up context does not generate another rise front.

Lowering an already-down context does not generate another fall front.

---

## Active state

Every rule has an active state independent of its context.

The active state is:

```text
on
off
```

Example:

```caret
specialAttack = rule
  A off
  T specialRequested
  E performSpecialAttack
```

An inactive rule cannot apply.

Rules may be activated and deactivated at runtime:

```caret
activate @specialAttack
deactivate @specialAttack
```

Context and active state have different meanings:

```text
Context
    describes whether circumstances permit the rule.

Active state
    describes whether the rule itself is enabled.
```

---

## Trigger

`T` defines the condition or Boolean combination of conditions that causes a rule to become applicable.

Example:

```caret
death = rule
  T player.health <= 0
  E destroy player
```

Normal persistent conditions use transition semantics.

For:

```caret
player.health <= 0
```

the triggering event is normally:

```text
false -> true
```

A continuously true condition does not repeatedly trigger the rule.

A rule therefore becomes applicable when:

```text
C is up
AND
A is on
AND
T triggers
```

### Fronts in triggers

Context fronts may be used directly:

```caret
beginTurn = rule
  T rise playerTurn
  E prepareTurn

resume = rule
  T fall dialog
  E resumeGame
```

This is particularly important for chaining rules.

### Context and active state are gates

`C` and `A` permit application but do not normally generate a delayed trigger.

For example:

```caret
rule
  C combat
  T enemy.health <= 0
```

If:

```caret
enemy.health <= 0
```

becomes true while `combat` is down, subsequently raising `combat` does not retroactively apply the rule.

If entering combat should itself cause evaluation as an event, it should be expressed explicitly:

```caret
rule
  T rise combat and enemy.health <= 0
```

---

## Effect

`E` contains the changes caused by application of the rule.

Example:

```caret
capture = rule
  T validCapture
  E
    move selectedPiece target
    destroy capturedPiece
    addScore currentPlayer captureValue
```

`E` is an ordinary Caret block.

It may call ordinary functions.

Like an ordinary `cycle` transformation, an `E` block executes against persistent previous and
next rule-cycle state. Unqualified state reads observe the previous state, `^field = value` writes
the next state, and `next.field` observes writes already made by the current effect. Unmentioned
fields carry forward. The complete next state becomes visible atomically after the selected rule's
effect finishes and before applicability is reevaluated.

Typical operations may include:

```caret
raise context
lower context

activate @rule
deactivate @rule

create object
destroy object

send message
```

The rule system does not require a closed hard-coded set of effect operations.

### Effect inference

Calls made from `E` participate in Caret's ordinary effect system.

An effect involving networking, file access, GUI state, or other externally observable behavior introduces the corresponding inferred effects.

`C` and `T` should normally remain pure because the rule engine may reevaluate them freely.

---

## Name

`N` optionally identifies a rule with a string literal. It does not accept a bare identifier or an
arbitrary runtime string expression.

Example:

```caret
rule
  N "capture"
  T validCapture
  E capturePiece
```

When a rule is assigned directly:

```caret
capture = rule
  T validCapture
  E capturePiece
```

the implementation should normally infer:

```text
N "capture"
```

unless another explicit string-literal ID is supplied.

Binding name and rule identity are conceptually distinct:

```caret
r = rule
  N "capture"
  ...
```

---

## Optional CATEN components

All CATEN components are optional.

Recommended defaults are:

```text
C omitted  -> always up
A omitted  -> initially on
T omitted  -> no autonomous trigger
E omitted  -> no explicit effect
N omitted  -> anonymous/internal identity
```

A rule without `E` still produces its implicit rule context when applied.

A rule without `T` may still participate in mechanisms such as explicit invocation or chaining.

---

## Implicit rule context

Every rule owns an implicit context.

When a rule applies:

```text
raise rule.context
execute E
lower rule.context
```

Therefore every application produces:

```caret
rise @rule.context
fall @rule.context
```

Other rules may respond to those fronts.

Example:

```caret
capture = rule
  T captureRequested
  E capturePiece

score = rule
  T fall @capture.context
  E addScore currentPlayer captureValue
```

The implicit context exists even when the rule has no explicit `E`.

---

# Rule ordering

## Unordered rules

Rule definition order does **not** imply execution order.

If several rules are simultaneously applicable and no ordering relationship between them has been specified, the `ruleCycle` may choose any of them.

For example:

```caret
a = rule
  T event
  E effectA

b = rule
  T event
  E effectB
```

If both become applicable, either sequence is valid:

```text
a
b
```

or:

```text
b
a
```

Caret deliberately provides no guarantee that the chosen order remains the same across:

* executions;
* compiler versions;
* platforms;
* optimization levels;
* runtime implementations.

Source order must never be relied upon as implicit rule priority.

---

## Effects affect subsequent scheduling

Applicable rules are not normally executed as an immutable simultaneous batch.

The scheduler conceptually operates as follows:

```text
determine applicable rules

choose one permitted rule

apply it

propagate its effects

reevaluate affected rules

choose another applicable rule

...
```

Therefore the first selected rule may alter whether another previously applicable rule remains applicable.

Example:

```caret
a = rule
  T condition
  E disableSomething

b = rule
  T condition and somethingEnabled
  E otherEffect
```

If both initially become applicable and `a` executes first, its effect may make `b` no longer applicable.

If `b` executes first, both effects may occur.

If that difference matters, the developer must specify ordering.

---

## Unordered-rule diagnostics

Because accidental ordering dependencies can produce difficult bugs, Caret tooling should warn when it detects potentially significant unordered rule application.

A diagnostic may conceptually report:

```text
warning:
rules `a` and `b` may become applicable without a defined order
their effects may be observed in either order
```

Static analysis should report cases it can reasonably identify.

A development or debug runtime may additionally report actual cases where several unordered rules become applicable together.

This is a warning, not an error.

Unordered application is a legitimate and intentional design technique.

---

## Explicit acknowledgement of unordered execution

A developer may explicitly state that arbitrary ordering is acceptable.

The `unordered` contract marks such intent:

```caret
(unordered) ambientEffect = rule
  T event
  E updateAmbientEffect
```

A ruleset may similarly declare that unordered interactions among its relevant rules are intentional:

```caret
(unordered) AmbientRules =
  ruleset
    ...
```

The annotation suppresses applicable unordered-order diagnostics.

It does **not** change scheduling behavior.

```caret
(unordered)
```

means:

> Arbitrary ordering is semantically acceptable here.

It does not mean that the runtime must randomize execution order.

`unordered` is a built-in declaration contract, not a second annotation system. Like `pure`, it
has compiler-recognized semantic behavior beyond an ordinary Boolean predicate. It is valid on a
rule or ruleset declaration and invalid on unrelated values.

---

## Enforcing order

When execution order matters, it must be represented explicitly.

The preferred mechanism is a causal relationship between rules.

For example:

```caret
damage = rule
  T attack
  E applyDamage

death = rule
  T fall @damage.context
  E checkDeath
```

`death` cannot precede completion of `damage`.

This is a semantic dependency rather than a source-order convention.

---

# Rule chaining

## Explicit chain

A sequence of rules may be defined explicitly through rule contexts:

```caret
first = rule
  T start
  E firstEffect

second = rule
  T fall @first.context
  E secondEffect

third = rule
  T fall @second.context
  E thirdEffect
```

This imposes:

```text
first
  ↓
second
  ↓
third
```

---

## `chain` sugar

Caret should provide concise sugar for this common pattern:

```caret
chain
  rule
    T start
    E firstEffect

  rule
    E secondEffect

  rule
    E thirdEffect
```

This is equivalent to connecting each subsequent rule to:

```caret
fall @previous.context
```

The chain therefore compiles to ordinary rules and ordinary contexts.

It does not introduce a separate execution mechanism.

---

## Explicit trigger in a chain

A chained rule may additionally specify a trigger:

```caret
chain
  rule
    T start
    E first

  rule
    T ready
    E second
```

The effective trigger of the second rule is conceptually:

```caret
fall @previous.context and ready
```

Thus `ready` must hold at the completion front of the previous rule.

If the desired meaning is instead:

> first must have completed, then wait however long necessary for `ready`

that should be represented using a persistent context rather than ordinary chain-front semantics.

---

## Partial ordering

Rule dependencies may form a partial order rather than a single sequence.

Conceptually:

```text
       A
      / \
     B   C
      \ /
       D
```

`B` and `C` have no ordering relationship and may therefore execute in arbitrary order.

Both are constrained to occur after `A`.

`D` is constrained by both branches.

This is intentional.

Caret should constrain only those rule relationships explicitly expressed by the program.

Independent branches remain unordered.

Numeric priorities or implicit source-order priorities are not required for the core rule model.

---

# Rulesets

## Overview

A `RuleSet` is a first-class reusable scope containing rules and supporting definitions.

Rulesets may contain:

* rules;
* contexts;
* helper functions;
* data;
* configuration;
* nested rulesets;
* private implementation state.

Example:

```caret
Combat attacker target damage =
  ruleset
    prepare = rule
      T attackRequested attacker
      E prepareAttack attacker

    ^attack = rule
      T fall @prepare.context
      E damage target (damage attacker target)

    cleanup = rule
      T fall @attack.context
      E finishAttack attacker
```

---

## Ruleset templates

Caret does not require a separate template language for rulesets.

An ordinary function returning a `RuleSet` acts as a template:

```caret
Combat attacker target damage =
  ruleset
    ...
```

Its ordinary Caret parameters are the ruleset holes.

Ruleset parameters may include:

* objects;
* contexts;
* functions;
* rules;
* rulesets;
* collections;
* predicates;
* formats;
* configuration values;
* effect functions.

Normal contracts may constrain them.

Normal partial application also applies:

```caret
standardCombat =
  Combat _ _ standardDamage
```

The remaining `_` positions are supplied when the template is instantiated.

---

## Ruleset encapsulation

Members of a ruleset are private by default.

`^` exposes a member through the ruleset's public interface.

Example:

```caret
TurnSystem players =
  ruleset
    index = 0
    internalState = context down

    ^turn = context down

    ^next = rule
      T endTurn
      E advancePlayer players
```

External code may access:

```caret
turnSystem.turn
turnSystem.next
```

but cannot access private bindings such as:

```caret
turnSystem.index
turnSystem.internalState
```

This uses the normal Caret meaning of `^`.

Rulesets do not introduce another visibility system.

---

## Exported rules

Rules are exported in exactly the same way:

```caret
Movement board pieces =
  ruleset
    validate = rule
      ...

    update = rule
      ...

    ^completed = rule
      ...
```

External users may refer to:

```caret
movement.completed
@movement.completed
@movement.completed.context
```

Private internal rules remain inaccessible.

Exported rules and contexts provide stable integration points between ruleset libraries.

---

## Ruleset instances

Every ruleset construction creates an independent instance.

For example:

```caret
playerCombat = Combat player enemy damage
enemyCombat = Combat enemy player enemyDamage
```

must create independent runtime state for:

* active states;
* rule contexts;
* private contexts;
* private instance state;
* instance-local rules.

The ruleset definition may be shared, but runtime state belongs to each instance.

---

## Nested rulesets

Rulesets may build larger systems from smaller rulesets:

```caret
TurnBasedCombat players world damage =
  ruleset
    install TurnRules players
    install TargetSelection world
    install DamageRules world damage
    install DeathRules world
```

This allows reusable libraries to be assembled hierarchically.

---

# `ruleCycle`

## Overview

`ruleCycle` is the execution environment for rules.

A rule cycle:

1. executes initialization;
2. establishes its objects, contexts, rules, and rulesets;
3. raises its master context;
4. traverses relevant objects and rules;
5. generates implicit contexts and fronts;
6. determines applicable rules;
7. applies one permitted applicable rule at a time;
8. propagates its effects;
9. reevaluates affected rules;
10. continues until stable;
11. advances its traversal;
12. terminates when its master context goes down.

---

## Initialization

A rule cycle contains an `init` part:

```caret
system =
  ruleCycle
    init
      ...
```

Initialization establishes the initial rule-cycle universe.

It may create:

* objects;
* contexts;
* rules;
* ruleset instances;
* data;
* other cycle-local state.

Example:

```caret
game =
  ruleCycle
    init
      player = object
        ^health = 100

      enemy = object
        ^health = 50

      gameOver = rule
        T player.health <= 0
        E lower cycle
```

---

## Installing rulesets

A ruleset may be constructed independently:

```caret
combat = Combat player enemy calculateDamage
```

and installed into the current cycle:

```caret
install combat
```

or commonly:

```caret
install Combat player enemy calculateDamage
```

according to normal Caret application rules.

Installation makes the ruleset's relevant rules and contexts part of the current `ruleCycle`.

A constructed but uninstalled ruleset remains an ordinary value and does not autonomously execute.

---

## Template-based system construction

A principal purpose of `ruleCycle` is to assemble systems from reusable rule libraries.

Example:

```caret
game =
  ruleCycle
    init
      board = makeBoard 8 8

      white = Player "White"
      black = Player "Black"

      pieces = makePieces board white black

      install AlternatingTurns white black
      install ChessMovement board pieces
      install CaptureRules pieces
      install ChessVictory white black pieces
```

The application-specific definition may therefore consist mainly of objects, configuration, and instantiated rulesets.

The same mechanism can construct a data interpreter:

```caret
parser =
  ruleCycle
    init
      source = stream bytes

      install Signature pngSignature
      install ChunkReader source PngChunk
      install StopAt "IEND"
```

---

## Master cycle context

Every `ruleCycle` owns an implicit master context.

At cycle start:

```text
down -> up
```

producing its rise front.

The cycle runs while that context is up.

A rule may terminate the cycle:

```caret
finish = rule
  T completed
  E lower cycle
```

The cycle ends when its master context goes down.

---

## Object traversal

A rule cycle implicitly traverses the objects belonging to its runtime universe.

Application code does not normally write this outer traversal explicitly.

When an object is entered, processed, or left, the cycle may implicitly raise and lower object-related contexts.

Conceptually:

```text
object A context rises
    rule propagation
object A context falls

object B context rises
    rule propagation
object B context falls
```

These transitions produce ordinary fronts available to rule triggers.

Objects may also participate in category or state contexts where defined by their contracts or object model.

---

## Rule scheduling

The observable scheduling model is:

```text
1. Update context/object state.

2. Determine applicable rules.

3. Respect explicit causal ordering relationships.

4. If multiple unordered rules are applicable,
   choose an arbitrary one.

5. Raise the chosen rule's implicit context.

6. Execute its effect.

7. Propagate state and context changes.

8. Lower the rule's implicit context.

9. Propagate the resulting fall front.

10. Reevaluate affected rules.

11. Repeat until no applicable rule remains
    for the current propagation step.
```

The implementation need not literally scan every rule.

It may maintain dependency indexes, queues, or other optimized structures.

The observable result must follow the same scheduling semantics.

---

## No source-order guarantee

The order in which rules appear in:

* source code;
* a `ruleset`;
* an `init` block;
* an internal collection

does not create a scheduling constraint.

For example:

```caret
firstInSource = rule
  ...

secondInSource = rule
  ...
```

does not imply:

```text
firstInSource -> secondInSource
```

If order matters, the program must state the relationship explicitly.

---

## Propagation to stability

Effects may change:

* object state;
* contexts;
* rule active states;
* object existence;
* installed state;
* values used by triggers.

These changes may make other rules applicable.

The cycle continues applying and propagating rules until the current processing step reaches a state in which no further rule is applicable.

Conceptually:

```text
change
  ↓
rule A
  ↓
change
  ↓
rule B
  ↓
rule C
  ↓
stable
```

Only then does normal traversal advance.

---

## Trigger stability

Repeated evaluation must not repeatedly fire a continuously true trigger.

For:

```caret
rule
  T x > 10
  E ...
```

application occurs on the relevant transition:

```text
false -> true
```

not on every internal scan while `x > 10` remains true.

The runtime must retain sufficient trigger history to preserve this behavior.

---

## Object creation and destruction

Objects in a rule cycle are persistent values with stable logical identities. An object version is
an immutable public scope constructed with ordinary exported bindings:

```caret
player = object
  ^health = 100
  ^name = "Ada"
```

The cycle state stores the current version under an exported state field. Replacing that field with
a newly constructed version preserves the object's logical identity; the previous version remains
unchanged for any code that still holds it. Objects outside a cycle remain ordinary inert values and
do not acquire autonomous behavior.

Effects may create objects:

```caret
create bullet
```

or destroy them:

```caret
destroy enemy
```

Created objects become part of the rule-cycle universe.

Destroyed objects cease to participate after destruction becomes effective.

Creation adds a new logical identity to the next persistent cycle state. Destruction removes that
identity from the next state. Both changes commit at the effect boundary and alter traversal only at
the next deterministic traversal boundary; neither operation reenters traversal while an object is
being processed.

Rule-cycle object traversal order is deliberately unspecified and is not observable language
behavior. Each traversal operates on a stable membership snapshot and visits every identity in that
snapshot exactly once. Creation and destruction take effect at the next documented traversal
boundary. Code requiring cross-object order must express it through contexts, triggers, or chains.
An implementation may use a stable internal order, but programs and tests must not depend on it.

The initial implementation should avoid uncontrolled traversal reentrancy when an object is created during another object's propagation.

---

## Dynamic rule state

Rule effects may change rule active states:

```caret
activate @specialRule
deactivate @tutorialRule
```

Such changes participate in normal propagation.

The active state is runtime state, not merely a compile-time annotation.

---

## Cycle termination

The rule cycle runs while its master context remains up.

A normal termination operation is:

```caret
lower cycle
```

Once the cycle context falls, no new ordinary traversal iteration should begin.

The runtime may finish the currently required deterministic cleanup or propagation before returning.

---

## Relationship to ordinary `cycle`

Ordinary:

```caret
cycle initial condition body prepare
```

explicitly provides state transformations.

`ruleCycle` derives them from:

* objects;
* contexts;
* installed rules;
* installed rulesets;
* CATEN semantics;
* the rule scheduler.

Conceptually:

```text
condition:
    cycle context is up

body:
    process objects and contexts
    schedule applicable rules
    propagate rule effects to stability

prepare:
    advance traversal
```

`ruleCycle` may internally reuse ordinary cycle machinery, but its reactive scheduling semantics are defined separately.

---

# Implementation requirements

The initial implementation should support at minimum:

1. A first-class `Rule` value.
2. Optional CATEN clauses:

```text
C Context
A Active
T Trigger
E Effect
N Name (string-literal ID)
```

3. Persistent up/down contexts.
4. Boolean context combinations.
5. `rise` and `fall` fronts.
6. Runtime rule active states.
7. Edge-based trigger behavior.
8. Implicit contexts for rule application.
9. Rule chaining through rule-context fronts.
10. `chain` sugar.
11. Explicitly unordered rule execution when no dependency defines order.
12. No implicit source-order priority.
13. Reevaluation after each selected rule's effects.
14. Warning diagnostics for potentially significant unordered rule interactions.
15. `(unordered)` as explicit acknowledgement/suppression of those diagnostics.
16. Explicit ordering through rule dependencies and chains.
17. A first-class `RuleSet`.
18. Ruleset parameters through ordinary Caret functions.
19. Partial ruleset application using `_`.
20. `^` exports for rules and other ruleset members.
21. Independent ruleset instances.
22. `install ruleset`.
23. `ruleCycle` initialization.
24. An implicit master cycle context.
25. Object traversal and implicit object-related context changes.
26. Rule propagation until stable.
27. Object creation and destruction.
28. Runtime rule activation/deactivation.
29. Cycle termination by lowering its master context.
30. Ordinary Caret effect inference through rule effects.

The initial implementation may postpone:

* numeric rule priorities;
* parallel execution;
* transactional batches;
* distributed rule cycles;
* optimized dependency graphs;
* dynamic ruleset unloading;
* debugger visualization;
* formal conflict analysis.

These later features must preserve the principle that rule order is constrained only where the program explicitly specifies a dependency.

---

# Design principle

A `ruleCycle` establishes a reactive universe of objects, contexts, rules, and rulesets.

A rule becomes applicable when:

```text
C is up
AND
A is on
AND
T triggers
```

Application causes:

```text
rule context rises
E executes
rule context falls
```

Those effects and fronts may make additional rules applicable.

When several rules are applicable:

```text
explicit dependency
    -> constrains their order

no dependency
    -> order is deliberately arbitrary
```

The runtime applies one permitted rule, propagates its effects, reevaluates the system, and continues until the current propagation reaches stability.

The developer may explicitly acknowledge harmless unordered behavior with:

```caret
(unordered)
```

and should express required ordering through causal relationships such as rule-context dependencies or `chain`.

Rulesets package reusable parameterized behavior.

`^` defines their public interface.

The `ruleCycle` `init` block assembles those reusable rule libraries with concrete objects and configuration, allowing systems such as games, interpreters, simulations, and workflows to be built primarily by composition rather than explicit control flow.

## Planned modules and compilation

### Source modules and stable module IDs

A source module is one Caret source file. A file may optionally declare one stable logical
`ModuleId` at file top level:

```caret
clientServer = module
```

This is a module-ID declaration, not an ordinary assignment. The left-hand name identifies the
current source module in the compilation environment's module catalog. It is not a runtime binding,
is neither private nor exported, does not require `^`, and does not appear in the module's exported
scope. The declaration may occur at most once in a file and only at file/module top level.

`module` remains reserved. Bare `module` is not a general expression and is valid only as the exact
right-hand side of `moduleId = module`. A source file need not declare an ID; such a file remains
importable by path. The declared ID uses the ordinary identifier spelling rules, but occupies the
separate flat module-ID namespace. It may therefore have the same spelling as an unrelated ordinary
lexical binding without either declaration shadowing or replacing the other.

These terms remain distinct:

* a **source module** is a Caret source file;
* a **ModuleId** is its optional stable logical catalog identifier;
* a **module value** is the immutable exported scope obtained by importing the source module; and
* `@module` is the metadata/reflection reference for the module containing currently executing code.

### Import expressions

A source module is evaluated through an ordinary import expression. A string imports by physical
source location:

```caret
math = import "lib/math.caret"
```

The path is resolved relative to the importing source file after normalizing `.` and `..`. A path
import requires the explicit file name and does not search a global package path.
A `ModuleId` imports through the compilation environment's module catalog:

```caret
shared = import clientServer
```

Conceptually, `import` has both contracts:

```text
import : String -> Module
import : ModuleId -> Module
```

Module IDs form a compiler-known namespace, not an ordinary lexical scope. The compiler resolves an
identifier through the module catalog where a `ModuleId` is required, notably as this `import`
operand. Catalog entries are not injected as runtime globals. Consequently, a discovered
`client = module` declaration in another file does not prevent ordinary code from declaring
`client = createClient`.

Successful module evaluation is cached by canonical source path for the lifetime of one execution
environment generation. A ModuleId is a stable lookup identity that resolves to a source module; it
does not replace canonical source path as the actual loading, cycle-detection, or evaluation-cache
key. Path and ID imports that resolve to the same canonical file therefore share one evaluated
module value. Every importer in that environment receives the same immutable module scope
containing only top-level `^` exports.

Sandboxes evaluate modules independently: immutable parsed or compiled artifacts may be shared,
but evaluated modules, initialization effects, bindings, and mutable runtime state may not cross
environment boundaries. Reloading a sandbox creates a fresh module-evaluation cache. Private
bindings remain inaccessible through lookup and reflection.

An import cycle is a located module diagnostic that reports the import chain. A module that fails to
load or evaluate is not cached as successful. Importing the same canonical module again does not
repeat its initialization effects.

### Module catalog discovery

Before resolving ordinary imports for a compilation root, the normal compilation environment
recursively examines Caret source files below the directory containing that root. It shallowly
collects their top-level module-ID declarations without semantically compiling or evaluating every
file. An unrelated, unreachable file with an ordinary semantic error therefore does not fail the
build merely because it is below the root directory. A malformed module declaration may fail
catalog construction.

All discovered project IDs are entries in one flat project catalog. The normal environment combines
that catalog with environment-supplied module IDs, including standard-library module IDs. Every
visible ID must be unique. Duplicate project declarations and collisions with visible
standard-library IDs are compilation errors even when no conflicting module is eventually imported;
the diagnostic identifies every conflicting declaration or supplied catalog location. Importing an
ID absent from the visible catalog is a located unresolved-ModuleId diagnostic.

There is no implied package hierarchy, version namespace, wildcard import, package manifest, global
package search path, or special standard-library spelling such as `std.collections`. Standard-library
modules participate through the same visible catalog, so `import collections` is an ordinary
ModuleId import when that ID is supplied by the environment.

### Module diagnostics and implementation requirements

The initial module implementation must:

1. parse at most one well-formed `moduleId = module` declaration at file top level;
2. diagnose a second declaration, a declaration in a nested scope, and malformed declaration forms;
3. discover declarations shallowly below the compilation-root directory without compiling or
   evaluating unrelated files;
4. combine project and environment-supplied catalogs without injecting their IDs into lexical scope;
5. diagnose every location participating in a duplicate project ID or visible standard-library
   collision;
6. resolve both `String` and `ModuleId` imports and diagnose an unresolved ModuleId at its use;
7. preserve relative normalized path imports and canonical-source-path evaluation caching;
8. treat path and ID imports resolving to the same canonical source as one module evaluation;
9. retain the existing located canonical-source import-cycle diagnostic; and
10. enforce environment-relative catalog visibility for normal execution, compile-time execution,
    sandboxes, reflection, and code reification.

## `@root`, Program Reification, Quines, and Sandboxes

### Normative reference model

`@root` and `@module` are synthetic, metadata-only reflection references. Neither corresponds to an
ordinary scope object and neither is callable. Bare `root` is reserved and invalid as an expression.
Bare `module` is likewise not a general expression; its sole non-reflective use is the right-hand
marker in a top-level `moduleId = module` declaration. Neither spelling can be defined as an ordinary
binding or parameter. The parser recognizes each special reference as a primary expression, so
compact access such as `@root.code` and `@module.code` is valid without changing the precedence of
ordinary `@value` reflection and field access.

`@root` identifies the root metadata of the current execution environment. `@module` identifies
the module containing the currently executing code. They compare equal exactly when that module is
loaded as the root module:

```caret
@module == @root
```

`@module` does not denote the optional ModuleId declared by that source file. The declaration is a
catalog lookup identity; `@module` reflects the current source module. Module metadata may eventually
expose its ID when present, but this specification does not yet assign a field name for it.

The initial metadata common to these references consists of `kind`, `name`, visible binding
`names`, and semantic `code`. Future catalogs such as `functions`, `contracts`, and `modules` may
be added, but their entries are non-callable descriptors; ordinary bindings remain the invocation
path.

The existing reflective `name` metadata is not thereby defined as the optional stable ModuleId.

An imported module may be reflected through its binding:

```caret
math = import "lib/math.caret"
print toString @math.code
```

For the initial language, imported-module code is always visible and contains complete semantic
code, including private declarations and literal values. This grants information, not authority:
private bindings remain inaccessible and non-invocable. Programs must not embed secrets in source
under the assumption that private module code is hidden. Fine-grained code visibility is deferred.

### Code values, snapshots, and equality

`Code` and `CodeElement` are immutable structural semantic values. They contain no source text,
comments, formatting, source paths, offsets, line/column locations, or original grouping. An
implementation may retain spans privately for diagnostics, but must not expose them as code
metadata. Live reflective references retain identity equality; obtaining structural `.code` from a
reference does not change that identity.

The code of a file module contains the whole admitted analyzed unit, including declarations that
occur later in source order. A REPL root contains all prior successful submissions plus the current
submission provisionally while it evaluates, permitting a quine; a failed submission contributes
neither code nor bindings. Tests have no special root: separately executed test programs have
separate roots, while tests run by one central program share that program's root. Nested
environments apply their own visibility boundary.

`toString Code` recursively serializes the complete semantic structure of that code unit. Imports
remain semantic import references and never inline imported module bodies; those bodies are
available separately through the imported module's reflection reference. Built-in and native
operations appear as portable semantic external references containing their language identity,
contract, and effects, never a JVM class, Java method, native address, or backend body.
The semantic code graph preserves shared references rather than duplicating referenced definitions;
canonical text emits each definition once and uses canonical references at every other occurrence.

Structural code equality and canonical serialization:

* compare binding relationships rather than parameter and private-local spelling;
* preserve every externally or reflectively observable name, including exports, fields, contracts,
  module bindings, metadata names, and dynamic lookup targets;
* may reorder elements only when semantic analysis proves them independent, using a
  language-defined structural order; and
* retain source/evaluation order whenever independence cannot be proved.

Canonical serialization assigns deterministic names to alpha-equivalent private bindings. Path
imports are emitted as normalized logical paths: `.` and `..` are resolved lexically, `/` is the
separator on every platform, and absolute host filesystem paths are never emitted. A ModuleId import
retains its stable logical ID rather than serializing the catalog's current physical source path.
Canonical code declares the portable imports and semantic catalog dependencies required to parse
it; missing or incompatible dependencies are located diagnostics. The form is shared by all Caret
implementations rather than being JVM- or process-specific.

Dynamically supplied host functions and capabilities are not serialized as code or dependency
implementations. Canonical source refers to their exposed binding names normally and requires a
compatible environment when evaluated again. Reflection and serialization reveal no host body,
native identity, origin, or private capture.

### Overview

Caret programs are reflectable from within themselves.

The special reference:

```caret
@root
```

refers to the root of the Caret execution environment visible to the current code.

The root exposes reflective metadata about that environment, including its code representation.

For example:

```caret
@root.code
```

is the program's code represented as structured Caret data containing references to definitions, functions, parameters, expressions, contracts, rules, and other program elements.

Because code is representable as data and may be converted back into canonical Caret text, a simple Caret quine may be written as:

```caret
print toString @root.code
```

The output need not preserve the exact original source text.

It must reproduce a canonical Caret program equivalent to the code represented by `@root.code`.

The meaning of `root` is relative to the current execution environment.

Inside a sandbox, `@root` refers to the sandbox root rather than to the host application's root.

This provides the foundation for Caret's sandbox and capability-isolation model.

---

# `@root`

## Root reference

`@root` represents the metadata of the current Caret execution environment. There is no
corresponding ordinary `root` object or binding.

Example:

```caret
r = @root
```

The root may eventually expose additional metadata catalogs such as:

```caret
@root.code
@root.name
@root.contracts
@root.functions
```

`kind`, `name`, `names`, and `code` are the settled minimum schema. Additional catalogs contain
non-callable descriptors, not callable bindings or ambient capabilities.

`@root` is available from anywhere in Caret code. Its contents are relative to, and filtered by,
the current execution environment.

---

## Root is environment-relative

`root` does not necessarily mean the physical top-level application process.

It means:

> the root of the Caret universe visible to the currently executing code.

For ordinary application code:

```text
visible root = application root
```

For sandboxed plugin code:

```text
visible root = plugin sandbox root
```

For a tutorial REPL:

```text
visible root = tutorial environment
```

For a test:

```text
visible root = test environment
```

Therefore code using:

```caret
@root
```

does not need to know whether it executes directly in a host application or inside one or more sandbox layers.

---

# Program code metadata

## `.code`

The root exposes the program through:

```caret
@root.code
```

`.code` is a structured representation of Caret code.

It is not required to be the original source text.

Conceptually, `@root.code` is a `Code` value whose elements are `CodeElement` values.

Code elements may represent:

* bindings;
* functions;
* parameters;
* contracts;
* expressions;
* literals;
* collections;
* rules;
* rulesets;
* cycles;
* imports;
* sandboxes;
* other language constructs.

The representation should preserve the semantic structure necessary to reconstruct equivalent Caret code.

---

## Code as data

Program code participates in the ordinary Caret value model.

Code elements may therefore be:

* stored;
* traversed;
* filtered;
* transformed;
* inspected;
* passed to functions;
* compared where appropriate;
* converted to textual representation.

Reflection should expose references rather than duplicating runtime objects unnecessarily.

For example, a function code element may expose information such as:

```text
name
parameters
contracts
body
effects
```

Code contains semantic references only and no source metadata.

---

## Canonical textual form

The standard polymorphic conversion:

```caret
toString value
```

may have a specialization for Caret code.

Conceptually:

```caret
(String) toString (Code) code =
  ...
```

It converts the structured code representation into canonical Caret syntax.

Canonicalization may normalize:

* whitespace;
* indentation;
* line breaks;
* redundant parentheses;
* equivalent formatting;
* other non-semantic source differences.

For example, source such as:

```caret
x=1
```

may canonicalize to:

```caret
x = 1
```

The canonical representation must preserve program meaning, not original textual formatting.

Comments and other source-only information are not represented as code metadata.

---

# Quines

## Canonical quine

Because the running program can access its own structured code and convert that code to canonical Caret text, a Caret quine may be:

```caret
print toString @root.code
```

Conceptually:

```text
@root
    ↓
.code
    ↓
structured representation of current program
    ↓
toString
    ↓
canonical Caret source
    ↓
print
```

The result is a canonical representation of the program.

The output is not required to be byte-for-byte identical to the source file from which the program was loaded.

It is sufficient that parsing the generated canonical code reconstructs the same relevant program structure.

---

## Quine property

For canonical code serialization, the desired relationship is conceptually:

```text
parse(toString(@root.code))
    ≈
@root.code
```

where `≈` means semantic/code-structure equivalence rather than exact source-text identity.

Thus differences in:

```text
whitespace
comments
formatting
redundant grouping
```

do not invalidate the quine.

---

# Sandboxes

## Construction, environment updates, and access

The shared construction form is:

```caret
plugin = sandbox source environment
```

`source` may be a module path or semantic `Code`, selected through ordinary contract dispatch.
`environment` is an immutable exported scope. `sandbox` returns `Result Sandbox`; on success its
`value` is the stable `Sandbox` handle used below. Named members use the universal exported-field
syntax:

```caret
environment =
  ^clock = restrictedClock
```

The host may atomically replace the complete environment snapshot without stopping the plugin:

```caret
swapEnv plugin environment2
```

`swapEnv` validates the replacement before installing it. Failure leaves the previous environment
installed. Success changes neither sandbox identity nor generation, plugin state, module state, or
plugin-export references. The new snapshot is visible at the next environment-boundary lookup; an
in-progress host operation finishes against the target resolved when it began. Consequently a host
function called by the plugin may itself invoke `swapEnv`, and the plugin observes the replacement
after that boundary call returns. `@root.names` and related environment metadata change atomically
with the snapshot; `@root.code` does not, because exposed host bindings are not sandbox code.

Environment-derived callable references are mediated named references. Calling one after a swap
resolves its name through the current snapshot, using the replacement target or producing an
unavailable-capability failure when absent. Values already copied across the boundary remain values.
A child may receive only authority reachable through its parent unless an outer host explicitly
injects more authority.

Exports are accessed like imported-module exports, and all plugin metadata is reached by reflection:

```caret
plugin.function arguments
plugin["dynamic"]~
@plugin.kind
@plugin.state
@plugin.names
@plugin.code
```

`@plugin` metadata is non-callable. Projected host functions reveal only arity, argument contracts,
and their result contract. They do not reveal their implementation, origin, captures, native nature,
or host identity. Effect information may participate in static checking but grants no authority.

Sandbox construction, `swapEnv`, lifecycle operations, and host-to-plugin exported calls return
`Result ValueContract`. Sandbox failure payloads satisfy `ErrorTemplate`, with an exact
sandbox-specific template for `details`. Successful void-like operations use `~` as their value.
If an exported plugin function itself returns a `Result`, that application result remains nested in
the boundary result; results are not flattened implicitly. Inside the sandbox, exposed host
functions retain their ordinary signatures. An unavailable capability aborts the current boundary
operation and becomes the failed result observed by the host.

## Lifecycle and boundary values

The host controls a sandbox with effectful functions:

```caret
terminate plugin
unload plugin
reload plugin
swapEnv plugin environment2
```

`terminate` stops execution, invalidates all references from that generation, and discards runtime
state. It provides no resumable state by default. `unload` terminates if necessary and additionally
releases loaded or compiled resources. The stable sandbox handle retains its source descriptor,
currently installed environment snapshot, and lifecycle metadata.

`reload` is stop-first: it terminates the old generation and invalidates its references before
initializing a fresh generation with a fresh module cache. It never restores the old generation.
If initialization fails, the sandbox becomes unloaded, its exports are unavailable, and a later
`reload` performs a complete fresh load using the retained source and currently installed
environment snapshot.

Reload never rebinds old references automatically. This includes saved functions, direct exports,
and references derived through fields, collections, or computations. The host must look up an
export again to obtain a reference from the new generation. References to the same target may be
equal within one generation; references from different generations are unequal. Immutable values
already obtained from a plugin remain ordinary valid values after reload. If such a value contains
references, the container remains valid but those old-generation references are invalid.

## Overview

`sandbox` evaluates or imports Caret code in a restricted execution environment.

Unlike a normal import, sandboxed code does not inherit unrestricted visibility into the host root.

Instead, the host constructs a sandbox root and chooses what the sandbox may observe and use.

Conceptually:

```caret
plugin =
  sandbox pluginCode environment
```

The sandboxed code sees `@root` as metadata describing the environment constructed from
`environment`; it is not the environment handle or an ordinary capability scope.

It cannot access the host application's root merely by referring to `@root`.

The visible module catalog is also part of the substituted environment boundary. A sandbox does not
automatically inherit the host project's discovered IDs or the normal environment's standard-library
IDs. Only module IDs explicitly made visible to that sandbox environment may be resolved there.
Selected application or standard-library modules may be supplied, but an absent ID behaves as an
unavailable module. Catalog visibility grants lookup visibility only; it does not grant effects or
authority unavailable through the sandbox environment.

Imports, `@root`, `@module`, and code reflection executed inside the sandbox all use this restricted
catalog and cannot reveal or resolve hidden host modules. The mechanism by which a host constructs
the restricted catalog belongs to the sandbox/compiler environment interface and introduces no
ordinary lexical bindings or additional Caret syntax.

---

## Root substitution

The fundamental sandbox operation is root substitution.

Conceptually:

```text
host root
    |
    +-- sandbox environment
            |
            +-- sandbox @root
```

If the host exposes:

```caret
environment =
  ^log = safeLog
  ^files = pluginFiles
  ^clock = safeClock
```

then sandboxed code may use the ordinary visible bindings:

```caret
log
files
clock
```

Bindings not exposed through the sandbox root are not part of the sandbox's observable environment.

For example:

```caret
database
internalState
```

should behave as unavailable if those bindings were not exposed.

The preferred security model is absence of authority rather than unrestricted authority combined with repeated global permission checks.

---

# Sandbox capabilities

## Exposed program capabilities

A sandbox may expose selected host functions, objects, rulesets, data, or services.

For example:

```caret
environment =
  ^print = sandboxPrint
  ^clock = sandboxClock
  ^storage = sandboxStorage
```

The sandbox receives only those capabilities.

It should not be able to discover unrelated host capabilities through ordinary reflection.

---

## Libraries

The host may select which libraries are visible inside a sandbox.

For example, a sandbox may expose:

```text
collections
math
string utilities
rule system
```

while omitting:

```text
filesystem
network
process control
native interop
```

Libraries unavailable to the sandbox should behave as absent rather than globally accessible but forbidden.

---

## Language features

A sandbox may also restrict language-level features.

Examples of potentially controllable features include:

```text
reflection
dynamic evaluation
native interop
unsafe memory access
thread/process creation
filesystem access
network access
```

Restrictions on actual language features cannot always be implemented merely by hiding bindings from `@root`.

The sandbox evaluator/compiler may therefore receive a language-feature capability set in addition to its visible root.

Conceptually:

```text
Sandbox
    root
    permitted language features
    permitted runtime capabilities
```

The initial configuration is an immutable exported scope. `swapEnv` may atomically replace that
complete snapshot while preserving the running sandbox generation.

---

# Isolation layers

## Capability mediation

A host may expose a capability directly:

```caret
^files = systemFiles
```

or through an isolation layer:

```caret
^files = restrictedFiles allowedDirectory
```

Sandboxed code still operates through the normal filesystem contract.

The implementation provided by the host determines what access is actually possible.

Conceptually:

```text
sandbox request
      ↓
isolation layer
      ↓
policy check
      ↓
real resource or replacement
```

---

## Filtered access

A filesystem isolation layer may inspect requested paths:

```caret
open path
```

and conceptually perform:

```caret
openRestricted path =
  allowed path &
    systemOpen path
  !
    accessDenied path
```

If access is allowed, the request is forwarded.

Otherwise the operation fails according to the normal Caret error model.

The unrestricted `systemOpen` capability is not exposed to the sandbox.

---

## Virtualized resources

An isolation layer may replace the underlying resource entirely.

For example:

```caret
^files = virtualFileSystem
```

may make:

```caret
open "example.txt"
```

operate on an in-memory filesystem rather than the operating-system filesystem.

The same principle may apply to:

* network connections;
* clock/time;
* randomness;
* databases;
* environment variables;
* GUI objects;
* clipboard;
* GPU devices;
* process control.

Sandboxed code should normally depend on contracts and behavior rather than on whether the supplied implementation is physical, filtered, simulated, or virtual.

---

# Effects and sandbox authority

Caret effects and sandbox permissions are related but distinct.

An effect contract describes what kind of observable action a function may perform.

For example:

```caret
(fs) load path =
  read path
```

means that `load` may cause a filesystem effect.

It does not grant unrestricted filesystem authority.

Inside different environments, the same effect may be backed by different capabilities:

```text
host:
    fs -> operating-system filesystem

plugin:
    fs -> restricted plugin directory

tutorial:
    fs -> virtual in-memory filesystem
```

Therefore:

```text
effect
    describes what kind of effect occurs

sandbox capability
    determines what authority or implementation is available
```

A sandbox must not treat an effect declaration itself as permission.

---

# Reflection across sandbox boundaries

## Reflective membrane

Caret reflection must respect sandbox boundaries.

Exposing a host reference to a sandbox must not allow the sandbox to navigate through reflection back into arbitrary host state.

For example, if the host exposes:

```caret
^log = hostLog
```

the sandbox may be permitted to inspect metadata such as:

```caret
@log.name
@log.parameters
@log.result
```

without being permitted to inspect:

```text
private host lexical scope
host root
unexposed closure captures
native implementation internals
unexposed capabilities
```

The sandbox boundary therefore acts as a reflective membrane.

---

## Reference projection

A reference crossing into a sandbox may be projected into a restricted reflective view.

Conceptually:

```text
host reference
      ↓
sandbox projection
      ↓
allowed callable behavior
allowed metadata
allowed reachable references
```

Reflection from the projected reference must remain within the authority of the sandbox.

A sandbox must not be escapable merely because Caret supports reification.

---

## Sandboxed `.code`

Inside sandboxed code:

```caret
@root.code
```

refers to the code visible in that sandbox environment.

It must not automatically reveal the complete host program.

It represents only the sandbox root module and its semantic references to visible Caret modules.
Import statements do not recursively inline module bodies. Exposed environment declarations and
implementations are not code inside the sandbox and are omitted. It never contains the hidden host
program, host function bodies, private captures, native identities, or origins. Changing the active
environment therefore does not change `@root.code`.

Thus the standard quine:

```caret
print toString @root.code
```

inside a sandbox reproduces the canonical code visible to that sandbox, not the host's hidden source.

---

# Revocable capabilities

## Persistent references

Replacing the environment with a snapshot that omits a name atomically revokes subsequent boundary
lookup through that name.

For example:

```caret
files = exposedFiles
```

does not preserve access through the environment after the corresponding name is hidden. Boundary
operations dereference the currently exposed host environment. Immutable values already copied into
the sandbox remain values; revoking access to resources reachable through an independently retained
reference requires resource-specific mediation.

Thus `swapEnv` revokes environment-mediated access, while revoking independent resource references
may still require mediation.

---

## Mediated revocation

Capabilities that must be revocable should be exposed through a mediation object whose policy can change.

Conceptually:

```text
sandbox
   ↓
capability proxy
   ↓
current policy
   ↓
resource
```

When permission is revoked, existing references to the proxy remain valid references but deny operations according to the new policy.

This is particularly relevant for:

* plugins;
* long-running scripts;
* dynamically changing permissions.

---

# Nested sandboxes

Sandboxes may contain additional sandboxes.

Conceptually:

```text
host root
    ↓
plugin sandbox root
    ↓
script sandbox root
```

A child sandbox may normally expose only capabilities available to its parent.

The general authority rule should be:

```text
child authority <= parent authority
```

A sandbox must not be able to grant authority it does not possess unless the host runtime explicitly supplies that authority from outside the parent environment.

This allows plugins themselves to safely host untrusted Caret code.

---

# Tutorial and REPL environments

A Caret tutorial may use a sandboxed REPL.

Conceptually:

```text
tutorial host
     |
     +-- sandbox
           |
           +-- REPL
           +-- student @root
           +-- controlled input/output
           +-- virtual resources
           +-- currently unlocked features
```

The initial environment may expose only a small subset:

```caret
[
  ^print = tutorialPrint
  ^Int = Int
  ^Boolean = Boolean
]
```

As the learner progresses, additional features may become available:

```text
arithmetic
functions
collections
contracts
reflection
rules
virtual filesystem
...
```

The student's:

```caret
@root
```

always reflects the environment currently available to that REPL.

---

## Tutorial isolation layer

Input and output may be mediated by the tutorial application.

For example, `print` may actually refer to:

```caret
^print = tutorialPrint
```

rather than unrestricted process output.

`tutorialPrint` may:

* capture the learner's output;
* compare it against lesson requirements;
* display feedback;
* update lesson progress;
* unlock additional capabilities.

Similarly, a virtual resource may enforce the state of the tutorial without changing the language syntax seen by the learner.

---

## Progressive capability exposure

A tutorial may progressively expand the sandbox root.

Conceptually:

```text
lesson 1:
    arithmetic + print

lesson 2:
    functions

lesson 3:
    collections

lesson 4:
    contracts

lesson 5:
    reflection

lesson 6:
    virtual filesystem
```

This allows the available programming environment itself to become part of the teaching progression.

If permissions only increase, simple capability addition may be sufficient.

If capabilities may later be revoked, revocable mediation objects should be used.

---

# Relationship to normal import

Normal import and sandbox import have different trust assumptions.

A normal import integrates code into the ordinary program environment according to normal visibility rules.

Conceptually:

```caret
import "module.caret"
```

means:

> load this code as part of my normal Caret program environment.

A sandbox:

```caret
sandbox module environment
```

means:

> evaluate this code under a substituted root and restricted authority.

An ordinary import may instead use a visible ModuleId. Either overload retains the semantic
distinction from sandbox execution, whose catalog, root visibility, and authority are explicitly
restricted.

---

# Security principle

Sandbox security must be based primarily on capability possession.

Sandboxed code should be unable to perform an operation when it possesses no path to the corresponding capability.

For example, code without access to an unrestricted filesystem function must not be able to manufacture that access merely by:

* naming it;
* reflecting over unrelated objects;
* traversing `@root`;
* inspecting closure internals;
* accessing hidden native state.

Sandbox isolation therefore applies to both ordinary name resolution and reflection.

---

# Implementation requirements

The initial implementation should support at minimum:

1. Global availability of:

```caret
@root
```

relative to the current execution environment.

2. Structured program metadata:

```caret
@root.code
```

3. A first-class code representation suitable for traversal and reflection.

4. Polymorphic:

```caret
toString code
```

producing canonical Caret syntax.

5. The canonical quine:

```caret
print toString @root.code
```

6. Canonical rather than exact-source reconstruction.

7. A `sandbox` execution/import mechanism.

8. Root substitution for sandboxed code.

9. Explicit exposure of selected host bindings.

10. Restricted library visibility.

11. Ability to expose filtered or virtual resource implementations.

12. Separation between effect contracts and actual sandbox authority.

13. Reflection that respects sandbox boundaries.

14. Sandboxed `@root.code` that does not reveal hidden host code.

15. Support for nested sandboxes.

16. Child sandboxes unable to automatically exceed parent authority.

The initial implementation may postpone:

* source-exact code reconstruction;
* comment-preserving serialization;
* fine-grained per-metadata-field permissions;
* dynamic language-feature unlocking;
* revocable capability proxies;
* operating-system process isolation;
* hardware-enforced sandboxing;
* sophisticated static information-flow analysis.

These later features must preserve the fundamental rule that a sandbox substitutes the visible root and restricts the authority reachable from that root.

---

# Design principle

`@root` means:

> the root of the Caret environment visible to this code.

The normal application root may expose the entire program.

A sandbox may expose only a controlled subset.

Code reflection follows the same boundary.

Therefore:

```caret
print toString @root.code
```

is always a quine for the program environment visible to the caller, reconstructed in canonical Caret syntax.

A sandbox does not merely hide names.

It defines a smaller Caret universe consisting of selected:

```text
code
bindings
libraries
language capabilities
runtime capabilities
resources
```

and may mediate those capabilities through filtered or virtual implementations.

This allows the same mechanism to support:

* plugins;
* embedded scripting;
* REPLs;
* tutorials;
* tests;
* virtualized environments;
* restricted automation;
* nested execution environments

without weakening Caret's reflection model.

## Templates

### Overview

A **template** is a contract describing the structure and contents of a collection.

A template is constructed by calling the ordinary `template` function with either a concrete
collection or a reifiable function produced by a collection expression containing:

* holes;
* contracted holes;
* fixed values;
* named fields;
* nested templates or collections.

Example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

`Point` is a contract describing a two-element collection whose elements both satisfy `Float`.

Therefore:

```caret
Point [10.0 20.0]
```

is true, while:

```caret
Point [10.0 "hello"]
```

is false.

Templates use the ordinary Caret contract system.

They do not introduce a separate type system.

---

### Template construction

The general form is:

```caret
template specimen
```

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

Collection literals are hole-expression boundaries: their holes are materialized as a constructor
function before a surrounding application consumes the literal's value. Because this collection
contains holes, it first becomes a function whose parameters fill those holes and whose result is
the completed collection. The ordinary `template` function then derives a contract from that
function's language-owned collection constructor descriptor.

The supported overloads are conceptually:

```text
template : Collection -> Contract
template : CollectionConstructor -> Contract
```

A concrete collection produces a fixed-only exact template. A `CollectionConstructor` is an
ordinary hole function whose retained descriptor is structurally a collection construction.
`template` is not syntax and creates no exception to ordinary application or hole evaluation.

For example, the compact form above is equivalent to:

```caret
PointConstructor =
  [
    (Float) _
    (Float) _
  ]

Point = template PointConstructor
```

Calling `PointConstructor 10.0 20.0` produces `[10.0 20.0]`; calling
`template PointConstructor` instead derives the corresponding membership contract.

Only reifiable hole functions whose expression directly constructs a collection are accepted
initially. Named functions, opaque/native callables, and partial expressions such as `[transform _]`
whose hole is used in a computed element are rejected with a located
`TEMPLATE_INVALID_CONSTRUCTOR` diagnostic. This restriction avoids attempting general function
inversion. The descriptor is language-owned metadata and must never expose Java AST or runtime
implementation objects.

Template membership is the structural inverse of construction: candidate elements occupy hole
positions, captured values compare equal, and fields and nested collections match recursively. The
constructor is not invoked during membership testing.

The resulting value may be used anywhere an ordinary contract may be used.

For example:

```caret
(Point) position
```

or:

```caret
(Collection Point) positions
```

---

### Holes

#### Unconstrained holes

A plain hole:

```caret
_
```

represents a position whose value may vary freely.

Example:

```caret
Pair =
  template [
    _
    _
  ]
```

matches any two-element collection:

```caret
[1 2]
["a" true]
[person 42]
```

provided the collection has the required shape.

---

#### Contracted holes

Normal Caret contract syntax may constrain a hole:

```caret
(Int) _
```

Example:

```caret
IntegerPair =
  template [
    (Int) _
    (Int) _
  ]
```

matches:

```caret
[10 20]
[-1 42]
```

but not:

```caret
[10 "twenty"]
```

Multiple contracts use the normal Caret contract syntax:

```caret
PositiveIntegerPair =
  template [
    (Int positive) _
    (Int positive) _
  ]
```

No template-specific constraint syntax is required.

#### Numbered holes

Numbered holes retain their ordinary partial-application meaning in collection constructors:

```caret
Diagonal = template [_1 _1]
Swapped = template [_2 _1]
```

Repeated occurrences of the same numbered hole describe the same constructor parameter and
therefore require the corresponding candidate positions to be equal. Numbering and reordering
change the constructor's parameter order, not the collection's structural order. Any contracts on
repeated occurrences must all hold for the shared candidate value.

As with every partial expression, numbered and unnumbered holes may not be mixed.

---

### Fixed values

A template element that is not a hole represents a fixed-value requirement.

Example:

```caret
Header =
  template [
    0xCA
    0xFE
    (Int) _
  ]
```

matches:

```caret
[0xCA 0xFE 100]
```

but not:

```caret
[0xCA 0xFF 100]
```

Conceptually, a fixed element:

```caret
42
```

requires:

```text
eq actual 42
```

using the applicable polymorphic equality operation. Non-hole subexpressions are evaluated and
captured eagerly when the collection-producing hole function is created; deriving or testing the
template does not repeat those effects. A fixed template value must support Caret
equality. Template construction fails with a located diagnostic when a fixed value is callable or
otherwise non-comparable; membership testing must not turn such a value into an exceptional
predicate.

Thus a template may combine exact-value requirements and contract requirements.

---

### Template semantics

For:

```caret
T =
  template [
    fixed
    (Contract) _
    _
  ]
```

a candidate collection satisfies `T` when:

1. it has the required collection shape;
2. the first element equals `fixed`;
3. the second element satisfies `Contract`;
4. the third element exists but is otherwise unrestricted.

Conceptually:

```text
T value =
    Collection value
    and shape value == templateShape
    and eq value[0] fixed
    and Contract value[1]
```

with no additional value constraint on `value[2]`.

The compiler may perform these checks statically when sufficient information is known.

Runtime validation is required only where the contract cannot be proven statically.

---

### Shape

A template describes collection **shape** as well as element constraints.

For a positional collection:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

the shape includes:

```text
element count: 2

position 0:
    Float hole

position 1:
    Float hole
```

Therefore a three-element collection does not satisfy `Point`:

```caret
Point [1.0 2.0 3.0]
```

is false.

Likewise:

```caret
Point [1.0]
```

is false.

For positional templates, element count and position are part of the template contract.

---

### Named fields

Templates may describe named structured collections using ordinary `^` fields.

Example:

```caret
Person =
  template [
    ^name = (String) _
    ^age = (Int positive) _
    ^active = true
  ]
```

A matching value may be:

```caret
[
  ^name = "Alice"
  ^age = 42
  ^active = true
]
```

The template requires:

```text
name:
    satisfies String

age:
    satisfies Int
    satisfies positive

active:
    equals true
```

Field names are part of the template structure. For the initial exact model, named shape means the
exact set of field names and the field ordering defined by the universal collection model. Missing,
additional, or reordered fields are incompatible whenever that ordering is observable for the
candidate collection.

The template system does not require a separate record-schema syntax.

---

### Dynamic fields

Templates may use ordinary field values where dynamic keys are required.

For example, where appropriate:

```caret
template [
  field key (String) _
]
```

uses the same first-class field mechanism as ordinary collection construction. The key expression
is evaluated exactly once when the collection or hole function is created. It must produce a valid field name, and
duplicate resolved names are located template-construction diagnostics.

Templates do not introduce another dictionary representation.

---

### Nested templates

Templates may contain nested collection structure.

Example:

```caret
Person =
  template [
    ^name = (String) _

    ^position =
      [
        (Float) _
        (Float) _
      ]
  ]
```

Within an eligible collection-constructor descriptor, a bare nested collection contributes
recursively to the outer template shape; it is not a fixed-value equality requirement.
Non-collection expressions that are neither holes nor contracted holes remain fixed-value
requirements.

A matching value is:

```caret
[
  ^name = "Alice"
  ^position = [
    10.0
    20.0
  ]
]
```

The implementation should recursively derive structural constraints from nested collection values.

Where a reusable nested contract is preferable, an explicitly defined template may be used:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

Person =
  template [
    ^name = (String) _
    ^position = (Point) _
  ]
```

Both forms participate in the ordinary contract system.

---

### Templates are contracts

The result of `template` is a normal Caret contract.

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

may be used as:

```caret
(Point) p
```

or:

```caret
(Collection Point) points
```

or as part of another contract:

```caret
VisiblePoint =
  contract Point visible
```

Templates therefore participate in:

* contract derivation;
* parameter contracts;
* return contracts;
* collection element contracts;
* polymorphic function dispatch;
* runtime contract checks;
* static contract inference.

No separate "template type" mechanism is required.

---

### Template derivation

Templates may participate in ordinary derived contracts.

Example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

NonZeroPoint =
  contract Point nonZero
```

Conceptually:

```text
NonZeroPoint value
    =
Point value
and nonZero value
```

Likewise, a structural template may derive from or be combined with other collection-related contracts.

---

### Collections of template-shaped values

Templates are particularly useful as common metadata for homogeneous structural collections.

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

(Collection Point) points =
  [
    [1.0 2.0]
    [3.0 4.0]
    [5.0 6.0]
  ]
```

Every element has the same template shape.

An implementation may avoid repeating the full structural metadata for every point.

Conceptually:

```text
collection metadata:

    element contract:
        Point

    Point shape:
        two Float positions

values:

    [1.0 2.0]
    [3.0 4.0]
    [5.0 6.0]
```

The template may be stored once as common collection metadata.

---

### Templates and metadata

A template may describe more precise structural metadata than a broad element contract.

For example:

```caret
(Collection Number) values
```

only establishes that every element satisfies `Number`.

By contrast:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

establishes:

* collection arity;
* element positions;
* element contracts;
* structural nesting;
* named fields where present;
* fixed values where present.

A collection whose elements all satisfy the same template can therefore share this metadata.

The optimization permission is:

> When every element of a collection has the same template shape, the template may serve as shared element metadata for the entire collection.

The physical metadata representation remains an implementation detail. Sharing must not change
value identity, structural equality, reflection, evaluation order, or any other observable behavior.

---

### Templates and packed collections

A template may also provide the structural information required for a packed representation.

For example:

```caret
Point =
  template [
    (Float32) _
    (Float32) _
  ]
```

then:

```caret
(Packed Point) points =
  [
    [1.0 2.0]
    [3.0 4.0]
    [5.0 6.0]
  ]
```

may be represented physically as:

```text
Float32 Float32
Float32 Float32
Float32 Float32
```

with the `Point` layout descriptor stored once for the collection.

Conceptually:

```text
element layout:

    position 0:
        Float32
        offset 0

    position 1:
        Float32
        offset 4

stride:
    8 bytes
```

followed by packed values.

The template itself describes logical structure.

`Packed` additionally requires that this structure have a uniform concrete physical representation.

Therefore:

```text
template shape
    !=
packed layout
```

but a sufficiently concrete template may allow a packed layout to be derived.

---

### Heterogeneous template collections

A collection may also contain templates themselves.

For example:

```caret
patterns =
  [
    template [1 _ _]
    template [2 _ _]
    template [3 _ _]
  ]
```

These templates share the same structural form:

```text
three positions

position 0:
    fixed value

position 1:
    hole

position 2:
    hole
```

Only the fixed value differs between instances.

The runtime may therefore share the common template metadata across the collection.

Conceptually:

```text
collection metadata:

    element kind:
        Contract

    common template shape:
        [Fixed Hole Hole]

elements:

    fixed value = 1
    fixed value = 2
    fixed value = 3
```

This allows collections of structurally equivalent templates to be represented efficiently.

---

### Template shape metadata

Two templates may have the same metadata shape while containing different fixed values.

For example:

```caret
template [1 _ _]
template [2 _ _]
template [100 _ _]
```

share the structural descriptor:

```text
[
    Fixed
    Hole
    Hole
]
```

Similarly:

```caret
template [
  ^opcode = 1
  ^arg = (Int) _
]

template [
  ^opcode = 2
  ^arg = (Int) _
]
```

share:

```text
fields:

opcode:
    fixed-value position

arg:
    Int hole
```

while the fixed `opcode` value differs.

An implementation may factor common template structure into collection-level metadata.

---

### Templates and ordinary collection literals

Square brackets retain exactly one fundamental meaning:

```caret
[...]
```

describes collection construction.

A collection expression containing holes follows the ordinary partial-application rule: it evaluates
to a function whose parameters fill the holes and whose result is the completed collection. A
collection literal owns the holes in its structural expression and materializes that function before
the value is passed to a surrounding call. Thus `consume [1 _]` passes a constructor function to
`consume`; it does not make the whole `consume [1 _]` application partial. This is a general
collection rule, not behavior specific to `template`.

Evaluation proceeds recursively. A nested literal used directly as an element belongs to the
enclosing collection's structural constructor, so its holes contribute to the same reifiable
descriptor. A nested literal used as the operand of an inner call materializes its constructor for
that call first. A collection expression never evaluates to a collection containing hole values and
does not automatically become a template.

Template construction is explicit:

```caret
template [...]
```

This distinction is intentional.

For example:

```caret
[
  (Float) _
  (Float) _
]
```

is an ordinary two-argument function. Supplying two values constructs the completed collection.

Passing that function to the ordinary `template` callable creates a structural contract:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

The language therefore does not need separate collection and template literal grammars.

---

### Relationship to ordinary holes

Templates reuse the normal Caret `_` syntax.

A hole means that some value is intentionally unspecified.

Within a collection-producing hole function passed to `template`:

```caret
template [...]
```

the hole represents a variable position in the matched collection.

A contracted hole:

```caret
(Float) _
```

means:

> this position is variable, but any value occupying it must satisfy `Float`.

A fixed element:

```caret
10
```

means:

> this position is not variable; its value must equal `10`.

No additional placeholder syntax is required.

---

### Templates and polymorphic dispatch

Because templates are contracts, they may specialize function definitions.

Example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

(String) toString (Point) p =
  ...
```

A value satisfying `Point` may therefore select the `Point` specialization of `toString`.

Likewise:

```caret
(Float) distance (Point) a (Point) b =
  ...
```

uses ordinary contract-based function polymorphism.

Template dispatch is not a separate dispatch system.

---

### Templates and static checking

The compiler should statically validate template contracts whenever possible.

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]

(Point) p =
  [1.0 2.0]
```

may be proven valid at compile time.

This:

```caret
(Point) p =
  [1.0 "two"]
```

should produce a compile-time error when the compiler knows that `"two"` does not satisfy `Float`.

Likewise:

```caret
(Point) p =
  [1.0 2.0 3.0]
```

may be rejected statically because its shape is incompatible.

When the candidate value is only known dynamically, normal runtime contract checking applies.

---

### Exact shape

The initial template model uses exact structural shape.

For positional collections:

```caret
template [
  (Int) _
  (Int) _
]
```

requires exactly two positions.

For named structures, the fields described by the template are part of its required shape.

The initial implementation should treat additional unmatched structural members as incompatible unless another contract explicitly provides open/extensible-template semantics.

A future contract may provide open structural matching where useful, but it should not silently change the meaning of ordinary `template`.

---

### Templates versus formats

Templates and formats describe different relationships.

A template describes:

```text
Value -> Boolean
```

by specifying a collection's logical shape.

A format describes:

```text
logical value <-> representation
```

A template may therefore be used as the logical contract of values processed by a format.

For example, a format may decode data satisfying:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

without making `Point` itself a serialization format.

Likewise, a concrete template may provide enough structural information to derive a packed memory layout without becoming a `Format`.

---

### Reflection

Templates are first-class contract values and should be reflectable. Their public kind remains
`Contract`; template shape is metadata on the language-owned contract descriptor rather than a
separate `Template` value kind.

Reflection may expose information such as:

```text
shape
element count
field names
hole positions
hole contracts
fixed positions
fixed values
nested templates
derived contracts
```

For example:

```caret
@Point
```

may expose the structure represented by:

```caret
template [
  (Float) _
  (Float) _
]
```

subject to ordinary Caret reflection and sandbox rules.

This enables tooling such as:

* schema viewers;
* editors;
* serializers;
* binary layout tools;
* GPU buffer inspectors;
* pattern editors;
* generated documentation.

---

### Implementation requirements

The initial implementation should support at minimum:

1. Ordinary `template` function application to concrete collections or eligible collection-producing
hole functions:

```caret
template [...]
```

2. Templates as ordinary contracts.

3. Unconstrained holes:

```caret
_
```

4. Contracted holes:

```caret
(Int) _
```

5. Multiple contracts on a hole:

```caret
(Int positive) _
```

6. Numbered-hole reordering and repeated-hole equality constraints.

7. Fixed-value positions.

8. Equality-based checking of fixed values.

9. Exact positional shape matching.

10. Named fields using `^`.

11. Nested collection shapes.

12. Reusable named templates:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

13. Templates usable as binding and parameter contracts.

14. Templates usable as collection element contracts:

```caret
(Collection Point) points
```

15. Templates participating in ordinary contract derivation.

16. Template-based polymorphic function specialization.

17. Static template validation where possible.

18. Runtime validation where static proof is unavailable.

19. Shared template metadata for collections whose elements have a common shape.

20. Compatibility with packed collection layout when all required representations are concrete.

21. Reflection over template structure.

22. Located diagnostics for invalid constructors, fixed values, dynamic field names, duplicate
fields, and malformed contracted holes.

23. Identical observable behavior with shared-template and packed-layout optimizations disabled.

The initial implementation may postpone:

* open structural templates;
* optional template fields;
* variable-length positional templates;
* repeated subpatterns;
* template unions;
* destructuring/binding values from matched holes;
* automatic construction from template holes;
* generalized pattern-matching syntax;
* compile-time layout optimization across arbitrary recursive templates.

These later features should preserve the fundamental model that:

```caret
template [...]
```

calls an ordinary function that constructs a contract from a concrete collection or from the
language-owned descriptor of an eligible collection-producing hole function.

---

### Design principle

A Caret template is an explicitly constructed structural contract.

For example:

```caret
Point =
  template [
    (Float) _
    (Float) _
  ]
```

means:

> `Point` is the contract for collections of exactly this shape, with two variable positions, each constrained by `Float`.

Within an eligible collection constructor:

```text
_                 unrestricted variable position
(Contract) _      constrained variable position
value             fixed-value requirement
collection        nested structural requirement
^name = ...       named structural member
```

Templates reuse ordinary:

* collection syntax;
* holes;
* contracts;
* fields;
* equality;
* polymorphic functions;
* reflection.

When many values or templates have the same shape, that shape may be stored once as shared collection metadata.

This lets templates serve simultaneously as structural types, reusable schemas, and compact shared metadata without introducing a separate object, record, tuple, or schema type system.

### Standard error template

Caret uses one structural information model for recoverable operation failures and aborting
diagnostics. The planned standard library defines an exact outer error shape equivalent to:

```caret
ErrorShape =
  template [
    ^code = (String) _
    ^phase = (String) _
    ^message = (String) _
    ^location = _
    ^related = (Collection) _
    ^cause = _
    ^details = (Collection) _
  ]

ErrorTemplate =
  contract [ErrorShape validErrorMembers]
```

The standard parameterized result contract has exactly three exported fields:

```caret
Result ValueContract =
  contract [
    (template [
      ^ok = (Boolean) _
      ^value = _
      ^error = _
    ])
    (validResult ValueContract)
  ]
```

A successful result is:

```caret
[
  ^ok = true
  ^value = value
  ^error = ~
]
```

A failed result is:

```caret
[
  ^ok = false
  ^value = ~
  ^error = error
]
```

`validResult` requires a successful `value` to satisfy `ValueContract` and requires a failed
`error` to satisfy `ErrorTemplate`. The `ok` discriminator is authoritative because `~` is a valid
successful value. Results do not flatten or propagate implicitly.

Every field is present. `location` and `cause` contain `~` when unavailable; a present cause must
itself satisfy `ErrorTemplate`. `related` is an empty collection when there are no related
locations. `validErrorMembers` supplies these recursive and source-location constraints until the
corresponding parameterized contracts can express them directly.

`code` is a stable machine-readable name. `phase` identifies the producing subsystem, `message` is
human-readable, and `location` is the primary source or representation location. `details` contains
domain-specific information. Formats, sandboxes, contracts, and other subsystems may define exact
templates for their `details` values, but they do not add fields to the outer error shape.

Expected failures of operations such as decoding or sandbox lifecycle calls return a failed
`Result` whose `error` satisfies `ErrorTemplate`. Lexer, parser, semantic, and aborting runtime
errors retain the same fields in their language-owned diagnostic descriptors and render them
consistently, but remain control-flow events rather than ordinary catchable return values.
Unexpected host exceptions and implementation faults must not be silently reclassified as expected
failures.

`ErrorTemplate` defines failure payloads, while `Result` defines the common enclosing protocol.
Public APIs that must return either success or failure still require a separately specified result
envelope; no union, exception-catching, or propagation syntax is implied here.

# Mutability Containers

## Overview

Caret values are immutable by default.

When a program needs a piece of state that changes over time, Caret uses an explicit **mutability container**.

A container has stable identity, but the value stored inside it may be replaced.

Example:

```caret
health = { (Int) 100 }
```

`health` is a container.

Its identity does not change, but its current content may change from:

```text
100
```

to:

```text
80
```

or another value satisfying the container's content contract.

This provides **contained mutability**:

```text
immutable structure
       ↓
stable container
       ↓
replaceable value
```

The surrounding object does not need to become mutable merely because one of its fields changes over time.

---

## Container literal

A mutability container is constructed using braces:

```caret
{ value }
```

Example:

```caret
health = { 100 }
```

The compiler may infer the content contract from the initial value.

An explicit content contract may be provided inside the braces:

```caret
health = { (Int) 100 }
```

The contract applies to the contents of the container, not to the container itself.

Therefore every future value placed into `health` must satisfy `Int`.

For example:

```caret
put health 75
```

is valid.

```caret
put health "dead"
```

is invalid.

Multiple contracts may be used normally:

```caret
health = { (Int nonnegative) 100 }
```

The initial value and every future value must satisfy all specified contracts.

---

## Container type

Conceptually:

```text
Container T
```

means:

> a stable container whose current content satisfies `T`.

For example:

```caret
health = { (Int) 100 }
```

has a contract conceptually equivalent to:

```text
Container Int
```

The exact parameterized contract syntax for referring directly to container types follows the normal Caret contract system.

---

## Containers are values

A container is itself an ordinary first-class Caret value.

It may be:

* assigned to bindings;
* stored in collections;
* stored in fields;
* passed to functions;
* returned from functions;
* shared between several immutable structures;
* reified;
* inspected through reflection where permitted.

Assigning a container does not copy its current content into a new container.

It copies or shares the reference to the same stable container identity.

For example:

```caret
health = { (Int) 100 }

player =
  ^health = health

healthBar =
  ^health = health

enemyAI =
  ^targetHealth = health
```

All three objects refer to the same container:

```text
health ───────────────┐
player.health ────────┼──> { 100 }
healthBar.health ─────┤
enemyAI.targetHealth ─┘
```

If:

```caret
put health 75
```

is executed, then:

```caret
player.health{}
healthBar.health{}
enemyAI.targetHealth{}
```

all observe:

```text
75
```

The enclosing `player`, `healthBar`, and `enemyAI` values remain immutable.

---

## Reading container contents

Reading the current content of a container is explicit.

The syntax is:

```caret
container{}
```

Example:

```caret
health{}
```

returns the current content of `health`.

If:

```caret
health = { (Int) 100 }
```

then:

```caret
health{}
```

initially evaluates to:

```text
100
```

Reading through a field works the same way:

```caret
player.health{}
```

The distinction is:

```caret
player.health
```

returns the container value.

```caret
player.health{}
```

returns the current value stored inside that container.

This distinction is intentional.

Caret does not implicitly dereference mutable containers during ordinary field access.

---

## Updating container contents

The standard operation for replacing container contents is:

```caret
put container value
```

Example:

```caret
put health 80
```

changes the current contents of `health` to `80`.

For a field:

```caret
put player.health 80
```

changes the contents of the container stored in `player.health`.

It does not replace the `health` field of `player`.

The immutable relationship:

```text
player.health -> container
```

remains unchanged.

Only:

```text
container current content
```

changes.

---

## Updating based on the previous value

The current value may be read, transformed, and written back:

```caret
damage player amount =
  put player.health
    (player.health{} - amount)
```

Conceptually:

```text
old = player.health{}
new = old - amount
put player.health new
```

Ordinary functions and partial application may be used normally.

Caret does not require special increment, decrement, or field-mutation syntax.

---

## Field reification

Ordinary field access and field reification are distinct.

```caret
player.health
```

evaluates the field and returns the value stored in that field.

If the field contains a container, this returns the container.

To reify the field itself:

```caret
player.@health
```

This produces a reference to the `health` field binding rather than evaluating it normally.

The general distinction is:

```text
object.field
    evaluate/access the field

object.@field
    reify/reference the field itself
```

This is independent of mutability.

For a container field:

```caret
player.health
```

is the container.

```caret
player.health{}
```

is the current content.

```caret
player.@health
```

is the reified field.

These are three distinct values and must not be conflated.

---

## `@` is not required for sharing containers

Because containers are ordinary first-class values, sharing a container does not require reference-assignment syntax.

For example:

```caret
health = { 100 }

player =
  ^health = health
```

is sufficient.

The field receives the container itself.

It is not necessary to write:

```caret
^health = @health
```

because `@health` would refer to the binding `health`, not to the container value stored in that binding.

Likewise, no special operator such as `@=` is required for container sharing.

Ordinary value assignment already has the intended semantics.

---

## Contained mutability

A mutable container does not make surrounding structures mutable.

Example:

```caret
player =
  [
    ^name "Alice"
    ^health { (Int) 100 }
    ^score { (Int) 0 }
  ]
```

The structure of `player` is immutable.

The following relationships remain fixed:

```text
player.name   -> "Alice"
player.health -> health container
player.score  -> score container
```

Only the contents of explicitly introduced containers may change.

This makes mutation boundaries visible in the value definition.

Compare:

```caret
^name "Alice"
```

with:

```caret
^health { (Int) 100 }
```

The first is immutable data.

The second explicitly introduces mutable state.

---

## No implicit deep mutability

A container makes only its own content replaceable.

It does not recursively make nested values mutable.

For example:

```caret
inventory =
  {
    (Collection Item)
    [sword potion]
  }
```

The container is mutable.

The collection:

```caret
[sword potion]
```

is still an ordinary immutable collection.

Adding an item means replacing the container contents with another collection:

```caret
put inventory
  (inventory{} add key)
```

Conceptually:

```text
same container identity

old content:
    [sword potion]

new content:
    [sword potion key]
```

The runtime may use structural sharing or other optimizations to avoid unnecessary copying.

---

## Nested mutability

Mutability may be introduced at any structural level.

For example:

```caret
player =
  [
    ^name "Alice"

    ^stats [
      ^health { (Int) 100 }
      ^strength 15
    ]
  ]
```

Here:

```caret
player.stats.health{}
```

reads mutable state.

But:

```caret
player.stats.strength
```

is immutable.

Putting a container around a larger structure has different semantics:

```caret
player =
  [
    ^stats {
      [
        ^health 100
        ^strength 15
      ]
    }
  ]
```

Here the entire `stats` value may be replaced:

```caret
put player.stats newStats
```

but its internal `health` and `strength` fields are not independently mutable unless they themselves contain containers.

---

## Mutable state and purity

Reading mutable state is observable.

Therefore:

```caret
health{}
```

must not be treated as an ordinary pure value lookup.

For example:

```caret
alive player =
  player.health{} > 0
```

can return different results at different times for the same immutable `player` value.

The function therefore depends on mutable state.

Similarly:

```caret
put player.health 50
```

changes mutable state.

Caret's effect system must distinguish computations that:

```text
do not observe mutable state
read mutable state
modify mutable state
```

The initial built-in effects are named `StateRead` and `StateWrite`.

Conceptually:

```text
container
    pure value access

container{}
    StateRead

put container value
    StateWrite
```

`StateWrite` grants neither an implicit read nor any authority by itself. Code that both reads and
writes declares or infers both effects, and the execution environment must separately supply access
to the particular container.

No mutable read or write may silently appear in a function that is required to be pure.

---

## Pure access to a container reference

Accessing the container itself does not read its mutable contents.

For example:

```caret
getHealthContainer player =
  player.health
```

may remain pure.

It returns the stable container reference.

By contrast:

```caret
getHealth player =
  player.health{}
```

observes mutable state.

This distinction allows immutable structures containing containers to be passed, compared by identity where appropriate, stored, and composed without automatically making every operation on those structures effectful.

---

## Contracts and `put`

A container's content contract is enforced whenever its contents change.

For:

```caret
health = { (Int nonnegative) 100 }
```

the following is valid:

```caret
put health 50
```

while:

```caret
put health -1
```

violates the container contract.

The compiler should reject invalid writes statically where possible.

If the new value cannot be proven statically, the ordinary runtime contract mechanism applies.

A container must never silently transition into a value that violates its declared content contract.
Validation happens before replacement. A failed dynamic check produces the ordinary located
contract-violation diagnostic and leaves the previous content unchanged.

After a successful replacement, `put` returns the newly stored value. This makes `put` usable as an
ordinary expression without introducing a second assignment-like result convention.

---

## Contract widening and inference

When no explicit contract is provided:

```caret
value = { 100 }
```

the compiler may infer a content contract from the initial value.

If broader future contents are intended, the broader contract should be explicit:

```caret
value = { (Number) 100 }
```

This permits:

```caret
put value 3.14
```

provided `Float` satisfies `Number`.

The inferred or explicit content contract is part of the container's stable metadata.

It must not change merely because different values are later stored.

---

## Container identity

A container has stable identity independent of its current content.

Therefore two containers:

```caret
a = { 100 }
b = { 100 }
```

have equal current contents but are distinct containers.

Updating:

```caret
put a 50
```

does not affect `b`.

By contrast:

```caret
a = { 100 }
b = a
```

makes `a` and `b` refer to the same container.

Then:

```caret
put a 50
```

causes:

```caret
b{}
```

to return `50`.

Container identity is therefore a meaningful runtime property. Ordinary `==` compares containers
by this identity: aliases of the same container compare equal, while independently constructed
containers compare unequal even when their current contents are equal. Comparing current contents
requires explicit reads, such as `a{} == b{}`, and therefore has `StateRead` effects.

Container identity is local to the current runtime environment generation. Persistence or identity
across unloading, reloading, or process restarts requires a separately specified facility.

---

## Containers in collections

Containers may appear inside ordinary collections:

```caret
values =
  [
    { (Int) 10 }
    { (Int) 20 }
    { (Int) 30 }
  ]
```

The collection itself remains immutable unless it is placed inside another container.

Individual cells may still change:

```caret
put values[1] 25
```

provided indexing returns the container at that position.

This does not replace the collection element structurally.

It changes the contents of the stable container referenced by that element.

---

## Containers and shared metadata

A collection of containers may share common metadata.

For example:

```caret
(Collection (Container Int)) counters =
  [
    { (Int) 0 }
    { (Int) 0 }
    { (Int) 0 }
  ]
```

may store:

```text
element metadata:
    Container Int
```

once at the collection level.

The individual elements need only preserve their separate container identities and current contents.

This follows Caret's normal collection metadata rules.

---

## Containers and templates

Templates may require container-valued positions.

For example, conceptually:

```caret
Player =
  template [
    ^name (String) _
    ^health (Container Int) _
  ]
```

A matching value may be:

```caret
[
  ^name "Alice"
  ^health { (Int) 100 }
]
```

The template constrains the field to contain a container whose content contract satisfies `Int`.

The template does not automatically dereference the container.

If a template needs to constrain the current mutable content rather than the container type, that requires an explicit predicate that performs a mutable-state read and therefore participates in the effect system.

Pure structural template matching should not silently read mutable container contents.

---

## Containers and `ruleCycle`

Containers provide a natural representation for mutable object state inside `ruleCycle`.

Example:

```caret
player =
  [
    ^name "Alice"
    ^health { (Int nonnegative) 100 }
    ^score { (Int) 0 }
  ]
```

A rule may change the health:

```caret
damage = rule
  T hit player
  E
    put player.health
      (player.health{} - hit.damage)
```

The surrounding `player` value remains immutable.

The rule changes only the explicitly mutable health container.

Containers complement rather than replace the persistent previous/next-state model of `cycle` and
`ruleCycle`. Exported next-state writes still form one atomic state transition. Container writes are
separate observable effects on explicitly shared identities and are not silently rolled into or
rolled back with that persistent-state commit.

---

## Reactive dependency tracking

A mutable-state read gives the `ruleCycle` a precise dependency.

For example:

```caret
death = rule
  T player.health{} <= 0
  E destroy player
```

depends on:

```caret
player.health
```

When:

```caret
put player.health newHealth
```

changes that container, the rule engine knows that `death` may need reevaluation.

An unrelated container change does not require reevaluating triggers that do not depend on it.

Containers therefore provide a natural unit of reactive dependency tracking.

Conceptually:

```text
container read
    ↓
dependency recorded

put to container
    ↓
dependent rules become candidates for reevaluation
```

This enables efficient rule-cycle execution without treating arbitrary object memory as mutable.
A successful `put` records the changed container and queues rules whose last relevant evaluation
read that identity. It does not require reevaluating rules dependent only on other containers.

---

## Context changes derived from containers

A rule cycle may derive contexts from mutable container values.

For example:

```text
player.health{} > 0
    -> player alive context up

player.health{} <= 0
    -> player alive context down
```

Then:

```caret
put player.health 0
```

may cause:

```text
player.alive:
    up -> down
```

which produces:

```caret
fall player.alive
```

and may trigger another rule.

The exact derived-context declaration mechanism is specified separately, but container updates are a primary source of observable state change inside rule cycles.

---

## Containers and sandboxes

A container passed into a sandbox is a capability to observe and potentially modify shared mutable state.

The sandbox boundary must therefore preserve access restrictions.

A host may choose to expose:

* the real container;
* a read-only projection;
* a mediated container;
* a copied snapshot;
* a virtual replacement.

For example, a sandbox may be allowed to read:

```caret
settings{}
```

without being given a `put` capability that can modify the host's real settings.

The exact capability interface may be represented through contracts and sandbox projections.

Reflection must not allow sandboxed code to obtain unrestricted mutable access merely because it can inspect a container reference.
Reflective metadata and field reification do not add `StateRead` or `StateWrite` authority. A
sandbox projection may expose identity and metadata, readable contents, writable contents, or a
snapshot only as explicitly selected by its environment.

---

## Containers and concurrency

Containers introduce shared mutable state and therefore require defined concurrency semantics if multiple computations may access them concurrently.

The initial language model does not require a particular concurrency mechanism.

A future implementation may provide:

* atomic containers;
* synchronization contracts;
* transactional updates;
* isolated actor ownership;
* thread-local containers;
* other concurrency policies.

Ordinary containers should not silently promise atomic multi-threaded mutation unless explicitly specified.

The core semantics only require stable identity and sequentially observable replacement of the contained value.

---

## Implementation freedom

The semantic model is:

```text
stable container identity
        +
replaceable contained value
```

The implementation is free to represent this using:

* a mutable machine-memory slot;
* an object containing a pointer;
* an atomic reference;
* an indirection table;
* runtime-managed state storage;
* another equivalent mechanism.

Replacing a contained immutable value does not require copying that value's entire object graph.

Persistent collections, structural sharing, ownership analysis, and uniqueness analysis may be used freely.

These optimizations must not change observable container identity or content semantics.

---

# Implementation requirements

The initial implementation should support at minimum:

1. Container literals:

```caret
{ value }
```

2. Explicit content contracts:

```caret
{ (Int) 100 }
```

3. Contract inference when no explicit content contract is given.

4. Stable container identity.

5. Explicit content reads:

```caret
container{}
```

6. Container updates using:

```caret
put container value
```

7. Contract validation on every `put`.

   Validation precedes replacement; failure leaves the old content unchanged, and success returns
   the newly stored value.

8. Containers stored in immutable fields and collections.

9. Sharing one container between multiple immutable structures.

10. No implicit deep mutability.

11. Explicit distinction between:

```caret
player.health
player.health{}
player.@health
```

12. `object.@field` as field reification.

13. `StateRead` effect inference for mutable-state reads.

14. `StateWrite` effect inference for mutable-state writes.

15. Purity rejection when mutable reads or writes occur in a pure function.

16. Container dependency tracking usable by `ruleCycle`.

17. Runtime reevaluation of dependent rules after `put`.

18. Reflection over containers subject to normal visibility and sandbox restrictions.

The initial implementation may postpone:

* atomic updates;
* transactions;
* concurrency-specific containers;
* revocable mutable capabilities;
* read-only container projections;
* lock-free containers;
* compare-and-swap operations;
* mutable slices;
* distributed shared containers;
* persistence of container identity across process restarts.

These later features must preserve the core contained-mutability model.

---

# Design principle

Caret uses explicit containers to isolate mutable state inside otherwise immutable structures.

The core syntax is:

```caret
health = { (Int) 100 }

health{}          // read current content

put health 80     // replace current content
```

Containers may be shared normally:

```caret
player =
  ^health = health

healthBar =
  ^health = health
```

No special reference-assignment syntax is required.

Field access remains precise:

```caret
player.health
```

returns the stable container.

```caret
player.health{}
```

reads its current mutable content.

```caret
player.@health
```

reifies the field itself.

The central rule is:

> Structures remain immutable unless mutability is explicitly introduced with `{ ... }`. The container's identity remains stable; only its contained value may be replaced.

This keeps mutation local, visible, shareable, compatible with Caret's effect system, and naturally observable by reactive systems such as `ruleCycle`.

# `with`, `outer`, and Low-Precedence Application

## Overview

Caret provides two related mechanisms for reducing syntactic noise:

* `with` temporarily exposes the named members of a value directly in lexical lookup;
* `$` provides low-precedence function application, reducing the need for parentheses.

These features do not introduce new object or record types.

A value used with `with` may simply be a heterogeneous collection containing named fields.

For example:

```caret
number = 11

record =
  [
    ^name "one"
    ^number 10
    ^content [1 2 3]
  ]

with record
  print name
  print number
  print outer.number
  map print content
```

Inside the `with` block:

```text
name
number
content
```

refer directly to exported named members of `record`.

`outer.number` refers to the `number` binding from the enclosing scope.

---

# `with`

## Basic syntax

The general form is:

```caret
with value
  body
```

The body is determined by effective logical indentation. The planned `\\` and `\*` layout markers
may shift its physical baseline but do not change `with` name resolution or block semantics.

`with` and `outer` are reserved spellings and cannot be declared as bindings or parameters.

Example:

```caret
with player
  print name
  print health{}
```

The expression supplied to `with` is evaluated once.

Its accessible named members participate directly in name resolution throughout the body.
The target must expose a public named-member interface; otherwise evaluation produces a located
diagnostic. Statically known members should resolve during semantic analysis. When the target's
shape is dynamic, member selection is checked at runtime without weakening ordinary lexical or
visibility rules.

---

## Named members

`with` operates on values that expose named members.

For example:

```caret
person =
  [
    ^name "Alice"
    ^age 42
  ]

with person
  print name
  print age
```

There is no separate `Record` type required.

A heterogeneous collection with named fields is sufficient.

Likewise, `with` may operate on:

* returned scopes;
* rulesets;
* structured collections;
* `@root`;
* sandbox roots;
* other values exposing named members.

Example:

```caret
with @root
  print code
```

subject to normal visibility and sandbox restrictions.

For `@root` and other metadata-only references, `with` exposes only the names already present on
the reference's public metadata interface. It does not turn metadata into binding authority or a
capability invocation path.

---

## Export visibility

Only members visible through the value's normal public interface participate in `with`.

For a scope or ruleset:

```caret
system =
  ruleset
    privateState = 10
    ^publicState = 20
```

then:

```caret
with system
  print publicState
```

is valid.

The private binding:

```caret
privateState
```

does not become visible merely because `with` is used.

`with` must preserve normal Caret visibility rules.

---

# Name resolution

## Lookup order

Inside a `with` block, unqualified names are resolved in the following order:

```text
1. local bindings declared in the current lexical block
2. named members exposed by the current `with` value
3. enclosing lexical scopes
```

For example:

```caret
number = 11

record =
  [
    ^number 10
  ]

with record
  print number
```

prints:

```text
10
```

because the exposed `record.number` shadows the enclosing `number`.

---

## Local bindings inside `with`

A local binding declared inside the block has higher precedence than a member supplied by `with`.
As in ordinary blocks, declarations are resolved for the whole block: reading such a local before
its initialization reports `READ_BEFORE_INITIALIZATION` rather than falling back to a same-named
`with` member.

Example:

```caret
number = 11

record =
  [
    ^number 10
  ]

with record
  number = 20

  print number
  print outer.number
```

Here:

```caret
number
```

refers to the local value `20`.

`outer.number` refers to the enclosing lexical environment.

If direct access to the original structured value remains available, its member can still be accessed explicitly:

```caret
record.number
```

---

# `outer`

## Enclosing scope access

Inside a `with` block, `outer` refers to the immediately enclosing lexical environment.

Example:

```caret
number = 11

record =
  [
    ^number 10
  ]

with record
  print number
  print outer.number
```

produces:

```text
10
11
```

`outer` is a reserved, resolver-owned lexical path used for explicit lookup. It does not
materialize the enclosing environment as a first-class scope: bare `outer` cannot be stored,
passed, called, dynamically indexed, or reflected. Only member traversal such as `outer.name` and
`outer.outer.name` is valid. This prevents `with` from exposing private lexical bindings through
reflection or dynamic lookup.

---

## Nested `with`

`with` blocks may be nested.

Example:

```caret
x = 1

a =
  [
    ^x 2
  ]

b =
  [
    ^x 3
  ]

with a
  with b
    print x
    print outer.x
    print outer.outer.x
```

produces:

```text
3
2
1
```

The scope chain is conceptually:

```text
inner with b
    ↓ outer
with a
    ↓ outer
enclosing lexical scope
```

---

## `outer.outer`

`outer` may be followed repeatedly:

```caret
outer.outer.name
outer.outer.outer.value
```

Each `outer` moves one level outward in the lexical scope chain.

Each step crosses one enclosing `with` lookup layer. After the outermost `with`, lookup continues
in the ordinary enclosing lexical scope. Normal initialization and visibility rules still apply;
`outer` grants no authority and cannot bypass module, export, root, or sandbox boundaries.

This provides explicit access to shadowed bindings without introducing multi-object `with` syntax.

Caret should prefer nested `with` blocks over constructs such as:

```caret
with a b c
```

because nesting makes lookup precedence visible and deterministic.

---

# `with` does not copy fields

`with` changes name resolution only.

It does not destructure or copy the value.

For:

```caret
with player
  print health{}
```

the name:

```caret
health
```

refers to the actual exported member of `player`.

This matters for containers.

Example:

```caret
player =
  [
    ^health { (Int) 100 }
  ]

with player
  put health 75
```

changes the same container accessible as:

```caret
player.health
```

Afterward:

```caret
player.health{}
```

returns:

```text
75
```

No local copy of the container was created.

---

# Field reification inside `with`

Normal reification rules apply to names introduced through `with`.

Outside:

```caret
player.@health
```

reifies the `health` field of `player`.

Inside:

```caret
with player
  @health
```

reifies the same field.

Conceptually:

```caret
player.@health
```

and:

```caret
with player
  @health
```

refer to the same binding.

This preserves the normal meaning of `@`:

> reify the binding resolved at this position.

---

# `with` as an expression

`with` is an expression.

Its result is the result of its body according to normal Caret block semantics.

Example:

```caret
distanceSquared point =
  with point
    x * x + y * y
```

or:

```caret
fullName person =
  with person
    firstName + " " + lastName
```

The `with` block does not require an explicit `return`.

---

# `with` and contained mutability

`with` is particularly useful with immutable structures containing mutable containers.

Example:

```caret
damage player amount =
  with player
    put health $ health{} - amount
```

Here:

```caret
health
```

is the container stored in `player.health`.

```caret
health{}
```

reads its mutable content.

```caret
put health ...
```

changes its content.

The surrounding `player` value remains immutable.

---

# `$`

## Overview

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

## Basic semantics

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

## `$` is syntax-level application

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

# Right associativity

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

# Low precedence

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

# `$` and ordinary application

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

# `$` and lambdas

`$` should bind more weakly than lambda construction.

Therefore:

```caret
map values $ x -> x * 2
```

means:

```caret
map values (x -> x * 2)
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

# `$` and conditionals

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

# `$` and composition

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
map values $ normalize >> validate
```

passes the composed function:

```caret
normalize >> validate
```

as the argument.

---

# `$` and `with`

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

# Suggested precedence

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

# Implementation requirements

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

9. `with` working with returned scopes and rulesets.

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

# Design principle

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

# Compile-Time Execution and Separate Compilation

## Overview

Caret may compile different runtime artifacts from different source roots.

For example:

```text
client.caret
server.caret
```

may be compiled independently:

```text
client.caret -> client artifact
server.caret -> server artifact
```

Each root determines its own reachable program.

The roots may import common Caret source and use compile-time computation to select different parts of that source before runtime code is generated.

Caret does not require a separate target-description language for this.

Instead, it provides compile-time execution through `#`.

The same ordinary Caret operations used at runtime may therefore also be used to:

* import modules;
* inspect code;
* filter rulesets;
* transform collections;
* generate lookup tables;
* derive configuration;
* construct formats or templates;
* generate or select program structure.

The fundamental model is:

```text
Caret source
    ↓
compile-time Caret computation
    ↓
resulting runtime program
    ↓
backend compilation
    ↓
artifact
```

Separate compilation is consequently based on ordinary source roots and ordinary Caret metaprogramming rather than on conditional-preprocessor syntax.

---

# Compile-time execution

## `#`

`#` moves the construct it prefixes into the compile-time execution stage.

For an expression:

```caret
value = # expression
```

`expression` is evaluated during compilation.

Its result becomes the value used by the runtime program.

For a binding:

```caret
# value = expression
```

the binding itself belongs to the compile-time environment.

It may be used by subsequent compile-time computation but is not itself a runtime binding.

This distinction is fundamental.

---

## Compile-time bindings

A compile-time binding is written:

```caret
# name = expression
```

Example:

```caret
# size = calculateSize configuration
```

Both the initializer and the resulting binding exist at compile time.

A later compile-time computation may use it:

```caret
table = # buildTable size
```

Conceptually:

```text
compile time:

    size = calculateSize configuration
    generatedTable = buildTable size

runtime:

    table = generatedTable
```

`size` need not exist in the runtime artifact.

---

## Compile-time expression values

When `#` applies to an initializer expression rather than to the binding:

```caret
table = # buildTable size
```

the computation occurs at compile time, but `table` is an ordinary runtime/program binding.

The result must therefore be representable in the resulting program.

For example:

```caret
squares =
  # range 100 map $
    x -> x * x
```

may calculate the collection during compilation and embed the resulting immutable value.

The distinction is:

```caret
# value = expression
```

means:

> `value` exists at compile time.

while:

```caret
value = # expression
```

means:

> evaluate `expression` at compile time and make its result part of the resulting program.

---

## Compile-time dependency rule

A compile-time computation may depend only on values available at compile time.

For example:

```caret
# size = 100

table =
  # buildTable size
```

is valid.

By contrast:

```caret
input = readInput

table =
  # buildTable input
```

is invalid when `input` is produced only at runtime.

The compiler should report a dependency diagnostic conceptually equivalent to:

```text
compile-time expression depends on runtime binding `input`
```

Compile-time availability propagates through compile-time bindings.

For example:

```caret
# configuration = loadConfiguration
# size = configuration.tableSize
# source = generateValues size
```

is valid when every dependency is itself available during compilation.

---

## Ordinary functions at compile time

Caret does not require separate compile-time function declarations.

An ordinary function may execute at compile time when:

* the function itself is available;
* all required inputs are available;
* its effects are permitted in the compile-time environment.

For example:

```caret
square x =
  x * x
```

may be used normally:

```caret
result = square input
```

or during compilation:

```caret
table =
  # range 256 map square
```

The function has one definition.

`#` determines the execution stage of the invocation.

This avoids a separate macro or compile-time function language.

---

# Compile-time imports

## Importing for metaprogramming

A module used for compile-time inspection or transformation should normally be bound at compile time:

```caret
# shared = import clientServer
```

This means:

1. `clientServer` is resolved through the compile-time environment's visible module catalog;
2. the resolved source module is loaded and evaluated in that environment;
3. its exported module value is bound to `shared`;
4. `shared` may be inspected and transformed by later compile-time expressions; and
5. the binding `shared` is not automatically included as a runtime module.

This differs from:

```caret
shared = import "client-server.caret"
```

which is an ordinary runtime/program import according to the normal module semantics.

`import` itself does not require separate compile-time syntax.

Its stage follows the surrounding Caret execution stage.

Both path and ModuleId overloads are available at either stage. Module-ID lookup is already known
from catalog construction and does not itself evaluate the module or make it runtime-reachable.

---

## Compile-time import does not imply runtime inclusion

Given:

```caret
# shared = import clientServer
```

the complete imported module is available to compile-time Caret code.

This does not mean that the complete imported module must be emitted into the runtime artifact.

Only program elements that survive compile-time transformation and are reachable from the resulting runtime root need to be emitted.

Conceptually:

```text
client-server.caret
        ↓
# import
        ↓
complete compile-time module
        ↓
compile-time transformation
        ↓
selected runtime program
        ↓
reachability analysis
        ↓
artifact
```

Compile-time availability and runtime inclusion are separate concepts.

Compile-time imports use the same module lookup, export visibility, initialization, and
per-environment caching rules as ordinary imports. Logical lookup identity and evaluation
identity remain distinct: a ModuleId resolves through the visible catalog, while the resulting
canonical source path keys evaluation and cycle detection. Reification may expose the complete
semantic code permitted for a visible module, but it does not turn private bindings into accessible
values or capabilities. A compiler must track every imported module and external input used by
staging as a semantic build dependency even when none of that module is emitted at runtime.

---

# Compile-time metaprogramming

## Ordinary values and code values

Compile-time Caret may operate on ordinary values:

```caret
table =
  # range 1000 map calculate
```

and on program structures:

```caret
# library = import "library.caret"
```

Modules, rulesets, code descriptors, templates, formats, contracts, and other reifiable language values may therefore participate in compile-time computation where their contracts permit it.

Combined with Caret reflection, `#` forms the basis of metaprogramming.

Conceptually:

```text
@
    reifies program entities and exposes semantic structure

#
    executes Caret computation while the program is being compiled
```

No textual macro substitution mechanism is required for ordinary structural metaprogramming.

---

## Compile-time transformation uses ordinary functions

Caret should prefer ordinary collection and higher-order functions for compile-time program transformation.

For example:

```caret
# shared = import "module.caret"

selected =
  # shared.rules filter $
    rule -> someCondition rule
```

`filter` is the ordinary Caret filtering operation.

It is not a compiler-specific filtering syntax.

The operation happens at compile time because its enclosing expression is prefixed by `#`.

The same `filter` may be used on runtime collections without `#`.

---

# Separate compilation roots

## Roots define artifacts

Separate artifacts may be compiled from separate root source files.

For example:

```text
client.caret
server.caret
```

may each be passed independently to the compiler.

Conceptually:

```text
compile client.caret
    -> client artifact

compile server.caret
    -> server artifact
```

Each source file is the root of its own compilation reachability graph.

For each invocation, catalog discovery begins below the directory containing that root file. Two
roots in the same directory therefore normally discover the same project IDs; roots compiled from
different directory trees may have different visible project catalogs. Environment-supplied IDs,
including the normal standard library, are then combined with that root's discovered project IDs.

Caret does not require both targets to be declared inside one special project-level source construct.

A build system may invoke the Caret compiler once per root.

---

## Shared source

Different roots may use the same source module:

```text
                  client-server.caret
                    /             \
                   /               \
          client.caret           server.caret
              |                       |
              v                       v
       client artifact          server artifact
```

The shared module may describe a larger logical system than either target individually needs.

Each compilation root may use compile-time computation to derive the portion relevant to that target.

This permits common definitions to remain in one source while producing separate deployment artifacts.

---

## Reachability

After compile-time execution is complete, the compiler performs normal program reachability analysis from the resulting runtime root.

Definitions reachable only from discarded compile-time structures do not belong to the runtime artifact.

For example, if a selected client rule requires:

```text
LoginMessage
LoginFormat
encode
validateName
```

those definitions remain reachable and are included as necessary.

A server-only rule and helpers used exclusively by that rule may be absent from the client artifact.

This is a semantic consequence of the resulting compiled program, not merely an optional size optimization.

The compiler must not require unreachable imported definitions to remain in an artifact solely because they were inspected during compile-time execution.

---

# Example: shared client/server rules

## Shared interaction module

A shared source file may define both sides of an interaction.

For example, `client-server.caret`:

```caret
clientServer = module

^client = context
^server = context

^interaction =
  ruleset
    sendLogin = rule
      C client
      T loginRequested
      E
        request = makeLoginRequest
        send server $ encode LoginRequest request

    authenticate = rule
      C server
      T loginReceived
      E
        result = authenticateRequest
        send client result

    showLoginResult = rule
      C client
      T loginResultReceived
      E
        showResult
```

The shared ruleset describes both client-side and server-side behavior.

The contexts:

```caret
client
server
```

are ordinary context values exported by the shared module.

They are not strings or compiler keywords.

---

## Client compilation root

`client.caret` may import the shared module at compile time:

```caret
# shared = import clientServer
```

and construct the runtime client ruleset by filtering the shared interaction:

```caret
clientRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.client
```

The resulting runtime program may then install those rules:

```caret
clientApp =
  ruleCycle
    init
      install clientRules
```

The binding:

```caret
shared
```

exists only during compilation.

Here `clientServer` is the shared file's stable ModuleId, `shared` is the client root's local
compile-time binding containing the imported module value, and `shared.client` is an exported
context value from that module. These are three different semantic entities. The import remains
valid if `client-server.caret` is moved to any other location below the directory used for this
compilation root's catalog discovery, provided its `clientServer = module` declaration remains.

The runtime artifact receives `clientRules` and whatever dependencies are reachable through them.

---

## Server compilation root

`server.caret` performs the corresponding selection:

```caret
# shared = import clientServer

serverRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.server

serverApp =
  ruleCycle
    init
      install serverRules
```

Both compilation roots evaluate the same logical shared source.

Each creates a different runtime ruleset.

---

## Resulting artifacts

Conceptually, the shared source contains:

```text
sendLogin
authenticate
showLoginResult
shared message definitions
shared formats
shared helper functions
client-only dependencies
server-only dependencies
```

The client compilation produces approximately:

```text
sendLogin
showLoginResult
required shared definitions
required client dependencies
client root
```

The server compilation produces approximately:

```text
authenticate
required shared definitions
required server dependencies
server root
```

A helper used by both sides may be included in both artifacts.

A helper used only by server rules need not appear in the client artifact.

The programmer specifies the semantic selection.

Normal compiler reachability determines the required dependency closure.

---

# Context filtering

## Context values rather than names

Compile-time rule filtering should normally compare or inspect actual context values rather than their textual names.

Prefer:

```caret
rule.context contains shared.client
```

over:

```caret
rule.context contains "client"
```

when `shared.client` is the context being selected.

The first form refers to the actual exported context value.

It therefore participates in normal Caret identity, reflection, renaming, and static analysis.

Strings remain appropriate only when an API intentionally operates on names.

---

## Complex context expressions

A rule context may contain combinations such as:

```caret
C client and authenticated
```

or:

```caret
C client or server
```

The filtering predicate may use ordinary context-inspection functions to determine whether a rule is relevant.

The simple example:

```caret
rule.context contains shared.client
```

is sufficient when structural containment expresses the desired criterion.

More sophisticated selection may use ordinary predicates such as:

```caret
contextCompatible rule.context targetContext
```

without changing the compile-time mechanism.

For example:

```caret
clientRules =
  # shared.interaction filter $
    rule ->
      contextCompatible rule.context shared.client
```

Context compatibility policy belongs to context/ruleset functions, not to `#`.

---

# Filtering and dependency closure

## `filter` selects rules

When filtering a ruleset:

```caret
selected =
  # rules filter predicate
```

`filter` determines which rules are present in the resulting ruleset.

It does not need to manually enumerate every function, contract, format, or helper referenced by those rules.

For example, if a selected rule calls:

```caret
encode LoginRequest request
```

the selected rule retains its semantic references to:

```text
encode
LoginRequest
```

Normal reachability analysis keeps those required definitions.

The programmer should therefore specify:

```text
which rules belong to the resulting ruleset
```

rather than:

```text
every source declaration that must appear in the artifact
```

---

## Unselected rules

A rule removed by compile-time filtering is not part of the resulting runtime ruleset.

If no remaining runtime definition depends on it, it is unreachable and need not be emitted.

Dependencies used only by that rule likewise need not be emitted.

Thus:

```caret
# shared = import clientServer

clientRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.client
```

does not imply that the client artifact contains `shared.interaction` in its original complete form.

Only the resulting `clientRules` value and runtime-reachable dependencies matter.

---

# Generality

The mechanism is not specific to client/server programs.

Different compilation roots may filter or transform shared code using any compile-time criterion expressible in Caret.

Examples may include:

```text
desktop / browser
CPU / GPU
editor / runtime
production / test
different embedded devices
different protocol roles
different application editions
different rule-system participants
```

For example:

```caret
# shared = import "platform-rules.caret"

browserRules =
  # shared.rules filter $
    rule ->
      rule.context contains shared.browser
```

The compiler does not need a built-in concept of `browser`, `client`, `server`, or `agent`.

These are ordinary program values interpreted by compile-time Caret code.

---

# Compile-time effects and authority

Compile-time execution remains subject to Caret's normal effect and capability principles.

`#` does not grant authority.

The compile-time environment is an ordinary explicit Caret execution environment. It may be more
restricted than the eventual runtime environment, and neither reflection nor staging may recover a
host root or capability omitted from it. Effect declarations remain descriptions rather than
authority grants at both stages.

For example, a compile-time operation that reads source files requires the corresponding capability in the compilation environment.

The compilation environment may expose facilities such as:

```text
module loading
source access
compiler metadata
target information
environment configuration
```

while omitting unrelated runtime capabilities.

Effects used during compile-time execution occur during compilation, not in the resulting runtime artifact.

A function executed through `#` retains its ordinary effect contract and must be permitted by the compile-time environment.

The exact standard compiler environment may be specified separately.

---

# Stage boundaries

## Values crossing into runtime

A value produced at compile time may enter the runtime program only when it has a valid runtime representation.

For example:

```caret
table =
  # buildTable configuration
```

may embed an immutable collection.

Likewise:

```caret
clientRules =
  # shared.interaction filter predicate
```

may produce executable ruleset structure that the compiler incorporates into the resulting program.

Compile-time-only capabilities, compiler handles, source-loader objects, and other values with no runtime representation must not cross the stage boundary accidentally.

Crossing the boundary has three distinct outcomes: an immutable representable value may be embedded;
a reifiable executable/code value may retain semantic references whose runtime dependency closure is
emitted; and a compiler-only or capability-bearing value without a portable runtime representation
is rejected. Backend serialization details do not define this language-level distinction.

The compiler should issue a located diagnostic when a compile-time-only value is required directly at runtime.

---

## Compile-time bindings remain compile-time

A binding declared:

```caret
# shared = import clientServer
```

does not itself become part of the runtime program merely because later code uses it during compilation.

This permits large modules and compiler-side structures to be inspected without forcing them into the emitted artifact.

The resulting runtime program contains only values deliberately crossing the stage boundary and their runtime-reachable dependencies.

---

# Parsing and extent of `#`

`#` is a compile-time remainder marker, not an ordinary unary operator. Operator precedence does
not determine its operand. In expression position it moves everything after it, through the end of
the current syntactic expression boundary, into compile-time execution. No later operator switches
execution back to runtime.

When applied to a binding:

```caret
# name = expression
```

it stages the complete binding.

When applied to an expression:

```caret
name = # expression
```

it stages the complete remainder of the initializer.

The part before `#` remains in its existing stage and consumes the already-computed result of the
staged suffix. For example:

```caret
result = runtimeFunction # calculate configuration
sum = runtimeValue + # calculateConstant input * scale
```

conceptually stage `calculate configuration` and `calculateConstant input * scale`, then supply
their results to the preceding runtime call and addition respectively.

For example:

```caret
clientRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.client
```

means:

```text
evaluate during compilation:

    shared.interaction filter $
      rule ->
        rule.context contains shared.client
```

and use the resulting ruleset as the value of the ordinary `clientRules` binding.

`#` must not stage only the immediately following function name, atom, application, or
higher-precedence subexpression. Whitespace application, postfix operations, infix operators,
conditionals, composition, `$`, and lambdas appearing later in the region all execute at compile
time.

The region ends at the nearest enclosing syntactic expression boundary: the end of the statement,
a closing parenthesis, or the end of an explicitly delimited nested expression such as a collection
element. Parentheses therefore provide a smaller boundary when required.

For example:

```caret
result =
  combine
    (# calculateConstant configuration)
    runtimeValue
```

For a conditional split across stages:

```caret
result = runtimeCondition & # yes ! no
```

both branch values belong to the staged suffix. They are computed at compile time and embedded; the
runtime condition selects between those results. When the condition itself is inside a staged region,
ordinary lazy conditional evaluation still selects only one branch during compilation.

A nested `#` inside a compile-time region is valid but redundant. A staged suffix may not depend on
a runtime-only value, including an earlier runtime subexpression or a runtime invocation parameter.
The parser represents the complete region explicitly and preserves a span from `#` through its
boundary; semantic analysis assigns stages and diagnoses invalid cross-stage dependencies.

---

# Relationship to `@`

`@` and `#` have complementary roles.

`@` reifies a binding or program entity:

```caret
@function
@root.code
player.@health
```

`#` controls execution stage:

```caret
# loadedModule = import "module.caret"

generated =
  # transform code
```

Conceptually:

```text
@
    expose semantic program structure as values

#
    execute Caret computation during compilation
```

Together they provide structural metaprogramming without requiring textual macros.

Neither operator replaces the other.

---

# Implementation requirements

The initial compile-time and separate-compilation implementation should support at minimum:

1. Compile-time bindings:

```caret
# value = expression
```

2. Compile-time initializer expressions:

```caret
value = # expression
```

3. Compile-time bindings available to later compile-time expressions.

4. Diagnostics when compile-time computation depends on runtime-only values.

5. Ordinary pure functions executable at compile time.

6. Effectful compile-time functions when their effects are permitted by the compilation environment.

7. Compile-time imports:

```caret
# shared = import clientServer
```

8. Compile-time path imports and ModuleId imports resolved through the compile-time environment's
visible catalog.

9. Compile-time imported modules that are not automatically emitted into the runtime artifact.

10. Compile-time transformation of ordinary Caret values.

11. Compile-time transformation of rulesets and other reifiable program structures.

12. Ordinary higher-order collection functions such as `filter` usable during compilation.

13. Values produced by compile-time expressions incorporated into the resulting program when they have valid runtime representation.

14. Separate source roots compiled independently into separate artifacts.

15. Different roots importing the same shared module by stable ModuleId at compile time.

16. Different roots producing different runtime rulesets from that shared module.

17. Normal reachability analysis after compile-time transformation.

18. Unreachable unselected rules omitted from the resulting artifact.

19. Dependencies required by selected rules retained automatically.

20. Shared dependencies permitted to appear in several independently compiled artifacts.

21. Context values usable as compile-time filtering criteria.

22. Compile-time authority and module-catalog visibility remaining subject to the compilation
environment's normal effect, capability, reflection, and sandbox restrictions.

The initial implementation may postpone:

* arbitrary syntax-generating macros;
* source-text macros;
* cross-target whole-program optimization;
* automatic coordination of several compiler invocations;
* distributed deployment;
* automatic protocol-version negotiation;
* target-specific package management;
* compile-time network access;
* incremental metaprogram cache invalidation;
* sophisticated static proof of arbitrary context predicates.

These later facilities must preserve the separation between:

```text
compile-time program values

runtime program values

source roots

runtime reachability

backend artifacts
```

---

# Design principle

Caret does not require a dedicated multi-target build language.

A compilation target begins with an ordinary Caret source root.

Different roots may inspect and transform the same shared source at compile time:

```caret
# shared = import clientServer
```

and derive different runtime values:

```caret
clientRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.client
```

or:

```caret
serverRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.server
```

`#` means that ordinary Caret computation happens while the program is being compiled.

Compile-time bindings remain outside the runtime artifact unless a resulting value deliberately crosses into runtime.

After compile-time transformation, ordinary dependency reachability determines what code is required.

The resulting model is:

```text
shared Caret source
        ↓
compile-time import
        ↓
ordinary Caret transformation
        ↓
target-specific runtime program
        ↓
normal reachability
        ↓
backend compilation
        ↓
artifact
```

Client/server separation is one application of this mechanism, not a special language feature.


### Compiler target and compatibility

The first compiler backend targets Java 21-compatible JVM class files. It can package a program and
its Caret modules as a runnable or library JAR while using a versioned Caret runtime ABI.

The Java tree-walking interpreter remains the reference implementation until differential tests
establish parity. Interpreted and compiled execution must agree on:

* values and structural equality;
* evaluation and effect order;
* missing versus null;
* exported visibility and reflection;
* contracts and effects;
* stable diagnostic codes and source locations;
* standard output and standard error; and
* process exit status.

Generated JVM class names are opaque backend implementation details. Java hosts use a documented
embedding facade rather than generated classes directly. Every artifact declares its Caret runtime
ABI version; an incompatible runtime rejects it clearly and recompilation is required across an
incompatible ABI change.

This ABI is specific to the initial JVM backend and is not part of Caret source semantics. Caret's
portable semantic module/interface model is shared across backends. The long-term language must be
conceptually and practically independent of the JVM, support other platforms, and permit a
self-hosted implementation.

## Deferred specification work

The public format and sandbox result envelope, serialization of dynamically supplied capabilities,
and environment replacement semantics are specified above. User-defined symbolic operators,
fine-grained module-code visibility, resumable sandbox state, and the standard compiler-environment
interface remain deferred for the initial language.

Source-exact and comment-preserving reconstruction, fine-grained metadata permissions, dynamic
language-feature unlocking, revocable capability proxies, resource quotas, operating-system or
hardware isolation, and sophisticated static information-flow analysis are explicitly deferred.
Their later implementation must not weaken root substitution or permit authority amplification.


## Not implemented

- trailing lambdas
- parameterized contracts, dispatch, complete static type proof,
  result contracts, effect inference, and ownership analysis
- universal collection literals, first-class fields, contract-selected representations, and persistent updates
- mutability containers, container reads/writes, and field reification
- `with`, resolver-only `outer` paths, and scoped member lookup
- `\\` and `\*` physical-to-logical layout baseline modifiers
- lambdas and higher-order standard collection operations
- cycles and transactional previous/next state views
- SIMD values and required vectorized application
- bytes, formats, codecs, and structured format failures
- contexts, rules, rulesets, persistent cycle objects, and rule cycles
- modules, module-ID declarations/catalog discovery, path and ModuleId imports, JVM compiler
  backend, runtime ABI, and optimizer
- environment-relative `@root`, structured program reification, canonical code serialization, and quines
- sandbox execution, capability isolation, reflective membranes, and nested sandboxes
- `#` compile-time bindings/expressions, compile-time imports and transformation, separate
  compilation roots, staged reachability, and target-specific artifacts
