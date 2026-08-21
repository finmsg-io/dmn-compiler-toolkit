package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.frontend.xml.dmn.UnsupportedDmnXmlException;
import io.finmsg.dmn.model.Decision;

public final class DecisionReader {

	private final NodeReader nodeReader = new NodeReader();

	private final VariableReader variableReader = new VariableReader();

	private final InformationRequirementReader informationRequirementReader = new InformationRequirementReader();

	private final KnowledgeRequirementReader knowledgeRequirementReader = new KnowledgeRequirementReader();

	private final AuthorityRequirementReader authorityRequirementReader = new AuthorityRequirementReader();

	private final DecisionLogicReader decisionLogicReader = new DecisionLogicReader();

	public Decision read(XmlCursor cursor) {

		Decision.Builder builder = Decision.newBuilder();

		//
		// DMN element attributes
		//
		builder.setNode(nodeReader.read(cursor));

		if (cursor.firstChild()) {

			do {

				switch (cursor.documentLocalName()) {
					case "variable" -> builder.setVariable(variableReader.read(cursor));

					case "informationRequirement" ->
						builder.addInformationRequirements(informationRequirementReader.read(cursor));

					case "knowledgeRequirement" ->
						builder.addKnowledgeRequirements(knowledgeRequirementReader.read(cursor));

					case "authorityRequirement" ->
						builder.addAuthorityRequirements(authorityRequirementReader.read(cursor));

					//
					// Decision logic
					//
					// DMN 1.6:
					//
					// literalExpression
					// decisionTable
					// invocation
					// context
					// relation
					// list
					// functionDefinition
					//
					//
					case "literalExpression", "decisionTable", "invocation", "context", "relation", "list",
							"functionDefinition", "some", "every", "filter", "conditional", "for" ->
						builder.setLogic(decisionLogicReader.read(cursor));

					//
					// Extension point
					//
					case "documentation", "description", "extensionElements" -> {
						// ignored for now
					}

					//
					// DMN 1.6 metadata extensions
					//
					case "supportedObjective", "impactedPerformanceIndicator", "decisionMaker", "decisionOwner",
							"usingProcess", "usingTask", "question", "allowedAnswers" -> {
						// ignored for now
					}

					default -> {
						if (!cursor.documentLocalName().isEmpty()) {
							throw new UnsupportedDmnXmlException(
									"Unsupported decision child <" + cursor.localName() + "> at " + cursor.path());
						}
					}
				}

			} while (cursor.nextSibling());

			cursor.parent();
		}

		return builder.build();
	}
}
