package io.finmsg.dmn.ir;

import java.util.Objects;

/** Typed constant retaining its canonical FEEL lexical value. */
public record RuntimeConstant(RuntimeConstantKind kind, String value, RuntimeType type)
    implements RuntimeExpression {
  public RuntimeConstant {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(type, "type");
  }
}
