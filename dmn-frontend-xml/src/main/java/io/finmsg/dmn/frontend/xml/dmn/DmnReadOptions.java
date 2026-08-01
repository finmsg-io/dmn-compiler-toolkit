package io.finmsg.dmn.frontend.xml.dmn;

import java.util.Objects;

/** Immutable limits and source metadata used while reading a DMN document. */
public record DmnReadOptions(
    long maxInputBytes,
    String systemId,
    boolean captureSourceLocations,
    int maxElementDepth) {

  public static final long DEFAULT_MAX_INPUT_BYTES = 16L * 1024L * 1024L;
  public static final int DEFAULT_MAX_ELEMENT_DEPTH = 256;

  public DmnReadOptions {
    if (maxInputBytes <= 0) {
      throw new IllegalArgumentException("maxInputBytes must be greater than zero.");
    }
    if (maxElementDepth <= 0) {
      throw new IllegalArgumentException("maxElementDepth must be greater than zero.");
    }
    systemId = Objects.requireNonNullElse(systemId, "");
  }

  public DmnReadOptions(long maxInputBytes, String systemId) {
    this(maxInputBytes, systemId, false, DEFAULT_MAX_ELEMENT_DEPTH);
  }

  public DmnReadOptions(long maxInputBytes, String systemId, boolean captureSourceLocations) {
    this(maxInputBytes, systemId, captureSourceLocations, DEFAULT_MAX_ELEMENT_DEPTH);
  }

  public static DmnReadOptions defaults() {
    return new DmnReadOptions(
        DEFAULT_MAX_INPUT_BYTES, "", false, DEFAULT_MAX_ELEMENT_DEPTH);
  }

  public DmnReadOptions withSystemId(String value) {
    return new DmnReadOptions(maxInputBytes, value, captureSourceLocations, maxElementDepth);
  }

  public DmnReadOptions withSourceLocations(boolean value) {
    return new DmnReadOptions(maxInputBytes, systemId, value, maxElementDepth);
  }

  public DmnReadOptions withMaxElementDepth(int value) {
    return new DmnReadOptions(maxInputBytes, systemId, captureSourceLocations, value);
  }
}
