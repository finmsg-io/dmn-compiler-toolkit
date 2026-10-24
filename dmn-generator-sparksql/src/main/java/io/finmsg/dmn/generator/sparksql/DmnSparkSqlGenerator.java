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

		RuntimeModel originalModel = optimizedModel.model();
		RuntimeModel model = options.hybridUdfFallback()
				? SparkSqlTemporalChain.lowerDependencies(SparkSqlRankedContextLoop.lowerDependencies(
						SparkSqlScalarGraph.lowerDependencies(SparkSqlStaticFunctionLowerer.lower(originalModel))))
				: originalModel;
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

		Set<Integer> calendarSlots = options.hybridUdfFallback() ? SparkSqlCalendarInput.slots(model) : Set.of();
		Set<Integer> stringSlots = options.hybridUdfFallback() ? SparkSqlStringInput.slots(model) : Set.of();
		Set<Integer> positionSlots = options.hybridUdfFallback() ? SparkSqlListPositionInput.slots(model) : Set.of();
		Set<Integer> numericFilters = options.hybridUdfFallback() ? SparkSqlNumericFilterInput.ids(model) : Set.of();
		Set<Integer> temporalTables = options.hybridUdfFallback() ? SparkSqlTemporalTableInput.ids(model) : Set.of();
		Set<Integer> joins = options.hybridUdfFallback() ? SparkSqlIndexedJoinInput.ids(model) : Set.of();
		Set<Integer> sorts = options.hybridUdfFallback() ? SparkSqlExactSortInput.ids(model) : Set.of();
		Set<Integer> products = options.hybridUdfFallback() ? SparkSqlExactProductInput.ids(model) : Set.of();
		Set<Integer> arithmetic = new LinkedHashSet<>();
		if (options.hybridUdfFallback()) {
			arithmetic.addAll(SparkSqlExactArithmeticInput.ids(model));
			arithmetic.addAll(SparkSqlFactorLoopInput.ids(model));
			arithmetic.addAll(SparkSqlNumericLoopInput.ids(model));
			arithmetic.addAll(SparkSqlScalarGraph.ids(model));
			arithmetic.addAll(SparkSqlRankedContextLoop.ids(model));
			arithmetic.addAll(SparkSqlTemporalChain.ids(model));
		}
		StructType inputSchema = options.hybridUdfFallback()
				? SparkSqlFeelValueCodec.calendarInputSchema(model, slotNames, calendarSlots, stringSlots)
				: SparkSqlSchemaGenerator.generateInputSchema(model, slotNames);

		Map<Integer, SparkSqlCapabilityAnalyzer.Capability> capabilities = new LinkedHashMap<>();
		SparkSqlCapabilityAnalyzer analyzer = new SparkSqlCapabilityAnalyzer();
		for (var decision : model.decisions())
			capabilities.put(decision.id(), analyzer.analyze(model, decision.id(), options.hybridUdfFallback()));
		boolean fallback = options.hybridUdfFallback() && capabilities.values().stream().anyMatch(c -> !c.nativeSql());
		if (fallback)
			inputSchema = SparkSqlFallbackTransport.schema(inputSchema, model, slotNames);
		Map<String, SparkSqlDecisionUdf> udfs = new LinkedHashMap<>();
		Map<Integer, String> udfNames = new LinkedHashMap<>();
		Map<Integer, String> fallbackExpressions = new LinkedHashMap<>();
		String modelKey = modelKey(originalModel);
		String inputStruct = "struct(" + String.join(", ",
				model.inputs().stream()
						.map(input -> "`" + (fallback && SparkSqlFallbackTransport.numeric(input.type())
								? SparkSqlFallbackTransport.column(input.valueSlot(), slotNames)
								: slotNames.get(input.valueSlot())).replace("`", "``") + "`")
						.toList())
				+ ")";
		for (RuntimeDecision decision : model.decisions()) {
			var capability = capabilities.get(decision.id());
			if (!capability.nativeSql()) {
				if (!options.hybridUdfFallback() || !capability.runtimeLimitations().isEmpty()) {
					throw new UnsupportedRelationalSqlException(
							"Decision " + decision.id() + ": " + String.join("; ", capability.reasons()) + "; "
									+ String.join("; ", capability.runtimeLimitations()));
				}
				String udfName = "dmn_" + modelKey + "_" + decision.id();
				udfs.put(udfName, new SparkSqlDecisionUdf(originalModel, decision.id()));
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
					options.inputTableName(), fallbackExpressions, calendarSlots, stringSlots, positionSlots,
					numericFilters, temporalTables, joins, sorts, products, arithmetic);
			sqlFiles.put(name + ".sql", sqlQuery);
		}
		if (!model.decisions().isEmpty()) {
			sqlFiles.put("AllDecisions.sql",
					buildAllDecisionsCteQuery(model, decisionMap, slotNames, bkmBySlot, options.inputTableName(),
							fallbackExpressions, calendarSlots, stringSlots, positionSlots, numericFilters,
							temporalTables, joins, sorts, products, arithmetic));
		}

		Map<String, String> javaSources = new HashMap<>();
		if (options.includeJavaRunner()) {
			String fqcn = options.packageName() + "." + options.className();
			String javaCode = buildJavaRunner(options, sqlFiles, originalModel, udfNames);
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
			Map<Integer, RuntimeBkm> bkmBySlot, String inputTable, Map<Integer, String> fallbackExpressions,
			Set<Integer> calendarSlots, Set<Integer> stringSlots, Set<Integer> positionSlots,
			Set<Integer> numericFilters, Set<Integer> temporalTables, Set<Integer> joins, Set<Integer> sorts,
			Set<Integer> products, Set<Integer> arithmetic) {

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
		if (!fallbackExpressions.isEmpty())
			model.inputs().stream().filter(i -> SparkSqlFallbackTransport.numeric(i.type()))
					.forEach(i -> currentCols.add(SparkSqlFallbackTransport.column(i.valueSlot(), slotNames)));
		calendarSlots.forEach(slot -> currentCols.add(SparkSqlCalendarInput.column(slot, slotNames)));
		stringSlots.forEach(slot -> currentCols.add(SparkSqlStringInput.column(slot, slotNames)));
		positionSlots.forEach(slot -> currentCols.add(SparkSqlListPositionInput.column(slot, slotNames)));
		numericFilters.forEach(id -> currentCols.add(SparkSqlNumericFilterInput.column(id, slotNames)));
		temporalTables.forEach(id -> currentCols.add(SparkSqlTemporalTableInput.column(id, slotNames)));
		joins.forEach(id -> currentCols.add(SparkSqlIndexedJoinInput.column(id, slotNames)));
		sorts.forEach(id -> currentCols.add(SparkSqlExactSortInput.column(id, slotNames)));
		products.forEach(id -> currentCols.add(SparkSqlExactProductInput.column(id, slotNames)));
		arithmetic.forEach(id -> currentCols.add(exactProjectionColumn(model, decisionMap.get(id), slotNames)));
		SparkSqlLookupKeyInput.slots(model).stream().filter(stringSlots::contains)
				.forEach(slot -> currentCols.add(SparkSqlLookupKeyInput.column(slot, slotNames)));
		for (RuntimeInput inp : model.inputs()) {
			String rawName = slotNames.getOrDefault(inp.valueSlot(), "input_" + inp.valueSlot()).replace("`", "");
			if (!currentCols.contains(rawName)) {
				currentCols.add(rawName);
			}
		}

		for (int idx = 0; idx < orderedDecisions.size(); idx++) {
			RuntimeDecision dec = orderedDecisions.get(idx);
			String decName = slotNames.get(dec.resultSlot()).replace("`", "");
			String exprCode = emitDecisionExpr(dec, slotNames, bkmBySlot, model, calendarSlots, stringSlots,
					positionSlots, numericFilters, temporalTables, joins, sorts, products, arithmetic);
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
			Map<Integer, String> fallbackExpressions, Set<Integer> calendarSlots, Set<Integer> stringSlots,
			Set<Integer> positionSlots, Set<Integer> numericFilters, Set<Integer> temporalTables, Set<Integer> joins,
			Set<Integer> sorts, Set<Integer> products, Set<Integer> arithmetic) {

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
		if (!fallbackExpressions.isEmpty())
			model.inputs().stream().filter(i -> SparkSqlFallbackTransport.numeric(i.type()))
					.forEach(i -> currentCols.add(SparkSqlFallbackTransport.column(i.valueSlot(), slotNames)));
		calendarSlots.forEach(slot -> currentCols.add(SparkSqlCalendarInput.column(slot, slotNames)));
		stringSlots.forEach(slot -> currentCols.add(SparkSqlStringInput.column(slot, slotNames)));
		positionSlots.forEach(slot -> currentCols.add(SparkSqlListPositionInput.column(slot, slotNames)));
		numericFilters.forEach(id -> currentCols.add(SparkSqlNumericFilterInput.column(id, slotNames)));
		temporalTables.forEach(id -> currentCols.add(SparkSqlTemporalTableInput.column(id, slotNames)));
		joins.forEach(id -> currentCols.add(SparkSqlIndexedJoinInput.column(id, slotNames)));
		sorts.forEach(id -> currentCols.add(SparkSqlExactSortInput.column(id, slotNames)));
		products.forEach(id -> currentCols.add(SparkSqlExactProductInput.column(id, slotNames)));
		arithmetic.forEach(id -> currentCols.add(exactProjectionColumn(model, decisionMap.get(id), slotNames)));
		SparkSqlLookupKeyInput.slots(model).stream().filter(stringSlots::contains)
				.forEach(slot -> currentCols.add(SparkSqlLookupKeyInput.column(slot, slotNames)));
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
					: emitDecisionExpr(dec, slotNames, bkmBySlot, model, calendarSlots, stringSlots, positionSlots,
							numericFilters, temporalTables, joins, sorts, products, arithmetic);
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

	private static String exactProjectionColumn(RuntimeModel model, RuntimeDecision d, Map<Integer, String> names) {
		if (SparkSqlTemporalChain.supports(model, d))
			return SparkSqlTemporalChain.column(d.id(), names);
		if (SparkSqlRankedContextLoop.supports(model, d))
			return SparkSqlScalarGraph.column(d.id(), names);
		if (SparkSqlScalarGraph.supports(model, d))
			return SparkSqlScalarGraph.column(d.id(), names);
		if (SparkSqlNumericLoopInput.supports(model, d))
			return SparkSqlNumericLoopInput.column(d.id(), names);
		if (SparkSqlFactorLoopInput.supports(model, d))
			return SparkSqlFactorLoopInput.column(d.id(), names);
		return SparkSqlExactArithmeticInput.column(d.id(), names);
	}

	private String emitDecisionExpr(RuntimeDecision decision, Map<Integer, String> slotNames,
			Map<Integer, RuntimeBkm> bkmBySlot, RuntimeModel model, Set<Integer> calendarSlots,
			Set<Integer> stringSlots, Set<Integer> positionSlots, Set<Integer> numericFilters,
			Set<Integer> temporalTables, Set<Integer> joins, Set<Integer> sorts, Set<Integer> products,
			Set<Integer> arithmetic) {
		if (arithmetic.contains(decision.id())) {
			if (SparkSqlTemporalChain.supports(model, decision))
				return SparkSqlTemporalChain.sql(model, decision, slotNames);
			if (SparkSqlRankedContextLoop.supports(model, decision))
				return SparkSqlRankedContextLoop.sql(model, decision, slotNames);
			if (SparkSqlScalarGraph.supports(model, decision))
				return SparkSqlScalarGraph.sql(model, decision, slotNames);
			if (SparkSqlNumericLoopInput.supports(model, decision))
				return SparkSqlNumericLoopInput.sql(model, decision, slotNames);
			if (SparkSqlFactorLoopInput.supports(model, decision))
				return SparkSqlFactorLoopInput.sql(decision, slotNames);
			return SparkSqlExactArithmeticInput.sql(model, decision, slotNames);
		}
		if (products.contains(decision.id()))
			return SparkSqlExactProductInput.sql(decision, slotNames);
		if (sorts.contains(decision.id()))
			return SparkSqlExactSortInput.sql(model, decision, slotNames);
		if (joins.contains(decision.id()))
			return SparkSqlIndexedJoinInput.sql(decision, slotNames);
		if (temporalTables.contains(decision.id()))
			return SparkSqlTemporalTableInput.sql(model, decision, slotNames);
		if (numericFilters.contains(decision.id()))
			return SparkSqlNumericFilterInput.sql(model, decision, slotNames);
		Integer positionSlot = SparkSqlListPositionInput.inputSlot(model, decision);
		if (positionSlot != null && positionSlots.contains(positionSlot))
			return SparkSqlListPositionInput.sql(model, decision, slotNames);
		String stringSelection = SparkSqlStringListTerminal.sql(model, decision, slotNames);
		if (stringSelection != null)
			return stringSelection;
		Integer lookupSlot = SparkSqlLookupKeyInput.inputSlot(model, decision);
		if (lookupSlot != null && stringSlots.contains(lookupSlot))
			return SparkSqlLookupKeyInput.sql(model, decision, slotNames);
		var projectedStrings = SparkSqlStringInput.inputSlots(model, decision);
		if (!projectedStrings.isEmpty() && stringSlots.containsAll(projectedStrings))
			return SparkSqlStringInput.sql(decision.expression().orElseThrow(), slotNames);
		var dateTableSlots = SparkSqlDateTableInput.slots(model, decision);
		if (!dateTableSlots.isEmpty() && calendarSlots.containsAll(dateTableSlots))
			return SparkSqlDateTableInput.sql(model, decision, slotNames);
		Integer calendarSlot = SparkSqlCalendarInput.inputSlot(model, decision);
		if (calendarSlot != null && calendarSlots.contains(calendarSlot)
				&& SparkSqlDateComparisonInput.inputSlot(model, decision) != null)
			return SparkSqlDateComparisonInput.sql(model, decision,
					SparkSqlCalendarInput.column(calendarSlot, slotNames), slotNames);
		if (calendarSlot != null && calendarSlots.contains(calendarSlot)
				&& SparkSqlCalendarArithmetic.inputSlot(model, decision) != null)
			return SparkSqlCalendarArithmetic.sql(model, decision,
					SparkSqlCalendarInput.column(calendarSlot, slotNames));
		if (calendarSlot != null && calendarSlots.contains(calendarSlot))
			return SparkSqlCalendarInput.sql(decision, SparkSqlCalendarInput.column(calendarSlot, slotNames));
		java.util.function.IntFunction<String> resolver = slot -> {
			String name = slotNames.get(slot);
			return name != null ? "`" + name.replace("`", "") + "`" : "slot_" + slot;
		};
		if (decision.decisionTable().isPresent()) {
			return SparkSqlExpressionEmitter.emitDecisionTable(decision.decisionTable().get(), bkmBySlot, resolver);
		} else if (decision.expression().isPresent()) {
			RuntimeExpression expr = decision.expression().get();
			Object terminalTemporal = SparkSqlExpressionEmitter.nativeTerminalTemporalValue(decision);
			if (terminalTemporal != null)
				return "X'" + java.util.HexFormat.of().formatHex(SparkSqlFeelValueCodec.encode(terminalTemporal)) + "'";
			if (SparkSqlCapabilityAnalyzer.nativeConstantTemporalResult(decision)) {
				var constant = (io.finmsg.dmn.ir.RuntimeConstant) expr;
				return "'" + constant.value().replace("'", "''") + "'";
			}
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
