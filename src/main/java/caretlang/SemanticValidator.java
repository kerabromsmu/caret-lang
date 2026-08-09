package caretlang;

import caretlang.Ast.Assign;
import caretlang.Ast.FunctionDef;
import caretlang.Ast.Stmt;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

final class SemanticValidator {
    private SemanticValidator() {}

    static void validate(List<Stmt> statements) {
        LinkedHashMap<String, SourceSpan> declarations = new LinkedHashMap<>();
        for (Stmt statement : statements) {
            String name = switch (statement) {
                case Assign assign -> assign.name();
                case FunctionDef function -> function.name();
                default -> null;
            };
            if (name != null) {
                SourceSpan original = declarations.putIfAbsent(name, statement.span());
                if (original != null) {
                    throw new LangException(new Diagnostic(Diagnostic.Phase.SEMANTIC,
                            Diagnostic.Codes.DUPLICATE_DEFINITION, "Duplicate definition: " + name,
                            statement.span(), List.of(new Diagnostic.Related(
                            "First definition of " + name, original))));
                }
            }
            if (statement instanceof FunctionDef function) {
                HashSet<String> parameters = new HashSet<>();
                for (String parameter : function.params()) {
                    if (!parameters.add(parameter)) {
                        throw new LangException(Diagnostic.Phase.SEMANTIC,
                                Diagnostic.Codes.DUPLICATE_PARAMETER,
                                "Duplicate parameter: " + parameter, function.span());
                    }
                }
            }
        }
    }
}
