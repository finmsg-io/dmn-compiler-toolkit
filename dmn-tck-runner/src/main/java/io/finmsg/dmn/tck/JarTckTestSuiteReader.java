package io.finmsg.dmn.tck;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * In-memory reader for streaming official DMN TCK test suites directly from
 * official JAR archives.
 */
public final class JarTckTestSuiteReader {

	public record TckJarModelSuite(String caseId, String dmnFileName, byte[] dmnBytes, URI modelUri,
			List<TckTestCase> testCases) {
	}

	public List<TckJarModelSuite> readFromJar(Path jarPath) throws IOException {
		try (InputStream input = Files.newInputStream(jarPath)) {
			return readFromJarStream(input, jarPath.toUri());
		}
	}

	public List<TckJarModelSuite> readFromJarStream(InputStream jarStream, URI baseUri) throws IOException {
		Map<String, byte[]> files = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(jarStream)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (!entry.isDirectory()) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					zip.transferTo(baos);
					files.put(entry.getName().replace('\\', '/'), baos.toByteArray());
				}
			}
		}

		Map<String, byte[]> dmnFiles = new LinkedHashMap<>();
		Map<String, byte[]> xmlFiles = new LinkedHashMap<>();

		files.forEach((name, content) -> {
			if (name.endsWith(".dmn")) {
				dmnFiles.put(name, content);
			} else if (name.endsWith("-test-01.xml")) {
				xmlFiles.put(name, content);
			}
		});

		List<TckJarModelSuite> suites = new ArrayList<>();
		TckTestCaseReader caseReader = new TckTestCaseReader();

		for (Map.Entry<String, byte[]> xmlEntry : xmlFiles.entrySet()) {
			String xmlPath = xmlEntry.getKey();
			String dmnPath = xmlPath.substring(0, xmlPath.length() - "-test-01.xml".length()) + ".dmn";
			byte[] dmnBytes = dmnFiles.get(dmnPath);

			if (dmnBytes == null) {
				// Fallback: search for any dmn in the same directory
				String dir = xmlPath.contains("/") ? xmlPath.substring(0, xmlPath.lastIndexOf('/') + 1) : "";
				for (String dmnCandidate : dmnFiles.keySet()) {
					if (dmnCandidate.startsWith(dir)) {
						dmnBytes = dmnFiles.get(dmnCandidate);
						dmnPath = dmnCandidate;
						break;
					}
				}
			}

			if (dmnBytes != null) {
				List<TckTestCase> testCases = caseReader.read(new ByteArrayInputStream(xmlEntry.getValue()));
				String caseId = dmnPath.contains("/") ? dmnPath.substring(dmnPath.lastIndexOf('/') + 1) : dmnPath;
				URI modelUri = URI.create("jar:" + baseUri + "!/" + dmnPath);
				suites.add(new TckJarModelSuite(caseId, dmnPath, dmnBytes, modelUri, testCases));
			}
		}

		return List.copyOf(suites);
	}
}
