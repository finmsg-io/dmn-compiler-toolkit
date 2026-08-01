package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuntimeBkm(
    int id,
    int resultSlot,
    RuntimeType type,
    List<Integer> dependencies,
    RuntimeFunctionKind functionKind,
    Optional<RuntimeFunctionDefinition> function) {
  public RuntimeBkm {
    if (id < 0 || resultSlot < 0) {
      throw new IllegalArgumentException("Runtime BKM IDs and slots must be non-negative.");
    }
    Objects.requireNonNull(type, "type");
    dependencies = List.copyOf(dependencies);
    Objects.requireNonNull(functionKind, "functionKind");
    function = Objects.requireNonNull(function, "function");
  }

  public RuntimeBkm(
      int id, int resultSlot, RuntimeType type, List<Integer> dependencies) {
    this(id, resultSlot, type, dependencies, RuntimeFunctionKind.FEEL, Optional.empty());
  }
}
