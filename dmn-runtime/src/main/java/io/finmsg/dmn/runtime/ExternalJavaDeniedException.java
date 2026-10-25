package io.finmsg.dmn.runtime;

/**
 * Authorization failed before resolving or executing a model-selected target.
 */
public final class ExternalJavaDeniedException extends DmnEvaluationException {
	public ExternalJavaDeniedException(String className, String signature) {
		super("EXTERNAL_JAVA_DENIED: " + className + "#" + signature);
	}
}
