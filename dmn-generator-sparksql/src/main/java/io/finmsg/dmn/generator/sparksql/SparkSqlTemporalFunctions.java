package io.finmsg.dmn.generator.sparksql;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.spark.sql.types.*;

/**
 * Exact component SQL; JDK factories are Spark built-ins, never FEEL evaluator
 * calls.
 */
final class SparkSqlTemporalFunctions {
	static final List<String> FIELDS = List.of("k", "t", "day", "second", "nano", "offset", "py", "pm", "pd", "ds", "n",
			"z", "zs");
	static StructType type() {
		return new StructType().add("k", DataTypes.StringType).add("t", DataTypes.StringType)
				.add("day", DataTypes.LongType).add("second", DataTypes.IntegerType).add("nano", DataTypes.IntegerType)
				.add("offset", DataTypes.IntegerType).add("py", DataTypes.IntegerType).add("pm", DataTypes.IntegerType)
				.add("pd", DataTypes.IntegerType).add("ds", DataTypes.LongType).add("n", SparkSqlDecimalSql.type())
				.add("z", SparkSqlTemporalZoneFacts.type()).add("zs", SparkSqlTemporalZoneFacts.type());
	}
	static String q(String s) {
		return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
	}
	static String let(String name, String value, String body) {
		return SparkSqlDecimalSql.let(name, value, body);
	}
	static String factory(String cls, String method, String... args) {
		return "try_reflect('java.time." + cls + "'," + q(method)
				+ (args.length == 0 ? "" : "," + String.join(",", args)) + ")";
	}
	static String pack(String... pairs) {
		return packFrom(null, pairs);
	}
	static String packFrom(String source, String... pairs) {
		var values = new LinkedHashMap<String, String>();
		for (var field : FIELDS)
			values.put(field, source == null ? switch (field) {
				case "k" -> "'NULL'";
				case "t" -> "CAST(NULL AS STRING)";
				case "day" -> "CAST(NULL AS BIGINT)";
				case "offset" -> "CAST(NULL AS INT)";
				case "ds" -> "0L";
				case "n" -> SparkSqlDecimalSql.nil();
				case "z", "zs" -> SparkSqlTemporalZoneFacts.nil();
				default -> "0";
			} : source + "." + field);
		for (int i = 0; i < pairs.length; i += 2)
			values.put(pairs[i], pairs[i + 1]);
		var fields = new ArrayList<String>();
		for (var e : values.entrySet()) {
			fields.add(q(e.getKey()));
			String value = e.getValue();
			if (Set.of("second", "nano", "offset", "py", "pm", "pd").contains(e.getKey()))
				value = "CAST(" + value + " AS INT)";
			if (Set.of("day", "ds").contains(e.getKey()))
				value = "CAST(" + value + " AS BIGINT)";
			fields.add(value);
		}
		return "named_struct(" + String.join(",", fields) + ")";
	}
	static String nil() {
		return pack();
	}
	static String re(String value, String pattern, int group) {
		return "regexp_extract(" + value + "," + q(pattern) + "," + group + ")";
	}
	static String year(String text) {
		return "CAST(" + re(text, "^([+-]?\\d+)-", 1) + " AS BIGINT)";
	}
	static String month(String text) {
		return "CAST(" + re(text, "^[+-]?\\d+-(\\d{2})-", 1) + " AS INT)";
	}
	static String day(String text) {
		return "CAST(" + re(text, "^[+-]?\\d+-\\d{2}-(\\d{2})", 1) + " AS INT)";
	}
	static String dateText(String value) {
		return "substring_index(" + value + ".t,'T',1)";
	}
	static String offsetText(String offset) {
		return factory("ZoneOffset", "ofTotalSeconds", "CAST(" + offset + " AS INT)");
	}
	private static String offsetSeconds(String text) {
		String pattern = "^([+-])(\\d{2}):(\\d{2})(?::(\\d{2}))?$";
		return "CASE WHEN " + text + "='Z' THEN 0 ELSE (CASE WHEN " + re(text, pattern, 1)
				+ "='-' THEN -1 ELSE 1 END)*(CAST(" + re(text, pattern, 2) + " AS INT)*3600+CAST("
				+ re(text, pattern, 3) + " AS INT)*60+coalesce(try_cast(nullif(" + re(text, pattern, 4)
				+ ",'') AS INT),0)) END";
	}
	private static String secondOfDay(String text) {
		return "CAST(substring(" + text + ",1,2) AS INT)*3600+CAST(substring(" + text
				+ ",4,2) AS INT)*60+coalesce(try_cast(nullif(" + re(text, "^\\d{2}:\\d{2}:(\\d{2})", 1)
				+ ",'') AS INT),0)";
	}
	private static String nano(String text) {
		return "CAST(rpad(" + re(text, "\\.(\\d+)", 1) + ",9,'0') AS INT)";
	}
	static String date(String text) {
		String regex = "^(\\d{4}|[1-9]\\d{4,8}|-\\d{4,9})-(\\d{2})-(\\d{2})$";
		String value = "CASE WHEN " + text + " RLIKE " + q(regex) + " THEN "
				+ factory("LocalDate", "of", "try_cast(" + re(text, regex, 1) + " AS INT)",
						"try_cast(" + re(text, regex, 2) + " AS INT)", "try_cast(" + re(text, regex, 3) + " AS INT)")
				+ " ELSE NULL END";
		return dateCanonical(value);
	}
	static String dateCanonical(String text) {
		return let("temporal_date", text, "CASE WHEN temporal_date IS NULL THEN " + nil() + " ELSE " + pack("k",
				"'DATE'", "t", "temporal_date", "day",
				SparkSqlTemporalZoneFacts.epoch(year("temporal_date"), month("temporal_date"), day("temporal_date")))
				+ " END");
	}
	private static String wrap32(String value) {
		return "CAST(pmod(CAST(" + value + " AS DECIMAL(28,0))+2147483648,4294967296)-2147483648 AS INT)";
	}
	static String int32(String number) {
		String digits = "CASE WHEN temporal_number.scale>=0 THEN substring(temporal_number.digits,1,CAST(greatest(0L,length(temporal_number.digits)-temporal_number.scale) AS INT)) WHEN temporal_number.scale<=-32 THEN '0' ELSE concat(temporal_number.digits,repeat('0',CAST(-temporal_number.scale AS INT))) END";
		String modulo = "CASE WHEN length(temporal_digits)=0 THEN 0L ELSE aggregate(sequence(1,length(temporal_digits)),0L,(temporal_mod,temporal_pos) -> pmod(temporal_mod*10L+CAST(substring(temporal_digits,temporal_pos,1) AS BIGINT),4294967296L)) END";
		String sign = "CASE WHEN temporal_number.negative AND temporal_modulo<>0 THEN 4294967296L-temporal_modulo ELSE temporal_modulo END";
		return let("temporal_number", number, let("temporal_digits", digits, let("temporal_modulo", modulo, let(
				"temporal_signed", sign,
				"CAST(CASE WHEN temporal_signed>=2147483648L THEN temporal_signed-4294967296L ELSE temporal_signed END AS INT)"))));
	}
	private static String fractionNano(String number) {
		String digits = "rpad(concat(repeat('0',CAST(greatest(0L,least(9L,temporal_number.scale-length(temporal_number.digits))) AS INT)),substring(temporal_number.digits,CAST(greatest(1L,length(temporal_number.digits)-temporal_number.scale+1L) AS INT),9)),9,'0')";
		return let("temporal_number", number,
				"CASE WHEN temporal_number.scale<=0 THEN 0 ELSE (CASE WHEN temporal_number.negative THEN -1 ELSE 1 END)*CAST("
						+ digits + " AS INT) END");
	}
	static String dateNumeric(String a, String b, String c) {
		String result = dateCanonical(factory("LocalDate", "of", int32(a + ".n"), int32(b + ".n"), int32(c + ".n")));
		return "CASE WHEN " + a + ".n.invalid OR " + b + ".n.invalid OR " + c
				+ ".n.invalid THEN CAST(raise_error('Invalid FEEL date number') AS " + type().sql() + ") ELSE " + result
				+ " END";
	}
	private static String timeCanonical(String local, String kind, String offset, String zone) {
		return let("temporal_time", local,
				"CASE WHEN temporal_time IS NULL THEN " + nil() + " ELSE "
						+ pack("k", kind, "t", "temporal_time", "second", secondOfDay("temporal_time"), "nano",
								nano("temporal_time"), "offset", offset, "z", zone)
						+ " END");
	}
	static String time(String cell) {
		String raw = cell + ".t";
		String end = "24:00(?::00(?:\\.0{1,9})?)?(?:Z|[+-]\\d{2}:\\d{2}|@[A-Za-z_]+(?:/[A-Za-z0-9_+\\-]+)*)?";
		String normalized = "CASE WHEN " + raw + " LIKE '24:%' THEN CASE WHEN " + raw + " RLIKE " + q("^" + end + "$")
				+ " THEN concat('00:',substring(" + raw + ",4)) ELSE NULL END ELSE " + raw + " END";
		String local = factory("LocalTime", "parse", "substring(temporal_text,1,instr(temporal_text,'@')-1)");
		String named = "CASE WHEN " + cell + ".zs.id IS NULL THEN " + nil() + " ELSE "
				+ timeCanonical(local, "'TIME_NAMED'", "NULL", cell + ".zs") + " END";
		String off = factory("OffsetTime", "parse", "temporal_text");
		String suffix = re("temporal_offset_time", "(Z|[+-]\\d{2}:\\d{2}(?::\\d{2})?)$", 1);
		String core = "regexp_replace(temporal_offset_time," + q("(Z|[+-]\\d{2}:\\d{2}(?::\\d{2})?)$") + ",'')";
		String offset = let("temporal_offset_time", off,
				timeCanonical(core, "'TIME_OFFSET'", offsetSeconds(suffix), SparkSqlTemporalZoneFacts.nil()));
		String plain = timeCanonical(factory("LocalTime", "parse", "temporal_text"), "'TIME_LOCAL'", "NULL",
				SparkSqlTemporalZoneFacts.nil());
		return let("temporal_text", normalized, "CASE WHEN instr(temporal_text,'@')>0 THEN " + named
				+ " WHEN temporal_text RLIKE " + q(".*[Z+-][0-9:]*$") + " THEN " + offset + " ELSE " + plain + " END");
	}
	static String timeNumeric(String a, String b, String c, String fourth) {
		String local = factory("LocalTime", "of", int32(a + ".n"), int32(b + ".n"), int32(c + ".n"),
				fractionNano(c + ".n"));
		String plain = timeCanonical("temporal_constructed_time", "'TIME_LOCAL'", "NULL",
				SparkSqlTemporalZoneFacts.nil());
		String result = plain;
		if (fourth != null) {
			String durationOffset = offsetText(wrap32(fourth + ".ds"));
			String offset = timeCanonical("temporal_constructed_time", "'TIME_OFFSET'",
					offsetSeconds("temporal_constructed_offset"), SparkSqlTemporalZoneFacts.nil());
			String fromDuration = let("temporal_constructed_offset", durationOffset,
					"CASE WHEN temporal_constructed_offset IS NULL THEN " + nil() + " ELSE " + offset + " END");
			String fromString = let("temporal_constructed_offset", factory("ZoneOffset", "of", fourth + ".t"),
					"CASE WHEN temporal_constructed_offset IS NOT NULL THEN " + offset + " WHEN " + fourth
							+ ".z.id IS NOT NULL THEN "
							+ timeCanonical("temporal_constructed_time", "'TIME_NAMED'", "NULL", fourth + ".z")
							+ " ELSE " + nil() + " END");
			result = "CASE WHEN " + fourth + ".k='DURATION' THEN " + fromDuration + " WHEN " + fourth
					+ ".k='STRING' THEN " + fromString + " ELSE " + plain + " END";
		}
		return "CASE WHEN " + a + ".n.invalid OR " + b + ".n.invalid OR " + c + ".n.invalid THEN " + nil() + " ELSE "
				+ let("temporal_constructed_time", local, result) + " END";
	}
	static String combine(String date, String time) {
		String kind = "CASE " + time
				+ ".k WHEN 'TIME_LOCAL' THEN 'DT_LOCAL' WHEN 'TIME_OFFSET' THEN 'DT_OFFSET' ELSE 'DT_NAMED' END";
		String text = factory("LocalDateTime", "parse", "concat(" + date + ".t,'T'," + time + ".t)");
		return "CASE WHEN " + date + ".k<>'DATE' OR " + time
				+ ".k NOT IN ('TIME_LOCAL','TIME_OFFSET','TIME_NAMED') THEN " + nil() + " ELSE "
				+ pack("k", kind, "t", text, "day", date + ".day", "second", time + ".second", "nano", time + ".nano",
						"offset", time + ".offset", "z", time + ".z")
				+ " END";
	}
	static String dateOf(String value) {
		return "CASE WHEN " + value + ".k='STRING' THEN " + date(value + ".t") + " WHEN " + value + ".k='DATE' THEN "
				+ value + " WHEN " + value + ".k IN ('DT_LOCAL','DT_OFFSET','DT_NAMED','DT_ZONED') THEN "
				+ dateCanonical(dateText(value)) + " ELSE " + nil() + " END";
	}
	static String timeOf(String value) {
		String kind = "CASE " + value
				+ ".k WHEN 'DT_LOCAL' THEN 'TIME_LOCAL' WHEN 'DT_NAMED' THEN 'TIME_NAMED' ELSE 'TIME_OFFSET' END";
		return "CASE WHEN " + value + ".k='STRING' THEN " + time(value) + " WHEN " + value + ".k='DATE' THEN "
				+ timeCanonical("'00:00'", "'TIME_OFFSET'", "0", SparkSqlTemporalZoneFacts.nil()) + " WHEN " + value
				+ ".k LIKE 'TIME_%' THEN " + value + " WHEN " + value + ".k LIKE 'DT_%' THEN "
				+ timeCanonical("substring_index(" + value + ".t,'T',-1)", kind, value + ".offset", value + ".z")
				+ " ELSE " + nil() + " END";
	}
	static String dateTime(String value) {
		String raw = value + ".t";
		String datePart = "CASE WHEN instr(" + raw + ",'T')>0 THEN substring(" + raw + ",1,instr(" + raw
				+ ",'T')-1) WHEN instr(" + raw + ",'@')>0 THEN substring(" + raw + ",1,instr(" + raw
				+ ",'@')-1) WHEN right(" + raw + ",1)='Z' THEN substring(" + raw + ",1,length(" + raw + ")-1) ELSE "
				+ raw + " END";
		String timePart = "CASE WHEN instr(" + raw + ",'T')>0 THEN substring(" + raw + ",instr(" + raw
				+ ",'T')+1) WHEN instr(" + raw + ",'@')>0 THEN concat('00:00@',substring(" + raw + ",instr(" + raw
				+ ",'@')+1)) WHEN right(" + raw + ",1)='Z' THEN '00:00Z' ELSE '00:00' END";
		String timeCell = packFrom(value, "t", "temporal_time_part");
		String shiftedDate = "CASE WHEN instr(" + raw
				+ ",'T')>0 AND temporal_time_part LIKE '24:%' AND temporal_parsed_time.k<>'NULL' THEN "
				+ dateCanonical(factory("LocalDate", "ofEpochDay", "temporal_parsed_date.day+1L"))
				+ " ELSE temporal_parsed_date END";
		return let("temporal_time_part", timePart, let("temporal_parsed_date", date(datePart), let(
				"temporal_parsed_time", time(timeCell),
				let("temporal_shifted_date", shiftedDate, combine("temporal_shifted_date", "temporal_parsed_time")))));
	}
	static String period(String years, String months, String days) {
		String text = factory("Period", "of", "CAST(" + years + " AS INT)", "CAST(" + months + " AS INT)",
				"CAST(" + days + " AS INT)");
		return pack("k", "'PERIOD'", "t", text, "py", years, "pm", months, "pd", days);
	}
	static String duration(String seconds, String nanos, boolean overflowError) {
		String sum = "CAST(" + seconds + " AS DECIMAL(28,0))+CAST(floor((" + nanos
				+ ")/1000000000.0) AS DECIMAL(28,0))";
		String nano = "pmod(" + nanos + ",1000000000L)";
		String value = pack("k", "'DURATION'", "t", factory("Duration", "ofSeconds",
				"CAST(temporal_duration_seconds AS BIGINT)", "CAST(" + nano + " AS BIGINT)"), "ds",
				"temporal_duration_seconds", "nano", nano);
		String error = overflowError ? "CAST(raise_error('FEEL duration overflow') AS " + type().sql() + ")" : nil();
		return let("temporal_duration_seconds", sum,
				"CASE WHEN temporal_duration_seconds < CAST('-9223372036854775808' AS DECIMAL(28,0)) OR temporal_duration_seconds > CAST('9223372036854775807' AS DECIMAL(28,0)) THEN "
						+ error + " ELSE " + value + " END");
	}
	static String durationParse(String value) {
		String trimmed = "regexp_replace(" + value + ".t," + q("^[\\x00-\\x20]+|[\\x00-\\x20]+$") + ",'')";
		String ym = "instr(temporal_duration_text,'Y')>0 OR (instr(temporal_duration_text,'M')>0 AND instr(temporal_duration_text,'T')=0 AND instr(temporal_duration_text,'D')=0)";
		String pi = "^P(?:(-?\\d+)Y)?(?:(-?\\d+)M)?(?:(-?\\d+)D)?$";
		String py = "coalesce(try_cast(nullif(" + re("temporal_period", pi, 1) + ",'') AS BIGINT),0L)";
		String pm = "coalesce(try_cast(nullif(" + re("temporal_period", pi, 2) + ",'') AS BIGINT),0L)";
		String pd = "coalesce(try_cast(nullif(" + re("temporal_period", pi, 3) + ",'') AS INT),0)";
		String total = "(" + py + "*12L+" + pm + ")";
		String normalized = let("temporal_total_months", total,
				"CASE WHEN temporal_total_months DIV 12L NOT BETWEEN -2147483648L AND 2147483647L THEN " + nil()
						+ " ELSE " + period("temporal_total_months DIV 12L", "temporal_total_months % 12L", pd)
						+ " END");
		String period = let("temporal_period", factory("Period", "parse", "temporal_duration_text"),
				"CASE WHEN temporal_period IS NULL THEN " + nil() + " ELSE " + normalized + " END");
		String di = "^PT(?:(-?\\d+)H)?(?:(-?\\d+)M)?(?:(-?\\d+)(?:\\.(\\d{1,9}))?S)?$";
		String h = "coalesce(try_cast(nullif(" + re("temporal_duration", di, 1) + ",'') AS DECIMAL(28,0)),0)";
		String m = "coalesce(try_cast(nullif(" + re("temporal_duration", di, 2) + ",'') AS DECIMAL(28,0)),0)";
		String s = "coalesce(try_cast(nullif(" + re("temporal_duration", di, 3) + ",'') AS DECIMAL(28,0)),0)";
		String n = "CAST(rpad(" + re("temporal_duration", di, 4) + ",9,'0') AS BIGINT)*(CASE WHEN "
				+ re("temporal_duration", di, 3) + " LIKE '-%' THEN -1L ELSE 1L END)";
		String dur = let("temporal_duration", factory("Duration", "parse", "temporal_duration_text"),
				"CASE WHEN temporal_duration IS NULL THEN " + nil() + " ELSE "
						+ duration(h + "*3600+" + m + "*60+" + s, n, false) + " END");
		return let("temporal_duration_text", trimmed, "CASE WHEN " + ym + " THEN " + period + " ELSE " + dur + " END");
	}
	static String localDateForPeriod(String value) {
		return "CASE WHEN " + value + ".k='STRING' THEN " + dateCanonical(factory("LocalDate", "parse", value + ".t"))
				+ " ELSE " + dateOf(value) + " END";
	}
	static String monthsBetween(String a, String b) {
		String start = dateText(a), end = dateText(b);
		String months = "(" + year(end) + "-" + year(start) + ")*12L+" + month(end) + "-" + month(start);
		String adjusted = "temporal_months + CASE WHEN temporal_months>0 AND " + day(end) + "<" + day(start)
				+ " THEN -1L WHEN temporal_months<0 AND " + day(end) + ">" + day(start) + " THEN 1L ELSE 0L END";
		return "CASE WHEN " + a + ".k<>'DATE' OR " + b + ".k<>'DATE' THEN " + nil() + " ELSE "
				+ let("temporal_months", months,
						let("temporal_adjusted_months", adjusted,
								period("temporal_adjusted_months DIV 12L", "temporal_adjusted_months % 12L", "0")))
				+ " END";
	}
	static String offset(String value) {
		return "CASE WHEN " + value + ".k='DT_NAMED' THEN "
				+ SparkSqlTemporalZoneFacts.offset(value + ".z", year(value + ".t"), value + ".day", value + ".second")
				+ " ELSE " + value + ".offset END";
	}
	static String binary(String a, String b, boolean subtract) {
		String sign = subtract ? "-" : "+";
		String dur = duration("CAST(" + a + ".ds AS DECIMAL(28,0))" + sign + "CAST(" + b + ".ds AS DECIMAL(28,0))",
				a + ".nano" + sign + b + ".nano", true);
		String months = "(" + a + ".py*12L+" + a + ".pm)" + sign + "(" + b + ".py*12L+" + b + ".pm)";
		String per = let("temporal_period_months", months,
				period(wrap32("temporal_period_months DIV 12L"), "temporal_period_months%12L", "0"));
		String choices = "CASE WHEN " + a + ".k='DURATION' AND " + b + ".k='DURATION' THEN " + dur + " WHEN " + a
				+ ".k='PERIOD' AND " + b + ".k='PERIOD' THEN " + per;
		if (subtract) {
			String localKinds = "(" + a + ".k='DT_LOCAL' AND " + b + ".k='DT_LOCAL') OR (" + a + ".k='DATE' AND " + b
					+ ".k='DATE') OR (" + a + ".k='TIME_LOCAL' AND " + b + ".k='TIME_LOCAL') OR (" + a
					+ ".k='TIME_NAMED' AND " + b + ".k='TIME_NAMED')";
			String instantKinds = "(" + a + ".k IN ('DT_OFFSET','DT_NAMED','DT_ZONED','DATE') AND " + b
					+ ".k IN ('DT_OFFSET','DT_NAMED','DT_ZONED','DATE')) OR (" + a + ".k='TIME_OFFSET' AND " + b
					+ ".k='TIME_OFFSET')";
			String local = "(coalesce(" + a + ".day,0L)-coalesce(" + b + ".day,0L))*86400L+" + a + ".second-" + b
					+ ".second";
			String instant = local + "-coalesce(" + offset(a) + ",0)+coalesce(" + offset(b) + ",0)";
			choices += " WHEN " + localKinds + " THEN " + duration(local, a + ".nano-" + b + ".nano", true) + " WHEN "
					+ instantKinds + " THEN " + duration(instant, a + ".nano-" + b + ".nano", true);
		}
		return choices + " ELSE " + nil() + " END";
	}
	static String number(String value) {
		return pack("k", "'NUMBER'", "n",
				SparkSqlDecimalSql.number("CAST(abs(" + value + ") AS STRING)", "0L", value + "<0"));
	}
	static String property(String value, String member) {
		String dt = value + ".k IN ('DATE','DT_LOCAL','DT_OFFSET','DT_NAMED','DT_ZONED')";
		String tm = value
				+ ".k IN ('TIME_LOCAL','TIME_OFFSET','TIME_NAMED','DT_LOCAL','DT_OFFSET','DT_NAMED','DT_ZONED')";
		return switch (member) {
			case "year", "month", "day" -> "CASE WHEN " + dt + " THEN " + number(switch (member) {
				case "year" -> year(value + ".t");
				case "month" -> month(value + ".t");
				default -> day(value + ".t");
			}) + " ELSE " + nil() + " END";
			case "weekday" ->
				"CASE WHEN " + dt + " THEN " + number("pmod(" + value + ".day+3L,7L)+1L") + " ELSE " + nil() + " END";
			case "hour", "minute", "second" -> "CASE WHEN " + tm + " THEN " + number(switch (member) {
				case "hour" -> value + ".second DIV 3600";
				case "minute" -> "(" + value + ".second DIV 60)%60";
				default -> value + ".second%60";
			}) + " ELSE " + nil() + " END";
			case "time offset" -> "CASE WHEN " + value + ".k IN ('TIME_OFFSET','DT_OFFSET','DT_NAMED','DT_ZONED') THEN "
					+ duration(offset(value), "0L", true) + " ELSE " + nil() + " END";
			case "timezone" -> "CASE WHEN " + value + ".k IN ('TIME_NAMED','DT_NAMED','DT_ZONED') THEN "
					+ pack("k", "'STRING'", "t", value + ".z.id") + " WHEN " + value
					+ ".k IN ('TIME_OFFSET','DT_OFFSET') THEN "
					+ pack("k", "'STRING'", "t", offsetText(value + ".offset")) + " ELSE " + nil() + " END";
			case "years", "months" -> "CASE WHEN " + value + ".k='PERIOD' THEN "
					+ number(value + (member.equals("years") ? ".py" : ".pm")) + " ELSE " + nil() + " END";
			case "days", "hours", "minutes", "seconds" ->
				"CASE WHEN " + value + ".k='DURATION' THEN " + number(switch (member) {
					case "days" -> value + ".ds DIV 86400L";
					case "hours" -> "(" + value + ".ds DIV 3600L)%24L";
					case "minutes" -> "(" + value + ".ds DIV 60L)%60L";
					default -> value + ".ds%60L";
				}) + " ELSE " + nil() + " END";
			default -> nil();
		};
	}
	private static String intBytes(String length) {
		return "unhex(lpad(right(hex(" + length + "),8),8,'0'))";
	}
	private static String stringBytes(String text) {
		return "concat(" + intBytes("length(encode(" + text + ",'UTF-8'))") + ",encode(" + text + ",'UTF-8'))";
	}
	static String stringConstantBytes(String text) {
		return "unhex('" + HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8)) + "')";
	}
	static String key(String text) {
		return "concat(" + intBytes(Integer.toString(text.getBytes(StandardCharsets.UTF_8).length)) + ","
				+ stringConstantBytes(text) + ")";
	}
	private static String namedRecord(String value, boolean dateTime) {
		String cls = "io.finmsg.dmn.runtime.DmnRuntime$" + (dateTime ? "NamedZoneDateTime" : "NamedZoneTime");
		String type = dateTime ? "java.time.LocalDateTime" : "java.time.LocalTime";
		return "concat(unhex('09')," + key(cls) + "," + intBytes("2") + "," + key("value") + "," + key(type)
				+ ",unhex('" + (dateTime ? "0d" : "0b") + "')," + stringBytes(value + ".t") + "," + key("zone") + ","
				+ key("java.time.ZoneId") + ",unhex('12')," + stringBytes(value + ".z.id") + ")";
	}
	static String payloadBody(String value) {
		String temporal = "CASE " + value
				+ ".k WHEN 'DATE' THEN unhex('0a') WHEN 'TIME_LOCAL' THEN unhex('0b') WHEN 'TIME_OFFSET' THEN unhex('0c') WHEN 'DT_LOCAL' THEN unhex('0d') WHEN 'DT_OFFSET' THEN unhex('0e') WHEN 'DT_ZONED' THEN unhex('0f') WHEN 'DURATION' THEN unhex('10') ELSE unhex('11') END";
		String text = "CASE WHEN " + value + ".k IN ('TIME_OFFSET','DT_OFFSET') THEN concat(" + value + ".t,"
				+ offsetText(value + ".offset") + ") WHEN " + value + ".k='DT_ZONED' THEN concat(" + value + ".t,"
				+ offsetText(value + ".offset") + ",'['," + value + ".z.id,']') ELSE " + value + ".t END";
		return "CASE WHEN " + value + ".k='NULL' THEN unhex('00') WHEN " + value + ".k='NUMBER' THEN substring("
				+ SparkSqlDecimalSql.payload(value + ".n") + ",5) WHEN " + value
				+ ".k='STRING' THEN concat(unhex('01')," + stringBytes(value + ".t") + ") WHEN " + value
				+ ".k='TIME_NAMED' THEN " + namedRecord(value, false) + " WHEN " + value + ".k='DT_NAMED' THEN "
				+ namedRecord(value, true) + " ELSE concat(" + temporal + "," + stringBytes(text) + ") END";
	}
}
