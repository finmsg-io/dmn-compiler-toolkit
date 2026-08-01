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
}