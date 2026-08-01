package io.finmsg.dmn.ir;

public record RuntimeConstantUse(int expressionOrdinal, int constantPoolId) {
  public RuntimeConstantUse {
    if (expressionOrdinal < 0 || constantPoolId < 0) {
      throw new IllegalArgumentException("Constant-use indices must be non-negative.");
    }
  }
}
