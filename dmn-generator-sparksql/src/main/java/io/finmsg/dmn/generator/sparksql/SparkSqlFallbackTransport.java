package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;
import org.apache.spark.sql.types.*;

/**
 * Lossless numeric transport for interpreter calls alongside native columns.
 */
final class SparkSqlFallbackTransport {
	static boolean numeric(RuntimeType type) {
		return type != null && switch (type.kind()) {
			case NUMBER -> true;
			case LIST -> numeric(type.elementType());
			case CONTEXT -> type.fieldLayout().stream().anyMatch(f -> numeric(f.type()));
			default -> false;
		};
	}
	static String column(int slot, Map<Integer, String> names) {
		String result = "__dmn_fallback_input_" + slot;
		var occupied = new HashSet<String>();
		names.values().forEach(n -> occupied.add(n.replace("`", "").toLowerCase(Locale.ROOT)));
		while (occupied.contains(result.toLowerCase(Locale.ROOT)))
			result += "_";
		return result;
	}
	static StructType schema(StructType nativeSchema, RuntimeModel model, Map<Integer, String> names) {
		var schema = nativeSchema;
		for (var input : model.inputs())
			if (numeric(input.type()))
				schema = schema.add(DataTypes.createStructField(column(input.valueSlot(), names), DataTypes.BinaryType,
						true, new MetadataBuilder().putLong("dmnFallbackInputSlot", input.valueSlot()).build()));
		return schema;
	}
}
