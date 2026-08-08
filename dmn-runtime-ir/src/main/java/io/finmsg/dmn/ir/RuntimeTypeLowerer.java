package io.finmsg.dmn.ir;

import io.finmsg.dmn.model.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural DMN/FEEL type lowering shared by all Runtime IR lowering paths.
 */
final class RuntimeTypeLowerer {
	private RuntimeTypeLowerer() {
	}

	static RuntimeType lower(TypeReference type, Map<String, ItemDefinition> items) {
		return lower(type, items, new HashSet<>());
	}

	private static RuntimeType lower(TypeReference type, Map<String, ItemDefinition> items, Set<String> resolving) {
		return switch (type.getKindCase()) {
			case BUILTIN -> RuntimeType.scalar(builtinKind(type.getBuiltin()));
			case LIST ->
				RuntimeType.element(RuntimeTypeKind.LIST, lower(type.getList().getElementType(), items, resolving));
			case RANGE ->
				RuntimeType.element(RuntimeTypeKind.RANGE, lower(type.getRange().getElementType(), items, resolving));
			case CONTEXT -> lowerContext(type.getContext(), items, resolving);
			case FUNCTION -> RuntimeType.function(
					type.getFunction().getParameterTypeList().stream()
							.map(parameter -> lower(parameter, items, new HashSet<>(resolving))).toList(),
					lower(type.getFunction().getReturnType(), items, new HashSet<>(resolving)));
			case NAMED -> lowerNamed(type.getNamed(), items, resolving);
			case KIND_NOT_SET -> RuntimeType.scalar(RuntimeTypeKind.ANY);
		};
	}

	static RuntimeType lower(FeelType type, Map<String, ItemDefinition> items) {
		return switch (type.getTypeCase()) {
			case QUALIFIED_NAME -> switch (type.getQualifiedName()) {
				case "any" -> RuntimeType.scalar(RuntimeTypeKind.ANY);
				case "null" -> RuntimeType.scalar(RuntimeTypeKind.NULL);
				case "boolean" -> RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
				case "number" -> RuntimeType.scalar(RuntimeTypeKind.NUMBER);
				case "string" -> RuntimeType.scalar(RuntimeTypeKind.STRING);
				case "date" -> RuntimeType.scalar(RuntimeTypeKind.DATE);
				case "time" -> RuntimeType.scalar(RuntimeTypeKind.TIME);
				case "date and time" -> RuntimeType.scalar(RuntimeTypeKind.DATE_TIME);
				case "duration" -> RuntimeType.scalar(RuntimeTypeKind.DURATION);
				case "years and months duration" -> RuntimeType.scalar(RuntimeTypeKind.YEARS_MONTHS_DURATION);
				case "days and time duration" -> RuntimeType.scalar(RuntimeTypeKind.DAYS_TIME_DURATION);
				case "range" -> RuntimeType.element(RuntimeTypeKind.RANGE, RuntimeType.scalar(RuntimeTypeKind.ANY));
				default -> lowerNamed(NamedTypeReference.newBuilder().setName(type.getQualifiedName()).build(), items,
						new HashSet<>());
			};
			case RANGE -> RuntimeType.element(RuntimeTypeKind.RANGE, lower(type.getRange().getElementType(), items));
			case LIST -> RuntimeType.element(RuntimeTypeKind.LIST, lower(type.getList().getElementType(), items));
			case CONTEXT -> {
				List<RuntimeField> fields = new ArrayList<>();
				for (int index = 0; index < type.getContext().getEntriesCount(); index++) {
					ContextTypeEntry entry = type.getContext().getEntries(index);
					fields.add(new RuntimeField(index, entry.getName(), lower(entry.getType(), items)));
				}
				yield RuntimeType.contextFields(fields);
			}
			case FUNCTION -> RuntimeType
					.function(
							type.getFunction().getParameterTypesList().stream()
									.map(parameter -> lower(parameter, items)).toList(),
							lower(type.getFunction().getReturnType(), items));
			case TYPE_NOT_SET -> RuntimeType.scalar(RuntimeTypeKind.ANY);
		};
	}

