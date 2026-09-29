package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;
import org.apache.spark.sql.types.StructType;

/** Pure Spark / Databricks SQL generator from Runtime IR. */
public final class DmnSparkSqlGenerator {

	public DmnSparkSqlGeneratorResult generate(RuntimeOptimizedModel optimizedModel) {
		return generate(optimizedModel, DmnSparkSqlGeneratorOptions.defaults());
	}

	public DmnSparkSqlGeneratorResult generate(RuntimeOptimizedModel optimizedModel,
			DmnSparkSqlGeneratorOptions options) {
		Objects.requireNonNull(optimizedModel, "optimizedModel");
		Objects.requireNonNull(options, "options");

		RuntimeModel model = optimizedModel.model();
		Map<Integer, String> slotNames = new HashMap<>();
		for (RuntimeInput input : model.inputs()) {
			String rawName = options.customSlotNames().getOrDefault(input.valueSlot(), "input_" + input.valueSlot());
			slotNames.put(input.valueSlot(), rawName);
		}
		for (RuntimeDecision decision : model.decisions()) {
			String rawName = options.customSlotNames().getOrDefault(decision.resultSlot(),
					"decision_" + decision.resultSlot());
			slotNames.put(decision.resultSlot(), rawName);
		}
		Map<Integer, RuntimeBkm> bkmBySlot = new HashMap<>();
		for (RuntimeBkm bkm : model.businessKnowledgeModels()) {
			String rawName = options.customSlotNames().getOrDefault(bkm.resultSlot(), "bkm_" + bkm.resultSlot());
			slotNames.put(bkm.resultSlot(), rawName);
			bkmBySlot.put(bkm.resultSlot(), bkm);
		}
		int synthSlotCounter = 100000;
		for (RuntimeDecision decision : model.decisions()) {
			if (decision.expression().isPresent()) {
				RuntimeExpression dExpr = decision.expression().get();
				if (dExpr instanceof RuntimeFunctionDefinition fn) {
					RuntimeBkm synthBkm = new RuntimeBkm(decision.id(), decision.resultSlot(), decision.type(),
							decision.dependencies(), RuntimeFunctionKind.FEEL, Optional.of(fn));
					bkmBySlot.put(decision.resultSlot(), synthBkm);
				} else if (dExpr instanceof RuntimeContextExpression ctx) {
					RuntimeBkm ctxBkm = new RuntimeBkm(decision.id(), decision.resultSlot(), decision.type(),
							decision.dependencies(), RuntimeFunctionKind.FEEL,
							Optional.of(new RuntimeFunctionDefinition(List.of(), Optional.of(ctx), false, 0,
									decision.type())));
					bkmBySlot.put(decision.resultSlot(), ctxBkm);
					for (RuntimeContextEntry entry : ctx.entries()) {
						if (entry.expression() instanceof RuntimeFunctionDefinition fn) {
							int synthSlot = ++synthSlotCounter;
							String decName = slotNames.get(decision.resultSlot());
							String pathName = (decName != null
									? decName.replace("`", "")
									: "decision_" + decision.resultSlot()) + "." + entry.name();
							slotNames.put(synthSlot, pathName);
							RuntimeBkm synthBkm = new RuntimeBkm(synthSlot, synthSlot, fn.type(),
									decision.dependencies(), RuntimeFunctionKind.FEEL, Optional.of(fn));
							bkmBySlot.put(synthSlot, synthBkm);
						}
					}
				}
			}
		}
		for (RuntimeBkm bkm : model.businessKnowledgeModels()) {
			if (bkm.function().isPresent() && bkm.function().get().body().isPresent()
					&& bkm.function().get().body().get() instanceof RuntimeContextExpression ctx) {
				for (RuntimeContextEntry entry : ctx.entries()) {
					if (entry.expression() instanceof RuntimeFunctionDefinition fn) {
						int synthSlot = ++synthSlotCounter;
						String bkmName = slotNames.get(bkm.resultSlot());
						String pathName = (bkmName != null ? bkmName.replace("`", "") : "bkm_" + bkm.resultSlot()) + "."
								+ entry.name();
						slotNames.put(synthSlot, pathName);
						RuntimeBkm synthBkm = new RuntimeBkm(synthSlot, synthSlot, fn.type(), bkm.dependencies(),
								RuntimeFunctionKind.FEEL, Optional.of(fn));
						bkmBySlot.put(synthSlot, synthBkm);
					}
				}
			}
		}

		StructType inputSchema = options.hybridUdfFallback()
				? SparkSqlFeelValueCodec.inputSchema(model, slotNames)
				: SparkSqlSchemaGenerator.generateInputSchema(model, slotNames);

		Map<Integer, SparkSqlCapabilityAnalyzer.Capability> capabilities = new LinkedHashMap<>();
		Map<String, SparkSqlDecisionUdf> udfs = new LinkedHashMap<>();
		Map<Integer, String> udfNames = new LinkedHashMap<>();
		Map<Integer, String> fallbackExpressions = new LinkedHashMap<>();
		SparkSqlCapabilityAnalyzer analyzer = new SparkSqlCapabilityAnalyzer();
		String modelKey = modelKey(model);
		String inputStruct = "struct("
				+ String.join(", ",
						model.inputs().stream()
								.map(input -> "`" + slotNames.get(input.valueSlot()).replace("`", "``") + "`").toList())
				+ ")";
		for (RuntimeDecision decision : model.decisions()) {
			var capability = analyzer.analyze(model, decision.id(), options.hybridUdfFallback());
			capabilities.put(decision.id(), capability);
			if (!capability.nativeSql()) {
				if (!options.hybridUdfFallback() || !capability.runtimeLimitations().isEmpty()) {
					throw new UnsupportedRelationalSqlException(
							"Decision " + decision.id() + ": " + String.join("; ", capability.reasons()) + "; "
									+ String.join("; ", capability.runtimeLimitations()));
				}
				String udfName = "dmn_" + modelKey + "_" + decision.id();
				udfs.put(udfName, new SparkSqlDecisionUdf(model, decision.id()));
				udfNames.put(decision.id(), udfName);
				fallbackExpressions.put(decision.id(), udfName + "(" + inputStruct + ")");
			}
		}

		Map<Integer, RuntimeDecision> decisionMap = new HashMap<>();
		for (RuntimeDecision decision : model.decisions()) {
			decisionMap.put(decision.id(), decision);
		}

		Map<String, String> sqlFiles = new LinkedHashMap<>();

		for (RuntimeDecision decision : model.decisions()) {
			String name = "Decision_" + decision.resultSlot();
			String sqlQuery = buildCteQuery(model, decision, decisionMap, slotNames, bkmBySlot,
					options.inputTableName(), fallbackExpressions);
			sqlFiles.put(name + ".sql", sqlQuery);
		}
		if (!model.decisions().isEmpty()) {
			sqlFiles.put("AllDecisions.sql", buildAllDecisionsCteQuery(model, decisionMap, slotNames, bkmBySlot,
					options.inputTableName(), fallbackExpressions));
		}

		Map<String, String> javaSources = new HashMap<>();
		if (options.includeJavaRunner()) {
			String fqcn = options.packageName() + "." + options.className();
			String javaCode = buildJavaRunner(options, sqlFiles, model, udfNames);
			javaSources.put(fqcn, javaCode);
		}

		return new DmnSparkSqlGeneratorResult(sqlFiles, javaSources, inputSchema, udfs, capabilities);
	}

