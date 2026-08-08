package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.feel.parser.DmnFeelParser;
import io.finmsg.dmn.frontend.xml.dmn.DmnXmlReader;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DrgElement;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class TrafficViolationSemanticAnalysisTest {

	@Test
	void resolvesAllNamesInTrafficViolationModel() {
		Definitions semanticModel;
		try (InputStream input = getClass().getResourceAsStream("/TrafficViolation.dmn")) {
			assertThat(input).as("TrafficViolation.dmn test resource").isNotNull();
			semanticModel = new DmnXmlReader().read(input);
		} catch (Exception exception) {
			throw new AssertionError("Could not read TrafficViolation.dmn", exception);
		}

		Definitions parsedModel = new DmnFeelParser().parse(semanticModel);
		DmnSemanticAnalysisResult result = new DmnSemanticAnalyzer().analyze(parsedModel);

		assertThat(result.isSuccess()).as("semantic diagnostics: %s", result.diagnostics()).isTrue();
		assertThat(result.diagnostics()).isEmpty();
		assertThat(result.model()).isSameAs(parsedModel);
	}

	@Test
	void infersTypesAcrossTrafficViolationModel() {
		Definitions semanticModel;
		try (InputStream input = getClass().getResourceAsStream("/TrafficViolation.dmn")) {
			assertThat(input).isNotNull();
			semanticModel = new DmnXmlReader().read(input);
		} catch (Exception exception) {
			throw new AssertionError(exception);
		}

		Definitions parsedModel = new DmnFeelParser().parse(semanticModel);
		DmnSemanticAnalysisResult result = new DmnTypeAnalyzer().analyze(parsedModel);

		assertThat(result.diagnostics()).isEmpty();
		assertThat(parsedModel).isNotEqualTo(result.model());

		Decision fine = decision(result.model(), "Fine");
		assertThat(fine.getLogic().getDecisionTable().getInputs(1).getInputExpression().getParsed().getAst()
				.getInferredType().getBuiltin()).isEqualTo(BuiltinType.BUILTIN_TYPE_NUMBER);

		Decision suspension = decision(result.model(), "Should the driver be suspended?");
		var context = suspension.getLogic().getBoxedExpression().getParsed().getContext();
		assertThat(context.getEntries(0).getExpression().getFeel().getAst().getInferredType().getBuiltin())
				.isEqualTo(BuiltinType.BUILTIN_TYPE_NUMBER);
		assertThat(context.getEntries(1).getExpression().getFeel().getAst().getInferredType().getBuiltin())
				.isEqualTo(BuiltinType.BUILTIN_TYPE_STRING);
		assertThat(context.getEntries(1).getExpression().getFeel().getAst().getIfExpression().getCondition()
				.getInferredType().getBuiltin()).isEqualTo(BuiltinType.BUILTIN_TYPE_BOOLEAN);
	}

	private static Decision decision(Definitions definitions, String name) {
		return definitions.getDrgElementsList().stream().filter(DrgElement::hasDecision).map(DrgElement::getDecision)
				.filter(decision -> decision.getNode().getName().equals(name)).findFirst().orElseThrow();
	}
}
