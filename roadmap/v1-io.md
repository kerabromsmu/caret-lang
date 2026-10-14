# V1 — File and console I/O pipeline subset

[Roadmap index](../PLAN.md) · [V1 feature card #109](https://github.com/kerabromsmu/caret-lang/issues/109) · [Complete generalized I/O design #99](https://github.com/kerabromsmu/caret-lang/issues/99)

## Release allocation and current status

V1 requires a bounded, synchronous file and console I/O subset compatible with #99's ordinary
function-composition model. #109 is a Feature card in **Underspecified**: release scope is approved,
but the public API must be settled in the canonical specification before runtime implementation.
This roadmap chunk introduces no new syntax, callable names, contracts, or implemented behavior.

Schedule runtime work after Phase 9B execution environments and the shared
[`Result T`](../spec/sections/06-07-templates-advanced.md#standard-error-template), following the
Phase 9A module foundation. Coordinate capability mediation and adversarial sandbox tests with
[Phase 10A](phase-10.md#phase-10a-initial-sandboxes-v1); v1 completion includes that integration.
The subset does not require completion of #99, full formats/codecs, gated computations, general
resumable failure handlers, concurrency, or a compiler backend. Its design discussion can happen
before these runtime prerequisites are implemented.

## Required v1 behavior

- Read whole files as UTF-8 text or binary data; replace or append whole supplied text/binary content.
- Read console lines and write text with or without a newline. Preserve distinctions between EOF,
  an empty line/file, missing, null, and a failed operation.
- Use ordinary values, functions, contracts, and composition. Reuse existing `Byte`/`UInt8`
  Collections for binary data; acquire and release file resources within each operation.
- Report recoverable unsuccessful operations through explicit `Result T` values whose errors
  satisfy `ErrorTemplate`. Invalid programs, contract/authority violations, and implementation
  faults retain aborting diagnostics; broad host-exception conversion is forbidden.
- Supply I/O through the execution environment's capabilities. Effects describe behavior and
  never grant authority. Preserve the existing `print` compatibility baseline.

## Specification prerequisite

Settle the following in the owning `spec/` documents before moving #109 to Todo or implementing it:

- Public source/destination and pipeline callable interfaces, default configuration, argument/result
  contracts, and transfer/representation stages that extend compatibly to #99. Do not introduce
  a separate stream/container model or silently invent syntax.
- Nullary input construction, storage, passing, invocation, and composition. Current
  [composition](../spec/03-functions-operators-and-lambdas.md#function-composition) rejects a nullary
  left operand; automatic name-read invocation and non-callable reflection metadata need an
  explicit first-class callable design.
- Placement of the Result policy and explicit composition across success/failure boundaries.
  Results never flatten, unwrap, or propagate implicitly; preserve the larger design's ordinary
  composed-data pipelines and compatibility with later failure handlers.
- EOF representation, newline handling, UTF-8 encoding/decoding failures, exact byte contracts,
  file creation/path resolution, replacement/append behavior, output success values, resource
  cleanup, and partial-write reporting. Do not assume rollback or atomic replacement.
- Environment-relative effect identities and capability exposure across CLI files, REPL, tests,
  embedding, modules, and sandboxes, including console input sharing with REPL submissions.
- Stable failure codes, located and sanitized error details, and which failures remain aborting.

## Acceptance and completion

- Test UTF-8/Unicode and binary round trips, every byte value, empty files, replacement/append,
  console lines/output, EOF versus empty lines, and continued execution after recoverable failures.
- Test missing/inaccessible files, invalid encoding/byte input, failed writes and partial transfers,
  cleanup on success/failure, and stable codes with one-based source locations. Use controlled host
  providers where real failure injection would be unreliable.
- Exercise aliases, shadowing, partial application, nullary inputs, effects, reflection, and
  invocation timing. Pipeline construction and inspection must not accidentally perform I/O.
- Document the threat model and test capability absence, restricted path/traversal/symlink access,
  reflective recovery of hidden authority, retained references, and nested sandbox limits.
  Reflection and diagnostics must expose no Java resource objects or secrets.
- Add runnable `.caret` integration examples using temporary files and controlled console input/output.
  Update canonical specifications, conformance evidence, diagnostics, README, and WEB_INTRODUCTION;
  run the full Gradle/integration suites, example coverage checks, and `git diff --check`.

No implementation evidence or conformance status is changed by this release allocation. Completing
#109 covers only this subset; it does not complete #99 or a broader mixed requirement.

## Complete generalized I/O after v1

#99 retains persistent/bidirectional endpoints and their lifetimes, incremental/streaming transfers,
network/HTTP/database resources, asynchronous/event-driven operation, general formats/codecs, and
resumable waiting/retry/failure handling. Its exact later version remains unassigned, and its
Specification card remains Underspecified. The v1 card and #99 link to each other; completing the
full design is not a dependency of the bounded subset.
