package io.finmsg.dmn.benchmark.provider;

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

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registry and provider for reference DMN models used in benchmarks. Discovers
 * and compiles models into both Runtime IR and generated Java bytecode.
 */
public final class ReferenceModelRegistry {

	private static final AtomicInteger COUNTER = new AtomicInteger(1);

	public record CompiledModelHolder(String modelName, DmnCompilationResult compilationResult,
			Object generatedEngineInstance, Method evaluateMethod, Map<String, Integer> inputSlotMapping) {
	}

	public static CompiledModelHolder loadFromClasspath(String resourcePath) throws Exception {
		URL resource = ReferenceModelRegistry.class.getClassLoader().getResource(resourcePath);
		if (resource == null) {
			throw new IllegalArgumentException("DMN Resource not found on classpath: " + resourcePath);
		}
		byte[] bytes;
		try (InputStream is = resource.openStream()) {
			bytes = is.readAllBytes();
		}
		String modelName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1).replace(".dmn", "");
		return compile(modelName,
				new DmnSource(new DmnSourceId(java.net.URI.create("urn:resource:" + modelName)), bytes));
	}

	public static CompiledModelHolder compile(String modelName, DmnSource source) throws Exception {
		DmnCompilationResult compilation = new DmnCompiler().compile(source);
		if (!compilation.isSuccess()) {
			throw ancientCompilationError(modelName, compilation);
		}

		RuntimeOptimizedModel optModel = compilation.optimizedRuntimeModel().orElseThrow();
		DmnJavaGenerator generator = new DmnJavaGenerator();
		String className = "BenchEngine_" + modelName.replaceAll("[^a-zA-Z0-9_]", "_") + "_"
				+ COUNTER.getAndIncrement();
		String pkgName = "io.finmsg.dmn.benchmark.gen";
		String fqcn = pkgName + "." + className;

		DmnJavaGeneratorResult genResult = generator.generate(optModel, DmnJavaGeneratorOptions.of(pkgName, className));
		Class<?> genClass = compileInMemory(fqcn, genResult.sources().get(fqcn));
		Object engineInstance = genClass.getDeclaredConstructor().newInstance();
		Method evalMethod = genClass.getMethod("evaluate", Object[].class);

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

		return new CompiledModelHolder(modelName, compilation, engineInstance, evalMethod, inputSlots);
	}

	public static Map<Integer, Object> buildInterpreterSlotMap(CompiledModelHolder holder, Map<String, Object> inputs) {
		Map<Integer, Object> slotMap = new LinkedHashMap<>();
		holder.compilationResult().optimizedRuntimeModel().orElseThrow().model().inputs()
				.forEach(input -> slotMap.put(input.valueSlot(), null));
		inputs.forEach((name, val) -> {
			Integer slot = holder.inputSlotMapping().get(name);
			if (slot != null) {
				slotMap.put(slot, val);
			}
		});
		return slotMap;
	}

	public static Object[] buildInputSlots(CompiledModelHolder holder, Map<String, Object> inputs) {
		int slotCount = holder.compilationResult().optimizedRuntimeModel().orElseThrow().model().valueSlotCount();
		Object[] slots = new Object[slotCount];
		inputs.forEach((name, val) -> {
			Integer slot = holder.inputSlotMapping().get(name);
			if (slot != null) {
				slots[slot] = val;
			}
		});
		return slots;
	}

	private static Class<?> compileInMemory(String fqcn, String code) throws Exception {
		Path tempDir = Files.createTempDirectory("dmn-bench-gen");
		String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);
		Path pkgDir = tempDir.resolve("io/finmsg/dmn/benchmark/gen");
		Files.createDirectories(pkgDir);
		Path sourceFile = pkgDir.resolve(className + ".java");
		Files.writeString(sourceFile, code);

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		int exitCode = compiler.run(null, null, null, sourceFile.toString());
		if (exitCode != 0) {
			throw new IllegalStateException("InMemory Java compilation failed for " + fqcn);
		}

		URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
				ReferenceModelRegistry.class.getClassLoader());
		return classLoader.loadClass(fqcn);
	}

	private static IllegalStateException ancientCompilationError(String name, DmnCompilationResult result) {
		return new IllegalStateException(
				"Compilation failed for reference model " + name + ": " + result.diagnostics());
	}
}
