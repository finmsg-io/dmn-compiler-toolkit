package io.finmsg.dmn.tck;

import io.finmsg.dmn.compiler.DmnModelResolver;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.compiler.InMemoryDmnModelResolver;
import io.finmsg.dmn.compiler.dmnir.DmnIrProductionException;
import io.finmsg.dmn.compiler.dmnir.DmnIrScalarProducer;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opt-in generator that pre-compiles the pinned OMG DMN TCK into serialized
 * portable {@code .dmnir} Protobuf files stored in the {@code dmn-tck} Maven
 * module ({@code dmn-tck/src/main/resources/tck-dmnir/}).
 *
 * <p>
 * Run only when the pinned TCK revision or DMNIR compiler/schema changes:
 * 
 * <pre>
 * mvn test -Ptck -pl dmn-ir-codec,dmn-compiler,dmn-tck,dmn-tck-runner \
 *     -Dtest=TckDmnIrExporterTest -DfailIfNoTests=false -Dtck.exportDmnIr=true
 * </pre>
 */
class TckDmnIrExporterTest {
	private static final String TCK_REVISION = "5cd912f281831bc535e710aba435e16de198028a";
	private static final String DMNIR_SCHEMA_VERSION = "0.11";
	private static final List<String> CATALOGUE_ROOTS = List.of("compliance-level-2", "compliance-level-3");

	@Test
	void exportOfficialTckToDmnIrArchive() throws Exception {
		Assumptions.assumeTrue(Boolean.getBoolean("tck.exportDmnIr"),
				"Skipping DMNIR archive regeneration (set -Dtck.exportDmnIr=true to regenerate).");

		long startNanos = System.nanoTime();
		URL officialResource = TckDmnIrExporterTest.class.getClassLoader().getResource("tck-official/TestCases");
		if (officialResource == null) {
			throw new IllegalStateException("Official OMG DMN TCK test suite is missing on classpath.");
		}
		Path testCasesDir = Path.of(officialResource.toURI());
		TckCatalogueInventory inventory = new TckCatalogueDiscovery().discover(testCasesDir, CATALOGUE_ROOTS);

		Path outRoot = Path.of("..", "dmn-tck", "src", "main", "resources", "tck-dmnir").normalize().toAbsolutePath();
		if (Files.exists(outRoot)) {
			try (var walk = Files.walk(outRoot)) {
				walk.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			}
		}
		Files.createDirectories(outRoot);

		Map<String, String[]> manifestRows = new ConcurrentHashMap<>();
		Set<String> writtenArtifacts = ConcurrentHashMap.newKeySet();
		AtomicInteger exportedCases = new AtomicInteger();
		AtomicInteger failedCases = new AtomicInteger();
		AtomicLong totalBytes = new AtomicLong();

		inventory.entries().parallelStream().forEach(entry -> {
			if (!entry.executable()) {
				return;
			}
			try {
				List<Path> orderedModels = new ArrayList<>();
				Path rootPath = entry.rootDmn().orElseThrow();
				orderedModels.add(rootPath);
				entry.dmnFiles().stream().filter(p -> !p.equals(rootPath)).forEach(orderedModels::add);
				List<DmnSource> sources = new ArrayList<>();
				for (Path model : orderedModels) {
					sources.add(new DmnSource(new DmnSourceId(model.toUri()), Files.readAllBytes(model)));
				}
				String entryDir = entry.id().substring(0, entry.id().lastIndexOf('/'));
				String modelBase = stripExtension(rootPath.getFileName().toString());

				ExportOutcome fullOutcome = tryExport(sources, Set.of());
				if (fullOutcome.bytes() != null) {
					String relPath = entryDir + "/" + modelBase + ".dmnir";
					writeArtifact(outRoot, relPath, fullOutcome.bytes(), writtenArtifacts, totalBytes);
					for (TckTestCase tc : entry.testCases()) {
						String caseKey = entry.id() + "#" + tc.id();
						manifestRows.put(caseKey, new String[]{caseKey, relPath, ""});
						exportedCases.incrementAndGet();
					}
					return;
				}

				Map<Set<String>, List<TckTestCase>> byResultSet = new LinkedHashMap<>();
				for (TckTestCase tc : entry.testCases()) {
					Set<String> resultNames = Set.copyOf(tc.expectedResults().keySet());
					byResultSet.computeIfAbsent(resultNames, k -> new ArrayList<>()).add(tc);
				}

				byResultSet.entrySet().parallelStream().forEach(group -> {
					Set<String> resultNames = group.getKey();
					ExportOutcome pruned = tryExport(sources, resultNames);
					if (pruned.bytes() != null) {
						String suffix = shortHash(String.join(",", resultNames.stream().sorted().toList()));
						String relPath = entryDir + "/" + modelBase + "__" + suffix + ".dmnir";
						try {
							writeArtifact(outRoot, relPath, pruned.bytes(), writtenArtifacts, totalBytes);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						for (TckTestCase tc : group.getValue()) {
							String caseKey = entry.id() + "#" + tc.id();
							manifestRows.put(caseKey, new String[]{caseKey, relPath, ""});
							exportedCases.incrementAndGet();
						}
					} else {
						String diag = sanitize(pruned.diagnostic());
						for (TckTestCase tc : group.getValue()) {
							String caseKey = entry.id() + "#" + tc.id();
							manifestRows.put(caseKey, new String[]{caseKey, "", diag});
							failedCases.incrementAndGet();
						}
					}
				});
			} catch (Exception e) {
				throw new RuntimeException("Failed exporting entry " + entry.id(), e);
			}
		});

		StringBuilder manifest = new StringBuilder();
		manifest.append("#tckRevision=").append(TCK_REVISION).append('\n');
		manifest.append("#dmnirSchemaVersion=").append(DMNIR_SCHEMA_VERSION).append('\n');
		manifest.append("caseKey\tartifactPath\texportDiagnostic\n");
		manifestRows.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
			String[] row = e.getValue();
			manifest.append(row[0]).append('\t').append(row[1]).append('\t').append(row[2]).append('\n');
		});
		Files.writeString(outRoot.resolve("manifest.tsv"), manifest.toString(), StandardCharsets.UTF_8);

