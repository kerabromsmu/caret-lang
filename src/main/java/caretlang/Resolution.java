package caretlang;

import caretlang.Ast.Name;
import caretlang.Ast.ContractClause;
import caretlang.Ast.AmbiguousCall;

import java.util.IdentityHashMap;
import java.util.List;

final class Resolution {
    enum CallMode { PREFIX, INFIX, DYNAMIC }
    record Binding(int lexicalDepth, int slot, int symbolId, SourceSpan declarationSpan,
                   boolean captured, Boolean refinementEligible) {}
    record ContractBinding(String name, Binding binding, java.util.List<ContractBinding> arguments,
                           boolean nullable, boolean optional, Ast.Expr inline, SourceSpan span) {
        ContractBinding(String name, Binding binding, java.util.List<ContractBinding> arguments,
                        boolean nullable, boolean optional, SourceSpan span) {
            this(name, binding, arguments, nullable, optional, null, span);
        }
    }
    record AnalyzedClause(List<ContractBinding> valueRequirements,
                          List<EffectDescriptor> effectAllowance, SourceSpan span) {
        AnalyzedClause {
            valueRequirements = List.copyOf(valueRequirements);
            effectAllowance = effectAllowance == null ? null : List.copyOf(effectAllowance);
        }
    }

    private final IdentityHashMap<Name, Binding> names;
    private final IdentityHashMap<ContractClause, java.util.List<ContractBinding>> contracts;
    private final IdentityHashMap<ContractClause, AnalyzedClause> clauses;
    private final IdentityHashMap<AmbiguousCall, CallMode> calls;
    private final IdentityHashMap<Ast.PrintLine, Boolean> builtinPrintLines;
    private final java.util.Map<SourceSpan, Integer> declarations;

    Resolution(IdentityHashMap<Name, Binding> names,
               IdentityHashMap<ContractClause, java.util.List<ContractBinding>> contracts,
               IdentityHashMap<ContractClause, AnalyzedClause> clauses,
               IdentityHashMap<AmbiguousCall, CallMode> calls,
               IdentityHashMap<Ast.PrintLine, Boolean> builtinPrintLines,
               java.util.Map<SourceSpan, Integer> declarations) {
        this.names = new IdentityHashMap<>(names);
        this.contracts = new IdentityHashMap<>(contracts);
        this.clauses = new IdentityHashMap<>(clauses);
        this.calls = new IdentityHashMap<>(calls);
        this.builtinPrintLines = new IdentityHashMap<>(builtinPrintLines);
        this.declarations = java.util.Map.copyOf(declarations);
    }

    Binding binding(Name name) {
        return names.get(name);
    }

    java.util.List<ContractBinding> contracts(ContractClause clause) {
        return clause == null ? java.util.List.of() : contracts.getOrDefault(clause, java.util.List.of());
    }

    AnalyzedClause clause(ContractClause clause) {
        return clause == null ? null : clauses.get(clause);
    }

    CallMode callMode(AmbiguousCall call) { return calls.getOrDefault(call, CallMode.DYNAMIC); }
    boolean usesBuiltinPrint(Ast.PrintLine line) { return builtinPrintLines.getOrDefault(line, false); }
    Integer symbolId(SourceSpan declaration) { return declarations.get(declaration); }
}
