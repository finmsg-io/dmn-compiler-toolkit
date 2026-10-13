# Native calendar inputs — 2026-10-13

Direct day-of-year, day-of-week, month-of-year and ISO week-of-year calls over
ANY model inputs use generated local-date projections. Original FEEL payloads
remain intact, including nanoseconds, offsets, named zones and duration kinds.
SQL computes calendar answers from the local Gregorian year/month/day, including
year zero, negative years and the supported LocalDate extremes. Unsupported
values produce the same null calendar result as the shared runtime.

The caller must use the generated input schema and the schema-aware
`SparkSqlFeelValueCodec.inputRow(model, values, generated.inputSchema())` overload.
Projection fields carry input-slot metadata and avoid collisions with existing
column names. Existing opaque encoding and the original row overload are retained.

## Verification

- Two targeted regressions passed: 82 native actions (98.80%), one deliberate
  fallback action (1.20%), denominator 83; zero failed or unknown routes.
- Complete generator suite: 80/80 tests passed; 764 native actions (97.70%),
  18 fallback actions (2.30%), denominator 782; zero failed or unknown routes.
- Official calendar suites 0095–0098: 62/62 passed; 57 native cases (91.94%),
  five fallback cases (8.06%), denominator 62. Previously 49 native and 13 fallback:
  eight verified conversions and no native-to-fallback regressions.
- Full official TCK: 3,391/3,391 passed; 2,520 native (92.82%), 195 fallback
  (7.18%), denominator 2,715 executable cases. Separately: 676 provisional
  expected model rejections. Zero failures or compilation/execution errors.
  Eight fallback-to-native conversions, zero reverse conversions. Maven exit 0.

The initial regression exposed ISO date-string coercion missing from the
projection. After matching shared calendar conversion it passed 80 native actions.
The next boundary test exposed a route-counter defect: Spark constant folding can
evaluate a fallback UDF before the executed plan is produced. The counter now
checks analyzed and executed plans; the deliberate fallback is explicitly asserted.
The earlier physical-plan-only 83-native/zero-fallback boundary measurement is
superseded. Historical generator rates used that earlier accounting method.

Evidence is in generator target/native-any-calendar-accounted-generator.log,
native-any-calendar-full-generator.log, and runner
target/native-any-calendar-focused-accounting.json. These are SQL-action counts
for regression runs and case counts for official TCK runs respectively.

## Outstanding work

Five focused calendar fallbacks remain: context results containing dynamic
temporal arithmetic and a boxed list argument. Broader shared gaps include lambda
and function binding, bounded loops, list functions and coercion. The goal remains
100% native across the official executable TCK; expected model rejections require
their separate semantic audit.
