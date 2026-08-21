package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TckCatalogueDiscoveryTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void discoversFilesAndCasesWithPortableStableIdentifiers() throws Exception {
		Path caseDirectory = Files.createDirectories(temporaryDirectory.resolve("level/0001-case"));
		writeModel(caseDirectory.resolve("0001-case.dmn"));
		writeCases(caseDirectory.resolve("0001-case-test-01.xml"), "case-1");

		TckCatalogueInventory inventory = new TckCatalogueDiscovery().discover(temporaryDirectory);

		assertThat(inventory.directoryCount()).isEqualTo(3);
		assertThat(inventory.testXmlCount()).isEqualTo(1);
		assertThat(inventory.dmnFileCount()).isEqualTo(1);
		assertThat(inventory.decodedCaseCount()).isEqualTo(1);
		assertThat(inventory.entries().getFirst().id()).isEqualTo("level/0001-case/0001-case-test-01.xml");
		assertThat(inventory.entries().getFirst().rootDmn()).contains(caseDirectory.resolve("0001-case.dmn"));
	}

	@Test
	void classifiesMissingAndAmbiguousRootsWithoutDroppingEntries() throws Exception {
		Path missing = Files.createDirectories(temporaryDirectory.resolve("missing"));
		writeCases(missing.resolve("missing-test-01.xml"), "missing-case");
		Path ambiguous = Files.createDirectories(temporaryDirectory.resolve("ambiguous"));
		writeCases(ambiguous.resolve("unknown-test-01.xml"), "ambiguous-case");
		writeModel(ambiguous.resolve("a.dmn"));
		writeModel(ambiguous.resolve("b.dmn"));

		TckCatalogueInventory inventory = new TckCatalogueDiscovery().discover(temporaryDirectory);

		assertThat(inventory.entries()).hasSize(2).allMatch(entry -> !entry.executable());
		assertThat(inventory.entries()).extracting(entry -> entry.invalidReason().orElseThrow())
				.anyMatch(reason -> reason.contains("No DMN model"))
				.anyMatch(reason -> reason.contains("multiple DMN candidates"));
	}

	@Test
	void classifiesMalformedAndEmptyXmlWithoutDroppingEntries() throws Exception {
		Path malformed = Files.createDirectories(temporaryDirectory.resolve("malformed"));
		writeModel(malformed.resolve("malformed.dmn"));
		Files.writeString(malformed.resolve("malformed-test-01.xml"), "<testCases><broken>");
		Path empty = Files.createDirectories(temporaryDirectory.resolve("empty"));
		writeModel(empty.resolve("empty.dmn"));
		Files.writeString(empty.resolve("empty-test-01.xml"), "<testCases/>");

		TckCatalogueInventory inventory = new TckCatalogueDiscovery().discover(temporaryDirectory);

		assertThat(inventory.entries()).hasSize(2).allMatch(entry -> !entry.executable());
		assertThat(inventory.entries()).extracting(entry -> entry.invalidReason().orElseThrow())
				.anyMatch(reason -> reason.contains("Invalid TCK test XML"))
				.anyMatch(reason -> reason.contains("no testCase"));
	}

	@Test
	void exactSelectorFailsWhenItMatchesNothing() throws Exception {
		Path caseDirectory = Files.createDirectories(temporaryDirectory.resolve("case"));
		writeModel(caseDirectory.resolve("case.dmn"));
		writeCases(caseDirectory.resolve("case-test-01.xml"), "case-1");
		TckCatalogueInventory inventory = new TckCatalogueDiscovery().discover(temporaryDirectory);

		assertThat(inventory.requireExactCase("case/case-test-01.xml#case-1")).isNotNull();
		assertThatThrownBy(() -> inventory.requireExactCase("does-not-exist")).hasMessageContaining("matched no case");
	}

	@Test
	void limitsDiscoveryToDeclaredCatalogueRoots() throws Exception {
		Path included = Files.createDirectories(temporaryDirectory.resolve("compliance-level-2/included"));
		writeModel(included.resolve("included.dmn"));
		writeCases(included.resolve("included-test-01.xml"), "included");
		Path excluded = Files.createDirectories(temporaryDirectory.resolve("non-compliant/excluded"));
		writeModel(excluded.resolve("excluded.dmn"));
		writeCases(excluded.resolve("excluded-test-01.xml"), "excluded");

		TckCatalogueInventory inventory = new TckCatalogueDiscovery().discover(temporaryDirectory,
				java.util.List.of("compliance-level-2"));

		assertThat(inventory.testXmlCount()).isEqualTo(1);
		assertThat(inventory.entries()).extracting(TckCatalogueEntry::id)
				.containsExactly("compliance-level-2/included/included-test-01.xml");
	}

	private static void writeCases(Path path, String id) throws Exception {
		Files.writeString(path, """
				<testCases xmlns="http://www.omg.org/spec/DMN/20160719/testcase">
				  <testCase id="%s" name="fixture">
				    <resultNode name="result"><expected><value>ok</value></expected></resultNode>
				  </testCase>
				</testCases>
				""".formatted(id));
	}

	private static void writeModel(Path path) throws Exception {
		Files.writeString(path, "<definitions/>");
	}
}
