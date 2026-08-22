package io.finmsg.dmn.compiler;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.model.*;
import io.finmsg.dmn.runtime.DmnEvaluationResult;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAmount;
import java.util.*;

/**
 * Immutable, thread-safe facade exposing name-based and Protobuf-native DMN
 * evaluation.
 */
public final class DmnCompiledModel {

	private final RuntimeOptimizedModel optimizedModel;
	private final CompiledModelMetadata metadata;
	private final Map<String, Integer> inputSlots;
	private final Map<String, Integer> decisionSlots;
	private final Map<String, RuntimeType> inputTypesMap;
	private final Map<String, RuntimeType> decisionTypesMap;
	private final DmnRuntime runtime = new DmnRuntime();

	public DmnCompiledModel(String namespace, String modelName, RuntimeOptimizedModel optimizedModel,
			Map<String, Integer> inputSlots, Map<String, Integer> decisionSlots, Map<String, RuntimeType> inputTypes,
			Map<String, RuntimeType> decisionTypes) {
		this.optimizedModel = Objects.requireNonNull(optimizedModel, "optimizedModel");
		this.inputSlots = Map.copyOf(inputSlots);
		this.decisionSlots = Map.copyOf(decisionSlots);
		this.inputTypesMap = Map.copyOf(inputTypes);
		this.decisionTypesMap = Map.copyOf(decisionTypes);

		Map<String, String> inTypeNames = new LinkedHashMap<>();
		inputTypes.forEach((k, v) -> inTypeNames.put(k, v.kind().name()));
		Map<String, String> decTypeNames = new LinkedHashMap<>();
		decisionTypes.forEach((k, v) -> decTypeNames.put(k, v.kind().name()));

		this.metadata = CompiledModelMetadata.newBuilder().setNamespace(namespace == null ? "" : namespace)
				.setModelName(modelName == null ? "" : modelName).putAllInputTypes(inTypeNames)
				.putAllDecisionTypes(decTypeNames).build();
	}

	public CompiledModelMetadata metadata() {
		return metadata;
	}

	public String namespace() {
		return metadata.getNamespace();
	}

	public String modelName() {
		return metadata.getModelName();
	}

	public Map<String, RuntimeType> inputTypes() {
		return inputTypesMap;
	}

	public Map<String, RuntimeType> decisionTypes() {
		return decisionTypesMap;
	}

	public RuntimeOptimizedModel optimizedModel() {
		return optimizedModel;
	}

	/** Returns the mapping of input data node names to their value slot indices. */
	public Map<String, Integer> inputSlots() {
		return inputSlots;
	}

	/** Returns the mapping of decision node names to their value slot indices. */
	public Map<String, Integer> decisionSlots() {
		return decisionSlots;
	}

	/** Evaluates model using Protobuf EvaluationRequest payload. */
	public EvaluationResponse evaluate(EvaluationRequest request) {
		long startTime = System.nanoTime();
		Map<String, Object> nativeInputs = new LinkedHashMap<>();
		request.getInputsMap().forEach((name, value) -> nativeInputs.put(name, toNativeValue(value)));

		Map<Integer, Object> slotInputs = new HashMap<>();
		for (Map.Entry<String, Object> entry : nativeInputs.entrySet()) {
			Integer slot = inputSlots.get(entry.getKey());
			if (slot != null) {
				slotInputs.put(slot, entry.getValue());
			}
		}

		DmnEvaluationResult result = runtime.evaluate(optimizedModel.model(), slotInputs);

		Set<String> filterDecisions = new HashSet<>(request.getRequestedDecisionsList());
		EvaluationResponse.Builder response = EvaluationResponse.newBuilder();

		decisionSlots.forEach((name, slot) -> {
			if (filterDecisions.isEmpty() || filterDecisions.contains(name)) {
				Object val = result.value(slot);
				response.putDecisionResults(name, toProtobufValue(val));
			}
		});

		response.setExecutionTimeNanos(System.nanoTime() - startTime);
		return response.build();
	}

