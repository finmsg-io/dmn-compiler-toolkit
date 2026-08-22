package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.FunctionTypeReference;
import io.finmsg.dmn.model.TypeReference;

final class FunctionItemTypeReader {
	private final TypeReferenceReader typeReferenceReader = new TypeReferenceReader();

	TypeReference read(XmlCursor cursor) {
		FunctionTypeReference.Builder function = FunctionTypeReference.newBuilder();
		TypeReference returnType = cursor.hasAttribute("outputTypeRef")
				? typeReferenceReader.read(cursor.requiredAttribute("outputTypeRef"), cursor)
				: anyType();
		function.setReturnType(returnType);
		if (cursor.firstChild()) {
			do {
				if ("parameters".equals(cursor.documentLocalName())) {
					TypeReference parameterType = cursor.hasAttribute("typeRef")
							? typeReferenceReader.read(cursor.requiredAttribute("typeRef"), cursor)
							: anyType();
					function.addParameterType(parameterType);
				}
			} while (cursor.nextSibling());
			cursor.parent();
		}
		return TypeReference.newBuilder().setFunction(function).build();
	}

	private static TypeReference anyType() {
		return TypeReference.newBuilder().setBuiltin(BuiltinType.BUILTIN_TYPE_ANY).build();
	}
}
