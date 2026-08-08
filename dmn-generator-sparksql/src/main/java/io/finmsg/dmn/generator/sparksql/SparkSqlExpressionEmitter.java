package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Emitter for lowering RuntimeExpression nodes into pure Spark SQL expressions.
 */
public final class SparkSqlExpressionEmitter {

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
		return switch (binary.operator()) {
			case ADD -> "(" + left + " + " + right + ")";
			case SUBTRACT -> "(" + left + " - " + right + ")";
			case MULTIPLY -> "(" + left + " * " + right + ")";
			case DIVIDE -> "(" + left + " / " + right + ")";
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
			case "string length", "length" -> "length(" + emitArg(args, 0, slotNameResolver) + ")";
			case "upper case", "upper" -> "upper(" + emitArg(args, 0, slotNameResolver) + ")";
			case "lower case", "lower" -> "lower(" + emitArg(args, 0, slotNameResolver) + ")";
			case "substring" ->
				"substring(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver) + ")";
			case "contains" ->
				"contains(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver) + ")";
			case "starts with" ->
				"startswith(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver) + ")";
			case "ends with" ->
				"endswith(" + emitArg(args, 0, slotNameResolver) + ", " + emitArg(args, 1, slotNameResolver) + ")";
			case "abs" -> "abs(" + emitArg(args, 0, slotNameResolver) + ")";
			case "floor" -> "floor(" + emitArg(args, 0, slotNameResolver) + ")";
			case "ceiling" -> "ceil(" + emitArg(args, 0, slotNameResolver) + ")";
			case "coalesce" -> "coalesce(" + emitArgsJoined(args, slotNameResolver) + ")";
			case "concat" -> "concat(" + emitArgsJoined(args, slotNameResolver) + ")";
			case "date" -> "to_date(" + emitArg(args, 0, slotNameResolver) + ")";
			case "date and time" -> "to_timestamp(" + emitArg(args, 0, slotNameResolver) + ")";
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
				sb.append("(").append(inputCol).append(" = ").append(emit(exprTest.expression(), slotNameResolver))
						.append(")");
			} else if (test instanceof RuntimeComparisonUnaryTest compTest) {
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
		StringBuilder sb = new StringBuilder("CASE\n");

		for (int r = 0; r < table.rules().size(); r++) {
			RuntimeDecisionTableRule rule = table.rules().get(r);
			sb.append("    WHEN ");
			if (rule.inputEntries().isEmpty()) {
				sb.append("TRUE");
			} else {
				for (int i = 0; i < rule.inputEntries().size(); i++) {
					if (i > 0)
						sb.append(" AND ");
					RuntimeDecisionTableInput input = table.inputs().get(i);
					String inputExpr = emit(input.expression(), slotNameResolver);
					RuntimeUnaryTests tests = rule.inputEntries().get(i);
					sb.append(emitUnaryTests(inputExpr, tests, slotNameResolver));
				}
			}
			sb.append(" THEN ");
			if (rule.outputEntries().isEmpty()) {
				sb.append("NULL\n");
			} else if (table.outputs().size() > 1) {
				sb.append("named_struct(");
				for (int o = 0; o < table.outputs().size(); o++) {
					if (o > 0)
						sb.append(", ");
					String name = table.outputs().get(o).name().orElse("output" + (o + 1));
					String valExpr = o < rule.outputEntries().size()
							? emit(rule.outputEntries().get(o), slotNameResolver)
							: "NULL";
					sb.append("'").append(escapeSqlString(name)).append("', ").append(valExpr);
				}
				sb.append(")\n");
			} else {
				sb.append(emit(rule.outputEntries().get(0), slotNameResolver)).append("\n");
			}
		}
		sb.append("    ELSE NULL\n  END");
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
