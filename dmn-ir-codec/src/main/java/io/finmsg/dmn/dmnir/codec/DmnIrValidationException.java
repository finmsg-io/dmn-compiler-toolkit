package io.finmsg.dmn.dmnir.codec;

/**
 * Fail-fast diagnostic with a stable code and an indexed artifact path; no
 * payload values.
 */
public final class DmnIrValidationException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	public enum Code {
		LIMIT_EXCEEDED, MALFORMED_PROTOBUF, UNKNOWN_FIELD, INVALID_HEADER, UNSUPPORTED_VERSION, UNSUPPORTED_CAPABILITY, INVALID_IDENTITY, INVALID_IMPORT, INVALID_TYPE, INVALID_EXPRESSION, INVALID_NUMBER, INVALID_REFERENCE, INVALID_DEPENDENCY, INVALID_EVALUATION_ORDER
	}

	private final Code code;
	private final String path;

	DmnIrValidationException(Code code, String path, String reason) {
		super(code + " at " + path + ": " + reason);
		this.code = code;
		this.path = path;
	}

	public Code code() {
		return code;
	}

	public String path() {
		return path;
	}
}
