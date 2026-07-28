package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.TypeConstraint;

public final class TypeConstraintReader {

  public TypeConstraint read(XmlCursor cursor) {

    TypeConstraint.Builder builder = TypeConstraint.newBuilder();

    if (cursor.firstChild("text")) {
      if (cursor.hasText()) {
        builder.setExpression(cursor.text().trim());
      }

      cursor.parent();
    }

    return builder.build();
  }
}
