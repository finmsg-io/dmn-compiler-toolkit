# DMN Spark SQL TCK Status and Handover Guide

Active check-in repair continuation: 439-case affected-suite TCK verified at **439 total; 439 passed (100.00%); 232 native; 195 fallback; 12 provisional expected rejections; 0 failed; 0 errors**. Scoped generator tests in `dmn-generator-sparksql`: **30 passed, 0 failures, 0 errors, 0 skips**; Spotless formatted cleanly. Enforced declared BKM and invocation return types via `declaredBkmReturnType`, parameter/body conformance in `inlineBkm`, and invocation typeRef checking in `emitInvocation`. This resolved both `0082#decision_bkm_004_a` and `0082#invoke_002` (suite `0082-feel-coercion` is now 36/36 passed, 14 native, 12 fallback, 10 expected rejections, 0 failures/errors). Next: run the full optimized hybrid Spark SQL TCK (3,391 cases) with 4 GB heap and history limits 20/20/40. Preserve all edits/deletions. Prior metrics below are historical.

Prior affected-suite rerun: **439 total; 437 passed; 228 native; 197 fallback; 12 provisional expected rejections; 2 failed; 0 errors**. Remaining blockers were `0082#decision_bkm_004_a` and `0082#invoke_002` (BKM declared return-type enforcement), now completely resolved above.

Check-in preparation usage stop: **18% five-hour / 67% weekly remaining** triggered the user-required implementation stop. Working-tree changes are preserved; **not ready for a clean conformance check-in**. Latest focused TCK filter `0057,0069,0070,0074,0081,0082,0085,0094,0095,0096,0097,0098,1148,1149,1156`: **439 total, 432 passed (98.41%), 226 native, 194 fallback, 12 provisional expected rejections, 6 failed, 1 error**. Evidence: `dmn-tck-runner/target/checkin-structural-tck.log` and `checkin-structural-accounting.json`. Focused generator build/install and 30 tests passed (0 failures/errors/skips), scoped Spotless ran successfully; evidence: `dmn-generator-sparksql/target/checkin-structural-focused.log`. Native fixes cover temporal try-casts, now/today arity, product scalar/null/arity handling, strict named product arguments, non-string range parsing, missing context fields, BKM known-kind checking/singleton coercion and call arity. Added recursive compile-time structural instance checks fixed five prior mismatches but **introduced context_019 regression**: resolve this first by validating the static structural inference against actual FEEL matching rules; do not claim zero regression. Remaining cases: `0070#context_019`, `0074#range_011` (equality unary range end emitted null), `0081#decision_002` (get entries passes struct to map_entries), `0082#decision_bkm_002`, `0082#decision_bkm_004_a`, `0082#invoke_002` (composite parameter validation/coercion), and `0082#decision_context_02` (declared context conversion drops a field). Full TCK has **not** been rerun after these changes because focused prerequisites remain failing. The preceding full metrics below are historical. Next safe action: repair context_019, implement the remaining semantic fixes, rerun this exact 439-case filter and focused generator checks, then complete full optimized Spark SQL hybrid TCK with 4 GB heap and 20/20/40 history limits before check-in. Recheck usage before resuming. No reset, clean, commit or push was performed.

Latest verified interval-native checkpoint: full optimized hybrid Spark SQL TCK **3,391 total; 3,357 passed (99.00%); 1,363 native (50.20% of 2,715 executable); 1,318 fallback (48.55% executable); 18 failed; 16 errors; 676 provisional expected rejections; 0 compilation errors**. Compared with the preceding full run: **+12 native, -12 fallback**, with total passes/failures/errors/rejections unchanged. All 14 interval relation TCK cases now pass natively (0 fallback/failures/errors/rejections), following endpoint/boundary SQL implementations for containment, meeting, starts/finishes, coincidence and overlaps. Focused generator tests: 30 passed, 0 failures/errors/skips. The interval fallback routing has been removed. Evidence: `dmn-generator-sparksql/target/interval-overlap-focused.log`; `dmn-tck-runner/target/interval-overlap-tck.log`, `interval-native-full.log`, `interval-native-full-accounting.json`, `interval-native-full-rejections.tsv`, and `interval-native-full-gc.log`. Maven exited 1 for the existing 34 residual conformance defects; native coverage remains below the goal. Documentation facts validation and diff whitespace check passed. Next bounded native promotion: implement correct median/mode/stddev list semantics and null/type validation to replace their temporary fallback, verify the corresponding TCK suites, and measure the full delta. Rejection audit remains outstanding. Preserve all edits/deletions. This supersedes earlier checkpoint metrics and statements that interval relations remain fallback below.

