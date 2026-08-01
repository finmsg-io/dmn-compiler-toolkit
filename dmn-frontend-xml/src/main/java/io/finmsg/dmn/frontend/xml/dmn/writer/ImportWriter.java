package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.model.Import;

public final class ImportWriter implements XmlWriter<Import> {

  private final NodeWriter nodeWriter = new NodeWriter();

  @Override
  public void write(XmlEmitter xml, Import value) {
    xml.startElement("import");
    nodeWriter.writeAttributes(xml, value.getNode());
    xml.attribute("name", value.getName());
    xml.attribute("namespace", value.getNamespace());
    xml.attribute("locationURI", value.getLocationUri());
    xml.attribute("importType", value.getImportType());
    nodeWriter.writeChildren(xml, value.getNode());
    xml.endElement();
  }
}
