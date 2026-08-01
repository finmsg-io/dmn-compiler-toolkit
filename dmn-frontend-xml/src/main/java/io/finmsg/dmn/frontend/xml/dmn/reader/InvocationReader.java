package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.Binding;
import io.finmsg.dmn.model.Invocation;

public final class InvocationReader {

  private final FeelReader feelReader = new FeelReader();

  public Invocation read(XmlCursor cursor) {

    Invocation.Builder builder = Invocation.newBuilder();

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {
          case "expression", "literalExpression" -> {
            builder.setExpression(feelReader.read(cursor)).build();
          }

          case "binding" -> {
            builder.addBindings(readBinding(cursor));
          }

          case "extensionElements" -> { }
          default -> UnsupportedContent.rejectDmnChild(cursor, "invocation");
        }

      } while (cursor.nextSibling());
      cursor.parent();
    }
    return builder.build();
  }

  private Binding readBinding(XmlCursor cursor) {

    Binding.Builder builder = Binding.newBuilder();

    if (cursor.firstChild()) {

      do {
        switch (cursor.documentLocalName()) {
          case "parameter" -> builder.setParameter(readParameterName(cursor));

          case "expression", "literalExpression" -> {
            builder.setExpression(feelReader.read(cursor)).build();
          }

          default -> UnsupportedContent.rejectDmnChild(cursor, "invocation binding");
        }
      } while (cursor.nextSibling());
      cursor.parent();
    }
    return builder.build();
  }

  private String readParameterName(XmlCursor cursor) {

    if (cursor.hasAttribute("name")) {
      return cursor.requiredAttribute("name");
    }

    if (!cursor.firstChild()) {
      return "";
    }

    String name = "";
    do {
      if ("name".equals(cursor.documentLocalName())) {
        name = cursor.text();
        break;
      }

    } while (cursor.nextSibling());
    cursor.parent();
    return name;
  }
}