Latest native implementation checkpoint: **3,391 total; 3,357 passed (99.00%); 1,351 native (49.76% of 2,715 executable); 1,330 fallback (48.99% executable); 18 failed; 16 errors; 676 provisional expected rejections; 0 compilation errors**. Maven exited 1 for the residual conformance defects. Compared with the preceding string-join full run: +31 passed, +19 native, +12 fallback, -16 failed, -15 errors; rejections unchanged. Native number conversion now validates separators, null handling, arity and named arguments; native all/any support scalar booleans and reject invalid arity/names/varargs; native before/after compare range endpoints with open/closed boundary semantics. The other 12 interval relations temporarily use explained fallback and should be converted to endpoint SQL next, not treated as permanent exceptions. Focused generator tests: 30 passed, 0 failures/errors/skips; targeted TCK 0058,0059,0060,1130: 71/71 passed, 52 native, 19 fallback, 0 failures/errors/rejections. Evidence: `dmn-generator-sparksql/target/interval-boolean-native-focused.log`; `dmn-tck-runner/target/interval-boolean-native-tck.log`, `interval-boolean-full.log`, `interval-boolean-full-accounting.json`, and `interval-boolean-full-rejections.tsv`. Remaining 34 non-passing cases include coercion (6), instance-of (5), product (5), decision services (3), temporal functions (10), and five singleton groups. Prioritize native implementations and fallback conversions per the user's latest clarification. The rejection audit remains outstanding, and the native milestone remains unmet. Preserve all edits/deletions. This supersedes earlier checkpoint metrics below.

Latest 2026-10-04 checkpoint: full optimized Spark SQL hybrid TCK **3,391 total; 3,326 passed (98.08%); 1,332 native (49.06% of 2,715 executable); 1,318 fallback (48.55% executable); 34 failed; 31 errors; 676 provisional expected rejections; 0 compilation errors**. Maven exited 1 for remaining conformance failures. Compared with the 3,229-pass full run: +97 passed, -18 native, +115 fallback, -26 failed, -71 errors; rejections unchanged. Context/statistical functions now route to fallback with explicit implementation-limit reasons; native string join validates arity/names/types, handles null delimiter as empty, and coerces a string to a singleton list. Focused generator tests: 30 passed, 0 failures/errors/skips. Targeted context/statistical/matches TCK: 141/141 passed with fallback. String-join TCK: 22/22 passed, 18 native, 4 fallback, 0 failures/errors/rejections. Evidence in `dmn-tck-runner/target`: `conformance-routing-tck.log`, `conformance-routing-full-accounting.json`, `string-join-tck.log`, `string-join-full.log`, `string-join-full-accounting.json`, and `string-join-full-rejections.tsv`. Generator evidence: `dmn-generator-sparksql/target/string-join-focused.log`. Documentation facts validation passed for 12 modules; source whitespace check passed. Earlier strict MkDocs warnings remain unresolved. Next: investigate the remaining 65 non-passing cases, starting with interval handling (14), then number conversion and boolean aggregates; audit the 676 rejection diagnostics before changing the denominator. Native fallback promotion and rejection correctness remain outstanding. Preserve all edits/deletions. This supersedes earlier checkpoint metrics below.

