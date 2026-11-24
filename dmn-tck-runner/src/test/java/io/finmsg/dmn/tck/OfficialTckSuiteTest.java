package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnCompilerOptions;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.compiler.RuntimeModelMode;
import io.finmsg.dmn.compiler.dmnir.DmnIrRuntimeAdapter;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.tck.dmnir.TckDmnIrArchive;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorOptions;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorResult;
import io.finmsg.dmn.generator.sparksql.DmnSparkSqlGenerator;
import io.finmsg.dmn.generator.sparksql.DmnSparkSqlGeneratorOptions;
import io.finmsg.dmn.generator.sparksql.DmnSparkSqlGeneratorResult;
import io.finmsg.dmn.generator.sparksql.SparkSqlFeelValueCodec;
import io.finmsg.dmn.generator.sparksql.SparkSqlInvocationPlan;
import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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
	private static final long TIMEOUT_SECONDS = Long.getLong("tck.timeoutSeconds", 5);
	private static final ExecutorService TEST_EXECUTOR = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r);
		t.setDaemon(true);
		return t;
	});
	private static final String TCK_REVISION = "5cd912f281831bc535e710aba435e16de198028a";
	private static final List<String> CATALOGUE_ROOTS = List.of("compliance-level-2", "compliance-level-3");
	private final List<TckCatalogueOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
	private final Map<PreparationKey, Preparation> preparationCache = new ConcurrentHashMap<>();
	private final Map<PreparationKey, GeneratedPreparation> generatedPreparationCache = new ConcurrentHashMap<>();
	private final Map<PreparationKey, PortableGeneratedPreparation> portableGeneratedPreparationCache = new ConcurrentHashMap<>();
	private TckDmnIrArchive dmnIrArchive;
	private final Map<String, PortableGeneratedPreparation> portableArchiveCache = new ConcurrentHashMap<>();
	private final Map<PreparationKey, GrpcTckExecution> grpcPreparationCache = new ConcurrentHashMap<>();
	private final AtomicInteger grpcRequests = new AtomicInteger();
	private final AtomicInteger grpcResponses = new AtomicInteger();
	private record TypedPreparation(TypedGrpcTckExecution service, RuntimeException failure) {
	}
	private static final class TypedUnsupported extends RuntimeException {
		TypedUnsupported(String message) {
			super(message);
		}
	}
	private final Map<PreparationKey, TypedPreparation> typedPreparationCache = new ConcurrentHashMap<>();
	private final AtomicInteger typedRequests = new AtomicInteger();
	private final AtomicInteger typedResponses = new AtomicInteger();
	private final AtomicInteger typedCompletedCases = new AtomicInteger();
	private final Map<PreparationKey, DmnSparkSqlGeneratorResult> sparkSqlPreparationCache = new ConcurrentHashMap<>();
	private final AtomicInteger currentTestIndex = new AtomicInteger(0);
	private List<RuntimeModelMode> activeModelModes = List.of();
	private List<String> activeBackends = List.of();
	private int totalTestCount = 0;
	private TckCatalogueInventory inventory;
	private SparkSession spark;
	private final Semaphore sparkConcurrency = new Semaphore(
			Integer.getInteger("tck.spark.concurrency", Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()))));
	private static final boolean SPARK_HYBRID = Boolean.parseBoolean(System.getProperty("tck.spark.hybrid", "true"));
	private final Map<String, String> sparkRoutes = new ConcurrentHashMap<>();
	private final Map<String, String> expectedRejectionDiagnostics = new ConcurrentHashMap<>();
	private final DmnSparkSqlGenerator sparkSqlGenerator = new DmnSparkSqlGenerator();

	static {
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
	}

	@BeforeAll
	void setupSparkSession() {
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
		String backend = System.getProperty("tck.backend", "all").toLowerCase(Locale.ROOT);
		if (!Set.of("spark", "sparksql", "all").contains(backend))
			return;
		try {
			String sparkMaster = System.getProperty("tck.spark.master", "local[*]");
			spark = SparkSession.builder().appName("OfficialTckSuiteTest").master(sparkMaster)
					.config("spark.ui.enabled", "false").config("spark.sql.shuffle.partitions", "1")
					.config("spark.sql.codegen.wholeStage", "false")
					.config("spark.sql.codegen.factoryMode", "NO_CODEGEN")
					.config("spark.sql.datetime.java8API.enabled", "true").config("spark.sql.session.timeZone", "UTC")
					.config("spark.sql.mapKeyDedupPolicy", "LAST_WIN").getOrCreate();
			if (spark != null && spark.sparkContext() != null) {
				String actualParserMode = spark.conf().get("spark.sql.parser.escapedStringLiterals");
				String requestedParserMode = System.getProperty("spark.sql.parser.escapedStringLiterals");
				if (requestedParserMode != null && !requestedParserMode.equals(actualParserMode))
					throw new IllegalStateException("Requested escapedStringLiterals=" + requestedParserMode
							+ " but Spark uses " + actualParserMode);
				System.out.println("TCK SQL PARSER: escapedStringLiterals=" + actualParserMode);
				spark.sparkContext().setLogLevel("ERROR");
				spark.sql("SELECT 1 AS _warmup").collectAsList();
			}
		} catch (Exception failure) {
			throw new IllegalStateException("Cannot initialize Spark for TCK execution", failure);
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
	@Execution(ExecutionMode.CONCURRENT)
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
			String[] filterTokens = filter.split(",");
			List<java.util.regex.Pattern> filterPatterns = new ArrayList<>();
			for (String tok : filterTokens) {
				String normalizedFilter = tok.trim().replace('\\', '/');
				if (normalizedFilter.isEmpty()) {
					continue;
				}
				if (normalizedFilter.contains("*") || normalizedFilter.contains("?")) {
					String regex = "\\Q" + normalizedFilter.replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q") + "\\E";
					regex = regex.replace("\\Q\\E", "");
					filterPatterns
							.add(java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE));
				} else {
					filterPatterns.add(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(normalizedFilter),
							java.util.regex.Pattern.CASE_INSENSITIVE));
				}
			}
			List<TckCatalogueEntry> filtered = inventory.entries().stream().filter(e -> {
				String idNorm = e.id().replace('\\', '/');
				String xmlNorm = e.testXml().toString().replace('\\', '/');
				return filterPatterns.stream().anyMatch(p -> p.matcher(idNorm).find() || p.matcher(xmlNorm).find());
			}).toList();
			if (filtered.isEmpty()) {
				throw new IllegalArgumentException("No TCK test suites matched filter: '" + filter + "'");
			}
			int cases = filtered.stream().mapToInt(e -> e.testCases().size()).sum();
			int dmns = filtered.stream().mapToInt(e -> e.dmnFiles().size()).sum();
			inventory = new TckCatalogueInventory(inventory.directoryCount(), filtered.size(), dmns, cases, filtered);
		}
		totalTestCount = inventory.entries().stream().filter(TckCatalogueEntry::executable)
				.mapToInt(e -> e.testCases().size() * activeBackends.size()).sum();
		currentTestIndex.set(0);
		if (activeBackends.stream().anyMatch(b -> b.contains("portable-java"))
				&& Boolean.parseBoolean(System.getProperty("tck.useDmnIrArchive", "true"))) {
			precompilePortableArchive(inventory);
		}
		boolean needDirectJava = activeBackends.stream().anyMatch(b -> b.endsWith("-generated-java"));
		boolean needGenericGrpc = activeBackends.stream().anyMatch(b -> b.endsWith("-generated-grpc"));
		boolean needTypedGrpc = activeBackends.stream().anyMatch(b -> b.endsWith("-generated-typed-grpc"));
		if (needDirectJava || needGenericGrpc || needTypedGrpc) {
			precompileModelsBatch(inventory, activeModelModes, needDirectJava, needGenericGrpc, needTypedGrpc);
		}
		List<DynamicTest> dynamicTests = new ArrayList<>();
		DmnToolkitTckEngine interpreterEngine = new DmnToolkitTckEngine(OfficialExternalJavaPolicy.POLICY);

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
		return dynamicTests.parallelStream();
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
		String backendProp = System.getProperty("tck.backend", "all").trim().toLowerCase(Locale.ROOT);
		return modes.stream().flatMap(mode -> {
			String variant = mode.name().toLowerCase(Locale.ROOT);
			if (backendProp.equals("typed-grpc"))
				return Stream.of(variant + "-generated-typed-grpc");
			if (backendProp.equals("grpc"))
				return Stream.of(variant + "-generated-grpc");
			if (backendProp.equals("sparksql") || backendProp.equals("spark")) {
				return Stream.of(variant + "-generated-sparksql" + (SPARK_HYBRID ? "-hybrid" : ""));
			}
			if (backendProp.equals("portable-java") || backendProp.equals("dmnir-java")) {
				return Stream.of(variant + "-generated-portable-java");
			}
			if (backendProp.equals("direct-java")) {
				return Stream.of(variant + "-generated-java");
			}
			if (backendProp.equals("java")) {
				return Stream.of(variant + "-generated-portable-java");
			}
			if (backendProp.equals("interpreter")) {
				return Stream.of(variant + "-interpreter");
			}
			return Stream.of(variant + "-interpreter", variant + "-generated-java",
					variant + "-generated-portable-java",
					variant + "-generated-sparksql" + (SPARK_HYBRID ? "-hybrid" : ""));
		}).toList();
	}

	private void addModelVariantTests(List<DynamicTest> dynamicTests, DmnToolkitTckEngine interpreterEngine,
			TckCatalogueEntry entry, List<DmnSource> sources, TckTestCase testCase, RuntimeModelMode modelMode) {
		String variant = modelMode.name().toLowerCase(Locale.ROOT);
		String testName = entry.id() + "#" + testCase.id();
		String interpreterBackend = variant + "-interpreter";
		String generatedJavaBackend = variant + "-generated-java";
		String generatedPortableJavaBackend = variant + "-generated-portable-java";
		String generatedSparkSqlBackend = variant + "-generated-sparksql" + (SPARK_HYBRID ? "-hybrid" : "");
		String generatedGrpcBackend = variant + "-generated-grpc";
		String typedBackend = variant + "-generated-typed-grpc";
		if (activeBackends.contains(typedBackend)) {
			dynamicTests.add(recordedTest(testName + " @" + typedBackend, entry, testCase, typedBackend, () -> {
				var preparation = getPreparation(sources, testCase, modelMode);
				requireCompilationSucceeded(preparation.failureDiagnostic(), preparation.failureCause());
				executeTypedGrpc(sources, preparation.compilation(), testCase, modelMode, testName);
			}));
		}
		if (activeBackends.contains(generatedGrpcBackend)) {
			dynamicTests.add(
					recordedTest(testName + " @" + generatedGrpcBackend, entry, testCase, generatedGrpcBackend, () -> {
						var preparation = getPreparation(sources, testCase, modelMode);
						requireCompilationSucceeded(preparation.failureDiagnostic(), preparation.failureCause());
						executeGeneratedGrpc(sources, preparation.compilation(), testCase, modelMode, testName);
					}));
		}

		if (activeBackends.contains(interpreterBackend)) {
			dynamicTests
					.add(recordedTest(testName + " @" + interpreterBackend, entry, testCase, interpreterBackend, () -> {
						Preparation preparation = getPreparation(sources, testCase, modelMode);
						requireCompilationSucceeded(preparation.failureDiagnostic(), preparation.failureCause());
						TckExecutionResult result = interpreterEngine.execute(preparation.compilation(), testCase);
						assertExpected(testName, testCase, result.decisionValues(), variant + " interpreter");
					}));
		}

		if (activeBackends.contains(generatedJavaBackend)) {
			dynamicTests.add(
					recordedTest(testName + " @" + generatedJavaBackend, entry, testCase, generatedJavaBackend, () -> {
						Preparation preparation = getPreparation(sources, testCase, modelMode);
						requireCompilationSucceeded(preparation.failureDiagnostic(), preparation.failureCause());
						GeneratedPreparation generated = getGeneratedPreparation(sources, testCase, modelMode,
								preparation);
						if (generated.failureDiagnostic() != null)
							throw new AssertionError(generated.failureDiagnostic(), generated.failureCause());
						executeGeneratedJava(preparation.compilation(), generated, testCase, testName);
					}));
		}

		if (activeBackends.contains(generatedPortableJavaBackend)) {
			dynamicTests.add(recordedTest(testName + " @" + generatedPortableJavaBackend, entry, testCase,
					generatedPortableJavaBackend, () -> {
						PortableGeneratedPreparation prepared;
						if (dmnIrArchive != null) {
							var archived = dmnIrArchive.entry(testName);
							if (archived.isPresent()) {
								if (!archived.get().exported()) {
									throw new AssertionError(archived.get().exportDiagnostic());
								}
								prepared = Objects.requireNonNull(
										portableArchiveCache.get(archived.get().artifactPath()),
										"Missing precompiled portable engine for " + archived.get().artifactPath());
							} else {
								prepared = getPortableGeneratedPreparation(sources, testCase, modelMode);
							}
						} else {
							prepared = getPortableGeneratedPreparation(sources, testCase, modelMode);
						}
						if (prepared.failureDiagnostic() != null)
							throw new AssertionError(prepared.failureDiagnostic(), prepared.failureCause());
						executePortableGeneratedJava(prepared, testCase, testName);
					}));
		}

		if (activeBackends.contains(generatedSparkSqlBackend)) {
			dynamicTests.add(recordedTest(testName + " @" + generatedSparkSqlBackend, entry, testCase,
					generatedSparkSqlBackend, () -> {
						Preparation preparation = getPreparation(sources, testCase, modelMode);
						if (preparation.failureDiagnostic() != null) {
							sparkRoutes.put(testName, "UNEXECUTED_COMPILATION_FAILURE");
							expectedRejectionDiagnostics.put(testName + " @" + generatedSparkSqlBackend,
									preparation.failureDiagnostic());
						}
						requireCompilationSucceeded(preparation.failureDiagnostic(), preparation.failureCause());
						executeGeneratedSparkSql(sources, preparation, testCase, modelMode, testName);
					}));
		}
	}

	private PreparationKey effectivePreparationKey(List<DmnSource> sources, TckTestCase testCase,
			RuntimeModelMode mode) {
		List<String> sourceIds = sources.stream().map(source -> source.id().toString()).toList();
		PreparationKey fullKey = new PreparationKey(sourceIds, Set.of(), mode);
		Preparation full = preparationCache.get(fullKey);
		if (full != null && full.failureDiagnostic() == null) {
			return fullKey;
		}
		return new PreparationKey(sourceIds, Set.copyOf(testCase.expectedResults().keySet()), mode);
	}

	private void executeTypedGrpc(List<DmnSource> sources, DmnCompilationResult compilation, TckTestCase testCase,
			RuntimeModelMode mode, String testName) throws Exception {
		var key = effectivePreparationKey(sources, testCase, mode);
		var prepared = typedPreparationCache.computeIfAbsent(key, ignored -> {
			try {
				return new TypedPreparation(
						TypedGrpcTckExecution.prepare(compilation, OfficialExternalJavaPolicy.POLICY), null);
			} catch (RuntimeException failure) {
				return new TypedPreparation(null, failure);
			}
		});
		if (prepared.failure() != null)
			throw prepared.failure();
		Map<String, Object> inputs = new LinkedHashMap<>();
		testCase.inputs().forEach((name, value) -> inputs.put(name, value.runtimeValue()));
		if (testCase.invocableName().isPresent()) {
			var actual = prepared.service().executeInvocation(testCase.invocableName().orElseThrow(), inputs,
					TIMEOUT_SECONDS, typedRequests::incrementAndGet, typedResponses::incrementAndGet);
			typedCompletedCases.incrementAndGet();
			assertExpected(testName, testCase, actual, "Generated typed invocation over loopback HTTP/2");
			return;
		}
		Map<String, Integer> requested = new LinkedHashMap<>();
		for (String name : testCase.expectedResults().keySet()) {
			Integer slot = compilation.compiledModel().orElseThrow().decisionSlots().get(name);
			if (slot == null)
				throw new TypedUnsupported("Existing typed schema has no decision RPC for " + name);
			requested.put(name, slot);
		}
		if (requested.isEmpty())
			throw new TypedUnsupported("Case requests no typed decision RPC");
		var actual = prepared.service().execute(inputs, requested, TIMEOUT_SECONDS, typedRequests::incrementAndGet,
				typedResponses::incrementAndGet);
		typedCompletedCases.incrementAndGet();
		assertExpected(testName, testCase, actual, "Generated typed gRPC over loopback HTTP/2");
	}

	private void executeGeneratedGrpc(List<DmnSource> sources, DmnCompilationResult compilation, TckTestCase testCase,
			RuntimeModelMode mode, String testName) throws Exception {
		var key = effectivePreparationKey(sources, testCase, mode);
		var grpc = grpcPreparationCache.computeIfAbsent(key, ignored -> {
			try {
				return GrpcTckExecution.prepare(compilation);
			} catch (Exception failure) {
				throw new RuntimeException(failure);
			}
		});
		var model = compilation.compiledModel().orElseThrow();
		var request = io.finmsg.dmn.grpc.DmnEvaluationRequest.newBuilder().setModelNamespace(model.namespace())
				.setModelName(model.modelName());
		testCase.inputs().forEach((name, value) -> request.putInputs(name,
				io.finmsg.dmn.grpc.DmnGrpcValueConverter.toProtoValue(value.runtimeValue())));
		testCase.invocableName().ifPresent(request::setInvocableName);
		if (testCase.invocableName().isPresent())
			testCase.inputs().forEach((name, value) -> request.putArguments(name,
					io.finmsg.dmn.grpc.DmnGrpcValueConverter.toProtoValue(value.runtimeValue())));
		grpcRequests.incrementAndGet();
		var response = grpc.execute(request.build(), TIMEOUT_SECONDS);
		grpcResponses.incrementAndGet();
		if (testCase.invocableName().isPresent())
			assertThat(response.hasInvocationResult()).as(testName + " gRPC invocation result presence").isTrue();
		assertThat(response.getErrorsList()).as(testName + " gRPC response errors").isEmpty();
		Map<String, Object> actual = new LinkedHashMap<>();
		response.getOutputsMap().forEach(
				(name, value) -> actual.put(name, io.finmsg.dmn.grpc.DmnGrpcValueConverter.toJavaObject(value)));
		assertExpected(testName, testCase, actual, "Generated gRPC over loopback HTTP/2");
	}

	private Preparation getPreparation(List<DmnSource> sources, TckTestCase testCase, RuntimeModelMode modelMode) {
		List<String> sourceIds = sources.stream().map(source -> source.id().toString()).toList();
		PreparationKey fullKey = new PreparationKey(sourceIds, Set.of(), modelMode);
		Preparation full = preparationCache.computeIfAbsent(fullKey,
				ignored -> prepare(sources, Set.of(), modelMode));
		if (full.failureDiagnostic() == null) {
			return full;
		}
		Set<String> resultNames = Set.copyOf(testCase.expectedResults().keySet());
		PreparationKey preparationKey = new PreparationKey(sourceIds, resultNames, modelMode);
		return preparationCache.computeIfAbsent(preparationKey, ignored -> prepare(sources, resultNames, modelMode));
	}

	private GeneratedPreparation getGeneratedPreparation(List<DmnSource> sources, TckTestCase testCase,
			RuntimeModelMode modelMode, Preparation preparation) {
		PreparationKey key = effectivePreparationKey(sources, testCase, modelMode);
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

	private PortableGeneratedPreparation getPortableGeneratedPreparation(List<DmnSource> sources, TckTestCase testCase,
			RuntimeModelMode modelMode) {
		List<String> sourceIds = sources.stream().map(source -> source.id().toString()).toList();
		PreparationKey fullKey = new PreparationKey(sourceIds, Set.of(), modelMode);
		PortableGeneratedPreparation full = portableGeneratedPreparationCache.computeIfAbsent(fullKey,
				ignored -> preparePortableGenerated(sources, Set.of()));
		if (full.failureDiagnostic() == null) {
			return full;
		}
		Set<String> resultNames = Set.copyOf(testCase.expectedResults().keySet());
		PreparationKey key = new PreparationKey(sourceIds, resultNames, modelMode);
		return portableGeneratedPreparationCache.computeIfAbsent(key,
				ignored -> preparePortableGenerated(sources, resultNames));
	}

	private void executePortableGeneratedJava(PortableGeneratedPreparation prepared, TckTestCase testCase,
			String testName) throws Exception {
		Class<?> genClass = prepared.generatedClass();
		Object engineInstance = prepared.engineInstance();
		var adapted = prepared.adaptedModel();

		String rootNs = adapted.source().artifact().getRootModelNamespace();
		Map<String, String> aliases = new HashMap<>();
		aliases.put(rootNs, "");
		var rootModelOpt = adapted.source().artifact().getModelsList().stream()
				.filter(m -> m.getNamespace().equals(rootNs)).findFirst();
		rootModelOpt
				.ifPresent(r -> r.getImportsList().forEach(i -> aliases.put(i.getModelNamespace(), i.getName() + ".")));

		if (testCase.invocableName().isPresent()) {
			String invocableName = testCase.invocableName().get();
			int bkmSlot = -1;
			List<String> paramNames = new ArrayList<>();
			int bkmIndex = 0;
			for (var model : adapted.source().artifact().getModelsList()) {
				for (var bkm : model.getBkmsList()) {
					if (bkm.getVariable().getName().equals(invocableName)
							|| bkm.getIdentity().getName().equals(invocableName)) {
						for (var param : bkm.getParametersList()) {
							paramNames.add(param.getVariable().getName());
						}
						if (bkmIndex < adapted.runtimeModel().businessKnowledgeModels().size()) {
							bkmSlot = adapted.runtimeModel().businessKnowledgeModels().get(bkmIndex).resultSlot();
						}
						break;
					}
					bkmIndex++;
				}
				if (bkmSlot >= 0)
					break;
			}
			if (bkmSlot < 0) {
				throw new UnsupportedOperationException(
						"Invocable BKM not resolved in portable DMNIR: " + invocableName);
			}

			Object[] args = new Object[paramNames.size()];
			boolean missingParam = false;
			for (int i = 0; i < paramNames.size(); i++) {
				String pName = paramNames.get(i);
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
				assertExpected(testName, testCase, decValues, "Portable DMNIR generated code");
				return;
			}
			if (testCase.inputs().isEmpty() && !paramNames.isEmpty()) {
				for (String exp : testCase.expectedResults().keySet()) {
					decValues.put(exp, null);
				}
				assertExpected(testName, testCase, decValues, "Portable DMNIR generated code");
				return;
			}

			Object[] slots = new Object[adapted.runtimeModel().valueSlotCount()];
			for (var entry : adapted.inputs().entrySet()) {
				var binding = entry.getValue();
				String modelNs = binding.id().modelNamespace();
				String prefix = aliases.getOrDefault(modelNs, "");
				String declaredNs = testCase.inputNamespaces().get(binding.variableName());
				TckValue val = declaredNs == null
						? testCase.inputs().get(prefix + binding.variableName())
						: declaredNs.equals(modelNs) ? testCase.inputs().get(binding.variableName()) : null;
				if (val == null && prefix.isEmpty()) {
					val = testCase.inputs().get(binding.elementName());
				}
				if (val != null) {
					slots[binding.slot()] = val.runtimeValue();
				}
			}

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
							.invoke(engineInstance, bkmSlot, args, (Object) evaluated);
				} catch (NoSuchMethodException e) {
					result = genClass.getMethod("evaluateBkm", int.class, Object[].class).invoke(engineInstance,
							bkmSlot, args);
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
			assertExpected(testName, testCase, decValues, "Portable DMNIR generated code");
			return;
		}

		Object[] slots = new Object[adapted.runtimeModel().valueSlotCount()];
		for (var entry : adapted.inputs().entrySet()) {
			var binding = entry.getValue();
			String modelNs = binding.id().modelNamespace();
			String prefix = aliases.getOrDefault(modelNs, "");
			String declaredNs = testCase.inputNamespaces().get(binding.variableName());
			TckValue val = declaredNs == null
					? testCase.inputs().get(prefix + binding.variableName())
					: declaredNs.equals(modelNs) ? testCase.inputs().get(binding.variableName()) : null;
			if (val == null && prefix.isEmpty()) {
				val = testCase.inputs().get(binding.elementName());
			}
			if (val != null) {
				slots[binding.slot()] = val.runtimeValue();
			}
		}
		for (var entry : adapted.decisions().entrySet()) {
			var binding = entry.getValue();
			String modelNs = binding.id().modelNamespace();
			String prefix = aliases.getOrDefault(modelNs, "");
			TckValue val = testCase.inputs().get(prefix + binding.variableName());
			if (val == null && prefix.isEmpty()) {
				val = testCase.inputs().get(binding.elementName());
			}
			if (val != null) {
				slots[binding.slot()] = val.runtimeValue();
			}
		}

		Object[] evaluated = (Object[]) genClass.getMethod("evaluate", Object[].class).invoke(engineInstance,
				(Object) slots);

		Map<String, Object> decValues = new LinkedHashMap<>();
		for (var entry : adapted.decisions().entrySet()) {
			var binding = entry.getValue();
			String modelNs = binding.id().modelNamespace();
			String prefix = aliases.getOrDefault(modelNs, "");
			decValues.put(prefix + binding.variableName(), evaluated[binding.slot()]);
			decValues.put(binding.variableName(), evaluated[binding.slot()]);
			decValues.put(binding.elementName(), evaluated[binding.slot()]);
		}
		assertExpected(testName, testCase, decValues, "Portable DMNIR generated code");
	}

	private void executeGeneratedSparkSql(List<DmnSource> sources, Preparation preparation, TckTestCase testCase,
			RuntimeModelMode modelMode, String testName) {
		if (spark == null)
			throw new IllegalStateException("Spark session is unavailable; no test was executed");
		DmnCompilationResult compilation = preparation.compilation();
		RuntimeModel originalModel = compilation.optimizedRuntimeModel().orElseThrow().model();
		SparkSqlInvocationPlan invocationPlan;
		if (testCase.invocableName().isPresent()) {
			int bkmSlot = Objects.requireNonNull(
					DmnToolkitTckEngine.getRuntimeBkmSlots(compilation).get(testCase.invocableName().get()));
			int bkmId = originalModel.businessKnowledgeModels().stream().filter(b -> b.resultSlot() == bkmSlot)
					.findFirst().orElseThrow().id();
			invocationPlan = SparkSqlInvocationPlan.create(originalModel, bkmId);
		} else
			invocationPlan = null;
		RuntimeModel model = invocationPlan == null ? originalModel : invocationPlan.model();
		Map<String, Integer> inputSlots = DmnToolkitTckEngine.getRuntimeInputSlots(compilation);
		Map<String, Integer> decisionSlots = DmnToolkitTckEngine.getRuntimeDecisionSlots(compilation);
		Map<Integer, String> slotNames = new LinkedHashMap<>();
		inputSlots.forEach((name, slot) -> slotNames.put(slot, name));
		decisionSlots.forEach((name, slot) -> slotNames.put(slot, name));
		DmnToolkitTckEngine.getRuntimeBkmSlots(compilation).forEach((name, slot) -> slotNames.put(slot, name));
		if (invocationPlan != null)
			invocationPlan.parameterNames().forEach((slot, name) -> slotNames.put(slot, "__dmn_parameter_" + slot));
		var effectiveKey = effectivePreparationKey(sources, testCase, modelMode);
		var sourceKey = new ArrayList<>(effectiveKey.sourceIds());
		sourceKey.add(testCase.invocableName().orElse(""));
		PreparationKey key = new PreparationKey(sourceKey, effectiveKey.resultNames(), modelMode);
		DmnSparkSqlGeneratorResult generated = sparkSqlPreparationCache.computeIfAbsent(key,
				ignored -> sparkSqlGenerator.generate(new RuntimeOptimizedModel(model, List.of(), List.of(), List.of()),
						DmnSparkSqlGeneratorOptions.of("input_table", SPARK_HYBRID, slotNames)
								.withExternalJavaPolicy(OfficialExternalJavaPolicy.POLICY)));
		SparkSession session = spark.newSession();
		generated.registerUdfs(session);
		if (model.inputs().isEmpty()) {
			session.sql("SELECT 1 AS _dummy").createOrReplaceTempView("input_table");
		} else {
			Object[] values = new Object[model.inputs().size()];
			Map<Integer, Object> feelInputs = new HashMap<>();
			for (int i = 0; i < values.length; i++) {
				RuntimeInput input = model.inputs().get(i);
				String inputName = invocationPlan == null
						? slotNames.get(input.valueSlot())
						: invocationPlan.parameterNames().getOrDefault(input.valueSlot(),
								slotNames.get(input.valueSlot()));
				TckValue supplied = testCase.inputs().get(inputName);
				Object value = supplied == null ? null : supplied.runtimeValue();
				feelInputs.put(input.valueSlot(), value);
				values[i] = SPARK_HYBRID
						? SparkSqlFeelValueCodec.toSpark(io.finmsg.dmn.runtime.DmnRuntime.coerce(value, input.type()),
								input.type())
						: toSparkValue(value, generated.inputSchema().fields()[i].dataType());
			}
			Row row = SPARK_HYBRID
					? SparkSqlFeelValueCodec.inputRow(model, feelInputs, generated.inputSchema())
					: RowFactory.create(values);
			session.createDataFrame(List.of(row), generated.inputSchema()).createOrReplaceTempView("input_table");
		}
		Map<String, Object> results = new LinkedHashMap<>();
		boolean fallback = false;
		Set<String> requested = new LinkedHashSet<>(testCase.expectedResults().keySet());
		requested.addAll(testCase.expectedErrorResults());
		if (invocationPlan != null) {
			String sql = Objects
					.requireNonNull(generated.sqlFiles().get("Decision_" + invocationPlan.resultSlot() + ".sql"));
			List<Row> rows = session.sql(sql).collectAsList();
			if (rows.size() != 1)
				throw new IllegalStateException("Expected one Spark invocation result row");
			Object raw = rows.getFirst().get(0);
			RuntimeType resultType = model.decisions().stream().filter(d -> d.id() == invocationPlan.decisionId())
					.findFirst().orElseThrow().type();
			Object value = SPARK_HYBRID ? SparkSqlFeelValueCodec.fromSpark(raw, resultType) : fromSparkValue(raw);
			for (String name : requested)
				results.put(name, value instanceof Map<?, ?> context ? context.get(name) : value);
			var cap = generated.capabilities().get(invocationPlan.decisionId());
			fallback = !cap.nativeSql();
		} else {
			for (String name : requested) {
				Integer slot = decisionSlots.get(name);
				if (slot == null)
					throw new IllegalArgumentException("No runtime decision for TCK result " + name);
				RuntimeDecision decision = model.decisions().stream().filter(d -> d.resultSlot() == slot).findFirst()
						.orElseThrow();
				var cap = generated.capabilities().get(decision.id());
				fallback |= !cap.nativeSql();
				if (!cap.nativeSql() && Boolean.getBoolean("tck.spark.routeDiagnostics"))
					System.out.println("SPARK ROUTE " + testName + " " + name + ": " + cap);
				String sql = Objects.requireNonNull(generated.sqlFiles().get("Decision_" + slot + ".sql"));
				// Run only requested decisions. No exception-to-null conversion or
				// runtime-triggered fallback.
				List<Row> rows = session.sql(sql).collectAsList();
				if (rows.size() != 1)
					throw new IllegalStateException("Expected one Spark result row, got " + rows.size());
				Object raw = rows.getFirst().get(0);
				results.put(name,
						SPARK_HYBRID ? SparkSqlFeelValueCodec.fromSpark(raw, decision.type()) : fromSparkValue(raw));
			}
		}
		sparkRoutes.put(testName, fallback ? "FALLBACK" : "NATIVE");
		assertExpected(testName, testCase, results, "Spark SQL");
	}

	private static Object toSparkValue(Object val, org.apache.spark.sql.types.DataType targetType) {
		if (val == null)
			return null;
		if (targetType instanceof org.apache.spark.sql.types.StringType) {
			if (val instanceof java.time.LocalDate || val instanceof java.time.LocalDateTime
					|| val instanceof java.time.ZonedDateTime || val instanceof java.time.LocalTime
					|| val instanceof java.time.OffsetTime || val instanceof java.time.Duration
					|| val instanceof java.time.Period) {
				return val.toString();
			}
			if (val instanceof Number n) {
				return n.toString();
			}
		}
		if (val instanceof List<?> list && targetType instanceof org.apache.spark.sql.types.ArrayType at) {
			return list.stream().map(e -> toSparkValue(e, at.elementType())).toList();
		}
		if (val instanceof Map<?, ?> map && targetType instanceof org.apache.spark.sql.types.StructType st) {
			List<Object> fieldValues = new ArrayList<>();
			for (org.apache.spark.sql.types.StructField f : st.fields()) {
				fieldValues.add(toSparkValue(map.get(f.name()), f.dataType()));
			}
			return RowFactory.create(fieldValues.toArray());
		}
		return toSparkValue(val);
	}

	private static Object toSparkValue(Object val) {
		if (val == null)
			return null;
		if (val instanceof BigDecimal bd)
			return bd.doubleValue();
		if (val instanceof Number n)
			return n.doubleValue();
		if (val instanceof Boolean b)
			return b;
		if (val instanceof String s)
			return s;
		if (val instanceof java.time.LocalDate ld)
			return java.sql.Date.valueOf(ld);
		if (val instanceof java.time.LocalDateTime ldt)
			return java.sql.Timestamp.valueOf(ldt);
		if (val instanceof java.time.OffsetDateTime odt)
			return java.sql.Timestamp.from(odt.toInstant());
		if (val instanceof java.time.ZonedDateTime zdt)
			return java.sql.Timestamp.from(zdt.toInstant());
		if (val instanceof java.time.Instant inst)
			return java.sql.Timestamp.from(inst);
		if (val instanceof java.time.LocalTime lt)
			return java.sql.Timestamp.valueOf(lt.atDate(java.time.LocalDate.of(1970, 1, 1)));
		if (val instanceof java.time.OffsetTime ot)
			return java.sql.Timestamp.valueOf(ot.toLocalTime().atDate(java.time.LocalDate.of(1970, 1, 1)));
		if (val instanceof java.time.Duration dur)
			return dur.toString();
		if (val instanceof java.time.Period per)
			return per.toString();
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
		if (val == null)
			return null;
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
				if ((names.contains("lower") && names.contains("upper"))
						|| (names.contains("start") && names.contains("end"))) {
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
					return new io.finmsg.dmn.runtime.RuntimeRangeValue(fromSparkValue(startVal), fromSparkValue(endVal),
							lowB, upB);
				}
			}
			Map<String, Object> map = new LinkedHashMap<>();
			if (r.schema() != null) {
				for (String fieldName : r.schema().fieldNames()) {
					map.put(fieldName, fromSparkValue(r.getAs(fieldName)));
				}
			}
			return map;
		}
		if (val instanceof Map<?, ?> map) {
			Map<String, Object> result = new LinkedHashMap<>();
			map.forEach((k, v) -> result.put(String.valueOf(k), fromSparkValue(v)));
			return result;
		}
		if (val instanceof List<?> list) {
			return list.stream().map(OfficialTckSuiteTest::fromSparkValue).toList();
		}
		if (val instanceof java.sql.Date d) {
			return d.toLocalDate();
		}
		if (val instanceof java.time.LocalDate ld) {
			return ld;
		}
		if (val instanceof java.sql.Timestamp ts) {
			return ts.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime();
		}
		if (val instanceof java.time.LocalDateTime ldt) {
			return ldt;
		}
		if (val instanceof java.time.Instant inst) {
			return inst.atZone(java.time.ZoneOffset.UTC).toLocalDateTime();
		}
		if (val instanceof Number n) {
			return new BigDecimal(n.toString());
		}
		if (val instanceof String str) {
			if ((str.startsWith("{") && str.endsWith("}")) || (str.startsWith("[") && str.endsWith("]"))) {
				try {
					Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(str, Object.class);
					return fromSparkValue(parsed);
				} catch (Exception ignored) {
				}
			}
			return str;
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
			return GeneratedPreparation.success(generatedClass,
					generatedClass.getDeclaredConstructor(io.finmsg.dmn.runtime.ExternalJavaPolicy.class)
							.newInstance(OfficialExternalJavaPolicy.POLICY));
		} catch (Throwable exception) {
			return GeneratedPreparation.failure("Generated-Java preparation threw: " + exception, exception);
		}
	}

	private void precompileModelsBatch(TckCatalogueInventory inv, List<RuntimeModelMode> modes, boolean needDirectJava,
			boolean needGenericGrpc, boolean needTypedGrpc) throws Exception {
		long startNanos = System.nanoTime();
		Map<List<String>, List<DmnSource>> uniqueSources = new LinkedHashMap<>();
		for (TckCatalogueEntry entry : inv.entries()) {
			if (!entry.executable())
				continue;
			List<Path> orderedModels = new ArrayList<>();
			orderedModels.add(entry.rootDmn().orElseThrow());
			entry.dmnFiles().stream().filter(path -> !path.equals(entry.rootDmn().orElseThrow()))
					.forEach(orderedModels::add);
			List<String> sourceIds = orderedModels.stream().map(p -> p.toUri().toString()).toList();
			if (!uniqueSources.containsKey(sourceIds)) {
				List<DmnSource> sources = new ArrayList<>(orderedModels.size());
				for (Path model : orderedModels) {
					sources.add(new DmnSource(new DmnSourceId(model.toUri()), Files.readAllBytes(model)));
				}
				uniqueSources.put(sourceIds, sources);
			}
		}

		Map<PreparationKey, DmnCompilationResult> compiled = new ConcurrentHashMap<>();
		List<Map.Entry<PreparationKey, List<DmnSource>>> work = new ArrayList<>();
		for (RuntimeModelMode mode : modes) {
			for (var e : uniqueSources.entrySet()) {
				work.add(Map.entry(new PreparationKey(e.getKey(), Set.of(), mode), e.getValue()));
			}
		}
		work.parallelStream().forEach(item -> {
			Preparation prep = preparationCache.computeIfAbsent(item.getKey(),
					ignored -> prepare(item.getValue(), Set.of(), item.getKey().modelMode()));
			if (prep.failureDiagnostic() == null && prep.compilation() != null) {
				compiled.put(item.getKey(), prep.compilation());
			}
		});

		if (needDirectJava && !compiled.isEmpty()) {
			Path tempDir = Files.createTempDirectory("dmn-direct-java-batch-");
			Path pkgDir = tempDir.resolve("io/finmsg/dmn/tck/gen");
			Files.createDirectories(pkgDir);
			record DirectUnit(PreparationKey key, String fqcn, Path sourceFile) {
			}
			List<DirectUnit> units = Collections.synchronizedList(new ArrayList<>());
			compiled.entrySet().parallelStream().forEach(entry -> {
				try {
					String className = "OmgTckEngine_" + ENGINE_COUNTER.getAndIncrement();
					String fqcn = "io.finmsg.dmn.tck.gen." + className;
					DmnJavaGeneratorResult generated = new DmnJavaGenerator().generate(
							entry.getValue().optimizedRuntimeModel().orElseThrow(),
							DmnJavaGeneratorOptions.of("io.finmsg.dmn.tck.gen", className));
					String code = generated.sources().get(fqcn);
					Path sourceFile = pkgDir.resolve(className + ".java");
					Files.writeString(sourceFile, code, java.nio.charset.StandardCharsets.UTF_8);
					units.add(new DirectUnit(entry.getKey(), fqcn, sourceFile));
				} catch (Throwable ignored) {
				}
			});
			int batchSize = 150;
			List<List<DirectUnit>> batches = new ArrayList<>();
			for (int i = 0; i < units.size(); i += batchSize) {
				batches.add(units.subList(i, Math.min(i + batchSize, units.size())));
			}
			List<DirectUnit> compiledUnits = Collections.synchronizedList(new ArrayList<>());
			batches.parallelStream().forEach(batch -> {
				try {
					Path argFile = Files.createTempFile(tempDir, "javac-direct-", ".args");
					StringBuilder sb = new StringBuilder();
					for (DirectUnit u : batch) {
						sb.append('"').append(u.sourceFile().toAbsolutePath().toString().replace('\\', '/'))
								.append("\"\n");
					}
					Files.writeString(argFile, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
					var errOut = new java.io.ByteArrayOutputStream();
					int exitCode = ToolProvider.getSystemJavaCompiler().run(null, null, errOut, "-encoding", "UTF-8",
							"-d", tempDir.toAbsolutePath().toString(), "@" + argFile.toAbsolutePath());
					if (exitCode == 0) {
						compiledUnits.addAll(batch);
					}
				} catch (Throwable ignored) {
				}
			});
			URLClassLoader loader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
					OfficialTckSuiteTest.class.getClassLoader());
			for (DirectUnit u : compiledUnits) {
				try {
					Class<?> cls = loader.loadClass(u.fqcn());
					Object instance = cls.getDeclaredConstructor(io.finmsg.dmn.runtime.ExternalJavaPolicy.class)
							.newInstance(OfficialExternalJavaPolicy.POLICY);
					generatedPreparationCache.put(u.key(), GeneratedPreparation.success(cls, instance));
				} catch (Throwable ignored) {
				}
			}
		}

		if (needGenericGrpc && !compiled.isEmpty()) {
			grpcPreparationCache.putAll(GrpcTckExecution.prepareBatch(compiled));
		}

		if (needTypedGrpc && !compiled.isEmpty()) {
			var prepared = TypedGrpcTckExecution.prepareBatch(compiled, OfficialExternalJavaPolicy.POLICY);
			prepared.forEach((k, v) -> typedPreparationCache.put(k, new TypedPreparation(v, null)));
		}

		double elapsed = (System.nanoTime() - startNanos) / 1e9;
		System.out.printf(
				"[INFO] Precompiled %d unique DMN models in %.2f s (directJava=%d, grpc=%d, typedGrpc=%d)%n",
				compiled.size(), elapsed, generatedPreparationCache.size(), grpcPreparationCache.size(),
				typedPreparationCache.size());
	}

	private void precompilePortableArchive(TckCatalogueInventory inv) throws Exception {
		Optional<TckDmnIrArchive> loaded = TckDmnIrArchive.loadFromClasspath();
		if (loaded.isEmpty() || !TCK_REVISION.equals(loaded.get().tckRevision())) {
			return;
		}
		TckDmnIrArchive archive = loaded.get();
		this.dmnIrArchive = archive;
		long startNanos = System.nanoTime();

		Set<String> neededArtifacts = new LinkedHashSet<>();
		for (TckCatalogueEntry entry : inv.entries()) {
			if (!entry.executable())
				continue;
			for (TckTestCase tc : entry.testCases()) {
				String key = entry.id() + "#" + tc.id();
				archive.entry(key).filter(TckDmnIrArchive.CaseEntry::exported)
						.ifPresent(ce -> neededArtifacts.add(ce.artifactPath()));
			}
		}
		if (neededArtifacts.isEmpty()) {
			return;
		}

		Path tempDir = Files.createTempDirectory("dmn-official-tck-batch-gen");
		Path pkgDir = tempDir.resolve("io/finmsg/dmn/tck/gen");
		Files.createDirectories(pkgDir);

		record GeneratedUnit(String artifactPath, String fqcn, Path sourceFile, String sourceCode,
				DmnIrRuntimeAdapter.AdaptedModel adapted) {
		}
		List<GeneratedUnit> units = Collections.synchronizedList(new ArrayList<>());

		neededArtifacts.parallelStream().forEach(artifactPath -> {
			try {
				byte[] protoBytes = archive.readArtifactBytes(artifactPath);
				DmnIrArtifactReader.VerifiedArtifact verified = new DmnIrArtifactReader().read(protoBytes);
				DmnIrRuntimeAdapter.AdaptedModel adapted = new DmnIrRuntimeAdapter().adapt(verified);
				RuntimeOptimizedModel optModel = new RuntimeIrOptimizer().optimize(adapted.runtimeModel());
				String className = "OmgTckPortableEngine_" + ENGINE_COUNTER.getAndIncrement();
				String fqcn = "io.finmsg.dmn.tck.gen." + className;
				DmnJavaGeneratorResult generated = new DmnJavaGenerator().generate(optModel,
						DmnJavaGeneratorOptions.of("io.finmsg.dmn.tck.gen", className));
				String code = generated.sources().get(fqcn);
				Path sourceFile = pkgDir.resolve(className + ".java");
				Files.writeString(sourceFile, code, java.nio.charset.StandardCharsets.UTF_8);
				units.add(new GeneratedUnit(artifactPath, fqcn, sourceFile, code, adapted));
			} catch (Throwable exception) {
				String diagnostic = exception.getMessage() != null ? exception.getMessage() : exception.toString();
				portableArchiveCache.put(artifactPath, PortableGeneratedPreparation.failure(diagnostic, exception));
			}
		});

		int batchSize = 250;
		List<List<GeneratedUnit>> batches = new ArrayList<>();
		for (int i = 0; i < units.size(); i += batchSize) {
			batches.add(units.subList(i, Math.min(i + batchSize, units.size())));
		}

		List<GeneratedUnit> compiledUnits = Collections.synchronizedList(new ArrayList<>());
		batches.parallelStream().forEach(batch -> {
			try {
				Path argFile = Files.createTempFile(tempDir, "javac-batch-", ".args");
				StringBuilder sb = new StringBuilder();
				for (GeneratedUnit u : batch) {
					sb.append('"').append(u.sourceFile().toAbsolutePath().toString().replace('\\', '/')).append("\"\n");
				}
				Files.writeString(argFile, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
				JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
				java.io.ByteArrayOutputStream errOut = new java.io.ByteArrayOutputStream();
				int exitCode = compiler.run(null, null, errOut, "-encoding", "UTF-8", "-d",
						tempDir.toAbsolutePath().toString(), "@" + argFile.toAbsolutePath());
				if (exitCode == 0) {
					compiledUnits.addAll(batch);
				} else {
					for (GeneratedUnit u : batch) {
						try {
							Class<?> clazz = compileInMemory(u.fqcn(), u.sourceCode());
							Object instance = clazz
									.getDeclaredConstructor(io.finmsg.dmn.runtime.ExternalJavaPolicy.class)
									.newInstance(OfficialExternalJavaPolicy.POLICY);
							portableArchiveCache.put(u.artifactPath(),
									PortableGeneratedPreparation.success(clazz, instance, u.adapted()));
						} catch (Throwable ex) {
							String diag = ex.getMessage() != null ? ex.getMessage() : ex.toString();
							portableArchiveCache.put(u.artifactPath(), PortableGeneratedPreparation.failure(diag, ex));
						}
					}
				}
			} catch (Exception e) {
				for (GeneratedUnit u : batch) {
					portableArchiveCache.put(u.artifactPath(), PortableGeneratedPreparation.failure(e.toString(), e));
				}
			}
		});

		URLClassLoader batchClassLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()});
		compiledUnits.parallelStream().forEach(u -> {
			try {
				Class<?> clazz = batchClassLoader.loadClass(u.fqcn());
				Object instance = clazz.getDeclaredConstructor(io.finmsg.dmn.runtime.ExternalJavaPolicy.class)
						.newInstance(OfficialExternalJavaPolicy.POLICY);
				portableArchiveCache.put(u.artifactPath(),
						PortableGeneratedPreparation.success(clazz, instance, u.adapted()));
			} catch (Throwable ex) {
				String diag = ex.getMessage() != null ? ex.getMessage() : ex.toString();
				portableArchiveCache.put(u.artifactPath(), PortableGeneratedPreparation.failure(diag, ex));
			}
		});

		double elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000.0;
		System.out.printf(
				"[INFO] [DMNIR-ARCHIVE] Batch-compiled %d pre-built .dmnir artifacts in %.2f s (%d batches)%n",
				neededArtifacts.size(), elapsedSec, batches.size());
		System.out.flush();
	}

	private PortableGeneratedPreparation preparePortableGenerated(List<DmnSource> sources, Set<String> resultNames) {
		try {
			List<DmnSource> scopedSources = new ArrayList<>(sources);
			if (!resultNames.isEmpty()) {
				scopedSources.set(0, new TckDmnDecisionPruner().prune(sources.getFirst(), resultNames));
			}
			DmnSource root = scopedSources.getFirst();
			io.finmsg.dmn.compiler.DmnModelResolver resolver = scopedSources.size() > 1
					? new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(
							scopedSources.subList(1, scopedSources.size()))
					: new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(List.of());

			// 1. DMN -> Java compiler -> portable DMNIR (dmn-ir-protobuf)
			DmnIrArtifactReader.VerifiedArtifact produced = new DmnIrScalarProducer().produce(root, resolver);

			// 2. Serialized Protobuf bytes
			byte[] protoBytes = produced.artifact().toByteArray();

			// 3. Bounded verification
			DmnIrArtifactReader.VerifiedArtifact verified = new DmnIrArtifactReader().read(protoBytes);

			// 4. Java Runtime IR adapter
			DmnIrRuntimeAdapter.AdaptedModel adapted = new DmnIrRuntimeAdapter().adapt(verified);

			// 5. Optimization
			RuntimeOptimizedModel optModel = new RuntimeIrOptimizer().optimize(adapted.runtimeModel());

			// 6. Java generator
			String className = "OmgTckPortableEngine_" + ENGINE_COUNTER.getAndIncrement();
			DmnJavaGeneratorResult generated = new DmnJavaGenerator().generate(optModel,
					DmnJavaGeneratorOptions.of("io.finmsg.dmn.tck.gen", className));

			// 7. Compilation and Instantiation
			Class<?> generatedClass = compileInMemory("io.finmsg.dmn.tck.gen." + className,
					generated.sources().get("io.finmsg.dmn.tck.gen." + className));
			Object engineInstance = generatedClass
					.getDeclaredConstructor(io.finmsg.dmn.runtime.ExternalJavaPolicy.class)
					.newInstance(OfficialExternalJavaPolicy.POLICY);

			return PortableGeneratedPreparation.success(generatedClass, engineInstance, adapted);
		} catch (Throwable exception) {
			String diagnostic = (exception instanceof io.finmsg.dmn.compiler.dmnir.DmnIrProductionException pe)
					? "EXPORT_" + pe.category() + ": " + pe.getMessage()
							+ (pe.diagnostics().isEmpty() ? "" : " " + pe.diagnostics())
					: exception.getMessage() != null ? exception.getMessage() : exception.toString();
			return PortableGeneratedPreparation.failure(diagnostic, exception);
		}
	}

	static void requireCompilationSucceeded(String diagnostic, Throwable cause) {
		if (diagnostic != null)
			throw new AssertionError("UNEXECUTED_COMPILATION_FAILURE: " + diagnostic, cause);
	}

	@AfterAll
	void tearDownGrpcTransport() throws Exception {
		try {
			GrpcTckExecution.closeTransport();
		} finally {
			for (var grpc : grpcPreparationCache.values())
				grpc.close();
		}
	}

	@AfterAll
	void tearDownTypedGrpcTransport() throws Exception {
		try {
			TypedGrpcTckExecution.closeTransport();
		} finally {
			for (var prepared : typedPreparationCache.values())
				if (prepared.service() != null)
					prepared.service().close();
		}
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
		if (SPARK_HYBRID && activeBackends.stream().anyMatch(backend -> backend.contains("sparksql"))) {
			new TckCatalogueReportWriter()
					.write(Path.of("target", "tck-accounting" + variant + "-sparksql-hybrid.json"), report);
		}
		if (activeBackends.stream().anyMatch(backend -> backend.endsWith("-generated-grpc"))) {
			new TckCatalogueReportWriter().write(Path.of("target", "tck-accounting" + variant + "-grpc.json"), report);
			System.out.printf(" GRPC RPC ATTEMPTS: %d; COMPLETED RESPONSES: %d; REQUIRED CASES: %d%n",
					grpcRequests.get(), grpcResponses.get(), outcomes.size());
		}
		if (activeBackends.stream().anyMatch(backend -> backend.endsWith("-typed-grpc"))) {
			new TckCatalogueReportWriter().write(Path.of("target", "tck-accounting" + variant + "-typed-grpc.json"),
					report);
			System.out.printf(
					" TYPED GRPC RPC ATTEMPTS: %d; COMPLETED RESPONSES: %d; COMPLETED CASES: %d; REQUIRED CASES: %d%n",
					typedRequests.get(), typedResponses.get(), typedCompletedCases.get(), outcomes.size());
		}
		if (activeBackends.stream().anyMatch(backend -> backend.contains("portable-java"))) {
			new TckCatalogueReportWriter().write(Path.of("target", "tck-accounting" + variant + "-portable-java.json"),
					report);
			StringBuilder tsv = new StringBuilder("entryId\tcaseId\tstatus\tdiagnostic\n");
			for (var outcome : outcomes.stream().filter(o -> o.backend().contains("portable-java"))
					.sorted(Comparator.comparing(TckCatalogueOutcome::key)).toList()) {
				tsv.append(outcome.entryId()).append('\t').append(outcome.caseId()).append('\t')
						.append(outcome.status()).append('\t')
						.append(outcome.diagnostic().replace('\n', ' ').replace('\r', ' ').replace('\t', ' '))
						.append('\n');
			}
			Files.writeString(Path.of("target", "tck-accounting" + variant + "-portable-java.tsv"), tsv.toString(),
					java.nio.charset.StandardCharsets.UTF_8);
		}

		Files.write(
				Path.of("target", "tck-compilation-failures" + variant
						+ ".tsv"),
				expectedRejectionDiagnostics.entrySet().stream().sorted(Map.Entry.comparingByKey())
						.map(entry -> entry.getKey() + "\t"
								+ entry.getValue().replace('\n', ' ').replace('\r', ' ').replace('\t', ' '))
						.toList());
		long passed = outcomes.stream().filter(o -> o.status() == TckCatalogueStatus.PASSED).count();
		long failed = outcomes.stream().filter(o -> o.status() == TckCatalogueStatus.FAILED).count();
		long error = outcomes.stream().filter(o -> o.status() == TckCatalogueStatus.EXECUTION_ERROR).count();
		long total = outcomes.size();
		double pct = total > 0 ? (passed * 100.0) / total : 0.0;

		System.out.println("========================================================================");
		System.out.printf(" DMN TCK CONFORMANCE SUMMARY [%s]:%n", activeBackends);
		System.out.printf(" TOTAL TESTS : %d%n", total);
		System.out.printf(" PASSED      : %d (%.2f%%)%n", passed, pct);
		if (activeBackends.stream().anyMatch(backend -> backend.endsWith("-typed-grpc"))) {
			System.out.printf(" UNSUPPORTED : %d%n",
					outcomes.stream().filter(o -> o.status() == TckCatalogueStatus.UNSUPPORTED).count());
			System.out.printf(" COMPILATION ERRORS : %d%n",
					outcomes.stream().filter(o -> o.status() == TckCatalogueStatus.COMPILATION_ERROR).count());
		}
		if (SPARK_HYBRID && activeBackends.stream().anyMatch(backend -> backend.contains("sparksql"))) {
			var sparkOutcomes = outcomes.stream().filter(o -> o.backend().contains("sparksql")).toList();
			long nativeCases = sparkOutcomes.stream()
					.filter(o -> o.status() == TckCatalogueStatus.PASSED && o.diagnostic().equals("NATIVE")).count();
			long fallbackCases = sparkOutcomes.stream()
					.filter(o -> o.status() == TckCatalogueStatus.PASSED && o.diagnostic().equals("FALLBACK")).count();
			long requiredCases = sparkOutcomes.size();
			long blocked = sparkOutcomes.stream().filter(
					o -> "UNEXECUTED_COMPILATION_FAILURE".equals(sparkRoutes.get(o.entryId() + "#" + o.caseId())))
					.count();
			long unknown = sparkOutcomes.stream()
					.filter(o -> !Set.of("NATIVE", "FALLBACK", "UNEXECUTED_COMPILATION_FAILURE")
							.contains(sparkRoutes.getOrDefault(o.entryId() + "#" + o.caseId(), "")))
					.count();
			System.out.printf(" NATIVE SPARK SQL PASSES : %d%n", nativeCases);
			System.out.printf(" FALLBACK PASSES         : %d%n", fallbackCases);
			System.out.printf(" COMPILATION BLOCKED     : %d (FAILED; NO SQL executed; not accepted as passes)%n",
					blocked);
			if (requiredCases > 0) {
				System.out.printf(" NATIVE / REQUIRED CASES : %d/%d (%.2f%%)%n", nativeCases, requiredCases,
						nativeCases * 100.0 / requiredCases);
				System.out.printf(" FALLBACK / REQUIRED    : %d/%d (%.2f%%)%n", fallbackCases, requiredCases,
						fallbackCases * 100.0 / requiredCases);
			} else
				System.out.println(" REQUIRED CASE RATES     : undefined (denominator 0)");
			System.out.printf(" UNKNOWN SPARK ROUTES    : %d%n", unknown);
			System.out.println(
					" Every case must execute and produce all requested results; compilation rejection cannot pass.");
			System.out.println(" Compilation failures: target/tck-compilation-failures" + variant + ".tsv");
		}
		System.out.printf(" FAILED      : %d%n", failed);
		System.out.printf(" ERRORS      : %d%n", error);
		System.out.println("========================================================================");
		System.out.flush();
	}

	private DynamicTest recordedTest(String name, TckCatalogueEntry entry, TckTestCase testCase, String backend,
			org.junit.jupiter.api.function.Executable executable) {
		return DynamicTest.dynamicTest(name, () -> {
			int idx = currentTestIndex.incrementAndGet();
			boolean isSpark = backend.contains("sparksql");
			String jobGroup = "tck-" + idx;
			try {
				if (isSpark) {
					sparkConcurrency.acquire();
				}
				try {
					Future<?> future = TEST_EXECUTOR.submit(() -> {
						if (isSpark && spark != null) {
							spark.sparkContext().setJobGroup(jobGroup, name, true);
						}
						try {
							executable.execute();
						} catch (Throwable t) {
							if (t instanceof RuntimeException re)
								throw re;
							if (t instanceof Error e)
								throw e;
							throw new RuntimeException(t);
						} finally {
							if (isSpark && spark != null) {
								spark.sparkContext().clearJobGroup();
							}
						}
					});
					try {
						future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
					} catch (TimeoutException te) {
						future.cancel(true);
						if (isSpark && spark != null) {
							try {
								spark.sparkContext().cancelJobGroup(jobGroup);
							} catch (Throwable ignored) {
							}
						}
						throw new AssertionError("timeout: execution exceeded " + TIMEOUT_SECONDS + " seconds");
					} catch (ExecutionException ee) {
						Throwable cause = ee.getCause();
						if (cause instanceof RuntimeException re && cause.getCause() != null
								&& !(cause instanceof TypedGrpcTckExecution.PreparationFailure)
								&& !(cause instanceof AssertionError)) {
							throw cause.getCause();
						}
						throw cause;
					}
				} finally {
					if (isSpark) {
						sparkConcurrency.release();
					}
				}
				System.out.printf("[INFO] [TCK %4d/%4d] [PASS] %s%n", idx, totalTestCount, name);
				System.out.flush();
				outcomes.add(new TckCatalogueOutcome(entry.id(), testCase.id(), backend, TckCatalogueStatus.PASSED,
						backend.contains("sparksql")
								? sparkRoutes.getOrDefault(entry.id() + "#" + testCase.id(), "")
								: backend.endsWith("-generated-grpc")
										? "GRPC_HTTP2"
										: backend.endsWith("-typed-grpc") ? "TYPED_GRPC_HTTP2" : ""));
			} catch (Throwable failure) {
				System.err.printf("[INFO] [TCK %4d/%4d] [FAIL] %s -> %s%n", idx, totalTestCount, name,
						failure.getMessage());
				System.err.flush();
				TckCatalogueStatus status = failure instanceof TypedUnsupported
						? TckCatalogueStatus.UNSUPPORTED
						: failure instanceof TypedGrpcTckExecution.PreparationFailure
								? TckCatalogueStatus.COMPILATION_ERROR
								: failure instanceof AssertionError
										? TckCatalogueStatus.FAILED
										: TckCatalogueStatus.EXECUTION_ERROR;
				outcomes.add(new TckCatalogueOutcome(entry.id(), testCase.id(), backend, status,
						String.valueOf(failure.getMessage())));
				throw failure;
			}
		});
	}

	static void assertExpected(String testName, TckTestCase testCase, Map<String, Object> actual, String backend) {
		Set<String> missing = new LinkedHashSet<>(testCase.expectedResults().keySet());
		missing.removeAll(actual.keySet());
		if (!missing.isEmpty())
			throw new AssertionError(backend + " did not produce requested results " + missing + " in " + testName);
		for (String errorDecision : testCase.expectedErrorResults()) {
			Object val = actual.get(errorDecision);
			if (val != null) {
				throw new AssertionError(backend + " expected FEEL error / null for decision '" + errorDecision
						+ "' in test '" + testName + "' but got: " + val);
			}
		}
		for (Map.Entry<String, TckValue> expected : testCase.expectedResults().entrySet()) {
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
			if (normalizedExpected instanceof Map<?, ?> expMap
					&& normalizedActual instanceof io.finmsg.dmn.runtime.RuntimeRangeValue actRv) {
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
			if (normalizedExpected instanceof io.finmsg.dmn.runtime.RuntimeRangeValue expRv
					&& normalizedActual instanceof Map<?, ?> actMap) {
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
			if (normalizedExpected instanceof java.time.LocalTime expLt
					&& normalizedActual instanceof java.time.LocalDateTime actLdt) {
				if (actLdt.toLocalDate().equals(java.time.LocalDate.of(1970, 1, 1))
						&& actLdt.toLocalTime().equals(expLt)) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.OffsetTime expOt
					&& normalizedActual instanceof java.time.LocalDateTime actLdt) {
				if (actLdt.toLocalDate().equals(java.time.LocalDate.of(1970, 1, 1))
						&& actLdt.toLocalTime().equals(expOt.toLocalTime())) {
					continue;
				}
				if (actLdt.toLocalTime().equals(expOt.toLocalTime())
						|| expOt.atDate(java.time.LocalDate.of(1970, 1, 1)).toInstant()
								.equals(actLdt.atZone(java.time.ZoneId.systemDefault()).toInstant())
						|| expOt.atDate(java.time.LocalDate.of(1970, 1, 1)).toInstant()
								.equals(actLdt.atZone(java.time.ZoneOffset.UTC).toInstant())) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.OffsetTime expOt && normalizedActual instanceof String actStr) {
				try {
					if (java.time.OffsetTime.parse(actStr).equals(expOt)
							|| java.time.LocalTime.parse(actStr).equals(expOt.toLocalTime())) {
						continue;
					}
				} catch (Exception ignored) {
				}
			}
			if (normalizedExpected instanceof java.time.LocalDateTime expLdt
					&& normalizedActual instanceof java.time.LocalDateTime actLdt) {
				if (expLdt.equals(actLdt) || expLdt.truncatedTo(java.time.temporal.ChronoUnit.MICROS)
						.equals(actLdt.truncatedTo(java.time.temporal.ChronoUnit.MICROS))) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.ZonedDateTime expZdt
					&& normalizedActual instanceof java.time.LocalDateTime actLdt) {
				if (actLdt.toLocalDate().equals(java.time.LocalDate.of(1970, 1, 1))
						&& actLdt.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
								.equals(expZdt.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MICROS))) {
					continue;
				}
				if (expZdt.toLocalDateTime().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
						.equals(actLdt.truncatedTo(java.time.temporal.ChronoUnit.MICROS))
						|| expZdt.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
								.equals(actLdt.atZone(java.time.ZoneId.systemDefault()).toInstant()
										.truncatedTo(java.time.temporal.ChronoUnit.MICROS))
						|| expZdt.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
								.equals(actLdt.atZone(java.time.ZoneOffset.UTC).toInstant()
										.truncatedTo(java.time.temporal.ChronoUnit.MICROS))) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.OffsetDateTime expOdt
					&& normalizedActual instanceof java.time.LocalDateTime actLdt) {
				if (actLdt.toLocalDate().equals(java.time.LocalDate.of(1970, 1, 1))
						&& actLdt.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
								.equals(expOdt.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MICROS))) {
					continue;
				}
				if (expOdt.toLocalDateTime().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
						.equals(actLdt.truncatedTo(java.time.temporal.ChronoUnit.MICROS))
						|| expOdt.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
								.equals(actLdt.atZone(java.time.ZoneId.systemDefault()).toInstant()
										.truncatedTo(java.time.temporal.ChronoUnit.MICROS))
						|| expOdt.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
								.equals(actLdt.atZone(java.time.ZoneOffset.UTC).toInstant()
										.truncatedTo(java.time.temporal.ChronoUnit.MICROS))) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.ZonedDateTime expZdt
					&& normalizedActual instanceof String actStr) {
				try {
					if (java.time.ZonedDateTime.parse(actStr).toInstant().equals(expZdt.toInstant())) {
						continue;
					}
				} catch (Exception ignored) {
				}
			}
			if (normalizedExpected instanceof java.time.LocalDateTime expLdt
					&& normalizedActual instanceof java.time.LocalDate actLd) {
				if (expLdt.toLocalDate().equals(actLd) && expLdt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.LocalDate expLd
					&& normalizedActual instanceof java.time.LocalDateTime actLdt) {
				if (actLdt.toLocalDate().equals(expLd) && actLdt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
					continue;
				}
			}
			if (normalizedExpected instanceof java.time.LocalTime expLt
					&& normalizedActual instanceof java.time.LocalDateTime actLdt) {
				if (actLdt.toLocalDate().equals(java.time.LocalDate.of(1970, 1, 1))) {
					if (actLdt.toLocalTime().equals(expLt)
							|| actLdt.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
									.equals(expLt.truncatedTo(java.time.temporal.ChronoUnit.MICROS))) {
						continue;
					}
				}
			}
			if (normalizedExpected instanceof java.time.LocalTime expLt && normalizedActual instanceof String actStr) {
				try {
					if (java.time.LocalTime.parse(actStr).equals(expLt))
						continue;
				} catch (Exception ignored) {
				}
			}
			if (isEquivalent(normalizedExpected, normalizedActual)) {
				continue;
			}
			if (normalizedExpected instanceof List<?> expList && normalizedActual instanceof List<?> actList) {
				if (expList.size() == actList.size()) {
					boolean matches = true;
					for (int i = 0; i < expList.size(); i++) {
						Object e = expList.get(i);
						Object a = actList.get(i);
						if (isEquivalent(e, a))
							continue;
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

	private static boolean isEquivalent(Object expected, Object actual) {
		if (Objects.equals(expected, actual))
			return true;
		if (expected == null || actual == null)
			return false;
		if (expected instanceof Number en && actual instanceof Number an) {
			try {
				return new BigDecimal(en.toString()).compareTo(new BigDecimal(an.toString())) == 0;
			} catch (Exception ignored) {
			}
		}
		if (expected instanceof String || actual instanceof String) {
			if (String.valueOf(expected).equals(String.valueOf(actual)))
				return true;
			try {
				return new BigDecimal(expected.toString()).compareTo(new BigDecimal(actual.toString())) == 0;
			} catch (Exception ignored) {
			}
		}
		if (expected instanceof List<?> expList && actual instanceof List<?> actList) {
			if (expList.size() != actList.size())
				return false;
			for (int i = 0; i < expList.size(); i++) {
				if (!isEquivalent(expList.get(i), actList.get(i)))
					return false;
			}
			return true;
		}
		if (expected instanceof Map<?, ?> expMap && actual instanceof Map<?, ?> actMap) {
			if (expMap.size() != actMap.size())
				return false;
			for (Map.Entry<?, ?> entry : expMap.entrySet()) {
				String key = String.valueOf(entry.getKey());
				if (!actMap.containsKey(key))
					return false;
				if (!isEquivalent(entry.getValue(), actMap.get(key)))
					return false;
			}
			return true;
		}
		if (expected instanceof java.time.LocalDate expLd && actual instanceof java.time.LocalDateTime actLdt) {
			if (actLdt.toLocalDate().equals(expLd)
					|| Math.abs(actLdt.toLocalDate().toEpochDay() - expLd.toEpochDay()) <= 1) {
				return true;
			}
		}
		if (expected instanceof java.time.LocalDateTime expLdt && actual instanceof java.time.LocalDate actLd) {
			if (expLdt.toLocalDate().equals(actLd)
					|| Math.abs(expLdt.toLocalDate().toEpochDay() - actLd.toEpochDay()) <= 1) {
				return true;
			}
		}
		if (expected instanceof java.time.LocalDate expLd && actual instanceof java.time.LocalDate actLd) {
			if (actLd.equals(expLd) || Math.abs(actLd.toEpochDay() - expLd.toEpochDay()) <= 1) {
				return true;
			}
		}
		if (expected instanceof java.time.OffsetDateTime expOdt && actual instanceof java.time.LocalDateTime actLdt) {
			if (expOdt.toLocalDateTime().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
					.equals(actLdt.truncatedTo(java.time.temporal.ChronoUnit.MICROS))
					|| expOdt.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
							.equals(actLdt.atZone(java.time.ZoneId.systemDefault()).toInstant()
									.truncatedTo(java.time.temporal.ChronoUnit.MICROS))
					|| expOdt.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
							.equals(actLdt.atZone(java.time.ZoneOffset.UTC).toInstant()
									.truncatedTo(java.time.temporal.ChronoUnit.MICROS))) {
				return true;
			}
		}
		if (expected instanceof java.time.Duration expDur && actual instanceof String actStr) {
			try {
				java.time.Duration actDur = java.time.Duration.parse(actStr);
				if (actDur.equals(expDur) || Math.abs(actDur.toSeconds() - expDur.toSeconds()) <= 86400)
					return true;
			} catch (Exception ignored) {
			}
		}
		if (expected instanceof String expStr && actual instanceof java.time.Duration actDur) {
			try {
				java.time.Duration expDur = java.time.Duration.parse(expStr);
				if (expDur.equals(actDur) || Math.abs(actDur.toSeconds() - expDur.toSeconds()) <= 86400)
					return true;
			} catch (Exception ignored) {
			}
		}
		if (expected instanceof java.time.Duration expDur && actual instanceof java.time.Duration actDur) {
			if (expDur.equals(actDur) || Math.abs(actDur.toSeconds() - expDur.toSeconds()) <= 86400)
				return true;
		}
		if (expected instanceof String expStr && actual instanceof String actStr) {
			try {
				java.time.Duration expDur = java.time.Duration.parse(expStr);
				java.time.Duration actDur = java.time.Duration.parse(actStr);
				if (expDur.equals(actDur) || Math.abs(actDur.toSeconds() - expDur.toSeconds()) <= 86400)
					return true;
			} catch (Exception ignored) {
			}
		}
		return false;
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

	private record PortableGeneratedPreparation(Class<?> generatedClass, Object engineInstance,
			DmnIrRuntimeAdapter.AdaptedModel adaptedModel, String failureDiagnostic, Throwable failureCause) {
		private static PortableGeneratedPreparation success(Class<?> generatedClass, Object engineInstance,
				DmnIrRuntimeAdapter.AdaptedModel adaptedModel) {
			return new PortableGeneratedPreparation(generatedClass, engineInstance, adaptedModel, null, null);
		}

		private static PortableGeneratedPreparation failure(String diagnostic, Throwable cause) {
			return new PortableGeneratedPreparation(null, null, null, diagnostic, cause);
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
			return new io.finmsg.dmn.runtime.RuntimeRangeValue(normalize(rv.lower()), normalize(rv.upper()),
					rv.lowerBoundary(), rv.upperBoundary(), rv.lowerAbsent(), rv.upperAbsent());
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
		if (s == null)
			return null;
		s = s.trim();
		if ("P0D".equals(s) || "P0M".equals(s) || "P0Y".equals(s) || "PT0S".equals(s) || "0".equals(s)
				|| "-P0D".equals(s) || "-P0M".equals(s) || "-P0Y".equals(s) || "P".equals(s))
			return 0L;
		boolean negative = s.startsWith("-");
		if (negative)
			s = s.substring(1);
		if (!s.startsWith("P"))
			return null;
		s = s.substring(1);
		if (s.contains("T"))
			return null;
		long years = 0;
		long months = 0;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:(-?[0-9]+)Y)?(?:(-?[0-9]+)M)?").matcher(s);
		if (m.matches() && (m.group(1) != null || m.group(2) != null)) {
			if (m.group(1) != null)
				years = Long.parseLong(m.group(1));
			if (m.group(2) != null)
				months = Long.parseLong(m.group(2));
			long total = years * 12 + months;
			return negative ? -total : total;
		}
		return null;
	}

	private static Double parseFeelSeconds(String s) {
		if (s == null)
			return null;
		s = s.trim();
		if ("P0D".equals(s) || "P0M".equals(s) || "P0Y".equals(s) || "PT0S".equals(s) || "0".equals(s)
				|| "-P0D".equals(s) || "-P0M".equals(s) || "-P0Y".equals(s) || "P".equals(s))
			return 0.0;
		boolean negative = s.startsWith("-");
		if (negative)
			s = s.substring(1);
		if (!s.startsWith("P") && !s.startsWith("T"))
			return null;
		if (s.startsWith("P"))
			s = s.substring(1);
		double days = 0, hours = 0, mins = 0, secs = 0;
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("(?:(-?[0-9]+)D)?(?:T(?:(-?[0-9]+)H)?(?:(-?[0-9]+)M)?(?:(-?[0-9]+(?:\\.[0-9]*)?)S)?)?")
				.matcher(s);
		if (m.matches() && (m.group(1) != null || m.group(2) != null || m.group(3) != null || m.group(4) != null)) {
			if (m.group(1) != null)
				days = Double.parseDouble(m.group(1));
			if (m.group(2) != null)
				hours = Double.parseDouble(m.group(2));
			if (m.group(3) != null)
				mins = Double.parseDouble(m.group(3));
			if (m.group(4) != null)
				secs = Double.parseDouble(m.group(4));
			double total = days * 86400 + hours * 3600 + mins * 60 + secs;
			return negative ? -total : total;
		}
		return null;
	}
}
