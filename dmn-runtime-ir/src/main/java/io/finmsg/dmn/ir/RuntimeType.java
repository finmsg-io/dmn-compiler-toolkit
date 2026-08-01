package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;

/** Structural, source-name-free runtime value type. */
public record RuntimeType(
    RuntimeTypeKind kind,
    RuntimeType elementType,
    List<RuntimeType> fields,
    List<RuntimeType> parameterTypes,
    RuntimeType returnType) {

  public RuntimeType {
    Objects.requireNonNull(kind, "kind");
    fields = List.copyOf(fields);
    parameterTypes = List.copyOf(parameterTypes);
  }

  public static RuntimeType scalar(RuntimeTypeKind kind) {
    return new RuntimeType(kind, null, List.of(), List.of(), null);
  }

  public static RuntimeType element(RuntimeTypeKind kind, RuntimeType elementType) {
    return new RuntimeType(kind, Objects.requireNonNull(elementType), List.of(), List.of(), null);
  }

  public static RuntimeType context(List<RuntimeType> fields) {
    return new RuntimeType(RuntimeTypeKind.CONTEXT, null, fields, List.of(), null);
  }

  public static RuntimeType function(List<RuntimeType> parameters, RuntimeType result) {
    return new RuntimeType(RuntimeTypeKind.FUNCTION, null, List.of(), parameters,
        Objects.requireNonNull(result));
  }
}