## 1. Prime Directive & Constraints
- **Fallback Passes Must Be Minimized Towards 0**: The primary goal is to execute 100% of runnable OMG DMN TCK models natively in Spark SQL CTE expressions, driving hybrid UDF fallback passes to 0.
- **Zero Failures & Zero Errors**: Maintain regression-free execution.
- **Strict Reporting Requirement**: Whenever reporting that a TCK test run for a suite or full run has finished, ALWAYS immediately include the exact metrics block:
  - **Total Tests**: `<N>`
  - **Passed**: `<N> (<pct>%)`
  - **Native Passes**: `<N> (<pct>%)`
  - **Fallback Passes**: `<N> (<pct>%)`
  - **Failed**: `<N>`
  - **Errors**: `<N>`
  - **Expected Model Rejection Passes**: `<N>`

---

## 2. Environment & Build Commands
- **OS**: Windows 11
- **Maven**: `C:\10-tools\apache-maven-3.9.12\bin\mvn.cmd`
- **JDK**: Java 25 (`C:\10-tools\zulu25.32.21-ca-jdk25.0.2-win_x64`)
- **Key Modules**:
  - `dmn-generator-sparksql`: Generates pure Spark SQL CTEs (`SparkSqlExpressionEmitter.java`, `SparkSqlDecisionTableEmitter.java`, `SparkSqlGenerator.java`).
  - `dmn-optimizer`: Optimizes DMN IR prior to code generation.
  - `dmn-tck-runner`: Runs the OMG DMN TCK test suite against generated code.

### Standard Maven Commands
- **Recompile generator & optimizer**:
  ```powershell
  & "C:\10-tools\apache-maven-3.9.12\bin\mvn.cmd" install -DskipTests -pl dmn-optimizer,dmn-generator-sparksql
  ```
- **Run targeted TCK test suite(s)**:
  ```powershell
  & "C:\10-tools\apache-maven-3.9.12\bin\mvn.cmd" test -Ptck -pl dmn-tck-runner "-Dtest=OfficialTckSuiteTest" "-Dtck.skip=false" "-Dtck.backend=sparksql" "-Dtck.spark.hybrid=true" "-Dtck.modelVariant=optimized" "-Dtck.requireFull=false" "-Dtck.filter=0002,0034,0035"
  ```
- **Run full TCK suite**:
  ```powershell
  & "C:\10-tools\apache-maven-3.9.12\bin\mvn.cmd" test -Ptck -pl dmn-tck-runner -am "-Dtest=OfficialTckSuiteTest" "-Dtck.skip=false" "-Dtck.backend=sparksql" "-Dtck.spark.hybrid=true" "-Dtck.modelVariant=optimized" "-Dtck.requireFull=false"
  ```

---

## 3. High-Priority Bugs & Immediate Root Causes

### Bug 1: Spark Catalyst Type Check Failure on Binary `+` (`DATATYPE_MISMATCH.BINARY_OP_WRONG_TYPE`)
- **Files**: `dmn-generator-sparksql/src/main/java/io/finmsg/dmn/generator/sparksql/SparkSqlExpressionEmitter.java`
- **Status (2026-10-04)**: Resolved in focused verification. All `0034`/`0035` cases pass after selected-context/BKM return inference and floating-point string formatting fixes; see section 6 for exact evidence. Full-suite conformance remains under verification.
- **Historical symptom**: `0035-test-structure-output` timed out or failed Catalyst analysis on addition through `named_struct(...).__result__`. The rebuilt reproduction confirmed selected string operands were reaching raw `+` because unknown result types prevented structural inference.
- **Root Cause**:
  In `emitBinary`, when operand types cannot be statically proven numeric or string, code was attempting a runtime fallback:
  ```java
  CASE WHEN (typeof(left) = 'string' OR typeof(right) = 'string') 
       THEN concat(...) 
       ELSE (left + right) 
  END
  ```
  **Spark Catalyst analyzes both branches at query preparation time regardless of runtime conditions!** Because both sides were string expressions, Spark Catalyst evaluated `(left + right)` in the `ELSE` branch, recognized `STRING + STRING`, and threw a fatal analysis error before query execution even began.
- **Verified correction**:
  1. Enhance compile-time type resolution in `resolveTypeKind`:
     - Infer unresolved BKM return types from the body with a recursion guard.
     - For selected context fields, resolve the selected entry and its local binding rather than the enclosing struct.
  2. For `BinaryOperator.ADD`:
     - If either side is known `STRING` -> emit `concat(cast(left as string), cast(right as string))`.
     - If both sides are known `NUMERIC` -> emit `(left + right)`.
     - Preserve existing unknown-type mitigation pending broader coverage; do not generalize SQL-text heuristics into the typing strategy.

