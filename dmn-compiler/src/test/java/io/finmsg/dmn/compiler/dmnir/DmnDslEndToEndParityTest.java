package io.finmsg.dmn.compiler.dmnir;

import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.dmnir.codec.DmnIrArtifactReader;
import io.finmsg.dmn.frontend.dsl.DmnDslReader;
import io.finmsg.dmn.frontend.xml.dmn.DmnVersion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * End-to-end parity tests proving that {@code .decision} DSL models flow
 * through {@link DmnCompiler}, portable DMNIR ({@code .dmnir}), runtime
 * execution, and regenerated DMN 1.3 / 1.5 / 1.6 XML with identical results.
 */
class DmnDslEndToEndParityTest {

	private static final String RATES_DSL = """
			model "Rates Library"
			namespace "urn:example:dsl:rates"
			id "rates-lib"

			bkm MonthlyRate id "bkm_MonthlyRate"(annualRate: number): number =
			  annualRate / 12
			""";

	private static final String POLICY_DSL = """
			model "Loan Underwriting"
			namespace "urn:example:dsl:policy"
			id "loan-underwriting"

			import Rates from "rates.decision" namespace "urn:example:dsl:rates"

			type tApplicant {
			  Age: number constraint >= 0
			  Score: number
			}

			input Applicant: tApplicant
			input Principal: number
			input AnnualRate: number

			decision RiskTier: string
			  requires Applicant
			  table first {
			    when Applicant.Score: number | Applicant.Age: number => result: string default "HIGH"
			    | >= 700 | >= 21 | => "LOW"
			    | [600 .. 699] | >= 18 | => "MEDIUM"
			    | - | - | => "HIGH"
			  }

			decision MonthlyInterest: number
			  requires Principal, AnnualRate
			  uses Rates.MonthlyRate#bkm_MonthlyRate
			  = Principal * Rates.MonthlyRate(AnnualRate)

			decision Approved: boolean
			  requires RiskTier, MonthlyInterest
			  = RiskTier != "HIGH" and MonthlyInterest <= 1000
			""";

