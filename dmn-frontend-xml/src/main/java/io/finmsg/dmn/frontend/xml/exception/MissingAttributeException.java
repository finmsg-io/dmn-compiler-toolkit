package io.finmsg.dmn.frontend.xml.exception;

public final class MissingAttributeException extends XmlException {

	public MissingAttributeException(String attribute, String path) {
		super("Missing attribute '" + attribute + "' at " + path);
	}
}
