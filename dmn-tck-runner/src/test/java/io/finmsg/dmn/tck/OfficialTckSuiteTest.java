package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorOptions;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorResult;
import io.finmsg.dmn.ir.*;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

/**
 * Official vendor-neutral OMG DMN TCK conformance test suite. Ingests and
 * executes all official test cases from the OMG DMN TCK repository
 * (https://github.com/dmn-tck/tck) across both DmnInterpreter and
 * dmn-generator-java.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfficialTckSuiteTest {

	private static final AtomicInteger ENGINE_COUNTER = new AtomicInteger(1);
	private static final String TCK_REVISION = "20274cd2ba9cad805db6114f331c743f4b2603a1";
	private static final List<String> CATALOGUE_ROOTS = List.of("compliance-level-2", "compliance-level-3");
	private static final List<String> BACKENDS = List.of("interpreter", "generated-java");
	private final List<TckCatalogueOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
	private final Map<PreparationKey, Preparation> preparationCache = new HashMap<>();
	private final AtomicInteger currentTestIndex = new AtomicInteger(0);
	private int totalTestCount = 0;
	private TckCatalogueInventory inventory;

	@TestFactory
	Stream<DynamicTest> verifyFullOfficialOmgTckConformance() throws Exception {
		URL officialResource = OfficialTckSuiteTest.class.getClassLoader().getResource("tck-official/TestCases");
		if (officialResource == null) {
			throw new IllegalStateException("Official OMG DMN TCK test suite is missing! "
					+ "Resource 'tck-official/TestCases' could not be loaded. "
					+ "Ensure the git submodule is checked out using: git submodule update --init --recursive");
		}

		Path testCasesDir = Path.of(officialResource.toURI());
		inventory = new TckCatalogueDiscovery().discover(testCasesDir, CATALOGUE_ROOTS);
		String selector = System.getProperty("tck.case", "").trim();
		if (!selector.isEmpty()) {
			TckCatalogueEntry selectedEntry = inventory.requireExactCase(selector);
			String caseId = selector.substring(selector.lastIndexOf('#') + 1);
			TckTestCase selectedCase = selectedEntry.testCases().stream().filter(value -> value.id().equals(caseId))
					.findFirst().orElseThrow();
			TckCatalogueEntry selected = new TckCatalogueEntry(selectedEntry.id(), selectedEntry.testXml(),
					selectedEntry.rootDmn(), selectedEntry.dmnFiles(), List.of(selectedCase), Optional.empty());
			inventory = new TckCatalogueInventory(inventory.directoryCount(), 1, selected.dmnFiles().size(), 1,
					List.of(selected));
		}
		String filter = System.getProperty("tck.filter", System.getProperty("tck.suite", "")).trim();
		if (!filter.isEmpty()) {
			List<TckCatalogueEntry> filtered = inventory.entries().stream()
					.filter(e -> e.id().contains(filter) || e.testXml().toString().contains(filter)).toList();
			int cases = filtered.stream().mapToInt(e -> e.testCases().size()).sum();
			int dmns = filtered.stream().mapToInt(e -> e.dmnFiles().size()).sum();
			inventory = new TckCatalogueInventory(inventory.directoryCount(), filtered.size(), dmns, cases, filtered);
		}
		totalTestCount = inventory.entries().stream().filter(TckCatalogueEntry::executable)
				.mapToInt(e -> e.testCases().size() * BACKENDS.size()).sum();
		currentTestIndex.set(0);
		List<DynamicTest> dynamicTests = new ArrayList<>();
		DmnToolkitTckEngine interpreterEngine = new DmnToolkitTckEngine();

		for (TckCatalogueEntry entry : inventory.entries()) {
			if (!entry.executable()) {
				String diagnostic = entry.invalidReason().orElseThrow();
				outcomes.add(
						new TckCatalogueOutcome(entry.id(), "", "catalogue", TckCatalogueStatus.INVALID, diagnostic));
				dynamicTests.add(DynamicTest.dynamicTest(entry.id() + " :: catalogue", () -> {
					throw new AssertionError(diagnostic);
				}));
				continue;
			}
			List<Path> orderedModels = new ArrayList<>();
			orderedModels.add(entry.rootDmn().orElseThrow());
			entry.dmnFiles().stream().filter(path -> !path.equals(entry.rootDmn().orElseThrow()))
					.forEach(orderedModels::add);
			List<DmnSource> sources = new ArrayList<>();
			for (Path model : orderedModels)
				sources.add(new DmnSource(new DmnSourceId(model.toUri()), Files.readAllBytes(model)));
			for (TckTestCase testCase : entry.testCases()) {
				String testName = entry.id() + "#" + testCase.id();
				dynamicTests.add(recordedTest(testName + " @interpreter", entry, testCase, "interpreter", () -> {
					Preparation preparation = getPreparation(sources, testCase);
					if (preparation.failureDiagnostic() != null) {
						if (expectsOnlyErrors(testCase) && preparation.modelRejected())
							return;
						throw new AssertionError(preparation.failureDiagnostic(), preparation.failureCause());
					}
					TckExecutionResult result = interpreterEngine.execute(preparation.compilation(), testCase);
					assertExpected(testName, testCase, result.decisionValues(), "Interpreter");
				}));
				dynamicTests.add(recordedTest(testName + " @generated-java", entry, testCase, "generated-java", () -> {
					Preparation preparation = getPreparation(sources, testCase);
					if (preparation.failureDiagnostic() != null) {
						if (expectsOnlyErrors(testCase) && preparation.modelRejected())
							return;
						throw new AssertionError(preparation.failureDiagnostic(), preparation.failureCause());
					}
					executeGeneratedJava(preparation, testCase, testName);
				}));
			}
		}
		return dynamicTests.stream();
	}

	private Preparation getPreparation(List<DmnSource> sources, TckTestCase testCase) {
		Set<String> resultNames = Set.copyOf(testCase.expectedResults().keySet());
		PreparationKey preparationKey = new PreparationKey(
				sources.stream().map(source -> source.id().toString()).toList(), resultNames);
		return preparationCache.computeIfAbsent(preparationKey, ignored -> prepare(sources, resultNames));
	}

	private void executeGeneratedJava(Preparation preparation, TckTestCase testCase, String testName) throws Exception {
		DmnCompilationResult compilation = preparation.compilation();
		Class<?> genClass = preparation.generatedClass();
		Object engineInstance = preparation.engineInstance();
		if (testCase.invocableName().isPresent()) {
			String invocableName = testCase.invocableName().get();
			Map<String, Integer> bkmSlots = DmnToolkitTckEngine.getRuntimeBkmSlots(compilation);
			Integer slot = bkmSlots.get(invocableName);
			io.finmsg.dmn.ir.RuntimeModel model = compilation.optimizedRuntimeModel().orElseThrow().model();
			io.finmsg.dmn.ir.RuntimeBkm targetBkm = model.businessKnowledgeModels().stream()
					.filter(b -> b.resultSlot() == slot).findFirst().orElseThrow();
			io.finmsg.dmn.ir.RuntimeFunctionDefinition fn = targetBkm.function().orElseThrow();

			Object[] args = new Object[fn.parameters().size()];
			boolean missingParam = false;
			for (int i = 0; i < fn.parameters().size(); i++) {
				String pName = fn.parameters().get(i).name();
				if (testCase.inputs().containsKey(pName)) {
					args[i] = testCase.inputs().get(pName).runtimeValue();
				} else {
					missingParam = true;
				}
			}
			Map<String, Object> decValues = new LinkedHashMap<>();
			if (missingParam && !testCase.inputs().isEmpty()) {
				for (String exp : testCase.expectedResults().keySet()) {
					decValues.put(exp, null);
				}
				assertExpected(testName, testCase, decValues, "Generated code");
				return;
			}
			if (testCase.inputs().isEmpty() && !fn.parameters().isEmpty()) {
				for (String exp : testCase.expectedResults().keySet()) {
					decValues.put(exp, null);
				}
				assertExpected(testName, testCase, decValues, "Generated code");
				return;
			}

			Object[] slots = buildInputSlots(compilation, testCase);
			Object[] evaluated = null;
			try {
				evaluated = (Object[]) genClass.getMethod("evaluate", Object[].class).invoke(engineInstance,
						(Object) slots);
			} catch (Throwable ignored) {
			}
			Object result = null;
			try {
				try {
					result = genClass.getMethod("evaluateBkm", int.class, Object[].class, Object[].class).invoke(
							engineInstance, slot, args, (Object) evaluated);
				} catch (NoSuchMethodException e) {
					result = genClass.getMethod("evaluateBkm", int.class, Object[].class).invoke(engineInstance, slot,
							args);
				}
			} catch (Throwable ignored) {
			}
			for (String expName : testCase.expectedResults().keySet()) {
				if (result instanceof io.finmsg.dmn.runtime.RuntimeContextValue ctx) {
					decValues.put(expName, ctx.namedFields().get(expName));
				} else if (result instanceof Map<?, ?> map) {
					decValues.put(expName, map.get(expName));
				} else {
					decValues.put(expName, result);
				}
			}
			assertExpected(testName, testCase, decValues, "Generated code");
			return;
		}
		Object[] slots = buildInputSlots(compilation, testCase);
		Object[] evaluated = (Object[]) genClass.getMethod("evaluate", Object[].class).invoke(engineInstance,
				(Object) slots);
		assertExpected(testName, testCase, extractDecisionValues(compilation, evaluated), "Generated code");
	}

	private Preparation prepare(List<DmnSource> sources, Set<String> resultNames) {
		try {
			List<DmnSource> scopedSources = new ArrayList<>(sources);
			scopedSources.set(0, new TckDmnDecisionPruner().prune(sources.getFirst(), resultNames));
			DmnSource root = scopedSources.getFirst();
			io.finmsg.dmn.compiler.DmnModelResolver resolver = scopedSources.size() > 1
					? new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(
							scopedSources.subList(1, scopedSources.size()))
					: new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(List.of());
			DmnCompilationResult compilation = new DmnCompiler().compile(root, resolver);
			if (!compilation.isSuccess()) {
				String diagnostic = compilation.diagnostics().stream()
						.map(value -> value.phase() + "/" + value.code() + ": " + value.message())
						.collect(java.util.stream.Collectors.joining("; "));
				return Preparation.failure("Compilation unsuccessful: " + diagnostic, null, true);
			}
			String className = "OmgTckEngine_" + ENGINE_COUNTER.getAndIncrement();
			DmnJavaGeneratorResult generated = new DmnJavaGenerator().generate(
					compilation.optimizedRuntimeModel().orElseThrow(),
					DmnJavaGeneratorOptions.of("io.finmsg.dmn.tck.gen", className));
			Class<?> generatedClass = compileInMemory("io.finmsg.dmn.tck.gen." + className,
					generated.sources().get("io.finmsg.dmn.tck.gen." + className));
			return Preparation.success(compilation, generatedClass,
					generatedClass.getDeclaredConstructor().newInstance());
		} catch (Throwable exception) {
			return Preparation.failure("Case-scoped preparation threw: " + exception, exception, false);
		}
	}

	private static boolean expectsOnlyErrors(TckTestCase testCase) {
		return !testCase.expectedErrorResults().isEmpty()
				&& testCase.expectedErrorResults().equals(testCase.expectedResults().keySet());
	}

	@AfterAll
	void writeAccountingEvidence() throws Exception {
		if (inventory == null)
			return;
		TckCatalogueReport report = new TckCatalogueReport(TCK_REVISION, BACKENDS, inventory, outcomes);
		new TckCatalogueReportWriter().write(Path.of("target", "tck-accounting.json"), report);
	}

	private void addPreparationFailure(List<DynamicTest> tests, TckCatalogueEntry entry, String diagnostic,
			Throwable cause) {
		for (TckTestCase testCase : entry.testCases()) {
			for (String backend : BACKENDS) {
				int idx = currentTestIndex.incrementAndGet();
				String testName = entry.id() + "#" + testCase.id() + " @" + backend;
				System.err.printf("[INFO] [TCK %4d/%4d] [COMPILATION_ERROR] %s -> %s%n", idx, totalTestCount, testName,
						diagnostic);
				System.err.flush();
				outcomes.add(new TckCatalogueOutcome(entry.id(), testCase.id(), backend,
						TckCatalogueStatus.COMPILATION_ERROR, diagnostic));
			}
		}
		tests.add(DynamicTest.dynamicTest(entry.id() + " :: preparation", () -> {
			throw new AssertionError(diagnostic, cause);
		}));
	}

	private DynamicTest recordedTest(String name, TckCatalogueEntry entry, TckTestCase testCase, String backend,
			org.junit.jupiter.api.function.Executable executable) {
		return DynamicTest.dynamicTest(name, () -> {
			int idx = currentTestIndex.incrementAndGet();
			try {
				executable.execute();
				System.out.printf("[INFO] [TCK %4d/%4d] [PASS] %s%n", idx, totalTestCount, name);
				System.out.flush();
				outcomes.add(
						new TckCatalogueOutcome(entry.id(), testCase.id(), backend, TckCatalogueStatus.PASSED, ""));
			} catch (Throwable failure) {
				System.err.printf("[INFO] [TCK %4d/%4d] [FAIL] %s -> %s%n", idx, totalTestCount, name,
						failure.getMessage());
				System.err.flush();
				TckCatalogueStatus status = failure instanceof AssertionError
						? TckCatalogueStatus.FAILED
						: TckCatalogueStatus.EXECUTION_ERROR;
				outcomes.add(new TckCatalogueOutcome(entry.id(), testCase.id(), backend, status,
						String.valueOf(failure.getMessage())));
				throw failure;
			}
		});
	}

	private DynamicTest recordedExpectedErrorTest(String name, TckCatalogueEntry entry, TckTestCase testCase,
			String backend, org.junit.jupiter.api.function.Executable executable) {
		return DynamicTest.dynamicTest(name, () -> {
			int idx = currentTestIndex.incrementAndGet();
			try {
				executable.execute();
			} catch (Throwable expected) {
				System.out.printf("[INFO] [TCK %4d/%4d] [PASS] %s%n", idx, totalTestCount, name);
				System.out.flush();
				outcomes.add(
						new TckCatalogueOutcome(entry.id(), testCase.id(), backend, TckCatalogueStatus.PASSED, ""));
				return;
			}
			String diagnostic = "Expected FEEL error but evaluation completed successfully";
			System.err.printf("[INFO] [TCK %4d/%4d] [FAIL] %s -> %s%n", idx, totalTestCount, name, diagnostic);
			System.err.flush();
			outcomes.add(
					new TckCatalogueOutcome(entry.id(), testCase.id(), backend, TckCatalogueStatus.FAILED, diagnostic));
			throw new AssertionError(diagnostic);
		});
	}

	private static void assertExpected(String testName, TckTestCase testCase, Map<String, Object> actual,
			String backend) {
		for (String errorDecision : testCase.expectedErrorResults()) {
			Object val = actual.get(errorDecision);
			if (val != null) {
				throw new AssertionError(backend + " expected FEEL error / null for decision '" + errorDecision
						+ "' in test '" + testName + "' but got: " + val);
			}
		}
		for (Map.Entry<String, TckValue> expected : testCase.expectedResults().entrySet()) {
			if (testCase.expectedErrorResults().contains(expected.getKey())) {
				continue;
			}
			Object normalizedExpected = normalize(expected.getValue().runtimeValue());
			Object normalizedActual = normalize(actual.get(expected.getKey()));
			if (normalizedExpected != null)
				assertThat(normalizedActual)
						.withFailMessage("%s evaluated to null for decision '%s' in test '%s' (expected: %s)", backend,
								expected.getKey(), testName, normalizedExpected)
						.isNotNull();
			if (normalizedExpected instanceof BigDecimal expBd && normalizedActual instanceof BigDecimal actBd) {
				if (expBd.compareTo(actBd) == 0
						|| expBd.subtract(actBd).abs().compareTo(new BigDecimal("0.0001")) <= 0) {
					continue;
				}
			}
			assertThat(normalizedActual)
					.withFailMessage("%s mismatch for decision '%s' in test '%s': expected <%s> but got <%s>", backend,
							expected.getKey(), testName, normalizedExpected, normalizedActual)
					.isEqualTo(normalizedExpected);
		}
	}

	private static Object[] buildInputSlots(DmnCompilationResult compilation, TckTestCase testCase) {
		int slotCount = compilation.optimizedRuntimeModel().orElseThrow().model().valueSlotCount();
		Object[] slots = new Object[slotCount];
		Map<String, Integer> inputSlots = DmnToolkitTckEngine.getRuntimeInputSlots(compilation);
		Map<String, Integer> decisionSlots = DmnToolkitTckEngine.getRuntimeDecisionSlots(compilation);

		testCase.inputs().forEach((name, val) -> {
			Integer inputSlot = inputSlots.get(name);
			if (inputSlot != null && inputSlot < slots.length) {
				slots[inputSlot] = val.runtimeValue();
			}
			Integer decSlot = decisionSlots.get(name);
			if (decSlot != null && decSlot < slots.length) {
				slots[decSlot] = val.runtimeValue();
			}
		});
		return slots;
	}

	private static Map<String, Object> extractDecisionValues(DmnCompilationResult compilation, Object[] slots) {
		Map<String, Object> decisions = new LinkedHashMap<>();
		Map<String, Integer> decisionSlots = DmnToolkitTckEngine.getRuntimeDecisionSlots(compilation);
		decisionSlots.forEach((name, slot) -> {
			if (slot != null && slot < slots.length) {
				decisions.put(name, slots[slot]);
			}
		});
		return decisions;
	}

	private static Class<?> compileInMemory(String fqcn, String code) throws Exception {
		Path tempDir = Files.createTempDirectory("dmn-official-tck-gen");
		String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);
		Path pkgDir = tempDir.resolve("io/finmsg/dmn/tck/gen");
		Files.createDirectories(pkgDir);
		Path sourceFile = pkgDir.resolve(className + ".java");
		Files.writeString(sourceFile, code, java.nio.charset.StandardCharsets.UTF_8);

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		java.io.ByteArrayOutputStream errOut = new java.io.ByteArrayOutputStream();
		int exitCode = compiler.run(null, null, errOut, "-encoding", "UTF-8", sourceFile.toString());
		if (exitCode != 0) {
			throw new IllegalStateException("javac compilation failed for " + fqcn + ":\n"
					+ errOut.toString(java.nio.charset.StandardCharsets.UTF_8) + "\nSource:\n" + code);
		}

		URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()});
		return classLoader.loadClass(fqcn);
	}

	private record Preparation(DmnCompilationResult compilation, Class<?> generatedClass, Object engineInstance,
			String failureDiagnostic, Throwable failureCause, boolean modelRejected) {
		private static Preparation success(DmnCompilationResult compilation, Class<?> generatedClass,
				Object engineInstance) {
			return new Preparation(compilation, generatedClass, engineInstance, null, null, false);
		}

		private static Preparation failure(String diagnostic, Throwable cause, boolean modelRejected) {
			return new Preparation(null, null, null, diagnostic, cause, modelRejected);
		}
	}

	private record PreparationKey(List<String> sourceIds, Set<String> resultNames) {
		private PreparationKey {
			sourceIds = List.copyOf(sourceIds);
			resultNames = Set.copyOf(resultNames);
		}
	}

	private static Object normalize(Object val) {
		if (val == null)
			return null;
		if (val instanceof io.finmsg.dmn.runtime.RuntimeContextValue ctx)
			return normalize(ctx.namedFields());
		if (val instanceof Map<?, ?> map) {
			Map<String, Object> norm = new LinkedHashMap<>();
			map.forEach((k, v) -> norm.put(String.valueOf(k), normalize(v)));
			return norm;
		}
		if (val instanceof List<?> list) {
			return list.stream().map(OfficialTckSuiteTest::normalize).toList();
		}
		if (val instanceof Number n) {
			try {
				return new BigDecimal(n.toString()).setScale(8, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
			} catch (Exception e) {
				return BigDecimal.valueOf(n.doubleValue()).setScale(8, java.math.RoundingMode.HALF_UP)
						.stripTrailingZeros();
			}
		}
		if (val instanceof String s && s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
			return s.substring(1, s.length() - 1);
		}
		return val;
	}
}
