package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;

/** Structural, source-name-free runtime value type. */
public record RuntimeType(
    RuntimeTypeKind kind,
    RuntimeType elementType,
    List<RuntimeType> fields,
    List<RuntimeField> fieldLayout,
    List<RuntimeType> parameterTypes,
    RuntimeType returnType) {

  public RuntimeType {
    Objects.requireNonNull(kind, "kind");
    fields = List.copyOf(fields);
    fieldLayout = List.copyOf(fieldLayout);
    parameterTypes = List.copyOf(parameterTypes);
    if (!fieldLayout.isEmpty()) {
      if (kind != RuntimeTypeKind.CONTEXT || fieldLayout.size() != fields.size()) {
        throw new IllegalArgumentException("Field layout must match a context's field types.");
      }
      for (int index = 0; index < fieldLayout.size(); index++) {
        RuntimeField field = fieldLayout.get(index);
        if (field.index() != index || !field.type().equals(fields.get(index))) {
          throw new IllegalArgumentException("Field layout indices and types must be canonical.");
        }
      }
    }
  }

  public RuntimeType(
      RuntimeTypeKind kind,
      RuntimeType elementType,
      List<RuntimeType> fields,
      List<RuntimeType> parameterTypes,
      RuntimeType returnType) {
    this(kind, elementType, fields, List.of(), parameterTypes, returnType);
  }

  public static RuntimeType scalar(RuntimeTypeKind kind) {
    return new RuntimeType(kind, null, List.of(), List.of(), List.of(), null);
  }

  public static RuntimeType element(RuntimeTypeKind kind, RuntimeType elementType) {
    return new RuntimeType(
        kind, Objects.requireNonNull(elementType), List.of(), List.of(), List.of(), null);
  }

  public static RuntimeType context(List<RuntimeType> fields) {
    return new RuntimeType(RuntimeTypeKind.CONTEXT, null, fields, List.of(), List.of(), null);
  }

  public static RuntimeType contextFields(List<RuntimeField> fields) {
    List<RuntimeField> layout = List.copyOf(fields);
    return new RuntimeType(RuntimeTypeKind.CONTEXT, null,
        layout.stream().map(RuntimeField::type).toList(), layout, List.of(), null);
  }

  public static RuntimeType function(List<RuntimeType> parameters, RuntimeType result) {
    return new RuntimeType(RuntimeTypeKind.FUNCTION, null, List.of(), List.of(), parameters,
        Objects.requireNonNull(result));
  }
}
