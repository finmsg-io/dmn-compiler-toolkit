package io.finmsg.dmn.ir;

import io.finmsg.dmn.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lowers decision-table clauses, rules, unary tests, and hit-policy metadata.
 */
final class RuntimeDecisionTableLowerer {
	private RuntimeDecisionTableLowerer() {
	}

	static Optional<RuntimeDecisionTable> lowerDecisionTable(Decision decision,
			List<io.finmsg.dmn.semantic.analysis.DmnSymbolBinding> bindings, Map<String, Integer> slots,
			Map<String, ItemDefinition> itemTypes, Map<String, LocalSlotAddress> localSlots, int[] nextLocalSlot) {
		if (!decision.hasLogic() || !decision.getLogic().hasDecisionTable()) {
			return Optional.empty();
		}
		DecisionTable table = decision.getLogic().getDecisionTable();
		String path = "definitions/decision[" + decision.getNode().getName() + "]/logic/decisionTable";
		List<RuntimeDecisionTableInput> inputs = new ArrayList<>();
		for (int index = 0; index < table.getInputsCount(); index++) {
			InputClause input = table.getInputs(index);
			if (!input.hasInputExpression() || !input.getInputExpression().hasParsed()) {
				throw new RuntimeIrLoweringException(
						"Decision-table input requires parsed FEEL at " + path + "/input[" + index + "].");
			}
			String inputPath = path + "/input[" + index + "]";
			Optional<RuntimeUnaryTests> allowed = input.hasInputValues() && input.getInputValues().hasParsed()
					? Optional.of(RuntimeExpressionLowerer.lowerUnaryTests(
							input.getInputValues().getParsed().getAst().getUnaryTests(), inputPath + "/inputValues",
							bindings, slots, itemTypes, localSlots, nextLocalSlot))
					: Optional.empty();
			inputs.add(new RuntimeDecisionTableInput(
					RuntimeExpressionLowerer.lowerExpression(input.getInputExpression().getParsed().getAst(), inputPath,
							bindings, slots, itemTypes, localSlots, nextLocalSlot),
					allowed, RuntimeTypeLowerer.lower(input.getType(), itemTypes)));
		}
		List<RuntimeDecisionTableOutput> outputs = new ArrayList<>();
		for (int index = 0; index < table.getOutputsCount(); index++) {
			OutputClause output = table.getOutputs(index);
			String outputPath = path + "/output[" + index + "]";
			Optional<RuntimeUnaryTests> allowed = output.hasOutputValues() && output.getOutputValues().hasParsed()
					? Optional.of(RuntimeExpressionLowerer.lowerUnaryTests(
							output.getOutputValues().getParsed().getAst().getUnaryTests(), outputPath + "/outputValues",
							bindings, slots, itemTypes, localSlots, nextLocalSlot))
					: Optional.empty();
			Optional<RuntimeExpression> defaultValue = Optional.empty();
			if (output.hasDefaultOutputEntry() && output.getDefaultOutputEntry().hasParsed()) {
				ExpressionParsed parsed = output.getDefaultOutputEntry().getParsed();
				if (!parsed.hasFeel()) {
					throw new RuntimeIrLoweringException(
							"Boxed default outputs are not yet supported at " + outputPath + ".");
				}
				defaultValue = Optional.of(RuntimeExpressionLowerer.lowerExpression(parsed.getFeel().getAst(),
						outputPath + "/defaultOutputEntry", bindings, slots, itemTypes, localSlots, nextLocalSlot));
			}
			outputs.add(new RuntimeDecisionTableOutput(
					output.getNode().getName().isBlank() ? Optional.empty() : Optional.of(output.getNode().getName()),
					RuntimeTypeLowerer.lower(output.getType(), itemTypes), allowed, defaultValue));
		}
		List<RuntimeDecisionTableRule> rules = new ArrayList<>();
		for (int ruleIndex = 0; ruleIndex < table.getRulesCount(); ruleIndex++) {
			DecisionRule rule = table.getRules(ruleIndex);
			String rulePath = path + "/rule[" + ruleIndex + "]";
			List<RuntimeUnaryTests> inputEntries = new ArrayList<>();
			for (int index = 0; index < rule.getInputEntriesCount(); index++) {
				UnaryTest entry = rule.getInputEntries(index);
				if (!entry.hasParsed()) {
					throw new RuntimeIrLoweringException(
							"Decision-table input entry requires parsed FEEL at " + rulePath + ".");
				}
				inputEntries.add(RuntimeExpressionLowerer.lowerUnaryTests(entry.getParsed().getTests(),
						rulePath + "/inputEntry[" + index + "]", bindings, slots, itemTypes, localSlots,
						nextLocalSlot));
			}
			List<RuntimeExpression> outputEntries = new ArrayList<>();
			for (int index = 0; index < rule.getOutputEntriesCount(); index++) {
				Feel entry = rule.getOutputEntries(index);
				if (!entry.hasParsed()) {
					throw new RuntimeIrLoweringException(
							"Decision-table output entry requires parsed FEEL at " + rulePath + ".");
				}
				outputEntries.add(RuntimeExpressionLowerer.lowerExpression(entry.getParsed().getAst(),
						rulePath + "/outputEntry[" + index + "]", bindings, slots, itemTypes, localSlots,
						nextLocalSlot));
			}
			rules.add(new RuntimeDecisionTableRule(rule.getRuleIndex(), inputEntries, outputEntries,
					rule.getAnnotationEntriesList().stream().map(RuleAnnotation::getText).toList()));
		}
		return Optional.of(new RuntimeDecisionTable(hitPolicy(table.getHitPolicy().getPolicy()),
				aggregation(table.getHitPolicy().getAggregation()), inputs, outputs, rules,
				table.getAnnotationsCount()));
	}

	static RuntimeHitPolicy hitPolicy(HitPolicy policy) {
		return switch (policy) {
			case HIT_POLICY_UNSPECIFIED, HIT_POLICY_UNIQUE -> RuntimeHitPolicy.UNIQUE;
			case HIT_POLICY_FIRST -> RuntimeHitPolicy.FIRST;
			case HIT_POLICY_PRIORITY -> RuntimeHitPolicy.PRIORITY;
			case HIT_POLICY_ANY -> RuntimeHitPolicy.ANY;
			case HIT_POLICY_COLLECT -> RuntimeHitPolicy.COLLECT;
			case HIT_POLICY_RULE_ORDER -> RuntimeHitPolicy.RULE_ORDER;
			case HIT_POLICY_OUTPUT_ORDER -> RuntimeHitPolicy.OUTPUT_ORDER;
			case UNRECOGNIZED ->
				throw new RuntimeIrLoweringException("Unsupported decision-table hit policy " + policy + ".");
		};
	}

	static Optional<RuntimeAggregation> aggregation(Aggregation aggregation) {
		return switch (aggregation) {
			case AGGREGATION_UNSPECIFIED -> Optional.empty();
			case AGGREGATION_SUM -> Optional.of(RuntimeAggregation.SUM);
			case AGGREGATION_MIN -> Optional.of(RuntimeAggregation.MIN);
			case AGGREGATION_MAX -> Optional.of(RuntimeAggregation.MAX);
			case AGGREGATION_COUNT -> Optional.of(RuntimeAggregation.COUNT);
			case UNRECOGNIZED ->
				throw new RuntimeIrLoweringException("Unsupported decision-table aggregation " + aggregation + ".");
		};
	}
}
