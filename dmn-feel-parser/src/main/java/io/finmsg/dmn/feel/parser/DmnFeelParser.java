package io.finmsg.dmn.feel.parser;

import io.finmsg.dmn.model.Binding;
import io.finmsg.dmn.model.BoxedExpression;
import io.finmsg.dmn.model.BoxedExpressionParsed;
import io.finmsg.dmn.model.BoxedExpressionText;
import io.finmsg.dmn.model.BusinessKnowledgeModel;
import io.finmsg.dmn.model.ContextEntryParsed;
import io.finmsg.dmn.model.ContextParsed;
import io.finmsg.dmn.model.ContextText;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.model.DecisionRule;
import io.finmsg.dmn.model.DecisionTable;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.Expression;
import io.finmsg.dmn.model.ExpressionNode;
import io.finmsg.dmn.model.ExpressionParsed;
import io.finmsg.dmn.model.ExpressionText;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.FeelParsed;
import io.finmsg.dmn.model.FeelText;
import io.finmsg.dmn.model.FunctionDefinition;
import io.finmsg.dmn.model.FunctionDefinitionParsed;
import io.finmsg.dmn.model.FunctionDefinitionText;
import io.finmsg.dmn.model.FunctionKind;
import io.finmsg.dmn.model.InputClause;
import io.finmsg.dmn.model.Invocation;
import io.finmsg.dmn.model.ItemComponent;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.ListExpressionParsed;
import io.finmsg.dmn.model.ListExpressionText;
import io.finmsg.dmn.model.OutputClause;
import io.finmsg.dmn.model.RelationColumnParsed;
import io.finmsg.dmn.model.RelationParsed;
import io.finmsg.dmn.model.RelationRowParsed;
import io.finmsg.dmn.model.RelationText;
import io.finmsg.dmn.model.SourceLocation;
import io.finmsg.dmn.model.TypeConstraint;
import io.finmsg.dmn.model.UnaryTest;
import io.finmsg.dmn.model.UnaryTestParsed;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Parses every textual FEEL node in a DMN protobuf model.
 *
 * <p>
 * The input model is never changed. The returned model is a copied protobuf
 * tree in which textual FEEL representations are replaced with parsed
 * representations.
 */
public final class DmnFeelParser {

	private final FeelParserFacade parser;
	private final ThreadLocal<ParseSession> currentSession = new ThreadLocal<>();

	public DmnFeelParser() {
		this(new FeelParserFacade());
	}

	DmnFeelParser(FeelParserFacade parser) {
		this.parser = Objects.requireNonNull(parser, "parser");
	}

	public Definitions parse(Definitions semanticModel) {
		DmnFeelParseResult result = parseWithDiagnostics(semanticModel);
		if (!result.isSuccess()) {
			throw new DmnFeelParseException(result.diagnostics());
		}
		return result.model();
	}

	public DmnFeelParseResult parseWithDiagnostics(Definitions semanticModel) {
		Objects.requireNonNull(semanticModel, "semanticModel");

		ParseSession previous = currentSession.get();
		ParseSession session = new ParseSession();
		currentSession.set(session);
		try {
			return new DmnFeelParseResult(parseModel(semanticModel), session.diagnostics);
		} finally {
			if (previous == null) {
				currentSession.remove();
			} else {
				currentSession.set(previous);
			}
		}
	}

