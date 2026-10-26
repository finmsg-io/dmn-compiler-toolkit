package io.finmsg.dmn.tck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only, case-specific acceptance of reviewed static negative evidence. */
final class ExpectedRejectionAudit {
	private static final Map<String, Review> REVIEWS = load();
	private static final Map<Path, String> FILE_HASHES = new ConcurrentHashMap<>();

	private ExpectedRejectionAudit() {
	}

	static void requireReviewed(TckCatalogueEntry entry, TckTestCase testCase, String diagnostic) {
		String key = entry.id() + "#" + testCase.id();
		Review review = REVIEWS.get(key);
		if (review == null)
			fail(key, "No case-level review exists");
		if (!testCase.expectedErrorResults().equals(testCase.expectedResults().keySet())
				|| testCase.expectedErrorResults().isEmpty())
			fail(key, "Not every requested result expects an error");
		if (!review.results().equals(testCase.expectedErrorResults()))
			fail(key, "Requested result nodes changed");
		if (!review.modelHash().equals(fileHash(entry.rootDmn().orElseThrow()))
				|| !review.testHash().equals(fileHash(entry.testXml())))
			fail(key, "Official fixture changed; review is stale");
		if (!review.diagnosticHash().equals(hash(diagnostic)))
			fail(key, "Compiler diagnostic changed; review is stale");
		if (!"RELATED_STATIC_REJECTION".equals(review.classification()))
			fail(key, review.justification());
	}

	private static void fail(String key, String reason) {
		throw new AssertionError("UNVERIFIED_MODEL_REJECTION: " + key + ": " + reason);
	}

	static Map<String, Review> reviews() {
		return REVIEWS;
	}

	private static Map<String, Review> load() {
		try (var stream = ExpectedRejectionAudit.class.getResourceAsStream("/expected-rejection-audit.tsv")) {
			if (stream == null)
				throw new IllegalStateException("Missing expected-rejection-audit.tsv");
			Map<String, Review> reviews = new LinkedHashMap<>();
			for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().skip(1).toList()) {
				String[] fields = line.split("\t", -1);
				if (fields.length != 10)
					throw new IllegalStateException("Malformed rejection audit row");
				Review review = new Review(fields[1], Set.of(fields[2].split("\\|")), fields[3], fields[4], fields[5],
						fields[6], fields[9]);
				if (!hash(review.diagnostic()).equals(review.diagnosticHash()))
					throw new IllegalStateException("Corrupt diagnostic hash: " + fields[0]);
				if (reviews.put(fields[0], review) != null)
					throw new IllegalStateException("Duplicate rejection audit key: " + fields[0]);
			}
			return Map.copyOf(reviews);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot load rejection audit", e);
		}
	}

	private static String fileHash(Path path) {
		return FILE_HASHES.computeIfAbsent(path.toAbsolutePath().normalize(), key -> {
			try {
				return hash(Files.readString(key, StandardCharsets.UTF_8).replace("\r\n", "\n"));
			} catch (IOException e) {
				throw new IllegalStateException("Cannot verify fixture " + key, e);
			}
		});
	}

	private static String hash(String text) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	record Review(String classification, Set<String> results, String modelHash, String testHash, String diagnosticHash,
			String justification, String diagnostic) {
	}
}
