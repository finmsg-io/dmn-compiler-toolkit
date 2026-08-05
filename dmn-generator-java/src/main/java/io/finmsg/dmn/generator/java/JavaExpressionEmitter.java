package io.finmsg.dmn.generator.java;

import io.finmsg.dmn.ir.*;

/** Emitter for lowering RuntimeExpression nodes into pure Java code statements. */
public final class JavaExpressionEmitter {

  public static String emit(RuntimeExpression expr) {
    if (expr == null) {
      return "null";
    }
    return switch (expr) {
      case RuntimeConstant constant -> emitConstant(constant);
      case RuntimeValueReference ref -> "slots[" + ref.sourceSlot() + "]";
      case RuntimeLocalReference local -> "local_" + local.localSlot();
      case RuntimeBinaryExpression binary -> emitBinary(binary);
      case RuntimeUnaryExpression unary -> emitUnary(unary);
      case RuntimeConditionalExpression cond -> "(isTrue(" + emit(cond.condition())
          + ") ? " + emit(cond.thenExpression()) + " : " + emit(cond.elseExpression()) + ")";
      case RuntimePathExpression path -> "getPath(" + emit(path.source()) + ", \"" + escapeString(path.member()) + "\")";
      case RuntimeContextExpression ctx -> emitContext(ctx);
      case RuntimeListExpression list -> emitList(list);
      default -> "null";
    };
  }

  private static String emitConstant(RuntimeConstant constant) {
    if (constant.kind() == RuntimeConstantKind.NULL || constant.value() == null) {
      return "null";
    }
    return switch (constant.kind()) {
      case BOOLEAN -> String.valueOf(constant.value());
      case STRING -> "\"" + escapeString(constant.value()) + "\"";
      case NUMBER -> "new BigDecimal(\"" + constant.value() + "\")";
      default -> "\"" + escapeString(constant.value()) + "\"";
    };
  }

  private static String emitBinary(RuntimeBinaryExpression binary) {
    String left = emit(binary.left());
    String right = emit(binary.right());
    return switch (binary.operator()) {
      case ADD -> "add(" + left + ", " + right + ")";
      case SUBTRACT -> "subtract(" + left + ", " + right + ")";
      case MULTIPLY -> "multiply(" + left + ", " + right + ")";
      case DIVIDE -> "divide(" + left + ", " + right + ")";
      case EQUAL -> "equal(" + left + ", " + right + ")";
      case NOT_EQUAL -> "!equal(" + left + ", " + right + ")";
      case LESS -> "compare(" + left + ", " + right + ") < 0";
      case LESS_EQUAL -> "compare(" + left + ", " + right + ") <= 0";
      case GREATER -> "compare(" + left + ", " + right + ") > 0";
      case GREATER_EQUAL -> "compare(" + left + ", " + right + ") >= 0";
      case AND -> "and(" + left + ", " + right + ")";
      case OR -> "or(" + left + ", " + right + ")";
      case POWER -> "null";
    };
  }

  private static String emitUnary(RuntimeUnaryExpression unary) {
    String op = emit(unary.operand());
    return switch (unary.operator()) {
      case POSITIVE -> op;
      case NEGATE -> "negate(" + op + ")";
      case NOT -> "not(" + op + ")";
    };
  }

  private static String emitContext(RuntimeContextExpression ctx) {
    if (ctx.entries().isEmpty()) {
      return "Collections.emptyMap()";
    }
    StringBuilder sb = new StringBuilder("((java.util.function.Supplier<Object>) () -> {\n");
    sb.append("      Map<String, Object> _ctx = new LinkedHashMap<>();\n");
    for (int i = 0; i < ctx.entries().size(); i++) {
      RuntimeContextEntry entry = ctx.entries().get(i);
      String exprCode = emit(entry.expression());
      sb.append("      Object local_").append(i).append(" = ").append(exprCode).append(";\n");
      if (entry.name() != null && !entry.name().isBlank()) {
        sb.append("      _ctx.put(\"").append(escapeString(entry.name())).append("\", local_").append(i).append(");\n");
      }
    }
    RuntimeContextEntry lastEntry = ctx.entries().get(ctx.entries().size() - 1);
    if (lastEntry.name() == null || lastEntry.name().isBlank()) {
      sb.append("      return local_").append(ctx.entries().size() - 1).append(";\n");
    } else {
      sb.append("      return _ctx;\n");
    }
    sb.append("    }).get()");
    return sb.toString();
  }

  private static String emitList(RuntimeListExpression list) {
    StringBuilder sb = new StringBuilder("List.of(");
    for (int i = 0; i < list.elements().size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append(emit(list.elements().get(i)));
    }
    sb.append(")");
    return sb.toString();
  }

  private static String escapeString(String input) {
    if (input == null) return "";
    return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }
}