	/** Evaluates model using native Java Map of inputs. */
	public EvaluationResponse evaluateInputs(Map<String, Object> namedInputs) {
		EvaluationRequest.Builder request = EvaluationRequest.newBuilder().setNamespace(metadata.getNamespace())
				.setModelName(metadata.getModelName());
		if (namedInputs != null) {
			namedInputs.forEach((k, v) -> request.putInputs(k, toProtobufValue(v)));
		}
		return evaluate(request.build());
	}

	/** Evaluates a single decision by name. */
	public Object evaluateDecision(String decisionName, Map<String, Object> namedInputs) {
		Objects.requireNonNull(decisionName, "decisionName");
		Integer slot = decisionSlots.get(decisionName);
		if (slot == null) {
			throw new IllegalArgumentException("Unknown decision name '" + decisionName + "' in model");
		}
		EvaluationResponse response = evaluateInputs(namedInputs);
		Value val = response.getDecisionResultsMap().get(decisionName);
		return val == null ? null : toNativeValue(val);
	}

	public static Value toProtobufValue(Object value) {
		Value.Builder builder = Value.newBuilder();
		if (value == null) {
			return builder.setNullValue(NullValue.NULL_VALUE).build();
		}
		if (value instanceof Boolean b) {
			return builder.setBooleanValue(b).build();
		}
		if (value instanceof BigDecimal d) {
			return builder.setNumberValue(d.toPlainString()).build();
		}
		if (value instanceof Number n) {
			return builder.setNumberValue(n.toString()).build();
		}
		if (value instanceof String s) {
			return builder.setStringValue(s).build();
		}
		if (value instanceof LocalDate d) {
			return builder.setDateValue(d.toString()).build();
		}
		if (value instanceof LocalTime t) {
			return builder.setTimeValue(t.toString()).build();
		}
		if (value instanceof OffsetTime t) {
			return builder.setTimeValue(t.toString()).build();
		}
		if (value instanceof LocalDateTime dt) {
			return builder.setDateTimeValue(dt.toString()).build();
		}
		if (value instanceof OffsetDateTime dt) {
			return builder.setDateTimeValue(dt.toString()).build();
		}
		if (value instanceof TemporalAmount d) {
			return builder.setDurationValue(d.toString()).build();
		}
		if (value instanceof List<?> list) {
			ListValue.Builder listBuilder = ListValue.newBuilder();
			list.forEach(elem -> listBuilder.addElements(toProtobufValue(elem)));
			return builder.setListValue(listBuilder).build();
		}
		if (value instanceof Map<?, ?> map) {
			ContextValue.Builder ctxBuilder = ContextValue.newBuilder();
			map.forEach((k, v) -> ctxBuilder.putEntries(String.valueOf(k), toProtobufValue(v)));
			return builder.setContextValue(ctxBuilder).build();
		}
		return builder.setStringValue(value.toString()).build();
	}

	public static Object toNativeValue(Value value) {
		if (value == null) {
			return null;
		}
		return switch (value.getValueKindCase()) {
			case NULL_VALUE, VALUEKIND_NOT_SET -> null;
			case BOOLEAN_VALUE -> value.getBooleanValue();
			case STRING_VALUE -> value.getStringValue();
			case NUMBER_VALUE -> new BigDecimal(value.getNumberValue());
			case DATE_VALUE -> LocalDate.parse(value.getDateValue());
			case TIME_VALUE -> value.getTimeValue().matches(".*[Z+-][0-9:]*$")
					? OffsetTime.parse(value.getTimeValue())
					: LocalTime.parse(value.getTimeValue());
			case DATE_TIME_VALUE -> value.getDateTimeValue().matches(".*[Z+-][0-9:]*$")
					? OffsetDateTime.parse(value.getDateTimeValue())
					: LocalDateTime.parse(value.getDateTimeValue());
			case DURATION_VALUE -> value.getDurationValue().contains("T")
					? Duration.parse(value.getDurationValue())
					: Period.parse(value.getDurationValue());
			case LIST_VALUE ->
				value.getListValue().getElementsList().stream().map(DmnCompiledModel::toNativeValue).toList();
			case CONTEXT_VALUE -> {
				Map<String, Object> map = new LinkedHashMap<>();
				value.getContextValue().getEntriesMap().forEach((k, v) -> map.put(k, toNativeValue(v)));
				yield Map.copyOf(map);
			}
			case RANGE_VALUE -> null;
		};
	}
}
