# S-SPARK-01 — Promote Number / Decimal Types to Native Spark SQL Execution

Status: in progress
Owner: dmn-generator-sparksql
Requirements: [sparksql-architecture.md](../../sparksql-architecture.md), [actual-task.md](../../../prompts/actual-task.md)
Milestone: P11 / Hybrid 4

## Outcome

DMN decisions and inputs operating on `NUMBER` (`BigDecimal`) evaluate via native Spark SQL expressions and Common Table Expressions (CTEs) rather than falling back to `SparkSqlDecisionUdf`. Lossless numerical transport and precision boundaries are maintained at the Spark-Java codec boundary, migrating pure-numeric subgraphs to native Spark SQL execution.

## In scope

- Treat `RuntimeTypeKind.NUMBER` as a native type in `SparkSqlFeelValueCodec.isNativeType`.
- Adapt `SparkSqlFeelValueCodec.toSpark` and `fromSpark` to handle `BigDecimal` conversion cleanly across native Spark row representations (`DecimalType` / `DoubleType`).
- Update `SparkSqlCapabilityAnalyzer` so that `NUMBER` typed inputs, expressions, and decision outputs do not emit `"requires lossless FEEL transport"`.
- Preserve fallback routing for decisions that involve unsupported non-numeric types (e.g., Dates, Lists, Contexts).
- Verify pure-numeric arithmetic, comparisons, and numeric decision table CTE generation on local Spark session.

## Non-goals

- Promoting non-numeric complex types (`DATE`, `TIME`, `LIST`, `CONTEXT`) to native execution (deferred to subsequent slices).
- Changing FEEL AST representations or runtime IR semantics.
- Introducing external UDF dependencies for standard arithmetic operations.

## Constraints

- Zero regression across the full 3,391 TCK suite: all 3,390+ previously passing cases must continue to pass.
- Native SQL execution must strictly match Java DMN runtime evaluation semantics.
- No precision loss for integer and decimal values within standard Spark SQL decimal/double limits.

## Acceptance scenarios

- **Scenario 1: Pure numeric input and arithmetic evaluation**
  - Given a DMN model with numeric inputs and arithmetic decisions (e.g. `0002-input-data-number`, `0008-LX-arithmetic`)
  - When compiled and evaluated with hybrid fallback enabled
  - Then the decision executes via native Spark SQL CTE with diagnostic `NATIVE` and matches expected numeric results.

- **Scenario 2: Single-hit numeric decision table evaluation**
  - Given a DMN model with numeric input conditions and numeric outputs evaluated through a Unique/First hit policy decision table
  - When compiled with hybrid fallback enabled
  - Then the decision table evaluates natively in Spark SQL with diagnostic `NATIVE`.

- **Scenario 3: Non-numeric dependencies retain safe fallback**
  - Given a decision whose dependency graph includes unsupported temporal or complex list expressions
  - When analyzed by `SparkSqlCapabilityAnalyzer`
  - Then the decision is routed safely to `SparkSqlDecisionUdf` with diagnostic `FALLBACK`, without execution failure.

## Affected modules

- `dmn-generator-sparksql`
- `dmn-tck-runner` (rejection diagnostic evidence only; pass/route accounting is preserved)

### Scalar decimal rounding continuation contract

- Parameterized Spark decimal types must be recognized as numeric in native scalar built-ins.
- `decimal` and half-even rounding must match the interpreter's HALF_EVEN semantics; half-up and half-down must preserve the correct negative-tie behavior.
- Validate positive/negative ties, non-ties, decimal operands, invalid argument types, and named argument ordering against live Spark and the interpreter, then the focused official numeric TCK cases.
- Capture every expected compilation rejection's case/backend and compiler diagnostic in a separate generated evidence file. This does not certify its correctness or change the executable denominator.

## Verification plan

1. **Focused Unit & Integration Tests:**
   - `mvn test -pl dmn-generator-sparksql -Dtest=SparkSqlHybridTest,SparkSqlDmnIntegrationTest,SparkSqlHybridIntegrationTest`
2. **Full TCK Suite Run (~45 min checkpoint):**
   - Run TCK runner accounting for `dmn-generator-sparksql`
   - Record and report exact counts: total test cases, passing TCs, failing TCs, native CTE pass rate, and fallback UDF rate.

## Completion evidence

