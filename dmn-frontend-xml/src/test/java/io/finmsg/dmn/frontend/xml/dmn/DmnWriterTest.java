package io.finmsg.dmn.frontend.xml.dmn;

import static org.assertj.core.api.Assertions.assertThat;

import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.AuthorityRequirement;
import io.finmsg.dmn.model.BusinessKnowledgeModel;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.ElementReference;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.FeelText;
import io.finmsg.dmn.model.FunctionDefinition;
import io.finmsg.dmn.model.FunctionKind;
import io.finmsg.dmn.model.InformationItem;
import io.finmsg.dmn.model.InputData;
import io.finmsg.dmn.model.Import;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.KnowledgeRequirement;
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
            .addImports(
                Import.newBuilder()
                    .setNode(Node.newBuilder().setId("import-1"))
                    .setName("risk")
                    .setNamespace("https://finmsg.io/dmn/risk")
                    .setLocationUri("risk.dmn")
                    .setImportType("https://www.omg.org/spec/DMN/20230324/MODEL/"))
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
        .contains("name=\"risk\"")
        .contains("locationURI=\"risk.dmn\"")
        .contains("importType=\"https://www.omg.org/spec/DMN/20230324/MODEL/\"")
        .contains("<inputData")
        .contains("typeRef=\"ApplicantType\"");

    Definitions readBack = new DmnXmlReader().read(bytes);

    assertThat(readBack).isEqualTo(definitions);
  }

  @Test
  void writesBusinessKnowledgeModelAndRequirementsRoundTrip() {
    ElementReference knowledge = ElementReference.newBuilder().setHref("#knowledge-1").build();
    ElementReference authority = ElementReference.newBuilder().setHref("#authority-1").build();
    ElementReference decision = ElementReference.newBuilder().setHref("#decision-1").build();
    BusinessKnowledgeModel bkm =
        BusinessKnowledgeModel.newBuilder()
            .setNode(Node.newBuilder().setId("bkm-1").setName("Double"))
            .setVariable(
                InformationItem.newBuilder()
                    .setNode(Node.newBuilder().setId("result-1").setName("result"))
                    .setType(TypeReference.newBuilder().setBuiltin(BuiltinType.BUILTIN_TYPE_NUMBER)))
            .setFunction(
                FunctionDefinition.newBuilder()
                    .setKind(FunctionKind.FUNCTION_KIND_FEEL)
                    .addFormalParameters(
                        InformationItem.newBuilder()
                            .setNode(Node.newBuilder().setId("parameter-1").setName("value"))
                            .setType(
                                TypeReference.newBuilder()
                                    .setBuiltin(BuiltinType.BUILTIN_TYPE_NUMBER)))
                    .setLogic(
                        Feel.newBuilder()
                            .setText(FeelText.newBuilder().setText("value * 2"))))
            .addKnowledgeRequirements(
                KnowledgeRequirement.newBuilder().setRequiredKnowledge(knowledge))
            .addAuthorityRequirements(
                AuthorityRequirement.newBuilder()
                    .setRequiredAuthority(authority)
                    .setDecision(decision))
            .build();
    Definitions definitions =
        Definitions.newBuilder()
            .setNode(Node.getDefaultInstance())
            .setNamespace("https://example.com/functions")
            .addDrgElements(DrgElement.newBuilder().setBusinessKnowledgeModel(bkm))
            .build();

    byte[] xml = new DmnWriter().write(definitions);
    Definitions readBack = new DmnXmlReader().read(xml);

    assertThat(new String(xml, StandardCharsets.UTF_8))
        .contains("<businessKnowledgeModel")
        .contains("<formalParameter")
        .contains("<requiredKnowledge href=\"#knowledge-1\"")
        .contains("<requiredAuthority href=\"#authority-1\"")
        .contains("<requiredDecision href=\"#decision-1\"");
    assertThat(readBack).isEqualTo(definitions);
  }
}
