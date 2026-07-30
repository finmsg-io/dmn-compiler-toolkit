package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.Decision;

public final class DecisionWriter implements XmlWriter<Decision> {

  private final NodeWriter nodeWriter = new NodeWriter();
  private final InformationItemWriter informationItemWriter = new InformationItemWriter();

  @Override
  public void write(XmlEmitter xml, Decision value) {
    if (value.getInformationRequirementsCount() != 0
        || value.getKnowledgeRequirementsCount() != 0
        || value.getAuthorityRequirementsCount() != 0
        || value.hasLogic()) {
      throw new XmlWriteException(
          "Decision requirements and decision logic are not supported by this writer slice yet.");
    }

    xml.startElement("decision");
    nodeWriter.writeAttributes(xml, value.getNode());
    if (value.hasVariable()) {
      informationItemWriter.write(xml, "variable", value.getVariable());
    }
    xml.endElement();
  }
}
