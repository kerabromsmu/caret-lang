package caretlang;

import caretlang.Ast.FunctionDef;
import caretlang.Ast.Stmt;

import java.util.ArrayList;
import java.util.List;

/** Deterministic plain-text projection of environment-visible semantic callable facts. */
final class InferenceReporter {
    private InferenceReporter() {}

    static String render(List<Stmt> program, ContractInference inference, Resolution resolution) {
        ArrayList<FunctionDef> functions = new ArrayList<>();
        collect(program, functions);
        StringBuilder output = new StringBuilder();
        for (FunctionDef function : functions) {
            CallableSignature signature = CallableSignature.inferred(function, inference, resolution);
            SourcePosition position = function.span().start();
            output.append("function ").append(function.name()).append('/').append(function.params().size())
                    .append(" @ ").append(position.line()).append(':').append(position.column()).append('\n');
            for (CallableSignature.Parameter parameter : signature.parameters()) {
                output.append("  parameter ").append(parameter.name()).append(": effective=")
                        .append(terms(parameter.requirements())).append(" declared=")
                        .append(nullableTerms(parameter.declared())).append(" inferred=")
                        .append(nullableTerms(parameter.inferred())).append('\n');
            }
            output.append("  result: effective=").append(terms(signature.result().guarantees()))
                    .append(" declared=").append(nullableTerms(signature.result().declared()))
                    .append(" inferred=").append(nullableTerms(signature.result().inferred())).append('\n');
            output.append("  effects: upper=").append(nullableEffects(signature.effects().upperBound()))
                    .append(" declared=").append(nullableEffects(signature.effects().declared()))
                    .append(" inferred=").append(nullableEffects(signature.effects().inferred())).append('\n');
            output.append("  variables: [");
            for (int index = 0; index < signature.variables().size(); index++) {
                if (index > 0) output.append(", ");
                CallableSignature.Variable variable = signature.variables().get(index);
                output.append('_').append(variable.index() + 1).append(':')
                        .append(terms(variable.requirements()));
            }
            output.append("]\n");
        }
        return output.toString();
    }

    private static void collect(List<Stmt> statements, List<FunctionDef> functions) {
        for (Stmt statement : statements) {
            if (statement instanceof FunctionDef function) {
                functions.add(function);
                collect(function.body(), functions);
            }
        }
    }

    private static String nullableTerms(List<CallableSignature.ContractTerm> terms) {
        return terms == null ? "~" : terms(terms);
    }

    private static String terms(List<CallableSignature.ContractTerm> terms) {
        return "[" + String.join(", ", terms.stream().map(CallableSignature.ContractTerm::render).toList()) + "]";
    }

    private static String nullableEffects(List<CallableSignature.EffectRef> effects) {
        return effects == null ? "~" : "[" + String.join(", ", effects.stream()
                .map(CallableSignature.EffectRef::name).toList()) + "]";
    }
}
