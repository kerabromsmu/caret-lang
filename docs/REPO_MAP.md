# Caret repository map

This is a navigation aid. [`LANGUAGE.md`](../LANGUAGE.md) and its linked `spec/` sections own language
semantics; [`CONFORMANCE.md`](../CONFORMANCE.md) owns implementation status and test evidence.
Read the linked section for the feature at hand, then its implementation and tests.

| Area | Canonical starting point | Main implementation | Focused tests and examples |
|---|---|---|---|
| Source layout and parsing | [Source and diagnostics](../spec/01-source-layout-and-diagnostics.md) | `Lexer`, `Parser`, `ExpressionParser` | `LexerTest`, `ParserTest`, `examples/features/layout_mapping.caret` |
| Values and lexical binding | [Values and evaluation](../spec/02-values-bindings-and-evaluation.md) | `Value`, `Environment`, `Resolver`, `Interpreter` | `CoreCallableInterpreterTest`, `ValueTest`, `ResolverTest` |
| Functions and effects | [Functions](../spec/03-functions-operators-and-lambdas.md), [contracts](../spec/04-contracts-inference-and-dispatch.md), [effects](../spec/05-effects-and-callable-signatures.md) | `CallableSignature`, `ContractInference`, `Interpreter` | `ContractInterpreterTest`, `ReflectionContractInterpreterTest`, `examples/features/contracts.caret` |
| Collections and templates | [Collection chapter](../spec/06-collections-fields-and-templates.md) | `CollectionRuntime`, `Value`, `TemplateContract`, `Interpreter` | `CollectionStateInterpreterTest`, `NumericTemplateInterpreterTest`, `examples/features/templates.caret` |
| Numeric, conversion, and packed values | [Packed layouts](../spec/sections/06-02-packed-layouts.md), [numeric contracts](../spec/04-contracts-inference-and-dispatch.md) | `NumericValues`, `PackedLayout`, `Interpreter` | `ConversionInterpreterTest`, `NumericTemplateInterpreterTest`, `PackedLayoutTest` |
| Containers, scoped lookup, and `$` | [State and scoped lookup](../spec/07-state-containers-and-scoped-lookup.md) | `OwnershipTracker`, `Interpreter` | `CollectionStateInterpreterTest`, `EagerScopedInterpreterTest`, `examples/features/with_outer.caret` |
| Reflection and embedding | [Modules and reflection](../spec/12-modules-reflection-and-code.md), [sandbox security](../spec/13-sandboxes-and-security.md) | `EmbeddingBridge`, `embedding/CaretSandbox`, `Interpreter` | `CaretSandboxTest`, `CoreCallableInterpreterTest`, `examples/embedding.caret` |
| Future language areas | [Specification index](../LANGUAGE.md) | See the linked canonical section and [roadmap](../PLAN.md) | Follow the relevant [conformance group](../CONFORMANCE.md) |

The large `Interpreter.java` is still the runtime coordinator. Search for the behavior or method
named in a focused test and read that range instead of loading the whole class.