### Bug 2: `compliance-level-3/0002-string-functions#003` Failed
- **File**: `SparkSqlExpressionEmitter.java` (function `replace`)
- **Symptom**: Test `#003` failed returning `null` instead of the expected string.
- **Root Cause**:
  Test case `#003` executes `replace(A, "[aeiouy]", "[$0]")`.
  In Spark SQL `regexp_replace(input, pattern, replacement)`:
  Spark SQL treats `$0` as group 0 or backreference. Verify how Spark SQL handles `$0` in `regexp_replace` compared to Java regex or FEEL regex specifications.
- **Resolution (2026-10-03)**:
  Targeted TCK evidence showed `replace` was being shadowed by a synthetic context BKM for the decision named `Replace`; case-insensitive name matching recursively inlined that context. Name-based inlining now accepts only function-typed BKM entries. The exact suite passed 4/4 cases natively, including `#003`, so no `$0` translation was needed.

### Bug 3: `LocalScopeResolver` Recursive Chaining in `SparkSqlExpressionEmitter.java`
- **File**: `SparkSqlExpressionEmitter.java` (lines ~1012 in `emitContext` and ~1312 in `inlineBkm`)
- **Symptom**: Potential `StackOverflowError` when nested contexts or inlined BKMs call each other.
- **Root Cause**:
  Wrapping `slotNameResolver` inside a new anonymous `LocalScopeResolver` where delegating calls re-wrap without peeling to the root base resolver.
- **Fix Required**:
  Flatten the resolver lookup or extract root base resolver so chaining depth does not cause mutual recursion.
- **Resolution (2026-10-03)**:
  Replaced anonymous recursive resolver wrappers with an explicit `ScopedResolver` parent chain and restricted name-based BKM expansion to function-typed entries. This also fixed the `Replace` context/BKM name collision above. The targeted `0002-string-functions` suite passed 4/4 natively with no errors.

---

## 4. Current Passing Suites
- `compliance-level-2` (116 test cases): **116 / 116 NATIVE PASSES (100.0%), 0 FALLBACKS, 0 ERRORS, 0 FAILURES**.
- `0064, 0065, 0066` (44 test cases): **44 / 44 NATIVE PASSES (100.0%), 0 FALLBACKS**.
- `0067-feel-split-function` (9 cases): **9 / 9 NATIVE PASSES (100.0%)**.
- `1115-feel-date-function` (52 cases): **52 / 52 PASSES (100.0%)**.
- `0020-vacation-days` (7 cases): **7 / 7 PASSES (100.0%)** (fixed with COLLECT table defaults).

## 5. Latest Partial Run Evidence (2026-10-03)
- Focused Spark SQL TCK filter `0034,0035`: 1 of 4 TCK cases passed (`0034#001`); 3 failed (`0035#001` timed out, `0035#002` and `#003` failed Catalyst analysis). Maven reported 4 test cases, 1 failure, and 2 errors; the failing run did not produce native/fallback accounting totals.
- Full optimized Spark SQL hybrid TCK run: terminated with `OutOfMemoryError: Java heap space` around case 202/3,391. Its partial log contains 193 passes and 9 failures, including the unresolved `0034` error from before the focused fix and several timeouts. No final suite accounting was produced; these are partial log counts, not final results.
- Do not report the full suite as complete. The full run must be repeated after resolving the remaining `0035` issue and addressing the runner heap exhaustion.

## 6. Continuation Evidence (2026-10-04)

