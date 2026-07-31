package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.TypeReference;
import java.util.List;

public record FeelFunctionSignature(
    String name,
    List<TypeReference> parameterTypes,
    TypeReference returnType,
    boolean variadic) {

  public FeelFunctionSignature {
    parameterTypes = List.copyOf(parameterTypes);
  }
}
