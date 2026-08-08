package io.finmsg.dmn.optimizer.pass;

import io.finmsg.dmn.ir.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Optimization pass that prunes unreachable rules in hitPolicy="FIRST" decision
 * tables.
 */
public class DecisionTableOptimizationPass implements OptimizerPass {

	@Override
	public RuntimeModel transform(RuntimeModel model) {
		Objects.requireNonNull(model, "model");

		List<RuntimeDecision> newDecisions = new ArrayList<>();
		for (RuntimeDecision decision : model.decisions()) {
			newDecisions.add(transformDecision(decision));
		}

		return new RuntimeModel(model.inputs(), newDecisions, model.businessKnowledgeModels(), model.evaluationOrder(),
				model.valueSlotCount());
	}

	private RuntimeDecision transformDecision(RuntimeDecision decision) {
		if (decision.decisionTable().isEmpty()) {
			return decision;
		}

		RuntimeDecisionTable table = decision.decisionTable().get();
		RuntimeDecisionTable optimizedTable = transformDecisionTable(table);

		return new RuntimeDecision(decision.id(), decision.resultSlot(), decision.type(), decision.dependencies(),
				decision.expression(), Optional.of(optimizedTable), decision.localSlotCount());
	}

	private RuntimeDecisionTable transformDecisionTable(RuntimeDecisionTable table) {
		if (table.hitPolicy() != RuntimeHitPolicy.FIRST || table.rules().isEmpty()) {
			return table;
		}

		List<RuntimeDecisionTableRule> pruned = new ArrayList<>();
		boolean wildcardMatched = false;

		for (RuntimeDecisionTableRule rule : table.rules()) {
			if (wildcardMatched) {
				// Unreachable rule - skip
				continue;
			}

			pruned.add(new RuntimeDecisionTableRule(pruned.size(), // Contiguous re-indexed ruleIndex
					rule.inputEntries(), rule.outputEntries(), rule.annotations()));

			if (isCatchAllRule(rule)) {
				wildcardMatched = true;
			}
		}

		if (pruned.size() == table.rules().size()) {
			return table;
		}

		return new RuntimeDecisionTable(table.hitPolicy(), table.aggregation(), table.inputs(), table.outputs(), pruned,
				table.annotationColumnCount());
	}

	private boolean isCatchAllRule(RuntimeDecisionTableRule rule) {
		for (RuntimeUnaryTests tests : rule.inputEntries()) {
			if (tests != null && !tests.tests().isEmpty()) {
				// Not a catch-all if input entries specify unary tests
				return false;
			}
		}
		return true;
	}
}
