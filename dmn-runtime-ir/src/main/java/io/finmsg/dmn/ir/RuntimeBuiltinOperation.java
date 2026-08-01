package io.finmsg.dmn.ir;

import java.util.Arrays;
import java.util.Optional;

/** Stable evaluator dispatch IDs for the currently validated FEEL built-ins. */
public enum RuntimeBuiltinOperation {
  NOT(0, "not"), STRING(1, "string"), NUMBER(2, "number"), DATE(3, "date"),
  TIME(4, "time"), DATE_AND_TIME(5, "date and time"), DURATION(6, "duration"),
  COUNT(7, "count"), SUM(8, "sum"), MIN(9, "min"), MAX(10, "max"), ABS(11, "abs");

  private final int id;
  private final String feelName;

  RuntimeBuiltinOperation(int id, String feelName) {
    this.id = id;
    this.feelName = feelName;
  }

  public int id() { return id; }
  public String feelName() { return feelName; }

  public static Optional<RuntimeBuiltinOperation> find(String name) {
    String normalized = name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    return Arrays.stream(values()).filter(value -> value.feelName.equals(normalized)).findFirst();
  }
}
