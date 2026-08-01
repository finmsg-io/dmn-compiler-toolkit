package io.finmsg.dmn.ir;

import java.util.Objects;

/** Stable field layout entry for an execution-time context value. */
public record RuntimeField(int index, String name, RuntimeType type) {
  public RuntimeField {
    if (index < 0) {
      throw new IllegalArgumentException("index must not be negative.");
    }
    name = Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
  }
}
