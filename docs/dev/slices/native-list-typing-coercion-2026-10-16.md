# Native list typing and literal coercion

Continuation after the context/statistics checkpoint and empty-key recovery.
The previous batch is pushed as `1d0e780`. This checkpoint includes the
empty-key recovery, homogeneous list typing and shared literal coercion.

## Changes

Homogeneous positional list builtins now retain verified result element types
through static lowering. Supported operations are reverse, distinct values,
concatenate, two-list union, scalar append, sublist, remove and insert before.
Only primitive string, boolean and number element layouts are admitted. Position
arguments must be exact literal integers within +/-1,000,000; sublist excludes
zero starts and negative lengths. Named, heterogeneous and unverified forms keep
their existing route.

Native SQL explicitly handles negative and out-of-range positions. List result
validation binds the result once through a singleton-array transform, avoiding
duplicating the entire result SQL in its null-element guard. Generic list/context
declarations remain compatible with verified native layouts; incompatible
result declarations retain coercion checks.

Bounded closed constants, lists and contexts use shared DmnRuntime declared-type
coercion at generation time. The proof retains 2,048 nodes, depth 16 and bounded
text; local slots are sized and capped. Invalid non-null function arguments
short-circuit to null before evaluating the function body, matching shared
binding. Decimal precision and heterogeneous array representation guards remain.

The official runner has opt-in `tck.spark.routeDiagnostics` output for fallback
capability reasons; default output and routing are unchanged.

## Verification so far

- Diagnostic official list/coercion baseline: 55/55 passed, 25 native (55.56%),
  20 fallback (44.44%) of 45 executable cases; ten provisional expected model
  rejections separately, zero failed/unknown. Runner target
  `native-list-coercion-diagnostics.log`.
- Dynamic list position parity: one test passed, 114 native actions (100%),
  zero fallback/failed/unknown, denominator 114. Generator target
  `native-typed-list-focused.log`.
- Official list typing: 55/55 passed, 35 native (77.78%), ten fallback (22.22%)
  of 45 executable cases; ten provisional expected model rejections separately,
  zero failed/unknown. Exactly ten forward conversions (0012 cases 008, 009,
  010, 011, 012, 013, 014, 015, 017, 018), zero reverse conversions against the
  saved full checkpoint. Runner target `native-typed-list-official.log` and
  `native-typed-list-accounting-2026-10-16.json`.
- Focused lists/coercion/decimal guards: three tests passed; 130 native actions
  (97.74%), three deliberate precision-boundary fallbacks (2.26%) of 133,
  zero failed/unknown. Generator target `native-list-coercion-focused.log`.
- First full regression failed five of 99 JUnit tests. Executed SQL actions:
  1,090 native (97.50%), 28 fallback (2.50%) of 1,118, zero failed/unknown SQL
  routes. Four context route assertions exposed generic-result layout handling;
  one guard assertion expected an invalid typed call to remain unlowered. Fixed
  layout handling and changed that assertion to permit only a proven null
  literal while retaining runtime parity. This failed run is not a checkpoint.
  Generator target `native-list-coercion-full-generator.log`.
- Repaired full generator: 99/99 passed; 1,128 native actions (97.33%),
  31 fallback (2.67%) of 1,159, zero failed/unknown. Formatting/install passed.
  Generator target `native-list-coercion-repaired-generator.log`.
- Additional empty context and local-slot coercion parity: two tests passed;
  28 native actions (100%), zero fallback/failed/unknown, denominator 28.
  Generator target `native-list-coercion-empty-layout.log`.

