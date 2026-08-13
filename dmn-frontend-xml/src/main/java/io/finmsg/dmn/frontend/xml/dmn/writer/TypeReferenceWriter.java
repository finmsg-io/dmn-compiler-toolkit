package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.TypeReference;
import io.finmsg.dmn.frontend.xml.XmlEmitter;

public final class TypeReferenceWriter {

	public String write(TypeReference value) {
		return write(null, value);
	}

	public String write(XmlEmitter xml, TypeReference value) {
		if (value.hasBuiltin()) {
			return writeBuiltin(value.getBuiltin());
		}

		if (value.hasNamed()) {
			if (value.getNamed().getNamespace().isEmpty()) {
				return value.getNamed().getName();
			}
			if (xml == null) {
				throw new XmlWriteException("A namespace-qualified type reference requires an XML emitter.");
			}
			return xml.qualifiedName(value.getNamed().getNamespace(), value.getNamed().getName());
		}

		if (value.equals(TypeReference.getDefaultInstance())) {
			return "";
		}

		throw new XmlWriteException("Type reference cannot be represented as a DMN typeRef.");
	}

	private String writeBuiltin(BuiltinType value) {
		return switch (value) {
			case BUILTIN_TYPE_ANY -> "Any";
			case BUILTIN_TYPE_NUMBER -> "number";
			case BUILTIN_TYPE_STRING -> "string";
			case BUILTIN_TYPE_BOOLEAN -> "boolean";
			case BUILTIN_TYPE_DATE -> "date";
			case BUILTIN_TYPE_TIME -> "time";
			case BUILTIN_TYPE_DATE_AND_TIME -> "date and time";
			case BUILTIN_TYPE_DURATION -> "duration";
			case BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION -> "years and months duration";
			case BUILTIN_TYPE_DAYS_AND_TIME_DURATION -> "days and time duration";
			case BUILTIN_TYPE_RANGE -> "range";
			case BUILTIN_TYPE_NULL -> "null";
			default -> throw new XmlWriteException("Unsupported builtin type: " + value);
		};
	}
}
