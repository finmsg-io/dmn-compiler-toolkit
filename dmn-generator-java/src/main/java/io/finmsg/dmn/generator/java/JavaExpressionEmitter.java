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
		return emitWithBkms(expr, Map.of(), List.of());
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
		return emitWithBkms(expr, bkmBySlot, List.of());
	}

	private static String emitWithBkms(RuntimeExpression expr, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		if (expr == null) {
			return "null";
		}
		return switch (expr) {
			case RuntimeConstant constant -> emitConstant(constant);
			case RuntimeValueReference ref -> emitValueReference(ref, bkmBySlot);
			case RuntimeLocalReference local -> {
				int depth = local.lexicalDepth();
				if (depth < fnIds.size()) {
					int targetFnId = fnIds.get(fnIds.size() - 1 - depth);
					yield paramName(targetFnId, local.localSlot());
				}
				yield slotName(local.localSlot());
			}
			case RuntimeBinaryExpression binary -> emitBinaryB(binary, bkmBySlot, fnIds);
			case RuntimeUnaryExpression unary -> emitUnaryB(unary, bkmBySlot, fnIds);
			case RuntimeConditionalExpression cond -> "(isTrue(" + emitWithBkms(cond.condition(), bkmBySlot, fnIds)
					+ ") ? " + emitWithBkms(cond.thenExpression(), bkmBySlot, fnIds) + " : "
					+ emitWithBkms(cond.elseExpression(), bkmBySlot, fnIds) + ")";
			case RuntimePathExpression path -> "getPath(" + emitWithBkms(path.source(), bkmBySlot, fnIds) + ", \""
					+ escapeString(path.member()) + "\")";
			case RuntimeContextExpression ctx -> emitContextB(ctx, bkmBySlot, fnIds);
			case RuntimeListExpression list -> emitListB(list, bkmBySlot, fnIds);
			case RuntimeFunctionCall fn -> emitFunctionCallB(fn, bkmBySlot, fnIds);
			case RuntimeInvocationExpression inv -> emitInvocationB(inv, bkmBySlot, fnIds);
			case RuntimeFilterExpression filter -> emitFilterB(filter, bkmBySlot, fnIds);
			case RuntimeQuantifiedExpression quant -> emitQuantifiedB(quant, bkmBySlot, fnIds);
			case RuntimeForExpression forExpr -> emitForB(forExpr, bkmBySlot, fnIds);
			case RuntimeRangeExpression range -> emitRangeB(range, bkmBySlot, fnIds);
			case RuntimeFunctionDefinition fnDef -> emitFunctionDefinition(fnDef, bkmBySlot, fnIds);
			case RuntimeBetweenExpression btn -> emitBetweenB(btn, bkmBySlot, fnIds);
			case RuntimeInExpression inExpr -> emitInExpression(inExpr, bkmBySlot, fnIds);
			case RuntimeInstanceOfExpression inst ->
				"io.finmsg.dmn.runtime.DmnRuntime.instanceOf(" + emitWithBkms(inst.expression(), bkmBySlot, fnIds)
						+ ", " + emitTypeLiteral(inst.testedType()) + ")";
			case RuntimeRelationExpression rel -> emitRelationB(rel, bkmBySlot, fnIds);
			default -> "null";
		};
	}

	private static String emitValueReference(RuntimeValueReference ref, Map<Integer, RuntimeBkm> bkmBySlot) {
		RuntimeBkm bkm = bkmBySlot.get(ref.sourceSlot());
		if (bkm == null || bkm.functionKind() != RuntimeFunctionKind.FEEL || bkm.function().isEmpty()) {
			return "slots[" + ref.sourceSlot() + "]";
		}
		int parameterCount = bkm.function().get().parameters().size();
		String method = "bkm_" + bkm.resultSlot();
		if (parameterCount == 1) {
			return "(java.util.function.Function<Object, Object>) (_arg0) -> " + method + "(slots, _arg0)";
		}
		if (parameterCount == 2) {
			return "(java.util.function.BiFunction<Object, Object, Object>) (_arg0, _arg1) -> " + method
					+ "(slots, _arg0, _arg1)";
		}
		StringBuilder callable = new StringBuilder("((FeelCallable) (_args) -> ").append(method).append("(slots");
		for (int index = 0; index < parameterCount; index++) {
			callable.append(", _args.size() > ").append(index).append(" ? _args.get(").append(index).append(") : null");
		}
		return callable.append("))").toString();
	}

	private static String emitTypeLiteral(RuntimeType type) {
		if (type == null)
			return "null";
		if (type.elementType() != null) {
			return "io.finmsg.dmn.ir.RuntimeType.element(io.finmsg.dmn.ir.RuntimeTypeKind." + type.kind().name() + ", "
					+ emitTypeLiteral(type.elementType()) + ")";
		}
		if (type.fieldLayout() != null && !type.fieldLayout().isEmpty()) {
			StringBuilder sb = new StringBuilder("io.finmsg.dmn.ir.RuntimeType.contextFields(java.util.List.of(");
			for (int i = 0; i < type.fieldLayout().size(); i++) {
				if (i > 0)
					sb.append(", ");
				io.finmsg.dmn.ir.RuntimeField f = type.fieldLayout().get(i);
				sb.append("new io.finmsg.dmn.ir.RuntimeField(").append(f.index()).append(", \"").append(f.name())
						.append("\", ").append(emitTypeLiteral(f.type())).append(")");
			}
			sb.append("))");
			return sb.toString();
		}
		return "io.finmsg.dmn.ir.RuntimeType.scalar(io.finmsg.dmn.ir.RuntimeTypeKind." + type.kind().name() + ")";
	}

	private static final java.util.concurrent.atomic.AtomicInteger VAR_SEQ = new java.util.concurrent.atomic.AtomicInteger();

	private static String emitBetweenB(RuntimeBetweenExpression btn, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		String lower = emitWithBkms(btn.lower(), bkmBySlot, fnIds);
		String upper = emitWithBkms(btn.upper(), bkmBySlot, fnIds);
		if (isTrivialExpression(btn.value())) {
			String val = emitWithBkms(btn.value(), bkmBySlot, fnIds);
			return "(" + val + " != null && compare(" + val + ", " + lower + ") >= 0 && compare(" + val + ", " + upper
					+ ") <= 0)";
		}
		String tempVar = "_btnVal_" + VAR_SEQ.incrementAndGet();
		return "((java.util.function.Supplier<Boolean>) () -> { Object " + tempVar + " = "
				+ emitWithBkms(btn.value(), bkmBySlot, fnIds) + "; return " + tempVar + " != null && compare(" + tempVar
				+ ", " + lower + ") >= 0 && compare(" + tempVar + ", " + upper + ") <= 0; }).get()";
	}

	private static boolean isTrivialExpression(RuntimeExpression expr) {
		return expr instanceof RuntimeConstant || expr instanceof RuntimeValueReference
				|| expr instanceof RuntimeLocalReference;
	}

	private static String emitInExpression(RuntimeInExpression inExpr, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		String value = emitWithBkms(inExpr.value(), bkmBySlot, fnIds);
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
			sb.append("{ Object _t = ").append(emitInUnaryTest(tempVar, test, bkmBySlot, fnIds)).append("; ");
			sb.append("if (Boolean.TRUE.equals(_t)) return ").append(tests.negated() ? "false" : "true").append("; ");
			sb.append("if (_t == null) _res = null; } ");
		}
		sb.append("return _res == null ? null : (").append(tests.negated() ? "!_res" : "_res").append("); }).get()");
		return sb.toString();
	}

	private static String emitInUnaryTest(String inputVar, RuntimeUnaryTest test, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		return switch (test) {
			case RuntimeComparisonUnaryTest comparison -> switch (comparison.operator()) {
				case EQUAL -> "equal(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot, fnIds) + ")";
				case NOT_EQUAL ->
					"notEqual(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot, fnIds) + ")";
				case LESS -> "less(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot, fnIds) + ")";
				case LESS_EQUAL ->
					"lessEqual(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot, fnIds) + ")";
				case GREATER ->
					"greater(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot, fnIds) + ")";
				case GREATER_EQUAL ->
					"greaterEqual(" + inputVar + ", " + emitWithBkms(comparison.endpoint(), bkmBySlot, fnIds) + ")";
			};
			case RuntimeRangeUnaryTest rangeTest -> {
				RuntimeRangeExpression range = rangeTest.range();
				StringBuilder sb = new StringBuilder("((java.util.function.Supplier<Object>) () -> { ");
				if (range.lower().isPresent()) {
					String lowerExpr = emitWithBkms(range.lower().get(), bkmBySlot, fnIds);
					String op = range.lowerBoundary() == RuntimeRangeBoundary.CLOSED ? "lessEqual(" : "less(";
					sb.append("Object _l = ").append(lowerExpr).append("; if (_l == null) return null; ");
					sb.append("Boolean _cL = ").append(op).append("_l, ").append(inputVar)
							.append("); if (_cL == null) return null; ");
					sb.append("if (!Boolean.TRUE.equals(_cL)) return false; ");
				}
				if (range.upper().isPresent()) {
					String upperExpr = emitWithBkms(range.upper().get(), bkmBySlot, fnIds);
					String op = range.upperBoundary() == RuntimeRangeBoundary.CLOSED ? "lessEqual(" : "less(";
					sb.append("Object _u = ").append(upperExpr).append("; if (_u == null) return null; ");
					sb.append("Boolean _cU = ").append(op).append(inputVar)
							.append(", _u); if (_cU == null) return null; ");
					sb.append("if (!Boolean.TRUE.equals(_cU)) return false; ");
				}
				sb.append("return true; }).get()");
				yield sb.toString();
			}
			case RuntimeExpressionUnaryTest expr -> {
				String emitted = emitWithBkms(expr.expression(), bkmBySlot, fnIds);
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

	private static String emitBinaryB(RuntimeBinaryExpression binary, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		String left = emitWithBkms(binary.left(), bkmBySlot, fnIds);
		String right = emitWithBkms(binary.right(), bkmBySlot, fnIds);
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

	private static String emitUnaryB(RuntimeUnaryExpression unary, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		String op = emitWithBkms(unary.operand(), bkmBySlot, fnIds);
		return switch (unary.operator()) {
			case POSITIVE -> op;
			case NEGATE -> "negate(" + op + ")";
			case NOT -> "not(" + op + ")";
		};
	}

	private static String emitFunctionDefinition(RuntimeFunctionDefinition fnDef, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		if (fnDef.body().isEmpty())
			return "null";
		if (fnDef.parameters().isEmpty() && fnDef.body().get() instanceof RuntimeInvocationExpression inv
				&& inv.function().isPresent()) {
			return "builtinCallable(\"" + escapeString(inv.function().get()) + "\")";
		}
		int fnId = VAR_SEQ.incrementAndGet();
		List<Integer> nextFnIds = new java.util.ArrayList<>(fnIds);
		nextFnIds.add(fnId);
		StringBuilder params = new StringBuilder();
		StringBuilder checks = new StringBuilder();
		for (int i = 0; i < fnDef.parameters().size(); i++) {
			if (i > 0)
				params.append(", ");
			String pN = paramName(fnId, fnDef.parameters().get(i).localSlot());
			RuntimeFunctionParameter p = fnDef.parameters().get(i);
			if (p.type() != null && p.type().kind() != RuntimeTypeKind.ANY) {
				params.append(pN).append("_raw");
				checks.append("Object ").append(pN).append(" = coerce(").append(pN).append("_raw, ")
						.append(DmnJavaGenerator.emitTypeConstant(p.type())).append("); ");
				checks.append("if (").append(pN).append(" == null && ").append(pN)
						.append("_raw != null) return null; ");
			} else {
				params.append(pN);
			}
		}
		String body = emitWithBkms(fnDef.body().get(), bkmBySlot, nextFnIds);
		if (fnDef.type() != null && fnDef.type().returnType() != null
				&& fnDef.type().returnType().kind() != RuntimeTypeKind.ANY) {
			body = "coerce(" + body + ", " + DmnJavaGenerator.emitTypeConstant(fnDef.type().returnType()) + ")";
		}
		StringBuilder sb = new StringBuilder("new FeelCallable() {\n");
		sb.append("  public List<String> parameterNames() { return Arrays.asList(");
		for (int i = 0; i < fnDef.parameters().size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append("\"").append(escapeString(fnDef.parameters().get(i).name())).append("\"");
		}
		sb.append("); }\n");
		sb.append("  public Object call(List<Object> _pos, Map<String, Object> _named) {\n");
		for (int i = 0; i < fnDef.parameters().size(); i++) {
			RuntimeFunctionParameter p = fnDef.parameters().get(i);
			String rawVar = (p.type() != null && p.type().kind() != RuntimeTypeKind.ANY)
					? paramName(fnId, p.localSlot()) + "_raw"
					: paramName(fnId, p.localSlot());
			sb.append("    Object ").append(rawVar).append(" = (_named != null && _named.containsKey(\"")
					.append(escapeString(p.name())).append("\")) ? _named.get(\"").append(escapeString(p.name()))
					.append("\") : (_pos != null && _pos.size() > ").append(i).append(" ? _pos.get(").append(i)
					.append(") : null);\n");
		}
		sb.append("    ").append(checks).append("\n");
		sb.append("    return ").append(body).append(";\n");
		sb.append("  }\n");
		sb.append("}");
		return sb.toString();
	}

	private static String emitFunctionCallB(RuntimeFunctionCall fn, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		if (fn.arguments().isEmpty()) {
			return "builtin(\"" + escapeString(fn.function()) + "\", Collections.emptyList())";
		}
		StringBuilder sb = new StringBuilder("builtin(\"").append(escapeString(fn.function())).append("\", asList(");
		for (int i = 0; i < fn.arguments().size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(emitWithBkms(fn.arguments().get(i), bkmBySlot, fnIds));
		}
		sb.append("))");
		return sb.toString();
	}

	private static String emitInvocationB(RuntimeInvocationExpression inv, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		if (inv.function().isPresent()) {
			String fnName = inv.function().get();
			if (!inv.namedArguments().isEmpty()) {
				if (!inv.positionalArguments().isEmpty())
					return "null";
				StringBuilder sb = new StringBuilder("builtinNamed(\"").append(escapeString(fnName))
						.append("\", asMap(");
				boolean first = true;
				for (RuntimeNamedArgument arg : inv.namedArguments()) {
					if (!first)
						sb.append(", ");
					sb.append("\"").append(escapeString(arg.name())).append("\", ")
							.append(emitWithBkms(arg.expression(), bkmBySlot, fnIds));
					first = false;
				}
				sb.append("))");
				return sb.toString();
			}
			StringBuilder sb = new StringBuilder("builtin(\"").append(escapeString(fnName)).append("\", asList(");
			boolean first = true;
			for (RuntimeExpression arg : inv.positionalArguments()) {
				if (!first)
					sb.append(", ");
				sb.append(emitWithBkms(arg, bkmBySlot, fnIds));
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
					if (!inv.positionalArguments().isEmpty()) {
						if (inv.positionalArguments().size() != params.size()) {
							return "null";
						}
					} else if (!inv.namedArguments().isEmpty()) {
						if (inv.namedArguments().size() != params.size()) {
							return "null";
						}
						for (RuntimeNamedArgument named : inv.namedArguments()) {
							if (params.stream().noneMatch(p -> p.name().equals(named.name()))) {
								return "null";
							}
						}
					} else if (!params.isEmpty()) {
						return "null";
					}
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
						sb.append(argExpr != null ? emitWithBkms(argExpr, bkmBySlot, fnIds) : "null");
					}
					sb.append(")");
					return sb.toString();
				}
			}
			// Fallback: emit the target expression invocation (non-BKM callable)
			String targetCode = emitWithBkms(target, bkmBySlot, fnIds);
			StringBuilder posArgs = new StringBuilder("asList(");
			boolean first = true;
			for (RuntimeExpression arg : inv.positionalArguments()) {
				if (!first)
					posArgs.append(", ");
				posArgs.append(emitWithBkms(arg, bkmBySlot, fnIds));
				first = false;
			}
			posArgs.append(")");
			if (inv.namedArguments().isEmpty()) {
				return "invokeCallable(" + targetCode + ", " + posArgs + ", Collections.emptyMap())";
			}
			StringBuilder namedArgs = new StringBuilder("mapOf(");
			first = true;
			for (RuntimeNamedArgument arg : inv.namedArguments()) {
				if (!first)
					namedArgs.append(", ");
				namedArgs.append("entry(\"").append(escapeString(arg.name())).append("\", ")
						.append(emitWithBkms(arg.expression(), bkmBySlot, fnIds)).append(")");
				first = false;
			}
			namedArgs.append(")");
			return "invokeCallable(" + targetCode + ", " + posArgs + ", " + namedArgs + ")";
		}
		return "null";
	}

	private static String emitFilterB(RuntimeFilterExpression filter, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		String src = emitWithBkms(filter.source(), bkmBySlot, fnIds);
		String flt = emitWithBkms(filter.filter(), bkmBySlot, fnIds);
		String slot = slotName(filter.localSlot());
		return "filter(" + src + ", " + slot + " -> { return " + flt + "; })";
	}

	private static String emitQuantifiedB(RuntimeQuantifiedExpression quant, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		boolean isEvery = quant.quantifier() == RuntimeQuantifier.EVERY;
		if (quant.bindings().isEmpty())
			return String.valueOf(isEvery);
		return emitNestedQuantifiedB(isEvery, quant.bindings(), 0, quant.satisfies(), bkmBySlot, fnIds);
	}

	private static String emitNestedQuantifiedB(boolean isEvery, List<RuntimeQuantifiedBinding> bindings, int index,
			RuntimeExpression satisfies, Map<Integer, RuntimeBkm> bkmBySlot, List<Integer> fnIds) {
		if (index == bindings.size()) {
			return emitWithBkms(satisfies, bkmBySlot, fnIds);
		}
		RuntimeQuantifiedBinding binding = bindings.get(index);
		String src = emitWithBkms(binding.source(), bkmBySlot, fnIds);
		String slot = slotName(binding.localSlot());
		String inner = emitNestedQuantifiedB(isEvery, bindings, index + 1, satisfies, bkmBySlot, fnIds);
		return "quantify(" + isEvery + ", " + src + ", " + slot + " -> " + inner + ")";
	}

	private static String emitForB(RuntimeForExpression forExpr, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		if (forExpr.iterations().isEmpty())
			return "Collections.emptyList()";
		if (forExpr.iterations().size() == 1) {
			RuntimeIteration iter = forExpr.iterations().get(0);
			String src = iter.end().isPresent()
					? "rangeList(" + emitWithBkms(iter.source(), bkmBySlot, fnIds) + ", "
							+ emitWithBkms(iter.end().get(), bkmBySlot, fnIds) + ")"
					: emitWithBkms(iter.source(), bkmBySlot, fnIds);
			String slot = slotName(iter.localSlot());
			String pSlot = forExpr.partialSlot() < 0
					? "_p_partial_" + iter.localSlot()
					: slotName(forExpr.partialSlot());
			String res = emitWithBkms(forExpr.result(), bkmBySlot, fnIds);
			return "forLoop(" + src + ", (" + slot + ", " + pSlot + ") -> " + res + ")";
		}
		StringBuilder sb = new StringBuilder("((java.util.function.Supplier<Object>) () -> { ");
		sb.append("List<Object> _res = new ArrayList<>(); ");
		for (int i = 0; i < forExpr.iterations().size(); i++) {
			RuntimeIteration iter = forExpr.iterations().get(i);
			String src = iter.end().isPresent()
					? "rangeList(" + emitWithBkms(iter.source(), bkmBySlot, fnIds) + ", "
							+ emitWithBkms(iter.end().get(), bkmBySlot, fnIds) + ")"
					: emitWithBkms(iter.source(), bkmBySlot, fnIds);
			sb.append("Object _src_").append(i).append(" = ").append(src).append("; ");
			sb.append("if (!(_src_").append(i).append(" instanceof List<?> _list_").append(i)
					.append(")) return null; ");
			sb.append("for (Object ").append(slotName(iter.localSlot())).append(" : _list_").append(i).append(") { ");
		}
		if (forExpr.partialSlot() >= 0) {
			sb.append("Object ").append(slotName(forExpr.partialSlot()))
					.append(" = Collections.unmodifiableList(new ArrayList<>(_res)); ");
		}
		sb.append("_res.add(").append(emitWithBkms(forExpr.result(), bkmBySlot, fnIds)).append("); ");
		for (int i = 0; i < forExpr.iterations().size(); i++) {
			sb.append("} ");
		}
		sb.append("return _res; }).get()");
		return sb.toString();
	}

	private static String emitRangeB(RuntimeRangeExpression range, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		String low = range.lower().map(v -> emitWithBkms(v, bkmBySlot, fnIds)).orElse("null");
		String up = range.upper().map(v -> emitWithBkms(v, bkmBySlot, fnIds)).orElse("null");
		boolean lowerAbsent = range.lower().isEmpty();
		boolean upperAbsent = range.upper().isEmpty();
		return "new io.finmsg.dmn.runtime.RuntimeRangeValue(" + low + ", " + up
				+ ", io.finmsg.dmn.ir.RuntimeRangeBoundary." + range.lowerBoundary()
				+ ", io.finmsg.dmn.ir.RuntimeRangeBoundary." + range.upperBoundary() + ", " + lowerAbsent + ", "
				+ upperAbsent + ")";
	}

	private static String emitContextB(RuntimeContextExpression ctx, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		if (ctx.entries().isEmpty()) {
			return "Collections.emptyMap()";
		}
		String ctxVar = "_ctx_" + VAR_SEQ.incrementAndGet();
		StringBuilder sb = new StringBuilder("((java.util.function.Supplier<Object>) () -> { ");
		sb.append("java.util.Map<String, Object> ").append(ctxVar).append(" = new java.util.LinkedHashMap<>(); ");
		for (int i = 0; i < ctx.entries().size(); i++) {
			RuntimeContextEntry entry = ctx.entries().get(i);
			String exprCode = emitWithBkms(entry.expression(), bkmBySlot, fnIds);
			String varName = slotName(entry.localSlot());
			sb.append("Object ").append(varName).append(" = ").append(exprCode).append("; ");
			if (entry.localSlot() >= 0) {
				sb.append(ctxVar).append(".put(\"").append(escapeString(entry.name())).append("\", ").append(varName)
						.append("); ");
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

	private static String paramName(int fnId, int slot) {
		return slot < 0 ? "_p" + fnId + "_neg_" + Math.abs(slot) : "_p" + fnId + "_" + slot;
	}

	private static String emitListB(RuntimeListExpression list, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
		if (list.elements().isEmpty()) {
			return "Collections.emptyList()";
		}
		StringBuilder sb = new StringBuilder("asList(");
		for (int i = 0; i < list.elements().size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(emitWithBkms(list.elements().get(i), bkmBySlot, fnIds));
		}
		sb.append(")");
		return sb.toString();
	}

	private static String emitRelationB(RuntimeRelationExpression rel, Map<Integer, RuntimeBkm> bkmBySlot,
			List<Integer> fnIds) {
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
				sb.append("{\"").append(escapeString(colName)).append("\", ")
						.append(emitWithBkms(row.get(c), bkmBySlot, fnIds)).append("}");
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
