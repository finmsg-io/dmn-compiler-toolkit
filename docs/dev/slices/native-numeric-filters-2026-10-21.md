# Exact numeric equality filters with string projection

A terminal path over a typed context-list equality filter can now use an exact
numeric-key projection. The proof requires a direct declared list input, a
numeric context member compared with a bounded numeric literal, and a declared
string output member. Terminal ANY or string/ANY-list result declarations are
supported. Other predicates and scalar declared results retain their existing
route.

Input preparation applies shared declared-input coercion, then represents the
numeric member as a canonical coefficient/scale string and the output member as text.
It does not evaluate equality or select rows. Native SQL filters by the canonical
key and transforms matching rows to their text values. This avoids comparisons
through the original double representation, including beyond-double and beyond-
Spark-decimal precision. Original input columns are preserved. Invalid numeric
conversions are flagged for SQL-time rejection, leaving unused projections inert.
Both CTE query forms carry collision-safe projection columns.

## Verification

- Focused regression passed: 48 native SQL actions (100%), zero fallback (0%),
  denominator 48; failed/unknown zero. One JUnit test passed. Shared-runtime
  comparisons cover decimals separated by 1e-38, integers beyond exact double
  precision, 1E+400, scaled-equal values, quoted strings/backslashes, null names,
  null rows, empty/null inputs, invalid declared inputs, ANY/list declarations,
  original-column preservation, projection-name collisions and both query forms.
  Generator target log: `native-numeric-filter-focused-2026-10-21.log`.
- First full generator regression/install passed: 126/126 tests; 2,202 native
  actions (96.16%), 88 fallback (3.84%) of 2,290; failed/unknown zero. Log:
  `native-numeric-filter-generator-2026-10-21.log`. Review then replaced
  stripTrailingZeros-based keys with explicit coefficient trimming and a long
  scale, avoiding arithmetic overflow and reparsing at extreme BigDecimal scales.
- Expanded focused regression passed: 48 native actions (100%), zero fallback
  (0%) of 48; failed/unknown zero. Added Java minimum/maximum-scale decimals.
  Log: `native-numeric-filter-scale-focused-2026-10-21.log`.
- Corrected full generator regression/install passed: 126/126 tests; 2,202
  native actions (96.16%), 88 fallback (3.84%) of 2,290; failed/unknown zero.
  Hybrid integration: 2,189 native (96.14%), 88 fallback (3.86%) of 2,277.
  DMN integration: 13 native (100%), zero fallback (0%) of 13.
  Formatting/install/Maven exit zero. Generator target log:
  `native-numeric-filter-scale-generator-2026-10-21.log`. Target/installed JAR SHA256:
  `8B49AD6E22D4ABC2E254D6D830F7FECE605C1D18EB6279DD3DA40BBF14131BAD`.
- Official filter TCK passed: one native case (100%), zero fallback (0%) of
  one; failed/unknown zero, expected model rejections zero. Exactly one conversion
  versus the full baseline: `0001-filter#001`, with no reversals or rejection
  changes within the suite. Maven exit zero. Runner target evidence:
  `native-numeric-filter-accounting-2026-10-21.json` and
  `native-numeric-filter-tck-2026-10-21.log`.
- Combined with the earlier user-defined-function conversion: targeted estimate
  2,691 native (99.12%), 24 fallback (0.88%) of 2,715 executable baseline cases.
  This is not a new full result. Last full remains 2,689 native (99.04%),
  26 fallback (0.96%), plus 676 provisional expected model rejections separately.
  Two conversions since the latest full checkpoint. Latest usage check: 84%
  primary remaining; full cadence remains 45 minutes work, usage below 20%,
  or twenty conversions.

Changes remain uncommitted. The 100% native goal stays open.
