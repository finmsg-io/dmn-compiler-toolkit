package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.ElementReference;
import io.finmsg.dmn.model.InformationRequirement;

public final class InformationRequirementReader {

  public InformationRequirement read(XmlCursor cursor) {

    InformationRequirement.Builder builder = InformationRequirement.newBuilder();

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {
          case "requiredInput" ->
              builder.setInput(
                  ElementReference.newBuilder().setHref(cursor.attribute("href").get()).build());

          case "requiredDecision" ->
              builder.setDecision(
                  ElementReference.newBuilder().setHref(cursor.attribute("href").get()).build());

          default -> {}
        }
      } while (cursor.nextSibling());
      cursor.parent();
    }
    return builder.build();
  }
}
