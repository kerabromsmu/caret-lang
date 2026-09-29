# Phase 10 — Sandboxes and capability isolation

[Roadmap index](../PLAN.md)

## Phase 10 — Sandboxes and capability isolation

- Implement `sandbox source environment` for module paths and semantic `Code`, returning `Result
  Sandbox` with a stable sandbox handle and an immutable exported environment snapshot. Keep normal
  imports semantically distinct and evaluated module caches private to each generation.
- Implement atomic, validate-then-install `swapEnv`, current-environment lookup at every boundary
  operation, mediated named capability references, and bounded authority inheritance. Preserve the
  running generation and state; failed swaps retain the previous snapshot.
- Implement effectful `terminate`, `unload`, and stop-first `reload`. Discard resumable state,
  invalidate all old-generation references without rebinding, preserve copied immutable values,
  and leave a failed reload unloaded but retryable from retained configuration.
- Support direct, filtered, and virtual capabilities. Treat effect declarations as descriptions,
  never authority grants, and report unavailable authority through `Result` and `ErrorTemplate`.
  Apply the same boundary result envelope to construction, lifecycle, swaps, and exported calls.
- Make effect-catalog visibility environment-relative and independent from callable/capability
  projection. Nested sandboxes cannot introduce effect identities or implementations hidden by
  their parent without explicit outer-host injection.
- Project containers explicitly as shared read/write identities, read-only views, mediated values,
  snapshots, or virtual replacements. Reflection and field reification must not upgrade the chosen
  projection or recover hidden host mutation authority.
- Project references crossing the boundary through a reflective membrane that cannot expose host
  roots, private captures, native implementation state, hidden code, or other capabilities.
- Support nested sandboxes with child authority bounded by parent authority unless the outer host
  explicitly injects an additional capability.
- Define and test a threat model covering name lookup, reflection, code metadata, imports, effects,
  retained references, nested environments, and interpreter/compiler parity. Keep revocation,
  quotas, OS/process isolation, and advanced information-flow enforcement deferred.

