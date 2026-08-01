package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.model.InputData;

public final class InputDataWriter implements XmlWriter<InputData> {

  private final NodeWriter nodeWriter = new NodeWriter();
  private final InformationItemWriter informationItemWriter = new InformationItemWriter();

  @Override
  public void write(XmlEmitter xml, InputData value) {
    xml.startElement("inputData");
    nodeWriter.writeAttributes(xml, value.getNode());
    nodeWriter.writeChildren(xml, value.getNode());
    if (value.hasVariable()) {
      informationItemWriter.write(xml, "variable", value.getVariable());
    }
    xml.endElement();
  }
}
