package io.finmsg.dmn.generator.sparksql;
import java.math.BigDecimal;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.*;

/** Native digit arithmetic over exact decimal representation components. */
final class SparkSqlDecimalSql {
	static StructType type() {
		return new StructType().add("digits", DataTypes.StringType).add("scale", DataTypes.LongType)
				.add("negative", DataTypes.BooleanType).add("invalid", DataTypes.BooleanType);
	}
	static Object row(Object value) {
		if (value == null)
			return RowFactory.create(null, 0L, false, false);
		try {
			var n = value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
			return RowFactory.create(n.unscaledValue().abs().toString(), (long) n.scale(), n.signum() < 0, false);
		} catch (NumberFormatException invalid) {
			return RowFactory.create(null, 0L, false, true);
		}
	}
	static String number(String digits, String scale, String negative) {
		return "named_struct('digits'," + digits + ",'scale',CAST(" + scale + " AS BIGINT),'negative'," + negative
				+ ",'invalid',FALSE)";
	}
	static String nil() {
		return number("CAST(NULL AS STRING)", "0L", "FALSE");
	}
	static String constant(BigDecimal n) {
		return number("'" + n.unscaledValue().abs() + "'", n.scale() + "L", n.signum() < 0 ? "TRUE" : "FALSE");
	}
	private static String error(String message) {
		return "CAST(raise_error('" + message
				+ "') AS STRUCT<digits:STRING,scale:BIGINT,negative:BOOLEAN,invalid:BOOLEAN>)";
	}
	static String let(String name, String value, String expression) {
		return "element_at(transform(array(" + value + ")," + name + " -> " + expression + "),1)";
	}
	private static String operands(String left, String right, String body) {
		return let("decimal_a", left, let("decimal_b", right,
				"CASE WHEN decimal_a.digits IS NULL AND NOT decimal_a.invalid OR decimal_b.digits IS NULL AND NOT decimal_b.invalid THEN "
						+ nil() + " WHEN decimal_a.invalid OR decimal_b.invalid THEN "
						+ error("Invalid FEEL arithmetic number") + " ELSE " + body + " END"));
	}
	static String payload(String value) {
		String signed = "CASE WHEN decimal_result.negative AND decimal_result.digits<>'0' THEN concat('-',decimal_result.digits) ELSE decimal_result.digits END";
		return let("decimal_result", value,
				"CASE WHEN decimal_result.invalid THEN CAST(raise_error('Invalid FEEL arithmetic number') AS BINARY) WHEN decimal_result.digits IS NULL THEN CAST(NULL AS BINARY) ELSE "
						+ let("decimal_text", signed,
								"concat(unhex('444D4E0113'),unhex(lpad(right(hex(decimal_result.scale),8),8,'0')),unhex(lpad(hex(length(decimal_text)),8,'0')),encode(decimal_text,'UTF-8'))")
						+ " END");
	}
	static String multiply(String left, String right) {
		String sum = "decimal_state.carry + aggregate(sequence(greatest(0,decimal_pos-length(decimal_bd)+1),least(decimal_pos,length(decimal_ad)-1)),0L,(decimal_sum,decimal_j) -> decimal_sum + CAST(substring(decimal_ad,decimal_j+1,1) AS BIGINT)*CAST(substring(decimal_bd,decimal_pos-decimal_j+1,1) AS BIGINT))";
		String merge = let("decimal_sum", sum,
				"named_struct('carry',coalesce(CAST(floor(decimal_sum/10.0) AS BIGINT),0L),'digits',coalesce(concat(CAST(pmod(decimal_sum,10) AS STRING),decimal_state.digits),''))");
		String digits = "aggregate(sequence(0,length(decimal_ad)+length(decimal_bd)-2),named_struct('carry',0L,'digits',''),(decimal_state,decimal_pos) -> "
				+ merge
				+ ",decimal_done -> concat(CASE WHEN decimal_done.carry=0 THEN '' ELSE CAST(decimal_done.carry AS STRING) END,decimal_done.digits))";
		digits = let("decimal_ad", "reverse(decimal_a.digits)", let("decimal_bd", "reverse(decimal_b.digits)", digits));
		String coefficient = "CASE WHEN decimal_a.digits='0' OR decimal_b.digits='0' THEN '0' ELSE regexp_replace("
				+ digits + ",'^0+(?!$)','') END";
		String result = number(coefficient, "greatest(-2147483648L,least(2147483647L,decimal_scale))",
				"decimal_a.negative<>decimal_b.negative");
		result = let("decimal_scale", "decimal_a.scale+decimal_b.scale",
				"CASE WHEN (decimal_scale < -2147483648L OR decimal_scale > 2147483647L) AND decimal_a.digits<>'0' THEN "
						+ error("FEEL arithmetic scale overflow") + " ELSE " + result + " END");
		return operands(left, right, result);
	}
	private static String digit(String text, String index) {
		return "CAST(coalesce(nullif(substring(" + text + "," + index + "+1,1),''),'0') AS BIGINT)";
	}
	static String add(String left, String right, boolean subtract) {
		String a = "CASE WHEN decimal_a.digits='0' THEN '0' ELSE concat(decimal_a.digits,repeat('0',CAST(decimal_scale-decimal_a.scale AS INT))) END";
		String b = "CASE WHEN decimal_b.digits='0' THEN '0' ELSE concat(decimal_b.digits,repeat('0',CAST(decimal_scale-decimal_b.scale AS INT))) END";
		String rightNegative = subtract ? "NOT decimal_b.negative AND decimal_b.digits<>'0'" : "decimal_b.negative";
		String aDigit = digit("decimal_ar", "decimal_pos"), bDigit = digit("decimal_br", "decimal_pos");
		String sum = let("decimal_sum", aDigit + "+" + bDigit + "+decimal_state.carry",
				"named_struct('carry',coalesce(CAST(floor(decimal_sum/10.0) AS BIGINT),0L),'digits',coalesce(concat(CAST(pmod(decimal_sum,10) AS STRING),decimal_state.digits),''))");
		String plus = "aggregate(sequence(0,greatest(length(decimal_ar),length(decimal_br))-1),named_struct('carry',0L,'digits',''),(decimal_state,decimal_pos) -> "
				+ sum
				+ ",decimal_done -> concat(CASE WHEN decimal_done.carry=0 THEN '' ELSE CAST(decimal_done.carry AS STRING) END,decimal_done.digits))";
		String diff = let("decimal_diff",
				digit("decimal_big", "decimal_pos") + "-" + digit("decimal_small", "decimal_pos")
						+ "-decimal_state.carry",
				"named_struct('carry',CASE WHEN decimal_diff<0 THEN 1L ELSE 0L END,'digits',coalesce(concat(CAST(pmod(decimal_diff,10) AS STRING),decimal_state.digits),''))");
		String minus = "aggregate(sequence(0,length(decimal_big)-1),named_struct('carry',0L,'digits',''),(decimal_state,decimal_pos) -> "
				+ diff + ",decimal_done -> regexp_replace(decimal_done.digits,'^0+(?!$)',''))";
		minus = let("decimal_big", "CASE WHEN decimal_ge THEN decimal_ar ELSE decimal_br END",
				let("decimal_small", "CASE WHEN decimal_ge THEN decimal_br ELSE decimal_ar END", minus));
		String digits = "CASE WHEN decimal_a.negative=decimal_b_negative THEN " + plus + " ELSE " + minus + " END";
		String negative = "CASE WHEN decimal_a.negative=decimal_b_negative OR decimal_ge THEN decimal_a.negative ELSE decimal_b_negative END";
		String result = number(digits, "decimal_scale", negative);
		result = let("decimal_ge",
				"length(decimal_av)>length(decimal_bv) OR length(decimal_av)=length(decimal_bv) AND decimal_av>=decimal_bv",
				let("decimal_ar", "reverse(decimal_av)", let("decimal_br", "reverse(decimal_bv)", result)));
		result = let("decimal_av", a, let("decimal_bv", b, result));
		result = "CASE WHEN decimal_a.digits<>'0' AND decimal_scale-decimal_a.scale+length(decimal_a.digits)>2147483647L OR decimal_b.digits<>'0' AND decimal_scale-decimal_b.scale+length(decimal_b.digits)>2147483647L THEN "
				+ error("FEEL arithmetic coefficient overflow") + " ELSE " + result + " END";
		result = let("decimal_scale", "greatest(decimal_a.scale,decimal_b.scale)",
				let("decimal_b_negative", rightNegative, result));
		return operands(left, right, result);
	}
	static String compare(String left, String right) {
		String signA = "CASE WHEN decimal_ca.digits='0' THEN 0 WHEN decimal_ca.negative THEN -1 ELSE 1 END";
		String signB = "CASE WHEN decimal_cb.digits='0' THEN 0 WHEN decimal_cb.negative THEN -1 ELSE 1 END";
		String order = "CASE WHEN length(decimal_ca.digits)-decimal_ca.scale < length(decimal_cb.digits)-decimal_cb.scale THEN -1 WHEN length(decimal_ca.digits)-decimal_ca.scale > length(decimal_cb.digits)-decimal_cb.scale THEN 1 WHEN rpad(decimal_ca.digits,greatest(length(decimal_ca.digits),length(decimal_cb.digits)),'0') < rpad(decimal_cb.digits,greatest(length(decimal_ca.digits),length(decimal_cb.digits)),'0') THEN -1 WHEN rpad(decimal_ca.digits,greatest(length(decimal_ca.digits),length(decimal_cb.digits)),'0') > rpad(decimal_cb.digits,greatest(length(decimal_ca.digits),length(decimal_cb.digits)),'0') THEN 1 ELSE 0 END";
		String body = let("decimal_sa", signA, let("decimal_sb", signB,
				"CASE WHEN decimal_sa<decimal_sb THEN -1 WHEN decimal_sa>decimal_sb THEN 1 WHEN decimal_sa=0 THEN 0 ELSE decimal_sa*("
						+ order + ") END"));
		return let("decimal_ca", left, let("decimal_cb", right,
				"CASE WHEN decimal_ca.digits IS NULL AND NOT decimal_ca.invalid OR decimal_cb.digits IS NULL AND NOT decimal_cb.invalid THEN CAST(NULL AS INT) WHEN decimal_ca.invalid OR decimal_cb.invalid THEN CAST(raise_error('Invalid FEEL arithmetic comparison') AS INT) ELSE "
						+ body + " END"));
	}
}
