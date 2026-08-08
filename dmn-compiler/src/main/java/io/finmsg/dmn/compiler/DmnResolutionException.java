package io.finmsg.dmn.compiler;

/** Unrecoverable failure while accessing a model source through a resolver. */
public final class DmnResolutionException extends RuntimeException {

	public DmnResolutionException(String message, Throwable cause) {
		super(message, cause);
	}
}
