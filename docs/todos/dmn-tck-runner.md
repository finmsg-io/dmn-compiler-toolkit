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
