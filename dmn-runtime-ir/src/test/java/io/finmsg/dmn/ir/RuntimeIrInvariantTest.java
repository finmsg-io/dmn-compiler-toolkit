package io.finmsg.dmn.ir;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeIrInvariantTest {
	private static final RuntimeType NUMBER = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
	private static final RuntimeConstant ONE = new RuntimeConstant(RuntimeConstantKind.NUMBER, "1", NUMBER);

	@Test
	void rejectsNegativeLeafAddresses() {
		assertThatThrownBy(() -> new RuntimeInput(-1, 0, NUMBER)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("non-negative");
		assertThatThrownBy(() -> new RuntimeDecision(0, -1, NUMBER, List.of()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-negative");
	}

	@Test
	void rejectsDuplicateIdsAndSlots() {
		RuntimeInput input = new RuntimeInput(0, 0, NUMBER);
		RuntimeDecision duplicateId = decision(0, 1, List.of(), ONE, 0);
		assertThatThrownBy(() -> new RuntimeModel(List.of(input), List.of(duplicateId), List.of(), List.of(0), 2))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("IDs");

		RuntimeDecision duplicateSlot = decision(1, 0, List.of(), ONE, 0);
		assertThatThrownBy(() -> new RuntimeModel(List.of(input), List.of(duplicateSlot), List.of(), List.of(1), 2))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("slots");
	}

	@Test
	void rejectsSlotCountMismatchAndOutOfBoundsReferences() {
		RuntimeDecision decision = decision(0, 0, List.of(), new RuntimeValueReference(1, NUMBER), 0);
		assertThatThrownBy(() -> new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(0), 1))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outside the model slot range");

		assertThatThrownBy(
				() -> new RuntimeModel(List.of(new RuntimeInput(0, 0, NUMBER)), List.of(), List.of(), List.of(), 2))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("valueSlotCount");
	}

	@Test
	void rejectsInvalidDependencies() {
		RuntimeDecision unknown = decision(1, 1, List.of(4), ONE, 0);
		assertThatThrownBy(() -> new RuntimeModel(List.of(new RuntimeInput(0, 0, NUMBER)), List.of(unknown), List.of(),
				List.of(1), 2)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dependencies");

		RuntimeDecision self = decision(0, 0, List.of(0), ONE, 0);
		assertThatThrownBy(() -> new RuntimeModel(List.of(), List.of(self), List.of(), List.of(0), 1))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dependencies");
	}

	@Test
	void rejectsIncompleteOrDuplicateEvaluationOrder() {
		RuntimeDecision decision = decision(0, 0, List.of(), ONE, 0);
		assertThatThrownBy(() -> new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(), 1))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Evaluation order");
		assertThatThrownBy(() -> new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(0, 0), 1))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Evaluation order");
	}

	@Test
	void rejectsLocalReferencesOutsidePersistedFrames() {
		RuntimeDecision decision = decision(0, 0, List.of(), new RuntimeLocalReference(0, 1, NUMBER), 1);
		assertThatThrownBy(() -> new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(0), 1))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lexical frame");
	}

	@Test
	void rejectsInvalidDecisionTableShapeAndAggregation() {
		RuntimeDecisionTableInput input = new RuntimeDecisionTableInput(ONE, Optional.empty(), NUMBER);
		RuntimeDecisionTableOutput output = new RuntimeDecisionTableOutput(Optional.of("result"), NUMBER,
				Optional.empty(), Optional.empty());
		RuntimeDecisionTableRule narrowRule = new RuntimeDecisionTableRule(0, List.of(), List.of(ONE), List.of());

		assertThatThrownBy(() -> new RuntimeDecisionTable(RuntimeHitPolicy.UNIQUE, Optional.empty(), List.of(input),
				List.of(output), List.of(narrowRule), 0)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("widths");
		assertThatThrownBy(() -> new RuntimeDecisionTable(RuntimeHitPolicy.FIRST, Optional.of(RuntimeAggregation.SUM),
				List.of(input), List.of(output), List.of(), 0)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("COLLECT");
	}

	private static RuntimeDecision decision(int id, int slot, List<Integer> dependencies, RuntimeExpression expression,
			int localSlotCount) {
		return new RuntimeDecision(id, slot, NUMBER, dependencies, Optional.of(expression), Optional.empty(),
				localSlotCount);
	}
}