- Focused module suite: `SparkSqlHybridTest`, `SparkSqlDmnIntegrationTest`, and `SparkSqlHybridIntegrationTest` passed (24 tests; 0 failures, 0 errors) on 2026-10-03.
- Native Spark integration coverage asserts no UDF fallback for numeric arithmetic and a numeric `FIRST` decision table, and checks both results locally in Spark.
- Targeted optimized Spark SQL TCK filter (`0002,0004,0008`): 22/22 passed, 16 native passes, 6 fallback passes, 0 failures, 0 errors, 0 expected model rejections.
- Targeted `0002-string-functions`: 4/4 passed natively after restricting name-based BKM inlining to function-typed entries; this prevents a context decision named `Replace` from shadowing FEEL `replace` and recursively inlining itself.
- Focused optimized Spark SQL TCK filter (`0034,0035`): 1/4 cases passed (`0034#001`); 3/4 failed (`0035#001` timed out, `0035#002` and `#003` failed Catalyst analysis on `named_struct(...) + named_struct(...)`). The focused run ended with 1 Maven test failure and 2 errors; native/fallback accounting was not produced.
- A partial emitter mitigation makes an untyped `+` use `concat` when its generated operand SQL contains known string operations; it fixed `0034#001` in the focused rerun. The `0035` context-result/type issue remains unresolved, so the slice stays in progress.
- Full TCK: incomplete. A fresh full run terminated with `OutOfMemoryError: Java heap space` around case 202/3,391. Its partial log recorded 193 passes and 9 failures, but no final accounting; these counts are not full-suite metrics. Repeat the full run after addressing the remaining regression and runner heap exhaustion.

### 2026-10-04 continuation

Active check-in repair continuation: user explicitly authorized continuing despite the below-20% usage threshold for the remaining blockers. Last measured usage: 12% five-hour / 66% weekly remaining; this override applies to the current check-in repair run. Last completed affected-suite TCK remains **439 total; 432 passed; 226 native; 194 fallback; 12 provisional expected rejections; 6 failed; 1 error**, in `dmn-tck-runner/target/checkin-structural-tck.log` and `checkin-structural-accounting.json`. Subsequent source fixes: allow null members in structural instance matching (context_019 regression); emit a singleton equality range's upper endpoint; validate declared context BKM arguments and list return compatibility; preserve extra context fields in fallback results by binary lossless UDF transport; implement native get entries from known struct fields. The first generator verification after the structural/range/transport fixes passed (30 focused tests, no failures/errors/skips). A second scoped Spotless/build/install plus the same focused tests is currently running after native get entries, logged to `dmn-generator-sparksql/target/checkin-final-focused.log`; do not claim that run passed until its exit/report is checked. Next: wait for that run, rerun the exact 439-case TCK filter, fix any residual/regression, then run the full optimized hybrid Spark SQL TCK with 4 GB heap and history limits 20/20/40. No post-repair full-suite result exists yet, and **clean check-in readiness is unverified**. Preserve all edits/deletions. Prior below-threshold stop and metrics below are historical.

Check-in preparation usage stop: **18% five-hour / 67% weekly remaining** triggered the user-required implementation stop. Working-tree changes are preserved; **not ready for a clean conformance check-in**. Latest focused TCK filter `0057,0069,0070,0074,0081,0082,0085,0094,0095,0096,0097,0098,1148,1149,1156`: **439 total, 432 passed (98.41%), 226 native, 194 fallback, 12 provisional expected rejections, 6 failed, 1 error**. Evidence: `dmn-tck-runner/target/checkin-structural-tck.log` and `checkin-structural-accounting.json`. Focused generator build/install and 30 tests passed (0 failures/errors/skips), scoped Spotless ran successfully; evidence: `dmn-generator-sparksql/target/checkin-structural-focused.log`. Native fixes cover temporal try-casts, now/today arity, product scalar/null/arity handling, strict named product arguments, non-string range parsing, missing context fields, BKM known-kind checking/singleton coercion and call arity. Added recursive compile-time structural instance checks fixed five prior mismatches but **introduced context_019 regression**: resolve this first by validating the static structural inference against actual FEEL matching rules; do not claim zero regression. Remaining cases: `0070#context_019`, `0074#range_011` (equality unary range end emitted null), `0081#decision_002` (get entries passes struct to map_entries), `0082#decision_bkm_002`, `0082#decision_bkm_004_a`, `0082#invoke_002` (composite parameter validation/coercion), and `0082#decision_context_02` (declared context conversion drops a field). Full TCK has **not** been rerun after these changes because focused prerequisites remain failing. The preceding full metrics below are historical. Next safe action: repair context_019, implement the remaining semantic fixes, rerun this exact 439-case filter and focused generator checks, then complete full optimized Spark SQL hybrid TCK with 4 GB heap and 20/20/40 history limits before check-in. Recheck usage before resuming. No reset, clean, commit or push was performed.

