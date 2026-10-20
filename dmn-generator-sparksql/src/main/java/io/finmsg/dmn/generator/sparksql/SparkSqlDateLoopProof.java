package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.time.LocalDate;
import java.util.*;

/** Exact terminal date lists from bounded, closed identity loops. */
final class SparkSqlDateLoopProof {
	static Object value(RuntimeDecision decision) {
		if (decision.decisionTable().isPresent() || decision.type() == null
				|| !(decision.type().kind() == RuntimeTypeKind.ANY
						|| decision.type().kind() == RuntimeTypeKind.LIST && decision.type().elementType() != null
								&& decision.type().elementType().kind() == RuntimeTypeKind.DATE)
				|| !(decision.expression().orElse(null) instanceof RuntimeForExpression loop) || loop.partialSlot() >= 0
				|| loop.iterations().size() != 1)
			return null;
		var iteration = loop.iterations().getFirst();
		if (iteration.localSlot() < 0 || iteration.localSlot() >= 2048 || iteration.end().isEmpty()
				|| !(loop.result() instanceof RuntimeLocalReference reference)
				|| reference.localSlot() != iteration.localSlot() || reference.lexicalDepth() != 0)
			return null;
		try {
			LocalDate first = date(iteration.source()), last = date(iteration.end().get());
			if (first == null || last == null || Math.abs(last.toEpochDay() - first.toEpochDay()) >= 256)
				return null;
			var literal = new RuntimeDecision(0, 0, decision.type(), List.of(), Optional.of(loop), Optional.empty(),
					2048);
			Object result = new io.finmsg.dmn.runtime.DmnRuntime()
					.evaluate(new RuntimeModel(List.of(), List.of(literal), List.of(), List.of(0), 1), Map.of())
					.decisionValue(0);
			return result instanceof List<?> list && list.size() <= 256
					&& list.stream().allMatch(item -> item instanceof LocalDate) ? result : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static LocalDate date(RuntimeExpression expression) {
		return expression instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.DATE
				&& constant.value().length() <= 32 ? LocalDate.parse(constant.value()) : null;
	}
}
