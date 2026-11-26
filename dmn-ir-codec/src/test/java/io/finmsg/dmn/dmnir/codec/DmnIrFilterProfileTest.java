package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;
import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.dmnir.proto.v0.*;
import org.junit.jupiter.api.Test;

class DmnIrFilterProfileTest {
	private static final DmnIrExpression ITEM = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER)
			.setLocalReference(DmnIrLocalReference.newBuilder().setBindingId("item")).build();

	@Test
	void pinsFilterTagAndRequiresExactProfile() {
		assertEquals(13, DmnIrExpression.getDescriptor().findFieldByName("filter").getNumber());
		assertEquals(2, DmnIrFilterExpression.getDescriptor().findFieldByName("item_binding_id").getNumber());
		var artifact = artifact();
		read(artifact.build());
		var old = DmnIrCollectionProfileTest.artifact();
		read(old.build());
		old.getModelsBuilder(0).getDecisionsBuilder(0)
				.setExpression(artifact.getModels(0).getDecisions(0).getExpression());
		rejects(old.build(), UNSUPPORTED_CAPABILITY);
	}

	@Test
	void rejectsInvalidBodiesTypesAndItemScope() {
		var artifact = artifact();
		var filter = filter(artifact);
		var original = filter.build();
		filter.clearPredicate();
		rejects(artifact.build(), INVALID_EXPRESSION);
		filter.setPredicate(ITEM);
		rejects(artifact.build(), INVALID_TYPE);
		filter.setPredicate(original.getPredicate()).setItemBindingId("");
		rejects(artifact.build(), INVALID_IDENTITY);
		filter.setItemBindingId("unknown");
		rejects(artifact.build(), INVALID_REFERENCE);
		filter.setItemBindingId("item");
		filter.getSourceBuilder().getListBuilder().setElements(0, ITEM);
		rejects(artifact.build(), INVALID_REFERENCE);
		filter.setSource(original.getSource().toBuilder().setFilter(original));
		rejects(artifact.build(), INVALID_IDENTITY);
	}

	@Test
	void predicatesRequireDeclaredDependenciesAndRejectNestedFilters() {
		var artifact = artifact();
		var model = artifact.getModelsBuilder(0);
		model.addInputs(DmnIrInput.newBuilder()
				.setIdentity(DmnIrElementIdentity.newBuilder().setId("enabled").setName("Enabled")).setVariable(
						DmnIrVariable.newBuilder().setName("Enabled").setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)));
		var filter = filter(artifact);
		var nested = model.getDecisions(0).getExpression();
		filter.setPredicate(
				DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN).setGlobalReference(
						DmnIrReference.newBuilder().setModelNamespace("urn:tables").setElementId("enabled")));
		rejects(artifact.build(), INVALID_DEPENDENCY);
		filter.setPredicate(DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
				.setBinary(DmnIrBinaryExpression.newBuilder().setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL)
						.setLeft(nested).setRight(nested)));
		rejects(artifact.build(), INVALID_EXPRESSION);
	}

	private static DmnIrFilterExpression.Builder filter(DmnIrArtifact.Builder artifact) {
		return artifact.getModelsBuilder(0).getDecisionsBuilder(0).getExpressionBuilder().getFilterBuilder();
	}

	static DmnIrArtifact.Builder artifact() {
		var artifact = DmnIrCollectionProfileTest.artifact()
				.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(8))
				.setSemanticsVersion("finmsg-dmnir-scalar-filters/0").addRequiredCapabilities("dmnir.scalar-filters");
		var decision = artifact.getModelsBuilder(0).getDecisionsBuilder(0);
		var source = decision.getExpression();
		var predicate = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
				.setBinary(DmnIrBinaryExpression.newBuilder().setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_EQUAL)
						.setLeft(ITEM).setRight(source.getList().getElements(0)));
		decision.setExpression(source.toBuilder().setFilter(
				DmnIrFilterExpression.newBuilder().setSource(source).setItemBindingId("item").setPredicate(predicate)));
		return artifact;
	}

	private static void read(DmnIrArtifact artifact) {
		new DmnIrArtifactReader().read(artifact.toByteArray());
	}

	private static void rejects(DmnIrArtifact artifact, DmnIrValidationException.Code code) {
		assertEquals(code, assertThrows(DmnIrValidationException.class, () -> read(artifact)).code());
	}
}
