package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Resolves IDs and expression slot references without confusing the two address
 * spaces.
 */
final class SparkSqlDecisionGraph {
	private SparkSqlDecisionGraph() {
	}

	static Set<Integer> required(RuntimeModel model, int decisionId) {
		Map<Integer, Object> nodes = new HashMap<>();
		Map<Integer, Integer> slotIds = new HashMap<>();
		model.inputs().forEach(n -> {
			nodes.put(n.id(), n);
			slotIds.put(n.valueSlot(), n.id());
		});
		model.decisions().forEach(n -> {
			nodes.put(n.id(), n);
			slotIds.put(n.resultSlot(), n.id());
		});
		model.businessKnowledgeModels().forEach(n -> {
			nodes.put(n.id(), n);
			slotIds.put(n.resultSlot(), n.id());
		});
		if (model.decisions().stream().noneMatch(d -> d.id() == decisionId))
			throw new IllegalArgumentException("Unknown decision ID " + decisionId);
		Set<Integer> required = new LinkedHashSet<>();
		Queue<Integer> pending = new ArrayDeque<>();
		pending.add(decisionId);
		while (!pending.isEmpty()) {
			int id = pending.remove();
			if (!required.add(id))
				continue;
			Object node = nodes.get(id);
			if (node instanceof RuntimeDecision d)
				pending.addAll(d.dependencies());
			if (node instanceof RuntimeBkm b)
				pending.addAll(b.dependencies());
			SparkSqlPayloadCodec.walk(node, value -> {
				Integer slot = value instanceof RuntimeValueReference r
						? r.sourceSlot()
						: value instanceof RuntimeDecisionTableReference r ? r.decisionSlot() : null;
				if (slot != null)
					pending.add(Objects.requireNonNull(slotIds.get(slot), "Unknown value slot"));
			});
		}
		return required;
	}

	static RuntimeModel slice(RuntimeModel model, int decisionId) {
		Set<Integer> required = required(model, decisionId);
		// Preserve slot addresses by replacing unrelated executable nodes with unused
		// null inputs.
		var inputs = new ArrayList<>(model.inputs());
		model.decisions().stream().filter(d -> !required.contains(d.id()))
				.forEach(d -> inputs.add(new RuntimeInput(d.id(), d.resultSlot(), d.type())));
		model.businessKnowledgeModels().stream().filter(b -> !required.contains(b.id()))
				.forEach(b -> inputs.add(new RuntimeInput(b.id(), b.resultSlot(), b.type())));
		return new RuntimeModel(inputs, model.decisions().stream().filter(d -> required.contains(d.id())).toList(),
				model.businessKnowledgeModels().stream().filter(b -> required.contains(b.id())).toList(),
				model.evaluationOrder().stream().filter(required::contains).toList(), model.valueSlotCount());
	}
}