	@Test
	void dslCompilesAndMatchesDmnIrAndExportedDmn13And15And16Xml() throws Exception {
		DmnDslReader dslReader = new DmnDslReader();
		// Formatting must be idempotent and semantically identical
		String formattedPolicy = dslReader.format(POLICY_DSL);
		assertEquals(formattedPolicy, dslReader.format(formattedPolicy));

		DmnSource ratesSrc = new DmnSource(DmnSourceId.of("memory:/rates.decision"),
				RATES_DSL.getBytes(StandardCharsets.UTF_8));
		DmnSource policySrc = new DmnSource(DmnSourceId.of("memory:/policy.decision"),
				formattedPolicy.getBytes(StandardCharsets.UTF_8));
		DmnModelResolver resolver = new InMemoryDmnModelResolver(List.of(ratesSrc));

		// 1. Direct DSL compilation via shared DmnCompiler
		DmnCompiler compiler = new DmnCompiler();
		DmnCompilationResult directCompiled = compiler.compile(policySrc, resolver, DmnCompilerOptions.optimized());
		assertTrue(directCompiled.isSuccess(), "DSL must compile: " + directCompiled.diagnostics());
		DmnCompiledModel directModel = directCompiled.compiledModel().orElseThrow();

		// 2. DSL -> portable DMNIR (.dmnir) -> bounded verification -> runtime adapter
		byte[] dmnirBytes = new DmnIrScalarProducer().produce(policySrc, resolver).artifact().toByteArray();
		var verified = new DmnIrArtifactReader().read(dmnirBytes);
		var adapted = new DmnIrRuntimeAdapter().adapt(verified);

		List<Map<String, Object>> cases = List.of(
				Map.of("Applicant", Map.of("Age", new BigDecimal("30"), "Score", new BigDecimal("750")), "Principal",
						new BigDecimal("120000"), "AnnualRate", new BigDecimal("0.06")),
				Map.of("Applicant", Map.of("Age", new BigDecimal("25"), "Score", new BigDecimal("650")), "Principal",
						new BigDecimal("300000"), "AnnualRate", new BigDecimal("0.06")),
				Map.of("Applicant", Map.of("Age", new BigDecimal("19"), "Score", new BigDecimal("500")), "Principal",
						new BigDecimal("50000"), "AnnualRate", new BigDecimal("0.06")));

		List<String> expectedTiers = List.of("LOW", "MEDIUM", "HIGH");
		List<Boolean> expectedApproved = List.of(true, false, false);

		// 3. Export .dmnir -> DMN 1.3, 1.5, and 1.6 XML and recompile with zero access
		// to DSL
		DmnIrScalarDmnExporter exporter = new DmnIrScalarDmnExporter();
		Map<DmnVersion, DmnCompiledModel> exportedModels = new LinkedHashMap<>();
		for (DmnVersion version : List.of(DmnVersion.DMN_1_3, DmnVersion.DMN_1_5, DmnVersion.DMN_1_6)) {
			var bundle = exporter.export(verified, version);
			List<DmnSource> xmlImports = new ArrayList<>();
			DmnSource xmlRoot = null;
			for (var doc : bundle.documents().entrySet()) {
				DmnSource src = new DmnSource(DmnSourceId.of("memory:/" + doc.getKey()),
						doc.getValue().getBytes(StandardCharsets.UTF_8));
				if (doc.getKey().equals(bundle.rootDocument())) {
					xmlRoot = src;
				} else {
					xmlImports.add(src);
				}
			}
			DmnCompilationResult xmlCompiled = compiler.compile(xmlRoot, new InMemoryDmnModelResolver(xmlImports),
					DmnCompilerOptions.optimized());
			assertTrue(xmlCompiled.isSuccess(),
					"Exported " + version + " XML from DSL must compile: " + xmlCompiled.diagnostics());
			exportedModels.put(version, xmlCompiled.compiledModel().orElseThrow());
		}

		// 4. Verify exact semantic parity across Direct DSL, DMNIR, DMN 1.3, 1.5, and
		// 1.6
		for (int i = 0; i < cases.size(); i++) {
			Map<String, Object> inputs = cases.get(i);
			Object directTier = directModel.evaluateDecision("RiskTier", inputs);
			Object directApproved = directModel.evaluateDecision("Approved", inputs);
			assertEquals(expectedTiers.get(i), directTier);
			assertEquals(expectedApproved.get(i), directApproved);

			Map<DmnIrRuntimeAdapter.ElementId, Object> dmnirInputs = Map.of(
					new DmnIrRuntimeAdapter.ElementId("urn:example:dsl:policy", "input_Applicant"),
					inputs.get("Applicant"),
					new DmnIrRuntimeAdapter.ElementId("urn:example:dsl:policy", "input_Principal"),
					inputs.get("Principal"),
					new DmnIrRuntimeAdapter.ElementId("urn:example:dsl:policy", "input_AnnualRate"),
					inputs.get("AnnualRate"));
			var dmnirOut = adapted.evaluate(dmnirInputs);
			assertEquals(directTier,
					dmnirOut.get(new DmnIrRuntimeAdapter.ElementId("urn:example:dsl:policy", "decision_RiskTier")));
			assertEquals(directApproved,
					dmnirOut.get(new DmnIrRuntimeAdapter.ElementId("urn:example:dsl:policy", "decision_Approved")));

			for (var entry : exportedModels.entrySet()) {
				assertEquals(directTier, entry.getValue().evaluateDecision("RiskTier", inputs),
						"RiskTier mismatch on " + entry.getKey());
				assertEquals(directApproved, entry.getValue().evaluateDecision("Approved", inputs),
						"Approved mismatch on " + entry.getKey());
			}
		}
	}
}
