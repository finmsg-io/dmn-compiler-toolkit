package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Emitter for lowering RuntimeExpression nodes and Decision Tables into pure Spark SQL expressions.
 */
public final class SparkSqlExpressionEmitter {

	private SparkSqlExpressionEmitter() {
	}

	public static String emit(RuntimeExpression expr, IntFunction<String> slotNameResolver) {
		if (expr == null) {
			return "NULL";
		}
		return switch (expr) {
			case RuntimeConstant constant -> emitConstant(constant);
			case RuntimeValueReference ref -> slotNameResolver.apply(ref.sourceSlot());
			case RuntimeLocalReference local -> slotNameResolver.apply(local.localSlot());
			case RuntimeBinaryExpression binary -> emitBinary(binary, slotNameResolver);
			case RuntimeUnaryExpression unary -> emitUnary(unary, slotNameResolver);
			case RuntimeConditionalExpression cond -> "CASE WHEN (" + emit(cond.condition(), slotNameResolver)
					+ ") THEN (" + emit(cond.thenExpression(), slotNameResolver) + ") ELSE ("
					+ emit(cond.elseExpression(), slotNameResolver) + ") END";
			case RuntimePathExpression path ->
				"(" + emit(path.source(), slotNameResolver) + "." + sanitizeColumn(path.member()) + ")";
			case RuntimeContextExpression ctx -> emitContext(ctx, slotNameResolver);
			case RuntimeListExpression list -> emitList(list, slotNameResolver);
			case RuntimeFunctionCall fn -> emitFunctionCall(fn, slotNameResolver);
			case RuntimeBetweenExpression btn ->
				"(" + emit(btn.value(), slotNameResolver) + " >= " + emit(btn.lower(), slotNameResolver) + " AND "
						+ emit(btn.value(), slotNameResolver) + " <= " + emit(btn.upper(), slotNameResolver) + ")";
			case RuntimeInExpression inExpr -> "(" + emit(inExpr.value(), slotNameResolver) + " IS NOT NULL)";
			case RuntimeRangeExpression range -> emitRange(range, slotNameResolver);
			default -> "NULL";
		};
	}

	private static String emitConstant(RuntimeConstant constant) {
		if (constant.kind() == RuntimeConstantKind.NULL || constant.value() == null) {
			return "NULL";
		}
		return switch (constant.kind()) {
			case BOOLEAN -> String.valueOf(constant.value()).toUpperCase();
			case STRING -> "'" + escapeSqlString(constant.value()) + "'";
			case NUMBER -> constant.value();
			default -> "'" + escapeSqlString(constant.value()) + "'";
		};
	}

	private static String emitBinary(RuntimeBinaryExpression binary, IntFunction<String> slotNameResolver) {
		String left = emit(binary.left(), slotNameResolver);
		String right = emit(binary.right(), slotNameResolver);
		if (binary.operator() == RuntimeBinaryOperator.ADD) {
			if (binary.type().kind() == RuntimeTypeKind.STRING || binary.type().kind() == RuntimeTypeKind.LIST) {
				return "concat(" + left + ", " + right + ")";
			}
			return "(" + left + " + " + right + ")";
		}
		return switch (binary.operator()) {
			case ADD -> "(" + left + " + " + right + ")";
			case SUBTRACT -> "(" + left + " - " + right + ")";
			case MULTIPLY -> "(" + left + " * " + right + ")";
			case DIVIDE -> "(CASE WHEN " + right + " = 0 THEN NULL ELSE (" + left + " / " + right + ") END)";
			case EQUAL -> "(" + left + " = " + right + ")";
			case NOT_EQUAL -> "(" + left + " <> " + right + ")";
			case LESS -> "(" + left + " < " + right + ")";
			case LESS_EQUAL -> "(" + left + " <= " + right + ")";
			case GREATER -> "(" + left + " > " + right + ")";
			case GREATER_EQUAL -> "(" + left + " >= " + right + ")";
			case AND -> "(" + left + " AND " + right + ")";
			case OR -> "(" + left + " OR " + right + ")";
			case POWER -> "pow(" + left + ", " + right + ")";
		};
	}

	private static String emitUnary(RuntimeUnaryExpression unary, IntFunction<String> slotNameResolver) {
		String op = emit(unary.operand(), slotNameResolver);
		return switch (unary.operator()) {
			case POSITIVE -> op;
			case NEGATE -> "(-" + op + ")";
			case NOT -> "(NOT " + op + ")";
		};
	}

