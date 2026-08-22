package io.finmsg.dmn.generator.java;

import java.util.Map;

/** Stable invocation contract implemented by generated DMN decision engines. */
public interface GeneratedDecisionEngine {

	Object[] evaluate(Object[] inputSlots);

	default Object evaluateInvocable(String invocableName, Map<String, Object> inputs) {
		return null;
	}
}
