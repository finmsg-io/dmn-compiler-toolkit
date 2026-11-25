package io.finmsg.dmn.tck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnCompilerOptions;
import io.finmsg.dmn.compiler.DmnModelResolver;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.compiler.InMemoryDmnModelResolver;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarDmnExporter;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.frontend.xml.dmn.DmnNamespaces;
import io.finmsg.dmn.frontend.xml.dmn.DmnVersion;
import io.finmsg.dmn.runtime.DmnRuntime;
import io.finmsg.dmn.tck.dmnir.TckDmnIrArchive;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * End-to-end DMNIR -> DMN XML regeneration and semantic round-trip verification
 * against all 151 deduplicated {@code .dmnir} artifacts and all 3,407 official
 * OMG DMN TCK cases ({@code 5cd912f2...}).
 */
class TckDmnIrDmnXmlRoundTripTest {
	private static final List<String> CATALOGUE_ROOTS = List.of("compliance-level-2", "compliance-level-3");

	@Test
	void roundTripAllOfficialTckCasesViaDmn16Xml() throws Exception {
		runRoundTrip(DmnVersion.DMN_1_6);
	}

	@Test
	void roundTripAllOfficialTckCasesViaDmn15Xml() throws Exception {
		runRoundTrip(DmnVersion.DMN_1_5);
	}

	@Test
	void roundTripAllOfficialTckCasesViaDmn13Xml() throws Exception {
		runRoundTrip(DmnVersion.DMN_1_3);
	}

	@Test
	void roundTripRealWorldDmnModelsAcrossSupportedVersions() throws Exception {
		Path modelsRoot = Path.of("..", "dmn-models", "src", "main", "resources", "models").toAbsolutePath()
				.normalize();
		if (!Files.isDirectory(modelsRoot)) {
			modelsRoot = Path.of("dmn-models", "src", "main", "resources", "models").toAbsolutePath().normalize();
		}
		assertTrue(Files.isDirectory(modelsRoot), "Missing dmn-models directory at " + modelsRoot);

		record ModelFixture(String dir, String rootFile, List<String> importFiles, String expectedDecision) {
		}
		List<ModelFixture> fixtures = List.of(
				new ModelFixture("discount-calculation", "pricing-root.dmn", List.of("tier-rules.dmn"),
						"FinalDiscountRate"),
				new ModelFixture("loan-approval", "main-loan.dmn", List.of("applicant-risk.dmn", "credit-score.dmn"),
						"LoanApprovalStatus"),
				new ModelFixture("order-fulfillment", "fulfillment-root.dmn",
						List.of("inventory-check.dmn", "shipping-calculator.dmn"), "CanFulfillOrder"));

		DmnCompiler compiler = new DmnCompiler();
		DmnIrScalarProducer producer = new DmnIrScalarProducer();
		DmnIrArtifactReader reader = new DmnIrArtifactReader();
		DmnIrScalarDmnExporter exporter = new DmnIrScalarDmnExporter();
		DmnRuntime runtime = new DmnRuntime();

		for (ModelFixture fixture : fixtures) {
			Path dir = modelsRoot.resolve(fixture.dir());
			DmnSource rootSrc = new DmnSource(DmnSourceId.of("memory:/" + fixture.rootFile()),
					Files.readAllBytes(dir.resolve(fixture.rootFile())));
			List<DmnSource> importSrcs = new ArrayList<>();
			for (String imp : fixture.importFiles()) {
				importSrcs.add(new DmnSource(DmnSourceId.of("memory:/" + imp), Files.readAllBytes(dir.resolve(imp))));
			}
			DmnModelResolver origResolver = new InMemoryDmnModelResolver(importSrcs);
			DmnCompilationResult origCompiled = compiler.compile(rootSrc, origResolver, DmnCompilerOptions.optimized());
			assertTrue(origCompiled.isSuccess(),
					"Original model must compile: " + fixture.dir() + " -> " + origCompiled.diagnostics());
			var origModel = origCompiled.compiledModel().orElseThrow();
			Object expectedValue = runtime.evaluate(origModel.optimizedModel().model(), Map.of())
					.value(origModel.decisionSlots().get(fixture.expectedDecision()));

			byte[] dmnirBytes = producer.produce(rootSrc, origResolver).artifact().toByteArray();
			var verified = reader.read(dmnirBytes);

			for (DmnVersion version : List.of(DmnVersion.DMN_1_3, DmnVersion.DMN_1_5, DmnVersion.DMN_1_6)) {
				var bundle = exporter.export(verified, version);
				assertEquals(bundle.documents(), exporter.export(verified, version).documents(),
						"Export must be deterministic for " + fixture.dir() + " (" + version + ")");
				String expectedNs = switch (version) {
					case DMN_1_3 -> DmnNamespaces.DMN_1_3;
					case DMN_1_5 -> DmnNamespaces.DMN_1_5;
					case DMN_1_6 -> DmnNamespaces.DMN_1_6;
					default -> throw new AssertionError();
				};
				for (String xml : bundle.documents().values()) {
					assertTrue(xml.contains(expectedNs),
							"Exported XML for " + fixture.dir() + " must declare namespace " + expectedNs);
				}

				List<DmnSource> regenImports = new ArrayList<>();
				DmnSource regenRoot = null;
				for (var doc : bundle.documents().entrySet()) {
					DmnSource src = new DmnSource(DmnSourceId.of("memory:/" + doc.getKey()),
							doc.getValue().getBytes(StandardCharsets.UTF_8));
					if (doc.getKey().equals(bundle.rootDocument())) {
						regenRoot = src;
					} else {
						regenImports.add(src);
					}
				}
				DmnCompilationResult regenCompiled = compiler.compile(regenRoot,
						new InMemoryDmnModelResolver(regenImports), DmnCompilerOptions.optimized());
				assertTrue(regenCompiled.isSuccess(), "Regenerated " + version + " model must compile: " + fixture.dir()
						+ " -> " + regenCompiled.diagnostics());
				var regenModel = regenCompiled.compiledModel().orElseThrow();
				Object actualValue = runtime.evaluate(regenModel.optimizedModel().model(), Map.of())
						.value(regenModel.decisionSlots().get(fixture.expectedDecision()));
				assertEquals(expectedValue, actualValue,
						"Semantic parity mismatch for " + fixture.dir() + " (" + version + ")");
			}
		}
	}

