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
		sb.append("import java.util.*;\n\n");
		sb.append("/** Generated high-performance DMN Decision Engine. */\n");
		sb.append("public final class ").append(options.className()).append(" {\n\n");

		sb.append("  public static final int SLOT_COUNT = ").append(model.valueSlotCount()).append(";\n\n");

		// Evaluation entry point
		sb.append("  public Object[] evaluate(Object[] inputSlots) {\n");
		sb.append("    Object[] slots = new Object[SLOT_COUNT];\n");
		sb.append("    if (inputSlots != null) {\n");
		sb.append("      System.arraycopy(inputSlots, 0, slots, 0, Math.min(inputSlots.length, SLOT_COUNT));\n");
		sb.append("    }\n\n");

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

		// Generate individual decision methods
		for (RuntimeDecision decision : model.decisions()) {
			generateDecisionMethod(sb, decision);
		}

		// Generate runtime helper methods
		generateHelperMethods(sb);

		sb.append("}\n");

		return new DmnJavaGeneratorResult(fqcn, Map.of(fqcn, sb.toString()));
	}

	private static void generateDecisionMethod(StringBuilder sb, RuntimeDecision decision) {
		sb.append("  private Object evaluate_").append(decision.resultSlot()).append("(Object[] slots) {\n");

		if (decision.decisionTable().isPresent()) {
			RuntimeDecisionTable table = decision.decisionTable().get();
			generateDecisionTableBody(sb, table);
		} else if (decision.expression().isPresent()) {
			RuntimeExpression expr = decision.expression().get();
			sb.append("    return ").append(JavaExpressionEmitter.emit(expr)).append(";\n");
		} else {
			sb.append("    return null;\n");
		}

		sb.append("  }\n\n");
	}

	private static void generateDecisionTableBody(StringBuilder sb, RuntimeDecisionTable table) {
		sb.append("    // Evaluate Decision Table Inputs\n");
		for (int i = 0; i < table.inputs().size(); i++) {
			RuntimeDecisionTableInput input = table.inputs().get(i);
			sb.append("    Object in_").append(i).append(" = ").append(JavaExpressionEmitter.emit(input.expression()))
					.append(";\n");
		}

		boolean isMultiMatch = table.hitPolicy() == RuntimeHitPolicy.COLLECT
				|| table.hitPolicy() == RuntimeHitPolicy.RULE_ORDER
				|| table.hitPolicy() == RuntimeHitPolicy.OUTPUT_ORDER;
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
							? JavaExpressionEmitter.emit(rule.outputEntries().get(o))
							: "null";
					ctxSb.append("{\"").append(name).append("\", ").append(valCode).append("}");
				}
				ctxSb.append("})");
				outExpr = ctxSb.toString();
			} else {
				outExpr = JavaExpressionEmitter.emit(rule.outputEntries().get(0));
			}
			if (isMultiMatch) {
				sb.append("      matches.add(").append(outExpr).append(");\n");
			} else {
				sb.append("      return ").append(outExpr).append(";\n");
			}
			sb.append("    }\n");
		}

		if (isMultiMatch) {
			if (table.aggregation().isPresent()) {
				sb.append("    return aggregate(matches, \"").append(table.aggregation().get().name()).append("\");\n");
			} else {
				sb.append("    return List.copyOf(matches);\n");
			}
		} else {
			sb.append("    return null;\n");
		}
	}

	private static String emitUnaryTests(String inputVar, RuntimeUnaryTests tests) {
		if (tests == null || tests.tests().isEmpty()) {
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
		return sb.toString();
	}

	private static String emitUnaryTest(String inputVar, RuntimeUnaryTest test) {
		return switch (test) {
			case RuntimeComparisonUnaryTest comparison -> switch (comparison.operator()) {
				case EQUAL -> "equal(" + inputVar + ", " + JavaExpressionEmitter.emit(comparison.endpoint()) + ")";
				case NOT_EQUAL -> "!equal(" + inputVar + ", " + JavaExpressionEmitter.emit(comparison.endpoint()) + ")";
				case LESS -> "compare(" + inputVar + ", " + JavaExpressionEmitter.emit(comparison.endpoint()) + ") < 0";
				case LESS_EQUAL ->
					"compare(" + inputVar + ", " + JavaExpressionEmitter.emit(comparison.endpoint()) + ") <= 0";
				case GREATER ->
					"compare(" + inputVar + ", " + JavaExpressionEmitter.emit(comparison.endpoint()) + ") > 0";
				case GREATER_EQUAL ->
					"compare(" + inputVar + ", " + JavaExpressionEmitter.emit(comparison.endpoint()) + ") >= 0";
			};
			case RuntimeRangeUnaryTest range -> "true";
			case RuntimeExpressionUnaryTest expr -> {
				String emitted = JavaExpressionEmitter.emit(expr.expression());
				if (emitted.startsWith("equal(") || emitted.startsWith("compare(") || emitted.startsWith("isTrue(")
						|| emitted.startsWith("in(")) {
					yield emitted;
				}
				yield "equal(" + inputVar + ", " + emitted + ")";
			}
		};
	}

	private static void generateHelperMethods(StringBuilder sb) {
		sb.append(
				"""
						  private static boolean isTrue(Object val) {
						    return Boolean.TRUE.equals(val);
						  }
						  private static BigDecimal toBigDecimal(Object val) {
						    if (val instanceof BigDecimal d) return d;
						    if (val instanceof Number n) return new BigDecimal(n.toString());
						    if (val instanceof String s) {
						      try { return new BigDecimal(s); } catch (Exception e) { return null; }
						    }
						    return null;
						  }
						  private static Object add(Object a, Object b) {
						    if (a instanceof String || b instanceof String) return String.valueOf(a) + b;
						    BigDecimal da = toBigDecimal(a), db = toBigDecimal(b);
						    return (da == null || db == null) ? null : da.add(db);
						  }
						  private static Object subtract(Object a, Object b) {
						    BigDecimal da = toBigDecimal(a), db = toBigDecimal(b);
						    return (da == null || db == null) ? null : da.subtract(db);
						  }
						  private static Object multiply(Object a, Object b) {
						    BigDecimal da = toBigDecimal(a), db = toBigDecimal(b);
						    return (da == null || db == null) ? null : da.multiply(db);
						  }
						  private static Object divide(Object a, Object b) {
						    BigDecimal da = toBigDecimal(a), db = toBigDecimal(b);
						    return (da == null || db == null || db.signum() == 0) ? null : da.divide(db, java.math.MathContext.DECIMAL128);
						  }
						  private static Object negate(Object a) {
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
						  private static boolean equal(Object a, Object b) {
						    if (a == null && b == null) return true;
						    if (a == null || b == null) return false;
						    if (a instanceof Number && b instanceof Number) return toBigDecimal(a).compareTo(toBigDecimal(b)) == 0;
						    return Objects.equals(a, b);
						  }
						  @SuppressWarnings("unchecked")
						  private static int compare(Object a, Object b) {
						    if (a == null || b == null) return 0;
						    if (a instanceof Number && b instanceof Number) return toBigDecimal(a).compareTo(toBigDecimal(b));
						    if (a instanceof Comparable c && b instanceof Comparable) return ((Comparable<Object>) c).compareTo(b);
						    return 0;
						  }
						  private static Object getPath(Object source, String property) {
						    if (source instanceof Map<?, ?> map) return map.get(property);
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
						    if (!(listObj instanceof List<?> list)) return null;
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
						  private static Object forLoop(Object listObj, java.util.function.Function<Object, Object> mapper) {
						    if (!(listObj instanceof List<?> list)) return List.of();
						    List<Object> result = new ArrayList<>();
						    for (Object item : list) {
						      Object res = mapper.apply(item);
						      if (res instanceof List<?> inner) {
						        result.addAll(inner);
						      } else {
						        result.add(res);
						      }
						    }
						    return result;
						  }
						  private static Object builtin(String name, List<Object> args) {
						    if ("contains".equals(name) && args.size() == 2) {
						      return args.get(0) != null && args.get(1) != null && String.valueOf(args.get(0)).contains(String.valueOf(args.get(1)));
						    }
						    if ("string length".equals(name) && args.size() == 1) {
						      return args.get(0) == null ? null : BigDecimal.valueOf(String.valueOf(args.get(0)).length());
						    }
						    if ("sort".equals(name) && !args.isEmpty() && args.get(0) instanceof List<?> list) {
						      List<Object> copy = new ArrayList<>((List<Object>) list);
						      if (args.size() > 1 && args.get(1) instanceof java.util.function.BiFunction<?, ?, ?> fn) {
						        copy.sort((a, b) -> {
						          Object res = ((java.util.function.BiFunction<Object, Object, Object>) fn).apply(a, b);
						          return Boolean.TRUE.equals(res) ? -1 : (Boolean.FALSE.equals(res) ? 1 : 0);
						        });
						      } else {
						        copy.sort((a, b) -> compare(a, b));
						      }
						      return List.copyOf(copy);
						    }
						    if ("distinct values".equals(name) && !args.isEmpty() && args.get(0) instanceof List<?> list) {
						      List<Object> result = new ArrayList<>();
						      for (Object item : list) {
						        if (result.stream().noneMatch(existing -> equal(existing, item))) result.add(item);
						      }
						      return List.copyOf(result);
						    }
						    if ("list replace".equals(name) && args.size() >= 3 && args.get(0) instanceof List<?> list) {
						      List<Object> copy = new ArrayList<>((List<Object>) list);
						      Object second = args.get(1);
						      Object newItem = args.get(2);
						      if (second instanceof Number n) {
						        int pos = n.intValue();
						        int idx = pos > 0 ? pos - 1 : copy.size() + pos;
						        if (idx >= 0 && idx < copy.size()) copy.set(idx, newItem);
						      } else if (second instanceof java.util.function.Function<?, ?> fn) {
						        for (int i = 0; i < copy.size(); i++) {
						          if (Boolean.TRUE.equals(((java.util.function.Function<Object, Object>) fn).apply(copy.get(i)))) {
						            copy.set(i, newItem);
						          }
						        }
						      }
						      return List.copyOf(copy);
						    }
						    return null;
						  }
						""");
	}
}
