package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.FunctionDefinition;
import io.finmsg.dmn.model.FunctionKind;

public final class FunctionDefinitionReader {

	private final InformationItemReader informationItemReader = new InformationItemReader();
	private final FeelReader feelReader = new FeelReader();

	public FunctionDefinition read(XmlCursor cursor) {
		FunctionDefinition.Builder builder = FunctionDefinition.newBuilder();

		//
		// kind
		//

		if (cursor.hasAttribute("kind")) {
			builder.setKind(toFunctionKind(cursor.requiredAttribute("kind")));
		} else {
			builder.setKind(FunctionKind.FUNCTION_KIND_FEEL);
		}

		if (cursor.firstChild()) {
			do {
				switch (cursor.documentLocalName()) {
					case "formalParameter" -> builder.addFormalParameters(informationItemReader.read(cursor));

					case "literalExpression", "context", "relation", "list", "decisionTable", "invocation", "some",
							"every", "filter" ->
						builder.setLogic(feelReader.read(cursor));

					case "functionDefinition" -> builder.setLogic(feelReader.read(cursor));

					case "documentation", "description", "extensionElements" -> {
					}
					default -> UnsupportedContent.rejectDmnChild(cursor, "functionDefinition");
				}
			} while (cursor.nextSibling());

			cursor.parent();
		}
		return builder.build();
	}

	private static FunctionKind toFunctionKind(String kind) {

		return switch (kind.toLowerCase()) {
			case "feel" -> FunctionKind.FUNCTION_KIND_FEEL;
			case "java" -> FunctionKind.FUNCTION_KIND_JAVA;
			case "pmml" -> FunctionKind.FUNCTION_KIND_PMML;
			default -> FunctionKind.FUNCTION_KIND_UNSPECIFIED;
		};
	}
}
