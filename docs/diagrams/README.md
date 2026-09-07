# Caret class diagrams

These PlantUML sources describe the current Java 21 prototype. They favor stable architectural
relationships over exhaustive fields and methods, so they remain useful as the implementation
evolves.

| Diagram | Purpose |
| --- | --- |
| [`caret-overview.puml`](caret-overview.puml) | End-to-end compiler/interpreter and embedding flow |
| [`caret-ast.puml`](caret-ast.puml) | Syntax tree hierarchy and AST utilities |
| [`caret-runtime.puml`](caret-runtime.puml) | Runtime values, callables, environments, and collections |
| [`caret-contracts.puml`](caret-contracts.puml) | Contracts, signatures, inference, and effects |
| [`caret-embedding.puml`](caret-embedding.puml) | Public Java embedding API and internal bridge |

To render all diagrams locally with PlantUML installed:

```bash
plantuml docs/diagrams/*.puml
```

Generated images are intentionally not tracked. Public embedding types use blue styling; internal
interpreter types use neutral styling. Dashed arrows denote dependencies, diamonds denote ownership
or composition, and hollow triangles denote inheritance or interface implementation.
