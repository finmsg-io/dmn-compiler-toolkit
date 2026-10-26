Current requirement/checkpoint — 2026-10-26: [hardening evidence](docs/dev/slices/security-correctness-hardening-task-2026-10-24.md).
Every TCK case must execute and return its expected result. Compiler rejection
is not a pass. The duration([]) fallback is fixed: 2,729/3,391 cases executed
natively and matched expected results (80.48% of inventory), zero fallback;
662 cases remain unexecuted (19.52%). Native/fallback rates among executed cases
are 100%/0%, but whole-inventory execution remains incomplete. The current
runner's 3,391-pass total still includes the 662 rejections and does not satisfy
the clarified requirement. Full generator regression: 168 tests passed.
User order: commit/push this checkpoint, then remove rejection-as-pass and fix
blocked execution. Do not count the historical static negative classifications
as runtime conformance. Legacy-parser timeouts remain deferred.

Historical checkpoint follows; its figures describe its dated source state.

2026-10-24 verified full temporal-chain checkpoint: 3,391/3,391 official TCK
outcomes passed. Native Spark SQL: 2,715/2,715 executable cases (100%), which is
2,715/3,391 total cases (80.06%). Fallback: 0/2,715 executable cases (0%).
Compiler-rejected with NO Spark SQL executed: 676/3,391 cases (19.94%). These
negative cases are neither native nor fallback. Their exact rejection reasons
have not been audited against the TCK's intended error; the runner currently
checks error expectation at case level. Failed/errors/unknown 0; unsupported,
excluded, invalid and compilation-error outcomes 0. The 676 compiler rejections
are recorded as expected passing outcomes, separately from unexpected compilation
errors. Identical case keys/statuses to the ranking checkpoint; exactly one route
changed: 0007-date-time#001 FALLBACK -> NATIVE. No reversals or rejection changes.
This proves native execution for this executable TCK inventory, not all possible
DMN/FEEL models. Outside bounded proofs, models can still require fallback.
Temporal SQL retains nanoseconds, offsets, period/duration identity and named-zone
rules. It uses Spark try_reflect built-ins calling standard JDK java.time factories
for parsing/validation/formatting; arithmetic and zone selection are SQL. No FEEL
evaluator or custom calculation UDF is called from SQL. Generated-schema inputRow
projections are required. Constant-only expressions retain existing native paths.
Full generator regression/install: 161/161 tests passed; 2,973 native SQL actions
(96.15%), 119 fallback actions (3.85%) of 3,092; unknown 0. All 59 failed SQL
actions were expected assertions: 58 native and one fallback. This action metric
includes intentional fallback/error tests and is not the TCK-case denominator.
Generator SHA256 (target and installed):
941A917B54C82D03B59C865D37193E21FBDB98132167E6C2CAD7EA0748C45E8C.
Accounting: dmn-tck-runner/target/native-temporal-chain-full-accounting-2026-10-24.json.
Rejections: dmn-tck-runner/target/native-temporal-chain-full-rejections-2026-10-24.tsv.
TCK log: dmn-tck-runner/target/native-temporal-chain-full-tck-2026-10-24.log.
Generator log: dmn-generator-sparksql/target/native-temporal-chain-corrected-full-generator-2026-10-24.log.
Scope, per-run history, limitations and rejection semantics:
docs/dev/slices/native-temporal-chains-2026-10-24.md.
Next: audit the 676 expected rejection reasons and broaden beyond-TCK temporal
proofs where useful; never imply rejected cases executed native SQL.
Checkpoint implementation follows bb0e090; use Git history for its commit identity.

2026-10-24 verified full structured-ranking checkpoint: 3,391/3,391 official
cases passed. Executable denominator 2,715: 2,714 native (99.96%), 1 fallback
(0.04%); failed/errors/unknown 0. Expected model rejections remain 676;
unsupported, excluded, invalid and compilation errors 0. Exact comparison with
the prior full checkpoint has identical case keys and statuses; the only route
change is 0014-loan-comparison#001 FALLBACK -> NATIVE. No reversals or rejection
changes. Remaining official fallback: 0007-date-time#001. Fallback count fell 50%.
The new reusable proof computes scalar context records through static FEEL
functions over bounded literal relations/lists, then ranks by exact numeric fields.
Tests vary row count, sort direction, names, Unicode, bindings, precision, nulls
and ties; coercing comparators, aliased slots and unproven consumers stay fallback.
Native SQL performs calculation, sorting and final payload encoding; power retains
the previously documented Spark java_method(Math.pow) built-in implementation.
Lossless numeric fallback transport also now uses encoded original inputs and
numeric results. Non-finite unused arguments retain their values until FEEL
actually evaluates them. Ordinary native NUMBER columns still use DOUBLE unless
an exact native proof/projection applies. Callers must use generated-schema input
rows and regenerate SQL/schema/runner artifacts together; see the transport slice.
Complete generator regression passed 154/154 tests: 2,954 native actions (96.16%),
118 fallback (3.84%) of 3,072; unknown 0, 58 expected native SQL errors. After the
final non-finite codec addition, focused regression/install passed five tests:
18 native (38.30%), 29 fallback (61.70%) of 47; unknown 0, two expected native
and one expected fallback SQL errors. Full TCK ran against this final artifact.
Installed generator SHA256:
5FC167F36DBEA59856A201A644416397408DA36D0DF41CF073C32A32ACDB05CF.
Accounting: dmn-tck-runner/target/native-ranked-context-full-accounting-2026-10-24.json.
Log: dmn-tck-runner/target/native-ranked-context-full-tck-2026-10-24.log.
Implementation and per-run history: docs/dev/slices/native-ranked-contexts-2026-10-24.md.
Next: general lossless temporal construction/validation and dependent arithmetic,
retaining nanoseconds, offsets and duration kind identity. Zero TCK fallback would
still describe these 2,715 executable cases, not unrestricted DMN/FEEL support.
Changes remain uncommitted after bb0e090.

2026-10-23 exact numeric scalar-context extension verified: generator 151/151
passed, with 2,938 native SQL actions (96.80%), 97 fallback (3.20%) of 3,035;
unknown 0, 58 expected native SQL error assertions. The added fallback action
checks the unsupported consumer boundary. Full official TCK: 3,391/3,391 passed;
2,713 native (99.93%), 2 fallback (0.07%) of 2,715 executable cases, plus 676
expected model rejections. Failed/errors/unknown 0. All outcomes and routes are
identical to the previous full checkpoint: no conversions or reversals this batch.
This adds reusable exact NUMBER terminal results to scalar BKM/context plans,
with renamed-field, binding, precision, null, lazy-branch and eager-error tests.
Unproven numeric consumers retain fallback; proven aliases can expand natively.
It does not yet lower the structured loan loop results and sorting operations.
Important separate limitation: the ordinary declared NUMBER hybrid input/output
bridge uses DOUBLE, including numeric fallback output; arbitrary BigDecimal
precision is not preserved there. A reproducer and explicit follow-up are recorded
in docs/dev/slices/native-numeric-scalar-contexts-2026-10-23.md. Do not conflate
exact native scalar results with precision guarantees for every fallback route.
Remaining official fallbacks: 0007-date-time#001 and 0014-loan-comparison#001.
Next: lossless numeric hybrid transport and structured loan result/sort support.
Accounting: dmn-tck-runner/target/native-numeric-scalar-full-accounting-2026-10-23.json.
TCK log: dmn-tck-runner/target/native-numeric-scalar-full-tck-2026-10-23.log.
Generator log: dmn-generator-sparksql/target/native-numeric-scalar-full-generator-2026-10-23.log.
Installed generator SHA256:
F361EF61D06275B0057A191D1F4F4E2643A0D760994D6346DFDD938D5A4BDC6D.
Changes are uncommitted after bb0e090.

Native coverage interpretation (2026-10-23): native lowering is reusable but
bounded by supported IR shapes, types, bindings and representation limits. A small
model change can cross a boundary and require fallback. Zero official TCK fallback
would establish native coverage of 2,715 executable suite cases, not all DMN/FEEL.
Broader validation must cover model variations and feature combinations beyond
the fixtures. See docs/sparksql-architecture.md, "Native coverage: what the TCK rate means".

2026-10-23 requested confirmation full TCK rerun: 3,391/3,391 passed.
Executable cases: 2,713 native (99.93%), 2 fallback (0.07%) of 2,715;
676 expected model rejections; failed/errors/unknown 0. All case outcomes and
routes are identical to the preceding scalar-context full run. Remaining fallback:
0007-date-time#001 and 0014-loan-comparison#001. This confirms six conversions
from the eight-fallback payment-loop checkpoint, with no reversals.
Accounting: dmn-tck-runner/target/native-scalar-context-confirmation-full-accounting-2026-10-23.json.
Log: dmn-tck-runner/target/native-scalar-context-confirmation-full-tck-2026-10-23.log.
Generator artifact SHA256: 72FA1829B2E181FC7F027F3033ED00B49BE5E4527DD30DBEA85EACCD9BB223EA.
Generator regression remains 149/149 passed: 2,916 native (96.81%), 96 fallback
(3.19%) of 3,012 SQL actions; unknown 0, 56 expected SQL error assertions.
No production changes were made during or after these verification runs.

2026-10-23 verified full scalar-context checkpoint: 3,391/3,391 official
cases passed. Executable denominator 2,715: 2,713 native (99.93%), 2 fallback
(0.07%); failed/unknown 0. Expected model rejections remain 676; errors,
unsupported, excluded and invalid 0. Exact comparison with the payment-loop
checkpoint has identical case keys and six FALLBACK -> NATIVE conversions:
0004-lending#001 and 0087-chapter-11-example#001/#002/#003d/#003e/#004.
No route reversals or rejection changes. Fallback count decreased 75% (8 to 2).
Bounded boolean BKM/context plans preserve exact numeric intermediates, eager
context/argument evaluation, lazy branches and UTF-16 string comparison identity.
Generator regression/install passed 149/149 tests: 2,916 native SQL actions
(96.81%), 96 fallback (3.19%) of 3,012; unknown 0. Fifty-six native SQL errors
are expected passing assertions; no JUnit failures/errors/skips. Corrected focused
regression: 42 native (100%), 0 fallback (0%) of 42; unknown 0, eight expected
native SQL errors. Per-run results and the corrected expression-level in semantics
are recorded in docs/dev/slices/native-scalar-contexts-2026-10-23.md.
Target/installed generator SHA256:
72FA1829B2E181FC7F027F3033ED00B49BE5E4527DD30DBEA85EACCD9BB223EA.
Accounting: dmn-tck-runner/target/native-scalar-context-full-accounting-2026-10-23.json.
Log: dmn-tck-runner/target/native-scalar-context-full-tck-2026-10-23.log.
Maven exited 0; Surefire logged a fork shutdown timeout after successful completion.
Remaining: 0007-date-time#001 (temporal construction/validation) and
0014-loan-comparison#001 (context/list metrics and sorting). Next work must retain
precision, offsets and duration identity for temporal values, or prove the full
loan-comparison result and sorting semantics. Official fixtures are unchanged.
This checkpoint is included in the scalar-context and payment-loop implementation commit.

2026-10-23 verified full numeric-context-loop checkpoint: 3,391/3,391 official
cases passed. Executable denominator 2,715: 2,707 native (99.71%), 8 fallback
(0.29%); failed/unknown 0. Expected model rejections remain 676; errors,
unsupported, excluded and invalid 0. Exact prior-full comparison has identical
case keys and one FALLBACK -> NATIVE conversion: 0003-iteration#001. No route
reversals or rejection changes. The standalone payment iteration is now native.
Generator regression/install passed 145/145 tests: 2,874 native SQL actions
(96.77%), 96 fallback (3.23%) of 2,970; unknown 0. Forty-eight native SQL errors
are intentional passed assertions; no JUnit failures/errors/skips. Final focused
validation: 26 native (100%), 0 fallback (0%) of 26; unknown 0, fourteen expected
native SQL errors. Earlier focused attempts exposed blocked dynamic context paths,
an analyzer depth limit, and two incorrect null-input fixture expectations;
all corrections and per-run counts are recorded in the numeric-context-loop slice.
SQL computes bounded arithmetic steps in topological order under default analyzer
settings. Power uses Spark's built-in java_method expression calling Math.pow,
matching runtime double-to-decimal conversion. No FEEL evaluator UDF is used in
this route; Java reflection inside a Spark built-in is part of its native metric.
Target/installed generator SHA256:
AEDE0F6878CDF7287CD1D08C578480C538C20C3B04CF887FB9D7BC5F4866AB00.
Accounting: dmn-tck-runner/target/native-payment-loop-full-accounting-2026-10-23.json.
Log: dmn-tck-runner/target/native-payment-loop-full-tck-2026-10-23.log.
Remaining groups: temporal construction/validation (1), composite lending (7).
Next: inspect shared lending BKM/scalar numeric result boundaries and reuse the
flat exact arithmetic/power plan where the complete result can be proven native.
Temporal construction needs a separate transport/validation chain implementation.
This checkpoint is included in the scalar-context and payment-loop implementation commit.

2026-10-22 verified full division/factor-loop checkpoint: 3,391/3,391 official
cases passed. Executable denominator 2,715: 2,706 native (99.67%), 9 fallback
(0.33%); failed/unknown 0. Expected model rejections remain 676; errors,
unsupported, excluded and invalid 0. Exact comparison with the previous full
run has identical case keys and three FALLBACK -> NATIVE conversions:
0031-user-defined-functions#001/#002 and 0033-for-loops#003. No route reversals
or rejection changes. This full result supersedes the targeted estimates below.
Corrected generator regression/install passed 142/142 tests: 2,848 native SQL
actions (96.74%), 96 fallback (3.26%) of 2,944; unknown 0. Thirty-four native
SQL errors are intentional passed assertions; no JUnit failures/errors/skips.
Two new fallback actions verify that other decimal rounding scales remain fallback.
Initial official loop run passed 4/4 with 3 native (75%), 1 fallback (25%) of 4;
failed/unknown 0. Its missing conversion exposed LIST<ANY> expression typing
beneath a declared LIST<BOOLEAN>. The lowering and fixture now handle that compiler
shape. Corrected focused: 22 native (91.67%), 2 fallback (8.33%) of 24; unknown 0,
eight intentional native SQL errors. Full execution confirms the correction.
Target/installed generator SHA256:
7F9423C1437840B3A4248B1C63879D421573EBCB6D1616F7A56B183402CFC174.
Accounting: dmn-tck-runner/target/native-factor-loop-full-accounting-2026-10-22.json.
Log: dmn-tck-runner/target/native-factor-loop-full-tck-2026-10-22.log.
Remaining groups: temporal construction (1), payment iteration with power (1),
composite lending (7). This checkpoint accompanies the requested commit/push.

2026-10-22 latest exact-division checkpoint: official 0031-user-defined-functions
passed 3/3: 3 native cases (100%), 0 fallback (0%) of 3; failed/unknown 0,
expected model rejections 0. Cases #001 and #002 changed FALLBACK -> NATIVE;
#003 remains native. No reversals or rejection changes within the suite.
Targeted estimate: 2,705 native (99.63%), 10 fallback (0.37%) of 2,715 executable
cases. This is not a new full run. Latest full remains 2,703 native (99.56%),
12 fallback (0.44%), plus 676 expected model rejections.
Generator regression/install passed 140/140 tests: 2,826 native SQL actions
(96.78%), 94 fallback (3.22%) of 2,920; unknown 0. Twenty-six native SQL errors
are intentional passed assertions. No JUnit failures/errors/skips. Corrected
focused division tests passed: 70 native (100%), 0 fallback (0%) of 70;
unknown 0, ten intentional SQL error assertions. Initial normal run: 48 native
(100%), 0 fallback (0%) of 48, failed/unknown 0. Expanded boundary run stopped
on a reference-runtime overflow after 58 native (100%), 0 fallback (0%) of 58,
failed SQL/unknown 0. SQL and fixture now match BigDecimal provisional scale checks.
SQL performs DECIMAL128 long division with half-even rounding, exact preferred
scale, denominator-first validation and null/zero behavior; projections carry
representation only. Target/installed generator SHA256:
0EA05DDB465AA2EAE7F206E32819E27183571F803D749B05D77643A214C2D3DD.
Accounting: dmn-tck-runner/target/native-exact-division-accounting-2026-10-22.json.
Log: dmn-tck-runner/target/native-exact-division-tck-2026-10-22.log.
Next groups: temporal construction (1), iteration/factor loop (2), composite
lending (7). Changes remain uncommitted after a9b3449.

2026-10-22 verified full exact-numeric checkpoint: 3,391/3,391 official cases
passed. Executable denominator 2,715: 2,703 native (99.56%), 12 fallback
(0.44%); failed/unknown 0. Expected model rejections remain 676; errors,
unsupported, excluded and invalid 0. Exact comparison with the last full run
confirms two FALLBACK -> NATIVE conversions: 0031-user-defined-functions#003
and 0033-for-loops#002. Identical case keys; no route reversals or rejection changes.
Generator regression/install passed 138/138 tests: 2,756 native SQL actions
(96.70%), 94 fallback (3.30%) of 2,850; unknown 0. Sixteen native SQL errors
are intentional passed assertions; no JUnit failures/errors/skips. Focused numeric
function tests passed: 32 native (100%), 0 fallback (0%) of 32; failed/unknown 0.
Exact scalar SQL now supports numeric contexts with addition, subtraction,
multiplication, comparisons and range branches after static context-function
binding. Division remains fallback. Ordinary context coercion was restored after
four initial capability-test failures; the corrected full regression passed.
Target/installed generator SHA256:
7F54771BB1B1439751478D6808D2E5F5744338F697956222046613C92323EF6C.
Accounting: dmn-tck-runner/target/native-numeric-functions-full-accounting-2026-10-22.json.
Log: dmn-tck-runner/target/native-numeric-functions-full-tck-2026-10-22.log.
Remaining groups: temporal construction (1), numeric functions with division (2),
iteration/factor loop (2), composite lending (7). This verified checkpoint
supersedes the targeted estimates below and accompanies the requested commit/push.

