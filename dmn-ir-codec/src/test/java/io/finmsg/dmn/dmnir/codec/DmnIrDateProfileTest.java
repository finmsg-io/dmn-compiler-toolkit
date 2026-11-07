package io.finmsg.dmn.dmnir.codec;

import static org.junit.jupiter.api.Assertions.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DmnIrDateProfileTest {
	private static final DmnIrScalarType DATE = DmnIrScalarType.SCALAR_TYPE_DATE;
	@Test
	void validatesGregorianDatesAndPinsWireIdentity() {
		for (int year : List.of(-999999999, -400, -1, 1, 1900, 2000, 2024, 9999, 10000, 999999999))
			read(artifact(literal(date(year, 1, 1))));

		read(artifact(literal(date(-400, 2, 29))));
		read(artifact(literal(date(2000, 2, 29))));
		assertEquals(5, DATE.getNumber());
		assertEquals(5, DmnIrLiteral.getDescriptor().findFieldByName("date_value").getNumber());
		assertEquals("08d01f1002181d", HexFormat.of().formatHex(date(2024, 2, 29).toByteArray()));
		assertTrue(date(0, 1, 1).hasYear());
	}
	@Test
	void rejectsMissingComponentsInvalidDaysAndOutOfRangeYears() {
		var good = date(2024, 2, 29);
		for (var invalid : List.of(good.toBuilder().clearYear().build(), good.toBuilder().clearMonth().build(),
				good.toBuilder().clearDay().build(), date(0, 1, 1), date(1900, 2, 29), date(2023, 2, 29),
				date(2024, 2, 30), date(2024, 0, 1), date(2024, 13, 1), date(2024, 1, 0), date(2024, -1, 1),
				date(Integer.MIN_VALUE, 1, 1), date(1000000000, 1, 1)))
			rejects(artifact(literal(invalid)), DmnIrValidationException.Code.INVALID_EXPRESSION);
	}
	@Test
	void requiresExactProfileAndLiteralTypeAgreement() {
		var value = artifact(literal(date(2024, 1, 1)));
		var old = value.toBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(3))
				.setSemanticsVersion(DmnIrArtifactReader.LOCAL_SEMANTICS_VERSION).clearRequiredCapabilities()
				.addAllRequiredCapabilities(
						List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records", "dmnir.local-bindings"))
				.build();
		rejects(old, DmnIrValidationException.Code.INVALID_TYPE);
		rejects(value.toBuilder().clearRequiredCapabilities()
				.addAllRequiredCapabilities(
						List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records", "dmnir.local-bindings"))
				.build(), DmnIrValidationException.Code.INVALID_HEADER);
		rejects(artifact(literal(date(2024, 1, 1)).toBuilder().setType(DmnIrScalarType.SCALAR_TYPE_STRING).build()),
				DmnIrValidationException.Code.INVALID_TYPE);
	}
	@Test
	void admitsDateOrderingButRejectsMixedOrderingAndArithmetic() {
		var a = literal(date(2024, 1, 1));
		var b = literal(date(2024, 2, 1));
		var ordered = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
				.setBinary(DmnIrBinaryExpression.newBuilder().setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_LESS)
						.setLeft(a).setRight(b))
				.build();
		read(artifact(ordered));
		var mixed = ordered.toBuilder();
		mixed.getBinaryBuilder().setRight(DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_STRING)
				.setLiteral(DmnIrLiteral.newBuilder().setStringValue("2024-02-01")));
		rejects(artifact(mixed.build()), DmnIrValidationException.Code.INVALID_TYPE);
		var add = ordered.toBuilder().setType(DATE);
		add.getBinaryBuilder().setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_ADD);
		rejects(artifact(add.build()), DmnIrValidationException.Code.INVALID_TYPE);
	}
	@Test
	void validatesUnusedDateRecordFieldsUnderTheSameProfileGate() {
		var number = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER).setLiteral(
				DmnIrLiteral.newBuilder().setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("1").setScale(0)))
				.build();
		var value = artifact(number).toBuilder();
		value.getModelsBuilder(0)
				.addRecordDefinitions(DmnIrRecordDefinition.newBuilder()
						.setIdentity(DmnIrElementIdentity.newBuilder().setId("unused").setName("Unused"))
						.addFields(DmnIrRecordField.newBuilder().setName("Date")
								.setType(DmnIrTypeReference.newBuilder().setScalarType(DATE))));
		read(value.build());
		value.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(3))
				.setSemanticsVersion(DmnIrArtifactReader.LOCAL_SEMANTICS_VERSION).clearRequiredCapabilities()
				.addAllRequiredCapabilities(
						List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records", "dmnir.local-bindings"));
		rejects(value.build(), DmnIrValidationException.Code.INVALID_TYPE);
	}
	private static DmnIrDate date(int y, int m, int d) {
		return DmnIrDate.newBuilder().setYear(y).setMonth(m).setDay(d).build();
	}
	private static DmnIrExpression literal(DmnIrDate date) {
		return DmnIrExpression.newBuilder().setType(DATE).setLiteral(DmnIrLiteral.newBuilder().setDateValue(date))
				.build();
	}
	private static DmnIrArtifact artifact(DmnIrExpression expression) {
		return DmnIrArtifact.newBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(4))
				.setSemanticsVersion(DmnIrArtifactReader.DATE_SEMANTICS_VERSION)
				.addAllRequiredCapabilities(List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records",
						"dmnir.local-bindings", "dmnir.date"))
				.setRootModelNamespace("urn:dates")
				.addModels(DmnIrModel.newBuilder().setNamespace("urn:dates")
						.setIdentity(DmnIrElementIdentity.newBuilder().setId("model").setName("Dates"))
						.addDecisions(DmnIrDecision.newBuilder()
								.setIdentity(DmnIrElementIdentity.newBuilder().setId("answer").setName("Answer"))
								.setVariable(DmnIrVariable.newBuilder().setName("Answer").setType(expression.getType()))
								.setExpression(expression)))
				.addEvaluationOrder(DmnIrReference.newBuilder().setModelNamespace("urn:dates").setElementId("answer"))
				.build();
	}
	private static void read(DmnIrArtifact value) {
		new DmnIrArtifactReader().read(value.toByteArray());
	}
	private static void rejects(DmnIrArtifact value, DmnIrValidationException.Code code) {
		assertEquals(code, assertThrows(DmnIrValidationException.class, () -> read(value)).code());
	}
}
