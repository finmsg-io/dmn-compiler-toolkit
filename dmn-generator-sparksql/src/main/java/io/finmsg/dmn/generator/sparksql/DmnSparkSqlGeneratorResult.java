package io.finmsg.dmn.generator.sparksql;

import java.util.Map;
import java.util.Objects;
import org.apache.spark.sql.types.StructType;

/** Result containing generated Spark SQL CTE query files, Java runner code, and schema. */
public final class DmnSparkSqlGeneratorResult {

  private final Map<String, String> sqlFiles;
  private final Map<String, String> javaSources;
  private final StructType inputSchema;

  public DmnSparkSqlGeneratorResult(
      Map<String, String> sqlFiles,
      Map<String, String> javaSources,
      StructType inputSchema) {
    this.sqlFiles = Map.copyOf(Objects.requireNonNull(sqlFiles, "sqlFiles"));
    this.javaSources = Map.copyOf(Objects.requireNonNull(javaSources, "javaSources"));
    this.inputSchema = Objects.requireNonNull(inputSchema, "inputSchema");
  }

  public Map<String, String> sqlFiles() {
    return sqlFiles;
  }

  public Map<String, String> javaSources() {
    return javaSources;
  }

  public StructType inputSchema() {
    return inputSchema;
  }
}
