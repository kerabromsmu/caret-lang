# Phase 9 — Modules, execution roots, and program reification

[Roadmap index](../PLAN.md)

## Phase 9 — Modules, execution roots, and program reification

Phase 9 is split into v1 modules/metadata and later semantic-code work. The phase as a whole is not
complete when only the v1 chunks are implemented. No compiler backend, format system, or rules
runtime is required for 9A/9B.

<a id="phase-9a-modules-and-imports-v1"></a>
### Phase 9A — Modules and imports (v1)

Tracked by [#114](https://github.com/kerabromsmu/caret-lang/issues/114), estimated at **6–12 engineer-days**.

- Implement relative `String` path imports, explicit exports, private bindings, initialization order
  and located import-chain/cycle diagnostics. Cache successful evaluation by canonical source path
  per environment generation; normalized aliases share initialization and one module value.
- Do not cache failed loads as successful. Keep module state private to its environment and enforce
  explicit source authorization at sandbox boundaries, including traversal and canonical-path aliases.
- Preserve backend-independent interfaces and public contract/effect metadata. Compiler, formats,
  staging and persisted compilation caches are not prerequisites.
- Test privacy, repeated/aliased path identity, initialization effects, failed loads and import chains.
- Module-ID declarations/imports, discovery and catalog diagnostics are outside v1; see #115 below.

<a id="module-ids-and-catalogs-post-v1"></a>
### Module IDs and catalog discovery (post-v1)

Tracked by [#115](https://github.com/kerabromsmu/caret-lang/issues/115), following #114; exact release
unassigned. Re-estimate incremental work after path modules rather than adding the original full estimate.

- Parse optional top-level `moduleId = module` declarations in a separate namespace. Shallowly
  discover declarations below compilation roots without evaluating or semantically compiling unrelated files.
- Merge project and environment-supplied IDs; diagnose malformed, duplicate, colliding and unresolved
  IDs with relevant source locations. Never inject IDs as runtime globals.
- Add ModuleId imports sharing canonical-path evaluation-cache and cycle-detection identity with
  path imports. Test mixed path/ID imports, declaration discovery and collisions.
- Restrict visible catalogs at sandbox boundaries without widening permitted module sources or authority.

<a id="phase-9b-execution-environments-metadata-and-results-v1"></a>
### Phase 9B — Execution environments, metadata, and results (v1)

Execution-context foundation: [#116](https://github.com/kerabromsmu/caret-lang/issues/116),
**8–15 engineer-days**, with unchanged scope and three implementation milestones:

1. Explicit context and module-state ownership (**3–5 days**).
2. Context preservation through imports, closures and invocation (**3–6 days**).
3. File, REPL, test and Java embedding integration (**2–4 days**).

Metadata (**4–8 days**) and shared Results (**2–4 days**) remain separate allowances, not part of #116.
Module-ID catalogs integrate after v1. Preserve the implemented Java embedding API and its environment swapping.

- Include the [v1 visible-identifier portion of #5](../spec/12-modules-reflection-and-code.md#planned-v1-visible-identifiers):
  deterministic local/imported visible identifiers through metadata-only `@root.ids`, preserving
  `id`/`ids` naming, exports, privacy, and environment authority. Coordinate sandbox checks with 10A;
  semantic Code remains 9C work rather than a prerequisite for identifier metadata.
- Give file execution, imports, tests, and REPL sessions an explicit execution-environment object
  containing the visible root, permitted module sources/cache, libraries, language capabilities, and runtime
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
