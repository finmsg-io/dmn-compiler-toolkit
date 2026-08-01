package io.finmsg.dmn.frontend.xml.dmn.writer;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.XmlWriter;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.frontend.xml.dmn.DmnNamespaces;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;

public final class DefinitionsWriter implements XmlWriter<Definitions> {

  public static final String DMN_1_5_NAMESPACE =
      DmnNamespaces.DMN_1_5;

  private final NodeWriter nodeWriter = new NodeWriter();
  private final ImportWriter importWriter = new ImportWriter();
  private final ItemDefinitionWriter itemDefinitionWriter = new ItemDefinitionWriter();
  private final DrgElementWriter drgElementWriter = new DrgElementWriter();

  @Override
  public void write(XmlEmitter xml, Definitions value) {
    xml.startElement("definitions");
    String modelNamespace = value.getModelNamespaceUri().isEmpty()
        ? DMN_1_5_NAMESPACE : value.getModelNamespaceUri();
    xml.defaultNamespace(modelNamespace);
    value.getNamespacesList().forEach(namespace -> {
      if (!namespace.getUri().equals(modelNamespace)) {
        if (namespace.getPrefix().isEmpty()) {
          throw new XmlWriteException(
              "Cannot preserve a non-DMN default namespace with unprefixed DMN output.");
        }
        xml.namespace(namespace.getPrefix(), namespace.getUri());
      }
    });
    nodeWriter.writeAttributes(xml, value.getNode());
    xml.attribute("namespace", value.getNamespace());
    xml.attribute("expressionLanguage", value.getExpressionLanguage());
    xml.attribute("typeLanguage", value.getTypeLanguage());
    xml.attribute("exporter", value.getExporter());
    xml.attribute("exporterVersion", value.getExporterVersion());
    nodeWriter.writeChildren(xml, value.getNode());

    value.getImportsList().forEach(item -> importWriter.write(xml, item));
    value.getItemDefinitionsList().forEach(item -> itemDefinitionWriter.write(xml, item));
    value.getDrgElementsList().forEach(item -> drgElementWriter.write(xml, item));

    xml.endElement();
  }
}
