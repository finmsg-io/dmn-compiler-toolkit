package io.finmsg.dmn.tck;

import java.util.Map;
import java.util.Objects;

/** One input/expected-result combination from a DMN TCK test file. */
public record TckTestCase(
    String id, String name, Map<String, TckValue> inputs, Map<String, TckValue> expectedResults) {
  public TckTestCase {
    if (Objects.requireNonNull(id, "id").isBlank()) throw new IllegalArgumentException("TCK case ID is blank");
    Objects.requireNonNull(name, "name");
    inputs = Map.copyOf(inputs);
    expectedResults = Map.copyOf(expectedResults);
    if (expectedResults.isEmpty()) throw new IllegalArgumentException("TCK case has no expected results");
  }
}
