package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.RuntimeModel;
import io.finmsg.dmn.runtime.DmnRuntime;
import io.finmsg.dmn.runtime.RuntimeContextValue;
import io.finmsg.dmn.runtime.RuntimeRangeValue;
import java.io.*;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Versioned, type-tagged transport for IR and FEEL values; never Java object
 * deserialization.
 */
final class SparkSqlPayloadCodec {
	private static final int MAGIC = 0x444d4e01;
	private SparkSqlPayloadCodec() {
	}

	static byte[] encode(Object value) {
		try {
			var bytes = new ByteArrayOutputStream();
			var out = new DataOutputStream(bytes);
			out.writeInt(MAGIC);
			write(out, value);
			return bytes.toByteArray();
		} catch (IOException | ReflectiveOperationException e) {
			throw new IllegalArgumentException("Cannot encode DMN payload", e);
		}
	}

	static Object decode(byte[] bytes) {
		try {
			var in = new DataInputStream(new ByteArrayInputStream(bytes));
			if (in.readInt() != MAGIC)
				throw new IOException("Unsupported DMN payload version");
			Object value = read(in);
			if (in.available() != 0)
				throw new IOException("Trailing DMN payload data");
			return value;
		} catch (IOException | ReflectiveOperationException e) {
			throw new IllegalArgumentException("Invalid DMN payload", e);
		}
	}

	static void walk(Object value, Consumer<Object> visitor) {
		if (value == null)
			return;
		visitor.accept(value);
		if (value instanceof Optional<?> optional)
			optional.ifPresent(v -> walk(v, visitor));
		else if (value instanceof List<?> list)
			list.forEach(v -> walk(v, visitor));
		else if (value.getClass().isRecord()
				&& value.getClass().getPackageName().equals(RuntimeModel.class.getPackageName())) {
			try {
				for (RecordComponent component : value.getClass().getRecordComponents()) {
					walk(component.getAccessor().invoke(value), visitor);
				}
			} catch (ReflectiveOperationException e) {
				throw new IllegalArgumentException("Cannot inspect Runtime IR", e);
			}
		}
	}