	private static String modelKey(RuntimeModel model) {
		try {
			return HexFormat.of().formatHex(
					java.security.MessageDigest.getInstance("SHA-256").digest(SparkSqlPayloadCodec.encode(model)))
					.substring(0, 24);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private String buildCteQuery(RuntimeModel model, RuntimeDecision targetDecision,
			Map<Integer, RuntimeDecision> decisionMap, Map<Integer, String> slotNames,
			Map<Integer, RuntimeBkm> bkmBySlot, String inputTable, Map<Integer, String> fallbackExpressions) {

		if (fallbackExpressions.containsKey(targetDecision.id())) {
			// Evaluate the complete subgraph from original inputs, never materialize
			// captured functions as columns.
			return "WITH _base_input AS (SELECT * FROM " + inputTable + ")\nSELECT "
					+ fallbackExpressions.get(targetDecision.id()) + " AS `"
					+ slotNames.get(targetDecision.resultSlot()).replace("`", "``") + "` FROM _base_input;\n";
		}

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
			return "SELECT NULL AS `" + targetDecision.resultSlot() + "`;";
		}

		sb.append("WITH _base_input AS (\n");
		sb.append("  SELECT * FROM ").append(inputTable).append("\n");
		sb.append(")");

		String currentTable = "_base_input";

		List<String> currentCols = new ArrayList<>();
		for (RuntimeInput inp : model.inputs()) {
			String rawName = slotNames.getOrDefault(inp.valueSlot(), "input_" + inp.valueSlot()).replace("`", "");
			if (!currentCols.contains(rawName)) {
				currentCols.add(rawName);
			}
		}

		for (int idx = 0; idx < orderedDecisions.size(); idx++) {
			RuntimeDecision dec = orderedDecisions.get(idx);
			String decName = slotNames.get(dec.resultSlot()).replace("`", "");
			String exprCode = emitDecisionExpr(dec, slotNames, bkmBySlot);
			String cteName = "_cte_" + dec.resultSlot();

			List<String> projectedCols = new ArrayList<>();
			for (String col : currentCols) {
				if (!col.equalsIgnoreCase(decName)) {
					projectedCols.add("`" + col + "`");
				}
			}
			String colsPrefix = projectedCols.isEmpty() ? "" : String.join(", ", projectedCols) + ",\n    ";

			sb.append(",\n").append(cteName).append(" AS (\n");
			sb.append("  SELECT ").append(colsPrefix);
			sb.append("(").append(exprCode).append(") AS `").append(decName).append("`\n");
			sb.append("  FROM ").append(currentTable).append("\n");
			sb.append(")");

			if (!currentCols.contains(decName)) {
				currentCols.add(decName);
			}
			currentTable = cteName;
		}

		String finalName = slotNames.get(targetDecision.resultSlot());
		sb.append("\nSELECT `").append(finalName.replace("`", "")).append("` FROM ").append(currentTable).append(";\n");

		return sb.toString();
	}

	private String buildAllDecisionsCteQuery(RuntimeModel model, Map<Integer, RuntimeDecision> decisionMap,
			Map<Integer, String> slotNames, Map<Integer, RuntimeBkm> bkmBySlot, String inputTable,
			Map<Integer, String> fallbackExpressions) {

		StringBuilder sb = new StringBuilder();
		sb.append("-- Generated Spark SQL CTE Query for all DMN Decisions\n");

		List<Integer> evalOrder = model.evaluationOrder();
		List<RuntimeDecision> orderedDecisions = new ArrayList<>();
		for (int nodeId : evalOrder) {
			if (decisionMap.containsKey(nodeId)) {
				orderedDecisions.add(decisionMap.get(nodeId));
			}
		}

		if (orderedDecisions.isEmpty()) {
			return "SELECT 1 AS `_empty`;";
		}

		sb.append("WITH _base_input AS (\n");
		sb.append("  SELECT * FROM ").append(inputTable).append("\n");
		sb.append(")");

		String currentTable = "_base_input";

		List<String> currentCols = new ArrayList<>();
		for (RuntimeInput inp : model.inputs()) {
			String rawName = slotNames.getOrDefault(inp.valueSlot(), "input_" + inp.valueSlot()).replace("`", "");
			if (!currentCols.contains(rawName)) {
				currentCols.add(rawName);
			}
		}

		List<String> allDecisionColNames = new ArrayList<>();

		for (int idx = 0; idx < orderedDecisions.size(); idx++) {
			RuntimeDecision dec = orderedDecisions.get(idx);
			String decName = slotNames.get(dec.resultSlot()).replace("`", "");
			String exprCode = fallbackExpressions.containsKey(dec.id())
					? fallbackExpressions.get(dec.id())
					: emitDecisionExpr(dec, slotNames, bkmBySlot);
			String cteName = "_cte_" + dec.resultSlot();

			List<String> projectedCols = new ArrayList<>();
			for (String col : currentCols) {
				if (!col.equalsIgnoreCase(decName)) {
					projectedCols.add("`" + col + "`");
				}
			}
			String colsPrefix = projectedCols.isEmpty() ? "" : String.join(", ", projectedCols) + ",\n    ";

			sb.append(",\n").append(cteName).append(" AS (\n");
			sb.append("  SELECT ").append(colsPrefix);
			sb.append("(").append(exprCode).append(") AS `").append(decName).append("`\n");
			sb.append("  FROM ").append(currentTable).append("\n");
			sb.append(")");

			if (!currentCols.contains(decName)) {
				currentCols.add(decName);
			}
			if (!allDecisionColNames.contains(decName)) {
				allDecisionColNames.add(decName);
			}
			currentTable = cteName;
		}

		sb.append("\nSELECT ");
		for (int i = 0; i < allDecisionColNames.size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append("`").append(allDecisionColNames.get(i)).append("`");
		}
		sb.append(" FROM ").append(currentTable).append(";\n");

		return sb.toString();
	}

	private Set<Integer> findDependencies(RuntimeDecision decision, Map<Integer, RuntimeDecision> decisionMap) {
		Set<Integer> deps = new HashSet<>();
		Queue<RuntimeDecision> queue = new LinkedList<>();
		queue.add(decision);

		while (!queue.isEmpty()) {
			RuntimeDecision current = queue.poll();
			for (int req : current.dependencies()) {
				if (decisionMap.containsKey(req) && deps.add(req)) {
					queue.add(decisionMap.get(req));
				}
				for (RuntimeDecision candidate : decisionMap.values()) {
					if (candidate.resultSlot() == req && deps.add(candidate.id())) {
						queue.add(candidate);
					}
				}
			}
		}
		return deps;
	}

	private String emitDecisionExpr(RuntimeDecision decision, Map<Integer, String> slotNames,
			Map<Integer, RuntimeBkm> bkmBySlot) {
		java.util.function.IntFunction<String> resolver = slot -> {
			String name = slotNames.get(slot);
			return name != null ? "`" + name.replace("`", "") + "`" : "slot_" + slot;
		};
		if (decision.decisionTable().isPresent()) {
			return SparkSqlExpressionEmitter.emitDecisionTable(decision.decisionTable().get(), bkmBySlot, resolver);
		} else if (decision.expression().isPresent()) {
			RuntimeExpression expr = decision.expression().get();
			String emitted = SparkSqlExpressionEmitter.emitWithBkms(expr, bkmBySlot, resolver);
			if (decision.type() != null && decision.type().kind() != io.finmsg.dmn.ir.RuntimeTypeKind.LIST
					&& decision.type().kind() != io.finmsg.dmn.ir.RuntimeTypeKind.ANY) {
				if (expr instanceof io.finmsg.dmn.ir.RuntimeFilterExpression filter) {
					if (filter.filter().type() == null
							|| filter.filter().type().kind() != io.finmsg.dmn.ir.RuntimeTypeKind.NUMBER) {
						if (!"NULL".equalsIgnoreCase(emitted.trim())) {
							return "(CASE WHEN " + emitted + " IS NULL THEN NULL WHEN size(" + emitted
									+ ") > 0 THEN element_at(" + emitted + ", 1) ELSE NULL END)";
						}
					}
				} else if (expr.type() != null && expr.type().kind() == io.finmsg.dmn.ir.RuntimeTypeKind.LIST) {
					if (!"NULL".equalsIgnoreCase(emitted.trim())) {
						return "(CASE WHEN " + emitted + " IS NULL THEN NULL WHEN size(" + emitted
								+ ") > 0 THEN element_at(" + emitted + ", 1) ELSE NULL END)";
					}
				}
			}
			return emitted;
		}
		return "NULL";
	}

	private String buildJavaRunner(DmnSparkSqlGeneratorOptions options, Map<String, String> sqlFiles,
			RuntimeModel model, Map<Integer, String> udfNames) {
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
		if (!udfNames.isEmpty()) {
			sb.append(
					"  private static final Map<String, io.finmsg.dmn.generator.sparksql.SparkSqlDecisionUdf> UDFS = new HashMap<>();\n");
		}
		sb.append("  static {\n");
		if (!udfNames.isEmpty()) {
			String payload = Base64.getEncoder().encodeToString(SparkSqlPayloadCodec.encode(model));
			sb.append("    String modelPayload = String.join(\"\",\n");
			for (int offset = 0; offset < payload.length(); offset += 16000) {
				if (offset > 0)
					sb.append(",\n");
				sb.append("      \"").append(payload, offset, Math.min(offset + 16000, payload.length())).append("\"");
			}
			sb.append(");\n");
			udfNames.forEach((id, name) -> sb.append("    UDFS.put(\"").append(name)
					.append("\", io.finmsg.dmn.generator.sparksql.SparkSqlDecisionUdf.fromModelPayload(modelPayload, ")
					.append(id).append("));\n"));
		}

		for (Map.Entry<String, String> entry : sqlFiles.entrySet()) {
			String key = entry.getKey().replace(".sql", "");
			String escapedSql = entry.getValue().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n",
					"\\n\" +\n      \"");
			sb.append("    SQL_QUERIES.put(\"").append(key).append("\", \"").append(escapedSql).append("\");\n");
		}

		sb.append("  }\n\n");

		sb.append(
				"  public static Dataset<Row> evaluate(SparkSession spark, Dataset<Row> inputDf, String decisionName) {\n");
		sb.append("    String sqlQuery = SQL_QUERIES.get(decisionName);\n");
		sb.append("    if (sqlQuery == null) {\n");
		sb.append("      throw new IllegalArgumentException(\"Unknown decision name: \" + decisionName);\n");
		sb.append("    }\n");
		sb.append("    inputDf.createOrReplaceTempView(\"").append(options.inputTableName()).append("\");\n");
		if (!udfNames.isEmpty())
			sb.append("    UDFS.forEach((name, udf) -> spark.udf().register(name, udf, udf.returnType()));\n");
		sb.append("    return spark.sql(sqlQuery);\n");
		sb.append("  }\n\n");

		sb.append("  public static Map<String, String> getSqlFiles() {\n");
		sb.append("    return Map.copyOf(SQL_QUERIES);\n");
		sb.append("  }\n");
		sb.append("}\n");

		return sb.toString();
	}
}
