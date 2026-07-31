package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.ListTypeReference;
import io.finmsg.dmn.model.TypeReference;
import java.util.List;
import java.util.Map;

public final class BuiltinFeelFunctionRegistry implements FeelFunctionRegistry {

  private static final TypeReference ANY = type(BuiltinType.BUILTIN_TYPE_ANY);
  private static final TypeReference BOOLEAN = type(BuiltinType.BUILTIN_TYPE_BOOLEAN);
  private static final TypeReference NUMBER = type(BuiltinType.BUILTIN_TYPE_NUMBER);
  private static final TypeReference STRING = type(BuiltinType.BUILTIN_TYPE_STRING);
  private static final TypeReference DATE = type(BuiltinType.BUILTIN_TYPE_DATE);
  private static final TypeReference TIME = type(BuiltinType.BUILTIN_TYPE_TIME);
  private static final TypeReference DATE_TIME = type(BuiltinType.BUILTIN_TYPE_DATE_AND_TIME);
  private static final TypeReference DURATION = type(BuiltinType.BUILTIN_TYPE_DURATION);
  private static final TypeReference LIST_ANY = list(ANY);
  private static final TypeReference LIST_NUMBER = list(NUMBER);

  private final Map<String, List<FeelFunctionSignature>> signatures = Map.ofEntries(
      entry("not", signature("not", BOOLEAN, BOOLEAN)),
      entry("string", signature("string", STRING, ANY)),
      entry("number", List.of(
          signature("number", NUMBER, STRING),
          signature("number", NUMBER, STRING, STRING, STRING))),
      entry("date", List.of(
          signature("date", DATE, STRING),
          signature("date", DATE, NUMBER, NUMBER, NUMBER))),
      entry("time", signature("time", TIME, STRING)),
      entry("date and time", signature("date and time", DATE_TIME, STRING)),
      entry("duration", signature("duration", DURATION, STRING)),
      entry("count", signature("count", NUMBER, LIST_ANY)),
      entry("sum", signature("sum", NUMBER, LIST_NUMBER)),
      entry("min", signature("min", ANY, LIST_ANY)),
      entry("max", signature("max", ANY, LIST_ANY)));

  @Override
  public List<FeelFunctionSignature> find(String name) {
    return signatures.getOrDefault(name, List.of());
  }

  private static Map.Entry<String, List<FeelFunctionSignature>> entry(
      String name, FeelFunctionSignature signature) {
    return Map.entry(name, List.of(signature));
  }

  private static Map.Entry<String, List<FeelFunctionSignature>> entry(
      String name, List<FeelFunctionSignature> signatures) {
    return Map.entry(name, signatures);
  }

  private static FeelFunctionSignature signature(
      String name, TypeReference result, TypeReference... parameters) {
    return new FeelFunctionSignature(name, List.of(parameters), result, false);
  }

  private static TypeReference type(BuiltinType type) {
    return TypeReference.newBuilder().setBuiltin(type).build();
  }

  private static TypeReference list(TypeReference element) {
    return TypeReference.newBuilder()
        .setList(ListTypeReference.newBuilder().setElementType(element))
        .build();
  }
}
