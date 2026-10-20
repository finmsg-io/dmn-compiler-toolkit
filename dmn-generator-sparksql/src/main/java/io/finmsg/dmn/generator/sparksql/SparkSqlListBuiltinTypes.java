package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Result layouts for homogeneous list operations with bounded positional
 * arguments.
 */
final class SparkSqlListBuiltinTypes {
	private SparkSqlListBuiltinTypes() {
	}

	static RuntimeType result(String name, List<RuntimeExpression> args) {
		if (args.isEmpty() || args.size() > 32)
			return null;
		RuntimeType list = args.getFirst().type();
		if (nestedTextList(list)) {
			return switch (name.toLowerCase(Locale.ROOT)) {
				case "flatten" -> args.size() == 1 ? list.elementType() : null;
				case "concatenate" -> args.stream().allMatch(a -> list.equals(a.type())) ? list : null;
				case "append" -> args.size() >= 2
						&& args.subList(1, args.size()).stream().allMatch(a -> list.elementType().equals(a.type()))
								? list
								: null;
				default -> null;
			};
		}
		if (list == null || list.kind() != RuntimeTypeKind.LIST || list.elementType() == null
				|| !Set.of(RuntimeTypeKind.STRING, RuntimeTypeKind.BOOLEAN, RuntimeTypeKind.NUMBER)
						.contains(list.elementType().kind()))
			return null;
		boolean supported = switch (name.toLowerCase(Locale.ROOT)) {
			case "reverse", "distinct values" -> args.size() == 1;
			case "concatenate" -> args.stream().allMatch(a -> list.equals(a.type()));
			case "union" -> args.size() == 2 && list.equals(args.get(1).type());
			case "append" -> args.size() >= 2
					&& args.subList(1, args.size()).stream().allMatch(a -> list.elementType().equals(a.type()));
			case "sublist" ->
				(args.size() == 2 || args.size() == 3) && integer(args.get(1)) != null && integer(args.get(1)) != 0
						&& (args.size() == 2 || integer(args.get(2)) != null && integer(args.get(2)) >= 0);
			case "remove" -> args.size() == 2 && integer(args.get(1)) != null;
			case "insert before" ->
				args.size() == 3 && integer(args.get(1)) != null && list.elementType().equals(args.get(2).type());
			default -> false;
		};
		return supported ? list : null;
	}

	static boolean nestedTextList(RuntimeType type) {
		return type != null && type.kind() == RuntimeTypeKind.LIST && type.elementType() != null
				&& type.elementType().kind() == RuntimeTypeKind.LIST && type.elementType().elementType() != null
				&& Set.of(RuntimeTypeKind.STRING, RuntimeTypeKind.BOOLEAN)
						.contains(type.elementType().elementType().kind());
	}

	static Integer integer(RuntimeExpression expression) {
		if (!(expression instanceof RuntimeConstant value) || value.kind() != RuntimeConstantKind.NUMBER
				|| value.value().length() > 32)
			return null;
		try {
			int result = new java.math.BigDecimal(value.value()).intValueExact();
			return Math.abs((long) result) <= 1_000_000 ? result : null;
		} catch (ArithmeticException | NumberFormatException exception) {
			return null;
		}
	}
}
