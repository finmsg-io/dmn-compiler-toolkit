package io.finmsg.dmn.feel.parser;

import io.finmsg.dmn.model.BetweenExpression;
import io.finmsg.dmn.model.BinaryExpression;
import io.finmsg.dmn.model.BinaryOperator;
import io.finmsg.dmn.model.ComparisonUnaryTest;
import io.finmsg.dmn.model.ContextExpression;
import io.finmsg.dmn.model.ContextType;
import io.finmsg.dmn.model.ContextTypeEntry;
import io.finmsg.dmn.model.DescendantExpression;
import io.finmsg.dmn.model.Expression;
import io.finmsg.dmn.model.FeelParsed;
import io.finmsg.dmn.model.FeelType;
import io.finmsg.dmn.model.FilterExpression;
import io.finmsg.dmn.model.ForExpression;
import io.finmsg.dmn.model.FormalParameter;
import io.finmsg.dmn.model.FunctionDefinitionExpression;
import io.finmsg.dmn.model.FunctionType;
import io.finmsg.dmn.model.IfExpression;
import io.finmsg.dmn.model.InExpression;
import io.finmsg.dmn.model.InstanceOfExpression;
import io.finmsg.dmn.model.InvocationExpression;
import io.finmsg.dmn.model.IterationBinding;
import io.finmsg.dmn.model.IterationContext;
import io.finmsg.dmn.model.ListExpression;
import io.finmsg.dmn.model.ListType;
import io.finmsg.dmn.model.LiteralExpression;
import io.finmsg.dmn.model.LiteralKind;
import io.finmsg.dmn.model.NameExpression;
import io.finmsg.dmn.model.NamedArgument;
import io.finmsg.dmn.model.PathExpression;
import io.finmsg.dmn.model.PositiveUnaryTest;
import io.finmsg.dmn.model.QuantifiedExpression;
import io.finmsg.dmn.model.Quantifier;
import io.finmsg.dmn.model.RangeBoundary;
import io.finmsg.dmn.model.RangeExpression;
import io.finmsg.dmn.model.RangeType;
import io.finmsg.dmn.model.UnaryExpression;
import io.finmsg.dmn.model.UnaryOperator;
import io.finmsg.dmn.model.UnaryTestOperator;
import io.finmsg.dmn.model.UnaryTestParsed;
import io.finmsg.dmn.model.UnaryTestsExpression;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts validated ANTLR FEEL parse trees to the parser-independent protobuf
 * AST.
 */
public final class FeelAstBuilder {

	private final java.util.Set<String> declaredNames;

	public FeelAstBuilder() {
		this(java.util.Set.of());
	}

	public FeelAstBuilder(java.util.Set<String> declaredNames) {
		this.declaredNames = declaredNames != null ? declaredNames : java.util.Set.of();
	}

	public FeelParsed build(FeelParser.ExpressionRootContext root) {
		Objects.requireNonNull(root, "root");
		return FeelParsed.newBuilder().setAst(expression(root.expression())).build();
	}

	public UnaryTestParsed build(FeelParser.UnaryTestsRootContext root) {
		Objects.requireNonNull(root, "root");
		return UnaryTestParsed.newBuilder().setTests(unaryTests(root.unaryTests())).build();
	}

	private Expression expression(FeelParser.ExpressionContext context) {
		return textualExpression(context.textualExpression());
	}

	private Expression textualExpression(FeelParser.TextualExpressionContext context) {
		if (context.negatedUnaryTests() != null) {
			return negatedUnaryTestsExpression(context.negatedUnaryTests());
		}
		if (context.forExpression() != null) {
			return forExpression(context.forExpression());
		}
		if (context.ifExpression() != null) {
			return ifExpression(context.ifExpression());
		}
		if (context.quantifiedExpression() != null) {
			return quantifiedExpression(context.quantifiedExpression());
		}
		return disjunction(context.disjunction());
	}

	private Expression forExpression(FeelParser.ForExpressionContext context) {
		ForExpression.Builder builder = ForExpression.newBuilder();
		for (FeelParser.IterationContextContext iteration : context.iterationContext()) {
			IterationContext.Builder parsed = IterationContext.newBuilder().setVariable(name(iteration.name()))
					.setStart(expression(iteration.expression(0)));
			if (iteration.expression().size() == 2) {
				parsed.setEnd(expression(iteration.expression(1)));
			}
			builder.addIterations(parsed);
		}
		builder.setReturnExpression(expression(context.expression()));
		return Expression.newBuilder().setForExpression(builder).build();
	}

