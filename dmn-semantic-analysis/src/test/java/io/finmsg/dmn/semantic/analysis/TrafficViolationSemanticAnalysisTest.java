package io.finmsg.dmn.semantic.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.feel.parser.DmnFeelParser;
import io.finmsg.dmn.frontend.xml.dmn.DmnXmlReader;
import io.finmsg.dmn.model.Definitions;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class TrafficViolationSemanticAnalysisTest {

  @Test
  void resolvesAllNamesInTrafficViolationModel() {
    Definitions semanticModel;
    try (InputStream input = getClass().getResourceAsStream("/TrafficViolation.dmn")) {
      assertThat(input).as("TrafficViolation.dmn test resource").isNotNull();
      semanticModel = new DmnXmlReader().read(input);
    } catch (Exception exception) {
      throw new AssertionError("Could not read TrafficViolation.dmn", exception);
    }

    Definitions parsedModel = new DmnFeelParser().parse(semanticModel);
    DmnSemanticAnalysisResult result = new DmnSemanticAnalyzer().analyze(parsedModel);

    assertThat(result.isSuccess())
        .as("semantic diagnostics: %s", result.diagnostics())
        .isTrue();
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.model()).isSameAs(parsedModel);
  }
}
