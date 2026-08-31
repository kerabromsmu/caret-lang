# Caret example coverage

The example suite mirrors every requirement currently marked `implemented` in
[`CONFORMANCE.md`](../CONFORMANCE.md). Successfully executable behavior is asserted comprehensively
by [`implemented_features_test.caret`](implemented_features_test.caret) and independently demonstrated
by the focused scripts under `features/`. Each `.caret` file under
`errors/` contains one intentional failure, has an adjacent `.expected` file containing its exact
stderr, and is exercised by `test.sh`. [The diagnostic matrix](../DIAGNOSTICS.md) also records
internal and host variants that require focused Java or host-level tests. Every public diagnostic
variant has executable `.caret` evidence. `scripts/check-example-coverage.sh` rejects implemented
conformance rows without an existing Caret example and fixtures not exercised by the harness.

| Conformance requirements | Example coverage |
|---|---|
| `CORE-VAL-001`, `CORE-VAL-002`, `CORE-TEXT-001`, `CORE-COMMENT-001` | Scalar, name, escape, and comment sections in the successful tour; `errors/invalid_escape.caret` |
| `CORE-NUM-001` | Arithmetic section; `errors/division_by_zero.caret`, `errors/remainder_by_zero.caret`, `errors/non_finite_result.caret` |
| `CORE-DIAG-001`, `CORE-DIAG-002`, `CORE-DIAG-003` | All `errors/` fixtures; duplicate-definition and nested-expression fixtures demonstrate related spans and bounded diagnostics |
| `CORE-TEST-001` | `implemented_features_test.caret` and `testing.caret` |
| `CORE-BIND-001`, `CORE-BIND-002`, `CORE-CAPTURE-001` | Binding, closure, direct-recursion, mutual-recursion, and captured partial sections |
| `CORE-BIND-003` | Lazy branches in the successful tour; `errors/duplicate_definition.caret`, `errors/reserved_binding.caret`, and `errors/read_before_initialization.caret` |
| `CORE-CALL-001`, `CORE-PRINT-001` | Application/precedence sections, including `print 2 joinDigits 3` |
| `CORE-CALL-002`, `CORE-CALL-003` | Grouped lookup and grouped/ungrouped multiline sections |
| `CORE-CALL-004` | Nullary invocation and function-reflection sections; `errors/call_depth.caret` |
| `CORE-LOWAPP-001`, `CORE-LOWAPP-002` | Right-associative `$` examples with arithmetic and conditional right operands; `errors/missing_dollar_operand.caret` |
| `CORE-COND-001`, `CORE-BOOL-001` | Short-circuit Boolean and conditional section |
| `CORE-PART-001`, `CORE-PART-002` | Ordinary/numbered hole section; `errors/mixed_holes.caret` |
| `CORE-SCOPE-001`, `CORE-SCOPE-002`, `CORE-SCOPE-003` | Named Collection export and lookup sections; missing-field and invalid-key fixtures |
| `CORE-EQ-001` | Named Collection and positional collection equality sections; `errors/callable_equality.caret` |
| `CORE-REFLECT-001` | Collection, scalar, operator, and function reflection section |
| `CORE-INFIX-001` | Named infix precedence, associativity, partial, and callable-parameter examples; invalid-target/arity fixtures |
| `CORE-INFIX-002` | Symbolic prefix, infix, grouped subtraction, and symbolic partial examples |
| `CORE-COMP-001` | Left-to-right pipelines, chaining, partial left operands, reflection, and invalid operand/arity fixtures |
| `LAYOUT-MAP-001`, `LAYOUT-BASE-001`, `LAYOUT-RESTORE-001`, `LAYOUT-STACK-001`, `LAYOUT-PLACE-001` | `features/layout_mapping.caret` and the master layout-mapping assertion |
| `CONTRACT-001`, `CONTRACT-CORE-001`, `CONTRACT-003`, `CONTRACT-005`, `CONTRACT-006`, `CONTRACT-IMPLY-001`, `CONTRACT-VARIANCE-001` | `contracts.caret`; built-in, forward multiple-base derivation, nullable, optional, and parameterized `Sequence T` predicates, binding/parameter/result clauses, nesting, partial application, reflection, and the derivation-cycle error fixture |
| `CONTRACT-004`, `CLAUSE-PARAM-001`, `CLAUSE-ASSIGN-001`, `CLAUSE-RESOLVE-001`, `CLAUSE-DIAG-001`, `EFFECT-CATALOG-001` | `features/effects.caret`; mixed clauses, explicit allowances, pure callable constraints, normalization, diagnostics, and reflection |
| `CONTRACT-002` | `refinements.caret`; proven-pure predicate requirements and the invalid-refinement fixture |
| `CONTRACT-INFER-001` | `contract_inference.caret`; declared/inferred separation, generalized identity flow, numeric constraints, string `+`, truth semantics, and conditional joins; incompatible-constraint fixtures |
| `OPERATOR-CONTRACT-001`, `OPERATOR-PLUS-001`, `OPERATOR-EQ-001`, `OPERATOR-TRUTH-001`, `OPERATOR-INFER-001`, `OPERATOR-DIAG-001` | `features/operators.caret`; numeric and concatenation variants, language rendering, structural equality, normalized lazy truth, reflected signatures, and established operator error fixtures |
| `DATA-HOLE-001` | `features/collection_constructors.caret`; collection-owned structural holes, nesting, fields, eager captures, contracted and repeated numbered holes, and callable signature reflection |
| `CALL-SIG-001`, `CALL-SIG-002`, `CALL-SIG-PART-001`, `CALL-SIG-COMP-001`, `CALL-SIG-EFFECT-001` | `features/callable_reflection.caret`, `features/arrow_contracts.caret`, and `features/effects.caret`; declared/inferred schemes and derived specialization |
| `CALL-CONTRACT-SYNTAX-001`, `CALL-CONTRACT-VAR-001`, `CALL-CONTRACT-SUBTYPE-001`, `CALL-CONTRACT-PRED-001` | `features/arrow_contracts.caret`; exact arity, generalized variables, variance, effects, and observational predicates |
| `CALL-REFLECT-SCHEMA-001`, `CALL-REFLECT-DERIVED-001`, `CALL-REFLECT-OVERLOAD-001`, `CALL-REFLECT-IDENTITY-001`, `CALL-REFLECT-SAFE-001` | `features/callable_reflection.caret`; fixed schema, partials, compositions, overload survivors, identity, and safe dereference |
| `CALL-REFLECT-VIS-001` | `features/callable_reflection.caret` demonstrates defining-environment projection; `InterpreterTest#callableReflectionProjectsLazilyWithoutAmplifyingVisibilityOrAuthority` covers external/sandbox projection because observation contexts are intentionally not Caret values |
| `DISPATCH-001`, `DISPATCH-SPEC-001`, `DISPATCH-DECL-001`, `DISPATCH-APPLY-001`, `DISPATCH-APPLY-003`, `DISPATCH-DIAG-001` | `features/overloads.caret` plus inconsistent, missing, and ambiguous overload error fixtures |
| `DISPATCH-PART-001`, `DISPATCH-PART-002`, `DISPATCH-PART-003`, `DISPATCH-SIG-001` | Overload partial narrowing in `features/overloads.caret` and survivor metadata in `features/callable_reflection.caret` |
| `EFFECT-002` foundation | Focused Java tests cover transitive named-call effects, eager partial captures, lexical identity, reflection, and conservative dynamic calls; the full public effect requirement remains planned |
| `CORE-TEXT-002` | Unicode code-point indexing/slicing and conversion section |
| `CORE-COLL-001`, `CORE-COLL-002` | Persistent sequence/dictionary, safe lookup, replacement-order, and nested equality sections |
| `CORE-MAP-001` | `features/map.caret`; empty, named, partial, composed, and identity transforms |
| `CORE-RENDER-001`, `CORE-RENDER-002` | `features/rendering.caret`; overload selection and recursive default rendering |
| `DATA-COLL-001`, `DATA-001`, `DATA-002`, `DATA-005`, `DATA-COLL-002`, `DATA-COLL-007`, `DATA-COLL-004` | Collection sections in the master test, `features/collection_order.caret`, and the mixed-shape error fixture |

Run all examples with:

```bash
./test.sh
```
