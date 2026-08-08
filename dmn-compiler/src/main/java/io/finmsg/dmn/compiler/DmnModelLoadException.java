package io.finmsg.dmn.compiler;

/** Failure to parse or transitively load a bounded DMN source graph. */
public final class DmnModelLoadException extends RuntimeException {

	public DmnModelLoadException(String message) {
		super(message);
	}

	public DmnModelLoadException(String message, Throwable cause) {
		super(message, cause);
	}
}
