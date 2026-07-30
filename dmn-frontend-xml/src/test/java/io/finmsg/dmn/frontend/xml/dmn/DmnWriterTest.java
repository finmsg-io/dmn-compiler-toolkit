package io.finmsg.dmn.frontend.xml.dmn;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.InformationItem;
import io.finmsg.dmn.model.InputData;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.NamedTypeReference;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.model.TypeReference;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DmnWriterTest {

  @Test
  void writesDefinitionsAndReadsThemBack() {
    Definitions definitions =
        Definitions.newBuilder()
            .setNode(Node.newBuilder().setId("definitions-1").setName("Writer test"))
            .setNamespace("https://finmsg.io/dmn/writer-test")
            .setExpressionLanguage("https://www.omg.org/spec/DMN/20230324/FEEL/")
            .setTypeLanguage("https://www.omg.org/spec/DMN/20230324/FEEL/")
            .setExporter("dmn-compiler-toolkit")
            .setExporterVersion("1.0.0")
            .addItemDefinitions(
                ItemDefinition.newBuilder()
                    .setNode(Node.newBuilder().setId("type-1").setName("Age < 18"))
                    .setType(
                        TypeReference.newBuilder()
                            .setBuiltin(BuiltinType.BUILTIN_TYPE_NUMBER)))
            .addDrgElements(
                DrgElement.newBuilder()
                    .setInputData(
                        InputData.newBuilder()
                            .setNode(Node.newBuilder().setId("input-1").setName("Applicant"))
                            .setVariable(
                                InformationItem.newBuilder()
                                    .setNode(
                                        Node.newBuilder()
                                            .setId("variable-1")
                                            .setName("Applicant"))
                                    .setType(
                                        TypeReference.newBuilder()
                                            .setNamed(
                                                NamedTypeReference.newBuilder()
                                                    .setName("ApplicantType"))))))
            .build();

    byte[] bytes = new DmnWriter().write(definitions);
    String xml = new String(bytes, StandardCharsets.UTF_8);

    assertThat(xml)
        .contains("xmlns=\"https://www.omg.org/spec/DMN/20230324/MODEL/\"")
        .contains("name=\"Age &lt; 18\"")
        .contains("typeRef=\"number\"")
        .contains("<inputData")
        .contains("typeRef=\"ApplicantType\"");

    Definitions readBack = new DmnXmlReader().read(bytes);

    assertThat(readBack).isEqualTo(definitions);
  }
}
