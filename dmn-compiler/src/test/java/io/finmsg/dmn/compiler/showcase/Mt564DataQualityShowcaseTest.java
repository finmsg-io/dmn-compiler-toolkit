package io.finmsg.dmn.compiler.showcase;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiledModel;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnModelResolver;
import io.finmsg.dmn.compiler.FilesystemDmnModelResolver;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Mt564DataQualityShowcaseTest {

	private final DmnCompiler compiler = new DmnCompiler();
	private final Path modelsDir = Paths.get("../dmn-models/src/main/resources/models/data-quality");

	private DmnCompiledModel compiledModel;

	@BeforeEach
	void setUp() throws IOException {
		Path rootFile = modelsDir.resolve("swift-mt564-dqm.dmn");
		DmnSource rootSource = new DmnSource(DmnSourceId.of(rootFile.toUri().toString()), Files.readAllBytes(rootFile));
		DmnModelResolver resolver = new FilesystemDmnModelResolver(modelsDir);

		DmnCompilationResult compilation = compiler.compile(rootSource, resolver);
		assertThat(compilation.isSuccess())
				.withFailMessage("Compilation failed with diagnostics: %s", compilation.diagnostics()).isTrue();

		this.compiledModel = compilation.compiledModel().orElseThrow();
	}

	@Test
	void testValidMt564Message_PreExtractedFormat() {
		Map<String, Object> message = Map.of("semeRef", "01787", "msgFunction", "NEWM", "caEvent", "ACTV");

		Object result = compiledModel.evaluateDecision("QualityReport", Map.of("Message", message));
		assertThat(result).isInstanceOf(List.class);

		@SuppressWarnings("unchecked")
		List<Object> violations = (List<Object>) result;
		assertThat(violations).isEmpty();
	}

	@Test
	void testValidMt564Message_RawProtobufTreeFormat() {
		Map<String, Object> message = Map.of("mt_std",
				Map.of("seq_A", Map.of("semeRef", "01787", "msgFunction", "NEWM", "caEvent", "ACTV")));

		Object result = compiledModel.evaluateDecision("QualityReport", Map.of("Message", message));
		assertThat(result).isInstanceOf(List.class);

		@SuppressWarnings("unchecked")
		List<Object> violations = (List<Object>) result;
		assertThat(violations).isEmpty();
	}

	@Test
	void testMissingSemeReference() {
		Map<String, Object> message = Map.of("semeRef", "", "msgFunction", "NEWM", "caEvent", "ACTV");

		Object result = compiledModel.evaluateDecision("QualityReport", Map.of("Message", message));
		assertThat(result).isInstanceOf(List.class);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> violations = (List<Map<String, Object>>) result;
		assertThat(violations).hasSize(1);
		Map<String, Object> v = violations.get(0);
		assertThat(v.get("ruleId")).isEqualTo("DQ-MT564-001");
		assertThat(v.get("severity")).isEqualTo("ERROR");
		assertThat(v.get("path")).isEqualTo("mt_std.seq[A].rptn[20C::SEME].Ref");
		assertThat(v.get("actualValue")).isEqualTo("blank");
	}

	@Test
	void testInvalidMessageFunction() {
		Map<String, Object> message = Map.of("semeRef", "01787", "msgFunction", "INVALID_FUNC", "caEvent", "ACTV");

		Object result = compiledModel.evaluateDecision("QualityReport", Map.of("Message", message));
		assertThat(result).isInstanceOf(List.class);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> violations = (List<Map<String, Object>>) result;
		assertThat(violations).hasSize(1);
		Map<String, Object> v = violations.get(0);
		assertThat(v.get("ruleId")).isEqualTo("DQ-MT564-002");
		assertThat(v.get("severity")).isEqualTo("ERROR");
		assertThat(v.get("actualValue")).isEqualTo("INVALID_FUNC");
	}

	@Test
	void testInvalidCorporateActionEvent() {
		Map<String, Object> message = Map.of("semeRef", "01787", "msgFunction", "NEWM", "caEvent", "BAD_EVENT");

		Object result = compiledModel.evaluateDecision("QualityReport", Map.of("Message", message));
		assertThat(result).isInstanceOf(List.class);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> violations = (List<Map<String, Object>>) result;
		assertThat(violations).hasSize(1);
		Map<String, Object> v = violations.get(0);
		assertThat(v.get("ruleId")).isEqualTo("DQ-MT564-003");
		assertThat(v.get("severity")).isEqualTo("ERROR");
		assertThat(v.get("actualValue")).isEqualTo("BAD_EVENT");
	}

	@Test
	void testMultiViolationMessage() {
		Map<String, Object> message = Map.of("semeRef", "", "msgFunction", "INVALID_FUNC", "caEvent", "BAD_EVENT");

		Object result = compiledModel.evaluateDecision("QualityReport", Map.of("Message", message));
		assertThat(result).isInstanceOf(List.class);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> violations = (List<Map<String, Object>>) result;
		assertThat(violations).hasSize(3);
		assertThat(violations.get(0).get("ruleId")).isEqualTo("DQ-MT564-001");
		assertThat(violations.get(1).get("ruleId")).isEqualTo("DQ-MT564-002");
		assertThat(violations.get(2).get("ruleId")).isEqualTo("DQ-MT564-003");
	}
}
