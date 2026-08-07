package io.finmsg.dmn.tck;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAmount;
import java.util.*;

/** Canonical value decoded from official OMG DMN TCK test-case XML. */
public record TckValue(Kind kind, Object value) {
  public enum Kind { NULL, BOOLEAN, NUMBER, STRING, DATE, TIME, DATE_TIME, DURATION, LIST, CONTEXT }

  public TckValue {
    Objects.requireNonNull(kind, "kind");
    if (kind == Kind.NULL && value != null || kind != Kind.NULL && value == null) {
      throw new IllegalArgumentException("TCK value does not match kind " + kind);
    }
  }

  public static TckValue nullValue() { return new TckValue(Kind.NULL, null); }
  public static TckValue bool(boolean value) { return new TckValue(Kind.BOOLEAN, value); }
  public static TckValue number(String value) { return new TckValue(Kind.NUMBER, new BigDecimal(value)); }
  public static TckValue string(String value) { return new TckValue(Kind.STRING, value); }
  public static TckValue date(LocalDate value) { return new TckValue(Kind.DATE, value); }
  public static TckValue time(Object value) { return new TckValue(Kind.TIME, value); }
  public static TckValue dateTime(Object value) { return new TckValue(Kind.DATE_TIME, value); }
  public static TckValue duration(TemporalAmount value) { return new TckValue(Kind.DURATION, value); }
  public static TckValue list(List<?> value) { return new TckValue(Kind.LIST, List.copyOf(value)); }
  public static TckValue context(Map<String, ?> value) { return new TckValue(Kind.CONTEXT, Map.copyOf(value)); }

  public Object runtimeValue() { return value; }
}
