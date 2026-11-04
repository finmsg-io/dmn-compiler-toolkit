package io.finmsg.dmn.compiler.dmnir;

import static org.junit.jupiter.api.Assertions.*;
import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader.VerifiedArtifact;
import io.finmsg.dmn.dmnir.proto.v0.*;
import io.finmsg.dmn.frontend.xml.dmn.DmnVersion;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;

class DmnIrScalarDmnExporterTest {
	private static final String NS = "urn:example:export";

	@Test
	void artifactOnlyDefaultExportIsDmn16SchemaValidAndMatchesExecution() throws Exception {
		var verified = fixture();
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		assertEquals("model-0000.dmn", bundle.rootDocument());
		assertEquals(1, bundle.documents().size());
		assertTrue(
				bundle.documents().get(bundle.rootDocument()).contains("https://www.omg.org/spec/DMN/20240513/MODEL/"));
		validate(bundle);
		parity(verified, bundle, Map.of(key("input"), new BigDecimal("123.4500")),
				Map.of("Amount", new BigDecimal("123.4500")));
		Map<DmnIrRuntimeAdapter.ElementId, Object> portable = new LinkedHashMap<>();
		portable.put(key("input"), null);
		Map<String, Object> nativeInputs = new LinkedHashMap<>();
		nativeInputs.put("Amount", null);
		parity(verified, bundle, portable, nativeInputs);
		assertThrows(UnsupportedOperationException.class, () -> bundle.documents().clear());
	}

	@Test
	void escapesRawStringsAndPreservesNegativeScaleNumericValue() throws Exception {
		var artifact = fixture().artifact().toBuilder();
		var model = artifact.getModelsBuilder(0);
		model.addDecisions(literal("raw", "Raw", DmnIrScalarType.SCALAR_TYPE_STRING, DmnIrLiteral.newBuilder()
				.setStringValue("quotes\" slash\\ newline\n tab\t CR\r Unicode é 😀 & <").build()));
		model.addDecisions(literal("exponent", "Exponent", DmnIrScalarType.SCALAR_TYPE_NUMBER, DmnIrLiteral.newBuilder()
				.setNumberValue(DmnIrDecimal.newBuilder().setUnscaledValue("123").setScale(-30)).build()));
		artifact.addEvaluationOrder(ref(NS, "raw")).addEvaluationOrder(ref(NS, "exponent"));
		var verified = read(artifact.build());
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		validate(bundle);
		parity(verified, bundle, Map.of(key("input"), BigDecimal.ONE), Map.of("Amount", BigDecimal.ONE));
	}

	@Test
	void explicitUnsupportedVersionDoesNotChangeLegacyWriterDefault() {
		var exporter = new DmnIrScalarDmnExporter();
		for (var version : DmnVersion.values())
			if (version != DmnVersion.DMN_1_6) {
				var error = assertThrows(DmnIrExportException.class, () -> exporter.export(fixture(), version));
				assertEquals(DmnIrExportException.Category.UNSUPPORTED_VERSION, error.category());
			}
		var definitions = io.finmsg.dmn.model.Definitions.newBuilder().setNamespace(NS)
				.setNode(io.finmsg.dmn.model.Node.newBuilder().setId("legacy").setName("Legacy")).build();
		assertTrue(new String(new io.finmsg.dmn.frontend.xml.dmn.DmnWriter().write(definitions), StandardCharsets.UTF_8)
				.contains("https://www.omg.org/spec/DMN/20230324/MODEL/"));
	}

	@Test
	void outputIsStableAcrossDeclarationAndScheduleReordering() {
		var first = fixture().artifact();
		var reversed = first.toBuilder();
		var decisions = new ArrayList<>(first.getModels(0).getDecisionsList());
		Collections.reverse(decisions);
		reversed.getModelsBuilder(0).clearDecisions().addAllDecisions(decisions);
		// All fixture decisions depend only on the input or are constants.
		var schedule = new ArrayList<>(first.getEvaluationOrderList());
		Collections.reverse(schedule);
		reversed.clearEvaluationOrder().addAllEvaluationOrder(schedule);
		var exporter = new DmnIrScalarDmnExporter();
		assertEquals(exporter.export(read(first)).documents(), exporter.export(read(reversed.build())).documents());
	}

