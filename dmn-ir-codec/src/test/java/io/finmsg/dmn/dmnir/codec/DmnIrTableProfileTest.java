package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;
import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.dmnir.proto.v0.*;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class DmnIrTableProfileTest {
	private static final DmnIrScalarType NUMBER = DmnIrScalarType.SCALAR_TYPE_NUMBER;
	private static final DmnIrExpression ONE = DmnIrExpression.newBuilder().setType(NUMBER).setLiteral(
			DmnIrLiteral.newBuilder().setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("1").setScale(0)))
			.build();
	private static final List<String> DATE_CAPABILITIES = List.of("dmnir.scalar", "dmnir.scalar-expressions",
			"dmnir.records", "dmnir.local-bindings", "dmnir.date");

	@Test
	void pinsTableTagsAndProfileAndAcceptsEarlierArtifacts() {
		var artifact = artifact().build();
		assertEquals(5, DmnIrDecision.getDescriptor().findFieldByName("decision_table").getNumber());
		assertEquals(5, DmnIrDecisionTable.getDescriptor().findFieldByName("rules").getNumber());
		assertEquals(2, DmnIrUnaryTest.getDescriptor().findFieldByName("range").getNumber());
		assertEquals(3, DmnIrTableHitPolicy.TABLE_HIT_POLICY_ANY.getNumber());
		assertEquals(artifact, new DmnIrArtifactReader().read(artifact.toByteArray()).artifact());
		var old = artifact.toBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(4))
				.setSemanticsVersion("finmsg-dmnir-dates/0").clearRequiredCapabilities()
				.addAllRequiredCapabilities(DATE_CAPABILITIES);
		rejects(old.build(), UNSUPPORTED_CAPABILITY);
		old.getModelsBuilder(0).getDecisionsBuilder(0).clearDecisionTable().setExpression(ONE);
		assertEquals(old.build(), new DmnIrArtifactReader().read(old.build().toByteArray()).artifact());
		rejects(artifact.toBuilder().clearRequiredCapabilities().addAllRequiredCapabilities(DATE_CAPABILITIES).build(),
				INVALID_HEADER);
	}

	@Test
	void rejectsMalformedTablesAndIncompatibleExpressions() {
		var duplicateBody = artifact();
		duplicateBody.getModelsBuilder(0).getDecisionsBuilder(0).setExpression(ONE);
		rejects(duplicateBody.build(), INVALID_EXPRESSION);
		var noBody = artifact();
		noBody.getModelsBuilder(0).getDecisionsBuilder(0).clearDecisionTable();
		rejects(noBody.build(), INVALID_EXPRESSION);
		mutate(DmnIrDecisionTable.Builder::clearInputs, INVALID_EXPRESSION);
		mutate(t -> t.getRulesBuilder(0).clearOutputEntries(), INVALID_EXPRESSION);
		mutate(t -> t.getOutputsBuilder(0).setId("column"), INVALID_IDENTITY);
		mutate(t -> t.setHitPolicyValue(99), INVALID_EXPRESSION);
		mutate(t -> t.getRulesBuilder(0).getInputEntriesBuilder(0).setNegated(true), INVALID_EXPRESSION);
		mutate(t -> t.getRulesBuilder(0).setInputEntries(0,
				DmnIrUnaryTests.newBuilder()
						.addTests(DmnIrUnaryTest.newBuilder().setRange(DmnIrTestRange.newBuilder().setLower(ONE)))),
				INVALID_TYPE);
		mutate(t -> t.getOutputsBuilder(0)
				.setDefaultExpression(DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_STRING)
						.setLiteral(DmnIrLiteral.newBuilder().setStringValue("bad"))),
				INVALID_TYPE);
		var missingDependency = artifact();
		var model = missingDependency.getModelsBuilder(0);
		model.addInputs(DmnIrInput.newBuilder().setIdentity(identity("input", "Input"))
				.setVariable(DmnIrVariable.newBuilder().setName("Input").setType(NUMBER)));
		model.getDecisionsBuilder(0).getDecisionTableBuilder().getInputsBuilder(0)
				.setExpression(DmnIrExpression.newBuilder().setType(NUMBER).setGlobalReference(reference("input")));
		rejects(missingDependency.build(), INVALID_DEPENDENCY);
	}

	@Test
	void countsColumnsAndRulesDuringWirePreflight() {
		var limits = new DmnIrLimits(4096, 32, 100, 100, 128, 1, 3, 10, 100, 100);
		var bytes = artifact().build().toByteArray();
		var failure = assertThrows(DmnIrValidationException.class, () -> new DmnIrArtifactReader(limits).read(bytes));
		assertEquals(LIMIT_EXCEEDED, failure.code());
		assertTrue(failure.getMessage().contains("rules[0]"));
	}

	private static void mutate(Consumer<DmnIrDecisionTable.Builder> change, DmnIrValidationException.Code code) {
		var artifact = artifact();
		change.accept(artifact.getModelsBuilder(0).getDecisionsBuilder(0).getDecisionTableBuilder());
		rejects(artifact.build(), code);
	}

	private static void rejects(DmnIrArtifact artifact, DmnIrValidationException.Code code) {
		assertEquals(code, assertThrows(DmnIrValidationException.class,
				() -> new DmnIrArtifactReader().read(artifact.toByteArray())).code());
	}

	private static DmnIrArtifact.Builder artifact() {
		return DmnIrArtifact.newBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(5))
				.setSemanticsVersion("finmsg-dmnir-tables/0").addAllRequiredCapabilities(DATE_CAPABILITIES)
				.addRequiredCapabilities("dmnir.tables").setRootModelNamespace("urn:tables")
				.addEvaluationOrder(reference("answer"))
				.addModels(DmnIrModel.newBuilder().setNamespace("urn:tables").setIdentity(identity("model", "Tables"))
						.addDecisions(DmnIrDecision.newBuilder().setIdentity(identity("answer", "Answer"))
								.setVariable(DmnIrVariable.newBuilder().setName("Answer").setType(NUMBER))
								.setDecisionTable(DmnIrDecisionTable.newBuilder().setId("table")
										.setHitPolicy(DmnIrTableHitPolicy.TABLE_HIT_POLICY_UNIQUE)
										.addInputs(DmnIrTableInput.newBuilder().setId("column").setType(NUMBER)
												.setExpression(ONE))
										.addOutputs(DmnIrTableOutput.newBuilder().setType(NUMBER))
										.addRules(DmnIrTableRule.newBuilder()
												.addInputEntries(DmnIrUnaryTests.newBuilder().setWildcard(true))
												.addOutputEntries(ONE)))));
	}

	private static DmnIrElementIdentity identity(String id, String name) {
		return DmnIrElementIdentity.newBuilder().setId(id).setName(name).build();
	}

	private static DmnIrReference reference(String id) {
		return DmnIrReference.newBuilder().setModelNamespace("urn:tables").setElementId(id).build();
	}
}
