package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnCompilerOptions;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.ir.RuntimeConstant;
import io.finmsg.dmn.ir.RuntimeDecision;
import io.finmsg.dmn.ir.RuntimeModel;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Cross-module checks for the production optimized execution path. */
class OptimizedModelPipelineTest {

	@Test
	void optimizedCompilationFeedsInterpreterAndGeneratedJava() {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
				             namespace="https://finmsg.io/test/opt" name="OptimizerTest">
				  <decision id="d1" name="FoldedResult">
				    <variable name="FoldedResult" typeRef="number"/>
				    <literalExpression><text>10 * 5 + (100 - 50)</text></literalExpression>
				  </decision>
				</definitions>
				""";
		DmnSource source = new DmnSource(new DmnSourceId(URI.create("urn:opt-test")),
				xml.getBytes(StandardCharsets.UTF_8));

		DmnCompilationResult result = new DmnCompiler().compile(source,
				new io.finmsg.dmn.compiler.InMemoryDmnModelResolver(java.util.List.of()),
				DmnCompilerOptions.optimized());
		assertThat(result.isSuccess()).isTrue();

		RuntimeModel model = result.optimizedRuntimeModel().orElseThrow().model();
		RuntimeDecision decision = model.decisions().getFirst();
		assertThat(decision.expression()).containsInstanceOf(RuntimeConstant.class);

		DmnEvaluationResult evaluation = new DmnRuntime().evaluate(model, Map.of());
		assertThat(evaluation.value(decision.resultSlot()).toString()).isEqualTo("100");

		assertThat(new DmnJavaGenerator().generate(result.optimizedRuntimeModel().orElseThrow()).mainSource())
				.contains("class");
	}
}
