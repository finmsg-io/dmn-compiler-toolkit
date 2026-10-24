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

# `dmn-generator-sparksql` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
<!-- generated-toc:end -->

Last reviewed: 2026-09-28

The module owns native Spark SQL CTE generation and an opt-in decision-level Java
UDF bridge. Hybrid runners require the interpreter and bridge dependencies on
driver and executors. See [architecture](../sparksql-architecture.md) for the
lossless input/output contract and current limits.

<a id="contents-section-1"></a>
## Current status

Verified 2026-10-24: generated hybrid fallback now has lossless numeric-bearing
input and numeric-result payloads, including lazy handling of non-finite arguments.
The ordinary native NUMBER column remains DOUBLE unless an exact projection is
used. Callers must supply generated-schema rows; see
[transport contract](../dev/slices/lossless-numeric-fallback-transport-2026-10-23.md).
[Structured metric loops and rankings](../dev/slices/native-ranked-contexts-2026-10-24.md)
converted loan comparison: full TCK 2,714 native (99.96%), 1 fallback (0.04%)
of 2,715 executable cases; all 3,391 cases passed, including the separately
counted 676 expected model rejections. Remaining work is temporal
construction/validation with precise offsets and duration identity.


| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P11.1 | Lower FEEL expressions & decision tables into Spark SQL CTE expressions | `done` | `SparkSqlExpressionEmitter` |
| P11.2 | Generate Spark SQL `StructType` input/output schemas | `done` | `SparkSqlSchemaGenerator` |
| P11.3 | Generate CTE `<decision-name>.sql` files & `DmnSparkSqlRunner` Java wrapper | `done` | `DmnSparkSqlGenerator` |
| P11.4 | Integration test suite on local Spark Session | `done` | `SparkSqlDmnIntegrationTest` |
| P15.S | Upgrade Spark dependency to 4.2.0 (released 2026-07-14) | `done` | `pom.xml` `spark.version=4.2.0` |
| Hybrid 1 | Decision-level UDF, model transport, and lossless FEEL value codec | `implemented` | `SparkSqlDecisionUdf`, `SparkSqlFeelValueCodec`, hybrid tests |
| Hybrid 2 | Static subgraph routing and generated UDF registration | `implemented` | `SparkSqlCapabilityAnalyzer`, generator result and Java runner |
| Hybrid 3 | Pinned TCK runner integration and separate native/fallback accounting | `done` | 100.00% conformance measured across 3,391 cases (0 failures, 0 errors) |
| Hybrid 4 | Promote verified operations into native SQL | `in-progress` | Level 2 100% native (116/116 passes); Level 3 promotion in progress |
