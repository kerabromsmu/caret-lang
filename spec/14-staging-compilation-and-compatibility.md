<a id="staging-compilation-and-compatibility"></a>
# Staging, Compilation, and Compatibility

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

<a id="compile-time-execution-and-separate-compilation"></a>
## Compile-Time Execution and Separate Compilation

<a id="overview"></a>
### Overview

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

<a id="compile-time-execution"></a>
## Compile-time execution

<a id="section"></a>
### `#`

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

<a id="compile-time-bindings"></a>
### Compile-time bindings

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

<a id="compile-time-expression-values"></a>
### Compile-time expression values

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

<a id="compile-time-dependency-rule"></a>
### Compile-time dependency rule

A compile-time computation may depend only on values available at compile time.

For example:

```caret
```caret
<a id="size-100"></a>
```caret
# size = 100
```
```

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
```caret
<a id="size-configurationtablesize"></a>
```caret
# size = configuration.tableSize
```
```
```caret
<a id="source-generatevalues-size"></a>
```caret
# source = generateValues size
```
```
```

is valid when every dependency is itself available during compilation.

---

<a id="ordinary-functions-at-compile-time"></a>
### Ordinary functions at compile time

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

The ordinary callable rule includes `contract`, `template`, `format`, `rule`, `cycle`, and
`sandbox`. Any of them may execute under `#` when its callable and arguments are available, its
effects are permitted, required authority exists, and any result that crosses the stage boundary
is representable there. None has special staging syntax. Language-owned callable identity may
enable static analysis or lowering, but lexical spelling alone does not affect parsing or staging.

---

<a id="compile-time-imports"></a>
## Compile-time imports

<a id="importing-for-metaprogramming"></a>
### Importing for metaprogramming

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

<a id="compile-time-import-does-not-imply-runtime-inclusion"></a>
### Compile-time import does not imply runtime inclusion

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
```caret
<a id="import"></a>
```caret
# import
```
```
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

<a id="compile-time-metaprogramming"></a>
## Compile-time metaprogramming

<a id="ordinary-values-and-code-values"></a>
### Ordinary values and code values

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

<a id="compile-time-transformation-uses-ordinary-functions"></a>
### Compile-time transformation uses ordinary functions

Caret should prefer ordinary collection and higher-order functions for compile-time program transformation.

For example:

```caret
```caret
<a id="shared-import-modulecaret"></a>
```caret
# shared = import "module.caret"
```
```

selected =
  # shared.rules filter $
    rule -> someCondition rule
```

`filter` is the ordinary Caret filtering operation.

It is not a compiler-specific filtering syntax.

The operation happens at compile time because its enclosing expression is prefixed by `#`.

The same `filter` may be used on runtime collections without `#`.

---

<a id="separate-compilation-roots"></a>
## Separate compilation roots

<a id="roots-define-artifacts"></a>
### Roots define artifacts

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

<a id="shared-source"></a>
### Shared source

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

<a id="reachability"></a>
### Reachability

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

<a id="example-shared-clientserver-rules"></a>
## Example: shared client/server rules

<a id="shared-interaction-module"></a>
### Shared interaction module

A shared source file may define both sides of an interaction.

For example, `client-server.caret`:

```caret
clientServer = module

^client = context
^server = context

^interaction =
  ruleset
    sendLogin = rule [
      ^C = client
      ^T = loginRequestedTrigger
      ^E = sendLoginRequest
    ]

    authenticate = rule [
      ^C = server
      ^T = loginReceivedTrigger
      ^E = authenticateAndReply
    ]

    showLoginResult = rule [
      ^C = client
      ^T = loginResultReceivedTrigger
      ^E = showResult
    ]
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

<a id="client-compilation-root"></a>
### Client compilation root

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

<a id="server-compilation-root"></a>
### Server compilation root

`server.caret` performs the corresponding selection:

```caret
```caret
<a id="shared-import-clientserver-4"></a>
```caret
# shared = import clientServer
```
```

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

