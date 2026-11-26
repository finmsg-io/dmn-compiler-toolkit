package io.finmsg.dmn.ir;

import static org.junit.jupiter.api.Assertions.*;
import io.finmsg.dmn.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RuntimeDateLiteralLoweringTest {
	@Test
	void normalizesValidFeelDateLiteralsToInternalIsoConstants() {
		for (var value : Map.of("-0400-02-29", "-0400-02-29", "-0001-01-01", "-0001-01-01", "0001-01-01", "0001-01-01",
				"10000-01-01", "+10000-01-01", "999999999-12-31", "+999999999-12-31").entrySet()) {
			var result = lower(value.getKey(), LiteralKind.LITERAL_KIND_DATE);
			assertEquals(RuntimeConstantKind.DATE, result.kind());
			assertEquals(value.getValue(), result.value());
		}
	}
	@Test
	void rejectsInvalidDateSpellingsWithoutChangingStringLiterals() {
		for (String value : List.of("0000-01-01", "-0000-01-01", "2023-02-29", "+10000-01-01", "010000-01-01",
				"-00001-01-01", "1000000000-01-01")) {
			assertEquals(RuntimeConstantKind.NULL, lower(value, LiteralKind.LITERAL_KIND_DATE).kind());
			assertEquals(value, lower(value, LiteralKind.LITERAL_KIND_STRING).value());
		}
	}
	private static RuntimeConstant lower(String value, LiteralKind kind) {
		var type = TypeReference.newBuilder()
				.setBuiltin(kind == LiteralKind.LITERAL_KIND_DATE
						? BuiltinType.BUILTIN_TYPE_DATE
						: BuiltinType.BUILTIN_TYPE_STRING);
		var source = Expression.newBuilder().setInferredType(type)
				.setLiteral(LiteralExpression.newBuilder().setKind(kind).setValue(value)).build();
		return (RuntimeConstant) RuntimeExpressionLowerer.lowerExpression(source, "literal", List.of(), Map.of(),
				Map.of(), Map.of(), new int[]{0});
	}
}
