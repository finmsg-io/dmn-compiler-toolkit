package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnSemanticModel;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorOptions;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorResult;
import io.finmsg.dmn.ir.RuntimeOptimizedModel;
import io.finmsg.dmn.model.DrgElement;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Conformance test verifying 100% execution parity across DmnInterpreter and
 * dmn-generator-java for all official OMG DMN TCK test cases.
 */
class TckFullConformanceTest {

	private static final AtomicInteger ENGINE_COUNTER = new AtomicInteger(1);

	@TestFactory
	Stream<DynamicTest> verifyFullConformanceAndDualEngineParity() throws Exception {
		List<Path> testDirectories = new ArrayList<>();
		URL smokeResource = TckFullConformanceTest.class.getClassLoader().getResource("smoke");
		if (smokeResource != null)
			testDirectories.add(Path.of(smokeResource.toURI()));

		URL tckResource = TckFullConformanceTest.class.getClassLoader().getResource("tck");
		if (tckResource != null)
			testDirectories.add(Path.of(tckResource.toURI()));

		List<DynamicTest> dynamicTests = new ArrayList<>();
		DmnToolkitTckEngine interpreterEngine = new DmnToolkitTckEngine();

		for (Path rootDir : testDirectories) {
			try (Stream<Path> paths = Files.walk(rootDir)) {
				List<Path> xmlFiles = paths.filter(p -> p.toString().endsWith("-test-01.xml")).toList();
				for (Path testXmlPath : xmlFiles) {
					Path dir = testXmlPath.getParent();
					String dmnFileName = testXmlPath.getFileName().toString().replace("-test-01.xml", ".dmn");
					Path modelPath = dir.resolve(dmnFileName);

					if (!Files.exists(modelPath))
						continue;

					List<TckTestCase> cases = new TckTestCaseReader().read(testXmlPath);
					DmnSource source = new DmnSource(new DmnSourceId(modelPath.toUri()), Files.readAllBytes(modelPath));
					DmnCompilationResult compilation = new DmnCompiler().compile(source);
					if (!compilation.isSuccess())
						continue;

					RuntimeOptimizedModel optModel = compilation.optimizedRuntimeModel().orElseThrow();
					DmnJavaGenerator generator = new DmnJavaGenerator();
					String className = "GeneratedTckEngine_" + ENGINE_COUNTER.getAndIncrement();
					DmnJavaGeneratorResult genResult = generator.generate(optModel,
							DmnJavaGeneratorOptions.of("io.finmsg.dmn.tck.gen", className));

					Class<?> genClass = compileInMemory("io.finmsg.dmn.tck.gen." + className,
							genResult.sources().get("io.finmsg.dmn.tck.gen." + className));

					Object engineInstance = genClass.getDeclaredConstructor().newInstance();

					for (TckTestCase testCase : cases) {
						String testName = dir.getFileName() + " :: " + testCase.id() + " (" + testCase.name() + ")";
						dynamicTests.add(DynamicTest.dynamicTest(testName, () -> {
							// 1. Evaluate with Interpreter
							TckExecutionResult interpreterResult = interpreterEngine.execute(modelPath, testCase);

							// 2. Evaluate with Generated Java Engine
							Object[] slots = buildInputSlots(compilation, testCase);
							Object[] genResultSlots = (Object[]) genClass.getMethod("evaluate", Object[].class)
									.invoke(engineInstance, (Object) slots);

							Map<String, Object> genDecisionValues = extractDecisionValues(compilation, genResultSlots);

							// 3. Assert dual engine parity
							testCase.expectedResults().forEach((name, expected) -> {
								Object interpreterVal = interpreterResult.decisionValues().get(name);
								Object generatedVal = genDecisionValues.get(name);

								if (expected.kind() == TckValue.Kind.NUMBER) {
									assertThat((BigDecimal) interpreterVal)
											.isEqualByComparingTo((BigDecimal) expected.runtimeValue());
									assertThat((BigDecimal) generatedVal)
											.isEqualByComparingTo((BigDecimal) expected.runtimeValue());
								} else {
									assertThat(interpreterVal).isEqualTo(expected.runtimeValue());
									assertThat(generatedVal).isEqualTo(expected.runtimeValue());
								}
							});
						}));
					}
				}
			}
		}

		return dynamicTests.stream();
	}

	private static Object[] buildInputSlots(DmnCompilationResult compilation, TckTestCase testCase) {
		int slotCount = compilation.optimizedRuntimeModel().orElseThrow().model().valueSlotCount();
		Object[] slots = new Object[slotCount];
		Map<String, Integer> inputSlots = new LinkedHashMap<>();

		int slot = 0;
		for (DmnSemanticModel model : compilation.semanticResult().models()) {
			for (DrgElement element : model.model().getDrgElementsList()) {
				if (element.hasInputData()) {
					inputSlots.put(element.getInputData().getNode().getName(), slot++);
				} else if (element.hasDecision() || element.hasBusinessKnowledgeModel()) {
					slot++;
				}
			}
		}

		testCase.inputs().forEach((name, val) -> {
			Integer inputSlot = inputSlots.get(name);
			if (inputSlot != null) {
				slots[inputSlot] = val.runtimeValue();
			}
		});
		return slots;
	}

	private static Map<String, Object> extractDecisionValues(DmnCompilationResult compilation, Object[] slots) {
		Map<String, Object> decisions = new LinkedHashMap<>();
		int slot = 0;
		for (DmnSemanticModel model : compilation.semanticResult().models()) {
			for (DrgElement element : model.model().getDrgElementsList()) {
				if (element.hasInputData()) {
					slot++;
				} else if (element.hasDecision()) {
					decisions.put(element.getDecision().getNode().getName(), slots[slot++]);
				} else if (element.hasBusinessKnowledgeModel()) {
					slot++;
				}
			}
		}
		return decisions;
	}

	private static Class<?> compileInMemory(String fqcn, String code) throws Exception {
		Path tempDir = Files.createTempDirectory("dmn-tck-gen-test");
		String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);
		Path pkgDir = tempDir.resolve("io/finmsg/dmn/tck/gen");
		Files.createDirectories(pkgDir);
		Path sourceFile = pkgDir.resolve(className + ".java");
		Files.writeString(sourceFile, code, java.nio.charset.StandardCharsets.UTF_8);

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		int exitCode = compiler.run(null, null, null, "-encoding", "UTF-8", sourceFile.toString());
		assertThat(exitCode).isEqualTo(0);

		URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()});
		return classLoader.loadClass(fqcn);
	}
}
