# Closed nested path equality, duration ranges, context functions, and list flatten

Continues from committed checkpoint `62c26d2`: full TCK 3,391/3,391 passed;
2,668 native (98.27%), 47 fallback (1.73%) of 2,715 executable cases.
Separately, 676 provisional expected model rejections remain unchanged.

## Conversions Verified in this Slice
Six official TCK test cases were converted from FALLBACK to 100% NATIVE Spark SQL execution:
1. `compliance-level-3/1156-range-function/1156-range-function-test-01.xml#001_h`
   - Static days-and-time duration range type validation (`instance of range<days and time duration>`) admitted in `SparkSqlStaticTypeProof.literal()` and `RuntimeRangeExpression`.
2. `compliance-level-3/1156-range-function/1156-range-function-test-01.xml#001_i`
   - Static years-and-months duration range type validation (`instance of range<years and months duration>`).
3. `compliance-level-3/0090-feel-paths/0090-feel-paths-test-01.xml#nested_list_001`
   - Equality between closed nested `.a.b` and `.b` projections over closed collection structures. Admitted in `SparkSqlClosedCollectionProof`.
4. `compliance-level-3/0090-feel-paths/0090-feel-paths-test-01.xml#nested_list_002`
   - Equality of a closed mixed scalar/list path projection with `[1, [2.1, 2.2], 3, 4, 5]`.
5. `compliance-level-3/1145-feel-context-function/1145-feel-context-function-test-01.xml#010`
   - Legitimate empty-key context maps (`context({key: "", value: 1})`). Disambiguated in `SparkSqlStaticTypeProof` and `SparkSqlExpressionEmitter` from anonymous boxed return wrappers.
6. `compliance-level-3/0012-list-functions/0012-list-functions-test-01.xml#019`
   - Nested list flattening of appended lists (`flatten(append(list1, list2))`). Added unrolling in `SparkSqlFlattenLowerer.collect()`.

## Target Suite Verification
- `1156-range-function`: 56/56 passed (100%), 56 native, 0 fallback.
- `0090-feel-paths`: 4/4 passed (100%), 4 native, 0 fallback.
- `1145-feel-context-function`: 18/18 passed (100%), 18 native, 0 fallback.
- `0012-list-functions`: 19/19 passed (100%), 19 native, 0 fallback.

## Generator Integration Verification
- Full generator regression: 114/114 JUnit tests passed, 0 failures, 0 errors.
- `SPARK SQL PROCESSING [SparkSqlHybridIntegrationTest]`:
  `native=1502 (96.65%), fallback=52 (3.35%), unknown=0, denominator=1554, failed=0`.
- Target artifact installed in `~/.m2/repository/io/finmsg/dmn/dmn-generator-sparksql/1.1.0-SNAPSHOT/`.

## Remaining fallback backlog (41 cases; confirmed by latest full)
- Temporal (5): `0096-feel-day-of-week-function` (`date_001`, `datetime_001`), `0097-feel-month-of-year-function` (`date_001`, `datetime_001`), `0098-feel-week-of-year-function` (`date_008`).
- Lists / Context / Tables (14): `0001-filter#001`, `0006-join#001`, `0007-date-time#001`, `0009-append-flatten#001`, `0010-concatenate#001`, `0011-insert-remove#001`, `0013-sort#001`, `0014-loan-comparison#001`, `0016-some-every#001`, `0017-tableTests` (`dateCompare1`, `dateCompare2`), `0021-singleton-list#001`, `0080-feel-getvalue-function` (`decision_013`, `decision_014`).
- Loops / Lambdas / UDFs / Ch. 11 (22): `0030-user-defined-functions#001`, `0031` (3), `0033-for-loops` (2), `0036-dt-variable-input` (3), `0076-feel-external-java#incorrect_002`, `0084-feel-for-loops` (`decision_017`, `decision_018`), `0087-chapter-11-example` (5), `0088-no-decision-logic#001`, `0092-feel-lambda` (2), `0003-iteration#001`, `0004-lending#001`.

## Reconciliation with the earlier full run

Before the additional range/context/append changes, the path-equality artifact
passed the full TCK: 3,391/3,391, 2,670 native (98.34%), 45 fallback (1.66%) of
2,715 executable cases, 676 provisional expected model rejections separately.
Failed/unknown routes were zero. Maven exited zero. The earlier generator run
passed 112/112 tests: 1,512 native actions (96.49%), 55 fallback (3.51%) of
1,567, zero failed/unknown. Its installed JAR SHA256 was
`2E9DF631EA4C811E351CD2820633D7241B3500DEB1C3FDDD0401589A33ACB845`.
The earlier full log is `native-path-equality-full-tck-2026-10-19.log`.
Subsequent targeted runs overwrote the default accounting JSON; the original
full log remains available. The 45-fallback total must not be attributed to the
newer artifact or combined with targeted-suite counts as a full result.

Commit `699e324` subsequently added the other four targeted conversions.
Its target and installed JAR SHA256 agree:
`2D330C8DAF8F0BC86B08AAC2A9E5AC3DA8B026E5BC8442614A5B86B78A7DA517`.
A fresh full Spark SQL TCK was started against that artifact because remaining
primary usage was below the user's 20% threshold. Full scope is 3,391 cases for
one optimized Spark SQL hybrid backend. Generator SQL action rates and TCK case
rates have different denominators. The subsequent full run confirms the 41-fallback total; see the verified checkpoint below.

## Latest verified full checkpoint

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

Maven completed the current full TCK with exit code zero.
