# Native flatten, bounded quantifiers and literal JDK calls

Continuation from pushed commit `0ced747`. Previous full TCK baseline:
3,391/3,391 passed; 2,628 native (96.80%), 87 fallback (3.20%) of 2,715
executable cases, with 676 provisional expected model rejections separately.
The 100% native goal and semantic rejection audit remain open.

## Changes

Flatten lowering collects homogeneous primitive leaves into typed array
segments instead of constructing heterogeneous Spark arrays. It preserves
order and duplicates and drops null leaves, including null dynamic lists.
The proof is limited to 128 syntax nodes and depth 16; different non-null
leaf kinds and unrepresentable combined decimal widths retain their route.

Closed-list SOME/EVERY lowering admits at most four bindings, 64 items per
binding and 128 expanded predicates within the existing shared lowering budget.
Closed literal decision references retain shared declared-type coercion.
Null predicates count as false; empty SOME is false and empty EVERY is true.
Lexical substitution uses isolated binding maps, and boolean combinations form
balanced trees. Dynamic or unverified sources keep the established route.

Literal Java folding admits only explicit trusted JDK classes and pure static
methods with closed descriptors and constant arguments. Class and parameter
lookup use fixed class maps; no untrusted class is loaded. A missing method on
a known class yields the shared runtime's null result. Descriptor limits are
32 nodes, depth four and 256-character strings; calls allow at most sixteen
primitive constant arguments with bounded strings and decimal precision/scale.
Results must have a native exact representation; strings are capped at 32 KiB
and unpaired UTF-16 surrogates retain fallback.
String formatting admits literal text, %% and %s only. Dynamic arguments,
nondeterministic methods, locale-sensitive formatting and oversized numeric
results retain fallback. Original positional/named ordering and duplicate
binding evaluation use shared runtime semantics. Accepted methods are evaluated
once at generation time; query execution uses SQL literals without Java UDFs.

An attempted broader function-library promotion exposed a real DECIMAL128
mismatch for 2/3. That promotion was removed. The final code retains lossless
fallback for those dynamic FEEL arithmetic libraries, with parity guards for
2/3 and 0.1 + 0.2.

## Verification history

- Flatten focused parity: one test passed, 24 native actions (92.31%), two
  deliberate fallback actions (7.69%) of 26; zero failed/unknown routes.
  Generator target `native-flatten-focused-2026-10-18.log`.
- Official list suites: 29/29 passed; 28 native (96.55%), one fallback (3.45%)
  of 29 executable cases, no expected model rejections, zero failed/unknown.
  Compared with the full baseline, listGen 006, 007, 008, 009 and 010 converted
  from fallback to native; zero reverse conversions. Runner target
  `native-flatten-official-2026-10-18.log` and
  `native-flatten-accounting-2026-10-18.json`.
- Experimental library promotion: one of two tests failed exact 2/3 parity.
  Executed SQL actions were 25 native (92.59%), two fallback (7.41%) of 27,
  zero failed/unknown SQL routes. The promotion is absent from the final change.
  Generator target `native-flatten-library-focused-2026-10-18.log`.
- Initial quantifier fixture: one of three tests failed because the compiler
  rejected a null-only non-boolean predicate. Executed actions: 35 native
  (74.47%), twelve fallback (25.53%) of 47, zero failed/unknown SQL routes.
  Replaced the invalid fixture with an explicit boolean null comparison.
  Generator target `native-flatten-quantifier-focused-2026-10-18.log`.
- Corrected focused set: three tests passed, 39 native actions (76.47%), twelve
  deliberate fallback actions (23.53%) of 51, zero failed/unknown.
  Generator target `native-flatten-quantifier-verified-2026-10-18.log`.
- Initial pure-JDK/quantifier parity: two tests passed, 30 native actions
  (93.75%), two deliberate fallback actions (6.25%) of 32, zero failed/unknown.
  Generator target `native-java-quantifier-focused-2026-10-18.log`.

- Both full generator runs passed all 104 tests: 1,198 native SQL actions
  (96.30%), 46 deliberate fallback actions (3.70%) of 1,244; zero failed/unknown.
  The final run includes the unpaired-surrogate guard. Generator target logs:
  `native-flatten-quantifier-java-generator-2026-10-18.log` and
  `native-flatten-quantifier-java-final-generator-2026-10-18.log`.

## Full official checkpoint