	private Expression ifExpression(FeelParser.IfExpressionContext context) {
		return Expression.newBuilder()
				.setIfExpression(IfExpression.newBuilder().setCondition(expression(context.expression(0)))
						.setThenExpression(expression(context.expression(1)))
						.setElseExpression(expression(context.expression(2))))
				.build();
	}

	private Expression quantifiedExpression(FeelParser.QuantifiedExpressionContext context) {
		QuantifiedExpression.Builder builder = QuantifiedExpression.newBuilder()
				.setQuantifier(context.SOME() != null ? Quantifier.QUANTIFIER_SOME : Quantifier.QUANTIFIER_EVERY);

		for (FeelParser.IterationBindingContext binding : context.iterationBinding()) {
			builder.addBindings(IterationBinding.newBuilder().setVariable(name(binding.name()))
					.setIn(expression(binding.expression())));
		}
		builder.setSatisfies(expression(context.expression()));
		return Expression.newBuilder().setQuantified(builder).build();
	}

	private Expression disjunction(FeelParser.DisjunctionContext context) {
		List<FeelParser.ConjunctionContext> operands = context.conjunction();
		Expression result = conjunction(operands.get(0));
		for (int i = 1; i < operands.size(); i++) {
			result = binary(BinaryOperator.BINARY_OPERATOR_OR, result, conjunction(operands.get(i)));
		}
		return result;
	}

	private Expression conjunction(FeelParser.ConjunctionContext context) {
		List<FeelParser.ComparisonContext> operands = context.comparison();
		Expression result = comparison(operands.get(0));
		for (int i = 1; i < operands.size(); i++) {
			result = binary(BinaryOperator.BINARY_OPERATOR_AND, result, comparison(operands.get(i)));
		}
		return result;
	}

	private Expression comparison(FeelParser.ComparisonContext context) {
		Expression value = additive(context.additiveExpression());
		FeelParser.ComparisonSuffixContext suffix = context.comparisonSuffix();
		if (suffix == null) {
			return value;
		}

		if (suffix.comparisonOperator() != null) {
			return binary(binaryComparisonOperator(suffix.comparisonOperator()), value,
					additive(suffix.additiveExpression()));
		}

		if (suffix.BETWEEN() != null) {
			return Expression.newBuilder()
					.setBetween(BetweenExpression.newBuilder().setValue(value)
							.setLower(expression(suffix.expression(0))).setUpper(expression(suffix.expression(1))))
					.build();
		}

		UnaryTestsExpression.Builder tests = UnaryTestsExpression.newBuilder();
		if (suffix.positiveUnaryTest() != null) {
			PositiveUnaryTest put = positiveUnaryTest(suffix.positiveUnaryTest());
			if (value.hasName() && put.hasExpression() && put.getExpression().hasName()) {
				String candidate = value.getName().getName() + " in " + put.getExpression().getName().getName();
				if (declaredNames.contains(candidate)) {
					return Expression.newBuilder().setName(NameExpression.newBuilder().setName(candidate)).build();
				}
			}
			tests.addTests(put);
		} else {
			tests.addAllTests(positiveUnaryTests(suffix.positiveUnaryTests()));
		}
		return Expression.newBuilder().setIn(InExpression.newBuilder().setValue(value).setTests(tests)).build();
	}

