package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuntimeDecisionTable(RuntimeHitPolicy hitPolicy, Optional<RuntimeAggregation> aggregation,
		List<RuntimeDecisionTableInput> inputs, List<RuntimeDecisionTableOutput> outputs,
		List<RuntimeDecisionTableRule> rules, int annotationColumnCount) {
	public RuntimeDecisionTable {
		Objects.requireNonNull(hitPolicy, "hitPolicy");
		aggregation = Objects.requireNonNull(aggregation, "aggregation");
		inputs = List.copyOf(inputs);
		outputs = List.copyOf(outputs);
		rules = List.copyOf(rules);
		if (inputs.isEmpty() || outputs.isEmpty()) {
			throw new IllegalArgumentException("Decision tables require inputs and outputs.");
		}
		if (annotationColumnCount < 0) {
			throw new IllegalArgumentException("annotationColumnCount must not be negative.");
		}
		if (aggregation.isPresent() && hitPolicy != RuntimeHitPolicy.COLLECT) {
			throw new IllegalArgumentException("Aggregation is only valid with COLLECT hit policy.");
		}
		for (int index = 0; index < rules.size(); index++) {
			RuntimeDecisionTableRule rule = rules.get(index);
			if (rule.ruleIndex() != index) {
				throw new IllegalArgumentException("Decision-table rule indices must be contiguous.");
			}
			if (rule.inputEntries().size() != inputs.size() || rule.outputEntries().size() != outputs.size()
					|| rule.annotations().size() != annotationColumnCount) {
				throw new IllegalArgumentException(
						"Decision-table rule widths must match inputs, outputs, and annotations.");
			}
		}
	}
}
