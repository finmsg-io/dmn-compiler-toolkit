package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class TckSmokeTest {
	@TestFactory
	Stream<DynamicTest> executesTestsDefinedAlongsideDmnModel() throws Exception {
		Path directory = resourceDirectory("smoke");
		List<TckTestCase> cases = new TckTestCaseReader().read(directory.resolve("scalar-arithmetic-test-01.xml"));
		DmnToolkitTckEngine engine = new DmnToolkitTckEngine();

		return cases.stream().map(testCase -> DynamicTest.dynamicTest(testCase.id(), () -> {
			TckExecutionResult result = engine.execute(directory.resolve("scalar-arithmetic.dmn"), testCase);
			testCase.expectedResults().forEach((name, expected) -> {
				Object actual = result.decisionValues().get(name);
				if (expected.kind() == TckValue.Kind.NUMBER) {
					assertThat((BigDecimal) actual).isEqualByComparingTo((BigDecimal) expected.runtimeValue());
				} else {
					assertThat(actual).isEqualTo(expected.runtimeValue());
				}
			});
		}));
	}

	private static Path resourceDirectory(String name) throws URISyntaxException {
		return Path.of(TckSmokeTest.class.getClassLoader().getResource(name).toURI());
	}
}
