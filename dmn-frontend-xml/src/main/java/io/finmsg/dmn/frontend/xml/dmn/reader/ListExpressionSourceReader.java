package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.ListExpressionSource;

public final class ListExpressionSourceReader {

    private final ExpressionSourceReader expressionSourceReader =
            new ExpressionSourceReader();

    public ListExpressionSource read(XmlCursor cursor) {

        ListExpressionSource.Builder builder =
                ListExpressionSource.newBuilder();

        if (cursor.firstChild()) {
            do {

                switch (cursor.localName()) {

                    case "literalExpression",
                         "context",
                         "relation",
                         "list",
                         "functionDefinition" ->
                            builder.addElements(
                                    expressionSourceReader.read(cursor));

                    //
                    // Extension points
                    //
                    case "extensionElements" -> {}

                    default -> {
                        // ignore unknown elements
                    }
                }

            } while (cursor.nextSibling());

            cursor.parent();
        }

        return builder.build();
    }
}