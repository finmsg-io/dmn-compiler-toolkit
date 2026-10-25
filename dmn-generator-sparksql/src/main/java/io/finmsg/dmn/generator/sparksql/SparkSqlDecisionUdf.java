package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.util.*;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.types.DataType;

/**
 * Serializable executor-side evaluator. Model bytes and the immutable host
 * policy cross Spark's task boundary.
 */
public final class SparkSqlDecisionUdf implements UDF1<Row, Object> {
	private static final long serialVersionUID = 1L;
	private final byte[] modelPayload;
	private final io.finmsg.dmn.runtime.ExternalJavaPolicy policy;
	private final int decisionId;
	private final int[] inputSlots;
	private transient volatile RuntimeModel model;

	public SparkSqlDecisionUdf(RuntimeModel model, int decisionId) {
		this(model, decisionId, io.finmsg.dmn.runtime.ExternalJavaPolicy.denyAll());
	}

	public SparkSqlDecisionUdf(RuntimeModel model, int decisionId, io.finmsg.dmn.runtime.ExternalJavaPolicy policy) {
		this.policy = Objects.requireNonNull(policy, "policy");
		this.decisionId = decisionId;
		this.inputSlots = model.inputs().stream().mapToInt(RuntimeInput::valueSlot).toArray();
		this.modelPayload = SparkSqlPayloadCodec.encode(SparkSqlDecisionGraph.slice(model, decisionId));
	}

	/**
	 * Reconstruct a generated model on the driver; payloads require the same
	 * toolkit version.
	 */
	public static SparkSqlDecisionUdf fromModelPayload(String base64, int decisionId) {
		return fromModelPayload(base64, decisionId, io.finmsg.dmn.runtime.ExternalJavaPolicy.denyAll());
	}

	public static SparkSqlDecisionUdf fromModelPayload(String base64, int decisionId,
			io.finmsg.dmn.runtime.ExternalJavaPolicy policy) {
		return new SparkSqlDecisionUdf((RuntimeModel) SparkSqlPayloadCodec.decode(Base64.getDecoder().decode(base64)),
				decisionId, policy);
	}

	private RuntimeModel model() {
		RuntimeModel value = model;
		if (value == null) {
			synchronized (this) {
				value = model;
				if (value == null)
					model = value = (RuntimeModel) SparkSqlPayloadCodec.decode(modelPayload);
			}
		}
		return value;
	}

	public DataType returnType() {
		if (decision().type().kind() == io.finmsg.dmn.ir.RuntimeTypeKind.CONTEXT
				|| SparkSqlFallbackTransport.numeric(decision().type()))
			return org.apache.spark.sql.types.DataTypes.BinaryType;
		return SparkSqlFeelValueCodec.sparkType(decision().type());
	}

	private RuntimeDecision decision() {
		return model().decisions().stream().filter(d -> d.id() == decisionId).findFirst().orElseThrow();
	}

	/** Returns a FEEL Java value. Row fields are in RuntimeModel.inputs() order. */
	public Object evaluate(Row row) {
		Objects.requireNonNull(row, "input row");
		if (row.size() != inputSlots.length)
			throw new IllegalArgumentException("Expected " + inputSlots.length + " input columns, got " + row.size());
		Map<Integer, Object> inputs = new HashMap<>();
		model().inputs().forEach(input -> inputs.put(input.valueSlot(), null));
		for (int i = 0; i < inputSlots.length; i++)
			inputs.put(inputSlots[i], SparkSqlFeelValueCodec.fromSpark(row.get(i)));
		return new DmnRuntime(policy).evaluate(model(), inputs).decisionValue(decisionId);
	}

	@Override
	public Object call(Row row) {
		if (decision().type().kind() == io.finmsg.dmn.ir.RuntimeTypeKind.CONTEXT
				|| SparkSqlFallbackTransport.numeric(decision().type())) {
			Object result = evaluate(row);
			return result == null ? null : SparkSqlFeelValueCodec.encode(result);
		}
		return SparkSqlFeelValueCodec.toSpark(evaluate(row), decision().type());
	}
}
