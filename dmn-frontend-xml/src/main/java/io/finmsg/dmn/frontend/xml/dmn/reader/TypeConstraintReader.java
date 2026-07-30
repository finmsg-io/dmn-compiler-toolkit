package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.FeelText;
import io.finmsg.dmn.model.TypeConstraint;

public final class TypeConstraintReader {

  public TypeConstraint read(XmlCursor cursor) {

    TypeConstraint.Builder builder = TypeConstraint.newBuilder();

    if (cursor.firstChild("text")) {
      if (cursor.hasText()) {
        builder.setText(
            FeelText.newBuilder()
                .setText(cursor.text().trim())
                .build());
      }

      cursor.parent();
    }

    return builder.build();
  }
}
