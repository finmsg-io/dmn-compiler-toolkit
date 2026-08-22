package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.compiler.DmnSource;
import io.finmsg.dmn.compiler.DmnSourceId;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TckDmnDecisionPrunerTest {
	@Test
	void retainsSelectedDecisionItsDependenciesAndInputs() {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/">
				  <inputData id="input" name="Input"/>
				  <decision id="required" name="Required"/>
				  <decision id="selected" name="Selected">
				    <informationRequirement><requiredDecision href="#required"/></informationRequirement>
				  </decision>
				  <decision id="unrelated" name="Unrelated"/>
				</definitions>
				""";
		DmnSource source = new DmnSource(new DmnSourceId(URI.create("memory:/model.dmn")),
				xml.getBytes(StandardCharsets.UTF_8));

		String pruned = new String(new TckDmnDecisionPruner().prune(source, Set.of("Selected")).content(),
				StandardCharsets.UTF_8);

		assertThat(pruned).contains("name=\"Input\"", "name=\"Required\"", "name=\"Selected\"")
				.doesNotContain("name=\"Unrelated\"");
	}

	@Test
	void retainsDecisionServiceOwningSelectedOutputAndItsInputs() {
		String xml = """
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/">
				  <inputData id="input" name="Service Input"/>
				  <decision id="output" name="Service Output"/>
				  <decision id="unrelated" name="Unrelated"/>
				  <decisionService id="service" name="Selected Service">
				    <outputDecision href="#output"/>
				    <inputData href="#input"/>
				  </decisionService>
				</definitions>
				""";
		DmnSource source = new DmnSource(new DmnSourceId(URI.create("memory:/service.dmn")),
				xml.getBytes(StandardCharsets.UTF_8));

		String pruned = new String(new TckDmnDecisionPruner().prune(source, Set.of("Service Output")).content(),
				StandardCharsets.UTF_8);

		assertThat(pruned).contains("name=\"Service Input\"", "name=\"Service Output\"", "name=\"Selected Service\"")
				.doesNotContain("name=\"Unrelated\"");
	}
}
