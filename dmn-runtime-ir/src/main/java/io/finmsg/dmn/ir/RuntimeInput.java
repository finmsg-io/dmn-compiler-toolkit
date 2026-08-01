package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeInput(int id, int valueSlot, RuntimeType type) {
  public RuntimeInput {
    if (id < 0 || valueSlot < 0) {
      throw new IllegalArgumentException("Runtime input IDs and slots must be non-negative.");
    }
    Objects.requireNonNull(type, "type");
  }
}
