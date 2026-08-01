package io.finmsg.dmn.ir;

/** Protobuf-free executable expression in Runtime IR. */
public sealed interface RuntimeExpression permits
    RuntimeConstant,
    RuntimeValueReference,
    RuntimeUnaryExpression,
    RuntimeBinaryExpression,
    RuntimeConditionalExpression,
    RuntimeListExpression,
    RuntimeFunctionCall,
    RuntimeContextExpression,
    RuntimeLocalReference,
    RuntimePathExpression {
  RuntimeType type();
}
