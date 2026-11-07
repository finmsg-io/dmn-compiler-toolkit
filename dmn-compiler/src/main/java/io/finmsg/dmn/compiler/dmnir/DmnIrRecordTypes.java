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
	private final Map<DmnIrReference, DmnIrCollectionDefinition> collections = new HashMap<>();
	private final Map<DmnIrReference, RuntimeType> layouts = new HashMap<>();

	DmnIrRecordTypes(DmnIrArtifact artifact) {
		for (var model : artifact.getModelsList()) {
			for (var record : model.getRecordDefinitionsList())
				definitions.put(ref(model.getNamespace(), record.getIdentity().getId()), record);
			for (var collection : model.getCollectionDefinitionsList()) {
				var reference = ref(model.getNamespace(), collection.getIdentity().getId());
				collections.put(reference, collection);
				layouts.put(reference, RuntimeType.element(RuntimeTypeKind.LIST,
						type(collection.getElementType(), false, DmnIrReference.getDefaultInstance())));
			}
		}
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
					fields.add(new RuntimeField(fields.size(), field.getName(),
							field.getType().hasCollectionType()
									? collectionType(field.getType().getCollectionType())
									: type(field.getType().getScalarType(), field.getType().hasRecordType(),
											field.getType().getRecordType()),
							field.hasConstraint() ? Optional.of(constraint(field.getConstraint())) : Optional.empty()));
				layouts.put(key, RuntimeType.contextFields(fields));
				pending.remove(key);
				progress = true;
			}
			if (!progress)
				throw new IllegalArgumentException("Unresolved verified record graph.");
		}
	}
	private RuntimeUnaryTests constraint(DmnIrUnaryTests tests) {
		return new RuntimeUnaryTests(tests.getNegated(), tests.getWildcard(), tests
				.getTestsList().stream().map(
						test -> DmnIrRuntimeAdapter.test(test,
								endpoint -> DmnIrRuntimeAdapter.literal(endpoint.getLiteral(),
										type(endpoint.getType(), false, DmnIrReference.getDefaultInstance()))))
				.toList());
	}

	DmnIrCollectionDefinition collection(DmnIrReference reference) {
		return Objects.requireNonNull(collections.get(reference));
	}
	RuntimeType collectionType(DmnIrReference reference) {
		return Objects.requireNonNull(layouts.get(reference));
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
