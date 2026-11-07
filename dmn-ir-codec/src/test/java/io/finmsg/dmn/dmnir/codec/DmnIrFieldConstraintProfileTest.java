package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;
import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.dmnir.proto.v0.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class DmnIrFieldConstraintProfileTest {
	private static final DmnIrScalarType NUMBER = DmnIrScalarType.SCALAR_TYPE_NUMBER;
	private static final DmnIrExpression ONE = DmnIrExpression.newBuilder().setType(NUMBER).setLiteral(
			DmnIrLiteral.newBuilder().setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("1").setScale(0)))
			.build();

	@Test
	void pinsFieldConstraintTagAndPreservesOlderProfiles() {
		assertEquals(4, DmnIrRecordField.getDescriptor().findFieldByName("constraint").getNumber());
		var artifact = artifact().build();
		assertEquals(artifact, new DmnIrArtifactReader().read(artifact.toByteArray()).artifact());
		var old = DmnIrTableProfileTest.artifact();
		addRecord(old, field());
		rejects(old.build(), UNSUPPORTED_CAPABILITY);
		old.getModelsBuilder(0).getRecordDefinitionsBuilder(0).getFieldsBuilder(0).clearConstraint();
		assertEquals(old.build(), new DmnIrArtifactReader().read(old.build().toByteArray()).artifact());
	}

	@Test
	void rejectsEmptyDynamicAndIncompatibleDomains() {
		rejectField(field -> field.getConstraintBuilder().clearTests(), INVALID_EXPRESSION);
		rejectField(
				field -> comparison(field).setEndpoint(ONE.toBuilder().setGlobalReference(
						DmnIrReference.newBuilder().setModelNamespace("urn:tables").setElementId("answer"))),
				INVALID_EXPRESSION);
		rejectField(field -> field.getTypeBuilder().setScalarType(DmnIrScalarType.SCALAR_TYPE_STRING), INVALID_TYPE);
		rejectField(
				field -> comparison(field).setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_GREATER)
						.setEndpoint(DmnIrExpression.newBuilder().setType(NUMBER)
								.setLiteral(DmnIrLiteral.newBuilder().setNullValue(DmnIrNull.NULL_VALUE))),
				INVALID_TYPE);
		rejectField(field -> field.getConstraintBuilder().getTestsBuilder(0)
				.setRange(DmnIrTestRange.newBuilder().setLower(ONE)), INVALID_TYPE);
	}

	private static DmnIrTestComparison.Builder comparison(DmnIrRecordField.Builder field) {
		return field.getConstraintBuilder().getTestsBuilder(0).getComparisonBuilder();
	}

	private static void rejectField(Consumer<DmnIrRecordField.Builder> change, DmnIrValidationException.Code code) {
		var artifact = artifact();
		change.accept(artifact.getModelsBuilder(0).getRecordDefinitionsBuilder(0).getFieldsBuilder(0));
		rejects(artifact.build(), code);
	}

	private static void rejects(DmnIrArtifact artifact, DmnIrValidationException.Code code) {
		assertEquals(code, assertThrows(DmnIrValidationException.class,
				() -> new DmnIrArtifactReader().read(artifact.toByteArray())).code());
	}

	static DmnIrArtifact.Builder artifact() {
		var artifact = DmnIrTableProfileTest.artifact()
				.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(6))
				.setSemanticsVersion("finmsg-dmnir-field-constraints/0")
				.addRequiredCapabilities("dmnir.field-constraints");
		addRecord(artifact, field());
		return artifact;
	}

	private static void addRecord(DmnIrArtifact.Builder artifact, DmnIrRecordField field) {
		artifact.getModelsBuilder(0).addRecordDefinitions(DmnIrRecordDefinition.newBuilder()
				.setIdentity(DmnIrElementIdentity.newBuilder().setId("record").setName("Record")).addFields(field));
	}

	private static DmnIrRecordField field() {
		var field = DmnIrRecordField.newBuilder().setName("Value")
				.setType(DmnIrTypeReference.newBuilder().setScalarType(NUMBER))
				.setConstraint(DmnIrUnaryTests.newBuilder()
						.addTests(DmnIrUnaryTest.newBuilder().setComparison(DmnIrTestComparison.newBuilder()
								.setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL).setEndpoint(ONE))));
		return field.build();
	}
}
