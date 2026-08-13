package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.ArrayList;
import java.util.List;
import org.apache.spark.sql.types.*;

/**
 * Generates Spark SQL StructType schemas from DMN Runtime IR types and inputs.
 */
public final class SparkSqlSchemaGenerator {

	public static StructType generateInputSchema(RuntimeModel model) {
		List<StructField> fields = new ArrayList<>();
		for (RuntimeInput input : model.inputs()) {
			DataType dataType = mapType(input.type());
			String name = "input_" + input.valueSlot();
			fields.add(DataTypes.createStructField(name, dataType, true));
		}
		return DataTypes.createStructType(fields);
	}

	public static DataType mapType(RuntimeType type) {
		if (type == null) {
			return DataTypes.StringType;
		}
		return switch (type.kind()) {
			case BOOLEAN -> DataTypes.BooleanType;
			case NUMBER -> DataTypes.DoubleType;
			case STRING -> DataTypes.StringType;
			case DATE -> DataTypes.DateType;
			case TIME, DATE_TIME -> DataTypes.TimestampType;
			case LIST -> {
				DataType elemType = type.elementType() != null ? mapType(type.elementType()) : DataTypes.StringType;
				yield DataTypes.createArrayType(elemType, true);
			}
			case CONTEXT -> {
				if (!type.fieldLayout().isEmpty()) {
					List<StructField> fields = new ArrayList<>();
					for (RuntimeField field : type.fieldLayout()) {
						fields.add(DataTypes.createStructField(field.name(), mapType(field.type()), true));
					}
					yield DataTypes.createStructType(fields);
				}
				yield DataTypes.StringType;
			}
			default -> DataTypes.StringType;
		};
	}
}
