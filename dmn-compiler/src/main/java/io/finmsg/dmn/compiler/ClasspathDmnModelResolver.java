package io.finmsg.dmn.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;

/**
 * Resolves logical {@code classpath:} imports through a supplied class loader.
 */
public final class ClasspathDmnModelResolver implements DmnModelResolver {
	private final ClassLoader classLoader;
	private final String rootPrefix;

	public ClasspathDmnModelResolver(ClassLoader classLoader) {
		this(classLoader, "");
	}

	public ClasspathDmnModelResolver(ClassLoader classLoader, String rootPrefix) {
		this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
		this.rootPrefix = normalizeRoot(rootPrefix);
	}

	@Override
	public DmnResolutionResult resolve(DmnImportRequest request) {
		Objects.requireNonNull(request, "request");
		return request.resolvedLocation().filter(ClasspathDmnModelResolver::isClasspathUri)
				.map(id -> resolveResource(request, id)).orElseGet(() -> DmnResolutionResult.missing(request));
	}

	private DmnResolutionResult resolveResource(DmnImportRequest request, DmnSourceId id) {
		String resourceName = resourceName(id.uri());
		if (!resourceName.startsWith(rootPrefix)) {
			return DmnResolutionResult.missing(request);
		}
		try (InputStream input = classLoader.getResourceAsStream(resourceName)) {
			if (input == null) {
				return DmnResolutionResult.missing(request);
			}
			return DmnResolutionResult.resolved(request, new DmnSource(id, input.readAllBytes()));
		} catch (IOException exception) {
			throw new DmnResolutionException("Cannot read classpath DMN source: " + id, exception);
		}
	}

	private static boolean isClasspathUri(DmnSourceId id) {
		return "classpath".equalsIgnoreCase(id.uri().getScheme());
	}

	private static String resourceName(URI uri) {
		String path = uri.getPath();
		return path.startsWith("/") ? path.substring(1) : path;
	}

	private static String normalizeRoot(String root) {
		Objects.requireNonNull(root, "rootPrefix");
		String normalized = root.replace('\\', '/');
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		if (normalized.contains("../") || normalized.equals("..")) {
			throw new IllegalArgumentException("Classpath root must not contain parent traversal");
		}
		return normalized.isEmpty() || normalized.endsWith("/") ? normalized : normalized + "/";
	}
}
