package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.KnowledgeSource;

public final class KnowledgeSourceReader {

  private final NodeReader nodeReader = new NodeReader();

  private final AuthorityRequirementReader authorityRequirementReader =
      new AuthorityRequirementReader();

  public KnowledgeSource read(XmlCursor cursor) {

    KnowledgeSource.Builder builder = KnowledgeSource.newBuilder();

    builder.setNode(nodeReader.read(cursor));

    if (cursor.firstChild()) {

      do {

        switch (cursor.localName()) {
          case "authorityRequirement" -> {
            // TODO
          }

          case "owner" -> {
            // later
          }

          case "locationURI" -> {
            // later
          }

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
}
