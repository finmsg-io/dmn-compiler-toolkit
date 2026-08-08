package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.frontend.xml.exception.XmlException;
import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.FunctionTypeReference;
import io.finmsg.dmn.model.ListTypeReference;
import io.finmsg.dmn.model.NamedTypeReference;
import io.finmsg.dmn.model.TypeReference;

public final class TypeReferenceReader {

	public TypeReference read(String typeRef) {
		return read(typeRef, null);
	}

	public TypeReference read(String typeRef, XmlCursor cursor) {

		if (typeRef == null || typeRef.isBlank()) {
			return TypeReference.getDefaultInstance();
		}

		String type = typeRef.trim();

		// remove namespace prefix (feel:string -> string)
		int idx = type.indexOf(':');
		String namespace = "";
		if (idx >= 0) {
			String prefix = type.substring(0, idx);
			if (cursor != null) {
				namespace = cursor.namespaceUri(prefix)
						.orElseThrow(() -> new XmlException("Unknown namespace prefix '" + prefix + "' in typeRef."));
			}
			type = type.substring(idx + 1);
			if (!namespace.isEmpty() && !namespace.endsWith("/FEEL/")) {
				return TypeReference.newBuilder()
						.setNamed(NamedTypeReference.newBuilder().setName(type).setNamespace(namespace)).build();
			}
		}

		TypeReference.Builder builder = TypeReference.newBuilder();

		switch (type) {

			case "Any" :
			case "any" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_ANY);
				break;

			case "string" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_STRING);
				break;

			case "number" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_NUMBER);
				break;

			case "boolean" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_BOOLEAN);
				break;

			case "date" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_DATE);
				break;

			case "time" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_TIME);
				break;

			case "dateTime" :
			case "date and time" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_DATE_AND_TIME);
				break;

			case "duration" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_DURATION);
				break;

			case "years and months duration" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION);
				break;

			case "days and time duration" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_DAYS_AND_TIME_DURATION);
				break;

			case "range" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_RANGE);
				break;

			case "null" :
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_NULL);
				break;

			case "list" :
				builder.setList(ListTypeReference.newBuilder()
						.setElementType(TypeReference.newBuilder().setBuiltin(BuiltinType.BUILTIN_TYPE_ANY).build())
						.build());
				break;

			case "function" :
				builder.setFunction(FunctionTypeReference.newBuilder()
						.setReturnType(TypeReference.newBuilder().setBuiltin(BuiltinType.BUILTIN_TYPE_ANY).build())
						.build());
				break;

			case "context" :
				// anonymous context type
				// resolved later by semantic analysis
				builder.setBuiltin(BuiltinType.BUILTIN_TYPE_ANY);
				break;

			default :
				// DMN itemDefinition reference
				builder.setNamed(NamedTypeReference.newBuilder().setName(type).setNamespace(namespace).build());
				break;
		}

		return builder.build();
	}
}