		double elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000.0;
		System.out.printf(
				"========================================================================%n"
						+ " DMN TCK -> DMNIR ARCHIVE EXPORT COMPLETE%n" + " OUTPUT DIR      : %s%n"
						+ " TCK REVISION    : %s%n" + " TOTAL CASES     : %d (EXPORTED: %d, UNSUPPORTED: %d)%n"
						+ " UNIQUE .dmnir   : %d files (%.2f MB)%n" + " ELAPSED         : %.2f s%n"
						+ "========================================================================%n",
				outRoot, TCK_REVISION, manifestRows.size(), exportedCases.get(), failedCases.get(),
				writtenArtifacts.size(), totalBytes.get() / (1024.0 * 1024.0), elapsedSec);
	}

	private record ExportOutcome(byte[] bytes, String diagnostic) {
	}

	private static ExportOutcome tryExport(List<DmnSource> sources, Set<String> resultNames) {
		try {
			List<DmnSource> scoped = new ArrayList<>(sources);
			if (!resultNames.isEmpty()) {
				scoped.set(0, new TckDmnDecisionPruner().prune(sources.getFirst(), resultNames));
			}
			DmnSource root = scoped.getFirst();
			DmnModelResolver resolver = scoped.size() > 1
					? new InMemoryDmnModelResolver(scoped.subList(1, scoped.size()))
					: new InMemoryDmnModelResolver(List.of());
			DmnIrArtifactReader.VerifiedArtifact produced = new DmnIrScalarProducer().produce(root, resolver);
			byte[] protoBytes = produced.artifact().toByteArray();
			// Verify round-trip bounded verification before storing in dmn-tck archive.
			new DmnIrArtifactReader().read(protoBytes);
			return new ExportOutcome(protoBytes, "");
		} catch (Throwable exception) {
			String diagnostic = (exception instanceof DmnIrProductionException pe)
					? "EXPORT_" + pe.category() + ": " + pe.getMessage()
							+ (pe.diagnostics().isEmpty() ? "" : " " + pe.diagnostics())
					: exception.getMessage() != null ? exception.getMessage() : exception.toString();
			return new ExportOutcome(null, diagnostic);
		}
	}

	private static void writeArtifact(Path outRoot, String relPath, byte[] bytes, Set<String> written,
			AtomicLong totalBytes) throws IOException {
		if (written.add(relPath)) {
			Path target = outRoot.resolve(relPath);
			Files.createDirectories(target.getParent());
			Files.write(target, bytes);
			totalBytes.addAndGet(bytes.length);
		}
	}

	private static String stripExtension(String name) {
		int idx = name.lastIndexOf('.');
		return idx > 0 ? name.substring(0, idx) : name;
	}

	private static String shortHash(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 6; i++) {
				hex.append(String.format("%02x", digest[i]));
			}
			return hex.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static String sanitize(String s) {
		return s == null ? "" : s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
	}
}
