package io.finmsg.dmn.generator.java;

import java.util.Map;
import java.util.Objects;

/**
 * Output result of Java code generation containing fully qualified class names
 * and Java source content.
 */
public record DmnJavaGeneratorResult(String mainClassName, Map<String, String> sources) {

	public DmnJavaGeneratorResult {
		Objects.requireNonNull(mainClassName, "mainClassName");
		sources = Map.copyOf(sources);
	}

	public String mainSource() {
		return sources.get(mainClassName);
	}
}
