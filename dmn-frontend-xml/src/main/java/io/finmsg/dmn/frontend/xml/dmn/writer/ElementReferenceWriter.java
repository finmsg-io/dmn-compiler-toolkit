package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.model.ElementReference;

final class ElementReferenceWriter {

  void write(XmlEmitter xml, String elementName, ElementReference value) {
    xml.startElement(elementName);
    xml.attribute("href", value.getHref());
    xml.endElement();
  }
}
