package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;
import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.dmnir.proto.v0.*;
import org.junit.jupiter.api.Test;

class DmnIrCollectionProfileTest {
	private static final DmnIrReference FEES = DmnIrReference.newBuilder().setModelNamespace("urn:tables")
			.setElementId("fees").build();
	private static final DmnIrExpression ONE = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER)
			.setLiteral(DmnIrLiteral.newBuilder()
					.setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("1").setScale(0)))
			.build();

	@Test
	void pinsTagsAndRetainsPriorProfiles() {
		assertEquals(7, DmnIrModel.getDescriptor().findFieldByName("collection_definitions").getNumber());
		assertEquals(5, DmnIrVariable.getDescriptor().findFieldByName("collection_type").getNumber());
		assertEquals(11, DmnIrExpression.getDescriptor().findFieldByName("collection_type").getNumber());
		assertEquals(12, DmnIrExpression.getDescriptor().findFieldByName("list").getNumber());
		assertEquals(3, DmnIrTypeReference.getDescriptor().findFieldByName("collection_type").getNumber());
		read(artifact().build());
		var old = DmnIrFieldConstraintProfileTest.artifact();
		read(old.build());
		old.getModelsBuilder(0).addCollectionDefinitions(collection());
		rejects(old.build(), UNSUPPORTED_CAPABILITY);
	}

	@Test
	void rejectsInvalidCollectionDeclarationsAndElements() {
		var artifact = artifact();
		var model = artifact.getModelsBuilder(0);
		var decision = model.getDecisionsBuilder(0);
		decision.getExpressionBuilder().getListBuilder()
				.addElements(DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_STRING)
						.setLiteral(DmnIrLiteral.newBuilder().setStringValue("mixed")));
		rejects(artifact.build(), INVALID_TYPE);
		decision.getExpressionBuilder().getListBuilder().clearElements();
		read(artifact.build());
		decision.getVariableBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER);
		rejects(artifact.build(), INVALID_TYPE);
		decision.getVariableBuilder().clearType();
		model.addCollectionDefinitions(collection().toBuilder()
				.setIdentity(DmnIrElementIdentity.newBuilder().setId("other").setName("Record")));
		rejects(artifact.build(), INVALID_IDENTITY);
		model.removeCollectionDefinitions(1);
		model.getCollectionDefinitionsBuilder(0).setElementType(DmnIrScalarType.SCALAR_TYPE_NULL);
		rejects(artifact.build(), INVALID_TYPE);
	}

	@Test
	void collectionReferencesNeedImportsAndListElementsConsumeWireBudget() {
		var artifact = artifact();
		artifact.addModels(DmnIrModel.newBuilder().setNamespace("urn:other")
				.setIdentity(DmnIrElementIdentity.newBuilder().setId("other").setName("Other"))
				.addCollectionDefinitions(collection()));
		var model = artifact.getModelsBuilder(0);
		var decision = model.getDecisionsBuilder(0);
		var other = FEES.toBuilder().setModelNamespace("urn:other");
		decision.getVariableBuilder().setCollectionType(other);
		decision.getExpressionBuilder().setCollectionType(other);
		rejects(artifact.build(), INVALID_IMPORT);
		model.addImports(DmnIrImport.newBuilder().setName("other").setModelNamespace("urn:other"));
		read(artifact.build());
		for (int i = 0; i < 80; i++)
			decision.getExpressionBuilder().getListBuilder().addElements(ONE);
		var limits = new DmnIrLimits(8192, 32, 100, 5000, 128, 2, 100, 100, 100, 100);
		assertEquals(LIMIT_EXCEEDED, assertThrows(DmnIrValidationException.class,
				() -> new DmnIrArtifactReader(limits).read(artifact.build().toByteArray())).code());
	}

	private static void read(DmnIrArtifact value) {
		new DmnIrArtifactReader().read(value.toByteArray());
	}

	private static void rejects(DmnIrArtifact value, DmnIrValidationException.Code code) {
		assertEquals(code, assertThrows(DmnIrValidationException.class, () -> read(value)).code());
	}

	private static DmnIrCollectionDefinition collection() {
		return DmnIrCollectionDefinition.newBuilder()
				.setIdentity(DmnIrElementIdentity.newBuilder().setId("fees").setName("Fees"))
				.setElementType(DmnIrScalarType.SCALAR_TYPE_NUMBER).build();
	}

	static DmnIrArtifact.Builder artifact() {
		var artifact = DmnIrFieldConstraintProfileTest.artifact()
				.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(7))
				.setSemanticsVersion("finmsg-dmnir-scalar-collections/0")
				.addRequiredCapabilities("dmnir.scalar-collections");
		var model = artifact.getModelsBuilder(0).addCollectionDefinitions(collection());
		var decision = model.getDecisionsBuilder(0).clearDecisionTable();
		decision.getVariableBuilder().clearType().setCollectionType(FEES);
		decision.setExpression(DmnIrExpression.newBuilder().setCollectionType(FEES)
				.setList(DmnIrListExpression.newBuilder().addElements(ONE)));
		return artifact;
	}
}
