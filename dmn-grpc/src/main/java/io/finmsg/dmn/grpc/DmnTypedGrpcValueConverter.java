package io.finmsg.dmn.grpc;

import com.google.protobuf.*;
import com.google.protobuf.Descriptors.FieldDescriptor;
import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Exact, null-preserving conversion for generated model-specific protobuf
 * messages.
 */
public final class DmnTypedGrpcValueConverter {
	private DmnTypedGrpcValueConverter() {
	}

	public static boolean has(Message message, String name) {
		return message.hasField(field(message, name));
	}

	public static Object read(Message message, String name, RuntimeType type) {
		FieldDescriptor field = field(message, name);
		if (!message.hasField(field))
			return null;
		return decode((Message) message.getField(field), type);
	}

	private static Object decode(Message wrapper, RuntimeType type) {
		FieldDescriptor state = wrapper.getOneofFieldDescriptor(wrapper.getDescriptorForType().getOneofs().getFirst());
		if (state == null)
			throw new IllegalArgumentException("Typed FEEL value has no state");
		if (state.getName().equals("null_value"))
			return null;
		Object value = wrapper.getField(state);
		if (state.getName().equals("invalid_value"))
			return dynamic((Message) value);
		if (state.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
			Message nested = (Message) value;
			if (nested.getDescriptorForType().getFullName().equals("io.finmsg.dmn.grpc.Value"))
				return dynamic(nested);
			if (type.kind() == RuntimeTypeKind.LIST) {
				var values = new ArrayList<Object>();
				for (Object item : (List<?>) nested.getField(field(nested, "items")))
					values.add(decode((Message) item, type.elementType()));
				return Collections.unmodifiableList(values);
			}
			var values = new LinkedHashMap<String, Object>();
			for (int i = 0; i < type.fieldLayout().size(); i++) {
				RuntimeField member = type.fieldLayout().get(i);
				if (has(nested, "field_" + i))
					values.put(member.name(), read(nested, "field_" + i, member.type()));
			}
			for (Object item : (List<?>) nested.getField(field(nested, "additional_entries"))) {
				Message entry = (Message) item;
				String name = (String) entry.getField(field(entry, "name"));
				if (values.containsKey(name))
					throw new IllegalArgumentException("Duplicate typed context entry " + name);
				values.put(name, dynamic((Message) entry.getField(field(entry, "value"))));
			}
			return Collections.unmodifiableMap(values);
		}
		if (type.kind() == RuntimeTypeKind.NUMBER)
			return new BigDecimal((String) value);
		if (type.kind() == RuntimeTypeKind.BOOLEAN || type.kind() == RuntimeTypeKind.STRING)
			return value;
		return switch (type.kind()) {
			case DATE -> DmnGrpcValueConverter.toJavaObject(Value.newBuilder().setDateValue((String) value).build());
			case TIME -> DmnGrpcValueConverter.toJavaObject(Value.newBuilder().setTimeValue((String) value).build());
			case DATE_TIME ->
				DmnGrpcValueConverter.toJavaObject(Value.newBuilder().setDateTimeValue((String) value).build());
			case DURATION, YEARS_MONTHS_DURATION, DAYS_TIME_DURATION ->
				DmnGrpcValueConverter.toJavaObject(Value.newBuilder().setDurationValue((String) value).build());
			default -> throw new IllegalArgumentException("Unexpected typed scalar " + type.kind());
		};
	}

	public static void write(Message.Builder parent, String name, RuntimeType type, Object value) {
		FieldDescriptor field = field(parent, name);
		Message.Builder wrapper = parent.newBuilderForField(field);
		encode(wrapper, type, value);
		parent.setField(field, wrapper.build());
	}

	private static void encode(Message.Builder wrapper, RuntimeType type, Object value) {
		if (value == null) {
			wrapper.setField(field(wrapper, "null_value"), true);
			return;
		}
		Value generic = DmnGrpcValueConverter.toProtoValue(value);
		boolean matches = switch (type.kind()) {
			case BOOLEAN -> value instanceof Boolean;
			case NUMBER -> value instanceof Number;
			case STRING -> value instanceof String;
			case DATE -> generic.hasDateValue();
			case TIME -> generic.hasTimeValue();
			case DATE_TIME -> generic.hasDateTimeValue();
			case DURATION, YEARS_MONTHS_DURATION, DAYS_TIME_DURATION -> generic.hasDurationValue();
			case LIST -> value instanceof List<?>;
			case CONTEXT -> value instanceof Map<?, ?>;
			default -> true;
		};
		if (!matches) {
			wrapper.setField(field(wrapper, "invalid_value"), generic);
			return;
		}
		FieldDescriptor destination = field(wrapper, "value");
		if (destination.getJavaType() != FieldDescriptor.JavaType.MESSAGE) {
			wrapper.setField(destination,
					type.kind() == RuntimeTypeKind.NUMBER
							? generic.getNumberValue()
							: type.kind() == RuntimeTypeKind.BOOLEAN ? value : value.toString());
			return;
		}
		if (destination.getMessageType().getFullName().equals("io.finmsg.dmn.grpc.Value")) {
			wrapper.setField(destination, generic);
			return;
		}
		Message.Builder nested = wrapper.newBuilderForField(destination);
		if (type.kind() == RuntimeTypeKind.LIST) {
			FieldDescriptor items = field(nested, "items");
			for (Object item : (List<?>) value) {
				Message.Builder element = nested.newBuilderForField(items);
				encode(element, type.elementType(), item);
				nested.addRepeatedField(items, element.build());
			}
		} else {
			Map<?, ?> values = (Map<?, ?>) value;
			Set<String> declared = new HashSet<>();
			for (int i = 0; i < type.fieldLayout().size(); i++) {
				RuntimeField member = type.fieldLayout().get(i);
				declared.add(member.name());
				if (values.containsKey(member.name()))
					write(nested, "field_" + i, member.type(), values.get(member.name()));
			}
			FieldDescriptor additional = field(nested, "additional_entries");
			for (var entry : values.entrySet()) {
				if (declared.contains(entry.getKey()))
					continue;
				Message.Builder extra = nested.newBuilderForField(additional);
				extra.setField(field(extra, "name"), Objects.toString(entry.getKey()));
				extra.setField(field(extra, "value"), DmnGrpcValueConverter.toProtoValue(entry.getValue()));
				nested.addRepeatedField(additional, extra.build());
			}
		}
		wrapper.setField(destination, nested.build());
	}

	private static FieldDescriptor field(MessageOrBuilder message, String name) {
		return Objects.requireNonNull(message.getDescriptorForType().findFieldByName(name), name);
	}
	private static Object dynamic(Message value) {
		try {
			Value parsed = Value.parseFrom(value.toByteArray());
			if (parsed.getValueKindCase() == Value.ValueKindCase.VALUEKIND_NOT_SET)
				throw new IllegalArgumentException("Dynamic FEEL value has no state");
			return DmnGrpcValueConverter.toJavaObject(parsed);
		} catch (InvalidProtocolBufferException failure) {
			throw new IllegalArgumentException("Invalid FEEL value", failure);
		}
	}
}
