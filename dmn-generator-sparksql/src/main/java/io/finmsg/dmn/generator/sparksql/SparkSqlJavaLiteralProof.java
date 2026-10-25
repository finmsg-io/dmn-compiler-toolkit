package io.finmsg.dmn.generator.sparksql;

import io.finmsg.dmn.ir.*;
import java.util.*;

/**
 * Bounded literal calls to pure JDK methods or proven absent bootstrap classes.
 */
final class SparkSqlJavaLiteralProof {
	private static final Map<String, Class<?>> CLASSES = Map.of("java.lang.Math", Math.class, "java.lang.Byte",
			Byte.class, "java.lang.Short", Short.class, "java.lang.Integer", Integer.class, "java.lang.Long",
			Long.class, "java.lang.Float", Float.class, "java.lang.Double", Double.class, "java.lang.String",
			String.class);
	private static final Map<String, Class<?>> PARAMETERS = Map.ofEntries(Map.entry("byte", byte.class),
			Map.entry("short", short.class), Map.entry("int", int.class), Map.entry("long", long.class),
			Map.entry("float", float.class), Map.entry("double", double.class), Map.entry("boolean", boolean.class),
			Map.entry("char", char.class), Map.entry("java.lang.String", String.class),
			Map.entry("String", String.class), Map.entry("string", String.class),
			Map.entry("java.lang.Object", Object.class), Map.entry("Object", Object.class),
			Map.entry("[Ljava.lang.Object;", Object[].class));

	private SparkSqlJavaLiteralProof() {
	}

	static boolean supports(RuntimeFunctionDefinition function) {
		return supports(function, io.finmsg.dmn.runtime.ExternalJavaPolicy.denyAll());
	}

	static boolean supports(RuntimeFunctionDefinition function, io.finmsg.dmn.runtime.ExternalJavaPolicy policy) {
		Map<String, Object> descriptor = descriptor(function);
		if (descriptor == null)
			return false;
		String className = ((String) descriptor.get("class")).trim();
		String signature = ((String) descriptor.get("method signature")).trim();
		if (!policy.permits(className, signature))
			return false;
		Class<?> target = CLASSES.get(className);
		int open = signature.indexOf('(');
		if (open <= 0 || !signature.endsWith(")"))
			return false;
		String method = signature.substring(0, open).trim();
		String parameterText = signature.substring(open + 1, signature.length() - 1).trim();
		String[] names = parameterText.isEmpty() ? new String[0] : parameterText.split(",");
		if (names.length > 4)
			return false;
		Class<?>[] parameters = new Class<?>[names.length];
		for (int index = 0; index < names.length; index++) {
			parameters[index] = PARAMETERS.get(names[index].trim());
			if (parameters[index] == null)
				return false;
		}
		if (target == null)
			return missingBootstrapClass(className);
		try {
			var reflected = target.getMethod(method, parameters);
			if (!java.lang.reflect.Modifier.isStatic(reflected.getModifiers()))
				return false;
		} catch (NoSuchMethodException exception) {
			// Shared reflection returns null for a missing method on a known JDK class.
			return true;
		} catch (SecurityException exception) {
			return false;
		}
		if (target == Math.class)
			return Set.of("abs", "max", "min", "cos", "sin", "tan", "sqrt", "pow", "ceil", "floor", "round", "log",
					"exp", "toDegrees", "toRadians").contains(method);
		if (target == String.class)
			return Set.of("valueOf", "format").contains(method);
		return method.equals("valueOf");
	}

	private static boolean missingBootstrapClass(String name) {
		// java.* packages cannot be supplied by an application class loader.
		// Existing classes outside the pure-method whitelist remain unsupported.
		if (name.length() > 128 || !name.matches("java\\.lang\\.[A-Za-z][A-Za-z0-9]*"))
			return false;
		try {
			Class.forName(name, false, null);
			return false;
		} catch (ClassNotFoundException absent) {
			return true;
		} catch (SecurityException | LinkageError unavailable) {
			return false;
		}
	}

