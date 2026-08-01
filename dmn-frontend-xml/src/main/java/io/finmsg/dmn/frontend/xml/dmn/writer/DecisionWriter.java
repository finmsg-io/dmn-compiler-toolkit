package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.model.Decision;

public final class DecisionWriter implements XmlWriter<Decision> {

  private final NodeWriter nodeWriter = new NodeWriter();
  private final InformationItemWriter informationItemWriter = new InformationItemWriter();
  private final RequirementWriter requirementWriter = new RequirementWriter();
  private final DecisionLogicWriter decisionLogicWriter = new DecisionLogicWriter();

  @Override
  public void write(XmlEmitter xml, Decision value) {
    xml.startElement("decision");
    nodeWriter.writeAttributes(xml, value.getNode());
    if (value.hasVariable()) {
      informationItemWriter.write(xml, "variable", value.getVariable());
    }
    value.getInformationRequirementsList().forEach(item -> requirementWriter.write(xml, item));
    value.getKnowledgeRequirementsList().forEach(item -> requirementWriter.write(xml, item));
    value.getAuthorityRequirementsList().forEach(item -> requirementWriter.write(xml, item));
    if (value.hasLogic()) {
      decisionLogicWriter.write(xml, value.getLogic());
    }
    xml.endElement();
  }
}
