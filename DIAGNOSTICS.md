# Diagnostic coverage matrix

Every stable message variant in `DiagnosticCatalog` and `HostMessageCatalog` is listed here. Public
fixtures compare complete stderr with the adjacent `.expected` file. Entries that cannot be reached
from ordinary Caret source use focused Java evidence.

| Variant ID | Category | Code | Evidence |
|---|---|---|---|
| LEX-INCOMPLETE-ESCAPE | public | LEX_INVALID_ESCAPE | `examples/errors/incomplete_escape.caret` |
| LEX-UNKNOWN-ESCAPE | public | LEX_INVALID_ESCAPE | `examples/errors/invalid_escape.caret` |
| LEX-UNTERMINATED-STRING | public | LEX_UNTERMINATED_STRING | `examples/errors/unterminated_string.caret` |
| LEX-INVALID-NUMBER | public | LEX_INVALID_NUMBER | `examples/errors/invalid_number.caret` |
| LEX-UNEXPECTED-CHARACTER | public | LEX_UNEXPECTED_CHARACTER | `examples/errors/unexpected_character.caret` |
| LEX-UNICODE-FORM | public | LEX_INVALID_ESCAPE | `examples/errors/invalid_unicode_form.caret` |
| LEX-INVALID-UNICODE | public | LEX_INVALID_ESCAPE | `examples/errors/invalid_unicode_escape.caret` |
| LEX-INVALID-CODE-POINT | public | LEX_INVALID_ESCAPE | `examples/errors/invalid_unicode_code_point.caret` |
| LEX-INVALID-LAYOUT-MARKER | public | LEX_INVALID_LAYOUT_MARKER | `LexerTest#layoutMarkersIgnoreCommentsAndStringsAndRejectInvalidPlacement` |
| PARSE-NESTING-DEPTH | public | PARSE_INVALID_EXPRESSION | `examples/errors/expression_nesting_depth.caret` |
| PARSE-UNEXPECTED-INDENT | public | PARSE_UNEXPECTED_INDENT | `examples/errors/unexpected_indent.caret` |
| PARSE-INCONSISTENT-INDENT | public | PARSE_UNEXPECTED_INDENT | `examples/errors/inconsistent_continuation_indent.caret` |
| PARSE-FUNCTION-BODY | public | PARSE_INVALID_SYNTAX | `examples/errors/missing_function_body.caret` |
| PARSE-INVALID-DEFINITION | public | PARSE_INVALID_SYNTAX | `examples/errors/invalid_definition.caret` |
| PARSE-CONTINUATION-DEFINITION | public | PARSE_INVALID_SYNTAX | `examples/errors/definition_in_continuation.caret` |
| PARSE-RESERVED-BINDING | public | PARSE_RESERVED_BINDING | `examples/errors/reserved_binding.caret` |
| PARSE-INVALID-CONTRACT | public | PARSE_INVALID_CONTRACT | `examples/errors/invalid_contract.caret` |
| PARSE-UNCLOSED-DELIMITER | public | PARSE_UNCLOSED_DELIMITER | `examples/errors/unclosed_delimiter.caret` |
| PARSE-INVALID-HOLE | public | PARSE_INVALID_HOLE | `examples/errors/invalid_numbered_hole.caret` |
| PARSE-INVALID-NUMBER | internal | PARSE_INVALID_NUMBER | `ParserTest#rejectsNonFiniteNumberLiteralsAsLocatedParserDiagnostics` |
| PARSE-NONFINITE-NUMBER | public | PARSE_INVALID_NUMBER | `examples/errors/non_finite_literal.caret` |
| PARSE-EXPECTED-EXPRESSION | public | PARSE_INVALID_EXPRESSION | `examples/errors/invalid_expression.caret` |
| SEMANTIC-DUPLICATE-PARAMETER | public | DUPLICATE_PARAMETER | `examples/errors/duplicate_parameter.caret` |
| SEMANTIC-DUPLICATE-DEFINITION | public | DUPLICATE_DEFINITION | `examples/errors/duplicate_definition.caret` |
| SEMANTIC-PREMATURE-READ | public | READ_BEFORE_INITIALIZATION | `examples/errors/read_before_initialization.caret` |
| SEMANTIC-UNKNOWN-CONTRACT | public | UNKNOWN_CONTRACT | `examples/errors/unknown_contract.caret` |
| SEMANTIC-NOT-A-CONTRACT | public | NOT_A_CONTRACT | `examples/errors/not_a_contract.caret` |
| RUNTIME-NOT-A-CONTRACT | public | NOT_A_CONTRACT | `examples/errors/dynamic_not_a_contract.caret` |
| RUNTIME-DUPLICATE-DEFINITION | internal | DUPLICATE_DEFINITION | `InterpreterTest#rejectsDuplicateDefinitionsAndParameters` |
| RUNTIME-PREMATURE-READ | internal | READ_BEFORE_INITIALIZATION | `InterpreterTest#reportsReadsBeforeSequentialDeclarations` |
| RUNTIME-UNKNOWN-NAME | public | UNKNOWN_NAME | `examples/errors/unknown_name.caret` |
| RUNTIME-NOT-CALLABLE | public | NOT_CALLABLE | `examples/errors/not_callable.caret` |
| RUNTIME-NOT-DEREFERENCEABLE | public | NOT_DEREFERENCEABLE | `InterpreterTest#dereferencesReflectionDictionariesForFunctionsValuesAndMembers` |
| RUNTIME-INFIX-NOT-CALLABLE | public | NOT_CALLABLE | `examples/errors/non_callable_infix.caret` |
| RUNTIME-INVALID-INFIX-ARITY | public | INVALID_INFIX_ARITY | `examples/errors/invalid_infix_arity.caret` |
| RUNTIME-INVALID-COMPOSITION-LEFT | public | INVALID_COMPOSITION_LEFT | `examples/errors/non_callable_composition.caret` |
| RUNTIME-INVALID-COMPOSITION-RIGHT | public | INVALID_COMPOSITION_RIGHT | `examples/errors/invalid_composition_arity.caret` |
| RUNTIME-INVALID-MAP-TRANSFORM | public | INVALID_MAP_TRANSFORM | `InterpreterTest#mapRejectsInvalidInputsAndRetainsLocatedElementFailures` |
| RUNTIME-AMBIGUOUS-CALL-ARITY | public | TOO_MANY_ARGUMENTS | `examples/errors/ambiguous_call_arity.caret` |
| INTERNAL-TOO-MANY-FUNCTION-ARGUMENTS | internal | TOO_MANY_ARGUMENTS | `DiagnosticCoverageTest#internalCatalogVariantsAreConstructible` |
| INTERNAL-TOO-MANY-PARTIAL-ARGUMENTS | internal | TOO_MANY_ARGUMENTS | `DiagnosticCoverageTest#internalCatalogVariantsAreConstructible` |
| RUNTIME-EVALUATION-DEPTH | internal | CALL_DEPTH_EXCEEDED | `DiagnosticCoverageTest#internalCatalogVariantsAreConstructible` |
| RUNTIME-CALL-DEPTH | public | CALL_DEPTH_EXCEEDED | `examples/errors/call_depth.caret` |
| RUNTIME-INVALID-CONDITION | public | INVALID_CONDITION | `examples/errors/invalid_condition.caret` |
| RUNTIME-EXPECTED-NUMBER | public | EXPECTED_NUMBER | `examples/errors/expected_number.caret` |
| RUNTIME-EXPECTED-STRING | public | EXPECTED_STRING | `examples/errors/expected_string.caret` |
| RUNTIME-EXPECTED-SEQUENCE | public | EXPECTED_SEQUENCE | `examples/errors/expected_sequence.caret` |
| RUNTIME-EXPECTED-DICTIONARY | public | EXPECTED_DICTIONARY | `examples/errors/expected_dictionary.caret` |
| RUNTIME-INVALID-DICTIONARY-KEY | public | INVALID_DICTIONARY_KEY | `examples/errors/invalid_dictionary_key.caret` |
| RUNTIME-DIVISION-BY-ZERO | public | DIVISION_BY_ZERO | `examples/errors/division_by_zero.caret` |
| RUNTIME-NONFINITE-RESULT | public | NON_FINITE_RESULT | `examples/errors/non_finite_result.caret` |
| RUNTIME-INVALID-FIELD-TARGET | public | INVALID_FIELD_TARGET | `examples/errors/invalid_field_target.caret` |
| RUNTIME-MISSING-COLLECTION-FIELD | public | MISSING_FIELD | `examples/errors/required_missing_field.caret` |
| RUNTIME-MISSING-REFLECTED-FIELD | public | MISSING_FIELD | `examples/errors/missing_reflected_field.caret` |
| RUNTIME-INVALID-DYNAMIC-FIELD | public | INVALID_DYNAMIC_FIELD_NAME | `examples/errors/invalid_dynamic_key.caret` |
| RUNTIME-CALLABLE-EQUALITY | public | CALLABLE_EQUALITY | `examples/errors/callable_equality.caret` |
| RUNTIME-CALLABLE-RENDERING | public | CALLABLE_RENDERING | `InterpreterTest#toStringRejectsUnsupportedCallablesAndNonStringSpecializationResults` |
| RUNTIME-MIXED-HOLES | public | MIXED_HOLE_STYLES | `examples/errors/mixed_holes.caret` |
| RUNTIME-INVALID-ASSERTION | public | INVALID_ASSERTION | `examples/errors/invalid_assertion.caret` |
| RUNTIME-CONTRACT-VIOLATION | public | CONTRACT_VIOLATION | `examples/errors/contract_violation.caret` |
| RUNTIME-EFFECT-CONSTRAINT-REQUIRES-CALLABLE | public | EFFECT_CONSTRAINT_REQUIRES_CALLABLE | `InterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced` |
| RUNTIME-EFFECT-ALLOWANCE-EXCEEDED | public | EFFECT_ALLOWANCE_EXCEEDED | `InterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced` |
| RUNTIME-UNKNOWN-CALL-EFFECTS | public | UNKNOWN_CALL_EFFECTS | `InterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced` |
| SEMANTIC-INCOMPATIBLE-CONTRACTS | public | INCOMPATIBLE_CONTRACTS | `examples/errors/incompatible_inferred_contracts.caret` |
| SEMANTIC-INCOMPATIBLE-COMPOSITION | public | INCOMPATIBLE_CONTRACTS | `examples/errors/incompatible_composition_contracts.caret` |
| SEMANTIC-AMBIGUOUS-CONTRACT | public | AMBIGUOUS_CONTRACT | `examples/errors/ambiguous_inferred_contract.caret` |
| SEMANTIC-MIXED-COLLECTION-SHAPE | public | MIXED_COLLECTION_SHAPE | `examples/errors/mixed_collection_shape.caret` |
| SEMANTIC-DUPLICATE-FIELD | public | DUPLICATE_FIELD | `InterpreterTest#rejectsMixedAndDuplicateNamedCollectionElements` |
| RUNTIME-MIXED-COLLECTION-SHAPE | public | MIXED_COLLECTION_SHAPE | `InterpreterTest#fieldCollectionsRejectMixedDuplicateAndNonStringKeys` |
| RUNTIME-DUPLICATE-FIELD | public | DUPLICATE_FIELD | `InterpreterTest#fieldCollectionsRejectMixedDuplicateAndNonStringKeys` |
| SEMANTIC-INVALID-REFINEMENT | public | INVALID_REFINEMENT | `ContractInferenceTest#validatesOnlyProvenPureUnaryBooleanRefinements`; `InterpreterTest#invalidRefinementsAreRejectedBeforeProgramEffects`; `examples/errors/invalid_refinement.caret` |
| SEMANTIC-INVALID-CONTRACT-VARIABLE | public | INVALID_CONTRACT_VARIABLE | `InterpreterTest#arrowContractVariablesAreContiguousAndRequireGenericRelationships`; `InterpreterTest#declarationVariablesIncludeNestedArrowsAndRejectUnrelatedOccurrences` |
| SEMANTIC-AMBIGUOUS-CLAUSE-NAME | public | AMBIGUOUS_CLAUSE_NAME | `InterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced` |
| SEMANTIC-UNKNOWN-CLAUSE-NAME | public | UNKNOWN_CLAUSE_NAME | `InterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced` |
| SEMANTIC-CONFLICTING-EFFECT-ALLOWANCE | public | CONFLICTING_EFFECT_ALLOWANCE | `InterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced` |
| SEMANTIC-INVALID-EFFECT-MODIFIER | public | INVALID_EFFECT_MODIFIER | `InterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced` |
| SEMANTIC-EFFECT-AS-CONTRACT-ARGUMENT | public | EFFECT_AS_CONTRACT_ARGUMENT | `InterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced` |
| SEMANTIC-EFFECT-ALLOWANCE-EXCEEDED | public | EFFECT_ALLOWANCE_EXCEEDED | `InterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced` |
| SEMANTIC-UNKNOWN-CALL-EFFECTS | public | UNKNOWN_CALL_EFFECTS | `InterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced` |
| SEMANTIC-INCONSISTENT-OVERLOAD-ARITY | public | INCONSISTENT_OVERLOAD_ARITY | `examples/errors/inconsistent_overload_arity.caret` |
| RUNTIME-NO-APPLICABLE-OVERLOAD | public | NO_APPLICABLE_OVERLOAD | `examples/errors/no_applicable_overload.caret` |
| RUNTIME-AMBIGUOUS-OVERLOAD | public | AMBIGUOUS_OVERLOAD | `examples/errors/ambiguous_overload.caret` |
| INTERNAL-UNKNOWN-UNARY-OPERATOR | internal | UNKNOWN_OPERATOR | `DiagnosticCoverageTest#internalCatalogVariantsAreConstructible` |
| INTERNAL-UNKNOWN-BINARY-OPERATOR | internal | UNKNOWN_OPERATOR | `DiagnosticCoverageTest#internalCatalogVariantsAreConstructible` |
| INTERNAL-INVARIANT | internal | INTERNAL_ERROR | `DiagnosticCoverageTest#internalCatalogVariantsAreConstructible` |
| HOST-FILE-USAGE | host | — | `MainTest#rejectsExtraFileModeArguments` |
| HOST-TEST-USAGE | host | — | `MainTest#hostCatalogMessagesHaveExactOutput` |
| HOST-SOURCE-READ-FAILURE | host | — | `MainTest#fileSystemFailuresAreReportedWithoutAStackTrace` |
| HOST-TEST-READ-FAILURE | host | — | `MainTest#hostCatalogMessagesHaveExactOutput` |
| HOST-REPL-TERMINAL-REQUIRED | host | — | `DiagnosticCoverageTest#catalogsAndCoverageRowsStayInSync` |
| HOST-REPL-HISTORY-READ | host | — | `JLineReplTest#unreadableHistoryFallsBackToMemoryWithAClearWarning` |
| HOST-REPL-HISTORY-WRITE | host | — | `DiagnosticCoverageTest#catalogsAndCoverageRowsStayInSync` |
