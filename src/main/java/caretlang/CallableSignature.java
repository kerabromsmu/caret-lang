package caretlang;

import caretlang.Ast.ContractClause;
import caretlang.Ast.ContractName;
import caretlang.Ast.FunctionDef;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Language-owned callable metadata. Runtime callables expose only immutable projections of this model. */
public record CallableSignature(List<Parameter> parameters, Result result, Effects effects,
                                List<Variable> variables) {
    public record Parameter(String name, List<String> requirements, List<String> declared,
                            List<String> inferred) {
        public Parameter {
            requirements = copy(requirements);
            declared = nullableCopy(declared);
            inferred = nullableCopy(inferred);
        }
    }

    public record Result(List<String> guarantees, List<String> declared, List<String> inferred) {
        public Result {
            guarantees = copy(guarantees);
            declared = nullableCopy(declared);
            inferred = nullableCopy(inferred);
        }
    }

    public record Effects(List<String> upperBound, List<String> declared, List<String> inferred) {
        public Effects {
            upperBound = nullableCopy(upperBound);
            declared = nullableCopy(declared);
            inferred = nullableCopy(inferred);
        }
    }

    public record Variable(int index, List<String> requirements) {
        public Variable { requirements = copy(requirements); }
    }

    public CallableSignature {
        parameters = List.copyOf(parameters);
        variables = List.copyOf(variables);
    }

    static CallableSignature unknown(List<String> names) {
        return new CallableSignature(names.stream()
                .map(name -> new Parameter(name, List.of(), null, null)).toList(),
                new Result(List.of(), null, null), new Effects(null, null, null), List.of());
    }

    static CallableSignature builtin(List<String> names, List<String> effects) {
        return new CallableSignature(names.stream()
                .map(name -> new Parameter(name, List.of(), null, null)).toList(),
                new Result(List.of(), null, null), new Effects(effects, null, effects), List.of());
    }

    static CallableSignature inferred(FunctionDef function, ContractInference inference) {
        ContractInference.FunctionContract facts = inference.contract(function);
        ContractInference.EffectSummary effectFacts = inference.effects(function);
        ArrayList<Parameter> parameters = new ArrayList<>();
        for (int index = 0; index < function.params().size(); index++) {
            List<String> declared = names(function.params().get(index).contracts());
            List<String> inferred = facts == null ? List.of() : sorted(facts.parameterRequirements().get(index));
            parameters.add(new Parameter(function.params().get(index).name(), union(declared, inferred),
                    function.params().get(index).contracts() == null ? null : declared, inferred));
        }
        List<String> declaredResult = names(function.resultContracts());
        List<String> inferredResult = facts == null ? List.of() : sorted(facts.resultGuarantees());
        ArrayList<Variable> variables = new ArrayList<>();
        if (facts != null && facts.resultParameter() != null) {
            inferredResult = List.of("_1");
            int parameterIndex = facts.resultParameter();
            Parameter parameter = parameters.get(parameterIndex);
            parameters.set(parameterIndex, new Parameter(parameter.name(),
                    union(parameter.requirements(), List.of("_1")), parameter.declared(),
                    union(parameter.inferred(), List.of("_1"))));
            variables.add(new Variable(0, parameter.requirements()));
        }
        List<String> effects = effectFacts == null || effectFacts.unknownDynamicCall() ? null
                : effectFacts.effects().stream().map(CallableSignature::effectName).sorted().toList();
        return new CallableSignature(parameters,
                new Result(union(declaredResult, inferredResult),
                        function.resultContracts() == null ? null : declaredResult, inferredResult),
                new Effects(effects, null, effects), variables);
    }

    CallableSignature dropFirst() {
        return parameters.isEmpty() ? this
                : new CallableSignature(parameters.subList(1, parameters.size()), result, effects, variables);
    }

    CallableSignature withParameters(List<Parameter> newParameters) {
        return new CallableSignature(newParameters, result, effects, variables);
    }

    static CallableSignature compose(CallableSignature left, CallableSignature right) {
        return new CallableSignature(left.parameters, right.result,
                unionEffects(left.effects, right.effects), List.of());
    }

    static CallableSignature summarize(List<CallableSignature> variants) {
        if (variants.size() == 1) return variants.getFirst();
        int arity = variants.getFirst().parameters.size();
        ArrayList<Parameter> parameters = new ArrayList<>();
        for (int index = 0; index < arity; index++) parameters.add(new Parameter(null, List.of(), null, null));
        List<String> commonResults = new ArrayList<>(variants.getFirst().result.guarantees);
        for (CallableSignature variant : variants.subList(1, variants.size())) {
            commonResults.retainAll(variant.result.guarantees);
        }
        Effects effects = variants.getFirst().effects;
        for (CallableSignature variant : variants.subList(1, variants.size())) {
            effects = unionEffects(effects, variant.effects);
        }
        return new CallableSignature(parameters, new Result(commonResults, null, commonResults), effects, List.of());
    }

    private static Effects unionEffects(Effects left, Effects right) {
        List<String> upper = left.upperBound == null || right.upperBound == null
                ? null : union(left.upperBound, right.upperBound);
        List<String> inferred = left.inferred == null || right.inferred == null
                ? null : union(left.inferred, right.inferred);
        return new Effects(upper, null, inferred);
    }

    private static List<String> names(ContractClause clause) {
        return clause == null ? List.of() : clause.names().stream().map(CallableSignature::name).toList();
    }

    private static String name(ContractName contract) {
        String arguments = contract.arguments().isEmpty() ? "" : " "
                + String.join(" ", contract.arguments().stream().map(CallableSignature::name).toList());
        return contract.name() + arguments + (contract.nullable() ? "?" : "")
                + (contract.optional() ? "~" : "");
    }

    private static List<String> sorted(Iterable<? extends ContractDescriptor> contracts) {
        ArrayList<String> result = new ArrayList<>();
        contracts.forEach(contract -> result.add(contract.publicName()));
        return result.stream().sorted().toList();
    }

    private static String effectName(ContractInference.BuiltinEffect effect) {
        return switch (effect) {
            case OUTPUT -> "Output";
            case TEST_REPORT -> "TestReport";
        };
    }

    private static List<String> union(List<String> left, List<String> right) {
        LinkedHashSet<String> values = new LinkedHashSet<>(left);
        values.addAll(right);
        return List.copyOf(values);
    }

    private static <T> List<T> copy(List<T> values) { return List.copyOf(values); }
    private static <T> List<T> nullableCopy(List<T> values) { return values == null ? null : List.copyOf(values); }
}
