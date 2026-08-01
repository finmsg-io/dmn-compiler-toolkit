package io.finmsg.dmn.frontend.xml.dmn;

import java.util.Objects;

/** Immutable limits and source metadata used while reading a DMN document. */
public record DmnReadOptions(long maxInputBytes, String systemId, boolean captureSourceLocations) {

  public static final long DEFAULT_MAX_INPUT_BYTES = 16L * 1024L * 1024L;

  public DmnReadOptions {
    if (maxInputBytes <= 0) {
      throw new IllegalArgumentException("maxInputBytes must be greater than zero.");
    }
    systemId = Objects.requireNonNullElse(systemId, "");
  }

  public DmnReadOptions(long maxInputBytes, String systemId) {
    this(maxInputBytes, systemId, false);
  }

  public static DmnReadOptions defaults() {
    return new DmnReadOptions(DEFAULT_MAX_INPUT_BYTES, "", false);
  }

  public DmnReadOptions withSystemId(String value) {
    return new DmnReadOptions(maxInputBytes, value, captureSourceLocations);
  }

  public DmnReadOptions withSourceLocations(boolean value) {
    return new DmnReadOptions(maxInputBytes, systemId, value);
  }
}
