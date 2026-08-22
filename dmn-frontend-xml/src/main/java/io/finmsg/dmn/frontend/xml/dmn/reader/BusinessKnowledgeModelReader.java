package io.finmsg.dmn.frontend.xml.dmn.reader;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.model.BusinessKnowledgeModel;

public final class BusinessKnowledgeModelReader {

	private final NodeReader nodeReader = new NodeReader();
	private final VariableReader variableReader = new VariableReader();
	private final KnowledgeRequirementReader knowledgeRequirementReader = new KnowledgeRequirementReader();
	private final AuthorityRequirementReader authorityRequirementReader = new AuthorityRequirementReader();
	private final FeelReader feelReader = new FeelReader();
	private final FunctionDefinitionReader functionDefinitionReader = new FunctionDefinitionReader();
	private final TypeReferenceReader typeReferenceReader = new TypeReferenceReader();

	public BusinessKnowledgeModel read(XmlCursor cursor) {

		BusinessKnowledgeModel.Builder builder = BusinessKnowledgeModel.newBuilder();
		builder.setNode(nodeReader.read(cursor));
		String encapsulatedTypeRef = null;

		if (cursor.firstChild()) {
			do {
				switch (cursor.documentLocalName()) {

					case "variable" -> builder.setVariable(variableReader.read(cursor));

					case "encapsulatedLogic" -> {
						if (cursor.hasAttribute("typeRef")) {
							encapsulatedTypeRef = cursor.requiredAttribute("typeRef");
						}
						if (cursor.firstChild()) {
							do {
								if (!"formalParameter".equals(cursor.documentLocalName())
										&& cursor.hasAttribute("typeRef") && encapsulatedTypeRef == null) {
									encapsulatedTypeRef = cursor.requiredAttribute("typeRef");
								}
							} while (cursor.nextSibling());
							cursor.parent();
						}
						builder.setFunction(functionDefinitionReader.read(cursor));
					}

					case "knowledgeRequirement" ->
						builder.addKnowledgeRequirements(knowledgeRequirementReader.read(cursor));

					case "authorityRequirement" ->
						builder.addAuthorityRequirements(authorityRequirementReader.read(cursor));

					case "documentation", "description", "extensionElements" -> {
					}

					default -> UnsupportedContent.rejectDmnChild(cursor, "businessKnowledgeModel");
				}

			} while (cursor.nextSibling());

			cursor.parent();
		}

		if (encapsulatedTypeRef != null) {
			if (!builder.hasVariable()) {
				builder.setVariable(io.finmsg.dmn.model.InformationItem.newBuilder()
						.setNode(io.finmsg.dmn.model.Node.newBuilder().setName(builder.getNode().getName()))
						.setType(typeReferenceReader.read(encapsulatedTypeRef, cursor)).build());
			} else if (!builder.getVariable().hasType() || builder.getVariable().getType()
					.getKindCase() == io.finmsg.dmn.model.TypeReference.KindCase.KIND_NOT_SET) {
				builder.setVariable(builder.getVariable().toBuilder()
						.setType(typeReferenceReader.read(encapsulatedTypeRef, cursor)).build());
			}
		}

		return builder.build();
	}
}
