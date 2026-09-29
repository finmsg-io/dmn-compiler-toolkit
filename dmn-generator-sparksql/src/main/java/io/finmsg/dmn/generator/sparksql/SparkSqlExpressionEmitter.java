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
			case TIME ->
				// Time literals emitted as raw strings so temporalAddSubSql can detect and
				// handle them (including zoned variants like '10:10:10+11:00', '10:10:10@Tz')
				"'" + escapeSqlString(constant.value()) + "'";
			case DATE_TIME -> {
				String val = constant.value();
				// T24:00:00 midnight roll
				if (val.contains("T24:00:00") || val.contains(" 24:00:00")) {
					String[] parts = val.split("[T ]");
					try {
						java.time.LocalDate next = java.time.LocalDate.parse(parts[0]).plusDays(1);
						String rest = parts.length > 1 ? parts[1].substring(8) : "";
						val = next + "T00:00:00" + rest;
					} catch (Exception ignored) {
					}
				}
				// Zoned / offset / BC datetimes: emit as raw string literal
				// (try_to_timestamp cannot parse '+11:00', '@Tz', or '-2021-...' forms)
				if (val.contains("@") || val.startsWith("-") || val.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$")) {
					yield "'" + escapeSqlString(val) + "'";
				}
				// Plain local datetime
				yield "try_to_timestamp('" + escapeSqlString(val.replace("T", " ")) + "')";
			}
			case DURATION -> "'" + escapeSqlString(constant.value()) + "'";
			default -> "'" + escapeSqlString(constant.value()) + "'";
		};
	}

	// ── Duration formatting helpers ──────────────────────────────────────────────

	/**
	 * Formats total seconds (possibly fractional) as ISO-8601 day-time duration
	 * string.
	 */
	private static String formatDayTimeDurationSql(String secSql) {
		return "(CASE WHEN (" + secSql + ") IS NULL THEN NULL " + "WHEN (" + secSql + ") = 0 THEN 'PT0S' "
				+ "ELSE concat(" + "(CASE WHEN (" + secSql + ") < 0 THEN '-P' ELSE 'P' END), " + "(CASE WHEN cast(abs("
				+ secSql + ") / 86400 as bigint) > 0 THEN concat(cast(cast(abs(" + secSql
				+ ") / 86400 as bigint) as string), 'D') ELSE '' END), " + "(CASE WHEN (cast(abs(" + secSql
				+ ") as bigint) % 86400) > 0 OR (abs(" + secSql + ") - cast(abs(" + secSql
				+ ") as bigint)) > 0 OR cast(abs(" + secSql + ") / 86400 as bigint) = 0 THEN concat('T', "
				+ "(CASE WHEN cast((cast(abs(" + secSql
				+ ") as bigint) % 86400) / 3600 as bigint) > 0 THEN concat(cast(cast((cast(abs(" + secSql
				+ ") as bigint) % 86400) / 3600 as bigint) as string), 'H') ELSE '' END), "
				+ "(CASE WHEN cast((cast(abs(" + secSql
				+ ") as bigint) % 3600) / 60 as bigint) > 0 THEN concat(cast(cast((cast(abs(" + secSql
				+ ") as bigint) % 3600) / 60 as bigint) as string), 'M') ELSE '' END), " + "(CASE WHEN (abs(" + secSql
				+ ") - cast(abs(" + secSql + ") / 60 as bigint) * 60) > 0 OR (cast(abs(" + secSql
				+ ") / 86400 as bigint) = 0 AND cast((cast(abs(" + secSql
				+ ") as bigint) % 86400) / 3600 as bigint) = 0 AND cast((cast(abs(" + secSql
				+ ") as bigint) % 3600) / 60 as bigint) = 0) " + "THEN concat(cast(abs(" + secSql + ") - cast(abs("
				+ secSql + ") / 60 as bigint) * 60 as string), 'S') ELSE '' END)" + ") ELSE '' END)" + ") END)";
	}

	/** Formats total months as ISO-8601 year-month duration string. */
	private static String formatYearMonthDurationSql(String monthSql) {
		return "(CASE WHEN (" + monthSql + ") IS NULL THEN NULL " + "WHEN (" + monthSql + ") = 0 THEN 'P0M' "
				+ "ELSE concat(" + "(CASE WHEN (" + monthSql + ") < 0 THEN '-P' ELSE 'P' END), "
				+ "(CASE WHEN cast(abs(" + monthSql + ") / 12 as bigint) > 0 THEN concat(cast(cast(abs(" + monthSql
				+ ") / 12 as bigint) as string), 'Y') ELSE '' END), " + "(CASE WHEN (cast(abs(" + monthSql
				+ ") as bigint) % 12) > 0 OR cast(abs(" + monthSql + ") / 12 as bigint) = 0 THEN concat(cast(cast(abs("
				+ monthSql + ") as bigint) % 12 as string), 'M') ELSE '' END)" + ") END)";
	}

	// ── Duration arithmetic helpers ──────────────────────────────────────────────

	private static String durationAddSql(String left, String right) {
		return "(CASE WHEN (" + left + " LIKE '%Y%' OR (" + left + " LIKE '%M%' AND " + left + " NOT LIKE '%T%')) AND ("
				+ right + " LIKE '%Y%' OR (" + right + " LIKE '%M%' AND " + right + " NOT LIKE '%T%')) AND " + left
				+ " NOT LIKE '%D%' AND " + left + " NOT LIKE '%T%' AND " + right + " NOT LIKE '%D%' AND " + right
				+ " NOT LIKE '%T%' THEN "
				+ formatYearMonthDurationSql(
						"((CASE WHEN " + left + " LIKE '-%' THEN -1 ELSE 1 END) * (coalesce(try_cast(regexp_extract("
								+ left + ", '([0-9]+)Y', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract("
								+ left + ", '([0-9]+)M', 1) as bigint), 0L)) + (CASE WHEN " + right
								+ " LIKE '-%' THEN -1 ELSE 1 END) * (coalesce(try_cast(regexp_extract(" + right
								+ ", '([0-9]+)Y', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract(" + right
								+ ", '([0-9]+)M', 1) as bigint), 0L)))")
				+ " WHEN (" + left + " LIKE '%D%' OR " + left + " LIKE '%T%') AND (" + right + " LIKE '%D%' OR " + right
				+ " LIKE '%T%') AND " + left + " NOT LIKE '%Y%' AND " + right + " NOT LIKE '%Y%' THEN "
				+ formatDayTimeDurationSql("((CASE WHEN " + left
						+ " LIKE '-%' THEN -1.0 ELSE 1.0 END) * (coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)D', 1) as bigint), 0L) * 86400 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)H', 1) as bigint), 0L) * 3600 + coalesce(try_cast(regexp_extract(" + left
						+ ", 'T.*?([0-9]+)M', 1) as bigint), 0L) * 60 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)) + (CASE WHEN " + right
						+ " LIKE '-%' THEN -1.0 ELSE 1.0 END) * (coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)D', 1) as bigint), 0L) * 86400 + coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)H', 1) as bigint), 0L) * 3600 + coalesce(try_cast(regexp_extract(" + right
						+ ", 'T.*?([0-9]+)M', 1) as bigint), 0L) * 60 + coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)))")
				+ " ELSE NULL END)";
	}

	private static String durationSubSql(String left, String right) {
		return "(CASE WHEN (" + left + " LIKE '%Y%' OR (" + left + " LIKE '%M%' AND " + left + " NOT LIKE '%T%')) AND ("
				+ right + " LIKE '%Y%' OR (" + right + " LIKE '%M%' AND " + right + " NOT LIKE '%T%')) AND " + left
				+ " NOT LIKE '%D%' AND " + left + " NOT LIKE '%T%' AND " + right + " NOT LIKE '%D%' AND " + right
				+ " NOT LIKE '%T%' THEN "
				+ formatYearMonthDurationSql(
						"((CASE WHEN " + left + " LIKE '-%' THEN -1 ELSE 1 END) * (coalesce(try_cast(regexp_extract("
								+ left + ", '([0-9]+)Y', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract("
								+ left + ", '([0-9]+)M', 1) as bigint), 0L)) - (CASE WHEN " + right
								+ " LIKE '-%' THEN -1 ELSE 1 END) * (coalesce(try_cast(regexp_extract(" + right
								+ ", '([0-9]+)Y', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract(" + right
								+ ", '([0-9]+)M', 1) as bigint), 0L)))")
				+ " WHEN (" + left + " LIKE '%D%' OR " + left + " LIKE '%T%') AND (" + right + " LIKE '%D%' OR " + right
				+ " LIKE '%T%') AND " + left + " NOT LIKE '%Y%' AND " + right + " NOT LIKE '%Y%' THEN "
				+ formatDayTimeDurationSql("((CASE WHEN " + left
						+ " LIKE '-%' THEN -1.0 ELSE 1.0 END) * (coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)D', 1) as bigint), 0L) * 86400 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)H', 1) as bigint), 0L) * 3600 + coalesce(try_cast(regexp_extract(" + left
						+ ", 'T.*?([0-9]+)M', 1) as bigint), 0L) * 60 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)) - (CASE WHEN " + right
						+ " LIKE '-%' THEN -1.0 ELSE 1.0 END) * (coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)D', 1) as bigint), 0L) * 86400 + coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)H', 1) as bigint), 0L) * 3600 + coalesce(try_cast(regexp_extract(" + right
						+ ", 'T.*?([0-9]+)M', 1) as bigint), 0L) * 60 + coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)))")
				+ " ELSE NULL END)";
	}

	// ── Type detection helpers (compile-time SQL-expression analysis) ────────────

	private static boolean isLikelyDurationSql(String sql) {
		if (sql == null)
			return false;
		String t = sql.trim();
		return t.startsWith("'-P") || t.startsWith("'P") || t.startsWith("'-PT") || t.startsWith("'PT")
				|| t.contains("formatDayTimeDurationSql") || t.contains("formatYearMonthDurationSql")
				|| t.contains("durationAddSql") || t.contains("durationSubSql");
	}

	/**
	 * A SQL expression that evaluates to a time value (HH:MM:SS or HH:MM:SS+offset
	 * or HH:MM:SS@Tz).
	 */
	private static boolean isLikelyTimeSql(String sql) {
		if (sql == null)
			return false;
		String t = sql.trim();
		// Raw time string literals: '10:10:10', '10:10:10+11:00', '10:10:10@Tz'
		return t.matches("^'[0-9]{2}:[0-9]{2}.*'$");
	}

	/** A SQL expression that evaluates to a date, datetime, or timestamp. */
	private static boolean isLikelyDateTimeOrDateSql(String sql) {
		if (sql == null)
			return false;
		String t = sql.trim();
		if (isLikelyTimeSql(t) || isLikelyDurationSql(t))
			return false;
		return t.startsWith("TIMESTAMP '") || t.startsWith("DATE '") || t.contains("to_timestamp(")
				|| t.contains("to_utc_timestamp(") || t.contains("try_to_timestamp(") || t.contains("try_to_date(")
				|| (t.contains("make_timestamp(") && !t.contains("1970, 1, 1")) || t.contains("date_add(")
				|| t.contains("date_sub(") || t.contains("add_months(")
				// Raw datetime string literals: '2021-01-01T10:10:10+11:00', '-2021-01-01T...'
				|| t.matches("^'(-?[0-9]{4,}-[0-9]{2}-[0-9]{2}.*)'$");
	}

	private static boolean isLikelyStringSql(String sql) {
		if (sql == null)
			return false;
		String t = sql.trim();
		if (isLikelyDurationSql(t) || isLikelyTimeSql(t) || isLikelyDateTimeOrDateSql(t))
			return false;
		return (t.startsWith("'") && t.endsWith("'")) || t.startsWith("concat(") || t.startsWith("concat_ws(")
				|| t.startsWith("upper(") || t.startsWith("lower(") || t.startsWith("trim(")
				|| t.startsWith("substring(") || t.startsWith("regexp_replace(")
				|| (t.contains("cast(") && t.contains("as string)"));
	}

	private static boolean isDuration(RuntimeTypeKind k) {
		return k == RuntimeTypeKind.DURATION || k == RuntimeTypeKind.DAYS_TIME_DURATION
				|| k == RuntimeTypeKind.YEARS_MONTHS_DURATION;
	}

	// ── UTC-aware epoch seconds for zoned datetime subtraction ──────────────────

	/**
	 * Generates SQL to compute UTC epoch seconds from a datetime expression.
	 * Handles: local datetime (no zone), @-named timezone, +HH:MM offset, plain
	 * date. All CASE branches return BIGINT (never DATE or TIMESTAMP).
	 */
	private static String utcSecondsSql(String expr) {
		String s = "cast(" + expr + " as string)";
		// Extract local datetime part (YYYY-MM-DD HH:MM:SS)
		String localDt = "replace(regexp_extract(" + s
				+ ", '^(-?[0-9]{4,}-[0-9]{2}-[0-9]{2}[T ][0-9]{2}:[0-9]{2}:[0-9]{2})', 1), 'T', ' ')";
		// Derive timezone: @Name or GMT+HH:MM or UTC
		String zone = "coalesce(nullif(regexp_extract(" + s
				+ ", '@(.*)$', 1), ''), nullif(concat('GMT', regexp_extract(" + s
				+ ", '([+-][0-9]{2}:[0-9]{2})$', 1)), 'GMT'), 'UTC')";
		return "(CASE "
				// Plain date (no T, no colon) → treat as midnight UTC
				+ "WHEN instr(" + s + ", 'T') = 0 AND instr(" + s + ", ':') = 0 "
				+ "THEN unix_timestamp(try_to_timestamp(concat(regexp_extract(" + s
				+ ", '^(-?[0-9]{4,}-[0-9]{2}-[0-9]{2})', 1), ' 00:00:00'))) "
				// Zoned/offset datetime → convert to UTC
				+ "ELSE unix_timestamp(to_utc_timestamp(try_to_timestamp(" + localDt + "), " + zone + ")) END)";
	}

	// ── Temporal + Duration arithmetic ──────────────────────────────────────────

	/**
	 * Generates SQL for: temporal ± duration → temporal. Handles all FEEL datetime
	 * flavours (local, @tz, +offset, BC, plain date, time). IMPORTANT: every CASE
	 * branch must return STRING to avoid Catalyst type-unification casting a branch
	 * result to DATE/TIMESTAMP which causes CAST_INVALID_INPUT.
	 */
	private static String temporalAddSubSql(String left, String right, boolean isSubtract) {
		String dur = right;
		String signMult = isSubtract
				? "(-1 * (CASE WHEN " + dur + " LIKE '-%' THEN -1 ELSE 1 END))"
				: "((CASE WHEN " + dur + " LIKE '-%' THEN -1 ELSE 1 END))";
		String monthsExpr = signMult + " * (coalesce(try_cast(regexp_extract(" + dur
				+ ", 'P(?:(-?[0-9]+)Y)?', 1) as int), 0) * 12" + " + coalesce(try_cast(regexp_extract(" + dur
				+ ", 'P(?:.*?)?(-?[0-9]+)M', 1) as int), 0))";
		String daysExpr = signMult + " * coalesce(try_cast(regexp_extract(" + dur
				+ ", 'P(?:(-?[0-9]+)D)?', 1) as int), 0)";
		String secSignMult = isSubtract
				? "(-1.0 * (CASE WHEN " + dur + " LIKE '-%' THEN -1.0 ELSE 1.0 END))"
				: "((CASE WHEN " + dur + " LIKE '-%' THEN -1.0 ELSE 1.0 END))";
		String secsExpr = secSignMult + " * (coalesce(try_cast(regexp_extract(" + dur
				+ ", 'P(?:(-?[0-9]+)D)?', 1) as bigint), 0L) * 86400" + " + coalesce(try_cast(regexp_extract(" + dur
				+ ", 'T(?:(-?[0-9]+)H)?', 1) as bigint), 0L) * 3600" + " + coalesce(try_cast(regexp_extract(" + dur
				+ ", 'T(?:.*?)?(-?[0-9]+)M', 1) as bigint), 0L) * 60" + " + coalesce(try_cast(regexp_extract(" + dur
				+ ", 'T(?:.*?)?(-?[0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0))";

		String lStr = "cast(" + left + " as string)";

		// Safely extract parts via regexp (avoids Spark 3 rejecting 'T' / offset /
		// negative-year in to_date)
		String lDate = "regexp_extract(" + lStr + ", '^(-?[0-9]{4,}-[0-9]{2}-[0-9]{2})', 1)";
		String lLocalDt = "replace(regexp_extract(" + lStr
				+ ", '^(-?[0-9]{4,}-[0-9]{2}-[0-9]{2}[T ][0-9]{2}:[0-9]{2}:[0-9]{2})', 1), 'T', ' ')";
		String lTSuffix = "(CASE WHEN instr(" + lStr + ", 'T') > 0 THEN substring(" + lStr + ", instr(" + lStr
				+ ", 'T')) ELSE '' END)";
		String lOffset = "regexp_extract(" + lStr + ", '([+-][0-9]{2}:[0-9]{2})$', 1)";
		String lAtSuffix = "substring(" + lStr + ", instr(" + lStr + ", '@'))";

		// BC month arithmetic (pure string arithmetic — never calls to_date on negative
		// years)
		String bcMonthsSql = "concat(" + "cast(floor((try_cast(regexp_extract(" + lStr
				+ ", '^(-?[0-9]+)-', 1) as bigint) * 12" + " + try_cast(regexp_extract(" + lStr
				+ ", '^-?[0-9]+-([0-9]{2})-', 1) as int) - 1 + " + monthsExpr + ") / 12.0) as bigint), "
				+ "'-', lpad(cast((try_cast(regexp_extract(" + lStr + ", '^(-?[0-9]+)-', 1) as bigint) * 12"
				+ " + try_cast(regexp_extract(" + lStr + ", '^-?[0-9]+-([0-9]{2})-', 1) as int) - 1 + " + monthsExpr
				+ ")" + " - floor((try_cast(regexp_extract(" + lStr + ", '^(-?[0-9]+)-', 1) as bigint) * 12"
				+ " + try_cast(regexp_extract(" + lStr + ", '^-?[0-9]+-([0-9]{2})-', 1) as int) - 1 + " + monthsExpr
				+ ") / 12.0) * 12 + 1 as string), 2, '0'), " + "'-', regexp_extract(" + lStr
				+ ", '^-?[0-9]+-[0-9]{2}-([0-9]{2})', 1), " + lTSuffix + ")";

		// Time epoch: ALWAYS via explicit concat('1970-01-01 ', hms) — never
		// unix_timestamp(rawString)
		// which causes Spark Catalyst to attempt cast(string as DATE) during type
		// resolution.
		String lTimeHms = "regexp_extract(" + lStr + ", '^([0-9]{2}:[0-9]{2}(?::[0-9]{2}(?:\\\\.[0-9]+)?)?)', 1)";
		String lTimeEpoch = "unix_timestamp(try_to_timestamp(concat('1970-01-01 ', " + lTimeHms + ")))";
		String timeSum = "(" + lTimeEpoch + " + cast(" + secsExpr + " as bigint))";
		String timeMod = "(" + timeSum + " % 86400 + (CASE WHEN " + timeSum + " % 86400 < 0 THEN 86400 ELSE 0 END))";
		// All time results → STRING via date_format — never naked DATE/TIMESTAMP
		String timeFmt = "date_format(try_to_timestamp(from_unixtime(" + timeMod + ")), 'HH:mm:ss')";

		// Datetime epoch (local part only, zone stripped)
		String lDtEpoch = "unix_timestamp(try_to_timestamp(" + lLocalDt + "))";
		String dtSum = "(" + lDtEpoch + " + cast(" + secsExpr + " as bigint))";
		// DT results → STRING via date_format — never naked DATE/TIMESTAMP
		String dtDate = "date_format(try_to_timestamp(from_unixtime(" + dtSum + ")), 'yyyy-MM-dd')";
		String dtTime = "date_format(try_to_timestamp(from_unixtime(" + dtSum + ")), 'HH:mm:ss')";

		return "(CASE "
				// ══ YM-DURATION ═══════════════════════════════════════════════════════════
				+ "WHEN (" + dur + " LIKE '%Y%' OR (" + dur + " LIKE '%M%' AND " + dur + " NOT LIKE '%T%')) AND " + dur
				+ " NOT LIKE '%D%' THEN " + "(CASE "
				// BC: pure arithmetic (never calls to_date on negative year string)
				+ "WHEN " + lStr + " LIKE '-%' THEN " + bcMonthsSql + " "
				// @-zoned datetime: extract date via regexp, keep T-suffix with @tz
				+ "WHEN instr(" + lStr + ", '@') > 0 THEN " + "concat(cast(add_months(to_date(" + lDate + "), "
				+ monthsExpr + ") as string), " + lTSuffix + ") "
				// Offset datetime (+HH:MM): extract date via regexp, reattach offset
				+ "WHEN " + lStr + " rlike '([+-][0-9]{2}:[0-9]{2})$' THEN " + "concat(cast(add_months(to_date(" + lDate
				+ "), " + monthsExpr + ") as string), 'T', " + "date_format(try_to_timestamp(" + lLocalDt
				+ "), 'HH:mm:ss'), " + lOffset + ") "
				// Local datetime with T or space
				+ "WHEN typeof(" + left + ") = 'timestamp' OR instr(" + lStr + ", 'T') > 0 OR instr(" + lStr
				+ ", ' ') > 0 THEN " + "concat(cast(add_months(to_date(" + lDate + "), " + monthsExpr + ") as string), "
				+ "(CASE WHEN instr(" + lStr + ", 'T') > 0 THEN substring(" + lStr + ", instr(" + lStr + ", 'T')) "
				+ "WHEN instr(" + lStr + ", ' ') > 0 THEN concat('T', substring(" + lStr + ", instr(" + lStr
				+ ", ' ') + 1)) " + "ELSE '' END)) "
				// Plain date
				+ "ELSE cast(add_months(to_date(" + lStr + "), " + monthsExpr + ") as string) END) "

				// ══ DT-DURATION ═══════════════════════════════════════════════════════════
				+ "ELSE " + "(CASE "
				// TIME strings detected FIRST — before @-datetime — so '10:10:10@Tz' doesn't
				// fall into the @-zoned-datetime branch. All branches return STRING via
				// date_format.
				+ "WHEN " + lStr + " LIKE '1970-01-01%' OR " + lStr + " rlike '^[0-9]{2}:[0-9]{2}' THEN "
				+ "(CASE WHEN instr(" + lStr + ", '@') > 0 THEN concat(" + timeFmt + ", " + lAtSuffix + ") " + "WHEN "
				+ lStr + " rlike '([+-][0-9]{2}:[0-9]{2})$' THEN concat(" + timeFmt + ", " + lOffset + ") " + "ELSE "
				+ timeFmt + " END) "
				// Plain date (no T / space / colon) — cast(... as string) everywhere, no naked
				// DATE
				+ "WHEN typeof(" + left + ") = 'date' OR (instr(" + lStr + ", 'T') = 0 AND instr(" + lStr
				+ ", ' ') = 0 AND instr(" + lStr + ", ':') = 0) THEN " + "(CASE WHEN " + dur
				+ " NOT LIKE '%T%' THEN cast(date_add(to_date(" + lStr + "), cast(" + daysExpr + " as int)) as string) "
				+ "ELSE cast(to_date(try_to_timestamp(from_unixtime(unix_timestamp(try_to_timestamp(concat(" + lStr
				+ ", ' 00:00:00'))) + cast(" + secsExpr + " as bigint)))) as string) END) "
				// @-zoned datetime
				+ "WHEN instr(" + lStr + ", '@') > 0 THEN " + "(CASE WHEN " + dur + " NOT LIKE '%T%' THEN "
				+ "concat(cast(date_add(to_date(" + lDate + "), cast(" + daysExpr + " as int)) as string), " + lTSuffix
				+ ") " + "ELSE concat(" + dtDate + ", 'T', " + dtTime + ", " + lAtSuffix + ") END) "
				// Offset datetime (+HH:MM)
				+ "WHEN " + lStr + " rlike '([+-][0-9]{2}:[0-9]{2})$' THEN " + "(CASE WHEN " + dur
				+ " NOT LIKE '%T%' THEN " + "concat(cast(date_add(to_date(" + lDate + "), cast(" + daysExpr
				+ " as int)) as string), 'T', " + "date_format(try_to_timestamp(" + lLocalDt + "), 'HH:mm:ss'), "
				+ lOffset + ") " + "ELSE concat(" + dtDate + ", 'T', " + dtTime + ", " + lOffset + ") END) "
				// Local datetime with T or space
				+ "WHEN typeof(" + left + ") = 'timestamp' OR instr(" + lStr + ", 'T') > 0 OR instr(" + lStr
				+ ", ' ') > 0 THEN " + "(CASE WHEN " + dur + " NOT LIKE '%T%' THEN "
				+ "concat(cast(date_add(to_date(coalesce(split(" + lStr + ", 'T')[0], split(" + lStr
				+ ", ' ')[0])), cast(" + daysExpr + " as int)) as string), " + "(CASE WHEN instr(" + lStr
				+ ", 'T') > 0 THEN substring(" + lStr + ", instr(" + lStr + ", 'T')) " + "WHEN instr(" + lStr
				+ ", ' ') > 0 THEN concat('T', substring(" + lStr + ", instr(" + lStr + ", ' ') + 1)) "
				+ "ELSE '' END)) " + "ELSE concat(" + dtDate + ", 'T', " + dtTime + ") END) "
				// Fallback
				+ "ELSE cast(date_add(to_date(" + lStr + "), cast(" + daysExpr + " as int)) as string) END) " + "END)";
	}

	private static RuntimeTypeKind resolveTypeKind(RuntimeExpression expr) {
		if (expr == null)
			return RuntimeTypeKind.ANY;
		if (expr.type() != null && expr.type().kind() != null) {
			return expr.type().kind();
		}
		if (expr instanceof RuntimeConstant c) {
			return switch (c.kind()) {
				case BOOLEAN -> RuntimeTypeKind.BOOLEAN;
				case STRING -> RuntimeTypeKind.STRING;
				case NUMBER -> RuntimeTypeKind.NUMBER;
				case DATE -> RuntimeTypeKind.DATE;
				case TIME -> RuntimeTypeKind.TIME;
				case DATE_TIME -> RuntimeTypeKind.DATE_TIME;
				case DURATION -> RuntimeTypeKind.DURATION;
				case NULL -> RuntimeTypeKind.ANY;
				default -> RuntimeTypeKind.ANY;
			};
		}
		if (expr instanceof RuntimeListExpression) {
			return RuntimeTypeKind.LIST;
		}
		if (expr instanceof RuntimeContextExpression) {
			return RuntimeTypeKind.CONTEXT;
		}
		if (expr instanceof RuntimeRangeExpression) {
			return RuntimeTypeKind.RANGE;
		}
		if (expr instanceof RuntimeFilterExpression) {
			return RuntimeTypeKind.LIST;
		}
		if (expr instanceof RuntimeForExpression) {
			return RuntimeTypeKind.LIST;
		}
		if (expr instanceof RuntimeQuantifiedExpression) {
			return RuntimeTypeKind.BOOLEAN;
		}
		if (expr instanceof RuntimeUnaryExpression u) {
			if (u.operator() == RuntimeUnaryOperator.NOT) {
				return RuntimeTypeKind.BOOLEAN;
			}
			return resolveTypeKind(u.operand());
		}
		if (expr instanceof RuntimeBinaryExpression b) {
			RuntimeBinaryOperator op = b.operator();
			if (op == RuntimeBinaryOperator.AND || op == RuntimeBinaryOperator.OR || op == RuntimeBinaryOperator.EQUAL
					|| op == RuntimeBinaryOperator.NOT_EQUAL || op == RuntimeBinaryOperator.LESS
					|| op == RuntimeBinaryOperator.LESS_EQUAL || op == RuntimeBinaryOperator.GREATER
					|| op == RuntimeBinaryOperator.GREATER_EQUAL) {
				return RuntimeTypeKind.BOOLEAN;
			}
			return RuntimeTypeKind.ANY;
		}
		if (expr instanceof RuntimeFunctionCall fc) {
			return resolveFunctionTypeKind(fc.function());
		}
		if (expr instanceof RuntimeInvocationExpression inv && inv.function().isPresent()) {
			return resolveFunctionTypeKind(inv.function().get());
		}
		return RuntimeTypeKind.ANY;
	}

	private static RuntimeTypeKind resolveFunctionTypeKind(String fnName) {
		String name = fnName.toLowerCase();
		return switch (name) {
			case "string length", "length", "upper case", "upper", "lower case", "lower", "substring",
					"substring before", "substring after", "replace", "trim", "concat", "string", "string join" ->
				RuntimeTypeKind.STRING;
			case "number", "abs", "floor", "ceiling", "ceil", "round", "round up", "round_up", "round down",
					"round_down", "round half up", "round_half_up", "round half down", "round_half_down",
					"round half even", "round_half_even", "sqrt", "exp", "ln", "log", "modulo", "mod", "min", "max",
					"sum", "product", "mean", "avg", "median", "mode", "stddev", "pmt", "pmt2", "count", "year",
					"month", "day", "weekday", "day of year", "day_of_year", "week of year", "week_of_year", "hour",
					"minute", "second" ->
				RuntimeTypeKind.NUMBER;
			case "date" -> RuntimeTypeKind.DATE;
			case "time" -> RuntimeTypeKind.TIME;
			case "date and time" -> RuntimeTypeKind.DATE_TIME;
			case "duration", "years and months duration", "years_and_months_duration", "day and time duration",
					"day_and_time_duration" ->
				RuntimeTypeKind.DURATION;
			case "is", "contains", "starts with", "startswith", "ends with", "endswith", "matches", "odd", "even",
					"list contains", "all", "any" ->
				RuntimeTypeKind.BOOLEAN;
			case "split", "reverse", "flatten", "distinct values", "union", "append", "concatenate", "sublist" ->
				RuntimeTypeKind.LIST;
			case "range" -> RuntimeTypeKind.RANGE;
			default -> RuntimeTypeKind.ANY;
		};
	}

	private static String emitBinary(RuntimeBinaryExpression binary, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		String left = emitWithBkms(binary.left(), bkmBySlot, slotNameResolver);
		String right = emitWithBkms(binary.right(), bkmBySlot, slotNameResolver);

		RuntimeTypeKind lk = resolveTypeKind(binary.left());
		RuntimeTypeKind rk = resolveTypeKind(binary.right());

		RuntimeBinaryOperator op = binary.operator();

		// Comparison and Boolean operators
		if (op == RuntimeBinaryOperator.AND)
			return "(" + left + " AND " + right + ")";
		if (op == RuntimeBinaryOperator.OR)
			return "(" + left + " OR " + right + ")";
		if (lk == RuntimeTypeKind.RANGE || rk == RuntimeTypeKind.RANGE) {
			String eq = "((" + left + ").startIncluded <=> (" + right + ").startIncluded AND (" + left
					+ ").endIncluded <=> (" + right + ").endIncluded AND (" + left + ").start <=> (" + right
					+ ").start AND (" + left + ").end <=> (" + right + ").end)";
			if (op == RuntimeBinaryOperator.EQUAL) {
				return "(CASE WHEN " + left + " IS NULL OR " + right + " IS NULL THEN NULL ELSE " + eq + " END)";
			}
			if (op == RuntimeBinaryOperator.NOT_EQUAL) {
				return "(CASE WHEN " + left + " IS NULL OR " + right + " IS NULL THEN NULL ELSE NOT (" + eq + ") END)";
			}
		}
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
			// STRING + STRING → concat
			if (lk == RuntimeTypeKind.STRING && rk == RuntimeTypeKind.STRING) {
				return "concat(" + left + ", " + right + ")";
			}
			// NUMBER + NUMBER
			if (lk == RuntimeTypeKind.NUMBER && rk == RuntimeTypeKind.NUMBER) {
				return "(" + left + " + " + right + ")";
			}
			// DURATION + DURATION
			if (isDuration(lk) && isDuration(rk)) {
				return durationAddSql(left, right);
			}
			// (DATE | DATE_TIME | TIME) + DURATION or DURATION + (DATE | DATE_TIME | TIME)
			if ((lk == RuntimeTypeKind.DATE || lk == RuntimeTypeKind.DATE_TIME || lk == RuntimeTypeKind.TIME
					|| isLikelyDateTimeOrDateSql(left) || isLikelyTimeSql(left))
					&& (isDuration(rk) || isLikelyDurationSql(right))) {
				if (lk == RuntimeTypeKind.TIME || isLikelyTimeSql(left)) {
					// TIME + YM-duration → NULL per FEEL spec
					if (rk == RuntimeTypeKind.YEARS_MONTHS_DURATION || (isLikelyDurationSql(right)
							&& (right.contains("'P") && right.contains("M") && !right.contains("T"))))
						return "NULL";
				}
				return temporalAddSubSql(left, right, false);
			}
			if ((isDuration(lk) || isLikelyDurationSql(left))
					&& (rk == RuntimeTypeKind.DATE || rk == RuntimeTypeKind.DATE_TIME || rk == RuntimeTypeKind.TIME
							|| isLikelyDateTimeOrDateSql(right) || isLikelyTimeSql(right))) {
				if (rk == RuntimeTypeKind.TIME || isLikelyTimeSql(right)) {
					if (lk == RuntimeTypeKind.YEARS_MONTHS_DURATION || (isLikelyDurationSql(left)
							&& (left.contains("'P") && left.contains("M") && !left.contains("T"))))
						return "NULL";
				}
				return temporalAddSubSql(right, left, false);
			}
			// Typed guard: incompatible types → NULL
			if (lk == RuntimeTypeKind.LIST || rk == RuntimeTypeKind.LIST || isDuration(lk) || isDuration(rk)
					|| lk == RuntimeTypeKind.DATE || rk == RuntimeTypeKind.DATE || lk == RuntimeTypeKind.DATE_TIME
					|| rk == RuntimeTypeKind.DATE_TIME || lk == RuntimeTypeKind.TIME || rk == RuntimeTypeKind.TIME
					|| lk == RuntimeTypeKind.STRING || rk == RuntimeTypeKind.STRING || isLikelyDateTimeOrDateSql(left)
					|| isLikelyDateTimeOrDateSql(right) || isLikelyTimeSql(left) || isLikelyTimeSql(right)
					|| isLikelyDurationSql(left) || isLikelyDurationSql(right)) {
				return "NULL";
			}
			return "(" + left + " + " + right + ")";
		}

		if (op == RuntimeBinaryOperator.SUBTRACT) {
			// NUMBER - NUMBER
			if (lk == RuntimeTypeKind.NUMBER && rk == RuntimeTypeKind.NUMBER) {
				return "(" + left + " - " + right + ")";
			}
			// DATE - DATE → day-time duration
			if (lk == RuntimeTypeKind.DATE && rk == RuntimeTypeKind.DATE) {
				return formatDayTimeDurationSql("(datediff(" + left + ", " + right + ") * 86400)");
			}
			// DATE_TIME - DATE_TIME → day-time duration; FEEL: mixed zone → NULL
			if (lk == RuntimeTypeKind.DATE_TIME && rk == RuntimeTypeKind.DATE_TIME) {
				String lHasZone = "(instr(cast(" + left + " as string), '@') > 0 OR cast(" + left
						+ " as string) rlike '([+-][0-9]{2}:[0-9]{2}|[zZ])$')";
				String rHasZone = "(instr(cast(" + right + " as string), '@') > 0 OR cast(" + right
						+ " as string) rlike '([+-][0-9]{2}:[0-9]{2}|[zZ])$')";
				return "(CASE WHEN " + lHasZone + " <> " + rHasZone + " THEN NULL " + "ELSE "
						+ formatDayTimeDurationSql("(" + utcSecondsSql(left) + " - " + utcSecondsSql(right) + ")")
						+ " END)";
			}
			// TIME - TIME → day-time duration
			if (lk == RuntimeTypeKind.TIME && rk == RuntimeTypeKind.TIME) {
				String lHms = "regexp_extract(cast(" + left
						+ " as string), '^([0-9]{2}:[0-9]{2}(?::[0-9]{2}(?:\\\\.[0-9]+)?)?)', 1)";
				String rHms = "regexp_extract(cast(" + right
						+ " as string), '^([0-9]{2}:[0-9]{2}(?::[0-9]{2}(?:\\\\.[0-9]+)?)?)', 1)";
				String lSec = "unix_timestamp(try_to_timestamp(concat('1970-01-01 ', " + lHms + ")))";
				String rSec = "unix_timestamp(try_to_timestamp(concat('1970-01-01 ', " + rHms + ")))";
				return formatDayTimeDurationSql("(" + lSec + " - " + rSec + ")");
			}
			// DURATION - DURATION
			if (isDuration(lk) && isDuration(rk)) {
				return durationSubSql(left, right);
			}
			// DATE_TIME - DATE → day-time duration; lhs must be zoned
			if ((lk == RuntimeTypeKind.DATE_TIME || isLikelyDateTimeOrDateSql(left)) && rk == RuntimeTypeKind.DATE) {
				return "(CASE WHEN instr(cast(" + left + " as string), '@') > 0 OR cast(" + left
						+ " as string) rlike '([+-][0-9]{2}:[0-9]{2}|[zZ])$' THEN "
						+ formatDayTimeDurationSql("(" + utcSecondsSql(left) + " - " + utcSecondsSql(right) + ")")
						+ " ELSE NULL END)";
			}
			// DATE - DATE_TIME → day-time duration; rhs must be zoned
			if (lk == RuntimeTypeKind.DATE && (rk == RuntimeTypeKind.DATE_TIME || isLikelyDateTimeOrDateSql(right))) {
				return "(CASE WHEN instr(cast(" + right + " as string), '@') > 0 OR cast(" + right
						+ " as string) rlike '([+-][0-9]{2}:[0-9]{2}|[zZ])$' THEN "
						+ formatDayTimeDurationSql("(" + utcSecondsSql(left) + " - " + utcSecondsSql(right) + ")")
						+ " ELSE NULL END)";
			}
			// (DATE | DATE_TIME | TIME) - DURATION
			if ((lk == RuntimeTypeKind.DATE || lk == RuntimeTypeKind.DATE_TIME || lk == RuntimeTypeKind.TIME
					|| isLikelyDateTimeOrDateSql(left) || isLikelyTimeSql(left))
					&& (isDuration(rk) || isLikelyDurationSql(right))) {
				if (lk == RuntimeTypeKind.TIME || isLikelyTimeSql(left)) {
					// TIME - YM-duration → NULL
					if (rk == RuntimeTypeKind.YEARS_MONTHS_DURATION || (isLikelyDurationSql(right)
							&& right.contains("'P") && right.contains("M") && !right.contains("T")))
						return "NULL";
				}
				return temporalAddSubSql(left, right, true);
			}
			// Typed guard
			if (lk == RuntimeTypeKind.LIST || rk == RuntimeTypeKind.LIST || lk == RuntimeTypeKind.STRING
					|| rk == RuntimeTypeKind.STRING || isDuration(lk) || isDuration(rk) || lk == RuntimeTypeKind.DATE
					|| rk == RuntimeTypeKind.DATE || lk == RuntimeTypeKind.DATE_TIME || rk == RuntimeTypeKind.DATE_TIME
					|| lk == RuntimeTypeKind.TIME || rk == RuntimeTypeKind.TIME || isLikelyDateTimeOrDateSql(left)
					|| isLikelyDateTimeOrDateSql(right) || isLikelyTimeSql(left) || isLikelyTimeSql(right)
					|| isLikelyDurationSql(left) || isLikelyDurationSql(right)) {
				return "NULL";
			}
			return "(" + left + " - " + right + ")";
		}

		if (op == RuntimeBinaryOperator.MULTIPLY) {
			if (lk == RuntimeTypeKind.NUMBER && rk == RuntimeTypeKind.NUMBER) {
				return "(" + left + " * " + right + ")";
			}
			if (isDuration(lk) && (rk == RuntimeTypeKind.NUMBER || rk == RuntimeTypeKind.ANY)) {
				String ds = "(CASE WHEN " + left + " LIKE '-%' THEN -1.0 ELSE 1.0 END)";
				String ymTot = "(" + ds + " * (coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)Y', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)M', 1) as bigint), 0L)) * cast(" + right + " as double))";
				String dtTot = "(" + ds + " * (coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)D', 1) as bigint), 0L) * 86400.0 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)H', 1) as bigint), 0L) * 3600.0 + coalesce(try_cast(regexp_extract(" + left
						+ ", 'T.*?([0-9]+)M', 1) as bigint), 0L) * 60.0 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)) * cast(" + right + " as double))";
				return "(CASE WHEN " + left + " LIKE '%Y%' OR (" + left + " LIKE '%M%' AND " + left
						+ " NOT LIKE '%T%') THEN " + formatYearMonthDurationSql(ymTot) + " ELSE "
						+ formatDayTimeDurationSql(dtTot) + " END)";
			}
			if ((lk == RuntimeTypeKind.NUMBER || lk == RuntimeTypeKind.ANY) && isDuration(rk)) {
				String ds = "(CASE WHEN " + right + " LIKE '-%' THEN -1.0 ELSE 1.0 END)";
				String ymTot = "(" + ds + " * (coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)Y', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)M', 1) as bigint), 0L)) * cast(" + left + " as double))";
				String dtTot = "(" + ds + " * (coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)D', 1) as bigint), 0L) * 86400.0 + coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)H', 1) as bigint), 0L) * 3600.0 + coalesce(try_cast(regexp_extract(" + right
						+ ", 'T.*?([0-9]+)M', 1) as bigint), 0L) * 60.0 + coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)) * cast(" + left + " as double))";
				return "(CASE WHEN " + right + " LIKE '%Y%' OR (" + right + " LIKE '%M%' AND " + right
						+ " NOT LIKE '%T%') THEN " + formatYearMonthDurationSql(ymTot) + " ELSE "
						+ formatDayTimeDurationSql(dtTot) + " END)";
			}
			if (lk == RuntimeTypeKind.LIST || rk == RuntimeTypeKind.LIST || lk == RuntimeTypeKind.STRING
					|| rk == RuntimeTypeKind.STRING || isDuration(lk) || isDuration(rk) || lk == RuntimeTypeKind.DATE
					|| rk == RuntimeTypeKind.DATE || lk == RuntimeTypeKind.DATE_TIME || rk == RuntimeTypeKind.DATE_TIME
					|| lk == RuntimeTypeKind.TIME || rk == RuntimeTypeKind.TIME) {
				return "NULL";
			}
			return "(" + left + " * " + right + ")";
		}

		if (op == RuntimeBinaryOperator.DIVIDE) {
			if (lk == RuntimeTypeKind.NUMBER && rk == RuntimeTypeKind.NUMBER) {
				return "(CASE WHEN " + right + " = 0 THEN NULL ELSE (" + left + " / " + right + ") END)";
			}
			if (isDuration(lk) && (rk == RuntimeTypeKind.NUMBER || rk == RuntimeTypeKind.ANY)) {
				String ds = "(CASE WHEN " + left + " LIKE '-%' THEN -1.0 ELSE 1.0 END)";
				String ymTot = "(cast((" + ds + " * (coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)Y', 1) as bigint), 0L) * 12 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)M', 1) as bigint), 0L))) as double) / NULLIF(cast(" + right
						+ " as double), 0.0))";
				String dtTot = "((" + ds + " * (coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)D', 1) as bigint), 0L) * 86400.0 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)H', 1) as bigint), 0L) * 3600.0 + coalesce(try_cast(regexp_extract(" + left
						+ ", 'T.*?([0-9]+)M', 1) as bigint), 0L) * 60.0 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0))) / NULLIF(cast(" + right
						+ " as double), 0.0))";
				return "(CASE WHEN cast(" + right + " as double) = 0 OR cast(" + right
						+ " as double) IS NULL THEN NULL WHEN " + left + " LIKE '%Y%' OR (" + left + " LIKE '%M%' AND "
						+ left + " NOT LIKE '%T%') THEN " + formatYearMonthDurationSql(ymTot) + " ELSE "
						+ formatDayTimeDurationSql(dtTot) + " END)";
			}
			if (isDuration(lk) && isDuration(rk)) {
				String lds = "(CASE WHEN " + left + " LIKE '-%' THEN -1.0 ELSE 1.0 END)";
				String rds = "(CASE WHEN " + right + " LIKE '-%' THEN -1.0 ELSE 1.0 END)";
				String lym = "(" + lds + " * (coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)Y', 1) as double), 0.0) * 12 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)M', 1) as double), 0.0)))";
				String rym = "(" + rds + " * (coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)Y', 1) as double), 0.0) * 12 + coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)M', 1) as double), 0.0)))";
				String ldt = "(" + lds + " * (coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)D', 1) as double), 0.0) * 86400 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+)H', 1) as double), 0.0) * 3600 + coalesce(try_cast(regexp_extract(" + left
						+ ", 'T.*?([0-9]+)M', 1) as double), 0.0) * 60 + coalesce(try_cast(regexp_extract(" + left
						+ ", '([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)))";
				String rdt = "(" + rds + " * (coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)D', 1) as double), 0.0) * 86400 + coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+)H', 1) as double), 0.0) * 3600 + coalesce(try_cast(regexp_extract(" + right
						+ ", 'T.*?([0-9]+)M', 1) as double), 0.0) * 60 + coalesce(try_cast(regexp_extract(" + right
						+ ", '([0-9]+(?:\\\\.[0-9]+)?)S', 1) as double), 0.0)))";
				return "(CASE WHEN " + left + " LIKE '%Y%' OR (" + left + " LIKE '%M%' AND " + left
						+ " NOT LIKE '%T%') THEN (CASE WHEN " + right + " LIKE '%Y%' OR (" + right + " LIKE '%M%' AND "
						+ right + " NOT LIKE '%T%') THEN " + lym + " / NULLIF(" + rym
						+ ", 0.0) ELSE NULL END) ELSE (CASE WHEN " + right + " LIKE '%Y%' OR (" + right
						+ " LIKE '%M%' AND " + right + " NOT LIKE '%T%') THEN NULL ELSE " + ldt + " / NULLIF(" + rdt
						+ ", 0.0) END) END)";
			}
			if (lk == RuntimeTypeKind.LIST || rk == RuntimeTypeKind.LIST || lk == RuntimeTypeKind.STRING
					|| rk == RuntimeTypeKind.STRING || isDuration(lk) || isDuration(rk) || lk == RuntimeTypeKind.DATE
					|| rk == RuntimeTypeKind.DATE || lk == RuntimeTypeKind.DATE_TIME || rk == RuntimeTypeKind.DATE_TIME
					|| lk == RuntimeTypeKind.TIME || rk == RuntimeTypeKind.TIME) {
				return "NULL";
			}
			return "(CASE WHEN " + right + " = 0 THEN NULL ELSE (" + left + " / " + right + ") END)";
		}

		return "NULL";
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

		// Build entries with an incrementally-scoped resolver so that later entries can
		// reference earlier ones via their localSlot (e.g. sort(metricsTable, ...)).
		Map<Integer, String> localBindings = new HashMap<>();
		List<String> emittedExprs = new ArrayList<>();

		for (RuntimeContextEntry entry : ctx.entries()) {
			final Map<Integer, String> bindingsSnapshot = new HashMap<>(localBindings);
			IntFunction<String> scopedResolver = slot -> {
				if (bindingsSnapshot.containsKey(slot))
					return bindingsSnapshot.get(slot);
				return slotNameResolver.apply(slot);
			};
			String emittedExpr = emitWithBkms(entry.expression(), bkmBySlot, scopedResolver);
			emittedExprs.add(emittedExpr);
			if (entry.localSlot() >= 0) {
				localBindings.put(entry.localSlot(), "(" + emittedExpr + ")");
			}
		}

		StringBuilder sb = new StringBuilder("named_struct(");
		List<RuntimeContextEntry> entries = ctx.entries();
		for (int i = 0; i < entries.size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append("'").append(escapeSqlString(entries.get(i).name())).append("', ").append(emittedExprs.get(i));
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
		RuntimeTypeKind srcKind = resolveTypeKind(filter.source());
		boolean isNumericIndex = filter.filter().type() != null
				&& filter.filter().type().kind() == RuntimeTypeKind.NUMBER;
		// Non-list source with non-index filter → FEEL null
		if (srcKind != RuntimeTypeKind.LIST && srcKind != RuntimeTypeKind.ANY && !isNumericIndex) {
			return "NULL";
		}
		String source = emitWithBkms(filter.source(), bkmBySlot, slotNameResolver);
		String itemVar = "_item_" + Math.abs(filter.localSlot());
		IntFunction<String> scopedResolver = slot -> (slot == filter.localSlot())
				? itemVar
				: slotNameResolver.apply(slot);
		String filterExpr = emitWithBkms(filter.filter(), bkmBySlot, scopedResolver);
		if (isNumericIndex) {
			return "element_at(" + source + ", cast(" + filterExpr + " as int))";
		}
		return "(CASE WHEN " + source + " IS NULL THEN NULL ELSE filter(" + source + ", " + itemVar + " -> "
				+ filterExpr + ") END)";
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
		RuntimeTypeKind srcKind = resolveTypeKind(binding.source());
		if (srcKind != RuntimeTypeKind.LIST && srcKind != RuntimeTypeKind.ANY) {
			// non-list source → FEEL null semantics
			return "NULL";
		}
		String source = emitWithBkms(binding.source(), bkmBySlot, slotNameResolver);
		String itemVar = "_q_" + Math.abs(binding.localSlot());
		IntFunction<String> scopedResolver = slot -> (slot == binding.localSlot())
				? itemVar
				: slotNameResolver.apply(slot);
		String inner = emitNestedQuantified(isEvery, bindings, index + 1, satisfies, bkmBySlot, scopedResolver);
		String func = isEvery ? "forall" : "exists";
		return "(CASE WHEN " + source + " IS NULL THEN NULL ELSE " + func + "(" + source + ", " + itemVar + " -> "
				+ inner + ") END)";
	}

	private static String emitFor(RuntimeForExpression forExpr, Map<Integer, RuntimeBkm> bkmBySlot,
			IntFunction<String> slotNameResolver) {
		if (forExpr.iterations().isEmpty()) {
			return "array()";
		}
		if (forExpr.iterations().size() == 1) {
			RuntimeIteration iter = forExpr.iterations().get(0);
			boolean isRange = iter.end().isPresent();
			// Non-list, non-range source → FEEL null (Catalyst rejects transform on
			// non-array at analysis time)
			if (!isRange) {
				RuntimeTypeKind srcKind = resolveTypeKind(iter.source());
				if (srcKind != RuntimeTypeKind.LIST && srcKind != RuntimeTypeKind.ANY) {
					return "NULL";
				}
			}
			String source = isRange
					? "sequence(" + emitWithBkms(iter.source(), bkmBySlot, slotNameResolver) + ", "
							+ emitWithBkms(iter.end().get(), bkmBySlot, slotNameResolver) + ")"
					: emitWithBkms(iter.source(), bkmBySlot, slotNameResolver);
			String itemVar = "_for_" + Math.abs(iter.localSlot());
			IntFunction<String> scopedResolver = slot -> (slot == iter.localSlot())
					? itemVar
					: slotNameResolver.apply(slot);
			String result = emitWithBkms(forExpr.result(), bkmBySlot, scopedResolver);
			return "(CASE WHEN " + source + " IS NULL THEN NULL ELSE transform(" + source + ", " + itemVar + " -> "
					+ result + ") END)";
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
		return (index < iterations.size() - 1) ? "flatten(" + transformed + ")" : transformed;
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
			List<RuntimeExpression> args;
			if (!inv.positionalArguments().isEmpty()) {
				args = inv.positionalArguments();
			} else {
				args = reorderNamedArguments(fnName, inv.namedArguments());
			}
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
							+ ", '..') > 0 THEN (" + "CASE WHEN (" + str + " LIKE '[%' AND trim(substring(" + str
							+ ", 2, instr(" + str + ", '..') - 2)) = '') " + "OR (" + str
							+ " LIKE '%]' AND trim(substring(" + str + ", instr(" + str + ", '..') + 2, length(" + str
							+ ") - instr(" + str + ", '..') - 1)) = '') " + "OR (trim(substring(" + str + ", 2, instr("
							+ str + ", '..') - 2)) = '' AND trim(substring(" + str + ", instr(" + str
							+ ", '..') + 2, length(" + str + ") - instr(" + str + ", '..') - 1)) = '') " + "THEN NULL "
							+ "ELSE named_struct('start', (CASE WHEN try_cast(trim(substring(" + str + ", 2, instr("
							+ str + ", '..') - 2)) as double) IS NOT NULL THEN try_cast(trim(substring(" + str
							+ ", 2, instr(" + str + ", '..') - 2)) as double) " + "WHEN trim(substring(" + str
							+ ", 2, instr(" + str + ", '..') - 2)) LIKE '\"%\"' THEN substring(trim(substring(" + str
							+ ", 2, instr(" + str + ", '..') - 2)), 2, length(trim(substring(" + str + ", 2, instr("
							+ str + ", '..') - 2))) - 2) " + "WHEN trim(substring(" + str + ", 2, instr(" + str
							+ ", '..') - 2)) = '' THEN NULL " + "ELSE trim(substring(" + str + ", 2, instr(" + str
							+ ", '..') - 2)) END), " + "'end', (CASE WHEN try_cast(trim(substring(" + str + ", instr("
							+ str + ", '..') + 2, length(" + str + ") - instr(" + str
							+ ", '..') - 1)) as double) IS NOT NULL THEN try_cast(trim(substring(" + str + ", instr("
							+ str + ", '..') + 2, length(" + str + ") - instr(" + str + ", '..') - 1)) as double) "
							+ "WHEN trim(substring(" + str + ", instr(" + str + ", '..') + 2, length(" + str
							+ ") - instr(" + str + ", '..') - 1)) LIKE '\"%\"' THEN substring(trim(substring(" + str
							+ ", instr(" + str + ", '..') + 2, length(" + str + ") - instr(" + str
							+ ", '..') - 1)), 2, length(trim(substring(" + str + ", instr(" + str
							+ ", '..') + 2, length(" + str + ") - instr(" + str + ", '..') - 1))) - 2) "
							+ "WHEN trim(substring(" + str + ", instr(" + str + ", '..') + 2, length(" + str
							+ ") - instr(" + str + ", '..') - 1)) = '' THEN NULL " + "ELSE trim(substring(" + str
							+ ", instr(" + str + ", '..') + 2, length(" + str + ") - instr(" + str
							+ ", '..') - 1)) END), " + "'start included', (" + str + " LIKE '[%'), 'end included', ("
							+ str + " LIKE '%]'), " + "'startIncluded', (" + str + " LIKE '[%'), 'endIncluded', (" + str
							+ " LIKE '%]')) END) " + "ELSE NULL END)";
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
				String d = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String t = emitArg(args, 1, bkmBySlot, slotNameResolver);
				yield "make_timestamp(year(" + d + "), month(" + d + "), day(" + d + "), hour(" + t + "), minute(" + t
						+ "), second(" + t + "))";
			}
			case "time" -> {
				if (args.size() == 1) {
					String arg = emitArg(args, 0, bkmBySlot, slotNameResolver);
					RuntimeType argType = args.get(0).type();
					if (argType != null && argType.kind() == RuntimeTypeKind.DATE_TIME) {
						yield "make_timestamp(1970, 1, 1, hour(" + arg + "), minute(" + arg + "), second(" + arg + "))";
					}
					yield "try_to_timestamp(concat('1970-01-01 ', " + arg + "))";
				} else if (args.size() >= 3) {
					String h = emitArg(args, 0, bkmBySlot, slotNameResolver);
					String m = emitArg(args, 1, bkmBySlot, slotNameResolver);
					String s = emitArg(args, 2, bkmBySlot, slotNameResolver);
					yield "make_timestamp(1970, 1, 1, cast(" + h + " as int), cast(" + m + " as int), cast(" + s
							+ " as double))";
				}
				yield "try_to_timestamp(concat('1970-01-01 ', " + emitArg(args, 0, bkmBySlot, slotNameResolver) + "))";
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
			case "list contains" -> {
				String list = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String match = emitArg(args, 1, bkmBySlot, slotNameResolver);
				RuntimeType matchType = args.size() > 1 ? args.get(1).type() : null;
				if (matchType != null && matchType.kind() == RuntimeTypeKind.LIST) {
					yield "(CASE WHEN (" + list + " IS NULL OR " + match + " IS NULL) THEN NULL ELSE FALSE END)";
				}
				yield "array_contains(" + list + ", " + match + ")";
			}
			case "count" -> "size(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "reverse" -> "reverse(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "flatten" -> {
				String arr = emitArg(args, 0, bkmBySlot, slotNameResolver);
				RuntimeType arrType = !args.isEmpty() ? args.get(0).type() : null;
				if (arrType != null && arrType.kind() == RuntimeTypeKind.LIST && arrType.elementType() != null
						&& arrType.elementType().kind() == RuntimeTypeKind.LIST) {
					yield "flatten(" + arr + ")";
				}
				yield arr;
			}
			case "distinct values" -> "array_distinct(" + emitArg(args, 0, bkmBySlot, slotNameResolver) + ")";
			case "union" -> "array_union(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";
			case "append" -> {
				String list = emitArg(args, 0, bkmBySlot, slotNameResolver);
				if (args.size() == 2) {
					RuntimeType itemType = args.get(1).type();
					String item1 = emitArg(args, 1, bkmBySlot, slotNameResolver);
					if (itemType != null && itemType.kind() == RuntimeTypeKind.LIST) {
						yield "concat(" + list + ", " + item1 + ")";
					} else {
						yield "concat(" + list + ", array(" + item1 + "))";
					}
				}
				StringBuilder items = new StringBuilder("array(");
				for (int i = 1; i < args.size(); i++) {
					if (i > 1)
						items.append(", ");
					items.append(emitWithBkms(args.get(i), bkmBySlot, slotNameResolver));
				}
				items.append(")");
				yield "concat(" + list + ", " + items + ")";
			}
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
					RuntimeTypeKind k0 = resolveTypeKind(args.get(0));
					if (k0 != RuntimeTypeKind.LIST && k0 != RuntimeTypeKind.ANY)
						yield "NULL";
					String arr = emitArg(args, 0, bkmBySlot, slotNameResolver);
					yield "(CASE WHEN " + arr + " IS NULL THEN NULL WHEN size(" + arr + ") = 0 THEN TRUE WHEN exists("
							+ arr + ", x -> x = false) THEN FALSE WHEN exists(" + arr
							+ ", x -> x IS NULL) THEN NULL ELSE TRUE END)";
				}
				String arr = "array(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";
				yield "(CASE WHEN exists(" + arr + ", x -> x = false) THEN FALSE WHEN exists(" + arr
						+ ", x -> x IS NULL) THEN NULL ELSE TRUE END)";
			}
			case "any" -> {
				if (args.size() == 1) {
					RuntimeTypeKind k0 = resolveTypeKind(args.get(0));
					if (k0 != RuntimeTypeKind.LIST && k0 != RuntimeTypeKind.ANY)
						yield "NULL";
					String arr = emitArg(args, 0, bkmBySlot, slotNameResolver);
					yield "(CASE WHEN " + arr + " IS NULL THEN NULL WHEN size(" + arr + ") = 0 THEN FALSE WHEN exists("
							+ arr + ", x -> x = true) THEN TRUE WHEN exists(" + arr
							+ ", x -> x IS NULL) THEN NULL ELSE FALSE END)";
				}
				String arr = "array(" + emitArgsJoined(args, bkmBySlot, slotNameResolver) + ")";
				yield "(CASE WHEN exists(" + arr + ", x -> x = true) THEN TRUE WHEN exists(" + arr
						+ ", x -> x IS NULL) THEN NULL ELSE FALSE END)";
			}
			case "index of", "indexof" -> {
				RuntimeTypeKind k0 = resolveTypeKind(args.get(0));
				if (k0 != RuntimeTypeKind.LIST && k0 != RuntimeTypeKind.ANY)
					yield "array()";
				String list = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String match = emitArg(args, 1, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN " + list + " IS NULL THEN array() ELSE filter(transform(" + list
						+ ", (x, i) -> (CASE WHEN x <=> " + match
						+ " THEN i + 1 ELSE NULL END)), x -> x IS NOT NULL) END)";
			}
			case "remove" -> {
				String list0 = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String pos0 = emitArg(args, 1, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN " + list0 + " IS NULL OR " + pos0 + " IS NULL THEN NULL ELSE concat(slice(" + list0
						+ ", 1, cast(" + pos0 + " as int) - 1), slice(" + list0 + ", cast(" + pos0
						+ " as int) + 1, size(" + list0 + "))) END)";
			}
			case "insert before" -> {
				String list0 = emitArg(args, 0, bkmBySlot, slotNameResolver);
				String pos0 = emitArg(args, 1, bkmBySlot, slotNameResolver);
				String newEl = emitArg(args, 2, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN " + list0 + " IS NULL OR " + pos0 + " IS NULL THEN NULL ELSE concat(slice(" + list0
						+ ", 1, cast(" + pos0 + " as int) - 1), array(" + newEl + "), slice(" + list0 + ", cast(" + pos0
						+ " as int), size(" + list0 + "))) END)";
			}
			case "list replace", "list_replace" -> {
				if (args.size() != 3) {
					yield "NULL";
				}
				// If first arg is definitely non-list and cannot be coerced to list, return
				// null
				RuntimeTypeKind listKind0 = resolveTypeKind(args.get(0));
				if (listKind0 == RuntimeTypeKind.CONTEXT || listKind0 == RuntimeTypeKind.RANGE
						|| listKind0 == RuntimeTypeKind.FUNCTION) {
					yield "NULL";
				}
				String rawList = emitArg(args, 0, bkmBySlot, slotNameResolver);
				// Guard: if rawList is NULL literal or evaluates to NULL, result must be NULL
				// (no singleton coercion of null)
				if ("NULL".equalsIgnoreCase(rawList.trim())) {
					yield "NULL";
				}
				// If first argument is scalar, FEEL spec allows singleton list coercion
				String list = (listKind0 != RuntimeTypeKind.LIST && listKind0 != RuntimeTypeKind.ANY)
						? "array(" + rawList + ")"
						: rawList;

				// Lambda/function form: list replace(list, function(item, newItem) predicate,
				// newItem)
				if (args.get(1) instanceof RuntimeFunctionDefinition lrFn && lrFn.body().isPresent()) {
					List<RuntimeFunctionParameter> lrParams = lrFn.parameters();
					if (lrParams.size() != 2) {
						yield "NULL";
					}
					String itemVar = "_lr_i_" + Math.abs(lrParams.get(0).localSlot());
					String newVar = "_lr_n_" + Math.abs(lrParams.get(1).localSlot());
					final int itemSlot = lrParams.get(0).localSlot();
					final int newSlot = lrParams.get(1).localSlot();
					String newItem = emitArg(args, 2, bkmBySlot, slotNameResolver);

					IntFunction<String> lrResolver = slot -> {
						if (slot == itemSlot)
							return itemVar;
						if (slot == newSlot)
							return newItem;
						return slotNameResolver.apply(slot);
					};
					String predSql = emitWithBkms(lrFn.body().get(), bkmBySlot, lrResolver);
					yield "(CASE WHEN " + list + " IS NULL THEN NULL ELSE transform(" + list + ", " + itemVar
							+ " -> CASE WHEN (" + predSql + ") THEN " + newItem + " ELSE " + itemVar + " END) END)";
				}

				// Position form: list replace(list, position, newItem)
				// Position must be numeric. If position has non-numeric type, return null.
				RuntimeTypeKind posKind = resolveTypeKind(args.get(1));
				if (posKind != RuntimeTypeKind.NUMBER && posKind != RuntimeTypeKind.ANY) {
					yield "NULL";
				}
				String rawPos = emitArg(args, 1, bkmBySlot, slotNameResolver);
				// In FEEL: decimal position is truncated towards zero: cast(try_cast(rawPos as
				// double) as bigint)
				String pos = "(CASE WHEN try_cast(" + rawPos + " as double) IS NULL THEN NULL ELSE cast(try_cast("
						+ rawPos + " as double) as bigint) END)";
				String item = emitArg(args, 2, bkmBySlot, slotNameResolver);
				yield "(CASE WHEN " + list + " IS NULL OR " + pos + " IS NULL THEN NULL" + " WHEN " + pos
						+ " = 0 THEN NULL" + " WHEN " + pos + " > size(" + list + ") THEN NULL" + " WHEN " + pos
						+ " < -size(" + list + ") THEN NULL" + " ELSE transform(" + list + ", (x, i) -> CASE"
						+ " WHEN (" + pos + " > 0 AND i + 1 = " + pos + ")" + "   OR (" + pos + " < 0 AND i + 1 = size("
						+ list + ") + " + pos + " + 1)" + " THEN " + item + " ELSE x END) END)";
			}
			case "sort" -> {
				String list = emitArg(args, 0, bkmBySlot, slotNameResolver);
				if (args.size() >= 2 && args.get(1) instanceof RuntimeFunctionDefinition comparatorFn
						&& !comparatorFn.parameters().isEmpty() && comparatorFn.body().isPresent()) {
					// sort(list, function(x,y) predicate) → array_sort with null-safe comparator
					List<RuntimeFunctionParameter> params = comparatorFn.parameters();
					String leftVar = "_sort_l_" + Math.abs(params.get(0).localSlot());
					String rightVar = "_sort_r_" + (params.size() > 1 ? Math.abs(params.get(1).localSlot()) : 1);
					final int leftSlot = params.get(0).localSlot();
					final int rightSlot = params.size() > 1 ? params.get(1).localSlot() : -999;
					IntFunction<String> cmpResolver = slot -> {
						if (slot == leftSlot)
							return leftVar;
						if (slot == rightSlot)
							return rightVar;
						return slotNameResolver.apply(slot);
					};
					// The body is a boolean predicate: "x < y" means "x comes before y"
					// (ascending).
					// We must emit a comparator returning -1/0/1 for array_sort.
					String predSql = emitWithBkms(comparatorFn.body().get(), bkmBySlot, cmpResolver);
					// Build null-safe tri-value comparator
					String comparator = "(CASE WHEN (" + leftVar + " IS NULL AND " + rightVar + " IS NULL) THEN 0"
							+ " WHEN " + leftVar + " IS NULL THEN 1" + " WHEN " + rightVar + " IS NULL THEN -1"
							+ " WHEN (" + predSql + ") THEN -1" + " WHEN (" + swapComparator(predSql, leftVar, rightVar)
							+ ") THEN 1" + " ELSE 0 END)";
					yield "(CASE WHEN " + list + " IS NULL THEN NULL ELSE array_sort(" + list + ", (" + leftVar + ", "
							+ rightVar + ") -> " + comparator + ") END)";
				}
				// 1-arg sort: natural order
				yield "(CASE WHEN " + list + " IS NULL THEN NULL ELSE array_sort(" + list + ", (left, right) -> "
						+ "(CASE WHEN (left IS NULL AND right IS NULL) THEN 0 WHEN left IS NULL THEN 1 "
						+ "WHEN right IS NULL THEN -1 WHEN (left < right) THEN -1 "
						+ "WHEN (left > right) THEN 1 ELSE 0 END)) END)";
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

	/**
	 * Produces a copy of {@code predSql} with {@code leftVar} and {@code rightVar}
	 * swapped. Used to build the "1" branch of the tri-value sort comparator from
	 * the same boolean predicate body (e.g. {@code x < y} becomes {@code y < x}).
	 */
	private static String swapComparator(String predSql, String leftVar, String rightVar) {
		String placeholder = "\u0000SWAP\u0000";
		return predSql.replace(leftVar, placeholder).replace(rightVar, leftVar).replace(placeholder, rightVar);
	}

	private static List<RuntimeExpression> reorderNamedArguments(String fnName, List<RuntimeNamedArgument> namedArgs) {
		Map<String, RuntimeExpression> map = new HashMap<>();
		for (RuntimeNamedArgument na : namedArgs) {
			map.put(na.name(), na.expression());
		}
		String norm = fnName.toLowerCase();
		if ("list replace".equals(norm) || "list_replace".equals(norm)) {
			// Expected signatures: (list, position, newItem) OR (list, match, newItem)
			if (namedArgs.size() != 3) {
				return namedArgs.stream().map(RuntimeNamedArgument::expression).toList();
			}
			RuntimeExpression list = map.get("list");
			RuntimeExpression newItem = map.get("newItem");
			RuntimeExpression second = map.containsKey("position") ? map.get("position") : map.get("match");
			if (list != null && newItem != null && second != null) {
				return List.of(list, second, newItem);
			}
		} else if ("sublist".equals(norm)) {
			// (list, start, length)
			RuntimeExpression list = map.get("list");
			RuntimeExpression start = map.get("start");
			RuntimeExpression length = map.get("length");
			if (list != null && start != null) {
				return length != null ? List.of(list, start, length) : List.of(list, start);
			}
		} else if ("substring".equals(norm)) {
			// (string, start, length)
			RuntimeExpression string = map.get("string");
			RuntimeExpression start = map.get("start");
			RuntimeExpression length = map.get("length");
			if (string != null && start != null) {
				return length != null ? List.of(string, start, length) : List.of(string, start);
			}
		} else if ("insert before".equals(norm)) {
			// (list, position, newItem)
			RuntimeExpression list = map.get("list");
			RuntimeExpression position = map.get("position");
			RuntimeExpression newItem = map.get("newItem");
			if (list != null && position != null && newItem != null) {
				return List.of(list, position, newItem);
			}
		} else if ("remove".equals(norm)) {
			// (list, position)
			RuntimeExpression list = map.get("list");
			RuntimeExpression position = map.get("position");
			if (list != null && position != null) {
				return List.of(list, position);
			}
		} else if ("range".equals(norm)) {
			// (from)
			if (map.containsKey("from") && map.size() == 1) {
				return List.of(map.get("from"));
			}
			return List.of(); // invalid named arguments -> yield empty -> NULL
		}
		return namedArgs.stream().map(RuntimeNamedArgument::expression).toList();
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
				} else if (exprTest.expression() instanceof RuntimeRangeExpression range) {
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
				} else if (resolveTypeKind(exprTest.expression()) == RuntimeTypeKind.RANGE) {
					String r = emitWithBkms(exprTest.expression(), bkmBySlot, slotNameResolver);
					sb.append("(CASE WHEN ").append(r).append(" IS NULL OR ").append(inputCol)
							.append(" IS NULL THEN NULL ").append("WHEN (").append(r)
							.append(".start IS NULL OR (CASE WHEN ").append(r).append(".startIncluded THEN ")
							.append(inputCol).append(" >= ").append(r).append(".start ELSE ").append(inputCol)
							.append(" > ").append(r).append(".start END)) ").append("AND (").append(r)
							.append(".end IS NULL OR (CASE WHEN ").append(r).append(".endIncluded THEN ")
							.append(inputCol).append(" <= ").append(r).append(".end ELSE ").append(inputCol)
							.append(" < ").append(r).append(".end END)) ").append("THEN TRUE ELSE FALSE END)");
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
