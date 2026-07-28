package io.finmsg.dmn.frontend.xml.dmn;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.finmsg.dmn.model.Definitions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class DmnVersionDetectorTest {

  @Test
  void detect() {
    DmnXmlReader reader = new DmnXmlReader();
    Definitions d = reader.read(getClass().getResourceAsStream("/TrafficViolation.dmn"));
    System.out.println("--- definitions of TrafficViolation.dmn-------------");
    System.out.println(d);
    assertThat(d).isNotNull();
  }
}
