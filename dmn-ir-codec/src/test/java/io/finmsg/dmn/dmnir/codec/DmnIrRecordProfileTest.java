package io.finmsg.dmn.dmnir.codec;

import static org.junit.jupiter.api.Assertions.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DmnIrRecordProfileTest {
	private static final String NS = "urn:example:records";
	private static final DmnIrScalarType NUMBER = DmnIrScalarType.SCALAR_TYPE_NUMBER;

	@Test
	void acceptsNamedRecordAndPinsAdditiveWireIdentities() throws Exception {
		var artifact = fixture();
		assertEquals(artifact, read(artifact));
		assertEquals(6, DmnIrModel.getDescriptor().findFieldByName("record_definitions").getNumber());
		assertEquals(4, DmnIrVariable.getDescriptor().findFieldByName("record_type").getNumber());
		assertEquals(7, DmnIrExpression.getDescriptor().findFieldByName("record_type").getNumber());
		assertEquals(8, DmnIrExpression.getDescriptor().findFieldByName("field_access").getNumber());
		var type = DmnIrTypeReference.newBuilder()
				.setRecordType(DmnIrReference.newBuilder().setModelNamespace("n").setElementId("r")).build();
		assertEquals("12060a016e120172", HexFormat.of().formatHex(type.toByteArray()));
		assertEquals(type, DmnIrTypeReference.parseFrom(HexFormat.of().parseHex("12060a016e120172")));
	}

	@Test
	void rejectsOldHeadersMissingFieldsAndSimultaneousTypes() {
		var artifact = fixture().toBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(1))
				.setSemanticsVersion(DmnIrArtifactReader.EXPRESSION_SEMANTICS_VERSION).clearRequiredCapabilities()
				.addRequiredCapabilities("dmnir.scalar").addRequiredCapabilities("dmnir.scalar-expressions");
		rejects(artifact.build(), DmnIrValidationException.Code.UNSUPPORTED_CAPABILITY);
		artifact = fixture().toBuilder();
		artifact.getModelsBuilder(0).getInputsBuilder(0).getVariableBuilder().setType(NUMBER);
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_TYPE);
		artifact = fixture().toBuilder();
		artifact.getModelsBuilder(0).getDecisionsBuilder(0).getExpressionBuilder().getFieldAccessBuilder()
				.setFieldName("Missing");
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_REFERENCE);
		artifact = fixture().toBuilder();
		artifact.getModelsBuilder(0).getDecisionsBuilder(0).getExpressionBuilder().getFieldAccessBuilder()
				.clearSource();
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_EXPRESSION);
		artifact = fixture().toBuilder();
		artifact.getModelsBuilder(0).getRecordDefinitionsBuilder(0).getFieldsBuilder(0).clearType();
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_TYPE);
		artifact = fixture().toBuilder();
		artifact.getModelsBuilder(0).getDecisionsBuilder(0).getExpressionBuilder().getFieldAccessBuilder()
				.setSource(DmnIrExpression.newBuilder().setType(NUMBER).setLiteral(DmnIrLiteral.newBuilder()
						.setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("1").setScale(0))));
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_TYPE);
	}

	@Test
	void rejectsUnresolvedRecursiveEmptyAndDuplicateRecordDeclarations() {
		var artifact = fixture().toBuilder();
		artifact.getModelsBuilder(0).getInputsBuilder(0).getVariableBuilder().setRecordType(ref("missing"));
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_REFERENCE);
		var invalid = artifact.build();
		assertEquals("artifact.models[0].inputs[0].variable.record_type",
				assertThrows(DmnIrValidationException.class, () -> read(invalid)).path());
		artifact = fixture().toBuilder();
		artifact.getModelsBuilder(0).getRecordDefinitionsBuilder(0).getFieldsBuilder(0)
				.setType(DmnIrTypeReference.newBuilder().setRecordType(ref("driver-type")));
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_TYPE);
		artifact = fixture().toBuilder();
		artifact.getModelsBuilder(0).getRecordDefinitionsBuilder(0).clearFields();
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_TYPE);
		artifact = fixture().toBuilder();
		artifact.getModelsBuilder(0).addRecordDefinitions(artifact.getModels(0).getRecordDefinitions(0));
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_IDENTITY);
		artifact = fixture().toBuilder();
		artifact.getModelsBuilder(0).getRecordDefinitionsBuilder(0)
				.addFields(artifact.getModels(0).getRecordDefinitions(0).getFields(0));
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_IDENTITY);
	}

	@Test
	void sameShapeRecordsKeepDistinctNamedIdentity() {
		var artifact = fixture().toBuilder();
		artifact.getModelsBuilder(0).addRecordDefinitions(record("other-type", "Other"));
		artifact.getModelsBuilder(0).addInputs(DmnIrInput.newBuilder().setIdentity(identity("other", "OtherInput"))
				.setVariable(DmnIrVariable.newBuilder().setName("OtherInput").setRecordType(ref("other-type"))));
		assertDoesNotThrow(() -> read(artifact.build()));
		var decision = artifact.getModelsBuilder(0).getDecisionsBuilder(0);
		decision.getVariableBuilder().clearType().setRecordType(ref("driver-type"));
		decision.addDependencies(ref("other"));
		decision.setExpression(DmnIrExpression.newBuilder().setRecordType(ref("driver-type"))
				.setConditional(DmnIrConditionalExpression.newBuilder()
						.setCondition(DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
								.setLiteral(DmnIrLiteral.newBuilder().setBooleanValue(true)))
						.setThenExpression(global("driver", "driver-type"))
						.setElseExpression(global("other", "other-type"))));
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_TYPE);
	}

	@Test
	void boundsSharedTypeExpansionAndReferenceDepthBeforeConsumersRun() {
		var artifact = fixture().toBuilder();
		String previous = "driver-type";
		for (int i = 0; i < 20; i++) {
			String id = "branch-" + i;
			artifact.getModelsBuilder(0).addRecordDefinitions(DmnIrRecordDefinition.newBuilder()
					.setIdentity(identity(id, "Branch" + i))
					.addFields(field("Left", DmnIrTypeReference.newBuilder().setRecordType(ref(previous)).build()))
					.addFields(field("Right", DmnIrTypeReference.newBuilder().setRecordType(ref(previous)).build())));
			previous = id;
		}
		rejects(artifact.build(), DmnIrValidationException.Code.LIMIT_EXCEEDED);
		artifact = fixture().toBuilder();
		previous = "driver-type";
		for (int i = 0; i < 40; i++) {
			String id = "link-" + i;
			artifact.getModelsBuilder(0).addRecordDefinitions(
					DmnIrRecordDefinition.newBuilder().setIdentity(identity(id, "Link" + i)).addFields(
							field("Child", DmnIrTypeReference.newBuilder().setRecordType(ref(previous)).build())));
			previous = id;
		}
		rejects(artifact.build(), DmnIrValidationException.Code.LIMIT_EXCEEDED);
		var d = DmnIrLimits.defaults();
		var limited = new DmnIrArtifactReader(
				new DmnIrLimits(d.maxBytes(), d.maxDepth(), d.maxMessages(), d.maxFields(), d.maxStringBytes(),
						d.maxModels(), 3, d.maxReferences(), d.maxDecimalDigits(), d.maxAbsScale()));
		assertEquals(DmnIrValidationException.Code.LIMIT_EXCEEDED,
				assertThrows(DmnIrValidationException.class, () -> limited.read(fixture().toByteArray())).code());
	}

	@Test
	void importedTypeReferencesRequireDirectBundledImports() {
		var artifact = fixture().toBuilder();
		var definition = artifact.getModels(0).getRecordDefinitions(0);
		artifact.getModelsBuilder(0).clearRecordDefinitions();
		artifact.addModels(DmnIrModel.newBuilder().setIdentity(identity("types", "Types"))
				.setNamespace("urn:example:types").addRecordDefinitions(definition));
		var target = ref("driver-type").toBuilder().setModelNamespace("urn:example:types").build();
		artifact.getModelsBuilder(0).getInputsBuilder(0).getVariableBuilder().setRecordType(target);
		artifact.getModelsBuilder(0).getDecisionsBuilder(0).getExpressionBuilder().getFieldAccessBuilder()
				.getSourceBuilder().setRecordType(target);
		rejects(artifact.build(), DmnIrValidationException.Code.INVALID_IMPORT);
		artifact.getModelsBuilder(0)
				.addImports(DmnIrImport.newBuilder().setName("types").setModelNamespace("urn:example:types"));
		assertDoesNotThrow(() -> read(artifact.build()));
	}

	private static DmnIrArtifact read(DmnIrArtifact artifact) {
		return new DmnIrArtifactReader().read(artifact.toByteArray()).artifact();
	}
	private static void rejects(DmnIrArtifact artifact, DmnIrValidationException.Code code) {
		assertEquals(code, assertThrows(DmnIrValidationException.class, () -> read(artifact)).code());
	}
	private static DmnIrElementIdentity identity(String id, String name) {
		return DmnIrElementIdentity.newBuilder().setId(id).setName(name).build();
	}
	private static DmnIrReference ref(String id) {
		return DmnIrReference.newBuilder().setModelNamespace(NS).setElementId(id).build();
	}
	private static DmnIrRecordField field(String name, DmnIrTypeReference type) {
		return DmnIrRecordField.newBuilder().setName(name).setType(type).build();
	}
	private static DmnIrRecordDefinition record(String id, String name) {
		return DmnIrRecordDefinition.newBuilder().setIdentity(identity(id, name))
				.addFields(field("Points", DmnIrTypeReference.newBuilder().setScalarType(NUMBER).build())).build();
	}
	private static DmnIrExpression global(String id, String type) {
		return DmnIrExpression.newBuilder().setRecordType(ref(type)).setGlobalReference(ref(id)).build();
	}
	private static DmnIrArtifact fixture() {
		return DmnIrArtifact.newBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(2))
				.setSemanticsVersion(DmnIrArtifactReader.RECORD_SEMANTICS_VERSION)
				.addRequiredCapabilities("dmnir.scalar").addRequiredCapabilities("dmnir.scalar-expressions")
				.addRequiredCapabilities("dmnir.records").setRootModelNamespace(NS)
				.addModels(DmnIrModel.newBuilder().setNamespace(NS).setIdentity(identity("model", "Records"))
						.addRecordDefinitions(record("driver-type", "DriverType"))
						.addInputs(DmnIrInput.newBuilder().setIdentity(identity("driver", "Driver")).setVariable(
								DmnIrVariable.newBuilder().setName("Driver").setRecordType(ref("driver-type"))))
						.addDecisions(DmnIrDecision.newBuilder().setIdentity(identity("result", "Result"))
								.setVariable(DmnIrVariable.newBuilder().setName("Result").setType(NUMBER))
								.addDependencies(ref("driver"))
								.setExpression(DmnIrExpression.newBuilder().setType(NUMBER)
										.setFieldAccess(DmnIrFieldAccess.newBuilder()
												.setSource(global("driver", "driver-type")).setFieldName("Points")))))
				.addEvaluationOrder(ref("result")).build();
	}
}
