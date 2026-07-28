package io.finmsg.dmn.frontend.xml.dmn;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.frontend.xml.dmn.reader.DefinitionsReader;
import io.finmsg.dmn.frontend.xml.exception.XmlException;
import io.finmsg.dmn.frontend.xml.vtd.VtdXmlCursor;
import io.finmsg.dmn.model.Definitions;
import java.io.InputStream;
import java.nio.file.Path;

public final class DmnXmlReader {

  public Definitions read(Path xml) {

    try (XmlCursor cursor = new VtdXmlCursor(xml)) {
      return read(cursor);
    }
  }

  public Definitions read(InputStream in) {

    try (XmlCursor cursor = new VtdXmlCursor(in)) {
      return read(cursor);
    }
  }

  public Definitions read(byte[] xml) {

    try (XmlCursor cursor = new VtdXmlCursor(xml)) {
      return read(cursor);
    }
  }

  private Definitions read(XmlCursor cursor) {

    if (!cursor.toRoot()) {
      throw new XmlException("Empty XML document.");
    }

    DmnXmlContext context = new DmnVersionDetector().detect(cursor);

    return new DefinitionsReader(context).read(cursor);
  }
}
