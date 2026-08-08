package io.finmsg.dmn.generator.sparksql;

import java.util.Objects;

/** Configuration options for DMN Spark SQL generation. */
public record DmnSparkSqlGeneratorOptions(
    String packageName,
    String className,
    String inputTableName,
    boolean includeJavaRunner) {

  public DmnSparkSqlGeneratorOptions {
    Objects.requireNonNull(packageName, "packageName");
    Objects.requireNonNull(className, "className");
    Objects.requireNonNull(inputTableName, "inputTableName");
  }

  public static DmnSparkSqlGeneratorOptions defaults() {
    return new DmnSparkSqlGeneratorOptions("io.finmsg.dmn.spark", "DmnSparkSqlRunner", "input_table", true);
  }
}
