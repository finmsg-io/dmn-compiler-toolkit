package io.finmsg.dmn.compiler;

import java.util.Objects;

/** One resolved import edge retained for later structural validation. */
public record DmnImportEdge(
    DmnSourceId importer,
    int importIndex,
    DmnImportRequest request,
    DmnSourceId imported) {

  public DmnImportEdge {
    Objects.requireNonNull(importer, "importer");
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(imported, "imported");
    if (importIndex < 0) {
      throw new IllegalArgumentException("importIndex must not be negative");
    }
    if (!request.importer().equals(importer)) {
      throw new IllegalArgumentException("Import request must belong to the importing source");
    }
  }
}
