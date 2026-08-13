package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuntimeDecision(int id, int resultSlot, RuntimeType type, List<Integer> dependencies,
		Optional<RuntimeExpression> expression, Optional<RuntimeDecisionTable> decisionTable, int localSlotCount) {
	public RuntimeDecision {
		if (id < 0 || resultSlot < 0) {
			throw new IllegalArgumentException("Runtime decision IDs and slots must be non-negative.");
		}
		Objects.requireNonNull(type, "type");
		dependencies = List.copyOf(dependencies);
		expression = Objects.requireNonNull(expression, "expression");
		decisionTable = Objects.requireNonNull(decisionTable, "decisionTable");
		if (expression.isPresent() && decisionTable.isPresent()) {
			throw new IllegalArgumentException("Decision cannot have two runtime logic forms.");
		}
		if (localSlotCount < 0) {
			throw new IllegalArgumentException("localSlotCount must not be negative.");
		}
	}

	public RuntimeDecision(int id, int resultSlot, RuntimeType type, List<Integer> dependencies,
			Optional<RuntimeExpression> expression) {
		this(id, resultSlot, type, dependencies, expression, Optional.empty(), 0);
	}

	public RuntimeDecision(int id, int resultSlot, RuntimeType type, List<Integer> dependencies) {
		this(id, resultSlot, type, dependencies, Optional.empty(), Optional.empty(), 0);
	}
}
