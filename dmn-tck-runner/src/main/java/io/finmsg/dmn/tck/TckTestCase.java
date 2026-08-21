package io.finmsg.dmn.tck;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One input/expected-result combination from a DMN TCK test file. */
public record TckTestCase(String id, String name, Map<String, TckValue> inputs, Map<String, TckValue> expectedResults,
		Set<String> expectedErrorResults) {
	public TckTestCase {
		if (Objects.requireNonNull(id, "id").isBlank())
			throw new IllegalArgumentException("TCK case ID is blank");
		Objects.requireNonNull(name, "name");
		inputs = Map.copyOf(inputs);
		expectedResults = Map.copyOf(expectedResults);
		expectedErrorResults = Set.copyOf(expectedErrorResults);
		if (expectedResults.isEmpty())
			throw new IllegalArgumentException("TCK case has no expected results");
		if (!expectedResults.keySet().containsAll(expectedErrorResults))
			throw new IllegalArgumentException("Expected-error result is not a declared result node");
	}

	public TckTestCase(String id, String name, Map<String, TckValue> inputs, Map<String, TckValue> expectedResults) {
		this(id, name, inputs, expectedResults, Set.of());
	}
}
