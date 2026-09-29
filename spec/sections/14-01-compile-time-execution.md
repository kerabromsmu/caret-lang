# Compile-time execution

[Chapter index](../14-staging-compilation-and-compatibility.md) · [Language specification index](../../LANGUAGE.md)


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
