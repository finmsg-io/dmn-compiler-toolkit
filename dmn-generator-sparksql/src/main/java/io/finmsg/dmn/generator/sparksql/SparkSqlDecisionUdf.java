package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.util.*;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.types.DataType;

/**
 * Serializable executor-side evaluator. Only the versioned model bytes cross
 * Spark's task boundary.
 */
public final class SparkSqlDecisionUdf implements UDF1<Row, Object> {
	private static final long serialVersionUID = 1L;
	private final byte[] modelPayload;
	private final int decisionId;
	private final int[] inputSlots;
	private transient volatile RuntimeModel model;

	public SparkSqlDecisionUdf(RuntimeModel model, int decisionId) {
		this.decisionId = decisionId;
		this.inputSlots = model.inputs().stream().mapToInt(RuntimeInput::valueSlot).toArray();
		this.modelPayload = SparkSqlPayloadCodec.encode(SparkSqlDecisionGraph.slice(model, decisionId));
	}

	/**
	 * Reconstruct a generated model on the driver; payloads require the same
	 * toolkit version.
	 */
	public static SparkSqlDecisionUdf fromModelPayload(String base64, int decisionId) {
		return new SparkSqlDecisionUdf((RuntimeModel) SparkSqlPayloadCodec.decode(Base64.getDecoder().decode(base64)),
				decisionId);
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
		if (decision().type().kind() == io.finmsg.dmn.ir.RuntimeTypeKind.CONTEXT)
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
		return new DmnRuntime().evaluate(model(), inputs).decisionValue(decisionId);
	}

	@Override
	public Object call(Row row) {
		if (decision().type().kind() == io.finmsg.dmn.ir.RuntimeTypeKind.CONTEXT)
			return SparkSqlFeelValueCodec.encode(evaluate(row));
		return SparkSqlFeelValueCodec.toSpark(evaluate(row), decision().type());
	}
}
