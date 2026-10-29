# Shared result typing for nested string and Boolean lists

Continues from uncommitted date-loop, literal-calendar and dynamic-lookup work.
Last full Spark SQL TCK: 3,391/3,391 passed; 2,674 native (98.49%), 41 fallback
(1.51%) of 2,715 executable cases, plus 676 provisional expected model rejections.
Subsequent targeted checks verified five conversions, estimating 36 fallbacks.

Shared list builtin result typing now admits homogeneous two-level string or
Boolean lists for append, concatenate and flatten, preserving inner list shape.
The established typed dispatcher already wraps appended list items correctly and
coalesces concatenate's null sources. It now handles flatten directly, converting
null sources/inner lists to empty lists and discarding null leaves. Nested append
and concatenate retain shared runtime rejection of null outer entries with a
native SQL error. Mixed kinds, deeper nesting and nested numeric lists retain
fallback. This does not relax numeric precision bounds or use observed TCK inputs
for routing.

## Verification history

- Initial focused attempt stopped at Java compilation after an incorrect return
  in a switch expression: zero SQL actions, rates undefined; corrected to yield.
- Next focused attempt executed 10 native actions (100%), zero fallback (0%),
  denominator 10; failed/unknown SQL routes zero. One generation error occurred
  when flatten reached a dispatcher expecting positional arguments. Extended the
  shared typed dispatcher rather than adding a parallel emitter path.
- Corrected focused regression passed: 15 native actions (100%), zero fallback
  (0%), denominator 15; failed/unknown zero. Covers append item shape, concatenate,
  flatten, null sources, empty lists/inner lists and null leaves.
  Logs under generator target: `native-nested-text-focused-2026-10-19.log`,
  `native-nested-text-focused-fixed-2026-10-19.log`, and
  `native-nested-text-focused-final-2026-10-19.log`.
- Complete generator regression/install passed: 118/118 JUnit tests. Combined
  1,587 native actions (94.58%), 91 fallback (5.42%), denominator 1,678;
  failed/unknown zero. Hybrid integration: 1,577 native (94.71%), 88 fallback
  (5.29%) of 1,665. DMN integration: 10 native (76.92%), 3 fallback (23.08%)
  of 13. Formatting/install/Maven exit zero. Target log:
  `native-nested-text-generator-2026-10-20.log`.
- Target and installed generator JAR SHA256 match:
  `9E474569B86359ED932EB77E5ABD49E8577A4A7154C0BE53A2B257B6E861C0CD`.
- Official append/flatten and concatenate suites passed: 2/2 native (100%),
  zero fallback (0%), denominator two, failed/unknown zero; no expected model
  rejections. Exactly two forward conversions compared with the saved full
  baseline: `0009-append-flatten#001` and `0010-concatenate#001`, no reversals or
  rejection changes within the tested suites. Maven exited zero. Runner target
  evidence: `native-nested-text-accounting-2026-10-20.json` and
  `native-nested-text-tck-2026-10-20.log`.
- Combined targeted estimate with prior date-loop/calendar/lookup work:
  2,681 native (98.75%), 34 fallback (1.25%) of 2,715 executable baseline cases.
  Seven conversions since the last full checkpoint. This is not a new full
  result: last full remains 2,674 native / 41 fallback, with 676 provisional
  expected model rejections separately. Changes remain uncommitted.

## Verified full TCK checkpoint

2026-10-20 LATEST FULL generated Spark SQL checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,681 native (98.75%), 34 fallback (1.25%);
failed/unknown routes zero. Compilation/execution errors, unsupported, excluded
and invalid counts all zero. Separately: 676 provisional expected model rejections,
unchanged. Exact comparison with the previous 41-fallback full checkpoint confirms
seven fallback-to-native conversions, zero reversals and no rejection reclassifications.
Fallback decreased 41 to 34 (17.07% fewer fallback cases). The previous targeted
estimate is now verified by a complete full run. Converted: 0009#001, 0010#001,
0080#decision_013/014, 0084#decision_017/018, and 0098#date_008.
Target/installed generator JAR SHA256:
9E474569B86359ED932EB77E5ABD49E8577A4A7154C0BE53A2B257B6E861C0CD.
Saved runner target evidence: native-nested-text-full-accounting-2026-10-20.json
and native-nested-text-full-tck-2026-10-20.log. Maven exited zero. No source changes
were made during this run. Generator evidence remains 118/118 tests passed:
1,587 native SQL actions (94.58%), 91 fallback (5.42%) of 1,678; failed/unknown zero.
Generator SQL action rates and executable TCK case rates have different denominators.
Changes remain uncommitted. The 100% native goal and semantic rejection audit
remain open. Restart full-run cadence here: 45 minutes work, usage below 20%,
or twenty verified conversions. Preserve exact arithmetic, temporal identity,
lexical binding and shared declared-result coercion in the remaining work.
