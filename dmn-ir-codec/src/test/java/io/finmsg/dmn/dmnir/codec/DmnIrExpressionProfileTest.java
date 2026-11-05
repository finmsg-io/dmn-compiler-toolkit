package io.finmsg.dmn.dmnir.codec;

import static org.junit.jupiter.api.Assertions.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DmnIrExpressionProfileTest {
	private static final DmnIrScalarType N = DmnIrScalarType.SCALAR_TYPE_NUMBER;
	private static final DmnIrScalarType B = DmnIrScalarType.SCALAR_TYPE_BOOLEAN;

	@Test
	void goldenUnaryBytesPinNodeAndOperatorNumbers() throws Exception {
		var expression = DmnIrExpression.newBuilder().setType(N).setUnary(DmnIrUnaryExpression.newBuilder()
				.setOperator(DmnIrUnaryOperator.UNARY_OPERATOR_NEGATE).setOperand(number())).build();
		assertEquals("0803220f0802120b0803120722050a01311000", HexFormat.of().formatHex(expression.toByteArray()));
		assertEquals(expression,
				DmnIrExpression.parseFrom(HexFormat.of().parseHex("0803220f0802120b0803120722050a01311000")));
		assertEquals(List.of(1, 2, 3),
				List.of(DmnIrUnaryOperator.UNARY_OPERATOR_POSITIVE.getNumber(),
						DmnIrUnaryOperator.UNARY_OPERATOR_NEGATE.getNumber(),
						DmnIrUnaryOperator.UNARY_OPERATOR_NOT.getNumber()));
		assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13),
				List.of(DmnIrBinaryOperator.BINARY_OPERATOR_ADD.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_SUBTRACT.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_MULTIPLY.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_DIVIDE.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_POWER.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_NOT_EQUAL.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_LESS.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_LESS_EQUAL.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_GREATER.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_GREATER_EQUAL.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_AND.getNumber(),
						DmnIrBinaryOperator.BINARY_OPERATOR_OR.getNumber()));
	}

	@Test
	void acceptsNewProfileAndRejectsVersionCapabilityMixesAndOldHeaderNodes() {
		var artifact = artifact(add(number(), number()));
		assertEquals(artifact, new DmnIrArtifactReader().read(artifact.toByteArray()).artifact());
		rejects(artifact.toBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(0))
				.setSemanticsVersion(DmnIrArtifactReader.SEMANTICS_VERSION).clearRequiredCapabilities()
				.addRequiredCapabilities(DmnIrArtifactReader.SCALAR_CAPABILITY).build(),
				DmnIrValidationException.Code.UNSUPPORTED_CAPABILITY);
		rejects(artifact.toBuilder().setSemanticsVersion(DmnIrArtifactReader.SEMANTICS_VERSION).build(),
				DmnIrValidationException.Code.UNSUPPORTED_VERSION);
		rejects(artifact.toBuilder().clearRequiredCapabilities().addRequiredCapabilities("dmnir.scalar").build(),
				DmnIrValidationException.Code.INVALID_HEADER);
		rejects(artifact.toBuilder().clearRequiredCapabilities().addRequiredCapabilities("dmnir.scalar-expressions")
				.addRequiredCapabilities("dmnir.scalar").build(), DmnIrValidationException.Code.INVALID_HEADER);
	}

	@Test
	void rejectsMissingChildrenUnknownOperatorsAndInvalidSignatures() {
		for (int operator : List.of(0, 999)) {
			var expression = add(number(), number()).toBuilder();
			expression.getBinaryBuilder().setOperatorValue(operator);
			rejects(artifact(expression.build()), DmnIrValidationException.Code.INVALID_TYPE);
			var unary = DmnIrExpression.newBuilder().setType(N)
					.setUnary(DmnIrUnaryExpression.newBuilder().setOperatorValue(operator).setOperand(number()))
					.build();
			rejects(artifact(unary), DmnIrValidationException.Code.INVALID_TYPE);
		}
		var missing = add(number(), number()).toBuilder();
		missing.getBinaryBuilder().clearLeft();
		rejects(artifact(missing.build()), DmnIrValidationException.Code.INVALID_EXPRESSION);
		rejects(artifact(DmnIrExpression.newBuilder().setType(N)
				.setUnary(DmnIrUnaryExpression.newBuilder().setOperator(DmnIrUnaryOperator.UNARY_OPERATOR_NEGATE))
				.build()), DmnIrValidationException.Code.INVALID_EXPRESSION);
		rejects(artifact(add(bool(), number())), DmnIrValidationException.Code.INVALID_TYPE);
		var conditional = DmnIrExpression.newBuilder().setType(N)
				.setConditional(
						DmnIrConditionalExpression.newBuilder().setCondition(bool()).setThenExpression(number()))
				.build();
		rejects(artifact(conditional), DmnIrValidationException.Code.INVALID_EXPRESSION);
		conditional = conditional.toBuilder()
				.setConditional(conditional.getConditional().toBuilder().setElseExpression(bool())).build();
		rejects(artifact(conditional), DmnIrValidationException.Code.INVALID_TYPE);
	}

	@Test
	void validatesReferencesInNestedAndUnreachableBranches() {
		var unresolved = DmnIrExpression.newBuilder().setType(N).setGlobalReference(ref("missing")).build();
		var conditional = DmnIrExpression.newBuilder().setType(N).setConditional(DmnIrConditionalExpression.newBuilder()
				.setCondition(bool()).setThenExpression(number()).setElseExpression(unresolved)).build();
		rejects(artifact(conditional), DmnIrValidationException.Code.INVALID_REFERENCE);
		var self = add(number(), DmnIrExpression.newBuilder().setType(N).setGlobalReference(ref("result")).build());
		rejects(artifact(self), DmnIrValidationException.Code.INVALID_DEPENDENCY);
	}

	@Test
	void wireDepthAndReferenceBudgetsApplyInsideExpressionTrees() {
		var expression = number();
		for (int i = 0; i < 40; i++)
			expression = add(number(), expression);
		rejects(artifact(expression), DmnIrValidationException.Code.LIMIT_EXCEEDED);
		var shallow = artifact(add(number(), number()));
		var d = DmnIrLimits.defaults();
		var limited = new DmnIrArtifactReader(
				new DmnIrLimits(d.maxBytes(), 7, d.maxMessages(), d.maxFields(), d.maxStringBytes(), d.maxModels(),
						d.maxElements(), d.maxReferences(), d.maxDecimalDigits(), d.maxAbsScale()));
		assertDoesNotThrow(() -> limited.read(artifact(number()).toByteArray()));
		assertEquals(DmnIrValidationException.Code.LIMIT_EXCEEDED,
				assertThrows(DmnIrValidationException.class, () -> limited.read(shallow.toByteArray())).code());
		var reference = DmnIrExpression.newBuilder().setType(N).setGlobalReference(ref("input")).build();
		var refs = artifact(add(reference, add(reference, reference))).toBuilder();
		refs.getModelsBuilder(0)
				.addInputs(DmnIrInput.newBuilder()
						.setIdentity(DmnIrElementIdentity.newBuilder().setId("input").setName("Input"))
						.setVariable(DmnIrVariable.newBuilder().setName("Input").setType(N)));
		refs.getModelsBuilder(0).getDecisionsBuilder(0).addDependencies(ref("input"));
		byte[] bytes = refs.build().toByteArray();
		var four = new DmnIrArtifactReader(new DmnIrLimits(d.maxBytes(), d.maxDepth(), d.maxMessages(), d.maxFields(),
				d.maxStringBytes(), d.maxModels(), d.maxElements(), 4, d.maxDecimalDigits(), d.maxAbsScale()));
		var five = new DmnIrArtifactReader(new DmnIrLimits(d.maxBytes(), d.maxDepth(), d.maxMessages(), d.maxFields(),
				d.maxStringBytes(), d.maxModels(), d.maxElements(), 5, d.maxDecimalDigits(), d.maxAbsScale()));
		assertEquals(DmnIrValidationException.Code.LIMIT_EXCEEDED,
				assertThrows(DmnIrValidationException.class, () -> four.read(bytes)).code());
		assertDoesNotThrow(() -> five.read(bytes));
	}

	private static void rejects(DmnIrArtifact value, DmnIrValidationException.Code code) {
		assertEquals(code,
				assertThrows(DmnIrValidationException.class, () -> new DmnIrArtifactReader().read(value.toByteArray()))
						.code());
	}
	private static DmnIrReference ref(String id) {
		return DmnIrReference.newBuilder().setModelNamespace("urn:example:expr").setElementId(id).build();
	}
	private static DmnIrExpression number() {
		return DmnIrExpression.newBuilder().setType(N).setLiteral(
				DmnIrLiteral.newBuilder().setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("1").setScale(0)))
				.build();
	}
	private static DmnIrExpression bool() {
		return DmnIrExpression.newBuilder().setType(B).setLiteral(DmnIrLiteral.newBuilder().setBooleanValue(true))
				.build();
	}
	private static DmnIrExpression add(DmnIrExpression left, DmnIrExpression right) {
		return DmnIrExpression.newBuilder().setType(N).setBinary(DmnIrBinaryExpression.newBuilder()
				.setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_ADD).setLeft(left).setRight(right)).build();
	}
	private static DmnIrArtifact artifact(DmnIrExpression expression) {
		return DmnIrArtifact.newBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(1))
				.setSemanticsVersion(DmnIrArtifactReader.EXPRESSION_SEMANTICS_VERSION)
				.addRequiredCapabilities("dmnir.scalar").addRequiredCapabilities("dmnir.scalar-expressions")
				.setRootModelNamespace("urn:example:expr")
				.addModels(DmnIrModel.newBuilder().setNamespace("urn:example:expr")
						.setIdentity(DmnIrElementIdentity.newBuilder().setId("model").setName("Model"))
						.addDecisions(DmnIrDecision.newBuilder()
								.setIdentity(DmnIrElementIdentity.newBuilder().setId("result").setName("Result"))
								.setVariable(DmnIrVariable.newBuilder().setName("Result").setType(expression.getType()))
								.setExpression(expression)))
				.addEvaluationOrder(ref("result")).build();
	}
}
