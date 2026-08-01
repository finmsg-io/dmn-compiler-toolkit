package io.finmsg.dmn.ir;

import java.util.Objects;

public record RuntimeRelationColumn(String name, RuntimeType type) {
  public RuntimeRelationColumn {
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank.");
    }
    Objects.requireNonNull(type, "type");
  }
}
