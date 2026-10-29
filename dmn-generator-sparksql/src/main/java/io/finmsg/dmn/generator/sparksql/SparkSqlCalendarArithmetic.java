package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.time.*;
import java.util.*;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/**
 * Calendar contexts over bounded literal shifts; SQL performs the arithmetic.
 */
final class SparkSqlCalendarArithmetic {
	private record Item(String name, String function, int slot, boolean shifted, long days, long months) {
	}
	private static List<Item> plan(RuntimeModel model, RuntimeDecision decision) {
		if (decision.decisionTable().isPresent() || decision.type().kind() != RuntimeTypeKind.ANY
				|| !(decision.expression().orElse(null) instanceof RuntimeContextExpression context)
				|| context.entries().isEmpty() || context.entries().size() > 16)
			return List.of();
		var items = new ArrayList<Item>();
		var names = new HashSet<String>();
		for (var entry : context.entries()) {
			if (entry.name().length() > 256 || !names.add(entry.name().toLowerCase(Locale.ROOT))
					|| entry.name().codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF))
				return List.of();
			String function;
			List<RuntimeExpression> args;
			if (entry.expression() instanceof RuntimeFunctionCall call) {
				function = call.function();
				args = call.arguments();
			} else if (entry.expression() instanceof RuntimeInvocationExpression call && call.function().isPresent()) {
				function = call.function().get();
				args = call.namedArguments().isEmpty()
						? call.positionalArguments()
						: SparkSqlExpressionEmitter.reorderNamedArguments(function, call.namedArguments());
			} else
				return List.of();
			function = function.toLowerCase(Locale.ROOT);
			if (!Set.of("day of week", "month of year").contains(function) || args.size() != 1
					|| !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.STRING).contains(entry.expression().type().kind()))
				return List.of();
			RuntimeExpression source = args.getFirst();
			boolean shifted = false;
			long days = 0, months = 0;
			if (source instanceof RuntimeBinaryExpression binary && binary.operator() == RuntimeBinaryOperator.ADD
					&& binary.right() instanceof RuntimeConstant constant
					&& constant.kind() == RuntimeConstantKind.DURATION) {
				shifted = true;
				source = binary.left();
				try {
					var amount = DmnRuntime.parseDuration(constant.value());
					if (amount instanceof Duration duration && duration.getNano() == 0
							&& duration.getSeconds() % 86400 == 0) {
						days = duration.getSeconds() / 86400;
						if (Math.abs(days) > 366)
							return List.of();
					} else if (amount instanceof Period period && period.getDays() == 0) {
						months = period.toTotalMonths();
						if (Math.abs(months) > 12)
							return List.of();
					} else
						return List.of();
				} catch (RuntimeException invalid) {
					return List.of();
				}
			}
			if (!(source instanceof RuntimeValueReference ref)
					|| !Set.of(RuntimeTypeKind.ANY, RuntimeTypeKind.DATE, RuntimeTypeKind.DATE_TIME)
							.contains(ref.type().kind())
					|| model.inputs().stream()
							.noneMatch(i -> i.valueSlot() == ref.sourceSlot() && i.type().equals(ref.type()))
					|| !items.isEmpty() && items.getFirst().slot() != ref.sourceSlot())
				return List.of();
			items.add(new Item(entry.name(), function, ref.sourceSlot(), shifted, days, months));
		}
		return items;
	}
	static Integer inputSlot(RuntimeModel model, RuntimeDecision decision) {
		var items = plan(model, decision);
		return items.isEmpty() ? null : items.getFirst().slot();
	}
	static StructType extend(StructType date) {
		var transition = new StructType().add("instant", DataTypes.LongType).add("before", DataTypes.IntegerType)
				.add("after", DataTypes.IntegerType);
		return date.add("epochDay", DataTypes.LongType).add("second", DataTypes.IntegerType)
				.add("zoned", DataTypes.BooleanType).add("offset", DataTypes.IntegerType)
				.add("transitions", DataTypes.createArrayType(transition, false));
	}
	static Row row(Object value, LocalDate date, String kind) {
		int second = value instanceof LocalDateTime t
				? t.toLocalTime().toSecondOfDay()
				: value instanceof OffsetDateTime t
						? t.toLocalTime().toSecondOfDay()
						: value instanceof ZonedDateTime t
								? t.toLocalTime().toSecondOfDay()
								: value instanceof DmnRuntime.NamedZoneDateTime t
										? t.value().toLocalTime().toSecondOfDay()
										: 0;
		var transitions = new ArrayList<Row>();
		int offset = 0;
		if (value instanceof ZonedDateTime zoned) {
			offset = zoned.getOffset().getTotalSeconds();
			var instant = zoned.toInstant();
			var rules = zoned.getZone().getRules();
			var end = Instant
					.ofEpochSecond(Math.min(Instant.MAX.getEpochSecond(), instant.getEpochSecond() + 800L * 86400));
			var next = nextTransition(rules, Instant
					.ofEpochSecond(Math.max(Instant.MIN.getEpochSecond(), instant.getEpochSecond() - 800L * 86400)));
			while (next != null && !next.getInstant().isAfter(end)) {
				transitions.add(RowFactory.create(next.toEpochSecond(), next.getOffsetBefore().getTotalSeconds(),
						next.getOffsetAfter().getTotalSeconds()));
				next = nextTransition(rules, next.getInstant());
			}
		}
		return RowFactory.create(kind, date == null ? null : date.getYear(), date == null ? null : date.getMonthValue(),
				date == null ? null : date.getDayOfMonth(), date == null ? null : date.toEpochDay(), second,
				value instanceof ZonedDateTime, offset, transitions);
	}
	private static java.time.zone.ZoneOffsetTransition nextTransition(java.time.zone.ZoneRules rules, Instant instant) {
		try {
			return rules.nextTransition(instant);
		} catch (DateTimeException outsideLocalDateRange) {
			return null;
		}
	}

	static String sql(RuntimeModel model, RuntimeDecision decision, String column) {
		var fields = new ArrayList<String>();
		String source = "`" + column.replace("`", "``") + "`";
		for (var item : plan(model, decision)) {
			fields.add(SparkSqlLiterals.string(item.name()));
			String epoch = shiftedDay(item, source);
			String result = item.function().equals("day of week")
					? "element_at(array('Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'), CAST(pmod(cal_day + 3,7)+1 AS INT))"
					: monthName("cal_day");
			String valid = source + ".kind IN ("
					+ (item.shifted() ? "'DATE','DATE_TIME'" : "'DATE','DATE_TIME','PARSED_DATE'") + ")";
			fields.add("CASE WHEN " + valid + " THEN " + let("cal_day", epoch,
					"CASE WHEN cal_day < -365243219162L OR cal_day > 365241780471L THEN CAST(raise_error('FEEL calendar date overflow') AS STRING) ELSE "
							+ result + " END")
					+ " ELSE NULL END");
		}
		return "named_struct(" + String.join(",", fields) + ")";
	}
	private static String shiftedDay(Item item, String s) {
		if (!item.shifted())
			return s + ".epochDay";
		if (item.months() == 0) {
			String local = "(" + s + ".epochDay * 86400L + " + s + ".second + " + item.days() * 86400L + "L)";
			String instant = "(" + local + " - " + s + ".offset)";
			String initial = "coalesce(try_element_at(" + s + ".transitions,1).before," + s + ".offset)";
			String offset = "aggregate(" + s + ".transitions," + initial
					+ ",(cal_offset,cal_t) -> CASE WHEN cal_t.instant <= cal_instant THEN cal_t.after ELSE cal_offset END)";
			return "CASE WHEN " + s + ".zoned THEN "
					+ let("cal_instant", instant, "CAST(floor((cal_instant + " + offset + ") / 86400.0) AS BIGINT)")
					+ " ELSE " + s + ".epochDay + " + item.days() + "L END";
		}
		String total = "(CAST(" + s + ".year AS BIGINT)*12 + " + s + ".month - 1 + " + item.months() + "L)";
		String days = "element_at(array(31,CASE WHEN pmod(cal_year,4)=0 AND (pmod(cal_year,100)<>0 OR pmod(cal_year,400)=0) THEN 29 ELSE 28 END,31,30,31,30,31,31,30,31,30,31),CAST(cal_month AS INT))";
		String ordinal = "element_at(array(0,31,59,90,120,151,181,212,243,273,304,334),CAST(cal_month AS INT)) + least("
				+ s + ".day," + days
				+ ") + CASE WHEN cal_month>2 AND pmod(cal_year,4)=0 AND (pmod(cal_year,100)<>0 OR pmod(cal_year,400)=0) THEN 1 ELSE 0 END";
		String epoch = "CAST(365L*(cal_year-1) + floor((cal_year-1)/4.0) - floor((cal_year-1)/100.0) + floor((cal_year-1)/400.0) + "
				+ ordinal + " - 719163 AS BIGINT)";
		String local = "(cal_target_day*86400L + " + s + ".second)";
		String gap = "aggregate(" + s + ".transitions,0,(cal_gap,cal_t) -> CASE WHEN cal_t.after>cal_t.before AND "
				+ local + ">=cal_t.instant+cal_t.before AND " + local
				+ "<cal_t.instant+cal_t.after THEN cal_t.after-cal_t.before ELSE cal_gap END)";
		return let("cal_total", total, let("cal_year", "CAST(floor(cal_total/12.0) AS BIGINT)", let("cal_month",
				"pmod(cal_total,12)+1",
				"CASE WHEN cal_year < -999999999 OR cal_year > 999999999 THEN CAST(raise_error('FEEL calendar year overflow') AS BIGINT) ELSE "
						+ let("cal_target_day", epoch, "cal_target_day + CASE WHEN " + s + ".zoned THEN CAST(floor(("
								+ s + ".second + " + gap + ")/86400.0) AS BIGINT) ELSE 0L END")
						+ " END")));
	}
	// Gregorian civil-from-days decomposition, valid throughout LocalDate's range.
	private static String monthName(String epoch) {
		String expression = "element_at(array('January','February','March','April','May','June','July','August','September','October','November','December'),CAST(cal_mp + CASE WHEN cal_mp<10 THEN 3 ELSE -9 END AS INT))";
		expression = let("cal_mp", "CAST(floor((5*cal_doy+2)/153.0) AS BIGINT)", expression);
		expression = let("cal_doy", "cal_doe-(365*cal_yoe+floor(cal_yoe/4.0)-floor(cal_yoe/100.0))", expression);
		expression = let("cal_yoe",
				"CAST(floor((cal_doe-floor(cal_doe/1460.0)+floor(cal_doe/36524.0)-floor(cal_doe/146096.0))/365.0) AS BIGINT)",
				expression);
		expression = let("cal_doe", "pmod(" + epoch + "+719468L,146097L)", expression);
		return expression;
	}
	private static String let(String name, String value, String expression) {
		return "element_at(transform(array(" + value + "), " + name + " -> " + expression + "),1)";
	}
}
