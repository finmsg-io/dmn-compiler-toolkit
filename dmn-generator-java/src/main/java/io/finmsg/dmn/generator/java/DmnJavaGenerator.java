package io.finmsg.dmn.generator.java;

import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Pure Java code generator from Runtime IR optimized for maximum execution
 * speed.
 */
public final class DmnJavaGenerator {

	public DmnJavaGeneratorResult generate(RuntimeOptimizedModel optimizedModel) {
		return generate(optimizedModel, DmnJavaGeneratorOptions.defaults());
	}

	public DmnJavaGeneratorResult generate(RuntimeOptimizedModel optimizedModel, DmnJavaGeneratorOptions options) {
		Objects.requireNonNull(optimizedModel, "optimizedModel");
		Objects.requireNonNull(options, "options");

		RuntimeModel model = optimizedModel.model();
		String fqcn = options.packageName() + "." + options.className();

		StringBuilder sb = new StringBuilder();
		sb.append("package ").append(options.packageName()).append(";\n\n");
		sb.append("import java.math.BigDecimal;\n");
		sb.append("import java.time.*;\n");
		sb.append("import java.util.*;\n");
		sb.append("import io.finmsg.dmn.runtime.RuntimeRangeValue;\n");
		sb.append("import io.finmsg.dmn.ir.RuntimeRangeBoundary;\n");
		sb.append("import io.finmsg.dmn.ir.RuntimeType;\n");
		sb.append("import io.finmsg.dmn.ir.RuntimeTypeKind;\n");
		sb.append("import io.finmsg.dmn.ir.RuntimeField;\n\n");
		sb.append("/** Generated high-performance DMN Decision Engine. */\n");
		sb.append("public final class ").append(options.className())
				.append(" implements io.finmsg.dmn.generator.java.GeneratedDecisionEngine {\n\n");

		sb.append("  public static final int SLOT_COUNT = ").append(model.valueSlotCount()).append(";\n\n");

		// Build BKM slot map so emitter can resolve BKM invocations to direct method
		// calls
		Map<Integer, RuntimeBkm> bkmBySlot = new HashMap<>();
		model.businessKnowledgeModels().forEach(bkm -> bkmBySlot.put(bkm.resultSlot(), bkm));

		// Evaluation entry point
		sb.append("  @Override\n");
		sb.append("  public Object[] evaluate(Object[] inputSlots) {\n");
		sb.append("    Object[] slots = new Object[SLOT_COUNT];\n");
		sb.append("    if (inputSlots != null) {\n");
		sb.append("      System.arraycopy(inputSlots, 0, slots, 0, Math.min(inputSlots.length, SLOT_COUNT));\n");
		sb.append("    }\n\n");
		for (RuntimeInput in : model.inputs()) {
			sb.append("    slots[").append(in.valueSlot()).append("] = coerce(slots[").append(in.valueSlot())
					.append("], ").append(emitTypeConstant(in.type())).append(");\n");
		}
		sb.append("\n");

		Map<Integer, RuntimeDecision> decisionMap = new HashMap<>();
		model.decisions().forEach(dec -> decisionMap.put(dec.id(), dec));

		for (int nodeId : model.evaluationOrder()) {
			RuntimeDecision decision = decisionMap.get(nodeId);
			if (decision != null) {
				sb.append("    slots[").append(decision.resultSlot()).append("] = evaluate_")
						.append(decision.resultSlot()).append("(slots);\n");
			}
		}

		sb.append("\n    return slots;\n");
		sb.append("  }\n\n");

		sb.append("  public Object evaluateBkm(int slot, Object[] args) {\n");
		sb.append("    return evaluateBkm(slot, args, new Object[SLOT_COUNT]);\n");
		sb.append("  }\n");
		sb.append("  public Object evaluateBkm(int slot, Object[] args, Object[] slots) {\n");
		sb.append("    if (slots == null) slots = new Object[SLOT_COUNT];\n");
		sb.append("    switch (slot) {\n");
		for (RuntimeBkm bkm : model.businessKnowledgeModels()) {
			if (bkm.functionKind() == RuntimeFunctionKind.FEEL && bkm.function().isPresent()) {
				RuntimeFunctionDefinition fn = bkm.function().get();
				sb.append("      case ").append(bkm.resultSlot()).append(": {\n");
				sb.append("        if (args != null && args.length != ").append(fn.parameters().size())
						.append(") return null;\n");
				sb.append("        return bkm_").append(bkm.resultSlot()).append("(slots");
				for (int i = 0; i < fn.parameters().size(); i++) {
					sb.append(", (args != null && args.length > ").append(i).append(" ? args[").append(i)
							.append("] : null)");
				}
				sb.append(");\n");
				sb.append("      }\n");
			}
		}
		sb.append("      default: return null;\n");
		sb.append("    }\n");
		sb.append("  }\n\n");

		// Generate BKM methods first (decisions may call them)
		for (RuntimeBkm bkm : model.businessKnowledgeModels()) {
			generateBkmMethod(sb, bkm, bkmBySlot);
		}

		// Generate individual decision methods
		for (RuntimeDecision decision : model.decisions()) {
			generateDecisionMethod(sb, decision, bkmBySlot);
		}

		// Generate runtime helper methods
		generateHelperMethods(sb);

		sb.append("}\n");

		return new DmnJavaGeneratorResult(fqcn, Map.of(fqcn, sb.toString()));
	}

	public static String emitTypeConstant(RuntimeType type) {
		if (type == null)
			return "null";
		return switch (type.kind()) {
			case ANY -> "RuntimeType.scalar(RuntimeTypeKind.ANY)";
			case NULL -> "RuntimeType.scalar(RuntimeTypeKind.NULL)";
			case BOOLEAN -> "RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)";
			case NUMBER -> "RuntimeType.scalar(RuntimeTypeKind.NUMBER)";
			case STRING -> "RuntimeType.scalar(RuntimeTypeKind.STRING)";
			case DATE -> "RuntimeType.scalar(RuntimeTypeKind.DATE)";
			case TIME -> "RuntimeType.scalar(RuntimeTypeKind.TIME)";
			case DATE_TIME -> "RuntimeType.scalar(RuntimeTypeKind.DATE_TIME)";
			case DURATION -> "RuntimeType.scalar(RuntimeTypeKind.DURATION)";
			case YEARS_MONTHS_DURATION -> "RuntimeType.scalar(RuntimeTypeKind.YEARS_MONTHS_DURATION)";
			case DAYS_TIME_DURATION -> "RuntimeType.scalar(RuntimeTypeKind.DAYS_TIME_DURATION)";
			case LIST -> "RuntimeType.element(RuntimeTypeKind.LIST, " + emitTypeConstant(type.elementType()) + ")";
			case RANGE -> "RuntimeType.element(RuntimeTypeKind.RANGE, " + emitTypeConstant(type.elementType()) + ")";
			case CONTEXT -> {
				if (type.fieldLayout().isEmpty()) {
					yield "RuntimeType.scalar(RuntimeTypeKind.CONTEXT)";
				}
				StringBuilder sb = new StringBuilder("RuntimeType.contextFields(List.of(");
				for (int i = 0; i < type.fieldLayout().size(); i++) {
					if (i > 0)
						sb.append(", ");
					io.finmsg.dmn.ir.RuntimeField f = type.fieldLayout().get(i);
					sb.append("new RuntimeField(").append(f.index()).append(", \"").append(f.name()).append("\", ")
							.append(emitTypeConstant(f.type())).append(")");
				}
				sb.append("))");
				yield sb.toString();
			}
			case FUNCTION -> "RuntimeType.scalar(RuntimeTypeKind.FUNCTION)";
		};
	}

	/**
	 * Generates a static helper method for each BKM. Each parameter becomes a Java
	 * parameter; the body is the lowered FEEL expression.
	 */
	private static void generateBkmMethod(StringBuilder sb, RuntimeBkm bkm, Map<Integer, RuntimeBkm> bkmBySlot) {
		if (bkm.functionKind() != RuntimeFunctionKind.FEEL || bkm.function().isEmpty()) {
			return; // skip external/PMML BKMs
		}
		RuntimeFunctionDefinition fn = bkm.function().get();
		if (fn.body().isEmpty()) {
			return;
		}
		sb.append("  private static Object bkm_").append(bkm.resultSlot()).append("(Object[] slots");
		for (RuntimeFunctionParameter param : fn.parameters()) {
			sb.append(", Object ").append(JavaExpressionEmitter.localSlotName(param.localSlot())).append("_raw");
		}
		sb.append(") {\n");
		for (RuntimeFunctionParameter param : fn.parameters()) {
			String pName = JavaExpressionEmitter.localSlotName(param.localSlot());
			if (param.type().kind() != RuntimeTypeKind.ANY) {
				sb.append("    Object ").append(pName).append(" = coerce(").append(pName).append("_raw, ")
						.append(emitTypeConstant(param.type())).append(");\n");
				sb.append("    if (").append(pName).append(" == null && ").append(pName)
						.append("_raw != null) return null;\n");
			} else {
				sb.append("    Object ").append(pName).append(" = ").append(pName).append("_raw;\n");
			}
		}
		String body = JavaExpressionEmitter.emitWithBkms(fn.body().get(), bkmBySlot);
		RuntimeType retType = fn.type().kind() == RuntimeTypeKind.FUNCTION && fn.type().returnType() != null
				? fn.type().returnType()
				: fn.type();
		if (retType.kind() != RuntimeTypeKind.ANY) {
			sb.append("    return coerce(").append(body).append(", ").append(emitTypeConstant(retType)).append(");\n");
		} else {
			sb.append("    return ").append(body).append(";\n");
		}
		sb.append("  }\n\n");
	}

	private static void generateDecisionMethod(StringBuilder sb, RuntimeDecision decision,
			Map<Integer, RuntimeBkm> bkmBySlot) {
		sb.append("  private Object evaluate_").append(decision.resultSlot()).append("(Object[] slots) {\n");

		if (decision.decisionTable().isPresent()) {
			RuntimeDecisionTable table = decision.decisionTable().get();
			sb.append("    return coerce(evaluate_table_").append(decision.resultSlot()).append("(slots), ")
					.append(emitTypeConstant(decision.type())).append(");\n");
			sb.append("  }\n\n");
			sb.append("  private Object evaluate_table_").append(decision.resultSlot()).append("(Object[] slots) {\n");
			generateDecisionTableBody(sb, table, bkmBySlot);
		} else if (decision.expression().isPresent()) {
			RuntimeExpression expr = decision.expression().get();
			sb.append("    return coerce(").append(JavaExpressionEmitter.emitWithBkms(expr, bkmBySlot)).append(", ")
					.append(emitTypeConstant(decision.type())).append(");\n");
		} else {
			sb.append("    return slots[").append(decision.resultSlot()).append("];\n");
		}

		sb.append("  }\n\n");
	}

