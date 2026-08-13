package io.finmsg.dmn.tck;

import java.util.Map;

/** Actual named decision values produced by one TCK execution. */
public record TckExecutionResult(String testCaseId, Map<String, Object> decisionValues) {
	public TckExecutionResult {
		decisionValues = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(decisionValues));
	}
}
