package io.finmsg.dmn.generator.java;

import java.util.Objects;

/** Configuration options for Java code generation. */
public record DmnJavaGeneratorOptions(
    String packageName,
    String className,
    boolean generateTypedRecords) {

  public DmnJavaGeneratorOptions {
    packageName = packageName == null || packageName.isBlank() ? "io.finmsg.dmn.generated" : packageName;
    className = className == null || className.isBlank() ? "GeneratedDecisionModel" : className;
  }

  public static DmnJavaGeneratorOptions defaults() {
    return new DmnJavaGeneratorOptions("io.finmsg.dmn.generated", "GeneratedDecisionModel", false);
  }

  public static DmnJavaGeneratorOptions of(String packageName, String className) {
    return new DmnJavaGeneratorOptions(packageName, className, false);
  }
}
