package io.finmsg.dmn.generator.java;

import io.finmsg.dmn.ir.*;
import java.util.List;
import java.util.Map;

/**
 * Emitter for lowering RuntimeExpression nodes into pure Java code statements.
 */
public final class JavaExpressionEmitter {

	/**
	 * Emits an expression without BKM context (for compatibility with old call
	 * sites).
	 */
	public static String emit(RuntimeExpression expr) {
		return emitWithBkms(expr, Map.of());
	}

	/**
	 * Public accessor for local slot names (used by DmnJavaGenerator for BKM method
	 * params).
	 */
	public static String localSlotName(int slot) {
		return slotName(slot);
	}

	/**
	 * Emits an expression, resolving BKM slot references to direct method calls.
	 * 
	 * @param bkmBySlot
	 *            map from slot number to RuntimeBkm, for resolving invocation
	 *            targets
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
			case RuntimeFunctionDefinition fnDef -> emitFunctionDefinition(fnDef, bkmBySlot);
			case RuntimeBetweenExpression btn -> "(" + emitWithBkms(btn.value(), bkmBySlot) + " != null && compare("
					+ emitWithBkms(btn.value(), bkmBySlot) + ", " + emitWithBkms(btn.lower(), bkmBySlot)
					+ ") >= 0 && compare(" + emitWithBkms(btn.value(), bkmBySlot) + ", "
					+ emitWithBkms(btn.upper(), bkmBySlot) + ") <= 0)";
			case RuntimeInExpression inExpr -> emitInExpression(inExpr, bkmBySlot);
			case RuntimeInstanceOfExpression inst -> "io.finmsg.dmn.runtime.DmnRuntime.instanceOf("
					+ emitWithBkms(inst.expression(), bkmBySlot) + ", " + emitTypeLiteral(inst.testedType()) + ")";
			case RuntimeRelationExpression rel -> emitRelationB(rel, bkmBySlot);
			default -> "null";
		};
	}

	private static String emitTypeLiteral(RuntimeType type) {
		if (type == null) return "null";
		if (type.elementType() != null) {
			return "io.finmsg.dmn.ir.RuntimeType.element(io.finmsg.dmn.ir.RuntimeTypeKind." + type.kind().name() + ", " + emitTypeLiteral(type.elementType()) + ")";
		}
		return "io.finmsg.dmn.ir.RuntimeType.scalar(io.finmsg.dmn.ir.RuntimeTypeKind." + type.kind().name() + ")";
	}

	private static final java.util.concurrent.atomic.AtomicInteger VAR_SEQ = new java.util.concurrent.atomic.AtomicInteger();

	private static String emitInExpression(RuntimeInExpression inExpr, Map<Integer, RuntimeBkm> bkmBySlot) {
		String value = emitWithBkms(inExpr.value(), bkmBySlot);
		RuntimeUnaryTests tests = inExpr.tests();
		if (tests == null || tests.tests().isEmpty()) {
			return value + " != null";
		}
		String tempVar = "_inVal_" + VAR_SEQ.incrementAndGet();
		StringBuilder sb = new StringBuilder("((java.util.function.Supplier<Object>) () -> { Object ");
		sb.append(tempVar).append(" = ").append(value).append("; ");
		sb.append("if (").append(tempVar).append(" == null) return null; ");
		sb.append("Boolean _res = false; ");
		for (int i = 0; i < tests.tests().size(); i++) {
			RuntimeUnaryTest test = tests.tests().get(i);
			sb.append("{ Object _t = ").append(emitInUnaryTest(tempVar, test, bkmBySlot)).append("; ");
			sb.append("if (Boolean.TRUE.equals(_t)) return ").append(tests.negated() ? "false" : "true").append("; ");
			sb.append("if (_t == null) _res = null; } ");
		}
		sb.append("return _res == null ? null : (").append(tests.negated() ? "!_res" : "_res").append("); }).get()");
		return sb.toString();
	}

	private static String emitInUnaryTest(String inputVar, RuntimeUnaryTest test, Map<Integer, RuntimeBkm> bkmBySlot) {
		return switch (test) {
			case RuntimeComparisonUnaryTest comparison -> switch (comparison.operator()) {
				case EQUAL -> "equal(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot) + ")";
				case NOT_EQUAL -> "notEqual(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot) + ")";
				case LESS -> "less(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot) + ")";
				case LESS_EQUAL -> "lessEqual(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot) + ")";
				case GREATER -> "greater(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot) + ")";
				case GREATER_EQUAL -> "greaterEqual(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot) + ")";
			};
			case RuntimeRangeUnaryTest rangeTest -> {
				RuntimeRangeExpression range = rangeTest.range();
				StringBuilder sb = new StringBuilder("((java.util.function.Supplier<Object>) () -> { ");
				if (range.lower().isPresent()) {
					String lowerExpr = emitWithBkms(range.lower().get(), bkmBySlot);
					String op = range.lowerBoundary() == RuntimeRangeBoundary.CLOSED ? "lessEqual(" : "less(";
					sb.append("Object _l = ").append(lowerExpr).append("; if (_l == null) return null; ");
					sb.append("Boolean _cL = ").append(op).append("_l, ").append(inputVar).append("); if (_cL == null) return null; ");
					sb.append("if (!Boolean.TRUE.equals(_cL)) return false; ");
				}
				if (range.upper().isPresent()) {
					String upperExpr = emitWithBkms(range.upper().get(), bkmBySlot);
					String op = range.upperBoundary() == RuntimeRangeBoundary.CLOSED ? "lessEqual(" : "less(";
					sb.append("Object _u = ").append(upperExpr).append("; if (_u == null) return null; ");
					sb.append("Boolean _cU = ").append(op).append(inputVar).append(", _u); if (_cU == null) return null; ");
					sb.append("if (!Boolean.TRUE.equals(_cU)) return false; ");
				}
				sb.append("return true; }).get()");
				yield sb.toString();
			}
			case RuntimeExpressionUnaryTest expr -> {
				String emitted = emitWithBkms(expr.expression(), bkmBySlot);
				yield "testEqualsOrContains(" + inputVar + ", " + emitted + ")";
			}
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
			case DATE -> "parseDate(\"" + escapeString(constant.value()) + "\")";
			case TIME -> "parseTime(\"" + escapeString(constant.value()) + "\")";
			case DATE_TIME -> "parseDateTime(\"" + escapeString(constant.value()) + "\")";
			case DURATION -> "parseDuration(\"" + escapeString(constant.value()) + "\")";
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
			case NOT_EQUAL -> "notEqual(" + left + ", " + right + ")";
			case LESS -> "less(" + left + ", " + right + ")";
			case LESS_EQUAL -> "lessEqual(" + left + ", " + right + ")";
			case GREATER -> "greater(" + left + ", " + right + ")";
			case GREATER_EQUAL -> "greaterEqual(" + left + ", " + right + ")";
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

	private static String emitFunctionDefinition(RuntimeFunctionDefinition fnDef, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (fnDef.body().isEmpty())
			return "null";
		if (fnDef.parameters().isEmpty() && fnDef.body().get() instanceof RuntimeInvocationExpression inv
				&& inv.function().isPresent()) {
			return "builtinCallable(\"" + escapeString(inv.function().get()) + "\")";
		}
		StringBuilder params = new StringBuilder();
		for (int i = 0; i < fnDef.parameters().size(); i++) {
			if (i > 0)
				params.append(", ");
			params.append(slotName(fnDef.parameters().get(i).localSlot()));
		}
		String body = emitWithBkms(fnDef.body().get(), bkmBySlot);
		if (fnDef.parameters().size() == 1) {
			return "(java.util.function.Function<Object, Object>) (" + params + ") -> " + body;
		} else if (fnDef.parameters().size() == 2) {
			return "(java.util.function.BiFunction<Object, Object, Object>) (" + params + ") -> " + body;
		}
		StringBuilder sb = new StringBuilder("((FeelCallable) (_args) -> { ");
		for (int i = 0; i < fnDef.parameters().size(); i++) {
			sb.append("Object ").append(slotName(fnDef.parameters().get(i).localSlot()))
					.append(" = _args != null && _args.size() > ").append(i).append(" ? _args.get(").append(i).append(") : null; ");
		}
		sb.append("return ").append(body).append("; })");
		return sb.toString();
	}

	private static String emitFunctionCallB(RuntimeFunctionCall fn, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (fn.arguments().isEmpty()) {
			return "builtin(\"" + escapeString(fn.function()) + "\", Collections.emptyList())";
		}
		StringBuilder sb = new StringBuilder("builtin(\"").append(escapeString(fn.function()))
				.append("\", asList(");
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
			String fnName = inv.function().get();
			if (!inv.namedArguments().isEmpty()) {
				if (!inv.positionalArguments().isEmpty()) return "null";
				StringBuilder sb = new StringBuilder("builtinNamed(\"").append(escapeString(fnName))
						.append("\", asMap(");
				boolean first = true;
				for (RuntimeNamedArgument arg : inv.namedArguments()) {
					if (!first)
						sb.append(", ");
					sb.append("\"").append(escapeString(arg.name())).append("\", ")
							.append(emitWithBkms(arg.expression(), bkmBySlot));
					first = false;
				}
				sb.append("))");
				return sb.toString();
			}
			if (inv.positionalArguments().isEmpty()) {
				return "builtin(\"" + escapeString(fnName) + "\", Collections.emptyList())";
			}
			StringBuilder sb = new StringBuilder("builtin(\"").append(escapeString(fnName))
					.append("\", asList(");
			boolean first = true;
			for (RuntimeExpression arg : inv.positionalArguments()) {
				if (!first)
					sb.append(", ");
				sb.append(emitWithBkms(arg, bkmBySlot));
				first = false;
			}
			sb.append("))");
			return sb.toString();
		}
		if (inv.target().isPresent()) {
			RuntimeExpression target = inv.target().get();
			// BKM invocation: if the target references a BKM slot, emit a direct method
			// call
			if (target instanceof RuntimeValueReference ref && bkmBySlot.containsKey(ref.sourceSlot())) {
				RuntimeBkm bkm = bkmBySlot.get(ref.sourceSlot());
				if (bkm.functionKind() == RuntimeFunctionKind.FEEL && bkm.function().isPresent()) {
					RuntimeFunctionDefinition fn = bkm.function().get();
					List<RuntimeFunctionParameter> params = fn.parameters();
					StringBuilder sb = new StringBuilder("bkm_").append(bkm.resultSlot()).append("(slots");
					for (RuntimeFunctionParameter param : params) {
						sb.append(", ");
						RuntimeExpression argExpr = null;
						for (int i = 0; i < inv.positionalArguments().size(); i++) {
							if (i < params.size() && params.get(i).name().equals(param.name())) {
								argExpr = inv.positionalArguments().get(i);
								break;
							}
						}
						if (argExpr == null) {
							for (RuntimeNamedArgument named : inv.namedArguments()) {
								if (named.name().equals(param.name())) {
									argExpr = named.expression();
									break;
								}
							}
						}
						sb.append(argExpr != null ? emitWithBkms(argExpr, bkmBySlot) : "null");
					}
					sb.append(")");
					return sb.toString();
				}
			}
			// Fallback: emit the target expression invocation (non-BKM callable)
			String targetCode = emitWithBkms(target, bkmBySlot);
			StringBuilder args = new StringBuilder("asList(");
			boolean first = true;
			for (RuntimeExpression arg : inv.positionalArguments()) {
				if (!first) args.append(", ");
				args.append(emitWithBkms(arg, bkmBySlot));
				first = false;
			}
			for (RuntimeNamedArgument arg : inv.namedArguments()) {
				if (!first) args.append(", ");
				args.append(emitWithBkms(arg.expression(), bkmBySlot));
				first = false;
			}
			args.append(")");
			return "invokeCallable(" + targetCode + ", " + args + ")";
		}
		return "null";
	}

	private static String emitFilterB(RuntimeFilterExpression filter, Map<Integer, RuntimeBkm> bkmBySlot) {
		String src = emitWithBkms(filter.source(), bkmBySlot);
		String flt = emitWithBkms(filter.filter(), bkmBySlot);
		String slot = slotName(filter.localSlot());
		return "filter(" + src + ", " + slot + " -> { return " + flt + "; })";
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
		String partialSlot = slotName(forExpr.partialSlot());
		return emitNestedForB(forExpr.iterations(), 0, forExpr.result(), partialSlot, bkmBySlot);
	}

	private static String emitNestedForB(List<RuntimeIteration> iterations, int index, RuntimeExpression result,
			String partialSlot, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (index == iterations.size()) {
			return emitWithBkms(result, bkmBySlot);
		}
		RuntimeIteration iter = iterations.get(index);
		String src = iter.end().isPresent()
				? "rangeList(" + emitWithBkms(iter.source(), bkmBySlot) + ", " + emitWithBkms(iter.end().get(), bkmBySlot) + ")"
				: emitWithBkms(iter.source(), bkmBySlot);
		String slot = slotName(iter.localSlot());
		String pSlot = index == 0 ? partialSlot : partialSlot + "_" + index;
		String inner = emitNestedForB(iterations, index + 1, result, partialSlot, bkmBySlot);
		return "forLoop(" + src + ", (" + slot + ", " + pSlot + ") -> " + inner + ")";
	}

	private static String emitRangeB(RuntimeRangeExpression range, Map<Integer, RuntimeBkm> bkmBySlot) {
		String low = range.lower().map(v -> emitWithBkms(v, bkmBySlot)).orElse("null");
		String up = range.upper().map(v -> emitWithBkms(v, bkmBySlot)).orElse("null");
		boolean lowerAbsent = range.lower().isEmpty();
		boolean upperAbsent = range.upper().isEmpty();
		return "new io.finmsg.dmn.runtime.RuntimeRangeValue(" + low + ", " + up
				+ ", io.finmsg.dmn.ir.RuntimeRangeBoundary." + range.lowerBoundary()
				+ ", io.finmsg.dmn.ir.RuntimeRangeBoundary." + range.upperBoundary()
				+ ", " + lowerAbsent + ", " + upperAbsent + ")";
	}

	private static String emitContextB(RuntimeContextExpression ctx, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (ctx.entries().isEmpty()) {
			return "Collections.emptyMap()";
		}
		String ctxVar = "_ctx_" + VAR_SEQ.incrementAndGet();
		StringBuilder sb = new StringBuilder("((java.util.function.Supplier<Object>) () -> { ");
		sb.append("java.util.Map<String, Object> ").append(ctxVar).append(" = new java.util.LinkedHashMap<>(); ");
		for (int i = 0; i < ctx.entries().size(); i++) {
			RuntimeContextEntry entry = ctx.entries().get(i);
			String exprCode = emitWithBkms(entry.expression(), bkmBySlot);
			String varName = slotName(entry.localSlot());
			sb.append("Object ").append(varName).append(" = ").append(exprCode).append("; ");
			if (entry.localSlot() >= 0) {
				sb.append(ctxVar).append(".put(\"").append(escapeString(entry.name())).append("\", ").append(varName).append("); ");
			}
			if (i == ctx.entries().size() - 1 && entry.localSlot() == -1) {
				sb.append("return ").append(varName).append("; ");
			}
		}
		RuntimeContextEntry last = ctx.entries().get(ctx.entries().size() - 1);
		if (last.localSlot() >= 0) {
			sb.append("return ").append(ctxVar).append("; ");
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
		StringBuilder sb = new StringBuilder("asList(");
		for (int i = 0; i < list.elements().size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(emitWithBkms(list.elements().get(i), bkmBySlot));
		}
		sb.append(")");
		return sb.toString();
	}

	private static String emitRelationB(RuntimeRelationExpression rel, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (rel.rows().isEmpty()) {
			return "Collections.emptyList()";
		}
		StringBuilder sb = new StringBuilder("asList(");
		for (int r = 0; r < rel.rows().size(); r++) {
			if (r > 0)
				sb.append(", ");
			sb.append("createContext(new Object[][] {");
			List<RuntimeExpression> row = rel.rows().get(r);
			for (int c = 0; c < row.size(); c++) {
				if (c > 0)
					sb.append(", ");
				String colName = c < rel.columns().size() ? rel.columns().get(c).name() : "col" + c;
				sb.append("{\"").append(escapeString(colName)).append("\", ").append(emitWithBkms(row.get(c), bkmBySlot)).append("}");
			}
			sb.append("})");
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
