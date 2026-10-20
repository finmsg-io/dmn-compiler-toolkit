# Native dynamic keys in literal string contexts

Continues from uncommitted date-loop/calendar work. Last full TCK: 3,391/3,391
passed, 2,674 native (98.49%), 41 fallback (1.51%) of 2,715 executable cases;
676 provisional expected model rejections separately. Subsequent targeted work
verified three conversions, yielding an estimated 38 fallback cases.

## Change

A terminal get value call over a bounded literal context of string/null values
can use native SQL CASE with a dynamic ANY or STRING input key. The input adapter
provides a separate canonical lookup-key projection: null stays null; other
values use String.valueOf after decoding the original transported value, exactly
as the fallback runtime sees it. This is input conversion, not context lookup or
FEEL expression evaluation. The SQL query performs the lookup. Original payload
bytes remain intact. Concatenation retains its separate string-only projection,
so numeric, boolean and structured keys do not become concatenation strings.

Bounds: at most 64 entries, unique nonempty names of at most 256 characters,
string values at most 2,048 characters, valid UTF-16, direct input key references
matching declared ANY/STRING types, terminal ANY/STRING result. No boxed result
entry, functions, dynamic context values, duplicate keys or duplicate/unknown
named arguments are admitted. Positional calls and exact m/key named bindings
are supported. Both CTE query forms carry collision-safe key projection columns.

## Verification

- Focused regression passed: one test, 33 native actions (50%), 33 deliberate
  fallback actions (50%), denominator 66; failed/unknown zero. The fallback
  queries execute AllDecisions with an opaque identity result. The 33 standalone
  lookup queries are native. Tests cover positional/named calls, case-sensitive
  keys, missing keys, null values, decimal scale, booleans, list/map keys,
  projection-name collisions, concatenation isolation and exact timestamp
  payload bytes. Generator target log: `native-lookup-key-focused-2026-10-19.log`.
- Complete generator regression/install passed: 117/117 JUnit tests. Combined:
  1,569 native actions (94.69%), 88 fallback (5.31%), denominator 1,657;
  failed/unknown zero. Hybrid integration: 1,559 native (94.83%), 85 fallback
  (5.17%) of 1,644; DMN integration: 10 native (76.92%), 3 fallback (23.08%)
  of 13. Formatting/install/Maven exit zero. Target/installed JAR SHA256 agree:
  `AF01B74BA82AD4FAA2338DD948D46A854408853A80B5CC3D9DFE3BDC1AC0D37B`.
  Generator target log: `native-lookup-key-generator-2026-10-19.log`.
- Extended focused regression passed with quoted/backslash-containing key:
  36 native actions (50%), 36 deliberate fallback actions (50%), denominator 72;
  failed/unknown zero, Maven exit zero. Generator target log:
  `native-lookup-key-escaping-2026-10-19.log`. Only test data changed after the
  complete generator run; production source and installed artifact are unchanged.
- Official get-value TCK passed 14/14: 14 native (100%), zero fallback (0%),
  denominator 14; failed/unknown zero, expected model rejections zero, Maven
  exit zero. Exact baseline comparison shows only `decision_013` and
  `decision_014` changed fallback to native, with no reversals or rejection
  reclassifications within the suite. Runner target evidence:
  `native-lookup-key-accounting-2026-10-19.json` and
  `native-lookup-key-tck-2026-10-19.log`.
- Combined targeted estimate including date-loop and literal-calendar work:
  2,679 native (98.67%), 36 fallback (1.33%) of 2,715 executable baseline cases.
  This is not a new full-run result. Last full remains 2,674 native / 41 fallback;
  676 provisional expected model rejections separately. Five forward conversions
  have been verified since that full checkpoint. Changes remain uncommitted.

At this batch checkpoint primary usage remaining is 39%, above the 20% full-run
trigger. Full cadence remains 45 minutes work, usage below 20%, or twenty verified
conversions. Do not present targeted extrapolations as a new full result.
