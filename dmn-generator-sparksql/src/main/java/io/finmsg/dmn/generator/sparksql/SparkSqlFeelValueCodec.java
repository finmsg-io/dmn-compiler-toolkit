package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
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
		return type != null && (type.kind() == RuntimeTypeKind.STRING || type.kind() == RuntimeTypeKind.BOOLEAN);
	}

	public static DataType sparkType(RuntimeType type) {
		return isNativeType(type) ? SparkSqlSchemaGenerator.mapType(type) : DataTypes.BinaryType;
	}

	public static Object toSpark(Object value, RuntimeType type) {
		if (value == null)
			return null;
		if (isNativeType(type)) {
			if (type.kind() == RuntimeTypeKind.STRING && value instanceof String
					|| type.kind() == RuntimeTypeKind.BOOLEAN && value instanceof Boolean)
				return value;
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
