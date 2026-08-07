package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;
import org.apache.spark.sql.types.StructType;

/** Pure Spark / Databricks SQL generator from Runtime IR. */
public final class DmnSparkSqlGenerator {

  public DmnSparkSqlGeneratorResult generate(RuntimeOptimizedModel optimizedModel) {
    return generate(optimizedModel, DmnSparkSqlGeneratorOptions.defaults());
  }

  public DmnSparkSqlGeneratorResult generate(RuntimeOptimizedModel optimizedModel, DmnSparkSqlGeneratorOptions options) {
    Objects.requireNonNull(optimizedModel, "optimizedModel");
    Objects.requireNonNull(options, "options");

    RuntimeModel model = optimizedModel.model();
    StructType inputSchema = SparkSqlSchemaGenerator.generateInputSchema(model);

    Map<Integer, String> slotNames = new HashMap<>();
    for (RuntimeInput input : model.inputs()) {
      slotNames.put(input.valueSlot(), "input_" + input.valueSlot());
    }
    for (RuntimeDecision decision : model.decisions()) {
      slotNames.put(decision.resultSlot(), sanitizeIdentifier("decision_" + decision.resultSlot()));
    }

    Map<Integer, RuntimeDecision> decisionMap = new HashMap<>();
    for (RuntimeDecision decision : model.decisions()) {
      decisionMap.put(decision.id(), decision);
    }

    Map<String, String> sqlFiles = new LinkedHashMap<>();

    for (RuntimeDecision decision : model.decisions()) {
      String name = sanitizeIdentifier("Decision_" + decision.resultSlot());
      String sqlQuery = buildCteQuery(model, decision, decisionMap, slotNames, options.inputTableName());
      sqlFiles.put(name + ".sql", sqlQuery);
    }

    Map<String, String> javaSources = new HashMap<>();
    if (options.includeJavaRunner()) {
      String fqcn = options.packageName() + "." + options.className();
      String javaCode = buildJavaRunner(options, sqlFiles);
      javaSources.put(fqcn, javaCode);
    }

    return new DmnSparkSqlGeneratorResult(sqlFiles, javaSources, inputSchema);
  }

  private String buildCteQuery(
      RuntimeModel model,
      RuntimeDecision targetDecision,
      Map<Integer, RuntimeDecision> decisionMap,
      Map<Integer, String> slotNames,
      String inputTable) {

    StringBuilder sb = new StringBuilder();
    sb.append("-- Generated Spark SQL CTE Query for DMN Decision: ").append(targetDecision.id()).append("\n");

    List<Integer> evalOrder = model.evaluationOrder();
    Set<Integer> requiredNodeIds = findDependencies(targetDecision, decisionMap);
    requiredNodeIds.add(targetDecision.id());

    List<RuntimeDecision> orderedDecisions = new ArrayList<>();
    for (int nodeId : evalOrder) {
      if (requiredNodeIds.contains(nodeId) && decisionMap.containsKey(nodeId)) {
        orderedDecisions.add(decisionMap.get(nodeId));
      }
    }

    if (orderedDecisions.isEmpty()) {
      return "SELECT NULL AS " + sanitizeIdentifier("result_" + targetDecision.resultSlot()) + ";";
    }

    sb.append("WITH _base_input AS (\n");
    sb.append("  SELECT * FROM ").append(inputTable).append("\n");
    sb.append(")");

    String currentTable = "_base_input";

    for (int idx = 0; idx < orderedDecisions.size(); idx++) {
      RuntimeDecision dec = orderedDecisions.get(idx);
      String decName = slotNames.get(dec.resultSlot());
      String exprCode = emitDecisionExpr(dec, slotNames);
      String cteName = "_cte_" + decName;

      sb.append(",\n").append(cteName).append(" AS (\n");
      sb.append("  SELECT *,\n");
      sb.append("    (").append(exprCode).append(") AS `").append(decName).append("`\n");
      sb.append("  FROM ").append(currentTable).append("\n");
      sb.append(")");

      currentTable = cteName;
    }

    String finalName = slotNames.get(targetDecision.resultSlot());
    sb.append("\nSELECT `").append(finalName).append("` FROM ").append(currentTable).append(";\n");

    return sb.toString();
  }

  private Set<Integer> findDependencies(RuntimeDecision decision, Map<Integer, RuntimeDecision> decisionMap) {
    Set<Integer> deps = new HashSet<>();
    Queue<RuntimeDecision> queue = new LinkedList<>();
    queue.add(decision);

    while (!queue.isEmpty()) {
      RuntimeDecision current = queue.poll();
      for (int reqSlot : current.dependencies()) {
        for (RuntimeDecision candidate : decisionMap.values()) {
          if (candidate.resultSlot() == reqSlot && deps.add(candidate.id())) {
            queue.add(candidate);
          }
        }
      }
    }
    return deps;
  }

  private String emitDecisionExpr(RuntimeDecision decision, Map<Integer, String> slotNames) {
    if (decision.decisionTable().isPresent()) {
      return SparkSqlExpressionEmitter.emitDecisionTable(decision.decisionTable().get(), slotNames::get);
    } else if (decision.expression().isPresent()) {
      return SparkSqlExpressionEmitter.emit(decision.expression().get(), slotNames::get);
    }
    return "NULL";
  }

  private String buildJavaRunner(DmnSparkSqlGeneratorOptions options, Map<String, String> sqlFiles) {
    StringBuilder sb = new StringBuilder();
    sb.append("package ").append(options.packageName()).append(";\n\n");
    sb.append("import org.apache.spark.sql.Dataset;\n");
    sb.append("import org.apache.spark.sql.Row;\n");
    sb.append("import org.apache.spark.sql.SparkSession;\n");
    sb.append("import java.util.HashMap;\n");
    sb.append("import java.util.Map;\n\n");
    sb.append("/** Generated Spark SQL DMN Decision Execution Runner. */\n");
    sb.append("public final class ").append(options.className()).append(" {\n\n");
    sb.append("  private static final Map<String, String> SQL_QUERIES = new HashMap<>();\n\n");
    sb.append("  static {\n");

    for (Map.Entry<String, String> entry : sqlFiles.entrySet()) {
      String key = entry.getKey().replace(".sql", "");
      String escapedSql = entry.getValue().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n\" +\n      \"");
      sb.append("    SQL_QUERIES.put(\"").append(key).append("\", \"").append(escapedSql).append("\");\n");
    }

    sb.append("  }\n\n");

    sb.append("  public static Dataset<Row> evaluate(SparkSession spark, Dataset<Row> inputDf, String decisionName) {\n");
    sb.append("    String sqlQuery = SQL_QUERIES.get(decisionName);\n");
    sb.append("    if (sqlQuery == null) {\n");
    sb.append("      throw new IllegalArgumentException(\"Unknown decision name: \" + decisionName);\n");
    sb.append("    }\n");
    sb.append("    inputDf.createOrReplaceTempView(\"").append(options.inputTableName()).append("\");\n");
    sb.append("    return spark.sql(sqlQuery);\n");
    sb.append("  }\n\n");

    sb.append("  public static Map<String, String> getSqlFiles() {\n");
    sb.append("    return Map.copyOf(SQL_QUERIES);\n");
    sb.append("  }\n");
    sb.append("}\n");

    return sb.toString();
  }

  private String sanitizeIdentifier(String name) {
    return name.replaceAll("[^a-zA-Z0-9_]", "_");
  }
}
