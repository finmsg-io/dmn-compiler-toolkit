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
	/** DECIMAL128 long division, including preferred scale for exact quotients. */
	static String divide(String left, String right) {
		String subtract = add(number("div_state.remainder", "0L", "FALSE"),
				number("element_at(div_multiples,div_digit+1)", "0L", "FALSE"), true);
		String remainder = let("div_difference", subtract,
				"CASE WHEN div_difference.digits='0' THEN '0' ELSE concat(div_difference.digits,'0') END");
		String digit = "array_max(filter(sequence(0,9),div_candidate -> length(element_at(div_multiples,div_candidate+1))<length(div_state.remainder) OR length(element_at(div_multiples,div_candidate+1))=length(div_state.remainder) AND element_at(div_multiples,div_candidate+1)<=div_state.remainder))";
		String step = let("div_digit", digit, "named_struct('remainder'," + remainder
				+ ",'digits',concat(div_state.digits,CAST(div_digit AS STRING)))");
		String quotient = "aggregate(sequence(1,35),named_struct('remainder',CASE WHEN div_less THEN concat(div_na,'0') ELSE div_na END,'digits',''),(div_state,div_position) -> "
				+ step + ")";
		String rounded = add(number("substring(div_quotient.digits,1,34)", "0L", "FALSE"), number(
				"CASE WHEN substring(div_quotient.digits,35,1)>'5' OR substring(div_quotient.digits,35,1)='5' AND (div_quotient.remainder<>'0' OR pmod(CAST(substring(div_quotient.digits,34,1) AS INT),2)=1) THEN '1' ELSE '0' END",
				"0L", "FALSE"), false);
		String output = number("substring(div_coefficient,1,length(div_coefficient)-div_trim)", "div_scale-div_trim",
				"decimal_a.negative<>decimal_b.negative");
		output = "CASE WHEN div_scale-div_trim < -2147483648L OR div_scale-div_trim > 2147483647L THEN "
				+ error("FEEL division scale overflow") + " ELSE " + output + " END";
		output = let("div_trim",
				"CAST(CASE WHEN div_quotient.remainder='0' AND substring(div_quotient.digits,35,1)='0' THEN least(length(div_coefficient)-length(regexp_replace(div_coefficient,'0+$','')),greatest(0L,div_scale-div_preferred)) ELSE 0L END AS INT)",
				output);
		output = let("div_coefficient",
				"CASE WHEN length(div_rounded.digits)>34 THEN substring(div_rounded.digits,1,34) ELSE div_rounded.digits END",
				let("div_scale", "33L-div_exponent-CASE WHEN length(div_rounded.digits)>34 THEN 1L ELSE 0L END",
						output));
		output = let("div_rounded", rounded, output);
		output = let("div_quotient", quotient, output);
		String multiples = "transform(sequence(0,9),div_multiple -> " + let("div_product",
				multiply(number("div_nb", "0L", "FALSE"), number("CAST(div_multiple AS STRING)", "0L", "FALSE")),
				"div_product.digits") + ")";
		output = let("div_multiples", multiples, let("div_exponent",
				"CAST(length(decimal_a.digits) AS BIGINT)-length(decimal_b.digits)-decimal_a.scale+decimal_b.scale-CASE WHEN div_less THEN 1L ELSE 0L END",
				output));
		// BigDecimal checks its provisional quotient scale before stripping exact
		// zeros.
		String provisional = "decimal_a.scale-decimal_b.scale+CAST(length(decimal_b.digits) AS BIGINT)-length(decimal_a.digits)+34L-CASE WHEN div_na>div_nb THEN 1L ELSE 0L END";
		output = let("div_provisional", provisional,
				"CASE WHEN div_provisional < -2147483648L OR div_provisional > 2147483647L OR decimal_a.scale-decimal_b.scale < -2147483648L OR decimal_a.scale-decimal_b.scale > 2147483647L THEN "
						+ error("FEEL division provisional scale overflow") + " ELSE " + output + " END");
		output = let("div_less", "div_na<div_nb", output);
		output = let("div_na", "rpad(decimal_a.digits,greatest(length(decimal_a.digits),length(decimal_b.digits)),'0')",
				let("div_nb", "rpad(decimal_b.digits,greatest(length(decimal_a.digits),length(decimal_b.digits)),'0')",
						output));
		output = "CASE WHEN decimal_b.digits='0' THEN " + nil() + " WHEN decimal_a.digits='0' THEN "
				+ number("'0'", "div_preferred", "FALSE") + " ELSE " + output + " END";
		output = let("div_preferred", "greatest(-2147483648L,least(2147483647L,decimal_a.scale-decimal_b.scale))",
				output);
		// Runtime checks the denominator before converting the numerator to BigDecimal.
		return let("decimal_a", left, let("decimal_b", right,
				"CASE WHEN decimal_a.digits IS NULL AND NOT decimal_a.invalid OR decimal_b.digits IS NULL AND NOT decimal_b.invalid THEN "
						+ nil() + " WHEN decimal_b.invalid THEN " + error("Invalid FEEL division denominator")
						+ " WHEN decimal_b.digits='0' THEN " + nil() + " WHEN decimal_a.invalid THEN "
						+ error("Invalid FEEL division numerator") + " ELSE " + output + " END"));
	}

	/**
	 * SQL's reflection expression uses the same Math.pow and Double.toString as
	 * FEEL.
	 */
	static String power(String left, String right) {
		String a = "CAST(concat(CASE WHEN decimal_a.negative THEN '-' ELSE '' END,decimal_a.digits,'E',CAST(-decimal_a.scale AS STRING)) AS DOUBLE)";
		String b = "CAST(concat(CASE WHEN decimal_b.negative THEN '-' ELSE '' END,decimal_b.digits,'E',CAST(-decimal_b.scale AS STRING)) AS DOUBLE)";
		String result = number("regexp_replace(regexp_replace(div_power_mantissa,'[-.]',''),'^0+(?!$)','')",
				"CASE WHEN instr(div_power_mantissa,'.')=0 THEN 0L ELSE CAST(length(div_power_mantissa)-instr(div_power_mantissa,'.') AS BIGINT) END-coalesce(try_cast(try_element_at(split(div_power_text,'E'),2) AS BIGINT),0L)",
				"div_power_text LIKE '-%' AND CAST(div_power_text AS DOUBLE)<>0.0");
		result = let("div_power_mantissa", "element_at(split(div_power_text,'E'),1)", result);
		result = "CASE WHEN div_power_text IN ('NaN','Infinity','-Infinity') THEN "
				+ error("Non-finite FEEL power result") + " ELSE " + result + " END";
		return operands(left, right,
				let("div_power_text", "java_method('java.lang.Math','pow'," + a + "," + b + ")", result));
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