	private Definitions parseModel(Definitions semanticModel) {

		Definitions.Builder parsed = semanticModel.toBuilder();

		for (int i = 0; i < semanticModel.getItemDefinitionsCount(); i++) {
			ItemDefinition value = semanticModel.getItemDefinitions(i);
			parsed.setItemDefinitions(i, at("itemDefinition[" + displayName(value.getNode().getName(), i) + "]",
					value.getNode().getSourceLocation(), () -> parseItemDefinition(value)));
		}

		for (int i = 0; i < semanticModel.getDrgElementsCount(); i++) {
			DrgElement value = semanticModel.getDrgElements(i);
			String name = switch (value.getElementCase()) {
				case DECISION -> value.getDecision().getNode().getName();
				case BUSINESS_KNOWLEDGE_MODEL -> value.getBusinessKnowledgeModel().getNode().getName();
				case INPUT_DATA -> value.getInputData().getNode().getName();
				case KNOWLEDGE_SOURCE -> value.getKnowledgeSource().getNode().getName();
				case DECISION_SERVICE -> value.getDecisionService().getNode().getName();
				case ELEMENT_NOT_SET -> "unknown";
			};
			SourceLocation location = switch (value.getElementCase()) {
				case DECISION -> value.getDecision().getNode().getSourceLocation();
				case BUSINESS_KNOWLEDGE_MODEL -> value.getBusinessKnowledgeModel().getNode().getSourceLocation();
				case INPUT_DATA -> value.getInputData().getNode().getSourceLocation();
				case KNOWLEDGE_SOURCE -> value.getKnowledgeSource().getNode().getSourceLocation();
				case DECISION_SERVICE -> value.getDecisionService().getNode().getSourceLocation();
				case ELEMENT_NOT_SET -> SourceLocation.getDefaultInstance();
			};
			parsed.setDrgElements(i,
					at("drgElement[" + displayName(name, i) + "]", location, () -> parseDrgElement(value)));
		}

		return parsed.build();
	}

	private ItemDefinition parseItemDefinition(ItemDefinition value) {
		ItemDefinition.Builder parsed = value.toBuilder();

		for (int i = 0; i < value.getComponentsCount(); i++) {
			ItemComponent component = value.getComponents(i);
			parsed.setComponents(i, at("component[" + displayName(component.getNode().getName(), i) + "]",
					component.getNode().getSourceLocation(), () -> parseItemComponent(component)));
		}

		if (value.hasConstraint()) {
			parsed.setConstraint(at("typeConstraint", () -> parseTypeConstraint(value.getConstraint())));
		}

		return parsed.build();
	}

	private ItemComponent parseItemComponent(ItemComponent value) {
		ItemComponent.Builder parsed = value.toBuilder();
		if (value.hasConstraint()) {
			parsed.setConstraint(at("typeConstraint", () -> parseTypeConstraint(value.getConstraint())));
		}
		return parsed.build();
	}

	private TypeConstraint parseTypeConstraint(TypeConstraint value) {
		return switch (value.getRepresentationCase()) {
			case TEXT -> {
				UnaryTestParsed parsed = safelyParse(value.getText().getText(), () -> parseUnaryTests(value.getText()));
				yield parsed == null ? value : value.toBuilder().setParsed(parsed).build();
			}
			case PARSED, REPRESENTATION_NOT_SET -> value;
		};
	}

	private DrgElement parseDrgElement(DrgElement value) {
		return switch (value.getElementCase()) {
			case DECISION -> value.toBuilder().setDecision(parseDecision(value.getDecision())).build();
			case BUSINESS_KNOWLEDGE_MODEL -> value.toBuilder()
					.setBusinessKnowledgeModel(parseBusinessKnowledgeModel(value.getBusinessKnowledgeModel())).build();
			case INPUT_DATA, KNOWLEDGE_SOURCE, DECISION_SERVICE, ELEMENT_NOT_SET -> value;
		};
	}

	private Decision parseDecision(Decision value) {
		Decision.Builder parsed = value.toBuilder();
		if (value.hasLogic()) {
			parsed.setLogic(at("logic", () -> parseDecisionLogic(value.getLogic())));
		}
		return parsed.build();
	}

	private DecisionLogic parseDecisionLogic(DecisionLogic value) {
		return switch (value.getTypeCase()) {
			case LITERAL_EXPRESSION -> value.toBuilder()
					.setLiteralExpression(at("literalExpression", () -> parseFeel(value.getLiteralExpression())))
					.build();
			case DECISION_TABLE -> value.toBuilder()
					.setDecisionTable(at("decisionTable", value.getDecisionTable().getNode().getSourceLocation(),
							() -> parseDecisionTable(value.getDecisionTable())))
					.build();
			case BOXED_EXPRESSION -> value.toBuilder()
					.setBoxedExpression(at("boxedExpression", () -> parseBoxedExpression(value.getBoxedExpression())))
					.build();
			case INVOCATION ->
				value.toBuilder().setInvocation(at("invocation", () -> parseInvocation(value.getInvocation()))).build();
			case TYPE_NOT_SET -> value;
		};
	}

