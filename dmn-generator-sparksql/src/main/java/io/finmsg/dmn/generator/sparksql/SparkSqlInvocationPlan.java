package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Adapts a directly invoked BKM/decision service into a decision-level Spark
 * execution plan.
 */
public record SparkSqlInvocationPlan(RuntimeModel model, int decisionId, int resultSlot,
		Map<Integer, String> parameterNames) {
	public SparkSqlInvocationPlan {
		parameterNames = Map.copyOf(parameterNames);
	}

	public static SparkSqlInvocationPlan create(RuntimeModel model, int bkmId) {
		RuntimeBkm bkm = model.businessKnowledgeModels().stream().filter(b -> b.id() == bkmId).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown invocable ID " + bkmId));
		RuntimeFunctionDefinition function = bkm.function()
				.orElseThrow(() -> new IllegalArgumentException("Invocable has no function"));
		int nextId = java.util.stream.Stream
				.concat(model.inputs().stream().map(RuntimeInput::id),
						java.util.stream.Stream.concat(model.decisions().stream().map(RuntimeDecision::id),
								model.businessKnowledgeModels().stream().map(RuntimeBkm::id)))
				.mapToInt(Integer::intValue).max().orElse(-1) + 1;
		int nextSlot = model.valueSlotCount();
		var inputs = new ArrayList<>(model.inputs());
		var arguments = new ArrayList<RuntimeExpression>();
		var dependencies = new ArrayList<Integer>();
		dependencies.add(bkmId);
		Map<Integer, String> names = new LinkedHashMap<>();
		for (RuntimeFunctionParameter parameter : function.parameters()) {
			int id = nextId++;
			int slot = nextSlot++;
			inputs.add(new RuntimeInput(id, slot, parameter.type()));
			names.put(slot, parameter.name());
			dependencies.add(id);
			arguments.add(new RuntimeValueReference(slot, parameter.type()));
		}
		RuntimeType resultType = Objects.requireNonNullElse(function.type().returnType(),
				RuntimeType.scalar(RuntimeTypeKind.ANY));
		var invocation = new RuntimeInvocationExpression(Optional.empty(),
				Optional.of(new RuntimeValueReference(bkm.resultSlot(), bkm.type())), List.of(), arguments, resultType);
		var decisions = new ArrayList<>(model.decisions());
		decisions.add(new RuntimeDecision(nextId, nextSlot, resultType, dependencies, Optional.of(invocation)));
		var order = new ArrayList<>(model.evaluationOrder());
		order.add(nextId);
		return new SparkSqlInvocationPlan(
				new RuntimeModel(inputs, decisions, model.businessKnowledgeModels(), order, nextSlot + 1), nextId,
				nextSlot, names);
	}
}
