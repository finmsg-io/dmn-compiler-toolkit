# Exact native date comparison tables

Terminal UNIQUE tables with one date input, two complementary ordered tests,
and one Boolean output now use the existing lossless calendar-field projections.
The endpoint can be a fixed date or a second declared DATE input. Both required
input projections travel through both CTE query forms. Original input payloads
remain available to fallback consumers.

The proof requires the same endpoint in both rules, complementary relational
operators, Boolean literal outputs, and no allowed-value constraints or default
output. Equality tests, overlapping rules, other hit policies, arbitrary endpoint
expressions, and other result kinds retain their existing route. Null or invalid
coerced dates match neither rule and yield null. SQL orders a BIGINT year/month/day
key, preserving year zero, negative years, and the full LocalDate range without
Spark date or timestamp casts.

## Verification

- Focused regression passed: 160 native actions (100%), zero fallback (0%),
  denominator 160; failed/unknown zero. One JUnit test passed. Cases cover all
  four ordered operators, literal and dynamic endpoints, nulls on either side,
  invalid/singleton/empty-list inputs, year zero, negative years, LocalDate.MIN,
  LocalDate.MAX, and both individual and AllDecisions query forms. Shared runtime
  evaluation provides expected results. Generator target log:
  `native-date-table-focused-2026-10-20.log`.
- Full generator regression/install passed: 121/121 JUnit tests; 1,961 native
  actions (95.57%), 91 fallback (4.43%), denominator 2,052; failed/unknown zero.
  Hybrid integration: 1,951 native (95.68%), 88 fallback (4.32%) of 2,039.
  DMN integration: 10 native (76.92%), 3 fallback (23.08%) of 13.
  Formatting/install/Maven exit zero. Generator target log:
  `native-date-table-generator-2026-10-20.log`. Target/installed JAR SHA256:
  `89EAF0F5B1418DA320E40E901805EAD54916E51A2193E8AFE83562942CA63ABA`.
- Targeted official table TCK passed: 4/4 cases, 4 native (100%), zero
  fallback (0%), denominator four; failed/unknown zero, expected model rejections
  zero. Exactly two conversions against the last full baseline:
  `0017-tableTests#003` and `0017-tableTests#004`; no reversals or rejection
  changes within the suite. Maven exit zero. Runner target evidence:
  `native-date-table-accounting-2026-10-20.json` and
  `native-date-table-tck-2026-10-20.log`.
- Full official Spark SQL TCK passed: 3,391/3,391; 2,685 native (98.90%),
  30 fallback (1.10%) of 2,715 executable cases. Failed/unknown routes zero;
  compilation/execution errors, unsupported, excluded and invalid counts zero.
  Separately: 676 provisional expected model rejections, unchanged. Exact
  comparison with the previous full run verifies four conversions: the two
  date tables plus `0011-insert-remove#001` and `0021-singleton-list#001`.
  Zero reversals and no rejection changes. Fallback decreased 34 to 30 (11.76%).
  Runner target evidence: `native-date-table-full-accounting-2026-10-20.json`
  and `native-date-table-full-tck-2026-10-20.log`. Full run triggered by usage
  falling to 13% remaining. The previous targeted estimate is now fully verified.
  Full Maven exit zero. Documentation facts, strict MkDocs build and whitespace
  checks passed.

Changes remain uncommitted. The 100% native goal stays open. Next work: exact
numeric/context filters, calendar arithmetic preserving zones, lexical function
binding and decision-service input decisions. Restart the agreed full-run cadence
at this checkpoint.
