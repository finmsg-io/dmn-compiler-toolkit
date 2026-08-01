package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.ItemDefinition;

public final class ItemDefinitionReader {

  private final NodeReader nodeReader = new NodeReader();
  private final TypeReferenceReader typeReferenceReader = new TypeReferenceReader();
  private final ItemComponentReader itemComponentReader = new ItemComponentReader();
  private final TypeConstraintReader typeConstraintReader = new TypeConstraintReader();

  public ItemDefinition read(XmlCursor cursor) {

    ItemDefinition.Builder builder = ItemDefinition.newBuilder();

    builder.setNode(nodeReader.read(cursor));

    if (cursor.hasAttribute("typeRef")) {
      builder.setType(typeReferenceReader.read(cursor.requiredAttribute("typeRef"), cursor));
    }

    if (cursor.hasAttribute("isCollection")) {
      builder.setIsCollection(cursor.attributeAsBoolean("isCollection"));
    }

    if (cursor.firstChild()) {
      do {
        switch (cursor.documentLocalName()) {
          case "typeRef" -> builder.setType(typeReferenceReader.read(cursor.text(), cursor));

          case "itemComponent" -> builder.addComponents(itemComponentReader.read(cursor));

          case "allowedValues", "typeConstraint" ->
              builder.setConstraint(typeConstraintReader.read(cursor));

          case "documentation", "extensionElements" -> { }
          default -> UnsupportedContent.rejectDmnChild(cursor, "itemDefinition");
        }
      } while (cursor.nextSibling());

      cursor.parent();
    }

    return builder.build();
  }
}