- `0035` is resolved in focused verification. The emitter now treats `ANY` as unresolved, infers a selected context member rather than its enclosing struct, and inspects unknown BKM return types with a recursion guard. Floating-point `string(number)` conversion removes Spark's integral `.0` suffix while preserving decimal scale, string inputs, booleans, and nulls. `string length` is inferred as numeric.
- Focused generator verification: 27 tests passed, 0 failures, 0 errors, 0 skips. Includes live Spark/interpreter parity for unknown BKM context results (string and numeric), numeric string conversion boundaries, and numeric addition of unknown-typed string length. Local evidence: `dmn-generator-sparksql/target/continuation-focused-verified.log` and Surefire reports.
- Targeted optimized hybrid Spark SQL TCK filter `0002,0034,0035`: **total 9; passed 9 (100.00%); native 9 (100.00%); fallback 0 (0.00%); failed 0; errors 0; expected model rejections 0**. All three `0035` cases pass at the unchanged 30-second timeout. Evidence: `dmn-tck-runner/target/context-string-verified.log` and `target/context-string-verified-accounting.json` in that module (local generated artifacts).
- Comparable `0034,0035` subset delta versus the initial 2026-10-04 reproduction: passed/native 1/4 to 4/4 (+3 cases, +75 percentage points); failures/errors 3 to 0. The intermediate typing-only run completed with 1 native pass, 3 result mismatches, 0 errors; those mismatches exposed floating-point string formatting rather than a remaining Catalyst error. Evidence: `dmn-tck-runner/target/context-typing-0034-0035.log`.
- Full 3,391-case baseline **completed without OOM**, retaining the 4 GB heap and bounding Spark SQL execution/job/stage history at 20/20/40. These limits are documented by [Spark 4.2 configuration](https://spark.apache.org/docs/4.2.0/configuration.html). GC logging and two allocation histograms were retained; the individual cause of the earlier OOM is not proven. Evidence paths: `dmn-tck-runner/target/native-coverage-baseline.log`, `target/native-coverage-baseline-accounting.json`, `target/native-coverage-gc.log`, and `target/native-coverage-heap-{early,later}.txt` in that module (local generated artifacts). No OOM heap dump was needed.

### Full baseline accounting and approved target

| Metric | Count | Total-suite rate | Executable-case rate |
| --- | ---: | ---: | ---: |
| Total cases | 3,391 | 100.00% | — |
| Passed | 3,177 | 93.69% | — |
| Native passes | 1,310 | 38.63% | 48.25% |
| Fallback passes | 1,191 | 35.12% | 43.87% |
| Failed | 112 | 3.30% | — |
| Errors | 102 | 3.01% | — |
| Expected model rejection passes | 676 | 19.94% | excluded |
| Executable cases | 2,715 | 80.06% | 100.00% |

- User-approved denominator: executable cases, with expected model rejections reported separately. Current goals are **at least 2,580 native passes** and **at most 135 fallback passes**, plus zero failed/errored cases. Recompute thresholds when the verified population changes.
- Compared with the older complete `native-promoted.log` reference (2026-10-01): native +1,145, fallback -1,358, total passed -213, failed +111, errors +102, expected rejections unchanged at 676. This comparison includes prior handover changes; it is not a causal attribution to the current continuation.
- Conformance restoration takes precedence over further promotion. Largest affected suites: matches (21), context put (16), interval (14), context (14), median (11), mode (11), context merge (9), decimal (9), number conversion (8), string join (8).
- Largest fallback suite groups: `0072-feel-in` (216), `0100-arithmetic` (188), `1116-feel-time-function` (77), `1117-feel-date-and-time-function` (75), `0070-feel-instance-of` (57), `1115-feel-date-function` (48). These are measured suite groups, not yet verified root-cause feature groups.
- Modulo correction verified: parameterized Spark decimal types are recognized, decimal arithmetic is preserved, and named arguments are validated and reordered. Focused generator tests: **28 passed, 0 failures, 0 errors, 0 skips**. Targeted `0056,0035`: **31 total; 31 passed (100.00%); 26 native (83.87%); 5 fallback (16.13%); 0 failed; 0 errors; 0 expected model rejections**. Comparable subset delta versus the full baseline: +5 passed/native cases, -5 failures, fallback unchanged. Evidence: `dmn-generator-sparksql/target/modulo-focused.log` and `dmn-tck-runner/target/modulo-tck.log`. A post-modulo full-suite delta is not yet verified.
- User refinement: aim for near-100% native CTE execution beyond the minimum 95% milestone. Audit the 676 provisional expected rejections against the models, expected outcomes, diagnostics, and DMN/FEEL semantics; correct misclassifications and recompute the executable population. Every remaining fallback/rejection group needs an evidence-backed limitation and feasible next native step. Do not exclude an implementation gap merely to improve coverage.
- Latest checkpoint: implementation stopped at 19% five-hour / 81% weekly remaining. Pre-guard rounding TCK evidence (`target/rounding-tck.log` in the runner): 105 total, 94 passed (89.52%), 89 native (84.76%), 5 fallback (4.76%), 8 failed, 3 errors, 0 expected rejections. An extreme-scale capability guard subsequently installed successfully and passed its dedicated regression (1 test covering three scales, 0 failures/errors/skips). The targeted TCK rerun and full-suite delta remain unverified. The concrete continuation handover is in `docs/dev/evidence-backed-spec-driven-development.md`.
- Subsequent user-requested full optimized hybrid run completed all **3,391 cases**: **3,229 passed (95.22%), 1,350 native (39.81% total / 49.72% executable), 1,203 fallback (35.48% total / 44.31% executable), 60 failed, 102 errors, 676 expected rejections**, with 0 compilation errors. Maven exited 1 because conformance failures remain. Versus the complete baseline: +52 passed, +40 native, +12 fallback, -52 failed, errors/rejections unchanged. The provisional executable denominator remains 2,715; the 95% native milestone is unmet. Evidence in `dmn-tck-runner/target`: `native-coverage-rounding-full.log`, `native-coverage-rounding-full-accounting.json`, `native-coverage-rounding-full-rejections.tsv` (676 diagnostic rows), and `rounding-full-gc.log`. The run used the established 4 GB heap and 20/20/40 Spark history limits. Rejection correctness remains unaudited.
- Documentation facts validation passed. Strict MkDocs remains blocked by 15 pre-existing missing slice/nav/outside-docs-link warnings associated with preserved handover files/deletions. Evidence: `dmn-tck-runner/target/continuation-docs.log`; do not restore deleted documents or claim the strict gate passed.
- **2026-10-04 Handover Verification & Conformance Root Causes**:
  - Scoped Spotless formatting verified across `dmn-generator-sparksql` and `dmn-tck-runner` (`BUILD SUCCESS`).
  - Focused generator tests passed: **30 tests run, 0 failures, 0 errors, 0 skipped**.
  - Targeted 105-case TCK filter (`0035,0056,1100,1141,1142,1143,1144`) against the installed extreme-scale rounding guard (>308 scale):
    **Total: 105; Passed: 105 (100.00%); Native: 88 (83.81%); Fallback: 17 (16.19%); Failed: 0; Errors: 0; Expected rejections: 0**.
    Completely resolved the prior 8 failures and 3 errors in this suite with 0 regressions.
  - Conformance restoration diagnosis (162 non-passing cases in the full suite):
    - `1111-feel-matches-function` (21 failures/errors): Spark SQL Java regex cannot parse FEEL/XQuery flags (`(?p)`, `(? )`, `(?X)`). Adding `"matches"` to `NON_NATIVE_FUNCTIONS` in `SparkSqlCapabilityAnalyzer` restored 100% pass on filter `1111` (**40 total, 40 passed, 0 native, 40 fallback, 0 failures, 0 errors**).
    - `1145, 1146, 1147` context functions (39 errors): `map_concat` and `map_from_entries` applied to `named_struct` instead of `map`, causing Catalyst type mismatch.
    - `1130-feel-interval` (14 errors): Direct scalar `<` / `>` comparisons with interval structs and unresolved routines like `coincides`.
    - `0061, 0062, 0063` statistical functions (29 failures/errors): Semantic differences (`mode` returning scalar, `median` even-length average) and Catalyst `UNEXPECTED_INPUT_TYPE` on `array_sort(NULL, ...)`.
  - Next safe action: Route remaining verified non-native constructs to conformant hybrid fallback or implement null-safe native equivalents to eliminate the remaining 102 errors and 60 failures, then execute full 3,391-case suite.
