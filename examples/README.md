# Caret example coverage

The example suite mirrors every requirement currently marked `implemented` in
[`CONFORMANCE.md`](../CONFORMANCE.md). Successful behavior is demonstrated by
[`implemented_features.caret`](implemented_features.caret) and asserted by
[`implemented_features_test.caret`](implemented_features_test.caret). Each `.caret` file under
`errors/` contains one intentional failure, has an adjacent `.expected` file containing its exact
stderr, and is exercised by `test.sh`. [The diagnostic matrix](../DIAGNOSTICS.md) also records
internal and host variants that require focused Java or host-level tests. Every public diagnostic
variant has executable `.caret` evidence.

| Conformance requirements | Example coverage |
|---|---|
| `CORE-VAL-001`, `CORE-VAL-002`, `CORE-TEXT-001`, `CORE-COMMENT-001` | Scalar, name, escape, and comment sections in the successful tour; `errors/invalid_escape.caret` |
| `CORE-NUM-001` | Arithmetic section; `errors/division_by_zero.caret`, `errors/remainder_by_zero.caret`, `errors/non_finite_result.caret` |
| `CORE-DIAG-001`, `CORE-DIAG-002` | All `errors/` fixtures; `errors/duplicate_definition.caret` demonstrates a related span |
| `CORE-TEST-001` | `implemented_features_test.caret` and `testing.caret` |
| `CORE-BIND-001`, `CORE-BIND-002` | Binding, closure, direct-recursion, and mutual-recursion sections |
| `CORE-BIND-003` | Lazy branches in the successful tour; `errors/duplicate_definition.caret`, `errors/reserved_binding.caret`, and `errors/read_before_initialization.caret` |
| `CORE-CALL-001`, `CORE-PRINT-001` | Application/precedence sections, including `print 2 joinDigits 3` |
| `CORE-CALL-002`, `CORE-CALL-003` | Grouped lookup and grouped/ungrouped multiline sections |
| `CORE-CALL-004` | Nullary invocation and function-reflection sections; `errors/call_depth.caret` |
| `CORE-LOWAPP-001`, `CORE-LOWAPP-002` | Right-associative `$` examples with arithmetic and conditional right operands; `errors/missing_dollar_operand.caret` |
| `CORE-COND-001`, `CORE-BOOL-001` | Short-circuit Boolean and conditional section |
| `CORE-PART-001`, `CORE-PART-002` | Ordinary/numbered hole section; `errors/mixed_holes.caret` |
| `CORE-SCOPE-001`, `CORE-SCOPE-002`, `CORE-SCOPE-003` | Exported-scope and lookup sections; missing-field and invalid-key fixtures |
| `CORE-EQ-001` | Scope/collection equality sections; `errors/callable_equality.caret` |
| `CORE-REFLECT-001` | Scope, scalar, operator, and function reflection section |
| `CORE-INFIX-001` | Named infix precedence, associativity, partial, and callable-parameter examples; invalid-target/arity fixtures |
| `CORE-INFIX-002` | Symbolic prefix, infix, grouped subtraction, and symbolic partial examples |
| `CORE-COMP-001` | Left-to-right pipelines, chaining, partial left operands, reflection, and invalid operand/arity fixtures |
| `CONTRACT-CORE-001` | `contracts.caret`; built-in predicates, binding/parameter clauses, partial application, and reflection |
| `CONTRACT-INFER-001` | `contract_inference.caret`; generalized identity flow, numeric constraints, and conditional joins; incompatible-constraint fixture |
| `CORE-TEXT-002` | Unicode code-point indexing/slicing and conversion section |
| `CORE-COLL-001`, `CORE-COLL-002` | Persistent sequence/dictionary, safe lookup, replacement-order, and nested equality sections |

Run all examples with:

```bash
./test.sh
```
