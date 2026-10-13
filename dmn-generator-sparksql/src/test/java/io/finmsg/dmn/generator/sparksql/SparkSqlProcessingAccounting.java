package io.finmsg.dmn.generator.sparksql;

import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.ScalaUDF;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.WithCTE;
import org.apache.spark.sql.execution.QueryExecution;
import org.apache.spark.sql.execution.SparkPlan;
import org.apache.spark.sql.execution.adaptive.AdaptiveSparkPlanExec;
import org.apache.spark.sql.util.QueryExecutionListener;

/** Counts completed SQL actions, rather than generated plans or Spark jobs. */
final class SparkSqlProcessingAccounting implements QueryExecutionListener {
	record Counts(long nativeActions, long fallbackActions, long unknownActions, long failedActions) {
	}
	private long nativeActions;
	private long fallbackActions;
	private long unknownActions;
	private long failedActions;

	static SparkSqlProcessingAccounting attach(SparkSession spark) {
		var accounting = new SparkSqlProcessingAccounting();
		spark.listenerManager().register(accounting);
		return accounting;
	}

	@Override
	public void onSuccess(String function, QueryExecution query, long durationNs) {
		record(function, query, false);
	}

	@Override
	public void onFailure(String function, QueryExecution query, Exception exception) {
		record(function, query, true);
	}

	private synchronized void record(String function, QueryExecution query, boolean failed) {
		// View registration and other eager setup commands do not process a
		// generator result. Each head/collect/count action is one observation.
		if (function.equals("command"))
			return;
		if (failed)
			failedActions++;
		try {
			// Catalyst can evaluate deterministic fallback UDFs while optimizing a
			// local relation. Their absence from the final physical plan does not
			// make that query native: inspect the analyzed route as well.
			if (containsFallback(query.analyzed()) || containsFallback(query.executedPlan()))
				fallbackActions++;
			else
				nativeActions++;
		} catch (RuntimeException exception) {
			unknownActions++;
		}
	}

	private static boolean containsFallback(SparkPlan plan) {
		if (plan instanceof AdaptiveSparkPlanExec adaptive)
			return containsFallback(adaptive.executedPlan());
		var expressions = plan.expressions().iterator();
		while (expressions.hasNext()) {
			if (containsFallback(expressions.next()))
				return true;
		}
		var children = plan.children().iterator();
		while (children.hasNext()) {
			if (containsFallback(children.next()))
				return true;
		}
		return false;
	}

	private static boolean containsFallback(LogicalPlan plan) {
		if (plan instanceof WithCTE with) {
			var definitions = with.cteDefs().iterator();
			while (definitions.hasNext()) {
				if (containsFallback(definitions.next()))
					return true;
			}
		}
		var expressions = plan.expressions().iterator();
		while (expressions.hasNext()) {
			if (containsFallback(expressions.next()))
				return true;
		}
		var children = plan.children().iterator();
		while (children.hasNext()) {
			if (containsFallback(children.next()))
				return true;
		}
		return false;
	}

	private static boolean containsFallback(Expression expression) {
		if (expression instanceof ScalaUDF udf && udf.udfName().isDefined()
				&& udf.udfName().get().matches("dmn_[0-9a-f]+_-?[0-9]+"))
			return true;
		var children = expression.children().iterator();
		while (children.hasNext()) {
			if (containsFallback(children.next()))
				return true;
		}
		return false;
	}

	void report(SparkSession spark, String suite) throws TimeoutException {
		Counts counts = snapshot(spark);
		spark.listenerManager().unregister(this);
		long denominator = counts.nativeActions() + counts.fallbackActions() + counts.unknownActions();
		if (denominator == 0) {
			System.out.printf(
					"SPARK SQL PROCESSING [%s]: native=0, fallback=0, denominator=0, rates=N/A (no SQL actions)%n",
					suite);
			return;
		}
		System.out.printf(Locale.ROOT,
				"SPARK SQL PROCESSING [%s]: native=%d (%.2f%%), fallback=%d (%.2f%%), unknown=%d, denominator=%d, failed=%d%n",
				suite, counts.nativeActions(), rate(counts.nativeActions(), denominator), counts.fallbackActions(),
				rate(counts.fallbackActions(), denominator), counts.unknownActions(), denominator,
				counts.failedActions());
	}

	Counts snapshot(SparkSession spark) throws TimeoutException {
		// Query notifications are asynchronous; drain them before taking counts.
		spark.sparkContext().listenerBus().waitUntilEmpty(10000);
		synchronized (this) {
			return new Counts(nativeActions, fallbackActions, unknownActions, failedActions);
		}
	}

	private static double rate(long count, long denominator) {
		return denominator == 0 ? 0 : 100.0 * count / denominator;
	}
}
