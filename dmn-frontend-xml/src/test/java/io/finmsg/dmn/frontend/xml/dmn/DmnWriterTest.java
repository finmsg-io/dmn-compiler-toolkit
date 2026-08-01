package io.finmsg.dmn.frontend.xml.dmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.finmsg.dmn.model.BuiltinType;
import io.finmsg.dmn.model.AuthorityRequirement;
import io.finmsg.dmn.model.Aggregation;
import io.finmsg.dmn.model.AnnotationClause;
import io.finmsg.dmn.model.BusinessKnowledgeModel;
import io.finmsg.dmn.model.Binding;
import io.finmsg.dmn.model.BoxedExpression;
import io.finmsg.dmn.model.BoxedExpressionText;
import io.finmsg.dmn.model.Decision;
import io.finmsg.dmn.model.DecisionLogic;
import io.finmsg.dmn.model.DecisionRule;
import io.finmsg.dmn.model.DecisionTable;
import io.finmsg.dmn.model.ContextEntryText;
import io.finmsg.dmn.model.ContextText;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DecisionService;
import io.finmsg.dmn.model.DrgElement;
import io.finmsg.dmn.model.Documentation;
import io.finmsg.dmn.model.ElementReference;
import io.finmsg.dmn.model.ExpressionNode;
import io.finmsg.dmn.model.ExpressionText;
import io.finmsg.dmn.model.ExtensionAttribute;
import io.finmsg.dmn.model.ExtensionElement;
import io.finmsg.dmn.model.ExtensionElements;
import io.finmsg.dmn.model.Feel;
import io.finmsg.dmn.model.FeelText;
import io.finmsg.dmn.model.FunctionDefinition;
import io.finmsg.dmn.model.FunctionKind;
import io.finmsg.dmn.model.FunctionDefinitionText;
import io.finmsg.dmn.model.HitPolicy;
import io.finmsg.dmn.model.HitPolicySpec;
import io.finmsg.dmn.model.InformationItem;
import io.finmsg.dmn.model.InformationRequirement;
import io.finmsg.dmn.model.InputData;
import io.finmsg.dmn.model.InputClause;
import io.finmsg.dmn.model.Import;
import io.finmsg.dmn.model.ItemDefinition;
import io.finmsg.dmn.model.ItemComponent;
import io.finmsg.dmn.model.KnowledgeRequirement;
import io.finmsg.dmn.model.KnowledgeSource;
import io.finmsg.dmn.model.Invocation;
import io.finmsg.dmn.model.ListExpressionText;
import io.finmsg.dmn.model.NamedTypeReference;
import io.finmsg.dmn.model.Node;
import io.finmsg.dmn.model.Namespace;
import io.finmsg.dmn.model.Orientation;
import io.finmsg.dmn.model.OutputClause;
import io.finmsg.dmn.model.RuleAnnotation;
import io.finmsg.dmn.model.RelationColumnText;
import io.finmsg.dmn.model.RelationRowText;
import io.finmsg.dmn.model.RelationText;
import io.finmsg.dmn.model.TypeReference;
import io.finmsg.dmn.model.TypeConstraint;
import io.finmsg.dmn.model.UnaryTest;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DmnWriterTest {

  @Test
  void writesStructuredItemDefinitionsAndConstraintsRoundTrip() {
    ItemDefinition applicant =
        ItemDefinition.newBuilder()
            .setNode(Node.newBuilder().setId("type-applicant").setName("Applicant"))
            .setIsCollection(true)
            .setConstraint(
                TypeConstraint.newBuilder()
                    .setText(FeelText.newBuilder().setText("count(?) > 0")))
            .addComponents(
                ItemComponent.newBuilder()
                    .setNode(Node.newBuilder().setId("component-age").setName("age"))
                    .setType(
                        TypeReference.newBuilder()
                            .setBuiltin(BuiltinType.BUILTIN_TYPE_NUMBER))
                    .setConstraint(
                        TypeConstraint.newBuilder()
                            .setText(FeelText.newBuilder().setText("[0..120]"))))
            .addComponents(
                ItemComponent.newBuilder()
                    .setNode(Node.newBuilder().setId("component-tags").setName("tags"))
                    .setIsCollection(true)
                    .setType(
                        TypeReference.newBuilder()
                            .setBuiltin(BuiltinType.BUILTIN_TYPE_STRING)))
            .build();
    Definitions definitions = Definitions.newBuilder()
        .setNode(Node.getDefaultInstance())
        .setNamespace("https://finmsg.io/dmn/types")
        .addItemDefinitions(applicant)
        .build();

    byte[] bytes = new DmnWriter().write(definitions);
    String xml = new String(bytes, StandardCharsets.UTF_8);

    assertThat(xml)
        .contains("<itemDefinition")
        .contains("isCollection=\"true\"")
        .contains("<itemComponent")
        .contains("<typeRef>number</typeRef>")
        .contains("<allowedValues><text>[0..120]</text></allowedValues>");
    assertThat(new DmnXmlReader().read(bytes)).isEqualTo(definitions);
  }

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

  @Test
  void writesKnowledgeSourceAndDecisionServiceRoundTrip() {
    KnowledgeSource source =
        KnowledgeSource.newBuilder()
            .setNode(Node.newBuilder().setId("source-1").setName("Regulation"))
            .setAuthority("#owner-1")
            .setLocationUri("https://example.com/regulation")
            .build();
    DecisionService service =
        DecisionService.newBuilder()
            .setNode(Node.newBuilder().setId("service-1").setName("Risk service"))
            .addOutputDecisions(reference("#output-1"))
            .addEncapsulatedDecisions(reference("#encapsulated-1"))
            .addInputDecisions(reference("#input-decision-1"))
            .addInputData(reference("#input-data-1"))
            .build();
    Definitions definitions =
        Definitions.newBuilder()
            .setNode(Node.getDefaultInstance())
            .setNamespace("https://example.com/services")
            .addDrgElements(DrgElement.newBuilder().setKnowledgeSource(source))
            .addDrgElements(DrgElement.newBuilder().setDecisionService(service))
            .build();

    byte[] xml = new DmnWriter().write(definitions);
    Definitions readBack = new DmnXmlReader().read(xml);

    assertThat(new String(xml, StandardCharsets.UTF_8))
        .contains("<knowledgeSource")
        .contains("<owner href=\"#owner-1\"")
        .contains("<locationURI>https://example.com/regulation</locationURI>")
        .contains("<decisionService")
        .contains("<outputDecision href=\"#output-1\"")
        .contains("<encapsulatedDecision href=\"#encapsulated-1\"")
        .contains("<inputDecision href=\"#input-decision-1\"")
        .contains("<inputData href=\"#input-data-1\"");
    assertThat(readBack).isEqualTo(definitions);
  }

  @Test
  void writesDecisionRequirementsLiteralExpressionsAndInvocationsRoundTrip() {
    Decision literal =
        Decision.newBuilder()
            .setNode(Node.newBuilder().setId("decision-1").setName("Risk"))
            .addInformationRequirements(
                InformationRequirement.newBuilder().setInput(reference("#input-1")))
            .setLogic(DecisionLogic.newBuilder().setLiteralExpression(feel("Applicant.age")))
            .build();
    Decision invocation =
        Decision.newBuilder()
            .setNode(Node.newBuilder().setId("decision-2").setName("Adjusted risk"))
            .addInformationRequirements(
                InformationRequirement.newBuilder().setDecision(reference("#decision-1")))
            .setLogic(
                DecisionLogic.newBuilder()
                    .setInvocation(
                        Invocation.newBuilder()
                            .setExpression(feel("AdjustRisk"))
                            .addBindings(
                                Binding.newBuilder()
                                    .setParameter("risk")
                                    .setExpression(feel("Risk")))
                            .addBindings(
                                Binding.newBuilder()
                                    .setParameter("factor")
                                    .setExpression(feel("1.2")))))
            .build();
    Definitions definitions =
        Definitions.newBuilder()
            .setNode(Node.getDefaultInstance())
            .setNamespace("https://example.com/decisions")
            .addDrgElements(DrgElement.newBuilder().setDecision(literal))
            .addDrgElements(DrgElement.newBuilder().setDecision(invocation))
            .build();

    byte[] xml = new DmnWriter().write(definitions);
    Definitions readBack = new DmnXmlReader().read(xml);

    assertThat(new String(xml, StandardCharsets.UTF_8))
        .contains("<requiredInput href=\"#input-1\"")
        .contains("<requiredDecision href=\"#decision-1\"")
        .contains("<literalExpression><text>Applicant.age</text></literalExpression>")
        .contains("<invocation>")
        .contains("<parameter name=\"risk\"")
        .contains("<parameter name=\"factor\"");
    assertThat(readBack).isEqualTo(definitions);
  }

  @Test
  void writesDecisionTableRoundTrip() {
    DecisionTable table =
        DecisionTable.newBuilder()
            .setNode(Node.newBuilder().setId("table-1"))
            .setHitPolicy(
                HitPolicySpec.newBuilder()
                    .setPolicy(HitPolicy.HIT_POLICY_COLLECT)
                    .setAggregation(Aggregation.AGGREGATION_SUM))
            .setPreferredOrientation(Orientation.ORIENTATION_RULE_AS_ROW)
            .addInputs(
                InputClause.newBuilder()
                    .setNode(Node.newBuilder().setId("input-clause-1"))
                    .setInputExpression(feel("Applicant.age"))
                    .setInputValues(feel("[18..120]"))
                    .setType(
                        TypeReference.newBuilder().setBuiltin(BuiltinType.BUILTIN_TYPE_NUMBER)))
            .addOutputs(
                OutputClause.newBuilder()
                    .setNode(Node.newBuilder().setId("output-clause-1").setName("score"))
                    .setType(
                        TypeReference.newBuilder().setBuiltin(BuiltinType.BUILTIN_TYPE_NUMBER))
                    .setOutputValues(feel("1, 2, 3"))
                    .setDefaultOutputEntry(
                        ExpressionNode.newBuilder()
                            .setText(
                                ExpressionText.newBuilder()
                                    .setFeel(FeelText.newBuilder().setText("0")))))
            .addAnnotations(
                AnnotationClause.newBuilder()
                    .setNode(Node.newBuilder().setId("annotation-1").setName("reason")))
            .addRules(
                DecisionRule.newBuilder()
                    .setNode(Node.newBuilder().setId("rule-1"))
                    .addInputEntries(
                        UnaryTest.newBuilder().setText(FeelText.newBuilder().setText(">= 18")))
                    .addOutputEntries(feel("2"))
                    .addAnnotationEntries(RuleAnnotation.newBuilder().setText("adult")))
            .build();
    Definitions definitions =
        Definitions.newBuilder()
            .setNode(Node.getDefaultInstance())
            .setNamespace("https://example.com/table")
            .addDrgElements(
                DrgElement.newBuilder()
                    .setDecision(
                        Decision.newBuilder()
                            .setNode(Node.newBuilder().setId("decision-table-1"))
                            .setLogic(DecisionLogic.newBuilder().setDecisionTable(table))))
            .build();

    byte[] xml = new DmnWriter().write(definitions);
    Definitions readBack = new DmnXmlReader().read(xml);

    assertThat(new String(xml, StandardCharsets.UTF_8))
        .contains("hitPolicy=\"COLLECT\"")
        .contains("aggregation=\"SUM\"")
        .contains("preferredOrientation=\"Rule-as-Row\"")
        .contains("<inputExpression typeRef=\"number\"")
        .contains("<defaultOutputEntry><text>0</text></defaultOutputEntry>")
        .contains("<annotationEntry><text>adult</text></annotationEntry>");
    assertThat(readBack).isEqualTo(definitions);
  }

  @Test
  void writesAllBoxedExpressionFormsRoundTrip() {
    BoxedExpressionText context =
        BoxedExpressionText.newBuilder()
            .setContext(
                ContextText.newBuilder()
                    .addEntries(
                        ContextEntryText.newBuilder()
                            .setVariable(
                                InformationItem.newBuilder()
                                    .setNode(Node.newBuilder().setId("context-variable").setName("x")))
                            .setExpression(expression("1"))))
            .build();
    BoxedExpressionText list =
        BoxedExpressionText.newBuilder()
            .setList(
                ListExpressionText.newBuilder()
                    .addElements(expression("1"))
                    .addElements(expression("2")))
            .build();
    BoxedExpressionText relation =
        BoxedExpressionText.newBuilder()
            .setRelation(
                RelationText.newBuilder()
                    .addColumns(
                        RelationColumnText.newBuilder()
                            .setVariable(
                                InformationItem.newBuilder()
                                    .setNode(Node.newBuilder().setId("column-1").setName("amount"))))
                    .addRows(
                        RelationRowText.newBuilder().addExpressions(expression("100"))))
            .build();
    BoxedExpressionText function =
        BoxedExpressionText.newBuilder()
            .setFunctionDefinition(
                FunctionDefinitionText.newBuilder()
                    .addParameters(
                        InformationItem.newBuilder()
                            .setNode(Node.newBuilder().setId("function-parameter").setName("value")))
                    .setBody(expression("value + 1")))
            .build();
    Definitions definitions =
        Definitions.newBuilder()
            .setNode(Node.getDefaultInstance())
            .setNamespace("https://example.com/boxed")
            .addDrgElements(boxedDecision("context-decision", context))
            .addDrgElements(boxedDecision("list-decision", list))
            .addDrgElements(boxedDecision("relation-decision", relation))
            .addDrgElements(boxedDecision("function-decision", function))
            .build();

    byte[] xml = new DmnWriter().write(definitions);
    Definitions readBack = new DmnXmlReader().read(xml);

    assertThat(new String(xml, StandardCharsets.UTF_8))
        .contains("<context>")
        .contains("<contextEntry>")
        .contains("<list>")
        .contains("<relation>")
        .contains("<column>")
        .contains("<row>")
        .contains("<functionDefinition>")
        .contains("<formalParameter");
    assertThat(readBack).isEqualTo(definitions);
  }

  @Test
  void preservesDmnVersionAndNamespaceDeclarationsRoundTrip() {
    String dmn14 = "https://www.omg.org/spec/DMN/20211108/MODEL/";
    Definitions definitions =
        Definitions.newBuilder()
            .setNode(Node.getDefaultInstance())
            .setNamespace("https://example.com/model")
            .setModelNamespaceUri(dmn14)
            .addNamespaces(
                Namespace.newBuilder().setPrefix("vendor").setUri("https://example.com/vendor"))
            .build();

    byte[] xml = new DmnWriter().write(definitions);
    Definitions readBack = new DmnXmlReader().read(xml);

    assertThat(new String(xml, StandardCharsets.UTF_8))
        .contains("xmlns=\"" + dmn14 + "\"")
        .contains("xmlns:vendor=\"https://example.com/vendor\"");
    assertThat(readBack).isEqualTo(definitions);
  }

  @Test
  void rejectsConflictingNonDmnDefaultNamespace() {
    Definitions definitions =
        Definitions.newBuilder()
            .addNamespaces(
                Namespace.newBuilder().setUri("https://example.com/business-types"))
            .build();

    assertThatThrownBy(() -> new DmnWriter().write(definitions))
        .isInstanceOf(XmlWriteException.class)
        .hasMessageContaining("non-DMN default namespace");
  }

  @Test
  void preservesDocumentationAndStructuredExtensionsRoundTrip() {
    String vendorNamespace = "https://example.com/vendor";
    Node decisionNode =
        Node.newBuilder()
            .setId("documented-decision")
            .setName("Documented")
            .setDocumentation(Documentation.newBuilder().setText("Business documentation"))
            .setExtensionElements(
                ExtensionElements.newBuilder()
                    .addElement(
                        ExtensionElement.newBuilder()
                            .setNamespace(vendorNamespace)
                            .setName("audit")
                            .setValue("enabled")
                            .addAttribute(
                                ExtensionAttribute.newBuilder()
                                    .setName("mode")
                                    .setValue("strict"))))
            .build();
    Definitions definitions =
        Definitions.newBuilder()
            .setNode(Node.getDefaultInstance())
            .setNamespace("https://example.com/documented")
            .addNamespaces(Namespace.newBuilder().setPrefix("vendor").setUri(vendorNamespace))
            .addDrgElements(
                DrgElement.newBuilder()
                    .setDecision(Decision.newBuilder().setNode(decisionNode)))
            .build();

    byte[] xml = new DmnWriter().write(definitions);
    Definitions readBack = new DmnXmlReader().read(xml);

    assertThat(new String(xml, StandardCharsets.UTF_8))
        .contains("<documentation>Business documentation</documentation>")
        .contains("<vendor:audit mode=\"strict\">enabled</vendor:audit>");
    assertThat(readBack).isEqualTo(definitions);
  }

  private static ElementReference reference(String href) {
    return ElementReference.newBuilder().setHref(href).build();
  }

  private static Feel feel(String source) {
    return Feel.newBuilder().setText(FeelText.newBuilder().setText(source)).build();
  }

  private static ExpressionText expression(String source) {
    return ExpressionText.newBuilder()
        .setFeel(FeelText.newBuilder().setText(source))
        .build();
  }

  private static DrgElement boxedDecision(String id, BoxedExpressionText expression) {
    return DrgElement.newBuilder()
        .setDecision(
            Decision.newBuilder()
                .setNode(Node.newBuilder().setId(id))
                .setLogic(
                    DecisionLogic.newBuilder()
                        .setBoxedExpression(
                            BoxedExpression.newBuilder().setText(expression))))
        .build();
  }
}
