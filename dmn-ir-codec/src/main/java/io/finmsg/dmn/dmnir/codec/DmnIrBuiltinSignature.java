package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.proto.v0.DmnIrBuiltinOperation.*;
import static io.finmsg.dmn.dmnir.proto.v0.DmnIrScalarType.*;

import io.finmsg.dmn.dmnir.proto.v0.DmnIrBuiltinOperation;
import io.finmsg.dmn.dmnir.proto.v0.DmnIrScalarType;
import java.util.List;
import java.util.Optional;

/** Fixed signatures in the portable built-in call profile. */
public enum DmnIrBuiltinSignature {
	ABS(BUILTIN_OPERATION_ABS_NUMBER, "abs", SCALAR_TYPE_NUMBER, List.of(SCALAR_TYPE_NUMBER)), STRING_LENGTH(
			BUILTIN_OPERATION_STRING_LENGTH, "string length", SCALAR_TYPE_NUMBER,
			List.of(SCALAR_TYPE_STRING)), CONTAINS(BUILTIN_OPERATION_CONTAINS_STRING, "contains", SCALAR_TYPE_BOOLEAN,
					List.of(SCALAR_TYPE_STRING, SCALAR_TYPE_STRING));

	private final DmnIrBuiltinOperation operation;
	private final String feelName;
	private final DmnIrScalarType result;
	private final List<DmnIrScalarType> parameters;

	DmnIrBuiltinSignature(DmnIrBuiltinOperation operation, String feelName, DmnIrScalarType result,
			List<DmnIrScalarType> parameters) {
		this.operation = operation;
		this.feelName = feelName;
		this.result = result;
		this.parameters = parameters;
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

	public static Optional<DmnIrBuiltinSignature> forOperation(DmnIrBuiltinOperation operation) {
		return switch (operation) {
			case BUILTIN_OPERATION_ABS_NUMBER -> Optional.of(ABS);
			case BUILTIN_OPERATION_STRING_LENGTH -> Optional.of(STRING_LENGTH);
			case BUILTIN_OPERATION_CONTAINS_STRING -> Optional.of(CONTAINS);
			default -> Optional.empty();
		};
	}

	public static Optional<DmnIrBuiltinSignature> forFeelName(String name) {
		return switch (name) {
			case "abs" -> Optional.of(ABS);
			case "string length" -> Optional.of(STRING_LENGTH);
			case "contains" -> Optional.of(CONTAINS);
			default -> Optional.empty();
		};
	}
}
