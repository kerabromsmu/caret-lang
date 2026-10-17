# Stage authority and boundaries

[Chapter index](../14-staging-compilation-and-compatibility.md) · [Language specification index](../../LANGUAGE.md)


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
