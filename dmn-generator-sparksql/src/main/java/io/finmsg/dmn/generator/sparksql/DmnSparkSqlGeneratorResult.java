package io.finmsg.dmn.generator.sparksql;

import java.util.Map;
import java.util.Objects;
import org.apache.spark.sql.types.StructType;

/**
 * Result containing generated Spark SQL CTE query files, Java runner code, and
 * schema.
 */
public record DmnSparkSqlGeneratorResult(Map<String, String> sqlFiles, Map<String, String> javaSources,
		StructType inputSchema) {

	public DmnSparkSqlGeneratorResult {
		sqlFiles = Map.copyOf(Objects.requireNonNull(sqlFiles, "sqlFiles"));
		javaSources = Map.copyOf(Objects.requireNonNull(javaSources, "javaSources"));
		Objects.requireNonNull(inputSchema, "inputSchema");
	}
}
