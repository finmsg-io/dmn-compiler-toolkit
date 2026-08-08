package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.DecisionRule;
import io.finmsg.dmn.model.RuleAnnotation;
import io.finmsg.dmn.model.UnaryTest;

public final class DecisionRuleReader {

	private final NodeReader nodeReader = new NodeReader();
	private final FeelReader feelReader = new FeelReader();

	public DecisionRule read(XmlCursor cursor) {

		DecisionRule.Builder builder = DecisionRule.newBuilder();
		builder.setNode(nodeReader.read(cursor));

		if (cursor.firstChild()) {
			do {
				switch (cursor.documentLocalName()) {
					case "inputEntry" ->
						builder.addInputEntries(UnaryTest.newBuilder().setText(feelReader.readText(cursor)).build());

					case "outputEntry" -> builder.addOutputEntries(feelReader.read(cursor));

					case "annotationEntry" -> builder.addAnnotationEntries(readAnnotation(cursor));

					case "documentation", "extensionElements" -> {
					}
					default -> UnsupportedContent.rejectDmnChild(cursor, "decision rule");
				}

			} while (cursor.nextSibling());
			cursor.parent();
		}
		return builder.build();
	}

	private RuleAnnotation readAnnotation(XmlCursor cursor) {

		RuleAnnotation.Builder builder = RuleAnnotation.newBuilder();

		if (cursor.firstChild()) {
			do {
				if ("text".equals(cursor.documentLocalName())) {
					if (cursor.hasText()) {
						builder.setText(cursor.text().trim());
					}
				} else {
					UnsupportedContent.rejectDmnChild(cursor, "annotationEntry");
				}
			} while (cursor.nextSibling());
			cursor.parent();
		}
		return builder.build();
	}
}
