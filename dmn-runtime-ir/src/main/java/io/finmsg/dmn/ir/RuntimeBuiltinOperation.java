package io.finmsg.dmn.ir;

import java.util.Arrays;
import java.util.Optional;

/** Stable evaluator dispatch IDs for standard FEEL 1.5 built-in functions. */
public enum RuntimeBuiltinOperation {
	NOT(0, "not"), STRING(1, "string"), NUMBER(2, "number"), DATE(3, "date"), TIME(4, "time"), DATE_AND_TIME(5,
			"date and time"), DURATION(6, "duration"), COUNT(7, "count"), SUM(8, "sum"), MIN(9,
					"min"), MAX(10, "max"), ABS(11, "abs"), SUBSTRING(12, "substring"), SUBSTRING_BEFORE(13,
							"substring before"), SUBSTRING_AFTER(14, "substring after"), STRING_LENGTH(15,
									"string length"), UPPER_CASE(16, "upper case"), LOWER_CASE(17,
											"lower case"), CONTAINS(18, "contains"), STARTS_WITH(19,
													"starts with"), ENDS_WITH(20, "ends with"), MATCHES(21,
															"matches"), REPLACE(22, "replace"), SPLIT(23,
																	"split"), FLOOR(24, "floor"), CEILING(25,
																			"ceiling"), DECIMAL(26,
																					"decimal"), ROUND_HALF_UP(27,
																							"round half up"), ROUND_HALF_EVEN(
																									28,
																									"round half even"), SUBLIST(
																											29,
																											"sublist"), CONCATENATE(
																													30,
																													"concatenate"), DISTINCT_VALUES(
																															31,
																															"distinct values"), FLATTEN(
																																	32,
																																	"flatten"), REVERSE(
																																			33,
																																			"reverse"), INDEX_OF(
																																					34,
																																					"index of"), YEARS_AND_MONTHS_DURATION(
																																							35,
																																							"years and months duration"), SORT(
																																									36,
																																									"sort"), LIST_REPLACE(
																																											37,
																																											"list replace");

	private final int id;
	private final String feelName;

	RuntimeBuiltinOperation(int id, String feelName) {
		this.id = id;
		this.feelName = feelName;
	}

	public int id() {
		return id;
	}
	public String feelName() {
		return feelName;
	}

	public static Optional<RuntimeBuiltinOperation> find(String name) {
		String normalized = name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
		return Arrays.stream(values()).filter(value -> value.feelName.equals(normalized)).findFirst();
	}
}
