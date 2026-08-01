package io.finmsg.dmn.frontend.xml.dmn;

import java.util.Objects;

/** Immutable limits and source metadata used while reading a DMN document. */
public record DmnReadOptions(long maxInputBytes, String systemId) {

  public static final long DEFAULT_MAX_INPUT_BYTES = 16L * 1024L * 1024L;

  public DmnReadOptions {
    if (maxInputBytes <= 0) {
      throw new IllegalArgumentException("maxInputBytes must be greater than zero.");
    }
    systemId = Objects.requireNonNullElse(systemId, "");
  }

  public static DmnReadOptions defaults() {
    return new DmnReadOptions(DEFAULT_MAX_INPUT_BYTES, "");
  }

  public DmnReadOptions withSystemId(String value) {
    return new DmnReadOptions(maxInputBytes, value);
  }
}
