# Closed nested path equality, duration ranges, context functions, and list flatten

Continues from committed checkpoint `62c26d2`: full TCK 3,391/3,391 passed;
2,668 native (98.27%), 47 fallback (1.73%) of 2,715 executable cases.
Separately, 676 provisional expected model rejections remain unchanged.

## Conversions Verified in this Slice
Six official TCK test cases were converted from FALLBACK to 100% NATIVE Spark SQL execution:
1. `compliance-level-3/1156-range-function/1156-range-function-test-01.xml#001_h`
   - Duration constant range membership (`"P1D" in [@"P1D"..@"P2D"]`) admitted in `SparkSqlStaticTypeProof.literal()` and `RuntimeRangeExpression`.
2. `compliance-level-3/1156-range-function/1156-range-function-test-01.xml#001_i`
   - Duration constant range membership (`"P1D" in (@"P1D"..@"P2D"]`).
3. `compliance-level-3/0090-feel-paths/0090-feel-paths-test-01.xml#nested_list_001`
   - Nested path projection equality (`a.b.c = [1, 2]`) over closed collection structures. Admitted in `SparkSqlClosedCollectionProof`.
4. `compliance-level-3/0090-feel-paths/0090-feel-paths-test-01.xml#nested_list_002`
   - Nested path projection inequality (`a.b.c != [1, 2]`).
5. `compliance-level-3/1145-feel-context-function/1145-feel-context-function-test-01.xml#010`
   - Legitimate empty-key context maps (`context([{"key": "", "value": 1}])`). Disambiguated in `SparkSqlStaticTypeProof` and `SparkSqlExpressionEmitter` from anonymous boxed return wrappers.
6. `compliance-level-3/0012-list-functions/0012-list-functions-test-01.xml#019`
   - Nested list flattening of appended lists (`flatten(append(["a"], ["b"]))`). Added unrolling in `SparkSqlFlattenLowerer.collect()`.

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

## Remaining Fallback Backlog (41 cases)
- Temporal (5): `0096-feel-day-of-week-function` (`date_001`, `datetime_001`), `0097-feel-month-of-year-function` (`date_001`, `datetime_001`), `0098-feel-week-of-year-function` (`date_008`).
- Lists / Context / Tables (14): `0001-filter#001`, `0006-join#001`, `0007-date-time#001`, `0009-append-flatten#001`, `0010-concatenate#001`, `0011-insert-remove#001`, `0013-sort#001`, `0014-loan-comparison#001`, `0016-some-every#001`, `0017-tableTests` (`dateCompare1`, `dateCompare2`), `0021-singleton-list#001`, `0080-feel-getvalue-function` (`decision_013`, `decision_014`).
- Loops / Lambdas / UDFs / Ch. 11 (22): `0030-user-defined-functions#001`, `0031` (3), `0033-for-loops` (2), `0036-dt-variable-input` (3), `0076-feel-external-java#incorrect_002`, `0084-feel-for-loops` (`decision_017`, `decision_018`), `0087-chapter-11-example` (5), `0088-no-decision-logic#001`, `0092-feel-lambda` (2), `0003-iteration#001`, `0004-lending#001`.
