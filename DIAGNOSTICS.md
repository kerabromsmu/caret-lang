# Diagnostic coverage matrix

Every stable message variant in `DiagnosticCatalog` and `HostMessageCatalog` is listed here. Public
fixtures compare complete stderr with the adjacent `.expected` file. Entries that cannot be reached
from ordinary Caret source use focused Java evidence.
`DiagnosticCoverageTest#errorFixturesExerciseTheirActualCatalogVariants` executes each cited error
fixture and checks its actual catalog variant, phase, code, location, and complete golden output.
The call-depth fixture uses a dedicated test thread with sufficient host stack to reach the ordinary
call guard; `test.sh` independently checks its CLI output. On the smaller JUnit worker stack, the
same recursion can reach the internal evaluation-depth fallback first.

`SEMANTIC-UNKNOWN-CALL-EFFECTS` is listed in the catalog but has no current semantic emission
path. Its evidence checks catalog representation only; actual unknown-bound invocation and
callable-value constraint failures use the runtime variant. This is a remaining reachability gap,
not evidence of an implemented semantic rejection path.

Phase 4 numeric warnings, conversion errors, and contiguous packed-storage diagnostics are specified in
[the diagnostic owner](spec/01-source-layout-and-diagnostics.md#phase-4-numeric-template-conversion-and-packed-diagnostics-implemented)
and [acceptance matrix](spec/sections/06-02-packed-layouts.md#packed-and-prerequisite-acceptance-matrix).
`IMPLICIT_PRECISION_LOSS` is implemented as a warning or strict error; `div` uses the existing
zero-division and contract diagnostics. Conversion has exact fixture or focused Java evidence for
unsupported conversions, range failures, and invalid layouts. Warnings must
remain separate from program output and embedded failure diagnostics.
Expected-template literal completion is implemented: omitted required or nondefaultable fields
reuse `CONTRACT_VIOLATION` at the literal with the template field as related context, while
successful direct `T~`/`T?~` default insertion is silent. See
`examples/errors/template_missing_required.caret` and
`NumericTemplateInterpreterTest#templateCompletionRejectsNondefaultableAndWrongShapesWithLocations`.

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
| LEX-INVALID-LAYOUT-MARKER | public | LEX_INVALID_LAYOUT_MARKER | `LexerTest#layoutMarkersIgnoreCommentsAndStringsAndRejectInvalidPlacement`; `examples/errors/invalid_layout_marker.caret` |
| PARSE-NESTING-DEPTH | public | PARSE_INVALID_EXPRESSION | `examples/errors/expression_nesting_depth.caret` |
| PARSE-UNEXPECTED-INDENT | public | PARSE_UNEXPECTED_INDENT | `examples/errors/unexpected_indent.caret` |
| PARSE-INCONSISTENT-INDENT | public | PARSE_UNEXPECTED_INDENT | `examples/errors/inconsistent_continuation_indent.caret` |
| PARSE-FUNCTION-BODY | public | PARSE_INVALID_SYNTAX | `examples/errors/missing_function_body.caret` |
| PARSE-INVALID-DEFINITION | public | PARSE_INVALID_SYNTAX | `examples/errors/invalid_definition.caret` |
| PARSE-CONTINUATION-DEFINITION | public | PARSE_INVALID_SYNTAX | `examples/errors/definition_in_continuation.caret` |
| PARSE-LAMBDA-HEADER | public | PARSE_INVALID_SYNTAX | `ParserTest#rejectsInvalidLambdaHeadersWithExactDiagnostics`; `examples/errors/invalid_lambda_header.caret` |
| PARSE-LAMBDA-BODY | public | PARSE_INVALID_SYNTAX | `ParserTest#rejectsMissingLambdaBodiesWithExactDiagnostics`; `examples/errors/missing_lambda_body.caret` |
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
| RUNTIME-DUPLICATE-DEFINITION | internal | DUPLICATE_DEFINITION | `CoreCallableInterpreterTest#rejectsDuplicateDefinitionsAndParameters` |
| RUNTIME-PREMATURE-READ | internal | READ_BEFORE_INITIALIZATION | `CoreCallableInterpreterTest#reportsReadsBeforeSequentialDeclarations` |
| RUNTIME-UNKNOWN-NAME | public | UNKNOWN_NAME | `examples/errors/unknown_name.caret` |
| RUNTIME-NOT-CALLABLE | public | NOT_CALLABLE | `examples/errors/not_callable.caret` |
| RUNTIME-NOT-DEREFERENCEABLE | public | NOT_DEREFERENCEABLE | `CoreCallableInterpreterTest#dereferencesReflectionDictionariesForFunctionsValuesAndMembers` |
| RUNTIME-INFIX-NOT-CALLABLE | public | NOT_CALLABLE | `examples/errors/non_callable_infix.caret` |
| RUNTIME-INVALID-INFIX-ARITY | public | INVALID_INFIX_ARITY | `examples/errors/invalid_infix_arity.caret` |
| RUNTIME-INVALID-COMPOSITION-LEFT | public | INVALID_COMPOSITION_LEFT | `examples/errors/non_callable_composition.caret` |
| RUNTIME-INVALID-COMPOSITION-RIGHT | public | INVALID_COMPOSITION_RIGHT | `examples/errors/invalid_composition_arity.caret` |
| RUNTIME-INVALID-MAP-TRANSFORM | public | INVALID_MAP_TRANSFORM | `TransformInterpreterTest#mapRejectsInvalidInputsAndRetainsLocatedElementFailures` |
| RUNTIME-INVALID-COLLECTION-CALLBACK | public | INVALID_COLLECTION_CALLBACK | `examples/errors/invalid_collection_callback.caret` |
| RUNTIME-ZIP-LENGTH-MISMATCH | public | ZIP_LENGTH_MISMATCH | `examples/errors/zip_length_mismatch.caret` |
| RUNTIME-INVALID-PREDICATE-RESULT | public | INVALID_PREDICATE_RESULT | `examples/errors/invalid_predicate_result.caret` |
| RUNTIME-AMBIGUOUS-CALL-ARITY | public | TOO_MANY_ARGUMENTS | `examples/errors/ambiguous_call_arity.caret` |
| INTERNAL-TOO-MANY-FUNCTION-ARGUMENTS | internal | TOO_MANY_ARGUMENTS | `DiagnosticCoverageTest#everyInternalCatalogVariantIsIndividuallyIdentifiable` |
| INTERNAL-TOO-MANY-PARTIAL-ARGUMENTS | internal | TOO_MANY_ARGUMENTS | `DiagnosticCoverageTest#everyInternalCatalogVariantIsIndividuallyIdentifiable` |
| RUNTIME-EVALUATION-DEPTH | internal | CALL_DEPTH_EXCEEDED | `DiagnosticCoverageTest#everyInternalCatalogVariantIsIndividuallyIdentifiable` |
| RUNTIME-CALL-DEPTH | public | CALL_DEPTH_EXCEEDED | `examples/errors/call_depth.caret` |
| RUNTIME-INVALID-CONDITION | public | INVALID_CONDITION | `examples/errors/runtime_invalid_condition.caret` |
| RUNTIME-EXPECTED-NUMBER | public | EXPECTED_NUMBER | `examples/errors/expected_number.caret` |
| RUNTIME-EXPECTED-STRING | public | EXPECTED_STRING | `examples/errors/expected_string.caret` |
| RUNTIME-EXPECTED-SEQUENCE | public | EXPECTED_SEQUENCE | `examples/errors/expected_sequence.caret` |
| RUNTIME-CONVERSION-KEYED-SEQUENCE | public | EXPECTED_SEQUENCE | `examples/errors/keyed_conversion.caret`; `ConversionInterpreterTest#keyedConversionRequiresProjectionAndEmptyPackingIsSelected` |
| RUNTIME-EXPECTED-DICTIONARY | public | EXPECTED_DICTIONARY | `examples/errors/expected_dictionary.caret` |
| RUNTIME-EXPECTED-COLLECTION | public | EXPECTED_COLLECTION | `examples/errors/expected_collection.caret` |
| RUNTIME-EXPECTED-CONTAINER | public | EXPECTED_CONTAINER | `examples/errors/expected_container.caret`; `CollectionStateInterpreterTest#containerReadsAndWritesRejectNonContainersAtLocatedOperands` |
| RUNTIME-EXPECTED-WITH-TARGET | public | EXPECTED_WITH_TARGET | `EagerScopedInterpreterTest#withRejectsInvalidTargetsAndOuterCannotBecomeAScopeValue`; `examples/errors/invalid_with_target.caret` |
| SEMANTIC-INVALID-OUTER-PATH | public | INVALID_OUTER_PATH | `EagerScopedInterpreterTest#withRejectsInvalidTargetsAndOuterCannotBecomeAScopeValue`; `examples/errors/invalid_outer_path.caret` |
| RUNTIME-CONTRADICTORY-COLLECTION-GUARANTEES | public | CONTRADICTORY_COLLECTION_GUARANTEES | `CollectionStateInterpreterTest#internalCollectionProvidersRejectContradictoryGuaranteesWithoutReadingContent` |
| RUNTIME-EAGER-INFINITE | public | EAGER_INFINITE | `EagerScopedInterpreterTest#eagerRejectsInfiniteAndCyclicCollectionsWithLocatedErrors` |
| RUNTIME-CONVERSION-INFINITE | public | EAGER_INFINITE | `ConversionInterpreterTest#conversionRejectsDeclaredInfiniteInputBeforeEnumeration` |
| RUNTIME-EAGER-CYCLE | public | EAGER_CYCLE | `EagerScopedInterpreterTest#eagerRejectsInfiniteAndCyclicCollectionsWithLocatedErrors` |
| RUNTIME-INVALID-DICTIONARY-KEY | public | INVALID_DICTIONARY_KEY | `examples/errors/invalid_dictionary_key.caret` |
| RUNTIME-INVALID-COLLECTION-KEY | public | INVALID_COLLECTION_KEY | `examples/errors/invalid_collection_key.caret`; `examples/errors/eager_invalid_collection_key.caret`; `CoreCallableInterpreterTest#unifiedCollectionAccessSupportsSugarContractsCompositeKeysAndPartials`; `CoreCallableInterpreterTest#eagerSnapshotsKeepTheirAccessKeyContracts` |
| RUNTIME-AMBIGUOUS-COLLECTION-SHAPE | public | AMBIGUOUS_COLLECTION_SHAPE | `examples/errors/ambiguous_collection_shape.caret` |
| RUNTIME-DIVISION-BY-ZERO | public | DIVISION_BY_ZERO | `examples/errors/division_by_zero.caret` |
| RUNTIME-NONFINITE-RESULT | public | NON_FINITE_RESULT | `examples/errors/non_finite_result.caret` |
| RUNTIME-IMPLICIT-PRECISION-LOSS | public | IMPLICIT_PRECISION_LOSS | `NumericTemplateInterpreterTest#dynamicPrecisionLossRetainsRuntimePhaseAndExactLocation`; `NumericTemplateInterpreterTest#modifiedAndDerivedNumericRequirementsKeepStrictPolicyAcrossAliases`; `NumericTemplateInterpreterTest#dynamicDerivedRequirementsDoNotLetStaticWarningsBypassStrictChecks`; `examples/errors/aliased_precision_loss_strict.caret`; `NumericTemplateInterpreterTest#mixedNumericOperationsReportLossBeforePromotingExactIntegers`; `examples/errors/mixed_precision_loss_strict.caret`; `CaretSandboxTest#precisionWarningsRemainSeparateAcrossEmbeddingOperations` |
| RUNTIME-UNSUPPORTED-CONVERSION | public | UNSUPPORTED_CONVERSION | `examples/errors/unsupported_conversion.caret`; `ConversionInterpreterTest#conversionUsesSelectedToStringAndConsumesLazyInput` |
| RUNTIME-INVALID-PACKED-LAYOUT | public | INVALID_PACKED_LAYOUT | `examples/errors/invalid_packed_layout.caret`; `ConversionInterpreterTest#packedConversionSelectsMembershipAndSequenceConversionRemovesIt` |
| SEMANTIC-IMPLICIT-PRECISION-LOSS | public | IMPLICIT_PRECISION_LOSS | `NumericTemplateInterpreterTest#literalPrecisionLossIsReportedAtAnalysisOnce` |
| RUNTIME-INVALID-DYNAMIC-FIELD | public | INVALID_DYNAMIC_FIELD_NAME | `examples/errors/invalid_dynamic_key.caret` |
| RUNTIME-TEMPLATE-INVALID-CONSTRUCTOR | public | TEMPLATE_INVALID_CONSTRUCTOR | `examples/errors/template_invalid_constructor.caret` |
| RUNTIME-TEMPLATE-NONCOMPARABLE-FIXED-VALUE | public | TEMPLATE_NONCOMPARABLE_FIXED_VALUE | `examples/errors/template_noncomparable_fixed.caret` |
| RUNTIME-CALLABLE-EQUALITY | public | CALLABLE_EQUALITY | `examples/errors/callable_equality.caret` |
| RUNTIME-CALLABLE-RENDERING | public | CALLABLE_RENDERING | `DispatchDiagnosticInterpreterTest#toStringRejectsUnsupportedCallablesAndNonStringSpecializationResults` |
| RUNTIME-MIXED-HOLES | public | MIXED_HOLE_STYLES | `examples/errors/mixed_holes.caret` |
| RUNTIME-INVALID-ASSERTION | public | INVALID_ASSERTION | `examples/errors/invalid_assertion.caret` |
| RUNTIME-CONTRACT-VIOLATION | public | CONTRACT_VIOLATION | `examples/errors/contract_violation.caret`; `examples/errors/template_missing_required.caret`; `NumericTemplateInterpreterTest#templateCompletionRejectsNondefaultableAndWrongShapesWithLocations` |
| RUNTIME-CONVERSION-CONTRACT-VIOLATION | public | CONTRACT_VIOLATION | `examples/errors/conversion_range.caret`; `ConversionInterpreterTest#numericConversionChecksBoundariesAfterTruncation` |
| RUNTIME-PACKED-APPEND-VIOLATION | public | CONTRACT_VIOLATION | `ConversionInterpreterTest#packedAppendEnumerationEagerAndAliasesPreserveSelectedLayout` |
| RUNTIME-PACKED-LAYOUT-VIOLATION | public | CONTRACT_VIOLATION | `PackedLayoutTest#broadAndNullableLayoutsFailBeforeReadingAnyPayload` |
| RUNTIME-CONVERSION-SHAPE-VIOLATION | public | CONTRACT_VIOLATION | `examples/errors/conversion_shape.caret`; `ConversionInterpreterTest#structuralConversionUsesExactShapeAndValidatesRepeatedAndFixedValues` |
| RUNTIME-EFFECT-CONSTRAINT-REQUIRES-CALLABLE | public | EFFECT_CONSTRAINT_REQUIRES_CALLABLE | `ReflectionContractInterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced`; `examples/errors/effect_constraint_noncallable.caret` |
| RUNTIME-EFFECT-ALLOWANCE-EXCEEDED | public | EFFECT_ALLOWANCE_EXCEEDED | `ReflectionContractInterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced` |
| RUNTIME-UNKNOWN-CALL-EFFECTS | public | UNKNOWN_CALL_EFFECTS | `ReflectionContractInterpreterTest#invocationRejectsUnavailableEffectBoundsBeforeExecutingTheCallable`; `TransformInterpreterTest#mapRejectsInvalidInputsAndRetainsLocatedElementFailures`; `examples/errors/unknown_call_effects.caret` |
| SEMANTIC-INCOMPATIBLE-CONTRACTS | public | INCOMPATIBLE_CONTRACTS | `examples/errors/incompatible_inferred_contracts.caret`; `examples/errors/incompatible_declared_inference.caret`; `examples/errors/invalid_condition.caret` |
| SEMANTIC-INCOMPATIBLE-COMPOSITION | public | INCOMPATIBLE_CONTRACTS | `examples/errors/incompatible_composition_contracts.caret` |
| SEMANTIC-AMBIGUOUS-CONTRACT | public | AMBIGUOUS_CONTRACT | `examples/errors/ambiguous_inferred_contract.caret` |
| SEMANTIC-CONTRACT-DERIVATION-CYCLE | public | CONTRACT_DERIVATION_CYCLE | `examples/errors/contract_derivation_cycle.caret` |
| RUNTIME-MIXED-COLLECTION-SHAPE | public | MIXED_COLLECTION_SHAPE | `examples/errors/mixed_collection_shape.caret`; `CollectionStateInterpreterTest#fieldCollectionsSupportContextualShapesMissingPartsAndGeneralKeys` |
| SEMANTIC-INVALID-REFINEMENT | public | INVALID_REFINEMENT | `ContractInferenceTest#validatesOnlyProvenPureUnaryBooleanRefinements`; `ContractInferenceTest#validatesLambdaRefinementsWithTheOrdinaryCallableProof`; `DispatchDiagnosticInterpreterTest#invalidRefinementsAreRejectedBeforeProgramEffects`; `ContractInterpreterTest#rejectsInvalidLambdaRefinementsBeforeProgramEffects`; `examples/errors/invalid_refinement.caret`; `examples/errors/invalid_lambda_refinement.caret` |
| SEMANTIC-INVALID-CONTRACT-VARIABLE | public | INVALID_CONTRACT_VARIABLE | `ReflectionContractInterpreterTest#arrowContractVariablesAreContiguousAndRequireGenericRelationships`; `ReflectionContractInterpreterTest#declarationVariablesIncludeNestedArrowsAndRejectUnrelatedOccurrences` |
| SEMANTIC-AMBIGUOUS-CLAUSE-NAME | public | AMBIGUOUS_CLAUSE_NAME | `ReflectionContractInterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced`; `examples/errors/ambiguous_clause_name.caret` |
| SEMANTIC-UNKNOWN-CLAUSE-NAME | public | UNKNOWN_CLAUSE_NAME | `ReflectionContractInterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced`; `examples/errors/unknown_clause_name.caret` |
| SEMANTIC-CONFLICTING-EFFECT-ALLOWANCE | public | CONFLICTING_EFFECT_ALLOWANCE | `ReflectionContractInterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced`; `examples/errors/conflicting_effect_allowance.caret` |
| SEMANTIC-INVALID-EFFECT-MODIFIER | public | INVALID_EFFECT_MODIFIER | `ReflectionContractInterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced`; `examples/errors/invalid_effect_modifier.caret` |
| SEMANTIC-EFFECT-AS-CONTRACT-ARGUMENT | public | EFFECT_AS_CONTRACT_ARGUMENT | `ReflectionContractInterpreterTest#effectCatalogMixedClausesAndExplicitArrowAllowancesAreEnforced`; `examples/errors/effect_contract_argument.caret` |
| SEMANTIC-EFFECT-ALLOWANCE-EXCEEDED | public | EFFECT_ALLOWANCE_EXCEEDED | `ReflectionContractInterpreterTest#inferredAllowanceViolationsAbortBeforeAnyProgramEffects`; `ReflectionContractInterpreterTest#lazyMemberReificationUsesEstablishedProviderEntriesAndRemainsPureForContainers`; `examples/errors/function_effect_allowance.caret` |
| SEMANTIC-UNKNOWN-CALL-EFFECTS | public | UNKNOWN_CALL_EFFECTS | `DiagnosticCoverageTest#catalogedSemanticUnknownCallEffectsHasExactRepresentation` (catalog-only; no current semantic emitter) |
| SEMANTIC-INCONSISTENT-OVERLOAD-ARITY | public | INCONSISTENT_OVERLOAD_ARITY | `examples/errors/inconsistent_overload_arity.caret` |
| RUNTIME-NO-APPLICABLE-OVERLOAD | public | NO_APPLICABLE_OVERLOAD | `examples/errors/no_applicable_overload.caret` |
| RUNTIME-AMBIGUOUS-OVERLOAD | public | AMBIGUOUS_OVERLOAD | `examples/errors/ambiguous_overload.caret` |
| INTERNAL-UNKNOWN-UNARY-OPERATOR | internal | UNKNOWN_OPERATOR | `DiagnosticCoverageTest#everyInternalCatalogVariantIsIndividuallyIdentifiable` |
| INTERNAL-UNKNOWN-BINARY-OPERATOR | internal | UNKNOWN_OPERATOR | `DiagnosticCoverageTest#everyInternalCatalogVariantIsIndividuallyIdentifiable` |
| INTERNAL-INVARIANT | internal | INTERNAL_ERROR | `DiagnosticCoverageTest#everyInternalCatalogVariantIsIndividuallyIdentifiable` |
| HOST-FILE-USAGE | host | — | `MainTest#rejectsExtraFileModeArguments` |
| HOST-TEST-USAGE | host | — | `MainTest#hostCatalogMessagesHaveExactOutput` |
| HOST-INSPECT-USAGE | host | — | `MainTest#hostCatalogMessagesHaveExactOutput` |
| HOST-SOURCE-READ-FAILURE | host | — | `MainTest#fileSystemFailuresAreReportedWithoutAStackTrace` |
| HOST-TEST-READ-FAILURE | host | — | `MainTest#hostCatalogMessagesHaveExactOutput` |
| HOST-INSPECT-READ-FAILURE | host | — | `MainTest#inspectReportsUnreadableFilesWithoutAStackTrace` |
| HOST-REPL-TERMINAL-REQUIRED | host | — | `MainTest#hostCatalogMessagesHaveExactOutput` |
| HOST-REPL-HISTORY-READ | host | — | `JLineReplTest#unreadableHistoryFallsBackToMemoryWithAClearWarning` |
| HOST-REPL-HISTORY-WRITE | host | — | `JLineReplTest#unwritableHistoryReportsOneCataloguedWarning` |
