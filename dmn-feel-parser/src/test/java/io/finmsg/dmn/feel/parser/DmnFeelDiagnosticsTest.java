package io.finmsg.dmn.feel.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.model.DecisionRule;
import io.finmsg.dmn.model.DecisionTable;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.FeelText;
import io.finmsg.dmn.model.InputClause;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.model.SourceLocation;
import io.finmsg.dmn.model.UnaryTest;
import org.junit.jupiter.api.Test;

class DmnFeelDiagnosticsTest {

	private final DmnFeelParser parser = new DmnFeelParser();

	@Test
	void collectsAllErrorsWithDmnPathsAndKeepsInvalidNodesAsText() {
		SourceLocation location = SourceLocation.newBuilder().setSystemId("invalid.dmn").setLine(12).setColumn(5)
				.build();

		DecisionTable table = DecisionTable.newBuilder().setNode(Node.newBuilder().setSourceLocation(location))
				.addInputs(InputClause.newBuilder().setInputExpression(feel("1 +")))
				.addInputs(InputClause.newBuilder().setInputExpression(feel("Applicant.age")))
				.addRules(DecisionRule.newBuilder().addInputEntries(unaryTest("[1.."))).build();

		Definitions semanticModel = Definitions.newBuilder()
				.addDrgElements(DrgElement.newBuilder()
						.setDecision(Decision.newBuilder().setNode(Node.newBuilder().setName("Bad decision"))
								.setLogic(DecisionLogic.newBuilder().setDecisionTable(table))))
				.build();

		DmnFeelParseResult result = parser.parseWithDiagnostics(semanticModel);

		assertThat(result.isSuccess()).isFalse();
		assertThat(result.diagnostics()).hasSize(2);
		assertThat(result.diagnostics()).extracting(DmnFeelDiagnostic::path).containsExactly(
				"definitions/drgElement[Bad decision]/logic/decisionTable/input[0]/inputExpression",
				"definitions/drgElement[Bad decision]/logic/decisionTable/rule[0]/inputEntry[0]");
		assertThat(result.diagnostics()).extracting(DmnFeelDiagnostic::source).containsExactly("1 +", "[1..");
		assertThat(result.diagnostics()).allSatisfy(diagnostic -> {
			assertThat(diagnostic.sourceLocation()).isEqualTo(location);
			assertThat(diagnostic.diagnostics()).isNotEmpty();
		});

		DecisionTable parsedTable = result.model().getDrgElements(0).getDecision().getLogic().getDecisionTable();
		assertThat(parsedTable.getInputs(0).getInputExpression().hasText()).isTrue();
		assertThat(parsedTable.getInputs(1).getInputExpression().hasParsed()).isTrue();
		assertThat(parsedTable.getRules(0).getInputEntries(0).hasText()).isTrue();

		assertThat(semanticModel.getDrgElements(0).getDecision().getLogic().getDecisionTable().getInputs(1)
				.getInputExpression().hasText()).isTrue();
	}

	@Test
	void strictApiThrowsModelAwareException() {
		Definitions semanticModel = Definitions.newBuilder()
				.addDrgElements(
						DrgElement.newBuilder()
								.setDecision(Decision.newBuilder().setNode(Node.newBuilder().setName("Invalid"))
										.setLogic(DecisionLogic.newBuilder().setLiteralExpression(feel("1 +")))))
				.build();

		assertThatThrownBy(() -> parser.parse(semanticModel)).isInstanceOf(DmnFeelParseException.class)
				.hasMessageContaining("definitions/drgElement[Invalid]/logic/literalExpression");
	}

	private static Feel feel(String source) {
		return Feel.newBuilder().setText(FeelText.newBuilder().setText(source)).build();
	}

	private static UnaryTest unaryTest(String source) {
		return UnaryTest.newBuilder().setText(FeelText.newBuilder().setText(source)).build();
	}
}
