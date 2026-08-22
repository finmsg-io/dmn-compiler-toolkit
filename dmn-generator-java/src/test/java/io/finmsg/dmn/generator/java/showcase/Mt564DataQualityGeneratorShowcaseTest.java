package io.finmsg.dmn.generator.java.showcase;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiledModel;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnModelResolver;
import io.finmsg.dmn.compiler.FilesystemDmnModelResolver;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorOptions;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorResult;
import io.finmsg.dmn.ir.RuntimeOptimizedModel;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Mt564DataQualityGeneratorShowcaseTest {

	private final DmnCompiler compiler = new DmnCompiler();
	private final DmnRuntime runtime = new DmnRuntime();
	private final DmnJavaGenerator generator = new DmnJavaGenerator();
	private final Path modelsDir = Paths.get("../dmn-models/src/main/resources/models/data-quality");

	private DmnCompiledModel compiledModel;
	private RuntimeOptimizedModel optimizedModel;

	@BeforeEach
	void setUp() throws IOException {
		Path rootFile = modelsDir.resolve("swift-mt564-dqm.dmn");
		DmnSource rootSource = new DmnSource(DmnSourceId.of(rootFile.toUri().toString()), Files.readAllBytes(rootFile));
		DmnModelResolver resolver = new FilesystemDmnModelResolver(modelsDir);

		DmnCompilationResult compilation = compiler.compile(rootSource, resolver);
		assertThat(compilation.isSuccess())
				.withFailMessage("Compilation failed with diagnostics: %s", compilation.diagnostics()).isTrue();

		this.compiledModel = compilation.compiledModel().orElseThrow();
		this.optimizedModel = compilation.optimizedRuntimeModel().orElseThrow();
	}

	@Test
	void testInterpreterAndGeneratedJavaParity(@TempDir Path tempDir) throws Exception {
		DmnJavaGeneratorOptions options = DmnJavaGeneratorOptions.of("io.finmsg.dmn.generated",
				"Mt564DqmGeneratedModel");
		DmnJavaGeneratorResult genResult = generator.generate(optimizedModel, options);
		assertThat(genResult.mainSource()).contains("public final class Mt564DqmGeneratedModel");

		Class<?> compiledClass = compileJavaSource(tempDir, genResult.mainClassName(), genResult.mainSource());
		Object instance = compiledClass.getDeclaredConstructor().newInstance();
		Method evalMethod = compiledClass.getMethod("evaluate", Object[].class);

		Map<String, Object> namedInputs = Map.of("Message",
				Map.of("semeRef", "", "msgFunction", "INVALID_FUNC", "caEvent", "BAD_EVENT"));

		Object facadeResult = compiledModel.evaluateDecision("QualityReport", namedInputs);
		assertThat(facadeResult).isNotNull();

		// Build slot-indexed input array for the generated model using the compiled
		// model's slot mapping
		Object[] inputSlots = new Object[optimizedModel.model().valueSlotCount()];
		Map<Integer, Object> runtimeInputMap = new HashMap<>();

		// Populate all declared input slots; unmapped inputs default to null
		optimizedModel.model().inputs().forEach(input -> {
			runtimeInputMap.put(input.valueSlot(), null);
			inputSlots[input.valueSlot()] = null;
		});

		// Apply named inputs via the compiled model's slot mapping
		compiledModel.inputSlots().forEach((name, slot) -> {
			Object value = namedInputs.get(name);
			if (value != null) {
				runtimeInputMap.put(slot, value);
				inputSlots[slot] = value;
			}
		});

		Object[] outputSlots = (Object[]) evalMethod.invoke(instance, (Object) inputSlots);
		assertThat(outputSlots).isNotNull();

		DmnEvaluationResult interpResult = runtime.evaluate(optimizedModel.model(), runtimeInputMap);

		Set<Integer> bkmSlots = new HashSet<>();
		optimizedModel.model().businessKnowledgeModels().forEach(bkm -> bkmSlots.add(bkm.resultSlot()));
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

	private Class<?> compileJavaSource(Path tempDir, String fqcn, String sourceCode) throws Exception {
		String relativePath = fqcn.replace('.', '/') + ".java";
		Path sourceFile = tempDir.resolve(relativePath);
		Files.createDirectories(sourceFile.getParent());
		Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);

		JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
		assertThat(javaCompiler).withFailMessage("JDK JavaCompiler not available").isNotNull();

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		try (var fileManager = javaCompiler.getStandardFileManager(diagnostics, null, null)) {
			Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(sourceFile.toFile());
			JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, diagnostics, null, null,
					compilationUnits);
			boolean success = task.call();
			assertThat(success).withFailMessage("Compilation failed: %s", diagnostics.getDiagnostics()).isTrue();
		}

		try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
				Thread.currentThread().getContextClassLoader())) {
			return classLoader.loadClass(fqcn);
		}
	}
}
