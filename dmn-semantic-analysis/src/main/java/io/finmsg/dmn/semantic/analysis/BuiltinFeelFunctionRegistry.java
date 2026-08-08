package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.ListTypeReference;
import io.finmsg.dmn.model.TypeReference;
import java.util.List;
import java.util.Map;

public final class BuiltinFeelFunctionRegistry implements FeelFunctionRegistry {

	private static final TypeReference ANY = type(BuiltinType.BUILTIN_TYPE_ANY);
	private static final TypeReference BOOLEAN = type(BuiltinType.BUILTIN_TYPE_BOOLEAN);
	private static final TypeReference NUMBER = type(BuiltinType.BUILTIN_TYPE_NUMBER);
	private static final TypeReference STRING = type(BuiltinType.BUILTIN_TYPE_STRING);
	private static final TypeReference DATE = type(BuiltinType.BUILTIN_TYPE_DATE);
	private static final TypeReference TIME = type(BuiltinType.BUILTIN_TYPE_TIME);
	private static final TypeReference DATE_TIME = type(BuiltinType.BUILTIN_TYPE_DATE_AND_TIME);
	private static final TypeReference DURATION = type(BuiltinType.BUILTIN_TYPE_DURATION);
	private static final TypeReference LIST_ANY = list(ANY);
	private static final TypeReference LIST_NUMBER = list(NUMBER);
	private static final TypeReference YEARS_MONTHS_DURATION = type(BuiltinType.BUILTIN_TYPE_YEARS_AND_MONTHS_DURATION);

	private final Map<String, List<FeelFunctionSignature>> signatures = Map.ofEntries(
			entry("not", signature("not", BOOLEAN, BOOLEAN)), entry("string", signature("string", STRING, ANY)),
			entry("number",
					List.of(signature("number", NUMBER, STRING), signature("number", NUMBER, STRING, STRING, STRING))),
			entry("date", List.of(signature("date", DATE, STRING), signature("date", DATE, NUMBER, NUMBER, NUMBER))),
			entry("time", signature("time", TIME, STRING)),
			entry("date and time", signature("date and time", DATE_TIME, STRING)),
			entry("duration", signature("duration", DURATION, STRING)),
			entry("years and months duration",
					signature("years and months duration", YEARS_MONTHS_DURATION, DATE_TIME, DATE_TIME)),
			entry("count", signature("count", NUMBER, LIST_ANY)), entry("sum", signature("sum", NUMBER, LIST_NUMBER)),
			entry("min", signature("min", ANY, LIST_ANY)), entry("max", signature("max", ANY, LIST_ANY)),
			entry("abs", signature("abs", NUMBER, NUMBER)),
			entry("substring",
					List.of(signature("substring", STRING, STRING, NUMBER),
							signature("substring", STRING, STRING, NUMBER, NUMBER))),
			entry("substring before", signature("substring before", STRING, STRING, STRING)),
			entry("substring after", signature("substring after", STRING, STRING, STRING)),
			entry("string length", signature("string length", NUMBER, STRING)),
			entry("upper case", signature("upper case", STRING, STRING)),
			entry("lower case", signature("lower case", STRING, STRING)),
			entry("contains", signature("contains", BOOLEAN, STRING, STRING)),
			entry("starts with", signature("starts with", BOOLEAN, STRING, STRING)),
			entry("ends with", signature("ends with", BOOLEAN, STRING, STRING)),
			entry("matches", signature("matches", BOOLEAN, STRING, STRING)),
			entry("replace", signature("replace", STRING, STRING, STRING, STRING)),
			entry("split", signature("split", list(STRING), STRING, STRING)),
			entry("floor", signature("floor", NUMBER, NUMBER)), entry("ceiling", signature("ceiling", NUMBER, NUMBER)),
			entry("decimal", signature("decimal", NUMBER, NUMBER, NUMBER)),
			entry("round half up", signature("round half up", NUMBER, NUMBER, NUMBER)),
			entry("round half even", signature("round half even", NUMBER, NUMBER, NUMBER)),
			entry("sublist",
					List.of(signature("sublist", LIST_ANY, LIST_ANY, NUMBER),
							signature("sublist", LIST_ANY, LIST_ANY, NUMBER, NUMBER))),
			entry("concatenate", signature("concatenate", LIST_ANY, LIST_ANY)),
			entry("distinct values", signature("distinct values", LIST_ANY, LIST_ANY)),
			entry("flatten", signature("flatten", LIST_ANY, LIST_ANY)),
			entry("reverse", signature("reverse", LIST_ANY, LIST_ANY)),
			entry("index of", signature("index of", list(NUMBER), LIST_ANY, ANY)));

	@Override
	public List<FeelFunctionSignature> find(String name) {
		return signatures.getOrDefault(name, List.of());
	}

	private static Map.Entry<String, List<FeelFunctionSignature>> entry(String name, FeelFunctionSignature signature) {
		return Map.entry(name, List.of(signature));
	}

	private static Map.Entry<String, List<FeelFunctionSignature>> entry(String name,
			List<FeelFunctionSignature> signatures) {
		return Map.entry(name, signatures);
	}

	private static FeelFunctionSignature signature(String name, TypeReference result, TypeReference... parameters) {
		return new FeelFunctionSignature(name, List.of(parameters), result, false);
	}

	private static TypeReference type(BuiltinType type) {
		return TypeReference.newBuilder().setBuiltin(type).build();
	}

	private static TypeReference list(TypeReference element) {
		return TypeReference.newBuilder().setList(ListTypeReference.newBuilder().setElementType(element)).build();
	}
}
