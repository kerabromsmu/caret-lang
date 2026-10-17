<a id="rules-rulesets-and-objects"></a>
# Rules, Rulesets, and Objects

[Language specification index](../LANGUAGE.md) · [Conformance status](../CONFORMANCE.md)

## Release availability

The complete rules, rulesets, objects, and `ruleCycle` target follows v1 in
[Phase 8](../roadmap/phase-08.md), provisionally as 1.1. Approved incompatible changes require 2.0
instead. Rule phase contracts and the tracked container-read/purity interaction must be settled
before implementation; assigning a release does not settle those language-design questions.
SIMD, formats, semantic Code, staging, and a compiler backend are not prerequisites. Later
reflection/projection/backend interactions follow their owning phases.

The canonical text of this chapter is organized into the following sections:

- [Rules](sections/11-01-rules.md)
- [Rule ordering and chaining](sections/11-02-rule-ordering-and-chaining.md)
- [Rulesets](sections/11-03-rulesets.md)
- [Rule cycles and implementation requirements](sections/11-04-rulecycle.md)

## Previous section anchors

The links below preserve existing section URLs and point to the canonical text.

- <a id="overview"></a> [overview](../spec/sections/11-01-rules.md#overview)
- <a id="rules"></a> [rules](../spec/sections/11-01-rules.md#rules)
- <a id="basic-definition"></a> [basic-definition](../spec/sections/11-01-rules.md#basic-definition)
- <a id="context"></a> [context](../spec/sections/11-01-rules.md#context)
- <a id="context-fronts"></a> [context-fronts](../spec/sections/11-01-rules.md#context-fronts)
- <a id="changing-contexts"></a> [changing-contexts](../spec/sections/11-01-rules.md#changing-contexts)
- <a id="active-state"></a> [active-state](../spec/sections/11-01-rules.md#active-state)
- <a id="trigger"></a> [trigger](../spec/sections/11-01-rules.md#trigger)
- <a id="fronts-in-triggers"></a> [fronts-in-triggers](../spec/sections/11-01-rules.md#fronts-in-triggers)
- <a id="context-and-active-state-are-gates"></a> [context-and-active-state-are-gates](../spec/sections/11-01-rules.md#context-and-active-state-are-gates)
- <a id="effect"></a> [effect](../spec/sections/11-01-rules.md#effect)
- <a id="effect-inference"></a> [effect-inference](../spec/sections/11-01-rules.md#effect-inference)
- <a id="name"></a> [name](../spec/sections/11-01-rules.md#name)
- <a id="optional-caten-components"></a> [optional-caten-components](../spec/sections/11-01-rules.md#optional-caten-components)
- <a id="implicit-rule-context"></a> [implicit-rule-context](../spec/sections/11-01-rules.md#implicit-rule-context)
- <a id="rule-ordering"></a> [rule-ordering](../spec/sections/11-02-rule-ordering-and-chaining.md#rule-ordering)
- <a id="unordered-rules"></a> [unordered-rules](../spec/sections/11-02-rule-ordering-and-chaining.md#unordered-rules)
- <a id="effects-affect-subsequent-scheduling"></a> [effects-affect-subsequent-scheduling](../spec/sections/11-02-rule-ordering-and-chaining.md#effects-affect-subsequent-scheduling)
- <a id="unordered-rule-diagnostics"></a> [unordered-rule-diagnostics](../spec/sections/11-02-rule-ordering-and-chaining.md#unordered-rule-diagnostics)
- <a id="explicit-acknowledgement-of-unordered-execution"></a> [explicit-acknowledgement-of-unordered-execution](../spec/sections/11-02-rule-ordering-and-chaining.md#explicit-acknowledgement-of-unordered-execution)
- <a id="enforcing-order"></a> [enforcing-order](../spec/sections/11-02-rule-ordering-and-chaining.md#enforcing-order)
- <a id="rule-chaining"></a> [rule-chaining](../spec/sections/11-02-rule-ordering-and-chaining.md#rule-chaining)
- <a id="explicit-chain"></a> [explicit-chain](../spec/sections/11-02-rule-ordering-and-chaining.md#explicit-chain)
- <a id="chain-sugar"></a> [chain-sugar](../spec/sections/11-02-rule-ordering-and-chaining.md#chain-sugar)
- <a id="explicit-trigger-in-a-chain"></a> [explicit-trigger-in-a-chain](../spec/sections/11-02-rule-ordering-and-chaining.md#explicit-trigger-in-a-chain)
- <a id="partial-ordering"></a> [partial-ordering](../spec/sections/11-02-rule-ordering-and-chaining.md#partial-ordering)
- <a id="rulesets"></a> [rulesets](../spec/sections/11-03-rulesets.md#rulesets)
- <a id="overview-2"></a> [overview-2](../spec/sections/11-03-rulesets.md#overview-2)
- <a id="overview-1"></a> [overview-1](../spec/sections/11-03-rulesets.md#overview-1)
- <a id="ruleset-templates"></a> [ruleset-templates](../spec/sections/11-03-rulesets.md#ruleset-templates)
- <a id="ruleset-encapsulation"></a> [ruleset-encapsulation](../spec/sections/11-03-rulesets.md#ruleset-encapsulation)
- <a id="exported-rules"></a> [exported-rules](../spec/sections/11-03-rulesets.md#exported-rules)
- <a id="ruleset-instances"></a> [ruleset-instances](../spec/sections/11-03-rulesets.md#ruleset-instances)
- <a id="nested-rulesets"></a> [nested-rulesets](../spec/sections/11-03-rulesets.md#nested-rulesets)
- <a id="rulecycle"></a> [rulecycle](../spec/sections/11-04-rulecycle.md#rulecycle)
- <a id="overview-3"></a> [overview-3](../spec/sections/11-04-rulecycle.md#overview-3)
- <a id="initialization"></a> [initialization](../spec/sections/11-04-rulecycle.md#initialization)
- <a id="installing-rulesets"></a> [installing-rulesets](../spec/sections/11-04-rulecycle.md#installing-rulesets)
- <a id="template-based-system-construction"></a> [template-based-system-construction](../spec/sections/11-04-rulecycle.md#template-based-system-construction)
- <a id="master-cycle-context"></a> [master-cycle-context](../spec/sections/11-04-rulecycle.md#master-cycle-context)
- <a id="object-traversal"></a> [object-traversal](../spec/sections/11-04-rulecycle.md#object-traversal)
- <a id="rule-scheduling"></a> [rule-scheduling](../spec/sections/11-04-rulecycle.md#rule-scheduling)
- <a id="no-source-order-guarantee"></a> [no-source-order-guarantee](../spec/sections/11-04-rulecycle.md#no-source-order-guarantee)
- <a id="propagation-to-stability"></a> [propagation-to-stability](../spec/sections/11-04-rulecycle.md#propagation-to-stability)
- <a id="trigger-stability"></a> [trigger-stability](../spec/sections/11-04-rulecycle.md#trigger-stability)
- <a id="object-creation-and-destruction"></a> [object-creation-and-destruction](../spec/sections/11-04-rulecycle.md#object-creation-and-destruction)
- <a id="dynamic-rule-state"></a> [dynamic-rule-state](../spec/sections/11-04-rulecycle.md#dynamic-rule-state)
- <a id="cycle-termination"></a> [cycle-termination](../spec/sections/11-04-rulecycle.md#cycle-termination)
- <a id="relationship-to-ordinary-cycle"></a> [relationship-to-ordinary-cycle](../spec/sections/11-04-rulecycle.md#relationship-to-ordinary-cycle)
- <a id="implementation-requirements"></a> [implementation-requirements](../spec/sections/11-04-rulecycle.md#implementation-requirements)
- <a id="design-principle"></a> [design-principle](../spec/sections/11-04-rulecycle.md#design-principle)
