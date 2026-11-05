package io.finmsg.dmn.compiler.dmnir;

import static org.junit.jupiter.api.Assertions.*;
import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.dmnir.proto.v0.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DmnIrScalarExpressionParityTest {
	private static final String NS = "urn:example:expressions";
	private record Example(String expression, String type) {
	}

	@Test
	void allOperatorsMatchSourceAndExportedDmnInBothCompilerModes() throws Exception {
		for (var example : List.of(new Example("-A", "number"), new Example("not(Flag)", "boolean"),
				new Example("A + B", "number"), new Example("A - B", "number"), new Example("A * B", "number"),
				new Example("A / B", "number"), new Example("A ** B", "number"), new Example("A = B", "boolean"),
				new Example("A != B", "boolean"), new Example("A < B", "boolean"), new Example("A <= B", "boolean"),
				new Example("A > B", "boolean"), new Example("A >= B", "boolean"),
				new Example("Flag and (A > B)", "boolean"), new Example("Flag or (A > B)", "boolean"),
				new Example("if A + B >= 20 then \"Yes\" else \"No\"", "string"),
				new Example("if Flag then A else B", "number"), new Example("false and (1 / 0 > 0)", "boolean"),
				new Example("true or (1 / 0 > 0)", "boolean"), new Example("if true then A else 1 / 0", "number"),
				new Example("if false then 1 / 0 else A", "number"), new Example("\"a\" < \"b\"", "boolean"),
				new Example("null = null", "boolean"), new Example("A = \"x\"", "boolean"))) {
			var source = source(example.expression(), example.type());
			io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader.VerifiedArtifact verified;
			try {
				verified = new DmnIrScalarProducer().produce(source);
			} catch (DmnIrProductionException e) {
				throw new AssertionError(example.expression() + ": " + e.diagnostics(), e);
			}
			assertEquals(1, verified.artifact().getSchemaVersion().getMinor(), example.expression());
			var adapted = new DmnIrRuntimeAdapter()
					.adapt(new DmnIrArtifactReader().read(verified.artifact().toByteArray()));
			var bundle = new DmnIrScalarDmnExporter().export(adapted.source());
			validate(bundle.documents().get(bundle.rootDocument()));
			var exported = new DmnSource(DmnSourceId.of("memory:/exported.dmn"),
					bundle.documents().get(bundle.rootDocument()).getBytes(StandardCharsets.UTF_8));
			for (var options : List.of(DmnCompilerOptions.lowered(), DmnCompilerOptions.optimized())) {
				var direct = new DmnCompiler().compile(source, new InMemoryDmnModelResolver(List.of()), options);
				var recompiled = new DmnCompiler().compile(exported, new InMemoryDmnModelResolver(List.of()), options);
				assertTrue(direct.isSuccess(), direct.diagnostics().toString());
				assertTrue(recompiled.isSuccess(), recompiled.diagnostics().toString());
				List<Map<String, Object>> cases = new ArrayList<>();
				cases.add(inputs(new BigDecimal("19.0000000000000000001"), new BigDecimal("2"), true));
				cases.add(inputs(new BigDecimal("-3"), BigDecimal.ZERO, false));
				cases.add(inputs(null, new BigDecimal("2"), null));
				for (var values : cases) {
					Map<DmnIrRuntimeAdapter.ElementId, Object> addressed = new LinkedHashMap<>();
					values.forEach((name, value) -> addressed.put(key(name), value));
					Object result = adapted.evaluate(addressed).get(key("result"));
					equal(direct.compiledModel().orElseThrow().evaluateDecision("Result", values), result);
					equal(recompiled.compiledModel().orElseThrow().evaluateDecision("Result", values), result);
				}
			}
		}
	}

	@Test
	void constantsKeepOldProfileAndUnsupportedSignaturesAreRejected() {
		var producer = new DmnIrScalarProducer();
		var old = producer.produce(source("A", "number"));
		assertEquals(0, old.artifact().getSchemaVersion().getMinor());
		assertEquals(List.of("dmnir.scalar"), old.artifact().getRequiredCapabilitiesList());
		for (var example : List.of(new Example("\"a\" + \"b\"", "string"), new Example("null + 1", "number")))
			assertEquals(DmnIrProductionException.Category.UNSUPPORTED_PROFILE,
					assertThrows(DmnIrProductionException.class,
							() -> producer.produce(source(example.expression(), example.type()))).category());
	}

	@Test
	void productionAndExportAreDeterministicAndRetainNestedDependencies() {
		var source = source("if Flag then A - B else B + A", "number");
		var producer = new DmnIrScalarProducer();
		var first = producer.produce(source);
		assertArrayEquals(first.artifact().toByteArray(), producer.produce(source).artifact().toByteArray());
		assertEquals(3, first.artifact().getModels(0).getDecisions(0).getDependenciesCount());
		var exporter = new DmnIrScalarDmnExporter();
		assertEquals(exporter.export(first).documents(), exporter.export(first).documents());
	}

	@Test
	void legacyNullTypedInputReferenceRemainsSupported() {
		var original = source("A", "null");
		String xml = new String(original.content(), StandardCharsets.UTF_8)
				.replace("<variable name=\"A\" typeRef=\"number\"/>", "<variable name=\"A\" typeRef=\"null\"/>");
		var source = new DmnSource(original.id(), xml.getBytes(StandardCharsets.UTF_8));
		var verified = new DmnIrScalarProducer().produce(source);
		assertEquals(0, verified.artifact().getSchemaVersion().getMinor());
		var adapted = new DmnIrRuntimeAdapter().adapt(verified);
		Map<DmnIrRuntimeAdapter.ElementId, Object> values = new LinkedHashMap<>();
		values.put(key("A"), null);
		values.put(key("B"), BigDecimal.ONE);
		values.put(key("Flag"), true);
		assertNull(adapted.evaluate(values).get(key("result")));
		var direct = new DmnCompiler().compile(source);
		assertTrue(direct.isSuccess(), direct.diagnostics().toString());
		assertNull(direct.compiledModel().orElseThrow().evaluateDecision("Result", inputs(null, BigDecimal.ONE, true)));
	}

	@Test
	void positiveUnaryWireExportsAsParenthesizedNumericOperand() {
		var artifact = new DmnIrScalarProducer().produce(source("A", "number")).artifact().toBuilder();
		artifact.setSchemaVersion(io.finmsg.dmn.dmnir.proto.v0.DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(1))
				.setSemanticsVersion(DmnIrArtifactReader.EXPRESSION_SEMANTICS_VERSION)
				.addRequiredCapabilities(DmnIrArtifactReader.EXPRESSION_CAPABILITY);
		var decision = artifact.getModelsBuilder(0).getDecisionsBuilder(0);
		var operand = decision.getExpression();
		decision.setExpression(io.finmsg.dmn.dmnir.proto.v0.DmnIrExpression.newBuilder().setType(operand.getType())
				.setUnary(io.finmsg.dmn.dmnir.proto.v0.DmnIrUnaryExpression.newBuilder()
						.setOperator(io.finmsg.dmn.dmnir.proto.v0.DmnIrUnaryOperator.UNARY_OPERATOR_POSITIVE)
						.setOperand(operand)));
		var verified = new DmnIrArtifactReader().read(artifact.build().toByteArray());
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		var recompiled = new DmnCompiler().compile(new DmnSource(DmnSourceId.of("memory:/positive.dmn"),
				bundle.documents().get(bundle.rootDocument()).getBytes(StandardCharsets.UTF_8)));
		assertTrue(recompiled.isSuccess(), recompiled.diagnostics().toString());
		var values = inputs(new BigDecimal("123.4500"), BigDecimal.ONE, true);
		Map<DmnIrRuntimeAdapter.ElementId, Object> addressed = new LinkedHashMap<>();
		values.forEach((k, v) -> addressed.put(key(k), v));
		equal(new DmnIrRuntimeAdapter().adapt(verified).evaluate(addressed).get(key("result")),
				recompiled.compiledModel().orElseThrow().evaluateDecision("Result", values));
	}

	@Test
	void separateJvmExecutesNewProfileWithoutSource(@TempDir Path dir) throws Exception {
		var artifact = new DmnIrScalarProducer().produce(source("if A - B >= 10 then A * B else A / B", "number"));
		var file = dir.resolve("expressions.dmnir");
		Files.write(file, artifact.artifact().toByteArray());
		var output = dir.resolve("consumer.log");
		String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
				? "java.exe"
				: "java";
		var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "-cp",
				System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
				Consumer.class.getName(), file.toString()).redirectErrorStream(true).redirectOutput(output.toFile())
				.start();
		try {
			assertTrue(process.waitFor(30, TimeUnit.SECONDS));
			assertEquals(0, process.exitValue(), Files.readString(output));
		} finally {
			process.destroyForcibly();
		}
	}

	@Test
	void wireAuthoredNullOperatorsAndConditionalPreserveExportedBehavior() {
		var nil = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NULL)
				.setLiteral(DmnIrLiteral.newBuilder().setNullValue(DmnIrNull.NULL_VALUE)).build();
		var truth = DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
				.setLiteral(DmnIrLiteral.newBuilder().setBooleanValue(true)).build();
		var operand = new DmnIrScalarProducer().produce(source("A", "number")).artifact().getModels(0).getDecisions(0)
				.getExpression();
		var expressions = List.of(
				DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
						.setUnary(DmnIrUnaryExpression.newBuilder().setOperator(DmnIrUnaryOperator.UNARY_OPERATOR_NOT)
								.setOperand(nil))
						.build(),
				DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
						.setBinary(DmnIrBinaryExpression.newBuilder()
								.setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_AND).setLeft(nil).setRight(truth))
						.build(),
				DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_BOOLEAN)
						.setBinary(DmnIrBinaryExpression.newBuilder()
								.setOperator(DmnIrBinaryOperator.BINARY_OPERATOR_OR).setLeft(nil).setRight(truth))
						.build(),
				DmnIrExpression.newBuilder().setType(DmnIrScalarType.SCALAR_TYPE_NUMBER)
						.setConditional(DmnIrConditionalExpression.newBuilder().setCondition(nil)
								.setThenExpression(operand).setElseExpression(operand))
						.build());
		for (var expression : expressions) {
			var artifact = new DmnIrScalarProducer().produce(source("A", "number")).artifact().toBuilder();
			artifact.setSchemaVersion(DmnIrSchemaVersion.newBuilder().setMajor(0).setMinor(1))
					.setSemanticsVersion(DmnIrArtifactReader.EXPRESSION_SEMANTICS_VERSION)
					.addRequiredCapabilities(DmnIrArtifactReader.EXPRESSION_CAPABILITY);
			artifact.getModelsBuilder(0).getDecisionsBuilder(0).setExpression(expression).getVariableBuilder()
					.setType(expression.getType());
			var verified = new DmnIrArtifactReader().read(artifact.build().toByteArray());
			var bundle = new DmnIrScalarDmnExporter().export(verified);
			var compiled = new DmnCompiler().compile(new DmnSource(DmnSourceId.of("memory:/nulls.dmn"),
					bundle.documents().get(bundle.rootDocument()).getBytes(StandardCharsets.UTF_8)));
			assertTrue(compiled.isSuccess(), compiled.diagnostics().toString());
			var values = inputs(new BigDecimal("42"), BigDecimal.ONE, true);
			Map<DmnIrRuntimeAdapter.ElementId, Object> addressed = new LinkedHashMap<>();
			values.forEach((k, v) -> addressed.put(key(k), v));
			equal(new DmnIrRuntimeAdapter().adapt(verified).evaluate(addressed).get(key("result")),
					compiled.compiledModel().orElseThrow().evaluateDecision("Result", values));
		}
	}

	public static class Consumer {
		public static void main(String[] args) throws Exception {
			var verified = new DmnIrArtifactReader().read(Files.readAllBytes(Path.of(args[0])));
			var result = new DmnIrRuntimeAdapter().adapt(verified)
					.evaluate(Map.of(key("A"), new BigDecimal("20"), key("B"), new BigDecimal("2"), key("Flag"), true));
			if (((BigDecimal) result.get(key("result"))).compareTo(new BigDecimal("40")) != 0)
				throw new AssertionError("Wrong artifact result");
		}
	}

	@Test
	void nestedImportedReferencesSurviveProductionAndDmnExport() throws Exception {
		String importedXml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="rates" name="Rates" namespace="urn:example:rates">
				<inputData id="A" name="Rate"><variable name="Rate" typeRef="number"/></inputData></definitions>
				""";
		var imported = new DmnSource(DmnSourceId.of("memory:/rates.dmn"), importedXml.getBytes(StandardCharsets.UTF_8));
		String xml = new String(source("A + rates.Rate", "number").content(), StandardCharsets.UTF_8).replace(
				"<inputData id=\"A\"",
				"<import name=\"rates\" namespace=\"urn:example:rates\" locationURI=\"rates.dmn\" importType=\"DMN\"/><inputData id=\"A\"")
				.replace("<literalExpression>",
						"<informationRequirement><requiredInput href=\"urn:example:rates#A\"/></informationRequirement><literalExpression>");
		var root = new DmnSource(DmnSourceId.of("memory:/expressions.dmn"), xml.getBytes(StandardCharsets.UTF_8));
		var verified = new DmnIrScalarProducer().produce(root, new InMemoryDmnModelResolver(List.of(imported)));
		var values = new DmnIrRuntimeAdapter().adapt(verified)
				.evaluate(Map.of(key("A"), BigDecimal.ONE, key("B"), BigDecimal.ONE, key("Flag"), true,
						new DmnIrRuntimeAdapter.ElementId("urn:example:rates", "A"), BigDecimal.TEN));
		assertEquals(new BigDecimal("11"), values.get(key("result")));
		var bundle = new DmnIrScalarDmnExporter().export(verified);
		List<DmnSource> sources = new ArrayList<>();
		for (var entry : bundle.documents().entrySet()) {
			validate(entry.getValue());
			sources.add(new DmnSource(DmnSourceId.of("memory:/" + entry.getKey()),
					entry.getValue().getBytes(StandardCharsets.UTF_8)));
		}
		var exportedRoot = sources.stream()
				.filter(s -> s.id().equals(DmnSourceId.of("memory:/" + bundle.rootDocument()))).findFirst()
				.orElseThrow();
		var compiled = new DmnCompiler().compile(exportedRoot, new InMemoryDmnModelResolver(sources));
		assertTrue(compiled.isSuccess(), compiled.diagnostics().toString());
		equal(values.get(key("result")), compiled.compiledModel().orElseThrow().evaluateDecision("Result",
				Map.of("A", BigDecimal.ONE, "B", BigDecimal.ONE, "Flag", true, "Rate", BigDecimal.TEN)));
	}

	private static void equal(Object expected, Object actual) {
		if (expected instanceof BigDecimal n)
			assertEquals(0, n.compareTo((BigDecimal) actual));
		else
			assertEquals(expected, actual);
	}
	private static Map<String, Object> inputs(Object a, Object b, Object flag) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("A", a);
		map.put("B", b);
		map.put("Flag", flag);
		return map;
	}
	private static DmnIrRuntimeAdapter.ElementId key(String id) {
		return new DmnIrRuntimeAdapter.ElementId(NS, id);
	}
	private static void validate(String xml) throws Exception {
		var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
		var validator = factory
				.newSchema(new java.io.File(
						DmnIrScalarExpressionParityTest.class.getResource("/dmn16-schema/DMN16.xsd").toURI()))
				.newValidator();
		validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		validator.validate(new StreamSource(new java.io.StringReader(xml)));
	}
	private static DmnSource source(String expression, String type) {
		String text = expression.replace("&", "&amp;").replace("<", "&lt;");
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20240513/MODEL/" id="model" name="Expressions" namespace="urn:example:expressions">
				<inputData id="A" name="A"><variable name="A" typeRef="number"/></inputData>
				<inputData id="B" name="B"><variable name="B" typeRef="number"/></inputData>
				<inputData id="Flag" name="Flag"><variable name="Flag" typeRef="boolean"/></inputData>
				<decision id="result" name="Result"><variable name="Result" typeRef="%s"/>
				<informationRequirement><requiredInput href="#A"/></informationRequirement>
				<informationRequirement><requiredInput href="#B"/></informationRequirement>
				<informationRequirement><requiredInput href="#Flag"/></informationRequirement>
				<literalExpression><text>%s</text></literalExpression></decision></definitions>
				"""
				.formatted(type, text);
		return new DmnSource(DmnSourceId.of("memory:/expressions.dmn"), xml.getBytes(StandardCharsets.UTF_8));
	}
}