	private Expression additive(FeelParser.AdditiveExpressionContext context) {
		List<FeelParser.MultiplicativeExpressionContext> operands = context.multiplicativeExpression();
		List<Expression> exprs = new ArrayList<>();
		for (FeelParser.MultiplicativeExpressionContext op : operands) {
			exprs.add(multiplicative(op));
		}
		List<String> ops = new ArrayList<>();
		for (int i = 1; i < operands.size(); i++) {
			ops.add(context.getChild((i * 2) - 1).getText());
		}

		int i = 0;
		while (i < ops.size()) {
			if ("-".equals(ops.get(i))) {
				Expression left = exprs.get(i);
				Expression right = exprs.get(i + 1);
				if (left.hasName() && right.hasName()) {
					String candidate = left.getName().getName() + "-" + right.getName().getName();
					if (declaredNames.contains(candidate)) {
						exprs.set(i, Expression.newBuilder()
								.setName(NameExpression.newBuilder().setName(candidate)).build());
						exprs.remove(i + 1);
						ops.remove(i);
						continue;
					}
				} else if (left.hasName() && right.hasPath() && right.getPath().getSource().hasName()) {
					String candidate = left.getName().getName() + "-" + right.getPath().getSource().getName().getName();
					if (declaredNames.contains(candidate)) {
						exprs.set(i, Expression.newBuilder().setPath(PathExpression.newBuilder()
								.setSource(Expression.newBuilder().setName(NameExpression.newBuilder().setName(candidate)))
								.setMember(right.getPath().getMember())).build());
						exprs.remove(i + 1);
						ops.remove(i);
						continue;
					}
				}
			}
			i++;
		}

		Expression result = exprs.get(0);
		for (int j = 0; j < ops.size(); j++) {
			String operator = ops.get(j);
			Expression right = exprs.get(j + 1);
			result = binary(
					"+".equals(operator) ? BinaryOperator.BINARY_OPERATOR_ADD : BinaryOperator.BINARY_OPERATOR_SUBTRACT,
					result, right);
		}
		return result;
	}

	private Expression multiplicative(FeelParser.MultiplicativeExpressionContext context) {
		List<FeelParser.ExponentiationExpressionContext> operands = context.exponentiationExpression();
		Expression result = exponentiation(operands.get(0));
		for (int i = 1; i < operands.size(); i++) {
			String operator = context.getChild((i * 2) - 1).getText();
			result = binary("*".equals(operator)
					? BinaryOperator.BINARY_OPERATOR_MULTIPLY
					: BinaryOperator.BINARY_OPERATOR_DIVIDE, result, exponentiation(operands.get(i)));
		}
		return result;
	}

	private Expression exponentiation(FeelParser.ExponentiationExpressionContext context) {
		List<FeelParser.ArithmeticNegationContext> operands = context.arithmeticNegation();
		Expression result = arithmeticNegation(operands.get(0));
		for (int i = 1; i < operands.size(); i++) {
			result = binary(BinaryOperator.BINARY_OPERATOR_POWER, result, arithmeticNegation(operands.get(i)));
		}
		return result;
	}

	private Expression arithmeticNegation(FeelParser.ArithmeticNegationContext context) {
		Expression result = instanceOf(context.instanceOfExpression());
		for (int i = 0; i < context.MINUS().size(); i++) {
			result = Expression.newBuilder().setUnary(
					UnaryExpression.newBuilder().setOperator(UnaryOperator.UNARY_OPERATOR_MINUS).setExpression(result))
					.build();
		}
		return result;
	}

	private Expression instanceOf(FeelParser.InstanceOfExpressionContext context) {
		Expression value = postfix(context.postfixExpression());
		if (context.type() == null) {
			return value;
		}
		return Expression.newBuilder()
				.setInstanceOf(InstanceOfExpression.newBuilder().setExpression(value).setType(type(context.type())))
				.build();
	}

	private Expression postfix(FeelParser.PostfixExpressionContext context) {
		Expression result = primary(context.primaryExpression());
		for (FeelParser.PostfixPartContext part : context.postfixPart()) {
			if (part.parameters() != null) {
				result = invocation(result, part.parameters());
			} else if (part.LBRACKET() != null) {
				result = Expression.newBuilder().setFilter(
						FilterExpression.newBuilder().setSource(result).setFilter(expression(part.expression())))
						.build();
			} else if (part.ELLIPSIS() != null) {
				result = Expression.newBuilder()
						.setDescendant(DescendantExpression.newBuilder().setSource(result).setMember(name(part.name())))
						.build();
			} else {
				result = Expression.newBuilder()
						.setPath(PathExpression.newBuilder().setSource(result).setMember(name(part.name()))).build();
			}
		}
		return result;
	}

