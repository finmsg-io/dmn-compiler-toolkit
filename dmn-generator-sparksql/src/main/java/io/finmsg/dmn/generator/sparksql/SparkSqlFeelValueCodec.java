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
	 * timestamp-without-timezone anchored at 1970-01-01 in generated SQL. Validated
	 * constant temporal and duration results use exact ISO text, preserving
	 * nanoseconds, offsets, named zones and supported FEEL year bounds.
	 */
	public static Object fromSpark(Object value, RuntimeType type) {
		Object decoded = fromSpark(value);
		if (type != null && decoded instanceof String text && Set
				.of(RuntimeTypeKind.DATE, RuntimeTypeKind.TIME, RuntimeTypeKind.DATE_TIME).contains(type.kind())) {
			Object temporal = switch (type.kind()) {
				case DATE -> {
					try {
						yield java.time.LocalDate.parse(text);
					} catch (java.time.DateTimeException ignored) {
						yield null;
					}
				}
				case TIME -> io.finmsg.dmn.runtime.DmnRuntime.parseTime(text);
				case DATE_TIME -> io.finmsg.dmn.runtime.DmnRuntime.parseDateTime(text);
				default -> throw new IllegalStateException();
			};
			if (temporal == null)
				throw new IllegalArgumentException("Native temporal result does not match " + type.kind());
			return temporal;
		}
		if (type != null && decoded instanceof String text && Set
				.of(RuntimeTypeKind.DURATION, RuntimeTypeKind.YEARS_MONTHS_DURATION, RuntimeTypeKind.DAYS_TIME_DURATION)
				.contains(type.kind())) {
			Object duration = io.finmsg.dmn.runtime.DmnRuntime.parseDuration(text);
			if (duration == null
					|| type.kind() == RuntimeTypeKind.YEARS_MONTHS_DURATION && !(duration instanceof java.time.Period)
					|| type.kind() == RuntimeTypeKind.DAYS_TIME_DURATION && !(duration instanceof java.time.Duration))
				throw new IllegalArgumentException("Native duration result does not match " + type.kind());
			return duration;
		}
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

	static StructType calendarInputSchema(RuntimeModel model, Map<Integer, String> names, Set<Integer> slots,
			Set<Integer> stringSlots) {
		var fields = new ArrayList<StructField>(Arrays.asList(inputSchema(model, names).fields()));
		var date = SparkSqlCalendarArithmetic
				.extend(new StructType().add("kind", DataTypes.StringType).add("year", DataTypes.IntegerType)
						.add("month", DataTypes.IntegerType).add("day", DataTypes.IntegerType));
		for (var input : model.inputs()) {
			if (slots.contains(input.valueSlot()))
				fields.add(DataTypes.createStructField(SparkSqlCalendarInput.column(input.valueSlot(), names), date,
						false, new MetadataBuilder().putLong("dmnCalendarInputSlot", input.valueSlot()).build()));
		}
		for (var input : model.inputs()) {
			if (stringSlots.contains(input.valueSlot()))
				fields.add(DataTypes.createStructField(SparkSqlStringInput.column(input.valueSlot(), names),
						DataTypes.StringType, true,
						new MetadataBuilder().putLong("dmnStringInputSlot", input.valueSlot()).build()));
		}
		for (int slot : SparkSqlLookupKeyInput.slots(model)) {
			if (stringSlots.contains(slot))
				fields.add(DataTypes.createStructField(SparkSqlLookupKeyInput.column(slot, names), DataTypes.StringType,
						true, new MetadataBuilder().putLong("dmnLookupKeyInputSlot", slot).build()));
		}
		for (int slot : SparkSqlListPositionInput.slots(model))
			fields.add(DataTypes.createStructField(SparkSqlListPositionInput.column(slot, names),
					new StructType().add("value", DataTypes.IntegerType).add("invalid", DataTypes.BooleanType), false,
					new MetadataBuilder().putLong("dmnListPositionSlot", slot).build()));
		for (int id : SparkSqlNumericFilterInput.ids(model))
			fields.add(
					DataTypes.createStructField(SparkSqlNumericFilterInput.column(id, names),
							DataTypes.createArrayType(
									new StructType().add("key", DataTypes.StringType).add("text", DataTypes.StringType)
											.add("invalid", DataTypes.BooleanType).add("sign", DataTypes.IntegerType)
											.add("exponent", DataTypes.LongType).add("digits", DataTypes.StringType),
									false),
							true, new MetadataBuilder().putLong("dmnNumericFilterDecisionId", id).build()));
		for (int id : SparkSqlTemporalTableInput.ids(model))
			fields.add(DataTypes.createStructField(SparkSqlTemporalTableInput.column(id, names),
					SparkSqlTemporalTableInput.type(), false,
					new MetadataBuilder().putLong("dmnTemporalTableDecisionId", id).build()));
		for (int id : SparkSqlIndexedJoinInput.ids(model))
			fields.add(DataTypes.createStructField(SparkSqlIndexedJoinInput.column(id, names),
					SparkSqlIndexedJoinInput.type(), false,
					new MetadataBuilder().putLong("dmnIndexedJoinDecisionId", id).build()));

		for (int id : SparkSqlExactSortInput.ids(model))
			fields.add(
					DataTypes.createStructField(SparkSqlExactSortInput.column(id, names), SparkSqlExactSortInput.type(),
							true, new MetadataBuilder().putLong("dmnExactSortDecisionId", id).build()));

		for (int id : SparkSqlExactProductInput.ids(model))
			fields.add(DataTypes.createStructField(SparkSqlExactProductInput.column(id, names),
					SparkSqlExactProductInput.type(), false,
					new MetadataBuilder().putLong("dmnExactProductDecisionId", id).build()));

		for (var decision : model.decisions())
			if (SparkSqlExactArithmeticInput.supports(model, decision))
				fields.add(DataTypes.createStructField(SparkSqlExactArithmeticInput.column(decision.id(), names),
						SparkSqlExactArithmeticInput.type(model, decision), false,
						new MetadataBuilder().putLong("dmnExactArithmeticDecisionId", decision.id())
								.putLongArray("dmnExactArithmeticSlots",
										SparkSqlExactArithmeticInput.slots(model, decision))
								.build()));

		for (var decision : model.decisions())
			if (SparkSqlFactorLoopInput.supports(model, decision))
				fields.add(DataTypes.createStructField(SparkSqlFactorLoopInput.column(decision.id(), names),
						SparkSqlFactorLoopInput.type(), false,
						new MetadataBuilder()
								.putLongArray("dmnFactorLoopSlots", SparkSqlFactorLoopInput.slots(model, decision))
								.build()));

		for (var decision : model.decisions())
			if (SparkSqlNumericLoopInput.supports(model, decision))
				fields.add(DataTypes.createStructField(SparkSqlNumericLoopInput.column(decision.id(), names),
						SparkSqlNumericLoopInput.type(model, decision), true,
						new MetadataBuilder()
								.putLong("dmnNumericLoopSlot", SparkSqlNumericLoopInput.slot(model, decision))
								.putStringArray("dmnNumericLoopFields",
										SparkSqlNumericLoopInput.fields(model, decision))
								.build()));

		for (var decision : model.decisions())
			if (SparkSqlScalarGraph.supports(model, decision))
				fields.add(DataTypes.createStructField(SparkSqlScalarGraph.column(decision.id(), names),
						SparkSqlScalarGraph.type(), false,
						new MetadataBuilder()
								.putLongArray("dmnScalarLeafSlots", SparkSqlScalarGraph.slots(model, decision))
								.putStringArray("dmnScalarLeafPaths", SparkSqlScalarGraph.paths(model, decision))
								.putStringArray("dmnScalarLeafKinds", SparkSqlScalarGraph.kinds(model, decision))
								.build()));

		return DataTypes.createStructType(fields);
	}

	/**
	 * Original payload columns followed by generator-declared calendar projections.
	 */
	public static Row inputRow(RuntimeModel model, Map<Integer, ?> values, StructType generatedSchema) {
		Row original = inputRow(model, values);
		Object[] columns = new Object[generatedSchema.size()];
		for (int index = 0; index < original.size(); index++)
			columns[index] = original.get(index);
		for (int index = original.size(); index < columns.length; index++) {
			Metadata metadata = generatedSchema.fields()[index].metadata();
			if (metadata.contains("dmnScalarLeafSlots")) {
				columns[index] = SparkSqlScalarGraph.project(model, metadata.getLongArray("dmnScalarLeafSlots"),
						metadata.getStringArray("dmnScalarLeafPaths"), metadata.getStringArray("dmnScalarLeafKinds"),
						values);
				continue;
			}
			if (metadata.contains("dmnNumericLoopSlot")) {
				columns[index] = SparkSqlNumericLoopInput.project(model,
						Math.toIntExact(metadata.getLong("dmnNumericLoopSlot")),
						metadata.getStringArray("dmnNumericLoopFields"), values);
				continue;
			}
			if (metadata.contains("dmnFactorLoopSlots")) {
				columns[index] = SparkSqlFactorLoopInput.project(metadata.getLongArray("dmnFactorLoopSlots"), values);
				continue;
			}
			if (metadata.contains("dmnExactArithmeticDecisionId")) {
				columns[index] = SparkSqlExactArithmeticInput.project(metadata.getLongArray("dmnExactArithmeticSlots"),
						values);
				continue;
			}

			if (metadata.contains("dmnExactProductDecisionId")) {
				columns[index] = SparkSqlExactProductInput.project(model,
						Math.toIntExact(metadata.getLong("dmnExactProductDecisionId")), values);
				continue;
			}

			if (metadata.contains("dmnExactSortDecisionId")) {
				columns[index] = SparkSqlExactSortInput.project(model,
						Math.toIntExact(metadata.getLong("dmnExactSortDecisionId")), values);
				continue;
			}

			if (metadata.contains("dmnIndexedJoinDecisionId")) {
				columns[index] = SparkSqlIndexedJoinInput.project(model,
						Math.toIntExact(metadata.getLong("dmnIndexedJoinDecisionId")), values);
				continue;
			}

			if (metadata.contains("dmnTemporalTableDecisionId")) {
				columns[index] = SparkSqlTemporalTableInput.project(model,
						Math.toIntExact(metadata.getLong("dmnTemporalTableDecisionId")), values);
				continue;
			}
			if (metadata.contains("dmnNumericFilterDecisionId")) {
				columns[index] = SparkSqlNumericFilterInput.project(model,
						Math.toIntExact(metadata.getLong("dmnNumericFilterDecisionId")), values);
				continue;
			}
			if (metadata.contains("dmnListPositionSlot")) {
				int slot = Math.toIntExact(metadata.getLong("dmnListPositionSlot"));
				Object number = io.finmsg.dmn.runtime.DmnRuntime.coerce(values.get(slot),
						RuntimeType.scalar(RuntimeTypeKind.NUMBER));
				try {
					columns[index] = RowFactory.create(
							number == null ? null : new java.math.BigDecimal(number.toString()).intValue(), false);
				} catch (RuntimeException invalid) {
					columns[index] = RowFactory.create(null, true);
				}
				continue;
			}
			if (metadata.contains("dmnLookupKeyInputSlot")) {
				int slot = Math.toIntExact(metadata.getLong("dmnLookupKeyInputSlot"));
				int inputIndex = java.util.stream.IntStream.range(0, model.inputs().size())
						.filter(i -> model.inputs().get(i).valueSlot() == slot).findFirst().orElseThrow();
				Object key = fromSpark(original.get(inputIndex));
				columns[index] = key == null ? null : String.valueOf(key);
				continue;
			}
			if (metadata.contains("dmnStringInputSlot")) {
				int slot = Math.toIntExact(metadata.getLong("dmnStringInputSlot"));
				if (!values.containsKey(slot))
					throw new IllegalArgumentException("Missing input slot " + slot);
				Object value = values.get(slot);
				columns[index] = value instanceof String ? value : null;
				continue;
			}
			if (!metadata.contains("dmnCalendarInputSlot"))
				throw new IllegalArgumentException("Unknown input projection");
			int slot = Math.toIntExact(metadata.getLong("dmnCalendarInputSlot"));
			if (!values.containsKey(slot))
				throw new IllegalArgumentException("Missing input slot " + slot);
			Object value = values.get(slot);
			RuntimeType projectedType = model.inputs().stream().filter(input -> input.valueSlot() == slot)
					.map(RuntimeInput::type).findFirst().orElseThrow();
			value = io.finmsg.dmn.runtime.DmnRuntime.coerce(value, projectedType);
			LocalDate date = value instanceof LocalDate local
					? local
					: value instanceof java.time.LocalDateTime local
							? local.toLocalDate()
							: value instanceof java.time.OffsetDateTime offset
									? offset.toLocalDate()
									: value instanceof java.time.ZonedDateTime zoned
											? zoned.toLocalDate()
											: value instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime named
													? named.value().toLocalDate()
													: null;
			boolean parsed = false;
			if (date == null && value != null) {
				try {
					date = LocalDate.parse(String.valueOf(value));
					parsed = true;
				} catch (java.time.DateTimeException ignored) {
					// Match calendar conversion without altering the payload.
				}
			}
			columns[index] = SparkSqlCalendarArithmetic.row(value, date,
					date == null
							? "OTHER"
							: parsed ? "PARSED_DATE" : value instanceof LocalDate ? "DATE" : "DATE_TIME");
		}
		return RowFactory.create(columns);
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
