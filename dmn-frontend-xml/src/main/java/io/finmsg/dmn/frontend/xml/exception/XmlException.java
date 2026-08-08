package io.finmsg.dmn.frontend.xml.exception;

public class XmlException extends RuntimeException {

	public XmlException(String message) {
		super(message);
	}

	public XmlException(String message, Throwable cause) {
		super(message, cause);
	}

	public XmlException(Throwable cause) {
		super(cause);
	}
}
