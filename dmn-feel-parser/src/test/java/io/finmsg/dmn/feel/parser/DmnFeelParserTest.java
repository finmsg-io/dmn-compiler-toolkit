package io.finmsg.dmn.feel.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.model.Binding;
import io.finmsg.dmn.model.BoxedExpression;
import io.finmsg.dmn.model.BoxedExpressionText;
import io.finmsg.dmn.model.BusinessKnowledgeModel;
import io.finmsg.dmn.model.ContextEntryText;
import io.finmsg.dmn.model.ContextText;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.model.DecisionRule;
import io.finmsg.dmn.model.DecisionTable;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.ExpressionNode;
import io.finmsg.dmn.model.ExpressionText;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.FeelText;
import io.finmsg.dmn.model.FunctionDefinition;
import io.finmsg.dmn.model.FunctionKind;
import io.finmsg.dmn.model.InputClause;
import io.finmsg.dmn.model.Invocation;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.OutputClause;
import io.finmsg.dmn.model.TypeConstraint;
import io.finmsg.dmn.model.UnaryTest;
import org.junit.jupiter.api.Test;

class DmnFeelParserTest {

	private final DmnFeelParser parser = new DmnFeelParser();

	@Test
	void parsesDecisionTableAndKeepsSemanticModelUnchanged() {
		DecisionTable table = DecisionTable.newBuilder()
				.addInputs(InputClause.newBuilder().setInputExpression(feel("Applicant.age"))
						.setInputValues(feel("[0..17]")))
				.addOutputs(OutputClause.newBuilder().setOutputValues(feel("\"minor\", \"adult\""))
						.setDefaultOutputEntry(expressionNode("\"unknown\"")))
				.addRules(DecisionRule.newBuilder().addInputEntries(unaryTest("< 18"))
						.addOutputEntries(feel("\"minor\"")))
				.build();

		Definitions semanticModel = Definitions.newBuilder()
				.addItemDefinitions(ItemDefinition.newBuilder()
						.setConstraint(TypeConstraint.newBuilder().setText(text("[0..120]"))))
				.addDrgElements(DrgElement.newBuilder().setDecision(
						Decision.newBuilder().setLogic(DecisionLogic.newBuilder().setDecisionTable(table))))
				.build();

		Definitions parsedModel = parser.parse(semanticModel);

		assertThat(semanticModel.getItemDefinitions(0).getConstraint().hasText()).isTrue();
		assertThat(semanticModel.getDrgElements(0).getDecision().getLogic().getDecisionTable().getInputs(0)
				.getInputExpression().hasText()).isTrue();

		assertThat(parsedModel).isNotSameAs(semanticModel);
		assertThat(parsedModel.getItemDefinitions(0).getConstraint().hasParsed()).isTrue();

		DecisionTable parsedTable = parsedModel.getDrgElements(0).getDecision().getLogic().getDecisionTable();

		assertThat(parsedTable.getInputs(0).getInputExpression().hasParsed()).isTrue();
		assertThat(parsedTable.getInputs(0).getInputValues().getParsed().getAst().hasUnaryTests()).isTrue();
		assertThat(parsedTable.getOutputs(0).getOutputValues().getParsed().getAst().getUnaryTests().getTestsCount())
				.isEqualTo(2);
		assertThat(parsedTable.getOutputs(0).getDefaultOutputEntry().hasParsed()).isTrue();
		assertThat(parsedTable.getRules(0).getInputEntries(0).hasParsed()).isTrue();
		assertThat(parsedTable.getRules(0).getOutputEntries(0).hasParsed()).isTrue();

		assertThat(parser.parse(parsedModel)).isEqualTo(parsedModel);
	}

	@Test
	void parsesBoxedExpressionsInvocationsAndBkmLogic() {
		BoxedExpression boxedExpression = BoxedExpression.newBuilder()
				.setText(BoxedExpressionText.newBuilder()
						.setContext(ContextText.newBuilder().addEntries(
								ContextEntryText.newBuilder().setExpression(expressionText("Applicant.age + 1")))))
				.build();

		Invocation invocation = Invocation.newBuilder().setExpression(feel("Risk"))
				.addBindings(Binding.newBuilder().setParameter("age").setExpression(feel("Applicant.age"))).build();

		Definitions semanticModel = Definitions.newBuilder()
				.addDrgElements(DrgElement.newBuilder().setDecision(
						Decision.newBuilder().setLogic(DecisionLogic.newBuilder().setBoxedExpression(boxedExpression))))
				.addDrgElements(DrgElement.newBuilder().setDecision(
						Decision.newBuilder().setLogic(DecisionLogic.newBuilder().setInvocation(invocation))))
				.addDrgElements(
						DrgElement.newBuilder()
								.setBusinessKnowledgeModel(BusinessKnowledgeModel.newBuilder()
										.setFunction(FunctionDefinition.newBuilder()
												.setKind(FunctionKind.FUNCTION_KIND_FEEL).setLogic(feel("x + 1")))))
				.build();

		Definitions parsedModel = parser.parse(semanticModel);

		assertThat(parsedModel.getDrgElements(0).getDecision().getLogic().getBoxedExpression().hasParsed()).isTrue();
		assertThat(parsedModel.getDrgElements(0).getDecision().getLogic().getBoxedExpression().getParsed().getContext()
				.getEntries(0).getExpression().hasFeel()).isTrue();

		Invocation parsedInvocation = parsedModel.getDrgElements(1).getDecision().getLogic().getInvocation();
		assertThat(parsedInvocation.getExpression().hasParsed()).isTrue();
		assertThat(parsedInvocation.getBindings(0).getExpression().hasParsed()).isTrue();

		assertThat(parsedModel.getDrgElements(2).getBusinessKnowledgeModel().getFunction().getLogic().hasParsed())
				.isTrue();
	}

	@Test
	void parsesDeclaredNamesWithAnd() {
		DecisionTable table = DecisionTable.newBuilder()
				.addInputs(InputClause.newBuilder().setInputExpression(feel("Another Date and Time"))).build();

		Definitions semanticModel = Definitions.newBuilder()
				.addDrgElements(DrgElement.newBuilder()
						.setInputData(io.finmsg.dmn.model.InputData.newBuilder()
								.setNode(io.finmsg.dmn.model.Node.newBuilder().setName("Another Date and Time"))))
				.addDrgElements(DrgElement.newBuilder().setDecision(
						Decision.newBuilder().setLogic(DecisionLogic.newBuilder().setDecisionTable(table))))
				.build();

		Definitions parsedModel = parser.parse(semanticModel);
		Feel parsedFeel = parsedModel.getDrgElements(1).getDecision().getLogic().getDecisionTable().getInputs(0)
				.getInputExpression();
		assertThat(parsedFeel.hasParsed()).isTrue();
		assertThat(parsedFeel.getParsed().getAst().hasName()).isTrue();
		assertThat(parsedFeel.getParsed().getAst().getName().getName()).isEqualTo("Another Date and Time");
	}

	private static Feel feel(String source) {
		return Feel.newBuilder().setText(text(source)).build();
	}

	private static FeelText text(String source) {
		return FeelText.newBuilder().setText(source).build();
	}

	private static UnaryTest unaryTest(String source) {
		return UnaryTest.newBuilder().setText(text(source)).build();
	}

	private static ExpressionText expressionText(String source) {
		return ExpressionText.newBuilder().setFeel(text(source)).build();
	}

	private static ExpressionNode expressionNode(String source) {
		return ExpressionNode.newBuilder().setText(expressionText(source)).build();
	}
}
