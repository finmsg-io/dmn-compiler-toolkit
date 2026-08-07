# DMN Generator Spark SQL (`dmn-generator-sparksql`)

`dmn-generator-sparksql` is a high-performance Spark / Databricks SQL code generator from Runtime IR.

## Key Features

- **Zero-UDF Overhead**: Compiles DMN decision models and decision tables directly into standard Spark SQL CTE queries (`WITH ... AS (...) SELECT ...`).
- **Engine-Native Execution**: Delegates all query execution, expression vectorization, Whole-Stage Codegen, and Catalyst/Photon optimizations directly to Apache Spark and Databricks.
- **`<decision-name>.sql` Artifact Bundles**: Generates self-contained `.sql` query files per root decision.
- **Zero-Dependency Java Runner**: Generates `DmnSparkSqlRunner` Java wrapper code to easily execute decision queries against Spark `Dataset<Row>` / `DataFrame` instances.
