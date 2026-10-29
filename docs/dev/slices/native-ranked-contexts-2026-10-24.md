# Native structured metric loops and numeric rankings

## Reusable lowering and scope

A bounded proof lowers a literal relation (or its equivalent typed list of literal
contexts), a one-variable loop producing scalar context fields through statically
bound FEEL functions, and zero or more numeric field rankings into native SQL.
It uses no official case IDs, model names, lender names or financial constants.
This targets the remaining loan-comparison pattern while admitting variations.

Each loop iteration uses the shared guarded scalar evaluation plan. Numeric
intermediates remain exact coefficient/scale values; division and power reuse the
existing DECIMAL128 and Math.pow conversion semantics. The source relation is
serialized into exact scalar SQL literals rather than consumed through rounded
DOUBLE columns. A topological aggregate computes context entries and static BKM
arguments in their required order. Rankings compare exact numeric fields and
preserve ties; null comparison results follow the runtime's false/false comparator
behavior. The full named context/list result is emitted as a versioned FEEL binary
payload, with valid Unicode strings encoded as UTF-8.

The proof is terminal. Unsupported consumers retain fallback rather than treating
encoded context or numeric results as ordinary Spark values. It accepts at most
256 source rows, 16 source/result fields and nine outer context entries (the
original metric list and up to eight sorts), subject to the scalar step, depth and
guard limits. Sources must be literal, schema-compatible scalar records; dynamic
input tables and arbitrary higher-order comparators remain outside this proof.
Comparator parameters must accept the computed records without incompatible
coercion. Local slots may not alias previous outer entries. Strings emitted in
results must be proven valid Unicode; arbitrary string input can still be used
for supported scalar comparisons but is not admitted as a result without a proof.

Generic static lowering preserves proven ranked roots. Source relations may be
lowered into equivalent literal lists; the declared source schema retains the
shape of an empty list even when its expression type loses its element layout.
Dependencies follow actual raw inputs and proven leaf columns, while interpreter
fallback slicing retains the original complete model graph.

The accompanying lossless numeric fallback transport is documented separately in
[its slice](lossless-numeric-fallback-transport-2026-10-23.md). That work preserves
precision in fallback actions and does not count them as native.

## Variations and run history

Regression variations cover empty, one-row and five-row relations; ascending and
descending sorts; renamed fields; reversed named versus positional BKM bindings;
Unicode labels; large/high-precision and null inputs; and stable ties. Capability
checks cover coercing comparator parameters, aliased context slots and downstream
structured consumers. Values and complete ranked results are compared with the
shared runtime, including repartitioned Spark execution.

| Run/log prefix | Tests | Native actions | Fallback actions | Denominator | Unknown | Expected native SQL errors | Outcome |
| --- | --- | --- | --- | --- | --- | --- | --- |
| lossless-fallback-numeric-focused-2026-10-23 | 2 | 2 (14.29%) | 12 (85.71%) | 14 | 0 | 2 | passed |
| lossless-fallback-numeric-full-generator-2026-10-23 | 152 | 2,938 (96.58%) | 104 (3.42%) | 3,042 | 0 | 58 | two assertion failures: null and old lossy numeric normalization |
| ranked-context-transport-compile-focused-2026-10-23 | none executed | 0 (undefined rate) | 0 (undefined rate) | 0 | 0 | 0 | compilation stopped at schema-loop scope error |
| ranked-context-focused-2026-10-23 | 4 | 2 (11.76%) | 15 (88.24%) | 17 | 0 | 2 | two assertions failed: legacy numeric comparison and premature native capability |
| ranked-context-preserved-focused-2026-10-23 | 4 | 2 (9.09%) | 20 (90.91%) | 22 | 0 | 2 | ranking capability assertion failed after source relation lowering |
| ranked-context-literal-list-focused-2026-10-23 | 4 | 2 (9.09%) | 20 (90.91%) | 22 | 0 | 2 | empty source lost its expression element layout |
| ranked-context-empty-source-focused-2026-10-23 | 1 | 8 (100%) | 0 (0%) | 8 | 0 | 0 | assertion wrongly required every unrelated source decision to be native |
| ranked-context-source-boundary-focused-2026-10-24 | 1 | 16 (66.67%) | 8 (33.33%) | 24 | 0 | 0 | passed all metric/ranking variations |

Logs are under dmn-generator-sparksql/target with the listed prefix and .log suffix.
For the passing focused run, all requested ranked-result queries are native.
Eight combined-query actions include an independently unsupported source decision
with Unicode labels and therefore correctly count as fallback. This demonstrates
that target routing and whole-model routing must be reported separately.

Initial full generator run: 154 tests, one failed legacy Unicode capability
assertion; all ranking and transport regressions passed. Combined accounting:
2,948 native (96.15%), 118 fallback (3.85%) of 3,066; unknown 0, 58 expected
native SQL error assertions. Log: native-ranked-context-full-generator-2026-10-24.log.
An accidental file-encoding conversion had corrupted four unchanged Unicode
literal occurrences in the integration test. The original literals were restored
from Git using explicit UTF-8 handling. The corrected complete regression/install
passed all 154 tests with no failures/errors/skips; production calculations were
unchanged by that fixture repair. Combined SQL accounting: 2,954 native (96.16%),
118 fallback (3.84%) of 3,072; unknown 0, 58 expected native SQL error assertions.
Hybrid integration: 2,941 native (96.14%), 118 fallback (3.86%) of 3,059;
unknown 0, 58 expected errors. DMN integration: 13 native (100%), 0 fallback (0%)
of 13; failed/unknown 0. Log: native-ranked-context-corrected-full-generator-2026-10-24.log.
Saved full-suite XML reports: target/native-ranked-context-full-surefire-2026-10-24.
Artifact SHA256 at that full-suite checkpoint:
DC516D3E43E245DF10D68EE102C6C1BFC64519FCBFD61FA205E298F63437A3CD.

A final codec addition preserves non-finite Double/Float inputs rather than
failing during serialization before FEEL evaluates them. The final focused
regression/install passed five tests: four Spark integration methods and one
payload round-trip method. Accounting: 18 native (38.30%), 29 fallback (61.70%)
of 47; unknown 0. Three SQL errors are expected assertions: two native and one
fallback. Unused NaN remains inactive; used NaN arithmetic still raises the
runtime error. This focused run re-executes ranked variations, numeric consumer
transport, nested lists and legacy sort coercion after the codec addition.
Log: native-ranked-context-final-focused-2026-10-24.log.
Final installed artifact SHA256:
5FC167F36DBEA59856A201A644416397408DA36D0DF41CF073C32A32ACDB05CF.

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

Maven completed the final full TCK with exit code 0. Target and installed artifact
SHA256 values match. Documentation facts, diff whitespace checks and the strict
MkDocs build passed. Official TCK fixtures were not modified.
