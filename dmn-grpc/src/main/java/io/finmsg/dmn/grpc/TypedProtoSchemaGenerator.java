package io.finmsg.dmn.grpc;

import io.finmsg.dmn.ir.*;
import java.util.*;

/** Generates strongly-typed Protobuf schemas (.proto) from DMN ItemDefinition types and models. */
public final class TypedProtoSchemaGenerator {

  public static String generateProto(RuntimeModel model, TypedGrpcGeneratorOptions options) {
    StringBuilder headerSb = new StringBuilder();
    headerSb.append("syntax = \"proto3\";\n\n");
    headerSb.append("package ").append(options.protoPackage()).append(";\n\n");
    headerSb.append("option java_package = \"").append(options.javaPackage()).append("\";\n");
    headerSb.append("option java_outer_classname = \"").append(options.outerClassName()).append("\";\n\n");

    Set<String> generatedMessages = new HashSet<>();
    StringBuilder structSb = new StringBuilder();

    // 1. Generate Messages for Inputs
    StringBuilder inputSb = new StringBuilder();
    inputSb.append("// DMN Input Data Structure\n");
    inputSb.append("message InputData {\n");
    int fieldTag = 1;
    for (RuntimeInput input : model.inputs()) {
      String name = "input_" + input.valueSlot();
      String protoType = mapProtoType(input.type(), structSb, generatedMessages);
      inputSb.append("  ").append(protoType).append(" ").append(name).append(" = ").append(fieldTag++).append(";\n");
    }
    inputSb.append("}\n\n");

    // 2. Generate Messages for Decisions
    StringBuilder decSb = new StringBuilder();
    decSb.append("// DMN Decision Evaluation Requests & Responses\n");
    for (RuntimeDecision decision : model.decisions()) {
      String decName = sanitizeName("Decision_" + decision.resultSlot());
      String outType = mapProtoType(decision.type(), structSb, generatedMessages);

      decSb.append("message ").append(decName).append("Request {\n");
      decSb.append("  InputData inputs = 1;\n");
      decSb.append("}\n\n");

      decSb.append("message ").append(decName).append("Response {\n");
      decSb.append("  ").append(outType).append(" result = 1;\n");
      decSb.append("}\n\n");
    }

    // 3. Generate gRPC Service Definition
    StringBuilder serviceSb = new StringBuilder();
    serviceSb.append("// Generated DMN Typed gRPC Service\n");
    serviceSb.append("service ").append(options.serviceName()).append(" {\n");
    for (RuntimeDecision decision : model.decisions()) {
      String decName = sanitizeName("Decision_" + decision.resultSlot());
      serviceSb.append("  rpc Evaluate").append(decName).append(" (")
        .append(decName).append("Request) returns (")
        .append(decName).append("Response);\n");
    }
    serviceSb.append("}\n");

    return headerSb.toString() + inputSb.toString() + structSb.toString() + decSb.toString() + serviceSb.toString();
  }

  private static String mapProtoType(RuntimeType type, StringBuilder structSb, Set<String> generatedMessages) {
    if (type == null) return "string";

    return switch (type.kind()) {
      case BOOLEAN -> "bool";
      case NUMBER -> "double";
      case STRING -> "string";
      case DATE, DATE_TIME, TIME -> "string";
      case LIST -> {
        String elemType = type.elementType() != null ? mapProtoType(type.elementType(), structSb, generatedMessages) : "string";
        yield "repeated " + elemType;
      }
      case CONTEXT -> {
        if (!type.fieldLayout().isEmpty()) {
          String msgName = "Context_" + Math.abs(type.hashCode());
          if (generatedMessages.add(msgName)) {
            structSb.append("message ").append(msgName).append(" {\n");
            int tag = 1;
            for (RuntimeField field : type.fieldLayout()) {
              String fType = mapProtoType(field.type(), structSb, generatedMessages);
              structSb.append("  ").append(fType).append(" ").append(sanitizeName(field.name())).append(" = ").append(tag++).append(";\n");
            }
            structSb.append("}\n\n");
          }
          yield msgName;
        }
        yield "string";
      }
      default -> "string";
    };
  }

  private static String sanitizeName(String name) {
    return name.replaceAll("[^a-zA-Z0-9_]", "_");
  }
}
