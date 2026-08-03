package io.finmsg.dmn.tck;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TckTestCaseReaderTest {
  @Test
  void decodesCanonicalScalarValuesAndNestedExpectedValue() throws Exception {
    String xml = """
        <testCases xmlns="urn:tck" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xmlns:xsd="http://www.w3.org/2001/XMLSchema">
          <testCase id="case-1" name="scalars">
            <inputNode name="text"><value xsi:type="xsd:string">hello</value></inputNode>
            <inputNode name="flag"><value xsi:type="xsd:boolean">1</value></inputNode>
            <inputNode name="nothing"><value xsi:nil="true"/></inputNode>
            <resultNode name="amount"><expected><value xsi:type="xsd:decimal">10.50</value></expected></resultNode>
          </testCase>
        </testCases>
        """;

    TckTestCase testCase = new TckTestCaseReader().read(
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getFirst();

    assertThat(testCase.inputs().get("text").runtimeValue()).isEqualTo("hello");
    assertThat(testCase.inputs().get("flag").runtimeValue()).isEqualTo(true);
    assertThat(testCase.inputs().get("nothing").kind()).isEqualTo(TckValue.Kind.NULL);
    assertThat((BigDecimal) testCase.expectedResults().get("amount").runtimeValue())
        .isEqualByComparingTo("10.50");
  }
}
