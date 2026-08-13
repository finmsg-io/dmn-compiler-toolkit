package io.finmsg.dmn.feel.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.frontend.xml.dmn.DmnXmlReader;
import io.finmsg.dmn.model.ContextParsed;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionTable;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.ItemDefinition;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class TrafficViolationIntegrationTest {

	private final DmnXmlReader xmlReader = new DmnXmlReader();
	private final DmnFeelParser feelParser = new DmnFeelParser();

	@Test
	void parsesAllFeelExpressionsInTrafficViolationModel() {
		Definitions semanticModel = readTrafficViolation();

		Decision semanticFine = decision(semanticModel, "Fine");
		Decision semanticSuspension = decision(semanticModel, "Should the driver be suspended?");
		ItemDefinition semanticViolation = itemDefinition(semanticModel, "tViolation");

		assertThat(semanticFine.getLogic().getDecisionTable().getInputs(0).getInputExpression().hasText()).isTrue();
		assertThat(semanticFine.getLogic().getDecisionTable().getRules(0).getInputEntries(1).hasText()).isTrue();
		assertThat(semanticSuspension.getLogic().getBoxedExpression().hasText()).isTrue();
		assertThat(semanticViolation.getComponents(2).getConstraint().hasText()).isTrue();

		Definitions parsedModel = feelParser.parse(semanticModel);

		// Parsing creates a copied model; the semantic input remains text-based.
		assertThat(semanticFine.getLogic().getDecisionTable().getInputs(0).getInputExpression().hasText()).isTrue();
		assertThat(semanticSuspension.getLogic().getBoxedExpression().hasText()).isTrue();

		DecisionTable table = decision(parsedModel, "Fine").getLogic().getDecisionTable();
		assertThat(table.getInputsCount()).isEqualTo(2);
		assertThat(table.getRulesCount()).isEqualTo(4);

		table.getInputsList().forEach(input -> assertThat(input.getInputExpression().hasParsed()).isTrue());
		table.getRulesList().forEach(rule -> {
			rule.getInputEntriesList().forEach(entry -> assertThat(entry.hasParsed()).isTrue());
			rule.getOutputEntriesList().forEach(entry -> assertThat(entry.hasParsed()).isTrue());
		});

		// The first speed interval is represented as a parsed unary range test.
		assertThat(table.getRules(0).getInputEntries(1).getParsed().getTests().getTests(0).hasRange()).isTrue();

		ItemDefinition parsedViolation = itemDefinition(parsedModel, "tViolation");
		assertThat(parsedViolation.getComponents(2).getConstraint().hasParsed()).isTrue();
		assertThat(parsedViolation.getComponents(2).getConstraint().getParsed().getTests().getTestsCount())
				.isEqualTo(3);

		ContextParsed context = decision(parsedModel, "Should the driver be suspended?").getLogic().getBoxedExpression()
				.getParsed().getContext();
		assertThat(context.getEntriesCount()).isEqualTo(2);
		assertThat(context.getEntries(0).getExpression().getFeel().getAst().hasBinary()).isTrue();
		assertThat(context.getEntries(1).getExpression().getFeel().getAst().hasIfExpression()).isTrue();

		// A second pass must not alter already parsed nodes.
		assertThat(feelParser.parse(parsedModel)).isEqualTo(parsedModel);
	}

	private Definitions readTrafficViolation() {
		try (InputStream input = getClass().getResourceAsStream("/TrafficViolation.dmn")) {
			assertThat(input).as("TrafficViolation.dmn test resource").isNotNull();
			return xmlReader.read(input);
		} catch (Exception exception) {
			throw new AssertionError("Could not read TrafficViolation.dmn", exception);
		}
	}

	private static Decision decision(Definitions definitions, String name) {
		return definitions.getDrgElementsList().stream().filter(DrgElement::hasDecision).map(DrgElement::getDecision)
				.filter(value -> value.getNode().getName().equals(name)).findFirst()
				.orElseThrow(() -> new AssertionError("Decision not found: " + name));
	}

	private static ItemDefinition itemDefinition(Definitions definitions, String name) {
		return definitions.getItemDefinitionsList().stream().filter(value -> value.getNode().getName().equals(name))
				.findFirst().orElseThrow(() -> new AssertionError("Item definition not found: " + name));
	}
}