	private static String emitRange(RuntimeRangeExpression range, IntFunction<String> slotNameResolver) {
		String lowerStr = range.lower().map(e -> emit(e, slotNameResolver)).orElse("NULL");
		String upperStr = range.upper().map(e -> emit(e, slotNameResolver)).orElse("NULL");
		return "named_struct('lower', " + lowerStr + ", 'upper', " + upperStr + ")";
	}

	private static String emitContext(RuntimeContextExpression ctx, IntFunction<String> slotNameResolver) {
		if (ctx.entries().isEmpty())
			return "named_struct()";
		StringBuilder sb = new StringBuilder("named_struct(");
		for (int i = 0; i < ctx.entries().size(); i++) {
			if (i > 0)
				sb.append(", ");
			RuntimeContextEntry entry = ctx.entries().get(i);
			sb.append("'").append(escapeSqlString(entry.name())).append("', ")
					.append(emit(entry.expression(), slotNameResolver));
		}
		sb.append(")");
		return sb.toString();
	}

	private static String emitList(RuntimeListExpression list, IntFunction<String> slotNameResolver) {
		if (list.elements().isEmpty())
			return "array()";
		StringBuilder sb = new StringBuilder("array(");
		for (int i = 0; i < list.elements().size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(emit(list.elements().get(i), slotNameResolver));
		}
		sb.append(")");
		return sb.toString();
	}

