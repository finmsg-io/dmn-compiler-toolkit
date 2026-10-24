# Exact boolean results from scalar function contexts

The shared lending affordability functions build dynamic contexts and return one
boolean field. A bounded expansion now resolves those scalar FEEL BKM calls,
named arguments, context locals and context-field selection into a guarded native
SQL evaluation plan. Numeric decision dependencies are expanded to their input
sources rather than read through double-valued intermediate columns. Numeric
operations reuse exact coefficient arithmetic, DECIMAL128 division and the
Math.pow conversion path established for numeric context loops.

All context entries and invocation arguments retain their evaluation order.
Conditional branches and boolean short circuits carry guards, so inactive
branches do not evaluate invalid arithmetic. Context entries that do not contribute
to the selected field still execute and retain their error behavior. Expansion
and scheduled steps are bounded; unsupported functions, types, cycles and result
shapes retain fallback.

This proof admits boolean results only. Raw string comparison keys preserve Java
UTF-16 code units, including unpaired surrogates. Computed string dependencies
are admitted only when their outputs are proven valid literal strings (or null),
such as risk-category enums. Numeric and string projections convert representation
only; all calculation and predicates execute in Spark SQL.

Native dependency planning follows the expanded inputs and lossless boolean/string
leaf decisions. The default dependency walker used for runtime fallback slicing
keeps the original complete function graph. Boolean outputs are standard Spark
booleans and remain usable by existing native decision consumers.

## Coverage boundary

