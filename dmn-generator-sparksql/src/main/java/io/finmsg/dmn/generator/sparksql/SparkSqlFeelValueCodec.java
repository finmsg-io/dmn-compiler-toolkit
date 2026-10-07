package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/**
 * Lossless hybrid boundary. Non-string/boolean values use a versioned binary
 * FEEL payload.
 */
public final class SparkSqlFeelValueCodec {
	private SparkSqlFeelValueCodec() {
	}

	public static boolean isNativeType(RuntimeType type) {
		if (type == null)
			return false;
		return switch (type.kind()) {
			case STRING, BOOLEAN, NUMBER, NULL -> true;
			case LIST -> type.elementType() != null && isNativeType(type.elementType());
			case CONTEXT ->
				!type.fieldLayout().isEmpty() && type.fieldLayout().stream().allMatch(f -> isNativeType(f.type()));
			default -> false;
		};
	}

	public static DataType sparkType(RuntimeType type) {
		return isNativeType(type) ? SparkSqlSchemaGenerator.mapType(type) : DataTypes.BinaryType;
	}

	public static Object toSpark(Object value, RuntimeType type) {
		if (value == null)
			return null;
		if (isNativeType(type)) {
			if (type.kind() == RuntimeTypeKind.NULL)
				return null;
			if (type.kind() == RuntimeTypeKind.STRING && (value instanceof String || value instanceof CharSequence))
				return value.toString();
			if (type.kind() == RuntimeTypeKind.BOOLEAN && value instanceof Boolean)
				return value;
			if (type.kind() == RuntimeTypeKind.NUMBER) {
				if (value instanceof Number n)
					return n.doubleValue();
				if (value instanceof String s)
					return Double.parseDouble(s);
			}
			if (type.kind() == RuntimeTypeKind.DATE) {
				if (value instanceof LocalDate ld)
					return java.sql.Date.valueOf(ld);
				if (value instanceof java.sql.Date d)
					return d;
				if (value instanceof String s)
					return java.sql.Date.valueOf(LocalDate.parse(s));
			}
			if (type.kind() == RuntimeTypeKind.LIST) {
				if (value instanceof List<?> list) {
					return list.stream().map(elem -> toSpark(elem, type.elementType())).toList();
				}
			}
			if (type.kind() == RuntimeTypeKind.CONTEXT) {
				if (value instanceof Map<?, ?> map) {
					List<Object> fieldValues = new ArrayList<>();
					for (RuntimeField field : type.fieldLayout()) {
						fieldValues.add(toSpark(map.get(field.name()), field.type()));
					}
					return RowFactory.create(fieldValues.toArray());
				}
			}
			throw new IllegalArgumentException("Value does not match " + type.kind());
		}
		return encode(value);
	}

	public static byte[] encode(Object value) {
		return SparkSqlPayloadCodec.encode(value);
	}
	public static Object decode(byte[] value) {
		return SparkSqlPayloadCodec.decode(value);
	}

	/**
	 * Decode a native result using its declared FEEL type. Local TIME uses
	 * timestamp-without-timezone anchored at 1970-01-01 in generated SQL.
	 */
	public static Object fromSpark(Object value, RuntimeType type) {
		Object decoded = fromSpark(value);
		if (type != null && type.kind() == RuntimeTypeKind.TIME && decoded instanceof java.time.LocalDateTime time) {
			if (!time.toLocalDate().equals(LocalDate.of(1970, 1, 1)))
				throw new IllegalArgumentException("Native TIME must use the 1970-01-01 anchor");
			return time.toLocalTime();
		}
		return decoded;
	}

	/**
	 * Converts a native Spark value, or a value previously encoded with
	 * {@link #encode}.
	 */
	public static Object fromSpark(Object value) {
		if (value == null)
			return null;
		if (value instanceof byte[] bytes)
			return decode(bytes);
		if (value instanceof java.sql.Date date)
			return date.toLocalDate();
		if (value instanceof LocalDate date)
			return date;
		if (value instanceof java.sql.Timestamp timestamp)
			return timestamp.toLocalDateTime();
		if (value instanceof Row row) {
			var result = new LinkedHashMap<String, Object>();
			String[] names = row.schema().fieldNames();
			for (int i = 0; i < names.length; i++)
				result.put(names[i], fromSpark(row.get(i)));
			return result;
		}
		if (value instanceof scala.collection.Map<?, ?> map) {
			var result = new LinkedHashMap<String, Object>();
			var iterator = map.iterator();
			while (iterator.hasNext()) {
				var entry = iterator.next();
				if (!(entry._1() instanceof String key))
					throw new IllegalArgumentException("FEEL context key must be a string");
				result.put(key, fromSpark(entry._2()));
			}
			return result;
		}
		if (value instanceof scala.collection.Seq<?> sequence) {
			var result = new ArrayList<Object>();
			var iterator = sequence.iterator();
			while (iterator.hasNext())
				result.add(fromSpark(iterator.next()));
			return result;
		}
		if (value instanceof List<?> list)
			return list.stream().map(SparkSqlFeelValueCodec::fromSpark).toList();
		if (value instanceof Map<?, ?> map) {
			var result = new LinkedHashMap<String, Object>();
			map.forEach((key, item) -> {
				if (!(key instanceof String))
					throw new IllegalArgumentException("FEEL context key must be a string");
				result.put((String) key, fromSpark(item));
			});
			return result;
		}
		if (value instanceof Double d) {
			if (d.isNaN() || d.isInfinite())
				return null;
			BigDecimal bd = BigDecimal.valueOf(d);
			if (bd.scale() > 0 && bd.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
				bd = bd.setScale(0);
			}
			return bd;
		}
		if (value instanceof Float f) {
			if (f.isNaN() || f.isInfinite())
				return null;
			BigDecimal bd = BigDecimal.valueOf(f.doubleValue());
			if (bd.scale() > 0 && bd.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
				bd = bd.setScale(0);
			}
			return bd;
		}
		if (value instanceof Number number && !(value instanceof BigDecimal))
			return new BigDecimal(number.toString());
		return value;
	}

	public static StructType inputSchema(RuntimeModel model, Map<Integer, String> slotNames) {
		return DataTypes.createStructType(model.inputs().stream()
				.map(input -> DataTypes.createStructField(
						slotNames.getOrDefault(input.valueSlot(), "input_" + input.valueSlot()),
						sparkType(input.type()), true))
				.toList());
	}

	/**
	 * Encodes one input row for the generated hybrid input schema; keys are runtime
	 * value slots.
	 */
	public static Row inputRow(RuntimeModel model, Map<Integer, ?> values) {
		Object[] columns = new Object[model.inputs().size()];
		for (int i = 0; i < columns.length; i++) {
			RuntimeInput input = model.inputs().get(i);
			if (!values.containsKey(input.valueSlot()))
				throw new IllegalArgumentException("Missing input slot " + input.valueSlot());
			columns[i] = toSpark(io.finmsg.dmn.runtime.DmnRuntime.coerce(values.get(input.valueSlot()), input.type()),
					input.type());
		}
		return RowFactory.create(columns);
	}
}
