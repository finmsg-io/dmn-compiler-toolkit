package io.finmsg.dmn.frontend.xml.dmn;

import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.DiagnosticSeverity;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class DmnXmlReaderTest {

    private static final String DMN = """
            <definitions
                xmlns="https://www.omg.org/spec/DMN/20211108/MODEL/"
                id="traffic"
                name="Traffic Rules"
                namespace="https://example.com"
                exporter="ChatGPT"
                exporterVersion="1.0">
            </definitions>
            """;

    @Test
    void readBytes() {
        Definitions definitions = new DmnXmlReader().read(DMN.getBytes());
        assertNotNull(definitions);
        assertEquals("traffic", definitions.getNode().getId());
        assertEquals("Traffic Rules", definitions.getNode().getName());
        assertEquals("https://example.com", definitions.getNamespace());
        assertEquals("ChatGPT", definitions.getExporter());
        assertEquals("1.0", definitions.getExporterVersion());
    }

    @Test
    void readsAllImportAttributesAndIgnoresForeignNamespaceCollisions() {
        String xml = """
                <definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/"
                    xmlns:ext="https://example.com/extension" namespace="https://example.com/model">
                  <ext:import namespace="https://example.com/not-dmn"/>
                  <import id="import-1" name="base" namespace="https://example.com/base"
                      locationURI="base.dmn"
                      importType="https://www.omg.org/spec/DMN/20230324/MODEL/"/>
                </definitions>
                """;

        Definitions definitions = new DmnXmlReader().read(xml.getBytes());

        assertEquals(1, definitions.getImportsCount());
        assertEquals("import-1", definitions.getImports(0).getNode().getId());
        assertEquals("", definitions.getImports(0).getNode().getName());
        assertEquals("base", definitions.getImports(0).getName());
        assertEquals("https://example.com/base", definitions.getImports(0).getNamespace());
        assertEquals("base.dmn", definitions.getImports(0).getLocationUri());
        assertEquals(
                "https://www.omg.org/spec/DMN/20230324/MODEL/",
                definitions.getImports(0).getImportType());
    }

    @Test
    void ignoresForeignNamespaceCollisionsInsideModelElements() {
        String xml = """
                <definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/"
                    xmlns:ext="https://example.com/extension" namespace="https://example.com/model">
                  <inputData id="input-1" name="Applicant">
                    <ext:variable id="wrong" name="Wrong" typeRef="number"/>
                    <variable id="right" name="Right" typeRef="string"/>
                  </inputData>
                </definitions>
                """;

        Definitions definitions = new DmnXmlReader().read(xml.getBytes());

        assertEquals(1, definitions.getDrgElementsCount());
        assertEquals(
                "right",
                definitions.getDrgElements(0).getInputData().getVariable().getNode().getId());
        assertEquals(
                "Right",
                definitions.getDrgElements(0).getInputData().getVariable().getNode().getName());
    }

    @Test
    void readsFunctionBodyAfterFormalParameters() {
        String xml = """
                <definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/"
                    xmlns:ext="https://example.com/extension" namespace="https://example.com/model">
                  <businessKnowledgeModel id="bkm-1" name="Double">
                    <encapsulatedLogic>
                      <ext:functionDefinition/>
                      <functionDefinition kind="FEEL">
                        <formalParameter id="parameter-1" name="value" typeRef="number"/>
                        <literalExpression><text>value * 2</text></literalExpression>
                      </functionDefinition>
                    </encapsulatedLogic>
                  </businessKnowledgeModel>
                </definitions>
                """;

        Definitions definitions = new DmnXmlReader().read(xml.getBytes());
        var function = definitions.getDrgElements(0).getBusinessKnowledgeModel().getFunction();

        assertEquals(1, function.getFormalParametersCount());
        assertEquals("value", function.getFormalParameters(0).getNode().getName());
        assertEquals("value * 2", function.getLogic().getText().getText());
    }

    @Test
    void returnsStructuredDiagnosticForMalformedXml() {
        DmnReadOptions options = new DmnReadOptions(1024, "memory:broken.dmn");

        DmnReadResult result =
                new DmnXmlReader().readResult("<definitions>".getBytes(), options);

        assertTrue(result.model().isEmpty());
        assertTrue(result.hasErrors());
        assertEquals("DMN-XML-003", result.diagnostics().get(0).getCode());
        assertEquals(
                DiagnosticSeverity.DIAGNOSTIC_SEVERITY_FATAL,
                result.diagnostics().get(0).getSeverity());
        assertEquals(
                "memory:broken.dmn",
                result.diagnostics().get(0).getLocation().getSystemId());
    }

    @Test
    void rejectsOversizedStreamsWithoutParsingThem() {
        byte[] xml = DMN.getBytes();
        DmnReadOptions options = new DmnReadOptions(16, "memory:large.dmn");

        DmnReadResult result =
                new DmnXmlReader().readResult(new ByteArrayInputStream(xml), options);

        assertTrue(result.model().isEmpty());
        assertEquals("DMN-XML-001", result.diagnostics().get(0).getCode());
        assertEquals(
                "memory:large.dmn",
                result.diagnostics().get(0).getLocation().getSystemId());
    }

    @Test
    void legacyReadApiStillThrowsForInvalidInput() {
        assertThrows(
                RuntimeException.class,
                () -> new DmnXmlReader().read("<definitions>".getBytes()));
    }
}
