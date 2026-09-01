<a id="sandboxes-and-security"></a>
# Sandboxes and Security

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

<a id="sandboxes"></a>
## Sandboxes

<a id="construction-environment-updates-and-access"></a>
### Construction, environment updates, and access

`sandbox` is an ordinary Caret function. The shared construction form is ordinary whitespace
application:

```caret
plugin = sandbox source environment
```

`source` may be a module path or semantic `Code`, selected through ordinary contract dispatch.
`environment` is an immutable named Collection. `sandbox` returns `Result Sandbox`; on success its
`value` is the stable `Sandbox` handle used below. Named members use the universal exported-field
syntax:

```caret
environment =
  ^clock = restrictedClock
```

Conceptually, the language-owned callable has ordinary overload signatures for each accepted source
contract, with an immutable named Collection environment and a `Result Sandbox` result. There is no
sandbox expression or sandbox-specific application grammar. For example, subject to the same
contracts, effects, and authority checks as any other partial application:

```caret
withEnvironment = sandbox _ environment
```

Security mediation, root substitution, catalog restriction, reflective membranes, lifecycle
state, and environment generation are semantics of invoking this privileged standard callable and
of the resulting `Sandbox` value. Compiler recognition attaches to that callable identity rather
than its lexical spelling. `sandbox` retains ordinary callable effect metadata: describing those
effects grants no authority, and aliases or partial applications cannot widen any capability or
visibility boundary.

This is shorthand for the equivalent explicit Collection:

```caret
environment =
  [
    ^clock = restrictedClock
  ]
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

<a id="lifecycle-and-boundary-values"></a>
### Lifecycle and boundary values

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

<a id="overview"></a>
### Overview

`sandbox` evaluates or imports Caret code in a restricted execution environment.

Unlike a normal import, sandboxed code does not inherit unrestricted visibility into the host root.

Instead, the host constructs a sandbox root and chooses what the sandbox may observe and use.

Conceptually:

```caret
plugin =
  sandbox pluginCode environment
```

The sandboxed code sees `@root` as metadata describing the lexical/name-resolution environment
constructed from `environment`; it is not the supplied Collection, the environment handle, or an
ordinary capability value.

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

<a id="root-substitution"></a>
### Root substitution

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
  [
    ^log = safeLog
    ^files = pluginFiles
    ^clock = safeClock
  ]
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

<a id="sandbox-capabilities"></a>
## Sandbox capabilities

<a id="exposed-program-capabilities"></a>
### Exposed program capabilities

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

<a id="libraries"></a>
### Libraries

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

<a id="language-features"></a>
### Language features

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

The initial configuration is an immutable named Collection. Root substitution constructs the
sandbox's lexical/name-resolution environment from that value; the Collection is not itself a
lexical root scope and does not make lexical environments reflectable. `swapEnv` may atomically
replace the complete snapshot while preserving the running sandbox generation.

---

<a id="isolation-layers"></a>
## Isolation layers

<a id="capability-mediation"></a>
### Capability mediation

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

<a id="filtered-access"></a>
### Filtered access

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

<a id="virtualized-resources"></a>
### Virtualized resources

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

<a id="effects-and-sandbox-authority"></a>
## Effects and sandbox authority

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

Here `fs` is a visible effect-catalog identity, not the filesystem capability itself. The
environment separately chooses whether a callable implementation is present and what authority it
holds. Replacing or restricting that implementation does not change the meaning of the effect set,
and exposing the effect name alone grants nothing.

Therefore:

```text
effect
    describes what kind of effect occurs

sandbox capability
    determines what authority or implementation is available
```

A sandbox must not treat an effect declaration itself as permission.

---

<a id="reflection-across-sandbox-boundaries"></a>
## Reflection across sandbox boundaries

<a id="reflective-membrane"></a>
### Reflective membrane

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

<a id="reference-projection"></a>
### Reference projection

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

<a id="sandboxed-code"></a>
### Sandboxed `.code`

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

<a id="revocable-capabilities"></a>
## Revocable capabilities

<a id="persistent-references"></a>
### Persistent references

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

<a id="mediated-revocation"></a>
### Mediated revocation

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

<a id="nested-sandboxes"></a>
## Nested sandboxes

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

<a id="tutorial-and-repl-environments"></a>
## Tutorial and REPL environments

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

<a id="tutorial-isolation-layer"></a>
### Tutorial isolation layer

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

<a id="progressive-capability-exposure"></a>
### Progressive capability exposure

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

<a id="relationship-to-normal-import"></a>
## Relationship to normal import

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

<a id="security-principle"></a>
## Security principle

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

<a id="implementation-requirements"></a>
## Implementation requirements

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

<a id="design-principle"></a>
## Design principle

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

---

<a id="java-embedding"></a>
## Java embedding sandbox

The Java 21 prototype exposes `caretlang.embedding` as its initial implemented sandbox boundary.
An embedded sandbox has a host-controlled environment, private Caret state, and exactly one script
source. Loading and executing are separate operations: the first `load` call permanently claims the
source slot and returns a single-attempt handle; `execute` consumes that handle before evaluation.
Multiple source files require the future module loader.

A successful script execution commits its state and returns an immutable named Collection of every
binding in that script's top lexical layer. A failed execution rolls Caret state back. Every later
Java invocation of a returned or registered Caret callable is an independent transaction with the
same commit rule. Output and completed host callback work are observable effects and cannot be
rolled back.

The host supplies an immutable `CaretEnvironment`. Named host values are lazy, resolve at most once
per environment snapshot, and remain cached even when a Caret transaction fails. An atomic
environment swap preserves sandbox and callable identity but discards the old value cache. Existing
callables resolve host values and callbacks against the current snapshot; removed authority causes
a Caret failure and never preserves the prior authority.

`print` and the reserved `registerCallbacks` bridge are available only when explicitly enabled.
The output destination belongs to the sandbox builder and cannot be redirected by a swap.
`registerCallbacks` accepts a named Collection of functions. Its last call in a successful operation
atomically replaces the Java-visible registry; failure preserves the previous immutable registry
snapshot. A callable returned through an ordinary result is also invocable and follows the same
lifetime and authority rules.

All Caret-originated lexer, parser, semantic, authority, callback, and runtime failures cross the
boundary as result diagnostics. Invalid Java use instead throws one coded `CaretEmbeddingException`.
An uncaught Java callback `Exception` becomes a sanitized Caret internal-error result; a JVM `Error`
escapes after best-effort rollback and invalidates the sandbox. Same-sandbox overlap and re-entry
fail immediately, while independent sandboxes may run concurrently. Closing is idempotent and
invalidates all sandbox-owned handles.

The public sealed value model preserves null versus missing and uses explicit conversions. It never
exposes interpreter values, AST nodes, lexical scopes, Java reflection, implementation descriptors,
or hidden host failures. This embedding API does not implement Caret `sandbox`, modules, `@root`,
`@module`, or compile-time `#` execution.
