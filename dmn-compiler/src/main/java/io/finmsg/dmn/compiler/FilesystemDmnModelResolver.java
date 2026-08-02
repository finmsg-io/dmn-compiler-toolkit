package io.finmsg.dmn.compiler;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves file imports confined to one filesystem root. */
public final class FilesystemDmnModelResolver implements DmnModelResolver {
  private final Path root;

  public FilesystemDmnModelResolver(Path root) {
    Objects.requireNonNull(root, "root");
    try {
      this.root = root.toRealPath();
    } catch (IOException exception) {
      throw new DmnResolutionException("Cannot access DMN resolver root: " + root, exception);
    }
    if (!Files.isDirectory(this.root)) {
      throw new IllegalArgumentException("DMN resolver root must be a directory: " + root);
    }
  }

  @Override
  public DmnResolutionResult resolve(DmnImportRequest request) {
    Objects.requireNonNull(request, "request");
    return request.resolvedLocation()
        .filter(FilesystemDmnModelResolver::isFileUri)
        .map(id -> resolveFile(request, id))
        .orElseGet(() -> DmnResolutionResult.missing(request));
  }

  private DmnResolutionResult resolveFile(DmnImportRequest request, DmnSourceId requestedId) {
    Path candidate;
    try {
      candidate = Path.of(requestedId.uri()).toAbsolutePath().normalize();
    } catch (IllegalArgumentException exception) {
      return DmnResolutionResult.missing(request);
    }
    if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
      return DmnResolutionResult.missing(request);
    }
    try {
      Path realCandidate = candidate.toRealPath();
      if (!realCandidate.startsWith(root) || !Files.isRegularFile(realCandidate)) {
        return DmnResolutionResult.missing(request);
      }
      DmnSource source = new DmnSource(
          new DmnSourceId(realCandidate.toUri()), Files.readAllBytes(realCandidate));
      return DmnResolutionResult.resolved(request, source);
    } catch (IOException exception) {
      throw new DmnResolutionException("Cannot read DMN source: " + candidate, exception);
    }
  }

  private static boolean isFileUri(DmnSourceId id) {
    URI uri = id.uri();
    return "file".equalsIgnoreCase(uri.getScheme());
  }
}
