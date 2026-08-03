package io.finmsg.dmn.tck;

import java.math.BigDecimal;
import java.util.Objects;

/** Canonical scalar value decoded from TCK test-case XML. */
public record TckValue(Kind kind, Object value) {
  public enum Kind { NULL, BOOLEAN, NUMBER, STRING }

  public TckValue {
    Objects.requireNonNull(kind, "kind");
    if (kind == Kind.NULL && value != null || kind != Kind.NULL && value == null) {
      throw new IllegalArgumentException("TCK value does not match kind " + kind);
    }
    if (kind == Kind.BOOLEAN && !(value instanceof Boolean)
        || kind == Kind.NUMBER && !(value instanceof BigDecimal)
        || kind == Kind.STRING && !(value instanceof String)) {
      throw new IllegalArgumentException("TCK scalar has the wrong Java representation");
    }
  }

  public static TckValue nullValue() { return new TckValue(Kind.NULL, null); }
  public static TckValue bool(boolean value) { return new TckValue(Kind.BOOLEAN, value); }
  public static TckValue number(String value) { return new TckValue(Kind.NUMBER, new BigDecimal(value)); }
  public static TckValue string(String value) { return new TckValue(Kind.STRING, value); }

  public Object runtimeValue() { return value; }
}
