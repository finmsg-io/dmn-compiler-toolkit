# Literal calendar calls with shared validation

Continues from the uncommitted date-loop checkpoint. Last full TCK result:
3,391/3,391 passed; 2,674 native (98.49%), 41 fallback (1.51%) of 2,715
executable cases; 676 provisional expected model rejections separately.
The preceding targeted date-loop conversion estimated 39 remaining fallbacks.

## Change

The static function lowerer folds bounded closed calls to day of year, day of
week, month of year and week of year. A dedicated proof admits only literals
and nested date, date and time, time and duration constructors. It uses the
original expression and shared runtime validation, preserving argument arity,
named binding, invalid-value behavior, local dates and exact temporal values.
Only results accepted by the existing native literal converter are emitted.

Bounds: 64 operand nodes, depth eight, at most four arguments per call,
256-character literal values and numeric precision/absolute scale at most 16.
Dynamic references, other function calls, loops and arithmetic are excluded.
Query execution uses native SQL literals without a runtime interpreter/UDF.

The remaining dynamic calendar contexts include temporal arithmetic over ANY
inputs. The current calendar projection omits zone/time fields needed to prove
all such arithmetic lossless. Those cases retain fallback pending a separate
proof; their observed TCK inputs must not be used to specialize routing.

## Verification

- Focused regression passed: one JUnit test, 18 native SQL actions (100%),
  zero fallback (0%), denominator 18; failed/unknown zero. Both optimized and
  unoptimized compilation cover ISO-week boundaries, leap years, year zero,
  extreme year, timestamp nanoseconds/offsets and invalid constructor/binding.
  Generator target log: `native-calendar-literal-focused-2026-10-19.log`.
- Complete generator regression/install passed: 116/116 JUnit tests. Combined
  SQL actions: 1,536 native (96.54%), 55 fallback (3.46%), denominator 1,591;
  failed/unknown zero. Hybrid integration: 1,526 native (96.70%), 52 fallback
  (3.30%) of 1,578. DMN integration: 10 native (76.92%), 3 fallback (23.08%)
  of 13. Formatting/install/Maven exit zero. Generator target log:
  `native-calendar-literal-generator-2026-10-19.log`.
- Target/installed generator JAR SHA256 match:
  `BA25061A396DB3784C55D5FA83AE0820A5CB997D793CE9F9A898C0605793B603`.
- Official calendar TCK passed 43/43 cases: 39 native (90.70%), 4 fallback
  (9.30%), denominator 43; failed/unknown zero, expected model rejections zero.
  Exact comparison with the saved full baseline shows only
  `0098-feel-week-of-year-function#date_008` changed fallback to native;
  no reversals or rejection reclassifications within these suites. The four
  dynamic calendar context cases remain fallback. Maven exited zero.
  Saved runner target evidence: `native-calendar-literal-accounting-2026-10-19.json`
  and `native-calendar-literal-tck-2026-10-19.log`.
- Combined targeted estimate with the preceding two date-loop conversions:
  2,677 native (98.60%), 38 fallback (1.40%) of the 2,715 executable baseline.
  This is not a new full-run result. Last full remains 2,674 native / 41 fallback,
  with 676 provisional expected model rejections separately.
