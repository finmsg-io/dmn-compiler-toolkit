package io.finmsg.dmn.frontend.xml.dmn;

import io.finmsg.dmn.frontend.xml.XmlEmitter;
import io.finmsg.dmn.frontend.xml.dmn.writer.DefinitionsWriter;
import io.finmsg.dmn.frontend.xml.exception.XmlWriteException;
import io.finmsg.dmn.model.Definitions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DmnWriter {

  private final DefinitionsWriter definitionsWriter = new DefinitionsWriter();

  public void write(Path path, Definitions definitions) {
    try (OutputStream output = Files.newOutputStream(path)) {
      write(output, definitions);
    } catch (IOException e) {
      throw new XmlWriteException("Could not write DMN file: " + path, e);
    }
  }

  public void write(OutputStream output, Definitions definitions) {
    try (XmlEmitter xml = new XmlEmitter(output)) {
      xml.startDocument();
      definitionsWriter.write(xml, definitions);
      xml.endDocument();
      xml.flush();
    }
  }

  public byte[] write(Definitions definitions) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    write(output, definitions);
    return output.toByteArray();
  }
}
