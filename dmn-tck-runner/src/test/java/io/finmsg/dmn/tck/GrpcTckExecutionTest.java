package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.*;

import io.finmsg.dmn.compiler.*;
import io.finmsg.dmn.grpc.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class GrpcTckExecutionTest {
	@AfterAll
	static void shutdown() throws Exception {
		GrpcTckExecution.closeTransport();
	}

	private GrpcTckExecution model(String value) throws Exception {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/" id="d" name="same" namespace="urn:same">
				<decision id="r" name="Result"><variable name="Result"/>
				<literalExpression><text>%s</text></literalExpression></decision></definitions>
				"""
				.formatted(value);
		var compilation = new DmnCompiler().compile(new DmnSource(DmnSourceId.of("memory:grpc-model"),
				xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertThat(compilation.isSuccess()).isTrue();
		return GrpcTckExecution.prepare(compilation);
	}

	@Test
	void sharedHttp2HostRoutesDifferentGeneratedModelsAndRetainsNullOutput() throws Exception {
		try (var first = model("1"); var second = model("2"); var empty = model("null")) {
			var request = DmnEvaluationRequest.newBuilder().setModelNamespace("urn:same").setModelName("same").build();
			assertThat(DmnGrpcValueConverter.toJavaObject(first.execute(request, 30).getOutputsOrThrow("Result")))
					.isEqualTo(BigDecimal.ONE);
			assertThat(DmnGrpcValueConverter.toJavaObject(second.execute(request, 30).getOutputsOrThrow("Result")))
					.isEqualTo(BigDecimal.valueOf(2));
			assertThat(DmnGrpcValueConverter.toJavaObject(first.execute(request, 30).getOutputsOrThrow("Result")))
					.isEqualTo(BigDecimal.ONE);
			assertThat(empty.execute(request, 30).getOutputsOrThrow("Result").getValueKindCase())
					.isEqualTo(Value.ValueKindCase.NULL_VALUE);
		}
	}
}
