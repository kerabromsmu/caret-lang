# Phase 10 — Sandboxes and capability isolation

[Roadmap index](../PLAN.md)

## Phase 10 — Sandboxes and capability isolation

Phase 10 is split into a v1 boundary and later lifecycle/projection features. Depend on Phase 9A
modules and Phase 9B environments, metadata, and shared results. Keep the implemented Java embedding
API, including environment swapping, intact; its behavior is not reduced to the new Caret subset.

Coordinate Phase 10A with the separately scheduled [v1 file/console I/O subset (#109)](v1-io.md).
Once its API is settled, v1 acceptance includes explicit I/O capability mediation, environment-relative
effects, adversarial path/reflection/retained-reference tests and rejection of nested-sandbox
creation. This integration does not require completing generalized I/O (#99) or introducing persistent endpoints.

<a id="phase-10a-initial-sandboxes-v1"></a>
### Phase 10A — Restricted initial sandboxes (v1)

Tracked by [#117](https://github.com/kerabromsmu/caret-lang/issues/117), estimated at **12–22
engineer-days**. Remain Underspecified until supported boundary values, callable interfaces and
located failures are settled; no implementation or new interface is selected by this scope change.

- Implement ordinary `sandbox source environment` for module paths, returning `Result Sandbox`
  with a stable handle and a fixed immutable environment snapshot. Normal import remains distinct;
  evaluated module caches, initialization, and runtime state remain private to each generation.
- Expose only supplied bindings, capabilities, and allowed module sources. Check
  every path import against explicit source authorization; sandbox loading must not
  scan the host project or recover unexposed sources through traversal or canonical-path aliases.
- Mediate exported calls and retained callable references. Apply the shared `Result`/`ErrorTemplate`
  envelope to construction, exported calls, and lifecycle operations without flattening results.
  Metadata remains non-callable and cannot expose a lexical root, captures, or native implementation.
- Implement effectful `terminate` and `unload`. Discard runtime state and invalidate references from
  that generation, including references nested in immutable Collections. Copied immutable values
  remain values; the stable handle retains source/configuration and lifecycle metadata.
- Support immutable data and mediated callable crossings. Reject shared mutable containers and
  unsupported boundary values explicitly, including indirect crossings through Collections.
  Callers may pass immutable contents as ordinary snapshots; producing a snapshot retains its normal
  explicit state-read effect. Container sharing is deferred to #118, not silently converted to copying.
- Keep direct capabilities and caller-supplied filtered/virtual callable implementations possible
  through the ordinary environment. Defer dedicated richer projection mechanisms to 10C. Effect
  declarations describe behavior and never grant authority.
- Make effect-catalog visibility environment-relative and independent from callable/capability
  projection. Restricted code cannot recover hidden effect identities or implementations.
- Project references crossing the boundary through a reflective membrane that cannot expose host
  roots, private captures, native implementation state, hidden code, or other capabilities.
- Reject nested sandbox creation, including aliases, partials and supplied callable paths that
  attempt to bypass that restriction. Nested support and its authority tests are deferred to #118.
- Reject unsupported boundary values explicitly rather than exposing implementation objects.
  Semantic Code input, `.code`, swaps, reload, and read-only/virtual container views are later work.
- Document the threat model and add adversarial interpreter tests for lookup, reflective traversal,
  imports, effect visibility, rejection of shared containers/nested creation, retained references,
  and termination/unloading. Add code-visibility and compiler-parity tests when those features exist.

<a id="shared-containers-and-nesting-post-v1"></a>
### Complete boundary support: shared containers and nesting (post-v1)

Tracked by [#118](https://github.com/kerabromsmu/caret-lang/issues/118), following #117; exact release
unassigned. Re-estimate incremental work after the restricted boundary. This card does not duplicate
10B/10C, semantic Code or compiler work.

- Add explicitly supplied shared read/write containers with preserved identity, aliasing, content
  contracts and StateRead/StateWrite checking; reflection and field reification must not widen authority.
- Support nested sandboxes with child authority bounded by the parent, including visible imports,
  effects and capabilities. Additional authority requires explicit outer-host injection.
- Extend lifecycle/reference invalidation, threat model and adversarial tests to container sharing
  and nested environments. Settle expanded boundary interfaces before implementation.

<a id="phase-10b-environment-swaps-and-reload-post-v1"></a>
### Phase 10B — Environment swaps and reload (post-v1)

- Implement atomic, validate-then-install `swapEnv`, current-environment lookup at every boundary
  operation, and mediated named capability references. Preserve the running generation and state;
  failed swaps retain the previous snapshot, and removed names cannot retain their former authority.
- Implement stop-first `reload` with fresh module state/cache and old-reference invalidation without
  rebinding. Failed reload leaves the sandbox unloaded but retryable from retained configuration.
- Extend boundary results and metadata atomically. Test swaps during boundary callbacks, failures,
  saved/nested references, and nested authority without broadening the parent's capabilities.

<a id="phase-10c-richer-boundary-projections-post-v1"></a>
### Phase 10C — Richer boundary projections (post-v1)

- Add explicit read-only container views and richer mediated/virtual replacements. Reflection and
  field reification must preserve each projection and never recover hidden mutation authority.
- Extend direct/filtered/virtual capability projection mechanisms and cross-boundary support for
  later semantic values only after their owning phases settle their public descriptors.
- Add the semantic Code source overload after Phase 9C, retaining sandbox root/code visibility.
  Canonical sandbox quines follow 9D; compiler parity follows Phase 12.
- Keep revocation, quotas, OS/process isolation, and advanced information-flow enforcement deferred.
