# Compiler compatibility

[Chapter index](../14-staging-compilation-and-compatibility.md) · [Language specification index](../../LANGUAGE.md)


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
[sandbox specification](../13-sandboxes-and-security.md). User-defined symbolic operators,
fine-grained module-code visibility, resumable sandbox state, and the standard compiler-environment
interface remain deferred for the initial language.

Source-exact and comment-preserving reconstruction, fine-grained metadata permissions, dynamic
language-feature unlocking, revocable capability proxies, resource quotas, operating-system or
hardware isolation, and sophisticated static information-flow analysis are explicitly deferred.
Their later implementation must not weaken root substitution or permit authority amplification.


<a id="not-implemented"></a>
## Not implemented

- trailing lambdas
- parameterized contracts beyond the implemented callable `Sequence T`, `Field K V`, and
  `Dictionary K V` constructors, and complete static dispatch/type proof; current named, aliased,
  partial, composed, overloaded, closure, and recursive callable forms have higher-order effect
  propagation, while later value kinds extend that analysis
- storage ownership as public language state; conservative internal ownership analysis is implemented
  solely as an optimization with persistent behavior as the reference
- first-class dynamic fields, context-selected collection representations, and persistent updates;
  positional/static-named literals and exported named Collections are implemented
- mutability containers, container reads/writes, and field reification
- `with`, resolver-only `outer` paths, and scoped member lookup
- formatter support for `\\` and `\*`; physical-to-logical layout baseline mapping is implemented
  for the prototype's currently supported indentation-opening headers
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
