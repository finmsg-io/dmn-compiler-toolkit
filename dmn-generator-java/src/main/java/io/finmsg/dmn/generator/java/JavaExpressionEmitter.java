package io.finmsg.dmn.generator.java;

import io.finmsg.dmn.ir.*;
import java.util.List;
import java.util.Map;

/**
 * Emitter for lowering RuntimeExpression nodes into pure Java code statements.
 */
public final class JavaExpressionEmitter {

	/** Emits an expression without BKM context (for compatibility with old call sites). */
	public static String emit(RuntimeExpression expr) {
		return emitWithBkms(expr, Map.of());
	}

	/** Public accessor for local slot names (used by DmnJavaGenerator for BKM method params). */
	public static String localSlotName(int slot) {
		return slotName(slot);
	}

	/**
	 * Emits an expression, resolving BKM slot references to direct method calls.
	 * @param bkmBySlot map from slot number to RuntimeBkm, for resolving invocation targets
	 */
	public static String emitWithBkms(RuntimeExpression expr, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (expr == null) {
			return "null";
		}
		return switch (expr) {
			case RuntimeConstant constant -> emitConstant(constant);
			case RuntimeValueReference ref -> "slots[" + ref.sourceSlot() + "]";
			case RuntimeLocalReference local -> slotName(local.localSlot());
			case RuntimeBinaryExpression binary -> emitBinaryB(binary, bkmBySlot);
			case RuntimeUnaryExpression unary -> emitUnaryB(unary, bkmBySlot);
			case RuntimeConditionalExpression cond -> "(isTrue(" + emitWithBkms(cond.condition(), bkmBySlot) + ") ? "
					+ emitWithBkms(cond.thenExpression(), bkmBySlot) + " : "
					+ emitWithBkms(cond.elseExpression(), bkmBySlot) + ")";
			case RuntimePathExpression path ->
				"getPath(" + emitWithBkms(path.source(), bkmBySlot) + ", \"" + escapeString(path.member()) + "\")";
			case RuntimeContextExpression ctx -> emitContextB(ctx, bkmBySlot);
			case RuntimeListExpression list -> emitListB(list, bkmBySlot);
			case RuntimeFunctionCall fn -> emitFunctionCallB(fn, bkmBySlot);
			case RuntimeInvocationExpression inv -> emitInvocationB(inv, bkmBySlot);
			case RuntimeFilterExpression filter -> emitFilterB(filter, bkmBySlot);
			case RuntimeQuantifiedExpression quant -> emitQuantifiedB(quant, bkmBySlot);
			case RuntimeForExpression forExpr -> emitForB(forExpr, bkmBySlot);
			case RuntimeRangeExpression range -> emitRangeB(range, bkmBySlot);
			case RuntimeFunctionDefinition fnDef -> emitFunctionDefinition(fnDef);
			case RuntimeBetweenExpression btn ->
				"(" + emitWithBkms(btn.value(), bkmBySlot) + " != null && compare(" + emitWithBkms(btn.value(), bkmBySlot)
						+ ", " + emitWithBkms(btn.lower(), bkmBySlot) + ") >= 0 && compare("
						+ emitWithBkms(btn.value(), bkmBySlot) + ", " + emitWithBkms(btn.upper(), bkmBySlot) + ") <= 0)";
			case RuntimeInExpression inExpr -> emitWithBkms(inExpr.value(), bkmBySlot) + " != null";
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

	private static String emitBinaryB(RuntimeBinaryExpression binary, Map<Integer, RuntimeBkm> bkmBySlot) {
		String left = emitWithBkms(binary.left(), bkmBySlot);
		String right = emitWithBkms(binary.right(), bkmBySlot);
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

	private static String emitUnaryB(RuntimeUnaryExpression unary, Map<Integer, RuntimeBkm> bkmBySlot) {
		String op = emitWithBkms(unary.operand(), bkmBySlot);
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

	private static String emitFunctionCallB(RuntimeFunctionCall fn, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (fn.arguments().isEmpty()) {
			return "builtin(\"" + escapeString(fn.function()) + "\", Collections.emptyList())";
		}
		StringBuilder sb = new StringBuilder("builtin(\"").append(escapeString(fn.function())).append("\", Arrays.asList(");
		for (int i = 0; i < fn.arguments().size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(emitWithBkms(fn.arguments().get(i), bkmBySlot));
		}
		sb.append("))");
		return sb.toString();
	}

	private static String emitInvocationB(RuntimeInvocationExpression inv, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (inv.function().isPresent()) {
			// Builtin function call
			if (inv.positionalArguments().isEmpty() && inv.namedArguments().isEmpty()) {
				return "builtin(\"" + escapeString(inv.function().get()) + "\", Collections.emptyList())";
			}
			StringBuilder sb = new StringBuilder("builtin(\"").append(escapeString(inv.function().get()))
					.append("\", Arrays.asList(");
			boolean first = true;
			for (RuntimeExpression arg : inv.positionalArguments()) {
				if (!first) sb.append(", ");
				sb.append(emitWithBkms(arg, bkmBySlot));
				first = false;
			}
			for (RuntimeNamedArgument arg : inv.namedArguments()) {
				if (!first) sb.append(", ");
				sb.append(emitWithBkms(arg.expression(), bkmBySlot));
				first = false;
			}
			sb.append("))");
			return sb.toString();
		}
		if (inv.target().isPresent()) {
			RuntimeExpression target = inv.target().get();
			// BKM invocation: if the target references a BKM slot, emit a direct method call
			if (target instanceof RuntimeValueReference ref && bkmBySlot.containsKey(ref.sourceSlot())) {
				RuntimeBkm bkm = bkmBySlot.get(ref.sourceSlot());
				if (bkm.functionKind() == RuntimeFunctionKind.FEEL && bkm.function().isPresent()) {
					RuntimeFunctionDefinition fn = bkm.function().get();
					List<RuntimeFunctionParameter> params = fn.parameters();
					StringBuilder sb = new StringBuilder("bkm_").append(bkm.resultSlot()).append("(slots");
					// Match named arguments by parameter name, then by order
					Map<String, RuntimeExpression> namedMap = new java.util.LinkedHashMap<>();
					inv.namedArguments().forEach(na -> namedMap.put(na.name(), na.expression()));
					List<RuntimeExpression> positional = inv.positionalArguments();
					for (int i = 0; i < params.size(); i++) {
						sb.append(", ");
						RuntimeExpression argExpr = namedMap.get(params.get(i).name());
						if (argExpr == null && i < positional.size()) {
							argExpr = positional.get(i);
						}
						sb.append(argExpr != null ? emitWithBkms(argExpr, bkmBySlot) : "null");
					}
					sb.append(")");
					return sb.toString();
				}
			}
			// Fallback: emit the target expression (non-BKM callable)
			return emitWithBkms(target, bkmBySlot);
		}
		return "null";
	}

	private static String emitFilterB(RuntimeFilterExpression filter, Map<Integer, RuntimeBkm> bkmBySlot) {
		String src = emitWithBkms(filter.source(), bkmBySlot);
		String flt = emitWithBkms(filter.filter(), bkmBySlot);
		return "filter(" + src + ", _elem -> { return " + flt + "; })";
	}

	private static String emitQuantifiedB(RuntimeQuantifiedExpression quant, Map<Integer, RuntimeBkm> bkmBySlot) {
		boolean isEvery = quant.quantifier() == RuntimeQuantifier.EVERY;
		if (quant.bindings().isEmpty())
			return String.valueOf(isEvery);
		return emitNestedQuantifiedB(isEvery, quant.bindings(), 0, quant.satisfies(), bkmBySlot);
	}

	private static String emitNestedQuantifiedB(boolean isEvery, List<RuntimeQuantifiedBinding> bindings, int index,
			RuntimeExpression satisfies, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (index == bindings.size()) {
			return emitWithBkms(satisfies, bkmBySlot);
		}
		RuntimeQuantifiedBinding binding = bindings.get(index);
		String src = emitWithBkms(binding.source(), bkmBySlot);
		String slot = slotName(binding.localSlot());
		String inner = emitNestedQuantifiedB(isEvery, bindings, index + 1, satisfies, bkmBySlot);
		return "quantify(" + isEvery + ", " + src + ", " + slot + " -> " + inner + ")";
	}

	private static String emitForB(RuntimeForExpression forExpr, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (forExpr.iterations().isEmpty())
			return "Collections.emptyList()";
		return emitNestedForB(forExpr.iterations(), 0, forExpr.result(), bkmBySlot);
	}

	private static String emitNestedForB(List<RuntimeIteration> iterations, int index, RuntimeExpression result,
			Map<Integer, RuntimeBkm> bkmBySlot) {
		if (index == iterations.size()) {
			return emitWithBkms(result, bkmBySlot);
		}
		RuntimeIteration iter = iterations.get(index);
		String src = emitWithBkms(iter.source(), bkmBySlot);
		String slot = slotName(iter.localSlot());
		String inner = emitNestedForB(iterations, index + 1, result, bkmBySlot);
		return "forLoop(" + src + ", " + slot + " -> " + inner + ")";
	}

	private static String emitRangeB(RuntimeRangeExpression range, Map<Integer, RuntimeBkm> bkmBySlot) {
		String low = range.lower().map(v -> emitWithBkms(v, bkmBySlot)).orElse("null");
		String up = range.upper().map(v -> emitWithBkms(v, bkmBySlot)).orElse("null");
		return "new RuntimeRangeValue(" + low + ", " + up + ", " + range.lowerBoundary() + ", " + range.upperBoundary()
				+ ")";
	}

	private static String emitContextB(RuntimeContextExpression ctx, Map<Integer, RuntimeBkm> bkmBySlot) {
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
				sb.append("{\"").append(escapeString(entry.name())).append("\", ")
						.append(emitWithBkms(entry.expression(), bkmBySlot)).append("}");
			}
			sb.append("})");
			return sb.toString();
		}
		if (ctx.entries().size() == 1) {
			return emitWithBkms(ctx.entries().get(0).expression(), bkmBySlot);
		}
		StringBuilder sb = new StringBuilder("((java.util.function.Supplier<Object>) () -> { ");
		for (int i = 0; i < ctx.entries().size(); i++) {
			RuntimeContextEntry entry = ctx.entries().get(i);
			String exprCode = emitWithBkms(entry.expression(), bkmBySlot);
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

	private static String emitListB(RuntimeListExpression list, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (list.elements().isEmpty()) {
			return "Collections.emptyList()";
		}
		StringBuilder sb = new StringBuilder("Arrays.asList(");
		for (int i = 0; i < list.elements().size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(emitWithBkms(list.elements().get(i), bkmBySlot));
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
