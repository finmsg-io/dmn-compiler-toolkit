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
        switch (cursor.localName()) {
          case "expression" -> {
            builder.setExpression(feelReader.read(cursor)).build();
          }

          case "binding" -> {
            builder.addBindings(readBinding(cursor));
          }

          default -> {}
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
        switch (cursor.localName()) {
          case "parameter" -> {
            builder.setParameter(readParameterName(cursor));
          }

          case "expression" -> {
            builder.setExpression(feelReader.read(cursor)).build();
          }

          default -> {}
        }
      } while (cursor.nextSibling());
      cursor.parent();
    }
    return builder.build();
  }

  private String readParameterName(XmlCursor cursor) {

    if (!cursor.firstChild()) {
      return "";
    }

    String name = "";
    do {
      if ("name".equals(cursor.localName())) {
        name = cursor.text();
        break;
      }

    } while (cursor.nextSibling());
    cursor.parent();
    return name;
  }
}
