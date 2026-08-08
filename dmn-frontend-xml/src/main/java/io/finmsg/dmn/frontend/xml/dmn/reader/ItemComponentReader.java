package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.ItemComponent;

public final class ItemComponentReader {

  private final NodeReader nodeReader = new NodeReader();
  private final TypeReferenceReader typeReferenceReader = new TypeReferenceReader();
  private final TypeConstraintReader typeConstraintReader = new TypeConstraintReader();

  public ItemComponent read(XmlCursor cursor) {

    ItemComponent.Builder builder = ItemComponent.newBuilder();

    builder.setNode(nodeReader.read(cursor));

    if (cursor.hasAttribute("isCollection")) {
      builder.setIsCollection(cursor.attributeAsBoolean("isCollection"));
    }

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {
          case "typeRef" -> builder.setType(typeReferenceReader.read(cursor.text(), cursor));

          case "allowedValues", "typeConstraint" ->
              builder.setConstraint(typeConstraintReader.read(cursor));

          case "itemComponent" -> builder.addComponents(this.read(cursor));

          case "documentation", "description", "extensionElements" -> { }
          default -> UnsupportedContent.rejectDmnChild(cursor, "itemComponent");
        }
      } while (cursor.nextSibling());

      cursor.parent();
    }

    return builder.build();
  }
}
