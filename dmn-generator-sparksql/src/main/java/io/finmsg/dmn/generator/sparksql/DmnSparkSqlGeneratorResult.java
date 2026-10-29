package io.finmsg.dmn.generator.sparksql;

import java.util.Map;
import java.util.Objects;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.SparkSession;

/**
 * Result containing generated Spark SQL CTE query files, Java runner code, and
 * schema.
 */
public record DmnSparkSqlGeneratorResult(Map<String, String> sqlFiles, Map<String, String> javaSources,
		StructType inputSchema, Map<String, SparkSqlDecisionUdf> udfs,
		Map<Integer, SparkSqlCapabilityAnalyzer.Capability> capabilities) {

	public DmnSparkSqlGeneratorResult(Map<String, String> sqlFiles, Map<String, String> javaSources,
			StructType inputSchema) {
		this(sqlFiles, javaSources, inputSchema, Map.of(), Map.of());
	}

	/**
	 * Register these model-specific functions before executing sqlFiles directly.
	 */
	public void registerUdfs(SparkSession spark) {
		udfs.forEach((name, udf) -> spark.udf().register(name, udf, udf.returnType()));
	}

	public DmnSparkSqlGeneratorResult {
		sqlFiles = Map.copyOf(Objects.requireNonNull(sqlFiles, "sqlFiles"));
		javaSources = Map.copyOf(Objects.requireNonNull(javaSources, "javaSources"));
		Objects.requireNonNull(inputSchema, "inputSchema");
		udfs = Map.copyOf(udfs);
		capabilities = Map.copyOf(capabilities);
	}
}
