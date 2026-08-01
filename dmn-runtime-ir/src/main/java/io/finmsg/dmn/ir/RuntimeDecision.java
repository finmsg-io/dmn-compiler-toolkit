package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;

public record RuntimeDecision(
    int id, int resultSlot, RuntimeType type, List<Integer> dependencies) {
  public RuntimeDecision {
    Objects.requireNonNull(type, "type");
    dependencies = List.copyOf(dependencies);
  }
}
