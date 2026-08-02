package io.finmsg.dmn.compiler;

import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.semantic.analysis.DmnSymbolBinding;
import java.util.List;
import java.util.Objects;

/** One analyzed model and its semantic evidence, addressed by stable source identity. */
public record DmnSemanticModel(
    DmnSourceId sourceId,
    DmnModelIdentity identity,
    Definitions model,
    List<DrgElement> compilationOrder,
    List<DmnSymbolBinding> bindings) {

  public DmnSemanticModel {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(compilationOrder, "compilationOrder");
    Objects.requireNonNull(bindings, "bindings");
    if (!identity.namespace().equals(model.getNamespace())
        || !identity.name().equals(model.getNode().getName())) {
      throw new IllegalArgumentException("Model identity must match analyzed definitions");
    }
    compilationOrder = compilationOrder.stream()
        .map(element -> Objects.requireNonNull(element, "compilationOrder element"))
        .toList();
    bindings = bindings.stream()
        .map(binding -> Objects.requireNonNull(binding, "binding"))
        .toList();
  }
}
