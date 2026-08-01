package io.finmsg.dmn.frontend.xml.dmn;

import static org.junit.jupiter.api.Assertions.*;

import io.finmsg.dmn.model.Definitions;
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
}
