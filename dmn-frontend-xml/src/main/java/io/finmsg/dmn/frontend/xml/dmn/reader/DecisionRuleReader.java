package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.DecisionRule;
import io.finmsg.dmn.model.RuleAnnotation;
import io.finmsg.dmn.model.UnaryTest;

public final class DecisionRuleReader {

    private final NodeReader nodeReader = new NodeReader();
    private final FeelSourceReader feelSourceReader =
            new FeelSourceReader();

    public DecisionRule read(XmlCursor cursor) {

        DecisionRule.Builder builder = DecisionRule.newBuilder();
        builder.setNode(nodeReader.read(cursor));

        if (cursor.firstChild()) {
            do {
                switch (cursor.localName()) {
                    case "inputEntry" ->
                            builder.addInputEntries(
                                    UnaryTest.newBuilder()
                                            .setExpression(feelSourceReader.read(cursor))
                                            .build());

                    case "outputEntry" ->
                            builder.addOutputEntries(
                                    feelSourceReader.read(cursor));

                    case "annotationEntry" ->
                            builder.addAnnotationEntries(
                                    readAnnotation(cursor));

                    default -> {
                    }
                }

            } while (cursor.nextSibling());
            cursor.parent();
        }
        return builder.build();
    }

    private RuleAnnotation readAnnotation(XmlCursor cursor) {

        RuleAnnotation.Builder builder = RuleAnnotation.newBuilder();

        if (cursor.firstChild("text")) {
            if (cursor.hasText()) {
                builder.setText(cursor.text().trim());
            }
            cursor.parent();
        }
        return builder.build();
    }
}