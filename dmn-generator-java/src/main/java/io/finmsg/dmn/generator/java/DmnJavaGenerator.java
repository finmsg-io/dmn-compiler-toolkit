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

		// Build BKM slot map so emitter can resolve BKM invocations to direct method
		// calls
		Map<Integer, RuntimeBkm> bkmBySlot = new HashMap<>();
		model.businessKnowledgeModels().forEach(bkm -> bkmBySlot.put(bkm.resultSlot(), bkm));

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
			sb.append(", Object ").append(JavaExpressionEmitter.localSlotName(param.localSlot()));
		}
		sb.append(") {\n");
		sb.append("    return ").append(JavaExpressionEmitter.emitWithBkms(fn.body().get(), bkmBySlot)).append(";\n");
		sb.append("  }\n\n");
	}

	private static void generateDecisionMethod(StringBuilder sb, RuntimeDecision decision,
			Map<Integer, RuntimeBkm> bkmBySlot) {
		sb.append("  private Object evaluate_").append(decision.resultSlot()).append("(Object[] slots) {\n");

		if (decision.decisionTable().isPresent()) {
			RuntimeDecisionTable table = decision.decisionTable().get();
			generateDecisionTableBody(sb, table, bkmBySlot);
		} else if (decision.expression().isPresent()) {
			RuntimeExpression expr = decision.expression().get();
			sb.append("    return ").append(JavaExpressionEmitter.emitWithBkms(expr, bkmBySlot)).append(";\n");
		} else {
			sb.append("    return null;\n");
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

		if (isMultiMatch) {
			if (table.aggregation().isPresent()) {
				sb.append("    return aggregate(matches, \"").append(table.aggregation().get().name()).append("\");\n");
			} else if (table.hitPolicy() == RuntimeHitPolicy.OUTPUT_ORDER) {
				sb.append("    return sortOutputOrder(matches, ").append(emitAllowedValuesList(table)).append(");\n");
			} else {
				sb.append("    return Collections.unmodifiableList(new ArrayList<>(matches));\n");
			}
		} else {
			sb.append("    return null;\n");
		}
	}

	private static String emitAllowedValuesList(RuntimeDecisionTable table) {
		if (table.outputs().size() == 1 && table.outputs().get(0).allowedValues().isPresent()) {
			RuntimeUnaryTests tests = table.outputs().get(0).allowedValues().get();
			List<String> entries = new ArrayList<>();
			for (RuntimeUnaryTest test : tests.tests()) {
				if (test instanceof RuntimeComparisonUnaryTest comp) {
					entries.add(JavaExpressionEmitter.emit(comp.endpoint()));
				} else if (test instanceof RuntimeExpressionUnaryTest expr) {
					entries.add(JavaExpressionEmitter.emit(expr.expression()));
				}
			}
			if (!entries.isEmpty()) {
				return "Arrays.asList(" + String.join(", ", entries) + ")";
			}
		}
		return "Collections.emptyList()";
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
			case RuntimeRangeUnaryTest rangeTest -> emitRangeUnaryTest(inputVar, rangeTest);
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

	private static String emitRangeUnaryTest(String inputVar, RuntimeRangeUnaryTest rangeTest) {
		RuntimeRangeExpression range = rangeTest.range();
		StringBuilder sb = new StringBuilder("(");
		sb.append(inputVar).append(" != null");
		if (range.lower().isPresent()) {
			String lowerExpr = JavaExpressionEmitter.emit(range.lower().get());
			String op = range.lowerBoundary() == RuntimeRangeBoundary.CLOSED ? " >= 0" : " > 0";
			sb.append(" && compare(").append(inputVar).append(", ").append(lowerExpr).append(")").append(op);
		}
		if (range.upper().isPresent()) {
			String upperExpr = JavaExpressionEmitter.emit(range.upper().get());
			String op = range.upperBoundary() == RuntimeRangeBoundary.CLOSED ? " <= 0" : " < 0";
			sb.append(" && compare(").append(inputVar).append(", ").append(upperExpr).append(")").append(op);
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
						  private static Object power(Object a, Object b) {
						    BigDecimal da = toBigDecimal(a), db = toBigDecimal(b);
						    return (da == null || db == null) ? null : BigDecimal.valueOf(Math.pow(da.doubleValue(), db.doubleValue()));
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
						    if (a instanceof List<?> la && b instanceof List<?> lb) {
						      if (la.size() != lb.size()) return false;
						      for (int i = 0; i < la.size(); i++) {
						        if (!equal(la.get(i), lb.get(i))) return false;
						      }
						      return true;
						    }
						    return Objects.equals(a, b);
						  }
						  private static Comparable toComparable(Object val) {
						    if (val == null) return null;
						    if (val instanceof Comparable c) {
						      if (val instanceof String s && s.length() == 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
						        try { return java.time.LocalDate.parse(s); } catch (Exception ignored) {}
						      }
						      return c;
						    }
						    return null;
						  }
						  @SuppressWarnings("unchecked")
						  private static int compare(Object a, Object b) {
						    if (a == null || b == null) return 0;
						    if (a instanceof Number && b instanceof Number) return toBigDecimal(a).compareTo(toBigDecimal(b));
						    Comparable ca = toComparable(a);
						    Comparable cb = toComparable(b);
						    if (ca != null && cb != null) {
						      try {
						        return ((Comparable<Object>) ca).compareTo(cb);
						      } catch (Exception e) {
						        try {
						          return -((Comparable<Object>) cb).compareTo(ca);
						        } catch (Exception ignored) {}
						      }
						    }
						    return 0;
						  }
						  private static List<Object> sortOutputOrder(List<Object> matches, List<Object> domain) {
						    if (domain == null || domain.isEmpty() || matches == null) return matches;
						    List<Object> copy = new ArrayList<>(matches);
						    copy.sort(java.util.Comparator.comparingInt(val -> {
						      int idx = domain.indexOf(val);
						      return idx < 0 ? Integer.MAX_VALUE : idx;
						    }));
						    return List.copyOf(copy);
						  }
						  private static Object getPath(Object source, String property) {
						    if (source instanceof Map<?, ?> map) return map.get(property);
						    if (source instanceof List<?> list) {
						      List<Object> res = new ArrayList<>();
						      for (Object item : list) res.add(getPath(item, property));
						      return res;
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
						    if (!(listObj instanceof List<?> list)) return Collections.emptyList();
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
						  @SuppressWarnings("unchecked")
						  private static void flattenInto(Object val, List<Object> result) {
						    if (val instanceof List<?> list) {
						      for (Object item : list) flattenInto(item, result);
						    } else if (val != null) {
						      result.add(val);
						    }
						  }
						  private static Object builtin(String name, List<Object> args) {
						    if ("date".equals(name) && !args.isEmpty()) {
						      Object a = args.get(0);
						      if (a instanceof java.time.LocalDate d) return d;
						      if (a instanceof String s) { try { return java.time.LocalDate.parse(s); } catch (Exception e) { return null; } }
						    }
						    if (("date and time".equals(name) || "dateTime".equals(name)) && !args.isEmpty()) {
						      Object a = args.get(0);
						      if (a instanceof java.time.LocalDateTime dt) return dt;
						      if (a instanceof java.time.ZonedDateTime zdt) return zdt;
						      if (a instanceof String s) {
						        try { return s.contains("+") || s.contains("Z") ? java.time.ZonedDateTime.parse(s) : java.time.LocalDateTime.parse(s); } catch (Exception e) { return null; }
						      }
						    }
						    if ("contains".equals(name) && args.size() == 2) {
						      return args.get(0) != null && args.get(1) != null && String.valueOf(args.get(0)).contains(String.valueOf(args.get(1)));
						    }
						    if ("string length".equals(name) && args.size() == 1) {
						      return args.get(0) == null ? null : BigDecimal.valueOf(String.valueOf(args.get(0)).length());
						    }
						    if ("decimal".equals(name) && args.size() == 2 && args.get(0) instanceof Number n && args.get(1) instanceof Number scale) {
						      return toBigDecimal(n).setScale(scale.intValue(), java.math.RoundingMode.HALF_EVEN);
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
						     for (Object el : list) { if (equal(el, item)) return true; }
						     return false;
						   }
						   if ("not".equals(name) && args.size() == 1) {
						     return not(args.get(0));
						   }
						   if ("substring".equals(name) && args.size() >= 2) {
						     String s = args.get(0) == null ? null : String.valueOf(args.get(0));
						     if (s == null) return null;
						     int start = ((Number) args.get(1)).intValue();
						     int idx = start > 0 ? start - 1 : Math.max(0, s.length() + start);
						     if (idx < 0 || idx >= s.length()) return "";
						     if (args.size() >= 3) {
						       int len = ((Number) args.get(2)).intValue();
						       int end = Math.min(idx + len, s.length());
						       return s.substring(idx, end);
						     }
						     return s.substring(idx);
						   }
						   if ("substring before".equals(name) && args.size() == 2) {
						     String s = args.get(0) == null ? null : String.valueOf(args.get(0));
						     String m = args.get(1) == null ? null : String.valueOf(args.get(1));
						     if (s == null || m == null) return null;
						     int i = s.indexOf(m);
						     return i < 0 ? "" : s.substring(0, i);
						   }
						   if ("substring after".equals(name) && args.size() == 2) {
						     String s = args.get(0) == null ? null : String.valueOf(args.get(0));
						     String m = args.get(1) == null ? null : String.valueOf(args.get(1));
						     if (s == null || m == null) return null;
						     int i = s.indexOf(m);
						     return i < 0 ? "" : s.substring(i + m.length());
						   }
						   if ("upper case".equals(name) && args.size() == 1) {
						     return args.get(0) == null ? null : String.valueOf(args.get(0)).toUpperCase();
						   }
						   if ("lower case".equals(name) && args.size() == 1) {
						     return args.get(0) == null ? null : String.valueOf(args.get(0)).toLowerCase();
						   }
						   if ("string".equals(name) && args.size() == 1) {
						     return args.get(0) == null ? null : String.valueOf(args.get(0));
						   }
						   if ("number".equals(name) && !args.isEmpty()) {
						     return toBigDecimal(args.get(0));
						   }
						   if (("abs".equals(name) || "floor".equals(name) || "ceiling".equals(name) || "round up".equals(name) || "round down".equals(name)) && !args.isEmpty()) {
						     BigDecimal d = toBigDecimal(args.get(0));
						     if (d == null) return null;
						     if ("abs".equals(name)) return d.abs();
						     if ("floor".equals(name)) return d.setScale(0, java.math.RoundingMode.FLOOR);
						     if ("ceiling".equals(name)) return d.setScale(0, java.math.RoundingMode.CEILING);
						     if ("round up".equals(name)) return d.setScale(0, java.math.RoundingMode.UP);
						     return d.setScale(0, java.math.RoundingMode.DOWN);
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
						     for (Object it : items) { BigDecimal d = toBigDecimal(it); if (d != null && (d.compareTo(max) > 0 || max == null)) max = d; }
						     return max;
						   }
						   if ("index of".equals(name) && args.size() == 2) {
						     if (!(args.get(0) instanceof List<?> list)) return Collections.emptyList();
						     Object match = args.get(1);
						     List<Object> indices = new ArrayList<>();
						     for (int i = 0; i < list.size(); i++) { if (equal(list.get(i), match)) indices.add(BigDecimal.valueOf(i + 1)); }
						     return indices;
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
						   if ("matches".equals(name) && args.size() >= 2) {
						     if (args.get(0) == null || args.get(1) == null) return false;
						     return String.valueOf(args.get(0)).matches(String.valueOf(args.get(1)));
						   }
						   if ("replace".equals(name) && args.size() >= 3) {
						     if (args.get(0) == null) return null;
						     return String.valueOf(args.get(0)).replaceAll(String.valueOf(args.get(1)), String.valueOf(args.get(2)));
						   }
						   return null;
						  }
						""");
	}
}
