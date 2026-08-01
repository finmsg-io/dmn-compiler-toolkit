package io.finmsg.dmn.ir;

import java.util.List;
import java.util.Objects;

/** Lossless Runtime IR plus deterministic evaluator-oriented optimization metadata. */
public record RuntimeOptimizedModel(
    RuntimeModel model,
    List<RuntimeConstantPoolEntry> constantPool,
    List<RuntimeConstantUse> constantUses,
    List<RuntimeBuiltinBinding> builtinBindings) {
  public RuntimeOptimizedModel {
    Objects.requireNonNull(model, "model");
    constantPool = List.copyOf(constantPool);
    constantUses = List.copyOf(constantUses);
    builtinBindings = List.copyOf(builtinBindings);
  }
}
