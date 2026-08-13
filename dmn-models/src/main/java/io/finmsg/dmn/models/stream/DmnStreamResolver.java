package io.finmsg.dmn.models.stream;

import io.finmsg.dmn.compiler.DmnImportRequest;
import io.finmsg.dmn.compiler.DmnModelResolver;
import io.finmsg.dmn.compiler.DmnResolutionResult;
import io.finmsg.dmn.compiler.DmnSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Policy boundary resolver that resolves location-addressable DMN import
 * requests against an in-memory/streamed DmnStreamBundle.
 */
public final class DmnStreamResolver implements DmnModelResolver {

	private final DmnStreamBundle bundle;

	public DmnStreamResolver(DmnStreamBundle bundle) {
		this.bundle = Objects.requireNonNull(bundle, "bundle");
	}

	@Override
	public DmnResolutionResult resolve(DmnImportRequest request) {
		Objects.requireNonNull(request, "request");
		if (!request.location().isEmpty()) {
			String rawLocation = request.location();
			Optional<DmnSource> exact = bundle.findSource(rawLocation);
			if (exact.isPresent()) {
				return DmnResolutionResult.resolved(request, exact.get());
			}

			List<DmnSource> candidates = bundle.findMatchingSources(rawLocation);
			if (candidates.size() == 1) {
				return DmnResolutionResult.resolved(request, candidates.getFirst());
			}
			if (candidates.size() > 1) {
				return new DmnResolutionResult(request, candidates);
			}
		}

		if (!request.namespace().isEmpty()) {
			List<DmnSource> matches = bundle.findSourcesByNamespace(request.namespace());
			if (matches.size() == 1) {
				return DmnResolutionResult.resolved(request, matches.getFirst());
			}
			if (matches.size() > 1) {
				return new DmnResolutionResult(request, matches);
			}
		}

		return DmnResolutionResult.missing(request);
	}
}
