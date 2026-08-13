package io.finmsg.dmn.generator.java;

/** Stable invocation contract implemented by generated DMN decision engines. */
@FunctionalInterface
public interface GeneratedDecisionEngine {

	Object[] evaluate(Object[] inputSlots);
}
