package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;
import java.util.function.IntFunction;

/**
 * Emitter for lowering RuntimeExpression nodes and Decision Tables into pure
 * Spark SQL expressions.
 */
public final class SparkSqlExpressionEmitter {

	private SparkSqlExpressionEmitter() {
	}

	public static String emit(RuntimeExpression expr, IntFunction<String> slotNameResolver) {
		return emitWithBkms(expr, Map.of(), slotNameResolver);
	}

	public static String emitWithBkms(RuntimeExpression expr, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		if (expr == null) {
			return "NULL";
		}
		return switch (expr) {
			case RuntimeConstant constant -> emitConstant(constant);
			case RuntimeValueReference ref -> {
				if (bkmBySlot.containsKey(ref.sourceSlot())) {
					RuntimeBkm bkm = bkmBySlot.get(ref.sourceSlot());
					if (bkm.function().isPresent() && bkm.function().get().parameters().isEmpty()
							&& bkm.function().get().body().isPresent()) {
						yield emitWithBkms(bkm.function().get().body().get(), bkmBySlot, slotNameResolver);
					}
				}
				String resolved = slotNameResolver.apply(ref.sourceSlot());
				if (resolved != null) {
					if (resolved.startsWith("`") && resolved.endsWith("`")) {
						yield resolved;
					}
					yield "`" + resolved.replace("`", "") + "`";
				}
				yield "`input_" + ref.sourceSlot() + "`";
			}
			case RuntimeLocalReference local -> {
				String resolved = slotNameResolver.apply(local.localSlot());
				if (resolved != null) {
					yield resolved;
				}
				yield "_local_" + Math.abs(local.localSlot());
			}
			case RuntimeBinaryExpression binary -> emitBinary(binary, bkmBySlot, slotNameResolver);
			case RuntimeUnaryExpression unary -> emitUnary(unary, bkmBySlot, slotNameResolver);
			case RuntimeConditionalExpression cond ->
				"CASE WHEN (" + emitWithBkms(cond.condition(), bkmBySlot, slotNameResolver) + ") THEN ("
						+ emitWithBkms(cond.thenExpression(), bkmBySlot, slotNameResolver) + ") ELSE ("
						+ emitWithBkms(cond.elseExpression(), bkmBySlot, slotNameResolver) + ") END";
			case RuntimePathExpression path -> {
				String src = emitWithBkms(path.source(), bkmBySlot, slotNameResolver);
				String member = path.member().trim().toLowerCase();
				if (path.source().type() != null) {
					RuntimeTypeKind kind = path.source().type().kind();
					if (kind == RuntimeTypeKind.DATE || kind == RuntimeTypeKind.DATE_TIME
							|| kind == RuntimeTypeKind.TIME) {
						yield switch (member) {
							case "year" -> "year(" + src + ")";
							case "month" -> "month(" + src + ")";
							case "day" -> "day(" + src + ")";
							case "weekday" -> "dayofweek(" + src + ")";
							case "hour" -> "hour(" + src + ")";
							case "minute" -> "minute(" + src + ")";
							case "second" -> "second(" + src + ")";
							case "day of year", "dayofyear" -> "dayofyear(" + src + ")";
							case "day of week", "dayofweek" -> "date_format(" + src + ", 'EEEE')";
							case "month of year", "monthofyear" -> "date_format(" + src + ", 'MMMM')";
							case "week of year", "weekofyear" -> "weekofyear(" + src + ")";
							default -> "(" + src + "." + sanitizeColumn(path.member()) + ")";
						};
					}
					if (kind == RuntimeTypeKind.DURATION) {
						yield switch (member) {
							case "years" -> "(CASE WHEN " + src + " LIKE '%Y%' OR " + src
									+ " LIKE '%M%' THEN coalesce(try_cast(regexp_extract(" + src
									+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as bigint), 0L) ELSE NULL END)";
							case "months" -> "(CASE WHEN " + src + " LIKE '%Y%' OR " + src
									+ " LIKE '%M%' THEN coalesce(try_cast(regexp_extract(" + src
									+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as bigint), 0L) ELSE NULL END)";
							case "days" -> "(CASE WHEN " + src + " LIKE '%D%' OR " + src
									+ " LIKE '%T%' THEN coalesce(try_cast(regexp_extract(" + src
									+ ", 'P(?:([0-9]+)D)?', 1) as bigint), 0L) ELSE NULL END)";
							case "hours" -> "(CASE WHEN " + src + " LIKE '%D%' OR " + src
									+ " LIKE '%T%' THEN coalesce(try_cast(regexp_extract(" + src
									+ ", 'T(?:([0-9]+)H)?', 1) as bigint), 0L) ELSE NULL END)";
							case "minutes" -> "(CASE WHEN " + src + " LIKE '%D%' OR " + src
									+ " LIKE '%T%' THEN coalesce(try_cast(regexp_extract(" + src
									+ ", 'T(?:.*?)?([0-9]+)M', 1) as bigint), 0L) ELSE NULL END)";
							case "seconds" -> "(CASE WHEN " + src + " LIKE '%D%' OR " + src
									+ " LIKE '%T%' THEN coalesce(try_cast(regexp_extract(" + src
									+ ", 'T(?:.*?)?([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0D) ELSE NULL END)";
							default -> "(" + src + "." + sanitizeColumn(path.member()) + ")";
						};
					}
					if (kind == RuntimeTypeKind.LIST) {
						yield "transform(" + src + ", x -> x." + sanitizeColumn(path.member()) + ")";
					}
				}
				// Default property check for untyped date/time/duration members
				yield switch (member) {
					case "years" -> "(CASE WHEN " + src + " LIKE '%Y%' OR " + src
							+ " LIKE '%M%' THEN coalesce(try_cast(regexp_extract(" + src
							+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as bigint), 0L) ELSE NULL END)";
					case "months" -> "(CASE WHEN " + src + " LIKE '%Y%' OR " + src
							+ " LIKE '%M%' THEN coalesce(try_cast(regexp_extract(" + src
							+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as bigint), 0L) ELSE NULL END)";
					case "days" -> "(CASE WHEN " + src + " LIKE '%D%' OR " + src
							+ " LIKE '%T%' THEN coalesce(try_cast(regexp_extract(" + src
							+ ", 'P(?:([0-9]+)D)?', 1) as bigint), 0L) ELSE NULL END)";
					case "hours" -> "(CASE WHEN " + src + " LIKE '%D%' OR " + src
							+ " LIKE '%T%' THEN coalesce(try_cast(regexp_extract(" + src
							+ ", 'T(?:([0-9]+)H)?', 1) as bigint), 0L) ELSE NULL END)";
					case "minutes" -> "(CASE WHEN " + src + " LIKE '%D%' OR " + src
							+ " LIKE '%T%' THEN coalesce(try_cast(regexp_extract(" + src
							+ ", 'T(?:.*?)?([0-9]+)M', 1) as bigint), 0L) ELSE NULL END)";
					case "seconds" -> "(CASE WHEN " + src + " LIKE '%D%' OR " + src
							+ " LIKE '%T%' THEN coalesce(try_cast(regexp_extract(" + src
							+ ", 'T(?:.*?)?([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0D) ELSE NULL END)";
					case "time offset" -> "(CASE WHEN " + src
							+ " rlike '[+-][0-9]{2}:[0-9]{2}' THEN concat('PT', try_cast(regexp_extract(" + src
							+ ", '([+-][0-9]{2})(:[0-9]{2})?', 1) as int), 'H') ELSE NULL END)";
					case "timezone" -> "(CASE WHEN " + src + " LIKE '%@%' THEN regexp_extract(" + src
							+ ", '@([^@]+)$', 1) ELSE NULL END)";
					case "year" -> "year(" + src + ")";
					case "month" -> "month(" + src + ")";
					case "day" -> "day(" + src + ")";
					case "weekday" -> "(weekday(" + src + ") + 1)";
					case "hour" -> "hour(" + src + ")";
					case "minute" -> "minute(" + src + ")";
					case "second" -> "second(" + src + ")";
					default -> "(" + src + "." + sanitizeColumn(path.member()) + ")";
				};
			}
			case RuntimeContextExpression ctx -> emitContext(ctx, bkmBySlot, slotNameResolver);
			case RuntimeListExpression list -> emitList(list, bkmBySlot, slotNameResolver);
			case RuntimeFunctionCall fn -> emitFunctionCall(fn, bkmBySlot, slotNameResolver);
			case RuntimeInvocationExpression inv -> emitInvocation(inv, bkmBySlot, slotNameResolver);
			case RuntimeFilterExpression filter -> emitFilter(filter, bkmBySlot, slotNameResolver);
			case RuntimeQuantifiedExpression quant -> emitQuantified(quant, bkmBySlot, slotNameResolver);
			case RuntimeForExpression forExpr -> emitFor(forExpr, bkmBySlot, slotNameResolver);
			case RuntimeRelationExpression rel -> emitRelation(rel, bkmBySlot, slotNameResolver);
			case RuntimeBetweenExpression btn -> "(" + emitWithBkms(btn.value(), bkmBySlot, slotNameResolver) + " >= "
					+ emitWithBkms(btn.lower(), bkmBySlot, slotNameResolver) + " AND "
					+ emitWithBkms(btn.value(), bkmBySlot, slotNameResolver) + " <= "
					+ emitWithBkms(btn.upper(), bkmBySlot, slotNameResolver) + ")";
			case RuntimeInExpression inExpr -> emitUnaryTests(emitWithBkms(inExpr.value(), bkmBySlot, slotNameResolver),
					inExpr.tests(), bkmBySlot, slotNameResolver);
			case RuntimeUnaryTestsExpression testsExpr ->
				emitUnaryTests("?", testsExpr.tests(), bkmBySlot, slotNameResolver);
			case RuntimeDescendantExpression desc -> emitDescendant(desc, bkmBySlot, slotNameResolver);
			case RuntimeInstanceOfExpression inst -> {
				String exprCode = emitWithBkms(inst.expression(), bkmBySlot, slotNameResolver);
				RuntimeType exprType = inst.expression().type();
				RuntimeType targetType = inst.testedType();
				if (exprType != null && targetType != null) {
					if (targetType.kind() == RuntimeTypeKind.ANY) {
						yield "(" + exprCode + " IS NOT NULL)";
					}
					if (exprType.kind() != RuntimeTypeKind.ANY && exprType.kind() != targetType.kind()) {
						yield "FALSE";
					}
				}
				yield "(" + exprCode + " IS NOT NULL)";
			}
			case RuntimeRangeExpression range -> emitRange(range, bkmBySlot, slotNameResolver);
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
			case DATE -> "DATE '" + escapeSqlString(constant.value()) + "'";
			case TIME -> "cast('" + escapeSqlString(constant.value()) + "' as timestamp)";
			case DATE_TIME -> "TIMESTAMP '" + escapeSqlString(constant.value().replace("T", " ")) + "'";
			case DURATION -> "'" + escapeSqlString(constant.value()) + "'";
			default -> "'" + escapeSqlString(constant.value()) + "'";
		};
	}

	private static String emitBinary(RuntimeBinaryExpression binary, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		String left = emitWithBkms(binary.left(), bkmBySlot, slotNameResolver);
		String right = emitWithBkms(binary.right(), bkmBySlot, slotNameResolver);

		RuntimeType lt = binary.left().type();
		RuntimeType rt = binary.right().type();
		RuntimeTypeKind lk = lt != null ? lt.kind() : RuntimeTypeKind.ANY;
		RuntimeTypeKind rk = rt != null ? rt.kind() : RuntimeTypeKind.ANY;

		RuntimeBinaryOperator op = binary.operator();

		// Comparison and Boolean operators
		if (op == RuntimeBinaryOperator.AND)
			return "(" + left + " AND " + right + ")";
		if (op == RuntimeBinaryOperator.OR)
			return "(" + left + " OR " + right + ")";
		if (op == RuntimeBinaryOperator.EQUAL)
			return "(" + left + " <=> " + right + ")";
		if (op == RuntimeBinaryOperator.NOT_EQUAL)
			return "(NOT (" + left + " <=> " + right + "))";
		if (op == RuntimeBinaryOperator.LESS)
			return "(" + left + " < " + right + ")";
		if (op == RuntimeBinaryOperator.LESS_EQUAL)
			return "(" + left + " <= " + right + ")";
		if (op == RuntimeBinaryOperator.GREATER)
			return "(" + left + " > " + right + ")";
		if (op == RuntimeBinaryOperator.GREATER_EQUAL)
			return "(" + left + " >= " + right + ")";

		// Incompatible operand check for arithmetic operators (+, -, *, /, power)
		if (lk == RuntimeTypeKind.BOOLEAN || rk == RuntimeTypeKind.BOOLEAN || lk == RuntimeTypeKind.CONTEXT
				|| rk == RuntimeTypeKind.CONTEXT || lk == RuntimeTypeKind.RANGE || rk == RuntimeTypeKind.RANGE
				|| lk == RuntimeTypeKind.FUNCTION || rk == RuntimeTypeKind.FUNCTION) {
			return "NULL";
		}

		if (op == RuntimeBinaryOperator.POWER) {
			if (lk != RuntimeTypeKind.NUMBER && lk != RuntimeTypeKind.ANY)
				return "NULL";
			if (rk != RuntimeTypeKind.NUMBER && rk != RuntimeTypeKind.ANY)
				return "NULL";
			return "pow(" + left + ", " + right + ")";
		}

		if (op == RuntimeBinaryOperator.ADD) {
			if (lk == RuntimeTypeKind.STRING && rk == RuntimeTypeKind.STRING) {
				return "concat(" + left + ", " + right + ")";
			}
			if (lk == RuntimeTypeKind.LIST && rk == RuntimeTypeKind.LIST) {
				return "concat(" + left + ", " + right + ")";
			}
			if (lk == RuntimeTypeKind.NUMBER && rk == RuntimeTypeKind.NUMBER) {
				return "(" + left + " + " + right + ")";
			}
			if (lk == RuntimeTypeKind.DATE && rk == RuntimeTypeKind.DURATION) {
				return "(CASE WHEN " + right + " LIKE '%Y%' OR " + right + " LIKE '%M%' THEN add_months(" + left
						+ ", cast(coalesce(try_cast(regexp_extract(" + right
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as int), 0) * 12 + coalesce(try_cast(regexp_extract("
						+ right + ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as int), 0) as int)) ELSE date_add(" + left
						+ ", cast(coalesce(try_cast(regexp_extract(" + right
						+ ", 'P(?:([0-9]+)D)?', 1) as int), 0) as int)) END)";
			}
			if (lk == RuntimeTypeKind.DURATION && rk == RuntimeTypeKind.DATE) {
				return "(CASE WHEN " + left + " LIKE '%Y%' OR " + left + " LIKE '%M%' THEN add_months(" + right
						+ ", cast(coalesce(try_cast(regexp_extract(" + left
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as int), 0) * 12 + coalesce(try_cast(regexp_extract("
						+ left + ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as int), 0) as int)) ELSE date_add(" + right
						+ ", cast(coalesce(try_cast(regexp_extract(" + left
						+ ", 'P(?:([0-9]+)D)?', 1) as int), 0) as int)) END)";
			}
			if (lk == RuntimeTypeKind.DURATION && rk == RuntimeTypeKind.DURATION) {
				return "(CASE WHEN " + left + " LIKE '%Y%' OR " + left + " LIKE '%M%' THEN (CASE WHEN " + right
						+ " LIKE '%Y%' OR " + right
						+ " LIKE '%M%' THEN concat('P', cast((coalesce(try_cast(regexp_extract(" + left
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract("
						+ left
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as bigint), 0L)) + (coalesce(try_cast(regexp_extract("
						+ right
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract("
						+ right
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as bigint), 0L)) as string), 'M') ELSE NULL END) ELSE (CASE WHEN "
						+ right + " LIKE '%Y%' OR " + right
						+ " LIKE '%M%' THEN NULL ELSE concat('PT', cast((coalesce(try_cast(regexp_extract(" + left
						+ ", 'P(?:([0-9]+)D)?', 1) as bigint), 0L) * 86400 + coalesce(try_cast(regexp_extract(" + left
						+ ", 'T(?:([0-9]+)H)?', 1) as bigint), 0L) * 3600 + coalesce(try_cast(regexp_extract(" + left
						+ ", 'T(?:.*?)?([0-9]+)M', 1) as bigint), 0L) * 60 + coalesce(try_cast(regexp_extract(" + left
						+ ", 'T(?:.*?)?([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)) + (coalesce(try_cast(regexp_extract("
						+ right + ", 'P(?:([0-9]+)D)?', 1) as bigint), 0L) * 86400 + coalesce(try_cast(regexp_extract("
						+ right + ", 'T(?:([0-9]+)H)?', 1) as bigint), 0L) * 3600 + coalesce(try_cast(regexp_extract("
						+ right + ", 'T(?:.*?)?([0-9]+)M', 1) as bigint), 0L) * 60 + coalesce(try_cast(regexp_extract("
						+ right
						+ ", 'T(?:.*?)?([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)) as string), 'S') END) END)";
			}
			if (lk == RuntimeTypeKind.STRING || rk == RuntimeTypeKind.STRING) {
				return "concat(" + left + ", " + right + ")";
			}
			return "(" + left + " + " + right + ")";
		}

		if (op == RuntimeBinaryOperator.SUBTRACT) {
			if (lk == RuntimeTypeKind.NUMBER && rk == RuntimeTypeKind.NUMBER) {
				return "(" + left + " - " + right + ")";
			}
			if (lk == RuntimeTypeKind.DATE && rk == RuntimeTypeKind.DATE) {
				return "concat('P', cast(datediff(" + left + ", " + right + ") as string), 'D')";
			}
			if (lk == RuntimeTypeKind.DATE && rk == RuntimeTypeKind.DURATION) {
				return "(CASE WHEN " + right + " LIKE '%Y%' OR " + right + " LIKE '%M%' THEN add_months(" + left
						+ ", -cast(coalesce(try_cast(regexp_extract(" + right
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as int), 0) * 12 + coalesce(try_cast(regexp_extract("
						+ right + ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as int), 0) as int)) ELSE date_sub(" + left
						+ ", cast(coalesce(try_cast(regexp_extract(" + right
						+ ", 'P(?:([0-9]+)D)?', 1) as int), 0) as int)) END)";
			}
			if (lk == RuntimeTypeKind.DURATION && rk == RuntimeTypeKind.DURATION) {
				return "(CASE WHEN " + left + " LIKE '%Y%' OR " + left + " LIKE '%M%' THEN (CASE WHEN " + right
						+ " LIKE '%Y%' OR " + right
						+ " LIKE '%M%' THEN concat('P', cast((coalesce(try_cast(regexp_extract(" + left
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract("
						+ left
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as bigint), 0L)) - (coalesce(try_cast(regexp_extract("
						+ right
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract("
						+ right
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as bigint), 0L)) as string), 'M') ELSE NULL END) ELSE (CASE WHEN "
						+ right + " LIKE '%Y%' OR " + right
						+ " LIKE '%M%' THEN NULL ELSE concat('PT', cast((coalesce(try_cast(regexp_extract(" + left
						+ ", 'P(?:([0-9]+)D)?', 1) as bigint), 0L) * 86400 + coalesce(try_cast(regexp_extract(" + left
						+ ", 'T(?:([0-9]+)H)?', 1) as bigint), 0L) * 3600 + coalesce(try_cast(regexp_extract(" + left
						+ ", 'T(?:.*?)?([0-9]+)M', 1) as bigint), 0L) * 60 + coalesce(try_cast(regexp_extract(" + left
						+ ", 'T(?:.*?)?([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)) - (coalesce(try_cast(regexp_extract("
						+ right + ", 'P(?:([0-9]+)D)?', 1) as bigint), 0L) * 86400 + coalesce(try_cast(regexp_extract("
						+ right + ", 'T(?:([0-9]+)H)?', 1) as bigint), 0L) * 3600 + coalesce(try_cast(regexp_extract("
						+ right + ", 'T(?:.*?)?([0-9]+)M', 1) as bigint), 0L) * 60 + coalesce(try_cast(regexp_extract("
						+ right
						+ ", 'T(?:.*?)?([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)) as string), 'S') END) END)";
			}
			return "(" + left + " - " + right + ")";
		}

		if (op == RuntimeBinaryOperator.MULTIPLY) {
			if (lk == RuntimeTypeKind.NUMBER && rk == RuntimeTypeKind.NUMBER) {
				return "(" + left + " * " + right + ")";
			}
			if (lk == RuntimeTypeKind.DURATION && (rk == RuntimeTypeKind.NUMBER || rk == RuntimeTypeKind.ANY)) {
				return "(CASE WHEN " + left + " LIKE '%Y%' OR " + left
						+ " LIKE '%M%' THEN concat('P', cast(cast((coalesce(try_cast(regexp_extract(" + left
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract("
						+ left + ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as bigint), 0L)) * " + right
						+ " as bigint) as string), 'M') ELSE concat('PT', cast(cast((coalesce(try_cast(regexp_extract("
						+ left + ", 'P(?:([0-9]+)D)?', 1) as bigint), 0L) * 86400 + coalesce(try_cast(regexp_extract("
						+ left + ", 'T(?:([0-9]+)H)?', 1) as bigint), 0L) * 3600 + coalesce(try_cast(regexp_extract("
						+ left + ", 'T(?:.*?)?([0-9]+)M', 1) as bigint), 0L) * 60 + coalesce(try_cast(regexp_extract("
						+ left + ", 'T(?:.*?)?([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)) * " + right
						+ " as bigint) as string), 'S') END)";
			}
			if ((lk == RuntimeTypeKind.NUMBER || lk == RuntimeTypeKind.ANY) && rk == RuntimeTypeKind.DURATION) {
				return "(CASE WHEN " + right + " LIKE '%Y%' OR " + right
						+ " LIKE '%M%' THEN concat('P', cast(cast((coalesce(try_cast(regexp_extract(" + right
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract("
						+ right + ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as bigint), 0L)) * " + left
						+ " as bigint) as string), 'M') ELSE concat('PT', cast(cast((coalesce(try_cast(regexp_extract("
						+ right + ", 'P(?:([0-9]+)D)?', 1) as bigint), 0L) * 86400 + coalesce(try_cast(regexp_extract("
						+ right + ", 'T(?:([0-9]+)H)?', 1) as bigint), 0L) * 3600 + coalesce(try_cast(regexp_extract("
						+ right + ", 'T(?:.*?)?([0-9]+)M', 1) as bigint), 0L) * 60 + coalesce(try_cast(regexp_extract("
						+ right + ", 'T(?:.*?)?([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)) * " + left
						+ " as bigint) as string), 'S') END)";
			}
			return "(" + left + " * " + right + ")";
		}

		if (op == RuntimeBinaryOperator.DIVIDE) {
			if (lk == RuntimeTypeKind.NUMBER && rk == RuntimeTypeKind.NUMBER) {
				return "(CASE WHEN " + right + " = 0 THEN NULL ELSE (" + left + " / " + right + ") END)";
			}
			if (lk == RuntimeTypeKind.DURATION && (rk == RuntimeTypeKind.NUMBER || rk == RuntimeTypeKind.ANY)) {
				return "(CASE WHEN " + right + " = 0 THEN NULL WHEN " + left + " LIKE '%Y%' OR " + left
						+ " LIKE '%M%' THEN concat('P', cast(cast((coalesce(try_cast(regexp_extract(" + left
						+ ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract("
						+ left + ", 'P(?:([0-9]+)Y)?(?:([0-9]+)M)?', 2) as bigint), 0L)) / " + right
						+ " as bigint) as string), 'M') ELSE concat('PT', cast(cast((coalesce(try_cast(regexp_extract("
						+ left + ", 'P(?:([0-9]+)D)?', 1) as bigint), 0L) * 86400 + coalesce(try_cast(regexp_extract("
						+ left + ", 'T(?:([0-9]+)H)?', 1) as bigint), 0L) * 3600 + coalesce(try_cast(regexp_extract("
						+ left + ", 'T(?:.*?)?([0-9]+)M', 1) as bigint), 0L) * 60 + coalesce(try_cast(regexp_extract("
						+ left + ", 'T(?:.*?)?([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)) / " + right
						+ " as bigint) as string), 'S') END)";
			}
			return "(CASE WHEN " + right + " = 0 THEN NULL ELSE (" + left + " / " + right + ") END)";
		}

		return "(" + left + " + " + right + ")";
	}

	private static String emitUnary(RuntimeUnaryExpression unary, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		String op = emitWithBkms(unary.operand(), bkmBySlot, slotNameResolver);
		return switch (unary.operator()) {
			case POSITIVE -> op;
			case NEGATE -> "(-" + op + ")";
			case NOT -> "(NOT " + op + ")";
		};
	}

	private static String emitRange(RuntimeRangeExpression range, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		String lowerStr = range.lower().map(e -> emitWithBkms(e, bkmBySlot, slotNameResolver)).orElse("NULL");
		String upperStr = range.upper().map(e -> emitWithBkms(e, bkmBySlot, slotNameResolver)).orElse("NULL");
		boolean startInc = range.lowerBoundary() == RuntimeRangeBoundary.CLOSED;
		boolean endInc = range.upperBoundary() == RuntimeRangeBoundary.CLOSED;
		return "named_struct('start', " + lowerStr + ", 'end', " + upperStr + ", 'start included', " + startInc
				+ ", 'end included', " + endInc + ", 'startIncluded', " + startInc + ", 'endIncluded', " + endInc + ")";
	}

	private static String emitContext(RuntimeContextExpression ctx, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		if (ctx.entries().isEmpty())
			return "map()";
		StringBuilder sb = new StringBuilder("named_struct(");
		for (int i = 0; i < ctx.entries().size(); i++) {
			if (i > 0)
				sb.append(", ");
			RuntimeContextEntry entry = ctx.entries().get(i);
			sb.append("'").append(escapeSqlString(entry.name())).append("', ")
					.append(emitWithBkms(entry.expression(), bkmBySlot, slotNameResolver));
		}
		sb.append(")");
		return sb.toString();
	}

	private static String emitList(RuntimeListExpression list, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		if (list.elements().isEmpty())
			return "array()";
		StringBuilder sb = new StringBuilder("array(");
		for (int i = 0; i < list.elements().size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(emitWithBkms(list.elements().get(i), bkmBySlot, slotNameResolver));
		}
		sb.append(")");
		return sb.toString();
	}

	private static String emitFilter(RuntimeFilterExpression filter, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		String source = emitWithBkms(filter.source(), bkmBySlot, slotNameResolver);
		String itemVar = "_item_" + Math.abs(filter.localSlot());
		IntFunction<String> scopedResolver = slot -> (slot == filter.localSlot())
				? itemVar
				: slotNameResolver.apply(slot);
		String filterExpr = emitWithBkms(filter.filter(), bkmBySlot, scopedResolver);
		if (filter.filter().type() != null && filter.filter().type().kind() == RuntimeTypeKind.NUMBER) {
			return "element_at(" + source + ", cast(" + filterExpr + " as int))";
		}
		return "filter(" + source + ", " + itemVar + " -> " + filterExpr + ")";
	}

	private static String emitQuantified(RuntimeQuantifiedExpression quant, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		boolean isEvery = quant.quantifier() == RuntimeQuantifier.EVERY;
		if (quant.bindings().isEmpty()) {
			return String.valueOf(isEvery).toUpperCase();
		}
		return emitNestedQuantified(isEvery, quant.bindings(), 0, quant.satisfies(), bkmBySlot, slotNameResolver);
	}

	private static String emitNestedQuantified(boolean isEvery, List<RuntimeQuantifiedBinding> bindings, int index,
			RuntimeExpression satisfies, Map<Integer, RuntimeBkm> bkmBySlot, IntFunction<String> slotNameResolver) {
		if (index == bindings.size()) {
			return emitWithBkms(satisfies, bkmBySlot, slotNameResolver);
		}
		RuntimeQuantifiedBinding binding = bindings.get(index);
		String source = emitWithBkms(binding.source(), bkmBySlot, slotNameResolver);
		String itemVar = "_q_" + Math.abs(binding.localSlot());
		IntFunction<String> scopedResolver = slot -> (slot == binding.localSlot())
				? itemVar
				: slotNameResolver.apply(slot);
		String inner = emitNestedQuantified(isEvery, bindings, index + 1, satisfies, bkmBySlot, scopedResolver);
		String func = isEvery ? "forall" : "exists";
		return func + "(" + source + ", " + itemVar + " -> " + inner + ")";
	}

	private static String emitFor(RuntimeForExpression forExpr, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		if (forExpr.iterations().isEmpty()) {
			return "array()";
		}
		if (forExpr.iterations().size() == 1) {
			RuntimeIteration iter = forExpr.iterations().get(0);
			String source = iter.end().isPresent()
					? "sequence(" + emitWithBkms(iter.source(), bkmBySlot, slotNameResolver) + ", "
							+ emitWithBkms(iter.end().get(), bkmBySlot, slotNameResolver) + ")"
					: emitWithBkms(iter.source(), bkmBySlot, slotNameResolver);
			String itemVar = "_for_" + Math.abs(iter.localSlot());
			IntFunction<String> scopedResolver = slot -> (slot == iter.localSlot())
					? itemVar
					: slotNameResolver.apply(slot);
			String result = emitWithBkms(forExpr.result(), bkmBySlot, scopedResolver);
			return "transform(" + source + ", " + itemVar + " -> " + result + ")";
		}
		return emitNestedFor(forExpr.iterations(), 0, forExpr.result(), bkmBySlot, slotNameResolver);
	}

	private static String emitNestedFor(List<RuntimeIteration> iterations, int index, RuntimeExpression resultExpr,
			Map<Integer, RuntimeBkm> bkmBySlot, IntFunction<String> slotNameResolver) {
		if (index == iterations.size()) {
			return emitWithBkms(resultExpr, bkmBySlot, slotNameResolver);
		}
		RuntimeIteration iter = iterations.get(index);
		String source = iter.end().isPresent()
				? "sequence(" + emitWithBkms(iter.source(), bkmBySlot, slotNameResolver) + ", "
						+ emitWithBkms(iter.end().get(), bkmBySlot, slotNameResolver) + ")"
				: emitWithBkms(iter.source(), bkmBySlot, slotNameResolver);
		String itemVar = "_for_" + Math.abs(iter.localSlot());
		IntFunction<String> scopedResolver = slot -> (slot == iter.localSlot())
				? itemVar
				: slotNameResolver.apply(slot);
		String inner = emitNestedFor(iterations, index + 1, resultExpr, bkmBySlot, scopedResolver);
		String transformed = "transform(" + source + ", " + itemVar + " -> " + inner + ")";
		return (index > 0 && index < iterations.size() - 1) ? "flatten(" + transformed + ")" : transformed;
	}

	private static String emitRelation(RuntimeRelationExpression rel, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		if (rel.rows().isEmpty()) {
			return "array()";
		}
		StringBuilder sb = new StringBuilder("array(");
		for (int r = 0; r < rel.rows().size(); r++) {
			if (r > 0)
				sb.append(", ");
			sb.append("named_struct(");
			List<RuntimeExpression> row = rel.rows().get(r);
			for (int c = 0; c < row.size(); c++) {
				if (c > 0)
					sb.append(", ");
				String colName = c < rel.columns().size() ? rel.columns().get(c).name() : "col" + c;
				sb.append("'").append(escapeSqlString(colName)).append("', ")
						.append(emitWithBkms(row.get(c), bkmBySlot, slotNameResolver));
			}
			sb.append(")");
		}
		sb.append(")");
		return sb.toString();
	}

	private static String emitInvocation(RuntimeInvocationExpression inv, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		if (inv.function().isPresent()) {
			String fnName = inv.function().get();
			// Check if function name matches any BKM
			for (Map.Entry<Integer, RuntimeBkm> entry : bkmBySlot.entrySet()) {
				String bkmName = slotNameResolver.apply(entry.getKey());
				if (bkmName != null && fnName.equalsIgnoreCase(bkmName.replace("`", ""))) {
					return inlineBkm(entry.getValue(), inv.positionalArguments(), inv.namedArguments(), bkmBySlot,
							slotNameResolver);
				}
			}
			List<RuntimeExpression> args = !inv.positionalArguments().isEmpty()
					? inv.positionalArguments()
					: inv.namedArguments().stream().map(RuntimeNamedArgument::expression).toList();
			return emitFunctionCall(new RuntimeFunctionCall(fnName, args, inv.type()), bkmBySlot, slotNameResolver);
		}
		if (inv.target().isPresent()) {
			RuntimeExpression target = inv.target().get();
			if (target instanceof RuntimeValueReference ref && bkmBySlot.containsKey(ref.sourceSlot())) {
				RuntimeBkm bkm = bkmBySlot.get(ref.sourceSlot());
				return inlineBkm(bkm, inv.positionalArguments(), inv.namedArguments(), bkmBySlot, slotNameResolver);
			}
			return emitWithBkms(target, bkmBySlot, slotNameResolver);
		}
		return "NULL";
	}

	private static String inlineBkm(RuntimeBkm bkm, List<RuntimeExpression> posArgs,
			List<RuntimeNamedArgument> namedArgs, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		if (bkm.function().isEmpty() || bkm.function().get().body().isEmpty()) {
			return "NULL";
		}
		RuntimeFunctionDefinition fn = bkm.function().get();
		List<RuntimeFunctionParameter> params = fn.parameters();
		Map<Integer, String> paramValues = new HashMap<>();

		for (int i = 0; i < params.size(); i++) {
			RuntimeFunctionParameter param = params.get(i);
			RuntimeExpression argExpr = null;
			if (i < posArgs.size()) {
				argExpr = posArgs.get(i);
			} else if (namedArgs != null) {
				for (RuntimeNamedArgument named : namedArgs) {
					if (named.name().equals(param.name())) {
						argExpr = named.expression();
						break;
					}
				}
			}
			String emittedArg = (argExpr != null) ? emitWithBkms(argExpr, bkmBySlot, slotNameResolver) : "NULL";
			paramValues.put(param.localSlot(), "(" + emittedArg + ")");
		}

		IntFunction<String> bkmResolver = slot -> {
			if (paramValues.containsKey(slot)) {
				return paramValues.get(slot);
			}
			return slotNameResolver.apply(slot);
		};

		return "(" + emitWithBkms(fn.body().get(), bkmBySlot, bkmResolver) + ")";
	}

	private static String emitDescendant(RuntimeDescendantExpression desc, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		String src = emitWithBkms(desc.source(), bkmBySlot, slotNameResolver);
		String member = sanitizeColumn(desc.member());
		return "transform(" + src + ", x -> x." + member + ")";
	}

	private static String emitFunctionCall(RuntimeFunctionCall fn, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		String name = fn.function().toLowerCase();
		List<RuntimeExpression> args = fn.arguments();

		return switch (name) {
			// String functions
			case "string length", "length" -> "length(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "upper case", "upper" -> "upper(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "lower case", "lower" -> "lower(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "substring" -> {
				if (args.size() == 2) {
					yield "substring(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
							+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
				}
				yield "substring(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
						+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ", "
						+ emitArg(args, 2, bkmBySlot, slotNameResolver) + ")";
			}
			case "contains" -> "contains(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
			case "starts with", "startswith" -> "startswith(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
			case "ends with", "endswith" -> "endswith(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
			case "substring before" -> "(CASE WHEN instr(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ") > 0 THEN substring("
					+ emitArg(args, 0, bkmBySlot, slotNameResolver) + ", 1, instr("
					+ emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ") - 1) ELSE '' END)";
			case "substring after" -> {
				String str = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String match = emitArg(args, 1, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN instr(" + str + ", " + match + ") > 0 THEN substring(" + str + ", instr(" + str + ", "
						+ match + ") + length(" + match + ")) ELSE '' END)";
			}
			case "replace" -> {
				if (args.size() == 3) {
					yield "regexp_replace(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
							+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ", "
							+ emitArg(args, 2, bkmBySlot, slotNameResolver) + ")";
				}
				String in = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String pat = emitArg(args, 1, bkmBySlot, slotNameResolver);
				String rep = emitArg(args, 2, bkmBySlot, slotNameResolver);
				String flg = emitArg(args, 3, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN " + flg + " = 'q' THEN replace(" + in + ", " + pat + ", " + rep
						+ ") ELSE regexp_replace(" + in + ", concat('(?', " + flg + ", ')', " + pat + "), " + rep
						+ ") END)";
			}
			case "matches" -> {
				if (args.size() == 2) {
					yield "(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + " rlike "
							+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
				}
				String in = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String pat = emitArg(args, 1, bkmBySlot, slotNameResolver);
				String flg = emitArg(args, 2, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN " + flg + " = 'q' THEN (instr(" + in + ", " + pat + ") > 0) ELSE (" + in
						+ " rlike concat('(?', " + flg + ", ')', " + pat + ")) END)";
			}
			case "split" -> "split(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
			case "trim" -> "trim(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "concat" -> "concat(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";
			case "string" -> "cast(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + " as string)";
			case "string join" -> {
				if (args.size() == 1) {
					yield "array_join(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", '')";
				}
				yield "array_join(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
						+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
			}

			// Type conversion / General functions
			case "is" -> "(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + " <=> "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
			case "number" -> {
				if (args.size() == 1) {
					yield "try_cast(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + " as double)";
				} else if (args.size() == 3) {
					String val = emitArg(args, 0, bkmBySlot, slotNameResolver);
					String grp = emitArg(args, 1, bkmBySlot, slotNameResolver);
					String dec = emitArg(args, 2, bkmBySlot, slotNameResolver);
					yield "try_cast(replace(replace(" + val + ", " + grp + ", ''), " + dec + ", '.') as double)";
				}
				yield "try_cast(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + " as double)";
			}
			case "duration", "years and months duration", "years_and_months_duration", "day and time duration",
					"day_and_time_duration" -> {
				if (args.size() == 1) {
					yield "try_cast(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + " as string)";
				}
				if (args.size() == 2) {
					String from = emitArg(args, 0, bkmBySlot, slotNameResolver);
					String to = emitArg(args, 1, bkmBySlot, slotNameResolver);
					yield "(CASE WHEN months_between(" + to + ", " + from
							+ ") IS NULL THEN NULL WHEN cast(months_between(" + to + ", " + from
							+ ") as int) = 0 THEN 'P0M' WHEN cast(months_between(" + to + ", " + from
							+ ") as int) > 0 THEN concat('P', CASE WHEN cast(months_between(" + to + ", " + from
							+ ") as int) >= 12 THEN concat(cast(floor(cast(months_between(" + to + ", " + from
							+ ") as int) / 12) as int), 'Y') ELSE '' END, CASE WHEN (cast(months_between(" + to + ", "
							+ from + ") as int) % 12) != 0 OR cast(months_between(" + to + ", " + from
							+ ") as int) < 12 THEN concat(cast((cast(months_between(" + to + ", " + from
							+ ") as int) % 12) as int), 'M') ELSE '' END) ELSE concat('P', CASE WHEN abs(cast(months_between("
							+ to + ", " + from + ") as int)) >= 12 THEN concat('-', cast(floor(abs(cast(months_between("
							+ to + ", " + from
							+ ") as int)) / 12) as int), 'Y') ELSE '' END, CASE WHEN (abs(cast(months_between(" + to
							+ ", " + from + ") as int)) % 12) != 0 OR abs(cast(months_between(" + to + ", " + from
							+ ") as int)) < 12 THEN concat('-', cast((abs(cast(months_between(" + to + ", " + from
							+ ") as int)) % 12) as int), 'M') ELSE '' END) END)";
				}
				yield "try_cast(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + " as string)";
			}
			case "range" -> {
				if (args.size() == 1) {
					String str = emitArg(args, 0, bkmBySlot, slotNameResolver);
					yield "(CASE WHEN " + str + " IS NULL THEN NULL WHEN typeof(" + str + ") = 'string' AND (" + str
							+ " LIKE '[%' OR " + str + " LIKE '(%' OR " + str + " LIKE ']%') AND (" + str
							+ " LIKE '%]' OR " + str + " LIKE '%)' OR " + str + " LIKE '%[') AND instr(" + str
							+ ", '..') > 0 THEN named_struct('start', try_cast(trim(substring(" + str + ", 2, instr("
							+ str + ", '..') - 2)) as double), 'end', try_cast(trim(substring(" + str + ", instr(" + str
							+ ", '..') + 2, length(" + str + ") - instr(" + str
							+ ", '..') - 1)) as double), 'start included', (" + str + " LIKE '[%'), 'end included', ("
							+ str + " LIKE '%]'), 'startIncluded', (" + str + " LIKE '[%'), 'endIncluded', (" + str
							+ " LIKE '%]') ) ELSE NULL END)";
				} else if (args.size() == 2) {
					String start = emitArg(args, 0, bkmBySlot, slotNameResolver);
					String end = emitArg(args, 1, bkmBySlot, slotNameResolver);
					yield "(CASE WHEN " + start + " IS NULL OR " + end
							+ " IS NULL THEN NULL ELSE named_struct('start', " + start + ", 'end', " + end
							+ ", 'start included', true, 'end included', true"
							+ ", 'startIncluded', true, 'endIncluded', true) END)";
				} else if (args.size() == 4) {
					String start = emitArg(args, 0, bkmBySlot, slotNameResolver);
					String end = emitArg(args, 1, bkmBySlot, slotNameResolver);
					String sInc = emitArg(args, 2, bkmBySlot, slotNameResolver);
					String eInc = emitArg(args, 3, bkmBySlot, slotNameResolver);
					yield "(CASE WHEN " + start + " IS NULL OR " + end
							+ " IS NULL THEN NULL ELSE named_struct('start', " + start + ", 'end', " + end
							+ ", 'start included', " + sInc + ", 'end included', " + eInc + ", 'startIncluded', " + sInc
							+ ", 'endIncluded', " + eInc + ") END)";
				}
				yield "NULL";
			}

			// Math functions
			case "abs" -> {
				String x = emitArg(args, 0, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN typeof(" + x
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN abs(" + x
						+ ") WHEN typeof(" + x + ") = 'string' AND cast(" + x
						+ " as string) rlike '^-?P' THEN (CASE WHEN cast(" + x
						+ " as string) LIKE '-%' THEN substring(cast(" + x + " as string), 2) ELSE cast(" + x
						+ " as string) END) ELSE NULL END)";
			}
			case "floor" -> {
				String x = emitArg(args, 0, bkmBySlot, slotNameResolver);
				if (args.size() == 1) {
					yield "(CASE WHEN typeof(" + x
							+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN floor("
							+ x + ") ELSE NULL END)";
				}
				String scale = emitArg(args, 1, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN typeof(" + x
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN floor(" + x
						+ " * power(10, " + scale + ")) / power(10, " + scale + ") ELSE NULL END)";
			}
			case "ceiling", "ceil" -> {
				String x = emitArg(args, 0, bkmBySlot, slotNameResolver);
				if (args.size() == 1) {
					yield "(CASE WHEN typeof(" + x
							+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN ceil("
							+ x + ") ELSE NULL END)";
				}
				String scale = emitArg(args, 1, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN typeof(" + x
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN ceil(" + x
						+ " * power(10, " + scale + ")) / power(10, " + scale + ") ELSE NULL END)";
			}
			case "round", "decimal" -> {
				String n = emitArg(args, 0, bkmBySlot, slotNameResolver);
				if (args.size() == 1) {
					yield "(CASE WHEN typeof(" + n
							+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN round("
							+ n + ", 0) ELSE NULL END)";
				}
				String scale = emitArg(args, 1, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN typeof(" + n
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN round(" + n
						+ ", " + scale + ") ELSE NULL END)";
			}
			case "round up", "round_up" -> {
				String n = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String scale = args.size() > 1 ? emitArg(args, 1, bkmBySlot, slotNameResolver) : "0";
				yield "(CASE WHEN typeof(" + n
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN (CASE WHEN "
						+ n + " >= 0 THEN ceil(" + n + " * power(10, " + scale + ")) / power(10, " + scale
						+ ") ELSE floor(" + n + " * power(10, " + scale + ")) / power(10, " + scale
						+ ") END) ELSE NULL END)";
			}
			case "round down", "round_down" -> {
				String n = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String scale = args.size() > 1 ? emitArg(args, 1, bkmBySlot, slotNameResolver) : "0";
				yield "(CASE WHEN typeof(" + n
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN (CASE WHEN "
						+ n + " >= 0 THEN floor(" + n + " * power(10, " + scale + ")) / power(10, " + scale
						+ ") ELSE ceil(" + n + " * power(10, " + scale + ")) / power(10, " + scale
						+ ") END) ELSE NULL END)";
			}
			case "round half up", "round_half_up" -> {
				String n = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String scale = args.size() > 1 ? emitArg(args, 1, bkmBySlot, slotNameResolver) : "0";
				yield "(CASE WHEN typeof(" + n
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN (CASE WHEN ("
						+ n + " * power(10, " + scale + ")) - floor(" + n + " * power(10, " + scale
						+ ")) < 0.5 THEN floor(" + n + " * power(10, " + scale + ")) / power(10, " + scale
						+ ") ELSE ceil(" + n + " * power(10, " + scale + ")) / power(10, " + scale
						+ ") END) ELSE NULL END)";
			}
			case "round half down", "round_half_down", "round half even", "round_half_even" -> {
				String n = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String scale = args.size() > 1 ? emitArg(args, 1, bkmBySlot, slotNameResolver) : "0";
				yield "(CASE WHEN typeof(" + n
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN (CASE WHEN ("
						+ n + " * power(10, " + scale + ")) - floor(" + n + " * power(10, " + scale
						+ ")) <= 0.5 THEN floor(" + n + " * power(10, " + scale + ")) / power(10, " + scale
						+ ") ELSE ceil(" + n + " * power(10, " + scale + ")) / power(10, " + scale
						+ ") END) ELSE NULL END)";
			}
			case "sqrt" -> {
				String x = emitArg(args, 0, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN typeof(" + x
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') AND " + x
						+ " >= 0 THEN sqrt(" + x + ") ELSE NULL END)";
			}
			case "exp" -> {
				String x = emitArg(args, 0, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN typeof(" + x
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') THEN exp(" + x
						+ ") ELSE NULL END)";
			}
			case "ln", "log" -> {
				String x = emitArg(args, 0, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN typeof(" + x
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') AND " + x
						+ " > 0 THEN ln(" + x + ") ELSE NULL END)";
			}
			case "modulo", "mod" -> {
				String a = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String b = emitArg(args, 1, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN typeof(" + a
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') AND typeof(" + b
						+ ") IN ('tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal') AND " + b
						+ " != 0 THEN (" + a + " - floor(" + a + " / " + b + ") * " + b + ") ELSE NULL END)";
			}
			case "odd" -> {
				String x = emitArg(args, 0, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN typeof(" + x + ") IN ('tinyint', 'smallint', 'int', 'bigint') THEN (pmod(cast(" + x
						+ " as bigint), 2) <> 0) ELSE NULL END)";
			}
			case "even" -> {
				String x = emitArg(args, 0, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN typeof(" + x + ") IN ('tinyint', 'smallint', 'int', 'bigint') THEN (pmod(cast(" + x
						+ " as bigint), 2) = 0) ELSE NULL END)";
			}
			case "min" -> {
				if (args.size() == 1) {
					yield "array_min(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
				}
				yield "least(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";
			}
			case "max" -> {
				if (args.size() == 1) {
					yield "array_max(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
				}
				yield "greatest(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";
			}
			case "sum" -> {
				if (args.size() == 1) {
					yield "aggregate(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", 0.0D, (acc, x) -> acc + x)";
				}
				yield "(" + String.join(" + ",
						args.stream().map(a -> emitWithBkms(a, bkmBySlot, slotNameResolver)).toList()) + ")";
			}
			case "product" -> {
				if (args.size() == 1) {
					yield "aggregate(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", 1.0D, (acc, x) -> acc * x)";
				}
				yield "(" + String.join(" * ",
						args.stream().map(a -> emitWithBkms(a, bkmBySlot, slotNameResolver)).toList()) + ")";
			}
			case "mean", "avg" -> {
				if (args.size() == 1) {
					String arr = emitArg(args, 0, bkmBySlot, slotNameResolver);
					yield "(aggregate(" + arr + ", 0.0D, (acc, x) -> acc + x) / size(" + arr + "))";
				}
				String sumExpr = "(" + String.join(" + ",
						args.stream().map(a -> emitWithBkms(a, bkmBySlot, slotNameResolver)).toList()) + ")";
				yield "(" + sumExpr + " / " + args.size() + ".0)";
			}
			case "median" -> "element_at(array_sort(" + emitArg(args, 0, bkmBySlot, slotNameResolver)
					+ "), cast(ceil(size(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ") / 2.0) as int))";
			case "mode" -> "element_at(array_sort(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + "), 1)";
			case "stddev" -> {
				String list = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String avg = "(aggregate(" + list + ", 0.0D, (acc, x) -> acc + x) / size(" + list + "))";
				yield "(CASE WHEN " + list + " IS NULL OR size(" + list + ") <= 1 THEN NULL ELSE sqrt(aggregate(" + list
						+ ", 0.0D, (acc, x) -> acc + power(x - " + avg + ", 2)) / (size(" + list + ") - 1)) END)";
			}
			case "pmt", "pmt2" -> {
				String rate = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String nper = emitArg(args, 1, bkmBySlot, slotNameResolver);
				String pv = emitArg(args, 2, bkmBySlot, slotNameResolver);
				yield "((" + pv + " * " + rate + ") / (1.0 - power(1.0 + " + rate + ", -" + nper + ")))";
			}
			case "coalesce" -> "coalesce(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";

			// Temporal functions
			case "date" -> {
				if (args.size() == 3) {
					String y = emitArg(args, 0, bkmBySlot, slotNameResolver);
					String m = emitArg(args, 1, bkmBySlot, slotNameResolver);
					String d = emitArg(args, 2, bkmBySlot, slotNameResolver);
					yield "make_date(cast(" + y + " as int), cast(" + m + " as int), cast(" + d + " as int))";
				}
				yield "try_to_date(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			}
			case "date and time" -> {
				if (args.size() == 1) {
					yield "try_to_timestamp(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
				}
				yield "try_to_timestamp(concat(cast(" + emitArg(args, 0, bkmBySlot, slotNameResolver)
						+ " as string), 'T', cast(" + emitArg(args, 1, bkmBySlot, slotNameResolver) + " as string)))";
			}
			case "time" -> {
				if (args.size() == 1) {
					yield "try_to_timestamp(concat('1970-01-01T', " + emitArg(args, 0, bkmBySlot, slotNameResolver)
							+ "))";
				} else if (args.size() >= 3) {
					String h = emitArg(args, 0, bkmBySlot, slotNameResolver);
					String m = emitArg(args, 1, bkmBySlot, slotNameResolver);
					String s = emitArg(args, 2, bkmBySlot, slotNameResolver);
					yield "make_timestamp(1970, 1, 1, cast(" + h + " as int), cast(" + m + " as int), cast(" + s
							+ " as double))";
				}
				yield "try_to_timestamp(concat('1970-01-01T', " + emitArg(args, 0, bkmBySlot, slotNameResolver) + "))";
			}
			case "year" -> "year(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "month" -> "month(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "day" -> "day(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "weekday" -> "dayofweek(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "day of year", "day_of_year" -> "dayofyear(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "day of week", "day_of_week" ->
				"date_format(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", 'EEEE')";
			case "week of year", "week_of_year" -> "weekofyear(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "month of year", "month_of_year" ->
				"date_format(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", 'MMMM')";
			case "hour" -> "hour(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "minute" -> "minute(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "second" -> "second(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "today" -> "current_date()";
			case "now" -> "current_timestamp()";

			// List / Array functions
			case "list contains" -> "array_contains(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
			case "count" -> "size(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "reverse" -> "reverse(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "flatten" -> "flatten(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "distinct values" -> "array_distinct(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "union" -> "array_union(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";
			case "append" -> "concat(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", array("
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + "))";
			case "concatenate" -> "concat(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";
			case "sublist" -> {
				if (args.size() == 2) {
					yield "slice(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
							+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ", size("
							+ emitArg(args, 0, bkmBySlot, slotNameResolver) + "))";
				}
				yield "slice(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
						+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ", "
						+ emitArg(args, 2, bkmBySlot, slotNameResolver) + ")";
			}
			case "all" -> {
				if (args.size() == 1) {
					String arr = emitArg(args, 0, bkmBySlot, slotNameResolver);
					yield "(CASE WHEN " + arr + " IS NULL THEN NULL WHEN size(" + arr
							+ ") = 0 THEN TRUE WHEN array_contains(" + arr + ", false) THEN FALSE WHEN array_contains("
							+ arr + ", NULL) THEN NULL ELSE forall(" + arr + ", x -> x = true) END)";
				}
				String arr = "array(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";
				yield "(CASE WHEN array_contains(" + arr + ", false) THEN FALSE WHEN array_contains(" + arr
						+ ", NULL) THEN NULL ELSE TRUE END)";
			}
			case "any" -> {
				if (args.size() == 1) {
					String arr = emitArg(args, 0, bkmBySlot, slotNameResolver);
					yield "(CASE WHEN " + arr + " IS NULL THEN NULL WHEN size(" + arr
							+ ") = 0 THEN FALSE WHEN array_contains(" + arr + ", true) THEN TRUE WHEN array_contains("
							+ arr + ", NULL) THEN NULL ELSE FALSE END)";
				}
				String arr = "array(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";
				yield "(CASE WHEN array_contains(" + arr + ", true) THEN TRUE WHEN array_contains(" + arr
						+ ", NULL) THEN NULL ELSE FALSE END)";
			}
			case "index of", "indexof" -> "array_position(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
			case "remove" -> "concat(slice(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", 1, "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + " - 1), slice("
					+ emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + " + 1, size("
					+ emitArg(args, 0, bkmBySlot, slotNameResolver) + ")))";
			case "insert before" -> "concat(slice(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", 1, "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + " - 1), array("
					+ emitArg(args, 2, bkmBySlot, slotNameResolver) + "), slice("
					+ emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ", size("
					+ emitArg(args, 0, bkmBySlot, slotNameResolver) + ")))";
			case "list replace", "list_replace" -> {
				String list = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String pos = emitArg(args, 1, bkmBySlot, slotNameResolver);
				String item = emitArg(args, 2, bkmBySlot, slotNameResolver);
				yield "transform(" + list + ", (x, i) -> (CASE WHEN i + 1 = " + pos + " THEN " + item + " ELSE x END))";
			}
			case "sort" -> {
				String list = emitArg(args, 0, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN " + list + " IS NULL THEN NULL ELSE array_sort(" + list + ") END)";
			}

			// Context functions
			case "context" -> {
				if (args.isEmpty()) {
					yield "map()";
				}
				yield "map_from_entries(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			}
			case "context put", "context_put" -> {
				String ctx = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String key = emitArg(args, 1, bkmBySlot, slotNameResolver);
				String val = emitArg(args, 2, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN " + ctx + " IS NULL OR " + key + " IS NULL THEN NULL ELSE map_concat(" + ctx
						+ ", map(" + key + ", " + val + ")) END)";
			}
			case "context merge", "context_merge" -> {
				if (args.size() == 1) {
					yield "aggregate(" + emitArg(args, 0, bkmBySlot, slotNameResolver)
							+ ", map(), (acc, x) -> map_concat(acc, x))";
				}
				yield "map_concat(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";
			}
			case "get entries", "get_entries" -> "map_entries(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "get value", "get_value" -> "element_at(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ", "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";

			// Comparisons / Interval functions
			case "before" -> "(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + " < "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
			case "after" -> "(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + " > "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ")";
			case "during" -> "(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + " >= "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ".start AND "
					+ emitArg(args, 0, bkmBySlot, slotNameResolver) + " <= "
					+ emitArg(args, 1, bkmBySlot, slotNameResolver) + ".end)";

			// Boolean / Logical functions
			case "not" -> "(NOT " + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";

			default -> {
				StringBuilder sb = new StringBuilder(sanitizeName(name)).append("(");
				sb.append(emitArgsJoined(args, bkmBySlot, slotNameResolver));
				sb.append(")");
				yield sb.toString();
			}
		};
	}

	private static String emitArg(List<RuntimeExpression> args, int index, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		if (index >= args.size())
			return "NULL";
		return emitWithBkms(args.get(index), bkmBySlot, slotNameResolver);
	}

	private static String emitArgsJoined(List<RuntimeExpression> args, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(emitWithBkms(args.get(i), bkmBySlot, slotNameResolver));
		}
		return sb.toString();
	}

	public static String emitUnaryTests(String inputCol, RuntimeUnaryTests tests,
			IntFunction<String> slotNameResolver) {
		return emitUnaryTests(inputCol, tests, Map.of(), slotNameResolver);
	}

	public static String emitUnaryTests(String inputCol, RuntimeUnaryTests tests, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		if (tests == null || tests.tests().isEmpty() || tests.wildcard()) {
			return "TRUE";
		}
		StringBuilder sb = new StringBuilder("(");
		for (int i = 0; i < tests.tests().size(); i++) {
			if (i > 0)
				sb.append(" OR ");
			RuntimeUnaryTest test = tests.tests().get(i);
			if (test instanceof RuntimeExpressionUnaryTest exprTest) {
				if (exprTest.expression() instanceof RuntimeConstant rc && rc.kind() == RuntimeConstantKind.NULL) {
					sb.append("(").append(inputCol).append(" IS NULL)");
				} else if (exprTest.expression().type() != null
						&& exprTest.expression().type().kind() == RuntimeTypeKind.LIST) {
					sb.append("array_contains(")
							.append(emitWithBkms(exprTest.expression(), bkmBySlot, slotNameResolver)).append(", ")
							.append(inputCol).append(")");
				} else {
					sb.append("(").append(inputCol).append(" <=> ")
							.append(emitWithBkms(exprTest.expression(), bkmBySlot, slotNameResolver)).append(")");
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
							.append(emitWithBkms(compTest.endpoint(), bkmBySlot, slotNameResolver)).append(")");
				}
			} else if (test instanceof RuntimeRangeUnaryTest rangeTest) {
				RuntimeRangeExpression range = rangeTest.range();
				String lowerOp = range.lowerBoundary() == RuntimeRangeBoundary.CLOSED ? ">=" : ">";
				String upperOp = range.upperBoundary() == RuntimeRangeBoundary.CLOSED ? "<=" : "<";
				if (range.lower().isPresent() && range.upper().isPresent()) {
					sb.append("(").append(inputCol).append(" ").append(lowerOp).append(" ")
							.append(emitWithBkms(range.lower().get(), bkmBySlot, slotNameResolver)).append(" AND ")
							.append(inputCol).append(" ").append(upperOp).append(" ")
							.append(emitWithBkms(range.upper().get(), bkmBySlot, slotNameResolver)).append(")");
				} else if (range.lower().isPresent()) {
					sb.append("(").append(inputCol).append(" ").append(lowerOp).append(" ")
							.append(emitWithBkms(range.lower().get(), bkmBySlot, slotNameResolver)).append(")");
				} else if (range.upper().isPresent()) {
					sb.append("(").append(inputCol).append(" ").append(upperOp).append(" ")
							.append(emitWithBkms(range.upper().get(), bkmBySlot, slotNameResolver)).append(")");
				} else {
					sb.append("TRUE");
				}
			} else {
				sb.append("TRUE");
			}
		}
		sb.append(")");
		if (tests.negated()) {
			return "(NOT " + sb + ")";
		}
		return sb.toString();
	}

	public static String emitDecisionTable(RuntimeDecisionTable table, IntFunction<String> slotNameResolver) {
		return emitDecisionTable(table, Map.of(), slotNameResolver);
	}

	public static String emitDecisionTable(RuntimeDecisionTable table, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		RuntimeHitPolicy hitPolicy = table.hitPolicy();

		if (hitPolicy == RuntimeHitPolicy.COLLECT && table.aggregation().isPresent()) {
			RuntimeAggregation agg = table.aggregation().get();
			return emitCollectDecisionTable(table, agg, bkmBySlot, slotNameResolver);
		}

		if (hitPolicy == RuntimeHitPolicy.COLLECT || hitPolicy == RuntimeHitPolicy.RULE_ORDER) {
			return emitListCollectDecisionTable(table, bkmBySlot, slotNameResolver);
		}

		if (hitPolicy == RuntimeHitPolicy.OUTPUT_ORDER) {
			return emitOutputOrderDecisionTable(table, bkmBySlot, slotNameResolver);
		}

		if (hitPolicy == RuntimeHitPolicy.PRIORITY) {
			return emitPriorityDecisionTable(table, bkmBySlot, slotNameResolver);
		}

		// Standard CASE WHEN for UNIQUE, FIRST, ANY
		StringBuilder sb = new StringBuilder("CASE\n");
		for (int r = 0; r < table.rules().size(); r++) {
			RuntimeDecisionTableRule rule = table.rules().get(r);
			sb.append("    WHEN ").append(emitRuleCondition(table, rule, bkmBySlot, slotNameResolver));
			sb.append(" THEN ").append(emitRuleOutput(table, rule, bkmBySlot, slotNameResolver)).append("\n");
		}
		sb.append("    ELSE NULL\n  END");
		return sb.toString();
	}

	private static String emitPriorityDecisionTable(RuntimeDecisionTable table, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		List<RuntimeDecisionTableRule> sortedRules = new ArrayList<>(table.rules());
		sortedRules.sort(Comparator.comparingInt(rule -> computeRulePriorityRank(table, rule)));

		StringBuilder sb = new StringBuilder("CASE\n");
		for (RuntimeDecisionTableRule rule : sortedRules) {
			sb.append("    WHEN ").append(emitRuleCondition(table, rule, bkmBySlot, slotNameResolver));
			sb.append(" THEN ").append(emitRuleOutput(table, rule, bkmBySlot, slotNameResolver)).append("\n");
		}
		sb.append("    ELSE NULL\n  END");
		return sb.toString();
	}

	private static String emitOutputOrderDecisionTable(RuntimeDecisionTable table, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		List<RuntimeDecisionTableRule> sortedRules = new ArrayList<>(table.rules());
		sortedRules.sort(Comparator.comparingInt(rule -> computeRulePriorityRank(table, rule)));

		StringBuilder sb = new StringBuilder("filter(array(");
		for (int r = 0; r < sortedRules.size(); r++) {
			RuntimeDecisionTableRule rule = sortedRules.get(r);
			if (r > 0)
				sb.append(", ");
			sb.append("(CASE WHEN ").append(emitRuleCondition(table, rule, bkmBySlot, slotNameResolver))
					.append(" THEN ").append(emitRuleOutput(table, rule, bkmBySlot, slotNameResolver))
					.append(" ELSE NULL END)");
		}
		sb.append("), x -> x IS NOT NULL)");
		return sb.toString();
	}

	private static int computeRulePriorityRank(RuntimeDecisionTable table, RuntimeDecisionTableRule rule) {
		int rank = 0;
		int multiplier = 1000;
		for (int o = 0; o < table.outputs().size(); o++) {
			RuntimeDecisionTableOutput out = table.outputs().get(o);
			if (out.allowedValues().isPresent() && o < rule.outputEntries().size()) {
				RuntimeExpression outExpr = rule.outputEntries().get(o);
				if (outExpr instanceof RuntimeConstant rc) {
					String outVal = rc.value();
					List<String> allowed = extractAllowedValues(out.allowedValues().get());
					int idx = allowed.indexOf(outVal);
					int pos = idx >= 0 ? idx : 999;
					rank = rank * multiplier + pos;
				}
			}
		}
		return rank;
	}

	private static List<String> extractAllowedValues(RuntimeUnaryTests tests) {
		List<String> list = new ArrayList<>();
		for (RuntimeUnaryTest test : tests.tests()) {
			if (test instanceof RuntimeComparisonUnaryTest comp && comp.endpoint() instanceof RuntimeConstant rc) {
				list.add(rc.value());
			} else if (test instanceof RuntimeExpressionUnaryTest expr
					&& expr.expression() instanceof RuntimeConstant rc) {
				list.add(rc.value());
			}
		}
		return list;
	}

	private static String emitRuleCondition(RuntimeDecisionTable table, RuntimeDecisionTableRule rule,
			Map<Integer, RuntimeBkm> bkmBySlot, IntFunction<String> slotNameResolver) {
		if (rule.inputEntries().isEmpty()) {
			return "TRUE";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < rule.inputEntries().size(); i++) {
			if (i > 0)
				sb.append(" AND ");
			RuntimeDecisionTableInput input = table.inputs().get(i);
			String inputExpr = emitWithBkms(input.expression(), bkmBySlot, slotNameResolver);
			RuntimeUnaryTests tests = rule.inputEntries().get(i);
			sb.append(emitUnaryTests(inputExpr, tests, bkmBySlot, slotNameResolver));
		}
		return sb.toString();
	}

	private static String emitRuleOutput(RuntimeDecisionTable table, RuntimeDecisionTableRule rule,
			Map<Integer, RuntimeBkm> bkmBySlot, IntFunction<String> slotNameResolver) {
		if (rule.outputEntries().isEmpty()) {
			return "NULL";
		}
		if (table.outputs().size() > 1) {
			StringBuilder sb = new StringBuilder("named_struct(");
			for (int o = 0; o < table.outputs().size(); o++) {
				if (o > 0)
					sb.append(", ");
				String name = table.outputs().get(o).name().orElse("output" + (o + 1));
				String valExpr = o < rule.outputEntries().size()
						? emitWithBkms(rule.outputEntries().get(o), bkmBySlot, slotNameResolver)
						: "NULL";
				sb.append("'").append(escapeSqlString(name)).append("', ").append(valExpr);
			}
			sb.append(")");
			return sb.toString();
		}
		return emitWithBkms(rule.outputEntries().get(0), bkmBySlot, slotNameResolver);
	}

	private static String emitCollectDecisionTable(RuntimeDecisionTable table, RuntimeAggregation agg,
			Map<Integer, RuntimeBkm> bkmBySlot, IntFunction<String> slotNameResolver) {
		return switch (agg) {
			case SUM -> {
				StringBuilder sb = new StringBuilder("coalesce(");
				for (int r = 0; r < table.rules().size(); r++) {
					RuntimeDecisionTableRule rule = table.rules().get(r);
					if (r > 0)
						sb.append(" + ");
					sb.append("(CASE WHEN ").append(emitRuleCondition(table, rule, bkmBySlot, slotNameResolver))
							.append(" THEN ").append(emitRuleOutput(table, rule, bkmBySlot, slotNameResolver))
							.append(" ELSE 0 END)");
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
					sb.append("(CASE WHEN ").append(emitRuleCondition(table, rule, bkmBySlot, slotNameResolver))
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
					sb.append("(CASE WHEN ").append(emitRuleCondition(table, rule, bkmBySlot, slotNameResolver))
							.append(" THEN ").append(emitRuleOutput(table, rule, bkmBySlot, slotNameResolver))
							.append(" ELSE NULL END)");
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
					sb.append("(CASE WHEN ").append(emitRuleCondition(table, rule, bkmBySlot, slotNameResolver))
							.append(" THEN ").append(emitRuleOutput(table, rule, bkmBySlot, slotNameResolver))
							.append(" ELSE NULL END)");
				}
				sb.append(")");
				yield sb.toString();
			}
		};
	}

	private static String emitListCollectDecisionTable(RuntimeDecisionTable table, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		StringBuilder sb = new StringBuilder("filter(array(");
		for (int r = 0; r < table.rules().size(); r++) {
			RuntimeDecisionTableRule rule = table.rules().get(r);
			if (r > 0)
				sb.append(", ");
			sb.append("(CASE WHEN ").append(emitRuleCondition(table, rule, bkmBySlot, slotNameResolver))
					.append(" THEN ").append(emitRuleOutput(table, rule, bkmBySlot, slotNameResolver))
					.append(" ELSE NULL END)");
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
