package io.finmsg.dmn.generator.java;

import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Pure Java code generator from Runtime IR optimized for maximum execution speed.
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
      sb.append("    Object in_").append(i).append(" = ").append(JavaExpressionEmitter.emit(input.expression())).append(";\n");
    }

    boolean isMultiMatch = table.hitPolicy() == RuntimeHitPolicy.COLLECT || table.hitPolicy() == RuntimeHitPolicy.RULE_ORDER || table.hitPolicy() == RuntimeHitPolicy.OUTPUT_ORDER;
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
          if (i > 0) sb.append(" && ");
          RuntimeUnaryTests tests = rule.inputEntries().get(i);
          sb.append(emitUnaryTests("in_" + i, tests));
        }
      }
      sb.append(") {\n");
      String outExpr = !rule.outputEntries().isEmpty() ? JavaExpressionEmitter.emit(rule.outputEntries().get(0)) : "null";
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
      if (i > 0) sb.append(" || ");
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
        case LESS_EQUAL -> "compare(" + inputVar + ", " + JavaExpressionEmitter.emit(comparison.endpoint()) + ") <= 0";
        case GREATER -> "compare(" + inputVar + ", " + JavaExpressionEmitter.emit(comparison.endpoint()) + ") > 0";
        case GREATER_EQUAL -> "compare(" + inputVar + ", " + JavaExpressionEmitter.emit(comparison.endpoint()) + ") >= 0";
      };
      case RuntimeRangeUnaryTest range -> "true";
      case RuntimeExpressionUnaryTest expr -> JavaExpressionEmitter.emit(expr.expression());
    };
  }

  private static void generateHelperMethods(StringBuilder sb) {
    sb.append("""
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
      private static int compare(Object a, Object b) {
        if (a == null || b == null) return 0;
        if (a instanceof Number && b instanceof Number) return toBigDecimal(a).compareTo(toBigDecimal(b));
        if (a instanceof Comparable c && b instanceof Comparable) return c.compareTo(b);
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
    """);
  }
}
