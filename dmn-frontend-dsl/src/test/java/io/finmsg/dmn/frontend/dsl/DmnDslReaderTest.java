package io.finmsg.dmn.frontend.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.finmsg.dmn.model.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DmnDslReaderTest {

	private static final String SAMPLE_DSL = """
			// Loan underwriting policy authored in .decision DSL
			model "Loan Policy"
			namespace "urn:example:loan-policy"
			id "loan-policy"

			import Rates from "rates.decision" namespace "urn:example:rates"

			type tApplicant {
			  Age: number constraint >= 0
			  Score: number
			}

			type tScores list<number>

			input Applicant: tApplicant
			input RequestedAmount: number

			bkm MonthlyPayment(principal: number, rate: number): number =
			  principal * rate / 12

			decision RiskTier: string
			  requires Applicant
			  table first {
			    when Applicant.Score: number | Applicant.Age: number => result: string default "HIGH"
			    | >= 700 | >= 21 | => "LOW"
			    | [600 .. 699] | >= 18 | => "MEDIUM"
			    | - | - | => "HIGH"
			  }

			decision Approved: boolean
			  requires RiskTier, RequestedAmount
			  uses MonthlyPayment
			  = RiskTier != "HIGH" and MonthlyPayment(RequestedAmount, 0.06) <= 2000

			test "Low risk approved" {
			  given Applicant = { Age: 30, Score: 750 }
			  given RequestedAmount = 120000
			  expect RiskTier = "LOW"
			  expect Approved = true
			}
			""";

	@Test
	void parsesAndLowersFullDslModelToCanonicalDefinitions() {
		DmnDslReader reader = new DmnDslReader();
		Definitions defs = reader.read(SAMPLE_DSL);

		assertThat(defs.getNode().getId()).isEqualTo("loan-policy");
		assertThat(defs.getNode().getName()).isEqualTo("Loan Policy");
		assertThat(defs.getNamespace()).isEqualTo("urn:example:loan-policy");
		assertThat(defs.getModelNamespaceUri()).isEqualTo("https://www.omg.org/spec/DMN/20240513/MODEL/");
		assertThat(defs.getImportsList()).hasSize(1);
		assertThat(defs.getImports(0).getName()).isEqualTo("Rates");
		assertThat(defs.getItemDefinitionsList()).hasSize(2);
		assertThat(defs.getItemDefinitions(0).getComponentsList()).hasSize(2);
		assertThat(defs.getItemDefinitions(0).getComponents(0).getConstraint().getText().getText()).isEqualTo(">= 0");
		assertThat(defs.getItemDefinitions(1).getIsCollection()).isTrue();
		assertThat(defs.getDrgElementsList()).hasSize(5);

		Decision riskTier = defs.getDrgElementsList().stream().filter(DrgElement::hasDecision)
				.map(DrgElement::getDecision).filter(d -> d.getNode().getName().equals("RiskTier")).findFirst()
				.orElseThrow();
		assertThat(riskTier.getLogic().hasDecisionTable()).isTrue();
		assertThat(riskTier.getLogic().getDecisionTable().getHitPolicy().getPolicy())
				.isEqualTo(HitPolicy.HIT_POLICY_FIRST);
		assertThat(riskTier.getLogic().getDecisionTable().getRulesCount()).isEqualTo(3);
	}

	@Test
	void formatterIsDeterministicAndIdempotent() {
		DmnDslReader reader = new DmnDslReader();
		String first = reader.format(SAMPLE_DSL);
		String second = reader.format(first);
		assertThat(second).isEqualTo(first);
		assertThat(reader.read(first)).isEqualTo(reader.read(SAMPLE_DSL));
	}

	@Test
	void rejectsMissingHeadersWithSourcePositionedDiagnostics() {
		DmnDslReader reader = new DmnDslReader();
		assertThatThrownBy(() -> reader.read("input x: number")).isInstanceOf(DmnDslParseException.class)
				.satisfies(ex -> {
					DmnDslParseException pe = (DmnDslParseException) ex;
					assertThat(pe.diagnostics()).extracting(DmnDslDiagnostic::code).contains("DSL-MISSING-MODEL",
							"DSL-MISSING-NAMESPACE");
				});
	}

	@Test
	void enforcesMaxBytesAndMaxDeclarationsBounds() {
		DmnDslReader reader = new DmnDslReader();
		byte[] bytes = SAMPLE_DSL.getBytes(StandardCharsets.UTF_8);
		assertThatThrownBy(() -> reader.read(bytes, DmnDslReadOptions.defaults().withMaxBytes(32)))
				.isInstanceOf(DmnDslParseException.class).hasMessageContaining("DSL-LIMIT-BYTES");
		assertThatThrownBy(() -> reader.read(SAMPLE_DSL, DmnDslReadOptions.defaults().withMaxDeclarations(2)))
				.isInstanceOf(DmnDslParseException.class).hasMessageContaining("DSL-LIMIT-DECLS");
	}

	@Test
	void rejectsTableRuleArityMismatchAndUnknownLocalReferences() {
		DmnDslReader reader = new DmnDslReader();
		String badTable = """
				model "Bad"
				namespace "urn:example:bad"
				input x: number
				decision d: string
				  requires x
				  table unique {
				    when x: number => result: string
				    | > 0 | < 10 | => "A"
				  }
				""";
		assertThatThrownBy(() -> reader.read(badTable)).isInstanceOf(DmnDslParseException.class)
				.hasMessageContaining("DSL-TABLE-ARITY");

		String badRef = """
				model "BadRef"
				namespace "urn:example:badref"
				decision d: number
				  requires MissingInput
				  = 1
				""";
		assertThatThrownBy(() -> reader.read(badRef)).isInstanceOf(DmnDslParseException.class)
				.hasMessageContaining("DSL-UNKNOWN-REF");
	}

	@Test
	void detectsDslVsXmlSourcesAccurately() {
		assertThat(
				DmnDslReader.isDslSource("memory:/policy.decision", "<definitions/>".getBytes(StandardCharsets.UTF_8)))
				.isTrue();
		assertThat(DmnDslReader.isDslSource("memory:/policy.dmn",
				"model \"A\"\nnamespace \"urn:a\"".getBytes(StandardCharsets.UTF_8))).isTrue();
		assertThat(DmnDslReader.isDslSource("memory:/policy.dmn",
				"<?xml version=\"1.0\"?><definitions/>".getBytes(StandardCharsets.UTF_8))).isFalse();
	}
}
