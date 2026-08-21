package io.finmsg.dmn.ir;

import java.util.Arrays;
import java.util.Optional;

/** Stable evaluator dispatch IDs for standard FEEL 1.5 built-in functions. */
public enum RuntimeBuiltinOperation {
	NOT(0, "not"), STRING(1, "string"), NUMBER(2, "number"), DATE(3, "date"), TIME(4, "time"), DATE_AND_TIME(5,
			"date and time"), DURATION(6, "duration"), COUNT(7, "count"), SUM(8, "sum"), MIN(9, "min"), MAX(10,
					"max"), ABS(11, "abs"), SUBSTRING(12, "substring"), SUBSTRING_BEFORE(13,
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
																																											"list replace"), IS(
																																													38,
																																													"is"), MEAN(
																																															39,
																																															"mean"), MEDIAN(
																																																	40,
																																																	"median"), MODE(
																																																			41,
																																																			"mode"), STDDEV(
																																																					42,
																																																					"stddev"), SQRT(
																																																							43,
																																																							"sqrt"), EXP(
																																																									44,
																																																									"exp"), LOG(
																																																											45,
																																																											"log"), MODULO(
																																																													46,
																																																													"modulo"), EVEN(
																																																															47,
																																																															"even"), ODD(
																																																																	48,
																																																																	"odd"), PRODUCT(
																																																																			49,
																																																																			"product"), ALL(
																																																																					50,
																																																																					"all"), ANY(
																																																																							51,
																																																																							"any"), INSERT_BEFORE(
																																																																									52,
																																																																									"insert before"), REMOVE(
																																																																											53,
																																																																											"remove"), APPEND(
																																																																													54,
																																																																													"append"), UNION(
																																																																															55,
																																																																															"union"), LIST_CONTAINS(
																																																																																	56,
																																																																																	"list contains"), ROUND_UP(
																																																																																			57,
																																																																																			"round up"), ROUND_DOWN(
																																																																																					58,
																																																																																					"round down"), STRING_JOIN(
																																																																																							59,
																																																																																							"string join"), DAY_AND_TIME_DURATION(
																																																																																									60,
																																																																																									"day and time duration"), GET_ENTRIES(
																																																																																											61,
																																																																																											"get entries"), GET_VALUE(
																																																																																													62,
																																																																																													"get value"), RANGE(
																																																																																															63,
																																																																																															"range"), DAY_OF_YEAR(
																																																																																																	64,
																																																																																																	"day of year"), DAY_OF_WEEK(
																																																																																																			65,
																																																																																																			"day of week"), WEEK_OF_YEAR(
																																																																																																					66,
																																																																																																					"week of year"), MONTH_OF_YEAR(
																																																																																																							67,
																																																																																																							"month of year"), CONTEXT(
																																																																																																									68,
																																																																																																									"context"), CONTEXT_PUT(
																																																																																																											69,
																																																																																																											"context put"), CONTEXT_MERGE(
																																																																																																													70,
																																																																																																													"context merge"), PMT(
																																																																																																															71,
																																																																																																															"pmt"), PMT2(
																																																																																																																	72,
																																																																																																																	"pmt2"), NOW(
																																																																																																																			73,
																																																																																																																			"now"), TODAY(
																																																																																																																					74,
																																																																																																																					"today"), ROUND_HALF_DOWN(
																																																																																																																							75,
																																																																																																																							"round half down"), DURING(
																																																																																																																									76,
																																																																																																																									"during"), BEFORE(
																																																																																																																											77,
																																																																																																																											"before"), AFTER(
																																																																																																																													78,
																																																																																																																													"after"), MEETS(
																																																																																																																															79,
																																																																																																																															"meets"), MET_BY(
																																																																																																																																	80,
																																																																																																																																	"met by"), OVERLAPS(
																																																																																																																																			81,
																																																																																																																																			"overlaps"), OVERLAPS_BEFORE(
																																																																																																																																					82,
																																																																																																																																					"overlaps before"), OVERLAPS_AFTER(
																																																																																																																																							83,
																																																																																																																																							"overlaps after"), FINISHES(
																																																																																																																																									84,
																																																																																																																																									"finishes"), FINISHED_BY(
																																																																																																																																											85,
																																																																																																																																											"finished by"), INCLUDES(
																																																																																																																																													86,
																																																																																																																																													"includes"), STARTS(
																																																																																																																																															87,
																																																																																																																																															"starts"), STARTED_BY(
																																																																																																																																																	88,
																																																																																																																																																	"started by"), COINCIDES(
																																																																																																																																																			89,
																																																																																																																																																			"coincides");
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
