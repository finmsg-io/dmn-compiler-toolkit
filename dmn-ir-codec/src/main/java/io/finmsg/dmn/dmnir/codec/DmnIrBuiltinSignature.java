package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.proto.v0.DmnIrBuiltinOperation.*;
import static io.finmsg.dmn.dmnir.proto.v0.DmnIrScalarType.*;

import io.finmsg.dmn.dmnir.proto.v0.DmnIrBuiltinOperation;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrScalarType;
import java.util.List;
import java.util.Optional;

/** Fixed signatures in the portable built-in call profile. */
public enum DmnIrBuiltinSignature {
	ABS(BUILTIN_OPERATION_ABS_NUMBER, "abs", SCALAR_TYPE_NUMBER, List.of(SCALAR_TYPE_NUMBER), List.of("n"),
			1), STRING_LENGTH(BUILTIN_OPERATION_STRING_LENGTH, "string length", SCALAR_TYPE_NUMBER,
					List.of(SCALAR_TYPE_STRING), List.of("string"), 1), CONTAINS(BUILTIN_OPERATION_CONTAINS_STRING,
							"contains", SCALAR_TYPE_BOOLEAN, List.of(SCALAR_TYPE_STRING, SCALAR_TYPE_STRING),
							List.of("string", "match"), 2), MODULO(BUILTIN_OPERATION_MODULO, "modulo",
									SCALAR_TYPE_NUMBER, List.of(SCALAR_TYPE_NUMBER, SCALAR_TYPE_NUMBER),
									List.of("dividend", "divisor"), 2), FLOOR(BUILTIN_OPERATION_FLOOR, "floor",
											SCALAR_TYPE_NUMBER, List.of(SCALAR_TYPE_NUMBER, SCALAR_TYPE_NUMBER),
											List.of("n", "scale"), 1), CEILING(BUILTIN_OPERATION_CEILING, "ceiling",
													SCALAR_TYPE_NUMBER, List.of(SCALAR_TYPE_NUMBER, SCALAR_TYPE_NUMBER),
													List.of("n", "scale"), 1), EVEN(BUILTIN_OPERATION_EVEN, "even",
															SCALAR_TYPE_BOOLEAN, List.of(SCALAR_TYPE_NUMBER),
															List.of("number"), 1), ODD(BUILTIN_OPERATION_ODD, "odd",
																	SCALAR_TYPE_BOOLEAN, List.of(SCALAR_TYPE_NUMBER),
																	List.of("number"),
																	1), DECIMAL(BUILTIN_OPERATION_DECIMAL, "decimal",
																			SCALAR_TYPE_NUMBER,
																			List.of(SCALAR_TYPE_NUMBER,
																					SCALAR_TYPE_NUMBER),
																			List.of("n", "scale"),
																			2), ROUND_UP(BUILTIN_OPERATION_ROUND_UP,
																					"round up", SCALAR_TYPE_NUMBER,
																					List.of(SCALAR_TYPE_NUMBER,
																							SCALAR_TYPE_NUMBER),
																					List.of("n", "scale"),
																					2), ROUND_DOWN(
																							BUILTIN_OPERATION_ROUND_DOWN,
																							"round down",
																							SCALAR_TYPE_NUMBER,
																							List.of(SCALAR_TYPE_NUMBER,
																									SCALAR_TYPE_NUMBER),
																							List.of("n", "scale"),
																							2), ROUND_HALF_UP(
																									BUILTIN_OPERATION_ROUND_HALF_UP,
																									"round half up",
																									SCALAR_TYPE_NUMBER,
																									List.of(SCALAR_TYPE_NUMBER,
																											SCALAR_TYPE_NUMBER),
																									List.of("n",
																											"scale"),
																									2), ROUND_HALF_DOWN(
																											BUILTIN_OPERATION_ROUND_HALF_DOWN,
																											"round half down",
																											SCALAR_TYPE_NUMBER,
																											List.of(SCALAR_TYPE_NUMBER,
																													SCALAR_TYPE_NUMBER),
																											List.of("n",
																													"scale"),
																											2), UPPER_CASE(
																													BUILTIN_OPERATION_UPPER_CASE,
																													"upper case",
																													SCALAR_TYPE_STRING,
																													List.of(SCALAR_TYPE_STRING),
																													List.of("string"),
																													1), LOWER_CASE(
																															BUILTIN_OPERATION_LOWER_CASE,
																															"lower case",
																															SCALAR_TYPE_STRING,
																															List.of(SCALAR_TYPE_STRING),
																															List.of("string"),
																															1), SUBSTRING(
																																	BUILTIN_OPERATION_SUBSTRING,
																																	"substring",
																																	SCALAR_TYPE_STRING,
																																	List.of(SCALAR_TYPE_STRING,
																																			SCALAR_TYPE_NUMBER,
																																			SCALAR_TYPE_NUMBER),
																																	List.of("string",
																																			"start position",
																																			"length"),
																																	2), SUBSTRING_BEFORE(
																																			BUILTIN_OPERATION_SUBSTRING_BEFORE,
																																			"substring before",
																																			SCALAR_TYPE_STRING,
																																			List.of(SCALAR_TYPE_STRING,
																																					SCALAR_TYPE_STRING),
																																			List.of("string",
																																					"match"),
																																			2), SUBSTRING_AFTER(
																																					BUILTIN_OPERATION_SUBSTRING_AFTER,
																																					"substring after",
																																					SCALAR_TYPE_STRING,
																																					List.of(SCALAR_TYPE_STRING,
																																							SCALAR_TYPE_STRING),
																																					List.of("string",
																																							"match"),
																																					2), STARTS_WITH(
																																							BUILTIN_OPERATION_STARTS_WITH,
																																							"starts with",
																																							SCALAR_TYPE_BOOLEAN,
																																							List.of(SCALAR_TYPE_STRING,
																																									SCALAR_TYPE_STRING),
																																							List.of("string",
																																									"match"),
																																							2), ENDS_WITH(
																																									BUILTIN_OPERATION_ENDS_WITH,
																																									"ends with",
																																									SCALAR_TYPE_BOOLEAN,
																																									List.of(SCALAR_TYPE_STRING,
																																											SCALAR_TYPE_STRING),
																																									List.of("string",
																																											"match"),
																																									2), MATCHES(
																																											BUILTIN_OPERATION_MATCHES,
																																											"matches",
																																											SCALAR_TYPE_BOOLEAN,
																																											List.of(SCALAR_TYPE_STRING,
																																													SCALAR_TYPE_STRING,
																																													SCALAR_TYPE_STRING),
																																											List.of("input",
																																													"pattern",
																																													"flags"),
																																											2), REPLACE(
																																													BUILTIN_OPERATION_REPLACE,
																																													"replace",
																																													SCALAR_TYPE_STRING,
																																													List.of(SCALAR_TYPE_STRING,
																																															SCALAR_TYPE_STRING,
																																															SCALAR_TYPE_STRING,
																																															SCALAR_TYPE_STRING),
																																													List.of("input",
																																															"pattern",
																																															"replacement",
																																															"flags"),
																																													3), NUMBER_FROM_STRING(
																																															BUILTIN_OPERATION_NUMBER_FROM_STRING,
																																															"number",
																																															SCALAR_TYPE_NUMBER,
																																															List.of(SCALAR_TYPE_STRING,
																																																	SCALAR_TYPE_STRING,
																																																	SCALAR_TYPE_STRING),
																																															List.of("from",
																																																	"grouping separator",
																																																	"decimal separator"),
																																															1), SUM(BUILTIN_OPERATION_SUM_NUMBER_LIST,
																																																	"sum",
																																																	SCALAR_TYPE_NUMBER,
																																																	List.of(SCALAR_TYPE_NUMBER),
																																																	List.of("list"),
																																																	1), MIN(BUILTIN_OPERATION_MIN_NUMBER_LIST,
																																																			"min",
																																																			SCALAR_TYPE_NUMBER,
																																																			List.of(SCALAR_TYPE_NUMBER),
																																																			List.of("list"),
																																																			1), MAX(BUILTIN_OPERATION_MAX_NUMBER_LIST,
																																																					"max",
																																																					SCALAR_TYPE_NUMBER,
																																																					List.of(SCALAR_TYPE_NUMBER),
																																																					List.of("list"),
																																																					1), MEAN(
																																																							BUILTIN_OPERATION_MEAN_NUMBER_LIST,
																																																							"mean",
																																																							SCALAR_TYPE_NUMBER,
																																																							List.of(SCALAR_TYPE_NUMBER),
																																																							List.of("list"),
																																																							1), MEDIAN(
																																																									BUILTIN_OPERATION_MEDIAN_NUMBER_LIST,
																																																									"median",
																																																									SCALAR_TYPE_NUMBER,
																																																									List.of(SCALAR_TYPE_NUMBER),
																																																									List.of("list"),
																																																									1), ALL(BUILTIN_OPERATION_ALL_BOOLEAN_LIST,
																																																											"all",
																																																											SCALAR_TYPE_BOOLEAN,
																																																											List.of(SCALAR_TYPE_BOOLEAN),
																																																											List.of("list"),
																																																											1), ANY(BUILTIN_OPERATION_ANY_BOOLEAN_LIST,
																																																													"any",
																																																													SCALAR_TYPE_BOOLEAN,
																																																													List.of(SCALAR_TYPE_BOOLEAN),
																																																													List.of("list"),
																																																													1), SQRT(
																																																															BUILTIN_OPERATION_SQRT,
																																																															"sqrt",
																																																															SCALAR_TYPE_NUMBER,
																																																															List.of(SCALAR_TYPE_NUMBER),
																																																															List.of("number"),
																																																															1), PRODUCT(
																																																																	BUILTIN_OPERATION_PRODUCT_NUMBER_LIST,
																																																																	"product",
																																																																	SCALAR_TYPE_NUMBER,
																																																																	List.of(SCALAR_TYPE_NUMBER),
																																																																	List.of("list"),
																																																																	1), EXP(BUILTIN_OPERATION_EXP,
																																																																			"exp",
																																																																			SCALAR_TYPE_NUMBER,
																																																																			List.of(SCALAR_TYPE_NUMBER),
																																																																			List.of("number"),
																																																																			1), LOG(BUILTIN_OPERATION_LOG,
																																																																					"log",
																																																																					SCALAR_TYPE_NUMBER,
																																																																					List.of(SCALAR_TYPE_NUMBER),
																																																																					List.of("number"),
																																																																					1), STRING_JOIN(
																																																																							BUILTIN_OPERATION_STRING_JOIN,
																																																																							"string join",
																																																																							SCALAR_TYPE_STRING,
																																																																							List.of(SCALAR_TYPE_STRING,
																																																																									SCALAR_TYPE_STRING),
																																																																							List.of("list",
																																																																									"delimiter"),
																																																																							1), SPLIT(
																																																																									BUILTIN_OPERATION_SPLIT,
																																																																									"split",
																																																																									SCALAR_TYPE_STRING,
																																																																									List.of(SCALAR_TYPE_STRING,
																																																																											SCALAR_TYPE_STRING),
																																																																									List.of("string",
																																																																											"delimiter"),
																																																																									2), STRING_FROM(
																																																																											BUILTIN_OPERATION_STRING_FROM,
																																																																											"string",
																																																																											SCALAR_TYPE_STRING,
																																																																											List.of(SCALAR_TYPE_STRING),
																																																																											List.of("from"),
																																																																											1), SUBLIST(
																																																																													BUILTIN_OPERATION_SUBLIST,
																																																																													"sublist",
																																																																													SCALAR_TYPE_NULL,
																																																																													List.of(SCALAR_TYPE_NULL,
																																																																															SCALAR_TYPE_NUMBER,
																																																																															SCALAR_TYPE_NUMBER),
																																																																													List.of("list",
																																																																															"start position",
																																																																															"length"),
																																																																													2), APPEND(
																																																																															BUILTIN_OPERATION_APPEND,
																																																																															"append",
																																																																															SCALAR_TYPE_NULL,
																																																																															List.of(SCALAR_TYPE_NULL,
																																																																																	SCALAR_TYPE_NULL),
																																																																															List.of("list",
																																																																																	"item"),
																																																																															2), CONCATENATE(
																																																																																	BUILTIN_OPERATION_CONCATENATE,
																																																																																	"concatenate",
																																																																																	SCALAR_TYPE_NULL,
																																																																																	List.of(SCALAR_TYPE_NULL,
																																																																																			SCALAR_TYPE_NULL),
																																																																																	List.of("list1",
																																																																																			"list2"),
																																																																																	2), INSERT_BEFORE(
																																																																																			BUILTIN_OPERATION_INSERT_BEFORE,
																																																																																			"insert before",
																																																																																			SCALAR_TYPE_NULL,
																																																																																			List.of(SCALAR_TYPE_NULL,
																																																																																					SCALAR_TYPE_NUMBER,
																																																																																					SCALAR_TYPE_NULL),
																																																																																			List.of("list",
																																																																																					"position",
																																																																																					"newItem"),
																																																																																			3), REMOVE(
																																																																																					BUILTIN_OPERATION_REMOVE,
																																																																																					"remove",
																																																																																					SCALAR_TYPE_NULL,
																																																																																					List.of(SCALAR_TYPE_NULL,
																																																																																							SCALAR_TYPE_NUMBER),
																																																																																					List.of("list",
																																																																																							"position"),
																																																																																					2), REVERSE(
																																																																																							BUILTIN_OPERATION_REVERSE,
																																																																																							"reverse",
																																																																																							SCALAR_TYPE_NULL,
																																																																																							List.of(SCALAR_TYPE_NULL),
																																																																																							List.of("list"),
																																																																																							1), INDEX_OF(
																																																																																									BUILTIN_OPERATION_INDEX_OF,
																																																																																									"index of",
																																																																																									SCALAR_TYPE_NUMBER,
																																																																																									List.of(SCALAR_TYPE_NULL,
																																																																																											SCALAR_TYPE_NULL),
																																																																																									List.of("list",
																																																																																											"match"),
																																																																																									2), DISTINCT_VALUES(
																																																																																											BUILTIN_OPERATION_DISTINCT_VALUES,
																																																																																											"distinct values",
																																																																																											SCALAR_TYPE_NULL,
																																																																																											List.of(SCALAR_TYPE_NULL),
																																																																																											List.of("list"),
																																																																																											1), FLATTEN(
																																																																																													BUILTIN_OPERATION_FLATTEN,
																																																																																													"flatten",
																																																																																													SCALAR_TYPE_NULL,
																																																																																													List.of(SCALAR_TYPE_NULL),
																																																																																													List.of("list"),
																																																																																													1), UNION(
																																																																																															BUILTIN_OPERATION_UNION,
																																																																																															"union",
																																																																																															SCALAR_TYPE_NULL,
																																																																																															List.of(SCALAR_TYPE_NULL,
																																																																																																	SCALAR_TYPE_NULL),
																																																																																															List.of("list1",
																																																																																																	"list2"),
																																																																																															2), LIST_CONTAINS(
																																																																																																	BUILTIN_OPERATION_LIST_CONTAINS,
																																																																																																	"list contains",
																																																																																																	SCALAR_TYPE_BOOLEAN,
																																																																																																	List.of(SCALAR_TYPE_NULL,
																																																																																																			SCALAR_TYPE_NULL),
																																																																																																	List.of("list",
																																																																																																			"element"),
																																																																																																	2), COUNT(
																																																																																																			BUILTIN_OPERATION_COUNT,
																																																																																																			"count",
																																																																																																			SCALAR_TYPE_NUMBER,
																																																																																																			List.of(SCALAR_TYPE_NULL),
																																																																																																			List.of("list"),
																																																																																																			1), MODE(
																																																																																																					BUILTIN_OPERATION_MODE,
																																																																																																					"mode",
																																																																																																					SCALAR_TYPE_NULL,
																																																																																																					List.of(SCALAR_TYPE_NULL),
																																																																																																					List.of("list"),
																																																																																																					1), GET_VALUE(
																																																																																																							BUILTIN_OPERATION_GET_VALUE,
																																																																																																							"get value",
																																																																																																							SCALAR_TYPE_NULL,
																																																																																																							List.of(SCALAR_TYPE_NULL,
																																																																																																									SCALAR_TYPE_STRING),
																																																																																																							List.of("m",
																																																																																																									"key"),
																																																																																																							2), CONTEXT_PUT(
																																																																																																									BUILTIN_OPERATION_CONTEXT_PUT,
																																																																																																									"context put",
																																																																																																									SCALAR_TYPE_NULL,
																																																																																																									List.of(SCALAR_TYPE_NULL,
																																																																																																											SCALAR_TYPE_NULL,
																																																																																																											SCALAR_TYPE_NULL),
																																																																																																									List.of("context",
																																																																																																											"key",
																																																																																																											"value"),
																																																																																																									3), CONTEXT_MERGE(
																																																																																																											BUILTIN_OPERATION_CONTEXT_MERGE,
																																																																																																											"context merge",
																																																																																																											SCALAR_TYPE_NULL,
																																																																																																											List.of(SCALAR_TYPE_NULL),
																																																																																																											List.of("contexts"),
																																																																																																											1), LIST_REPLACE(
																																																																																																													BUILTIN_OPERATION_LIST_REPLACE,
																																																																																																													"list replace",
																																																																																																													SCALAR_TYPE_NULL,
																																																																																																													List.of(SCALAR_TYPE_NULL,
																																																																																																															SCALAR_TYPE_NUMBER,
																																																																																																															SCALAR_TYPE_NULL),
																																																																																																													List.of("list",
																																																																																																															"position",
																																																																																																															"newItem"),
																																																																																																													3), RANGE(
																																																																																																															BUILTIN_OPERATION_RANGE,
																																																																																																															"range",
																																																																																																															SCALAR_TYPE_NULL,
																																																																																																															List.of(SCALAR_TYPE_STRING),
																																																																																																															List.of("from"),
																																																																																																															1), SORT(
																																																																																																																	BUILTIN_OPERATION_SORT,
																																																																																																																	"sort",
																																																																																																																	SCALAR_TYPE_NULL,
																																																																																																																	List.of(SCALAR_TYPE_NULL,
																																																																																																																			SCALAR_TYPE_NULL),
																																																																																																																	List.of("list",
																																																																																																																			"precedes"),
																																																																																																																	1);

