# Native Spark SQL rejection audit: 2026-10-12

The active goal is **100% native execution and zero fallback**. The latest full
run passed 3,391/3,391, with **2,512 native (92.52%) and 203 fallback (7.48%) of
2,715 executable cases**. Another 676 cases are provisional model rejections,
not native SQL executions. This audit does not change those measured rates or
claim additional native conversions.

The compiler-diagnostic snapshot was copied from the completed full run's
`tck-expected-rejections-optimized.tsv` into
`dmn-tck-runner/target/priority-native-full-rejection-diagnostics.tsv`.
Every rejected accounting entry was then matched to its case in the current
official XML, and all result-node error flags were counted. The resulting
`priority-native-full-rejection-expectation-audit.tsv` has **676 matched cases,
all with every output marked `errorResult="true"`**. There were no missing cases
or unexpected successful-output expectations. This is an expectation and
diagnostic inventory, not a completed semantic justification of model rejection.

| Compiler diagnostic | Cases |
| --- | ---: |
| `INVALID_OPERAND_TYPES` | 635 |
| `DECISION_TYPE_MISMATCH` | 18 |
| `INVALID_OPERAND_TYPE` | 10 |
| `INCOMPATIBLE_BRANCH_TYPES` | 3 |
| `INVALID_BETWEEN_OPERAND` | 3 |
| `BKM_RETURN_TYPE_MISMATCH` | 1 |
| `DUPLICATE_NAME` | 1 |
| `INVALID_FILTER_TYPE` | 1 |
| `INVALID_PROPERTY_ACCESS` | 1 |
| `INVOCATION_ARGUMENT_TYPE_MISMATCH` | 1 |
| `MISSING_INVOCATION_BINDING` | 1 |
| `UNKNOWN_FUNCTION` | 1 |
| **Total** | **676** |

Of the 635 operand-type rejections, **626 are in 0100 arithmetic and nine in 0075
exponent**. This makes invalid-operand semantics a shared compiler blocker to
investigate, with a much larger cohort than the ten fallback conversions in the
latest implementation checkpoint. These 635 cases are candidates for semantic
review; this audit does not establish that all can or should execute as native
SQL nulls.

`OfficialTckSuiteTest` currently accepts a compilation rejection when
`expectsOnlyErrors(testCase)` is true and `preparation.modelRejected()` is true.
It records `EXPECTED_MODEL_REJECTION` without executing generated SQL. The error
flags therefore explain why these cases pass, but do not establish whether a
particular rejection is a required model-validation failure or a FEEL expression
error that should reach evaluation.

The next semantic review should classify the diagnostic cohorts by operator and
operand kinds, distinguish genuine invalid models from evaluable expression
errors, and verify the shared runtime and both lowered/optimized IR behavior.
Any change must apply to language semantics rather than TCK expected answers.
Do not globally downgrade type errors or return SQL NULL merely because a case
expects an error. Preserve legitimate validation failures and test the successful
counterexamples, temporal identity and precision, result coercion, and function
binding. Re-run full accounting after changes: newly executable cases change the
denominator and must no longer be reported as provisional rejections.

The executable-case work also remains incomplete: 203 fallbacks include 18
external Java cases, 17 lambda cases, 14 for-loop cases, 13 instance-of cases,
and 11 cases each in list functions and coercion. Retain the user's priority
order: temporal representation/validation; shared list/filter/quantifier typing;
bounded loops/lambdas/function binding. Do not mark the 100% goal complete until
the full required scope, including the rejection semantic audit, is verified.
