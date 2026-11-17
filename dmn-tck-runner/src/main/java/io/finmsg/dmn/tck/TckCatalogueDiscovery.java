package io.finmsg.dmn.tck;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Cross-platform, deterministic discovery for an unpacked TCK catalogue. */
public final class TckCatalogueDiscovery {
	private static final java.util.regex.Pattern TEST_FILE = java.util.regex.Pattern.compile("(.+)-test-[0-9]+\\.xml");

	public TckCatalogueInventory discover(Path catalogueRoot) throws IOException {
		return discover(catalogueRoot, List.of(""));
	}

	public TckCatalogueInventory discover(Path catalogueRoot, List<String> includedRoots) throws IOException {
		Path root = catalogueRoot.toAbsolutePath().normalize();
		if (!Files.isDirectory(root)) {
			throw new IOException("TCK catalogue root is not a directory: " + root);
		}
		List<Path> scopes = includedRoots.stream().map(root::resolve).map(path -> path.toAbsolutePath().normalize())
				.toList();
		for (Path scope : scopes)
			if (!Files.isDirectory(scope))
				throw new IOException("Included TCK catalogue root is not a directory: " + scope);
		List<Path> allPaths;
		try (Stream<Path> paths = Files.walk(root)) {
			allPaths = paths.filter(path -> scopes.stream().anyMatch(path::startsWith))
					.sorted(Comparator.comparing(path -> portable(root, path))).toList();
		}
		int directories = (int) allPaths.stream().filter(Files::isDirectory).count();
		int dmnFiles = (int) allPaths.stream().filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".dmn")).count();
		List<Path> testXmlFiles = allPaths.stream().filter(Files::isRegularFile)
				.filter(path -> TEST_FILE.matcher(path.getFileName().toString()).matches()).toList();
		List<TckCatalogueEntry> entries = new ArrayList<>(testXmlFiles.size());
		for (Path testXml : testXmlFiles) {
			entries.add(discoverEntry(root, testXml));
		}
		int cases = entries.stream().mapToInt(entry -> entry.testCases().size()).sum();
		return new TckCatalogueInventory(directories, testXmlFiles.size(), dmnFiles, cases, entries);
	}

	private static TckCatalogueEntry discoverEntry(Path root, Path testXml) throws IOException {
		String id = portable(root, testXml);
		List<Path> models;
		try (Stream<Path> paths = Files.list(testXml.getParent())) {
			models = paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".dmn"))
					.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
		}
		String testName = testXml.getFileName().toString();
		var matcher = TEST_FILE.matcher(testName);
		if (!matcher.matches())
			throw new IOException("Invalid test filename: " + testName);
		String expectedName = matcher.group(1) + ".dmn";
		try {
			var declaredName = new TckTestCaseReader().readModelName(testXml);
			if (declaredName.isPresent())
				expectedName = declaredName.get();
		} catch (IOException exception) {
			return invalid(id, testXml, models, "Invalid TCK test XML: " + exception.getMessage());
		}
		final String rootName = expectedName;
		Optional<Path> exact = models.stream().filter(path -> path.getFileName().toString().equals(rootName))
				.findFirst();
		Optional<Path> rootModel = exact.isPresent()
				? exact
				: models.size() == 1 ? Optional.of(models.getFirst()) : Optional.empty();
		if (rootModel.isEmpty()) {
			String reason = models.isEmpty()
					? "No DMN model in test directory"
					: "No exact root model and multiple DMN candidates: "
							+ models.stream().map(path -> path.getFileName().toString()).toList();
			return invalid(id, testXml, models, reason);
		}
		try {
			List<TckTestCase> cases = new TckTestCaseReader().read(testXml);
			if (cases.isEmpty()) {
				return invalid(id, testXml, models, "Test XML contains no testCase elements");
			}
			return new TckCatalogueEntry(id, testXml, rootModel, models, cases, Optional.empty());
		} catch (Exception exception) {
			return invalid(id, testXml, models,
					"Invalid TCK test XML: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
		}
	}

	private static TckCatalogueEntry invalid(String id, Path xml, List<Path> models, String reason) {
		return new TckCatalogueEntry(id, xml, Optional.empty(), models, List.of(), Optional.of(reason));
	}

	private static String portable(Path root, Path path) {
		return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
	}
}
