# Bounded partial-result loops and exact decimal arrays

Continuation from pushed commit `a7b3ec8`. Latest full baseline remains
3,391/3,391 passed: 2,651 native (97.64%), 64 fallback (2.36%) of 2,715
executable cases; 676 provisional expected model rejections separately.
The 100% native goal and semantic rejection audit remain open.

## Change

Closed numeric recurrences with one iteration binding and one partial lookup
per result expression can be evaluated at generation time by the shared FEEL
runtime. Limits: 64 iterations, 128 result syntax nodes, depth twelve, bounded
numeric constants, and local slots below 2,048. Dynamic values, nested loops,
function invocations, multiple partial references and unrepresentable decimal
results retain fallback. Integer range endpoints are bounded to one million.
Only exact native literal results are admitted; there is no recurrence UDF at
query time. The factorial loop is the first official target.

Numeric literal arrays now cast all elements to a shared decimal type derived
from their actual integral and fractional widths. This avoids Spark widening
an integer to DECIMAL(10,0) and truncating a mixed array's fractional scale.
Arrays requiring more than 38 combined digits retain their established route.

## Verification history

- Initial focused run: one test failed repeated-division parity after two native
  actions (100%), zero fallback (0%) of two, zero failed/unknown SQL routes.
  Spark array inference truncated 34-digit FEEL division results to 28 places.
  Log: generator target `native-partial-loop-focused-2026-10-19.log`.
- Decimal-cast rerun: one test failed a scale-sensitive BigDecimal comparison,
  after two native actions (100%), zero fallback (0%) of two; zero failed/unknown
  SQL routes. All digits were preserved; the assertion now compares numeric
  values with BigDecimal.compareTo, consistent with FEEL equality.
  Log: generator target `native-partial-loop-verified-2026-10-19.log`.

- Initial full generator run: 104 of 105 tests passed. The sole failure was an
  obsolete assertion expecting factorial to retain fallback; recurrence parity
  passed. SQL actions: 1,202 native (96.31%), 46 fallback (3.69%) of 1,248,
  zero failed/unknown. Log: `native-partial-loop-full-generator-2026-10-19.log`.
- Corrected full generator run: 105/105 passed. SQL actions: 1,202 native
  (96.31%), 46 fallback (3.69%) of 1,248; zero failed/unknown. Formatting and
  install passed. Log: `native-partial-loop-final-generator-2026-10-19.log`.
  Recurrence checks cover factorial, repeated division, descending decimal
  accumulation and list-driven multiplication; negative guards cover iteration
  count, repeated partial references and oversized decimals.

Target and installed generator JAR SHA-256 match:
`3BEC01768954493285B720CA0BCEBCEB63C834C867F0058E22CB17E4D20C029B`.

## Full official checkpoint

2026-10-19 LATEST FULL partial-loop checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,652 native (97.68%), 63 fallback (2.32%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections.
One verified fallback-to-native conversion: 0084 decision_013 (factorial with
partial[-1]); zero reverse conversions against the 2026-10-18 full checkpoint.
Fallback decreased from 64 to 63 (1.56% fewer fallback cases).
Generator: 105/105 passed; 1,202 native actions (96.31%), 46 fallback (3.69%)
of 1,248; zero failed/unknown. Formatting/install passed. Shared numeric literal
arrays now preserve exact fractional widths when combined with integer literals.
Target/installed JAR SHA-256:
3BEC01768954493285B720CA0BCEBCEB63C834C867F0058E22CB17E4D20C029B.
Saved runner target evidence: native-partial-loop-full-accounting-2026-10-19.json
and native-partial-loop-full-tck-2026-10-19.log.
The 100% native goal and semantic rejection audit remain open. Largest remaining
groups are chapter 11 example (eight) and decision services (five). Direct service
parameters declared ANY require lossless runtime type handling; do not replace
overridable decision inputs with their default expressions. Date-valued loop
results still require lossless typed transport. Retain temporal/list/binding priorities.
Restart full-run cadence here: 45 minutes work, usage remaining below 20%, or
twenty further verified conversions, whichever comes first.

Compared every case key and route with the saved prior full JSON: exactly
`compliance-level-3/0084-feel-for-loops/0084-feel-for-loops-test-01.xml#decision_013`
changed from fallback to native; all other routes are unchanged.

Maven exited zero. Surefire again reported its post-suite fork shutdown timeout
30 seconds after System.exit(0); every case had completed and passed. Spark/fork
cleanup remains a follow-up. This is separate from a TCK case timeout.
Documentation facts verification, strict MkDocs build and Git whitespace checks passed.
