package io.finmsg.dmn.optimizer.pass;

import io.finmsg.dmn.ir.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
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
		return new RuntimeDecision(decision.id(), decision.resultSlot(), decision.type(), decision.dependencies(),
				newExpr, decision.decisionTable(), decision.localSlotCount());
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
			case RuntimeContextExpression ctx -> foldContext(ctx);
			case RuntimeFilterExpression filter -> foldFilter(filter);
			default -> expr;
		};
	}

	private RuntimeExpression foldUnary(RuntimeUnaryExpression unary) {
		RuntimeExpression sub = transformExpression(unary.operand());
		RuntimeUnaryOperator op = unary.operator();

		if (sub instanceof RuntimeConstant c) {
			if (op == RuntimeUnaryOperator.NEGATE && c.kind() == RuntimeConstantKind.NUMBER) {
				try {
					BigDecimal val = new BigDecimal(c.value()).negate();
					return new RuntimeConstant(RuntimeConstantKind.NUMBER, val.toPlainString(), unary.type());
				} catch (NumberFormatException ignored) {
				}
			} else if (op == RuntimeUnaryOperator.NOT && c.kind() == RuntimeConstantKind.BOOLEAN) {
				boolean val = Boolean.parseBoolean(c.value());
				return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, String.valueOf(!val), unary.type());
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
			if (left instanceof RuntimeConstant cl && cl.kind() == RuntimeConstantKind.BOOLEAN) {
				boolean val = Boolean.parseBoolean(cl.value());
				if (!val) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", binary.type());
				} else {
					return right;
				}
			}
			if (right instanceof RuntimeConstant cr && cr.kind() == RuntimeConstantKind.BOOLEAN) {
				boolean val = Boolean.parseBoolean(cr.value());
				if (!val) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", binary.type());
				} else {
					return left;
				}
			}
		} else if (op == RuntimeBinaryOperator.OR) {
			if (left instanceof RuntimeConstant cl && cl.kind() == RuntimeConstantKind.BOOLEAN) {
				boolean val = Boolean.parseBoolean(cl.value());
				if (val) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", binary.type());
				} else {
					return right;
				}
			}
			if (right instanceof RuntimeConstant cr && cr.kind() == RuntimeConstantKind.BOOLEAN) {
				boolean val = Boolean.parseBoolean(cr.value());
				if (val) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", binary.type());
				} else {
					return left;
				}
			}
		}

		// Both sides are constants
		if (left instanceof RuntimeConstant cl && right instanceof RuntimeConstant cr) {
			// String concatenation
			if (op == RuntimeBinaryOperator.ADD && cl.kind() == RuntimeConstantKind.STRING
					&& cr.kind() == RuntimeConstantKind.STRING) {
				return new RuntimeConstant(RuntimeConstantKind.STRING, cl.value() + cr.value(), binary.type());
			}

			// Numeric operations
			if (cl.kind() == RuntimeConstantKind.NUMBER && cr.kind() == RuntimeConstantKind.NUMBER) {
				try {
					BigDecimal n1 = new BigDecimal(cl.value());
					BigDecimal n2 = new BigDecimal(cr.value());

					switch (op) {
						case ADD -> {
							return new RuntimeConstant(RuntimeConstantKind.NUMBER, n1.add(n2).toPlainString(),
									binary.type());
						}
						case SUBTRACT -> {
							return new RuntimeConstant(RuntimeConstantKind.NUMBER, n1.subtract(n2).toPlainString(),
									binary.type());
						}
						case MULTIPLY -> {
							return new RuntimeConstant(RuntimeConstantKind.NUMBER, n1.multiply(n2).toPlainString(),
									binary.type());
						}
						case DIVIDE -> {
							if (n2.compareTo(BigDecimal.ZERO) != 0) {
								BigDecimal res = n1.divide(n2, 10, RoundingMode.HALF_UP).stripTrailingZeros();
								return new RuntimeConstant(RuntimeConstantKind.NUMBER, res.toPlainString(),
										binary.type());
							}
						}
						case POWER -> {
							int exp = n2.intValueExact();
							if (exp >= 0 && exp <= 100) {
								return new RuntimeConstant(RuntimeConstantKind.NUMBER, n1.pow(exp).toPlainString(),
										binary.type());
							}
						}
						case EQUAL -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) == 0), binary.type());
						}
						case NOT_EQUAL -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) != 0), binary.type());
						}
						case LESS -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) < 0), binary.type());
						}
						case LESS_EQUAL -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) <= 0), binary.type());
						}
						case GREATER -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) > 0), binary.type());
						}
						case GREATER_EQUAL -> {
							return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
									String.valueOf(n1.compareTo(n2) >= 0), binary.type());
						}
						default -> {
						}
					}
				} catch (Exception ignored) {
				}
			}

			// String equality
			if (cl.kind() == RuntimeConstantKind.STRING && cr.kind() == RuntimeConstantKind.STRING) {
				if (op == RuntimeBinaryOperator.EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(cl.value().equals(cr.value())), binary.type());
				} else if (op == RuntimeBinaryOperator.NOT_EQUAL) {
					return new RuntimeConstant(RuntimeConstantKind.BOOLEAN,
							String.valueOf(!cl.value().equals(cr.value())), binary.type());
				}
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
		if (args.size() == 1 && args.get(0) instanceof RuntimeConstant c) {
			if (name.equals("abs") && c.kind() == RuntimeConstantKind.NUMBER) {
				try {
					BigDecimal val = new BigDecimal(c.value()).abs();
					return new RuntimeConstant(RuntimeConstantKind.NUMBER, val.toPlainString(), call.type());
				} catch (Exception ignored) {
				}
			} else if (name.equals("upper case") && c.kind() == RuntimeConstantKind.STRING) {
				return new RuntimeConstant(RuntimeConstantKind.STRING, c.value().toUpperCase(), call.type());
			} else if (name.equals("lower case") && c.kind() == RuntimeConstantKind.STRING) {
				return new RuntimeConstant(RuntimeConstantKind.STRING, c.value().toLowerCase(), call.type());
			} else if (name.equals("string length") && c.kind() == RuntimeConstantKind.STRING) {
				return new RuntimeConstant(RuntimeConstantKind.NUMBER, String.valueOf(c.value().length()), call.type());
			}
		}

		return new RuntimeFunctionCall(call.function(), args, call.type());
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
		return new RuntimeFilterExpression(source, filter.localSlot(), predicate, filter.type());
	}
}
