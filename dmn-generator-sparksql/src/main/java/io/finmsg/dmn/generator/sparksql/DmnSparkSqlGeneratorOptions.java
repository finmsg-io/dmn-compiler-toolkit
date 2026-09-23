package io.finmsg.dmn.generator.sparksql;

import java.util.Map;
import java.util.Objects;

/** Configuration options for DMN Spark SQL generation. */
public record DmnSparkSqlGeneratorOptions(
		String packageName,
		String className,
		String inputTableName,
		boolean includeJavaRunner,
		Map<Integer, String> customSlotNames) {

	public DmnSparkSqlGeneratorOptions {
		Objects.requireNonNull(packageName, "packageName");
		Objects.requireNonNull(className, "className");
		Objects.requireNonNull(inputTableName, "inputTableName");
		customSlotNames = customSlotNames != null ? Map.copyOf(customSlotNames) : Map.of();
	}

	public DmnSparkSqlGeneratorOptions(String packageName, String className, String inputTableName,
			boolean includeJavaRunner) {
		this(packageName, className, inputTableName, includeJavaRunner, Map.of());
	}

	public static DmnSparkSqlGeneratorOptions defaults() {
		return new DmnSparkSqlGeneratorOptions("io.finmsg.dmn.spark", "DmnSparkSqlRunner", "input_table", true,
				Map.of());
	}
}