	private final DmnIrBuiltinOperation operation;
	private final String feelName;
	private final DmnIrScalarType result;
	private final List<DmnIrScalarType> parameters;
	private final List<String> parameterNames;
	private final int minArity;

	DmnIrBuiltinSignature(DmnIrBuiltinOperation operation, String feelName, DmnIrScalarType result,
			List<DmnIrScalarType> parameters, List<String> parameterNames, int minArity) {
		this.operation = operation;
		this.feelName = feelName;
		this.result = result;
		this.parameters = parameters;
		this.parameterNames = parameterNames;
		this.minArity = minArity;
	}

	/**
	 * Returns true if the parameter at {@code parameterIndex} is a collection
	 * parameter where the parameter scalar denotes the collection element type.
	 */
	public boolean collectionParameter(int parameterIndex) {
		return switch (this) {
			case SUM, MIN, MAX, MEAN, MEDIAN, ALL, ANY, PRODUCT -> parameterIndex == 0;
			case STRING_JOIN, SUBLIST, APPEND, INSERT_BEFORE, REMOVE, REVERSE, DISTINCT_VALUES, FLATTEN, LIST_CONTAINS,
					COUNT, MODE, SORT, LIST_REPLACE, CONTEXT_MERGE ->
				parameterIndex == 0;
			case CONCATENATE, UNION -> parameterIndex == 0 || parameterIndex == 1;
			case INDEX_OF -> parameterIndex == 0;
			default -> false;
		};
	}

