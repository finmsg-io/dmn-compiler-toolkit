package io.finmsg.dmn.dmnir.proto.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class DmnIrWireContractTest {

	@Test
	void roundTripsLinkedDeclarationsAndResolvedReferencesWithoutSourceObjects() throws Exception {
		String rootNamespace = "urn:example:pricing";
		String importedNamespace = "urn:example:rates";
		DmnIrReference input = reference(rootNamespace, "amount");
		DmnIrReference first = reference(rootNamespace, "first");
		DmnIrReference rate = reference(importedNamespace, "amount");
		DmnIrModel root = DmnIrModel.newBuilder().setIdentity(identity("pricing", "Pricing model"))
				.setNamespace(rootNamespace)
				.addImports(DmnIrImport.newBuilder().setModelNamespace(importedNamespace).setName("rates")
						.setLocationUri("rates.dmn"))
				.addInputs(DmnIrInput.newBuilder().setIdentity(identity("amount", "Requested amount"))
						.setVariable(variable("amount-variable", "requested amount")))
				.addDecisions(decision("first", "First decision", "first result", input))
				.addDecisions(decision("second", "Second decision", "second result", first))
				.addDecisions(decision("imported", "Imported decision", "imported result", rate)).build();
		DmnIrModel imported = DmnIrModel.newBuilder().setIdentity(identity("rates", "Rates model"))
				.setNamespace(importedNamespace)
				.addInputs(DmnIrInput.newBuilder().setIdentity(identity("amount", "Rate amount"))
						.setVariable(variable("rate-variable", "rate amount")))
				.build();
		DmnIrArtifact original = header().setRootModelNamespace(rootNamespace).addModels(root).addModels(imported)
				.addEvaluationOrder(first).addEvaluationOrder(reference(rootNamespace, "second"))
				.addEvaluationOrder(reference(rootNamespace, "imported")).build();

		DmnIrArtifact decoded = DmnIrArtifact.parseFrom(original.toByteArray());

		assertEquals(original, decoded);
		DmnIrModel decodedRoot = decoded.getModels(0);
		assertEquals("Requested amount", decodedRoot.getInputs(0).getIdentity().getName());
		assertEquals("requested amount", decodedRoot.getInputs(0).getVariable().getName());
		assertEquals("amount-variable", decodedRoot.getInputs(0).getVariable().getId());
		assertEquals("first result", decodedRoot.getDecisions(0).getVariable().getName());
		assertEquals(List.of(input), decodedRoot.getDecisions(0).getDependenciesList());
		assertEquals(first, decodedRoot.getDecisions(1).getExpression().getGlobalReference());
		assertNotEquals(input, decodedRoot.getDecisions(2).getExpression().getGlobalReference());
		assertEquals(rate, decodedRoot.getDecisions(2).getExpression().getGlobalReference());
		assertEquals("rates", decodedRoot.getImports(0).getName());
		assertEquals(List.of(first, reference(rootNamespace, "second"), reference(rootNamespace, "imported")),
				decoded.getEvaluationOrderList());
	}

	@Test
	void preservesExactDecimalCoefficientAndSignedScaleAcrossBinaryTransport() throws Exception {
		for (String lexical : List.of("9007199254740993.0000000000000000001", "-0.0000000000000000000001", "123.4500",
				"1E+30", "0.00")) {
			BigDecimal expected = new BigDecimal(lexical);
			DmnIrDecimal wire = DmnIrDecimal.newBuilder().setUnscaledValue(expected.unscaledValue().toString())
					.setScale(expected.scale()).build();
			DmnIrExpression expression = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER)
					.setLiteral(DmnIrLiteral.newBuilder().setNumberValue(wire)).build();

			DmnIrDecimal decoded = DmnIrExpression.parseFrom(expression.toByteArray()).getLiteral().getNumberValue();

			assertTrue(decoded.hasScale());
			assertEquals(expected, new BigDecimal(new BigInteger(decoded.getUnscaledValue()), decoded.getScale()));
			assertEquals(expected.scale(), decoded.getScale());
		}
	}

	@Test
	void preservesExplicitDefaultValuedLiteralsAndDistinguishesMissingNodes() throws Exception {
		List<DmnIrLiteral> literals = List.of(DmnIrLiteral.newBuilder().setNullValue(DmnIrNull.NULL_VALUE).build(),
				DmnIrLiteral.newBuilder().setBooleanValue(false).build(),
				DmnIrLiteral.newBuilder().setStringValue("").build(), DmnIrLiteral.newBuilder()
						.setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("0").setScale(0)).build());
		List<DmnIrScalarType> types = List.of(DmnIrScalarType.SCALAR_TYPE_NULL, DmnIrScalarType.SCALAR_TYPE_BOOLEAN,
				DmnIrScalarType.SCALAR_TYPE_STRING, DmnIrScalarType.SCALAR_TYPE_NUMBER);
		for (int index = 0; index < literals.size(); index++) {
			DmnIrExpression expression = DmnIrExpression.newBuilder().setType(types.get(index))
					.setLiteral(literals.get(index)).build();
			DmnIrExpression decoded = DmnIrExpression.parseFrom(expression.toByteArray());

			assertEquals(DmnIrExpression.NodeCase.LITERAL, decoded.getNodeCase());
			assertEquals(literals.get(index).getValueCase(), decoded.getLiteral().getValueCase());
			assertEquals(literals.get(index), decoded.getLiteral());
		}
		DmnIrExpression missing = DmnIrExpression.parseFrom(new byte[0]);
		assertEquals(DmnIrExpression.NodeCase.NODE_NOT_SET, missing.getNodeCase());
		assertEquals(DmnIrLiteral.ValueCase.VALUE_NOT_SET, missing.getLiteral().getValueCase());
		assertFalse(DmnIrDecimal.parseFrom(new byte[0]).hasScale());
		assertTrue(DmnIrDecimal
				.parseFrom(DmnIrDecimal.newBuilder().setUnscaledValue("0").setScale(0).build().toByteArray())
				.hasScale());
	}

	@Test
	void preservesExplicitExperimentalVersionAndUnknownRequiredCapabilities() throws Exception {
		DmnIrArtifact original = header().addRequiredCapabilities("example:future-feature").build();

		DmnIrArtifact decoded = DmnIrArtifact.parseFrom(original.toByteArray());

		assertTrue(decoded.hasSchemaVersion());
		assertTrue(decoded.getSchemaVersion().hasMajor());
		assertTrue(decoded.getSchemaVersion().hasMinor());
		assertEquals(0, decoded.getSchemaVersion().getMajor());
		assertEquals("finmsg-dmnir-scalar/0", decoded.getSemanticsVersion());
		assertEquals(List.of("dmnir.scalar", "example:future-feature"), decoded.getRequiredCapabilitiesList());
		// Parsing preserves negotiation data; a future verifier must reject unsupported
		// requirements.
		assertFalse(DmnIrArtifact.parseFrom(new byte[0]).hasSchemaVersion());
		assertFalse(DmnIrSchemaVersion.parseFrom(new byte[0]).hasMajor());
	}

	private static DmnIrArtifact.Builder header() {
		return DmnIrArtifact.newBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(0))
				.setSemanticsVersion("finmsg-dmnir-scalar/0").addRequiredCapabilities("dmnir.scalar");
	}

	private static DmnIrElementIdentity identity(String id, String name) {
		return DmnIrElementIdentity.newBuilder().setId(id).setName(name).build();
	}

	private static DmnIrVariable variable(String id, String name) {
		return DmnIrVariable.newBuilder().setId(id).setName(name).setType(DmnIrScalarType.SCALAR_TYPE_NUMBER).build();
	}

	private static DmnIrReference reference(String namespace, String id) {
		return DmnIrReference.newBuilder().setModelNamespace(namespace).setElementId(id).build();
	}

	private static DmnIrDecision decision(String id, String name, String variableName, DmnIrReference target) {
		return DmnIrDecision.newBuilder().setIdentity(identity(id, name))
				.setVariable(variable(id + "-variable", variableName)).addDependencies(target)
				.setExpression(DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER)
						.setGlobalReference(target))
				.build();
	}
}
