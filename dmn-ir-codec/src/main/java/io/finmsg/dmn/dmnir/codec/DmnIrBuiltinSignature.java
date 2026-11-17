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
		return idx >= 0 ? idx : null;
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
