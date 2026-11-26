package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;
import static io.finmsg.dmn.dmnir.proto.v0.DmnIrScalarType.*;
import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.dmnir.proto.v0.*;
import org.junit.jupiter.api.Test;

class DmnIrBkmProfileTest {
	private static final DmnIrReference TARGET = DmnIrReference.newBuilder().setModelNamespace("urn:tables")
			.setElementId("bkm").build();

	@Test
	void pinsWireTagsAndRequiresProfile() {
		assertEquals(8, DmnIrModel.getDescriptor().findFieldByName("bkms").getNumber());
		assertEquals(6, DmnIrDecision.getDescriptor().findFieldByName("knowledge_dependencies").getNumber());
		assertEquals(15, DmnIrExpression.getDescriptor().findFieldByName("bkm_call").getNumber());
		assertEquals(1, DmnIrBkmArgument.getDescriptor().findFieldByName("parameter_position").getNumber());
		var artifact = artifact();
		read(artifact);
		var earlier = DmnIrBuiltinProfileTest.artifact().setModels(0, artifact.getModels(0));
		rejects(earlier, UNSUPPORTED_CAPABILITY);
	}

	@Test
	void validatesParametersCallTypesAndDependencies() {
		var artifact = artifact();
		var model = artifact.getModelsBuilder(0);
		var decision = model.getDecisionsBuilder(0);
		var call = decision.getExpressionBuilder().getFilterBuilder().getPredicateBuilder().getBinaryBuilder()
				.getRightBuilder().getBkmCallBuilder();
		var argument = call.getArguments(0);
		call.clearArguments();
		rejects(artifact, INVALID_EXPRESSION);
		call.addArguments(argument.toBuilder().setParameterPosition(0));
		rejects(artifact, INVALID_EXPRESSION);
		call.setArguments(0, argument.toBuilder().setExpression(DmnIrExpression.newBuilder().setType(SCALAR_TYPE_STRING)
				.setLiteral(DmnIrLiteral.newBuilder().setStringValue("wrong"))));
		rejects(artifact, INVALID_TYPE);
		call.setArguments(0, argument);
		decision.clearKnowledgeDependencies();
		rejects(artifact, INVALID_DEPENDENCY);
		decision.addKnowledgeDependencies(TARGET).addKnowledgeDependencies(TARGET);
		rejects(artifact, INVALID_DEPENDENCY);
		var input = TARGET.toBuilder().setElementId("not-a-function");
		model.addInputs(DmnIrInput.newBuilder()
				.setIdentity(DmnIrElementIdentity.newBuilder().setId(input.getElementId()).setName("Input"))
				.setVariable(DmnIrVariable.newBuilder().setName("Input").setType(SCALAR_TYPE_NUMBER)));
		decision.clearKnowledgeDependencies().addKnowledgeDependencies(input);
		rejects(artifact, INVALID_DEPENDENCY);
		decision.clearKnowledgeDependencies().addKnowledgeDependencies(TARGET);
		var bkm = model.getBkmsBuilder(0);
		bkm.getBodyBuilder().getLocalReferenceBuilder().setBindingId("escaped");
		rejects(artifact, INVALID_REFERENCE);
		bkm.getBodyBuilder().getLocalReferenceBuilder().setBindingId("formal");
		call.addArguments(argument);
		bkm.addParameters(parameter("other", "Other"));
		rejects(artifact, INVALID_EXPRESSION);
		call.getArgumentsBuilder(1).setParameterPosition(2);
		read(artifact);
	}

	@Test
	void rejectsCrossModelKnowledgeCyclesAndRequiresDirectImport() {
		var artifact = artifact();
		var first = artifact.getModelsBuilder(0);
		var other = TARGET.toBuilder().setModelNamespace("urn:other").build();
		first.getBkmsBuilder(0).addKnowledgeDependencies(other);
		artifact.addModels(DmnIrModel.newBuilder().setNamespace("urn:other")
				.setIdentity(DmnIrElementIdentity.newBuilder().setId("other").setName("Other"))
				.addBkms(first.getBkms(0).toBuilder().clearKnowledgeDependencies().addKnowledgeDependencies(TARGET))
				.addImports(DmnIrImport.newBuilder().setName("first").setModelNamespace(TARGET.getModelNamespace())));
		rejects(artifact, INVALID_REFERENCE);
		first.addImports(DmnIrImport.newBuilder().setName("other").setModelNamespace("urn:other"));
		rejects(artifact, INVALID_DEPENDENCY);
		artifact.getModelsBuilder(1).getBkmsBuilder(0).clearKnowledgeDependencies();
		read(artifact);
	}

	static DmnIrArtifact.Builder artifact() {
		var artifact = DmnIrBuiltinProfileTest.artifact()
				.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(10))
				.setSemanticsVersion(DmnIrArtifactReader.BKM_SEMANTICS_VERSION)
				.addRequiredCapabilities(DmnIrArtifactReader.BKM_CAPABILITY);
		var model = artifact.getModelsBuilder(0);
		model.addBkms(
				DmnIrBkm.newBuilder().setIdentity(DmnIrElementIdentity.newBuilder().setId("bkm").setName("Identity"))
						.setVariable(DmnIrVariable.newBuilder().setName("Identity").setType(SCALAR_TYPE_NUMBER))
						.addParameters(parameter("formal", "Formal"))
						.setBody(DmnIrExpression.newBuilder().setType(SCALAR_TYPE_NUMBER)
								.setLocalReference(DmnIrLocalReference.newBuilder().setBindingId("formal"))));
		var decision = model.getDecisionsBuilder(0).addKnowledgeDependencies(TARGET);
		var right = decision.getExpressionBuilder().getFilterBuilder().getPredicateBuilder().getBinaryBuilder()
				.getRightBuilder();
		var argument = right.build();
		right.setBkmCall(DmnIrBkmCall.newBuilder().setTarget(TARGET)
				.addArguments(DmnIrBkmArgument.newBuilder().setParameterPosition(1).setExpression(argument)));
		return artifact;
	}

	private static DmnIrBkmParameter parameter(String id, String name) {
		return DmnIrBkmParameter.newBuilder().setBindingId(id)
				.setVariable(DmnIrVariable.newBuilder().setName(name).setType(SCALAR_TYPE_NUMBER)).build();
	}

	private static void read(DmnIrArtifact.Builder artifact) {
		new DmnIrArtifactReader().read(artifact.build().toByteArray());
	}

	private static void rejects(DmnIrArtifact.Builder artifact, DmnIrValidationException.Code code) {
		assertEquals(code, assertThrows(DmnIrValidationException.class, () -> read(artifact)).code());
	}
}