	private BusinessKnowledgeModel parseBusinessKnowledgeModel(BusinessKnowledgeModel value) {
		BusinessKnowledgeModel.Builder parsed = value.toBuilder();
		if (value.hasFunction()) {
			parsed.setFunction(at("functionDefinition", () -> parseFunctionDefinition(value.getFunction())));
		}
		return parsed.build();
	}

	private FunctionDefinition parseFunctionDefinition(FunctionDefinition value) {
		FunctionDefinition.Builder parsed = value.toBuilder();
		if (value.hasLogic() && value.getKind() != FunctionKind.FUNCTION_KIND_JAVA
				&& value.getKind() != FunctionKind.FUNCTION_KIND_PMML) {
			parsed.setLogic(at("logic", () -> parseFeel(value.getLogic())));
		}
		return parsed.build();
	}

	private Invocation parseInvocation(Invocation value) {
		Invocation.Builder parsed = value.toBuilder();

		if (value.hasExpression()) {
			parsed.setExpression(at("expression", () -> parseFeel(value.getExpression())));
		}

		for (int i = 0; i < value.getBindingsCount(); i++) {
			int index = i;
			parsed.setBindings(i, at("binding[" + i + "]", () -> parseBinding(value.getBindings(index))));
		}

		return parsed.build();
	}

	private Binding parseBinding(Binding value) {
		Binding.Builder parsed = value.toBuilder();
		if (value.hasExpression()) {
			parsed.setExpression(at("expression", () -> parseFeel(value.getExpression())));
		}
		return parsed.build();
	}

	private DecisionTable parseDecisionTable(DecisionTable value) {
		DecisionTable.Builder parsed = value.toBuilder();

		for (int i = 0; i < value.getInputsCount(); i++) {
			int index = i;
			parsed.setInputs(i, at("input[" + i + "]", value.getInputs(i).getNode().getSourceLocation(),
					() -> parseInputClause(value.getInputs(index))));
		}

		for (int i = 0; i < value.getOutputsCount(); i++) {
			int index = i;
			parsed.setOutputs(i, at("output[" + i + "]", value.getOutputs(i).getNode().getSourceLocation(),
					() -> parseOutputClause(value.getOutputs(index))));
		}

		for (int i = 0; i < value.getRulesCount(); i++) {
			int index = i;
			parsed.setRules(i, at("rule[" + i + "]", value.getRules(i).getNode().getSourceLocation(),
					() -> parseDecisionRule(value.getRules(index))));
		}

		return parsed.build();
	}

	private InputClause parseInputClause(InputClause value) {
		InputClause.Builder parsed = value.toBuilder();
		if (value.hasInputExpression()) {
			parsed.setInputExpression(at("inputExpression", () -> parseFeel(value.getInputExpression())));
		}
		if (value.hasInputValues()) {
			parsed.setInputValues(at("inputValues", () -> parseFeelUnaryTests(value.getInputValues())));
		}
		return parsed.build();
	}

	private OutputClause parseOutputClause(OutputClause value) {
		OutputClause.Builder parsed = value.toBuilder();
		if (value.hasOutputValues()) {
			parsed.setOutputValues(at("outputValues", () -> parseFeelUnaryTests(value.getOutputValues())));
		}
		if (value.hasDefaultOutputEntry()) {
			parsed.setDefaultOutputEntry(
					at("defaultOutputEntry", () -> parseExpressionNode(value.getDefaultOutputEntry())));
		}
		return parsed.build();
	}

	private DecisionRule parseDecisionRule(DecisionRule value) {
		DecisionRule.Builder parsed = value.toBuilder();

		for (int i = 0; i < value.getInputEntriesCount(); i++) {
			int index = i;
			parsed.setInputEntries(i, at("inputEntry[" + i + "]", () -> parseUnaryTest(value.getInputEntries(index))));
		}

		for (int i = 0; i < value.getOutputEntriesCount(); i++) {
			int index = i;
			parsed.setOutputEntries(i, at("outputEntry[" + i + "]", () -> parseFeel(value.getOutputEntries(index))));
		}

		return parsed.build();
	}

	private UnaryTest parseUnaryTest(UnaryTest value) {
		return switch (value.getRepresentationCase()) {
			case TEXT -> {
				UnaryTestParsed parsed = safelyParse(value.getText().getText(), () -> parseUnaryTests(value.getText()));
				yield parsed == null ? value : value.toBuilder().setParsed(parsed).build();
			}
			case PARSED, REPRESENTATION_NOT_SET -> value;
		};
	}

