package io.finmsg.dmn.compiler.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.compiler.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests for {@link DmnDslCli} and {@link DmnDslTestRunner},
 * verifying check, format, inline test execution, production payload test
 * exclusion, compilation to {@code .dmnir}, and DMN 1.3/1.5/1.6 export.
 */
class DmnDslCliAndTestRunnerTest {

	private static final String RATES_DSL = """
			model "Rates"
			namespace "urn:cli:rates"
			id "rates"

			bkm MonthlyRate id "bkm_MonthlyRate"(annualRate: number): number =
			  annualRate / 12
			""";

	private static final String POLICY_BODY = """
			model "Underwriting"
			namespace "urn:cli:policy"
			id "policy"

			import Rates from "rates.decision" namespace "urn:cli:rates"

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

	private static final String POLICY_WITH_TESTS = POLICY_BODY + """

			test "Low risk approved" {
			  given Applicant = { Age: 30, Score: 750 }
			  given Principal = 120000
			  given AnnualRate = 0.06
			  expect RiskTier = "LOW"
			  expect MonthlyInterest = 600
			  expect Approved = true
			}

			test "High interest declined" {
			  given Applicant = { Age: 25, Score: 650 }
			  given Principal = 300000
			  given AnnualRate = 0.06
			  expect RiskTier = "MEDIUM"
			  expect Approved = false
			}
			""";

	@Test
	void cliCheckFormatTestCompileAndExportWorkflow(@TempDir Path dir) throws Exception {
		Path ratesFile = Files.writeString(dir.resolve("rates.decision"), RATES_DSL, StandardCharsets.UTF_8);
		Path policyFile = Files.writeString(dir.resolve("policy.decision"), POLICY_WITH_TESTS, StandardCharsets.UTF_8);
		Path policyNoTestsFile = Files.writeString(dir.resolve("policy-notests.decision"), POLICY_BODY,
				StandardCharsets.UTF_8);
		assertTrue(Files.exists(ratesFile));

		// 1. check
		var checkOut = new ByteArrayOutputStream();
		var checkErr = new ByteArrayOutputStream();
		assertEquals(DmnDslCli.EXIT_OK, new DmnDslCli(new PrintStream(checkOut), new PrintStream(checkErr))
				.run(new String[]{"check", policyFile.toString()}));
		assertTrue(checkOut.toString(StandardCharsets.UTF_8).contains("OK:"));

		// 2. format --write (idempotent)
		var fmtOut = new ByteArrayOutputStream();
		assertEquals(DmnDslCli.EXIT_OK,
				new DmnDslCli(new PrintStream(fmtOut), new PrintStream(new ByteArrayOutputStream()))
						.run(new String[]{"format", policyFile.toString(), "--write"}));
		String afterFirst = Files.readString(policyFile, StandardCharsets.UTF_8);
		assertEquals(DmnDslCli.EXIT_OK,
				new DmnDslCli(new PrintStream(new ByteArrayOutputStream()),
						new PrintStream(new ByteArrayOutputStream()))
						.run(new String[]{"format", policyFile.toString(), "--write"}));
		assertEquals(afterFirst, Files.readString(policyFile, StandardCharsets.UTF_8));

		// 3. test (all inline tests pass)
		var testOut = new ByteArrayOutputStream();
		var testErr = new ByteArrayOutputStream();
		assertEquals(DmnDslCli.EXIT_OK, new DmnDslCli(new PrintStream(testOut), new PrintStream(testErr))
				.run(new String[]{"test", policyFile.toString()}));
		assertTrue(testOut.toString(StandardCharsets.UTF_8).contains("2/2 passed, 0 failed"));

		// 4. compile -> .dmnir (inline tests MUST be excluded from production payload!)
		Path dmnirFile = dir.resolve("out/policy.dmnir");
		Path dmnirNoTestsFile = dir.resolve("out/policy-notests.dmnir");
		assertEquals(DmnDslCli.EXIT_OK,
				new DmnDslCli(new PrintStream(new ByteArrayOutputStream()),
						new PrintStream(new ByteArrayOutputStream()))
						.run(new String[]{"compile", policyFile.toString(), "--out", dmnirFile.toString()}));
		assertEquals(DmnDslCli.EXIT_OK, new DmnDslCli(new PrintStream(new ByteArrayOutputStream()),
				new PrintStream(new ByteArrayOutputStream()))
				.run(new String[]{"compile", policyNoTestsFile.toString(), "--out", dmnirNoTestsFile.toString()}));
		assertArrayEquals(Files.readAllBytes(dmnirNoTestsFile), Files.readAllBytes(dmnirFile),
				"Inline tests must be excluded from production .dmnir payload");

		// 5. export .dmnir -> DMN 1.6 XML and recompile
		Path exportDir = dir.resolve("exported-dmn16");
		var expOut = new ByteArrayOutputStream();
		assertEquals(DmnDslCli.EXIT_OK,
				new DmnDslCli(new PrintStream(expOut), new PrintStream(new ByteArrayOutputStream())).run(new String[]{
						"export", dmnirFile.toString(), "--out", exportDir.toString(), "--dmn-version", "1.6"}));
		assertTrue(Files.exists(exportDir.resolve("model-0000.dmn")));
		assertTrue(Files.exists(exportDir.resolve("model-0001.dmn")));
		assertTrue(Files.readString(exportDir.resolve("model-0000.dmn"), StandardCharsets.UTF_8)
				.contains("https://www.omg.org/spec/DMN/20240513/MODEL/"));

		// Verify exported DMN 1.6 checks cleanly via CLI check
		assertEquals(DmnDslCli.EXIT_OK,
				new DmnDslCli(new PrintStream(new ByteArrayOutputStream()),
						new PrintStream(new ByteArrayOutputStream()))
						.run(new String[]{"check", exportDir.resolve("model-0000.dmn").toString()}));
	}

	@Test
	void cliReportsFailingInlineTestAndUsageErrorsWithDocumentedExitCodes(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("rates.decision"), RATES_DSL, StandardCharsets.UTF_8);
		String failingPolicy = POLICY_BODY + """

				test "Intentional mismatch" {
				  given Applicant = { Age: 30, Score: 750 }
				  given Principal = 120000
				  given AnnualRate = 0.06
				  expect RiskTier = "HIGH"
				}
				""";
		Path failingFile = Files.writeString(dir.resolve("failing.decision"), failingPolicy, StandardCharsets.UTF_8);

		var err = new ByteArrayOutputStream();
		assertEquals(DmnDslCli.EXIT_FAILURE,
				new DmnDslCli(new PrintStream(new ByteArrayOutputStream()), new PrintStream(err))
						.run(new String[]{"test", failingFile.toString()}));
		String errText = err.toString(StandardCharsets.UTF_8);
		assertTrue(errText.contains("FAIL: Intentional mismatch"));
		assertTrue(errText.contains("expected <HIGH> but got <LOW>"));

		// Usage errors return EXIT_USAGE (2)
		assertEquals(DmnDslCli.EXIT_USAGE, new DmnDslCli(new PrintStream(new ByteArrayOutputStream()),
				new PrintStream(new ByteArrayOutputStream())).run(new String[]{"unknown-cmd"}));
		assertEquals(DmnDslCli.EXIT_USAGE, new DmnDslCli(new PrintStream(new ByteArrayOutputStream()),
				new PrintStream(new ByteArrayOutputStream()))
				.run(new String[]{"export", failingFile.toString(), "--out", dir.toString(), "--dmn-version", "9.9"}));
	}
}
