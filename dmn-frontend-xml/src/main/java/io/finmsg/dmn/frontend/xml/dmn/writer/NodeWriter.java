package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.model.Node;

public final class NodeWriter {

  public void writeAttributes(XmlEmitter xml, Node node) {
    xml.attribute("id", node.getId());
    xml.attribute("name", node.getName());
    xml.attribute("label", node.getLabel());
  }
}
