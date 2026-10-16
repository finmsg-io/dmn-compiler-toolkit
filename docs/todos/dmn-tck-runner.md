2026-10-16 LATEST FULL list/coercion checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,628 native (96.80%), 87 fallback (3.20%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections.
Verified twenty fallback-to-native conversions and zero reverse conversions
against native-context-statistics-full-accounting-2026-10-16.json: ten list
functions, nine coercion cases and one empty-key context-put recovery.
Fallback decreased from 107 to 87 (18.69% fewer fallback cases).
Full generator: 100/100 passed; 1,141 native SQL actions (97.44%), 30 fallback
(2.56%) of 1,171, zero failed/unknown; formatting/install passed.
Target and installed JAR hash: E02EE9F946757AF1A4533FEA7DD3B0ECBE257C7CF3619C4C3DB1F8FAFA94CF6E.
Saved full runner baseline: native-list-coercion-full-accounting-2026-10-16.json;
log: native-list-coercion-full-tck-2026-10-16.log. This full run was explicitly
requested and reaches the twenty-conversion checkpoint. Details:
[handover](../dev/slices/native-list-typing-coercion-2026-10-16.md).
Remaining 87 fallbacks and the semantic rejection audit stay open. Largest
groups: external Java 18, chapter 11 example 8, decision services 5, listGen 5.
Next candidates include bounded flatten lowering (five listGen plus one 0012).
Retain temporal precision/offset/duration and list/quantifier/binding priorities.
Restart cadence here: 45 minutes work, usage remaining below 20%, or twenty
further conversions, whichever comes first.

2026-10-16 continuation after pushed commit 1d0e780: restored empty-key
context-put native processing. Official context-put: 30/30 passed, 30 native
(100%), zero fallback/failed/unknown, denominator 30. Exactly one verified
conversion (004), zero reverse conversions against the full checkpoint below.
Focused generator: two tests passed; 13 native actions (81.25%), three deliberate
precision-boundary fallbacks (18.75%) of 16, zero failed/unknown. Full generator:
97/97 passed; 996 native actions (96.79%), 33 fallback (3.21%) of 1,029, zero
failed/unknown; formatting/install passed. Full-suite rates below remain the
latest global measurement. This recovery is included in the list/coercion checkpoint above.
Details: docs/dev/slices/native-context-statistics-2026-10-16.md.

2026-10-16 earlier FULL context/statistics checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,608 native (96.06%), 107 fallback (3.94%);
zero failures, unknown routes or compilation/execution errors. Separately:
676 provisional expected model rejections. Compared with the bounded-loop full
baseline: 13 fallback-to-native conversions and one native-to-fallback change
(1146 context-put case 004, still passing), net 12 fewer fallbacks.
Final generator: 97/97 tests passed; 994 native SQL actions (96.79%),
33 fallback (3.21%) of 1,027, zero failed/unknown routes. Formatting/install passed.
The full checkpoint was triggered by 5% usage remaining. Saved runner evidence:
`native-context-statistics-full-accounting-2026-10-16.json` and
`native-context-statistics-full-tck-2026-10-16.log`. Details:
[context/statistics handover](../dev/slices/native-context-statistics-2026-10-16.md).
Next: investigate the context-put 004 reverse route and reduce the remaining
107 fallbacks. The 100% native goal and semantic rejection audit remain open.
Restart full-run cadence here: 45 minutes working, usage remaining below 20%,
or 20 further fallback conversions, whichever comes first.

2026-10-15 earlier FULL bounded-loop checkpoint: 3,391/3,391 passed; 2,596 native
(95.62%), 119 fallback (4.38%) of 2,715 executable cases, zero failed/unknown or
compilation/execution errors. Separately: 676 provisional expected model
rejections. Twelve new conversions, zero reverse conversions. Generator: 91/91
tests; 958 native actions (96.96%), 30 fallback (3.04%) of 988, zero failed/unknown.
The full run was triggered by 18% usage remaining. Saved full baseline:
native-bounded-loop-full-accounting-2026-10-15.json. Details:
docs/dev/slices/native-bounded-loops-2026-10-15.md. The 119 fallbacks and semantic
rejection audit remain open; no further feature changes after this checkpoint.

2026-10-15 earlier FULL: 3,391/3,391 passed; native 2,584 (95.17%), fallback 131
(4.83%) of 2,715 executable cases, zero failed/unknown or compilation/execution
errors. Separately: 676 provisional expected model rejections. The combined
get-entries/list-builtin batch verifies 21 conversions and zero reverse changes.
0035 #001 also passed with the unchanged 30-second limit; no warmup fix is proven.
Generator: 90/90 tests, 945 native actions (96.92%), 30 fallback (3.08%) of 975,
zero failed/unknown. Save native-list-builtin-full-accounting-2026-10-15.json as
the full baseline. Restart the user's full-run cadence from this checkpoint.
The 131 fallbacks and semantic rejection audit remain open. See
docs/dev/slices/native-literal-list-builtins-2026-10-15.md.

2026-10-15 continuation: literal get entries moved seven official cases from
fallback to native. Focused suite: 9/9 native (100%), zero fallback or failures,
denominator nine. Full generator: 89/89 tests passed; 867 native SQL actions
(96.66%), 30 fallback (3.34%) of 897, zero failed/unknown. Global rates await the
next full checkpoint under the user's cadence. See
docs/dev/slices/native-get-entries-2026-10-15.md for saved evidence. The 0035 #001
timeout and provisional rejection audit remain open.

2026-10-15 full Spark SQL rerun: 3,390/3,391 passed (99.97%); one 30-second
timeout in 0035-test-structure-output case 001. Of 2,715 attempted executable
cases: 2,562 native passes (94.36%), 152 fallback passes (5.60%), one timeout
(0.04%). Separately: 676 provisional expected model rejections. No result
mismatches or compilation/execution errors. Full Maven exit 1 due to timeout.
The identical installed generator previously passed 3,391/3,391: 2,563 native
(94.40%), 152 fallback (5.60%). The batch verifies 25 fallback-to-native
conversions, zero native-to-fallback changes; the rerun's native-pass delta is
24 because a previously native case timed out. The focused 0035 suite repeated
the same timeout: two native passes (66.67%), zero fallback, one timeout
(33.33%), denominator three; Maven exit 1. This does not replace the full result.
Warmup remains unverified; no timeout or generator source changed for this rerun.
Retry evidence: native-type-rounding-timeout-retry-accounting-2026-10-15.json.
Full evidence: runner target/native-type-rounding-full-accounting-2026-10-15.json
and native-type-rounding-full-tck-2026-10-15.log. Previous green full evidence:
native-type-rounding-first-full-accounting.json and native-type-rounding-full-tck.log.
Generator: 88/88 tests passed, 843 native actions (96.56%), 30 fallback (3.44%),
denominator 873, zero failed/unknown routes. Details:
docs/dev/slices/native-type-rounding-proofs-2026-10-14.md. The 100% goal remains active.

2026-10-14 corrected full Spark SQL TCK: 3,391/3,391 passed. Native 2,538
(93.48%), fallback 177 (6.52%) of 2,715 executable cases; 676 provisional
expected model rejections separately. Zero failed/unknown routes or compilation/
execution errors. Compared with native-any-calendar-full-accounting.json: 18
fallback-to-native conversions (15 lambda, two coercion, one arithmetic negation),
zero reverse conversions. The implicit Java-descriptor regression is fixed and
0076 boxed_001 passes via fallback. Full generator: 86/86 tests; 781 native SQL
actions (96.90%), 25 fallback (3.10%), denominator 806, zero failed/unknown.
Evidence: runner target/native-static-functions-corrected-full-tck.log and
native-static-functions-corrected-full-accounting.json; generator
target/native-static-functions-corrected-generator.log. Details:
docs/dev/slices/native-static-functions-2026-10-13.md. The 100% goal remains active.

2026-10-13 latest full verification: 3,391/3,391 passed. Native 2,520 (92.82%)
and fallback 195 (7.18%) of 2,715 executable cases; 676 provisional expected
model rejections separately, zero failures or compilation/execution errors.
Eight actual fallback-to-native conversions versus priority-native-full-accounting.json,
zero reverse conversions. Full evidence: runner target/native-any-calendar-full-tck.log
and native-any-calendar-full-accounting.json. Generator verification: 80/80 tests;
764 native actions (97.70%), 18 fallback (2.30%), denominator 782, zero failed/unknown.
See docs/dev/slices/native-calendar-inputs-2026-10-13.md for the input schema contract
and correction of optimization-time fallback accounting. The 100% goal remains active.

# `dmn-tck-runner` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Next planned work](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-09-06

The `dmn-tck-runner` module executes the official OMG DMN Technology Compatibility Kit (TCK) against both the `DmnRuntime` interpreter and `dmn-generator-java` code generator with strict conformance assertions and machine-readable accounting.

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P6.1 | Official OMG DMN 1.5 TCK runner harness | `done` | `OfficialTckSuiteTest` |
| P6.2 | Hard assertions without skipped tests | `done` | Zero `assumeTrue` skips; 82/82 test files execute with strict assertions |
| P6.3 | 100% strict CL2/CL3 conformance | `done` | 3,391/3,391 declared test cases pass across both backends (6,782/6,782 backend outcomes) |
| P6.4 | Machine-readable canonical accounting | `done` | `tck-accounting.json` generated deterministically |
| P6.5 | Standalone HTML dashboard generator | `done` | `tools/generate_tck_dashboard.py` (`docs/tck-dashboard.html`) |
| P6.6 | TCK accounting diff & regression comparator | `done` | `tools/diff_tck_accounting.py` |

<a id="contents-section-2"></a>
## Next planned work

| Priority | Work item | Status | Target / Evidence |
| --- | --- | --- | --- |
| P19.1 | Automated TCK regression ratchet in CI | `ready` | Fails build if test case count drops below 3,391 or any failure occurs |
| P19.2 | Diagnose Spark SQL first-use timeout and evaluate representative warmup | `planned` | 2026-10-11 full run: `0035-test-structure-output-test-01.xml#001` exceeded 30 seconds; case 002 passed. Existing warmup is `SELECT 1`. |

P19.2 plan: reproduce case 001 in a fresh JVM and repeat it in the same Spark
session. Record separate timings for model preparation, SQL generation, UDF
registration, query planning and collection, and capture the active stack on
timeout. Compare the existing `SELECT 1` warmup with a representative structured
output/UDF query only if measurements identify first-use overhead. Keep the
30-second case limit and conformance assertions unchanged. Accept a warmup change
only after repeated fresh-JVM runs and a full suite show the timeout resolved
without changing routing or hiding a persistent slow case. Report actual native
and fallback counts and rates after every run; preserve routing for failed cases
so an unresolved timeout is explicitly accounted for.

Latest run evidence: `dmn-tck-runner/target/current-native-fallback-full-approved.log`
and `dmn-tck-runner/target/current-native-fallback-full-accounting.json`.
3,390/3,391 passed; one timeout; zero execution errors. Of 2,715 executable cases,
2,475 native passes (91.16%), 239 fallback passes (8.80%), and one failed case
with unrecorded route (0.04%). The 676 provisional expected model rejections are
separate. These are pass-route rates over all executable cases; the failed case's
route cannot be inferred from the report.

2026-10-11 latest checkpoint: the same 0035 case 001 timed out again in
native-temporal-context-full.log with the existing SELECT 1 warmup and unchanged
30-second limit. This makes the timeout reproducible across two full runs; stage
timings are still needed before attributing it to first-use overhead or accepting
a representative warmup. Latest full accounting: 3,390/3,391 passed, 2,501 native
(92.12%), 213 fallback (7.85%), one failed route unrecorded (0.04%) of 2,715
executable; 676 provisional expected rejections separately, no execution or
compilation errors. Maven exit 1. Evidence:
dmn-tck-runner/target/native-temporal-context-full-accounting.json.

2026-10-12 full checkpoint: all 3,391 cases passed with the unchanged SELECT 1 warmup and 30-second limit. Case 0035#001 passed natively. P19.2 remains planned: this one successful full run does not identify the earlier timeout cause or prove a warmup fix. Latest evidence is target/priority-native-full-tck.log and priority-native-full-accounting.json: 2,512 native (92.52%), 203 fallback (7.48%) of 2,715 executable, 676 provisional model rejections separately, zero failures or execution/compilation errors, Maven exit 0. User goal is now 100% native and zero fallback; the semantic rejection audit remains separate and outstanding.
