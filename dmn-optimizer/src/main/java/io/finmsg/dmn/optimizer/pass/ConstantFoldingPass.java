package io.finmsg.dmn.optimizer.pass;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Optimization pass that statically evaluates constant expressions into
 * RuntimeConstant nodes.
 */
public class ConstantFoldingPass implements OptimizerPass {

	@Override
	public RuntimeModel transform(RuntimeModel model) {
		Objects.requireNonNull(model, "model");

		List<RuntimeDecision> newDecisions = new ArrayList<>();
		for (RuntimeDecision decision : model.decisions()) {
			newDecisions.add(transformDecision(decision));
		}

		List<RuntimeBkm> newBkms = new ArrayList<>();
		for (RuntimeBkm bkm : model.businessKnowledgeModels()) {
			newBkms.add(transformBkm(bkm));
		}

		return new RuntimeModel(model.inputs(), newDecisions, newBkms, model.evaluationOrder(), model.valueSlotCount());
	}

	private RuntimeDecision transformDecision(RuntimeDecision decision) {
		Optional<RuntimeExpression> newExpr = decision.expression().map(this::transformExpression);
		RuntimeType resultType = decision.type();
		if (resultType.kind() == RuntimeTypeKind.ANY && newExpr.orElse(null) instanceof RuntimeConstant constant
				&& constant.kind() == RuntimeConstantKind.DURATION)
			resultType = RuntimeType.scalar(RuntimeTypeKind.DURATION);
		return new RuntimeDecision(decision.id(), decision.resultSlot(), resultType, decision.dependencies(), newExpr,
				decision.decisionTable(), decision.localSlotCount());
	}

	private RuntimeBkm transformBkm(RuntimeBkm bkm) {
		Optional<RuntimeFunctionDefinition> newFunc = bkm.function().map(this::transformFunctionDefinition);
		return new RuntimeBkm(bkm.id(), bkm.resultSlot(), bkm.type(), bkm.dependencies(), bkm.functionKind(), newFunc);
	}

	private RuntimeFunctionDefinition transformFunctionDefinition(RuntimeFunctionDefinition func) {
		Optional<RuntimeExpression> newBody = func.body().map(this::transformExpression);
		return new RuntimeFunctionDefinition(func.parameters(), newBody, func.external(), func.localSlotCount(),
				func.type());
	}

	public RuntimeExpression transformExpression(RuntimeExpression expr) {
		if (expr == null) {
			return null;
		}

		return switch (expr) {
			case RuntimeUnaryExpression unary -> foldUnary(unary);
			case RuntimeBinaryExpression binary -> foldBinary(binary);
			case RuntimeConditionalExpression cond -> foldConditional(cond);
			case RuntimeListExpression list -> foldList(list);
			case RuntimeFunctionCall call -> foldFunctionCall(call);
			case RuntimeInvocationExpression inv -> foldInvocation(inv);
			case RuntimeContextExpression ctx -> foldContext(ctx);
			case RuntimeFilterExpression filter -> foldFilter(filter);
			case RuntimeBetweenExpression btn -> foldBetween(btn);
			case RuntimeRangeExpression range -> new RuntimeRangeExpression(
					range.lower().map(this::transformExpression), range.upper().map(this::transformExpression),
					range.lowerBoundary(), range.upperBoundary(), range.type());
			case RuntimePathExpression path ->
				new RuntimePathExpression(transformExpression(path.source()), path.member(), path.type());
			case RuntimeInstanceOfExpression inst ->
				new RuntimeInstanceOfExpression(transformExpression(inst.expression()), inst.testedType(), inst.type());
			case RuntimeInExpression in -> foldIn(in);
			default -> expr;
		};
	}

	private RuntimeExpression foldInvocation(RuntimeInvocationExpression inv) {
		Optional<RuntimeExpression> target = inv.target().map(this::transformExpression);
		List<RuntimeExpression> posArgs = inv.positionalArguments().stream().map(this::transformExpression).toList();
		List<RuntimeNamedArgument> namedArgs = inv.namedArguments().stream()
				.map(na -> new RuntimeNamedArgument(na.name(), transformExpression(na.expression()))).toList();

		if (inv.function().isPresent()) {
			String fnName = inv.function().get();
			List<RuntimeExpression> effectiveArgs = posArgs;
			if (effectiveArgs.isEmpty() && !namedArgs.isEmpty()) {
				effectiveArgs = resolveNamedArgs(fnName, namedArgs);
				// Preserve named binding when no exact supported signature was resolved.
				if (effectiveArgs.isEmpty())
					return new RuntimeInvocationExpression(inv.function(), target, namedArgs, posArgs, inv.type());
			}
			RuntimeFunctionCall call = new RuntimeFunctionCall(fnName, effectiveArgs, inv.type());
			RuntimeExpression folded = foldFunctionCall(call);
			if (folded instanceof RuntimeConstant || folded instanceof RuntimeRangeExpression) {
				return folded;
			}
			if (!effectiveArgs.isEmpty() && posArgs.isEmpty()) {
				return new RuntimeInvocationExpression(inv.function(), target, Collections.emptyList(), effectiveArgs,
						inv.type());
			}
			return new RuntimeInvocationExpression(inv.function(), target, namedArgs, posArgs, inv.type());
		}

		return new RuntimeInvocationExpression(inv.function(), target, namedArgs, posArgs, inv.type());
	}

	private List<RuntimeExpression> resolveNamedArgs(String fnName, List<RuntimeNamedArgument> namedArgs) {
		Map<String, RuntimeExpression> map = new HashMap<>();
		for (RuntimeNamedArgument na : namedArgs) {
			map.put(na.name(), na.expression());
		}
		List<String> paramNames = switch (fnName.toLowerCase()) {
			case "date" -> map.containsKey("from")
					? List.of("from")
					: (map.containsKey("date") ? List.of("date") : List.of("year", "month", "day"));
			case "date and time" -> map.containsKey("from")
					? List.of("from")
					: (map.containsKey("date and time") ? List.of("date and time") : List.of("date", "time"));
			case "time" -> map.containsKey("from")
					? List.of("from")
					: (map.containsKey("time")
							? List.of("time")
							: (map.containsKey("offset")
									? List.of("hour", "minute", "second", "offset")
									: List.of("hour", "minute", "second")));
			case "duration" -> List.of("from");
			case "years and months duration" -> List.of("from", "to");
			case "substring" -> map.containsKey("length")
					? List.of("string", "start position", "length")
					: List.of("string", "start position");
			case "string join" -> map.containsKey("delimiter") ? List.of("list", "delimiter") : List.of("list");
			case "matches" ->
				map.containsKey("flags") ? List.of("input", "pattern", "flags") : List.of("input", "pattern");
			case "sublist" -> map.containsKey("length")
					? List.of("list", "start position", "length")
					: List.of("list", "start position");
			case "list replace",
					"list_replace" ->
				map.containsKey("position")
						? (map.size() == 3 ? List.of("list", "position", "newItem") : Collections.emptyList())
						: (map.containsKey("match") && map.size() == 3
								? List.of("list", "match", "newItem")
								: Collections.emptyList());
			case "insert before" -> List.of("list", "position", "newItem");
			case "remove" -> List.of("list", "position");
			case "range" -> map.containsKey("from") && map.size() == 1 ? List.of("from") : Collections.emptyList();
			default -> Collections.emptyList();
		};
		if (!paramNames.isEmpty() && paramNames.size() == namedArgs.size()
				&& paramNames.stream().allMatch(map::containsKey)) {
			return paramNames.stream().map(map::get).toList();
		}
		return Collections.emptyList();
	}

	private RuntimeExpression foldUnary(RuntimeUnaryExpression unary) {
		RuntimeExpression sub = transformExpression(unary.operand());
		RuntimeUnaryOperator op = unary.operator();

		if (sub instanceof RuntimeConstant c) {
			if (op == RuntimeUnaryOperator.NEGATE) {
				if (c.kind() == RuntimeConstantKind.NUMBER) {
					try {
						BigDecimal val = new BigDecimal(c.value()).negate();
						return new RuntimeConstant(RuntimeConstantKind.NUMBER, val.toPlainString(), unary.type());
					} catch (NumberFormatException ignored) {
					}
				} else if (c.kind() == RuntimeConstantKind.DURATION || isValidYearMonthDurationLiteral(c.value())
						|| isValidDayTimeDurationLiteral(c.value())) {
					String v = c.value();
					String neg = v.startsWith("-") ? v.substring(1) : "-" + v;
					return new RuntimeConstant(RuntimeConstantKind.DURATION, neg, unary.type());
				} else {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", unary.type());
				}
			} else if (op == RuntimeUnaryOperator.POSITIVE) {
				if (c.kind() == RuntimeConstantKind.NUMBER) {
					return c;
				} else {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", unary.type());
				}
			} else if (op == RuntimeUnaryOperator.NOT) {
				if (c.kind() == RuntimeConstantKind.BOOLEAN) {
					boolean val = Boolean.parseBoolean(c.value());
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(!val), unary.type());
				} else {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", unary.type());
				}
			}
		}

