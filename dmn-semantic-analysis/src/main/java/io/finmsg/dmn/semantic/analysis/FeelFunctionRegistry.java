package io.finmsg.dmn.semantic.analysis;

import java.util.List;

public interface FeelFunctionRegistry {
  List<FeelFunctionSignature> find(String name);
}
