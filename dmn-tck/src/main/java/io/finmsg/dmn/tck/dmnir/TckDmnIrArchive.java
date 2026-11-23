package io.finmsg.dmn.tck.dmnir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lightweight reader for pre-compiled {@code .dmnir} Protobuf artifacts and
 * their manifest stored in {@code dmn-tck} ({@code tck-dmnir/}).
 *
 * <p>
 * Uses a simple line-oriented TSV manifest ({@code manifest.tsv}) so this
 * module carries zero external JSON/runtime dependencies and can be consumed
 * identically by Java and standalone Rust harnesses.
 */
public final class TckDmnIrArchive {
	public static final String RESOURCE_ROOT = "tck-dmnir/";
	public static final String MANIFEST_RESOURCE = RESOURCE_ROOT + "manifest.tsv";

	public record CaseEntry(String caseKey, String artifactPath, String exportDiagnostic) {
		public boolean exported() {
			return artifactPath != null && !artifactPath.isEmpty();
		}
	}

	private final String tckRevision;
	private final String dmnirSchemaVersion;
	private final Map<String, CaseEntry> entries;

	private TckDmnIrArchive(String tckRevision, String dmnirSchemaVersion, Map<String, CaseEntry> entries) {
		this.tckRevision = tckRevision;
		this.dmnirSchemaVersion = dmnirSchemaVersion;
		this.entries = Map.copyOf(entries);
	}

	public static Optional<TckDmnIrArchive> loadFromClasspath() throws IOException {
		ClassLoader cl = TckDmnIrArchive.class.getClassLoader();
		try (InputStream in = cl.getResourceAsStream(MANIFEST_RESOURCE)) {
			if (in == null) {
				return Optional.empty();
			}
			String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			String revision = "";
			String schemaVersion = "";
			Map<String, CaseEntry> map = new LinkedHashMap<>();
			for (String line : content.split("\r?\n")) {
				if (line.isEmpty()) {
					continue;
				}
				if (line.startsWith("#tckRevision=")) {
					revision = line.substring("#tckRevision=".length()).trim();
					continue;
				}
				if (line.startsWith("#dmnirSchemaVersion=")) {
					schemaVersion = line.substring("#dmnirSchemaVersion=".length()).trim();
					continue;
				}
				if (line.startsWith("#") || line.startsWith("caseKey\t")) {
					continue;
				}
				String[] parts = line.split("\t", -1);
				if (parts.length >= 3) {
					map.put(parts[0], new CaseEntry(parts[0], parts[1], parts[2]));
				}
			}
			return Optional.of(new TckDmnIrArchive(revision, schemaVersion, map));
		}
	}

	public String tckRevision() {
		return tckRevision;
	}

	public String dmnirSchemaVersion() {
		return dmnirSchemaVersion;
	}

	public Map<String, CaseEntry> entries() {
		return entries;
	}

	public Optional<CaseEntry> entry(String caseKey) {
		return Optional.ofNullable(entries.get(caseKey));
	}

	public byte[] readArtifactBytes(String artifactPath) throws IOException {
		ClassLoader cl = TckDmnIrArchive.class.getClassLoader();
		try (InputStream in = cl.getResourceAsStream(RESOURCE_ROOT + artifactPath)) {
			if (in == null) {
				throw new IOException("Missing pre-compiled DMNIR resource: " + RESOURCE_ROOT + artifactPath);
			}
			return in.readAllBytes();
		}
	}
}