	private static String emitFunctionCall(RuntimeFunctionCall fn, IntFunction<String> slotNameResolver) {
		String name = fn.function().toLowerCase();
		List<RuntimeExpression> args = fn.arguments();

		return switch (name) {
			// String functions
			case "string length", "length" -> "length(" + emitArg(args, 0, slotNameResolver) + ")";
			case "upper case", "upper" -> "upper(" + emitArg(args, 0, slotNameResolver) + ")";
			case "lower case", "lower" -> "lower(" + emitArg(args, 0, slotNameResolver) + ")";
			case "substring" -> {
				if (args.size() == 2) {
					yield "substring(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver)
							+ ")";
				}
				yield "substring(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver)
						+ ", " + emitArg(args, 2, slotNameResolver) + ")";
			}
			case "contains" ->
				"contains(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver) + ")";
			case "starts with", "startswith" ->
				"startswith(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver) + ")";
			case "ends with", "endswith" ->
				"endswith(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver) + ")";
			case "substring before" -> "substring_index(" + emitArg(args, 0, slotNameResolver) + ", "
					+ emitArg(args, 1, slotNameResolver) + ", 1)";
			case "substring after" -> {
				String str = emitArg(args, 0, slotNameResolver);
				String match = emitArg(args, 1, slotNameResolver);
				yield "(CASE WHEN instr(" + str + ", " + match + ") > 0 THEN substring(" + str + ", instr(" + str
						+ ", " + match + ") + length(" + match + ")) ELSE '' END)";
			}
			case "replace" -> "regexp_replace(" + emitArg(args, 0, slotNameResolver) + ", "
					+ emitArg(args, 1, slotNameResolver) + ", " + emitArg(args, 2, slotNameResolver) + ")";
			case "matches" ->
				"(" + emitArg(args, 0, slotNameResolver) + " rlike " + emitArg(args, 1, slotNameResolver) + ")";
			case "split" ->
				"split(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver) + ")";
			case "trim" -> "trim(" + emitArg(args, 0, slotNameResolver) + ")";
			case "concat" -> "concat(" + emitArgsJoined(args, slotNameResolver) + ")";
			case "string" -> "cast(" + emitArg(args, 0, slotNameResolver) + " as string)";

			// Math functions
			case "abs" -> "abs(" + emitArg(args, 0, slotNameResolver) + ")";
			case "floor" -> "floor(" + emitArg(args, 0, slotNameResolver) + ")";
			case "ceiling", "ceil" -> "ceil(" + emitArg(args, 0, slotNameResolver) + ")";
			case "round" -> {
				if (args.size() == 1) {
					yield "round(" + emitArg(args, 0, slotNameResolver) + ", 0)";
				}
				yield "round(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver) + ")";
			}
			case "sqrt" -> "sqrt(" + emitArg(args, 0, slotNameResolver) + ")";
			case "exp" -> "exp(" + emitArg(args, 0, slotNameResolver) + ")";
			case "ln", "log" -> "ln(" + emitArg(args, 0, slotNameResolver) + ")";
			case "modulo", "mod" -> "(CASE WHEN " + emitArg(args, 1, slotNameResolver) + " = 0 THEN NULL ELSE pmod("
					+ emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver) + ") END)";
			case "odd" -> "(pmod(cast(" + emitArg(args, 0, slotNameResolver) + " as bigint), 2) <> 0)";
			case "even" -> "(pmod(cast(" + emitArg(args, 0, slotNameResolver) + " as bigint), 2) = 0)";
			case "min" -> {
				if (args.size() == 1) {
					yield "array_min(" + emitArg(args, 0, slotNameResolver) + ")";
				}
				yield "least(" + emitArgsJoined(args, slotNameResolver) + ")";
			}
			case "max" -> {
				if (args.size() == 1) {
					yield "array_max(" + emitArg(args, 0, slotNameResolver) + ")";
				}
				yield "greatest(" + emitArgsJoined(args, slotNameResolver) + ")";
			}
			case "sum" -> {
				if (args.size() == 1) {
					yield "aggregate(" + emitArg(args, 0, slotNameResolver) + ", 0.0D, (acc, x) -> acc + x)";
				}
				yield "(" + String.join(" + ", args.stream().map(a -> emit(a, slotNameResolver)).toList()) + ")";
			}
			case "product" -> {
				if (args.size() == 1) {
					yield "aggregate(" + emitArg(args, 0, slotNameResolver) + ", 1.0D, (acc, x) -> acc * x)";
				}
				yield "(" + String.join(" * ", args.stream().map(a -> emit(a, slotNameResolver)).toList()) + ")";
			}
			case "mean", "avg" -> {
				if (args.size() == 1) {
					String arr = emitArg(args, 0, slotNameResolver);
					yield "(aggregate(" + arr + ", 0.0D, (acc, x) -> acc + x) / size(" + arr + "))";
				}
				String sumExpr = "(" + String.join(" + ", args.stream().map(a -> emit(a, slotNameResolver)).toList())
						+ ")";
				yield "(" + sumExpr + " / " + args.size() + ".0)";
			}
			case "coalesce" -> "coalesce(" + emitArgsJoined(args, slotNameResolver) + ")";

			// Temporal functions
			case "date" -> "to_date(" + emitArg(args, 0, slotNameResolver) + ")";
			case "date and time" -> "to_timestamp(" + emitArg(args, 0, slotNameResolver) + ")";
			case "time" -> "to_timestamp(concat('1970-01-01T', " + emitArg(args, 0, slotNameResolver) + "))";
			case "year" -> "year(" + emitArg(args, 0, slotNameResolver) + ")";
			case "month" -> "month(" + emitArg(args, 0, slotNameResolver) + ")";
			case "day" -> "day(" + emitArg(args, 0, slotNameResolver) + ")";
			case "hour" -> "hour(" + emitArg(args, 0, slotNameResolver) + ")";
			case "minute" -> "minute(" + emitArg(args, 0, slotNameResolver) + ")";
			case "second" -> "second(" + emitArg(args, 0, slotNameResolver) + ")";
			case "today" -> "current_date()";
			case "now" -> "current_timestamp()";

			// List / Array functions
			case "list contains" -> "array_contains(" + emitArg(args, 0, slotNameResolver) + ", "
					+ emitArg(args, 1, slotNameResolver) + ")";
			case "count" -> "size(" + emitArg(args, 0, slotNameResolver) + ")";
			case "reverse" -> "reverse(" + emitArg(args, 0, slotNameResolver) + ")";
			case "flatten" -> "flatten(" + emitArg(args, 0, slotNameResolver) + ")";
			case "distinct values" -> "array_distinct(" + emitArg(args, 0, slotNameResolver) + ")";
			case "union" -> "array_union(" + emitArgsJoined(args, slotNameResolver) + ")";
			case "append" -> "concat(" + emitArg(args, 0, slotNameResolver) + ", array("
					+ emitArg(args, 1, slotNameResolver) + "))";
			case "concatenate" -> "concat(" + emitArgsJoined(args, slotNameResolver) + ")";
			case "sublist" -> {
				if (args.size() == 2) {
					yield "slice(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver)
							+ ", size(" + emitArg(args, 0, slotNameResolver) + "))";
				}
				yield "slice(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver) + ", "
						+ emitArg(args, 2, slotNameResolver) + ")";
			}

			default -> {
				StringBuilder sb = new StringBuilder(sanitizeName(name)).append("(");
				sb.append(emitArgsJoined(args, slotNameResolver));
				sb.append(")");
				yield sb.toString();
			}
		};
	}

	private static String emitArg(List<RuntimeExpression> args, int index, IntFunction<String> slotNameResolver) {
		if (index >= args.size())
			return "NULL";
		return emit(args.get(index), slotNameResolver);
	}

	private static String emitArgsJoined(List<RuntimeExpression> args, IntFunction<String> slotNameResolver) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(emit(args.get(i), slotNameResolver));
		}
		return sb.toString();
	}

	public static String emitUnaryTests(String inputCol, RuntimeUnaryTests tests,
			IntFunction<String> slotNameResolver) {
		if (tests == null || tests.tests().isEmpty() || tests.wildcard()) {
			return "TRUE";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < tests.tests().size(); i++) {
			if (i > 0)
				sb.append(" OR ");
			RuntimeUnaryTest test = tests.tests().get(i);
			if (test instanceof RuntimeExpressionUnaryTest exprTest) {
				if (exprTest.expression() instanceof RuntimeConstant rc && rc.kind() == RuntimeConstantKind.NULL) {
					sb.append("(").append(inputCol).append(" IS NULL)");
				} else {
					sb.append("(").append(inputCol).append(" = ").append(emit(exprTest.expression(), slotNameResolver))
							.append(")");
				}
			} else if (test instanceof RuntimeComparisonUnaryTest compTest) {
				if (compTest.endpoint() instanceof RuntimeConstant rc && rc.kind() == RuntimeConstantKind.NULL) {
					if (compTest.operator() == RuntimeUnaryTestOperator.EQUAL) {
						sb.append("(").append(inputCol).append(" IS NULL)");
					} else {
						sb.append("(").append(inputCol).append(" IS NOT NULL)");
					}
				} else {
					String op = switch (compTest.operator()) {
						case EQUAL -> "=";
						case NOT_EQUAL -> "<>";
						case LESS -> "<";
						case LESS_EQUAL -> "<=";
						case GREATER -> ">";
						case GREATER_EQUAL -> ">=";
					};
					sb.append("(").append(inputCol).append(" ").append(op).append(" ")
							.append(emit(compTest.endpoint(), slotNameResolver)).append(")");
				}
			} else if (test instanceof RuntimeRangeUnaryTest rangeTest) {
				RuntimeRangeExpression range = rangeTest.range();
				String lowerOp = range.lowerBoundary() == RuntimeRangeBoundary.CLOSED ? ">=" : ">";
				String upperOp = range.upperBoundary() == RuntimeRangeBoundary.CLOSED ? "<=" : "<";
				if (range.lower().isPresent() && range.upper().isPresent()) {
					sb.append("(").append(inputCol).append(" ").append(lowerOp).append(" ")
							.append(emit(range.lower().get(), slotNameResolver)).append(" AND ").append(inputCol)
							.append(" ").append(upperOp).append(" ").append(emit(range.upper().get(), slotNameResolver))
							.append(")");
				} else if (range.lower().isPresent()) {
					sb.append("(").append(inputCol).append(" ").append(lowerOp).append(" ")
							.append(emit(range.lower().get(), slotNameResolver)).append(")");
				} else if (range.upper().isPresent()) {
					sb.append("(").append(inputCol).append(" ").append(upperOp).append(" ")
							.append(emit(range.upper().get(), slotNameResolver)).append(")");
				} else {
					sb.append("TRUE");
				}
			} else {
				sb.append("TRUE");
			}
		}
		if (tests.negated()) {
			return "NOT (" + sb.toString() + ")";
		}
		return sb.toString();
	}

	public static String emitDecisionTable(RuntimeDecisionTable table, IntFunction<String> slotNameResolver) {
		RuntimeHitPolicy hitPolicy = table.hitPolicy();

		if (hitPolicy == RuntimeHitPolicy.COLLECT && table.aggregation().isPresent()) {
			RuntimeAggregation agg = table.aggregation().get();
			return emitCollectDecisionTable(table, agg, slotNameResolver);
		}

		if (hitPolicy == RuntimeHitPolicy.COLLECT || hitPolicy == RuntimeHitPolicy.RULE_ORDER) {
			return emitListCollectDecisionTable(table, slotNameResolver);
		}

		// Standard CASE WHEN for UNIQUE, FIRST, ANY, PRIORITY
		StringBuilder sb = new StringBuilder("CASE\n");
		for (int r = 0; r < table.rules().size(); r++) {
			RuntimeDecisionTableRule rule = table.rules().get(r);
			sb.append("    WHEN ").append(emitRuleCondition(table, rule, slotNameResolver));
			sb.append(" THEN ").append(emitRuleOutput(table, rule, slotNameResolver)).append("\n");
		}
		sb.append("    ELSE NULL\n  END");
		return sb.toString();
	}

	private static String emitRuleCondition(RuntimeDecisionTable table, RuntimeDecisionTableRule rule,
			IntFunction<String> slotNameResolver) {
		if (rule.inputEntries().isEmpty()) {
			return "TRUE";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < rule.inputEntries().size(); i++) {
			if (i > 0)
				sb.append(" AND ");
			RuntimeDecisionTableInput input = table.inputs().get(i);
			String inputExpr = emit(input.expression(), slotNameResolver);
			RuntimeUnaryTests tests = rule.inputEntries().get(i);
			sb.append(emitUnaryTests(inputExpr, tests, slotNameResolver));
		}
		return sb.toString();
	}

	private static String emitRuleOutput(RuntimeDecisionTable table, RuntimeDecisionTableRule rule,
			IntFunction<String> slotNameResolver) {
		if (rule.outputEntries().isEmpty()) {
			return "NULL";
		}
		if (table.outputs().size() > 1) {
			StringBuilder sb = new StringBuilder("named_struct(");
			for (int o = 0; o < table.outputs().size(); o++) {
				if (o > 0)
					sb.append(", ");
				String name = table.outputs().get(o).name().orElse("output" + (o + 1));
				String valExpr = o < rule.outputEntries().size() ? emit(rule.outputEntries().get(o), slotNameResolver)
						: "NULL";
				sb.append("'").append(escapeSqlString(name)).append("', ").append(valExpr);
			}
			sb.append(")");
			return sb.toString();
		}
		return emit(rule.outputEntries().get(0), slotNameResolver);
	}

	private static String emitCollectDecisionTable(RuntimeDecisionTable table, RuntimeAggregation agg,
			IntFunction<String> slotNameResolver) {
		return switch (agg) {
			case SUM -> {
				StringBuilder sb = new StringBuilder("coalesce(");
				for (int r = 0; r < table.rules().size(); r++) {
					RuntimeDecisionTableRule rule = table.rules().get(r);
					if (r > 0)
						sb.append(" + ");
					sb.append("(CASE WHEN ").append(emitRuleCondition(table, rule, slotNameResolver)).append(" THEN ")
							.append(emitRuleOutput(table, rule, slotNameResolver)).append(" ELSE 0 END)");
				}
				sb.append(", 0)");
				yield sb.toString();
			}
			case COUNT -> {
				StringBuilder sb = new StringBuilder("(");
				for (int r = 0; r < table.rules().size(); r++) {
					RuntimeDecisionTableRule rule = table.rules().get(r);
					if (r > 0)
						sb.append(" + ");
					sb.append("(CASE WHEN ").append(emitRuleCondition(table, rule, slotNameResolver))
							.append(" THEN 1 ELSE 0 END)");
				}
				sb.append(")");
				yield sb.toString();
			}
			case MIN -> {
				StringBuilder sb = new StringBuilder("least(");
				for (int r = 0; r < table.rules().size(); r++) {
					RuntimeDecisionTableRule rule = table.rules().get(r);
					if (r > 0)
						sb.append(", ");
					sb.append("(CASE WHEN ").append(emitRuleCondition(table, rule, slotNameResolver)).append(" THEN ")
							.append(emitRuleOutput(table, rule, slotNameResolver)).append(" ELSE NULL END)");
				}
				sb.append(")");
				yield sb.toString();
			}
			case MAX -> {
				StringBuilder sb = new StringBuilder("greatest(");
				for (int r = 0; r < table.rules().size(); r++) {
					RuntimeDecisionTableRule rule = table.rules().get(r);
					if (r > 0)
						sb.append(", ");
					sb.append("(CASE WHEN ").append(emitRuleCondition(table, rule, slotNameResolver)).append(" THEN ")
							.append(emitRuleOutput(table, rule, slotNameResolver)).append(" ELSE NULL END)");
				}
				sb.append(")");
				yield sb.toString();
			}
		};
	}

	private static String emitListCollectDecisionTable(RuntimeDecisionTable table,
			IntFunction<String> slotNameResolver) {
		StringBuilder sb = new StringBuilder("filter(array(");
		for (int r = 0; r < table.rules().size(); r++) {
			RuntimeDecisionTableRule rule = table.rules().get(r);
			if (r > 0)
				sb.append(", ");
			sb.append("(CASE WHEN ").append(emitRuleCondition(table, rule, slotNameResolver)).append(" THEN ")
					.append(emitRuleOutput(table, rule, slotNameResolver)).append(" ELSE NULL END)");
		}
		sb.append("), x -> x IS NOT NULL)");
		return sb.toString();
	}

	private static String escapeSqlString(String str) {
		if (str == null)
			return "";
		return str.replace("'", "''").replace("\\", "\\\\");
	}

	private static String sanitizeColumn(String name) {
		return "`" + name.replace("`", "") + "`";
	}

	private static String sanitizeName(String name) {
		return name.replaceAll("[^a-zA-Z0-9_]", "_");
	}
}
