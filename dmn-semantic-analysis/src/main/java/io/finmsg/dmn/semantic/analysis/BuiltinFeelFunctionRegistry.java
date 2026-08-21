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

	private static final TypeReference DAYS_TIME_DURATION = type(BuiltinType.BUILTIN_TYPE_DAYS_AND_TIME_DURATION);

	private static final Map<String, List<FeelFunctionSignature>> SIGNATURES = Map.ofEntries(
			entry("not", signature("not", BOOLEAN, BOOLEAN)),
			entry("is",
					List.of(signature("is", BOOLEAN, ANY), signature("is", BOOLEAN, ANY, ANY),
							signature("is", BOOLEAN, ANY, ANY, ANY))),
			entry("string", signature("string", STRING, ANY)),
			entry("number",
					List.of(signature("number", NUMBER, STRING), signature("number", NUMBER, STRING, STRING, STRING))),
			entry("date", List.of(signature("date", DATE, ANY), signature("date", DATE, NUMBER, NUMBER, NUMBER))),
			entry("time", List.of(signature("time", TIME, ANY), signature("time", TIME, NUMBER, NUMBER, NUMBER),
					signature("time", TIME, NUMBER, NUMBER, NUMBER, ANY))),
			entry("date and time", List.of(signature("date and time", DATE_TIME, ANY),
					signature("date and time", DATE_TIME, ANY, ANY))),
			entry("duration", signature("duration", DURATION, ANY)),
			entry("years and months duration",
					List.of(signature("years and months duration", YEARS_MONTHS_DURATION, ANY),
							signature("years and months duration", YEARS_MONTHS_DURATION, ANY, ANY))),
			entry("day and time duration",
					List.of(signature("day and time duration", DURATION, ANY),
							signature("day and time duration", DURATION, ANY, ANY))),
			entry("count", signature("count", NUMBER, LIST_ANY)),
			entry("sum", List.of(signature("sum", NUMBER, LIST_NUMBER),
					new FeelFunctionSignature("sum", List.of(NUMBER), NUMBER, true))),
			entry("min", List.of(signature("min", ANY, LIST_ANY),
					new FeelFunctionSignature("min", List.of(ANY), ANY, true))),
			entry("max", List.of(signature("max", ANY, LIST_ANY),
					new FeelFunctionSignature("max", List.of(ANY), ANY, true))),
			entry("mean", List.of(signature("mean", NUMBER, LIST_NUMBER),
					new FeelFunctionSignature("mean", List.of(NUMBER), NUMBER, true))),
			entry("median", List.of(signature("median", NUMBER, LIST_NUMBER),
					new FeelFunctionSignature("median", List.of(NUMBER), NUMBER, true))),
			entry("mode", List.of(signature("mode", LIST_NUMBER, LIST_NUMBER),
					new FeelFunctionSignature("mode", List.of(NUMBER), LIST_NUMBER, true))),
			entry("stddev", List.of(signature("stddev", NUMBER, LIST_NUMBER),
					new FeelFunctionSignature("stddev", List.of(NUMBER), NUMBER, true))),
			entry("abs", signature("abs", ANY, ANY)),
			entry("sqrt", signature("sqrt", NUMBER, NUMBER)),
			entry("exp", signature("exp", NUMBER, NUMBER)),
			entry("log", signature("log", NUMBER, NUMBER)),
			entry("modulo", signature("modulo", NUMBER, NUMBER, NUMBER)),
			entry("even", signature("even", BOOLEAN, NUMBER)),
			entry("odd", signature("odd", BOOLEAN, NUMBER)),
			entry("product", List.of(signature("product", NUMBER, LIST_NUMBER),
					new FeelFunctionSignature("product", List.of(NUMBER), NUMBER, true))),
			entry("substring",
					List.of(signature("substring", STRING, ANY, NUMBER),
							signature("substring", STRING, ANY, NUMBER, NUMBER))),
			entry("substring before", signature("substring before", STRING, ANY, ANY)),
			entry("substring after", signature("substring after", STRING, ANY, ANY)),
			entry("string length", signature("string length", NUMBER, ANY)),
			entry("upper case", signature("upper case", STRING, ANY)),
			entry("lower case", signature("lower case", STRING, ANY)),
			entry("contains", signature("contains", BOOLEAN, ANY, ANY)),
			entry("starts with", signature("starts with", BOOLEAN, ANY, ANY)),
			entry("ends with", signature("ends with", BOOLEAN, ANY, ANY)),
			entry("matches", List.of(signature("matches", BOOLEAN, ANY, ANY),
					signature("matches", BOOLEAN, ANY, ANY, ANY))),
			entry("replace", List.of(signature("replace", STRING, ANY, ANY, ANY),
					signature("replace", STRING, ANY, ANY, ANY, ANY))),
			entry("split", signature("split", list(STRING), ANY, ANY)),
			entry("floor", List.of(signature("floor", NUMBER, NUMBER), signature("floor", NUMBER, NUMBER, NUMBER))),
			entry("ceiling", List.of(signature("ceiling", NUMBER, NUMBER), signature("ceiling", NUMBER, NUMBER, NUMBER))),
			entry("decimal", signature("decimal", NUMBER, NUMBER, NUMBER)),
			entry("round half up", signature("round half up", NUMBER, NUMBER, NUMBER)),
			entry("round half down", signature("round half down", NUMBER, NUMBER, NUMBER)),
			entry("round half even", signature("round half even", NUMBER, NUMBER, NUMBER)),
			entry("round up", signature("round up", NUMBER, NUMBER, NUMBER)),
			entry("round down", signature("round down", NUMBER, NUMBER, NUMBER)),
			entry("sublist",
					List.of(signature("sublist", LIST_ANY, LIST_ANY, NUMBER),
							signature("sublist", LIST_ANY, LIST_ANY, NUMBER, NUMBER))),
			entry("concatenate",
					List.of(signature("concatenate", LIST_ANY, LIST_ANY, LIST_ANY),
							signature("concatenate", LIST_ANY, LIST_ANY, LIST_ANY, LIST_ANY),
							signature("concatenate", LIST_ANY, LIST_ANY, LIST_ANY, LIST_ANY, LIST_ANY),
							signature("concatenate", LIST_ANY, LIST_ANY))),
			entry("distinct values", signature("distinct values", LIST_ANY, LIST_ANY)),
			entry("flatten", signature("flatten", LIST_ANY, LIST_ANY)),
			entry("reverse", signature("reverse", LIST_ANY, LIST_ANY)),
			entry("index of", signature("index of", list(NUMBER), LIST_ANY, ANY)),
			entry("all", List.of(signature("all", BOOLEAN, list(BOOLEAN)),
					new FeelFunctionSignature("all", List.of(BOOLEAN), BOOLEAN, true))),
			entry("any", List.of(signature("any", BOOLEAN, list(BOOLEAN)),
					new FeelFunctionSignature("any", List.of(BOOLEAN), BOOLEAN, true))),
			entry("insert before", signature("insert before", LIST_ANY, LIST_ANY, NUMBER, ANY)),
			entry("remove", signature("remove", LIST_ANY, LIST_ANY, NUMBER)),
			entry("append", new FeelFunctionSignature("append", List.of(LIST_ANY, ANY), LIST_ANY, true)),
			entry("union", new FeelFunctionSignature("union", List.of(LIST_ANY), LIST_ANY, true)),
			entry("list contains", signature("list contains", BOOLEAN, LIST_ANY, ANY)),
			entry("sort", List.of(signature("sort", LIST_ANY, LIST_ANY), signature("sort", LIST_ANY, LIST_ANY, ANY))),
			entry("string join",
					List.of(signature("string join", STRING, LIST_ANY),
							signature("string join", STRING, LIST_ANY, ANY),
							signature("string join", STRING, LIST_ANY, ANY, ANY))),
			entry("get entries", signature("get entries", LIST_ANY, ANY)),
			entry("get value", signature("get value", ANY, ANY, STRING)),
			entry("range",
					List.of(signature("range", ANY, STRING), signature("range", ANY, ANY, ANY),
							signature("range", ANY, ANY, ANY, BOOLEAN, BOOLEAN))),
			entry("day of year", signature("day of year", NUMBER, ANY)),
			entry("day of week", signature("day of week", STRING, ANY)),
			entry("week of year", signature("week of year", NUMBER, ANY)),
			entry("month of year", signature("month of year", STRING, ANY)),
			entry("context", List.of(signature("context", ANY, LIST_ANY), signature("context", ANY, ANY))),
			entry("context put",
					List.of(signature("context put", ANY, ANY, STRING, ANY),
							signature("context put", ANY, ANY, LIST_ANY, ANY))),
			entry("context merge", new FeelFunctionSignature("context merge", List.of(ANY), ANY, true)),
			entry("list replace",
					List.of(signature("list replace", LIST_ANY, LIST_ANY, NUMBER, ANY),
							signature("list replace", LIST_ANY, LIST_ANY, ANY, ANY))),
			entry("PMT", signature("PMT", NUMBER, NUMBER, NUMBER, NUMBER)),
			entry("PMT2", signature("PMT2", NUMBER, NUMBER, NUMBER, NUMBER)),
			entry("now", signature("now", DATE_TIME)),
			entry("today", signature("today", DATE)),
			entry("during", signature("during", BOOLEAN, ANY, ANY)),
			entry("before", signature("before", BOOLEAN, ANY, ANY)),
			entry("after", signature("after", BOOLEAN, ANY, ANY)),
			entry("meets", signature("meets", BOOLEAN, ANY, ANY)),
			entry("met by", signature("met by", BOOLEAN, ANY, ANY)),
			entry("overlaps", signature("overlaps", BOOLEAN, ANY, ANY)),
			entry("overlaps before", signature("overlaps before", BOOLEAN, ANY, ANY)),
			entry("overlaps after", signature("overlaps after", BOOLEAN, ANY, ANY)),
			entry("finishes", signature("finishes", BOOLEAN, ANY, ANY)),
			entry("finished by", signature("finished by", BOOLEAN, ANY, ANY)),
			entry("includes", signature("includes", BOOLEAN, ANY, ANY)),
			entry("starts", signature("starts", BOOLEAN, ANY, ANY)),
			entry("started by", signature("started by", BOOLEAN, ANY, ANY)),
			entry("coincides", signature("coincides", BOOLEAN, ANY, ANY)));

	public static boolean isBuiltin(String name) {
		return SIGNATURES.containsKey(name);
	}

	@Override
	public List<FeelFunctionSignature> find(String name) {
		return SIGNATURES.getOrDefault(name, List.of());
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
