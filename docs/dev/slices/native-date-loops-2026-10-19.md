# Exact terminal literal date loops

Baseline: commit `699e324`, full Spark SQL TCK 3,391/3,391 passed;
2,674 native (98.49%), 41 fallback (1.51%) of 2,715 executable cases.
Separately: 676 provisional expected model rejections, unchanged.

A terminal identity loop over a closed literal date range can now emit a SQL
binary constant containing the exact FEEL date list. Generation uses the shared
runtime once; query execution uses no interpreter/UDF or Spark date conversion.
This retains date identity, list order, leap days, year zero and extended years.
The existing terminal temporal boundary keeps consumers on fallback unless they
have their own proof; date lists are not globally declared native Spark arrays.

The proof requires one binding, lexical depth zero, an identity result referring
to that binding, no partial result, literal DATE endpoints, and at most 256 dates.
The terminal declared result must be ANY or LIST<DATE>. Invalid dates, runtime
exceptions, other loop bodies, dynamic endpoints and larger ranges are rejected.
Shared runtime date iteration currently steps past the endpoint; terminal
LocalDate.MAX ascending and LocalDate.MIN descending ranges overflow. This proof
retains fallback for those cases rather than changing shared runtime behavior.

## Verification

- Initial Maven invocation stopped before tests due to dependency-cache write
  permission: zero SQL actions, native/fallback rates undefined.
- Focused regression passed: one test, six native SQL actions (100%), zero
  fallback (0%), denominator six, zero failed/unknown. Includes both directions,
  leap day, year zero and extended-year bounds. Log under generator target:
  `native-date-loops-focused-2026-10-19.log`.
- Full generator regression/install passed: 115/115 tests. Combined SQL actions:
  1,518 native (96.50%), 55 fallback (3.50%), denominator 1,573; failed/unknown
  zero. Hybrid integration: 1,508 native (96.67%), 52 fallback (3.33%) of 1,560;
  DMN integration: 10 native (76.92%), 3 fallback (23.08%) of 13.
  Added endpoint-overflow guards passed. Formatting/install/Maven exit zero.
  Generator target log: `native-date-loops-generator-2026-10-19.log`.
- Target and installed generator JAR SHA256 match:
  `E2361428BED53B8A54BCC9886524374630784DD09C44995E914D0206D4D18149`.
- Official for-loop TCK passed 24/24: 24 native (100%), zero fallback (0%),
  denominator 24, failed/unknown zero, expected model rejections zero.
  Exact comparison to the saved full baseline shows only `decision_017` and
  `decision_018` changed fallback to native, with no reversals or rejection changes.
  Saved runner target evidence: `native-date-loops-accounting-2026-10-19.json`
  and `native-date-loops-tck-2026-10-19.log`. Maven exit zero.
- Targeted-suite extrapolation: 41 to 39 fallback cases, 2,676 native (98.56%),
  39 fallback (1.44%) of the unchanged 2,715 executable baseline. This is not a
  new full-run result. Last full remains 2,674 native / 41 fallback, with 676
  provisional expected model rejections separately.

Usage at this batch start: 84% primary remaining. Continue with targeted official
verification after the complete generator regression. Full cadence remains
45 minutes work, usage below 20%, or twenty verified conversions.
