package io.finmsg.dmn.generator.sparksql;

import java.time.*;
import java.time.zone.*;
import java.util.*;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/**
 * Reference zone-rule metadata only; SQL resolves offsets against temporal
 * values.
 */
final class SparkSqlTemporalZoneFacts {
	static StructType transitionType() {
		return new StructType().add("instant", DataTypes.LongType).add("before", DataTypes.IntegerType).add("after",
				DataTypes.IntegerType);
	}
	static StructType ruleType() {
		return new StructType().add("month", DataTypes.IntegerType).add("dom", DataTypes.IntegerType)
				.add("dow", DataTypes.IntegerType).add("second", DataTypes.IntegerType)
				.add("end", DataTypes.BooleanType).add("definition", DataTypes.StringType)
				.add("standard", DataTypes.IntegerType).add("before", DataTypes.IntegerType)
				.add("after", DataTypes.IntegerType);
	}
	static StructType type() {
		return new StructType().add("id", DataTypes.StringType).add("initial", DataTypes.IntegerType)
				.add("history", DataTypes.createArrayType(transitionType(), false))
				.add("rules", DataTypes.createArrayType(ruleType(), false));
	}
	static Object row(String token) {
		if (token == null)
			return RowFactory.create(null, 0, List.of(), List.of());
		try {
			var zone = ZoneId.of(token);
			var rules = zone.getRules();
			var history = new ArrayList<Object>();
			var recurring = new ArrayList<Object>();
			for (var t : rules.getTransitions())
				history.add(RowFactory.create(t.toEpochSecond(), t.getOffsetBefore().getTotalSeconds(),
						t.getOffsetAfter().getTotalSeconds()));
			for (var r : rules.getTransitionRules())
				recurring.add(RowFactory.create(r.getMonth().getValue(), r.getDayOfMonthIndicator(),
						r.getDayOfWeek() == null ? 0 : r.getDayOfWeek().getValue(), r.getLocalTime().toSecondOfDay(),
						r.isMidnightEndOfDay(), r.getTimeDefinition().name(), r.getStandardOffset().getTotalSeconds(),
						r.getOffsetBefore().getTotalSeconds(), r.getOffsetAfter().getTotalSeconds()));
			int initial = rules.getTransitions().isEmpty()
					? rules.getOffset(Instant.EPOCH).getTotalSeconds()
					: rules.getTransitions().getFirst().getOffsetBefore().getTotalSeconds();
			return RowFactory.create(zone.getId(), initial, history, recurring);
		} catch (DateTimeException invalid) {
			return RowFactory.create(null, 0, List.of(), List.of());
		}
	}
	static String nil() {
		return "named_struct('id',CAST(NULL AS STRING),'initial',0,'history',CAST(array() AS ARRAY<STRUCT<instant:BIGINT,before:INT,after:INT>>),'rules',CAST(array() AS ARRAY<STRUCT<month:INT,dom:INT,dow:INT,second:INT,end:BOOLEAN,definition:STRING,standard:INT,before:INT,after:INT>>))";
	}
	static String epoch(String year, String month, String day) {
		return "CAST(365L*(" + year + "-1L)+floor((" + year + "-1L)/4.0)-floor((" + year + "-1L)/100.0)+floor((" + year
				+ "-1L)/400.0)+element_at(array(0,31,59,90,120,151,181,212,243,273,304,334),CAST(" + month
				+ " AS INT))+" + day + "+CASE WHEN " + month + ">2 AND pmod(" + year + ",4)=0 AND (pmod(" + year
				+ ",100)<>0 OR pmod(" + year + ",400)=0) THEN 1 ELSE 0 END-719163L AS BIGINT)";
	}
	static String days(String year, String month) {
		return "element_at(array(31,CASE WHEN pmod(" + year + ",4)=0 AND (pmod(" + year + ",100)<>0 OR pmod(" + year
				+ ",400)=0) THEN 29 ELSE 28 END,31,30,31,30,31,31,30,31,30,31),CAST(" + month + " AS INT))";
	}
	static String offset(String zone, String year, String day, String second) {
		String date = epoch("zone_year", "zone_rule.month", "zone_dom");
		String weekday = "pmod(zone_base+3L,7L)+1L";
		String adjusted = "zone_base + CASE WHEN zone_rule.dow=0 THEN 0L WHEN zone_rule.dom<0 THEN -pmod(" + weekday
				+ "-zone_rule.dow,7L) ELSE pmod(zone_rule.dow-(" + weekday
				+ "),7L) END + CASE WHEN zone_rule.end THEN 1L ELSE 0L END";
		String before = "zone_date*86400L+zone_rule.second+CASE zone_rule.definition WHEN 'UTC' THEN zone_rule.before WHEN 'STANDARD' THEN zone_rule.before-zone_rule.standard ELSE 0 END";
		String transition = "named_struct('instant',CAST(zone_before-zone_rule.before AS BIGINT),'before',zone_rule.before,'after',zone_rule.after)";
		transition = SparkSqlDecimalSql.let("zone_before", before, transition);
		transition = SparkSqlDecimalSql.let("zone_date", adjusted, transition);
		transition = SparkSqlDecimalSql.let("zone_base", date, transition);
		transition = SparkSqlDecimalSql.let("zone_dom", "CASE WHEN zone_rule.dom<0 THEN "
				+ days("zone_year", "zone_rule.month") + "+zone_rule.dom+1 ELSE zone_rule.dom END", transition);
		String future = "flatten(transform(sequence(CAST(" + year + "-1 AS BIGINT),CAST(" + year
				+ "+1 AS BIGINT)),zone_year -> transform(" + zone + ".rules,zone_rule -> " + transition + ")))";
		String last = "coalesce(try_element_at(" + zone + ".history,-1).instant,-9223372036854775807L)";
		String transitions = "array_sort(concat(" + zone + ".history,filter(" + future + ",zone_t -> zone_t.instant>"
				+ last + ")))";
		return "aggregate(" + transitions + "," + zone + ".initial,(zone_offset,zone_t) -> CASE WHEN " + day
				+ "*86400L+" + second
				+ ">=zone_t.instant+greatest(zone_t.before,zone_t.after) THEN zone_t.after ELSE zone_offset END)";
	}
}
