package io.finmsg.dmn.generator.sparksql;

import java.util.Objects;

/** Configuration options for DMN Spark SQL generation. */
public final class DmnSparkSqlGeneratorOptions {

  private final String packageName;
  private final String className;
  private final String inputTableName;
  private final boolean includeJavaRunner;

  public DmnSparkSqlGeneratorOptions(
      String packageName,
      String className,
      String inputTableName,
      boolean includeJavaRunner) {
    this.packageName = Objects.requireNonNull(packageName, "packageName");
    this.className = Objects.requireNonNull(className, "className");
    this.inputTableName = Objects.requireNonNull(inputTableName, "inputTableName");
    this.includeJavaRunner = includeJavaRunner;
  }

  public static DmnSparkSqlGeneratorOptions defaults() {
    return new DmnSparkSqlGeneratorOptions("io.finmsg.dmn.spark", "DmnSparkSqlRunner", "input_table", true);
  }

  public String packageName() {
    return packageName;
  }

  public String className() {
    return className;
  }

  public String inputTableName() {
    return inputTableName;
  }

  public boolean includeJavaRunner() {
    return includeJavaRunner;
  }
}
