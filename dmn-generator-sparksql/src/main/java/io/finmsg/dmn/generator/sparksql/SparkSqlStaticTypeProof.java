package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import io.finmsg.dmn.runtime.DmnRuntime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Structural type proofs over bounded literal constructors; function bodies are
 * never evaluated.
 */
final class SparkSqlStaticTypeProof {
	private enum FunctionLiteral {
		VALUE
	}
	private static final class Unsupported extends RuntimeException {
	}
	private int remaining = 4096;

	static String sql(RuntimeInstanceOfExpression expression) {
		var proof = new SparkSqlStaticTypeProof();
		try {
			Object value = proof.literal(expression.expression(), 0);
			return proof.matches(value, expression.testedType(), 0) ? "TRUE" : "FALSE";
		} catch (Unsupported | IllegalArgumentException | java.time.DateTimeException ignored) {
			return null;
		}
	}

	private void charge(int depth) {
		if (--remaining < 0 || depth > 48)
			throw new Unsupported();
	}

	private Object literal(RuntimeExpression expression, int depth) {
		charge(depth);
		if (expression instanceof RuntimeFunctionDefinition function && !function.external())
			return FunctionLiteral.VALUE;
		if (expression instanceof RuntimeConstant constant) {
			if (constant.kind() == RuntimeConstantKind.NULL)
				return null;
			if (constant.value() == null || constant.value().length() > 2048)
				throw new Unsupported();
			return switch (constant.kind()) {
				case STRING -> constant.value();
				case NUMBER -> new BigDecimal(constant.value());
				case BOOLEAN -> {
					if (!Set.of("true", "false").contains(constant.value().toLowerCase(Locale.ROOT)))
						throw new Unsupported();
					yield Boolean.valueOf(constant.value());
				}
				case DATE -> LocalDate.parse(constant.value());
				case TIME -> DmnRuntime.parseTime(constant.value());
				case DATE_TIME -> DmnRuntime.parseDateTime(constant.value());
				case DURATION -> DmnRuntime.parseDuration(constant.value());
				default -> throw new Unsupported();
			};
		}
		if (expression instanceof RuntimeRangeExpression range) {
			Object low = range.lower().isPresent() ? literal(range.lower().get(), depth + 1) : null;
			Object up = range.upper().isPresent() ? literal(range.upper().get(), depth + 1) : null;
			return new io.finmsg.dmn.runtime.RuntimeRangeValue(low, up, range.lowerBoundary(), range.upperBoundary(),
					range.lower().isEmpty(), range.upper().isEmpty());
		}
		if (expression instanceof RuntimeListExpression list) {
			List<Object> values = new ArrayList<>();
			for (var element : list.elements())
				values.add(literal(element, depth + 1));
			return values;
		}
		if (expression instanceof RuntimeContextExpression context) {
			Map<String, Object> values = new LinkedHashMap<>();
			for (var entry : context.entries()) {
				Object value = literal(entry.expression(), depth + 1);
				if (entry.name().isEmpty() && entry.localSlot() == -1 && (context.type() == null
						|| context.type().fieldLayout().stream().noneMatch(f -> f.name().isEmpty())))
					return value;
				values.put(entry.name(), value);
			}
			return values;
		}
		if (expression instanceof RuntimeInvocationExpression invocation && invocation.function().isPresent()
				&& invocation.namedArguments().isEmpty())
			expression = new RuntimeFunctionCall(invocation.function().get(), invocation.positionalArguments(),
					invocation.type());
		if (expression instanceof RuntimeFunctionCall call && call.function().equalsIgnoreCase("date")
				&& call.arguments().size() == 1 && call.arguments().getFirst() instanceof RuntimeConstant text
				&& text.kind() == RuntimeConstantKind.STRING && text.value() != null && text.value().length() <= 2048)
			return LocalDate.parse(text.value());
		throw new Unsupported();
	}

	private boolean matches(Object value, RuntimeType type, int depth) {
		charge(depth);
		if (value == null)
			return false;
		if (type.kind() == RuntimeTypeKind.ANY)
			return true;
		if (value == FunctionLiteral.VALUE) {
			// Typed callable signatures require a separate validation proof.
			if (type.kind() == RuntimeTypeKind.FUNCTION)
				throw new Unsupported();
			return false;
		}
		if (value instanceof List<?> list) {
			if (type.kind() != RuntimeTypeKind.LIST)
				return false;
			if (type.elementType() == null)
				return true;
			for (Object element : list)
				if (!matches(element, type.elementType(), depth + 1))
					return false;
			return true;
		}
		if (value instanceof Map<?, ?> context) {
			if (type.kind() != RuntimeTypeKind.CONTEXT)
				return false;
			for (RuntimeField field : type.fieldLayout()) {
				if (!context.containsKey(field.name()))
					return false;
				Object member = context.get(field.name());
				if (member != null && !matches(member, field.type(), depth + 1))
					return false;
			}
			return true;
		}
		return DmnRuntime.instanceOf(value, type);
	}
}
