package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;
import static io.finmsg.dmn.dmnir.proto.v0.DmnIrScalarType.SCALAR_TYPE_NUMBER;
import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.dmnir.proto.v0.*;
import org.junit.jupiter.api.Test;

class DmnIrClosureProfileTest {
	@Test
	void pinsWireTagsAndRequiresProfile() {
		assertEquals(16, DmnIrExpression.getDescriptor().findFieldByName("closure_invocation").getNumber());
		assertEquals(4, DmnIrLocalBinding.getDescriptor().findFieldByName("closure").getNumber());
		assertEquals(3, DmnIrClosure.getDescriptor().findFieldByName("captured_binding_ids").getNumber());
		var artifact = artifact();
		read(artifact);
		var earlier = DmnIrBkmProfileTest.artifact();
		read(earlier);
		earlier.addEvaluationOrder(artifact.getEvaluationOrder(1)).setModels(0, artifact.getModels(0));
		rejects(earlier, UNSUPPORTED_CAPABILITY);
	}

	@Test
	void validatesClosureScopesAndPositionalArguments() {
		var artifact = artifact();
		var context = artifact.getModelsBuilder(0).getDecisionsBuilder(1).getExpressionBuilder().getContextBuilder();
		var definition = context.getBindingsBuilder(1).getClosureBuilder().getDefinitionBuilder();
		var body = definition.getBody();
		definition.clearBody();
		rejects(artifact, INVALID_EXPRESSION);
		definition.setBody(body).clearCapturedBindingIds();
		rejects(artifact, INVALID_REFERENCE);
		definition.addCapturedBindingIds("prior").addCapturedBindingIds("prior");
		rejects(artifact, INVALID_REFERENCE);
		definition.clearCapturedBindingIds().addCapturedBindingIds("prior");
		definition.setBody(local("formal"));
		rejects(artifact, INVALID_REFERENCE);
		definition.setBody(body);
		var call = context.getResultBuilder().getClosureInvocationBuilder();
		call.setLocalBindingId("prior");
		rejects(artifact, INVALID_REFERENCE);
		call.setLocalBindingId("function").clearArguments();
		rejects(artifact, INVALID_EXPRESSION);
		call.addArguments(DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
				.setLiteral(DmnIrLiteral.newBuilder().setBooleanValue(true)));
		rejects(artifact, INVALID_TYPE);
		call.setArguments(0, number());
		read(artifact);
		var inline = definition.build();
		context.removeBindings(1);
		call.setInline(inline);
		read(artifact);
	}

	static DmnIrArtifact.Builder artifact() {
		var artifact = DmnIrBkmProfileTest.artifact()
				.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(11))
				.setSemanticsVersion(DmnIrArtifactReader.CLOSURE_SEMANTICS_VERSION)
				.addRequiredCapabilities(DmnIrArtifactReader.CLOSURE_CAPABILITY);
		var model = artifact.getModelsBuilder(0);
		var resultType = DmnIrTypeReference.newBuilder().setScalarType(SCALAR_TYPE_NUMBER);
		var definition = DmnIrClosure.newBuilder().addCapturedBindingIds("prior").setResultType(resultType)
				.addParameters(DmnIrBkmParameter.newBuilder().setBindingId("formal").setVariable(variable("Formal")))
				.setBody(DmnIrExpression.newBuilder().setType(SCALAR_TYPE_NUMBER).setBinary(
						DmnIrBinaryExpression.newBuilder().setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_ADD)
								.setLeft(local("prior")).setRight(local("formal"))));
		var context = DmnIrContextExpression.newBuilder()
				.addBindings(DmnIrLocalBinding.newBuilder().setBindingId("prior").setVariable(variable("Prior"))
						.setValue(number()))
				.addBindings(DmnIrLocalBinding.newBuilder().setBindingId("function")
						.setClosure(DmnIrClosureBinding.newBuilder().setName("Function").setDefinition(definition)))
				.setResult(DmnIrExpression.newBuilder().setType(SCALAR_TYPE_NUMBER).setClosureInvocation(
						DmnIrClosureInvocation.newBuilder().setLocalBindingId("function").addArguments(number())));
		model.addDecisions(DmnIrDecision.newBuilder()
				.setIdentity(DmnIrElementIdentity.newBuilder().setId("closure-result").setName("Closure Result"))
				.setVariable(variable("Closure Result"))
				.setExpression(DmnIrExpression.newBuilder().setType(SCALAR_TYPE_NUMBER).setContext(context)));
		return artifact.addEvaluationOrder(
				DmnIrReference.newBuilder().setModelNamespace(model.getNamespace()).setElementId("closure-result"));
	}

	private static DmnIrVariable.Builder variable(String name) {
		return DmnIrVariable.newBuilder().setName(name).setType(SCALAR_TYPE_NUMBER);
	}

	private static DmnIrExpression.Builder local(String id) {
		return DmnIrExpression.newBuilder().setType(SCALAR_TYPE_NUMBER)
				.setLocalReference(DmnIrLocalReference.newBuilder().setBindingId(id));
	}

	private static DmnIrExpression.Builder number() {
		return DmnIrExpression.newBuilder().setType(SCALAR_TYPE_NUMBER).setLiteral(
				DmnIrLiteral.newBuilder().setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("1").setScale(0)));
	}

	private static void read(DmnIrArtifact.Builder artifact) {
		new DmnIrArtifactReader().read(artifact.build().toByteArray());
	}

	private static void rejects(DmnIrArtifact.Builder artifact, DmnIrValidationException.Code code) {
		assertEquals(code, assertThrows(DmnIrValidationException.class, () -> read(artifact)).code());
	}
}
