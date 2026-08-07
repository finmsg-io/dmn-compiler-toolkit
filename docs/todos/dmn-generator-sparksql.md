# `dmn-generator-sparksql` TODO

<!-- generated-toc:start -->
## Table of contents

- [Current status](#contents-section-1)
- [Next planned work](#contents-section-2)
<!-- generated-toc:end -->

Last reviewed: 2026-08-07

The `dmn-generator-sparksql` module owns pure Spark / Databricks SQL query generation (`<decision-name>.sql`) using Common Table Expressions (CTEs) without UDF overhead, along with zero-dependency Java runner generation (`DmnSparkSqlRunner`).

<a id="contents-section-1"></a>
## Current status

| Priority | Work item | Status | Evidence |
| --- | --- | --- | --- |
| P11.1 | Lower FEEL expressions & decision tables into Spark SQL CTE expressions | `done` | `SparkSqlExpressionEmitter` |
| P11.2 | Generate Spark SQL `StructType` input/output schemas | `done` | `SparkSqlSchemaGenerator` |
| P11.3 | Generate CTE `<decision-name>.sql` files & `DmnSparkSqlRunner` Java wrapper | `done` | `DmnSparkSqlGenerator` |
| P11.4 | Integration test suite on local Spark Session | `done` | `SparkSqlDmnIntegrationTest` |
