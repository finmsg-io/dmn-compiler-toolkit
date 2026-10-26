package io.finmsg.dmn.generator.sparksql;
import static org.assertj.core.api.Assertions.*;
import io.finmsg.dmn.ir.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class SparkSqlLiteralBuiltinProofTest {
	@Test
	void predicateProofRejectsHostInputsExternalCallsForeignBindingsAndLargeSources() {
		var number = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
		var any = RuntimeType.scalar(RuntimeTypeKind.ANY);
		var bool = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);
		var one = new RuntimeConstant(RuntimeConstantKind.NUMBER, "1", number);
		var list = new RuntimeListExpression(List.of(one), RuntimeType.element(RuntimeTypeKind.LIST, number));
		assertThat(SparkSqlLiteralBuiltinProof
				.result(new RuntimeFunctionCall("duration", List.of(new RuntimeValueReference(0, any)), any))).isNull();
		var dynamic = new RuntimeValueReference(0, bool);
		var foreign = new RuntimeLocalReference(0, 1, bool);
		var call = new RuntimeFunctionCall("unknown", List.of(), any);
		for (var predicate : List.of(dynamic, foreign, call)) {
			assertThat(SparkSqlLiteralBuiltinProof.result(new RuntimeFilterExpression(list, 0, predicate, any)))
					.isNull();
		}
		var tooLarge = new RuntimeListExpression(Collections.nCopies(65, one), list.type());
		var valid = new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", bool);
		assertThat(SparkSqlLiteralBuiltinProof.result(new RuntimeQuantifiedExpression(RuntimeQuantifier.SOME,
				List.of(new RuntimeQuantifiedBinding(0, tooLarge)), valid, bool))).isNull();
	}
}