2026-10-22 latest exact-product checkpoint: official 0033-for-loops suite
passed 4/4: 3 native cases (75%), 1 fallback (25%) of 4; failed/unknown 0,
expected model rejections 0. New conversion 0033-for-loops#002: exact Cartesian
multiplication. Exact latest-full comparison confirms one FALLBACK -> NATIVE,
no reversals or rejection changes within the suite. Targeted estimate:
2,702 native (99.52%), 13 fallback (0.48%) of 2,715 executable cases.
This is not a new full run. Latest full remains 2,701 native (99.48%),
14 fallback (0.52%), plus 676 provisional expected model rejections.
Generator regression/install passed 136/136 tests: 2,724 native SQL actions
(96.66%), 94 fallback (3.34%) of 2,818; unknown 0. Sixteen native SQL errors
are intentional passed assertions (ten new product checks plus six existing).
No JUnit failures/errors/skips. Focused corrected: 20 native actions (100%),
0 fallback (0%) of 20; failed/unknown 0. Exact decimal payloads now use explicit
scale/unscaled tag 19; readers retain legacy tag 4 compatibility. Target/installed
JAR SHA256: C78BA2CEE6A8E6B67FCBBA3B55E74D03BB43C95640BB1C9413C3EA4031708088.
Accounting: dmn-tck-runner/target/native-exact-product-accounting-2026-10-22.json.
Next groups: temporal construction (1), numeric functions (3), iteration/factor
loop (2), composite lending (7). Changes remain uncommitted after 264056f.

2026-10-22 verified full native-sort checkpoint: 3,391/3,391 official cases
passed. Executable denominator 2,715: 2,701 native (99.48%), 14 fallback
(0.52%); failed/unknown 0. Expected model rejections remain 676; errors,
unsupported, excluded and invalid 0. Exact comparison with the previous full
baseline: six FALLBACK -> NATIVE conversions, identical case keys, no route
reversals and no rejection changes. New in this batch: 0013-sort#001, with all
three requested sort results native. The other five confirm prior calendar
and indexed-join targeted conversions. This is a new full baseline, not an estimate.
Generator regression/install passed 134/134 tests: 2,694 native SQL actions
(96.63%), 94 fallback (3.37%) of 2,788; unknown 0. Six native SQL errors are
intentional passed assertions. Six added fallback checks verify comparator
return coercion and aliased bindings retain fallback semantics. Target/installed
JAR SHA256: 591FB4471C7F408BCEB298F2C69D3DE84F37BEA9EC51D71699379D726EC61D7B.
Accounting: dmn-tck-runner/target/native-exact-sort-full-accounting-2026-10-22.json.
Log: dmn-tck-runner/target/native-exact-sort-full-tck-2026-10-22.log.
Remaining groups: temporal construction (1), numeric functions (3), iteration
and loops (3), composite lending (7). User requested commit/push after verification;
this checkpoint accompanies the native-sort implementation commit.

2026-10-21 latest indexed-join checkpoint: official 0006-join#001 passed
natively: 1 native case (100%), 0 fallback (0%) of 1; failed/unknown 0,
expected model rejections 0. Exact accounting comparison confirms FALLBACK ->
NATIVE. Combined with four prior calendar conversions, targeted estimate:
2,700 native (99.45%), 15 fallback (0.55%) of 2,715 executable cases.
This is not a new full result. Latest full remains 2,695 native (99.26%),
20 fallback (0.74%), plus 676 provisional expected model rejections.
Generator regression/install: 132/132 JUnit tests passed; route accounting
2,658 native actions (96.80%), 88 fallback (3.20%) of 2,746; unknown 0.
Six native SQL errors are intentional error-preservation assertions, all passed;
there are no JUnit failures/errors. Successful SQL actions alone: 2,652 native
(96.79%), 88 fallback (3.21%) of 2,740. Focused join: 34 native (100%),
0 fallback (0%) of 34, failed/unknown 0. Target/installed JAR SHA256:
C2F441AA68EC8B720F432B82778B534348AF14FC8DB6FDBA39493AEE4C0E0EE0.
Accounting: dmn-tck-runner/target/native-indexed-join-accounting-2026-10-21.json.
Remaining groups: temporal construction (1), numeric functions (3), iteration,
sort and loops (4), composite lending (7). Changes remain uncommitted.

2026-10-21 latest calendar checkpoint: official day-of-week/month-of-year
suites passed 24/24: 24 native cases (100%), 0 fallback (0%) of 24;
failed/unknown 0, expected model rejections 0. Four verified conversions:
0096 date_001/datetime_001 and 0097 date_001/datetime_001. No reversals or
rejection changes within these suites against the latest full baseline.
Targeted estimate: 2,699 native (99.41%), 16 fallback (0.59%) of 2,715.
This is not a new full run. Latest full remains 2,695 native (99.26%),
20 fallback (0.74%), plus 676 provisional expected model rejections.
Generator regression/install passed 130/130 tests: 2,618 native SQL actions
(96.75%), 88 fallback (3.25%) of 2,706; failed/unknown 0.
Generator target/installed SHA256:
9717E98E4B0C2F126B9C7A5A3703F7E771FF2C352A519A2F58FE560E99662246.
Accounting: dmn-tck-runner/target/native-calendar-context-accounting-2026-10-21.json.
Next groups: temporal construction (1), numeric functions (3), collections,
joins, sort and loops (5), composite lending (7). Changes remain uncommitted.

2026-10-21 full official temporal-comparison checkpoint: 3,391/3,391 passed.
Executable denominator 2,715: 2,695 native (99.26%), 20 fallback (0.74%);
failed/unknown 0. Expected model rejections remain 676; errors, unsupported,
excluded and invalid 0. Exact baseline comparison: six FALLBACK -> NATIVE,
no reversals, identical case keys and no rejection changes. Three conversions
are this temporal batch (all 0036-dt-variable-input cases); the other three
confirm earlier filter, quantifier and string-function targeted conversions.
Generator regression/install: 129/129 passed; 2,550 native actions (96.66%),
88 fallback (3.34%) of 2,638; failed/unknown 0. Target/installed JAR SHA256:
9897A6DB374BA2AEE2F6291628A8F0B98FE1D700FA09C0EBE4EFE63246865C87.
Accounting: dmn-tck-runner/target/native-temporal-full-accounting-2026-10-21.json.
Log: dmn-tck-runner/target/native-temporal-full-tck-2026-10-21.log.
Remaining priorities: calendar arithmetic (4), temporal construction (1),
numeric function library (3), collections/joins/sort/loops (5), lending (7).
Changes remain uncommitted. Continue from this verified full baseline.

2026-10-21 latest targeted numeric-quantifier checkpoint: official some/every
and filter suites passed 4/4; 4 native cases (100%), zero fallback (0%) of 4;
failed/unknown zero, expected model rejections zero. New conversion:
0016-some-every#001; prior 0001-filter#001 remains native. Exact comparison
with the latest full run confirms two forward conversions and no reversals or
rejection changes within these suites. Combined with earlier 0030 functions:
targeted estimate 2,692 native (99.15%), 23 fallback (0.85%) of 2,715 executable
baseline cases. This is not a new full result. Last full remains 2,689 native
(99.04%), 26 fallback (0.96%), plus 676 provisional expected model rejections.
Generator regression/install: 128/128 JUnit tests passed; 2,470 native SQL actions
(96.56%), 88 fallback (3.44%) of 2,558; failed/unknown zero. Hybrid integration:
2,457 native (96.54%), 88 fallback (3.46%) of 2,545. DMN integration: 13 native
(100%), zero fallback (0%) of 13. Corrected focused: three tests passed;
316 native actions (100%), zero fallback (0%) of 316, failed/unknown zero.
Initial focused run had one assertion failure for relation member lowering and
one shared-runtime null-source error; completed SQL actions 56 native (100%),
zero fallback (0%) of 56, failed/unknown SQL routes zero. Corrected path folding
and explicit null-source error semantics before successful focused/full checks.
Target/installed generator SHA256:
64ADE48B86275982C8A0E897AC53D3B0E3F8158CD140DF401ADB9B6E3FC55483.
Formatting/install/official Maven exits zero. Runner target evidence:
native-numeric-quantifier-accounting-2026-10-21.json and native-numeric-quantifier-tck-2026-10-21.log.
Numeric ordering uses sign, long decimal exponent and canonical digits; SQL
aggregate preserves early exit before later invalid values. Bounded literal
relations fold through shared runtime; closed context-row quantifiers and
predicate BKM bindings lower natively. Changes remain uncommitted, including
preceding batches; last commit/push 6974cb5. The 100% native goal stays open.
Next: exact numeric joins/sorts, zone-preserving temporal arithmetic and bounded
loops/functions. Latest usage: 48% primary remaining. Full cadence remains
45 minutes work, usage below 20%, or 20 conversions; three conversions since
latest full checkpoint. Generator action and TCK case denominators differ.

2026-10-21 latest targeted exact-numeric-filter checkpoint: official filter TCK
passed 1/1; 1 native case (100%), zero fallback (0%) of 1; failed/unknown zero,
expected model rejections zero. Exactly one new conversion: 0001-filter#001,
with no reversals or rejection changes in the suite. Combined with the earlier
0030 function conversion: targeted estimate 2,691 native (99.12%), 24 fallback
(0.88%) of 2,715 executable baseline cases. This is not a new full result.
Last full remains 2,689 native (99.04%), 26 fallback (0.96%), plus 676 provisional
expected model rejections separately. Two conversions since the latest full run.
Corrected generator regression/install: 126/126 tests; 2,202 native SQL actions
(96.16%), 88 fallback (3.84%) of 2,290; failed/unknown zero. Hybrid integration:
2,189 native (96.14%), 88 fallback (3.86%) of 2,277. DMN integration: 13 native
(100%), zero fallback (0%) of 13. Initial and expanded focused runs each passed:
48 native actions (100%), zero fallback (0%) of 48, failed/unknown zero.
The initial full regression also passed with the same counts. Review replaced
stripTrailingZeros normalization with coefficient/long-scale keys and added Java
minimum/maximum-scale decimals; corrected full regression passed afterward.
Target/installed generator SHA256:
8B49AD6E22D4ABC2E254D6D830F7FECE605C1D18EB6279DD3DA40BBF14131BAD.
Formatting/install/official Maven exits zero. Runner target evidence:
native-numeric-filter-accounting-2026-10-21.json and native-numeric-filter-tck-2026-10-21.log.
Projection prepares exact numeric keys and text fields after input coercion;
native SQL performs filtering and output projection. Original input columns stay
unchanged. Tests cover near-equal decimals, beyond-double integers, 1E+400,
extreme scales, null names/rows, empty/null/invalid inputs, collisions and both
query forms. This and preceding batches remain uncommitted; last commit/push
6974cb5. The 100% native goal remains open. Next: exact numeric quantifiers,
zone-preserving temporal arithmetic, and bounded loops/function binding.
Usage check: 84% primary remaining. Continue full cadence after 45 minutes work,
usage below 20%, or 20 verified conversions.

2026-10-21 latest targeted string-function checkpoint: official user-defined-function
TCK passed 1/1; 1 native case (100%), zero fallback (0%) of 1; failed/unknown
zero, expected model rejections zero. Both positional and named decisions native.
Exactly one conversion: 0030-user-defined-functions#001, with no reversals or
rejection changes in the suite. Targeted estimate: 2,690 native (99.08%),
25 fallback (0.92%) of 2,715 executable baseline cases. This is not a new full
result. Last full remains 2,689 native (99.04%), 26 fallback (0.96%), plus
676 provisional expected model rejections separately.
Generator regression/install completed successfully while this task was idle:
125/125 JUnit tests; 2,154 native SQL actions (96.07%), 88 fallback (3.93%)
of 2,242; failed/unknown zero. DMN integration: 13 native (100%), zero fallback
(0%) of 13; three existing actions changed to native. Hybrid integration:
2,141 native (96.05%), 88 fallback (3.95%) of 2,229. Focused regression:
48 native actions (100%), zero fallback (0%) of 48; failed/unknown zero.
Formatting/install/targeted Maven exits zero. Target/installed generator SHA256:
E5FE5E2CDB7CF7D0B8048D7163F9D8ACCFE2F61C6FCED71D2F1634C072AC45E1.
Runner target evidence: native-string-function-accounting-2026-10-21.json and
native-string-function-tck-2026-10-21.log. STRING is inferred for ADD after
binding only when both resolved operands are STRING and the original result
was ANY/STRING. Numeric arithmetic and arbitrary ANY operands are unchanged.
This and preceding service/sort/missing-class batches remain uncommitted.
The last commit/push remains 6974cb5. The 100% native goal stays open.
Next: exact numeric/context filters, zone-preserving calendar arithmetic and
bounded loops/functions. Full cadence: 45 minutes work, usage below 20%, or
20 verified conversions; one verified conversion since the latest full run.

2026-10-20 LATEST FULL generated Spark SQL checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,689 native (99.04%), 26 fallback (0.96%);
failed/unknown routes zero. Compilation/execution errors, unsupported, excluded
and invalid counts zero. Separately: 676 provisional expected model rejections,
unchanged. Exact comparison with the preceding 30-fallback full run confirms
four forward conversions, zero reversals and no rejection changes:
0076-feel-external-java#incorrect_002, 0088-no-decision-logic#001,
0092-feel-lambda#017/#018. Fallback decreased 30 to 26 (13.33% fewer cases).
The prior service/sort targeted estimates are now verified by the full run.
The Java missing-class change handles shared error/null semantics; it does not
add a successful Java method. Bootstrap absence is checked without initializing
classes; existing method and argument restrictions remain active.
Generator: 124/124 tests passed; 2,103 native SQL actions (95.85%), 91 fallback
(4.15%) of 2,194; failed/unknown zero. Focused: 24 native actions (100%), zero
fallback (0%) of 24; failed/unknown zero. Official external-Java suite: 18 native
cases (100%), zero fallback (0%) of 18; failed/unknown and expected rejections zero.
Target/installed generator SHA256:
DDC93867BAE03C4361C215C5E25D4F6D2DEE506B4A19BCB92473B5112A162C8C.
Runner target evidence: native-missing-java-full-accounting-2026-10-20.json and
native-missing-java-full-tck-2026-10-20.log. Formatting/install/targeted Maven
exits zero; full Maven exit zero. Documentation facts, strict MkDocs build and
whitespace checks passed. Full checkpoint refresh covers accumulated service, bounded sort and
missing-class changes. This batch and earlier service/sort work remain uncommitted;
previous full checkpoint was committed/pushed as 6974cb5. The 100% native goal
and provisional rejection audit remain open. Next: exact numeric/context filters,
zone-preserving calendar arithmetic, and bounded loop/function binding.
Restart full cadence here: 45 minutes work, usage below 20%, or 20 verified
conversions. Generator action rates and executable TCK case rates use different
denominators.

2026-10-20 latest targeted bounded-sort checkpoint: official lambda and service
TCK suites passed 19/19; 19 native cases (100%), zero fallback (0%) of 19;
failed/unknown zero, expected model rejections zero. Two new conversions:
0092-feel-lambda#017/#018. Earlier 0088-no-decision-logic#001 remains native.
Exact comparison with the last full run confirms three forward conversions and
zero reversals or rejection changes within these suites. Targeted estimate:
2,688 native (99.01%), 27 fallback (0.99%) of 2,715 executable baseline cases.
This is not a new full result: last full remains 2,685 native (98.90%),
30 fallback (1.10%), plus 676 provisional expected model rejections separately.
Corrected generator regression/install passed: 123/123 JUnit tests; 2,079 native
SQL actions (95.81%), 91 fallback (4.19%) of 2,170; failed/unknown zero.
Corrected focused regression: 64 native actions (100%), zero fallback (0%) of
64; failed/unknown zero. First focused/full runs also passed with the same action
counts, but route inspection found a nonzero captured-input slot could not be
used directly in a tiny temporary evaluation model. The proof now remaps that
single input to slot zero; regression covers nonzero model slots and ANY results.
Target/installed generator SHA256:
0A681BD48274D956D4CC0076B3028976C27D89857BF23FFD0355DCE68C11FBD7.
Formatting/install/official Maven exit zero. Runner target evidence:
native-bounded-sort-accounting-2026-10-20.json and native-bounded-sort-tck-2026-10-20.log.
Sort proof handles at most 16 fixed strings and equality-only Boolean comparator
branches over one parameter and a string literal or declared STRING input.
Generation uses shared sort semantics; query execution uses literal lists/CASE,
with no sort UDF or adapter comparator execution. Lexical binding is resolved
before specialization. Other comparator/source shapes retain their existing route.
This batch and prior service-string work remain uncommitted; preceding full
checkpoint was committed/pushed as 6974cb5. The 100% native goal remains open.
Next: exact numeric/context filters, zone-preserving calendar arithmetic, broader
bounded function binding. Full cadence: 45 minutes work, usage below 20%, or
20 verified conversions; three verified conversions since the last full run.

2026-10-20 latest targeted service-string checkpoint: official no-decision-logic
TCK passed 1/1; 1 native case (100%), 0 fallback (0%) of 1; failed/unknown zero,
expected model rejections zero. Exactly one verified conversion:
0088-no-decision-logic#001. No reversals or rejection changes within the suite.
Targeted estimate: 2,686 native (98.93%), 29 fallback (1.07%) of 2,715 executable
baseline cases. This is not a new full result. Last full remains 2,685 native
(98.90%), 30 fallback (1.10%), plus 676 provisional expected model rejections.
Generator: 122/122 tests passed; 2,015 native SQL actions (95.68%), 91 fallback
(4.32%) of 2,106; failed/unknown zero. Focused: 54 native actions (100%), zero
fallback (0%) of 54, failed/unknown zero. Formatting/install/Maven exit zero.
Target/installed generator JAR SHA256:
ED19877F67E34ED246F7255C779A70FAF10C0EC2A388AEF98B73036519F34AC7.
Runner target evidence: native-service-string-accounting-2026-10-20.json and
native-service-string-tck-2026-10-20.log. String projection now accepts typed
STRING decision references; producer dependency capability checks remain active.
This batch is uncommitted; the preceding full checkpoint was committed/pushed
as 6974cb5. The 100% native goal stays open. Next: exact numeric/context filters,
zone-preserving calendar arithmetic and bounded function binding. Usage check:
88% primary remaining. Continue full cadence after 45 minutes work, usage below
20%, or twenty verified conversions; one conversion since last full checkpoint.

