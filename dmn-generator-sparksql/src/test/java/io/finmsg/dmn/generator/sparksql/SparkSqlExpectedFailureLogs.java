package io.finmsg.dmn.generator.sparksql;

import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.AbstractFilterable;
import org.junit.jupiter.api.extension.*;

/** Keep expected negative-test diagnostics distinct from real CI failures. */
final class SparkSqlExpectedFailureLogs implements InvocationInterceptor {

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@ExtendWith(SparkSqlExpectedFailureLogs.class)
	@interface Capture {
	}

	@FunctionalInterface
	interface Action {
		void run() throws Throwable;
	}

	@Override
	public void interceptTestMethod(Invocation<Void> invocation,
			ReflectiveInvocationContext<java.lang.reflect.Method> method, ExtensionContext context) throws Throwable {
		run(context.getDisplayName(), invocation::proceed, events -> {
			System.err.println("[FAILED TEST: CAPTURED SPARK DIAGNOSTICS] " + context.getDisplayName());
			for (LogEvent event : events) {
				System.err.printf("%s %s: %s%n", event.getLevel(), event.getLoggerName(),
						event.getMessage().getFormattedMessage());
				if (event.getThrown() != null)
					event.getThrown().printStackTrace(System.err);
			}
		});
	}

	static void run(String description, Action assertion, Consumer<List<LogEvent>> failureDiagnostics)
			throws Throwable {
		System.out.printf("[INFO] EXPECTED SPARK FAILURE TEST BEGIN: %s (intentional failed SQL jobs)%n", description);
		var events = new ConcurrentLinkedQueue<LogEvent>();
		var filter = new AbstractFilter() {
			@Override
			public Result filter(LogEvent event) {
				if (event.getLoggerName().startsWith("org.apache.spark.")
						&& event.getLevel().isMoreSpecificThan(Level.WARN)) {
					events.add(event.toImmutable());
					return Result.DENY;
				}
				return Result.NEUTRAL;
			}
		};
		var context = (LoggerContext) LogManager.getContext(false);
		var appenders = new ArrayList<AbstractFilterable>();
		for (var appender : context.getConfiguration().getAppenders().values()) {
			if (appender instanceof AbstractFilterable filterable) {
				filterable.addFilter(filter);
				appenders.add(filterable);
			}
		}
		try {
			assertion.run();
		} catch (Throwable failure) {
			failureDiagnostics.accept(List.copyOf(events));
			throw failure;
		} finally {
			appenders.forEach(appender -> appender.removeFilter(filter));
		}
		System.out.printf("[INFO] EXPECTED SPARK FAILURE ASSERTIONS PASSED: %s (%d diagnostic records captured)%n",
				description, events.size());
	}
}
