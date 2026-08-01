package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.frontend.xml.dmn.DmnXmlContext;
import io.finmsg.dmn.frontend.xml.exception.XmlException;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.Namespace;

public final class DefinitionsReader {

  private final DmnXmlContext context;

  private final NodeReader nodeReader = new NodeReader();

  public DefinitionsReader(DmnXmlContext context) {
    this.context = context;
  }

  public Definitions read(XmlCursor cursor) {

    if (!cursor.isElement("definitions")) {
      throw new XmlException("Expected <definitions> but found <" + cursor.elementName() + ">");
    }

    Definitions.Builder builder = Definitions.newBuilder();

    if (!io.finmsg.dmn.frontend.xml.dmn.DmnNamespaces.DMN_1_5
        .equals(context.modelNamespace())) {
      builder.setModelNamespaceUri(context.modelNamespace());
    }
    cursor.documentNamespaceDeclarations().forEach((prefix, uri) -> {
      if (!uri.equals(context.modelNamespace())) {
        builder.addNamespaces(Namespace.newBuilder().setPrefix(prefix).setUri(uri));
      }
    });

    builder.setNode(nodeReader.read(cursor));

    if (cursor.hasAttribute("namespace")) {
      builder.setNamespace(cursor.requiredAttribute("namespace"));
    }

    if (cursor.hasAttribute("expressionLanguage")) {
      builder.setExpressionLanguage(cursor.requiredAttribute("expressionLanguage"));
    }

    if (cursor.hasAttribute("typeLanguage")) {
      builder.setTypeLanguage(cursor.requiredAttribute("typeLanguage"));
    }

    if (cursor.hasAttribute("exporter")) {
      builder.setExporter(cursor.requiredAttribute("exporter"));
    }

    if (cursor.hasAttribute("exporterVersion")) {
      builder.setExporterVersion(cursor.requiredAttribute("exporterVersion"));
    }

    new DefinitionsBodyReader(context).read(cursor, builder);

    return builder.build();
  }
}
