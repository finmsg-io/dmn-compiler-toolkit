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
Details: [handover](../dev/slices/native-closed-collections-2026-10-19.md).

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
Details: [handover](../dev/slices/native-date-comparisons-2026-10-19.md).

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
Details: [handover](../dev/slices/native-nested-score-lists-2026-10-19.md).

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
Details: [handover](../dev/slices/native-string-boxed-2026-10-19.md).

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
Details: [handover](../dev/slices/native-partial-loops-2026-10-19.md).

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
Details: [handover](../dev/slices/native-flatten-quantifiers-java-2026-10-18.md).

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
