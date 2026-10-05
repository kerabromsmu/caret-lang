# Test Coverage workflow

### `test coverage`

* Compare every `implemented` conformance requirement and every documented diagnostic with the
  canonical specification, actual implementation behavior, Gradle/JUnit tests, and Caret examples.
* Verify that cited Java tests genuinely exercise the claimed success paths, boundaries, failure
  modes, source locations, reflection/security invariants, and regressions; an existing test name
  alone is not sufficient evidence.
* Require every implemented feature to have a representative runnable `.caret` example with golden
  output and integration execution through `test.sh`. Require every public error or diagnostic to
  have Java-test or `.caret` fixture evidence, exact expected output, stable phase/code/location
  coverage where applicable, and no leaked Java exception.
* Repair unambiguous gaps by adding or strengthening Java tests, Caret feature/error fixtures,
  golden files, integration-harness entries, and conformance or diagnostic evidence. Preserve
  unrelated user changes and do not substitute an arbitrary line-coverage percentage for behavioral
  completeness.
* Treat discovered interpreter or specification defects as findings rather than silently changing
  observable language behavior. Ask before resolving conflicting semantics. This alias does not
  authorize interpreter behavior changes, `VERSION` changes, commits, pushes, pull-request changes,
  or GitHub issue/project mutations.
* Run `./gradlew test`, `./test.sh`, `bash scripts/check-example-coverage.sh`, and `git diff --check`.
  Report gaps found and repaired, tests run, remaining limitations, and any GitHub state changes.