	private Expression invocation(Expression target, FeelParser.ParametersContext context) {
		InvocationExpression.Builder builder = InvocationExpression.newBuilder().setTarget(target);
		if (context.namedParameters() != null) {
			for (FeelParser.NamedParameterContext parameter : context.namedParameters().namedParameter()) {
				builder.addArguments(NamedArgument.newBuilder().setName(parameter.parameterName().getText())
						.setExpression(expression(parameter.expression())));
			}
		} else if (context.positionalParameters() != null) {
			for (FeelParser.ExpressionContext argument : context.positionalParameters().expression()) {
				builder.addPositionalArguments(expression(argument));
			}
		}
		return Expression.newBuilder().setInvocation(builder).build();
	}

	private Expression primary(FeelParser.PrimaryExpressionContext context) {
		if (context.literal() != null) {
			return literal(context.literal());
		}
		if (context.rangeLiteral() != null) {
			return Expression.newBuilder().setRange(range(context.rangeLiteral())).build();
		}
		if (context.interval() != null) {
			return Expression.newBuilder().setRange(range(context.interval())).build();
		}
		if (context.list() != null) {
			ListExpression.Builder builder = ListExpression.newBuilder();
			for (FeelParser.ExpressionContext element : context.list().expression()) {
				builder.addElements(expression(element));
			}
			return Expression.newBuilder().setList(builder).build();
		}
		if (context.context() != null) {
			ContextExpression.Builder builder = ContextExpression.newBuilder();
			for (FeelParser.ContextEntryContext entry : context.context().contextEntry()) {
				String key = key(entry.key());
				builder.addEntries(io.finmsg.dmn.model.ContextEntry.newBuilder().setName(key)
						.setExpression(expression(entry.expression())));
			}
			return Expression.newBuilder().setContext(builder).build();
		}
		if (context.functionDefinition() != null) {
			return functionDefinition(context.functionDefinition());
		}
		if (context.negatedUnaryTests() != null) {
			return negatedUnaryTestsExpression(context.negatedUnaryTests());
		}
		if (context.expression() != null) {
			return expression(context.expression());
		}
		return Expression.newBuilder().setName(NameExpression.newBuilder().setName(name(context.name()))).build();
	}

	private Expression literal(FeelParser.LiteralContext context) {
		LiteralExpression.Builder literal = LiteralExpression.newBuilder();
		if (context.NUMBER_LITERAL() != null) {
			literal.setKind(LiteralKind.LITERAL_KIND_NUMBER).setValue(context.NUMBER_LITERAL().getText());
		} else if (context.STRING_LITERAL() != null) {
			literal.setKind(LiteralKind.LITERAL_KIND_STRING).setValue(decodeString(context.STRING_LITERAL().getText()));
		} else if (context.TRUE() != null || context.FALSE() != null) {
			literal.setKind(LiteralKind.LITERAL_KIND_BOOLEAN).setValue(context.getText());
		} else if (context.NULL() != null) {
			literal.setKind(LiteralKind.LITERAL_KIND_NULL).setValue("null");
		} else {
			String value = decodeString(context.atLiteral().STRING_LITERAL().getText());
			literal.setKind(atLiteralKind(value)).setValue(value);
		}
		return Expression.newBuilder().setLiteral(literal).build();
	}

	private LiteralKind atLiteralKind(String value) {
		if (value.startsWith("P") || value.startsWith("-P")) {
			return LiteralKind.LITERAL_KIND_DURATION;
		}
		String temporalValue = value.substring(0, value.indexOf('@') >= 0 ? value.indexOf('@') : value.length());
		if (temporalValue.contains("T")) {
			return LiteralKind.LITERAL_KIND_DATE_TIME;
		}
		if (value.indexOf(':') >= 0) {
			return LiteralKind.LITERAL_KIND_TIME;
		}
		return LiteralKind.LITERAL_KIND_DATE;
	}

	private Expression functionDefinition(FeelParser.FunctionDefinitionContext context) {
		FunctionDefinitionExpression.Builder builder = FunctionDefinitionExpression.newBuilder()
				.setExternal(context.EXTERNAL() != null);
		if (context.formalParameters() != null) {
			for (FeelParser.FormalParameterContext parameter : context.formalParameters().formalParameter()) {
				FormalParameter.Builder parsed = FormalParameter.newBuilder()
						.setName(parameter.parameterName().getText());
				if (parameter.type() != null) {
					parsed.setType(type(parameter.type()));
				}
				builder.addParameters(parsed);
			}
		}
		builder.setBody(expression(context.expression()));
		return Expression.newBuilder().setFunctionDefinition(builder).build();
	}

