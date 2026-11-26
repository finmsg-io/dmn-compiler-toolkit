package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;
import static io.finmsg.dmn.dmnir.proto.v0.DmnIrBuiltinOperation.*;
import static io.finmsg.dmn.dmnir.proto.v0.DmnIrScalarType.*;
import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.dmnir.proto.v0.*;
import org.junit.jupiter.api.Test;

class DmnIrBuiltinProfileTest {
	@Test
	void pinsFieldsAndOperationsAndRequiresProfile09() {
		assertEquals(14, DmnIrExpression.getDescriptor().findFieldByName("builtin_call").getNumber());
		assertEquals(1, DmnIrBuiltinCall.getDescriptor().findFieldByName("operation").getNumber());
		assertEquals(2, DmnIrBuiltinCall.getDescriptor().findFieldByName("arguments").getNumber());
		assertEquals(1, BUILTIN_OPERATION_ABS_NUMBER.getNumber());
		assertEquals(2, BUILTIN_OPERATION_STRING_LENGTH.getNumber());
		assertEquals(3, BUILTIN_OPERATION_CONTAINS_STRING.getNumber());
		var artifact = artifact();
		read(artifact);
		var earlier = DmnIrFilterProfileTest.artifact();
		read(earlier);
		earlier.setModels(0, artifact.getModels(0));
		rejects(earlier, UNSUPPORTED_CAPABILITY);
	}

	@Test
	void checksArityOperationAndTypesAndValidatesArgumentNodes() {
		var artifact = artifact();
		var right = right(artifact);
		var call = right.getBuiltinCallBuilder();
		var valid = call.build();
		call.clearArguments();
		rejects(artifact, INVALID_EXPRESSION);
		call.mergeFrom(valid).setOperation(BUILTIN_OPERATION_UNSPECIFIED);
		rejects(artifact, INVALID_EXPRESSION);
		call.setOperationValue(99);
		rejects(artifact, INVALID_EXPRESSION);
		call.setOperation(BUILTIN_OPERATION_STRING_LENGTH);
		rejects(artifact, INVALID_TYPE);
		call.setOperation(BUILTIN_OPERATION_ABS_NUMBER);
		right.setType(SCALAR_TYPE_STRING);
		rejects(artifact, INVALID_TYPE);
		right.setType(SCALAR_TYPE_NUMBER);
		call.setArguments(0, DmnIrExpression.newBuilder().setType(SCALAR_TYPE_NUMBER)
				.setLocalReference(DmnIrLocalReference.newBuilder().setBindingId("missing")));
		rejects(artifact, INVALID_REFERENCE);
	}

	static DmnIrArtifact.Builder artifact() {
		var artifact = DmnIrFilterProfileTest.artifact()
				.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(9))
				.setSemanticsVersion(DmnIrArtifactReader.BUILTIN_SEMANTICS_VERSION)
				.addRequiredCapabilities(DmnIrArtifactReader.BUILTIN_CAPABILITY);
		var right = right(artifact);
		var argument = right.build();
		right.setBuiltinCall(
				DmnIrBuiltinCall.newBuilder().setOperation(BUILTIN_OPERATION_ABS_NUMBER).addArguments(argument));
		return artifact;
	}

	private static DmnIrExpression.Builder right(DmnIrArtifact.Builder artifact) {
		return artifact.getModelsBuilder(0).getDecisionsBuilder(0).getExpressionBuilder().getFilterBuilder()
				.getPredicateBuilder().getBinaryBuilder().getRightBuilder();
	}

	private static void read(DmnIrArtifact.Builder artifact) {
		new DmnIrArtifactReader().read(artifact.build().toByteArray());
	}

	private static void rejects(DmnIrArtifact.Builder artifact, DmnIrValidationException.Code code) {
		assertEquals(code, assertThrows(DmnIrValidationException.class, () -> read(artifact)).code());
	}
}