	private static void generateDecisionTableBody(StringBuilder sb, RuntimeDecisionTable table,
			Map<Integer, RuntimeBkm> bkmBySlot) {
		sb.append("    // Evaluate Decision Table Inputs\n");
		for (int i = 0; i < table.inputs().size(); i++) {
			RuntimeDecisionTableInput input = table.inputs().get(i);
			sb.append("    Object in_").append(i).append(" = ")
					.append(JavaExpressionEmitter.emitWithBkms(input.expression(), bkmBySlot)).append(";\n");
		}

		boolean isMultiMatch = table.hitPolicy() == RuntimeHitPolicy.COLLECT
				|| table.hitPolicy() == RuntimeHitPolicy.RULE_ORDER
				|| table.hitPolicy() == RuntimeHitPolicy.OUTPUT_ORDER || table.hitPolicy() == RuntimeHitPolicy.PRIORITY;
		if (isMultiMatch) {
			sb.append("    List<Object> matches = new ArrayList<>();\n");
		}

		sb.append("\n    // Match Decision Table Rules (Hit Policy: ").append(table.hitPolicy()).append(")\n");
		for (int r = 0; r < table.rules().size(); r++) {
			RuntimeDecisionTableRule rule = table.rules().get(r);
			sb.append("    if (");
			if (rule.inputEntries().isEmpty()) {
				sb.append("true");
			} else {
				for (int i = 0; i < rule.inputEntries().size(); i++) {
					if (i > 0)
						sb.append(" && ");
					RuntimeUnaryTests tests = rule.inputEntries().get(i);
					sb.append(emitUnaryTests("in_" + i, tests));
				}
			}
			sb.append(") {\n");
			String outExpr;
			if (rule.outputEntries().isEmpty()) {
				outExpr = "null";
			} else if (table.outputs().size() > 1) {
				StringBuilder ctxSb = new StringBuilder("createContext(new Object[][]{");
				for (int o = 0; o < table.outputs().size(); o++) {
					if (o > 0)
						ctxSb.append(", ");
					String name = table.outputs().get(o).name().orElse("output" + (o + 1));
					String valCode = o < rule.outputEntries().size()
							? JavaExpressionEmitter.emitWithBkms(rule.outputEntries().get(o), bkmBySlot)
							: "null";
					ctxSb.append("{\"").append(name).append("\", ").append(valCode).append("}");
				}
				ctxSb.append("})");
				outExpr = ctxSb.toString();
			} else {
				outExpr = JavaExpressionEmitter.emitWithBkms(rule.outputEntries().get(0), bkmBySlot);
			}
			if (isMultiMatch) {
				sb.append("      matches.add(").append(outExpr).append(");\n");
			} else {
				sb.append("      return ").append(outExpr).append(";\n");
			}
			sb.append("    }\n");
		}

		String defaultExpr = null;
		if (table.outputs().size() == 1 && table.outputs().get(0).defaultValue().isPresent()) {
			defaultExpr = JavaExpressionEmitter.emitWithBkms(table.outputs().get(0).defaultValue().get(), bkmBySlot);
		} else if (table.outputs().size() > 1 && table.outputs().stream().anyMatch(o -> o.defaultValue().isPresent())) {
			StringBuilder ctxSb = new StringBuilder("createContext(new Object[][]{");
			for (int o = 0; o < table.outputs().size(); o++) {
				if (o > 0)
					ctxSb.append(", ");
				String name = table.outputs().get(o).name().orElse("output" + (o + 1));
				String valCode = table.outputs().get(o).defaultValue()
						.map(d -> JavaExpressionEmitter.emitWithBkms(d, bkmBySlot)).orElse("null");
				ctxSb.append("{\"").append(name).append("\", ").append(valCode).append("}");
			}
			ctxSb.append("})");
			defaultExpr = ctxSb.toString();
		}

		if (isMultiMatch) {
			if (defaultExpr != null) {
				sb.append("    if (matches.isEmpty()) return ").append(defaultExpr).append(";\n");
			}
			if (table.aggregation().isPresent()) {
				sb.append("    return aggregate(matches, \"").append(table.aggregation().get().name()).append("\");\n");
			} else if (table.hitPolicy() == RuntimeHitPolicy.OUTPUT_ORDER) {
				sb.append("    return sortOutputOrder(matches, ").append(emitAllowedValuesList(table)).append(");\n");
			} else if (table.hitPolicy() == RuntimeHitPolicy.PRIORITY) {
				sb.append("    List<Object> sorted = sortOutputOrder(matches, ").append(emitAllowedValuesList(table))
						.append(");\n");
				sb.append("    return sorted.isEmpty() ? null : sorted.get(0);\n");
			} else {
				sb.append("    return Collections.unmodifiableList(new ArrayList<>(matches));\n");
			}
		} else {
			sb.append("    return ").append(defaultExpr != null ? defaultExpr : "null").append(";\n");
		}
	}

	private static String emitAllowedValuesList(RuntimeDecisionTable table) {
		List<String> outputDomains = new ArrayList<>();
		for (RuntimeDecisionTableOutput out : table.outputs()) {
			if (out.allowedValues().isPresent()) {
				RuntimeUnaryTests tests = out.allowedValues().get();
				List<String> entries = new ArrayList<>();
				for (RuntimeUnaryTest test : tests.tests()) {
					if (test instanceof RuntimeComparisonUnaryTest comp) {
						entries.add(JavaExpressionEmitter.emit(comp.endpoint()));
					} else if (test instanceof RuntimeExpressionUnaryTest expr) {
						entries.add(JavaExpressionEmitter.emit(expr.expression()));
					}
				}
				if (!entries.isEmpty()) {
					outputDomains.add("Arrays.asList(" + String.join(", ", entries) + ")");
				} else {
					outputDomains.add("Collections.emptyList()");
				}
			} else {
				outputDomains.add("Collections.emptyList()");
			}
		}
		if (table.outputs().size() == 1) {
			return outputDomains.isEmpty() ? "Collections.emptyList()" : outputDomains.get(0);
		}
		return "Arrays.asList(" + String.join(", ", outputDomains) + ")";
	}

	private static String emitUnaryTests(String inputVar, RuntimeUnaryTests tests) {
		if (tests == null || tests.wildcard() || tests.tests().isEmpty()) {
			return "true";
		}
		StringBuilder sb = new StringBuilder("(");
		for (int i = 0; i < tests.tests().size(); i++) {
			if (i > 0)
				sb.append(" || ");
			RuntimeUnaryTest test = tests.tests().get(i);
			sb.append(emitUnaryTest(inputVar, test));
		}
		sb.append(")");
		if (tests.negated()) {
			return "(!" + sb + ")";
		}
		return sb.toString();
	}

	private static String emitUnaryTest(String inputVar, RuntimeUnaryTest test) {
		return switch (test) {
			case RuntimeComparisonUnaryTest comparison -> switch (comparison.operator()) {
				case EQUAL -> "Boolean.TRUE.equals(equal(" + inputVar + ", "
						+ JavaExpressionEmitter.emit(comparison.endpoint()) + "))";
				case NOT_EQUAL -> "Boolean.TRUE.equals(notEqual(" + inputVar + ", "
						+ JavaExpressionEmitter.emit(comparison.endpoint()) + "))";
				case LESS -> "Boolean.TRUE.equals(less(" + inputVar + ", "
						+ JavaExpressionEmitter.emit(comparison.endpoint()) + "))";
				case LESS_EQUAL -> "Boolean.TRUE.equals(lessEqual(" + inputVar + ", "
						+ JavaExpressionEmitter.emit(comparison.endpoint()) + "))";
				case GREATER -> "Boolean.TRUE.equals(greater(" + inputVar + ", "
						+ JavaExpressionEmitter.emit(comparison.endpoint()) + "))";
				case GREATER_EQUAL -> "Boolean.TRUE.equals(greaterEqual(" + inputVar + ", "
						+ JavaExpressionEmitter.emit(comparison.endpoint()) + "))";
			};
			case RuntimeRangeUnaryTest rangeTest -> emitRangeUnaryTest(inputVar, rangeTest);
			case RuntimeExpressionUnaryTest expr -> {
				String emitted = JavaExpressionEmitter.emit(expr.expression());
				if (emitted.startsWith("equal(") || emitted.startsWith("compare(") || emitted.startsWith("isTrue(")
						|| emitted.startsWith("in(")) {
					yield emitted;
				}
				yield "testEqualsOrContains(" + inputVar + ", " + emitted + ")";
			}
		};
	}

	private static String emitRangeUnaryTest(String inputVar, RuntimeRangeUnaryTest rangeTest) {
		RuntimeRangeExpression range = rangeTest.range();
		StringBuilder sb = new StringBuilder("(");
		sb.append(inputVar).append(" != null");
		if (range.lower().isPresent()) {
			String lowerExpr = JavaExpressionEmitter.emit(range.lower().get());
			String op = range.lowerBoundary() == RuntimeRangeBoundary.CLOSED ? "lessEqual(" : "less(";
			sb.append(" && ((").append(lowerExpr).append(") == null || Boolean.TRUE.equals(").append(op)
					.append(lowerExpr).append(", ").append(inputVar).append(")))");
		}
		if (range.upper().isPresent()) {
			String upperExpr = JavaExpressionEmitter.emit(range.upper().get());
			String op = range.upperBoundary() == RuntimeRangeBoundary.CLOSED ? "lessEqual(" : "less(";
			sb.append(" && ((").append(upperExpr).append(") == null || Boolean.TRUE.equals(").append(op)
					.append(inputVar).append(", ").append(upperExpr).append(")))");
		}
		sb.append(")");
		return sb.toString();
	}

