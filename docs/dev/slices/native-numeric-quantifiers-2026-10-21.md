# Exact numeric quantifiers and bounded relation rows

The numeric context-list projection now carries sign, decimal exponent and
canonical coefficient digits alongside equality keys. Ordered SQL comparisons
use sign, exponent and digit ordering, preserving exact decimal semantics without
Spark double/decimal casts. Terminal SOME/EVERY proofs allow one direct typed
context-list input, one numeric member, and one bounded numeric literal. All six
comparison operators and reversed literal operands are supported.

SQL aggregate preserves shared early exit: once SOME is true or EVERY is false,
later invalid numeric members are not evaluated. Null members use shared equality
and ordered-comparison truth rules. Empty lists produce the appropriate Boolean
identity. Shared quantification rejects null sources; generated SQL preserves an
explicit error path rather than treating null as empty.

Bounded literal relations now fold through shared runtime evaluation, retaining
column names and order. Quantifier unrolling accepts bounded closed context rows,
and member paths fold after lexical substitution. This also supports a statically
bound predicate BKM over a literal relation. Dynamic or oversized relations and
other quantifier shapes retain their existing route.

## Verification

- Initial focused run failed: one assertion failure for an unlowered relation
  member path and one shared-runtime null-source error across three JUnit tests.
  Completed SQL actions: 56 native (100%), zero fallback (0%) of 56;
  failed/unknown SQL routes zero. Log: `native-numeric-quantifier-focused-2026-10-21.log`.
  Corrected member-path folding and explicit null-source SQL rejection. Null
  source is not asserted as a Boolean result in the successful-value tests.
- Corrected focused run passed all three JUnit tests: 316 native actions (100%),
  zero fallback (0%) of 316; failed/unknown zero. Covers all six operators,
  negative bounds, reversed operands, nearby decimals separated by 1e-38,
  extreme scales, zero/null members, empty lists, early exit before NaN,
  literal relation rows, both SQL query forms and prior numeric filters.
  Log: `native-numeric-quantifier-corrected-focused-2026-10-21.log`.
- Full generator regression/install passed: 128/128 JUnit tests; 2,470 native
  actions (96.56%), 88 fallback (3.44%) of 2,558; failed/unknown zero.
  Hybrid integration: 2,457 native (96.54%), 88 fallback (3.46%) of 2,545.
  DMN integration: 13 native (100%), zero fallback (0%) of 13.
  Formatting/install/Maven exit zero. Generator target log:
  `native-numeric-quantifier-generator-2026-10-21.log`. Target/installed JAR SHA256:
  `64ADE48B86275982C8A0E897AC53D3B0E3F8158CD140DF401ADB9B6E3FC55483`.
- Official quantifier/filter TCK suites passed: 4/4; 4 native cases (100%),
  zero fallback (0%) of four; failed/unknown zero, expected model rejections zero.
  One new conversion: `0016-some-every#001`; prior `0001-filter#001` stays native.
  No reversals or rejection changes in these suites. Maven exit zero. Runner
  target evidence: `native-numeric-quantifier-accounting-2026-10-21.json` and
  `native-numeric-quantifier-tck-2026-10-21.log`.
- Combined targeted estimate with prior user-defined-function conversion:
  2,692 native (99.15%), 23 fallback (0.85%) of 2,715 executable baseline cases.
  This is not a new full result. Last full remains 2,689 native (99.04%),
  26 fallback (0.96%), plus 676 provisional expected model rejections. Three
  verified conversions since the latest full checkpoint. Latest usage check:
  48% primary remaining; full cadence stays 45 minutes work, usage below 20%,
  or twenty conversions.

Changes remain uncommitted. The 100% native goal stays open.
