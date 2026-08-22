<a id="modules-reflection-and-code"></a>
# Modules, Reflection, and Code

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

<a id="planned-modules-and-compilation"></a>
## Planned modules and compilation

<a id="source-modules-and-stable-module-ids"></a>
### Source modules and stable module IDs

A source module is one Caret source file. A file may optionally declare one stable logical
`ModuleId` at file top level:

```caret
clientServer = module
```

This is a module-ID declaration, not an ordinary assignment. The left-hand name identifies the
current source module in the compilation environment's module catalog. It is not a runtime binding,
is neither private nor exported, does not require `^`, and does not appear in the module's exported
Collection. The declaration may occur at most once in a file and only at file/module top level.

`module` remains reserved. Bare `module` is not a general expression and is valid only as the exact
right-hand side of `moduleId = module`. A source file need not declare an ID; such a file remains
importable by path. The declared ID uses the ordinary identifier spelling rules, but occupies the
separate flat module-ID namespace. It may therefore have the same spelling as an unrelated ordinary
lexical binding without either declaration shadowing or replacing the other.

These terms remain distinct:

* a **source module** is a Caret source file;
* a **ModuleId** is its optional stable logical catalog identifier;
* a **module value** is the immutable named Collection of top-level exports obtained by importing
  the source module; and
* `@module` is the metadata/reflection reference for the module containing currently executing code.

<a id="import-expressions"></a>
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
module value. Every importer in that environment receives the same immutable module value: a named
Collection containing only top-level `^` exports. The source module's private lexical scope exists
during evaluation but is not the imported value and cannot be reached through ordinary lookup.

Sandboxes evaluate modules independently: immutable parsed or compiled artifacts may be shared,
but evaluated modules, initialization effects, bindings, and mutable runtime state may not cross
environment boundaries. Reloading a sandbox creates a fresh module-evaluation cache. Private
bindings remain inaccessible through lookup and reflection.

An import cycle is a located module diagnostic that reports the import chain. A module that fails to
load or evaluate is not cached as successful. Importing the same canonical module again does not
repeat its initialization effects.

<a id="module-catalog-discovery"></a>
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

<a id="module-diagnostics-and-implementation-requirements"></a>
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

<a id="root-program-reification-quines-and-sandboxes"></a>
## `@root`, Program Reification, Quines, and Sandboxes

<a id="normative-reference-model"></a>
### Normative reference model

`@root` and `@module` are synthetic, metadata-only reflection references. Neither corresponds to a
lexical scope or ordinary Collection value and neither is callable. Bare `root` is reserved and invalid as an expression.
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

<a id="code-values-snapshots-and-equality"></a>
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

Semantic Code preserves lexical-scope relationships where name resolution, captures,
alpha-equivalence, or private/public relationships require them. Those relationships are program
structure, not serialized runtime Scope values. Canonical text may retain the shorter exported-block
form instead of rewriting it as `[...]`; either form must reconstruct the same named Collection.

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

<a id="overview"></a>
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

<a id="root"></a>
### `@root`

<a id="root-reference"></a>
#### Root reference

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

<a id="root-is-environment-relative"></a>
#### Root is environment-relative

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

<a id="program-code-metadata"></a>
### Program code metadata

<a id="code"></a>
#### `.code`

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

<a id="code-as-data"></a>
#### Code as data

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

<a id="canonical-textual-form"></a>
#### Canonical textual form

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

<a id="quines"></a>
### Quines

<a id="canonical-quine"></a>
#### Canonical quine

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

<a id="quine-property"></a>
#### Quine property

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