	private void runRoundTrip(DmnVersion version) throws Exception {
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
		long startNanos = System.nanoTime();

		TckDmnIrArchive archive = TckDmnIrArchive.loadFromClasspath()
				.orElseThrow(() -> new IllegalStateException("Missing tck-dmnir/manifest.tsv on classpath"));

		URL officialResource = TckDmnIrDmnXmlRoundTripTest.class.getClassLoader().getResource("tck-official/TestCases");
		if (officialResource == null) {
			throw new IllegalStateException("Official OMG DMN TCK test suite is missing on classpath.");
		}
		Path testCasesDir = Path.of(officialResource.toURI());
		TckCatalogueInventory inventory = new TckCatalogueDiscovery().discover(testCasesDir, CATALOGUE_ROOTS);
		assertEquals(3407, inventory.decodedCaseCount(), "Pinned TCK denominator must be 3,407");

		// 1. Collect unique .dmnir artifact paths
		Set<String> uniqueArtifacts = new TreeSet<>();
		for (TckDmnIrArchive.CaseEntry entry : archive.entries().values()) {
			assertTrue(entry.exported(), "Unexported archive case: " + entry.caseKey());
			uniqueArtifacts.add(entry.artifactPath());
		}

		// 2. Export each .dmnir -> DMN XML and recompile via DmnCompiler (zero access
		// to original XML)
		record CompiledArtifact(DmnCompilationResult compilation, String exportError, String compileError) {
		}
		Map<String, CompiledArtifact> compiledByArtifact = new ConcurrentHashMap<>();
		AtomicInteger exportedOk = new AtomicInteger();
		AtomicInteger exportFailed = new AtomicInteger();
		AtomicInteger compiledOk = new AtomicInteger();
		AtomicInteger compileFailed = new AtomicInteger();
		List<String> exportErrorSamples = Collections.synchronizedList(new ArrayList<>());
		List<String> compileErrorSamples = Collections.synchronizedList(new ArrayList<>());

		DmnIrArtifactReader reader = new DmnIrArtifactReader();
		DmnIrScalarDmnExporter exporter = new DmnIrScalarDmnExporter();
		DmnCompiler compiler = new DmnCompiler();

		uniqueArtifacts.parallelStream().forEach(artifactPath -> {
			try {
				byte[] bytes = archive.readArtifactBytes(artifactPath);
				var verified = reader.read(bytes);
				var bundle = exporter.export(verified, version);
				exportedOk.incrementAndGet();

				List<DmnSource> sources = new ArrayList<>();
				DmnSource root = null;
				for (var doc : bundle.documents().entrySet()) {
					DmnSource src = new DmnSource(DmnSourceId.of("memory:/" + doc.getKey()),
							doc.getValue().getBytes(StandardCharsets.UTF_8));
					if (doc.getKey().equals(bundle.rootDocument())) {
						root = src;
					} else {
						sources.add(src);
					}
				}
				DmnModelResolver resolver = new InMemoryDmnModelResolver(sources);
				DmnCompilationResult result = compiler.compile(root, resolver, DmnCompilerOptions.optimized());
				if (result.isSuccess()) {
					compiledOk.incrementAndGet();
					compiledByArtifact.put(artifactPath, new CompiledArtifact(result, null, null));
				} else {
					compileFailed.incrementAndGet();
					String diag = result.diagnostics().toString();
					compileErrorSamples.add(artifactPath + " => " + diag);
					compiledByArtifact.put(artifactPath, new CompiledArtifact(null, null, diag));
				}
			} catch (Throwable t) {
				exportFailed.incrementAndGet();
				String where = t.getStackTrace().length > 0 ? " @ " + t.getStackTrace()[0] : "";
				String where2 = t.getStackTrace().length > 1 ? " <- " + t.getStackTrace()[1] : "";
				String msg = t.getClass().getSimpleName() + ": " + t.getMessage() + where + where2;
				exportErrorSamples.add(artifactPath + " => " + msg);
				compiledByArtifact.put(artifactPath, new CompiledArtifact(null, msg, null));
			}
		});

		// 3. Execute all 3,407 TCK cases against the regenerated & recompiled models
		DmnToolkitTckEngine engine = new DmnToolkitTckEngine(OfficialExternalJavaPolicy.POLICY);
		AtomicInteger passedCases = new AtomicInteger();
		AtomicInteger failedCases = new AtomicInteger();
		List<String> executionErrorSamples = Collections.synchronizedList(new ArrayList<>());

		record CaseWork(String caseKey, TckTestCase testCase, String artifactPath) {
		}
		List<CaseWork> workItems = new ArrayList<>(3407);
		for (TckCatalogueEntry entry : inventory.entries()) {
			if (!entry.executable()) {
				continue;
			}
			for (TckTestCase tc : entry.testCases()) {
				String caseKey = entry.id() + "#" + tc.id();
				TckDmnIrArchive.CaseEntry archiveEntry = archive.entry(caseKey)
						.orElseThrow(() -> new IllegalStateException("Missing archive entry: " + caseKey));
				workItems.add(new CaseWork(caseKey, tc, archiveEntry.artifactPath()));
			}
		}

		workItems.parallelStream().forEach(item -> {
			CompiledArtifact ca = compiledByArtifact.get(item.artifactPath());
			if (ca == null || ca.compilation() == null) {
				failedCases.incrementAndGet();
				return;
			}
			try {
				TckExecutionResult result = engine.execute(ca.compilation(), item.testCase());
				OfficialTckSuiteTest.assertExpected(item.caseKey(), item.testCase(), result.decisionValues(),
						"dmn-xml-" + version.name().toLowerCase(Locale.ROOT));
				passedCases.incrementAndGet();
			} catch (Throwable t) {
				failedCases.incrementAndGet();
				String msg = t.getMessage() != null ? t.getMessage().replace('\n', ' ') : t.toString();
				executionErrorSamples.add(item.caseKey() + " => " + msg);
			}
		});

		double elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000.0;
		System.out.printf("========================================================================%n"
				+ " DMNIR -> %s XML ROUND-TRIP REPORT%n"
				+ " ARTIFACTS       : %d total | exported=%d | exportFailed=%d | compiled=%d | compileFailed=%d%n"
				+ " TCK CASES       : %d total | passed=%d (%.2f%%) | failed=%d%n" + " ELAPSED         : %.2f s%n"
				+ "========================================================================%n", version,
				uniqueArtifacts.size(), exportedOk.get(), exportFailed.get(), compiledOk.get(), compileFailed.get(),
				workItems.size(), passedCases.get(), (100.0 * passedCases.get()) / workItems.size(), failedCases.get(),
				elapsedSec);

		Collections.sort(exportErrorSamples);
		Collections.sort(compileErrorSamples);
		Collections.sort(executionErrorSamples);
		if (!exportErrorSamples.isEmpty()) {
			System.out.println("--- EXPORT FAILURES (" + exportErrorSamples.size() + ") ---");
			exportErrorSamples.stream().limit(40).forEach(System.out::println);
		}
		if (!compileErrorSamples.isEmpty()) {
			System.out.println("--- COMPILE FAILURES (" + compileErrorSamples.size() + ") ---");
			compileErrorSamples.stream().limit(40).forEach(System.out::println);
		}
		if (!executionErrorSamples.isEmpty()) {
			System.out.println("--- EXECUTION FAILURES (" + executionErrorSamples.size() + ") ---");
			executionErrorSamples.stream().limit(40).forEach(System.out::println);
		}

		assertEquals(uniqueArtifacts.size(), exportedOk.get(), "All .dmnir artifacts must export to " + version);
		assertEquals(uniqueArtifacts.size(), compiledOk.get(),
				"All regenerated " + version + " DMN XML bundles must recompile");
		assertEquals(workItems.size(), passedCases.get(),
				"All 3,407 TCK cases must pass on regenerated " + version + " DMN XML");
	}
}
