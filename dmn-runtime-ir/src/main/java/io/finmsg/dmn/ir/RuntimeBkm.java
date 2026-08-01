package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;

public record RuntimeBkm(
    int id, int resultSlot, RuntimeType type, List<Integer> dependencies) {
  public RuntimeBkm {
    Objects.requireNonNull(type, "type");
    dependencies = List.copyOf(dependencies);
  }
}
