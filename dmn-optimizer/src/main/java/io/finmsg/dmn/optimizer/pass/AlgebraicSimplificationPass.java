package io.finmsg.dmn.optimizer.pass;

import io.finmsg.dmn.ir.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Optimization pass applying algebraic identity rewrites and branch
 * eliminations.
 */
public class AlgebraicSimplificationPass implements OptimizerPass {

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
		Optional<RuntimeExpression> newExpr = decision.expression().map(this::simplifyExpression);
		return new RuntimeDecision(decision.id(), decision.resultSlot(), decision.type(), decision.dependencies(),
				newExpr, decision.decisionTable(), decision.localSlotCount());
	}

	private RuntimeBkm transformBkm(RuntimeBkm bkm) {
		Optional<RuntimeFunctionDefinition> newFunc = bkm.function().map(this::simplifyFunctionDefinition);
		return new RuntimeBkm(bkm.id(), bkm.resultSlot(), bkm.type(), bkm.dependencies(), bkm.functionKind(), newFunc);
	}

	private RuntimeFunctionDefinition simplifyFunctionDefinition(RuntimeFunctionDefinition func) {
		Optional<RuntimeExpression> newBody = func.body().map(this::simplifyExpression);
		return new RuntimeFunctionDefinition(func.parameters(), newBody, func.external(), func.localSlotCount(),
				func.type());
	}

	public RuntimeExpression simplifyExpression(RuntimeExpression expr) {
		if (expr == null) {
			return null;
		}

		return switch (expr) {
			case RuntimeUnaryExpression unary -> simplifyUnary(unary);
			case RuntimeBinaryExpression binary -> simplifyBinary(binary);
			case RuntimeConditionalExpression cond -> simplifyConditional(cond);
			case RuntimeListExpression list -> simplifyList(list);
			case RuntimeFunctionCall call -> simplifyFunctionCall(call);
			case RuntimeContextExpression ctx -> simplifyContext(ctx);
			case RuntimeFilterExpression filter -> simplifyFilter(filter);
			case RuntimeBetweenExpression btn -> new RuntimeBetweenExpression(simplifyExpression(btn.value()),
					simplifyExpression(btn.lower()), simplifyExpression(btn.upper()), btn.type());
			case RuntimeRangeExpression range -> new RuntimeRangeExpression(range.lower().map(this::simplifyExpression),
					range.upper().map(this::simplifyExpression), range.lowerBoundary(), range.upperBoundary(),
					range.type());
			case RuntimePathExpression path ->
				new RuntimePathExpression(simplifyExpression(path.source()), path.member(), path.type());
			case RuntimeInstanceOfExpression inst ->
				new RuntimeInstanceOfExpression(simplifyExpression(inst.expression()), inst.testedType(), inst.type());
			default -> expr;
		};
	}

	private RuntimeExpression simplifyUnary(RuntimeUnaryExpression unary) {
		RuntimeExpression sub = simplifyExpression(unary.operand());
		RuntimeUnaryOperator op = unary.operator();

		// Double negation: not(not(x)) -> x
		if (op == RuntimeUnaryOperator.NOT && sub instanceof RuntimeUnaryExpression inner) {
			if (inner.operator() == RuntimeUnaryOperator.NOT) {
				return inner.operand();
			}
		}

		return new RuntimeUnaryExpression(op, sub, unary.type());
	}

	private RuntimeExpression simplifyBinary(RuntimeBinaryExpression binary) {
		RuntimeExpression left = simplifyExpression(binary.left());
		RuntimeExpression right = simplifyExpression(binary.right());
		RuntimeBinaryOperator op = binary.operator();

		// Additive identity: x + 0 -> x, 0 + x -> x
		if (op == RuntimeBinaryOperator.ADD) {
			if (isZeroConstant(right)) {
				return left;
			}
			if (isZeroConstant(left)) {
				return right;
			}
		}

		// Subtractive identity: x - 0 -> x
		if (op == RuntimeBinaryOperator.SUBTRACT) {
			if (isZeroConstant(right)) {
				return left;
			}
		}

		// Multiplicative identity: x * 1 -> x, 1 * x -> x
		if (op == RuntimeBinaryOperator.MULTIPLY) {
			if (isOneConstant(right)) {
				return left;
			}
			if (isOneConstant(left)) {
				return right;
			}
		}

		// Division identity: x / 1 -> x
		if (op == RuntimeBinaryOperator.DIVIDE) {
			if (isOneConstant(right)) {
				return left;
			}
		}

		return new RuntimeBinaryExpression(op, left, right, binary.type());
	}

	private RuntimeExpression simplifyConditional(RuntimeConditionalExpression cond) {
		RuntimeExpression condition = simplifyExpression(cond.condition());
		RuntimeExpression thenExpr = simplifyExpression(cond.thenExpression());
		RuntimeExpression elseExpr = simplifyExpression(cond.elseExpression());

		if (condition instanceof RuntimeConstant c && c.kind() == RuntimeConstantKind.BOOLEAN) {
			boolean val = Boolean.parseBoolean(c.value());
			return val ? thenExpr : elseExpr;
		}

		return new RuntimeConditionalExpression(condition, thenExpr, elseExpr, cond.type());
	}

	private RuntimeExpression simplifyList(RuntimeListExpression list) {
		List<RuntimeExpression> elements = new ArrayList<>();
		for (RuntimeExpression e : list.elements()) {
			elements.add(simplifyExpression(e));
		}
		return new RuntimeListExpression(elements, list.type());
	}

	private RuntimeExpression simplifyFunctionCall(RuntimeFunctionCall call) {
		List<RuntimeExpression> args = new ArrayList<>();
		for (RuntimeExpression a : call.arguments()) {
			args.add(simplifyExpression(a));
		}
		return new RuntimeFunctionCall(call.function(), args, call.type());
	}

	private RuntimeExpression simplifyContext(RuntimeContextExpression ctx) {
		List<RuntimeContextEntry> entries = new ArrayList<>();
		for (RuntimeContextEntry entry : ctx.entries()) {
			entries.add(
					new RuntimeContextEntry(entry.name(), entry.localSlot(), simplifyExpression(entry.expression())));
		}
		return new RuntimeContextExpression(entries, ctx.type());
	}

	private RuntimeExpression simplifyFilter(RuntimeFilterExpression filter) {
		RuntimeExpression source = simplifyExpression(filter.source());
		RuntimeExpression predicate = simplifyExpression(filter.filter());
		return new RuntimeFilterExpression(source, filter.localSlot(), predicate, filter.type());
	}

	private boolean isZeroConstant(RuntimeExpression expr) {
		if (expr instanceof RuntimeConstant c && c.kind() == RuntimeConstantKind.NUMBER && c.value() != null) {
			try {
				return new java.math.BigDecimal(c.value()).compareTo(java.math.BigDecimal.ZERO) == 0;
			} catch (Exception ignored) {
				return "0".equals(c.value());
			}
		}
		return false;
	}

	private boolean isOneConstant(RuntimeExpression expr) {
		if (expr instanceof RuntimeConstant c && c.kind() == RuntimeConstantKind.NUMBER && c.value() != null) {
			try {
				return new java.math.BigDecimal(c.value()).compareTo(java.math.BigDecimal.ONE) == 0;
			} catch (Exception ignored) {
				return "1".equals(c.value());
			}
		}
		return false;
	}
}
