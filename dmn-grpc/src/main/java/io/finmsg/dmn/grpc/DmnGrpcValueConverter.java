package io.finmsg.dmn.grpc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-performance bidirectional converter between Protobuf Value messages and
 * Java runtime objects.
 */
public final class DmnGrpcValueConverter {

	private DmnGrpcValueConverter() {
	}

	public static Object toJavaObject(Value protoValue) {
		if (protoValue == null) {
			return null;
		}

		return switch (protoValue.getValueKindCase()) {
			case NULL_VALUE, VALUEKIND_NOT_SET -> null;
			case BOOLEAN_VALUE -> protoValue.getBooleanValue();
			case NUMBER_VALUE -> new BigDecimal(protoValue.getNumberValue());
			case STRING_VALUE -> protoValue.getStringValue();
			case DATE_VALUE -> LocalDate.parse(protoValue.getDateValue());
			case TIME_VALUE -> LocalTime.parse(protoValue.getTimeValue());
			case DATE_TIME_VALUE -> LocalDateTime.parse(protoValue.getDateTimeValue());
			case DURATION_VALUE -> parseDuration(protoValue.getDurationValue());
			case LIST_VALUE -> {
				List<Object> list = new ArrayList<>();
				for (Value elem : protoValue.getListValue().getElementsList()) {
					list.add(toJavaObject(elem));
				}
				yield List.copyOf(list);
			}
			case CONTEXT_VALUE -> {
				Map<String, Object> map = new LinkedHashMap<>();
				for (Map.Entry<String, Value> entry : protoValue.getContextValue().getEntriesMap().entrySet()) {
					map.put(entry.getKey(), toJavaObject(entry.getValue()));
				}
				yield Map.copyOf(map);
			}
		};
	}

	public static Value toProtoValue(Object javaValue) {
		Value.Builder builder = Value.newBuilder();
		if (javaValue == null) {
			return builder.setNullValue(true).build();
		}

		if (javaValue instanceof Boolean b) {
			return builder.setBooleanValue(b).build();
		} else if (javaValue instanceof BigDecimal bd) {
			return builder.setNumberValue(bd.toPlainString()).build();
		} else if (javaValue instanceof Number num) {
			return builder.setNumberValue(num.toString()).build();
		} else if (javaValue instanceof String s) {
			return builder.setStringValue(s).build();
		} else if (javaValue instanceof LocalDate ld) {
			return builder.setDateValue(ld.toString()).build();
		} else if (javaValue instanceof LocalTime lt) {
			return builder.setTimeValue(lt.toString()).build();
		} else if (javaValue instanceof LocalDateTime ldt) {
			return builder.setDateTimeValue(ldt.toString()).build();
		} else if (javaValue instanceof TemporalAmount ta) {
			return builder.setDurationValue(ta.toString()).build();
		} else if (javaValue instanceof List<?> list) {
			ListValue.Builder listBuilder = ListValue.newBuilder();
			for (Object elem : list) {
				listBuilder.addElements(toProtoValue(elem));
			}
			return builder.setListValue(listBuilder).build();
		} else if (javaValue instanceof Map<?, ?> map) {
			ContextValue.Builder contextBuilder = ContextValue.newBuilder();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (entry.getKey() != null) {
					contextBuilder.putEntries(entry.getKey().toString(), toProtoValue(entry.getValue()));
				}
			}
			return builder.setContextValue(contextBuilder).build();
		}

		return builder.setStringValue(javaValue.toString()).build();
	}

	private static TemporalAmount parseDuration(String val) {
		if (val.contains("T")) {
			return Duration.parse(val);
		}
		return Period.parse(val).normalized();
	}
}
