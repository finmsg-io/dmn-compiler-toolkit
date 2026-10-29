package io.finmsg.dmn.grpc;

import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Model-specific contracts preserving FEEL nulls, exact values and validation
 * inputs.
 */
public final class TypedProtoSchemaGenerator {
	public static String generateProto(RuntimeModel model, TypedGrpcGeneratorOptions options) {
		var types = new Types();
		var messages = new StringBuilder("message InputData {\n");
		int tag = 1;
		for (RuntimeInput input : model.inputs())
			messages.append("  ").append(types.name(input.type())).append(" input_").append(input.valueSlot())
					.append(" = ").append(tag++).append(";\n");
		messages.append("}\n\n");
		var methods = new StringBuilder("service " + options.serviceName() + " {\n");
		for (RuntimeDecision decision : model.decisions()) {
			String name = "Decision_" + decision.resultSlot();
			messages.append("message ").append(name).append("Request { InputData inputs = 1; }\n").append("message ")
					.append(name).append("Response { ").append(types.name(decision.type())).append(" result = 1; }\n");
			methods.append("  rpc Evaluate").append(name).append(" (").append(name).append("Request) returns (")
					.append(name).append("Response);\n");
		}
		for (RuntimeBkm bkm : model.businessKnowledgeModels()) {
			if (bkm.functionKind() != RuntimeFunctionKind.FEEL || bkm.function().isEmpty())
				continue;
			String name = "Knowledge_" + bkm.resultSlot();
			var parameters = bkm.function().orElseThrow().parameters();
			messages.append("message ").append(name).append("Arguments {\n");
			for (int i = 0; i < parameters.size(); i++)
				messages.append("  ").append(types.name(parameters.get(i).type())).append(" argument_").append(i)
						.append(" = ").append(i + 1).append(";\n");
			messages.append("}\nmessage ").append(name).append("Request { InputData inputs = 1; ").append(name)
					.append("Arguments arguments = 2; repeated string supplied_argument_names = 3; }\n")
					.append("message ").append(name).append("Response { ").append(types.name(invocationResultType(bkm)))
					.append(" result = 1; }\n");
			methods.append("  rpc Invoke").append(name).append(" (").append(name).append("Request) returns (")
					.append(name).append("Response);\n");
		}
		methods.append("}\n");
		return "syntax = \"proto3\";\npackage " + options.protoPackage() + ";\n"
				+ "import \"evaluation.proto\";\noption java_package = \"" + options.javaPackage() + "\";\n"
				+ "option java_outer_classname = \"" + options.outerClassName() + "\";\n\n"
				+ "message AdditionalContextEntry { string name = 1; io.finmsg.dmn.grpc.Value value = 2; }\n"
				+ String.join("\n", types.definitions.values()) + "\n" + messages + methods;
	}

	static RuntimeType invocationResultType(RuntimeBkm bkm) {
		RuntimeType type = bkm.function().orElseThrow().type();
		return type.kind() == RuntimeTypeKind.FUNCTION && type.returnType() != null
				? type.returnType()
				: RuntimeType.scalar(RuntimeTypeKind.ANY);
	}

	private static final class Types {
		final Map<RuntimeType, String> names = new LinkedHashMap<>();
		final Map<String, String> definitions = new LinkedHashMap<>();

		String name(RuntimeType requested) {
			RuntimeType type = requested == null ? RuntimeType.scalar(RuntimeTypeKind.ANY) : requested;
			if (names.containsKey(type))
				return names.get(type);
			String name = "TypedValue_" + names.size();
			names.put(type, name);
			definitions.put(name, "");
			String valueType = switch (type.kind()) {
				case BOOLEAN -> "bool";
				case NUMBER, STRING, DATE, TIME, DATE_TIME, DURATION, YEARS_MONTHS_DURATION, DAYS_TIME_DURATION ->
					"string";
				case LIST -> {
					String items = name + "Items";
					definitions.put(items,
							"message " + items + " { repeated " + name(type.elementType()) + " items = 1; }\n");
					yield items;
				}
				case CONTEXT -> {
					if (type.fieldLayout().isEmpty())
						yield "io.finmsg.dmn.grpc.Value";
					String fields = name + "Fields";
					var body = new StringBuilder("message " + fields + " {\n");
					for (int i = 0; i < type.fieldLayout().size(); i++)
						body.append("  ").append(name(type.fieldLayout().get(i).type())).append(" field_").append(i)
								.append(" = ").append(i + 1).append(";\n");
					body.append("  repeated AdditionalContextEntry additional_entries = ")
							.append(type.fieldLayout().size() + 1).append(";\n}\n");
					definitions.put(fields, body.toString());
					yield fields;
				}
				default -> "io.finmsg.dmn.grpc.Value";
			};
			definitions.put(name, "message " + name + " {\n  oneof state {\n    bool null_value = 1;\n    " + valueType
					+ " value = 2;\n    io.finmsg.dmn.grpc.Value invalid_value = 3;\n  }\n}\n");
			return name;
		}
	}
}
