package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.model.Definitions;

public final class DefinitionsWriter implements XmlWriter<Definitions> {

  public static final String DMN_1_5_NAMESPACE =
      "https://www.omg.org/spec/DMN/20230324/MODEL/";

  private final NodeWriter nodeWriter = new NodeWriter();
  private final ImportWriter importWriter = new ImportWriter();
  private final ItemDefinitionWriter itemDefinitionWriter = new ItemDefinitionWriter();
  private final DrgElementWriter drgElementWriter = new DrgElementWriter();

  @Override
  public void write(XmlEmitter xml, Definitions value) {
    xml.startElement("definitions");
    xml.defaultNamespace(DMN_1_5_NAMESPACE);
    nodeWriter.writeAttributes(xml, value.getNode());
    xml.attribute("namespace", value.getNamespace());
    xml.attribute("expressionLanguage", value.getExpressionLanguage());
    xml.attribute("typeLanguage", value.getTypeLanguage());
    xml.attribute("exporter", value.getExporter());
    xml.attribute("exporterVersion", value.getExporterVersion());

    value.getImportsList().forEach(item -> importWriter.write(xml, item));
    value.getItemDefinitionsList().forEach(item -> itemDefinitionWriter.write(xml, item));
    value.getDrgElementsList().forEach(item -> drgElementWriter.write(xml, item));

    xml.endElement();
  }
}
