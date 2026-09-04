package io.finmsg.dmn.example;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiledModel;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorOptions;
import io.finmsg.dmn.generator.java.DmnJavaGeneratorResult;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

/**
 * External Consumer Verification Application (G2 Gate 6).
 * Demonstrates compiling, interpreting, and generating Java from an external project.
 */
public class ConsumerVerificationApp {

	public static void main(String[] args) throws Exception {
		System.out.println("Starting DMN Compiler Toolkit External Consumer Verification...");

		byte[] dmnBytes;
		try (InputStream is = ConsumerVerificationApp.class.getResourceAsStream("/models/loan-approval.dmn")) {
			if (is == null) {
				throw new IOException("Cannot find /models/loan-approval.dmn on classpath");
			}
			dmnBytes = is.readAllBytes();
		}
		DmnSource source = new DmnSource(DmnSourceId.of("loan-approval.dmn"), dmnBytes);

		DmnCompiler compiler = new DmnCompiler();
		DmnCompilationResult compilation = compiler.compile(source);
		if (!compilation.isSuccess()) {
			System.err.println("Compilation failed: " + compilation.diagnostics());
			System.exit(1);
		}

		DmnCompiledModel compiledModel = compilation.compiledModel().orElseThrow();
		System.out.println("Compiled model: " + compiledModel.modelName());

		Map<String, Object> inputs = Map.of(
			"BorrowerScore", new BigDecimal("750"),
			"Income", new BigDecimal("60000")
		);

		Object interpretedResult = compiledModel.evaluateDecision("LoanApproval", inputs);
		System.out.println("Interpreter decision result: " + interpretedResult);

		DmnJavaGenerator generator = new DmnJavaGenerator();
		DmnJavaGeneratorOptions options = DmnJavaGeneratorOptions.of(
			"io.finmsg.dmn.generated.loan", "LoanApprovalDecisionModel"
		);
		DmnJavaGeneratorResult genResult = generator.generate(compilation.modelSet(), options);

		if (!genResult.isSuccess()) {
			System.err.println("Java generation failed: " + genResult.diagnostics());
			System.exit(2);
		}

		System.out.println("Generated Java files count: " + genResult.sourceFiles().size());
		System.out.println("External consumer verification completed successfully!");
	}
}