	private Feel parseFeel(Feel value) {
		return switch (value.getRepresentationCase()) {
			case TEXT -> {
				FeelParsed parsed = safelyParse(value.getText().getText(),
						() -> parser.parseExpressionAst(value.getText().getText()));
				yield parsed == null ? value : value.toBuilder().setParsed(parsed).build();
			}
			case PARSED, REPRESENTATION_NOT_SET -> value;
		};
	}

	private Feel parseFeelUnaryTests(Feel value) {
		return switch (value.getRepresentationCase()) {
			case TEXT -> {
				UnaryTestParsed unaryTests = safelyParse(value.getText().getText(),
						() -> parseUnaryTests(value.getText()));
				if (unaryTests == null) {
					yield value;
				}
				FeelParsed parsed = FeelParsed.newBuilder()
						.setAst(Expression.newBuilder().setUnaryTests(unaryTests.getTests())).build();
				yield value.toBuilder().setParsed(parsed).build();
			}
			case PARSED, REPRESENTATION_NOT_SET -> value;
		};
	}

	private UnaryTestParsed parseUnaryTests(FeelText value) {
		return parser.parseUnaryTestsAst(value.getText());
	}

	private ExpressionNode parseExpressionNode(ExpressionNode value) {
		return switch (value.getRepresentationCase()) {
			case TEXT -> {
				int before = currentSession.get().diagnostics.size();
				ExpressionParsed parsed = parseExpressionText(value.getText());
				yield parsed == null || currentSession.get().diagnostics.size() != before
						? value
						: value.toBuilder().setParsed(parsed).build();
			}
			case PARSED, REPRESENTATION_NOT_SET -> value;
		};
	}

	private ExpressionParsed parseExpressionText(ExpressionText value) {
		return switch (value.getTypeCase()) {
			case FEEL -> parseFeelExpressionText(value.getFeel());
			case BOXED -> ExpressionParsed.newBuilder().setBoxed(parseBoxedExpressionText(value.getBoxed())).build();
			case TYPE_NOT_SET -> ExpressionParsed.getDefaultInstance();
		};
	}

	private BoxedExpression parseBoxedExpression(BoxedExpression value) {
		return switch (value.getRepresentationCase()) {
			case TEXT -> {
				int before = currentSession.get().diagnostics.size();
				BoxedExpressionParsed parsed = parseBoxedExpressionText(value.getText());
				yield currentSession.get().diagnostics.size() != before
						? value
						: value.toBuilder().setParsed(parsed).build();
			}
			case PARSED, REPRESENTATION_NOT_SET -> value;
		};
	}

	private BoxedExpressionParsed parseBoxedExpressionText(BoxedExpressionText value) {
		return switch (value.getTypeCase()) {
			case CONTEXT -> BoxedExpressionParsed.newBuilder().setContext(parseContext(value.getContext())).build();
			case RELATION -> BoxedExpressionParsed.newBuilder().setRelation(parseRelation(value.getRelation())).build();
			case LIST -> BoxedExpressionParsed.newBuilder().setList(parseList(value.getList())).build();
			case FUNCTION_DEFINITION -> BoxedExpressionParsed.newBuilder()
					.setFunctionDefinition(parseFunctionDefinitionText(value.getFunctionDefinition())).build();
			case TYPE_NOT_SET -> BoxedExpressionParsed.getDefaultInstance();
		};
	}

	private ContextParsed parseContext(ContextText value) {
		ContextParsed.Builder parsed = ContextParsed.newBuilder();
		for (int i = 0; i < value.getEntriesCount(); i++) {
			var entry = value.getEntries(i);
			ContextEntryParsed.Builder parsedEntry = ContextEntryParsed.newBuilder().setVariable(entry.getVariable());
			if (entry.hasExpression()) {
				int index = i;
				ExpressionParsed expression = at("contextEntry[" + i + "]/expression",
						() -> parseExpressionText(value.getEntries(index).getExpression()));
				if (expression != null) {
					parsedEntry.setExpression(expression);
				}
			}
			parsed.addEntries(parsedEntry);
		}
		return parsed.build();
	}

