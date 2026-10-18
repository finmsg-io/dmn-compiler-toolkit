package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/** Bounded flatten lowering without constructing heterogeneous Spark arrays. */
final class SparkSqlFlattenLowerer {
	private final List<RuntimeExpression> arrays = new ArrayList<>();
	private final List<RuntimeExpression> numbers = new ArrayList<>();
	private RuntimeType listType;
	private int budget = 128;

	static RuntimeExpression lower(String name, List<RuntimeExpression> args) {
		if (!name.equalsIgnoreCase("flatten"))
			return null;
		var lowerer = new SparkSqlFlattenLowerer();
		for (RuntimeExpression argument : args)
			if (!lowerer.collect(argument, 0))
				return null;
		if (!SparkSqlLiteralBuiltinProof.exactNumericLiteralArray(new RuntimeListExpression(lowerer.numbers,
				RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType.scalar(RuntimeTypeKind.NUMBER)))))
			return null;
		if (lowerer.listType == null)
			return new RuntimeListExpression(List.of(),
					RuntimeType.element(RuntimeTypeKind.LIST, RuntimeType.scalar(RuntimeTypeKind.NULL)));
		return new RuntimeFunctionCall("flatten", List.of(
				new RuntimeListExpression(lowerer.arrays, RuntimeType.element(RuntimeTypeKind.LIST, lowerer.listType))),
				lowerer.listType);
	}

	private boolean collect(RuntimeExpression expression, int depth) {
		if (--budget < 0 || depth > 16)
			return false;
		if (expression instanceof RuntimeListExpression list) {
			for (RuntimeExpression item : list.elements())
				if (!collect(item, depth + 1))
					return false;
			return true;
		}
		if (expression instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.NULL)
			return true;
		RuntimeType type = expression.type();
		if (type == null)
			return false;
		RuntimeType element = type.kind() == RuntimeTypeKind.LIST ? type.elementType() : type;
		if (element == null || !Set.of(RuntimeTypeKind.STRING, RuntimeTypeKind.NUMBER, RuntimeTypeKind.BOOLEAN)
				.contains(element.kind()))
			return false;
		RuntimeType arrayType = RuntimeType.element(RuntimeTypeKind.LIST, element);
		if (listType != null && !listType.equals(arrayType))
			return false;
		listType = arrayType;
		if (expression instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.NUMBER)
			numbers.add(constant);
		arrays.add(type.kind() == RuntimeTypeKind.LIST
				? expression
				: new RuntimeListExpression(List.of(expression), arrayType));
		return true;
	}
}
