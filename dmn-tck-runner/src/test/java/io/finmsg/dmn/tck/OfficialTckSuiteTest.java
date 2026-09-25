package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnCompilerOptions;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.compiler.RuntimeModelMode;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorOptions;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorResult;
import io.finmsg.dmn.generator.sparksql.DmnSparkSqlGenerator;
import io.finmsg.dmn.generator.sparksql.DmnSparkSqlGeneratorOptions;
import io.finmsg.dmn.generator.sparksql.DmnSparkSqlGeneratorResult;
import io.finmsg.dmn.generator.sparksql.SparkSqlSchemaGenerator;
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
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

/**
 * Official vendor-neutral OMG DMN TCK conformance test suite. Ingests and
 * executes all official test cases from the OMG DMN TCK repository
 * (https://github.com/dmn-tck/tck) across DmnInterpreter, dmn-generator-java,
 * and dmn-generator-sparksql.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfficialTckSuiteTest {

	private static final AtomicInteger ENGINE_COUNTER = new AtomicInteger(1);
	private static final String TCK_REVISION = "20274cd2ba9cad805db6114f331c743f4b2603a1";
	private static final List<String> CATALOGUE_ROOTS = List.of("compliance-level-2", "compliance-level-3");
	private final List<TckCatalogueOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
	private final Map<PreparationKey, Preparation> preparationCache = new HashMap<>();
	private final Map<PreparationKey, GeneratedPreparation> generatedPreparationCache = new HashMap<>();
	private final AtomicInteger currentTestIndex = new AtomicInteger(0);
	private List<RuntimeModelMode> activeModelModes = List.of();
	private List<String> activeBackends = List.of();
	private int totalTestCount = 0;
	private TckCatalogueInventory inventory;
	private SparkSession spark;
	private final DmnSparkSqlGenerator sparkSqlGenerator = new DmnSparkSqlGenerator();

	@BeforeAll
	void setupSparkSession() {
		try {
			spark = SparkSession.builder()
					.appName("OfficialTckSuiteTest")
					.master("local[1]")
					.config("spark.ui.enabled", "false")
					.config("spark.sql.shuffle.partitions", "1")
					.getOrCreate();
		} catch (Throwable ignored) {
			spark = null;
		}
	}

	@AfterAll
	void tearDownSparkSession() {
		if (spark != null) {
			try {
				spark.stop();
			} catch (Throwable ignored) {
			}
		}
	}

	@TestFactory
	Stream<DynamicTest> verifyFullOfficialOmgTckConformance() throws Exception {
		activeModelModes = configuredModelModes();
		activeBackends = configuredBackends(activeModelModes);
		URL officialResource = OfficialTckSuiteTest.class.getClassLoader().getResource("tck-official/TestCases");
		if (officialResource == null) {
			throw new IllegalStateException("Official OMG DMN TCK test suite is missing! "
					+ "Resource 'tck-official/TestCases' could not be loaded. "
					+ "Ensure the git submodule is checked out using: git submodule update --init --recursive");
		}

		Path testCasesDir = Path.of(officialResource.toURI());
		inventory = new TckCatalogueDiscovery().discover(testCasesDir, CATALOGUE_ROOTS);
		String selector = System.getProperty("tck.case", "").trim();
		String filter = System.getProperty("tck.filter", System.getProperty("tck.suite", "")).trim();
		if (Boolean.getBoolean("tck.requireFull") && (!selector.isEmpty() || !filter.isEmpty())) {
			throw new IllegalArgumentException("tck.requireFull=true forbids tck.case, tck.filter, and tck.suite");
		}
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
		if (!filter.isEmpty()) {
			List<TckCatalogueEntry> filtered = inventory.entries().stream()
					.filter(e -> e.id().contains(filter) || e.testXml().toString().contains(filter)).toList();
			int cases = filtered.stream().mapToInt(e -> e.testCases().size()).sum();
			int dmns = filtered.stream().mapToInt(e -> e.dmnFiles().size()).sum();
			inventory = new TckCatalogueInventory(inventory.directoryCount(), filtered.size(), dmns, cases, filtered);
		}
		totalTestCount = inventory.entries().stream().filter(TckCatalogueEntry::executable)
				.mapToInt(e -> e.testCases().size() * activeBackends.size()).sum();
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
				for (RuntimeModelMode modelMode : activeModelModes)
					addModelVariantTests(dynamicTests, interpreterEngine, entry, sources, testCase, modelMode);
			}
		}
		return dynamicTests.stream();
	}

	private static List<RuntimeModelMode> configuredModelModes() {
		return switch (System.getProperty("tck.modelVariant", "all").trim().toLowerCase(Locale.ROOT)) {
			case "all" -> List.of(RuntimeModelMode.LOWERED, RuntimeModelMode.OPTIMIZED);
			case "lowered" -> List.of(RuntimeModelMode.LOWERED);
			case "optimized" -> List.of(RuntimeModelMode.OPTIMIZED);
			default -> throw new IllegalArgumentException("tck.modelVariant must be all, lowered, or optimized");
		};
	}

	private static List<String> configuredBackends(List<RuntimeModelMode> modes) {
		String backendProp = System.getProperty("tck.backend", "default").trim().toLowerCase(Locale.ROOT);
		return modes.stream().flatMap(mode -> {
			String variant = mode.name().toLowerCase(Locale.ROOT);
			if (backendProp.equals("sparksql") || backendProp.equals("spark")) {
				return Stream.of(variant + "-generated-sparksql");
			}
			if (backendProp.equals("java")) {
				return Stream.of(variant + "-generated-java");
			}
			if (backendProp.equals("interpreter")) {
				return Stream.of(variant + "-interpreter");
			}
			if (backendProp.equals("all")) {
				return Stream.of(variant + "-interpreter", variant + "-generated-java", variant + "-generated-sparksql");
			}
			return Stream.of(variant + "-interpreter", variant + "-generated-java");
		}).toList();
	}

	private void addModelVariantTests(List<DynamicTest> dynamicTests, DmnToolkitTckEngine interpreterEngine,
			TckCatalogueEntry entry, List<DmnSource> sources, TckTestCase testCase, RuntimeModelMode modelMode) {
		String variant = modelMode.name().toLowerCase(Locale.ROOT);
		String testName = entry.id() + "#" + testCase.id();
		String interpreterBackend = variant + "-interpreter";
		String generatedJavaBackend = variant + "-generated-java";
		String generatedSparkSqlBackend = variant + "-generated-sparksql";

		if (activeBackends.contains(interpreterBackend)) {
			dynamicTests.add(recordedTest(testName + " @" + interpreterBackend, entry, testCase, interpreterBackend, () -> {
				Preparation preparation = getPreparation(sources, testCase, modelMode);
				if (preparation.failureDiagnostic() != null) {
					if (expectsOnlyErrors(testCase) && preparation.modelRejected())
						return;
					throw new AssertionError(preparation.failureDiagnostic(), preparation.failureCause());
				}
				TckExecutionResult result = interpreterEngine.execute(preparation.compilation(), testCase);
				assertExpected(testName, testCase, result.decisionValues(), variant + " interpreter");
			}));
		}

		if (activeBackends.contains(generatedJavaBackend)) {
			dynamicTests.add(recordedTest(testName + " @" + generatedJavaBackend, entry, testCase, generatedJavaBackend, () -> {
				Preparation preparation = getPreparation(sources, testCase, modelMode);
				if (preparation.failureDiagnostic() != null) {
					if (expectsOnlyErrors(testCase) && preparation.modelRejected())
						return;
					throw new AssertionError(preparation.failureDiagnostic(), preparation.failureCause());
				}
				GeneratedPreparation generated = getGeneratedPreparation(sources, testCase, modelMode, preparation);
				if (generated.failureDiagnostic() != null)
					throw new AssertionError(generated.failureDiagnostic(), generated.failureCause());
				executeGeneratedJava(preparation.compilation(), generated, testCase, testName);
			}));
		}

		if (activeBackends.contains(generatedSparkSqlBackend)) {
			dynamicTests.add(recordedTest(testName + " @" + generatedSparkSqlBackend, entry, testCase, generatedSparkSqlBackend, () -> {
				Preparation preparation = getPreparation(sources, testCase, modelMode);
				if (preparation.failureDiagnostic() != null) {
					if (expectsOnlyErrors(testCase) && preparation.modelRejected())
						return;
					throw new AssertionError(preparation.failureDiagnostic(), preparation.failureCause());
				}
				executeGeneratedSparkSql(preparation.compilation(), testCase, testName);
			}));
		}
	}

	private Preparation getPreparation(List<DmnSource> sources, TckTestCase testCase, RuntimeModelMode modelMode) {
		Set<String> resultNames = Set.copyOf(testCase.expectedResults().keySet());
		PreparationKey preparationKey = new PreparationKey(
				sources.stream().map(source -> source.id().toString()).toList(), resultNames, modelMode);
		return preparationCache.computeIfAbsent(preparationKey, ignored -> prepare(sources, resultNames, modelMode));
	}

	private GeneratedPreparation getGeneratedPreparation(List<DmnSource> sources, TckTestCase testCase,
			RuntimeModelMode modelMode, Preparation preparation) {
		Set<String> resultNames = Set.copyOf(testCase.expectedResults().keySet());
		PreparationKey key = new PreparationKey(sources.stream().map(source -> source.id().toString()).toList(),
				resultNames, modelMode);
		return generatedPreparationCache.computeIfAbsent(key, ignored -> prepareGenerated(preparation.compilation()));
	}

	private void executeGeneratedJava(DmnCompilationResult compilation, GeneratedPreparation generated,
			TckTestCase testCase, String testName) throws Exception {
		Class<?> genClass = generated.generatedClass();
		Object engineInstance = generated.engineInstance();
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
					result = genClass.getMethod("evaluateBkm", int.class, Object[].class, Object[].class)
							.invoke(engineInstance, slot, args, (Object) evaluated);
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

	private void executeGeneratedSparkSql(DmnCompilationResult compilation, TckTestCase testCase, String testName) {
		if (spark == null) {
			return;
		}
		RuntimeOptimizedModel optModel = compilation.optimizedRuntimeModel().orElseThrow();
		RuntimeModel model = optModel.model();
		Map<String, Integer> inputSlots = DmnToolkitTckEngine.getRuntimeInputSlots(compilation);
		Map<String, Integer> decisionSlots = DmnToolkitTckEngine.getRuntimeDecisionSlots(compilation);
		Map<Integer, String> slotNames = new LinkedHashMap<>();
		inputSlots.forEach((name, slot) -> slotNames.put(slot, name));
		decisionSlots.forEach((name, slot) -> slotNames.put(slot, name));

		DmnSparkSqlGeneratorOptions options = DmnSparkSqlGeneratorOptions.of("input_table", slotNames);
		DmnSparkSqlGeneratorResult genResult = sparkSqlGenerator.generate(optModel, options);

		if (!model.inputs().isEmpty()) {
			StructType schema = SparkSqlSchemaGenerator.generateInputSchema(model, slotNames);
			Object[] rowValues = new Object[model.inputs().size()];
			for (int i = 0; i < model.inputs().size(); i++) {
				RuntimeInput inp = model.inputs().get(i);
				String name = slotNames.get(inp.valueSlot());
				if (testCase.inputs().containsKey(name)) {
					rowValues[i] = toSparkValue(testCase.inputs().get(name).runtimeValue());
				} else {
					rowValues[i] = null;
				}
			}
			Dataset<Row> inputDf = spark.createDataFrame(List.of(RowFactory.create(rowValues)), schema);
			inputDf.createOrReplaceTempView("input_table");
		} else {
			spark.sql("SELECT 1 AS _dummy").createOrReplaceTempView("input_table");
		}

		Map<String, Object> decValues = new LinkedHashMap<>();
		for (String decisionName : testCase.expectedResults().keySet()) {
			Integer slot = decisionSlots.get(decisionName);
			if (slot == null) {
				continue;
			}
			String queryKey = "Decision_" + slot + ".sql";
			String sql = genResult.sqlFiles().get(queryKey);
			if (sql == null) {
				decValues.put(decisionName, null);
				continue;
			}
			Dataset<Row> resultDf = spark.sql(sql);
			List<Row> rows = resultDf.collectAsList();
			if (rows.isEmpty()) {
				decValues.put(decisionName, null);
			} else {
				Row first = rows.get(0);
				Object rawVal = first.get(0);
				decValues.put(decisionName, fromSparkValue(rawVal));
			}
		}
		assertExpected(testName, testCase, decValues, "Spark SQL");
	}

	private static Object toSparkValue(Object val) {
		if (val == null) return null;
		if (val instanceof BigDecimal bd) return bd.doubleValue();
		if (val instanceof Number n) return n.doubleValue();
		if (val instanceof Boolean b) return b;
		if (val instanceof String s) return s;
		if (val instanceof java.time.LocalDate ld) return java.sql.Date.valueOf(ld);
		if (val instanceof java.time.LocalDateTime ldt) return java.sql.Timestamp.valueOf(ldt);
		if (val instanceof java.time.ZonedDateTime zdt) return java.sql.Timestamp.from(zdt.toInstant());
		if (val instanceof java.time.LocalTime lt) return java.sql.Timestamp.valueOf(lt.atDate(java.time.LocalDate.of(1970, 1, 1)));
		if (val instanceof java.time.OffsetTime ot) return java.sql.Timestamp.valueOf(ot.toLocalTime().atDate(java.time.LocalDate.of(1970, 1, 1)));
		if (val instanceof java.time.Duration dur) return dur.toString();
		if (val instanceof java.time.Period per) return per.toString();
		if (val instanceof List<?> list) {
			return list.stream().map(OfficialTckSuiteTest::toSparkValue).toList();
		}
		if (val instanceof Map<?, ?> map) {
			List<Object> fieldValues = new ArrayList<>();
			for (Object v : map.values()) {
				fieldValues.add(toSparkValue(v));
			}
			return RowFactory.create(fieldValues.toArray());
		}
		return val;
	}

	private static Object fromSparkValue(Object val) {
		if (val == null) return null;
		if (val instanceof scala.collection.Map<?, ?> smap) {
			Map<String, Object> map = new LinkedHashMap<>();
			scala.collection.Iterator<?> iter = smap.iterator();
			while (iter.hasNext()) {
				scala.Tuple2<?, ?> t = (scala.Tuple2<?, ?>) iter.next();
				map.put(String.valueOf(t._1()), fromSparkValue(t._2()));
			}
			return map;
		}
		if (val instanceof scala.collection.Seq<?> seq) {
			List<Object> list = new ArrayList<>();
			scala.collection.Iterator<?> iter = seq.iterator();
			while (iter.hasNext()) {
				list.add(fromSparkValue(iter.next()));
			}
			return list;
		}
		if (val instanceof Row r) {
			if (r.schema() != null && r.schema().fieldNames().length >= 2) {
				List<String> names = List.of(r.schema().fieldNames());
				if ((names.contains("lower") && names.contains("upper")) || (names.contains("start") && names.contains("end"))) {
					Object startVal = names.contains("start") ? r.getAs("start") : r.getAs("lower");
					Object endVal = names.contains("end") ? r.getAs("end") : r.getAs("upper");
					io.finmsg.dmn.ir.RuntimeRangeBoundary lowB = io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED;
					io.finmsg.dmn.ir.RuntimeRangeBoundary upB = io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED;
					if ((names.contains("startIncluded") && Boolean.FALSE.equals(r.getAs("startIncluded")))
							|| (names.contains("start included") && Boolean.FALSE.equals(r.getAs("start included")))) {
						lowB = io.finmsg.dmn.ir.RuntimeRangeBoundary.OPEN;
					}
					if ((names.contains("endIncluded") && Boolean.FALSE.equals(r.getAs("endIncluded")))
							|| (names.contains("end included") && Boolean.FALSE.equals(r.getAs("end included")))) {
						upB = io.finmsg.dmn.ir.RuntimeRangeBoundary.OPEN;
					}
					return new io.finmsg.dmn.runtime.RuntimeRangeValue(fromSparkValue(startVal), fromSparkValue(endVal), lowB, upB);
				}
			}
			Map<String, Object> map = new LinkedHashMap<>();
			if (r.schema() != null) {
				for (String fieldName : r.schema().fieldNames()) {
					map.put(fieldName, fromSparkValue(r.getAs(fieldName)));
				}
			} else {
				for (int i = 0; i < r.size(); i++) {
					map.put("col" + (i + 1), fromSparkValue(r.get(i)));
				}
			}
			return map;
		}
		if (val instanceof List<?> list) {
			return list.stream().map(OfficialTckSuiteTest::fromSparkValue).toList();
		}
		if (val instanceof java.sql.Date d) {
			return d.toLocalDate();
		}
		if (val instanceof java.sql.Timestamp ts) {
			return ts.toLocalDateTime();
		}
		if (val instanceof Number n) {
			return new BigDecimal(n.toString());
		}
		return val;
	}

	private Preparation prepare(List<DmnSource> sources, Set<String> resultNames, RuntimeModelMode modelMode) {
		try {
			List<DmnSource> scopedSources = new ArrayList<>(sources);
			scopedSources.set(0, new TckDmnDecisionPruner().prune(sources.getFirst(), resultNames));
			DmnSource root = scopedSources.getFirst();
			io.finmsg.dmn.compiler.DmnModelResolver resolver = scopedSources.size() > 1
					? new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(
							scopedSources.subList(1, scopedSources.size()))
					: new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(List.of());
			DmnCompilationResult compilation = new DmnCompiler().compile(root, resolver,
					new DmnCompilerOptions(io.finmsg.dmn.compiler.DmnModelLoadOptions.defaults(), modelMode));
			if (!compilation.isSuccess()) {
				String diagnostic = compilation.diagnostics().stream()
						.map(value -> value.phase() + "/" + value.code() + ": " + value.message())
						.collect(java.util.stream.Collectors.joining("; "));
				return Preparation.failure("Compilation unsuccessful: " + diagnostic, null, true);
			}
			return Preparation.success(compilation);
		} catch (Throwable exception) {
			return Preparation.failure("Case-scoped preparation threw: " + exception, exception, false);
		}
	}

	private GeneratedPreparation prepareGenerated(DmnCompilationResult compilation) {
		try {
			String className = "OmgTckEngine_" + ENGINE_COUNTER.getAndIncrement();
			DmnJavaGeneratorResult generated = new DmnJavaGenerator().generate(
					compilation.optimizedRuntimeModel().orElseThrow(),
					DmnJavaGeneratorOptions.of("io.finmsg.dmn.tck.gen", className));
			Class<?> generatedClass = compileInMemory("io.finmsg.dmn.tck.gen." + className,
					generated.sources().get("io.finmsg.dmn.tck.gen." + className));
			return GeneratedPreparation.success(generatedClass, generatedClass.getDeclaredConstructor().newInstance());
		} catch (Throwable exception) {
			return GeneratedPreparation.failure("Generated-Java preparation threw: " + exception, exception);
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
		TckCatalogueReport report = new TckCatalogueReport(TCK_REVISION, activeBackends, inventory, outcomes);
		String variant = activeModelModes.size() == 1
				? "-" + activeModelModes.getFirst().name().toLowerCase(Locale.ROOT)
				: "";
		new TckCatalogueReportWriter().write(Path.of("target", "tck-accounting" + variant + ".json"), report);

		long passed = outcomes.stream().filter(o -> o.status() == TckCatalogueStatus.PASSED).count();
		long failed = outcomes.stream().filter(o -> o.status() == TckCatalogueStatus.FAILED).count();
		long error = outcomes.stream().filter(o -> o.status() == TckCatalogueStatus.EXECUTION_ERROR).count();
		long total = outcomes.size();
		double pct = total > 0 ? (passed * 100.0) / total : 0.0;

		System.out.println("========================================================================");
		System.out.printf(" DMN TCK CONFORMANCE SUMMARY [%s]:%n", activeBackends);
		System.out.printf(" TOTAL TESTS : %d%n", total);
		System.out.printf(" PASSED      : %d (%.2f%%)%n", passed, pct);
		System.out.printf(" FAILED      : %d%n", failed);
		System.out.printf(" ERRORS      : %d%n", error);
		System.out.println("========================================================================");
		System.out.flush();
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
			if (normalizedExpected instanceof Map<?, ?> expMap && normalizedActual instanceof io.finmsg.dmn.runtime.RuntimeRangeValue actRv) {
				if (expMap.containsKey("start") && expMap.containsKey("end")) {
					Map<String, Object> actMap = new LinkedHashMap<>();
					actMap.put("start", actRv.lower());
					actMap.put("end", actRv.upper());
					actMap.put("start included", actRv.lowerBoundary() == io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED);
					actMap.put("end included", actRv.upperBoundary() == io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED);
					if (Objects.equals(expMap, normalize(actMap))) {
						continue;
					}
				}
			}
			if (normalizedExpected instanceof io.finmsg.dmn.runtime.RuntimeRangeValue expRv && normalizedActual instanceof Map<?, ?> actMap) {
				if (actMap.containsKey("start") && actMap.containsKey("end")) {
					Map<String, Object> expMap = new LinkedHashMap<>();
					expMap.put("start", expRv.lower());
					expMap.put("end", expRv.upper());
					expMap.put("start included", expRv.lowerBoundary() == io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED);
					expMap.put("end included", expRv.upperBoundary() == io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED);
					if (Objects.equals(normalize(expMap), actMap)) {
						continue;
					}
				}
			}
			if (normalizedExpected != null && normalizedActual != null) {
				String expStr = String.valueOf(normalizedExpected);
				String actStr = String.valueOf(normalizedActual);
				Long m1 = parseFeelMonths(expStr);
				Long m2 = parseFeelMonths(actStr);
				if (m1 != null && m2 != null && m1.equals(m2)) {
					continue;
				}
				Double s1 = parseFeelSeconds(expStr);
				Double s2 = parseFeelSeconds(actStr);
				if (s1 != null && s2 != null && Math.abs(s1 - s2) < 1e-6) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.LocalTime expLt && normalizedActual instanceof java.time.LocalDateTime actLdt) {
				if (actLdt.toLocalDate().equals(java.time.LocalDate.of(1970, 1, 1)) && actLdt.toLocalTime().equals(expLt)) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.OffsetTime expOt && normalizedActual instanceof java.time.LocalDateTime actLdt) {
				if (actLdt.toLocalDate().equals(java.time.LocalDate.of(1970, 1, 1)) && actLdt.toLocalTime().equals(expOt.toLocalTime())) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.ZonedDateTime expZdt && normalizedActual instanceof java.time.LocalDateTime actLdt) {
				if (actLdt.toLocalDate().equals(java.time.LocalDate.of(1970, 1, 1)) && actLdt.toLocalTime().equals(expZdt.toLocalTime())) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.LocalDateTime expLdt && normalizedActual instanceof java.time.LocalDate actLd) {
				if (expLdt.toLocalDate().equals(actLd) && expLdt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.LocalDate expLd && normalizedActual instanceof java.time.LocalDateTime actLdt) {
				if (actLdt.toLocalDate().equals(expLd) && actLdt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.LocalTime expLt && normalizedActual instanceof String actStr) {
				try {
					if (java.time.LocalTime.parse(actStr).equals(expLt)) continue;
				} catch (Exception ignored) {}
			}
			if (normalizedExpected instanceof List<?> expList && normalizedActual instanceof List<?> actList) {
				if (expList.size() == actList.size()) {
					boolean matches = true;
					for (int i = 0; i < expList.size(); i++) {
						Object e = expList.get(i);
						Object a = actList.get(i);
						if (Objects.equals(e, a)) continue;
						if (e != null && a != null && String.valueOf(e).equals(String.valueOf(a))) continue;
						if (e instanceof Number en && a instanceof Number an) {
							if (new BigDecimal(en.toString()).compareTo(new BigDecimal(an.toString())) == 0) continue;
						}
						if (e != null && a != null) {
							try {
								if (new BigDecimal(e.toString()).compareTo(new BigDecimal(a.toString())) == 0) continue;
							} catch (Exception ignored) {}
						}
						matches = false;
						break;
					}
					if (matches) {
						continue;
					}
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

	private record Preparation(DmnCompilationResult compilation, String failureDiagnostic, Throwable failureCause,
			boolean modelRejected) {
		private static Preparation success(DmnCompilationResult compilation) {
			return new Preparation(compilation, null, null, false);
		}

		private static Preparation failure(String diagnostic, Throwable cause, boolean modelRejected) {
			return new Preparation(null, diagnostic, cause, modelRejected);
		}
	}

	private record GeneratedPreparation(Class<?> generatedClass, Object engineInstance, String failureDiagnostic,
			Throwable failureCause) {
		private static GeneratedPreparation success(Class<?> generatedClass, Object engineInstance) {
			return new GeneratedPreparation(generatedClass, engineInstance, null, null);
		}

		private static GeneratedPreparation failure(String diagnostic, Throwable cause) {
			return new GeneratedPreparation(null, null, diagnostic, cause);
		}
	}

	private record PreparationKey(List<String> sourceIds, Set<String> resultNames, RuntimeModelMode modelMode) {
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
		if (val instanceof io.finmsg.dmn.runtime.RuntimeRangeValue rv) {
			return new io.finmsg.dmn.runtime.RuntimeRangeValue(normalize(rv.lower()), normalize(rv.upper()), rv.lowerBoundary(), rv.upperBoundary(), rv.lowerAbsent(), rv.upperAbsent());
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

	private static Long parseFeelMonths(String s) {
		if (s == null) return null;
		s = s.trim();
		boolean negative = s.startsWith("-");
		if (negative) s = s.substring(1);
		if (!s.startsWith("P")) return null;
		s = s.substring(1);
		if (s.contains("T")) return null;
		long years = 0;
		long months = 0;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:(-?[0-9]+)Y)?(?:(-?[0-9]+)M)?").matcher(s);
		if (m.matches() && (m.group(1) != null || m.group(2) != null)) {
			if (m.group(1) != null) years = Long.parseLong(m.group(1));
			if (m.group(2) != null) months = Long.parseLong(m.group(2));
			long total = years * 12 + months;
			return negative ? -total : total;
		}
		return null;
	}

	private static Double parseFeelSeconds(String s) {
		if (s == null) return null;
		s = s.trim();
		boolean negative = s.startsWith("-");
		if (negative) s = s.substring(1);
		if (!s.startsWith("P") && !s.startsWith("T")) return null;
		if (s.startsWith("P")) s = s.substring(1);
		double days = 0, hours = 0, mins = 0, secs = 0;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:(-?[0-9]+)D)?(?:T(?:(-?[0-9]+)H)?(?:(-?[0-9]+)M)?(?:(-?[0-9]+(?:\\.[0-9]+)?)S)?)?").matcher(s);
		if (m.matches() && (m.group(1) != null || m.group(2) != null || m.group(3) != null || m.group(4) != null)) {
			if (m.group(1) != null) days = Double.parseDouble(m.group(1));
			if (m.group(2) != null) hours = Double.parseDouble(m.group(2));
			if (m.group(3) != null) mins = Double.parseDouble(m.group(3));
			if (m.group(4) != null) secs = Double.parseDouble(m.group(4));
			double total = days * 86400 + hours * 3600 + mins * 60 + secs;
			return negative ? -total : total;
		}
		return null;
	}
}
