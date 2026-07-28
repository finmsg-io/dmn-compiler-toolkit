package io.finmsg.dmn.frontend.xml.exception;

public class InvalidAttributeException extends XmlException {

  public InvalidAttributeException(String attribute, String path) {
    super("Invalid attribute '" + attribute + "' at " + path);
  }
}
