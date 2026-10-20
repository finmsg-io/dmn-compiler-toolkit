# Exact dynamic positions for native list edits

Continues from uncommitted string-selection work (targeted estimate 33 fallbacks).
Last full: 3,391/3,391 passed; 2,681 native (98.75%), 34 fallback (1.25%) of
2,715 executable cases; 676 provisional expected model rejections separately.

Terminal insert before and remove calls over homogeneous string/Boolean lists
use a separate exact position projection. Input conversion follows shared NUMBER
coercion and BigDecimal.intValue, preserving fractional truncation and signed
32-bit wrapping before SQL. Positions are not derived through Spark doubles.
An invalid flag defers conversion errors until after source/item null guards.
SQL performs index arithmetic, bounds checks, slicing and reconstruction.

The proof admits typed list inputs or bounded literal decisions, a direct NUMBER
input position, compatible literal/direct-input inserted items, and terminal ANY
or matching list declarations. Numeric list values, arbitrary computations and
deeper nesting remain guarded. Both CTE forms carry collision-safe position fields.
SQL index arithmetic uses BIGINT with guarded INT slice arguments. Null entries
remaining in edited results preserve the shared runtime's rejection behavior.

## Verification

- Focused regression passed: 136 native actions (100%), zero fallback (0%),
  denominator 136; failed/unknown zero. Tests cover flat/nested lists, null/empty
  sources, positive/negative/zero/out-of-range positions, fractions near integers,
  signed wrapping, values beyond exact double precision, projection collisions
  and AllDecisions null short-circuit with NaN. Generator target log:
  `native-list-position-focused-2026-10-20.log`.
- The previous full-generator invocation was interrupted before launch, with
  no log or results. The resumed full regression/install passed: 120/120 JUnit
  tests; 1,801 native actions (95.19%), 91 fallback (4.81%), denominator 1,892;
  failed/unknown zero. Hybrid integration: 1,791 native (95.32%), 88 fallback
  (4.68%) of 1,879; DMN integration: 10 native (76.92%), 3 fallback (23.08%)
  of 13. Formatting/install/Maven exit zero. Generator target log:
  `native-list-position-generator-2026-10-20.log`.
- Target/installed generator JAR SHA256 match:
  `06BAD93247C2BAFBC4C1DE82934FFBEFA134A4BF0E3EA540E1A55F1C6F734980`.
- Official insert/remove TCK passed: one native case (100%), zero fallback
  (0%), denominator one; failed/unknown zero, expected model rejections zero.
  All five requested list-edit decisions are native. Exact comparison with the
  full baseline confirms `0011-insert-remove#001` changed fallback to native;
  no reversals or rejection changes within the suite. Maven exited zero.
  Runner target evidence: `native-list-position-accounting-2026-10-20.json`
  and `native-list-position-tck-2026-10-20.log`.
- Combined targeted estimate with the string-selection conversion:
  2,683 native (98.82%), 32 fallback (1.18%) of 2,715 executable baseline cases.
  This is not a new full result: last full remains 2,681 native (98.75%),
  34 fallback (1.25%), with 676 provisional expected model rejections separately.
  Changes remain uncommitted.
