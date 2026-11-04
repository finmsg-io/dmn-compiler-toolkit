package io.finmsg.dmn.dmnir.codec;

import static io.finmsg.dmn.dmnir.codec.DmnIrValidationException.Code.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.UnknownFieldSet;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DmnIrArtifactReaderTest {
	private static final String ROOT = "urn:example:root";
	private static final String RATES = "urn:example:rates";
	private static final DmnIrScalarType NUMBER = DmnIrScalarType.SCALAR_TYPE_NUMBER;

	@Test
	void acceptsLinkedGraphAndKeepsImmutableIdentityAndImportMetadata() {
		DmnIrArtifact artifact = linked();
		byte[] bytes = artifact.toByteArray();
		var verified = new DmnIrArtifactReader().read(bytes);
		bytes[0] = 0;
		assertEquals(artifact, verified.artifact());
		assertEquals("https://example.invalid/not-fetched.dmn",
				verified.artifact().getModels(0).getImports(0).getLocationUri());
		assertEquals(ref(RATES, "amount"),
				verified.artifact().getModels(0).getDecisions(2).getExpression().getGlobalReference());
	}

	@Test
	void preservesExactDecimalsIncludingNegativeScaleAndFractionalZeros() {
		for (DmnIrDecimal decimal : List.of(decimal("90071992547409930000000000001", 13), decimal("-1", 100),
				decimal("1234500", 4), decimal("1", -30), decimal("0", 0))) {
			DmnIrArtifact artifact = scalar(DmnIrLiteral.newBuilder().setNumberValue(decimal).build(), NUMBER);
			assertEquals(decimal,
					read(artifact).getModels(0).getDecisions(0).getExpression().getLiteral().getNumberValue());
		}
	}

	@Test
	void acceptsExplicitDefaultsAndNullableScalarDeclarations() {
		for (DmnIrScalarType type : List.of(DmnIrScalarType.SCALAR_TYPE_NULL, NUMBER,
				DmnIrScalarType.SCALAR_TYPE_BOOLEAN, DmnIrScalarType.SCALAR_TYPE_STRING)) {
			read(scalar(DmnIrLiteral.newBuilder().setNullValue(DmnIrNull.NULL_VALUE).build(), type));
		}
		read(scalar(DmnIrLiteral.newBuilder().setBooleanValue(false).build(), DmnIrScalarType.SCALAR_TYPE_BOOLEAN));
		read(scalar(DmnIrLiteral.newBuilder().setStringValue("").build(), DmnIrScalarType.SCALAR_TYPE_STRING));
	}

	@Test
	void rejectsMissingAndUnsupportedVersionsAndCapabilities() {
		reject(linked().toBuilder().clearSchemaVersion().build(), INVALID_HEADER, "schema_version");
		reject(linked().toBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMinor(0)).build(),
				INVALID_HEADER, "schema_version");
		reject(linked().toBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(1).setMinor(0)).build(),
				UNSUPPORTED_VERSION, "schema_version");
		reject(linked().toBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(1)).build(),
				UNSUPPORTED_VERSION, "schema_version");
		reject(linked().toBuilder().setSemanticsVersion("future").build(), UNSUPPORTED_VERSION, "schema_version");
		reject(linked().toBuilder().addRequiredCapabilities("future").build(), UNSUPPORTED_CAPABILITY,
				"required_capabilities[1]");
		reject(linked().toBuilder().addRequiredCapabilities(DmnIrArtifactReader.SCALAR_CAPABILITY).build(),
				INVALID_HEADER, "required_capabilities[1]");
		reject(linked().toBuilder().clearRequiredCapabilities().build(), INVALID_HEADER, "required_capabilities");
		reject(linked().toBuilder().setRootModelNamespace("unknown").build(), INVALID_HEADER, "root_model_namespace");
	}

	@Test
	void rejectsMalformedWireBeforeSemanticInterpretation() {
		for (byte[] wire : List.of(new byte[]{10, 3, 8}, new byte[]{8, 0}, new byte[]{34, 1, (byte) 255},
				new byte[]{10, 6, 8, (byte) 128, (byte) 128, (byte) 128, (byte) 128, 16},
				new byte[]{34, (byte) 128, (byte) 128, (byte) 128, (byte) 128, 16})) {
			rejectBytes(new DmnIrArtifactReader(), wire, MALFORMED_PROTOBUF, "artifact");
		}
	}

	@Test
	void rejectsUnknownFieldsAndDuplicateSingularAndOneofSelections() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		bytes.write(linked().toByteArray());
		CodedOutputStream wire = CodedOutputStream.newInstance(bytes);
		wire.writeUInt32(999, 1);
		wire.flush();
		rejectBytes(new DmnIrArtifactReader(), bytes.toByteArray(), UNKNOWN_FIELD, "artifact");

		bytes.reset();
		bytes.write(linked().toByteArray());
		wire = CodedOutputStream.newInstance(bytes);
		wire.writeString(4, ROOT);
		wire.flush();
		rejectBytes(new DmnIrArtifactReader(), bytes.toByteArray(), MALFORMED_PROTOBUF, "root_model_namespace");

		UnknownFieldSet conflict = UnknownFieldSet.newBuilder()
				.addField(3, UnknownFieldSet.Field.newBuilder().addLengthDelimited(ByteString.EMPTY).build()).build();
		DmnIrLiteral literal = DmnIrLiteral.newBuilder().setBooleanValue(false).setUnknownFields(conflict).build();
		reject(scalar(literal, DmnIrScalarType.SCALAR_TYPE_BOOLEAN), MALFORMED_PROTOBUF, "literal.string_value");

		DmnIrArtifact artifact = linked();
		var decision = artifact.getModels(0).getDecisions(0).toBuilder();
		decision.setExpression(decision.getExpression().toBuilder().setUnknownFields(UnknownFieldSet.newBuilder()
				.addField(999, UnknownFieldSet.Field.newBuilder().addVarint(1).build()).build()));
		reject(replace(artifact, 0, decision.build()), UNKNOWN_FIELD, "expression");
	}

	@Test
	void enforcesByteBoundaryAndWireBudgetsBeforeGeneratedDecoding() {
		byte[] bytes = linked().toByteArray();
		assertEquals(linked(), new DmnIrArtifactReader(limit(0, bytes.length)).read(bytes).artifact());
		rejectBytes(new DmnIrArtifactReader(limit(0, bytes.length - 1)), bytes, LIMIT_EXCEEDED, "artifact");
		for (int index : List.of(1, 2, 3, 4, 5, 6, 7)) {
			rejectBytes(new DmnIrArtifactReader(limit(index, 1)), bytes, LIMIT_EXCEEDED, "artifact");
		}
		DmnIrArtifact string = scalar(DmnIrLiteral.newBuilder().setStringValue("é".repeat(20)).build(),
				DmnIrScalarType.SCALAR_TYPE_STRING);
		rejectBytes(new DmnIrArtifactReader(limit(4, 32)), string.toByteArray(), LIMIT_EXCEEDED,
				"literal.string_value");
	}

	@Test
	void rejectsInvalidDecimalGrammarPresenceAndExpansionBounds() {
		for (String coefficient : List.of("", "+1", "00", "-0", "01", "1.2", "1e3", "١")) {
			reject(scalar(DmnIrLiteral.newBuilder().setNumberValue(decimal(coefficient, 0)).build(), NUMBER),
					INVALID_NUMBER, "number_value");
		}
		reject(scalar(DmnIrLiteral.newBuilder().setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("1")).build(),
				NUMBER), INVALID_NUMBER, "number_value");
		reject(scalar(DmnIrLiteral.newBuilder().setNumberValue(decimal("1", Integer.MIN_VALUE)).build(), NUMBER),
				LIMIT_EXCEEDED, "scale");
		reject(scalar(DmnIrLiteral.newBuilder().setNumberValue(decimal("1".repeat(4097), 0)).build(), NUMBER),
				LIMIT_EXCEEDED, "number_value");
		var atBoundary = scalar(DmnIrLiteral.newBuilder().setNumberValue(decimal("12", 1)).build(), NUMBER);
		assertEquals(atBoundary, new DmnIrArtifactReader(limit(8, 2)).read(atBoundary.toByteArray()).artifact());
		var tooManyDigits = scalar(DmnIrLiteral.newBuilder().setNumberValue(decimal("123", 1)).build(), NUMBER);
		rejectBytes(new DmnIrArtifactReader(limit(8, 2)), tooManyDigits.toByteArray(), LIMIT_EXCEEDED, "number_value");
		rejectBytes(new DmnIrArtifactReader(limit(9, 0)), atBoundary.toByteArray(), LIMIT_EXCEEDED, "scale");
	}

	@Test
	void rejectsDuplicateNamespacesElementIdsVariableIdsAndNames() {
		DmnIrArtifact artifact = linked();
		reject(artifact.toBuilder().addModels(artifact.getModels(0)).build(), INVALID_IDENTITY, "namespace");
		var first = artifact.getModels(0).getDecisions(0).toBuilder();
		reject(replace(artifact, 0, first.setIdentity(identity("amount", "First")).build()), INVALID_IDENTITY, "id");
		first = artifact.getModels(0).getDecisions(0).toBuilder();
		reject(replace(artifact, 0, first.setVariable(first.getVariable().toBuilder().setId("amount")).build()),
				INVALID_IDENTITY, "variable.id");
		first = artifact.getModels(0).getDecisions(0).toBuilder();
		reject(replace(artifact, 0, first.setVariable(first.getVariable().toBuilder().setName("amount")).build()),
				INVALID_IDENTITY, "variable.name");
		first = artifact.getModels(0).getDecisions(0).toBuilder();
		reject(replace(artifact, 0, first.setIdentity(identity("first", "Amount")).build()), INVALID_IDENTITY, "name");
	}

	@Test
	void rejectsUnbundledOrAmbiguousImportsAndUndeclaredCrossModelReferences() {
		DmnIrArtifact artifact = linked();
		var root = artifact.getModels(0).toBuilder();
		reject(artifact.toBuilder().setModels(0, root.clearImports()).build(), INVALID_REFERENCE, "dependencies");
		root = artifact.getModels(0).toBuilder();
		reject(artifact.toBuilder()
				.setModels(0, root.setImports(0, root.getImports(0).toBuilder().setModelNamespace("unknown"))).build(),
				INVALID_IMPORT, "imports");
		root = artifact.getModels(0).toBuilder();
		reject(artifact.toBuilder().setModels(0, root.addImports(root.getImports(0))).build(), INVALID_IMPORT, "name");
	}

	@Test
	void rejectsMissingDuplicateSelfAndUnresolvedDependencies() {
		DmnIrArtifact artifact = linked();
		var first = artifact.getModels(0).getDecisions(0).toBuilder();
		reject(replace(artifact, 0, first.clearDependencies().build()), INVALID_DEPENDENCY, "global_reference");
		first = artifact.getModels(0).getDecisions(0).toBuilder();
		reject(replace(artifact, 0, first.addDependencies(first.getDependencies(0)).build()), INVALID_DEPENDENCY,
				"dependencies[1]");
		first = artifact.getModels(0).getDecisions(0).toBuilder();
		reject(replace(artifact, 0, first.setDependencies(0, ref(ROOT, "first")).build()), INVALID_DEPENDENCY,
				"dependencies[0]");
		first = artifact.getModels(0).getDecisions(0).toBuilder();
		reject(replace(artifact, 0, first.setDependencies(0, ref(ROOT, "unknown")).build()), INVALID_REFERENCE,
				"dependencies[0]");
	}

	@Test
	void rejectsUnknownAndMismatchedTypesAndMissingExpressionForms() {
		DmnIrArtifact artifact = linked();
		var first = artifact.getModels(0).getDecisions(0).toBuilder();
		reject(replace(artifact, 0, first.setVariable(first.getVariable().toBuilder().setTypeValue(77)).build()),
				INVALID_TYPE, "variable.type");
		first = artifact.getModels(0).getDecisions(0).toBuilder();
		reject(replace(artifact, 0,
				first.setVariable(first.getVariable().toBuilder().setType(DmnIrScalarType.SCALAR_TYPE_STRING)).build()),
				INVALID_TYPE, "expression.type");
		first = artifact.getModels(0).getDecisions(0).toBuilder();
		reject(replace(artifact, 0, first.setExpression(DmnIrExpression.newBuilder().setType(NUMBER)).build()),
				INVALID_EXPRESSION, "expression");
		reject(scalar(DmnIrLiteral.getDefaultInstance(), NUMBER), INVALID_EXPRESSION, "literal");
		reject(scalar(DmnIrLiteral.newBuilder().setStringValue("wrong type").build(), NUMBER), INVALID_TYPE, "literal");
		reject(scalar(DmnIrLiteral.newBuilder().setNullValueValue(7).build(), NUMBER), INVALID_EXPRESSION, "literal");
		var root = artifact.getModels(0).toBuilder();
		root.setInputs(0, root.getInputs(0).toBuilder()
				.setVariable(root.getInputs(0).getVariable().toBuilder().setType(DmnIrScalarType.SCALAR_TYPE_STRING)));
		reject(artifact.toBuilder().setModels(0, root).build(), INVALID_TYPE, "expression.type");
	}

	@Test
	void rejectsMissingDuplicateInputUnknownAndForwardScheduleEntriesAndCycles() {
		DmnIrArtifact artifact = linked();
		reject(artifact.toBuilder().removeEvaluationOrder(1).build(), INVALID_EVALUATION_ORDER, "evaluation_order");
		reject(artifact.toBuilder().addEvaluationOrder(ref(ROOT, "first")).build(), INVALID_EVALUATION_ORDER,
				"evaluation_order");
		reject(artifact.toBuilder().setEvaluationOrder(0, ref(ROOT, "amount")).build(), INVALID_EVALUATION_ORDER,
				"evaluation_order");
		reject(artifact.toBuilder().setEvaluationOrder(0, ref(ROOT, "unknown")).build(), INVALID_REFERENCE,
				"evaluation_order");
		reject(artifact.toBuilder().setEvaluationOrder(0, ref(ROOT, "second")).setEvaluationOrder(1, ref(ROOT, "first"))
				.build(), INVALID_EVALUATION_ORDER, "dependencies");
		var first = decision("first", "First", ref(ROOT, "second"));
		reject(replace(artifact, 0, first), INVALID_EVALUATION_ORDER, "dependencies");
	}

	@Test
	void diagnosticsAreDeterministicAndReaderBudgetsResetBetweenReads() {
		DmnIrArtifact artifact = linked();
		var invalid = artifact.toBuilder().setSemanticsVersion("untrusted value must not be logged").build();
		DmnIrArtifactReader reader = new DmnIrArtifactReader();
		var first = assertThrows(DmnIrValidationException.class, () -> reader.read(invalid.toByteArray()));
		var second = assertThrows(DmnIrValidationException.class, () -> reader.read(invalid.toByteArray()));
		assertEquals(first.getMessage(), second.getMessage());
		assertFalse(first.getMessage().contains("untrusted value"));
		assertEquals(artifact, reader.read(artifact.toByteArray()).artifact());
		assertEquals(artifact, reader.read(artifact.toByteArray()).artifact());
		assertThrows(IllegalArgumentException.class, () -> limit(1, 65));
		assertThrows(IllegalArgumentException.class, () -> limit(0, 0));
	}

	@Test
	void rejectsSeededMalformedInputsWithoutLeakingParserFailures() {
		Random random = new Random(20261103);
		DmnIrArtifactReader reader = new DmnIrArtifactReader();
		for (int i = 0; i < 500; i++) {
			byte[] bytes = new byte[random.nextInt(128)];
			random.nextBytes(bytes);
			assertThrows(DmnIrValidationException.class, () -> reader.read(bytes));
		}
		assertEquals(linked(), reader.read(linked().toByteArray()).artifact());
	}

	@Test
	void acceptsExactDeclarationAndUtf8BudgetsAndUnusedDeclaredRequirements() {
		DmnIrArtifact artifact = linked();
		assertEquals(artifact, new DmnIrArtifactReader(limit(5, 2)).read(artifact.toByteArray()).artifact());
		assertEquals(artifact, new DmnIrArtifactReader(limit(6, 5)).read(artifact.toByteArray()).artifact());
		var first = artifact.getModels(0).getDecisions(0).toBuilder().addDependencies(ref(RATES, "amount")).build();
		read(replace(artifact, 0, first));
		var string = scalar(DmnIrLiteral.newBuilder().setStringValue("é".repeat(16)).build(),
				DmnIrScalarType.SCALAR_TYPE_STRING);
		assertEquals(string, new DmnIrArtifactReader(limit(4, 32)).read(string.toByteArray()).artifact());
	}

	private static DmnIrArtifact read(DmnIrArtifact artifact) {
		return new DmnIrArtifactReader().read(artifact.toByteArray()).artifact();
	}

	private static void reject(DmnIrArtifact artifact, DmnIrValidationException.Code code, String path) {
		rejectBytes(new DmnIrArtifactReader(), artifact.toByteArray(), code, path);
	}

	private static void rejectBytes(DmnIrArtifactReader reader, byte[] bytes, DmnIrValidationException.Code code,
			String path) {
		var diagnostic = assertThrows(DmnIrValidationException.class, () -> reader.read(bytes));
		assertEquals(code, diagnostic.code(), diagnostic.getMessage());
		assertTrue(diagnostic.path().contains(path), diagnostic.getMessage());
	}

	private static DmnIrLimits limit(int index, int value) {
		DmnIrLimits d = DmnIrLimits.defaults();
		int[] values = {d.maxBytes(), d.maxDepth(), d.maxMessages(), d.maxFields(), d.maxStringBytes(), d.maxModels(),
				d.maxElements(), d.maxReferences(), d.maxDecimalDigits(), d.maxAbsScale()};
		values[index] = value;
		return new DmnIrLimits(values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7],
				values[8], values[9]);
	}

	private static DmnIrElementIdentity identity(String id, String name) {
		return DmnIrElementIdentity.newBuilder().setId(id).setName(name).build();
	}

	private static DmnIrVariable variable(String id, String name, DmnIrScalarType type) {
		return DmnIrVariable.newBuilder().setId(id).setName(name).setType(type).build();
	}

	private static DmnIrReference ref(String namespace, String id) {
		return DmnIrReference.newBuilder().setModelNamespace(namespace).setElementId(id).build();
	}

	private static DmnIrDecision decision(String id, String name, DmnIrReference target) {
		return DmnIrDecision.newBuilder().setIdentity(identity(id, name)).setVariable(variable(id + "-var", id, NUMBER))
				.addDependencies(target)
				.setExpression(DmnIrExpression.newBuilder().setType(NUMBER).setGlobalReference(target)).build();
	}

	private static DmnIrArtifact.Builder header() {
		return DmnIrArtifact.newBuilder().setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(0))
				.setSemanticsVersion(DmnIrArtifactReader.SEMANTICS_VERSION)
				.addRequiredCapabilities(DmnIrArtifactReader.SCALAR_CAPABILITY).setRootModelNamespace(ROOT);
	}

	private static DmnIrArtifact linked() {
		var root = DmnIrModel.newBuilder().setIdentity(identity("root", "Root model")).setNamespace(ROOT)
				.addInputs(DmnIrInput.newBuilder().setIdentity(identity("amount", "Amount"))
						.setVariable(variable("amount-var", "amount", NUMBER)))
				.addImports(DmnIrImport.newBuilder().setModelNamespace(RATES).setName("rates")
						.setLocationUri("https://example.invalid/not-fetched.dmn"))
				.addDecisions(decision("first", "First", ref(ROOT, "amount")))
				.addDecisions(decision("second", "Second", ref(ROOT, "first")))
				.addDecisions(decision("third", "Third", ref(RATES, "amount")));
		var rates = DmnIrModel.newBuilder().setIdentity(identity("rates", "Rates model")).setNamespace(RATES)
				.addInputs(DmnIrInput.newBuilder().setIdentity(identity("amount", "Rate amount"))
						.setVariable(variable("rate-var", "rate", NUMBER)));
		return header().addModels(root).addModels(rates).addEvaluationOrder(ref(ROOT, "first"))
				.addEvaluationOrder(ref(ROOT, "second")).addEvaluationOrder(ref(ROOT, "third")).build();
	}

	private static DmnIrArtifact scalar(DmnIrLiteral literal, DmnIrScalarType type) {
		var decision = DmnIrDecision.newBuilder().setIdentity(identity("result", "Result"))
				.setVariable(variable("result-var", "result", type))
				.setExpression(DmnIrExpression.newBuilder().setType(type).setLiteral(literal));
		return header().addModels(
				DmnIrModel.newBuilder().setIdentity(identity("root", "Root")).setNamespace(ROOT).addDecisions(decision))
				.addEvaluationOrder(ref(ROOT, "result")).build();
	}

	private static DmnIrDecimal decimal(String coefficient, int scale) {
		return DmnIrDecimal.newBuilder().setUnscaledValue(coefficient).setScale(scale).build();
	}

	private static DmnIrArtifact replace(DmnIrArtifact artifact, int index, DmnIrDecision decision) {
		return artifact.toBuilder().setModels(0, artifact.getModels(0).toBuilder().setDecisions(index, decision))
				.build();
	}
}
