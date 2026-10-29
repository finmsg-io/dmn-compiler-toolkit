package io.finmsg.dmn.generator.sparksql;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.*;

class SparkSqlExpectedFailureLogsTest {

	private final List<LogEvent> delivered = new ArrayList<>();
	private final AbstractAppender appender = new AbstractAppender("expected-failure-test", null,
			PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
		@Override
		public void append(LogEvent event) {
			delivered.add(event.toImmutable());
		}
	};
	private final LoggerContext context = (LoggerContext) LogManager.getContext(false);
	private org.apache.logging.log4j.Level originalLevel;

	@BeforeEach
	void attach() {
		originalLevel = context.getConfiguration().getRootLogger().getLevel();
		context.getConfiguration().getRootLogger().setLevel(org.apache.logging.log4j.Level.INFO);
		appender.start();
		context.getConfiguration().addAppender(appender);
		context.getConfiguration().getRootLogger().addAppender(appender, null, null);
		context.updateLoggers();
	}

	@AfterEach
	void detach() {
		context.getConfiguration().getRootLogger().setLevel(originalLevel);
		context.getConfiguration().getRootLogger().removeAppender(appender.getName());
		context.getConfiguration().getAppenders().remove(appender.getName());
		context.updateLoggers();
		appender.stop();
	}

	@Test
	void passingExpectedFailureCapturesSparkErrorsAndRestoresLogging() throws Throwable {
		SparkSqlExpectedFailureLogs.run("synthetic negative test", () -> {
			LogManager.getLogger("org.apache.spark.ExpectedFailureTest").error("intentional guard failure");
		}, events -> fail("A passing assertion must not replay diagnostics"));
		assertThat(delivered).isEmpty();
		assertThat(appender.getFilter()).isNull();
	}

	@Test
	void failedAssertionRetainsDiagnosticsAndOriginalFailure() {
		var failure = new AssertionError("negative test assertion failed");
		var retained = new ArrayList<LogEvent>();
		assertThatThrownBy(() -> SparkSqlExpectedFailureLogs.run("failing negative test", () -> {
			LogManager.getLogger("org.apache.spark.ExpectedFailureTest").error("original Spark diagnostic");
			throw failure;
		}, retained::addAll)).isSameAs(failure);
		assertThat(retained).anySatisfy(
				event -> assertThat(event.getMessage().getFormattedMessage()).isEqualTo("original Spark diagnostic"));
		assertThat(appender.getFilter()).isNull();
	}

	@Test
	void ordinaryInformationalOutputRemainsVisible() throws Throwable {
		SparkSqlExpectedFailureLogs.run("logging control", () -> {
			LogManager.getRootLogger().info("ordinary informational control");
		}, events -> fail("Unexpected assertion failure"));
		assertThat(delivered).anySatisfy(event -> assertThat(event.getMessage().getFormattedMessage())
				.isEqualTo("ordinary informational control"));
	}
}
