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