Latest verified interval-native checkpoint: full optimized hybrid Spark SQL TCK **3,391 total; 3,357 passed (99.00%); 1,363 native (50.20% of 2,715 executable); 1,318 fallback (48.55% executable); 18 failed; 16 errors; 676 provisional expected rejections; 0 compilation errors**. Compared with the preceding full run: **+12 native, -12 fallback**, with total passes/failures/errors/rejections unchanged. All 14 interval relation TCK cases now pass natively (0 fallback/failures/errors/rejections), following endpoint/boundary SQL implementations for containment, meeting, starts/finishes, coincidence and overlaps. Focused generator tests: 30 passed, 0 failures/errors/skips. The interval fallback routing has been removed. Evidence: `dmn-generator-sparksql/target/interval-overlap-focused.log`; `dmn-tck-runner/target/interval-overlap-tck.log`, `interval-native-full.log`, `interval-native-full-accounting.json`, `interval-native-full-rejections.tsv`, and `interval-native-full-gc.log`. Maven exited 1 for the existing 34 residual conformance defects; native coverage remains below the goal. Documentation facts validation and diff whitespace check passed. Next bounded native promotion: implement correct median/mode/stddev list semantics and null/type validation to replace their temporary fallback, verify the corresponding TCK suites, and measure the full delta. Rejection audit remains outstanding. Preserve all edits/deletions. This supersedes earlier checkpoint metrics and statements that interval relations remain fallback below.

Latest native implementation checkpoint: **3,391 total; 3,357 passed (99.00%); 1,351 native (49.76% of 2,715 executable); 1,330 fallback (48.99% executable); 18 failed; 16 errors; 676 provisional expected rejections; 0 compilation errors**. Maven exited 1 for the residual conformance defects. Compared with the preceding string-join full run: +31 passed, +19 native, +12 fallback, -16 failed, -15 errors; rejections unchanged. Native number conversion now validates separators, null handling, arity and named arguments; native all/any support scalar booleans and reject invalid arity/names/varargs; native before/after compare range endpoints with open/closed boundary semantics. The other 12 interval relations temporarily use explained fallback and should be converted to endpoint SQL next, not treated as permanent exceptions. Focused generator tests: 30 passed, 0 failures/errors/skips; targeted TCK 0058,0059,0060,1130: 71/71 passed, 52 native, 19 fallback, 0 failures/errors/rejections. Evidence: `dmn-generator-sparksql/target/interval-boolean-native-focused.log`; `dmn-tck-runner/target/interval-boolean-native-tck.log`, `interval-boolean-full.log`, `interval-boolean-full-accounting.json`, and `interval-boolean-full-rejections.tsv`. Remaining 34 non-passing cases include coercion (6), instance-of (5), product (5), decision services (3), temporal functions (10), and five singleton groups. Prioritize native implementations and fallback conversions per the user's latest clarification. The rejection audit remains outstanding, and the native milestone remains unmet. Preserve all edits/deletions. This supersedes earlier checkpoint metrics below.

Latest 2026-10-04 checkpoint: full optimized Spark SQL hybrid TCK **3,391 total; 3,326 passed (98.08%); 1,332 native (49.06% of 2,715 executable); 1,318 fallback (48.55% executable); 34 failed; 31 errors; 676 provisional expected rejections; 0 compilation errors**. Maven exited 1 for remaining conformance failures. Compared with the 3,229-pass full run: +97 passed, -18 native, +115 fallback, -26 failed, -71 errors; rejections unchanged. Context/statistical functions now route to fallback with explicit implementation-limit reasons; native string join validates arity/names/types, handles null delimiter as empty, and coerces a string to a singleton list. Focused generator tests: 30 passed, 0 failures/errors/skips. Targeted context/statistical/matches TCK: 141/141 passed with fallback. String-join TCK: 22/22 passed, 18 native, 4 fallback, 0 failures/errors/rejections. Evidence in `dmn-tck-runner/target`: `conformance-routing-tck.log`, `conformance-routing-full-accounting.json`, `string-join-tck.log`, `string-join-full.log`, `string-join-full-accounting.json`, and `string-join-full-rejections.tsv`. Generator evidence: `dmn-generator-sparksql/target/string-join-focused.log`. Documentation facts validation passed for 12 modules; source whitespace check passed. Earlier strict MkDocs warnings remain unresolved. Next: investigate the remaining 65 non-passing cases, starting with interval handling (14), then number conversion and boolean aggregates; audit the 676 rejection diagnostics before changing the denominator. Native fallback promotion and rejection correctness remain outstanding. Preserve all edits/deletions. This supersedes earlier checkpoint metrics below.

- Subsequent requested full optimized hybrid run: **3,391 total; 3,229 passed (95.22%); 1,350 native (49.72% executable); 1,203 fallback (44.31% executable); 60 failed; 102 errors; 676 provisional expected rejections; 0 compilation errors**. Baseline delta: +40 native, +12 fallback, -52 failed; errors/rejections unchanged. Maven exited 1. Evidence: `dmn-tck-runner/target/native-coverage-rounding-full.log`, `native-coverage-rounding-full-accounting.json`, and `native-coverage-rounding-full-rejections.tsv`. This supersedes pending full-run statements below; the slice remains in progress.

