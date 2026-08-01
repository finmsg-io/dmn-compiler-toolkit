package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.KnowledgeRequirement;

public final class KnowledgeRequirementReader {

  public KnowledgeRequirement read(XmlCursor cursor) {

    KnowledgeRequirement.Builder builder = KnowledgeRequirement.newBuilder();

    if (cursor.firstChild()) {

      do {

        switch (cursor.documentLocalName()) {
          case "requiredKnowledge" -> {
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
}
