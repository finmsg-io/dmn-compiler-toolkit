package io.finmsg.dmn.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiledModel;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorOptions;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorResult;
import io.finmsg.dmn.generator.java.GeneratedDecisionEngine;
import io.finmsg.dmn.ir.RuntimeOptimizedModel;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end smoke test validating Java code generation, compilation via
 * standard javac, and execution via GeneratedDecisionEngine with 100% output
 * parity against DmnRuntime.
 */
class ConsumerJavaCodegenSmokeTest {

	private final DmnCompiler compiler = new DmnCompiler();
	private final DmnRuntime runtime = new DmnRuntime();
	private final DmnJavaGenerator generator = new DmnJavaGenerator();

	private DmnCompiledModel compiledModel;
	private RuntimeOptimizedModel optimizedModel;

	@BeforeEach
	void setUp() throws Exception {
		try (InputStream is = getClass().getResourceAsStream("/models/loan-eligibility.dmn")) {
			assertThat(is).withFailMessage("Sample model loan-eligibility.dmn must exist on classpath").isNotNull();
			byte[] bytes = is.readAllBytes();
			DmnSource source = new DmnSource(DmnSourceId.of("classpath:/models/loan-eligibility.dmn"), bytes);
			DmnCompilationResult result = compiler.compile(source);
			assertThat(result.isSuccess()).withFailMessage("Compilation failed: %s", result.diagnostics()).isTrue();
			compiledModel = result.compiledModel().orElseThrow();
			optimizedModel = result.optimizedRuntimeModel().orElseThrow();
		}
	}

	@Test
	@DisplayName("Generates Java code, compiles via standard JDK javac, and matches interpreter output exactly")
	void generatesCompilesAndExecutesJavaEngine(@TempDir Path tempDir) throws Exception {
		// 1. Generate Java source code
		DmnJavaGeneratorOptions options = DmnJavaGeneratorOptions.of("com.example.generated",
				"LoanEligibilityDecisionEngine");
		DmnJavaGeneratorResult genResult = generator.generate(optimizedModel, options);

		assertThat(genResult.mainSource()).contains("public final class LoanEligibilityDecisionEngine");
		assertThat(genResult.mainSource()).contains("implements io.finmsg.dmn.generator.java.GeneratedDecisionEngine");

		// 2. Compile generated Java source using standard JDK JavaCompiler
		Class<?> engineClass = compileJavaSource(tempDir, genResult.mainClassName(), genResult.mainSource());
		GeneratedDecisionEngine engine = (GeneratedDecisionEngine) engineClass.getDeclaredConstructor().newInstance();

		// 3. Test scenarios with input slot mapping
		Map<String, Integer> inSlots = compiledModel.inputSlots();
		Map<String, Integer> decSlots = compiledModel.decisionSlots();

		int offerSlot = decSlots.get("LoanOffer");
		int riskSlot = decSlots.get("RiskCategory");

		// Scenario A: Low Risk / Premium
		Object[] slotsA = evaluateEngine(engine, inSlots,
				Map.of("ApplicantName", "Alice Smith", "CreditScore", new BigDecimal("780"), "AnnualIncome",
						new BigDecimal("120000"), "LoanAmount", new BigDecimal("10000"), "ExistingDebts",
						new BigDecimal("500")));
		assertThat(slotsA[riskSlot]).isEqualTo("LOW");
		assertThat(slotsA[offerSlot]).isEqualTo("APPROVED_PREMIUM");

		// Scenario B: Medium Risk / Standard
		Object[] slotsB = evaluateEngine(engine, inSlots,
				Map.of("ApplicantName", "Bob Jones", "CreditScore", new BigDecimal("710"), "AnnualIncome",
						new BigDecimal("60000"), "LoanAmount", new BigDecimal("20000"), "ExistingDebts",
						new BigDecimal("1000")));
		assertThat(slotsB[riskSlot]).isEqualTo("MEDIUM");
		assertThat(slotsB[offerSlot]).isEqualTo("APPROVED_STANDARD");

		// Scenario C: High Risk / Subprime
		Object[] slotsC = evaluateEngine(engine, inSlots,
				Map.of("ApplicantName", "Charlie Brown", "CreditScore", new BigDecimal("630"), "AnnualIncome",
						new BigDecimal("60000"), "LoanAmount", new BigDecimal("20000"), "ExistingDebts",
						new BigDecimal("1200")));
		assertThat(slotsC[riskSlot]).isEqualTo("HIGH");
		assertThat(slotsC[offerSlot]).isEqualTo("APPROVED_SUBPRIME");

		// Scenario D: Rejected
		Object[] slotsD = evaluateEngine(engine, inSlots,
				Map.of("ApplicantName", "Dave Miller", "CreditScore", new BigDecimal("580"), "AnnualIncome",
						new BigDecimal("60000"), "LoanAmount", new BigDecimal("20000"), "ExistingDebts",
						new BigDecimal("1200")));
		assertThat(slotsD[riskSlot]).isEqualTo("REJECTED");
		assertThat(slotsD[offerSlot]).isEqualTo("DECLINED");

		// 4. Validate exact parity across ALL slots against DmnRuntime interpreter
		Map<Integer, Object> runtimeInputMap = new HashMap<>();
		inSlots.forEach((name, slot) -> runtimeInputMap.put(slot,
				Map.of("ApplicantName", "Alice Smith", "CreditScore", new BigDecimal("780"), "AnnualIncome",
						new BigDecimal("120000"), "LoanAmount", new BigDecimal("10000"), "ExistingDebts",
						new BigDecimal("500")).get(name)));

		DmnEvaluationResult interpResult = runtime.evaluate(optimizedModel.model(), runtimeInputMap);
		for (int i = 0; i < slotsA.length; i++) {
			Object interpVal = interpResult.value(i);
			Object genVal = slotsA[i];
			if (interpVal != null) {
				assertThat(genVal).as("Slot %d output parity", i).isEqualTo(interpVal);
			}
		}
	}

	private Object[] evaluateEngine(GeneratedDecisionEngine engine, Map<String, Integer> inSlots,
			Map<String, Object> inputs) {
		Object[] inputSlots = new Object[optimizedModel.model().valueSlotCount()];
		inputs.forEach((name, val) -> {
			Integer slot = inSlots.get(name);
			if (slot != null) {
				inputSlots[slot] = val;
			}
		});
		return engine.evaluate(inputSlots);
	}

	private Class<?> compileJavaSource(Path tempDir, String fqcn, String sourceCode) throws Exception {
		String relativePath = fqcn.replace('.', '/') + ".java";
		Path sourceFile = tempDir.resolve(relativePath);
		Files.createDirectories(sourceFile.getParent());
		Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);

		JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
		assertThat(javaCompiler).withFailMessage("JDK JavaCompiler not available in running JVM").isNotNull();

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		try (StandardJavaFileManager fileManager = javaCompiler.getStandardFileManager(diagnostics, null,
				StandardCharsets.UTF_8)) {
			Iterable<? extends JavaFileObject> compilationUnits = fileManager
					.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));

			String classpath = System.getProperty("java.class.path");
			List<String> options = List.of("-d", tempDir.toString(), "-classpath", classpath);

			JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, diagnostics, options, null,
					compilationUnits);
			boolean success = Boolean.TRUE.equals(task.call());
			assertThat(success).withFailMessage("Compilation failed: %s", diagnostics.getDiagnostics()).isTrue();
		}

		URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
				getClass().getClassLoader());
		return classLoader.loadClass(fqcn);
	}
}
