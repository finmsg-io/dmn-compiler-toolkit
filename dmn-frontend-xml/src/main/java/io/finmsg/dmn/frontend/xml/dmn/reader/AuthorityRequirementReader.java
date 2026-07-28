package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.AuthorityRequirement;

public final class AuthorityRequirementReader {

  public AuthorityRequirement read(XmlCursor cursor) {

    AuthorityRequirement.Builder builder = AuthorityRequirement.newBuilder();

    if (cursor.firstChild()) {

      do {

        switch (cursor.localName()) {
          case "requiredAuthority" -> {
            // later
          }

          case "requiredDecision" -> {
            // later
          }

          case "requiredInput" -> {
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