	@Test
	void rejectsInvalidXmlIdsDuplicateVariableIdsAndUnsafeNames() {
		for (String id : List.of("bad id", "1invalid", "bad:prefix")) {
			var artifact = fixture().artifact().toBuilder();
			artifact.getModelsBuilder(0).getIdentityBuilder().setId(id);
			rejected(read(artifact.build()));
		}
		for (String name : List.of("true", "a.b", "A  B", "if x", "Unicode é")) {
			var artifact = fixture().artifact().toBuilder();
			artifact.getModelsBuilder(0).getInputsBuilder(0).getIdentityBuilder().setName(name);
			artifact.getModelsBuilder(0).getInputsBuilder(0).getVariableBuilder().setName(name);
			rejected(read(artifact.build()));
		}
		var duplicate = fixture().artifact().toBuilder();
		duplicate.getModelsBuilder(0).getInputsBuilder(0).getVariableBuilder().setId("model");
		assertThrows(io.finmsg.dmn.dmnir.codec.DmnIrValidationException.class, () -> read(duplicate.build()));
		var mismatch = fixture().artifact().toBuilder();
		mismatch.getModelsBuilder(0).getInputsBuilder(0).getVariableBuilder().setName("Different");
		rejected(read(mismatch.build()));
	}

	@Test
	void rejectsInvalidXmlCharactersRatherThanEmittingBrokenXml() {
		var artifact = fixture().artifact().toBuilder();
		artifact.getModelsBuilder(0).addDecisions(literal("control", "Control", DmnIrScalarType.SCALAR_TYPE_STRING,
				DmnIrLiteral.newBuilder().setStringValue("\u0001").build()));
		artifact.addEvaluationOrder(ref(NS, "control"));
		rejected(read(artifact.build()));
		var attribute = fixture().artifact().toBuilder();
		attribute.getModelsBuilder(0).getIdentityBuilder().setName("Export\tModel");
		rejected(read(attribute.build()));
	}

