# Phase 9 — Modules, execution roots, and program reification

[Roadmap index](../PLAN.md)

## Phase 9 — Modules, execution roots, and program reification

### Modules

- Parse an optional top-level `moduleId = module` declaration into a namespace separate from lexical
  bindings. Shallowly discover project declarations below each compilation-root directory, merge
  environment-supplied IDs, and diagnose malformed, duplicate, colliding, and unresolved IDs with
  all relevant source locations.
- Implement both relative `String` path imports and catalog-resolved `ModuleId` imports, explicit
  exports, private bindings, initialization order, and duplicate/cyclic import diagnostics. Use the
  resolved canonical source path—not the logical ID—as the evaluation-cache and cycle-detection key,
  so path and ID imports of the same source share one module value per environment generation.
- Carry the visible module catalog through normal execution, staging, reflection, and sandboxes;
  never inject IDs as runtime globals or expose catalog entries outside the current environment.
- Compile/cache modules independently using a versioned semantic interface containing public names,
  types/contracts, effects, formats, and reflection descriptors.

### Execution roots and code values

- Give file execution, imports, tests, and REPL sessions an explicit execution-environment object
  containing the visible root, code snapshot, libraries, language capabilities, and runtime
  capabilities. Do not derive Caret authority from process-global Java state.
- Implement reserved metadata-only `@root` and `@module` reflective primaries. Make them equal only
  in the root module, keep their descriptors non-callable, and preserve ordinary export authority
  even though visible module code includes private semantic declarations.
- Reify analyzed source as language-owned `Code`/`CodeElement` values with stable public descriptors
  for bindings, functions, parameters, contracts, expressions, imports, and later constructs.
- Implement canonical code serialization with alpha-normalized private names, proven-safe ordering,
  logical import paths, portable language dependencies, and no source metadata. Keep dynamically
  supplied environment implementations out of canonical code and require compatible bindings when
  re-executing it. Require parse/serialize/parse structural equivalence and canonical quine/module
  fixtures.
