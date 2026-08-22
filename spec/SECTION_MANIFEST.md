# Language specification section manifest

[Language specification index](../LANGUAGE.md)

This manifest records the lossless structural migration from the former 13,639-line monolithic
`LANGUAGE.md`. Line numbers refer to commit `f9957a9`, the source immediately before the split.
Document titles, navigation links, and normalized heading levels added during migration are not
part of the source ranges.

| Destination | Former line ranges |
|---|---|
| `01-source-layout-and-diagnostics.md` | 32–81, 433–448, 542–572, 13306–13577 |
| `02-values-bindings-and-evaluation.md` | 9–31, 268–293, 330–398, 573–631 |
| `03-functions-operators-and-lambdas.md` | 82–97, 211–267, 294–329, 399–426, 471–541, 4060–4799 |
| `04-contracts-inference-and-dispatch.md` | 98–210, 683–1292 |
| `05-effects-and-callable-signatures.md` | 2163–2699 |
| `06-collections-fields-and-templates.md` | 449–470, 637–682, 1293–2162, 8561–9914 |
| `07-state-containers-and-scoped-lookup.md` | 9915–12128 |
| `08-cycles.md` | 4800–5724 |
| `09-simd.md` | 2701–2995 |
| `10-formats-and-codecs.md` | 2996–4059 |
| `11-rules-rulesets-and-objects.md` | 5725–7216 |
| `12-modules-reflection-and-code.md` | 7217–7709 |
| `13-sandboxes-and-security.md` | 7710–8560 |
| `14-staging-compilation-and-compatibility.md` | 12129–13305, 13578–13639 |

The former document-level titles on lines 7, 427, and 632 were replaced by the new index and
per-document titles. Lines 1–6 were replaced by the expanded normative introduction. No substantive
feature text was intentionally discarded.
