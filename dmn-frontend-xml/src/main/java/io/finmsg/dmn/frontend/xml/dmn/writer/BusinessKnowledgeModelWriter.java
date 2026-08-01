package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.model.BusinessKnowledgeModel;

public final class BusinessKnowledgeModelWriter implements XmlWriter<BusinessKnowledgeModel> {

  private final NodeWriter nodeWriter = new NodeWriter();
  private final InformationItemWriter informationItemWriter = new InformationItemWriter();
  private final FunctionDefinitionWriter functionWriter = new FunctionDefinitionWriter();
  private final RequirementWriter requirementWriter = new RequirementWriter();

  @Override
  public void write(XmlEmitter xml, BusinessKnowledgeModel value) {
    xml.startElement("businessKnowledgeModel");
    nodeWriter.writeAttributes(xml, value.getNode());
    nodeWriter.writeChildren(xml, value.getNode());
    if (value.hasVariable()) {
      informationItemWriter.write(xml, "variable", value.getVariable());
    }
    if (value.hasFunction()) {
      functionWriter.write(xml, value.getFunction());
    }
    value.getKnowledgeRequirementsList().forEach(item -> requirementWriter.write(xml, item));
    value.getAuthorityRequirementsList().forEach(item -> requirementWriter.write(xml, item));
    xml.endElement();
  }
}