	private RelationParsed parseRelation(RelationText value) {
		RelationParsed.Builder parsed = RelationParsed.newBuilder();

		for (var column : value.getColumnsList()) {
			parsed.addColumns(RelationColumnParsed.newBuilder().setVariable(column.getVariable()));
		}

		for (int rowIndex = 0; rowIndex < value.getRowsCount(); rowIndex++) {
			var row = value.getRows(rowIndex);
			RelationRowParsed.Builder parsedRow = RelationRowParsed.newBuilder();
			for (int columnIndex = 0; columnIndex < row.getExpressionsCount(); columnIndex++) {
				int rowPosition = rowIndex;
				int columnPosition = columnIndex;
				ExpressionParsed expression = at("row[" + rowIndex + "]/cell[" + columnIndex + "]",
						() -> parseExpressionText(value.getRows(rowPosition).getExpressions(columnPosition)));
				if (expression != null) {
					parsedRow.addExpressions(expression);
				}
			}
			parsed.addRows(parsedRow);
		}

		return parsed.build();
	}

	private ListExpressionParsed parseList(ListExpressionText value) {
		ListExpressionParsed.Builder parsed = ListExpressionParsed.newBuilder();
		for (int i = 0; i < value.getElementsCount(); i++) {
			int index = i;
			ExpressionParsed element = at("element[" + i + "]", () -> parseExpressionText(value.getElements(index)));
			if (element != null) {
				parsed.addElements(element);
			}
		}
		return parsed.build();
	}

	private FunctionDefinitionParsed parseFunctionDefinitionText(FunctionDefinitionText value) {
		FunctionDefinitionParsed.Builder parsed = FunctionDefinitionParsed.newBuilder()
				.addAllParameters(value.getParametersList());
		if (value.hasBody()) {
			ExpressionParsed body = at("body", () -> parseExpressionText(value.getBody()));
			if (body != null) {
				parsed.setBody(body);
			}
		}
		return parsed.build();
	}

	private ExpressionParsed parseFeelExpressionText(FeelText value) {
		FeelParsed parsed = safelyParse(value.getText(), () -> parser.parseExpressionAst(value.getText()));
		return parsed == null ? null : ExpressionParsed.newBuilder().setFeel(parsed).build();
	}

	private <T> T safelyParse(String source, Supplier<T> action) {
		try {
			return action.get();
		} catch (FeelParseException exception) {
			addDiagnostic(source, exception);
			return null;
		}
	}

	private void addDiagnostic(String source, FeelParseException exception) {
		ParseSession session = currentSession.get();
		String path = session.pendingPath != null ? session.pendingPath : session.path;
		SourceLocation location = session.pendingLocation != null ? session.pendingLocation : session.location;
		session.diagnostics.add(new DmnFeelDiagnostic(path, source, location, exception.diagnostics()));
		session.pendingPath = null;
		session.pendingLocation = null;
	}

	private <T> T at(String segment, Supplier<T> action) {
		return at(segment, SourceLocation.getDefaultInstance(), action);
	}

	private <T> T at(String segment, SourceLocation location, Supplier<T> action) {
		ParseSession session = currentSession.get();
		String previousPath = session.path;
		SourceLocation previousLocation = session.location;
		session.path = previousPath + "/" + segment;
		if (hasLocation(location)) {
			session.location = location;
		}
		try {
			return action.get();
		} catch (FeelParseException exception) {
			if (session.pendingPath == null) {
				session.pendingPath = session.path;
				session.pendingLocation = session.location;
			}
			throw exception;
		} finally {
			session.path = previousPath;
			session.location = previousLocation;
		}
	}

	private static boolean hasLocation(SourceLocation location) {
		return !location.getSystemId().isEmpty() || location.getLine() != 0 || location.getColumn() != 0
				|| location.getOffset() != 0 || location.getLength() != 0;
	}

	private static String displayName(String name, int index) {
		return name.isBlank() ? Integer.toString(index) : name;
	}

	private static final class ParseSession {
		private final List<DmnFeelDiagnostic> diagnostics = new ArrayList<>();
		private String path = "definitions";
		private SourceLocation location = SourceLocation.getDefaultInstance();
		private String pendingPath;
		private SourceLocation pendingLocation;
	}
}