	private RangeExpression range(FeelParser.IntervalContext context) {
		return RangeExpression.newBuilder().setLower(expression(context.endpoint(0).expression()))
				.setUpper(expression(context.endpoint(1).expression()))
				.setLowerBoundary(startBoundary(context.intervalStart().getText()))
				.setUpperBoundary(endBoundary(context.intervalEnd().getText())).build();
	}

	private RangeExpression range(FeelParser.RangeLiteralContext context) {
		if (context.LPAREN() != null && context.endpoint().size() == 1) {
			Expression endExpr = expression(context.endpoint(0).expression());
			if (context.comparisonOperator() != null) {
				FeelParser.ComparisonOperatorContext op = context.comparisonOperator();
				if (op.LT() != null) {
					return RangeExpression.newBuilder().setUpper(endExpr)
							.setLowerBoundary(RangeBoundary.RANGE_BOUNDARY_OPEN)
							.setUpperBoundary(RangeBoundary.RANGE_BOUNDARY_OPEN).build();
				}
				if (op.LE() != null) {
					return RangeExpression.newBuilder().setUpper(endExpr)
							.setLowerBoundary(RangeBoundary.RANGE_BOUNDARY_OPEN)
							.setUpperBoundary(RangeBoundary.RANGE_BOUNDARY_CLOSED).build();
				}
				if (op.GT() != null) {
					return RangeExpression.newBuilder().setLower(endExpr)
							.setLowerBoundary(RangeBoundary.RANGE_BOUNDARY_OPEN)
							.setUpperBoundary(RangeBoundary.RANGE_BOUNDARY_OPEN).build();
				}
				if (op.GE() != null) {
					return RangeExpression.newBuilder().setLower(endExpr)
							.setLowerBoundary(RangeBoundary.RANGE_BOUNDARY_CLOSED)
							.setUpperBoundary(RangeBoundary.RANGE_BOUNDARY_OPEN).build();
				}
				if (op.EQ() != null) {
					return RangeExpression.newBuilder().setLower(endExpr).setUpper(endExpr)
							.setLowerBoundary(RangeBoundary.RANGE_BOUNDARY_CLOSED)
							.setUpperBoundary(RangeBoundary.RANGE_BOUNDARY_CLOSED).build();
				}
				if (op.NE() != null) {
					return RangeExpression.newBuilder().setLower(endExpr).setUpper(endExpr)
							.setLowerBoundary(RangeBoundary.RANGE_BOUNDARY_OPEN)
							.setUpperBoundary(RangeBoundary.RANGE_BOUNDARY_OPEN).build();
				}
			}
		}
		if (context.intervalStart() == null) {
			return RangeExpression.getDefaultInstance();
		}
		RangeExpression.Builder builder = RangeExpression.newBuilder()
				.setLowerBoundary(startBoundary(context.intervalStart().getText()))
				.setUpperBoundary(endBoundary(context.intervalEnd() != null
						? context.intervalEnd().getText()
						: context.openIntervalEnd().getText()));
		List<FeelParser.EndpointContext> endpoints = context.endpoint();
		boolean missingLower = "..".equals(context.getChild(1).getText());
		if (endpoints.size() == 2) {
			builder.setLower(expression(endpoints.get(0).expression()))
					.setUpper(expression(endpoints.get(1).expression()));
		} else if (missingLower) {
			builder.setUpper(expression(endpoints.get(0).expression()));
		} else {
			builder.setLower(expression(endpoints.get(0).expression()));
		}
		return builder.build();
	}

	private RangeBoundary startBoundary(String delimiter) {
		return "[".equals(delimiter)
				? RangeBoundary.RANGE_BOUNDARY_CLOSED
				: RangeBoundary.RANGE_BOUNDARY_OPEN;
	}

	private RangeBoundary endBoundary(String delimiter) {
		return "]".equals(delimiter)
				? RangeBoundary.RANGE_BOUNDARY_CLOSED
				: RangeBoundary.RANGE_BOUNDARY_OPEN;
	}