	@Test
	void nullTypedDeclarationsRemainNullAfterExport() throws Exception {
		var artifact = fixture().artifact().toBuilder();
		artifact.getModelsBuilder(0).getInputsBuilder(0).getVariableBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NULL);
		var model = artifact.getModelsBuilder(0);
		var copy = model.getDecisionsBuilderList().stream().filter(d -> d.getIdentity().getId().equals("copy"))
				.findFirst().orElseThrow();
		copy.getVariableBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NULL);
		copy.getExpressionBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NULL);
		var verified = read(artifact.build());
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		validate(bundle);
		Map<DmnIrRuntimeAdapter.ElementId, Object> inputs = new LinkedHashMap<>();
		inputs.put(key("input"), null);
		Map<String, Object> named = new LinkedHashMap<>();
		named.put("Amount", null);
		parity(verified, bundle, inputs, named);
	}

	@Test
	void regeneratesImportLocationsAndPreservesCrossModelReferences() throws Exception {
		var imported = source("memory:/rates.dmn", "urn:example:rates", """
				<inputData id="input" name="Rate"><variable id="rate-var" name="Rate" typeRef="number"/></inputData>
				""");
		var root = source("memory:/root.dmn", NS, """
				<import name="rates" namespace="urn:example:rates" locationURI="rates.dmn" importType="DMN"/>
				<inputData id="input" name="Amount"><variable name="Amount" typeRef="number"/></inputData>
				<decision id="copy" name="Copy"><variable name="Copy" typeRef="number"/>
				<informationRequirement><requiredInput href="urn:example:rates#input"/></informationRequirement>
				<literalExpression><text>Rate</text></literalExpression></decision>
				""");
		var artifact = new DmnIrScalarProducer().produce(root, new InMemoryDmnModelResolver(List.of(imported)))
				.artifact().toBuilder();
		artifact.getModelsBuilder(0).getImportsBuilder(0).setLocationUri("https://example.invalid/do-not-fetch.dmn");
		var verified = read(artifact.build());
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		validate(bundle);
		assertFalse(bundle.documents().get(bundle.rootDocument()).contains("example.invalid"));
		assertTrue(bundle.documents().get(bundle.rootDocument()).contains("rates.Rate"));
		parity(verified, bundle, Map.of(key("input"), BigDecimal.ONE,
				new DmnIrRuntimeAdapter.ElementId("urn:example:rates", "input"), new BigDecimal("97")),
				Map.of("Amount", BigDecimal.ONE, "Rate", new BigDecimal("97")));
		var ambiguous = verified.artifact().toBuilder();
		ambiguous.getModelsBuilder(1).getInputsBuilder(0).getIdentityBuilder().setName("Amount");
		ambiguous.getModelsBuilder(1).getInputsBuilder(0).getVariableBuilder().setName("Amount");
		ambiguous.getModelsBuilder(0).getDecisionsBuilder(0).addDependencies(ref(NS, "input"));
		rejected(read(ambiguous.build()));
	}

	@Test
	void rejectsNamespacesThatCannotBeUsedForPortableDmnReferences() {
		for (String namespace : List.of("relative-model", "urn:example:model#fragment")) {
			var artifact = fixture().artifact().toBuilder().setRootModelNamespace(namespace).clearEvaluationOrder();
			var model = artifact.getModelsBuilder(0);
			var literal = model.getDecisionsList().stream().filter(d -> d.getIdentity().getId().equals("empty"))
					.findFirst().orElseThrow();
			model.setNamespace(namespace).clearInputs().clearDecisions().addDecisions(literal);
			artifact.addEvaluationOrder(ref(namespace, "empty"));
			rejected(read(artifact.build()));
		}
	}

	private static void rejected(VerifiedArtifact artifact) {
		assertEquals(DmnIrExportException.Category.UNREPRESENTABLE_ARTIFACT,
				assertThrows(DmnIrExportException.class, () -> new DmnIrScalarDmnExporter().export(artifact))
						.category());
	}
	private static void validate(DmnIrScalarDmnExporter.ExportedDmnBundle bundle) throws Exception {
		var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
		var schema = factory.newSchema(
				new java.io.File(DmnIrScalarDmnExporterTest.class.getResource("/dmn16-schema/DMN16.xsd").toURI()));
		for (String xml : bundle.documents().values()) {
			var validator = schema.newValidator();
			validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			validator.validate(new StreamSource(new StringReader(xml)));
		}
	}
	private static void parity(VerifiedArtifact artifact, DmnIrScalarDmnExporter.ExportedDmnBundle bundle,
			Map<DmnIrRuntimeAdapter.ElementId, ?> inputs, Map<String, Object> namedInputs) {
		List<DmnSource> sources = bundle.documents().entrySet().stream()
				.map(e -> new DmnSource(DmnSourceId.of("memory:/" + e.getKey()),
						e.getValue().getBytes(StandardCharsets.UTF_8)))
				.toList();
		var root = sources.stream().filter(s -> s.id().equals(DmnSourceId.of("memory:/" + bundle.rootDocument())))
				.findFirst().orElseThrow();
		var adapted = new DmnIrRuntimeAdapter().adapt(artifact);
		var expected = adapted.evaluate(inputs);
		for (var options : List.of(DmnCompilerOptions.lowered(), DmnCompilerOptions.optimized())) {
			var result = new DmnCompiler().compile(root, new InMemoryDmnModelResolver(sources), options);
			assertTrue(result.isSuccess(), result.diagnostics().toString());
			for (var binding : adapted.decisions().values()) {
				Object actual = result.compiledModel().orElseThrow().evaluateDecision(binding.variableName(),
						namedInputs);
				Object value = expected.get(binding.id());
				if (value instanceof BigDecimal n)
					assertEquals(0, n.compareTo((BigDecimal) actual));
				else
					assertEquals(value, actual);
			}
		}
	}
	private static VerifiedArtifact fixture() {
		return new DmnIrScalarProducer().produce(source("memory:/root.dmn", NS,
				"""
						<inputData id="input" name="Amount"><variable id="amount-var" name="Amount" typeRef="number"/></inputData>
						<decision id="copy" name="Copy"><variable name="Copy" typeRef="number"/>
						<informationRequirement><requiredInput href="#input"/></informationRequirement>
						<literalExpression><text>Amount</text></literalExpression></decision>
						<decision id="precise" name="Precise"><variable name="Precise" typeRef="number"/>
						<literalExpression><text>9007199254740993.0001</text></literalExpression></decision>
						<decision id="empty" name="Empty"><variable name="Empty" typeRef="string"/>
						<literalExpression><text>""</text></literalExpression></decision>
						<decision id="false" name="FalseValue"><variable name="FalseValue" typeRef="boolean"/>
						<literalExpression><text>false</text></literalExpression></decision>
						<decision id="null" name="Nullable"><variable name="Nullable" typeRef="number"/>
						<literalExpression><text>null</text></literalExpression></decision>
						"""));
	}
	private static DmnIrDecision literal(String id, String name, DmnIrScalarType type, DmnIrLiteral value) {
		return DmnIrDecision.newBuilder().setIdentity(DmnIrElementIdentity.newBuilder().setId(id).setName(name))
				.setVariable(DmnIrVariable.newBuilder().setName(name).setType(type))
				.setExpression(DmnIrExpression.newBuilder().setType(type).setLiteral(value)).build();
	}
	private static VerifiedArtifact read(DmnIrArtifact artifact) {
		return new DmnIrArtifactReader().read(artifact.toByteArray());
	}
	private static DmnIrReference ref(String namespace, String id) {
		return DmnIrReference.newBuilder().setModelNamespace(namespace).setElementId(id).build();
	}
	private static DmnIrRuntimeAdapter.ElementId key(String id) {
		return new DmnIrRuntimeAdapter.ElementId(NS, id);
	}
	private static DmnSource source(String uri, String namespace, String body) {
		return new DmnSource(DmnSourceId.of(uri),
				("<definitions xmlns=\"https://www.omg.org/spec/DMN/20240513/MODEL/\" id=\"model\" name=\"Export\" namespace=\""
						+ namespace + "\">" + body + "</definitions>").getBytes(StandardCharsets.UTF_8));
	}
}
