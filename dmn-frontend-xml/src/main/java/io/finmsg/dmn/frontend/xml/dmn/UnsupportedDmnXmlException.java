package io.finmsg.dmn.frontend.xml.dmn;

/** Indicates well-formed DMN XML whose model construct is not supported by this frontend. */
public final class UnsupportedDmnXmlException extends RuntimeException {
  public UnsupportedDmnXmlException(String message) {
    super(message);
  }
}
