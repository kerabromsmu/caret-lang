package caretlang;

import caretlang.Ast.FunctionDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Language-owned callable metadata. Runtime callables expose only immutable projections of this model. */
public record CallableSignature(List<Parameter> parameters, Result result, Effects effects,
                                List<Variable> variables) {
    enum Compatibility { COMPATIBLE, INCOMPATIBLE, UNKNOWN }
    record Composition(CallableSignature signature, Compatibility compatibility) {}
    public sealed interface ContractTerm permits NamedRef, VariableRef, AppliedRef, ModifiedRef, ArrowRef {
        String render();
    }
    public record NamedRef(String name) implements ContractTerm {
        @Override public String render() { return name; }
    }
    /** Canonical zero-based scheme-local variable reference. */
    public record VariableRef(int index) implements ContractTerm {
        @Override public String render() { return "_" + (index + 1); }
    }
    public record AppliedRef(ContractTerm constructor, List<ContractTerm> arguments) implements ContractTerm {
        public AppliedRef { arguments = List.copyOf(arguments); }
        @Override public String render() {
            return constructor.render() + " " + String.join(" ", arguments.stream()
                    .map(argument -> argument instanceof AppliedRef ? "(" + argument.render() + ")"
                            : argument.render()).toList());
        }
    }
    public record ModifiedRef(ContractTerm base, boolean nullable, boolean optional) implements ContractTerm {
        @Override public String render() { return base.render() + (nullable ? "?" : "") + (optional ? "~" : ""); }
    }
    public record ArrowRef(List<List<ContractTerm>> parameters, ContractTerm result) implements ContractTerm {
        public ArrowRef { parameters = parameters.stream().map(List::copyOf).toList(); }
        @Override public String render() {
            String left = String.join(" ", parameters.stream().map(requirements -> requirements.size() == 1
                    ? requirements.getFirst().render() : "(" + String.join(" ", requirements.stream()
                    .map(ContractTerm::render).toList()) + ")").toList());
            return "[" + left + "] -> " + result.render();
        }
    }

    public record Parameter(String name, List<ContractTerm> requirements, List<ContractTerm> declared,
                            List<ContractTerm> inferred) {
        public Parameter {
            requirements = copy(requirements);
            declared = nullableCopy(declared);
            inferred = nullableCopy(inferred);
        }
    }

    public record Result(List<ContractTerm> guarantees, List<ContractTerm> declared, List<ContractTerm> inferred) {
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

    public record Variable(int index, List<ContractTerm> requirements) {
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

    static CallableSignature inferred(FunctionDef function, ContractInference inference, Resolution resolution) {
        ContractInference.FunctionContract facts = inference.contract(function);
        ContractInference.EffectSummary effectFacts = inference.effects(function);
        ArrayList<Parameter> parameters = new ArrayList<>();
        for (int index = 0; index < function.params().size(); index++) {
            List<ContractTerm> declared = terms(resolution.clause(function.params().get(index).contracts()));
            List<ContractTerm> inferred = facts == null ? List.of() : sorted(facts.parameterRequirements().get(index));
            parameters.add(new Parameter(function.params().get(index).name(), union(declared, inferred),
                    function.params().get(index).contracts() == null ? null : declared, inferred));
        }
        List<ContractTerm> declaredResult = terms(resolution.clause(function.resultContracts()));
        List<ContractTerm> inferredResult = facts == null ? List.of() : sorted(facts.resultGuarantees());
        ArrayList<Variable> variables = new ArrayList<>();
        if (facts != null && facts.resultParameter() != null) {
            inferredResult = List.of(new VariableRef(0));
            int parameterIndex = facts.resultParameter();
            Parameter parameter = parameters.get(parameterIndex);
            parameters.set(parameterIndex, new Parameter(parameter.name(),
                    union(parameter.requirements(), List.of(new VariableRef(0))), parameter.declared(),
                    union(parameter.inferred(), List.of(new VariableRef(0)))));
            variables.add(new Variable(0, parameter.requirements()));
        }
        for (int variable : headerVariables(function, resolution)) {
            if (variables.stream().noneMatch(existing -> existing.index() == variable)) {
                variables.add(new Variable(variable, variableRequirements(variable, parameters, declaredResult)));
            }
        }
        List<String> effects = effectFacts == null || effectFacts.unknownDynamicCall() ? null
                : effectFacts.effects().stream().map(CallableSignature::effectName).sorted().toList();
        Resolution.AnalyzedClause analyzed = resolution.clause(function.resultContracts());
        List<String> declaredEffects = analyzed == null || analyzed.effectAllowance() == null
                ? List.of() : analyzed.effectAllowance().stream().map(EffectDescriptor::canonicalName).sorted().toList();
        return new CallableSignature(parameters,
                new Result(union(declaredResult, inferredResult),
                        function.resultContracts() == null ? null : declaredResult, inferredResult),
                new Effects(declaredEffects, declaredEffects, effects), variables);
    }

    CallableSignature dropFirst() {
        return parameters.isEmpty() ? this
                : new CallableSignature(parameters.subList(1, parameters.size()), result, effects, variables);
    }

    CallableSignature specializeFirst(Value value) {
        if (parameters.isEmpty()) return this;
        Map<Integer, ContractTerm> substitutions = substitutions(parameters.getFirst().requirements(), value);
        CallableSignature specialized = substitutions.isEmpty() ? this : substitute(substitutions);
        return specialized.dropFirst();
    }

    CallableSignature specializeParameter(int index, Value value) {
        if (index < 0 || index >= parameters.size()) return this;
        Map<Integer, ContractTerm> substitutions = substitutions(parameters.get(index).requirements(), value);
        return substitutions.isEmpty() ? this : substitute(substitutions);
    }

    CallableSignature withParameters(List<Parameter> newParameters) {
        return new CallableSignature(newParameters, result, effects, retainedVariables(newParameters, result, variables));
    }

    CallableSignature projectParameters(List<List<Integer>> sourcePositions) {
        ArrayList<Parameter> projected = new ArrayList<>();
        for (List<Integer> positions : sourcePositions) {
            List<Parameter> sources = positions.stream().filter(index -> index >= 0 && index < parameters.size())
                    .map(parameters::get).toList();
            projected.add(mergeParameters(sources));
        }
        return withParameters(projected);
    }

    static Composition compose(CallableSignature left, CallableSignature right) {
        if (right.parameters.isEmpty()) {
            return new Composition(new CallableSignature(left.parameters, right.result,
                    unionEffects(left.effects, right.effects), retainedVariables(
                    left.parameters, right.result, left.variables)), Compatibility.UNKNOWN);
        }

        int rightOffset = left.variables.stream().mapToInt(Variable::index).max().orElse(-1) + 1;
        CallableSignature shiftedRight = rightOffset == 0 ? right : right.remapVariables(rightOffset);
        LinkedHashMap<Integer, ContractTerm> substitutions = new LinkedHashMap<>();
        Compatibility compatibility = bridge(left.result.guarantees,
                shiftedRight.parameters.getFirst().requirements, substitutions);
        CallableSignature specializedLeft = substitutions.isEmpty() ? left : left.substitute(substitutions);
        CallableSignature specializedRight = substitutions.isEmpty() ? shiftedRight : shiftedRight.substitute(substitutions);
        List<Variable> combinedVariables = union(specializedLeft.variables, specializedRight.variables);
        CallableSignature signature = new CallableSignature(specializedLeft.parameters, specializedRight.result,
                unionEffects(specializedLeft.effects, specializedRight.effects), retainedVariables(
                specializedLeft.parameters, specializedRight.result, combinedVariables));
        return new Composition(signature, compatibility);
    }

    static CallableSignature summarize(List<CallableSignature> variants) {
        if (variants.size() == 1) return variants.getFirst();
        int arity = variants.getFirst().parameters.size();
        ArrayList<Parameter> parameters = new ArrayList<>();
        for (int index = 0; index < arity; index++) parameters.add(new Parameter(null, List.of(), null, null));
        List<ContractTerm> commonResults = new ArrayList<>(variants.getFirst().result.guarantees);
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

    private static Parameter mergeParameters(List<Parameter> sources) {
        if (sources.isEmpty()) return new Parameter(null, List.of(), null, null);
        String name = sources.size() == 1 ? sources.getFirst().name : null;
        List<ContractTerm> requirements = List.of();
        List<ContractTerm> declared = null;
        List<ContractTerm> inferred = null;
        for (Parameter source : sources) {
            requirements = union(requirements, source.requirements);
            declared = unionNullable(declared, source.declared);
            inferred = unionNullable(inferred, source.inferred);
        }
        return new Parameter(name, requirements, declared, inferred);
    }

    private static <T> List<T> unionNullable(List<T> left, List<T> right) {
        if (left == null) return right == null ? null : List.copyOf(right);
        if (right == null) return left;
        return union(left, right);
    }

    private CallableSignature remapVariables(int offset) {
        LinkedHashMap<Integer, ContractTerm> replacements = new LinkedHashMap<>();
        variables.forEach(variable -> replacements.put(variable.index, new VariableRef(variable.index + offset)));
        if (replacements.isEmpty()) return this;
        List<Parameter> remappedParameters = parameters.stream().map(parameter -> new Parameter(parameter.name,
                substitute(parameter.requirements, replacements), substituteNullable(parameter.declared, replacements),
                substituteNullable(parameter.inferred, replacements))).toList();
        Result remappedResult = new Result(substitute(result.guarantees, replacements),
                substituteNullable(result.declared, replacements), substituteNullable(result.inferred, replacements));
        List<Variable> remappedVariables = variables.stream().map(variable -> new Variable(variable.index + offset,
                substitute(variable.requirements, replacements))).toList();
        return new CallableSignature(remappedParameters, remappedResult, effects, remappedVariables);
    }

    private static Compatibility bridge(List<ContractTerm> guarantees, List<ContractTerm> requirements,
                                        Map<Integer, ContractTerm> substitutions) {
        if (guarantees.isEmpty() || requirements.isEmpty()) return Compatibility.UNKNOWN;
        boolean unknown = false;
        for (ContractTerm requirement : requirements) {
            Compatibility best = Compatibility.INCOMPATIBLE;
            List<ContractTerm> concrete = guarantees.stream()
                    .filter(guarantee -> !(guarantee instanceof VariableRef)).toList();
            List<ContractTerm> candidates = concrete.isEmpty() ? guarantees : concrete;
            for (ContractTerm guarantee : candidates) {
                LinkedHashMap<Integer, ContractTerm> candidate = new LinkedHashMap<>(substitutions);
                Compatibility relation = unifyBridge(guarantee, requirement, candidate);
                if (relation == Compatibility.COMPATIBLE) {
                    substitutions.clear();
                    substitutions.putAll(candidate);
                    best = relation;
                    break;
                }
                if (relation == Compatibility.UNKNOWN) best = relation;
            }
            if (best == Compatibility.INCOMPATIBLE) return best;
            if (best == Compatibility.COMPATIBLE) {
                guarantees.stream().filter(VariableRef.class::isInstance).map(VariableRef.class::cast)
                        .forEach(variable -> substitutions.putIfAbsent(variable.index, requirement));
            }
            unknown |= best == Compatibility.UNKNOWN;
        }
        return unknown ? Compatibility.UNKNOWN : Compatibility.COMPATIBLE;
    }

    private static Compatibility unifyBridge(ContractTerm supplied, ContractTerm required,
                                              Map<Integer, ContractTerm> substitutions) {
        ContractTerm substitutedSupplied = substitute(supplied, substitutions);
        ContractTerm substitutedRequired = substitute(required, substitutions);
        if (substitutedRequired instanceof VariableRef(int index1)) {
            substitutions.put(index1, substitutedSupplied);
            return Compatibility.COMPATIBLE;
        }
        if (substitutedSupplied instanceof VariableRef(int index1)) {
            substitutions.put(index1, substitutedRequired);
            return Compatibility.COMPATIBLE;
        }
        if (substitutedSupplied.equals(substitutedRequired)
                || substitutedRequired instanceof NamedRef(String name) && name.equals("Any")) {
            return Compatibility.COMPATIBLE;
        }
        switch (substitutedSupplied) {
            case AppliedRef(
                    ContractTerm leftConstructor, List<ContractTerm> leftArguments
            ) when substitutedRequired instanceof AppliedRef(
                    ContractTerm rightConstructor, List<ContractTerm> rightArguments
            ) -> {
                if (!leftConstructor.render().equals(rightConstructor.render())
                        || leftArguments.size() != rightArguments.size()) return Compatibility.INCOMPATIBLE;
                boolean unknown = false;
                for (int index = 0; index < leftArguments.size(); index++) {
                    Compatibility relation = unifyBridge(leftArguments.get(index), rightArguments.get(index), substitutions);
                    if (relation == Compatibility.INCOMPATIBLE) return relation;
                    unknown |= relation == Compatibility.UNKNOWN;
                }
                return unknown ? Compatibility.UNKNOWN : Compatibility.COMPATIBLE;
            }
            case ModifiedRef left when substitutedRequired instanceof ModifiedRef(
                    ContractTerm base, boolean nullable, boolean optional
            ) -> {
                if (left.nullable && !nullable || left.optional && !optional) {
                    return Compatibility.INCOMPATIBLE;
                }
                return unifyBridge(left.base, base, substitutions);
            }
            case NamedRef(String left) when substitutedRequired instanceof NamedRef(String right) -> {
                if (knownDisjoint(left, right)) return Compatibility.INCOMPATIBLE;
                return Compatibility.UNKNOWN;
            }
            default -> {
            }
        }
        return Compatibility.UNKNOWN;
    }

    private static boolean knownDisjoint(String left, String right) {
        Set<String> closed = Set.of("Number", "String", "Boolean", "Null", "Missing", "Function",
                "Sequence", "Dictionary", "Field");
        return closed.contains(left) && closed.contains(right) && !left.equals(right);
    }

    private static List<Variable> retainedVariables(List<Parameter> parameters, Result result,
                                                    List<Variable> candidates) {
        java.util.Set<Integer> used = new java.util.LinkedHashSet<>();
        parameters.forEach(parameter -> collectVariables(parameter.requirements, used));
        collectVariables(result.guarantees, used);
        return candidates.stream().filter(variable -> used.contains(variable.index)).toList();
    }

    private static List<ContractTerm> terms(Resolution.AnalyzedClause clause) {
        return clause == null ? List.of()
                : clause.valueRequirements().stream().map(CallableSignature::term).toList();
    }

    private static ContractTerm term(Resolution.ContractBinding contract) {
        ContractTerm base;
        if (contract.inline() instanceof Ast.ArrowContract arrow) base = arrowTerm(arrow);
        else if (contract.name().matches("_[1-9][0-9]*")) {
            base = new VariableRef(Integer.parseInt(contract.name().substring(1)) - 1);
        } else {
            base = new NamedRef(contract.name());
            if (!contract.arguments().isEmpty()) {
                base = new AppliedRef(base, contract.arguments().stream().map(CallableSignature::term).toList());
            }
        }
        return contract.nullable() || contract.optional()
                ? new ModifiedRef(base, contract.nullable(), contract.optional()) : base;
    }

    private static ContractTerm arrowTerm(Ast.ArrowContract arrow) {
        return new ArrowRef(arrow.parameters().stream().map(parameter -> parameter.stream()
                .map(CallableSignature::expressionTerm).toList()).toList(), expressionTerm(arrow.result()));
    }

    private static ContractTerm expressionTerm(Ast.Expr expression) {
        return switch (expression) {
            case Ast.ContractVariable variable -> new VariableRef(variable.index() - 1);
            case Ast.Name name -> new NamedRef(name.name());
            case Ast.Apply apply -> {
                ArrayList<ContractTerm> arguments = new ArrayList<>();
                Ast.Expr target = apply;
                while (target instanceof Ast.Apply application) {
                    arguments.addFirst(expressionTerm(application.argument()));
                    target = application.function();
                }
                yield new AppliedRef(expressionTerm(target), arguments);
            }
            case Ast.ContractModifier modifier -> new ModifiedRef(expressionTerm(modifier.target()),
                    modifier.nullable(), modifier.optional());
            case Ast.ArrowContract arrow -> arrowTerm(arrow);
            case Ast.Group group -> expressionTerm(group.expression());
            default -> new NamedRef(expression.toString());
        };
    }

    private static List<ContractTerm> sorted(Iterable<? extends ContractDescriptor> contracts) {
        ArrayList<ContractTerm> result = new ArrayList<>();
        contracts.forEach(contract -> result.add(new NamedRef(contract.publicName())));
        return result.stream().sorted(java.util.Comparator.comparing(ContractTerm::render)).toList();
    }

    private static String effectName(ContractInference.BuiltinEffect effect) {
        return switch (effect) {
            case OUTPUT -> "Output";
            case TEST_REPORT -> "TestReport";
        };
    }

    private static <T> List<T> union(List<T> left, List<T> right) {
        LinkedHashSet<T> values = new LinkedHashSet<>(left);
        values.addAll(right);
        return List.copyOf(values);
    }

    private static <T> List<T> copy(List<T> values) { return List.copyOf(values); }
    private static <T> List<T> nullableCopy(List<T> values) { return values == null ? null : List.copyOf(values); }

    private static List<Integer> headerVariables(FunctionDef function, Resolution resolution) {
        java.util.TreeSet<Integer> variables = new java.util.TreeSet<>();
        collectVariables(terms(resolution.clause(function.resultContracts())), variables);
        function.params().forEach(parameter ->
                collectVariables(terms(resolution.clause(parameter.contracts())), variables));
        return List.copyOf(variables);
    }

    private static List<ContractTerm> variableRequirements(int variable, List<Parameter> parameters,
                                                            List<ContractTerm> result) {
        LinkedHashSet<ContractTerm> requirements = new LinkedHashSet<>();
        parameters.forEach(parameter -> collectBounds(parameter.requirements(), variable, requirements));
        collectBounds(result, variable, requirements);
        return List.copyOf(requirements);
    }

    private static void collectVariables(List<ContractTerm> terms, java.util.Set<Integer> variables) {
        terms.forEach(term -> collectVariables(term, variables));
    }

    private static void collectVariables(ContractTerm term, java.util.Set<Integer> variables) {
        switch (term) {
            case VariableRef variable -> variables.add(variable.index());
            case AppliedRef applied -> applied.arguments().forEach(argument -> collectVariables(argument, variables));
            case ModifiedRef modified -> collectVariables(modified.base(), variables);
            case ArrowRef arrow -> {
                arrow.parameters().forEach(parameter -> parameter.forEach(value -> collectVariables(value, variables)));
                collectVariables(arrow.result(), variables);
            }
            default -> { }
        }
    }

    private static void collectBounds(List<ContractTerm> terms, int variable,
                                      java.util.Set<ContractTerm> requirements) {
        boolean contains = terms.stream().anyMatch(term -> containsVariable(term, variable));
        if (contains) terms.stream().filter(term -> !containsVariable(term, variable)).forEach(requirements::add);
    }

    private static boolean containsVariable(ContractTerm term, int variable) {
        if (term instanceof VariableRef(int index)) return index == variable;
        if (term instanceof AppliedRef applied) return applied.arguments().stream()
                .anyMatch(argument -> containsVariable(argument, variable));
        if (term instanceof ModifiedRef modified) return containsVariable(modified.base(), variable);
        if (term instanceof ArrowRef(List<List<ContractTerm>> parameters1, ContractTerm result1)) return parameters1.stream().flatMap(List::stream)
                .anyMatch(value -> containsVariable(value, variable)) || containsVariable(result1, variable);
        return false;
    }

    private static Map<Integer, ContractTerm> substitutions(List<ContractTerm> requirements, Value value) {
        java.util.LinkedHashMap<Integer, ContractTerm> substitutions = new java.util.LinkedHashMap<>();
        ContractTerm actual = valueTerm(value);
        for (ContractTerm requirement : requirements) inferSubstitution(requirement, actual, substitutions);
        return Map.copyOf(substitutions);
    }

    private static void inferSubstitution(ContractTerm pattern, ContractTerm actual,
                                          Map<Integer, ContractTerm> substitutions) {
        if (pattern instanceof VariableRef(int index1)) {
            substitutions.putIfAbsent(index1, actual);
        } else if (pattern instanceof AppliedRef(ContractTerm constructor, List<ContractTerm> arguments) && actual instanceof AppliedRef(
                ContractTerm constructor1, List<ContractTerm> arguments1
        )
                && constructor.render().equals(constructor1.render())
                && arguments.size() == arguments1.size()) {
            for (int index = 0; index < arguments.size(); index++) {
                inferSubstitution(arguments.get(index), arguments1.get(index), substitutions);
            }
        } else if (pattern instanceof ModifiedRef modified) {
            inferSubstitution(modified.base(), actual, substitutions);
        }
    }

    private CallableSignature substitute(Map<Integer, ContractTerm> substitutions) {
        List<Parameter> substitutedParameters = parameters.stream().map(parameter -> new Parameter(parameter.name(),
                substitute(parameter.requirements(), substitutions), substituteNullable(parameter.declared(), substitutions),
                substituteNullable(parameter.inferred(), substitutions))).toList();
        Result substitutedResult = new Result(substitute(result.guarantees(), substitutions),
                substituteNullable(result.declared(), substitutions), substituteNullable(result.inferred(), substitutions));
        List<Variable> remaining = variables.stream().filter(variable -> !substitutions.containsKey(variable.index()))
                .map(variable -> new Variable(variable.index(), substitute(variable.requirements(), substitutions))).toList();
        return new CallableSignature(substitutedParameters, substitutedResult, effects, remaining);
    }

    private static List<ContractTerm> substituteNullable(List<ContractTerm> terms,
                                                          Map<Integer, ContractTerm> substitutions) {
        return terms == null ? null : substitute(terms, substitutions);
    }

    private static List<ContractTerm> substitute(List<ContractTerm> terms,
                                                 Map<Integer, ContractTerm> substitutions) {
        return terms.stream().map(term -> substitute(term, substitutions)).toList();
    }

    private static ContractTerm substitute(ContractTerm term, Map<Integer, ContractTerm> substitutions) {
        return switch (term) {
            case VariableRef variable -> substitutions.getOrDefault(variable.index(), variable);
            case AppliedRef applied -> new AppliedRef(substitute(applied.constructor(), substitutions),
                    substitute(applied.arguments(), substitutions));
            case ModifiedRef modified -> new ModifiedRef(substitute(modified.base(), substitutions),
                    modified.nullable(), modified.optional());
            case ArrowRef arrow -> new ArrowRef(arrow.parameters().stream()
                    .map(parameter -> substitute(parameter, substitutions)).toList(),
                    substitute(arrow.result(), substitutions));
            default -> term;
        };
    }

    private static ContractTerm valueTerm(Value value) {
        value = ValueSemantics.underlying(value);
        if (value instanceof Value.Seq sequence && !sequence.values().isEmpty()) {
            ContractTerm element = valueTerm(sequence.values().getFirst());
            boolean uniform = sequence.values().stream().skip(1).map(CallableSignature::valueTerm)
                    .allMatch(element::equals);
            return new AppliedRef(new NamedRef("Sequence"), List.of(uniform ? element : new NamedRef("Any")));
        }
        return new NamedRef(switch (ValueKind.of(value)) {
            case NUMBER -> "Number";
            case STRING -> "String";
            case BOOLEAN -> "Boolean";
            case NULL -> "Null";
            case MISSING -> "Missing";
            case FUNCTION -> "Function";
            case COLLECTION -> "Collection";
            case SEQUENCE -> "Sequence";
            case DICTIONARY -> "Dictionary";
            case FIELD -> "Field";
            default -> "Any";
        });
    }
}
