package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.DecisionService;
import io.finmsg.dmn.model.ElementReference;

public final class DecisionServiceReader {

  private final NodeReader nodeReader = new NodeReader();

  public DecisionService read(XmlCursor cursor) {

    DecisionService.Builder builder = DecisionService.newBuilder();

    builder.setNode(nodeReader.read(cursor));

    if (cursor.firstChild()) {

      do {

        switch (cursor.documentLocalName()) {
          case "outputDecision" ->
              builder.addOutputDecisions(reference(cursor));

          case "encapsulatedDecision" ->
              builder.addEncapsulatedDecisions(reference(cursor));

          case "inputDecision" ->
              builder.addInputDecisions(reference(cursor));

          case "inputData" -> builder.addInputData(reference(cursor));

          case "extensionElements" -> {
            // later
          }

          default -> {
            // ignore unknown children
          }
        }

      } while (cursor.nextSibling());

      cursor.parent();
    }

    return builder.build();
  }

  private static ElementReference reference(XmlCursor cursor) {
    return ElementReference.newBuilder().setHref(cursor.requiredAttribute("href")).build();
  }
}