	static RuntimeExpression result(RuntimeFunctionDefinition function, RuntimeInvocationExpression invocation,
			io.finmsg.dmn.runtime.ExternalJavaPolicy policy) {
		if (!supports(function, policy)
				|| invocation.positionalArguments().size() + invocation.namedArguments().size() > 16)
			return null;
		if (invocation.positionalArguments().stream().anyMatch(argument -> !literal(argument))
				|| invocation.namedArguments().stream().anyMatch(argument -> !literal(argument.expression())))
			return null;
		var arguments = new ArrayList<>(invocation.positionalArguments());
		var named = new LinkedHashMap<String, RuntimeExpression>();
		invocation.namedArguments().forEach(argument -> named.put(argument.name(), argument.expression()));
		arguments.addAll(named.values());
		Map<String, Object> descriptor = descriptor(function);
		String signature = ((String) descriptor.get("method signature")).trim();
		if (((String) descriptor.get("class")).trim().equals("java.lang.String")
				&& signature.substring(0, signature.indexOf('(')).trim().equals("format")) {
			if (arguments.isEmpty() || !(arguments.getFirst() instanceof RuntimeConstant format)
					|| format.kind() != RuntimeConstantKind.STRING || !format.value().matches("(?:[^%]|%%|%s)*"))
				return null;
		}
		var call = new RuntimeInvocationExpression(Optional.empty(), Optional.of(function), invocation.namedArguments(),
				invocation.positionalArguments(), invocation.type());
		var decision = new RuntimeDecision(0, 0, RuntimeType.scalar(RuntimeTypeKind.ANY), List.of(), Optional.of(call));
		try {
			Object value = new io.finmsg.dmn.runtime.DmnRuntime(policy)
					.evaluate(new RuntimeModel(List.of(), List.of(decision), List.of(), List.of(0), 1), Map.of())
					.decisionValue(0);
			if (value instanceof String text && (text.length() > 32768
					|| text.codePoints().anyMatch(point -> point >= 0xD800 && point <= 0xDFFF)))
				return null;
			return SparkSqlExpressionEmitter.nativeContextLiteralValue(value);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static boolean literal(RuntimeExpression expression) {
		if (!(expression instanceof RuntimeConstant constant) || constant.value().length() > 2048)
			return false;
		if (constant.kind() == RuntimeConstantKind.NUMBER) {
			try {
				var value = new java.math.BigDecimal(constant.value());
				return value.precision() <= 38 && Math.abs((long) value.scale()) <= 38;
			} catch (NumberFormatException exception) {
				return false;
			}
		}
		return Set.of(RuntimeConstantKind.STRING, RuntimeConstantKind.BOOLEAN, RuntimeConstantKind.NULL)
				.contains(constant.kind());
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> descriptor(RuntimeFunctionDefinition function) {
		// Boxed Java functions carry the same descriptor in a non-external FEEL
		// definition. Evaluate the original definition so its arity and coercion
		// rules remain distinct from literal external functions.
		if (function.localSlotCount() > 2048 || function.body().isEmpty())
			return null;
		Object value = descriptorValue(function.body().get(), 0, new int[]{32});
		if (!(value instanceof Map<?, ?> map))
			return null;
		if (map.keySet().equals(Set.of("java")) && map.get("java") instanceof Map<?, ?> nested)
			map = nested;
		return map.keySet().equals(Set.of("class", "method signature")) && map.get("class") instanceof String
				&& map.get("method signature") instanceof String ? (Map<String, Object>) map : null;
	}

	private static Object descriptorValue(RuntimeExpression expression, int depth, int[] budget) {
		if (depth > 4 || --budget[0] < 0)
			return null;
		if (expression instanceof RuntimeConstant constant && constant.kind() == RuntimeConstantKind.STRING
				&& constant.value().length() <= 256)
			return constant.value();
		if (!(expression instanceof RuntimeContextExpression context) || context.entries().size() > 4)
			return null;
		var map = new LinkedHashMap<String, Object>();
		for (RuntimeContextEntry entry : context.entries()) {
			Object item = descriptorValue(entry.expression(), depth + 1, budget);
			if (entry.name().isEmpty() || item == null || map.putIfAbsent(entry.name(), item) != null)
				return null;
		}
		return map;
	}
}
