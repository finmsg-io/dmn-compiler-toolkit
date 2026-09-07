package io.finmsg.dmn.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.optimizer.pass.AlgebraicSimplificationPass;
import io.finmsg.dmn.optimizer.pass.ConstantFoldingPass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DmnOptimizerTest {

	private static final RuntimeType NUMBER_TYPE = RuntimeType.scalar(RuntimeTypeKind.NUMBER);
	private static final RuntimeType STRING_TYPE = RuntimeType.scalar(RuntimeTypeKind.STRING);
	private static final RuntimeType BOOLEAN_TYPE = RuntimeType.scalar(RuntimeTypeKind.BOOLEAN);

	@Test
	@DisplayName("Constant folding: arithmetic addition 1 + 2 -> 3")
	void testConstantFoldingArithmeticAddition() {
		ConstantFoldingPass pass = new ConstantFoldingPass();

		RuntimeConstant c1 = new RuntimeConstant(RuntimeConstantKind.NUMBER, "1", NUMBER_TYPE);
		RuntimeConstant c2 = new RuntimeConstant(RuntimeConstantKind.NUMBER, "2", NUMBER_TYPE);
		RuntimeBinaryExpression expr = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, c1, c2, NUMBER_TYPE);

		RuntimeExpression result = pass.transformExpression(expr);
		assertThat(result).isInstanceOf(RuntimeConstant.class);

		RuntimeConstant folded = (RuntimeConstant) result;
		assertThat(folded.kind()).isEqualTo(RuntimeConstantKind.NUMBER);
		assertThat(folded.value()).isEqualTo("3");
	}

	@Test
	@DisplayName("Constant folding: string concatenation 'hello ' + 'world'")
	void testConstantFoldingStringConcat() {
		ConstantFoldingPass pass = new ConstantFoldingPass();

		RuntimeConstant c1 = new RuntimeConstant(RuntimeConstantKind.STRING, "hello ", STRING_TYPE);
		RuntimeConstant c2 = new RuntimeConstant(RuntimeConstantKind.STRING, "world", STRING_TYPE);
		RuntimeBinaryExpression expr = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, c1, c2, STRING_TYPE);

		RuntimeExpression result = pass.transformExpression(expr);
		assertThat(result).isInstanceOf(RuntimeConstant.class);

		RuntimeConstant folded = (RuntimeConstant) result;
		assertThat(folded.kind()).isEqualTo(RuntimeConstantKind.STRING);
		assertThat(folded.value()).isEqualTo("hello world");
	}

	@Test
	@DisplayName("Constant folding: boolean logic true and false -> false")
	void testConstantFoldingBooleanAnd() {
		ConstantFoldingPass pass = new ConstantFoldingPass();

		RuntimeConstant c1 = new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", BOOLEAN_TYPE);
		RuntimeConstant c2 = new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", BOOLEAN_TYPE);
		RuntimeBinaryExpression expr = new RuntimeBinaryExpression(RuntimeBinaryOperator.AND, c1, c2, BOOLEAN_TYPE);

		RuntimeExpression result = pass.transformExpression(expr);
		assertThat(result).isInstanceOf(RuntimeConstant.class);

		RuntimeConstant folded = (RuntimeConstant) result;
		assertThat(folded.kind()).isEqualTo(RuntimeConstantKind.BOOLEAN);
		assertThat(folded.value()).isEqualTo("false");
	}

	@Test
	@DisplayName("Constant folding: unary not(false) -> true")
	void testConstantFoldingUnaryNot() {
		ConstantFoldingPass pass = new ConstantFoldingPass();

		RuntimeConstant c1 = new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "false", BOOLEAN_TYPE);
		RuntimeUnaryExpression expr = new RuntimeUnaryExpression(RuntimeUnaryOperator.NOT, c1, BOOLEAN_TYPE);

		RuntimeExpression result = pass.transformExpression(expr);
		assertThat(result).isInstanceOf(RuntimeConstant.class);

		RuntimeConstant folded = (RuntimeConstant) result;
		assertThat(folded.kind()).isEqualTo(RuntimeConstantKind.BOOLEAN);
		assertThat(folded.value()).isEqualTo("true");
	}

	@Test
	@DisplayName("Algebraic simplification: x + 0 -> x")
	void testAlgebraicSimplificationAddZero() {
		AlgebraicSimplificationPass pass = new AlgebraicSimplificationPass();

		RuntimeLocalReference ref = new RuntimeLocalReference(0, 0, NUMBER_TYPE);
		RuntimeConstant zero = new RuntimeConstant(RuntimeConstantKind.NUMBER, "0", NUMBER_TYPE);
		RuntimeBinaryExpression expr = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, ref, zero, NUMBER_TYPE);

		RuntimeExpression result = pass.simplifyExpression(expr);
		assertThat(result).isEqualTo(ref);
	}

	@Test
	@DisplayName("Algebraic simplification: if true then A else B -> A")
	void testAlgebraicSimplificationConditionalTrue() {
		AlgebraicSimplificationPass pass = new AlgebraicSimplificationPass();

		RuntimeConstant trueCond = new RuntimeConstant(RuntimeConstantKind.BOOLEAN, "true", BOOLEAN_TYPE);
		RuntimeConstant thenExpr = new RuntimeConstant(RuntimeConstantKind.STRING, "A", STRING_TYPE);
		RuntimeConstant elseExpr = new RuntimeConstant(RuntimeConstantKind.STRING, "B", STRING_TYPE);
		RuntimeConditionalExpression cond = new RuntimeConditionalExpression(trueCond, thenExpr, elseExpr, STRING_TYPE);

		RuntimeExpression result = pass.simplifyExpression(cond);
		assertThat(result).isEqualTo(thenExpr);
	}

	@Test
	@DisplayName("Algebraic simplification: double negation not(not(x)) -> x")
	void testAlgebraicSimplificationDoubleNegation() {
		AlgebraicSimplificationPass pass = new AlgebraicSimplificationPass();

		RuntimeLocalReference ref = new RuntimeLocalReference(0, 0, BOOLEAN_TYPE);
		RuntimeUnaryExpression inner = new RuntimeUnaryExpression(RuntimeUnaryOperator.NOT, ref, BOOLEAN_TYPE);
		RuntimeUnaryExpression outer = new RuntimeUnaryExpression(RuntimeUnaryOperator.NOT, inner, BOOLEAN_TYPE);

		RuntimeExpression result = pass.simplifyExpression(outer);
		assertThat(result).isEqualTo(ref);
	}

	@Test
	@DisplayName("Algebraic simplification: x + 0.0 -> x and 0.00 + x -> x with decimal normalization")
	void testAlgebraicSimplificationDecimalZero() {
		AlgebraicSimplificationPass pass = new AlgebraicSimplificationPass();

		RuntimeLocalReference ref = new RuntimeLocalReference(0, 0, NUMBER_TYPE);
		RuntimeConstant zeroPointZero = new RuntimeConstant(RuntimeConstantKind.NUMBER, "0.00", NUMBER_TYPE);
		RuntimeBinaryExpression addRight = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, ref, zeroPointZero,
				NUMBER_TYPE);
		RuntimeBinaryExpression addLeft = new RuntimeBinaryExpression(RuntimeBinaryOperator.ADD, zeroPointZero, ref,
				NUMBER_TYPE);
		RuntimeBinaryExpression subRight = new RuntimeBinaryExpression(RuntimeBinaryOperator.SUBTRACT, ref,
				zeroPointZero, NUMBER_TYPE);

		assertThat(pass.simplifyExpression(addRight)).isEqualTo(ref);
		assertThat(pass.simplifyExpression(addLeft)).isEqualTo(ref);
		assertThat(pass.simplifyExpression(subRight)).isEqualTo(ref);
	}

	@Test
	@DisplayName("Algebraic simplification: x * 1.00 -> x and x / 1.0 -> x with decimal normalization")
	void testAlgebraicSimplificationDecimalOne() {
		AlgebraicSimplificationPass pass = new AlgebraicSimplificationPass();

		RuntimeLocalReference ref = new RuntimeLocalReference(0, 0, NUMBER_TYPE);
		RuntimeConstant onePointZero = new RuntimeConstant(RuntimeConstantKind.NUMBER, "1.000", NUMBER_TYPE);
		RuntimeBinaryExpression mulRight = new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY, ref,
				onePointZero, NUMBER_TYPE);
		RuntimeBinaryExpression mulLeft = new RuntimeBinaryExpression(RuntimeBinaryOperator.MULTIPLY, onePointZero, ref,
				NUMBER_TYPE);
		RuntimeBinaryExpression divRight = new RuntimeBinaryExpression(RuntimeBinaryOperator.DIVIDE, ref, onePointZero,
				NUMBER_TYPE);

		assertThat(pass.simplifyExpression(mulRight)).isEqualTo(ref);
		assertThat(pass.simplifyExpression(mulLeft)).isEqualTo(ref);
		assertThat(pass.simplifyExpression(divRight)).isEqualTo(ref);
	}

	@Test
	@DisplayName("Constant folding: division uses DECIMAL128 precision")
	void testConstantFoldingDivisionDecimal128() {
		ConstantFoldingPass pass = new ConstantFoldingPass();

		RuntimeConstant c1 = new RuntimeConstant(RuntimeConstantKind.NUMBER, "1", NUMBER_TYPE);
		RuntimeConstant c3 = new RuntimeConstant(RuntimeConstantKind.NUMBER, "3", NUMBER_TYPE);
		RuntimeBinaryExpression expr = new RuntimeBinaryExpression(RuntimeBinaryOperator.DIVIDE, c1, c3, NUMBER_TYPE);

		RuntimeExpression result = pass.transformExpression(expr);
		assertThat(result).isInstanceOf(RuntimeConstant.class);

		RuntimeConstant folded = (RuntimeConstant) result;
		assertThat(folded.kind()).isEqualTo(RuntimeConstantKind.NUMBER);
		assertThat(folded.value()).startsWith("0.3333333333333333333333333333333333");
	}

	@Test
	@DisplayName("Constant folding: between expression 5 between 1 and 10 -> true, 15 between 1 and 10 -> false")
	void testConstantFoldingBetween() {
		ConstantFoldingPass pass = new ConstantFoldingPass();

		RuntimeConstant c5 = new RuntimeConstant(RuntimeConstantKind.NUMBER, "5", NUMBER_TYPE);
		RuntimeConstant c15 = new RuntimeConstant(RuntimeConstantKind.NUMBER, "15", NUMBER_TYPE);
		RuntimeConstant lower = new RuntimeConstant(RuntimeConstantKind.NUMBER, "1", NUMBER_TYPE);
		RuntimeConstant upper = new RuntimeConstant(RuntimeConstantKind.NUMBER, "10", NUMBER_TYPE);

		RuntimeBetweenExpression inRange = new RuntimeBetweenExpression(c5, lower, upper, BOOLEAN_TYPE);
		RuntimeBetweenExpression outOfRange = new RuntimeBetweenExpression(c15, lower, upper, BOOLEAN_TYPE);

		RuntimeExpression resTrue = pass.transformExpression(inRange);
		assertThat(resTrue).isInstanceOf(RuntimeConstant.class);
		assertThat(((RuntimeConstant) resTrue).value()).isEqualTo("true");

		RuntimeExpression resFalse = pass.transformExpression(outOfRange);
		assertThat(resFalse).isInstanceOf(RuntimeConstant.class);
		assertThat(((RuntimeConstant) resFalse).value()).isEqualTo("false");
	}
}