<a id="resulting-artifacts"></a>
### Resulting artifacts

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

<a id="context-filtering"></a>
## Context filtering

<a id="context-values-rather-than-names"></a>
### Context values rather than names

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

<a id="complex-context-expressions"></a>
### Complex context expressions

A RuleDefinition `C` field may refer to ordinary first-class context combinations such as:

```caret
client and authenticated
```

or:

```caret
client or server
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

<a id="filtering-and-dependency-closure"></a>
## Filtering and dependency closure

<a id="filter-selects-rules"></a>
### `filter` selects rules

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

<a id="unselected-rules"></a>
### Unselected rules

A rule removed by compile-time filtering is not part of the resulting runtime ruleset.

If no remaining runtime definition depends on it, it is unreachable and need not be emitted.

Dependencies used only by that rule likewise need not be emitted.

Thus:

```caret
```caret
<a id="shared-import-clientserver-5"></a>
```caret
# shared = import clientServer
```
```

clientRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.client
```

does not imply that the client artifact contains `shared.interaction` in its original complete form.

Only the resulting `clientRules` value and runtime-reachable dependencies matter.

---

<a id="generality"></a>
## Generality

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
```caret
<a id="shared-import-platform-rulescaret"></a>
```caret
# shared = import "platform-rules.caret"
```
```

browserRules =
  # shared.rules filter $
    rule ->
      rule.context contains shared.browser
```

The compiler does not need a built-in concept of `browser`, `client`, `server`, or `agent`.

These are ordinary program values interpreted by compile-time Caret code.

---

<a id="compile-time-effects-and-authority"></a>
## Compile-time effects and authority

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

<a id="stage-boundaries"></a>
## Stage boundaries

<a id="values-crossing-into-runtime"></a>
### Values crossing into runtime

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

<a id="compile-time-bindings-remain-compile-time"></a>
### Compile-time bindings remain compile-time

A binding declared:

```caret
# shared = import clientServer
```

does not itself become part of the runtime program merely because later code uses it during compilation.

This permits large modules and compiler-side structures to be inspected without forcing them into the emitted artifact.

The resulting runtime program contains only values deliberately crossing the stage boundary and their runtime-reachable dependencies.

---

<a id="parsing-and-extent-of"></a>
## Parsing and extent of `#`

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

<a id="relationship-to"></a>
## Relationship to `@`

`@` and `#` have complementary roles.

`@` reifies a binding or program entity:

```caret
@function
@root.code
player.@health
```

`#` controls execution stage:

```caret
```caret
<a id="loadedmodule-import-modulecaret"></a>
```caret
# loadedModule = import "module.caret"
```
```

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

<a id="implementation-requirements"></a>
## Implementation requirements

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

<a id="design-principle"></a>
## Design principle

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

<a id="compiler-target-and-compatibility"></a>
## Compiler target and compatibility

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

<a id="deferred-specification-work"></a>
## Deferred specification work

The public format and sandbox result envelope, serialization of dynamically supplied capabilities,
and environment replacement semantics are specified in the
[sandbox specification](13-sandboxes-and-security.md). User-defined symbolic operators,
fine-grained module-code visibility, resumable sandbox state, and the standard compiler-environment
interface remain deferred for the initial language.

Source-exact and comment-preserving reconstruction, fine-grained metadata permissions, dynamic
language-feature unlocking, revocable capability proxies, resource quotas, operating-system or
hardware isolation, and sophisticated static information-flow analysis are explicitly deferred.
Their later implementation must not weaken root substitution or permit authority amplification.


<a id="not-implemented"></a>
## Not implemented

- trailing lambdas
- general parameterized contracts beyond the implemented `Sequence T` foundation, complete static
  dispatch/type proof, complete higher-order effect propagation, and ownership analysis; overload
  dispatch, initial public effect declarations/enforcement/reflection, named-function inference,
  result clauses, and predicate refinements are implemented
- first-class dynamic fields, context-selected collection representations, and persistent updates;
  positional/static-named literals and exported named Collections are implemented
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