	private UnaryTestsExpression unaryTests(FeelParser.UnaryTestsContext context) {
		if ("-".equals(context.getText())) {
			return UnaryTestsExpression.newBuilder().setWildcard(true).build();
		}
		if (context.negatedUnaryTests() != null) {
			return negatedUnaryTests(context.negatedUnaryTests());
		}
		return UnaryTestsExpression.newBuilder().addAllTests(positiveUnaryTests(context.positiveUnaryTests())).build();
	}

	private UnaryTestsExpression negatedUnaryTests(FeelParser.NegatedUnaryTestsContext context) {
		return UnaryTestsExpression.newBuilder().setNegated(true)
				.addAllTests(positiveUnaryTests(context.positiveUnaryTests())).build();
	}

	private Expression negatedUnaryTestsExpression(FeelParser.NegatedUnaryTestsContext context) {
		List<FeelParser.PositiveUnaryTestContext> tests = context.positiveUnaryTests().positiveUnaryTest();
		boolean functionInvocation = tests.stream().allMatch(test -> test.expression() != null);

		if (functionInvocation) {
			InvocationExpression.Builder invocation = InvocationExpression.newBuilder()
					.setTarget(Expression.newBuilder().setName(NameExpression.newBuilder().setName("not")));
			for (FeelParser.PositiveUnaryTestContext test : tests) {
				invocation.addPositionalArguments(expression(test.expression()));
			}
			return Expression.newBuilder().setInvocation(invocation).build();
		}

		return Expression.newBuilder().setUnaryTests(negatedUnaryTests(context)).build();
	}

	private List<PositiveUnaryTest> positiveUnaryTests(FeelParser.PositiveUnaryTestsContext context) {
		return context.positiveUnaryTest().stream().map(this::positiveUnaryTest).toList();
	}

	private PositiveUnaryTest positiveUnaryTest(FeelParser.PositiveUnaryTestContext context) {
		if (context.simplePositiveUnaryTest() != null) {
			FeelParser.SimplePositiveUnaryTestContext simple = context.simplePositiveUnaryTest();
			if (simple.comparisonOperator() != null) {
				return PositiveUnaryTest.newBuilder()
						.setComparison(ComparisonUnaryTest.newBuilder()
								.setOperator(unaryTestOperator(simple.comparisonOperator()))
								.setEndpoint(expression(simple.endpoint().expression())))
						.build();
			}
			if (simple.rangeLiteral() != null && simple.rangeLiteral().comparisonOperator() != null) {
				return PositiveUnaryTest.newBuilder()
						.setComparison(ComparisonUnaryTest.newBuilder()
								.setOperator(unaryTestOperator(simple.rangeLiteral().comparisonOperator()))
								.setEndpoint(expression(simple.rangeLiteral().endpoint(0).expression())))
						.build();
			}
			return PositiveUnaryTest.newBuilder()
					.setRange(simple.interval() != null ? range(simple.interval()) : range(simple.rangeLiteral()))
					.build();
		}

		Expression parsed = expression(context.expression());
		if (parsed.hasRange()) {
			return PositiveUnaryTest.newBuilder().setRange(parsed.getRange()).build();
		}
		if (parsed.hasUnaryTests()) {
			if (parsed.getUnaryTests().getTestsCount() == 1) {
				return parsed.getUnaryTests().getTests(0);
			}
		}
		return PositiveUnaryTest.newBuilder().setExpression(parsed).build();
	}

	private FeelType type(FeelParser.TypeContext context) {
		FeelType.Builder builder = FeelType.newBuilder();
		if (context.qualifiedName() != null) {
			return builder.setQualifiedName(context.qualifiedName().name().stream().map(FeelAstBuilder::name)
					.collect(java.util.stream.Collectors.joining("."))).build();
		}
		if (context.RANGE() != null) {
			return builder.setRange(RangeType.newBuilder().setElementType(type(context.type()))).build();
		}
		if (context.LIST() != null) {
			return builder.setList(ListType.newBuilder().setElementType(type(context.type()))).build();
		}
		if (context.CONTEXT() != null) {
			ContextType.Builder parsed = ContextType.newBuilder();
			for (FeelParser.ContextTypeEntryContext entry : context.contextTypeEntry()) {
				parsed.addEntries(
						ContextTypeEntry.newBuilder().setName(name(entry.name())).setType(type(entry.type())));
			}
			return builder.setContext(parsed).build();
		}

		FunctionType.Builder parsed = FunctionType.newBuilder();
		if (context.typeList() != null) {
			context.typeList().type().stream().map(this::type).forEach(parsed::addParameterTypes);
		}
		parsed.setReturnType(type(context.type()));
		return builder.setFunction(parsed).build();
	}

