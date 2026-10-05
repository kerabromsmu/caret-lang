# Phase 9 — Modules, execution roots, and program reification

[Roadmap index](../PLAN.md)

## Phase 9 — Modules, execution roots, and program reification

Phase 9 is split into v1 modules/metadata and later semantic-code work. The phase as a whole is not
complete when only the v1 chunks are implemented. No compiler backend, format system, or rules
runtime is required for 9A/9B.

<a id="phase-9a-modules-and-imports-v1"></a>
### Phase 9A — Modules and imports (v1)

- Parse an optional top-level `moduleId = module` declaration into a namespace separate from lexical
  bindings. Shallowly discover project declarations below each compilation-root directory, merge
  environment-supplied IDs, and diagnose malformed, duplicate, colliding, and unresolved IDs with
  all relevant source locations.
- Implement both relative `String` path imports and catalog-resolved `ModuleId` imports, explicit
  exports, private bindings, initialization order, and duplicate/cyclic import diagnostics. Use the
  resolved canonical source path—not the logical ID—as the evaluation-cache and cycle-detection key,
  so path and ID imports of the same source share one module value per environment generation.
- Carry the visible module catalog through normal execution and analysis, with an environment-local
  cache ready for sandbox use in 10A. Never inject IDs as runtime globals. Add staging integration
  when staging exists.
- Keep module analysis and semantic interfaces backend-independent. V1 requires interpreted module
  evaluation and public contract/effect metadata; persisted compilation caches and format interfaces
  follow the later staging/backend and format phases.
- Test catalog discovery without evaluating unrelated files, duplicate/colliding IDs, normalized
  path/ID cache identity, private exports, initialization effects, failed loads, and import chains.

<a id="phase-9b-execution-environments-metadata-and-results-v1"></a>
### Phase 9B — Execution environments, metadata, and results (v1)

- Give file execution, imports, tests, and REPL sessions an explicit execution-environment object
  containing the visible root, module catalog/cache, libraries, language capabilities, and runtime
  capabilities. Preserve context across closures and imported functions. Do not derive Caret
  authority from process-global Java state.
- Implement reserved metadata-only `@root` and `@module` reflective primaries. Make them equal only
  in the root module; expose `kind`, `id`, and visible binding `ids`, keep descriptors non-callable,
  and preserve ordinary export authority. Semantic `.code` is not part of v1 metadata.
- Implement the specified shared
  [`Result T`](../spec/sections/06-07-templates-advanced.md#standard-error-template) using the
  existing `ErrorTemplate`: exact `ok`, `value`, and `error` fields, successful missing values,
  and no implicit flattening. This sandbox prerequisite does not require formats.
- Test root/module identity in files, imports, tests, and successful/failed REPL submissions;
  visible/private binding metadata; closure context; and result membership and failure payloads.

<a id="phase-9c-semantic-code-post-v1"></a>
### Phase 9C — Semantic Code (post-v1)

- Reify analyzed source as language-owned `Code`/`CodeElement` values with stable public descriptors
  for bindings, functions, parameters, contracts, expressions, imports, and later constructs.
- Add environment-relative `.code` to root/module metadata and imported-module reflection. Include
  complete semantic code for visible modules without granting invocation of private bindings.
- Preserve whole-module and successful/provisional REPL code snapshots, semantic binding
  relationships, ordinary application nodes, portable external references, and source-span privacy.
- Add semantic Code sandbox construction and code-visibility adversarial tests in coordination
  with Phase 10. Do not expose Java AST/runtime objects or dynamically supplied host implementations.

<a id="phase-9d-canonical-serialization-and-quines-post-v1"></a>
### Phase 9D — Canonical serialization and quines (post-v1)

- After 9C, implement structural code equality and canonical code serialization as one tested
  semantic model. Private alpha-equivalence must not erase observable names or evaluation order.
- Implement canonical code serialization with alpha-normalized private names, proven-safe ordering,
  logical import paths, portable language dependencies, and no source metadata. Keep dynamically
  supplied environment implementations out of canonical code and require compatible bindings when
  re-executing it. Require parse/serialize/parse structural equivalence and canonical quine/module
  fixtures.
