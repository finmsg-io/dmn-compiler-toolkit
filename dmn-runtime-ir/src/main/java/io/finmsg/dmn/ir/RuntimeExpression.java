package io.finmsg.dmn.ir;

/** Protobuf-free executable expression in Runtime IR. */
public sealed interface RuntimeExpression permits
    RuntimeConstant, RuntimeValueReference, RuntimeUnaryExpression, RuntimeBinaryExpression {
  RuntimeType type();
}
