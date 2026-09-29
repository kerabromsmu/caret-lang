# Caret Language Project Instructions

## Project goal

Caret is an experimental concise programming language. Its syntax should remove routine punctuation and boilerplate while retaining clear, predictable, statically analyzable semantics.

The current implementation is a Java 21 tree-walking interpreter.

Read `README.md`, `LANGUAGE.md`, and the linked canonical feature documents relevant to the change
before making architectural or syntactic changes.

## Core design principles

1. Common operations should have compact syntax.
2. Whitespace performs function application.
3. Indentation defines blocks. Parentheses and braces may remain available for explicit grouping.
4. Missing and null are different states.
5. Dynamic operations should not throw exceptions for expected conditions such as missing fields.
6. Reflection should be a normal language feature rather than a separate cumbersome API.
7. Exported blocks produce named Collections containing only explicitly exported bindings; lexical
   scopes are not first-class values.
8. Immutable and functional programming should be supported without making mutable programming impossible.
9. Do not add Java-like ceremony to the Caret language syntax.
10. Do not silently invent syntax when the specification is unresolved. Document the issue and propose alternatives.
11. Reflection is relative to the execution environment visible to the current code and must not
    cross sandbox visibility or authority boundaries.
12. Effect declarations describe observable behavior but do not grant authority. A child sandbox
    must not amplify the authority available to its parent.
13. `@root` and `@module` are metadata-only references, not hidden scope objects or capability
    invocation paths.
14. Code visibility and binding authority are distinct: the initial module-code reflection model
    exposes complete semantic code for a visible module without exposing its private bindings.
15. Planned `with` changes lexical lookup only; it must not copy members or widen their visibility
    or authority. Planned `outer.name` is a resolver-owned lexical path, never a first-class scope
    exposing enclosing private bindings.
16. Planned `#` changes execution stage but grants no effects or authority. Compile-time visibility,
    runtime inclusion, and artifact reachability are distinct; staging must preserve module export,
    reflection, sandbox, and capability boundaries.
17. Expression-form `#` is a compile-time remainder marker, not a unary precedence operator. Its
    region extends to the nearest enclosing expression boundary, and no later operator resumes
    runtime execution; binding-form `# name = expression` stages the complete binding.

For changes involving Caret syntax or semantics, read the [established syntax reference](docs/agent/syntax.md) before editing.
Use the [repository map](docs/REPO_MAP.md) to find the relevant canonical section, implementation,
tests, and example without reading unrelated chapters.

## Implementation rules

* Use Java 21.
* Keep lexer, parser, AST, runtime values, evaluation, and diagnostics separated.
* Add automated tests for every syntax or semantic change.
* Preserve source locations in tokens and AST nodes.
* Diagnostics must include line and column information.
* Do not catch broad exceptions and convert them into vague interpreter errors.
* Do not use reflection from the Java implementation as a substitute for implementing Caret reflection semantics.
* Represent program reification with language-owned descriptors; never expose Java AST/runtime
  objects directly as Caret code metadata.
* Treat sandbox and capability changes as security-sensitive. Document the threat model and add
  adversarial interpreter/compiler tests for name lookup, reflection, imports, effects, retained
  references, and nested sandboxes before marking the feature implemented.
* Invoke Gradle either directly as `./gradlew <arguments...>` or, when a login shell is required,
  as `/bin/bash -lc './gradlew "$@"' bash <arguments...>`. Do not embed Gradle arguments,
  environment assignments, command chains, or substitutions inside the `bash -lc` program string;
  those command shapes do not match the project's persistent Gradle-only approval rules.
* Run the full test suite after changes.
* Update the canonical owning document under `spec/` whenever observable language behavior changes.
  Update `LANGUAGE.md` only for corpus navigation, global invariants, or terminology shared by the
  whole language.
* Update `WEB_INTRODUCTION.md` whenever a language feature is added or altered so the public-facing
  description and examples remain accurate.
* Include representative Caret programs as integration tests.
* For every newly implemented language feature, add or extend a runnable `.caret` example that
  demonstrates the feature, and exercise that example from the integration test suite.
* `LANGUAGE.md` and its linked `spec/` documents collectively remain the canonical language
  specification. After the Phase 13 documentation generator exists, do not hand-edit generated site
  pages; update these canonical inputs and regenerate them instead.
* The final documentation release must provide a MkDocs Material site split into approachable
  Markdown pages with left-pane navigation, plus a shared-source “Learn Caret in Y Minutes” entry.
  Published examples must be executable or explicitly labeled conceptual/planned.

## Change discipline

Before substantial implementation:

1. Inspect the existing implementation.
2. State what already works.
3. Identify conflicts between the requested feature and current grammar or semantics.
4. Propose a concrete implementation plan.
5. Implement in small testable stages.
6. Report tests run and remaining limitations.

## Testing

Run the baseline tests with:
```bash
GRADLE_USER_HOME="$PWD/.gradle-codex" ./gradlew test
GRADLE_USER_HOME="$PWD/.gradle-codex" ./test.sh
```

## Repeatable workflows

The following case-insensitive shorthand requests invoke repository-specific workflows. Extra text
after an alias may narrow or extend its stated scope. Each alias authorizes only the repository and
GitHub mutations explicitly listed for that workflow; it does not authorize unrelated external
actions. Preserve pre-existing user changes, and stop if they cannot be separated safely. Ask before
unresolved language-design decisions, destructive operations, major-version changes, or materially
broader scope.

Read the complete workflow file when its case-insensitive shorthand is requested; its ordinary and repeated variants share a file. These files retain the workflow authorization and completion rules:

* `next step`: [docs/agent/workflows/next-step.md](docs/agent/workflows/next-step.md)
* `code review`: [docs/agent/workflows/code-review.md](docs/agent/workflows/code-review.md)
* `fix bugs`: [docs/agent/workflows/fix-bugs.md](docs/agent/workflows/fix-bugs.md)
* `card details`: [docs/agent/workflows/card-details.md](docs/agent/workflows/card-details.md)
* `spec sync`: [docs/agent/workflows/spec-sync.md](docs/agent/workflows/spec-sync.md)
* `test coverage`: [docs/agent/workflows/test-coverage.md](docs/agent/workflows/test-coverage.md)
* `new PR`: [docs/agent/workflows/new-pr.md](docs/agent/workflows/new-pr.md)

After every repeatable workflow, report tests run, GitHub state changes, remaining limitations, and
links to created or updated issues and pull requests.
