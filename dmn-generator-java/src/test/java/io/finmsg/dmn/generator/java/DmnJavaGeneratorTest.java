package io.finmsg.dmn.generator.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.tools.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DmnJavaGeneratorTest {
	@Test
	void externalCallsRequireInstancePolicyAndDoNotAuthorizeOtherInstances(@TempDir Path tempDir) throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="d" name="d" namespace="urn:test">
				<decision id="result" name="result"><variable name="result"/>
				<literalExpression><text>(function(x) external {java:{class:"java.lang.Math",method signature:"abs(int)"}})(-7)</text></literalExpression>
				</decision></definitions>
				""";
		var compilation = compiler.compile(new DmnSource(DmnSourceId.of("memory:external-policy"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compilation.isSuccess()).isTrue();
		var optimized = compilation.optimizedRuntimeModel().orElseThrow();
		var policy = io.finmsg.dmn.runtime.ExternalJavaPolicy
				.allowOnly(Set.of(new io.finmsg.dmn.runtime.ExternalJavaPolicy.Method("java.lang.Math", "abs(int)")));
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtime.evaluate(optimized.model(), Map.of()))
				.hasMessageStartingWith("EXTERNAL_JAVA_DENIED:");
		assertThat(new DmnRuntime(policy).evaluate(optimized.model(), Map.of())
				.decisionValue(optimized.model().decisions().getFirst().id())).isEqualTo(BigDecimal.valueOf(7));
		var generated = generator.generate(optimized);
		var type = compileJavaSource(tempDir, generated.mainClassName(), generated.mainSource());
		var denied = (GeneratedDecisionEngine) type.getDeclaredConstructor().newInstance();
		var approved = (GeneratedDecisionEngine) type
				.getDeclaredConstructor(io.finmsg.dmn.runtime.ExternalJavaPolicy.class).newInstance(policy);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> denied.evaluate(new Object[0]))
				.hasMessageStartingWith("EXTERNAL_JAVA_DENIED:");
		int slot = optimized.model().decisions().getFirst().resultSlot();
		assertThat(approved.evaluate(new Object[0])[slot]).isEqualTo(BigDecimal.valueOf(7));
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> denied.evaluate(new Object[0]))
				.hasMessageStartingWith("EXTERNAL_JAVA_DENIED:");
	}

	private final DmnCompiler compiler = new DmnCompiler();
	private final DmnRuntime runtime = new DmnRuntime();
	private final DmnJavaGenerator generator = new DmnJavaGenerator();

	@Test
	void comparatorCannotSwallowExternalPolicyDenial(@TempDir Path tempDir) throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="d" name="d" namespace="urn:test">
				<decision id="result" name="result"><variable name="result"/>
				<literalExpression><text>sort([2,1], function(a,b) (function(x) external {java:{class:"java.lang.Math",method signature:"abs(int)"}})(a) > 0)</text></literalExpression>
				</decision></definitions>
				""";
		var compilation = compiler.compile(new DmnSource(DmnSourceId.of("memory:comparator-policy"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compilation.isSuccess()).isTrue();
		var optimized = compilation.optimizedRuntimeModel().orElseThrow();
		var generated = generator.generate(optimized);
		var type = compileJavaSource(tempDir, generated.mainClassName(), generated.mainSource());
		var engine = (GeneratedDecisionEngine) type.getDeclaredConstructor().newInstance();
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.evaluate(new Object[0]))
				.isInstanceOf(io.finmsg.dmn.runtime.ExternalJavaDeniedException.class);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtime.evaluate(optimized.model(), Map.of()))
				.isInstanceOf(io.finmsg.dmn.runtime.ExternalJavaDeniedException.class);
	}

	@Test
	void generatesJavaSourceAndAssertsParityWithInterpreter(@TempDir Path tempDir) throws Exception {
		Path scenarioDir = Path.of("../dmn-compiler/src/test/resources/corpus/p2-09-lending-eligibility");
		Path rootFile = scenarioDir.resolve("credit-application.dmn");
		DmnSource rootSource = new DmnSource(DmnSourceId.of(rootFile.toUri().toString()), Files.readAllBytes(rootFile));
		DmnModelResolver resolver = new FilesystemDmnModelResolver(scenarioDir);

		DmnCompilationResult compilation = compiler.compile(rootSource, resolver);
		assertThat(compilation.isSuccess()).isTrue();

		DmnCompiledModel compiledModel = compilation.compiledModel().orElseThrow();
		RuntimeOptimizedModel optimized = compilation.optimizedRuntimeModel().orElseThrow();

		// 1. Generate Java Source
		DmnJavaGeneratorOptions options = DmnJavaGeneratorOptions.of("io.finmsg.dmn.generated",
				"CreditAppGeneratedModel");
		DmnJavaGeneratorResult genResult = generator.generate(optimized, options);
		assertThat(genResult.mainSource()).contains("public final class CreditAppGeneratedModel");

		// 2. Dynamically compile generated Java source using standard JDK JavaCompiler
		Class<?> compiledClass = compileJavaSource(tempDir, genResult.mainClassName(), genResult.mainSource());
		GeneratedDecisionEngine engine = (GeneratedDecisionEngine) compiledClass.getDeclaredConstructor().newInstance();

		// 3. Prepare Input Slots for Lending Eligibility Scenario
		// Input slots: RequestedAmount, Applicant map (score, income, monthlyDebt)
		Map<String, Object> inputs = Map.of("RequestedAmount", new BigDecimal("10000"), "Applicant", Map.of("score",
				new BigDecimal("750"), "income", new BigDecimal("50000"), "monthlyDebt", new BigDecimal("1000")));

		// Evaluate using Compiled Model Facade
		Object facadeResult = compiledModel.evaluateDecision("LoanApproval", inputs);
		assertThat(facadeResult).isEqualTo("APPROVED");

		// Evaluate using generated Java Engine
		Object[] inputSlots = new Object[optimized.model().valueSlotCount()];
		Map<Integer, Object> runtimeInputMap = new HashMap<>();

		// Map inputs to slot 0 (Applicant context) and slot 4 (RequestedAmount number)
		inputSlots[0] = inputs.get("Applicant");
		runtimeInputMap.put(0, inputs.get("Applicant"));

		inputSlots[4] = new BigDecimal("10000");
		runtimeInputMap.put(4, new BigDecimal("10000"));

		Object[] outputSlots = engine.evaluate(inputSlots);
		assertThat(outputSlots).isNotNull();
		assertThat(outputSlots.length).isEqualTo(optimized.model().valueSlotCount());

		// 4. Assert 100% Output Parity between DmnRuntime interpreter and generated
		// Java class
		DmnEvaluationResult interpResult = runtime.evaluate(optimized.model(), runtimeInputMap);

		Set<Integer> bkmSlots = new HashSet<>();
		optimized.model().businessKnowledgeModels().forEach(bkm -> bkmSlots.add(bkm.resultSlot()));
		for (int i = 0; i < outputSlots.length; i++) {
			if (bkmSlots.contains(i)) {
				continue;
			}
			Object interpVal = interpResult.value(i);
			Object genVal = outputSlots[i];
			if (interpVal != null && !interpVal.getClass().getName().contains("Lambda")) {
				assertThat(genVal).isEqualTo(interpVal);
			}
		}
	}

	@Test
	void testBetweenExpressionEmissionTrivialAndNonTrivial() {
		RuntimeType numType = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
		RuntimeType boolType = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);

		// Trivial operand: local reference
		RuntimeLocalReference valRef = new RuntimeLocalReference(0, 0, numType);
		RuntimeConstant lower = new RuntimeConstant(RuntimeConstantKind.NUMBER, "10", numType);
		RuntimeConstant upper = new RuntimeConstant(RuntimeConstantKind.NUMBER, "20", numType);
		RuntimeBetweenExpression trivialBetween = new RuntimeBetweenExpression(valRef, lower, upper, boolType);

		String emittedTrivial = JavaExpressionEmitter.emit(trivialBetween);
		assertThat(emittedTrivial).contains(
				"local_0 != null && compare(local_0, new BigDecimal(\"10\")) >= 0 && compare(local_0, new BigDecimal(\"20\")) <= 0");
		assertThat(emittedTrivial).doesNotContain("Supplier");

		// Trivial operand: slot value reference
		RuntimeValueReference slotRef = new RuntimeValueReference(0, numType);
		RuntimeBetweenExpression slotBetween = new RuntimeBetweenExpression(slotRef, lower, upper, boolType);
		String emittedSlot = JavaExpressionEmitter.emit(slotBetween);
		assertThat(emittedSlot).contains(
				"slots[0] != null && compare(slots[0], new BigDecimal(\"10\")) >= 0 && compare(slots[0], new BigDecimal(\"20\")) <= 0");
		assertThat(emittedSlot).doesNotContain("Supplier");

		// Non-trivial operand: binary addition (slots[0] + 5)
		RuntimeConstant five = new RuntimeConstant(RuntimeConstantKind.NUMBER, "5", numType);
		RuntimeBinaryExpression addExpr = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, valRef, five, numType);
		RuntimeBetweenExpression nonTrivialBetween = new RuntimeBetweenExpression(addExpr, lower, upper, boolType);

		String emittedNonTrivial = JavaExpressionEmitter.emit(nonTrivialBetween);
		assertThat(emittedNonTrivial).contains("java.util.function.Supplier<Boolean>");
		assertThat(emittedNonTrivial).contains("_btnVal_");
	}

	private Class<?> compileJavaSource(Path tempDir, String fqcn, String sourceCode) throws Exception {
		String relativePath = fqcn.replace('.', '/') + ".java";
		Path sourceFile = tempDir.resolve(relativePath);
		Files.createDirectories(sourceFile.getParent());
		Files.writeString(sourceFile, sourceCode, java.nio.charset.StandardCharsets.UTF_8);

		JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
		assertThat(javaCompiler).withFailMessage("JDK JavaCompiler not available").isNotNull();

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		StandardJavaFileManager fileManager = javaCompiler.getStandardFileManager(diagnostics, null, null);
		Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(sourceFile.toFile());

		JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, diagnostics,
				List.of("-d", tempDir.toString(), "-encoding", "UTF-8"), null, compilationUnits);
		boolean success = task.call();
		fileManager.close();

		if (!success) {
			System.err.println("=== FAILED GENERATED SOURCE CODE ===");
			System.err.println(sourceCode);
			System.err.println("====================================");
		}
		assertThat(success).withFailMessage("Generated Java compilation failed: %s", diagnostics.getDiagnostics())
				.isTrue();

		URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
				getClass().getClassLoader());
		return classLoader.loadClass(fqcn);
	}
}
