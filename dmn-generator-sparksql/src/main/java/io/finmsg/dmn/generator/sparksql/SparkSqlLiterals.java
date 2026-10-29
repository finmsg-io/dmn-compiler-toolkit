package io.finmsg.dmn.generator.sparksql;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;

/** Text expressions independent of spark.sql.parser.escapedStringLiterals. */
final class SparkSqlLiterals {
	private SparkSqlLiterals() {
	}

	static String string(String value) {
		Objects.requireNonNull(value, "value");
		if (value.chars().noneMatch(c -> c == '\'' || c == '\\' || c < 32 || c == 127)) {
			return "'" + value + "'";
		}
		return "CAST(X'" + HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8)) + "' AS STRING)";
	}
}
