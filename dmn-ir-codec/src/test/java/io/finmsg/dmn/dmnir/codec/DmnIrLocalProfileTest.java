package io.finmsg.dmn.dmnir.codec;

import static org.junit.jupiter.api.Assertions.*;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DmnIrLocalProfileTest {
	private static final DmnIrScalarType NUMBER = DmnIrScalarType.SCALAR_TYPE_NUMBER;
	@Test
	void acceptsSequentialAndNestedBindingsAndPinsWireTags() {
		var value = context(List.of(binding("a", "A", number()),
				binding("b", "B", context(List.of(binding("c", "C", local("a"))), local("c")))), local("b"));
		assertDoesNotThrow(() -> read(artifact(value)));
		assertEquals(9, DmnIrExpression.getDescriptor().findFieldByName("local_reference").getNumber());
		assertEquals(10, DmnIrExpression.getDescriptor().findFieldByName("context").getNumber());
		assertEquals("0a0178",
				HexFormat.of().formatHex(DmnIrLocalReference.newBuilder().setBindingId("x").build().toByteArray()));
	}
	@Test
	void rejectsSelfForwardAndEscapedReferences() {
		rejects(context(List.of(binding("a", "A", local("a"))), local("a")),
				DmnIrValidationException.Code.INVALID_REFERENCE);
		rejects(context(List.of(binding("a", "A", local("b")), binding("b", "B", number())), local("a")),
				DmnIrValidationException.Code.INVALID_REFERENCE);
		rejects(context(List.of(binding("a", "A", context(List.of(binding("b", "B", number())), local("b")))),
				local("b")), DmnIrValidationException.Code.INVALID_REFERENCE);
		rejects(local("missing"), DmnIrValidationException.Code.INVALID_REFERENCE);
	}
	@Test
	void rejectsDuplicateIdentitiesNamesAndMissingValues() {
		rejects(context(List.of(binding("a", "A", number()), binding("a", "B", number())), local("a")),
				DmnIrValidationException.Code.INVALID_IDENTITY);
		rejects(context(List.of(binding("a", "A", number()), binding("b", "A", number())), local("b")),
				DmnIrValidationException.Code.INVALID_IDENTITY);
		rejects(context(List.of(binding("a", "A", number()).toBuilder().setBindingId("").build()), number()),
				DmnIrValidationException.Code.INVALID_IDENTITY);
		rejects(context(List.of(binding("a", "A", number()).toBuilder().clearValue().build()), number()),
				DmnIrValidationException.Code.INVALID_EXPRESSION);
		rejects(DmnIrExpression.newBuilder().setType(NUMBER).setContext(DmnIrContextExpression.getDefaultInstance())
				.build(), DmnIrValidationException.Code.INVALID_EXPRESSION);
		rejects(context(List.of(binding("a", "A", number()).toBuilder()
				.setVariable(DmnIrVariable.newBuilder().setName("A").setId("decision").setType(NUMBER)).build()),
				number()), DmnIrValidationException.Code.INVALID_IDENTITY);
	}
	@Test
	void checksLocalTypesAndProfileHeaders() {
		var value = context(List.of(binding("a", "A", number())), local("a"));
		var wrong = context(List.of(binding("a", "A", number())),
				local("a").toBuilder().setType(DmnIrScalarType.SCALAR_TYPE_STRING).build());
		rejects(wrong, DmnIrValidationException.Code.INVALID_TYPE);
		var old = artifact(value).toBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(2))
				.setSemanticsVersion(DmnIrArtifactReader.RECORD_SEMANTICS_VERSION).clearRequiredCapabilities()
				.addAllRequiredCapabilities(List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records"))
				.build();
		assertEquals(DmnIrValidationException.Code.UNSUPPORTED_CAPABILITY,
				assertThrows(DmnIrValidationException.class, () -> read(old)).code());
		var caps = artifact(value).toBuilder().clearRequiredCapabilities()
				.addAllRequiredCapabilities(List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records"))
				.build();
		assertEquals(DmnIrValidationException.Code.INVALID_HEADER,
				assertThrows(DmnIrValidationException.class, () -> read(caps)).code());
	}
	@Test
	void appliesDeclarationAndLocalReferenceLimitsBeforeConsumers() {
		var value = artifact(context(List.of(binding("a", "A", number())), local("a")));
		var d = DmnIrLimits.defaults();
		var declarationLimits = new DmnIrLimits(d.maxBytes(), d.maxDepth(), d.maxMessages(), d.maxFields(),
				d.maxStringBytes(), d.maxModels(), 1, d.maxReferences(), d.maxDecimalDigits(), d.maxAbsScale());
		assertEquals(DmnIrValidationException.Code.LIMIT_EXCEEDED, assertThrows(DmnIrValidationException.class,
				() -> new DmnIrArtifactReader(declarationLimits).read(value.toByteArray())).code());
		var limits = new DmnIrLimits(d.maxBytes(), d.maxDepth(), d.maxMessages(), d.maxFields(), d.maxStringBytes(),
				d.maxModels(), d.maxElements(), 1, d.maxDecimalDigits(), d.maxAbsScale());
		var referenceLimits = limits;
		assertEquals(DmnIrValidationException.Code.LIMIT_EXCEEDED, assertThrows(DmnIrValidationException.class,
				() -> new DmnIrArtifactReader(referenceLimits).read(value.toByteArray())).code());
	}
	private static DmnIrArtifact artifact(DmnIrExpression expression) {
		return DmnIrArtifact.newBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(3))
				.setSemanticsVersion(DmnIrArtifactReader.LOCAL_SEMANTICS_VERSION)
				.addAllRequiredCapabilities(
						List.of("dmnir.scalar", "dmnir.scalar-expressions", "dmnir.records", "dmnir.local-bindings"))
				.setRootModelNamespace("urn:locals")
				.addModels(DmnIrModel.newBuilder().setNamespace("urn:locals")
						.setIdentity(DmnIrElementIdentity.newBuilder().setId("model").setName("Model"))
						.addDecisions(DmnIrDecision.newBuilder()
								.setIdentity(DmnIrElementIdentity.newBuilder().setId("decision").setName("Answer"))
								.setVariable(DmnIrVariable.newBuilder().setName("Answer").setType(NUMBER))
								.setExpression(expression)))
				.addEvaluationOrder(
						DmnIrReference.newBuilder().setModelNamespace("urn:locals").setElementId("decision"))
				.build();
	}
	private static DmnIrExpression context(List<DmnIrLocalBinding> bindings, DmnIrExpression result) {
		return DmnIrExpression.newBuilder().setType(NUMBER)
				.setContext(DmnIrContextExpression.newBuilder().addAllBindings(bindings).setResult(result)).build();
	}
	private static DmnIrLocalBinding binding(String id, String name, DmnIrExpression value) {
		return DmnIrLocalBinding.newBuilder().setBindingId(id)
				.setVariable(DmnIrVariable.newBuilder().setName(name).setType(NUMBER)).setValue(value).build();
	}
	private static DmnIrExpression number() {
		return DmnIrExpression.newBuilder().setType(NUMBER).setLiteral(
				DmnIrLiteral.newBuilder().setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("1").setScale(0)))
				.build();
	}
	private static DmnIrExpression local(String id) {
		return DmnIrExpression.newBuilder().setType(NUMBER)
				.setLocalReference(DmnIrLocalReference.newBuilder().setBindingId(id)).build();
	}
	private static void read(DmnIrArtifact artifact) {
		new DmnIrArtifactReader().read(artifact.toByteArray());
	}
	private static void rejects(DmnIrExpression value, DmnIrValidationException.Code code) {
		assertEquals(code, assertThrows(DmnIrValidationException.class, () -> read(artifact(value))).code());
	}
}
