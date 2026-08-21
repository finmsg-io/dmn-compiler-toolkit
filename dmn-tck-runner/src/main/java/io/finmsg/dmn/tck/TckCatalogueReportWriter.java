package io.finmsg.dmn.tck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Writes deterministic JSON without introducing a runtime JSON dependency. */
public final class TckCatalogueReportWriter {
	public void write(Path destination, TckCatalogueReport report) throws IOException {
		Path parent = destination.toAbsolutePath().normalize().getParent();
		if (parent != null)
			Files.createDirectories(parent);
		Files.writeString(destination, toJson(report), StandardCharsets.UTF_8);
	}

	String toJson(TckCatalogueReport report) {
		StringBuilder json = new StringBuilder(4096);
		TckCatalogueInventory inventory = report.inventory();
		json.append("{\n  \"revision\": \"").append(escape(report.revision())).append("\",\n")
				.append("  \"inventory\": {\"directories\": ").append(inventory.directoryCount())
				.append(", \"testXmlFiles\": ").append(inventory.testXmlCount()).append(", \"dmnFiles\": ")
				.append(inventory.dmnFileCount()).append(", \"decodedCases\": ").append(inventory.decodedCaseCount())
				.append("},\n  \"statusCounts\": {");
		boolean first = true;
		for (TckCatalogueStatus status : TckCatalogueStatus.values()) {
			if (!first)
				json.append(',');
			json.append("\n    \"").append(status).append("\": ").append(report.statusCounts().get(status));
			first = false;
		}
		json.append("\n  },\n  \"outcomes\": [");
		first = true;
		for (TckCatalogueOutcome outcome : report.outcomes().stream()
				.sorted(Comparator.comparing(TckCatalogueOutcome::key)).toList()) {
			if (!first)
				json.append(',');
			json.append("\n    {\"entryId\": \"").append(escape(outcome.entryId())).append("\", \"caseId\": \"")
					.append(escape(outcome.caseId())).append("\", \"backend\": \"").append(escape(outcome.backend()))
					.append("\", \"status\": \"").append(outcome.status()).append("\", \"diagnostic\": \"")
					.append(escape(outcome.diagnostic())).append("\"}");
			first = false;
		}
		return json.append("\n  ]\n}\n").toString();
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t",
				"\\t");
	}
}