- Latest usage checkpoint: stopped implementation at 19% five-hour / 81% weekly remaining. Rounding changes passed 29 focused generator tests before the extreme-scale guard; the 105-case TCK run recorded 94 passed, 89 native, 5 fallback, 8 failed, 3 errors, and 0 expected rejections. The subsequent guard install succeeded and its dedicated three-scale regression passed (1 test, 0 failures/errors/skips). The 105-case TCK rerun and post-rounding full run remain pending. See the concrete handover in `docs/dev/evidence-backed-spec-driven-development.md`; no new full-suite delta is claimed.

- Context/BKM result inference now resolves unknown types from the selected field and BKM body, with a guard against recursive inference. Numeric `string` conversion removes integral floating-point `.0` suffixes; exact decimal scale and string inputs are preserved. Unknown-typed `string length` remains numeric.
- Focused generator tests: **27 passed, 0 failures, 0 errors, 0 skips**. New live Spark checks compare string/numeric context BKM results and numeric string conversion with the interpreter. Evidence: `dmn-generator-sparksql/target/continuation-focused-verified.log` and Surefire reports (local generated artifacts).
- Focused optimized hybrid TCK filter `0002,0034,0035`: **9 total; 9 passed (100.00%); 9 native (100.00%); 0 fallback (0.00%); 0 failed; 0 errors; 0 expected model rejections**. All `0035` cases pass within the original timeout. Evidence: `dmn-tck-runner/target/context-string-verified.log` and `target/context-string-verified-accounting.json` in that module.
- Comparable `0034,0035` delta: 1/4 to 4/4 native passes (+3 cases, +75 percentage points), with failed/errored cases reduced from 3 to 0.
- Full-suite baseline completed without OOM at the existing 4 GB heap, with Spark SQL execution/job/stage history retention of 20/20/40. **3,391 total; 3,177 passed (93.69%); 1,310 native (38.63% of all cases); 1,191 fallback (35.12%); 112 failed; 102 errors; 676 expected model rejection passes**. Evidence: `dmn-tck-runner/target/native-coverage-baseline.log`, `target/native-coverage-baseline-accounting.json`, and `target/native-coverage-gc.log` in that module. The individual cause of the earlier OOM is unproven.
- User approved measuring coverage over the **2,715 executable cases**, excluding the 676 expected rejections: native **48.25%**, fallback **43.87%**; goals are at least **2,580 native** and at most **135 fallback**, with zero failures/errors. Conformance restoration is required before claiming this slice complete.
- Historical complete-run delta versus 2026-10-01 `native-promoted.log`: native +1,145; fallback -1,358; passed -213; failed +111; errors +102; expected rejections unchanged. This includes prior handover changes and is not attribution solely to the current fixes.
- Modulo follow-up verified: decimal type recognition, decimal-preserving arithmetic, and named argument validation pass **28 focused generator tests** with 0 failures/errors/skips. Targeted `0056,0035`: **31 total; 31 passed (100.00%); 26 native (83.87%); 5 fallback (16.13%); 0 failed; 0 errors; 0 expected rejections**. Compared with this subset in the full baseline: +5 passed/native cases, -5 failures, fallback unchanged. Evidence: `dmn-generator-sparksql/target/modulo-focused.log` and `dmn-tck-runner/target/modulo-tck.log`. No post-modulo full-suite delta is claimed.
- Coverage intent is near-100% native CTE execution, with 95% as the minimum milestone. The 676 expected rejections remain provisional pending audit, and all remaining non-native groups require evidence-backed explanations and next steps.
- Documentation facts check passed; strict MkDocs has 15 pre-existing warnings from preserved missing slice/nav/outside-docs links. See section 6 of the repository-root `TCK_SPARKSQL_STATUS.md` handover for complete evidence and next priorities.
- Targeted 105-case TCK filter (`0035,0056,1100,1141,1142,1143,1144`) verified against extreme-scale rounding fallback guard: **105 total; 105 passed (100.00%); 88 native (83.81%); 17 fallback (16.19%); 0 failures; 0 errors; 0 expected rejections**. Completely restored conformance on this suite.
- Targeted `1111-feel-matches-function` verified with `matches` in `NON_NATIVE_FUNCTIONS`: **40 total; 40 passed (100.00%); 0 native; 40 fallback; 0 failures; 0 errors**.
- Residual suite non-passing cases (162 total) diagnosed across `native-coverage-rounding-full-accounting.json`: `matches` (21, resolved), `context put/merge/func` (39), `interval` (14), `median/mode/stddev` (29). Conformance restoration required before full suite promotion.
