<a id="source-layout-and-diagnostics"></a>
# Source, Layout, and Diagnostics

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

<a id="comments"></a>
## Comments

`//` introduces a line comment. Field names and other identifiers represented as data use ordinary
strings rather than a separate name-literal syntax.

<a id="diagnostics"></a>
## Diagnostics

Lexical, parse, and runtime errors include the one-based line and column of the smallest relevant
source expression. Columns count raw source characters. A tab therefore advances the displayed
column by one, although a leading tab still contributes two spaces to indentation depth.
The planned layout-baseline modifiers do not change these coordinates: diagnostics continue to use
physical source lines and columns even when effective logical indentation differs.
Built-in argument validation retains individual argument spans, so an invalid operand points to
that operand rather than the complete call.

Internally, diagnostics retain their phase, a stable diagnostic code, message, primary source span,
related source spans, an optional language diagnostic cause, and Collection-valued subsystem details. These use the
[`ErrorTemplate`](06-collections-fields-and-templates.md#standard-error-template) model. A diagnostic that
aborts lexing, parsing, analysis, or evaluation is not thereby an ordinary catchable Caret value.
The CLI renders the primary location in the compact form below and follows it
with located `Note:` lines when a diagnostic has related locations, such as the first declaration
for a duplicate definition.

```text
Error: Line 1, column 7: Unknown name: absent
```

The execution parser is fail-fast: the REPL, file runner, and test runner stop at the first lexical
or parse failure. Compiler-oriented analysis may instead request parser recovery. Recovery reports
parser diagnostics in source order, omits the malformed declaration, and resumes at the next
sibling declaration boundary. A failure inside an indentation-defined body discards only the
unreliable declaration and its more-indented continuation or nested region, allowing later siblings
and enclosing declarations to remain available. One malformed construct must not generate cascaded
parser diagnostics. Lexical failures remain fail-fast until lexical recovery is specified.

Every parsed AST node retains its complete physical source span. Logical indentation mapping,
multiline grouping, implicit nodes, desugaring, and AST rebuilding must not replace those coordinates
with logical positions or truncate an enclosing node to one of its children.

<a id="test-files"></a>
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

<a id="source-text-operations"></a>
## Source text operations

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

<a id="ungrouped-multiline-application"></a>
## Ungrouped multiline application

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

<a id="planned-layout-baseline-modifiers"></a>
## Layout baseline modifiers

Caret normally derives logical block structure from physical indentation. The layout tokens
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

<a id="adjusts-the-physical-baseline"></a>
### `\\` adjusts the physical baseline

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

<a id="restores-the-previous-mapping"></a>
### `\*` restores the previous mapping

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

<a id="stacking-and-nested-indentation"></a>
### Stacking and nested indentation

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

<a id="placement-strings-and-comments"></a>
### Placement, strings, and comments

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

<a id="interaction-with-with-and-other-indentation-defined-forms"></a>
### Interaction with `with` and other indentation-defined forms

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

<a id="diagnostics-and-formatting"></a>
### Diagnostics and formatting

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

<a id="layout-modifier-implementation-requirements"></a>
### Layout-modifier implementation requirements

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

<a id="design-principle"></a>
### Design principle

Caret's indentation determines logical structure, but logical indentation need not always occupy
the same physical source columns. `\\` temporarily shifts the physical indentation baseline while
preserving logical nesting, and `\*` restores the previous baseline. The adjusted mapping continues
until explicitly restored or EOF; ordinary physical dedentation cannot end it because physical
indentation is what the modifier changes. After effective logical indentation is calculated, all
normal Caret parsing and semantic rules apply unchanged.
