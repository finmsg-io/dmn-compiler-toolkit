package io.finmsg.dmn.compiler.dmnir;

import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Consumer-local, memoized layouts derived from already verified named
 * declarations.
 */
final class DmnIrRecordTypes {
	private final Map<DmnIrReference, DmnIrRecordDefinition> definitions = new LinkedHashMap<>();
	private final Map<DmnIrReference, RuntimeType> layouts = new HashMap<>();

	DmnIrRecordTypes(DmnIrArtifact artifact) {
		for (var model : artifact.getModelsList())
			for (var record : model.getRecordDefinitionsList())
				definitions.put(ref(model.getNamespace(), record.getIdentity().getId()), record);
		Set<DmnIrReference> pending = new LinkedHashSet<>(definitions.keySet());
		while (!pending.isEmpty()) {
			boolean progress = false;
			for (var key : new ArrayList<>(pending)) {
				var record = definitions.get(key);
				if (record.getFieldsList().stream().anyMatch(
						f -> f.getType().hasRecordType() && !layouts.containsKey(f.getType().getRecordType())))
					continue;
				List<RuntimeField> fields = new ArrayList<>();
				for (var field : record.getFieldsList())
					fields.add(new RuntimeField(fields.size(), field.getName(), type(field.getType().getScalarType(),
							field.getType().hasRecordType(), field.getType().getRecordType())));
				layouts.put(key, RuntimeType.contextFields(fields));
				pending.remove(key);
				progress = true;
			}
			if (!progress)
				throw new IllegalArgumentException("Unresolved verified record graph.");
		}
	}
	DmnIrRecordDefinition definition(DmnIrReference reference) {
		return Objects.requireNonNull(definitions.get(reference));
	}
	RuntimeType type(DmnIrScalarType scalar, boolean hasRecord, DmnIrReference reference) {
		if (hasRecord)
			return Objects.requireNonNull(layouts.get(reference));
		return RuntimeType.scalar(switch (scalar) {
			case SCALAR_TYPE_NULL -> RuntimeTypeKind.NULL;
			case SCALAR_TYPE_BOOLEAN -> RuntimeTypeKind.BOOLEAN;
			case SCALAR_TYPE_NUMBER -> RuntimeTypeKind.NUMBER;
			case SCALAR_TYPE_STRING -> RuntimeTypeKind.STRING;
			case SCALAR_TYPE_DATE -> RuntimeTypeKind.DATE;
			default -> throw new IllegalArgumentException("Unsupported verified scalar type.");
		});
	}
	static DmnIrReference ref(String namespace, String id) {
		return DmnIrReference.newBuilder().setModelNamespace(namespace).setElementId(id).build();
	}
}