	private BinaryOperator binaryComparisonOperator(FeelParser.ComparisonOperatorContext context) {
		return switch (context.getText()) {
			case "=" -> BinaryOperator.BINARY_OPERATOR_EQUAL;
			case "!=" -> BinaryOperator.BINARY_OPERATOR_NOT_EQUAL;
			case "<" -> BinaryOperator.BINARY_OPERATOR_LESS;
			case "<=" -> BinaryOperator.BINARY_OPERATOR_LESS_EQUAL;
			case ">" -> BinaryOperator.BINARY_OPERATOR_GREATER;
			case ">=" -> BinaryOperator.BINARY_OPERATOR_GREATER_EQUAL;
			default -> throw unsupported("comparison operator", context.getText());
		};
	}

	private UnaryTestOperator unaryTestOperator(FeelParser.ComparisonOperatorContext context) {
		return switch (context.getText()) {
			case "=" -> UnaryTestOperator.UNARY_TEST_OPERATOR_EQUAL;
			case "!=" -> UnaryTestOperator.UNARY_TEST_OPERATOR_NOT_EQUAL;
			case "<" -> UnaryTestOperator.UNARY_TEST_OPERATOR_LESS;
			case "<=" -> UnaryTestOperator.UNARY_TEST_OPERATOR_LESS_EQUAL;
			case ">" -> UnaryTestOperator.UNARY_TEST_OPERATOR_GREATER;
			case ">=" -> UnaryTestOperator.UNARY_TEST_OPERATOR_GREATER_EQUAL;
			default -> throw unsupported("unary-test operator", context.getText());
		};
	}

	private Expression binary(BinaryOperator operator, Expression left, Expression right) {
		return Expression.newBuilder()
				.setBinary(BinaryExpression.newBuilder().setOperator(operator).setLeft(left).setRight(right)).build();
	}

	private IllegalArgumentException unsupported(String kind, String value) {
		return new IllegalArgumentException("Unsupported " + kind + ": " + value);
	}

	private static String decodeString(String token) {
		StringBuilder result = new StringBuilder(token.length() - 2);
		for (int i = 1; i < token.length() - 1; i++) {
			char current = token.charAt(i);
			if (current != '\\') {
				result.append(current);
				continue;
			}

			char escaped = token.charAt(++i);
			switch (escaped) {
				case '"' -> result.append('"');
				case '\'' -> result.append('\'');
				case '\\' -> result.append('\\');
				case 'n' -> result.append('\n');
				case 'r' -> result.append('\r');
				case 't' -> result.append('\t');
				case 'u' -> {
					result.append((char) Integer.parseInt(token.substring(i + 1, i + 5), 16));
					i += 4;
				}
				case 'U' -> {
					int codePoint = Integer.parseInt(token.substring(i + 1, i + 7), 16);
					result.appendCodePoint(codePoint);
					i += 6;
				}
				default -> result.append('\\').append(escaped);
			}
		}
		return result.toString();
	}

	private static String name(FeelParser.NameContext context) {
		return context.nameSegment().stream().map(org.antlr.v4.runtime.RuleContext::getText)
				.map(s -> s.replaceAll("\\s+", " "))
				.collect(java.util.stream.Collectors.joining(" "));
	}

	private static String key(FeelParser.KeyContext context) {
		if (context.STRING_LITERAL() != null) {
			return decodeString(context.STRING_LITERAL().getText());
		}
		if (context.keySegment() != null) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < context.keySegment().size(); i++) {
				FeelParser.KeySegmentContext seg = context.keySegment(i);
				if (i > 0) {
					org.antlr.v4.runtime.Token prev = context.keySegment(i - 1).getStop();
					org.antlr.v4.runtime.Token curr = seg.getStart();
					if (prev != null && curr != null && curr.getStartIndex() > prev.getStopIndex() + 1) {
						sb.append(" ");
					}
				}
				sb.append(seg.getText());
			}
			return sb.toString();
		}
		return "";
	}
}