2026-10-18 LATEST FULL flatten/quantifier/literal-JDK checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,651 native (97.64%), 64 fallback (2.36%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections.
Compared with the 2026-10-16 list/coercion full baseline: 23 fallback-to-native
conversions (five flatten, two quantifier, sixteen literal JDK), zero reverse
conversions. Fallback decreased from 87 to 64 (26.44% fewer fallback cases).
Final generator regression: 104/104 passed; 1,198 native SQL actions (96.30%),
46 deliberate fallback actions (3.70%) of 1,244; zero failed/unknown routes.
Formatting/install passed. Target and installed generator JAR SHA-256:
82FCF77DE4C709733C75AEDA03246EB4771621BA6DBFD80147209A8D0965A3A3.
Saved runner target evidence: native-flatten-quantifier-java-full-accounting-2026-10-18.json
and native-flatten-quantifier-java-full-tck-2026-10-18.log.
Largest remaining groups: chapter 11 example eight, decision services five;
external Java now two. Dynamic function-library arithmetic retains fallback
because Spark double division cannot preserve shared DECIMAL128 semantics.
The 100% native goal and semantic rejection audit remain open. Preserve temporal
precision/offset/duration identity, list validation, and lexical binding priorities.
Restart full-run cadence here: 45 minutes work, usage remaining below 20%, or
twenty further verified fallback conversions, whichever comes first.

### Verified route changes

- `compliance-level-3/0008-listGen/0008-listGen-test-01.xml#006`: fallback to native.
- `compliance-level-3/0008-listGen/0008-listGen-test-01.xml#007`: fallback to native.
- `compliance-level-3/0008-listGen/0008-listGen-test-01.xml#008`: fallback to native.
- `compliance-level-3/0008-listGen/0008-listGen-test-01.xml#009`: fallback to native.
- `compliance-level-3/0008-listGen/0008-listGen-test-01.xml#010`: fallback to native.
- `compliance-level-3/0016-some-every/0016-some-every-test-01.xml#decision_014`: fallback to native.
- `compliance-level-3/0016-some-every/0016-some-every-test-01.xml#decision_014a`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#incorrect_001`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#incorrect_003`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_001`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_002`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_003`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_004`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_005`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_006`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_007`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_007_a`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_008`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_009`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_010`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_011`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#literal_012`: fallback to native.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml#varargs_001`: fallback to native.

### Remaining fallback inventory

- `compliance-level-3/0087-chapter-11-example/0087-chapter-11-example-test-01.xml`: 8.
- `compliance-level-3/0085-decision-services/0085-decision-services-test-01.xml`: 5.
- `compliance-level-3/0031-user-defined-functions/0031-user-defined-functions-test-01.xml`: 3.
- `compliance-level-3/0032-conditionals/0032-conditionals-test-01.xml`: 3.
- `compliance-level-3/0036-dt-variable-input/0036-dt-variable-input-test-01.xml`: 3.
- `compliance-level-3/0084-feel-for-loops/0084-feel-for-loops-test-01.xml`: 3.
- `compliance-level-3/0017-tableTests/0017-tableTests-test-01.xml`: 2.
- `compliance-level-3/0033-for-loops/0033-for-loops-test-01.xml`: 2.
- `compliance-level-3/0069-feel-list/0069-feel-list-test-01.xml`: 2.
- `compliance-level-3/0072-feel-in/0072-feel-in-test-01.xml`: 2.
- `compliance-level-3/0076-feel-external-java/0076-feel-external-java-test-01.xml`: 2.
- `compliance-level-3/0080-feel-getvalue-function/0080-feel-getvalue-function-test-01.xml`: 2.
- `compliance-level-3/0090-feel-paths/0090-feel-paths-test-01.xml`: 2.
- `compliance-level-3/0092-feel-lambda/0092-feel-lambda-test-01.xml`: 2.
- `compliance-level-3/0096-feel-day-of-week-function/0096-feel-day-of-week-function-test-01.xml`: 2.
- `compliance-level-3/0097-feel-month-of-year-function/0097-feel-month-of-year-function-test-01.xml`: 2.
- `compliance-level-3/1156-range-function/1156-range-function-test-01.xml`: 2.
- `compliance-level-3/0001-filter/0001-filter-test-01.xml`: 1.
- `compliance-level-3/0003-iteration/0003-iteration-test-01.xml`: 1.
- `compliance-level-3/0004-lending/0004-lending-test-01.xml`: 1.
- `compliance-level-3/0006-join/0006-join-test-01.xml`: 1.
- `compliance-level-3/0007-date-time/0007-date-time-test-01.xml`: 1.
- `compliance-level-3/0009-append-flatten/0009-append-flatten-test-01.xml`: 1.
- `compliance-level-3/0010-concatenate/0010-concatenate-test-01.xml`: 1.
- `compliance-level-3/0011-insert-remove/0011-insert-remove-test-01.xml`: 1.
- `compliance-level-3/0012-list-functions/0012-list-functions-test-01.xml`: 1.
- `compliance-level-3/0013-sort/0013-sort-test-01.xml`: 1.
- `compliance-level-3/0014-loan-comparison/0014-loan-comparison-test-01.xml`: 1.
- `compliance-level-3/0016-some-every/0016-some-every-test-01.xml`: 1.
- `compliance-level-3/0021-singleton-list/0021-singleton-list-test-01.xml`: 1.
- `compliance-level-3/0030-user-defined-functions/0030-user-defined-functions-test-01.xml`: 1.
- `compliance-level-3/0088-no-decision-logic/0088-no-decision-logic-test-01.xml`: 1.
- `compliance-level-3/0098-feel-week-of-year-function/0098-feel-week-of-year-function-test-01.xml`: 1.
- `compliance-level-3/1145-feel-context-function/1145-feel-context-function-test-01.xml`: 1.

## Checkpoint checks and runner shutdown

Maven completed the full TCK with exit code zero. After the complete passing
summary, Surefire reported that its fork JVM required termination 30 seconds
after System.exit(0). This is a post-suite shutdown issue, not a timed-out TCK
case; preserve it as a follow-up to investigate Spark/fork cleanup.
Documentation facts verification, strict MkDocs build and Git whitespace checks
passed. Evidence logs are generated target artifacts and are not committed.