Intermediate generator passed 99/99 tests: 1,132 native SQL actions (97.33%),
31 fallback (2.67%) of 1,163, zero failed/unknown. Formatting/install passed,
Maven exit 0. Generator target `native-list-coercion-verified-generator.log`.
The first broader official check passed 194/194 cases: 156 native (84.78%),
28 fallback (15.22%) of 184 executable cases; ten provisional expected model
rejections separately, zero failed/unknown. Runner target
`native-list-coercion-broad-tck.log` and
`native-list-coercion-broad-accounting-2026-10-16.json`.
Comparison found nineteen forward conversions and one reverse conversion
(0082 decision_004), net eighteen. That reverse route and remaining
0082 decision_context_02 reject valid extra context fields. Structural context
compatibility now validates declared field names and types while retaining
extra fields; wrong-kind and missing required fields use shared literal
coercion to null. The completed full checkpoint below verifies this correction. The first broader
TCK launch failed before any tests
because a second Maven profile argument replaced the TCK profile; denominator
zero, no native/fallback rate. The corrected launch enables `tck,format` together. The previous full baseline was 3,391/3,391 passed, 2,608 native
(96.06%) and 107 fallback (3.94%) of 2,715 executable cases, with 676
provisional expected model rejections separately. The 100% native goal and
rejection semantic audit remain open. Follow the user's full-run cadence:
45 minutes of work, usage remaining below 20%, or twenty conversions.


## Structural context verification

The first structural-context full generator run executed 1,140 native actions
(97.44%) and 30 fallback (2.56%) of 1,170, with zero failed/unknown SQL routes.
One of 100 JUnit tests failed: the existing dynamic-key/collision fixture also
contained a constrained context update whose extra field is valid under shared
context coercion. That case now explicitly requires native processing; dynamic
keys and case-fold collisions still explicitly require fallback. The same test
continues to execute SQL and compare with shared runtime values.
Evidence: generator target `native-list-coercion-structural-generator.log`.

The user explicitly requested a full TCK followed by commit and push. Final
generator verification, full checkpoint and handover updates precede that commit.
No additional feature work is planned before completing the requested checkpoint.


Final combined generator: 100/100 tests passed; 1,141 native SQL actions
(97.44%), 30 fallback (2.56%) of 1,171; zero failed/unknown routes.
Formatting/install passed, Maven exit 0. Generator target
`native-list-coercion-final-generator.log`. Target and installed JAR SHA-256
match: `E02EE9F946757AF1A4533FEA7DD3B0ECBE257C7CF3619C4C3DB1F8FAFA94CF6E`.
The explicitly requested full TCK passed on that build as recorded below. No
further source changes followed this checkpoint.


## Completed full checkpoint

Full generated Spark SQL hybrid TCK: **3,391/3,391 passed**. Of 2,715 executable
cases, **2,628 native (96.80%)** and **87 fallback (3.20%)**. Zero failures,
unknown routes, compilation errors, execution errors, unsupported, excluded or
invalid cases. The **676 provisional expected model rejections** remain separate.
Maven exit 0. The semantic rejection audit and 100% native goal remain open.

Saved runner target evidence:
`native-list-coercion-full-tck-2026-10-16.log` and
`native-list-coercion-full-accounting-2026-10-16.json`.
This accounting file is the new full comparison baseline.

Comparison with `native-context-statistics-full-accounting-2026-10-16.json`
verifies **twenty fallback-to-native conversions and zero reverse conversions**:

- 0012 list functions: 008, 009, 010, 011, 012, 013, 014, 015, 017, 018.
- 0082 coercion: decisionService_001, decision_003, decision_bkm_005_a,
  decision_context_02, decision_context_03, fd_002, invoke_004, invoke_005,
  invoke_006.
- 1146 context put: 004 (empty-key route recovery).

The earlier intermediate reverse route, 0082 decision_004, is native again.
Fallback fell from 107 (3.94%) to 87 (3.20%), a reduction of twenty cases
(18.69% of the previous fallback population). Generator SQL-action rates and
full TCK case rates are separate measurements.

Largest remaining full-suite fallback groups: external Java (18), chapter 11
example (8), decision services (5), listGen (5), conditionals (3), decision-table
variable input (3), some/every (3), and for loops (3). The five listGen cases and
remaining 0012 flatten case are candidates for bounded flatten lowering;
heterogeneous arrays must retain lossless behavior. Continue the user's temporal
precision/offset/duration, list/quantifier validation and lexical-binding priorities.

Documentation facts, strict MkDocs build and diff whitespace checks passed.
Restart full-run cadence from this checkpoint: 45 minutes working, usage
remaining below 20%, or twenty further conversions, whichever comes first.
