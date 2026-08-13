package io.finmsg.dmn.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnCompilationResult;
import io.finmsg.dmn.compiler.DmnCompiler;
import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import io.finmsg.dmn.generator.java.DmnJavaGenerator;
import io.finmsg.dmn.ir.RuntimeDecision;
import io.finmsg.dmn.ir.RuntimeModel;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OptimizerIntegrationTest {

	@Test
	@DisplayName("Full integration: DMN compiler -> DmnOptimizer -> DmnRuntime & DmnJavaGenerator execution")
	void testFullOptimizerIntegrationPipeline() throws Exception {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
				             namespace="https://finmsg.io/test/opt"
				             name="OptimizerTest">
				    <decision id="d1" name="FoldedResult">
				        <variable name="FoldedResult" typeRef="number"/>
				        <literalExpression>
				            <text>10 * 5 + (100 - 50)</text>
				        </literalExpression>
				    </decision>
				</definitions>
				""";

		DmnSource source = new DmnSource(new DmnSourceId(URI.create("urn:opt-test")),
				xml.getBytes(StandardCharsets.UTF_8));

		DmnCompilationResult result = new DmnCompiler().compile(source);
		assertThat(result.isSuccess()).isTrue();

		RuntimeModel rawModel = result.optimizedRuntimeModel().orElseThrow().model();
		RuntimeModel optimizedModel = new DmnOptimizer().optimize(rawModel);

		// Evaluate on DmnRuntime interpreter
		DmnEvaluationResult evalResult = new DmnRuntime().evaluate(optimizedModel, Map.of());
		RuntimeDecision decision = optimizedModel.decisions().get(0);
		Object val = evalResult.value(decision.resultSlot());

		assertThat(val).isNotNull();
		assertThat(Double.parseDouble(val.toString())).isEqualTo(100.0);

		// Verify DmnJavaGenerator can generate clean Java from optimized model
		var optModel = new io.finmsg.dmn.ir.RuntimeIrOptimizer().optimize(optimizedModel);
		DmnJavaGenerator generator = new DmnJavaGenerator();
		var genResult = generator.generate(optModel);
		assertThat(genResult.mainSource()).contains("class");
	}
}