	public boolean collectionParameter() {
		return collectionParameter(0);
	}

	public boolean collectionResult() {
		return switch (this) {
			case SPLIT, SUBLIST, APPEND, CONCATENATE, INSERT_BEFORE, REMOVE, REVERSE, DISTINCT_VALUES, FLATTEN, UNION,
					MODE, SORT, LIST_REPLACE ->
				true;
			default -> false;
		};
	}

	public DmnIrBuiltinOperation operation() {
		return operation;
	}

	public String feelName() {
		return feelName;
	}

	public DmnIrScalarType result() {
		return result;
	}

	public List<DmnIrScalarType> parameters() {
		return parameters;
	}

	public List<String> parameterNames() {
		return parameterNames;
	}

	public int minArity() {
		return minArity;
	}

	public int maxArity() {
		return parameters.size();
	}

	public Integer parameterIndex(String name) {
		int idx = parameterNames.indexOf(name);
		if (idx >= 0)
			return idx;
		if (this == LIST_REPLACE && "match".equals(name))
			return 1;
		return null;
	}

	public static Optional<DmnIrBuiltinSignature> forOperation(DmnIrBuiltinOperation operation) {
		for (var sig : values()) {
			if (sig.operation == operation)
				return Optional.of(sig);
		}
		return Optional.empty();
	}

	public static Optional<DmnIrBuiltinSignature> forFeelName(String name) {
		for (var sig : values()) {
			if (sig.feelName.equals(name))
				return Optional.of(sig);
		}
		return Optional.empty();
	}
}
