package io.finmsg.dmn.ir;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RuntimeIrValidation {
	private RuntimeIrValidation() {
	}

	static void validateModel(List<RuntimeInput> inputs, List<RuntimeDecision> decisions, List<RuntimeBkm> bkms,
			List<Integer> evaluationOrder, int valueSlotCount) {
		if (valueSlotCount < 0) {
			throw new IllegalArgumentException("valueSlotCount must not be negative.");
		}
		Set<Integer> ids = new HashSet<>();
		Set<Integer> slots = new HashSet<>();
		inputs.forEach(value -> addAddress(value.id(), value.valueSlot(), ids, slots, valueSlotCount));
		decisions.forEach(value -> addAddress(value.id(), value.resultSlot(), ids, slots, valueSlotCount));
		bkms.forEach(value -> addAddress(value.id(), value.resultSlot(), ids, slots, valueSlotCount));
		if (slots.size() != valueSlotCount) {
			throw new IllegalArgumentException("valueSlotCount must equal the number of declared runtime values.");
		}

		Set<Integer> executableIds = new HashSet<>();
		decisions.forEach(value -> executableIds.add(value.id()));
		bkms.forEach(value -> executableIds.add(value.id()));
		decisions.forEach(value -> {
			validateDependencies(value.id(), value.dependencies(), ids);
			value.expression().ifPresent(
					expression -> validateExpression(expression, List.of(value.localSlotCount()), valueSlotCount));
			value.decisionTable()
					.ifPresent(table -> validateTable(table, List.of(value.localSlotCount()), valueSlotCount));
		});
		bkms.forEach(value -> {
			validateDependencies(value.id(), value.dependencies(), ids);
			value.function().ifPresent(function -> validateExpression(function, List.of(), valueSlotCount));
		});
		Set<Integer> ordered = new HashSet<>();
		for (int id : evaluationOrder) {
			if (!executableIds.contains(id) || !ordered.add(id)) {
				throw new IllegalArgumentException(
						"Evaluation order must contain each executable runtime ID exactly once.");
			}
		}
		if (!ordered.equals(executableIds)) {
			throw new IllegalArgumentException(
					"Evaluation order must contain each executable runtime ID exactly once.");
		}
	}

	private static void addAddress(int id, int slot, Set<Integer> ids, Set<Integer> slots, int valueSlotCount) {
		if (id < 0 || !ids.add(id)) {
			throw new IllegalArgumentException("Runtime IDs must be non-negative and unique.");
		}
		if (slot < 0 || slot >= valueSlotCount || !slots.add(slot)) {
			throw new IllegalArgumentException("Runtime value slots must be unique and in bounds.");
		}
	}

	private static void validateDependencies(int owner, List<Integer> dependencies, Set<Integer> ids) {
		Set<Integer> unique = new HashSet<>();
		for (int dependency : dependencies) {
			if (dependency == owner || !ids.contains(dependency) || !unique.add(dependency)) {
				throw new IllegalArgumentException(
						"Runtime dependencies must be unique, in bounds, and not self-referential.");
			}
		}
	}

	private static void validateTable(RuntimeDecisionTable table, List<Integer> frames, int valueSlotCount) {
		table.inputs().forEach(input -> {
			validateExpression(input.expression(), frames, valueSlotCount);
			input.allowedValues().ifPresent(tests -> validateTests(tests, frames, valueSlotCount));
		});
		table.outputs().forEach(output -> {
			output.allowedValues().ifPresent(tests -> validateTests(tests, frames, valueSlotCount));
			output.defaultValue().ifPresent(value -> validateExpression(value, frames, valueSlotCount));
		});
		table.rules().forEach(rule -> {
			rule.inputEntries().forEach(tests -> validateTests(tests, frames, valueSlotCount));
			rule.outputEntries().forEach(value -> validateExpression(value, frames, valueSlotCount));
		});
	}

	private static void validateTests(RuntimeUnaryTests tests, List<Integer> frames, int valueSlotCount) {
		tests.tests().forEach(test -> {
			switch (test) {
				case RuntimeComparisonUnaryTest comparison ->
					validateExpression(comparison.endpoint(), frames, valueSlotCount);
				case RuntimeRangeUnaryTest range -> validateExpression(range.range(), frames, valueSlotCount);
				case RuntimeExpressionUnaryTest expression ->
					validateExpression(expression.expression(), frames, valueSlotCount);
			}
		});
	}

	private static void validateExpression(RuntimeExpression expression, List<Integer> frames, int valueSlotCount) {
		switch (expression) {
			case RuntimeValueReference reference -> validateValueSlot(reference.sourceSlot(), valueSlotCount);
			case RuntimeDecisionTableReference reference -> validateValueSlot(reference.decisionSlot(), valueSlotCount);
			case RuntimeLocalReference reference -> validateLocal(reference, frames);
			case RuntimeUnaryExpression unary -> validateExpression(unary.operand(), frames, valueSlotCount);
			case RuntimeBinaryExpression binary -> {
				validateExpression(binary.left(), frames, valueSlotCount);
				validateExpression(binary.right(), frames, valueSlotCount);
			}
			case RuntimeConditionalExpression conditional -> {
				validateExpression(conditional.condition(), frames, valueSlotCount);
				validateExpression(conditional.thenExpression(), frames, valueSlotCount);
				validateExpression(conditional.elseExpression(), frames, valueSlotCount);
			}
			case RuntimeListExpression list ->
				list.elements().forEach(value -> validateExpression(value, frames, valueSlotCount));
			case RuntimeFunctionCall call ->
				call.arguments().forEach(value -> validateExpression(value, frames, valueSlotCount));
			case RuntimeContextExpression context ->
				context.entries().forEach(entry -> validateExpression(entry.expression(), frames, valueSlotCount));
			case RuntimePathExpression path -> validateExpression(path.source(), frames, valueSlotCount);
			case RuntimeDescendantExpression descendant ->
				validateExpression(descendant.source(), frames, valueSlotCount);
			case RuntimeRangeExpression range -> {
				range.lower().ifPresent(value -> validateExpression(value, frames, valueSlotCount));
				range.upper().ifPresent(value -> validateExpression(value, frames, valueSlotCount));
			}
			case RuntimeFilterExpression filter -> {
				validateExpression(filter.source(), frames, valueSlotCount);
				validateExpression(filter.filter(), frames, valueSlotCount);
			}
			case RuntimeBetweenExpression between -> {
				validateExpression(between.value(), frames, valueSlotCount);
				validateExpression(between.lower(), frames, valueSlotCount);
				validateExpression(between.upper(), frames, valueSlotCount);
			}
			case RuntimeInExpression in -> {
				validateExpression(in.value(), frames, valueSlotCount);
				validateTests(in.tests(), frames, valueSlotCount);
			}
			case RuntimeInstanceOfExpression instance ->
				validateExpression(instance.expression(), frames, valueSlotCount);
			case RuntimeUnaryTestsExpression tests -> validateTests(tests.tests(), frames, valueSlotCount);
			case RuntimeForExpression value -> {
				value.iterations().forEach(iteration -> {
					validateExpression(iteration.source(), frames, valueSlotCount);
					iteration.end().ifPresent(end -> validateExpression(end, frames, valueSlotCount));
				});
				validateExpression(value.result(), frames, valueSlotCount);
			}
			case RuntimeQuantifiedExpression value -> {
				value.bindings().forEach(binding -> validateExpression(binding.source(), frames, valueSlotCount));
				validateExpression(value.satisfies(), frames, valueSlotCount);
			}
			case RuntimeFunctionDefinition function -> {
				List<Integer> nestedFrames = new ArrayList<>();
				nestedFrames.add(function.localSlotCount());
				nestedFrames.addAll(frames);
				function.body().ifPresent(body -> validateExpression(body, nestedFrames, valueSlotCount));
			}
			case RuntimeInvocationExpression invocation -> {
				invocation.target().ifPresent(value -> validateExpression(value, frames, valueSlotCount));
				invocation.namedArguments()
						.forEach(argument -> validateExpression(argument.expression(), frames, valueSlotCount));
				invocation.positionalArguments().forEach(value -> validateExpression(value, frames, valueSlotCount));
			}
			case RuntimeRelationExpression relation ->
				relation.rows().forEach(row -> row.forEach(value -> validateExpression(value, frames, valueSlotCount)));
			case RuntimeConstant ignored -> {
			}
		}
	}

	private static void validateValueSlot(int slot, int valueSlotCount) {
		if (slot < 0 || slot >= valueSlotCount) {
			throw new IllegalArgumentException("Runtime value reference is outside the model slot range.");
		}
	}

	private static void validateLocal(RuntimeLocalReference reference, List<Integer> frames) {
		if (reference.lexicalDepth() >= frames.size()
				|| reference.localSlot() >= frames.get(reference.lexicalDepth())) {
			throw new IllegalArgumentException("Runtime local reference is outside its lexical frame.");
		}
	}
}
