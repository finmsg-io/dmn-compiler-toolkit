package io.finmsg.dmn.runtime;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExternalJavaPolicyTest {
	@Test
	void malformedDescriptorsAndUnresolvableApprovedTargetsReturnNull() {
		assertThat(DmnRuntime.invokeExternalJava(null, List.of())).isNull();
		assertThat(DmnRuntime.invokeExternalJava("not a descriptor", List.of())).isNull();
		assertThat(DmnRuntime.invokeExternalJava(Map.of("class", "java.lang.Math"), List.of())).isNull();
		var missingClass = descriptor("missing.Target", "run()");
		var missingMethod = descriptor("java.lang.Math", "missing()");
		var policy = ExternalJavaPolicy.allowOnly(Set.of(new ExternalJavaPolicy.Method("missing.Target", "run()"),
				new ExternalJavaPolicy.Method("java.lang.Math", "missing()")));
		assertThat(DmnRuntime.invokeExternalJava(missingClass, List.of(), policy)).isNull();
		assertThat(DmnRuntime.invokeExternalJava(missingMethod, List.of(), policy)).isNull();
	}

	@Test
	void permittedHostConversionAndInvocationFailuresReturnNull() {
		var call = descriptor("java.lang.Integer", "parseInt(java.lang.String)");
		var policy = ExternalJavaPolicy
				.allowOnly(Set.of(new ExternalJavaPolicy.Method("java.lang.Integer", "parseInt(java.lang.String)")));
		assertThat(DmnRuntime.invokeExternalJava(Map.of("java", call), List.of("42"), policy))
				.isEqualTo(BigDecimal.valueOf(42));
		assertThat(DmnRuntime.invokeExternalJava(call, List.of("not an integer"), policy)).isNull();
		assertThatThrownBy(() -> DmnRuntime.invokeExternalJava(call, List.of("42")))
				.isInstanceOf(ExternalJavaDeniedException.class);
	}

	private static boolean initialized;

	public static class Forbidden {
		static {
			initialized = true;
		}
		public static int terminate(int status) {
			throw new AssertionError("Forbidden target executed");
		}
	}

	private Map<String, String> descriptor(String owner, String signature) {
		return Map.of("class", owner, "method signature", signature);
	}

	@Test
	void deniesBeforeClassInitializationAndParameterResolution() {
		String owner = getClass().getName() + "$Forbidden";
		assertThatThrownBy(() -> DmnRuntime.invokeExternalJava(descriptor(owner, "terminate(int)"), List.of(0)))
				.isInstanceOf(DmnEvaluationException.class).hasMessageStartingWith("EXTERNAL_JAVA_DENIED:");
		assertThatThrownBy(
				() -> DmnRuntime.invokeExternalJava(descriptor("missing.Target", "run(" + owner + ")"), List.of()))
				.isInstanceOf(DmnEvaluationException.class).hasMessageStartingWith("EXTERNAL_JAVA_DENIED:");
		assertThat(initialized).isFalse();
	}

	@Test
	void exactPermissionDoesNotAuthorizeOtherOverloadsOrClassesOrLaterDefaultCalls() {
		var policy = ExternalJavaPolicy.allowOnly(Set.of(new ExternalJavaPolicy.Method("java.lang.Math", "abs(int)")));
		assertThat(DmnRuntime.invokeExternalJava(descriptor("java.lang.Math", "abs(int)"), List.of(-7), policy))
				.isEqualTo(BigDecimal.valueOf(7));
		for (var denied : List.of(descriptor("java.lang.Math", "abs(long)"),
				descriptor("java.lang.Integer", "abs(int)"), descriptor("java.lang.System", "exit(int)"))) {
			assertThatThrownBy(() -> DmnRuntime.invokeExternalJava(denied, List.of(0), policy))
					.isInstanceOf(DmnEvaluationException.class).hasMessageStartingWith("EXTERNAL_JAVA_DENIED:");
		}
		assertThatThrownBy(() -> DmnRuntime.invokeExternalJava(descriptor("java.lang.Math", "abs(int)"), List.of(-7)))
				.isInstanceOf(DmnEvaluationException.class);
	}

	@Test
	void trustedCompatibilityModeIsExplicit() {
		assertThat(DmnRuntime.invokeExternalJava(descriptor("java.lang.Math", "abs(int)"), List.of(-7),
				ExternalJavaPolicy.unrestrictedTrustedUse())).isEqualTo(BigDecimal.valueOf(7));
	}
}