	private static void generateHelperMethods(StringBuilder sb) {
		sb.append(
				"""
						  private static boolean isTrue(Object val) {
						    return Boolean.TRUE.equals(val);
						  }
						  private static Object coerce(Object value, RuntimeType targetType) {
						    return io.finmsg.dmn.runtime.DmnRuntime.coerce(value, targetType);
						  }
						  private static String stringValue(Object val) {
						    return io.finmsg.dmn.runtime.DmnRuntime.formatFeelString(val);
						  }
						  private static BigDecimal toBigDecimal(Object val) {
						    if (val == null) return null;
						    if (val instanceof BigDecimal d) return d;
						    if (val instanceof Number n) return new BigDecimal(n.toString());
						    return null;
						  }
						  private static java.time.Period periodFromMonths(long totalMonths) {
						    int years = (int) (totalMonths / 12);
						    int months = (int) (totalMonths % 12);
						    return java.time.Period.of(years, months, 0);
						  }
						  private static Object add(Object a, Object b) {
						    if (a == null || b == null) return null;
						    if (a instanceof String sl && b instanceof String sr) return sl + sr;
						    if (a instanceof Number && b instanceof Number) return toBigDecimal(a).add(toBigDecimal(b));
						    if (a instanceof java.time.LocalDate d && b instanceof java.time.Period p) return d.plus(p);
						    if (a instanceof java.time.Period p && b instanceof java.time.LocalDate d) return d.plus(p);
						    if (a instanceof java.time.LocalDate d && b instanceof java.time.Duration dur) return d.atStartOfDay().plus(dur).toLocalDate();
						    if (a instanceof java.time.Duration dur && b instanceof java.time.LocalDate d) return d.atStartOfDay().plus(dur).toLocalDate();
						    if (a instanceof java.time.LocalTime t && b instanceof java.time.Duration dur) return t.plus(dur);
						    if (a instanceof java.time.Duration dur && b instanceof java.time.LocalTime t) return t.plus(dur);
						    if (a instanceof java.time.OffsetTime ot && b instanceof java.time.Duration dur) return ot.plus(dur);
						    if (a instanceof java.time.Duration dur && b instanceof java.time.OffsetTime ot) return ot.plus(dur);
						    if (a instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime nzt && b instanceof java.time.Duration dur) return new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime(nzt.value().plus(dur), nzt.zone());
						    if (a instanceof java.time.Duration dur && b instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime nzt) return new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime(nzt.value().plus(dur), nzt.zone());
						    if (a instanceof java.time.LocalDateTime dt && b instanceof java.time.Period p) return dt.plus(p);
						    if (a instanceof java.time.Period p && b instanceof java.time.LocalDateTime dt) return dt.plus(p);
						    if (a instanceof java.time.LocalDateTime dt && b instanceof java.time.Duration dur) return dt.plus(dur);
						    if (a instanceof java.time.Duration dur && b instanceof java.time.LocalDateTime dt) return dt.plus(dur);
						    if (a instanceof java.time.OffsetDateTime odt && b instanceof java.time.Period p) return odt.plus(p);
						    if (a instanceof java.time.Period p && b instanceof java.time.OffsetDateTime odt) return odt.plus(p);
						    if (a instanceof java.time.OffsetDateTime odt && b instanceof java.time.Duration dur) return odt.plus(dur);
						    if (a instanceof java.time.Duration dur && b instanceof java.time.OffsetDateTime odt) return odt.plus(dur);
						    if (a instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime nzdt && b instanceof java.time.Period p) return new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime(nzdt.value().plus(p), nzdt.zone());
						    if (a instanceof java.time.Period p && b instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime nzdt) return new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime(nzdt.value().plus(p), nzdt.zone());
						    if (a instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime nzdt && b instanceof java.time.Duration dur) return new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime(nzdt.value().plus(dur), nzdt.zone());
						    if (a instanceof java.time.Duration dur && b instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime nzdt) return new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime(nzdt.value().plus(dur), nzdt.zone());
						    if (a instanceof java.time.Period p1 && b instanceof java.time.Period p2) return periodFromMonths(p1.toTotalMonths() + p2.toTotalMonths());
						    if (a instanceof java.time.Duration d1 && b instanceof java.time.Duration d2) return d1.plus(d2);
						    BigDecimal da = toBigDecimal(a), db = toBigDecimal(b);
						    return (da == null || db == null) ? null : da.add(db);
						  }
						  private static Object subtract(Object a, Object b) {
						    if (a == null || b == null) return null;
						    if (a instanceof Number && b instanceof Number) return toBigDecimal(a).subtract(toBigDecimal(b));
						    if (a instanceof java.time.LocalDate d1 && b instanceof java.time.LocalDate d2) return java.time.Duration.ofDays(java.time.temporal.ChronoUnit.DAYS.between(d2, d1));
						    if (a instanceof java.time.LocalDate d && b instanceof java.time.Period p) return d.minus(p);
						    if (a instanceof java.time.LocalDate d && b instanceof java.time.Duration dur) return d.atStartOfDay().minus(dur).toLocalDate();
						    if (a instanceof java.time.LocalTime t1 && b instanceof java.time.LocalTime t2) return java.time.Duration.between(t2, t1);
						    if (a instanceof java.time.LocalTime t && b instanceof java.time.Duration dur) return t.minus(dur);
						    if (a instanceof java.time.OffsetTime ot1 && b instanceof java.time.OffsetTime ot2) return java.time.Duration.between(ot2, ot1);
						    if (a instanceof java.time.OffsetTime ot && b instanceof java.time.Duration dur) return ot.minus(dur);
						    if (a instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime nzt && b instanceof java.time.Duration dur) return new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime(nzt.value().minus(dur), nzt.zone());
						    if (a instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime nzt1 && b instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime nzt2) return java.time.Duration.between(nzt2.value(), nzt1.value());
						    if (a instanceof java.time.LocalDateTime dt1 && b instanceof java.time.LocalDateTime dt2) return java.time.Duration.between(dt2, dt1);
						    if (a instanceof java.time.LocalDateTime dt && b instanceof java.time.Period p) return dt.minus(p);
						    if (a instanceof java.time.LocalDateTime dt && b instanceof java.time.Duration dur) return dt.minus(dur);
						    if (a instanceof java.time.OffsetDateTime odt && b instanceof java.time.Duration dur) return odt.minus(dur);
						    if (a instanceof java.time.OffsetDateTime odt && b instanceof java.time.Period p) return odt.minus(p);
						    if (a instanceof java.time.ZonedDateTime zdt && b instanceof java.time.Duration dur) return zdt.minus(dur);
						    if (a instanceof java.time.ZonedDateTime zdt && b instanceof java.time.Period p) return zdt.minus(p);
						    if (a instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime nzdt && b instanceof java.time.Period p) return new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime(nzdt.value().minus(p), nzdt.zone());
						    if (a instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime nzdt && b instanceof java.time.Duration dur) return new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime(nzdt.value().minus(dur), nzdt.zone());
						    java.time.Instant i1 = io.finmsg.dmn.runtime.DmnRuntime.toInstant(a);
						    java.time.Instant i2 = io.finmsg.dmn.runtime.DmnRuntime.toInstant(b);
						    if (i1 != null && i2 != null) return java.time.Duration.between(i2, i1);
						    if (i1 != null && b instanceof java.time.LocalDate d) return java.time.Duration.between(d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), i1);
						    if (a instanceof java.time.LocalDate d && i2 != null) return java.time.Duration.between(i2, d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
						    if (a instanceof java.time.Period p1 && b instanceof java.time.Period p2) return periodFromMonths(p1.toTotalMonths() - p2.toTotalMonths());
						    if (a instanceof java.time.Duration d1 && b instanceof java.time.Duration d2) return d1.minus(d2);
						    BigDecimal da = toBigDecimal(a), db = toBigDecimal(b);
						    return (da == null || db == null) ? null : da.subtract(db);
						  }
						  private static Object multiply(Object a, Object b) {
						    if (a == null || b == null) return null;
						    if (a instanceof Number && b instanceof Number) return toBigDecimal(a).multiply(toBigDecimal(b));
						    if (a instanceof java.time.Duration dur && b instanceof Number n) return java.time.Duration.ofNanos((long) (dur.toNanos() * n.doubleValue()));
						    if (a instanceof Number n && b instanceof java.time.Duration dur) return java.time.Duration.ofNanos((long) (dur.toNanos() * n.doubleValue()));
						    if (a instanceof java.time.Period p && b instanceof Number n) return periodFromMonths((long) (p.toTotalMonths() * n.doubleValue()));
						    if (a instanceof Number n && b instanceof java.time.Period p) return periodFromMonths((long) (p.toTotalMonths() * n.doubleValue()));
						    BigDecimal da = toBigDecimal(a), db = toBigDecimal(b);
						    return (da == null || db == null) ? null : da.multiply(db);
						  }
						  private static Object divide(Object a, Object b) {
						    if (a == null || b == null) return null;
						    if (a instanceof Number && b instanceof Number) {
						      BigDecimal da = toBigDecimal(a), db = toBigDecimal(b);
						      return (da == null || db == null || db.signum() == 0) ? null : da.divide(db, java.math.MathContext.DECIMAL128);
						    }
						    if (a instanceof java.time.Duration dur && b instanceof Number n) {
						      if (n.doubleValue() == 0) return null;
						      return java.time.Duration.ofNanos((long) (dur.toNanos() / n.doubleValue()));
						    }
						    if (a instanceof java.time.Duration d1 && b instanceof java.time.Duration d2) {
						      if (d2.toNanos() == 0) return null;
						      return BigDecimal.valueOf(d1.toNanos()).divide(BigDecimal.valueOf(d2.toNanos()), java.math.MathContext.DECIMAL128);
						    }
						    if (a instanceof java.time.Period p && b instanceof Number n) {
						      if (n.doubleValue() == 0) return null;
						      return periodFromMonths((long) (p.toTotalMonths() / n.doubleValue()));
						    }
						    if (a instanceof java.time.Period p1 && b instanceof java.time.Period p2) {
						      if (p2.toTotalMonths() == 0) return null;
						      return BigDecimal.valueOf(p1.toTotalMonths()).divide(BigDecimal.valueOf(p2.toTotalMonths()), java.math.MathContext.DECIMAL128);
						    }
						    return null;
						  }
						  private static Object power(Object a, Object b) {
						    BigDecimal da = toBigDecimal(a), db = toBigDecimal(b);
						    return (da == null || db == null) ? null : BigDecimal.valueOf(Math.pow(da.doubleValue(), db.doubleValue()));
						  }
						  private static Object negate(Object a) {
						    if (a == null) return null;
						    if (a instanceof java.time.Duration dur) return dur.negated();
						    if (a instanceof java.time.Period p) return p.negated();
						    BigDecimal da = toBigDecimal(a);
						    return da == null ? null : da.negate();
						  }
						  private static Boolean not(Object a) {
						    return a instanceof Boolean b ? !b : null;
						  }
						  private static Boolean and(Object a, Object b) {
						    if (Boolean.FALSE.equals(a) || Boolean.FALSE.equals(b)) return false;
						    if (Boolean.TRUE.equals(a) && Boolean.TRUE.equals(b)) return true;
						    return null;
						  }
						  private static Boolean or(Object a, Object b) {
						    if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) return true;
						    if (Boolean.FALSE.equals(a) && Boolean.FALSE.equals(b)) return false;
						    return null;
						  }
						  private static Boolean equal(Object a, Object b) {
						    return io.finmsg.dmn.runtime.DmnRuntime.equal(a, b);
						  }
						  private static Boolean notEqual(Object a, Object b) {
						    Boolean eq = equal(a, b);
						    return eq == null ? null : !eq;
						  }
						  private static Integer compare(Object a, Object b) {
						    return io.finmsg.dmn.runtime.DmnRuntime.compare(a, b);
						  }
						  private static Boolean less(Object a, Object b) {
						    Integer c = compare(a, b);
						    return c == null ? null : c < 0;
						  }
						  private static Boolean lessEqual(Object a, Object b) {
						    Integer c = compare(a, b);
						    return c == null ? null : c <= 0;
						  }
						  private static Boolean greater(Object a, Object b) {
						    Integer c = compare(a, b);
						    return c == null ? null : c > 0;
						  }
						  private static Boolean greaterEqual(Object a, Object b) {
						    Integer c = compare(a, b);
						    return c == null ? null : c >= 0;
						  }
						  private static boolean testEqualsOrContains(Object input, Object expected) {
						    if (expected instanceof java.util.Collection<?> col) {
						      for (Object elem : col) {
						        if (elem instanceof io.finmsg.dmn.runtime.RuntimeRangeValue range) {
						          if (Boolean.TRUE.equals(io.finmsg.dmn.runtime.DmnRuntime.contains(range, input))) return true;
						        } else if (Boolean.TRUE.equals(equal(input, elem))) {
						          return true;
						        }
						      }
						    }
						    if (expected instanceof io.finmsg.dmn.runtime.RuntimeRangeValue range) {
						      return Boolean.TRUE.equals(io.finmsg.dmn.runtime.DmnRuntime.contains(range, input));
						    }
						    return Boolean.TRUE.equals(equal(input, expected));
						  }
						  private static java.time.LocalDate parseDate(String s) {
						    return io.finmsg.dmn.runtime.DmnRuntime.parseFeelDate(s);
						  }
						  private static Object parseTime(String s) {
						    if (s == null) return null;
						    try {
						      return io.finmsg.dmn.runtime.DmnRuntime.parseTime(s);
						    } catch (Exception e) { return null; }
						  }
						  private static Object parseDateTime(String s) {
						    if (s == null) return null;
						    try {
						      return io.finmsg.dmn.runtime.DmnRuntime.parseDateTime(s);
						    } catch (Exception e) { return null; }
						  }
						  private static Object parseDuration(String s) {
						    if (s == null) return null;
						    try {
						      return io.finmsg.dmn.runtime.DmnRuntime.parseDuration(s);
						    } catch (Exception e) { return null; }
						  }
						  private static List<Object> sortOutputOrder(List<Object> matches, Object domainObj) {
						    if (matches == null || matches.isEmpty()) return matches;
						    if (domainObj instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof List<?>) {
						      List<List<Object>> domains = (List<List<Object>>) domainObj;
						      List<Object> copy = new ArrayList<>(matches);
						      copy.sort((a, b) -> {
						        for (int i = 0; i < domains.size(); i++) {
						          List<Object> dom = domains.get(i);
						          if (dom.isEmpty()) continue;
						          Object valA = (a instanceof Map<?, ?> mapA) ? (i < mapA.values().size() ? new ArrayList<>(mapA.values()).get(i) : null) : a;
						          Object valB = (b instanceof Map<?, ?> mapB) ? (i < mapB.values().size() ? new ArrayList<>(mapB.values()).get(i) : null) : b;
						          int idxA = dom.indexOf(valA);
						          int idxB = dom.indexOf(valB);
						          int posA = idxA < 0 ? Integer.MAX_VALUE : idxA;
						          int posB = idxB < 0 ? Integer.MAX_VALUE : idxB;
						          if (posA != posB) return Integer.compare(posA, posB);
						        }
						        return 0;
						      });
						      return List.copyOf(copy);
						    }
						    if (domainObj instanceof List<?> domain && !domain.isEmpty()) {
						      List<Object> copy = new ArrayList<>(matches);
						      copy.sort(java.util.Comparator.comparingInt(val -> {
						        int idx = domain.indexOf(val);
						        return idx < 0 ? Integer.MAX_VALUE : idx;
						      }));
						      return List.copyOf(copy);
						    }
						    return matches;
						  }
						  private static Object getPath(Object source, String property) {
						    if (source == null) return null;
						    if (source instanceof Map<?, ?> map) return map.get(property);
						    if (source instanceof List<?> list) {
						      List<Object> res = new ArrayList<>();
						      for (Object item : list) res.add(getPath(item, property));
						      return res;
						    }
						    if (source instanceof java.time.LocalDate ld) {
						      return switch (property) {
						        case "year" -> BigDecimal.valueOf(ld.getYear());
						        case "month" -> BigDecimal.valueOf(ld.getMonthValue());
						        case "day" -> BigDecimal.valueOf(ld.getDayOfMonth());
						        case "weekday" -> BigDecimal.valueOf(ld.getDayOfWeek().getValue());
						        default -> null;
						      };
						    }
						    if (source instanceof java.time.LocalTime lt) {
						      return switch (property) {
						        case "hour" -> BigDecimal.valueOf(lt.getHour());
						        case "minute" -> BigDecimal.valueOf(lt.getMinute());
						        case "second" -> BigDecimal.valueOf(lt.getSecond());
						        default -> null;
						      };
						    }
						    if (source instanceof java.time.OffsetTime ot) {
						      return switch (property) {
						        case "hour" -> BigDecimal.valueOf(ot.getHour());
						        case "minute" -> BigDecimal.valueOf(ot.getMinute());
						        case "second" -> BigDecimal.valueOf(ot.getSecond());
						        case "time offset" -> java.time.Duration.ofSeconds(ot.getOffset().getTotalSeconds());
						        case "timezone" -> ot.getOffset().getId();
						        default -> null;
						      };
						    }
						    if (source instanceof java.time.LocalDateTime ldt) {
						      return switch (property) {
						        case "year" -> BigDecimal.valueOf(ldt.getYear());
						        case "month" -> BigDecimal.valueOf(ldt.getMonthValue());
						        case "day" -> BigDecimal.valueOf(ldt.getDayOfMonth());
						        case "weekday" -> BigDecimal.valueOf(ldt.getDayOfWeek().getValue());
						        case "hour" -> BigDecimal.valueOf(ldt.getHour());
						        case "minute" -> BigDecimal.valueOf(ldt.getMinute());
						        case "second" -> BigDecimal.valueOf(ldt.getSecond());
						        default -> null;
						      };
						    }
						    if (source instanceof java.time.OffsetDateTime odt) {
						      return switch (property) {
						        case "year" -> BigDecimal.valueOf(odt.getYear());
						        case "month" -> BigDecimal.valueOf(odt.getMonthValue());
						        case "day" -> BigDecimal.valueOf(odt.getDayOfMonth());
						        case "weekday" -> BigDecimal.valueOf(odt.getDayOfWeek().getValue());
						        case "hour" -> BigDecimal.valueOf(odt.getHour());
						        case "minute" -> BigDecimal.valueOf(odt.getMinute());
						        case "second" -> BigDecimal.valueOf(odt.getSecond());
						        case "time offset" -> java.time.Duration.ofSeconds(odt.getOffset().getTotalSeconds());
						        case "timezone" -> odt.getOffset().getId();
						        default -> null;
						      };
						    }
						    if (source instanceof java.time.ZonedDateTime zdt) {
						      return switch (property) {
						        case "year" -> BigDecimal.valueOf(zdt.getYear());
						        case "month" -> BigDecimal.valueOf(zdt.getMonthValue());
						        case "day" -> BigDecimal.valueOf(zdt.getDayOfMonth());
						        case "weekday" -> BigDecimal.valueOf(zdt.getDayOfWeek().getValue());
						        case "hour" -> BigDecimal.valueOf(zdt.getHour());
						        case "minute" -> BigDecimal.valueOf(zdt.getMinute());
						        case "second" -> BigDecimal.valueOf(zdt.getSecond());
						        case "time offset" -> java.time.Duration.ofSeconds(zdt.getOffset().getTotalSeconds());
						        case "timezone" -> zdt.getZone().getId();
						        default -> null;
						      };
						    }
						    if (source instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime nzdt) {
						      return switch (property) {
						        case "year" -> BigDecimal.valueOf(nzdt.value().getYear());
						        case "month" -> BigDecimal.valueOf(nzdt.value().getMonthValue());
						        case "day" -> BigDecimal.valueOf(nzdt.value().getDayOfMonth());
						        case "weekday" -> BigDecimal.valueOf(nzdt.value().getDayOfWeek().getValue());
						        case "hour" -> BigDecimal.valueOf(nzdt.value().getHour());
						        case "minute" -> BigDecimal.valueOf(nzdt.value().getMinute());
						        case "second" -> BigDecimal.valueOf(nzdt.value().getSecond());
						        case "time offset" -> java.time.Duration.ofSeconds(nzdt.zone().getRules().getOffset(nzdt.value()).getTotalSeconds());
						        case "timezone" -> nzdt.zone().getId();
						        default -> null;
						      };
						    }
						    if (source instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime nzt) {
						      return switch (property) {
						        case "hour" -> BigDecimal.valueOf(nzt.value().getHour());
						        case "minute" -> BigDecimal.valueOf(nzt.value().getMinute());
						        case "second" -> BigDecimal.valueOf(nzt.value().getSecond());
						        case "timezone" -> nzt.zone().getId();
						        default -> null;
						      };
						    }
						    if (source instanceof java.time.Period p) {
						      return switch (property) {
						        case "years" -> BigDecimal.valueOf(p.getYears());
						        case "months" -> BigDecimal.valueOf(p.getMonths());
						        default -> null;
						      };
						    }
						    if (source instanceof java.time.Duration dur) {
						      return switch (property) {
						        case "days" -> BigDecimal.valueOf(dur.toDays());
						        case "hours" -> BigDecimal.valueOf(dur.toHoursPart());
						        case "minutes" -> BigDecimal.valueOf(dur.toMinutesPart());
						        case "seconds" -> BigDecimal.valueOf(dur.toSecondsPart());
						        default -> null;
						      };
						    }
						    if (source instanceof io.finmsg.dmn.runtime.RuntimeRangeValue range) {
						      return switch (property) {
						        case "start" -> range.lower();
						        case "end" -> range.upper() != null ? range.upper() : (range.lowerBoundary() == io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED && range.upperBoundary() == io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED ? range.lower() : null);
						        case "start included" -> range.lowerBoundary() == io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED;
						        case "end included" -> range.upperBoundary() == io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED;
						        default -> null;
						      };
						    }
						    return null;
						  }
						  private static Map<String, Object> createContext(Object[][] entries) {
						    Map<String, Object> map = new LinkedHashMap<>();
						    for (Object[] entry : entries) map.put((String) entry[0], entry[1]);
						    return map;
						  }
						  private static Object aggregate(List<Object> matches, String agg) {
						    if (matches == null || matches.isEmpty()) return null;
						    if ("SUM".equalsIgnoreCase(agg)) {
						      BigDecimal sum = BigDecimal.ZERO;
						      for (Object m : matches) { BigDecimal d = toBigDecimal(m); if (d != null) sum = sum.add(d); }
						      return sum;
						    }
						    if ("COUNT".equalsIgnoreCase(agg)) return BigDecimal.valueOf(matches.size());
						    if ("MIN".equalsIgnoreCase(agg)) {
						      BigDecimal min = null;
						      for (Object m : matches) {
						        BigDecimal d = toBigDecimal(m);
						        if (d != null && (min == null || d.compareTo(min) < 0)) min = d;
						      }
						      return min;
						    }
						    if ("MAX".equalsIgnoreCase(agg)) {
						      BigDecimal max = null;
						      for (Object m : matches) {
						        BigDecimal d = toBigDecimal(m);
						        if (d != null && (max == null || d.compareTo(max) > 0)) max = d;
						      }
						      return max;
						    }
						    return List.copyOf(matches);
						  }
						  private static boolean quantify(boolean isEvery, Object listObj, java.util.function.Function<Object, Object> predicate) {
						    if (!(listObj instanceof List<?> list)) return isEvery;
						    for (Object item : list) {
						      Boolean match = Boolean.TRUE.equals(predicate.apply(item));
						      if (!isEvery && match) return true;
						      if (isEvery && !match) return false;
						    }
						    return isEvery;
						  }
						  private static Object filter(Object listObj, java.util.function.Function<Object, Object> predicate) {
						    if (listObj == null) return null;
						    // FEEL spec: non-list scalars act as single-element lists for indexing
						    List<?> list = listObj instanceof List<?> l ? l : java.util.List.of(listObj);
						    List<Object> result = new ArrayList<>();
						    for (int i = 0; i < list.size(); i++) {
						      Object item = list.get(i);
						      Object res = predicate.apply(item);
						      if (res instanceof Number n) {
						        int pos = n.intValue();
						        int idx = pos > 0 ? pos - 1 : list.size() + pos;
						        return (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
						      }
						      if (Boolean.TRUE.equals(res)) result.add(item);
						    }
						    return result;
						  }
						  private static List<Object> asList(Object... items) {
						    if (items == null) {
						      List<Object> list = new ArrayList<>(1);
						      list.add(null);
						      return list;
						    }
						    return Arrays.asList(items);
						  }
						  private static Map<String, Object> asMap(Object... pairs) {
						    Map<String, Object> map = new LinkedHashMap<>();
						    if (pairs != null) {
						      for (int i = 0; i + 1 < pairs.length; i += 2) {
						        map.put(String.valueOf(pairs[i]), pairs[i + 1]);
						      }
						    }
						    return map;
						  }
						  private static Object builtinNamed(String name, Map<String, Object> named) {
						    List<Object> args = io.finmsg.dmn.runtime.DmnRuntime.bindNamedBuiltinArguments(name, named);
						    if (args == null) return null;
						    return builtin(name, args);
						  }
						  private static List<Object> rangeList(Object startObj, Object endObj) {
						    if (startObj instanceof io.finmsg.dmn.runtime.RuntimeRangeValue || endObj instanceof io.finmsg.dmn.runtime.RuntimeRangeValue) return null;
						    if (startObj instanceof java.time.LocalDate ldStart && endObj instanceof java.time.LocalDate ldEnd) {
						      List<Object> result = new ArrayList<>();
						      if (ldStart.isBefore(ldEnd) || ldStart.isEqual(ldEnd)) {
						        for (java.time.LocalDate cur = ldStart; !cur.isAfter(ldEnd); cur = cur.plusDays(1)) {
						          result.add(cur);
						        }
						      } else {
						        for (java.time.LocalDate cur = ldStart; !cur.isBefore(ldEnd); cur = cur.minusDays(1)) {
						          result.add(cur);
						        }
						      }
						      return result;
						    }
						    BigDecimal start = toBigDecimal(startObj);
						    BigDecimal end = toBigDecimal(endObj);
						    if (start == null || end == null) return null;
						    try {
						      int first = start.intValueExact();
						      int last = end.intValueExact();
						      List<Object> result = new ArrayList<>();
						      int step = first <= last ? 1 : -1;
						      for (int value = first;; value += step) {
						        result.add(BigDecimal.valueOf(value));
						        if (value == last) break;
						      }
						      return result;
						    } catch (Exception e) {
						      return null;
						    }
						  }
						  private static Object forLoop(Object listObj, java.util.function.BiFunction<Object, Object, Object> mapper) {
						    if (listObj == null) return null;
						    if (listObj instanceof io.finmsg.dmn.runtime.RuntimeRangeValue) return null;
						    if (!(listObj instanceof List<?> list)) return null;
						    List<Object> result = new ArrayList<>();
						    for (Object item : list) {
						      Object res = mapper.apply(item, Collections.unmodifiableList(new ArrayList<>(result)));
						      result.add(res);
						    }
						    return result;
						  }
						  @SuppressWarnings("unchecked")
						  private static Object invokeCallable(Object target, List<Object> args, Map<String, Object> named) {
						    if (target == null) return null;
						    Object res = null;
						    if (target instanceof FeelCallable fc) {
						      res = fc.call(args, named);
						    } else if (target instanceof java.util.function.BiFunction<?, ?, ?> bf && args != null && args.size() >= 2) {
						      res = ((java.util.function.BiFunction<Object, Object, Object>) bf).apply(args.get(0), args.get(1));
						    } else if (target instanceof java.util.function.Function<?, ?> fn && args != null && !args.isEmpty()) {
						      res = ((java.util.function.Function<Object, Object>) fn).apply(args.get(0));
						    } else if (io.finmsg.dmn.runtime.DmnRuntime.isExternalJavaDescriptor(target)) {
						      return io.finmsg.dmn.runtime.DmnRuntime.invokeExternalJava(target, args != null ? args : Collections.emptyList());
						    }
						    if (io.finmsg.dmn.runtime.DmnRuntime.isExternalJavaDescriptor(res)) {
						      return io.finmsg.dmn.runtime.DmnRuntime.invokeExternalJava(res, args != null ? args : Collections.emptyList());
						    }
						    return res;
						  }
						  private static Object invokeCallable(Object target, List<Object> args) {
						    return invokeCallable(target, args, Collections.emptyMap());
						  }
						  @FunctionalInterface
						  interface FeelCallable {
						    Object call(List<Object> args, Map<String, Object> named);
						    default Object call(List<Object> args) { return call(args, Collections.emptyMap()); }
						    default List<String> parameterNames() { return Collections.emptyList(); }
						  }
						  @SafeVarargs
						  private static Map<String, Object> mapOf(Map.Entry<String, Object>... entries) {
						    Map<String, Object> map = new LinkedHashMap<>();
						    for (Map.Entry<String, Object> e : entries) {
						      if (e != null) map.put(e.getKey(), e.getValue());
						    }
						    return map;
						  }
						  private static Map.Entry<String, Object> entry(String key, Object value) {
						    return new java.util.AbstractMap.SimpleImmutableEntry<>(key, value);
						  }
						  @SuppressWarnings("unchecked")
						  private static Object builtinCallable(String name) {
						    return (FeelCallable) (List<Object> args, Map<String, Object> named) -> {
						      if (args != null && args.size() == 1 && args.get(0) instanceof List<?> list
						          && ("sum".equals(name) || "min".equals(name) || "max".equals(name) || "count".equals(name) || "mean".equals(name) || "all".equals(name) || "any".equals(name))) {
						        return builtin(name, (List<Object>) list);
						      }
						      return builtin(name, args != null ? args : Collections.emptyList());
						    };
						  }
						  @SuppressWarnings("unchecked")
						  private static void flattenInto(Object val, List<Object> result) {
						    if (val instanceof List<?> list) {
						      for (Object item : list) flattenInto(item, result);
						    } else if (val != null) {
						      result.add(val);
						    }
						  }
						  private static java.time.LocalDate toLocalDate(Object obj) {
						    if (obj == null) return null;
						    if (obj instanceof java.time.LocalDate ld) return ld;
						    if (obj instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate();
						    if (obj instanceof java.time.OffsetDateTime odt) return odt.toLocalDate();
						    if (obj instanceof java.time.ZonedDateTime zdt) return zdt.toLocalDate();
						    if (obj instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime nzdt) return nzdt.value().toLocalDate();
						    try { return java.time.LocalDate.parse(String.valueOf(obj)); } catch (Exception e) { return null; }
						  }
						  private static Object builtin(String name, List<Object> args) {
						    if ("substring before".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelSubstringBefore(args);
						    if ("substring after".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelSubstringAfter(args);
						    if ("string join".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelStringJoin(args);
						    if ("abs".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelAbs(args);
						    if ("sqrt".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelSqrt(args);
						    if ("exp".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelExp(args);
						    if ("log".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelLog(args);
						    if ("modulo".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelModulo(args);
						    if ("even".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelEven(args);
						    if ("odd".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelOdd(args);
						    if ("mode".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelMode(args);
						    if ("all".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelAll(args);
						    if ("any".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelAny(args);
						    if ("number".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelNumber(args);
						    if ("is".equals(name)) return args.size() == 2 ? io.finmsg.dmn.runtime.DmnRuntime.isValues(args.get(0), args.get(1)) : null;
						    if ("date".equals(name) && !args.isEmpty()) {
						      if (args.size() == 1) {
						        Object a = args.get(0);
						        if (a == null) return null;
						        if (a instanceof java.time.LocalDate d) return d;
						        if (a instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate();
						        if (a instanceof java.time.OffsetDateTime odt) return odt.toLocalDate();
						        if (a instanceof java.time.ZonedDateTime zdt) return zdt.toLocalDate();
						        if (a instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime nzdt) return nzdt.value().toLocalDate();
						        if (a instanceof String s) return io.finmsg.dmn.runtime.DmnRuntime.parseFeelDate(s);
						      }
						      if (args.size() == 3) {
						        BigDecimal y = toBigDecimal(args.get(0)), m = toBigDecimal(args.get(1)), d = toBigDecimal(args.get(2));
						        if (y == null || m == null || d == null) return null;
						        try { return java.time.LocalDate.of(y.intValue(), m.intValue(), d.intValue()); } catch (Exception e) { return null; }
						      }
						    }
						    if ("time".equals(name) && !args.isEmpty()) {
						      if (args.size() == 1) {
						        Object a = args.get(0);
						        if (a == null) return null;
						        if (a instanceof java.time.LocalTime lt) return lt;
						        if (a instanceof java.time.OffsetTime ot) return ot;
						        if (a instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime nzt) return nzt;
						        if (a instanceof java.time.LocalDate) return java.time.OffsetTime.of(java.time.LocalTime.MIDNIGHT, java.time.ZoneOffset.UTC);
						        if (a instanceof java.time.LocalDateTime ldt) return ldt.toLocalTime();
						        if (a instanceof java.time.OffsetDateTime odt) return odt.toOffsetTime();
						        if (a instanceof java.time.ZonedDateTime zdt) return zdt.toOffsetDateTime().toOffsetTime();
						        if (a instanceof io.finmsg.dmn.runtime.DmnRuntime.NamedZoneDateTime nzdt) return new io.finmsg.dmn.runtime.DmnRuntime.NamedZoneTime(nzdt.value().toLocalTime(), nzdt.zone());
						        if (a instanceof String s) return parseTime(s);
						      }
						      if (args.size() >= 3) {
						        BigDecimal h = toBigDecimal(args.get(0)), m = toBigDecimal(args.get(1)), s = toBigDecimal(args.get(2));
						        if (h == null || m == null || s == null) return null;
						        int hi = h.intValue(), mi = m.intValue(), si = s.intValue();
						        int nano = s.subtract(BigDecimal.valueOf(si)).movePointRight(9).intValue();
						        try {
						          java.time.LocalTime lt = java.time.LocalTime.of(hi, mi, si, nano);
						          if (args.size() >= 4 && args.get(3) != null) {
						            Object offset = args.get(3);
						            if (offset instanceof java.time.Duration dur) return java.time.OffsetTime.of(lt, java.time.ZoneOffset.ofTotalSeconds((int) dur.getSeconds()));
						            if (offset instanceof String zoneStr) {
						              try { return java.time.OffsetTime.of(lt, java.time.ZoneOffset.of(zoneStr)); } catch (Exception e) {}
						            }
						          }
						          return lt;
						        } catch (Exception e) {
						          return null;
						        }
						      }
						    }
						    if ("duration".equals(name) && !args.isEmpty()) {
						      Object a = args.get(0);
						      if (a == null) return null;
						      return io.finmsg.dmn.runtime.DmnRuntime.parseDuration(String.valueOf(a));
						    }
						    if (("day and time duration".equals(name) || "days and time duration".equals(name)) && !args.isEmpty()) {
						      if (args.size() == 1) {
						        Object a = args.get(0);
						        if (a == null) return null;
						        try { return java.time.Duration.parse(String.valueOf(a)); } catch (Exception e) { return null; }
						      }
						      if (args.size() == 2 && args.get(0) != null && args.get(1) != null) {
						        try {
						          java.time.temporal.Temporal t1 = (java.time.temporal.Temporal) args.get(0);
						          java.time.temporal.Temporal t2 = (java.time.temporal.Temporal) args.get(1);
						          return java.time.Duration.between(t2, t1);
						        } catch (Exception e) { return null; }
						      }
						    }
						    if ("years and months duration".equals(name) && !args.isEmpty()) {
						      if (args.size() == 1) {
						        Object a = args.get(0);
						        if (a == null) return null;
						        try { return java.time.Period.parse(String.valueOf(a)).normalized(); } catch (Exception e) { return null; }
						      }
						      if (args.size() == 2 && args.get(0) != null && args.get(1) != null) {
						        java.time.LocalDate d1 = toLocalDate(args.get(0));
						        java.time.LocalDate d2 = toLocalDate(args.get(1));
						        if (d1 != null && d2 != null) {
						          java.time.Period p = java.time.Period.between(d1, d2);
						          return java.time.Period.of(p.getYears(), p.getMonths(), 0);
						        }
						      }
						    }
						    if (("date and time".equals(name) || "dateTime".equals(name)) && !args.isEmpty()) {
						      return io.finmsg.dmn.runtime.DmnRuntime.feelDateAndTime(args);
						    }
						    if ("contains".equals(name) && args.size() == 2) {
						      return args.get(0) == null || args.get(1) == null ? null : String.valueOf(args.get(0)).contains(String.valueOf(args.get(1)));
						    }
						    if ("string length".equals(name) && args.size() == 1) {
						      String s = args.get(0) == null ? null : String.valueOf(args.get(0));
						      return s == null ? null : BigDecimal.valueOf(s.codePointCount(0, s.length()));
						    }
						    if ("sort".equals(name) && !args.isEmpty() && args.get(0) instanceof List<?> list) {
						      List<Object> copy = new ArrayList<>((List<Object>) list);
						      if (args.size() > 1 && args.get(1) != null) {
						        Object fnObj = args.get(1);
						        copy.sort((a, b) -> {
						          Object res = null;
						          if (fnObj instanceof java.util.function.BiFunction<?, ?, ?> fn) {
						            res = ((java.util.function.BiFunction<Object, Object, Object>) fn).apply(a, b);
						          } else {
						            try {
						              java.lang.reflect.Method m = fnObj.getClass().getMethod("call", List.class, Map.class);
						              m.setAccessible(true);
						              res = m.invoke(fnObj, List.of(a, b), Map.of());
						            } catch (Exception e) {
						              try {
						                java.lang.reflect.Method m = fnObj.getClass().getMethod("apply", Object.class, Object.class);
						                m.setAccessible(true);
						                res = m.invoke(fnObj, a, b);
						              } catch (Exception ignored) {}
						            }
						          }
						          return Boolean.TRUE.equals(res) ? -1 : (Boolean.FALSE.equals(res) ? 1 : 0);
						        });
						      } else {
						        copy.sort((a, b) -> {
						          Integer c = compare(a, b);
						          return c != null ? c : 0;
						        });
						      }
						      return List.copyOf(copy);
						    }
						    if ("sublist".equals(name) && args.size() >= 2 && args.get(0) instanceof List<?> list) {
						      int start = toBigDecimal(args.get(1)).intValue();
						      int idx = start > 0 ? start - 1 : list.size() + start;
						      if (idx < 0 || idx >= list.size()) return java.util.Collections.emptyList();
						      if (args.size() > 2 && args.get(2) != null) {
						        int len = toBigDecimal(args.get(2)).intValue();
						        int end = Math.min(list.size(), idx + len);
						        return list.subList(idx, end);
						      }
						      return list.subList(idx, list.size());
						    }
						    if ("distinct values".equals(name) && !args.isEmpty() && args.get(0) instanceof List<?> list) {
						      List<Object> result = new ArrayList<>();
						      for (Object item : list) {
						        if (result.stream().noneMatch(existing -> Boolean.TRUE.equals(equal(existing, item)))) result.add(item);
						      }
						      return List.copyOf(result);
						    }
						    if ("list replace".equals(name) && args.size() >= 3 && args.get(0) != null) {
						      List<Object> copy = new ArrayList<>(args.get(0) instanceof List<?> list ? (List<Object>) list : Collections.singletonList(args.get(0)));
						      Object second = args.get(1);
						      Object newItem = args.get(2);
						      if (second instanceof Number n) {
						        int pos = n.intValue();
						        if (pos == 0 || pos < -copy.size() || pos > copy.size()) return null;
						        int idx = pos > 0 ? pos - 1 : copy.size() + pos;
						        if (idx >= 0 && idx < copy.size()) copy.set(idx, newItem);
						      } else if (second instanceof FeelCallable fc) {
						        if (fc.parameterNames().size() != 2) return null;
						        for (int i = 0; i < copy.size(); i++) {
						          Object res = fc.call(Arrays.asList(copy.get(i), newItem), Collections.emptyMap());
						          if (!(res instanceof Boolean)) return null;
						          if (Boolean.TRUE.equals(res)) copy.set(i, newItem);
						        }
						      } else if (second instanceof java.util.function.BiFunction<?, ?, ?> bf) {
						        for (int i = 0; i < copy.size(); i++) {
						          Object res = ((java.util.function.BiFunction<Object, Object, Object>) bf).apply(copy.get(i), newItem);
						          if (!(res instanceof Boolean)) return null;
						          if (Boolean.TRUE.equals(res)) copy.set(i, newItem);
						        }
						      } else {
						        return null;
						      }
						      return Collections.unmodifiableList(copy);
						    }
						    if ("during".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalDuring(args.get(0), args.get(1));
						    if ("before".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalBefore(args.get(0), args.get(1));
						    if ("after".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalAfter(args.get(0), args.get(1));
						    if ("meets".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalMeets(args.get(0), args.get(1));
						    if ("met by".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalMetBy(args.get(0), args.get(1));
						    if ("overlaps".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalOverlaps(args.get(0), args.get(1));
						    if ("overlaps before".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalOverlapsBefore(args.get(0), args.get(1));
						    if ("overlaps after".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalOverlapsAfter(args.get(0), args.get(1));
						    if ("finishes".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalFinishes(args.get(0), args.get(1));
						    if ("finished by".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalFinishedBy(args.get(0), args.get(1));
						    if ("includes".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalIncludes(args.get(0), args.get(1));
						    if ("starts".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalStarts(args.get(0), args.get(1));
						    if ("started by".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalStartedBy(args.get(0), args.get(1));
						    if ("coincides".equals(name) && args.size() >= 2) return io.finmsg.dmn.runtime.DmnRuntime.intervalCoincides(args.get(0), args.get(1));
						    if ("flatten".equals(name) && !args.isEmpty()) {
						     List<Object> result = new ArrayList<>();
						     flattenInto(args.get(0), result);
						     return result;
						   }
						   if ("concatenate".equals(name)) {
						     List<Object> result = new ArrayList<>();
						     for (Object arg : args) {
						       if (arg instanceof List<?> l) result.addAll((List<Object>) l);
						       else if (arg != null) result.add(arg);
						     }
						     return result;
						   }
						   if ("list contains".equals(name) && args.size() == 2) {
						     Object listArg = args.get(0);
						     Object item = args.get(1);
						     if (!(listArg instanceof List<?> list)) return false;
						     for (Object el : list) { if (Boolean.TRUE.equals(equal(el, item))) return true; }
						     return false;
						   }
						   if ("not".equals(name) && args.size() == 1) {
						     return not(args.get(0));
						   }
						   if ("substring".equals(name) && args.size() >= 2) {
						     String s = args.get(0) == null ? null : String.valueOf(args.get(0));
						     if (s == null || args.get(1) == null) return null;
						     int start = ((Number) args.get(1)).intValue();
						     Integer len = args.size() >= 3 && args.get(2) != null ? ((Number) args.get(2)).intValue() : null;
						     return io.finmsg.dmn.runtime.DmnRuntime.feelSubstring(s, start, len);
						   }
						   if ("upper case".equals(name) && args.size() == 1) {
						     return args.get(0) == null ? null : stringValue(args.get(0)).toUpperCase();
						   }
						   if ("lower case".equals(name) && args.size() == 1) {
						     return args.get(0) == null ? null : stringValue(args.get(0)).toLowerCase();
						   }
						   if ("string".equals(name) && args.size() == 1) {
						     return args.get(0) == null ? null : stringValue(args.get(0));
						   }
						   if ("number".equals(name) && !args.isEmpty()) {
						     return toBigDecimal(args.get(0));
						   }
						   if (("floor".equals(name) || "ceiling".equals(name) || "round up".equals(name) || "round down".equals(name) || "round half up".equals(name) || "round half down".equals(name) || "round half even".equals(name) || "decimal".equals(name)) && !args.isEmpty()) {
						     if (args.size() > 2 || !(args.get(0) instanceof Number)) return null;
						     BigDecimal d = toBigDecimal(args.get(0));
						     if (d == null) return null;
						     int scale = 0;
						     if (args.size() > 1) {
						       if (!(args.get(1) instanceof Number)) return null;
						       BigDecimal scaleValue = toBigDecimal(args.get(1));
						       if (scaleValue == null) return null;
						       scale = scaleValue.intValue();
						     }
						     if (scale < -6111 || scale > 6176) return null;
						     if ("floor".equals(name)) return d.setScale(scale, java.math.RoundingMode.FLOOR);
						     if ("ceiling".equals(name)) return d.setScale(scale, java.math.RoundingMode.CEILING);
						     if ("round up".equals(name)) return d.setScale(scale, java.math.RoundingMode.UP);
						     if ("round down".equals(name)) return d.setScale(scale, java.math.RoundingMode.DOWN);
						     if ("round half up".equals(name)) return d.setScale(scale, java.math.RoundingMode.HALF_UP);
						     if ("round half down".equals(name)) return d.setScale(scale, java.math.RoundingMode.HALF_DOWN);
						     if ("round half even".equals(name) || "decimal".equals(name)) return d.setScale(scale, java.math.RoundingMode.HALF_EVEN);
						   }
						   if ("count".equals(name) && !args.isEmpty()) {
						     Object a = args.get(0);
						     return a instanceof List<?> l ? BigDecimal.valueOf(l.size()) : (a == null ? BigDecimal.ZERO : BigDecimal.ONE);
						   }
						   if ("sum".equals(name) && !args.isEmpty()) {
						     List<?> items = args.get(0) instanceof List<?> l ? l : args;
						     BigDecimal sum = BigDecimal.ZERO;
						     for (Object it : items) { BigDecimal d = toBigDecimal(it); if (d != null) sum = sum.add(d); }
						     return sum;
						   }
						   if ("min".equals(name) && !args.isEmpty()) {
						     List<?> items = args.get(0) instanceof List<?> l ? l : args;
						     BigDecimal min = null;
						     for (Object it : items) { BigDecimal d = toBigDecimal(it); if (d != null && (min == null || d.compareTo(min) < 0)) min = d; }
						     return min;
						   }
						   if ("max".equals(name) && !args.isEmpty()) {
						     List<?> items = args.get(0) instanceof List<?> l ? l : args;
						     BigDecimal max = null;
						     for (Object it : items) { BigDecimal d = toBigDecimal(it); if (d != null && (max == null || d.compareTo(max) > 0)) max = d; }
						     return max;
						   }
						   if ("index of".equals(name) && args.size() == 2) {
						     if (!(args.get(0) instanceof List<?> list)) return Collections.emptyList();
						     Object match = args.get(1);
						     List<Object> indices = new ArrayList<>();
						     for (int i = 0; i < list.size(); i++) { if (Boolean.TRUE.equals(equal(list.get(i), match))) indices.add(BigDecimal.valueOf(i + 1)); }
						     return indices;
						   }
						   if ("reverse".equals(name) && args.size() == 1) {
						     if (!(args.get(0) instanceof List<?> l)) return null;
						     List<Object> copy = new ArrayList<>(l);
						     Collections.reverse(copy);
						     return List.copyOf(copy);
						   }
						   if ("append".equals(name) && args.size() >= 2) {
						     List<Object> result = new ArrayList<>();
						     Object first = args.get(0);
						     if (first instanceof List<?> l) result.addAll((List<Object>) l); else if (first != null) result.add(first);
						     for (int i = 1; i < args.size(); i++) result.add(args.get(i));
						     return result;
						   }
						   if ("starts with".equals(name) && args.size() == 2) {
						     return args.get(0) != null && args.get(1) != null && String.valueOf(args.get(0)).startsWith(String.valueOf(args.get(1)));
						   }
						   if ("ends with".equals(name) && args.size() == 2) {
						     return args.get(0) != null && args.get(1) != null && String.valueOf(args.get(0)).endsWith(String.valueOf(args.get(1)));
						   }
						   if ("matches".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelMatches(args);
						   if ("replace".equals(name)) return io.finmsg.dmn.runtime.DmnRuntime.feelReplace(args);
						   if ("mean".equals(name) && !args.isEmpty()) {
						     List<?> items = args.get(0) instanceof List<?> l ? l : args;
						     if (items.isEmpty()) return null;
						     BigDecimal sum = BigDecimal.ZERO;
						     for (Object it : items) { BigDecimal d = toBigDecimal(it); if (d != null) sum = sum.add(d); }
						     return sum.divide(BigDecimal.valueOf(items.size()), java.math.MathContext.DECIMAL128);
						   }
						   if ("product".equals(name) && !args.isEmpty()) {
						     List<?> items = args.get(0) instanceof List<?> l ? l : args;
						     if (items.isEmpty()) return null;
						     BigDecimal prod = BigDecimal.ONE;
						     for (Object it : items) {
						       if (it == null) return null;
						       BigDecimal d = toBigDecimal(it);
						       if (d == null) return null;
						       prod = prod.multiply(d);
						     }
						     return prod;
						   }
						   if ("median".equals(name) && !args.isEmpty()) {
						     List<?> items = args.get(0) instanceof List<?> l ? l : args;
						     if (items.isEmpty()) return null;
						     List<BigDecimal> nums = new ArrayList<>();
						     for (Object it : items) {
						       if (it == null) return null;
						       BigDecimal d = toBigDecimal(it);
						       if (d == null) return null;
						       nums.add(d);
						     }
						     Collections.sort(nums);
						     int n = nums.size();
						     if (n % 2 != 0) return nums.get(n / 2);
						     return nums.get(n / 2 - 1).add(nums.get(n / 2)).divide(BigDecimal.valueOf(2), java.math.MathContext.DECIMAL128);
						   }
						   if ("stddev".equals(name) && !args.isEmpty()) {
						     List<?> items = args.get(0) instanceof List<?> l ? l : args;
						     if (items.size() <= 1) return null;
						     BigDecimal sum = BigDecimal.ZERO;
						     List<BigDecimal> nums = new ArrayList<>();
						     for (Object it : items) {
						       if (it == null) return null;
						       BigDecimal d = toBigDecimal(it);
						       if (d == null) return null;
						       sum = sum.add(d);
						       nums.add(d);
						     }
						     BigDecimal mean = sum.divide(BigDecimal.valueOf(nums.size()), java.math.MathContext.DECIMAL128);
						     BigDecimal sqSum = BigDecimal.ZERO;
						     for (BigDecimal d : nums) {
						       BigDecimal diff = d.subtract(mean);
						       sqSum = sqSum.add(diff.multiply(diff));
						     }
						     BigDecimal var = sqSum.divide(BigDecimal.valueOf(nums.size() - 1), java.math.MathContext.DECIMAL128);
						     return BigDecimal.valueOf(Math.sqrt(var.doubleValue()));
						   }
						   if ("insert before".equals(name) && args.size() == 3) {
						     if (!(args.get(0) instanceof List<?> list)) return null;
						     int pos = ((Number) args.get(1)).intValue();
						     Object newItem = args.get(2);
						     int idx = pos > 0 ? pos - 1 : list.size() + pos;
						     if (idx < 0 || idx > list.size()) return null;
						     List<Object> copy = new ArrayList<>(list);
						     copy.add(idx, newItem);
						     return copy;
						   }
						   if ("remove".equals(name) && args.size() == 2) {
						     if (!(args.get(0) instanceof List<?> list)) return null;
						     int pos = ((Number) args.get(1)).intValue();
						     int idx = pos > 0 ? pos - 1 : list.size() + pos;
						     if (idx < 0 || idx >= list.size()) return null;
						     List<Object> copy = new ArrayList<>(list);
						     copy.remove(idx);
						     return copy;
						   }
						   if ("union".equals(name) && !args.isEmpty()) {
						     List<Object> result = new ArrayList<>();
						     for (Object arg : args) {
						       if (arg instanceof List<?> l) {
						         for (Object item : l) {
						           if (result.stream().noneMatch(existing -> Boolean.TRUE.equals(equal(existing, item)))) result.add(item);
						         }
						       } else {
						         if (result.stream().noneMatch(existing -> Boolean.TRUE.equals(equal(existing, arg)))) result.add(arg);
						       }
						     }
						     return result;
						   }
						   if ("day and time duration".equals(name) && args.size() == 1) {
						     String s = String.valueOf(args.get(0));
						     try { return java.time.Duration.parse(s); } catch (Exception e) { return null; }
						   }
						   if ("get entries".equals(name)) {
						     if (args.size() != 1 || !(args.get(0) instanceof Map<?, ?> map)) return null;
						     List<Map<String, Object>> res = new ArrayList<>();
						     for (Map.Entry<?, ?> e : map.entrySet()) {
						       res.add(createContext(new Object[][]{{"key", e.getKey()}, {"value", e.getValue()}}));
						     }
						     return res;
						   }
						   if ("get value".equals(name)) {
						     if (args.size() != 2 || !(args.get(0) instanceof Map<?, ?> map)) return null;
						     return map.get(args.get(1));
						   }
						   if ("time".equals(name) && args.size() > 1) {
						     try {
						       int h = ((Number) args.get(0)).intValue();
						       int m = ((Number) args.get(1)).intValue();
						       int s = ((Number) args.get(2)).intValue();
						       java.time.LocalTime t = java.time.LocalTime.of(h, m, s);
						       if (args.size() == 4) {
						         Object off = args.get(3);
						         if (off instanceof java.time.Duration d) {
						           return java.time.OffsetTime.of(t, java.time.ZoneOffset.ofTotalSeconds((int) d.getSeconds()));
						         }
						       }
						       return t;
						     } catch (Exception e) { return null; }
						   }
						   if (("date and time".equals(name) || "dateTime".equals(name)) && args.size() == 2) {
						     Object d = args.get(0);
						     Object t = args.get(1);
						     if (d instanceof java.time.LocalDate ld) {
						       if (t instanceof java.time.LocalTime lt) return java.time.LocalDateTime.of(ld, lt);
						       if (t instanceof java.time.OffsetTime ot) return java.time.OffsetDateTime.of(ld, ot.toLocalTime(), ot.getOffset()).toZonedDateTime();
						     }
						     return null;
						   }
						   if ("number".equals(name) && args.size() == 3) {
						     String str = String.valueOf(args.get(0));
						     String grp = String.valueOf(args.get(1));
						     String dec = String.valueOf(args.get(2));
						     if (grp != null && !grp.isEmpty()) str = str.replace(grp, "");
						     if (dec != null && !dec.isEmpty()) str = str.replace(dec, ".");
						     return toBigDecimal(str);
						   }
						   if ("split".equals(name) && args.size() >= 2) {
						     if (args.get(0) == null || args.get(1) == null) return null;
						     return Arrays.asList(String.valueOf(args.get(0)).split(String.valueOf(args.get(1)), -1));
						   }
						   if ("range".equals(name) && !args.isEmpty()) {
						     if (args.size() == 1) {
						       String s = String.valueOf(args.get(0)).trim();
						       boolean lowClosed = s.startsWith("[");
						       boolean upClosed = s.endsWith("]");
						       boolean lowOpen = s.startsWith("(") || s.startsWith("]");
						       boolean upOpen = s.endsWith(")") || s.endsWith("[");
						       if (s.length() >= 2 && (lowClosed || lowOpen) && (upClosed || upOpen)) {
						         String inner = s.substring(1, s.length() - 1);
						         String[] parts = inner.split(java.util.regex.Pattern.quote(".."), 2);
						         if (parts.length == 2) {
						           String p0 = parts[0].trim();
						           String p1 = parts[1].trim();
						           if (p0.isEmpty() && lowClosed) return null;
						           if (p1.isEmpty() && upClosed) return null;
						           Object low = p0.isEmpty() ? null : parseLiteral(p0);
						           Object up = p1.isEmpty() ? null : parseLiteral(p1);
						           if (!p0.isEmpty() && low == null) return null;
						           if (!p1.isEmpty() && up == null) return null;
						           if (low == null && up == null) return null;
						           if (low != null && up != null) {
						             if (low.getClass() != up.getClass() && !(low instanceof Number && up instanceof Number)) return null;
						             Integer c = compare(low, up);
						             if (c == null || c > 0) return null;
						           }
						           return new io.finmsg.dmn.runtime.RuntimeRangeValue(low, up,
						               lowClosed ? io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED : io.finmsg.dmn.ir.RuntimeRangeBoundary.OPEN,
						               upClosed ? io.finmsg.dmn.ir.RuntimeRangeBoundary.CLOSED : io.finmsg.dmn.ir.RuntimeRangeBoundary.OPEN);
						         }
						       }
						       return null;
						     }
						     return null;
						   }
						   if ("day of year".equals(name) && args.size() == 1) {
						     java.time.LocalDate d = toLocalDate(args.get(0));
						     return d == null ? null : BigDecimal.valueOf(d.getDayOfYear());
						   }
						   if ("day of week".equals(name) && args.size() == 1) {
						     java.time.LocalDate d = toLocalDate(args.get(0));
						     return d == null ? null : d.getDayOfWeek().name().substring(0, 1) + d.getDayOfWeek().name().substring(1).toLowerCase();
						   }
						   if ("week of year".equals(name) && args.size() == 1) {
						     java.time.LocalDate d = toLocalDate(args.get(0));
						     return d == null ? null : BigDecimal.valueOf(d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR));
						   }
						   if ("month of year".equals(name) && args.size() == 1) {
						     java.time.LocalDate d = toLocalDate(args.get(0));
						     return d == null ? null : d.getMonth().name().substring(0, 1) + d.getMonth().name().substring(1).toLowerCase();
						   }
						   if ("context".equals(name)) {
						     if (args.size() != 1 || args.get(0) == null) return null;
						     List<?> list = args.get(0) instanceof List<?> l ? l : List.of(args.get(0));
						     Map<String, Object> map = new LinkedHashMap<>();
						     for (Object item : list) {
						       if (item == null) return null;
						       Object keyObj = null;
						       Object valObj = null;
						       if (item instanceof Map<?, ?> m) {
						         if (!m.containsKey("key") || !m.containsKey("value")) return null;
						         keyObj = m.get("key");
						         valObj = m.get("value");
						       } else {
						         return null;
						       }
						       if (!(keyObj instanceof String strKey)) return null;
						       if (map.containsKey(strKey)) return null;
						       map.put(strKey, valObj);
						     }
						     return map;
						   }
						   if ("context put".equals(name) && args.size() == 3) {
						     return contextPutGen(args.get(0), args.get(1), args.get(2));
						   }
						   if ("context merge".equals(name)) {
						     if (args.size() != 1 || args.get(0) == null) return null;
						     List<?> list = args.get(0) instanceof List<?> l ? l : (args.get(0) instanceof Map<?, ?> ? List.of(args.get(0)) : null);
						     if (list == null) return null;
						     Map<String, Object> merged = new LinkedHashMap<>();
						     for (Object item : list) {
						       if (item instanceof Map<?, ?> m) {
						         for (Map.Entry<?, ?> e : m.entrySet()) {
						           if (!(e.getKey() instanceof String)) return null;
						           merged.put((String) e.getKey(), e.getValue());
						         }
						       } else {
						         return null;
						       }
						     }
						     return merged;
						   }
						   if ("now".equals(name) && args.isEmpty()) return java.time.ZonedDateTime.now();
						   if ("today".equals(name) && args.isEmpty()) return java.time.LocalDate.now();
						   return null;
						  }
						  private static Object contextPutGen(Object ctx, Object keyOrKeys, Object val) {
						    if (!(ctx instanceof Map<?, ?>)) return null;
						    if (keyOrKeys == null) return null;
						    Map<String, Object> copy = new LinkedHashMap<>();
						    ((Map<?, ?>) ctx).forEach((k, v) -> copy.put(String.valueOf(k), v));
						    if (keyOrKeys instanceof List<?> keys) {
						      if (keys.isEmpty()) return null;
						      for (Object k : keys) {
						        if (!(k instanceof String)) return null;
						      }
						      if (keys.size() == 1) {
						        copy.put((String) keys.get(0), val);
						        return copy;
						      }
						      String k0 = (String) keys.get(0);
						      Object sub = copy.get(k0);
						      if (sub != null && !(sub instanceof Map<?, ?>)) return null;
						      Object updatedSub = contextPutGen(sub != null ? sub : new LinkedHashMap<>(), keys.subList(1, keys.size()), val);
						      if (updatedSub == null) return null;
						      copy.put(k0, updatedSub);
						      return copy;
						    }
						    if (!(keyOrKeys instanceof String strKey)) return null;
						    copy.put(strKey, val);
						    return copy;
						  }
						  private static Object parseLiteral(String text) {
						    return io.finmsg.dmn.runtime.DmnRuntime.parseLiteralValue(text);
						  }
						""");
	}
}
