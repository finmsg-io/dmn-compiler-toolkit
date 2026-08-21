package io.finmsg.dmn.compiler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Location-based resolver for tests, embedded models, and programmatic
 * compilation.
 */
public final class InMemoryDmnModelResolver implements DmnModelResolver {
	private static final java.util.regex.Pattern NAMESPACE_PATTERN = java.util.regex.Pattern
			.compile("namespace\\s*=\\s*\"([^\"]+)\"");

	private final Map<DmnSourceId, DmnSource> sources;
	private final Map<String, DmnSource> sourcesByNamespace;

	public InMemoryDmnModelResolver(Collection<DmnSource> sources) {
		Objects.requireNonNull(sources, "sources");
		Map<DmnSourceId, DmnSource> indexed = new LinkedHashMap<>();
		Map<String, DmnSource> byNamespace = new LinkedHashMap<>();
		for (DmnSource source : sources) {
			Objects.requireNonNull(source, "source");
			if (indexed.putIfAbsent(source.id(), source) != null) {
				throw new IllegalArgumentException("Duplicate DMN source identity: " + source.id());
			}
			String ns = extractNamespace(source);
			if (!ns.isEmpty()) {
				byNamespace.putIfAbsent(ns, source);
			}
		}
		this.sources = Map.copyOf(indexed);
		this.sourcesByNamespace = Map.copyOf(byNamespace);
	}

	@Override
	public DmnResolutionResult resolve(DmnImportRequest request) {
		Objects.requireNonNull(request, "request");
		if (request.resolvedLocation().isPresent()) {
			DmnSource s = sources.get(request.resolvedLocation().get());
			if (s != null) {
				return DmnResolutionResult.resolved(request, s);
			}
		}
		if (!request.namespace().isEmpty()) {
			DmnSource s = sourcesByNamespace.get(request.namespace());
			if (s != null) {
				return DmnResolutionResult.resolved(request, s);
			}
		}
		return DmnResolutionResult.missing(request);
	}

	private static String extractNamespace(DmnSource source) {
		try {
			String text = new String(source.content(), java.nio.charset.StandardCharsets.UTF_8);
			var matcher = NAMESPACE_PATTERN.matcher(text);
			if (matcher.find()) {
				return matcher.group(1).trim();
			}
		} catch (Exception ignored) {
		}
		return "";
	}
}
