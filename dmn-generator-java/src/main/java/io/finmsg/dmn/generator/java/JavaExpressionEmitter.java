package io.finmsg.dmn.generator.java;

import io.finmsg.dmn.ir.*;
import java.util.List;

/**
 * Emitter for lowering RuntimeExpression nodes into pure Java code statements.
 */
public final class JavaExpressionEmitter {

	public static String emit(RuntimeExpression expr) {
		if (expr == null) {
			return "null";
		}
		return switch (expr) {
			case RuntimeConstant constant -> emitConstant(constant);
			case RuntimeValueReference ref -> "slots[" + ref.sourceSlot() + "]";
			case RuntimeLocalReference local -> slotName(local.localSlot());
			case RuntimeBinaryExpression binary -> emitBinary(binary);
			case RuntimeUnaryExpression unary -> emitUnary(unary);
			case RuntimeConditionalExpression cond -> "(isTrue(" + emit(cond.condition()) + ") ? "
					+ emit(cond.thenExpression()) + " : " + emit(cond.elseExpression()) + ")";
			case RuntimePathExpression path ->
				"getPath(" + emit(path.source()) + ", \"" + escapeString(path.member()) + "\")";
			case RuntimeContextExpression ctx -> emitContext(ctx);
			case RuntimeListExpression list -> emitList(list);
			case RuntimeFunctionCall fn -> emitFunctionCall(fn);
			case RuntimeInvocationExpression inv -> emitInvocation(inv);
			case RuntimeFilterExpression filter -> emitFilter(filter);
			case RuntimeQuantifiedExpression quant -> emitQuantified(quant);
			case RuntimeForExpression forExpr -> emitFor(forExpr);
			case RuntimeRangeExpression range -> emitRange(range);
			case RuntimeFunctionDefinition fnDef -> emitFunctionDefinition(fnDef);
			case RuntimeBetweenExpression btn ->
				"(" + emit(btn.value()) + " != null && compare(" + emit(btn.value()) + ", " + emit(btn.lower())
						+ ") >= 0 && compare(" + emit(btn.value()) + ", " + emit(btn.upper()) + ") <= 0)";
			case RuntimeInExpression inExpr -> emit(inExpr.value()) + " != null";
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
			case POWER -> "power(" + left + ", " + right + ")";
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

	private static String emitFunctionDefinition(RuntimeFunctionDefinition fnDef) {
		if (fnDef.body().isEmpty())
			return "null";
		StringBuilder params = new StringBuilder();
		for (int i = 0; i < fnDef.parameters().size(); i++) {
			if (i > 0)
				params.append(", ");
			params.append(slotName(fnDef.parameters().get(i).localSlot()));
		}
		String body = emit(fnDef.body().get());
		if (fnDef.parameters().size() == 1) {
			return "(java.util.function.Function<Object, Object>) (" + params + ") -> " + body;
		} else if (fnDef.parameters().size() == 2) {
			return "(java.util.function.BiFunction<Object, Object, Object>) (" + params + ") -> " + body;
		}
		return "(java.util.function.Function<List<Object>, Object>) (_args) -> " + body;
	}

	private static String emitFunctionCall(RuntimeFunctionCall fn) {
		StringBuilder sb = new StringBuilder("builtin(\"").append(escapeString(fn.function())).append("\", List.of(");
		for (int i = 0; i < fn.arguments().size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(emit(fn.arguments().get(i)));
		}
		sb.append("))");
		return sb.toString();
	}

	private static String emitInvocation(RuntimeInvocationExpression inv) {
		if (inv.function().isPresent()) {
			StringBuilder sb = new StringBuilder("builtin(\"").append(escapeString(inv.function().get())).append("\", List.of(");
			for (int i = 0; i < inv.positionalArguments().size(); i++) {
				if (i > 0)
					sb.append(", ");
				sb.append(emit(inv.positionalArguments().get(i)));
			}
			sb.append("))");
			return sb.toString();
		}
		if (inv.target().isPresent()) {
			return emit(inv.target().get());
		}
		return "null";
	}

	private static String emitFilter(RuntimeFilterExpression filter) {
		String src = emit(filter.source());
		String flt = emit(filter.filter());
		return "filter(" + src + ", _elem -> { return " + flt + "; })";
	}

	private static String emitQuantified(RuntimeQuantifiedExpression quant) {
		boolean isEvery = quant.quantifier() == RuntimeQuantifier.EVERY;
		if (quant.bindings().isEmpty())
			return String.valueOf(isEvery);
		return emitNestedQuantified(isEvery, quant.bindings(), 0, quant.satisfies());
	}

	private static String emitNestedQuantified(boolean isEvery, List<RuntimeQuantifiedBinding> bindings, int index,
			RuntimeExpression satisfies) {
		if (index == bindings.size()) {
			return emit(satisfies);
		}
		RuntimeQuantifiedBinding binding = bindings.get(index);
		String src = emit(binding.source());
		String slot = slotName(binding.localSlot());
		String inner = emitNestedQuantified(isEvery, bindings, index + 1, satisfies);
		return "quantify(" + isEvery + ", " + src + ", " + slot + " -> " + inner + ")";
	}

	private static String emitFor(RuntimeForExpression forExpr) {
		if (forExpr.iterations().isEmpty())
			return "List.of()";
		return emitNestedFor(forExpr.iterations(), 0, forExpr.result());
	}

	private static String emitNestedFor(List<RuntimeIteration> iterations, int index, RuntimeExpression result) {
		if (index == iterations.size()) {
			return emit(result);
		}
		RuntimeIteration iter = iterations.get(index);
		String src = emit(iter.source());
		String slot = slotName(iter.localSlot());
		String inner = emitNestedFor(iterations, index + 1, result);
		return "forLoop(" + src + ", " + slot + " -> " + inner + ")";
	}

	private static String emitRange(RuntimeRangeExpression range) {
		String low = range.lower().map(JavaExpressionEmitter::emit).orElse("null");
		String up = range.upper().map(JavaExpressionEmitter::emit).orElse("null");
		return "new RuntimeRangeValue(" + low + ", " + up + ", " + range.lowerBoundary() + ", " + range.upperBoundary()
				+ ")";
	}

	private static String emitContext(RuntimeContextExpression ctx) {
		if (ctx.entries().isEmpty()) {
			return "Collections.emptyMap()";
		}
		boolean isPureMap = ctx.entries().stream().allMatch(e -> e.name() != null && !e.name().isBlank());
		if (isPureMap) {
			StringBuilder sb = new StringBuilder("createContext(new Object[][]{");
			for (int i = 0; i < ctx.entries().size(); i++) {
				if (i > 0)
					sb.append(", ");
				RuntimeContextEntry entry = ctx.entries().get(i);
				sb.append("{\"").append(escapeString(entry.name())).append("\", ").append(emit(entry.expression()))
						.append("}");
			}
			sb.append("})");
			return sb.toString();
		}
		if (ctx.entries().size() == 1) {
			return emit(ctx.entries().get(0).expression());
		}
		StringBuilder sb = new StringBuilder("((java.util.function.Supplier<Object>) () -> { ");
		for (int i = 0; i < ctx.entries().size(); i++) {
			RuntimeContextEntry entry = ctx.entries().get(i);
			String exprCode = emit(entry.expression());
			String varName = slotName(entry.localSlot());
			if (i == ctx.entries().size() - 1 && (entry.name() == null || entry.name().isBlank())) {
				sb.append("return ").append(exprCode).append("; ");
			} else {
				sb.append("Object ").append(varName).append(" = ").append(exprCode).append("; ");
			}
		}
		sb.append("}).get()");
		return sb.toString();
	}

	private static String slotName(int slot) {
		return slot < 0 ? "local_neg_" + Math.abs(slot) : "local_" + slot;
	}

	private static String emitList(RuntimeListExpression list) {
		StringBuilder sb = new StringBuilder("List.of(");
		for (int i = 0; i < list.elements().size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(emit(list.elements().get(i)));
		}
		sb.append(")");
		return sb.toString();
	}

	private static String escapeString(String input) {
		if (input == null)
			return "";
		return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}
}