2026-10-20 LATEST FULL generated Spark SQL checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,685 native (98.90%), 30 fallback (1.10%);
failed/unknown routes zero. Compilation/execution errors, unsupported, excluded
and invalid counts all zero. Separately: 676 provisional expected model rejections,
unchanged. Exact comparison with the previous 34-fallback full checkpoint confirms
four fallback-to-native conversions, zero reversals and no rejection changes:
0011-insert-remove#001, 0017-tableTests#003/#004, 0021-singleton-list#001.
Fallback decreased 34 to 30 (11.76% fewer fallback cases). This full run verifies
the accumulated targeted estimate. The date-table batch added two conversions.
Generator: 121/121 tests passed, 1,961 native SQL actions (95.57%), 91 fallback
(4.43%) of 2,052; failed/unknown zero. Focused date-table regression: 160 native
(100%), zero fallback (0%) of 160; failed/unknown zero. Targeted official table
suite: 4 native cases (100%), zero fallback (0%) of 4, no model rejections.
Target/installed generator JAR SHA256:
89EAF0F5B1418DA320E40E901805EAD54916E51A2193E8AFE83562942CA63ABA.
Runner target evidence: native-date-table-full-accounting-2026-10-20.json and
native-date-table-full-tck-2026-10-20.log. Full Maven exit zero. Documentation facts, strict MkDocs build and whitespace
checks passed. Full run triggered by usage reaching 13% remaining. Original input payloads and exact date ordering are preserved.
Changes remain uncommitted. The 100% native goal and rejection audit remain open.
Next candidates: exact numeric/context filters, zone-preserving calendar arithmetic,
lexically bound functions and decision-service input decisions. Restart full-run
cadence here: 45 minutes work, usage below 20%, or twenty verified conversions.
Generator action rates and TCK executable-case rates have different denominators.

2026-10-20 latest targeted list-position checkpoint: insert/remove official TCK
1/1 passed, 1 native case (100%), 0 fallback (0%) of 1; failed/unknown zero,
expected model rejections zero. All five list-edit decisions are native. Exactly
one conversion: 0011-insert-remove#001, no reversals/rejection changes in the suite.
Combined targeted estimate with string selections: 2,683 native (98.82%),
32 fallback (1.18%) of 2,715 executable baseline cases. This is not a new full
result. Last full remains 2,681 native (98.75%), 34 fallback (1.25%), with
676 provisional expected model rejections separately. Generator: 120/120 passed;
1,801 native SQL actions (95.19%), 91 fallback (4.81%) of 1,892; failed/unknown zero.
Focused: 136 native (100%), zero fallback (0%) of 136, failed/unknown zero.
Formatting/install/Maven exit zero. Target/installed generator SHA256:
06BAD93247C2BAFBC4C1DE82934FFBEFA134A4BF0E3EA540E1A55F1C6F734980.
Runner target evidence: native-list-position-accounting-2026-10-20.json and
native-list-position-tck-2026-10-20.log. Position projection preserves shared
fractional truncation and signed 32-bit wrapping from raw coerced numbers,
without deriving positions through Spark doubles. Both CTE query forms are covered.
Changes remain uncommitted. The 100% native goal stays open. Next candidates:
exact numeric/context filters, zone-preserving calendar arithmetic, function binding.
Full cadence: 45 minutes work, usage below 20%, or twenty verified conversions
since the last full checkpoint.

2026-10-20 latest targeted string-selection checkpoint: singleton-list TCK
1/1 passed, 1 native case (100%), 0 fallback (0%), denominator 1; failed/unknown
zero, expected model rejections zero. All five requested decisions are native.
Exactly one conversion: 0021-singleton-list#001; no reversals/rejection changes
within the suite. Targeted estimate is 2,682 native (98.78%), 33 fallback (1.22%)
of the 2,715 executable baseline. This is not a new full result: last full remains
2,681 native (98.75%), 34 fallback (1.25%), with 676 provisional expected model
rejections separately. Generator: 119/119 passed, 1,665 native SQL actions
(94.82%), 91 fallback (5.18%) of 1,756; failed/unknown zero. Focused regression:
78 native (100%), zero fallback (0%) of 78, failed/unknown zero. Formatting,
install and Maven exit zero. Target/installed generator SHA256:
153F12374C6353E72BE7EDBF7D3BF4E06AB16D57C17D88FB242025573CAB725E.
Runner target evidence: native-string-selection-accounting-2026-10-20.json and
native-string-selection-tck-2026-10-20.log. Changes remain uncommitted.
The new proof covers typed string equality filters, bounded sublists, singleton
declared-result coercion and guarded case formatting. Numeric/context filters,
other predicates and captured locals retain fallback. The 100% goal stays open.
Continue with exact numeric/context filtering, dynamic list positions or bounded
function binding. Full cadence: 45 minutes work, usage below 20%, or twenty
verified conversions since the last full checkpoint.

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

2026-10-20 latest targeted nested-list checkpoint: official append/flatten and
concatenate suites 2/2 passed, 2 native (100%), 0 fallback (0%) of 2 executable
cases; failed/unknown zero, expected model rejections zero. Two conversions:
0009 append-flatten#001 and 0010 concatenate#001. No reversals/rejection changes
within the tested suites. Combined targeted estimate: 2,681 native (98.75%),
34 fallback (1.25%) of 2,715 executable baseline cases, seven conversions since
the last full. This is not a new full result. Last full: 2,674 native (98.49%),
41 fallback (1.51%), with 676 provisional expected model rejections separately.
Generator: 118/118 tests passed; 1,587 native actions (94.58%), 91 fallback
(5.42%) of 1,678; failed/unknown zero. Focused final: 15 native (100%), zero
fallback (0%) of 15, failed/unknown zero. Formatting/install/Maven exit zero.
Target/installed generator SHA256:
9E474569B86359ED932EB77E5ABD49E8577A4A7154C0BE53A2B257B6E861C0CD.
Runner target evidence: native-nested-text-accounting-2026-10-20.json and
native-nested-text-tck-2026-10-20.log. Shared list typing now covers two-level
string/Boolean append, concatenate and flatten; mixed/deeper/numeric layouts
retain guards. Dynamic context keys, exact date loops and calendar literals are
also uncommitted from the prior batches. Preserve all their regression evidence.
The 100% native goal remains open. Next candidates: typed filters/singleton result
coercion, dynamic list positions, calendar arithmetic preserving zones, function
binding. Full cadence: 45 minutes work, usage below 20%, or twenty conversions.

2026-10-19 latest targeted lookup checkpoint: official get-value suite 14/14
passed, 14 native (100%), 0 fallback (0%) of 14 executable cases; failed/unknown
zero, expected model rejections zero. Exactly two conversions: decision_013/014.
No reversals or rejection changes within the suite. Combined targeted estimate
with date-loop/calendar work: 2,679 native (98.67%), 36 fallback (1.33%) of 2,715.
This is not a new full-run result. Last full remains 2,674 native (98.49%),
41 fallback (1.51%), with 676 provisional expected model rejections separate.
Five verified conversions since that full checkpoint; changes remain uncommitted.
Generator: 117/117 passed; 1,569 native SQL actions (94.69%), 88 fallback
(5.31%) of 1,657; failed/unknown zero. Added mixed AllDecisions tests account for
33 intentional fallback actions. Extended escaping regression: 36 native (50%),
36 deliberate fallback (50%) of 72, failed/unknown zero; only test data changed
since complete generator run. Formatting/install and both Maven runs passed.
Target/installed generator SHA256:
AF01B74BA82AD4FAA2338DD948D46A854408853A80B5CC3D9DFE3BDC1AC0D37B.
Runner target: native-lookup-key-accounting-2026-10-19.json and
native-lookup-key-tck-2026-10-19.log. Original payload bytes and string-only
concatenation remain distinct from canonical key conversion; SQL does the lookup.
The 100% goal remains open. Next: calendar arithmetic preserving zones, list
filters/exact numeric input representation, or bounded function-library lowering.
Full cadence remains 45 minutes, usage below 20%, or twenty verified conversions.

2026-10-19 latest targeted calendar checkpoint: three official calendar suites
43/43 passed; 39 native (90.70%), 4 fallback (9.30%) of 43 executable cases,
failed/unknown zero, expected model rejections zero. One verified conversion:
0098 week-of-year#date_008. No reversals or rejection changes in these suites.
Together with date loops, targeted estimate: 2,677 native (98.60%), 38 fallback
(1.40%) of 2,715 executable baseline cases. This is not a new full result.
Last full: 2,674 native (98.49%), 41 fallback (1.51%), 676 provisional expected
model rejections separate. Four dynamic calendar contexts retain fallback because
ANY temporal arithmetic needs zone/time semantics beyond the current projection.
Generator: 116/116 tests passed; 1,536 native actions (96.54%), 55 fallback
(3.46%) of 1,591, zero failed/unknown. Formatting/install/Maven exit zero.
Target/installed generator SHA256:
BA25061A396DB3784C55D5FA83AE0820A5CB997D793CE9F9A898C0605793B603.
Runner target evidence: native-calendar-literal-accounting-2026-10-19.json and
native-calendar-literal-tck-2026-10-19.log. Changes remain uncommitted.
The 100% native goal stays open. Next: typed dynamic context lookup, calendar
arithmetic preserving zones, list filters and exact numeric representation.
Full cadence remains 45 minutes, usage below 20%, or twenty verified conversions.

2026-10-19 latest targeted date-loop checkpoint: official 0084 suite 24/24 passed,
24 native (100%), 0 fallback (0%) of 24 executable cases; failed/unknown zero,
expected model rejections zero. Exactly two conversions: decision_017/018;
no reversals or rejection changes within the suite. Targeted estimate is now
2,676 native (98.56%), 39 fallback (1.44%) of the 2,715 executable baseline.
This estimate is not a new full-run result: last full remains 2,674 native
(98.49%), 41 fallback (1.51%), with 676 provisional expected rejections separate.
Generator: 115/115 tests passed; 1,518 native actions (96.50%), 55 fallback
(3.50%) of 1,573; failed/unknown zero. Formatting/install passed, Maven exit zero.
Installed generator SHA256:
E2361428BED53B8A54BCC9886524374630784DD09C44995E914D0206D4D18149.
Saved runner target: native-date-loops-accounting-2026-10-19.json and
native-date-loops-tck-2026-10-19.log. Changes remain uncommitted.
The 100% native goal remains open; next candidates are calendar contexts and
typed dynamic context lookups. Full cadence: 45 minutes, usage below 20%, or
20 conversions. At batch start primary usage remaining was 84%.