	static List<RuntimeField> relationFields(List<RuntimeRelationColumn> columns) {
		List<RuntimeField> fields = new ArrayList<>();
		for (int index = 0; index < columns.size(); index++) {
			RuntimeRelationColumn column = columns.get(index);
			fields.add(new RuntimeField(index, column.name(), column.type()));
		}
		return List.copyOf(fields);
	}

	static int resolvedFieldIndex(RuntimeType source, String member) {
		RuntimeType candidate = source.kind() == RuntimeTypeKind.LIST ? source.elementType() : source;
		if (candidate == null || candidate.kind() != RuntimeTypeKind.CONTEXT) {
			return -1;
		}
		return candidate.fieldLayout().stream().filter(field -> field.name().equals(member)).map(RuntimeField::index)
				.findFirst().orElse(-1);
	}

	private static RuntimeType lowerContext(ContextTypeReference context, Map<String, ItemDefinition> items,
			Set<String> resolving) {
		List<RuntimeField> fields = new ArrayList<>();
		for (int index = 0; index < context.getEntriesCount(); index++) {
			ContextEntryTypeReference entry = context.getEntries(index);
			fields.add(
					new RuntimeField(index, entry.getName(), lower(entry.getType(), items, new HashSet<>(resolving))));
		}
		return RuntimeType.contextFields(fields);
	}

	private static RuntimeType lowerNamed(NamedTypeReference named, Map<String, ItemDefinition> items,
			Set<String> resolving) {
		String key = named.getNamespace().isBlank() ? named.getName() : named.getNamespace() + "#" + named.getName();
		ItemDefinition item = items.get(key);
		if (item == null || !resolving.add(key)) {
			throw new RuntimeIrLoweringException("Unresolved runtime type '" + named.getName() + "'.");
		}
		RuntimeType result;
		if (item.getComponentsCount() > 0) {
			List<RuntimeField> fields = new ArrayList<>();
			for (int index = 0; index < item.getComponentsCount(); index++) {
				ItemComponent component = item.getComponents(index);
				RuntimeType field = lower(component.getType(), items, new HashSet<>(resolving));
				if (component.getIsCollection()) {
					field = RuntimeType.element(RuntimeTypeKind.LIST, field);
				}
				fields.add(new RuntimeField(index, component.getNode().getName(), field));
			}
			result = RuntimeType.contextFields(fields);
		} else {
			result = lower(item.getType(), items, resolving);
		}
		return item.getIsCollection() ? RuntimeType.element(RuntimeTypeKind.LIST, result) : result;
	}

	private static RuntimeTypeKind builtinKind(BuiltinType type) {
		return switch (type) {
			case BUILTIN_TYPE_NULL -> RuntimeTypeKind.NULL;
			case BUILTIN_TYPE_BOOLEAN -> RuntimeTypeKind.BOOLEAN;
			case BUILTIN_TYPE_NUMBER -> RuntimeTypeKind.NUMBER;
			case BUILTIN_TYPE_STRING -> RuntimeTypeKind.STRING;
			case BUILTIN_TYPE_DATE -> RuntimeTypeKind.DATE;
			case BUILTIN_TYPE_TIME -> RuntimeTypeKind.TIME;
			case BUILTIN_TYPE_DATE_AND_TIME -> RuntimeTypeKind.DATE_TIME;
			case BUILTIN_TYPE_DURATION -> RuntimeTypeKind.DURATION;
			case BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION -> RuntimeTypeKind.YEARS_MONTHS_DURATION;
			case BUILTIN_TYPE_DAYS_AND_TIME_DURATION -> RuntimeTypeKind.DAYS_TIME_DURATION;
			case BUILTIN_TYPE_RANGE -> RuntimeTypeKind.RANGE;
			case BUILTIN_TYPE_ANY, BUILTIN_TYPE_UNSPECIFIED, UNRECOGNIZED -> RuntimeTypeKind.ANY;
		};
	}
}