	private static void string(DataOutputStream out, String value) throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		out.writeInt(bytes.length);
		out.write(bytes);
	}

	private static int size(DataInputStream in) throws IOException {
		int size = in.readInt();
		if (size < 0 || size > in.available())
			throw new IOException("Invalid payload length");
		return size;
	}

	private static String string(DataInputStream in) throws IOException {
		return new String(in.readNBytes(size(in)), StandardCharsets.UTF_8);
	}

	private static void write(DataOutputStream out, Object value) throws IOException, ReflectiveOperationException {
		if (value == null) {
			out.writeByte(0);
			return;
		}
		if (value instanceof String s) {
			out.writeByte(1);
			string(out, s);
			return;
		}
		if (value instanceof Boolean b) {
			out.writeByte(2);
			out.writeBoolean(b);
			return;
		}
		if (value instanceof Integer n) {
			out.writeByte(3);
			out.writeInt(n);
			return;
		}
		if (value instanceof BigDecimal n) {
			out.writeByte(4);
			string(out, n.toString());
			return;
		}
		if (value instanceof Optional<?> o) {
			out.writeByte(5);
			out.writeBoolean(o.isPresent());
			if (o.isPresent())
				write(out, o.get());
			return;
		}
		if (value instanceof List<?> list) {
			out.writeByte(6);
			out.writeInt(list.size());
			for (Object item : list)
				write(out, item);
			return;
		}
		// Keep RuntimeContextValue's indexed fields as well as its named fields.
		if (value instanceof Map<?, ?> map && !(value instanceof RuntimeContextValue)) {
			out.writeByte(7);
			out.writeInt(map.size());
			for (var entry : map.entrySet()) {
				if (!(entry.getKey() instanceof String))
					throw new IllegalArgumentException("FEEL context keys must be strings");
				string(out, (String) entry.getKey());
				write(out, entry.getValue());
			}
			return;
		}
		if (value instanceof Enum<?> e && allowed(e.getDeclaringClass())) {
			out.writeByte(8);
			string(out, e.getDeclaringClass().getName());
			string(out, e.name());
			return;
		}
		if (value.getClass().isRecord() && allowed(value.getClass())) {
			out.writeByte(9);
			string(out, value.getClass().getName());
			RecordComponent[] components = value.getClass().getRecordComponents();
			out.writeInt(components.length);
			for (RecordComponent component : components)
				write(out, component.getAccessor().invoke(value));
			return;
		}
		int temporal = value instanceof LocalDate
				? 10
				: value instanceof LocalTime
						? 11
						: value instanceof OffsetTime
								? 12
								: value instanceof LocalDateTime
										? 13
										: value instanceof OffsetDateTime
												? 14
												: value instanceof ZonedDateTime
														? 15
														: value instanceof Duration
																? 16
																: value instanceof Period
																		? 17
																		: value instanceof ZoneId ? 18 : -1;
		if (temporal != -1) {
			out.writeByte(temporal);
			string(out, value.toString());
			return;
		}
		if (value instanceof Number n) {
			out.writeByte(4);
			string(out, new BigDecimal(n.toString()).toString());
			return;
		}
		throw new IllegalArgumentException("Unsupported FEEL transport value: " + value.getClass().getName()
				+ "; function values must remain inside the evaluated decision subgraph");
	}

	private static boolean allowed(Class<?> type) {
		return type.getPackageName().equals(RuntimeModel.class.getPackageName()) || type == RuntimeContextValue.class
				|| type == RuntimeRangeValue.class || type == DmnRuntime.NamedZoneTime.class
				|| type == DmnRuntime.NamedZoneDateTime.class;
	}

	private static Class<?> payloadClass(String name) throws ClassNotFoundException, IOException {
		Class<?> type = Class.forName(name, false, SparkSqlPayloadCodec.class.getClassLoader());
		if (!allowed(type))
			throw new IOException("Disallowed payload type " + name);
		return type;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Object read(DataInputStream in) throws IOException, ReflectiveOperationException {
		return switch (in.readUnsignedByte()) {
			case 0 -> null;
			case 1 -> string(in);
			case 2 -> in.readBoolean();
			case 3 -> in.readInt();
			case 4 -> new BigDecimal(string(in));
			case 5 -> in.readBoolean() ? Optional.of(read(in)) : Optional.empty();
			case 6 -> {
				int n = size(in);
				var list = new ArrayList<Object>(n);
				for (int i = 0; i < n; i++)
					list.add(read(in));
				yield Collections.unmodifiableList(list);
			}
			case 7 -> {
				int n = size(in);
				var map = new LinkedHashMap<String, Object>();
				for (int i = 0; i < n; i++)
					map.put(string(in), read(in));
				yield Collections.unmodifiableMap(map);
			}
			case 8 -> {
				Class<?> type = payloadClass(string(in));
				if (!type.isEnum())
					throw new IOException("Expected enum");
				yield Enum.valueOf((Class) type, string(in));
			}
			case 9 -> {
				Class<?> type = payloadClass(string(in));
				if (!type.isRecord())
					throw new IOException("Expected record");
				RecordComponent[] components = type.getRecordComponents();
				if (in.readInt() != components.length)
					throw new IOException("Incompatible IR record");
				Object[] values = new Object[components.length];
				Class<?>[] types = new Class<?>[components.length];
				for (int i = 0; i < values.length; i++) {
					types[i] = components[i].getType();
					values[i] = read(in);
				}
				yield type.getConstructor(types).newInstance(values);
			}
			case 10 -> LocalDate.parse(string(in));
			case 11 -> LocalTime.parse(string(in));
			case 12 -> OffsetTime.parse(string(in));
			case 13 -> LocalDateTime.parse(string(in));
			case 14 -> OffsetDateTime.parse(string(in));
			case 15 -> ZonedDateTime.parse(string(in));
			case 16 -> Duration.parse(string(in));
			case 17 -> Period.parse(string(in));
			case 18 -> ZoneId.of(string(in));
			default -> throw new IOException("Unknown DMN payload tag");
		};
	}
}