2026-10-19 LATEST FULL checkpoint for commit `699e324`: 3,391/3,391 passed.
Of 2,715 executable cases: 2,674 native (98.49%), 41 fallback (1.51%);
zero failed/unknown routes and zero compilation/execution errors, unsupported,
excluded or invalid cases. Separately: 676 provisional expected model rejections,
unchanged. Compared with the saved 47-fallback checkpoint: six conversions,
zero reverse conversions and zero rejection reclassifications; 12.77% fewer
fallback cases. Converted: 0012 list-functions#019; 0090 paths#nested_list_001
and #nested_list_002; 1145 context#010; 1156 range#001_h and #001_i.
Target/installed generator JAR SHA256 remains
2D330C8DAF8F0BC86B08AAC2A9E5AC3DA8B026E5BC8442614A5B86B78A7DA517.
Saved runner target evidence: native-range-context-flatten-full-accounting-2026-10-19.json
and native-range-context-flatten-full-tck-2026-10-19.log. The earlier path-only
full run verified 2,670 native / 45 fallback and used a different artifact.
Do not confuse generator SQL action counts with executable TCK case counts.
The current code was committed/pushed as `699e324`; this full-run handover update
is a subsequent documentation change. The 100% native goal remains open.
Next candidates: bounded literal date-range loops (0084#decision_017/018),
calendar contexts (0096/0097/0098), then typed dynamic context lookups (0080).
Preserve precision, offsets, duration identity and lexical binding. Full-TCK cadence:
45 minutes work, remaining usage below 20%, or twenty verified conversions.

2026-10-19 commit handover: compared with pushed baseline `a7b3ec8`, the five
verified batches reduce fallback from 64 to 47: 17 additional native cases,
26.56% fewer fallback cases, zero reverse conversions and unchanged provisional
expected rejection accounting. Continue from the commit containing this handover.
The latest full TCK and generator evidence below covers the complete source changes.
The 100% native goal remains open; prioritize exact temporal semantics, shared list
result typing, and bounded lowering with lexical binding. No further code test run
is needed for this documentation checkpoint.

2026-10-19 LATEST FULL closed-collection checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,668 native (98.27%), 47 fallback (1.73%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections, unchanged.
Four verified fallback-to-native conversions: 0069 list cases 032/034 and 0072
context membership context_001/context_001_a. Zero reverse conversions or rejection
reclassifications against the date-comparison full checkpoint. Fallback decreased
from 51 to 47 (7.84% fewer fallback cases).
Generator: 111/111 passed; 1,505 native SQL actions (96.47%), 55 fallback (3.53%)
of 1,560; zero failed/unknown. Formatting/install passed. Target/installed JAR:
B2E6F69AF3F3297B72869510E7BC12A9F9E3DCA22875AFC48E8E8D4B5FFBAC32.
Retained dependency hashes: semantic analysis
68D10EA209CF6D27B4034BAA78FCC54F4A617D7C60EC1185AED662F5B27FC095;
runtime IR FB5E0BEC189171EB5DF6EF088BFA38500A00F48B8E95DF3AA5E2664E7DAAC7C4.
Saved runner target: native-closed-collections-full-accounting-2026-10-19.json and
native-closed-collections-full-tck-2026-10-19.log. The new capability guard retains
fallback for unrepresentable closed results while reusing existing nullable-context
native filter proofs. Missing context keys remain distinct from explicit nulls.
The 100% native goal and semantic rejection audit remain open. Remaining leading
groups: chapter 11 five; function libraries and table-variable inputs three each.
Preserve exact arithmetic, temporal precision/offset/duration identity, list typing
and lexical binding. Never promote dynamic FEEL division through Spark doubles.
Restart cadence here: 45 minutes work, remaining usage below 20%, or twenty
further verified conversions, whichever comes first. Verified evidence is retained in this handover.
Details: [handover](docs/dev/slices/native-closed-collections-2026-10-19.md).

2026-10-19 earlier FULL date-comparison checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,664 native (98.12%), 51 fallback (1.88%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections, unchanged.
Three verified fallback-to-native conversions: 0032 conditionals cases 004, 005,
006; zero reverse conversions or rejection reclassifications against the nested-score
full checkpoint. Fallback decreased from 54 to 51 (5.56% fewer fallback cases).
Generator: 110/110 passed; 1,493 native SQL actions (96.57%), 53 fallback (3.43%)
of 1,546; zero failed/unknown. Formatting/install passed. Target/installed JAR:
B4C2DB4CF5931D4F01FD96840198B653BED54E83E3E83B7B8C0C150BB7765566.
Retained dependency hashes from the previous checkpoint: semantic analysis
68D10EA209CF6D27B4034BAA78FCC54F4A617D7C60EC1185AED662F5B27FC095;
runtime IR FB5E0BEC189171EB5DF6EF088BFA38500A00F48B8E95DF3AA5E2664E7DAAC7C4.
Saved runner target: native-date-comparison-full-accounting-2026-10-19.json and
native-date-comparison-full-tck-2026-10-19.log. Full run triggered when primary
usage reached 20% remaining, after targeted verification; no feature edits followed.
The 100% native goal and semantic rejection audit remain open. Preserve temporal
precision/offset/duration identity and exact arithmetic. Remaining leading groups:
chapter 11 five; function libraries and table-variable inputs three each.
Restart cadence here: 45 minutes work, remaining usage below 20%, or twenty
further verified conversions, whichever comes first. Verified evidence is retained in this handover.
Details: [handover](docs/dev/slices/native-date-comparisons-2026-10-19.md).

2026-10-19 earlier FULL nested-type/score-list checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,661 native (98.01%), 54 fallback (1.99%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections, unchanged.
Three fallback-to-native conversions, zero reverse changes: chapter 11 cases
003a (Application risk score), 003b (Pre-bureau risk category), 003c (Bureau call type).
Fallback decreased from 57 to 54 (5.26% fewer fallback cases). No executable
cases moved into model rejection. Chapter 11 now has five fallback cases.
Generator: 109/109 passed; 1,229 native actions (95.87%), 53 fallback (4.13%)
of 1,282; zero failed/unknown. Semantic suite 70/70 and clean runtime IR 27/27
passed. Semantic/IR suites execute no Spark SQL (denominator zero, rates undefined).
Formatting/install passed. Generator target/installed SHA-256:
EDE4A2392E60A8C9D8F2521401BE5E4AE50692BBC0319A103A9D39ADD3F1F23D.
Semantic analysis SHA-256: 68D10EA209CF6D27B4034BAA78FCC54F4A617D7C60EC1185AED662F5B27FC095.
Runtime IR SHA-256: FB5E0BEC189171EB5DF6EF088BFA38500A00F48B8E95DF3AA5E2664E7DAAC7C4.
Important build finding: the old target/installed IR JAR lacked lowerComponent
although source already contained it. A clean dmn-runtime-ir rebuild fixed that;
javap confirmed the method. Generator hash equality alone is insufficient to
establish dependency freshness. The nested-input integration test guards this gap.
Saved runner target evidence: native-nested-score-full-accounting-2026-10-19.json
and native-nested-score-full-tck-2026-10-19.log. Baseline comparison is against
native-string-boxed-full-accounting-2026-10-19.json.
The 100% native goal and semantic rejection audit remain open. Remaining leading
groups: chapter 11 five; function libraries, conditionals and table variable inputs
three each. Preserve exact arithmetic, temporal precision/offset/duration identity,
shared list typing and lexical binding. Dynamic FEEL library division stays fallback.
Restart full-run cadence here: 45 minutes work, usage remaining below 20%, or
twenty further verified conversions, whichever comes first.
Details: [handover](docs/dev/slices/native-nested-score-lists-2026-10-19.md).

2026-10-19 earlier FULL string/boxed-Java checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,658 native (97.90%), 57 fallback (2.10%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections.
Six verified fallback-to-native conversions, zero reverse changes against the
partial-loop full checkpoint: 0076 boxed_001 and 0085 cases 002, 002_a, 002_b,
002_c and 003. Fallback decreased from 63 to 57 (9.52% fewer fallback cases).
Direct decision-service suite now has zero fallback cases. Original ANY payloads
remain intact; only proven string concatenations consume string-only projections.
Generator: 107/107 passed; 1,216 native actions (95.82%), 53 fallback (4.18%)
of 1,269; zero failed/unknown. Formatting/install passed. Target/installed JAR:
14EDC6A938E474994E1B23CF8B42CCE3A7FDAA63540502771A38B1A2C675925C.
Maven exited zero; post-suite Surefire fork shutdown warning recurred after
all cases passed. Spark/fork cleanup remains a follow-up.
Saved runner target evidence: native-string-boxed-full-accounting-2026-10-19.json
and native-string-boxed-full-tck-2026-10-19.log. Case 0035 passed unchanged timeout.
The 100% native goal and semantic rejection audit remain open. Chapter 11 still
has eight fallbacks, including an opaque input context with untyped Monthly field
and heterogeneous/untyped list expressions. Next work should preserve temporal
precision/offset/duration identity, shared list typing and lexical binding.
Restart full-run cadence here: 45 minutes work, usage remaining below 20%, or
twenty further verified conversions, whichever comes first.
Details: [handover](docs/dev/slices/native-string-boxed-2026-10-19.md).

2026-10-19 earlier FULL partial-loop checkpoint: 3,391/3,391 passed.
Of 2,715 executable cases: 2,652 native (97.68%), 63 fallback (2.32%);
zero failed/unknown routes, compilation/execution errors, unsupported, excluded
or invalid cases. Separately: 676 provisional expected model rejections.
One verified fallback-to-native conversion: 0084 decision_013 (factorial with
partial[-1]); zero reverse conversions against the 2026-10-18 full checkpoint.
Fallback decreased from 64 to 63 (1.56% fewer fallback cases).
Generator: 105/105 passed; 1,202 native actions (96.31%), 46 fallback (3.69%)
of 1,248; zero failed/unknown. Formatting/install passed. Shared numeric literal
arrays now preserve exact fractional widths when combined with integer literals.
Target/installed JAR SHA-256:
3BEC01768954493285B720CA0BCEBCEB63C834C867F0058E22CB17E4D20C029B.
Maven exited zero; the post-suite Surefire fork shutdown warning recurred.
All cases completed; Spark/fork cleanup remains a follow-up.
Saved runner target evidence: native-partial-loop-full-accounting-2026-10-19.json
and native-partial-loop-full-tck-2026-10-19.log.
The 100% native goal and semantic rejection audit remain open. Largest remaining
groups are chapter 11 example (eight) and decision services (five). Direct service
parameters declared ANY require lossless runtime type handling; do not replace
overridable decision inputs with their default expressions. Date-valued loop
results still require lossless typed transport. Retain temporal/list/binding priorities.
Restart full-run cadence here: 45 minutes work, usage remaining below 20%, or
twenty further verified conversions, whichever comes first.
Details: [handover](docs/dev/slices/native-partial-loops-2026-10-19.md).

2026-10-18 earlier FULL flatten/quantifier/literal-JDK checkpoint: 3,391/3,391 passed.
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
Maven exited zero; Surefire reported a post-suite fork shutdown timeout.
All cases completed; investigate Spark/fork cleanup separately.
Saved runner target evidence: native-flatten-quantifier-java-full-accounting-2026-10-18.json
and native-flatten-quantifier-java-full-tck-2026-10-18.log.
Largest remaining groups: chapter 11 example eight, decision services five;
external Java now two. Dynamic function-library arithmetic retains fallback
because Spark double division cannot preserve shared DECIMAL128 semantics.
The 100% native goal and semantic rejection audit remain open. Preserve temporal
precision/offset/duration identity, list validation, and lexical binding priorities.
Restart full-run cadence here: 45 minutes work, usage remaining below 20%, or
twenty further verified fallback conversions, whichever comes first.
Details: [handover](docs/dev/slices/native-flatten-quantifiers-java-2026-10-18.md).

2026-10-16 earlier FULL list/coercion checkpoint: 3,391/3,391 passed.
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
[handover](docs/dev/slices/native-list-typing-coercion-2026-10-16.md).
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
[context/statistics handover](docs/dev/slices/native-context-statistics-2026-10-16.md).
Next: investigate the context-put 004 reverse route and reduce the remaining
107 fallbacks. The 100% native goal and semantic rejection audit remain open.
Restart full-run cadence here: 45 minutes working, usage remaining below 20%,
or 20 further fallback conversions, whichever comes first.

2026-10-15 earlier FULL bounded-loop checkpoint: 3,391/3,391 passed (100%).
Native 2,596 (95.62%), fallback 119 (4.38%) of 2,715 executable cases; zero failed/
unknown or compilation/execution errors. Separately: 676 provisional expected
model rejections. Verified 12 further conversions, zero reverse conversions.
Generator: 91/91 tests; 958 native actions (96.96%), 30 fallback (3.04%) of 988,
zero failed/unknown. Full run triggered by usage remaining at 18%. Evidence:
docs/dev/slices/native-bounded-loops-2026-10-15.md. The 100% goal remains open.

2026-10-15 earlier FULL checkpoint: 3,391/3,391 passed (100%). Native 2,584
(95.17%), fallback 131 (4.83%) of 2,715 executable cases; zero failed/unknown
or compilation/execution errors. Separately: 676 provisional expected model
rejections. Verified 21 fallback-to-native conversions, zero reverse conversions.
0035 #001 also recovered from its previous timeout, so native passes rose by 22;
no timeout increase or proven warmup fix. Generator: 90/90 tests; 945 native
actions (96.92%), 30 fallback (3.08%) of 975, zero failed/unknown. Evidence:
docs/dev/slices/native-literal-list-builtins-2026-10-15.md. The 100% goal remains open.

2026-10-15 continuation: seven verified fallback-to-native conversions in the
focused get-entries suite, zero reverse conversions. Official: 9/9 passed,
9 native (100%), zero fallback, failed or unknown; denominator nine. Full
generator: 89/89 tests, 867 native actions (96.66%), 30 fallback (3.34%),
denominator 897, zero failed/unknown. This is not a new full-suite checkpoint.
Evidence and remaining work: docs/dev/slices/native-get-entries-2026-10-15.md.

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

2026-10-12 latest full Spark SQL checkpoint and updated goal

**User goal: 100% native execution and zero fallback.** This replaces the earlier
95% milestone and near-100% wording. The priority order remains temporal
representation/validation, lists/filters/quantifiers, then loops/lambdas/function
binding. The supplied 65/62/60 sizes are planning inputs, not measured gains.

Full latest optimized generated Spark SQL TCK: **3,391/3,391 passed (100%)**;
**2,512 native (92.52%) and 203 fallback (7.48%) of 2,715 executable cases**.
There are **676 provisional expected model rejections separately**, zero failed
cases, zero execution/compilation errors, and Maven exit 0. The user explicitly
requested this full run as the next run, before the earlier usage threshold.
The installed generator JAR matched the target JAR SHA-256:
D750919BC563239385A3E4C72655A62245400BEF97BE813E8B1CA70DA84741E1.

Compared with native-temporal-context-full-accounting.json: **only 10 actual
fallback-to-native conversions**, zero native-to-fallback regressions. The native
pass count increased by 11 because the previously timed-out 0035 case 001 also
passed natively. Conversions: four 0050 abs cases (011, 011_a, 012, 012_a), 0069
case 029, and 0084 cases decision_002/019/020/021/022. The 203 remaining fallbacks
and the separate rejection semantic audit remain outstanding; the 100% goal is
active and has not been achieved.

Final generator verification: **78/78 JUnit tests passed**, zero failures/errors/
skips, formatting/install passed, Maven exit 0. Measured whole executing
integration set: **691 native SQL actions (98.86%), 8 fallback (1.14%), denominator
699**, zero failed actions or unknown routes. These processing rates are test
SQL-action rates, not TCK case-coverage percentages. Spark query listeners now
report aggregate counts in both executing suites, exclude view-registration
setup commands, drain asynchronous notifications and inspect executed plans for
fallback UDFs. Planning-only tests do not contribute a processing denominator.

Implementation: terminal literal duration abs with actual compiled ANY metadata
uses the existing lossless binary constant representation, preserving nanoseconds
and zero Period/Duration identity; dependent consumers and dynamic/overflow
operands retain guards. Nullable literal context lists unify matching ordered
primitive field layouts while retaining null values and precision bounds.
Different field sets, reordered fields, mixed non-null kinds and incompatible
declared collection coercions remain guarded. Cartesian loops support two through
four independent primitive literal bindings, at most 2,048 combinations, bounded
result syntax and integer arithmetic, exact lexical binding and nested results.
Captured variables, dependent sources, partial accumulation, fractional data,
null items/results and unverified bodies retain guards. Single literal iteration
sources/intervals proven invalid by shared runtime rules emit native NULL, without
timestamp or duration coercion; valid numeric/date ranges retain their existing
paths.

Evidence: runner target/priority-native-full-tck.log,
priority-native-full-accounting.json, priority-native-full-rejections.tsv (676
rows); generator target/priority-final-verified-generator.log and Surefire reports.
The first 78-test run had one invalid compiler fixture (77/78 passed); correcting
the fixture and rerunning the affected test and complete suite resolved it.
Individual earlier run outcomes are retained in prompts/actual-task.md.

The 0035 case 001 passed with the unchanged SELECT 1 warmup and 30-second limit.
Its earlier timeout cause remains unproven; the stage-timing/warmup investigation
in docs/todos/dmn-tck-runner.md is still planned. Remaining measured concentrations
include 18 external Java cases, 17 lambda cases, 14 for-loop cases, 13 instance-of
cases, and 11 cases each in list functions and coercion. Future slices should
address shared blockers, preserve semantics and remeasure actual conversions.
The user requested commit and push of these changes after handover verification; the source baseline is pushed c197b09. Earlier dated
checkpoints below are historical and do not describe the latest source.


2026-10-12 duration abs and generator processing-accounting checkpoint:

The official 0050 run against the installed latest generator passed **17/17**:
**17 native (100%), 0 fallback (0%), denominator 17**, zero failed/execution/
compilation errors or provisional rejections; Maven exit 0. Cases 011, 011_a,
012 and 012_a convert from fallback compared with the 2026-10-11 full snapshot.
This is focused evidence; no new full-suite coverage percentage is claimed.
The earlier duration test declared DURATION, while compiled untyped official
results declare ANY and hit the compatible-duration-result guard. Bounded literal
abs now uses existing lossless terminal binary constants for ANY results. Dynamic
operands, overflow and dependent consumers keep guards. The regression compiles
actual DMN without overriding result metadata and compares to lowered runtime,
including zero Period vs Duration and nanoseconds.

Generator integration tests now count completed SQL actions using Spark's query
execution listener and physical fallback-UDF detection. View-registration setup
commands are excluded, asynchronous events are drained before reporting, failures
and unknown routes are explicit, and zero actions produce N/A rates. A regression
checks the exact 10 native / 1 fallback delta. Both executing integration classes
print SPARK SQL PROCESSING totals; planning-only unit tests do not contribute.
Rates describe test SQL actions, not TCK cases or model-coverage percentages.

| Run / target evidence prefix | Result | Native / fallback processing |
| --- | --- | --- |
| compiled-duration-abs-any-generator | 1 test passed, install/format passed | 10 native (100%), 0 fallback (0%), denominator 10 |
| compiled-duration-abs-any-tck | 17/17 passed | 17 native (100%), 0 fallback (0%), denominator 17 |
| compiled-duration-abs-any-boundaries | 4 tests passed | 74 native (97.37%), 2 fallback (2.63%), denominator 76 |
| compiled-duration-abs-any-full-generator | 76/76 tests passed | Explicitly reported subset: 41 native (95.35%), 2 fallback (4.65%), denominator 43; whole aggregate not collected in this pre-accounting run |
| compiled-duration-abs-accounted-generator | 1 test passed | 10 native (90.91%), 1 fallback (9.09%), denominator 11, zero failed/unknown |
| compiled-duration-abs-accounted-full-generator | 76/76 tests passed; install/format passed | Whole executing integration set: 663 native (98.81%), 8 fallback (1.19%), denominator 671, zero failed/unknown |

Evidence logs are in dmn-generator-sparksql/target except the focused TCK log and
compiled-duration-abs-any-accounting.json in dmn-tck-runner/target. The full
accounted generator aggregate combines 13 native/0 fallback DMN integration actions
and 650 native/8 fallback hybrid integration actions. Last full TCK remains the
historical 2026-10-11 snapshot below. Current five-hour usage remaining is 76%; run
full latest optimized generated Spark SQL TCK and refresh handover at <=20%.
Priority remains temporal representation/validation, lists/filters/quantifiers,
then loops/lambdas/function binding. Remaining calendar ANY-input routes need a
separate lossless input-projection proof; changing them to Spark date/timestamp
casts alone is not verified. The 0035 timeout diagnosis remains outstanding.

2026-10-11 temporal/context full checkpoint (current): **3,390/3,391 passed (99.97%); 2,501 native passes (92.12% of 2,715 executable), 213 fallback passes (7.85%), one failed case with unrecorded route (0.04%); 676 provisional expected model rejections separately; zero execution/compilation errors; Maven exit 1 because of the repeated 30-second 0035 case 001 timeout.** Compared with current-native-fallback-full: **+26 native/-26 fallback/+0.96 percentage points**, with no native-to-fallback regressions. This includes the previously focused terminal empty-list conversion. The 95% milestone requires 79 additional native passes. Implementation stopped when five-hour usage reached 18% remaining (weekly 71%); the requested full latest-generator run and handover followed.

Source changes: verified primitive context-list filters retain field layouts in lexical bindings, including missing fields and quoted keys; unknown/null-incompatible layouts and precision-sensitive values remain guarded. Terminal temporal ANY literals and bounded closed literal arithmetic emit existing lossless FEEL binary constants through native SQL, preserving nanoseconds, offsets, named zones and Duration/Period identity without UDF execution. Arithmetic uses generation-time literal folding through the shared runtime, not dynamic temporal SQL support. Dependent consumers retain fallback. The optimizer now leaves fractional temporal arithmetic in IR because its existing double-second folders can lose precision or produce invalid constants. Scalar-filter regressions were consolidated after discovering overlap with an earlier regression; the previous handover's untested statement was stale.

Final verification: **75/75 generator JUnit tests passed**, zero failures/errors/skips; the affected measured subset executed **95 native comparisons (98.96%) and one fallback comparison (1.04%)**. The whole generator test set has no aggregate execution-route counter. Optimizer tests: **15/15 passed**, native/fallback counts and rates not applicable because no Spark executes. Focused official runs: **0069 35/35 passed, 32 native (91.43%), 3 fallback (8.57%)**; **0074/0093 72/72 passed, 72 native (100%), zero fallback (0%)**. Full run confirms six 0069 conversions (including the earlier empty-list case), two property, two temporal-literal and sixteen arithmetic conversions against the preceding full accounting.

Evidence: generator target/native-temporal-context-final-generator.log and Surefire reports; optimizer target/native-temporal-precision-optimizer.log; runner target/native-context-filter-tck.log, native-context-filter-accounting.json, native-temporal-any-tck.log, native-temporal-any-accounting.json, native-temporal-context-full.log, native-temporal-context-full-accounting.json and native-temporal-context-full-rejections.tsv (676 rows). Detailed individual run outcomes and limits are recorded in prompts/actual-task.md.

Next work, in the user's order: temporal representation/validation (65 supplied planning cases), lists/filters/quantifiers/validation (62 supplied), then loops/lambdas/function binding (60 supplied). Re-read actual fallback accounting before choosing a slice; these supplied sizes are not current disjoint measured populations. Four 0050 abs cases (011, 011_a, 012, 012_a) still route to fallback in this full run, despite the older focused 17-native handover; investigate result metadata and installed-versus-reactor dependency evidence before claiming those conversions. Residual 0069 cases are 029, 032 and 034. The repeated 0035 timeout/warmup stage-timing investigation is planned in docs/todos/dmn-tck-runner.md; its cause remains unproven. The provisional rejection semantic audit remains outstanding. The user requested commit/push after this checkpoint; the changes and handover are prepared for publication. No reset/clean occurred; all previous edits are preserved.

2026-10-11 duration-abs continuation handover (user-requested): focused official **0050** run completed **17/17 passed (100.00%); 17 native (100.00% of 17 executable), 0 fallback (0.00%); 0 failures, 0 execution errors, 0 compilation errors, 0 provisional model rejections; Maven exit 0**. Five cases converted from fallback: 011, 011_a, 012, 012_a and 013. Evidence: dmn-tck-runner/target/native-duration-abs-tck.log and native-duration-abs-accounting.json. This is a focused measurement; no full-suite run has followed this fix. Do not add these five conversions to the previous full checkpoint and present the sum as measured full coverage.

Implementation: native abs over bounded literal durations/constructors normalizes ISO operands and uses SQL regexp_replace to remove component minus signs, retaining nanoseconds and duration subtype. Exact named n binding is supported. Zero year-month duration is represented as P0M, preserving Period identity through decoding. Invalid literal durations and date/time operands produce native null; dynamic duration operands and absolute-value overflow retain guards. The initial regression exposed zero Period formatting as P0D, incorrectly decoding as day-time Duration; corrected before the successful install/TCK run.

Per-run evidence: native-duration-abs-generator.log: **1 test, 1 failure, 0 errors/skips**, native/fallback counts/rates **not measured/not applicable** (generator regression). native-duration-abs-zero-generator.log: **1 test, 0 failures/errors/skips**, **33 SQL/shared-runtime parity comparisons**, native/fallback aggregate counts/rates **not measured/not applicable**; formatting/install exit 0. native-duration-abs-tck.log: **17 native (100.00%), 0 fallback (0.00%)**, all 17 cases passed, no rejections/errors. No failed generator build was used for TCK measurement. The full generator suite has not been rerun after abs; the earlier 69-test result belongs to the preceding temporal predicate source.

Unverified working-tree continuation: SparkSqlCapabilityAnalyzer.nativeFilter now admits primitive scalar literals with boolean selectors; SparkSqlExpressionEmitter.emitFilter wraps those sources with array(...) before filtering. This targets FEEL singleton-list behavior for 0069 cases 012-017. These scalar-filter edits were made after the installed abs build; **no regression or TCK test run has executed against them**, and their native/fallback counts/rates are **unavailable**. Do not claim their conversions or completion. Preserve the edits and add true/false/null selector, local-item binding and result-coercion regressions before install and official 0069 measurement. Validate numeric literal precision and inferred list result types before broadening eligibility.

User continuation scope/order: **Temporal representation/validation (65 supplied), Lists/filters/quantifiers/validation (62 supplied), Loops/lambdas/function binding (60 supplied)**. Those supplied category sizes are planning inputs, not remeasured disjoint counts. Continue bounded temporal representation/validation with precision, offsets and duration identity; shared list result typing/coercion and SQL validation; then bounded loop/lambda lowering with lexical substitution. Read the actual remaining fallback outcomes from the latest measured accounting before selecting cases. Pending loop candidates: bounded independent Cartesian bindings, signed/computed numeric endpoints, empty sources and reusable result typing. Captured variables, partial-result accumulation and dynamic heterogeneous layouts require distinct proofs. The 676 provisional rejection semantic audit and context-consumer regressions remain outstanding.

Reporting requirement: **after every fix, provide every test run's actual native and fallback counts and rates, including fallback rate and executable denominator**. Never substitute historical totals for a new run. Mark routing not measured/not applicable for generator runs, and pending/unavailable for unfinished TCK accounting. The latest full checkpoint below is historical evidence for the previous temporal-predicate source, not a full result for abs or scalar filters. No commit/push/reset/clean occurred; all existing edits are preserved.

2026-10-11 temporal predicate full checkpoint: **3,391/3,391 passed (100.00%); 2,469 native (90.94% of 2,715 executable), 246 fallback (9.06%), 676 provisional expected model rejections separately; 0 failed, 0 execution errors, 0 compilation errors; Maven exit 0**. Delta versus native-list-match-full: **+16 native/-16 fallback/+0.59 percentage points**. Ten 0071 duration-between cases, four 0093 offset/named-zone literal type checks and the 1148/1149 now/today type checks converted. Literal duration-range type checking is regression verified but produced no additional full-suite conversion. The 95% milestone requires **111 additional native passes**; near-100% and the separate rejection audit remain outstanding. This supersedes older current-state metrics below.

Final-source generator verification/install and formatting passed: **69/69 JUnit tests, 0 failures/errors/skips** (10 hybrid unit, nine DMN integration, 50 hybrid integration). Native/fallback accounting is not applicable to this generator-test aggregate. New regressions compare generated native SQL with the shared runtime across **30 duration-between scenarios and 54 temporal type-check scenarios**. Duration between preserves subtype identity, nanoseconds and inclusive bounds for compatible literal durations/constructors; mixed subtypes and dynamic operands remain guarded. Temporal type checks preserve offset/named-zone literal identity without transporting timestamps; zero-argument now/today checks use native current timestamp/date SQL; bounded literal duration-range type checks use existing runtime literal semantics at generation time. This does not establish native support for arbitrary dynamic temporal values.

Evidence: generator target/native-temporal-verified-generator.log and Surefire XML; runner target/native-temporal-full.log, native-temporal-full-accounting.json and native-temporal-full-rejections.tsv (676 rows). The first sandboxed Maven attempt failed with access denied; the approved retry passed. Full TCK retained 4 GB heap, localhost, Spark history 20/20/40 and existing timeouts. Maven exited 0 without the earlier delayed-fork-shutdown warning. No commit/push/reset/clean occurred; existing changes are preserved. Next native candidate remains scalar boolean filters 0069 cases 012-017, requiring singleton-list semantics and parity verification before capability promotion. Rejection semantic audit and context-consumer regressions remain outstanding.

# DMN Spark SQL TCK Status and Handover Guide

2026-10-11 native list-match full checkpoint: **3,391/3,391 passed (100.00%); 2,453 native (90.35% of 2,715 executable), 262 fallback (9.65%), 676 provisional expected model rejections separately; 0 failed, 0 execution errors, 0 compilation errors; Maven exit 0**. Total-suite rates, separately: native 72.34%, fallback 7.73%. Full delta versus native-list-replace-full: **+6 native/-6 fallback/+0.22 percentage points** (two wrong-arity cases from the prior continuation plus four callback cases today). The 95% milestone needs **127 additional native passes**, while near-100% remains the ideal. All 1155 cases now execute natively: repeated official selection **22/22 passed, 22 native (100%), 0 fallback (0%), 0 failed/errors/rejections**, Maven exit 0. This supersedes older current-state and pending-full-run notes below.

Implementation: bounded list-replace callback inference validates exact list/match/newItem named bindings, two distinct parameter slots, compatible parameter/return types and native homogeneous primitive literal lists/replacements. It admits uncaptured parameter references, bounded primitive constants, numeric comparisons, primitive equality and boolean/null conditional results. SQL computes the predicate array once; any null predicate invalidates the whole result. A proven non-boolean callback produces null for a nonempty source but preserves an empty list. Constants use their actual kind even with ANY metadata. Callback bodies are bounded to 16 nesting levels/64 nodes; lists and local frames to 2,048 elements/slots. Numeric data retains bounded integral-value guards. Null items/replacements, captured or unresolved bindings, external callbacks, dynamic data, unsupported expressions, incompatible result coercions and other unverified layouts retain fallback. This is native SQL execution, not evaluation against TCK expected answers. Do not claim that all possible callback programs are now native.

Verified evidence: final-source generator repeat **67/67 JUnit tests passed, 0 failures/errors/skips** (10 hybrid unit, nine DMN integration, 48 hybrid integration). These are regression tests, not TCK cases; native/fallback accounting is not applicable to their aggregate. The new callback regression performs **34 native SQL/shared-runtime parity scenarios**, including exact named bindings, empty sources, true/false/null/non-boolean predicates, mixed null/boolean conditional predicates, ANY metadata and primitive equality. Eligibility guards cover null values, captured references and duplicate named bindings. Final boundary test/install and repeated generator run exited 0. Initial generator run passed its 67 tests but failed the final Spotless check after boundary edits made during that run; reformatted, reran and passed before TCK verification. Generator logs: target/native-list-match-generator.log (formatting failure), native-list-match-final-generator.log (successful intermediate run), native-list-match-boundary-generator.log (final boundary/install), native-list-match-repeat-generator.log (requested final-source repeat). Runner logs/snapshots: target/native-list-match-repeat-tck.log, native-list-match-repeat-focused-accounting.json, native-list-match-full.log, native-list-match-full-accounting.json and native-list-match-full-rejections.tsv (676 rows). Earlier snapshots remain preserved.

Run limitation: after the completed full TCK summary, Surefire printed that it would force the forked JVM to exit after 30 seconds following System.exit(0). Maven subsequently exited 0, and accounting records all 3,391 passes with zero compilation/execution errors. The delayed JVM shutdown cause is unresolved; do not call the log error-free or infer clean resource finalization from the pass count. Full run retained the existing 4 GB heap, localhost, Spark history 20/20/40 and timeouts. No reset/clean/commit/push occurred in this continuation; source/tests and handovers remain uncommitted on feature/graduate-incubating-modules, following pushed 444bdf1.

Next safe action: inspect the delayed fork shutdown/resource finalization separately. For the next native slice, investigate scalar boolean filters **0069 cases 012-017**: shared runtime and official expected results use singleton-list coercion, while the current SQL filter path returns null for a statically scalar source. Correct native scalar wrapping and prove result inference/true-false-null behavior before changing capability routing; do not merely enable the existing wrong SQL. Also investigate the untyped empty-list result and reusable loop typing. These are candidate gains, not measured conversions. The 676-rejection semantic audit and context-consumer quoted-key/missing-versus-null regressions remain outstanding. User-mandated reporting is now explicit in both prompts/actual-task.md and prompts/generall-prompt.md: every TCK result must include passed/native/fallback counts and rates, failures, execution/compilation errors, denominator and separate provisional rejections; missing accounting is unavailable, not zero; focused gains cannot be presented as full-suite totals.


2026-10-10 continuation review at **2% five-hour / 16% weekly remaining**: user continuation override remains recorded. Read-only comparison confirms existing callback SQL uses CASE WHEN predicate THEN replacement ELSE item, which does not preserve the runtime whole-result null on a non-boolean/null callback return. The runtime constructs callback arguments with List.of(item, newItem), so null item/replacement behavior also requires an execution probe before broadening eligibility; this is an observed code-path concern, not a verified failing TCK case. Keep these cases guarded. The wrong-arity promotion remains verified only by the 22-case focused suite: **18 native (81.82%), four fallback (18.18%), zero rejections/failures/errors**. Latest full remains **2,447 native (90.13%), 268 fallback (9.87%), 676 provisional expected rejections separately**; no new full run or source edit in this review. Next implementation should evaluate predicate results once, validate their boolean/null semantics across the whole list, then perform replacement with verified lexical binding. Preserve current uncommitted changes and pushed commit 444bdf1.

2026-10-10 post-push invalid list-match checkpoint: commit **444bdf1** (eight files, including the preceding native promotions and handovers) was pushed successfully to **origin/feature/graduate-incubating-modules**. The commit's Spotless hook passed after putting installed Maven on PATH; the initial commit attempt could not find Maven and created no commit. User explicitly overrode the below-20% implementation stop for this continuation. Usage subsequently reached **4% five-hour / 16% weekly remaining**; this handover records the bounded continuation at the user's 4% handover threshold.

New uncommitted change: positional list-replace calls with non-external callback definitions whose parameter count is not two emit native SQL null, using the shared invalid-expression proof to avoid transporting/evaluating the callback. Eligibility is bounded to literal primitive list elements and a compatible bounded primitive replacement; valid two-parameter callbacks, external callbacks, dynamic data and named match bindings retain their existing guards. Wrong-arity callbacks return null even for an empty source; do not generalize non-boolean callback results to unconditional null because an empty source does not invoke a valid-arity callback.

Verified continuation evidence: affected generator regression passed (one JUnit test, 30 live Spark/shared-runtime invalid-expression scenarios, zero failures/errors/skips); Spotless and generator install exited 0. Official 1155 selection **22/22 passed, 18 native (81.82%), four fallback (18.18%), zero expected rejections/failures/errors**, Maven exit 0. Exactly two cases (017 and 018) converted from fallback. Remaining predicate fallbacks: **009, 013, 019, 020**. Logs: generator target/native-invalid-list-match-generator.log; runner target/native-invalid-list-match-tck.log and native-invalid-list-match-focused-accounting.json. No post-change full suite was run: latest verified full remains **3,391/3,391 passed, 2,447 native (90.13% of 2,715 executable), 268 fallback (9.87%), 676 provisional expected rejections separately**, with zero failures/errors. Do not claim a new full-suite total by adding the focused gain. Full native-list-replace snapshots remain preserved.

Next safe action: preserve the two new source/test edits and this handover. Continue valid two-parameter callback lowering with exact named match bindings, lexical substitution and whole-result null handling for non-boolean/null predicates; check empty lists, null replacement/items and captured slots against the runtime. Run focused generator/TCK checks, then a full suite at the next coherent checkpoint before claiming full coverage gains. The continuation edits are not included in pushed 444bdf1. No reset/clean or additional commit/push occurred after that push. The 676-rejection semantic audit and context consumer regressions remain outstanding.


2026-10-10 positional list-replace native checkpoint: **3,391/3,391 full TCK passes (100.00%); 2,447 native (90.13% of 2,715 executable), 268 fallback (9.87%), 676 provisional expected model rejections separately; zero failures/execution errors/compilation errors; Maven exit 0**. Total-suite rates: native 72.16%, fallback 7.90%. Delta versus native-filter-quantifier-full: **+13 native/-13 fallback/+0.48 percentage points**, all in 1155. Combined since native-context-builtins-full: **+59 native/-59 fallback/+2.17 percentage points**, reducing fallback count **327 to 268 (18.04%)**. The 95% milestone needs **133 additional native passes**. This checkpoint supersedes older current-state/in-progress notes below. Usage is **19% five-hour / 19% weekly remaining**; implementation stopped under the below-20% task rule, with verification/handover completed. All existing source changes are preserved and uncommitted; no commit/push/reset/clean in this continuation.

Implementation: native result-shape inference enables the existing positional list-replace SQL for homogeneous primitive literal lists and compatible literal replacements, including null new items and scalar-to-singleton sources. Position constants retain FEEL truncation semantics; eligibility checks bounded integer conversion and rejects Double conversion that changes truncation. Numeric data is conservatively limited to integral values with at most 15 integer digits; scale/exponent expansion is bounded before conversion, avoiding common-array decimal coercion and Double transport loss. Exact named bindings must be list/position/newItem, with no duplicates or extra names. Declared result types must be compatible; mismatched scalar/list coercions retain fallback. Dynamic positions/data, lambda predicates, heterogeneous layouts and other precision-sensitive values remain guarded. This is native SQL execution, not evaluation against TCK inputs or expected answers.

Evidence: **66 generator tests passed** (10 hybrid unit, nine DMN integration, 47 hybrid integration), then the affected test passed again after final precision/resource eligibility tightening, exercising **21 generated-CTE/shared-runtime parity scenarios** and an unsafe fractional-position guard. Generator install exit 0. Two earlier test attempts errored because the new fixture requested the wrong SQL filename; an initial broad correction matched an older test. Restored that older test, corrected the new fixture to Decision_0.sql, and passed the full generator set before the final bounded rerun. Official 1155 selection: **22/22 passed, 16 native (72.73%), six fallback (27.27%), zero expected rejections/failures/errors**, Maven exit 0. Residual cases are **009, 013, 017, 018, 019, 020**, all function-predicate overloads. Full suite confirmed exactly 13 conversions and no regressions, with unchanged 4 GB heap, Spark history 20/20/40, localhost and timeouts. Evidence: generator target/native-list-replace-generator.log and native-list-replace-final-generator.log (fixture failures), native-list-replace-verified-generator.log (66 passed), native-list-replace-bounded-generator.log (final affected regression passed); runner target/native-list-replace-tck.log, native-list-replace-focused-accounting.json, native-list-replace-full.log, native-list-replace-full-accounting.json and native-list-replace-full-rejections.tsv.

Next safe action when usage permits or explicitly overridden: continue the six list-replace predicate cases. Verify invalid one/three-parameter signatures and non-boolean returns produce native null without materializing functions; verify valid two-parameter boolean predicates lower through lexical substitution and native transform, preserving FEEL null/error behavior rather than blindly using SQL WHEN semantics. Cover null predicate/new-item behavior, named match bindings, captured slots and missing bindings before broadening. Then continue empty/null-compatible list typing and reusable loop/function lowering. Remaining review families: lists/filter/quantifier/validation 68; loops/lambdas/function binding 60; temporal representation/validation 65; contexts/coercion 29; external Java 18; extreme-scale rounding 12; services/dependent models 16 (total 268). These are candidate populations, not promised gains. Pending context-consumer quoted-key/missing-versus-null regressions and the semantic audit of all 676 provisional rejections remain outstanding; do not change the executable denominator without evidence.

2026-10-10 filter/quantifier native checkpoint: **3,391/3,391 full TCK passes (100.00%); 2,434 native (89.65% of 2,715 executable), 281 fallback (10.35%), 676 provisional expected model rejections separately; zero failures/execution errors/compilation errors; Maven exit 0**. Total-suite rates: native 71.78%, fallback 8.29%. Delta versus native-invalid-operations-full: **+12 native/-12 fallback/+0.44 percentage points**. Combined continuation versus native-context-builtins-full: **+46 native/-46 fallback/+1.69 percentage points**, reducing fallback count **327 to 281 (14.07%)**. The 95% milestone needs **146 additional native passes**. This checkpoint supersedes older current-state/in-progress/stopped-implementation notes below. Existing calendar/property/context and invalid-operation changes remain preserved; sources and all five canonical handovers are uncommitted. No commit/push/reset/clean in this continuation.

Implementation: capability routing now admits boolean filters over verified native lists of non-null primitive constants, and one-binding boolean quantifiers over native literal lists. Empty-list quantifiers emit their FEEL identity (some false/every true) without evaluating the predicate or transporting an untyped empty list. Native quantified predicates explicitly coalesce null to false, matching the shared runtime's true-only predicate contract. Dynamic filter sources, numeric index filters, multi-binding quantifiers, captured/unresolved values and heterogeneous/untyped nonempty lists retain guards. This batch broadens native execution, not FEEL results or public contracts.

Evidence: first generator run had **64 passing tests and one test-fixture error**: hand-built filter/quantifier IR omitted its local frame slot. Corrected fixture and narrowed source eligibility; the affected test then passed **20 live Spark/runtime parity scenarios** (true/false/null/comparison predicates, some/every and empty lists), with generator install exit 0. Official selection 0069/0016/0001/0006/0021/1151/1153/1154: **59/59 passed; 32 native (60.38%) and 21 fallback (39.62%) over 53 executable, six provisional expected rejections separately; zero failures/errors**, Maven exit 0. Gains: list filters four, boxed filter two, boxed some three, boxed every three. Full run confirmed exactly 12 conversions, zero regressions, unchanged 4 GB heap/Spark history 20/20/40/local host/timeouts. Evidence: generator target/native-filter-quantifier-generator.log (fixture failure) and native-filter-quantifier-final-generator.log (passed repair); runner target/native-filter-quantifier-tck.log, native-filter-quantifier-focused-accounting.json, native-filter-quantifier-full.log, native-filter-quantifier-full-accounting.json and native-filter-quantifier-full-rejections.tsv. Prior 34-conversion invalid-operation full snapshot remains preserved. Documentation facts passed for 12 modules; the review handover's outside-docs hyperlink initially failed strict MkDocs, was replaced with a plain canonical path, and the corrected strict build passed (native-filter-quantifier-docs.log).

Next safe action: inspect **19 list-replace fallbacks** using actual IR/capability reasons. Its existing position-form SQL suggests verified native result-type inference can unlock a bounded group; lambda overloads also need parameter/boolean-return validation, captured-slot handling and FEEL error/null parity. Check positive/negative/fractional/zero/out-of-range positions, scalar-to-singleton coercion, null list/position/new item and exact named bindings before broadening. Then continue empty/null-compatible list typing and reusable function/loop lowering. Residual review families: lists/filter/quantifier/validation 81; loops/lambdas/function binding 60; temporal representation/validation 65; contexts/coercion 29; external Java 18; extreme-scale rounding 12; services/dependent models 16 (total 281). These are candidate populations, not promised gains. Pending context-consumer quoted-key/missing-versus-null regressions and the 676-case rejection semantic audit remain outstanding; keep provisional rejection accounting separate and do not alter the denominator without evidence.

2026-10-10 invalid-operation native checkpoint: **3,391/3,391 full TCK passes (100.00%); 2,422 native (89.21% of 2,715 executable), 293 fallback (10.79%), 676 provisional expected model rejections separately; zero failures/execution errors/compilation errors**. Total-suite rates: native 71.42%, fallback 8.64%. Delta versus native-context-builtins-full: **+34 native/-34 fallback/+1.25 percentage points**; fallback count reduced by 10.40%. The 95% milestone needs **158 additional native passes**. This checkpoint supersedes older current-state and stopped-implementation notes below. Sources remain uncommitted; no commit/push/reset/clean in this continuation.

Implementation: shared structural validation emits native SQL null for arithmetic with non-external function definitions, invocation of constant non-function targets, invalid positional date signatures/list arguments, and sqrt/exp/log/even/odd/modulo with duration operands. The analyzer uses the same proof and does not traverse operands that SQL does not evaluate. Unknown dynamic targets and external functions keep their existing guards; required input transport guards remain. No TCK inputs or expected outputs are used at generation. Existing calendar/property/context changes are preserved.

Evidence: **64 generator tests passed** (10 hybrid unit, nine DMN integration, 45 hybrid integration), including live Spark/shared-runtime parity for 24 invalid expressions. Focused official selection 0100/0051/0052/0053/0054/0055/0056/1115/1131: **1,255/1,255 passed; 612 native (97.45%), 16 fallback (2.55%) over 628 executable, 627 provisional expected rejections separately; zero failures/errors**. Verified gains: arithmetic 12, numeric-duration arguments 12, constant non-function calls seven, date signatures three. The 16 residual focused fallbacks are valid temporal arithmetic requiring lossless representation. Logs in generator target/native-invalid-operations-generator.log and runner target/native-invalid-operations-tck.log, native-invalid-operations-focused-accounting.json, native-invalid-operations-full.log, native-invalid-operations-full-accounting.json and native-invalid-operations-full-rejections.tsv. Full run retained 4 GB heap, Spark history 20/20/40, localhost, optimized hybrid backend and unchanged timeouts. First local Maven attempt encountered dependency-cache AccessDeniedException; the permitted external-cache retry passed.

Next safe action: reusable empty/null-compatible list typing and boxed filter/quantifier lowering, then list-replace overloads and statically resolved function/loop lowering. Current residual review families: lists/filter/quantifier/validation 93; loops/lambdas/function binding 60; temporal representation/validation 65; contexts/coercion 29; external Java 18; extreme-scale rounding 12; services/dependent models 16 (total 293). These populations are not promised conversion gains. Preserve pending quoted-key/missing-versus-null context-consumer regressions before widening context layouts. The 676-case rejection semantic audit remains outstanding; do not change the denominator without evidence.

## 2026-10-10 feasibility review of all 327 fallbacks

Read-only review; no new source changes or TCK execution. Baseline remains **2,388 native (87.96%), 327 fallback (12.04%) over 2,715 executable cases**, with **676 provisional expected rejections separately**, all 3,391 cases passing. Inventory extracted from `target/native-context-builtins-full-accounting.json` into local generated `target/fallback-feasibility-inventory.tsv` in dmn-tck-runner: exactly 327 rows with suite, case, selected results, selected decision FEEL text, description and review family. These are exclusive review families, not executed per-case capability diagnostics; dependencies can introduce additional blockers. The ledger records FALLBACK without detailed analyzer reasons.

| Review family | Cases | Feasibility and next bounded proof |
| --- | ---: | --- |
| Lists, filters, quantifiers and validation | 93 | Strong native candidates. Infer empty/null-compatible element types; lower boxed filters/some/every; implement both list-replace overloads. Heterogeneous and nested lists need lossless tagged values or verified structural specialization. |
| Loops, lambdas and FEEL function binding | 67 | Feasible for statically resolved functions using inlining, lexical substitution and higher-order SQL. Multi-iterator loops need ordered Cartesian expansion; partial-result loops need bounded aggregate state. Returned/captured functions require closure lowering rather than transporting Java function objects. |
| Contexts and declared-type coercion | 29 | Propagate structural layouts, retain empty/quoted keys and order, distinguish absent/null fields, implement dynamic lookup and declared coercion. Includes 14 residual context-family cases plus get-value, context membership and coercion suites. |
| Temporal representation and validation | 80 | Includes 16 temporal arithmetic cases and 12 invalid numeric-function calls on durations. Invalid types can often become verified SQL null; valid values need lossless dates/times, offsets, zones and duration-kind identity through input/output codecs. Current codec classifies temporal types as non-native; this is a toolkit boundary, not proof of Spark impossibility. |
| External Java contracts | 18 | Finite TCK signatures may be translated through an explicit signature whitelist, preserving Java conversion/float/error semantics. Three invalid signatures are null candidates. Arbitrary external Java behavior has no general pure-SQL translation; no claim that all 18 are impossible. |
| Invalid function operands in arithmetic | 12 | Strong candidates for structural operand validation and typed SQL null, without materializing function values. Must preserve FEEL behavior and avoid evaluating invalid SQL branches. |
| Extreme-scale rounding | 12 | Eight out-of-range scale cases are null candidates after semantic validation. Four scale-6176 cases require checking numeric value versus observable scale and result decoding. Spark DECIMAL precision 38 is a real limit of that representation, not proof that alternative SQL encodings cannot work. |
| Decision services and larger dependent models | 16 | Requires dependency-specific diagnosis: service parameter/default/no-logic behavior, function references and type propagation. Shared changes may unlock these; suite names alone do not establish the blocker. |
| Total | **327** | Candidate populations are not promised conversion gains. |

Recommended order: (1) operand/arity/null validation, empty-list typing and boxed filter/quantifier lowering; (2) shared list/function/loop lowering; (3) context layouts and coercion; (4) lossless temporal transport; (5) Java signature translations and extreme-scale exceptions. Prioritize reusable lowering over additional one-off literal folds; compile-time evaluation must depend only on verified model constants, never TCK inputs or expected answers. Keep zero failures/errors and full-suite native/fallback accounting after each verified batch. **192 additional native passes** reach the 95% milestone (2,580 native, at most 135 fallback); full removal remains unproven.

Spark 4.2 primary references: [collection higher-order functions](https://spark.apache.org/docs/4.2.0/api/sql/collection-functions/), [array/map/struct type contracts](https://spark.apache.org/docs/4.2.0/sql-ref-datatypes.html), [DECIMAL precision limits](https://spark.apache.org/docs/4.2.0/sql-ref-ansi-compliance.html), [VARIANT functions](https://spark.apache.org/docs/4.2.0/api/sql/variant-functions/). VARIANT is a possible representation experiment, not a proven lossless FEEL transport replacement. Feasibility is based on ledger/model/source review, not new execution proofs. The rejection semantic audit remains outstanding. Usage at review: **0% five-hour / 31% weekly remaining**; implementation remains stopped and the working tree preserved.

2026-10-10 native context batch checkpoint: **3,391/3,391 full TCK passes (100.00%); 2,388 native (87.96% of 2,715 executable), 327 fallback (12.04%), 676 provisional expected model rejections separately; zero failures/execution errors/compilation errors; Maven exit 0**. Total-suite rates separately: native 70.42%, fallback 9.64%. Delta from native-literal-properties-full: **+32 native/-32 fallback/+1.18 percentage points** (17 context construction, 14 context merge, one coercion case). Combined today versus pushed 603f9bb: **+64 native/-64 fallback**. The minimum 95% milestone needs **192 additional native passes**. Three generator source/test files remain changed, together with all five canonical handovers; preserve the existing calendar/property changes. Sources are uncommitted. Implementation stopped under the task usage rule at **13% five-hour / 33% weekly remaining**; only verification/handover continued. No commit/push/reset/clean in this continuation. This record supersedes older current-state and in-progress notes below.

Evidence: **63 generator tests passed**, then the three affected context integration tests passed after final decimal/resource guard tightening. Official context family (0081/1145/1146/1147): **71/71 passed, 57 native (80.28%), 14 fallback (19.72%), zero expected rejections/failures/errors**, Maven exit 0. Breakdown: get-entries 2 native/7 fallback; context construction 17 native/1 fallback; context put 24 native/6 fallback; context merge 14 native/0 fallback. Final full run used unchanged 4 GB heap, Spark history 20/20/40, localhost and timeouts. Preserved evidence: generator target/native-context-builtins-final-generator.log and native-context-builtins-boundary-generator.log; runner target/native-context-builtins-tck.log, native-context-builtins-focused-accounting.json, native-context-builtins-full.log, native-context-builtins-full-accounting.json and native-context-builtins-full-rejections.tsv. First generator attempt stopped at test compilation because two fixtures referenced a nonexistent BOOLEAN constant; corrected to RuntimeType.scalar(BOOLEAN), and all subsequent checks passed. No context TCK failures or errors occurred. Documentation validation passed: 12 reactor modules verified, strict MkDocs build exit 0 (native-context-builtins-docs.log), and git diff --check clean.

Implementation: validated literal context construction and merge are evaluated through the shared FEEL runtime contract during generation, then emitted as native SQL literals/structs. Constructor duplicate keys produce null; merge keeps original key order with later values overwriting earlier ones, including heterogeneous and nested values. Named arguments bind only exact entries/contexts signatures; invalid arity or bindings emit null. Static context equality also uses the runtime contract, avoiding Spark map equality/type-coercion errors for empty/heterogeneous contexts. Reconstructed context entries are unbound, preserving outer lexical bindings. Limits: at most 32 nesting levels, 2,048 literal nodes/local slots, exact decimal SQL literals within 38 digits including original scale, no case-folded key collisions, and homogeneous output list schemas. Dynamic inputs, lexical references, temporal values requiring lossless transport and unsupported result layouts retain fallback. Extreme exponents are rejected before expanding decimal text, avoiding overflow/allocation hazards. This is native execution of compile-time validated literals; dynamic context construction/merge is not claimed.

Next safe action: continue the approved context batch by examining the **14 residual targeted fallbacks**: get-entries seven, context put six and context construction case 010 (empty string key). Empty-key promotion requires preserving a named field rather than interpreting the IR's empty context-entry name as a boxed result. Get-entries needs verified element/value layouts and heterogeneous-list semantics; context put needs source/global/local-reference layout propagation and its get-entries consumer. Add execution regressions for context-function consumers, including quoted/backtick keys and missing versus present-null fields, before broadening inferred native layouts. Static root-result proof does not establish every consumer path; inspect the existing field-name sanitizer/projection behavior. Then proceed to the approved list/loop/lambda batch (74 current candidate fallbacks) and arithmetic (28). These populations are not promised gains. The 676-case rejection semantic audit remains outstanding; keep provisional rejection accounting separate and do not change the denominator without evidence. The earlier acceleration proposal is now approved by the user's continuation request; the first literal context slice is complete, and the remaining batch work is pending renewed usage capacity.

2026-10-10 calendar/property checkpoint: **3,391/3,391 full TCK passes (100.00%); 2,356 native (86.78% of 2,715 executable), 359 fallback (13.22%), 676 provisional expected model rejections separately; zero failures/execution errors/compilation errors; Maven exit 0**. Total-suite rates separately: native 69.48%, fallback 10.59%. Delta against pushed **603f9bb**: **+32 native/-32 fallback/+1.18 percentage points** (11 calendar-duration and 21 property cases). The 95% milestone needs **224 additional native passes**. Three generator source/test files are changed: SparkSqlExpressionEmitter, SparkSqlCapabilityAnalyzer and SparkSqlHybridIntegrationTest. Current source/test changes and the earlier post-push handover refresh remain uncommitted; no commit/push/reset/clean in this continuation. This record supersedes older current-state notes below. Always mention fallback counts and percentages in every native coverage/TCK results report, including zero fallback, with expected rejections separate.

Verified evidence: **58 generator tests passed** after fixing calendar zero-month encoding; **60 generator tests passed** with literal-property support, then the two affected property integration tests passed after the final result-type/null guards. Calendar official selection: **36/36 passed, 36 native (100%), zero fallback/rejections/errors**. First property/calendar selection: **89/89 passed, 85 native (95.51%), four fallback (4.49%)**. Final selection: **89/89 passed, 87 native (97.75%), two fallback (2.25%), zero rejections/failures/errors**; properties 51 native/2 fallback, calendar 36 native/0 fallback. Intermediate full native-static-calendar-full: **2,335 native (86.00%), 380 fallback (14.00%), 676 provisional rejections**, all 3,391 passed. Final full native-literal-properties-full: **2,356 native (86.78%), 359 fallback (13.22%), 676 provisional rejections**, all 3,391 passed. Both full Maven runs exited 0 using unchanged 4 GB heap, Spark histories 20/20/40, localhost and case timeouts. Logs and preserved accounting/rejection snapshots are under dmn-tck-runner/target with the native-static-calendar and native-literal-properties prefixes; generator logs are native-static-calendar-final-generator.log, native-literal-properties-generator.log and native-literal-properties-final-generator.log.

Native changes and limits: validated literal calendar operands use the runtime's local-date projection and Period.between whole-month result; historical dates, nanoseconds, offsets and named zones avoid Spark timestamp precision/timezone conversion. Zero months emits P0M, preserving year/month subtype. Invalid literal date constructors and list operands return native null without evaluating unused operands. Verified literal temporal/duration properties are evaluated through the shared runtime contract during generation and emitted as exact native SQL values. Dynamic temporal inputs retain fallback. Non-null duration-valued properties require compatible declared result types; the two remaining property fallbacks, dateTime_008 and time_004, have ANY result metadata and need structural result inference before native text decoding can preserve duration identity. Failed first calendar generator run: 58 tests, zero assertion failures, two decoding errors because Java's zero Period text P0D was interpreted as day/time duration; corrected P0M passed the final tests and both complete TCK runs. Rejection semantic classification remains outstanding; do not change the provisional denominator merely to improve coverage. The acceleration proposal in TCK_SPARKSQL_STATUS.md and prompts/actual-task.md is for user review; no new batch implementation has started. Documentation fact checks, strict MkDocs build and git diff --check passed (native-literal-properties-docs.log).

2026-10-10 acceleration proposal for review: prioritize reusable native capabilities across case groups. First create an evidence inventory in ignored target artifacts, mapping each of the 359 fallback cases to actual capability reasons, inferred IR types, a feasible SQL route and its blocker. Do not infer root causes from suite names alone. Proposed batch 1: structural context layouts/result types, then static-key context construction, merge and get-entries; candidate population **45 fallback cases** (1145:18, 1147:14, 0081:7, 1146:6), preserving heterogeneous values, duplicate-key order, missing/null fields and lexical bindings. Also investigate the two ANY-typed duration property results. Proposed batch 2: native list operations, nested loops and lambda invocation using verified Spark higher-order functions; candidate population **74 fallback cases** (0069:19, 0084:19, 1155:19, 0092:17), with explicit guards for captured variables, accumulator semantics, mixed types and resource bounds. Proposed batch 3: the **28 arithmetic fallbacks**, classified by exact numeric or temporal representation and coercion requirements. These **147 cases are candidate populations, not promised native gains**; even full conversion would leave 77 additional native passes needed for the minimum 95% milestone. Review each root cause before expanding scope, and rank subsequent groups from measured evidence. Keep external Java and rejection-semantic audit as separate investigations; neither is proved an unavoidable Spark limitation.

Proposed verification cadence: add runtime-versus-native execution regressions per semantic capability, run focused affected TCK selections after each coherent change, and run one full suite at each roughly 45-minute checkpoint after focused checks pass. Preserve each accounting snapshot and report native/fallback counts and rates together; retain all null, precision, ordering, type identity and cancellation/resource guards. Avoid repeating unchanged full generator suites or full TCK runs without a new concern, while completing required regression checks. Measure native conversions per cycle and verification time rather than promising a speed multiplier. Keep one implementation owner for the shared emitter/analyzer; this proposal does not authorize spawning agents or changing dependencies/architecture. User review is pending.

2026-10-09 temporal/context native checkpoint: **3,391/3,391 full TCK passes (100.00%); 2,324 native (85.60% of 2,715 executable), 391 fallback (14.40%), 676 provisional expected model rejections separately; zero failures/execution errors/compilation errors; Maven exit 0**. Total-suite rates, separately: native 68.53%, fallback 11.53%. Compared with pushed commit **396def3** (2,230 native/485 fallback): **+94 native/-94 fallback/+3.46 percentage points**. The 95% milestone still requires **256 additional native passes**. Implementation, tests and checkpoint documents were committed and pushed as **603f9bb** (Promote temporal results and context updates to native Spark SQL) on **feature/graduate-incubating-modules**. The repository Spotless commit hook passed; HEAD and upstream were synchronized and the working tree was clean after the push. This post-push handover refresh is documentation-only and remains uncommitted. Documentation validation completed: tools/verify_documentation.py verified 12 reactor modules; strict MkDocs build exited 0 (native-calendar-context-docs.log); git diff --check passed. Implementation stopped under the below-20% usage rule; remaining usage at the verification checkpoint was **8% five-hour / 48% weekly**. This checkpoint supersedes earlier totals and in-progress notes below. Resume implementation from pushed 603f9bb; preserve the documentation-only refresh. No reset or clean was performed.

Final generator build/install: **56 tests passed, zero failures/errors/skips**, in `dmn-generator-sparksql/target/native-calendar-context-final-generator.log`. Final affected official selection (1115/1116/1117/1121/1146): **289/289 passed, 269 native, 20 fallback, zero failures/errors/rejections**, Maven exit 0; breakdown: date 49/52 native, time 83/83, date-time 88/88, years/months duration 25/36, context put 24/30. Full verification used the unchanged 4 GB heap, Spark history 20/20/40, localhost and case timeouts. Evidence preserved in `dmn-tck-runner/target/native-calendar-context-final-tck.log`, `native-calendar-context-focused-accounting.json`, `native-calendar-context-full.log`, `native-calendar-context-full-accounting.json` and `native-calendar-context-full-rejections.tsv`. The earlier complete `native-temporal-text-full` checkpoint passed 3,391 cases with 2,286 native/429 fallback; the final checkpoint adds another 38 native passes. Final promotion groups versus 396def3: date +4, time +29, date-time +30, years/months duration +7, context put +24.

Next safe action: select a bounded measured fallback group, inspect the actual IR/runtime contract, implement native SQL with semantic regressions, then focused and full TCK accounting before reporting a new delta. Candidates: arithmetic 28, properties 23, list replace 19, for loops 19, list operations 19, external Java 18, context function 18, lambda 17, context merge 14, instance-of 13, coercion 12, years/months duration 11. Rejection classification remains outstanding: the 676-case expectation audit verifies expected-error markers but does not prove every compiler rejection is semantically correct. Keep the denominator provisional until classification evidence supports a change. Remaining fallbacks are implementation/verification gaps, not demonstrated Spark impossibilities. Detailed implementation, guards, failed probes and continuation actions follow in TCK_SPARKSQL_STATUS.md.

Implementation and limits: DmnSparkSqlGenerator, SparkSqlCapabilityAnalyzer, SparkSqlExpressionEmitter, SparkSqlFeelValueCodec and SparkSqlHybridIntegrationTest are the five changed source/test files. Validated constant temporal decision results use exact ISO text with declared DATE/TIME/DATE_TIME decoding, preserving nanoseconds, offsets, named zones and historical/extreme years across UTC and Europe/Zurich sessions. Existing bounded DATE and whole-second local temporal paths remain intact. Promotion applies only at the requested result boundary; temporal dependencies consumed by other expressions and unverified temporal inputs retain their guards. Invalid list/empty-arity temporal constructors and proven-null calendar-duration calls emit typed SQL null without evaluating unused operands; a shared unsupported operand used elsewhere remains inspected.

Context put now reconstructs heterogeneous SQL named_struct values for constant keys and verified field layouts, preserving field order, overwrite/add behavior and null source semantics. Named arguments are normalized and invalid arguments yield null. Literal nested key paths are bounded to 32 keys; static context reconstruction preserves missing versus null nested parents, rejects scalar parents, and does not shadow outer replacement bindings. Native capability checks retain fallback for dynamic keys, case-folded field collisions, ambiguous layouts, declared-result coercion and unverified dynamic nested shapes. No broad map_concat promotion was made. Remaining context-put cases are 002 (get-entries consumer), 014/015 and nested010/011/012 (source/local-reference layout inference and nested shape propagation). Remaining date cases are 002_9b9e6085ce, 010_6d4d58d23a and 049_a1644ce710. Years/months duration has 11 residual cases: investigate static calendar projection using the runtime's local-date and Period.between rules, invalid-list handling, exact arity and named binding before broadening eligibility. Do not truncate precision or treat missing struct fields as present null fields.

Failed probes are retained: native-temporal-text-focused.log ran 48 tests with one failure caused by an invalid manually constructed positive extreme DATE_TIME fixture with a leading plus; corrected runtime-compatible fixture and explicit invalid-value guards passed subsequent checks. native-context-path-focused.log ran four tests with one error caused by a manual local-slot/frame mismatch; corrected fixtures use unbound named entries, and rebuilt context nodes preserve outer bindings. Final 56-test execution and full TCK verify the corrected sources. Intermediate official checks also passed: temporal-text 171/171 (168 native/3 fallback), temporal-complete 171/171 native, first context-put 201/201 (186 native/15 fallback), temporal/context 253/253 (244 native/9 fallback). These are focused evidence, not substitutes for final full accounting.
2026-10-09 checkpoint: **3,391/3,391 passed; 2,230 native (82.14% executable), 485 fallback (17.86%), 676 provisional expected rejections separately; zero failures/execution errors/compilation errors; Maven exit 0**. Executable denominator remains 2,715. Compared with pushed commit 8db898d (2,082 native/633 fallback): **+148 native, -148 fallback, +5.45 percentage points**; reaching 95% requires 350 more native passes.

The first full checkpoint (`native-duration-type-full`) passed all 3,391 with **2,173 native/542 fallback (80.04%)**, gaining 85 arithmetic, four arithmetic-negation and two at-literal cases. Root cause: constant folding proved a duration value but preserved an ANY decision result type, causing lossless-transport fallback. ConstantFoldingPass now refines only unknown duration-constant decision results to DURATION, preserving concrete declarations. This is native execution of folded constants, not general dynamic duration arithmetic. A separate bounded SQL total-month path handles typed literal period addition/subtraction and numeric scaling, with whole-month truncation, zero-divisor null and finite/period-range guards. Membership literal numbers now compare exactly using BigDecimal during generation, avoiding Spark common-decimal truncation; null candidates propagate null. Adversarial tests cover 38/39-digit literals, 1E-50, named/bare null and list/range behavior.

The final full checkpoint (`native-temporal-for-full`) adds **57 native** versus that checkpoint: 22 duration instance-of, 25 is(), and 10 for-loop cases (two 0033, five 0084, two 0093, one 1152). Literal duration subtype checks preserve year/month versus day/time identity and emit SQL predicates; dynamic temporal inputs keep their guard. Constant is() uses the runtime identity rules during generation to preserve nanoseconds, offsets and named zones. A regression exposed shared-runtime identity incorrectly equating local times one nanosecond apart; DmnRuntime.isValues now compares LocalTime and LocalDateTime with full precision. Partial named value1/value2 arguments bind the missing operand to null; malformed arity still yields null. Bounded single-iterator homogeneous-list loops use transform on dynamic rows; constant integral ranges use sequence with exact BIGINT casts, int-range endpoints and at most 10,000 elements. Nested loops, accumulators, fractional/unbounded ranges and unverified temporal collections retain fallback. Instance-of now has 13 fallback cases (10 function and three list cases); is() has none. Remaining largest groups: context put 30, date-time 30, time 29, arithmetic 28, properties 23, list replace 19, for loops 0084 19, list operations 19. These are implementation/verification gaps unless separately proved otherwise.

Validation: **14 optimizer tests, eight runtime tests, two Java generator tests and 46 final generator tests (10 hybrid unit, nine DMN integration, 27 hybrid integration) passed**, zero failures/errors/skips. The first arithmetic/membership selection passed 1,415 but promoted none; the compiled-DMN regression exposed the missing result type, and the corrected selection passed 1,415 with 759 native/30 fallback/626 provisional rejections. Final predicate selection passed 192 with 179 native/13 fallback. Combined loop/predicate selection passed 220 with 186 native/34 fallback and no rejections. Failed probes are preserved: temporal identity nanosecond mismatch (`native-temporal-identity-probe.log`), first temporal predicate selection with two partial-named-argument failures (`native-temporal-predicate-tck.log`, `native-temporal-predicate-first-accounting.json`), and the first loop assertion scale mismatch (`native-for-first-focused.log`); all affected checks passed after correction. Both full runs used 4 GB heap, Spark history 20/20/40, localhost and unchanged timeouts.

Evidence: generator target logs `native-duration-type-focused.log`, `native-duration-arithmetic-final-focused.log`, `native-temporal-predicate-final-focused.log`, `native-temporal-predicate-bound-focused.log`, `native-for-focused.log`, `native-temporal-for-final-generator.log`; Java generator `target/native-duration-type-regression.log`; runner `target/native-duration-type-tck.log`, `native-duration-type-focused-accounting.json`, `native-duration-type-full.log`, `native-duration-type-full-accounting.json`, `native-duration-type-full-rejections.tsv`, `native-temporal-predicate-final-tck.log`, `native-temporal-predicate-final-accounting.json`, `native-temporal-for-tck.log`, `native-temporal-for-focused-accounting.json`, `native-temporal-for-full.log`, `native-temporal-for-full-accounting.json`, `native-temporal-for-full-rejections.tsv`.

Rejection audit first stage: `target/expected-rejection-expectation-audit.tsv` checks all 676 ledger rows against the original TCK XML; **every selected output is marked errorResult=true**. Diagnostics group as 635 INVALID_OPERAND_TYPES, 18 DECISION_TYPE_MISMATCH, 10 INVALID_OPERAND_TYPE, three INCOMPATIBLE_BRANCH_TYPES, three INVALID_BETWEEN_OPERAND, and seven one-case groups. This verifies expectation accounting, not inherently invalid-model classification or impossibility of native null/error execution. The next audit must compare each group with its DMN expression and applicable FEEL requirement, distinguishing genuine model invalidity from conservative frontend rejection and implementation gaps. Do not alter the denominator from error flags alone.

Next safe action: inspect the preserved diff; continue with measured remaining temporal constructors, context updates and property access, preserving exact fractions/zones and declared-type decoding. Run focused regressions before the next full checkpoint. Commit 8db898d remains pushed; all changes since it are preserved uncommitted. Documentation fact verification, strict MkDocs build and diff whitespace checks passed. Usage remaining at handover: 15% five-hour/63% weekly. No commit/push/reset/clean in this continuation. This checkpoint supersedes prior handovers below.

2026-10-08 4%-remaining handover after explicit usage-rule override: commit 8db898d is already pushed; the new continuation is uncommitted. Current changes: literal numeric membership comparisons fold exactly with BigDecimal into SQL boolean predicates, avoiding Spark's common-decimal precision truncation; null candidates propagate null consistently. Added adversarial Spark execution tests for 38/39-digit numbers, 1E-50, unequal high-scale decimals, bare-null RHS and null lists, checked against the runtime contract. Year/month-duration arithmetic now has a bounded native SQL path for literal period addition/subtraction and multiplication/division by numeric literals: exact total-month inputs, runtime-compatible Double scaling, truncation to whole months, ISO result formatting, zero-divisor null and finite/period-range guards. Capability promotion is limited to validated duration decision results; dynamic numeric inputs and unsupported duration arithmetic retain existing guards. Generator build/install succeeded; 41 generator tests passed (10 hybrid unit, nine DMN integration, 22 hybrid integration), zero failures/errors/skips. Evidence: native-membership-boundary-focused.log and native-month-arithmetic-focused.log. Documentation fact checks and diff whitespace checks passed. No post-change official focused or full TCK run has started; do not claim a native-coverage delta for these changes. Last verified full remains native-membership-full: 3,391 passed, 2,082 native (76.69% of 2,715 executable), 633 fallback, 676 provisional expected rejections separately, zero failures/execution errors/compilation errors. Usage at requested handover threshold: 4% five-hour/77% weekly remaining. Next safe action: run OfficialTckSuiteTest with optimized Spark SQL hybrid, tck.requireFull=false and tck.filter=0100,0072; inspect native arithmetic promotion and all null cases, fix any conformance differences, then run the complete 3,391-case suite with 4 GB heap, Spark history 20/20/40, localhost and unchanged timeouts. Preserve accounting/rejection snapshots before another TCK run. Use C:/10-tools/apache-maven-3.9.12/bin/mvn.cmd and C:/Users/lik/.m2/repository, SPARK_LOCAL_HOSTNAME=localhost. Update measured native/fallback counts only after that full checkpoint. The rejection audit remains outstanding. Preserve these sources; no new commit/push/reset/clean in this continuation.

2026-10-08 native membership checkpoint: latest full optimized hybrid Spark SQL TCK **3,391/3,391 passed (100.00%); 2,082 native (76.69% of 2,715 executable); 633 fallback (23.31% executable); 676 provisional expected model rejections separately; 0 failures/execution errors/compilation errors; Maven exit 0**. Delta versus the preceding calendar checkpoint: **+91 native/-91 fallback/+3.35 percentage points**. Today's total delta versus native-time-result-full: **+173 native/-173 fallback/+6.37 percentage points**. Official membership selection **0072: 327/327 passed, 325 native, two fallback, zero failures/errors/rejections**; its previous 93 fallbacks are reduced to two context-equality cases (context_001, context_001_a). Literal duration membership compares year/month durations as exact total months and day/time durations as DECIMAL(38,9) seconds, preserving nanoseconds, negative values and extreme durations. Mixed scalar lists emit type-aware SQL predicates instead of coercing every item to one Spark array type. Range endpoints retain open/closed behavior; null candidates or explicit null endpoints yield null for intervals, while null remains an ordinary item in literal lists. Dynamic STRING/BOOLEAN operands are supported; dynamic NUMBER operands remain outside this new promotion path because their Double transport requires a separate FEEL decimal precision solution. Unsupported nested lists and context comparisons retain fallback, and a shared list used as a decision result still receives normal capability inspection. **21 hybrid integration tests passed**, then the two affected membership integration tests passed after null propagation fixes. The first official membership attempt had five null-interval failures; all five passed in the corrected 327-case run and subsequent full suite. Evidence: generator `target/native-membership-focused.log`, `native-membership-null-focused.log`; runner `target/native-membership-tck.log`, `native-membership-first-accounting.json` (failed attempt), `native-membership-final-tck.log`, `native-membership-final-accounting.json`, `native-membership-full.log`, `native-membership-full-accounting.json`, `native-membership-full-rejections.tsv`. Full run used 4 GB heap, Spark history 20/20/40, localhost and unchanged timeouts. **Next safe action before check-in:** add adversarial decimal membership tests near Spark's 38-digit boundary, including comparisons whose common decimal type exceeds 38 digits, and tighten eligibility if required; also verify bare-null right-hand operands separately from lists and ranges. The full TCK proves the recorded inventory, not all such boundary inputs. Then address native duration arithmetic (113 arithmetic fallbacks), context structural equality and the six known-null/arity year/month-duration cases in measured batches; run focused tests first and one full TCK per checkpoint. Remaining fallbacks are implementation/verification gaps unless separately proved platform limits. The audit of all 676 provisional expected rejections is outstanding; do not change the executable denominator without verified classification. The 95% milestone needs **498 additional native passes**. Latest usage: **18% five-hour/79% weekly remaining**. Implementation stopped under the below-20% task rule after the successful full checkpoint; documentation facts, strict MkDocs build and diff whitespace checks passed. Preserve all uncommitted changes; no commit/push/reset/clean in this continuation. This checkpoint supersedes earlier metrics below.

2026-10-08 native duration/calendar checkpoint: latest full optimized hybrid Spark SQL TCK **3,391/3,391 passed (100.00%); 1,991 native (73.33% of 2,715 executable); 724 fallback (26.67% executable); 676 provisional expected rejections separately; 0 failures/execution errors/compilation errors; Maven exit 0**. Today's total delta from native-time-result-full: **+82 native/-82 fallback/+3.02 percentage points**. First verified full checkpoint native-constant-duration-full: **1,976 native/739 fallback**, +67 (38 duration constructors, 26 folded-null date cases, three year/month-duration cases). Validated constant duration results use exact ISO text and declared-type decoding, preserving nanoseconds, negative values and extreme ranges; global duration inputs and dynamic arithmetic remain lossless fallback. Folded null results of other declared types no longer require binary transport. Latest calendar slice adds **15 native year/month-duration cases** (+0.55 percentage points versus that checkpoint): SQL computes whole calendar months with the Period.between day-of-month adjustment, formats an ISO period, ignores time-of-day appropriately, and validates/reorders named from/to arguments. Bounded local DATE/DATE_TIME operands are supported, including mixed DATE/DATE_TIME for this function only; general mixed temporal comparisons retain their guard. **38 generator tests passed before the final mixed-operand extension; all 19 hybrid integration tests passed after it**, covering month ends, leap years, negative direction, named argument order and mixed local operands. Official 1121 selection: **36/36 passed; 18 native, 18 fallback; 0 failures/errors/rejections**. Remaining 18: six null/arity cases (002â€“007), three historical cases (017â€“019), five zoned cases (020â€“024), two fractional cases (025â€“026), two invalid constructor/list cases (027,030). These are implementation/verification gaps, not proved native SQL impossibilities. First affected selection: **1,288/1,288 passed; 436 native, 215 fallback, 637 provisional rejections**; 37 generator tests passed at that checkpoint. Evidence: generator `target/native-constant-duration-focused.log`, `native-year-month-focused.log`, `native-year-month-mixed-focused.log`; runner `target/native-constant-duration-tck.log`, `native-constant-duration-full.log`, `native-constant-duration-full-accounting.json`, `native-constant-duration-full-rejections.tsv`, `native-year-month-final-tck.log`, `native-year-month-final-accounting.json`, `native-year-month-full.log`, `native-year-month-full-accounting.json`, `native-year-month-full-rejections.tsv`. Both full runs used 4 GB heap, Spark history 20/20/40, localhost and unchanged timeouts. Largest remaining fallback groups: arithmetic 113, membership 93, instance-of 35, date-time 30, context put 30, time 29, is() 25, for loops 24. **Next safe action:** handle the six known-null/arity year/month-duration calls without evaluating unused arguments; separately verify local-calendar projection for historical/zoned/fractional operands before broadening the bounds. Then address measured membership/dynamic-duration gaps with focused execution tests and full accounting. The rejection audit remains outstanding; investigate all 676 provisional cases separately and recompute denominators only from verified classifications. The 95% milestone still requires **589 additional native passes**. Latest usage: **49% five-hour/84% weekly remaining**. Preserve all uncommitted changes; no commit/push/reset/clean performed in this continuation. This checkpoint supersedes earlier metrics below.

2026-10-07 end-of-day native TIME/result-null checkpoint: full optimized hybrid Spark SQL TCK **3,391/3,391 passed (100.00%); 1,909 native (70.31% of 2,715 executable); 806 fallback (29.69% executable); 676 provisional expected rejections separately; 0 failures/execution errors/compilation errors; Maven exit 0**. Delta against native-matches-complete-full: **+83 native, -83 fallback, +3.06 percentage points** (48 time-constructor and 35 date-time-constructor cases). Local whole-second TIME results now use the existing 1970-01-01 anchored timestamp-without-timezone SQL representation; `SparkSqlFeelValueCodec.fromSpark(value, declaredType)` converts native TIME results to LocalTime and validates the anchor, while preserving binary offset-time fallback. The TCK runner decodes both decision and invocation results using their declared type. Native null/invalid-arity time calls skip unused arguments, and folded null TIME/DATE_TIME results no longer require binary transport. SQL execution regressions verify UTC/Europe/Zurich identity, no fallback UDFs, offset fallback preservation, invalid-anchor rejection, null arguments with unused duration offsets, and optimizer-folded nulls. **35 generator tests passed before the folded-null adjustment; all 16 hybrid integration tests passed after that final adjustment**. Final affected TCK selection 1116/1117: **171/171 passed; 112 native, 59 fallback; 0 failures/errors/rejections**; breakdown: time 54 native/29 fallback, date-time 58 native/30 fallback. Evidence: generator `target/native-time-result-focused-final.log`, `native-time-result-folded-null.log`; runner `target/native-time-result-folded-null-tck.log`, `native-time-result-folded-null-accounting.json`, `native-time-result-full.log`, `native-time-result-full-accounting.json`, `native-time-result-full-rejections.tsv`. Full execution retained 4 GB heap, Spark history 20/20/40, localhost, and unchanged timeouts. Failed exploratory probes are preserved: generator `target/native-time-probe.log` rejects TIME(9) with UNSUPPORTED_TIME_PRECISION (supported range 0â€“6); `native-time-microsecond-probe.log` reports UNSUPPORTED_TIME_TYPE because the internal spark.sql.timeType.enabled flag is off. The final implementation does not require this flag. These probes do not prove that nanosecond/zoned FEEL values cannot use other native SQL representations. Fractional/zoned time, temporal inputs, mixed temporal types and dynamic regex validation remain implementation/verification gaps; do not truncate precision or assume permanent exceptions. Largest fallback groups: arithmetic 113, membership 93, duration 38, years/months duration 36, instance-of 35, date 33, context put 30, date-time 30, time 29. **Next safe action:** inspect those measured groups; for temporal continuation, verify a precision-safe native representation and fractional-second properties before broadening the current whole-second guard, then run focused and full checks. Audit the 676 provisional rejections separately. The 95% milestone needs **671 additional native passes**. Usage last checked: **58% five-hour / 93% weekly remaining**. Stopped for today at the user's request after full TCK and handover updates; preserve all uncommitted source/test/documentation changes. No commit/push/reset/clean performed. This checkpoint supersedes prior metrics below.

2026-10-07 native matches completion checkpoint: full optimized hybrid Spark SQL TCK **3,391/3,391 passed (100.00%); 1,826 native (67.26% of 2,715 executable); 889 fallback (32.74% executable); 676 provisional expected rejections reported separately; 0 failures/execution errors/compilation errors; Maven exit 0**. Delta against native-matches-full: **+9 native, -9 fallback, +0.33 percentage points**. All **40/40 matches TCK cases now pass natively**, including six free-spacing x cases and three statically invalid list-argument cases. FEEL whitespace preprocessing preserves spaces in character classes and normalizes property escapes; statically invalid matches arguments emit typed SQL null. Scoped traversal prunes only unused invalid-call arguments, preserving fallback validation when the same list is used elsewhere. Generator tests: **33 passed, 0 failures/errors/skips**, including dynamic-row free-spacing and shared-list regressions. Evidence: `dmn-generator-sparksql/target/native-matches-complete-focused.log`; `dmn-tck-runner/target/native-matches-complete-tck.log`, `native-matches-complete-full.log`, `native-matches-complete-full-accounting.json`, `native-matches-complete-full-rejections.tsv`. Full run used 4 GB heap, Spark history limits 20/20/40, localhost, and unchanged case timeouts. Dynamic regex patterns/flags remain implementation gaps requiring native validation/null semantics. Next measured candidate: native TIME result transport; installed Spark 4.2 TimeType uses a LocalTime encoder and nanosecond storage, but SQL constructors/properties/comparisons still require execution verification before promotion. Temporal inputs, zoned values and other fallbacks remain implementation/verification gaps, not proved platform impossibilities. Audit the 676 provisional rejections separately. The 95% milestone requires **754 additional native passes**. Latest usage: **82% five-hour / 97% weekly remaining**. Preserve all uncommitted changes; no commit/push/reset/clean performed. This checkpoint supersedes earlier metrics below.


2026-10-06 native static-regex checkpoint and usage stop: full optimized hybrid Spark SQL TCK **3,391/3,391 passed (100.00%); 1,817 native (66.92% of 2,715 executable / 53.58% total); 898 fallback (33.08% executable / 26.48% total); 676 provisional expected rejections separately; 0 failures/execution errors/compilation errors; Maven exit 0**. Delta against native-local-temporal-full: **+32 native, -32 fallback, +1.18 percentage points** (31 cases from 1111-feel-matches-function and one from 0002-string-functions). Constant patterns and flags are validated at compile time using the runtime's FEEL regex rules; dynamic input rows execute Spark rlike with translated Unicode case flags, literal quoting, BasicLatin properties, and character-class subtraction. Invalid static flags/backreferences emit typed SQL null; null flags retain the runtime default; named arguments are validated. No Java fallback UDF executes for promoted matches. Generator tests: **32 passed, 0 failures/errors/skips**, including dynamic-row Unicode/quoting/subtraction/invalid-pattern execution checks. Focused matches TCK: **40/40 passed; 31 native, 9 fallback; 0 failures/errors/rejections**. Remaining nine: **six free-spacing x cases (K2-MatchesFunc-1 through -6)** and **three invalid untyped-list argument cases (K-MatchesFunc-1, -3, -4)**; these are implementation/routing gaps, not proven platform limitations. Dynamic patterns/flags also retain explicit fallback because their validation/null behavior is not yet implemented natively. Evidence: `dmn-generator-sparksql/target/native-matches-focused.log`; `dmn-tck-runner/target/native-matches-tck.log`, `native-matches-full.log`, `native-matches-full-accounting.json`, `native-matches-full-rejections.tsv`. Final full run retained 4 GB heap and Spark history limits 20/20/40, with SPARK_LOCAL_HOSTNAME=localhost; no timeout or hostname error occurred. **Implementation stopped at 16% five-hour / 40% weekly remaining**, per prompts/actual-task.md; preserve all uncommitted source/test/documentation changes. No commit/push/reset/clean performed. Next safe action after checking usage: share or implement the existing stripFeelRegexWhitespace preprocessing for the six x cases, then recognize statically invalid matches list arguments before generic heterogeneous-list routing and emit native null, verify all 40 cases plus dynamic-row regressions, and run full TCK before claiming a new delta. Temporal input/result encoding and other measured fallback groups remain larger follow-on work; audit the 676 provisional rejections separately. The 95% milestone still needs **763 additional native passes** at the current denominator. This checkpoint supersedes earlier totals below.


2026-10-06 bounded local temporal promotion verified: full optimized hybrid Spark SQL TCK **3,391/3,391 passed (100.00%); 1,785 native (65.75% of 2,715 executable); 930 fallback (34.25% executable); 676 provisional expected rejections reported separately; 0 failures/execution errors/compilation errors; Maven exit 0**. Delta against native-dates-full: **+216 native, -216 fallback, +7.96 percentage points**. Local whole-second time constants/constructors are eligible as intermediate SQL values; bounded local date-time constants/constructors in years 1583â€“9999 can return native results. Timestamp-without-timezone SQL preserves local values across UTC and Europe/Zurich, including the DST gap; local timezone properties emit null. Native abs rejects temporal/non-numeric arguments before Spark analyzes invalid numeric casts. Mixed temporal kinds retain explicit fallback until FEEL identity/comparison checks are implemented. Generator tests: **31 passed, 0 failures/errors/skips**, including the new timezone/bounds/mixed-type/abs execution regression. Pre-final mixed-kind guard affected selection: **1,983/1,983 passed, 913 native, 441 fallback, 629 provisional expected rejections**; final abs selection: **17/17 passed, 12 native, 5 fallback, 0 failures/errors/rejections**. The final full run verifies the final source/artifacts. Evidence: `dmn-generator-sparksql/target/native-local-temporal-focused.log`; `dmn-tck-runner/target/native-local-temporal-full.log`, `native-local-temporal-full-accounting.json`, `native-local-temporal-full-rejections.tsv`, `native-local-temporal-before-mixed-guard-tck.log`, `native-local-temporal-abs-tck.log`. Earlier failed runs are preserved: pre-NTZ selection (59 failures/2 errors), hostname startup error (E14.home unresolved; final runs use SPARK_LOCAL_HOSTNAME=localhost), pre-abs full (2 errors), and timeout full (one 0035#001 timeout, zero errors). The unchanged 30-second 0035 limit then passed **3/3 natively** in isolation (`native-local-temporal-0035-tck.log`) and passed in the final full run; the intermittent timeout cause remains unresolved. Final full execution uses 4 GB heap and Spark history limits 20/20/40. Zoned values, fractional precision, historical/extreme years, temporal inputs, native TIME result transport, and mixed temporal kinds remain implementation/verification gaps rather than proven Spark impossibilities. Largest remaining fallback groups: arithmetic 113, membership 93, time constructor 77, date-time constructor 65, matches 40, duration constructor 38, years/months duration 36, instance-of 35. Next bounded options: native temporal result/input representation, or constant-pattern/flag FEEL regex translation using the existing runtime rules (Unicode flags, quoting, class subtraction, free spacing, invalid-backreference/null behavior); do not send raw FEEL flags directly to Java Pattern. Audit the 676 provisional rejections separately. The 95% milestone still needs 795 additional native passes at the current denominator. Preserve all uncommitted source, regression test, and documentation changes. This checkpoint supersedes earlier totals below.


2026-10-05 bounded native DATE promotion verified: **3,391/3,391 passed; 1,569 native (57.79% of 2,715 executable); 1,146 fallback (42.21% executable); 676 provisional expected rejections reported separately; 0 failures/errors/compilation errors; Maven exit 0**. Delta against native-statistics-full: **+140 native, -140 fallback, +5.16 percentage points**. Validated constant dates, constructors, and homogeneous date lists in years 1583â€“9999 now execute natively. Fixed FEEL weekday numbering (Monday=1), invalid calendar named parameters, and extra named arguments to is(); preserved its missing-argument behavior. Generator tests: **30 passed, 0 failures/errors/skips**. Focused date/membership/equality selection: **543/543 passed, 299 native, 244 fallback**; calendar/property selection after final fixes: **115/115 passed, 56 native, 59 fallback**. Evidence: `dmn-tck-runner/target/native-dates-full.log`, `native-dates-full-accounting.json`, `native-dates-full-rejections.tsv`, `native-dates-tck.log`, `native-dates-calendar-tck.log`; generator `target/native-dates-focused.log`. The earlier full date run had five failures (weekday and calendar argument validation), preserved in `native-dates-full-before-validation-fix.log`; the final full run supersedes it. Date inputs, dynamic or invalid constructors, historical-calendar and extreme-year dates retain lossless fallback: current implementation/verification gaps, not demonstrated Spark impossibilities. Next: measure remaining temporal arithmetic and input groups, implement bounded native representations with conformance checks, and audit the 676 provisional rejections. Preserve uncommitted source and documentation changes. This checkpoint supersedes earlier coverage totals below.

2026-10-05 native statistics promotion verified: full optimized hybrid Spark SQL TCK **3,391 total; 3,391 passed (100.00%); 1,429 native (52.63% of 2,715 executable); 1,286 fallback (47.37% executable); 676 provisional expected rejections; 0 failures/errors/compilation errors**, Maven exit 0. Compared with the clean post-coercion baseline: **+33 native, -33 fallback, +1.21 percentage points native coverage**, conformance and rejections unchanged. Native median now averages even-list middle values; native mode returns sorted tied-frequency values; native stddev uses sample variance. Numeric scalar/vararg/list forms and strict named arguments are supported; unresolved numeric types retain explained fallback. Targeted 0061/0062/0063: **39/39 passed, 33 native, 6 fallback, 0 failures/errors/rejections**. Focused generator tests: 30 passed, 0 failures/errors/skips. Evidence: `dmn-generator-sparksql/target/native-statistics-focused.log`; `dmn-tck-runner/target/native-statistics-tck.log`, `native-statistics-full.log`, `native-statistics-full-accounting.json`, `native-statistics-full-rejections.tsv`. Next priority remains increasing native CTE coverage through larger measured fallback groups. DATE promotion needs verified representation bounds because TCK includes extreme years; do not enable blanket native DATE routing without semantic/bounds checks. Rejection audit remains outstanding. This supersedes earlier baseline metrics below; preserve all other work.

2026-10-05 verified full baseline: **3,391 total; 3,391 passed (100.00%); 1,396 native (41.17% total / 51.42% of 2,715 executable); 1,319 fallback (38.90% total / 48.58% executable); 676 provisional expected rejections; 0 failures, execution errors, or compilation errors**. Maven exited 0. Delta versus `interval-native-full`: +34 passed, +33 native, +1 fallback, -18 failures, -16 errors; rejections unchanged. This clears the recorded Spark SQL TCK conformance blockers, while the 95% native milestone and rejection audit remain outstanding. Evidence in `dmn-tck-runner/target`: `post-coercion-full.log`, `post-coercion-full-accounting.json`, `post-coercion-full-rejections.tsv`, and `post-coercion-full-gc.log`. Committed optimizer/generator artifacts were rebuilt successfully (`dmn-generator-sparksql/target/post-coercion-install.log`) before Spark execution. The run retained 4 GB heap and 20/20/40 Spark history limits. Documentation facts passed for 12 modules; strict MkDocs exited 0 (`post-coercion-docs.log`). No source edits, commit or push during this verification checkpoint. Historical metrics/blockers below are superseded. Next: native fallback promotion against this clean baseline, beginning with conformant median/mode/stddev list semantics; audit the 676 rejection diagnostics before changing the denominator. Latest usage: 88% five-hour / 62% weekly remaining. Continue five-minute usage checks and approximately 45-minute checkpoints.

Latest status (2026-10-04 check-in & CI docs completion):
- **439-Case Affected-Suite TCK verified at 100.00% conformance**: **439 total; 439 passed (100.00%); 232 native; 195 fallback; 12 provisional expected rejections; 0 failed; 0 errors**.
- **Generator Tests**: Scoped tests in `dmn-generator-sparksql`: **30 passed, 0 failures, 0 errors, 0 skips**.
- **Spotless Formatting**: Clean repo-wide formatting verified with `./mvnw spotless:check`.
- **Commits Pushed to `origin/feature/graduate-incubating-modules`**:
  - `de0c395`: fix(sparksql): enforce declared BKM and invocation return types and verify 439-case affected suite conformance (resolved both `0082#decision_bkm_004_a` and `0082#invoke_002`; `0082-feel-coercion` is 36/36 passed, 14 native, 12 fallback, 10 expected rejections, 0 failures/errors).
  - `2c0f4e4`: docs: align mkdocs nav and links with active slice structure (resolved strict MkDocs CI failure with 0 warnings).
  - `64bf77b`: docs: document DMN FEEL to Spark SQL impedance mismatch (added comprehensive architecture analysis, triage matrix, strategies, and differential testing in `docs/sparksql-architecture.md`).
- **Next steps**: Run the full optimized hybrid Spark SQL TCK (3,391 cases) with 4 GB heap and history limits 20/20/40 to establish the post-coercion full-suite baseline metrics. Prior historical metrics below are preserved.

Prior affected-suite rerun: **439 total; 437 passed; 228 native; 197 fallback; 12 provisional expected rejections; 2 failed; 0 errors**. Blockers resolved in commit `de0c395`.

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
| Total cases | 3,391 | 100.00% | â€” |
| Passed | 3,177 | 93.69% | â€” |
| Native passes | 1,310 | 38.63% | 48.25% |
| Fallback passes | 1,191 | 35.12% | 43.87% |
| Failed | 112 | 3.30% | â€” |
| Errors | 102 | 3.01% | â€” |
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