This implementation applies to proven runtime-IR patterns, not official case IDs.
Its boolean result, binding, operation and expansion limits are intentional;
nearby model variants can retain fallback. Passing the official lending cases
does not establish support for arbitrary context results or dynamic calls.
See [native coverage interpretation](../../sparksql-architecture.md#native-coverage-what-the-tck-rate-means)
for the distinction between TCK routing and broader DMN/FEEL support, and the
validation needed beyond the fixtures.

## Verification

Initial focused regression passed both tests: 20 native SQL actions (100%),
0 fallback (0%) of 20; unknown 0, four intentional native SQL error assertions.
The tests compare exact affordability results beyond double precision and beyond
38-digit decimal precision, unknown/null risk handling, nested field paths,
named binding, lazy branches and eager unused-context-entry evaluation.
Log: `native-scalar-context-focused-2026-10-23.log`.

Guarded focused regression passed three tests: 30 native actions (100%),
0 fallback (0%) of 30; unknown 0, four intentional native SQL errors. It adds
UTF-16 identity checks for question marks, unpaired surrogates, supplementary
characters, empty strings and null. The proof is restricted to boolean outputs,
constant-string dependencies and bounded scheduled evaluation.
Log: `native-scalar-context-guarded-focused-2026-10-23.log`.

A capability diagnostic before the final guards classified every 0004-lending
decision as native and all chapter-example decisions except the numeric monthly
installment. The boolean proof expands that installment within its own calculation.
This diagnostic is not an executable TCK result.

Initial full generator regression/install passed 148/148 tests: 2,904 native
SQL actions (96.80%), 96 fallback (3.20%) of 3,000; unknown 0. Fifty-two native
SQL errors are intentional passed assertions; no JUnit failures/errors/skips.
Hybrid integration: 2,891 native (96.79%), 96 fallback (3.21%) of 2,987;
DMN integration: 13 native (100%), 0 fallback (0%) of 13.
Log: `native-scalar-context-generator-2026-10-23.log`.

A subsequent review added an explicit sequence for `in` candidates: wildcard and
empty tests must still evaluate a candidate expression that can raise an error.
The final full generator run includes this regression and rejects unrepresentable
input field names before emitting projection metadata. That 149-test run failed one value assertion in the new edge test. It recorded
2,905 native (96.80%), 96 fallback (3.20%) of 3,001; unknown 0, 52 intentional
SQL errors. The shared runtime's expression-level `in` iterates its test list
rather than honoring the decision-table wildcard flag, and returns null for a
null candidate. The SQL plan now follows that implementation and guards test
expressions when the candidate is null.
Log: `native-scalar-context-final-generator-2026-10-23.log`.

Corrected focused regression passed four tests: 42 native actions (100%),
0 fallback (0%) of 42; unknown 0, eight intentional native SQL errors. It covers
normal/null/invalid candidates for wildcard-marked and empty test lists, plus
all previous affordability, evaluation-order and UTF-16 cases.
Log: `native-scalar-context-in-corrected-focused-2026-10-23.log`.

Corrected full generator regression/install passed 149/149 tests, no JUnit
failures/errors/skips: 2,916 native SQL actions (96.81%), 96 fallback (3.19%)
of 3,012; unknown 0. Fifty-six native SQL errors are intentional passed assertions.
Hybrid integration: 2,903 native (96.80%), 96 fallback (3.20%) of 2,999;
56 expected errors, unknown 0. DMN integration: 13 native (100%), 0 fallback
(0%) of 13; failed/unknown 0.
Log: `native-scalar-context-corrected-generator-2026-10-23.log`.
Built and installed generator SHA256:
`72FA1829B2E181FC7F027F3033ED00B49BE5E4527DD30DBEA85EACCD9BB223EA`.

2026-10-23 verified full scalar-context checkpoint: 3,391/3,391 official
cases passed. Executable denominator 2,715: 2,713 native (99.93%), 2 fallback
(0.07%); failed/unknown 0. Expected model rejections remain 676; errors,
unsupported, excluded and invalid 0. Exact comparison with the payment-loop
checkpoint has identical case keys and six FALLBACK -> NATIVE conversions:
0004-lending#001 and 0087-chapter-11-example#001/#002/#003d/#003e/#004.
No route reversals or rejection changes. Fallback count decreased 75% (8 to 2).
Bounded boolean BKM/context plans preserve exact numeric intermediates, eager
context/argument evaluation, lazy branches and UTF-16 string comparison identity.
Generator regression/install passed 149/149 tests: 2,916 native SQL actions
(96.81%), 96 fallback (3.19%) of 3,012; unknown 0. Fifty-six native SQL errors
are expected passing assertions; no JUnit failures/errors/skips. Corrected focused
regression: 42 native (100%), 0 fallback (0%) of 42; unknown 0, eight expected
native SQL errors. Per-run results and the corrected expression-level in semantics
are recorded in docs/dev/slices/native-scalar-contexts-2026-10-23.md.
Target/installed generator SHA256:
72FA1829B2E181FC7F027F3033ED00B49BE5E4527DD30DBEA85EACCD9BB223EA.
Accounting: dmn-tck-runner/target/native-scalar-context-full-accounting-2026-10-23.json.
Log: dmn-tck-runner/target/native-scalar-context-full-tck-2026-10-23.log.
Maven exited 0; Surefire logged a fork shutdown timeout after successful completion.
Remaining: 0007-date-time#001 (temporal construction/validation) and
0014-loan-comparison#001 (context/list metrics and sorting). Next work must retain
precision, offsets and duration identity for temporal values, or prove the full
loan-comparison result and sorting semantics. Official fixtures are unchanged.
This checkpoint is included in the scalar-context and payment-loop implementation commit.

2026-10-23 requested confirmation full TCK rerun: 3,391/3,391 passed.
Executable cases: 2,713 native (99.93%), 2 fallback (0.07%) of 2,715;
676 expected model rejections; failed/errors/unknown 0. All case outcomes and
routes are identical to the preceding scalar-context full run. Remaining fallback:
0007-date-time#001 and 0014-loan-comparison#001. This confirms six conversions
from the eight-fallback payment-loop checkpoint, with no reversals.
Accounting: dmn-tck-runner/target/native-scalar-context-confirmation-full-accounting-2026-10-23.json.
Log: dmn-tck-runner/target/native-scalar-context-confirmation-full-tck-2026-10-23.log.
Generator artifact SHA256: 72FA1829B2E181FC7F027F3033ED00B49BE5E4527DD30DBEA85EACCD9BB223EA.
Generator regression remains 149/149 passed: 2,916 native (96.81%), 96 fallback
(3.19%) of 3,012 SQL actions; unknown 0, 56 expected SQL error assertions.
No production changes were made during or after these verification runs.

