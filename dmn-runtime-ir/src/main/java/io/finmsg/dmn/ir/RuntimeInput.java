package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeInput(int id, int valueSlot, RuntimeType type) {
  public RuntimeInput { Objects.requireNonNull(type, "type"); }
}
