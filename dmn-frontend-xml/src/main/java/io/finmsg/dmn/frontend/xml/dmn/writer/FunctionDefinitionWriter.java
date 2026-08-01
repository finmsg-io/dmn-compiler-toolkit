package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.model.FunctionDefinition;
import io.finmsg.dmn.model.FunctionKind;

final class FunctionDefinitionWriter {

  private final InformationItemWriter informationItemWriter = new InformationItemWriter();

  void write(XmlEmitter xml, FunctionDefinition value) {
    xml.startElement("encapsulatedLogic");
    xml.startElement("functionDefinition");
    xml.attribute("kind", kind(value.getKind()));
    value.getFormalParametersList()
        .forEach(parameter -> informationItemWriter.write(xml, "formalParameter", parameter));
    if (value.hasLogic()) {
      xml.startElement("literalExpression");
      xml.startElement("text");
      xml.text(value.getLogic().getText().getText());
      xml.endElement();
      xml.endElement();
    }
    xml.endElement();
    xml.endElement();
  }

  private static String kind(FunctionKind value) {
    return switch (value) {
      case FUNCTION_KIND_FEEL -> "FEEL";
      case FUNCTION_KIND_JAVA -> "Java";
      case FUNCTION_KIND_PMML -> "PMML";
      default -> "";
    };
  }
}
