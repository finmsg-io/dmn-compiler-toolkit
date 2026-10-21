package io.finmsg.dmn.generator.sparksql;

import java.time.*;
import java.util.List;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;
import io.finmsg.dmn.runtime.DmnRuntime;

/**
 * Canonical temporal input components; original opaque payloads remain
 * untouched.
 */
final class SparkSqlTemporalValue {
	private record Value(String kind, long a, long b, long c, long x, long y, long z, String equalityZone,
			Long localDay, Integer localSecond, Integer nano, Integer offset, String zone) {
	}
	static StructType type() {
		return new StructType().add("kind", DataTypes.StringType).add("equalityKind", DataTypes.StringType)
				.add("a", DataTypes.LongType).add("b", DataTypes.LongType).add("c", DataTypes.LongType)
				.add("x", DataTypes.LongType).add("y", DataTypes.LongType).add("z", DataTypes.LongType)
				.add("equalityZone", DataTypes.StringType).add("localDay", DataTypes.LongType)
				.add("localSecond", DataTypes.IntegerType).add("nano", DataTypes.IntegerType)
				.add("offset", DataTypes.IntegerType).add("zone", DataTypes.StringType);
	}
	static Row row(Object raw, boolean expressionEquality) {
		Value order = value(raw);
		Value equal = expressionEquality && raw instanceof List<?> list && list.size() == 1
				? value(list.getFirst())
				: order;
		return RowFactory.create(order.kind(), equal.kind(), order.a(), order.b(), order.c(), equal.x(), equal.y(),
				equal.z(), equal.equalityZone(), order.localDay(), order.localSecond(), order.nano(), order.offset(),
				order.zone());
	}
	private static Value value(Object raw) {
		if (raw instanceof Boolean value)
			return new Value("BOOLEAN", value ? 1 : 0, 0, 0, value ? 1 : 0, 0, 0, null, null, null, null, null, null);
		if (raw instanceof String text) {
			var key = new StringBuilder();
			for (int i = 0; i < text.length(); i++)
				key.append(String.format(java.util.Locale.ROOT, "%04x", (int) text.charAt(i)));
			return new Value("STRING", 0, 0, 0, 0, 0, 0, key.toString(), null, null, null, null, null);
		}
		if (raw instanceof Number number) {
			try {
				var value = number instanceof java.math.BigDecimal d ? d : new java.math.BigDecimal(number.toString());
				String key = numberKey(value);
				return new Value("NUMBER", 0, 0, 0, 0, 0, 0, key, null, null, null, null, null);
			} catch (NumberFormatException invalid) {
				return new Value("INVALID", 0, 0, 0, 0, 0, 0, null, null, null, null, null, null);
			}
		}

		if (raw instanceof LocalDate date)
			return new Value("DATE", date.toEpochDay(), 0, 0, date.toEpochDay(), 0, 0, null, date.toEpochDay(), null,
					null, null, null);
		if (raw instanceof LocalTime time)
			return time(time, null);
		if (raw instanceof DmnRuntime.NamedZoneTime named)
			return time(named.value(), named.zone().getId());
		if (raw instanceof OffsetTime time) {
			long seconds = time.toLocalTime().toSecondOfDay() - (long) time.getOffset().getTotalSeconds();
			return new Value("OFFSET_TIME", seconds, time.getNano(), time.toLocalTime().toNanoOfDay(), seconds,
					time.getNano() / 1000000, 0, null, null, time.toLocalTime().toSecondOfDay(), time.getNano(),
					time.getOffset().getTotalSeconds(), time.getOffset().getId());
		}
		if (raw instanceof LocalDateTime time)
			return new Value("LOCAL_DATE_TIME", time.toLocalDate().toEpochDay(), time.toLocalTime().toSecondOfDay(),
					time.getNano(), time.toLocalDate().toEpochDay(), time.toLocalTime().toSecondOfDay(),
					time.getNano() / 1000000, null, time.toLocalDate().toEpochDay(), time.toLocalTime().toSecondOfDay(),
					time.getNano(), null, null);
		LocalDateTime local = null;
		ZoneId zone = null;
		ZoneOffset offset = null;
		if (raw instanceof OffsetDateTime time) {
			local = time.toLocalDateTime();
			zone = time.getOffset();
			offset = time.getOffset();
		}
		if (raw instanceof ZonedDateTime time) {
			local = time.toLocalDateTime();
			zone = time.getZone();
			offset = time.getOffset();
		}
		if (raw instanceof DmnRuntime.NamedZoneDateTime time) {
			local = time.value();
			zone = time.zone();
			offset = local.atZone(zone).getOffset();
		}
		if (local != null) {
			Instant instant = DmnRuntime.toInstant(raw);
			return new Value("INSTANT", instant.getEpochSecond(), instant.getNano(), 0, instant.getEpochSecond(),
					instant.getNano() / 1000000, 0, null, local.toLocalDate().toEpochDay(),
					local.toLocalTime().toSecondOfDay(), local.getNano(), offset.getTotalSeconds(), zone.getId());
		}
		if (raw instanceof Duration duration)
			return new Value("DURATION", duration.getSeconds(), duration.getNano(), 0, duration.getSeconds(),
					duration.getNano(), 0, null, null, null, duration.getNano(), null, null);
		if (raw instanceof Period period)
			return new Value("PERIOD", period.toTotalMonths(), 0, 0, period.toTotalMonths(), 0, 0, null,
					(long) period.getDays(), null, null, null, null);
		return new Value(raw == null ? "NULL" : "OTHER", 0, 0, 0, 0, 0, 0, null, null, null, null, null, null);
	}
	private static String numberKey(java.math.BigDecimal value) {
		if (value.signum() == 0)
			return "1";
		String digits = value.unscaledValue().abs().toString();
		int end = digits.length();
		while (digits.charAt(end - 1) == '0')
			end--;
		digits = digits.substring(0, end);
		long exponent = value.precision() - (long) value.scale() + 2147483648L;
		String prefix = String.format(java.util.Locale.ROOT, "%010d", exponent);
		if (value.signum() > 0)
			return "2" + prefix + digits + "!";
		var complemented = new StringBuilder("0");
		for (char digit : (prefix + digits).toCharArray())
			complemented.append((char) ('9' - (digit - '0')));
		return complemented.append(':').toString();
	}

	private static Value time(LocalTime time, String zone) {
		return new Value(zone == null ? "LOCAL_TIME" : "NAMED_TIME", time.toSecondOfDay(), time.getNano(), 0,
				time.toSecondOfDay(), time.getNano() / 1000000, 0, zone, null, time.toSecondOfDay(), time.getNano(),
				null, zone);
	}
}