		return new RuntimeUnaryExpression(op, sub, unary.type());
	}

	private RuntimeExpression foldBinary(RuntimeBinaryExpression binary) {
		RuntimeExpression left = transformExpression(binary.left());
		RuntimeExpression right = transformExpression(binary.right());
		RuntimeBinaryOperator op = binary.operator();

		// Logical short-circuiting & constant folding
		if (op == RuntimeBinaryOperator.AND) {
			if (left instanceof RuntimeConstant cl && cl.kind() == RuntimeConstantKind.BOOLEAN
					&& "false".equalsIgnoreCase(cl.value())) {
				return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", binary.type());
			}
			if (right instanceof RuntimeConstant cr && cr.kind() == RuntimeConstantKind.BOOLEAN
					&& "false".equalsIgnoreCase(cr.value())) {
				return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", binary.type());
			}
			if (left instanceof RuntimeConstant cl && cl.kind() == RuntimeConstantKind.BOOLEAN
					&& "true".equalsIgnoreCase(cl.value())) {
				if (right instanceof RuntimeConstant cr) {
					if (cr.kind() == RuntimeConstantKind.BOOLEAN) {
						return cr;
					} else {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", binary.type());
					}
				}
				if (right.type() != null && right.type().kind() != RuntimeTypeKind.BOOLEAN
						&& right.type().kind() != RuntimeTypeKind.ANY) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", binary.type());
				}
			}
			if (right instanceof RuntimeConstant cr && cr.kind() == RuntimeConstantKind.BOOLEAN
					&& "true".equalsIgnoreCase(cr.value())) {
				if (left instanceof RuntimeConstant cl) {
					if (cl.kind() == RuntimeConstantKind.BOOLEAN) {
						return cl;
					} else {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", binary.type());
					}
				}
				if (left.type() != null && left.type().kind() != RuntimeTypeKind.BOOLEAN
						&& left.type().kind() != RuntimeTypeKind.ANY) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", binary.type());
				}
			}
			if (left instanceof RuntimeConstant cl && right instanceof RuntimeConstant cr) {
				if (cl.kind() != RuntimeConstantKind.BOOLEAN || cr.kind() != RuntimeConstantKind.BOOLEAN) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", binary.type());
				}
			}
		} else if (op == RuntimeBinaryOperator.OR) {
			if (left instanceof RuntimeConstant cl && cl.kind() == RuntimeConstantKind.BOOLEAN
					&& "true".equalsIgnoreCase(cl.value())) {
				return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", binary.type());
			}
			if (right instanceof RuntimeConstant cr && cr.kind() == RuntimeConstantKind.BOOLEAN
					&& "true".equalsIgnoreCase(cr.value())) {
				return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", binary.type());
			}
			if (left instanceof RuntimeConstant cl && cl.kind() == RuntimeConstantKind.BOOLEAN
					&& "false".equalsIgnoreCase(cl.value())) {
				if (right instanceof RuntimeConstant cr) {
					if (cr.kind() == RuntimeConstantKind.BOOLEAN) {
						return cr;
					} else {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", binary.type());
					}
				}
				if (right.type() != null && right.type().kind() != RuntimeTypeKind.BOOLEAN
						&& right.type().kind() != RuntimeTypeKind.ANY) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", binary.type());
				}
			}
			if (right instanceof RuntimeConstant cr && cr.kind() == RuntimeConstantKind.BOOLEAN
					&& "false".equalsIgnoreCase(cr.value())) {
				if (left instanceof RuntimeConstant cl) {
					if (cl.kind() == RuntimeConstantKind.BOOLEAN) {
						return cl;
					} else {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", binary.type());
					}
				}
				if (left.type() != null && left.type().kind() != RuntimeTypeKind.BOOLEAN
						&& left.type().kind() != RuntimeTypeKind.ANY) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", binary.type());
				}
			}
			if (left instanceof RuntimeConstant cl && right instanceof RuntimeConstant cr) {
				if (cl.kind() != RuntimeConstantKind.BOOLEAN || cr.kind() != RuntimeConstantKind.BOOLEAN) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", binary.type());
				}
			}
		}

		RuntimeType resultType = binary.type() != null ? binary.type() : RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);

		// List and Context comparisons
		if (left instanceof RuntimeListExpression l1 && right instanceof RuntimeListExpression l2) {
			if (op == RuntimeBinaryOperator.EQUAL || op == RuntimeBinaryOperator.NOT_EQUAL) {
				if (l1.elements().size() != l2.elements().size()) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(op != RuntimeBinaryOperator.EQUAL), resultType);
				}
				boolean allConstants = true;
				boolean equal = true;
				for (int i = 0; i < l1.elements().size(); i++) {
					RuntimeExpression e1 = l1.elements().get(i);
					RuntimeExpression e2 = l2.elements().get(i);
					RuntimeExpression eqExpr = foldBinary(new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, e1,
							e2, RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)));
					if (eqExpr instanceof RuntimeConstant ce && ce.kind() == RuntimeConstantKind.BOOLEAN) {
						if (!Boolean.parseBoolean(ce.value())) {
							equal = false;
							break;
						}
					} else {
						allConstants = false;
						break;
					}
				}
				if (allConstants) {
					boolean res = (op == RuntimeBinaryOperator.EQUAL) ? equal : !equal;
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(res), resultType);
				}
			}
		}

		if (left instanceof RuntimeContextExpression c1 && right instanceof RuntimeContextExpression c2) {
			if (op == RuntimeBinaryOperator.EQUAL || op == RuntimeBinaryOperator.NOT_EQUAL) {
				if (c1.entries().size() != c2.entries().size()) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(op != RuntimeBinaryOperator.EQUAL), resultType);
				}
				Map<String, RuntimeExpression> m1 = new HashMap<>();
				for (RuntimeContextEntry e : c1.entries()) {
					m1.put(e.name(), e.expression());
				}
				Map<String, RuntimeExpression> m2 = new HashMap<>();
				for (RuntimeContextEntry e : c2.entries()) {
					m2.put(e.name(), e.expression());
				}
				if (!m1.keySet().equals(m2.keySet())) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(op != RuntimeBinaryOperator.EQUAL), resultType);
				}
				boolean allConstants = true;
				boolean equal = true;
				for (String k : m1.keySet()) {
					RuntimeExpression e1 = m1.get(k);
					RuntimeExpression e2 = m2.get(k);
					RuntimeExpression eqExpr = foldBinary(new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL, e1,
							e2, RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)));
					if (eqExpr instanceof RuntimeConstant ce && ce.kind() == RuntimeConstantKind.BOOLEAN) {
						if (!Boolean.parseBoolean(ce.value())) {
							equal = false;
							break;
						}
					} else {
						allConstants = false;
						break;
					}
				}
				if (allConstants) {
					boolean res = (op == RuntimeBinaryOperator.EQUAL) ? equal : !equal;
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(res), resultType);
				}
			}
		}

		if (left instanceof RuntimeRangeExpression r1 && right instanceof RuntimeRangeExpression r2) {
			if (op == RuntimeBinaryOperator.EQUAL || op == RuntimeBinaryOperator.NOT_EQUAL) {
				if (r1.lowerBoundary() != r2.lowerBoundary() || r1.upperBoundary() != r2.upperBoundary()) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(op != RuntimeBinaryOperator.EQUAL), resultType);
				}
				if (r1.lower().isPresent() != r2.lower().isPresent()
						|| r1.upper().isPresent() != r2.upper().isPresent()) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(op != RuntimeBinaryOperator.EQUAL), resultType);
				}
				boolean allConstants = true;
				boolean equal = true;
				if (r1.lower().isPresent()) {
					RuntimeExpression eqExpr = foldBinary(new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL,
							r1.lower().get(), r2.lower().get(), RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)));
					if (eqExpr instanceof RuntimeConstant ce && ce.kind() == RuntimeConstantKind.BOOLEAN) {
						if (!Boolean.parseBoolean(ce.value())) {
							equal = false;
						}
					} else {
						allConstants = false;
					}
				}
				if (allConstants && equal && r1.upper().isPresent()) {
					RuntimeExpression eqExpr = foldBinary(new RuntimeBinaryExpression(RuntimeBinaryOperator.EQUAL,
							r1.upper().get(), r2.upper().get(), RuntimeType.scalar(RuntimeTypeKind.BOOLEAN)));
					if (eqExpr instanceof RuntimeConstant ce && ce.kind() == RuntimeConstantKind.BOOLEAN) {
						if (!Boolean.parseBoolean(ce.value())) {
							equal = false;
						}
					} else {
						allConstants = false;
					}
				}
				if (allConstants) {
					boolean res = (op == RuntimeBinaryOperator.EQUAL) ? equal : !equal;
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(res), resultType);
				}
			}
		}

		// List or Context compared to null or other types
		if (left instanceof RuntimeListExpression && right instanceof RuntimeConstant cr) {
			if (cr.kind() == RuntimeConstantKind.NULL) {
				if (op == RuntimeBinaryOperator.EQUAL)
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", resultType);
				if (op == RuntimeBinaryOperator.NOT_EQUAL)
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", resultType);
			}
			return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
		}
		if (left instanceof RuntimeConstant cl && right instanceof RuntimeListExpression) {
			if (cl.kind() == RuntimeConstantKind.NULL) {
				if (op == RuntimeBinaryOperator.EQUAL)
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", resultType);
				if (op == RuntimeBinaryOperator.NOT_EQUAL)
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", resultType);
			}
			return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
		}
		if (left instanceof RuntimeContextExpression && right instanceof RuntimeConstant cr) {
			if (cr.kind() == RuntimeConstantKind.NULL) {
				if (op == RuntimeBinaryOperator.EQUAL)
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", resultType);
				if (op == RuntimeBinaryOperator.NOT_EQUAL)
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", resultType);
			}
			return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
		}
		if (left instanceof RuntimeConstant cl && right instanceof RuntimeContextExpression) {
			if (cl.kind() == RuntimeConstantKind.NULL) {
				if (op == RuntimeBinaryOperator.EQUAL)
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", resultType);
				if (op == RuntimeBinaryOperator.NOT_EQUAL)
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", resultType);
			}
			return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
		}
		if ((left instanceof RuntimeContextExpression && right instanceof RuntimeListExpression)
				|| (left instanceof RuntimeListExpression && right instanceof RuntimeContextExpression)) {
			if (op == RuntimeBinaryOperator.EQUAL || op == RuntimeBinaryOperator.NOT_EQUAL
					|| op == RuntimeBinaryOperator.LESS || op == RuntimeBinaryOperator.LESS_EQUAL
					|| op == RuntimeBinaryOperator.GREATER || op == RuntimeBinaryOperator.GREATER_EQUAL) {
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
		}

		// Both sides are constants
		if (left instanceof RuntimeConstant cl && right instanceof RuntimeConstant cr) {
			RuntimeType numType = binary.type() != null ? binary.type() : RuntimeType.scalar(RuntimeTypeKind.NUMBER);
			RuntimeType strType = binary.type() != null ? binary.type() : RuntimeType.scalar(RuntimeTypeKind.STRING);
			RuntimeType nullType = binary.type() != null ? binary.type() : RuntimeType.scalar(RuntimeTypeKind.ANY);

			// Null equality & comparison rules in FEEL:
			// null = null is true, null != null is false
			// x = null (x != null) is false, x != null is true
			if (cl.kind() == RuntimeConstantKind.NULL && cr.kind() == RuntimeConstantKind.NULL) {
				if (op == RuntimeBinaryOperator.EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", resultType);
				}
				if (op == RuntimeBinaryOperator.NOT_EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", resultType);
				}
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", nullType);
			}
			if (cl.kind() == RuntimeConstantKind.NULL || cr.kind() == RuntimeConstantKind.NULL) {
				if (op == RuntimeBinaryOperator.EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", resultType);
				}
				if (op == RuntimeBinaryOperator.NOT_EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", resultType);
				}
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", nullType);
			}

			// Boolean equality
			if (cl.kind() == RuntimeConstantKind.BOOLEAN && cr.kind() == RuntimeConstantKind.BOOLEAN) {
				boolean b1 = Boolean.parseBoolean(cl.value());
				boolean b2 = Boolean.parseBoolean(cr.value());
				if (op == RuntimeBinaryOperator.EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(b1 == b2), resultType);
				}
				if (op == RuntimeBinaryOperator.NOT_EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(b1 != b2), resultType);
				}
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", nullType);
			}

			// String concatenation
			if (op == RuntimeBinaryOperator.ADD && cl.kind() == RuntimeConstantKind.STRING
					&& cr.kind() == RuntimeConstantKind.STRING) {
				return new RuntimeConstant(RuntimeConstantKind.STRING, cl.value() + cr.value(), strType);
			}

			// Temporal binary operations
			RuntimeExpression temporalResult = foldTemporalBinary(op, cl, cr, resultType);
			if (temporalResult != null) {
				return temporalResult;
			}

			// Numeric operations
			if (cl.kind() == RuntimeConstantKind.NUMBER && cr.kind() == RuntimeConstantKind.NUMBER) {
				try {
					BigDecimal n1 = new BigDecimal(cl.value());
					BigDecimal n2 = new BigDecimal(cr.value());

					switch (op) {
						case ADD -> {
							return new RuntimeConstant(RuntimeConstantKind.NUMBER, n1.add(n2).toPlainString(), numType);
						}
						case SUBTRACT -> {
							return new RuntimeConstant(RuntimeConstantKind.NUMBER, n1.subtract(n2).toPlainString(),
									numType);
						}
						case MULTIPLY -> {
							return new RuntimeConstant(RuntimeConstantKind.NUMBER, n1.multiply(n2).toPlainString(),
									numType);
						}
						case DIVIDE -> {
							if (n2.compareTo(BigDecimal.ZERO) == 0) {
								return new RuntimeConstant(RuntimeConstantKind.NULL, "null", nullType);
							}
							BigDecimal res;
							try {
								res = n1.divide(n2, MathContext.DECIMAL128).stripTrailingZeros();
							} catch (ArithmeticException e) {
								res = n1.divide(n2, 34, RoundingMode.HALF_UP).stripTrailingZeros();
							}
							return new RuntimeConstant(RuntimeConstantKind.NUMBER, res.toPlainString(), numType);
						}
						case POWER -> {
							try {
								int exp = n2.intValueExact();
								if (exp >= 0 && exp <= 100) {
									return new RuntimeConstant(RuntimeConstantKind.NUMBER, n1.pow(exp).toPlainString(),
											numType);
								}
							} catch (ArithmeticException ignored) {
							}
							double dRes = Math.pow(n1.doubleValue(), n2.doubleValue());
							if (Double.isFinite(dRes)) {
								return new RuntimeConstant(RuntimeConstantKind.NUMBER,
										BigDecimal.valueOf(dRes).stripTrailingZeros().toPlainString(), numType);
							}
							return new RuntimeConstant(RuntimeConstantKind.NULL, "null", nullType);
						}
						case EQUAL -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) == 0), resultType);
						}
						case NOT_EQUAL -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) != 0), resultType);
						}
						case LESS -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) < 0), resultType);
						}
						case LESS_EQUAL -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) <= 0), resultType);
						}
						case GREATER -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) > 0), resultType);
						}
						case GREATER_EQUAL -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) >= 0), resultType);
						}
						default -> {
						}
					}
				} catch (Exception ignored) {
				}
			}

			// String comparisons
			if (cl.kind() == RuntimeConstantKind.STRING && cr.kind() == RuntimeConstantKind.STRING) {
				if (op == RuntimeBinaryOperator.EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(cl.value().equals(cr.value())), resultType);
				} else if (op == RuntimeBinaryOperator.NOT_EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(!cl.value().equals(cr.value())), resultType);
				} else if (op == RuntimeBinaryOperator.LESS) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(cl.value().compareTo(cr.value()) < 0), resultType);
				} else if (op == RuntimeBinaryOperator.LESS_EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(cl.value().compareTo(cr.value()) <= 0), resultType);
				} else if (op == RuntimeBinaryOperator.GREATER) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(cl.value().compareTo(cr.value()) > 0), resultType);
				} else if (op == RuntimeBinaryOperator.GREATER_EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(cl.value().compareTo(cr.value()) >= 0), resultType);
				}
			}

			// Incompatible types for comparisons and arithmetic operations evaluate to null
			// in FEEL
			if (op == RuntimeBinaryOperator.EQUAL || op == RuntimeBinaryOperator.NOT_EQUAL
					|| op == RuntimeBinaryOperator.LESS || op == RuntimeBinaryOperator.LESS_EQUAL
					|| op == RuntimeBinaryOperator.GREATER || op == RuntimeBinaryOperator.GREATER_EQUAL
					|| op == RuntimeBinaryOperator.ADD || op == RuntimeBinaryOperator.SUBTRACT
					|| op == RuntimeBinaryOperator.MULTIPLY || op == RuntimeBinaryOperator.DIVIDE
					|| op == RuntimeBinaryOperator.POWER) {
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", nullType);
			}
		}

		return new RuntimeBinaryExpression(op, left, right, binary.type());
	}

	private RuntimeExpression foldConditional(RuntimeConditionalExpression cond) {
		RuntimeExpression condition = transformExpression(cond.condition());
		RuntimeExpression thenExpr = transformExpression(cond.thenExpression());
		RuntimeExpression elseExpr = transformExpression(cond.elseExpression());

		if (condition instanceof RuntimeConstant c && c.kind() == RuntimeConstantKind.BOOLEAN) {
			boolean val = Boolean.parseBoolean(c.value());
			return val ? thenExpr : elseExpr;
		}

		return new RuntimeConditionalExpression(condition, thenExpr, elseExpr, cond.type());
	}

	private RuntimeExpression foldList(RuntimeListExpression list) {
		List<RuntimeExpression> elements = new ArrayList<>();
		for (RuntimeExpression e : list.elements()) {
			elements.add(transformExpression(e));
		}
		return new RuntimeListExpression(elements, list.type());
	}

	private RuntimeExpression foldFunctionCall(RuntimeFunctionCall call) {
		List<RuntimeExpression> args = new ArrayList<>();
		for (RuntimeExpression a : call.arguments()) {
			args.add(transformExpression(a));
		}

		// Check pure built-in folding with constant args
		String name = call.function().toLowerCase();
		if (name.equals("range")) {
			if (args.size() != 1) {
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
			}
			if (args.get(0) instanceof RuntimeConstant c) {
				if (c.kind() != RuntimeConstantKind.STRING || c.value() == null) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				RuntimeExpression rangeExpr = parseRangeConstant(c.value(), call.type());
				if (rangeExpr != null) {
					return rangeExpr;
				}
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
			}
		}
		if (name.equals("string")) {
			if (args.isEmpty()) {
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
			}
			if (args.size() == 1 && args.get(0) instanceof RuntimeConstant c) {
				if (c.kind() == RuntimeConstantKind.NULL || c.value() == null) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				return new RuntimeConstant(RuntimeConstantKind.STRING, c.value(), call.type());
			}
		}
		if (name.equals("date")) {
			if (args.size() == 1 && args.get(0) instanceof RuntimeConstant c) {
				if (c.kind() == RuntimeConstantKind.NULL || c.value() == null) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				if (c.kind() == RuntimeConstantKind.STRING) {
					if (isValidDateLiteral(c.value())) {
						return new RuntimeConstant(RuntimeConstantKind.DATE, c.value(), call.type());
					}
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				if (c.kind() == RuntimeConstantKind.DATE) {
					return c;
				}
				if (c.kind() == RuntimeConstantKind.DATE_TIME && c.value() != null) {
					int tIdx = c.value().indexOf('T');
					String dPart = tIdx >= 0 ? c.value().substring(0, tIdx) : c.value();
					if (isValidDateLiteral(dPart)) {
						return new RuntimeConstant(RuntimeConstantKind.DATE, dPart, call.type());
					}
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
			}
			if (args.size() == 3 && args.get(0) instanceof RuntimeConstant c0
					&& args.get(1) instanceof RuntimeConstant c1 && args.get(2) instanceof RuntimeConstant c2) {
				if (c0.kind() != RuntimeConstantKind.NUMBER || c1.kind() != RuntimeConstantKind.NUMBER
						|| c2.kind() != RuntimeConstantKind.NUMBER || c0.value() == null || c1.value() == null
						|| c2.value() == null) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				try {
					long y = Long.parseLong(c0.value());
					int m = Integer.parseInt(c1.value());
					int d = Integer.parseInt(c2.value());
					if (y == 0 || y < -999999999L || y > 999999999L || m < 1 || m > 12 || d < 1 || d > 31) {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
					}
					int maxDays = switch (m) {
						case 2 -> isLeapYear(y) ? 29 : 28;
						case 4, 6, 9, 11 -> 30;
						default -> 31;
					};
					if (d > maxDays) {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
					}
					String formatted = (y < 0 ? "-" : "")
							+ (Math.abs(y) < 10000 ? String.format("%04d", Math.abs(y)) : String.valueOf(Math.abs(y)))
							+ "-" + String.format("%02d-%02d", m, d);
					return new RuntimeConstant(RuntimeConstantKind.DATE, formatted, call.type());
				} catch (Exception e) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
			}
		}
		if (name.equals("date and time")) {
			if (args.size() == 1 && args.get(0) instanceof RuntimeConstant c) {
				if (c.kind() == RuntimeConstantKind.NULL || c.value() == null) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				if (c.kind() == RuntimeConstantKind.STRING) {
					if (isValidDateTimeLiteral(c.value())) {
						String val = c.value();
						if (!val.contains("T")) {
							val = val + "T00:00:00";
						}
						return new RuntimeConstant(RuntimeConstantKind.DATE_TIME, val, call.type());
					}
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				if (c.kind() == RuntimeConstantKind.DATE_TIME) {
					return c;
				}
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
			}
			if (args.size() == 2 && args.get(0) instanceof RuntimeConstant c0
					&& args.get(1) instanceof RuntimeConstant c1) {
				if (c0.kind() == RuntimeConstantKind.NULL || c1.kind() == RuntimeConstantKind.NULL || c0.value() == null
						|| c1.value() == null) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				String dStr = c0.value();
				if (c0.kind() == RuntimeConstantKind.DATE_TIME && dStr.contains("T")) {
					dStr = dStr.substring(0, dStr.indexOf("T"));
				}
				String tStr = c1.value();
				if (c1.kind() == RuntimeConstantKind.DATE_TIME && tStr.contains("T")) {
					tStr = tStr.substring(tStr.indexOf("T") + 1);
				}
				if (isValidDateLiteral(dStr) && isValidTimeLiteral(tStr)) {
					return new RuntimeConstant(RuntimeConstantKind.DATE_TIME, dStr + "T" + tStr, call.type());
				}
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
			}
		}
		if (name.equals("time")) {
			if (args.size() == 1 && args.get(0) instanceof RuntimeConstant c) {
				if (c.kind() == RuntimeConstantKind.NULL || c.value() == null) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				if (c.kind() == RuntimeConstantKind.STRING) {
					if (isValidTimeLiteral(c.value())) {
						return new RuntimeConstant(RuntimeConstantKind.TIME, c.value(), call.type());
					}
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				if (c.kind() == RuntimeConstantKind.TIME) {
					return c;
				}
				if (c.kind() == RuntimeConstantKind.DATE_TIME && c.value() != null) {
					int tIdx = c.value().indexOf('T');
					String tPart = tIdx >= 0 ? c.value().substring(tIdx + 1) : c.value();
					if (isValidTimeLiteral(tPart)) {
						return new RuntimeConstant(RuntimeConstantKind.TIME, tPart, call.type());
					}
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				if (c.kind() == RuntimeConstantKind.DATE) {
					return new RuntimeConstant(RuntimeConstantKind.TIME, "00:00:00Z", call.type());
				}
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
			}
			if (args.size() >= 3 && args.size() <= 4 && args.get(0) instanceof RuntimeConstant c0
					&& args.get(1) instanceof RuntimeConstant c1 && args.get(2) instanceof RuntimeConstant c2) {
				if (c0.kind() != RuntimeConstantKind.NUMBER || c1.kind() != RuntimeConstantKind.NUMBER
						|| c2.kind() != RuntimeConstantKind.NUMBER || c0.value() == null || c1.value() == null
						|| c2.value() == null) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				try {
					int h = Integer.parseInt(c0.value());
					int m = Integer.parseInt(c1.value());
					double s = Double.parseDouble(c2.value());
					if (h < 0 || h > 23 || m < 0 || m > 59 || s < 0.0 || s >= 60.0) {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
					}
					String secStr = (s == (long) s)
							? String.format("%02d", (long) s)
							: (s < 10 ? "0" + s : String.valueOf(s));
					String formatted = String.format("%02d:%02d:", h, m) + secStr;
					if (args.size() == 4) {
						RuntimeExpression a3 = args.get(3);
						if (a3 instanceof RuntimeConstant c3) {
							if (c3.kind() == RuntimeConstantKind.NULL || c3.value() == null
									|| "null".equalsIgnoreCase(c3.value())) {
								// null offset -> no offset suffix
							} else if (c3.kind() == RuntimeConstantKind.DURATION
									|| c3.kind() == RuntimeConstantKind.STRING) {
								String dur = c3.value();
								String offFormatted = formatDurationOffset(dur);
								if (offFormatted == null) {
									return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
								}
								formatted += offFormatted;
							} else {
								return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
							}
						}
					}
					return new RuntimeConstant(RuntimeConstantKind.TIME, formatted, call.type());
				} catch (Exception e) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
			}
		}
		if (name.equals("duration") || name.equals("years and months duration")
				|| name.equals("days and time duration")) {
			if (args.size() == 1 && args.get(0) instanceof RuntimeConstant c) {
				if (c.kind() == RuntimeConstantKind.NULL || c.value() == null) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				if (c.kind() == RuntimeConstantKind.STRING) {
					String val = c.value();
					if (isValidYearMonthDurationLiteral(val) || isValidDayTimeDurationLiteral(val)) {
						return new RuntimeConstant(RuntimeConstantKind.DURATION, val, call.type());
					}
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
				}
				if (c.kind() == RuntimeConstantKind.DURATION) {
					return c;
				}
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", call.type());
			}
		}
		return new RuntimeFunctionCall(call.function(), args, call.type());
	}

	private static boolean isValidYearMonthDurationLiteral(String val) {
		if (val == null)
			return false;
		return val.matches("^-?P(?:-?[0-9]+Y)?(?:-?[0-9]+M)?$") && !val.equals("P") && !val.equals("-P")
				&& val.matches(".*[0-9].*") && !val.contains("T") && !val.contains("D") && !val.contains("S");
	}

	private static boolean isValidDayTimeDurationLiteral(String val) {
		if (val == null)
			return false;
		return (val.matches("^-?P(?:-?[0-9]+D)?(?:T(?:-?[0-9]+H)?(?:-?[0-9]+M)?(?:-?[0-9]+(?:\\.[0-9]*)?S)?)?$")
				|| val.matches("^-?PT(?:-?[0-9]+H)?(?:-?[0-9]+M)?(?:-?[0-9]+(?:\\.[0-9]*)?S)?$")) && !val.equals("P")
				&& !val.equals("PT") && !val.equals("-P") && !val.equals("-PT") && val.matches(".*[0-9].*")
				&& !val.contains("Y") && (!val.contains("M") || val.contains("T"));
	}

	private static Long parseFeelMonths(String s) {
		if (s == null)
			return null;
		s = s.trim();
		if ("P0D".equals(s) || "P0M".equals(s) || "P0Y".equals(s) || "PT0S".equals(s) || "0".equals(s)
				|| "-P0D".equals(s) || "-P0M".equals(s) || "-P0Y".equals(s) || "P".equals(s))
			return 0L;
		boolean negative = s.startsWith("-");
		if (negative)
			s = s.substring(1);
		if (!s.startsWith("P"))
			return null;
		s = s.substring(1);
		if (s.contains("T") || s.contains("D") || s.contains("S"))
			return null;
		long years = 0;
		long months = 0;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:(-?[0-9]+)Y)?(?:(-?[0-9]+)M)?").matcher(s);
		if (m.matches() && (m.group(1) != null || m.group(2) != null)) {
			if (m.group(1) != null)
				years = Long.parseLong(m.group(1));
			if (m.group(2) != null)
				months = Long.parseLong(m.group(2));
			long total = years * 12 + months;
			return negative ? -total : total;
		}
		return null;
	}

	private static Double parseFeelSeconds(String s) {
		if (s == null)
			return null;
		s = s.trim();
		if ("P0D".equals(s) || "P0M".equals(s) || "P0Y".equals(s) || "PT0S".equals(s) || "0".equals(s)
				|| "-P0D".equals(s) || "-P0M".equals(s) || "-P0Y".equals(s) || "P".equals(s))
			return 0.0;
		boolean negative = s.startsWith("-");
		if (negative)
			s = s.substring(1);
		if (s.contains("Y") || (s.contains("M") && !s.contains("T")))
			return null;
		if (!s.startsWith("P") && !s.startsWith("T"))
			return null;
		if (s.startsWith("P"))
			s = s.substring(1);
		double days = 0, hours = 0, mins = 0, secs = 0;
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("(?:(-?[0-9]+)D)?(?:T(?:(-?[0-9]+)H)?(?:(-?[0-9]+)M)?(?:(-?[0-9]+(?:\\.[0-9]*)?)S)?)?")
				.matcher(s);
		if (m.matches() && (m.group(1) != null || m.group(2) != null || m.group(3) != null || m.group(4) != null)) {
			if (m.group(1) != null)
				days = Double.parseDouble(m.group(1));
			if (m.group(2) != null)
				hours = Double.parseDouble(m.group(2));
			if (m.group(3) != null)
				mins = Double.parseDouble(m.group(3));
			if (m.group(4) != null)
				secs = Double.parseDouble(m.group(4));
			double total = days * 86400 + hours * 3600 + mins * 60 + secs;
			return negative ? -total : total;
		}
		return null;
	}

	private static String formatYearMonthDuration(long totalMonths) {
		if (totalMonths == 0)
			return "P0M";
		long abs = Math.abs(totalMonths);
		long years = abs / 12;
		long months = abs % 12;
		String prefix = totalMonths < 0 ? "-P" : "P";
		if (years != 0 && months != 0)
			return prefix + years + "Y" + months + "M";
		if (years != 0)
			return prefix + years + "Y";
		return prefix + months + "M";
	}

	private static String formatDayTimeDuration(double totalSec) {
		if (totalSec == 0.0)
			return "PT0S";
		boolean neg = totalSec < 0;
		double abs = Math.abs(totalSec);
		long wholeSec = (long) abs;
		double frac = abs - wholeSec;
		long days = wholeSec / 86400;
		long hours = (wholeSec % 86400) / 3600;
		long mins = (wholeSec % 3600) / 60;
		double secs = (wholeSec % 60) + frac;

		StringBuilder sb = new StringBuilder(neg ? "-P" : "P");
		if (days > 0)
			sb.append(days).append("D");
		if (hours > 0 || mins > 0 || secs > 0 || frac > 0 || days == 0) {
			sb.append("T");
			if (hours > 0)
				sb.append(hours).append("H");
			if (mins > 0)
				sb.append(mins).append("M");
			if (secs > 0 || (hours == 0 && mins == 0)) {
				if (frac > 0) {
					String sStr = BigDecimal.valueOf(secs).stripTrailingZeros().toPlainString();
					sb.append(sStr).append("S");
				} else {
					sb.append((long) secs).append("S");
				}
			}
		}
		return sb.toString();
	}

	private static int maxDaysInMonth(long year, int month) {
		return switch (month) {
			case 2 -> isLeapYear(year) ? 29 : 28;
			case 4, 6, 9, 11 -> 30;
			default -> 31;
		};
	}

	private static String addMonthsToDate(String dateStr, long months) {
		try {
			boolean isNegYear = dateStr.startsWith("-");
			String clean = isNegYear ? dateStr.substring(1) : dateStr;
			String[] parts = clean.split("-");
			long year = (isNegYear ? -1 : 1) * Long.parseLong(parts[0]);
			int month = Integer.parseInt(parts[1]);
			int day = Integer.parseInt(parts[2]);

			long deltaYears = months / 12;
			long remMonths = months % 12;
			long newYear = year + deltaYears;
			long newMonth = month + remMonths;
			while (newMonth > 12) {
				newMonth -= 12;
				newYear += 1;
			}
			while (newMonth < 1) {
				newMonth += 12;
				newYear -= 1;
			}
			int maxDays = maxDaysInMonth(newYear, (int) newMonth);
			int newDay = Math.min(day, maxDays);
			if (newYear < 0)
				return String.format("-%04d-%02d-%02d", Math.abs(newYear), newMonth, newDay);
			else
				return String.format("%04d-%02d-%02d", newYear, newMonth, newDay);
		} catch (Exception e) {
			return null;
		}
	}

	private static String addMonthsToDateTime(String dtStr, long months) {
		int tIdx = dtStr.indexOf('T');
		if (tIdx < 0)
			return addMonthsToDate(dtStr, months);
		String datePart = dtStr.substring(0, tIdx);
		String timePart = dtStr.substring(tIdx + 1);
		String newDate = addMonthsToDate(datePart, months);
		return newDate != null ? newDate + "T" + timePart : null;
	}

	private static String addSecondsToDate(String dateStr, double seconds) {
		try {
			long days = (long) Math.floor(seconds / 86400.0);
			boolean isNegYear = dateStr.startsWith("-");
			String clean = isNegYear ? dateStr.substring(1) : dateStr;
			String[] parts = clean.split("-");
			long year = (isNegYear ? -1 : 1) * Long.parseLong(parts[0]);
			int month = Integer.parseInt(parts[1]);
			long day = Long.parseLong(parts[2]);

			if (year < 0) {
				long newYear = year;
				int newMonth = month;
				long newDay = day + days;
				while (newDay > maxDaysInMonth(newYear, newMonth)) {
					newDay -= maxDaysInMonth(newYear, newMonth);
					newMonth++;
					if (newMonth > 12) {
						newMonth = 1;
						newYear++;
					}
				}
				while (newDay < 1) {
					newMonth--;
					if (newMonth < 1) {
						newMonth = 12;
						newYear--;
					}
					newDay += maxDaysInMonth(newYear, newMonth);
				}
				return String.format("-%04d-%02d-%02d", Math.abs(newYear), newMonth, newDay);
			} else {
				java.time.LocalDate ld = java.time.LocalDate.parse(dateStr).plusDays(days);
				return ld.toString();
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static String formatTimeOfDay(double totalSec) {
		long wholeSec = (long) totalSec;
		double frac = totalSec - wholeSec;
		long h = (wholeSec / 3600) % 24;
		long m = (wholeSec % 3600) / 60;
		double s = (wholeSec % 60) + frac;
		if (frac > 0) {
			String sStr = BigDecimal.valueOf(s).stripTrailingZeros().toPlainString();
			if (s < 10)
				sStr = "0" + sStr;
			return String.format("%02d:%02d:%s", h, m, sStr);
		}
		return String.format("%02d:%02d:%02d", h, m, (long) s);
	}

	private static String addSecondsToDateTime(String dtStr, double seconds) {
		try {
			int tIdx = dtStr.indexOf('T');
			if (tIdx < 0)
				return addSecondsToDate(dtStr, seconds);
			String datePart = dtStr.substring(0, tIdx);
			String timePart = dtStr.substring(tIdx + 1);
			if (timePart.startsWith("24:00:00")) {
				datePart = addSecondsToDate(datePart, 86400);
				timePart = "00:00:00" + timePart.substring(8);
				dtStr = datePart + "T" + timePart;
			}

			if (dtStr.contains("@")) {
				int atIdx = dtStr.indexOf('@');
				String localPart = dtStr.substring(0, atIdx);
				String zoneId = dtStr.substring(atIdx + 1);
				if (localPart.startsWith("-")) {
					int localTIdx = localPart.indexOf('T');
					String dPart = localPart.substring(0, localTIdx);
					String tP = localPart.substring(localTIdx + 1);
					String[] tParts = tP.split(":");
					long h = Long.parseLong(tParts[0]);
					long m = Long.parseLong(tParts[1]);
					double s = Double.parseDouble(tParts[2]);
					double totalSec = h * 3600 + m * 60 + s + seconds;
					long dayOffset = (long) Math.floor(totalSec / 86400.0);
					double timeSec = totalSec - dayOffset * 86400.0;
					String newDate = addSecondsToDate(dPart, dayOffset * 86400.0);
					return newDate + "T" + formatTimeOfDay(timeSec) + "@" + zoneId;
				} else {
					java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(localPart);
					java.time.ZonedDateTime zdt = ldt.atZone(java.time.ZoneId.of(zoneId));
					long sec = (long) seconds;
					long nanos = (long) ((seconds - sec) * 1e9);
					zdt = zdt.plusSeconds(sec).plusNanos(nanos);
					return zdt.toLocalDateTime().toString() + "@" + zoneId;
				}
			}

			if (dtStr.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$")) {
				if (dtStr.startsWith("-")) {
					int signIdx = Math.max(dtStr.lastIndexOf('+'), dtStr.lastIndexOf('-'));
					if (dtStr.endsWith("Z") || dtStr.endsWith("z"))
						signIdx = dtStr.length() - 1;
					String localPart = dtStr.substring(0, signIdx);
					String offsetPart = dtStr.substring(signIdx);
					int localTIdx = localPart.indexOf('T');
					String dPart = localPart.substring(0, localTIdx);
					String tP = localPart.substring(localTIdx + 1);
					String[] tParts = tP.split(":");
					long h = Long.parseLong(tParts[0]);
					long m = Long.parseLong(tParts[1]);
					double s = Double.parseDouble(tParts[2]);
					double totalSec = h * 3600 + m * 60 + s + seconds;
					long dayOffset = (long) Math.floor(totalSec / 86400.0);
					double timeSec = totalSec - dayOffset * 86400.0;
					String newDate = addSecondsToDate(dPart, dayOffset * 86400.0);
					return newDate + "T" + formatTimeOfDay(timeSec) + offsetPart;
				} else {
					java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(dtStr);
					long sec = (long) seconds;
					long nanos = (long) ((seconds - sec) * 1e9);
					odt = odt.plusSeconds(sec).plusNanos(nanos);
					return odt.toString();
				}
			}

			if (dtStr.startsWith("-")) {
				String[] tParts = timePart.split(":");
				long h = Long.parseLong(tParts[0]);
				long m = Long.parseLong(tParts[1]);
				double s = Double.parseDouble(tParts[2]);
				double totalSec = h * 3600 + m * 60 + s + seconds;
				long dayOffset = (long) Math.floor(totalSec / 86400.0);
				double timeSec = totalSec - dayOffset * 86400.0;
				String newDate = addSecondsToDate(datePart, dayOffset * 86400.0);
				return newDate + "T" + formatTimeOfDay(timeSec);
			} else {
				java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dtStr);
				long sec = (long) seconds;
				long nanos = (long) ((seconds - sec) * 1e9);
				ldt = ldt.plusSeconds(sec).plusNanos(nanos);
				return ldt.toString();
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static String addSecondsToTime(String timeStr, double seconds) {
		try {
			String suffix = "";
			String main = timeStr;
			if (timeStr.contains("@")) {
				int atIdx = timeStr.indexOf('@');
				main = timeStr.substring(0, atIdx);
				suffix = timeStr.substring(atIdx);
			} else if (timeStr.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$")) {
				int signIdx = Math.max(timeStr.lastIndexOf('+'), timeStr.lastIndexOf('-'));
				if (timeStr.endsWith("Z") || timeStr.endsWith("z"))
					signIdx = timeStr.length() - 1;
				main = timeStr.substring(0, signIdx);
				suffix = timeStr.substring(signIdx);
			}
			String[] parts = main.split(":");
			long h = Long.parseLong(parts[0]);
			long m = Long.parseLong(parts[1]);
			double s = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.0;
			double total = (h * 3600 + m * 60 + s + seconds) % 86400.0;
			if (total < 0)
				total += 86400.0;
			return formatTimeOfDay(total) + suffix;
		} catch (Exception e) {
			return null;
		}
	}

	private static java.time.Instant parseToInstant(String dtStr) {
		if (dtStr.contains("@")) {
			int atIdx = dtStr.indexOf('@');
			java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dtStr.substring(0, atIdx));
			return ldt.atZone(java.time.ZoneId.of(dtStr.substring(atIdx + 1))).toInstant();
		}
		if (dtStr.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$")) {
			return java.time.OffsetDateTime.parse(dtStr).toInstant();
		}
		return java.time.LocalDateTime.parse(dtStr).atZone(java.time.ZoneOffset.UTC).toInstant();
	}

	private static String subtractDateFromDate(String d1, String d2) {
		try {
			java.time.LocalDate ld1 = java.time.LocalDate.parse(d1);
			java.time.LocalDate ld2 = java.time.LocalDate.parse(d2);
			long days = java.time.temporal.ChronoUnit.DAYS.between(ld2, ld1);
			return (days < 0 ? "-P" : "P") + Math.abs(days) + "D";
		} catch (Exception e) {
			return null;
		}
	}

	private static String subtractDateTimeFromDate(String d1, String dt2) {
		try {
			boolean z2 = dt2.contains("@") || dt2.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$");
			if (!z2)
				return null;
			java.time.Instant i1 = java.time.LocalDate.parse(d1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
			java.time.Instant i2 = parseToInstant(dt2);
			java.time.Duration d = java.time.Duration.between(i2, i1);
			return formatDayTimeDuration(d.getSeconds() + d.getNano() / 1e9);
		} catch (Exception e) {
			return null;
		}
	}

	private static String subtractDateFromDateTime(String dt1, String d2) {
		try {
			boolean z1 = dt1.contains("@") || dt1.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$");
			if (!z1)
				return null;
			java.time.Instant i1 = parseToInstant(dt1);
			java.time.Instant i2 = java.time.LocalDate.parse(d2).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
			java.time.Duration d = java.time.Duration.between(i2, i1);
			return formatDayTimeDuration(d.getSeconds() + d.getNano() / 1e9);
		} catch (Exception e) {
			return null;
		}
	}

	private static String subtractDateTimeFromDateTime(String dt1, String dt2) {
		try {
			boolean z1 = dt1.contains("@") || dt1.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$");
			boolean z2 = dt2.contains("@") || dt2.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$");
			if (z1 != z2)
				return null;
			if (z1) {
				java.time.Instant i1 = parseToInstant(dt1);
				java.time.Instant i2 = parseToInstant(dt2);
				java.time.Duration d = java.time.Duration.between(i2, i1);
				return formatDayTimeDuration(d.getSeconds() + d.getNano() / 1e9);
			} else {
				java.time.LocalDateTime ldt1 = java.time.LocalDateTime.parse(dt1);
				java.time.LocalDateTime ldt2 = java.time.LocalDateTime.parse(dt2);
				java.time.Duration d = java.time.Duration.between(ldt2, ldt1);
				return formatDayTimeDuration(d.getSeconds() + d.getNano() / 1e9);
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static double parseTimeToSeconds(String tStr) {
		String main = tStr;
		String offsetStr = null;
		if (tStr.contains("@")) {
			int atIdx = tStr.indexOf('@');
			main = tStr.substring(0, atIdx);
			String zoneId = tStr.substring(atIdx + 1);
			java.time.ZoneId z = java.time.ZoneId.of(zoneId);
			java.time.ZoneOffset offset = z.getRules().getOffset(java.time.Instant.now());
			offsetStr = offset.getId();
		} else if (tStr.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$")) {
			int signIdx = Math.max(tStr.lastIndexOf('+'), tStr.lastIndexOf('-'));
			if (tStr.endsWith("Z") || tStr.endsWith("z"))
				signIdx = tStr.length() - 1;
			main = tStr.substring(0, signIdx);
			offsetStr = tStr.substring(signIdx);
		}
		String[] parts = main.split(":");
		long h = Long.parseLong(parts[0]);
		long m = Long.parseLong(parts[1]);
		double s = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.0;
		double totalSec = h * 3600 + m * 60 + s;
		if (offsetStr != null) {
			if (offsetStr.equalsIgnoreCase("Z")) {
				// 0 offset
			} else {
				boolean neg = offsetStr.startsWith("-");
				String[] offParts = (neg
						? offsetStr.substring(1)
						: (offsetStr.startsWith("+") ? offsetStr.substring(1) : offsetStr)).split(":");
				long offH = Long.parseLong(offParts[0]);
				long offM = offParts.length > 1 ? Long.parseLong(offParts[1]) : 0;
				long offSec = (offH * 3600 + offM * 60) * (neg ? -1 : 1);
				totalSec -= offSec;
			}
		}
		return totalSec;
	}

	private static String subtractTimeFromTime(String t1, String t2) {
		try {
			boolean z1 = t1.contains("@") || t1.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$");
			boolean z2 = t2.contains("@") || t2.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$");
			if (z1 != z2)
				return null;
			double s1 = parseTimeToSeconds(t1);
			double s2 = parseTimeToSeconds(t2);
			return formatDayTimeDuration(s1 - s2);
		} catch (Exception e) {
			return null;
		}
	}

	private RuntimeExpression foldTemporalBinary(RuntimeBinaryOperator op, RuntimeConstant cl, RuntimeConstant cr,
			RuntimeType resultType) {
		RuntimeConstantKind lk = cl.kind();
		RuntimeConstantKind rk = cr.kind();
		String lv = cl.value();
		String rv = cr.value();
		if (lv == null || rv == null)
			return null;

		// Detect if STRING constants are actually temporal literals
		if (lk == RuntimeConstantKind.STRING) {
			if (isValidDateLiteral(lv))
				lk = RuntimeConstantKind.DATE;
			else if (isValidDateTimeLiteral(lv))
				lk = RuntimeConstantKind.DATE_TIME;
			else if (isValidTimeLiteral(lv))
				lk = RuntimeConstantKind.TIME;
			else if (isValidYearMonthDurationLiteral(lv) || isValidDayTimeDurationLiteral(lv))
				lk = RuntimeConstantKind.DURATION;
		}
		if (rk == RuntimeConstantKind.STRING) {
			if (isValidDateLiteral(rv))
				rk = RuntimeConstantKind.DATE;
			else if (isValidDateTimeLiteral(rv))
				rk = RuntimeConstantKind.DATE_TIME;
			else if (isValidTimeLiteral(rv))
				rk = RuntimeConstantKind.TIME;
			else if (isValidYearMonthDurationLiteral(rv) || isValidDayTimeDurationLiteral(rv))
				rk = RuntimeConstantKind.DURATION;
		}

		boolean isLym = (lk == RuntimeConstantKind.DURATION) && parseFeelMonths(lv) != null;
		boolean isLdt = (lk == RuntimeConstantKind.DURATION) && parseFeelSeconds(lv) != null;
		boolean isRym = (rk == RuntimeConstantKind.DURATION) && parseFeelMonths(rv) != null;
		boolean isRdt = (rk == RuntimeConstantKind.DURATION) && parseFeelSeconds(rv) != null;

		if (op == RuntimeBinaryOperator.ADD) {
			// DATE + ymDuration / ymDuration + DATE
			if (lk == RuntimeConstantKind.DATE && isRym) {
				String res = addMonthsToDate(lv, parseFeelMonths(rv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			if (isLym && rk == RuntimeConstantKind.DATE) {
				String res = addMonthsToDate(rv, parseFeelMonths(lv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// DATE + dtDuration / dtDuration + DATE
			if (lk == RuntimeConstantKind.DATE && isRdt) {
				String res = addSecondsToDate(lv, parseFeelSeconds(rv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			if (isLdt && rk == RuntimeConstantKind.DATE) {
				String res = addSecondsToDate(rv, parseFeelSeconds(lv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// DATE_TIME + ymDuration / ymDuration + DATE_TIME
			if (lk == RuntimeConstantKind.DATE_TIME && isRym) {
				String res = addMonthsToDateTime(lv, parseFeelMonths(rv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE_TIME, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			if (isLym && rk == RuntimeConstantKind.DATE_TIME) {
				String res = addMonthsToDateTime(rv, parseFeelMonths(lv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE_TIME, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// DATE_TIME + dtDuration / dtDuration + DATE_TIME
			if (lk == RuntimeConstantKind.DATE_TIME && isRdt) {
				String res = addSecondsToDateTime(lv, parseFeelSeconds(rv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE_TIME, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			if (isLdt && rk == RuntimeConstantKind.DATE_TIME) {
				String res = addSecondsToDateTime(rv, parseFeelSeconds(lv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE_TIME, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// TIME + dtDuration / dtDuration + TIME
			if (lk == RuntimeConstantKind.TIME && isRdt) {
				String res = addSecondsToTime(lv, parseFeelSeconds(rv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.TIME, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			if (isLdt && rk == RuntimeConstantKind.TIME) {
				String res = addSecondsToTime(rv, parseFeelSeconds(lv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.TIME, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// ymDuration + ymDuration
			if (isLym && isRym) {
				long total = parseFeelMonths(lv) + parseFeelMonths(rv);
				return new RuntimeConstant(RuntimeConstantKind.DURATION, formatYearMonthDuration(total), resultType);
			}
			// dtDuration + dtDuration
			if (isLdt && isRdt) {
				double total = parseFeelSeconds(lv) + parseFeelSeconds(rv);
				return new RuntimeConstant(RuntimeConstantKind.DURATION, formatDayTimeDuration(total), resultType);
			}
		} else if (op == RuntimeBinaryOperator.SUBTRACT) {
			// DATE - DATE -> dtDuration
			if (lk == RuntimeConstantKind.DATE && rk == RuntimeConstantKind.DATE) {
				String res = subtractDateFromDate(lv, rv);
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DURATION, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// DATE - DATE_TIME / DATE_TIME - DATE -> dtDuration
			if (lk == RuntimeConstantKind.DATE && rk == RuntimeConstantKind.DATE_TIME) {
				String res = subtractDateTimeFromDate(lv, rv);
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DURATION, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			if (lk == RuntimeConstantKind.DATE_TIME && rk == RuntimeConstantKind.DATE) {
				String res = subtractDateFromDateTime(lv, rv);
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DURATION, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// DATE_TIME - DATE_TIME -> dtDuration
			if (lk == RuntimeConstantKind.DATE_TIME && rk == RuntimeConstantKind.DATE_TIME) {
				String res = subtractDateTimeFromDateTime(lv, rv);
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DURATION, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// TIME - TIME -> dtDuration
			if (lk == RuntimeConstantKind.TIME && rk == RuntimeConstantKind.TIME) {
				String res = subtractTimeFromTime(lv, rv);
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DURATION, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// DATE - ymDuration
			if (lk == RuntimeConstantKind.DATE && isRym) {
				String res = addMonthsToDate(lv, -parseFeelMonths(rv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// DATE - dtDuration
			if (lk == RuntimeConstantKind.DATE && isRdt) {
				String res = addSecondsToDate(lv, -parseFeelSeconds(rv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// DATE_TIME - ymDuration
			if (lk == RuntimeConstantKind.DATE_TIME && isRym) {
				String res = addMonthsToDateTime(lv, -parseFeelMonths(rv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE_TIME, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// DATE_TIME - dtDuration
			if (lk == RuntimeConstantKind.DATE_TIME && isRdt) {
				String res = addSecondsToDateTime(lv, -parseFeelSeconds(rv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.DATE_TIME, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// TIME - dtDuration
			if (lk == RuntimeConstantKind.TIME && isRdt) {
				String res = addSecondsToTime(lv, -parseFeelSeconds(rv));
				return res != null
						? new RuntimeConstant(RuntimeConstantKind.TIME, res, resultType)
						: new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
			// ymDuration - ymDuration
			if (isLym && isRym) {
				long total = parseFeelMonths(lv) - parseFeelMonths(rv);
				return new RuntimeConstant(RuntimeConstantKind.DURATION, formatYearMonthDuration(total), resultType);
			}
			// dtDuration - dtDuration
			if (isLdt && isRdt) {
				double total = parseFeelSeconds(lv) - parseFeelSeconds(rv);
				return new RuntimeConstant(RuntimeConstantKind.DURATION, formatDayTimeDuration(total), resultType);
			}
		} else if (op == RuntimeBinaryOperator.MULTIPLY) {
			// ymDuration * number / number * ymDuration
			if (isLym && rk == RuntimeConstantKind.NUMBER) {
				try {
					double n = Double.parseDouble(rv);
					long total = Math.round(parseFeelMonths(lv) * n);
					return new RuntimeConstant(RuntimeConstantKind.DURATION, formatYearMonthDuration(total),
							resultType);
				} catch (Exception ignored) {
				}
			}
			if (lk == RuntimeConstantKind.NUMBER && isRym) {
				try {
					double n = Double.parseDouble(lv);
					long total = Math.round(parseFeelMonths(rv) * n);
					return new RuntimeConstant(RuntimeConstantKind.DURATION, formatYearMonthDuration(total),
							resultType);
				} catch (Exception ignored) {
				}
			}
			// dtDuration * number / number * dtDuration
			if (isLdt && rk == RuntimeConstantKind.NUMBER) {
				try {
					double n = Double.parseDouble(rv);
					double total = parseFeelSeconds(lv) * n;
					return new RuntimeConstant(RuntimeConstantKind.DURATION, formatDayTimeDuration(total), resultType);
				} catch (Exception ignored) {
				}
			}
			if (lk == RuntimeConstantKind.NUMBER && isRdt) {
				try {
					double n = Double.parseDouble(lv);
					double total = parseFeelSeconds(rv) * n;
					return new RuntimeConstant(RuntimeConstantKind.DURATION, formatDayTimeDuration(total), resultType);
				} catch (Exception ignored) {
				}
			}
		} else if (op == RuntimeBinaryOperator.DIVIDE) {
			// ymDuration / number
			if (isLym && rk == RuntimeConstantKind.NUMBER) {
				try {
					double n = Double.parseDouble(rv);
					if (n == 0)
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
					long total = Math.round(parseFeelMonths(lv) / n);
					return new RuntimeConstant(RuntimeConstantKind.DURATION, formatYearMonthDuration(total),
							resultType);
				} catch (Exception ignored) {
				}
			}
			// dtDuration / number
			if (isLdt && rk == RuntimeConstantKind.NUMBER) {
				try {
					double n = Double.parseDouble(rv);
					if (n == 0)
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
					double total = parseFeelSeconds(lv) / n;
					return new RuntimeConstant(RuntimeConstantKind.DURATION, formatDayTimeDuration(total), resultType);
				} catch (Exception ignored) {
				}
			}
			// ymDuration / ymDuration -> NUMBER
			if (isLym && isRym) {
				long m2 = parseFeelMonths(rv);
				if (m2 == 0)
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
				BigDecimal res = BigDecimal.valueOf((double) parseFeelMonths(lv) / m2).stripTrailingZeros();
				return new RuntimeConstant(RuntimeConstantKind.NUMBER, res.toPlainString(), resultType);
			}
			// dtDuration / dtDuration -> NUMBER
			if (isLdt && isRdt) {
				double s2 = parseFeelSeconds(rv);
				if (s2 == 0)
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
				BigDecimal res = BigDecimal.valueOf(parseFeelSeconds(lv) / s2).stripTrailingZeros();
				return new RuntimeConstant(RuntimeConstantKind.NUMBER, res.toPlainString(), resultType);
			}
		}

		if (op == RuntimeBinaryOperator.EQUAL || op == RuntimeBinaryOperator.NOT_EQUAL
				|| op == RuntimeBinaryOperator.LESS || op == RuntimeBinaryOperator.LESS_EQUAL
				|| op == RuntimeBinaryOperator.GREATER || op == RuntimeBinaryOperator.GREATER_EQUAL) {
			// DATE vs DATE
			if (lk == RuntimeConstantKind.DATE && rk == RuntimeConstantKind.DATE) {
				int cmp = compareDates(lv, rv);
				boolean res = switch (op) {
					case EQUAL -> cmp == 0;
					case NOT_EQUAL -> cmp != 0;
					case LESS -> cmp < 0;
					case LESS_EQUAL -> cmp <= 0;
					case GREATER -> cmp > 0;
					case GREATER_EQUAL -> cmp >= 0;
					default -> false;
				};
				return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(res), resultType);
			}
			// DATE_TIME vs DATE_TIME
			if (lk == RuntimeConstantKind.DATE_TIME && rk == RuntimeConstantKind.DATE_TIME) {
				boolean z1 = lv.contains("@") || lv.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$");
				boolean z2 = rv.contains("@") || rv.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$");
				if (z1 != z2) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
				}
				int cmp;
				if (z1) {
					try {
						java.time.Instant i1 = parseToInstant(lv);
						java.time.Instant i2 = parseToInstant(rv);
						long m1 = i1.toEpochMilli();
						long m2 = i2.toEpochMilli();
						cmp = Long.compare(m1, m2);
					} catch (Exception e) {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
					}
				} else {
					try {
						String dt1 = lv.contains("T") ? lv : lv + "T00:00:00";
						String dt2 = rv.contains("T") ? rv : rv + "T00:00:00";
						java.time.LocalDateTime l1 = java.time.LocalDateTime.parse(dt1)
								.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
						java.time.LocalDateTime l2 = java.time.LocalDateTime.parse(dt2)
								.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
						cmp = l1.compareTo(l2);
					} catch (Exception e) {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
					}
				}
				boolean res = switch (op) {
					case EQUAL -> cmp == 0;
					case NOT_EQUAL -> cmp != 0;
					case LESS -> cmp < 0;
					case LESS_EQUAL -> cmp <= 0;
					case GREATER -> cmp > 0;
					case GREATER_EQUAL -> cmp >= 0;
					default -> false;
				};
				return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(res), resultType);
			}
			// TIME vs TIME
			if (lk == RuntimeConstantKind.TIME && rk == RuntimeConstantKind.TIME) {
				boolean z1 = lv.contains("@") || lv.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$");
				boolean z2 = rv.contains("@") || rv.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$");
				if (z1 != z2) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
				}
				try {
					double s1 = parseTimeToSeconds(lv);
					double s2 = parseTimeToSeconds(rv);
					long m1 = Math.round(s1 * 1000.0);
					long m2 = Math.round(s2 * 1000.0);
					int cmp = Long.compare(m1, m2);
					boolean res = switch (op) {
						case EQUAL -> cmp == 0;
						case NOT_EQUAL -> cmp != 0;
						case LESS -> cmp < 0;
						case LESS_EQUAL -> cmp <= 0;
						case GREATER -> cmp > 0;
						case GREATER_EQUAL -> cmp >= 0;
						default -> false;
					};
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(res), resultType);
				} catch (Exception e) {
					return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
				}
			}
			// DURATION vs DURATION
			if (lk == RuntimeConstantKind.DURATION && rk == RuntimeConstantKind.DURATION) {
				if (isLym && isRym) {
					int cmp = Long.compare(parseFeelMonths(lv), parseFeelMonths(rv));
					boolean res = switch (op) {
						case EQUAL -> cmp == 0;
						case NOT_EQUAL -> cmp != 0;
						case LESS -> cmp < 0;
						case LESS_EQUAL -> cmp <= 0;
						case GREATER -> cmp > 0;
						case GREATER_EQUAL -> cmp >= 0;
						default -> false;
					};
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(res), resultType);
				}
				if (isLdt && isRdt) {
					int cmp = Double.compare(parseFeelSeconds(lv), parseFeelSeconds(rv));
					boolean res = switch (op) {
						case EQUAL -> cmp == 0;
						case NOT_EQUAL -> cmp != 0;
						case LESS -> cmp < 0;
						case LESS_EQUAL -> cmp <= 0;
						case GREATER -> cmp > 0;
						case GREATER_EQUAL -> cmp >= 0;
						default -> false;
					};
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(res), resultType);
				}
				return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
			}
		}

		// Check if at least one operand is a temporal type (and thus the operation is
		// an incompatible temporal operation)
		if (lk == RuntimeConstantKind.DATE || lk == RuntimeConstantKind.DATE_TIME || lk == RuntimeConstantKind.TIME
				|| lk == RuntimeConstantKind.DURATION || rk == RuntimeConstantKind.DATE
				|| rk == RuntimeConstantKind.DATE_TIME || rk == RuntimeConstantKind.TIME
				|| rk == RuntimeConstantKind.DURATION) {
			return new RuntimeConstant(RuntimeConstantKind.NULL, "null", resultType);
		}

		return null;
	}

	private static int compareDates(String d1, String d2) {
		try {
			boolean neg1 = d1.startsWith("-");
			boolean neg2 = d2.startsWith("-");
			String clean1 = neg1 ? d1.substring(1) : d1;
			String clean2 = neg2 ? d2.substring(1) : d2;
			String[] p1 = clean1.split("-");
			String[] p2 = clean2.split("-");
			long y1 = (neg1 ? -1 : 1) * Long.parseLong(p1[0]);
			long y2 = (neg2 ? -1 : 1) * Long.parseLong(p2[0]);
			if (y1 != y2)
				return Long.compare(y1, y2);
			int m1 = Integer.parseInt(p1[1]);
			int m2 = Integer.parseInt(p2[1]);
			if (m1 != m2)
				return Integer.compare(m1, m2);
			int day1 = Integer.parseInt(p1[2]);
			int day2 = Integer.parseInt(p2[2]);
			return Integer.compare(day1, day2);
		} catch (Exception e) {
			return d1.compareTo(d2);
		}
	}

	private static boolean isValidDateLiteral(String val) {
		if (val == null)
			return false;
		if (!val.matches("^(?:-?[1-9][0-9]{3,8}|[0-9]{4})-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])$")) {
			return false;
		}
		if (val.startsWith("0000-") || val.startsWith("-0000-")) {
			return false;
		}
		if (val.startsWith("0") && val.indexOf('-') > 4) {
			return false;
		}
		long year;
		int month, day;
		if (val.startsWith("-")) {
			String[] parts = val.substring(1).split("-");
			year = -Long.parseLong(parts[0]);
			month = Integer.parseInt(parts[1]);
			day = Integer.parseInt(parts[2]);
		} else {
			String[] parts = val.split("-");
			year = Long.parseLong(parts[0]);
			month = Integer.parseInt(parts[1]);
			day = Integer.parseInt(parts[2]);
		}
		if (year == 0 || year < -999999999L || year > 999999999L)
			return false;
		if (month < 1 || month > 12 || day < 1 || day > 31)
			return false;
		int maxDays = switch (month) {
			case 2 -> isLeapYear(year) ? 29 : 28;
			case 4, 6, 9, 11 -> 30;
			default -> 31;
		};
		return day <= maxDays;
	}

	private static boolean isLeapYear(long year) {
		return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
	}

	private static boolean isValidTimeLiteral(String val) {
		if (val == null)
			return false;
		if (val.contains("@")) {
			int atIdx = val.indexOf("@");
			String tPart = val.substring(0, atIdx);
			String zoneId = val.substring(atIdx + 1);
			if (!tPart.matches("^(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:\\.[0-9]+)?$")) {
				return false;
			}
			try {
				java.time.ZoneId.of(zoneId);
				return true;
			} catch (Exception e) {
				return false;
			}
		} else {
			return val.matches(
					"^(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:\\.[0-9]+)?(?:[zZ]|[+-](?:0[0-9]|1[0-4]):[0-5][0-9])?$");
		}
	}

	private static boolean isValidDateTimeLiteral(String val) {
		if (val == null)
			return false;
		if (val.contains("@")) {
			int atIdx = val.indexOf("@");
			String dtPart = val.substring(0, atIdx);
			String zoneId = val.substring(atIdx + 1);
			if (dtPart.contains("+") || dtPart.contains("Z") || dtPart.contains("z")) {
				return false;
			}
			int tIdx = dtPart.indexOf('T');
			if (tIdx < 0)
				return false;
			String datePart = dtPart.substring(0, tIdx);
			String timePart = dtPart.substring(tIdx + 1);
			if (!isValidDateLiteral(datePart) || !isValidTimeLiteral(timePart)) {
				return false;
			}
			try {
				java.time.ZoneId.of(zoneId);
				return true;
			} catch (Exception e) {
				return false;
			}
		} else {
			int tIdx = val.indexOf('T');
			if (tIdx < 0) {
				return isValidDateLiteral(val);
			}
			String datePart = val.substring(0, tIdx);
			String timePart = val.substring(tIdx + 1);
			return isValidDateLiteral(datePart) && isValidTimeLiteral(timePart);
		}
	}

	private static String formatDurationOffset(String dur) {
		if (dur == null || dur.isEmpty() || "null".equalsIgnoreCase(dur))
			return "";
		boolean neg = dur.startsWith("-");
		String clean = neg ? dur.substring(1) : dur;
		java.util.regex.Matcher dt = java.util.regex.Pattern
				.compile("P(?:([0-9]+)D)?(?:T(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9]+(?:\\.[0-9]+)?)S)?)?")
				.matcher(clean);
		if (dt.matches()) {
			long d = dt.group(1) != null ? Long.parseLong(dt.group(1)) : 0;
			long h = dt.group(2) != null ? Long.parseLong(dt.group(2)) : 0;
			long m = dt.group(3) != null ? Long.parseLong(dt.group(3)) : 0;
			long s = dt.group(4) != null ? (long) Double.parseDouble(dt.group(4)) : 0;
			long totalSec = (d * 86400 + h * 3600 + m * 60 + s) * (neg ? -1 : 1);
			if (totalSec == 0)
				return "Z";
			String sign = totalSec < 0 ? "-" : "+";
			long abs = Math.abs(totalSec);
			long hours = abs / 3600;
			long mins = (abs % 3600) / 60;
			long secs = abs % 60;
			if (secs > 0) {
				return String.format("%s%02d:%02d:%02d", sign, hours, mins, secs);
			} else {
				return String.format("%s%02d:%02d", sign, hours, mins);
			}
		}
		return null;
	}

	private RuntimeExpression foldContext(RuntimeContextExpression ctx) {
		List<RuntimeContextEntry> entries = new ArrayList<>();
		for (RuntimeContextEntry entry : ctx.entries()) {
			entries.add(
					new RuntimeContextEntry(entry.name(), entry.localSlot(), transformExpression(entry.expression())));
		}
		return new RuntimeContextExpression(entries, ctx.type());
	}

	private RuntimeExpression foldFilter(RuntimeFilterExpression filter) {
		RuntimeExpression source = transformExpression(filter.source());
		RuntimeExpression predicate = transformExpression(filter.filter());
		if (predicate instanceof RuntimeConstant cp && cp.kind() == RuntimeConstantKind.NUMBER) {
			try {
				int idx = new BigDecimal(cp.value()).intValueExact();
				if (source instanceof RuntimeListExpression list) {
					int size = list.elements().size();
					int actualIdx = idx > 0 ? idx - 1 : (idx < 0 ? size + idx : -1);
					if (actualIdx >= 0 && actualIdx < size) {
						return list.elements().get(actualIdx);
					} else {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", filter.type());
					}
				}
				if (source instanceof RuntimeConstant cs) {
					// FEEL spec singleton list indexing: item[1] == item, item[-1] == item, item[2]
					// == null
					if (idx == 1 || idx == -1) {
						return cs;
					} else {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", filter.type());
					}
				}
				if (source instanceof RuntimeContextExpression ctx) {
					if (idx == 1 || idx == -1) {
						return ctx;
					} else {
						return new RuntimeConstant(RuntimeConstantKind.NULL, "null", filter.type());
					}
				}
			} catch (Exception ignored) {
			}
		}
		return new RuntimeFilterExpression(source, filter.localSlot(), predicate, filter.type());
	}

	private RuntimeExpression foldBetween(RuntimeBetweenExpression btn) {
		RuntimeExpression val = transformExpression(btn.value());
		RuntimeExpression lower = transformExpression(btn.lower());
		RuntimeExpression upper = transformExpression(btn.upper());

		if (val instanceof RuntimeConstant cv && lower instanceof RuntimeConstant cl
				&& upper instanceof RuntimeConstant cu) {
			if (cv.kind() == RuntimeConstantKind.NUMBER && cl.kind() == RuntimeConstantKind.NUMBER
					&& cu.kind() == RuntimeConstantKind.NUMBER) {
				try {
					BigDecimal nv = new BigDecimal(cv.value());
					BigDecimal nl = new BigDecimal(cl.value());
					BigDecimal nu = new BigDecimal(cu.value());
					boolean inRange = nv.compareTo(nl) >= 0 && nv.compareTo(nu) <= 0;
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(inRange), btn.type());
				} catch (Exception ignored) {
				}
			}
		}
		return new RuntimeBetweenExpression(val, lower, upper, btn.type());
	}

	private RuntimeExpression foldIn(RuntimeInExpression in) {
		RuntimeExpression val = transformExpression(in.value());
		List<RuntimeUnaryTest> newTests = new ArrayList<>();
		for (RuntimeUnaryTest t : in.tests().tests()) {
			if (t instanceof RuntimeExpressionUnaryTest eut) {
				RuntimeExpression folded = transformExpression(eut.expression());
				if (folded instanceof RuntimeRangeExpression rre) {
					newTests.add(new RuntimeRangeUnaryTest(rre));
				} else {
					newTests.add(new RuntimeExpressionUnaryTest(folded));
				}
			} else if (t instanceof RuntimeComparisonUnaryTest cut) {
				newTests.add(new RuntimeComparisonUnaryTest(cut.operator(), transformExpression(cut.endpoint())));
			} else if (t instanceof RuntimeRangeUnaryTest rut) {
				RuntimeRangeExpression r = (RuntimeRangeExpression) transformExpression(rut.range());
				newTests.add(new RuntimeRangeUnaryTest(r));
			} else {
				newTests.add(t);
			}
		}
		RuntimeUnaryTests tests = new RuntimeUnaryTests(in.tests().negated(), in.tests().wildcard(), newTests);
		return new RuntimeInExpression(val, tests, in.type());
	}

	private RuntimeExpression parseRangeConstant(String text, RuntimeType resultType) {
		String s = text.trim();
		boolean lowClosed = s.startsWith("[");
		boolean upClosed = s.endsWith("]");
		boolean lowOpen = s.startsWith("(") || s.startsWith("]");
		boolean upOpen = s.endsWith(")") || s.endsWith("[");
		if (s.length() < 2 || (!lowClosed && !lowOpen) || (!upClosed && !upOpen)) {
			return null;
		}
		String inner = s.substring(1, s.length() - 1);
		int dotdot = inner.indexOf("..");
		if (dotdot < 0) {
			return null;
		}
		String p0 = inner.substring(0, dotdot).trim();
		String p1 = inner.substring(dotdot + 2).trim();
		if (p0.isEmpty() && lowClosed)
			return null;
		if (p1.isEmpty() && upClosed)
			return null;
		if (p0.isEmpty() && p1.isEmpty())
			return null;

		RuntimeConstant lowConst = p0.isEmpty() ? null : parseRangeEndpointLiteral(p0);
		RuntimeConstant upConst = p1.isEmpty() ? null : parseRangeEndpointLiteral(p1);

		if (!p0.isEmpty() && lowConst == null)
			return null;
		if (!p1.isEmpty() && upConst == null)
			return null;
		if (lowConst == null && upConst == null)
			return null;

		if (lowConst != null && upConst != null) {
			if (lowConst.kind() != upConst.kind())
				return null;
			int cmp = compareRangeEndpoints(lowConst, upConst);
			if (cmp > 0)
				return null;
		}

		RuntimeRangeBoundary lb = lowClosed ? RuntimeRangeBoundary.CLOSED : RuntimeRangeBoundary.OPEN;
		RuntimeRangeBoundary ub = upClosed ? RuntimeRangeBoundary.CLOSED : RuntimeRangeBoundary.OPEN;
		return new RuntimeRangeExpression(Optional.ofNullable(lowConst), Optional.ofNullable(upConst), lb, ub,
				resultType != null ? resultType : RuntimeType.scalar(RuntimeTypeKind.RANGE));
	}

	private RuntimeConstant parseRangeEndpointLiteral(String t) {
		if (t == null || t.isBlank() || "null".equals(t))
			return null;
		if ("true".equalsIgnoreCase(t))
			return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true",
					RuntimeType.scalar(RuntimeTypeKind.BOOLEAN));
		if ("false".equalsIgnoreCase(t))
			return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false",
					RuntimeType.scalar(RuntimeTypeKind.BOOLEAN));
		if (t.startsWith("@\"") && t.endsWith("\"") && t.length() >= 3) {
			String raw = t.substring(2, t.length() - 1);
			if (raw.startsWith("P") || raw.startsWith("-P")) {
				return new RuntimeConstant(RuntimeConstantKind.DURATION, raw,
						RuntimeType.scalar(RuntimeTypeKind.DURATION));
			}
			if (raw.length() == 10 && raw.charAt(4) == '-' && raw.charAt(7) == '-') {
				return new RuntimeConstant(RuntimeConstantKind.DATE, raw, RuntimeType.scalar(RuntimeTypeKind.DATE));
			}
			if (raw.contains("T")) {
				return new RuntimeConstant(RuntimeConstantKind.DATE_TIME, raw,
						RuntimeType.scalar(RuntimeTypeKind.DATE_TIME));
			}
			if (raw.length() >= 8 && raw.charAt(2) == ':' && raw.charAt(5) == ':') {
				return new RuntimeConstant(RuntimeConstantKind.TIME, raw, RuntimeType.scalar(RuntimeTypeKind.TIME));
			}
			return new RuntimeConstant(RuntimeConstantKind.DATE_TIME, raw,
					RuntimeType.scalar(RuntimeTypeKind.DATE_TIME));
		}
		if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
			return new RuntimeConstant(RuntimeConstantKind.STRING, t.substring(1, t.length() - 1),
					RuntimeType.scalar(RuntimeTypeKind.STRING));
		}
		if (t.startsWith("date(\"") && t.endsWith("\")")) {
			String raw = t.substring(6, t.length() - 2);
			return new RuntimeConstant(RuntimeConstantKind.DATE, raw, RuntimeType.scalar(RuntimeTypeKind.DATE));
		}
		if ((t.startsWith("date and time(\"") || t.startsWith("dateTime(\"")) && t.endsWith("\")")) {
			int q = t.indexOf('\"');
			String raw = t.substring(q + 1, t.length() - 2);
			return new RuntimeConstant(RuntimeConstantKind.DATE_TIME, raw,
					RuntimeType.scalar(RuntimeTypeKind.DATE_TIME));
		}
		if (t.startsWith("time(\"") && t.endsWith("\")")) {
			String raw = t.substring(6, t.length() - 2);
			return new RuntimeConstant(RuntimeConstantKind.TIME, raw, RuntimeType.scalar(RuntimeTypeKind.TIME));
		}
		if ((t.startsWith("duration(\"") || t.startsWith("years and months duration(\"")
				|| t.startsWith("day and time duration(\"")) && t.endsWith("\")")) {
			int q = t.indexOf('\"');
			String raw = t.substring(q + 1, t.length() - 2);
			return new RuntimeConstant(RuntimeConstantKind.DURATION, raw, RuntimeType.scalar(RuntimeTypeKind.DURATION));
		}
		try {
			new BigDecimal(t);
			return new RuntimeConstant(RuntimeConstantKind.NUMBER, t, RuntimeType.scalar(RuntimeTypeKind.NUMBER));
		} catch (Exception ignored) {
		}
		return null;
	}

	private int compareRangeEndpoints(RuntimeConstant c1, RuntimeConstant c2) {
		if (c1.kind() == RuntimeConstantKind.NUMBER && c2.kind() == RuntimeConstantKind.NUMBER) {
			return new BigDecimal(c1.value()).compareTo(new BigDecimal(c2.value()));
		}
		if (c1.kind() == RuntimeConstantKind.STRING && c2.kind() == RuntimeConstantKind.STRING) {
			return c1.value().compareTo(c2.value());
		}
		if (c1.kind() == RuntimeConstantKind.DATE && c2.kind() == RuntimeConstantKind.DATE) {
			return LocalDate.parse(c1.value()).compareTo(LocalDate.parse(c2.value()));
		}
		return c1.value().compareTo(c2.value());
	}
}
