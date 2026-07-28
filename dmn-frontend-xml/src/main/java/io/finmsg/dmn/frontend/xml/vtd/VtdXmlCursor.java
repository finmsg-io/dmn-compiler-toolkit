package io.finmsg.dmn.frontend.xml.vtd;

import com.ximpleware.NavException;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;
import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.frontend.xml.exception.XmlException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class VtdXmlCursor implements XmlCursor {

  private final VTDNav nav;
  private final Map<String, String> namespaces = new HashMap<>();

  public VtdXmlCursor(Path path) {
    this(readAllBytes(path));
  }

  public VtdXmlCursor(InputStream in) {
    this(readAllBytes(in));
  }

  public VtdXmlCursor(byte[] xml) {
    Objects.requireNonNull(xml);
    try {
      VTDGen vg = new VTDGen();
      vg.setDoc(xml);
      vg.parse(true);
      nav = vg.getNav();
      loadNamespaces();
    } catch (Exception ex) {
      throw new XmlException("Cannot parse XML.", ex);
    }
  }

  // --------------------------------------------------------------------
  // Navigation
  // --------------------------------------------------------------------

  private static byte[] readAllBytes(Path path) {
    try {
      return Files.readAllBytes(path);
    } catch (IOException ex) {
      throw new XmlException(ex);
    }
  }

  private static byte[] readAllBytes(InputStream in) {

    try {
      return in.readAllBytes();
    } catch (IOException ex) {
      throw new XmlException(ex);
    }
  }

  @Override
  public boolean toRoot() {
    try {
      return nav.toElement(VTDNav.ROOT);
    } catch (NavException ex) {
      throw new XmlException(ex);
    }
  }

  @Override
  public boolean firstChild() {
    try {
      return nav.toElement(VTDNav.FIRST_CHILD);
    } catch (NavException ex) {
      throw new XmlException(ex);
    }
  }

  @Override
  public boolean firstChild(String wanted) {

    try {
      if (!nav.toElement(VTDNav.FIRST_CHILD)) {
        return false;
      }

      do {
        if (localName().equals(wanted)) {
          return true;
        }
      } while (nav.toElement(VTDNav.NEXT_SIBLING));

      nav.toElement(VTDNav.PARENT);
      return false;

    } catch (NavException ex) {
      throw new XmlException(ex);
    }
  }

  @Override
  public boolean nextSibling() {
    try {
      return nav.toElement(VTDNav.NEXT_SIBLING);
    } catch (NavException ex) {
      throw new XmlException(ex);
    }
  }

  //  @Override
  //  public boolean nextSibling(String localName) {
  //    try {
  //      return nav.toElement(VTDNav.NEXT_SIBLING, localName);
  //    } catch (NavException ex) {
  //      throw new XmlException(ex);
  //    }
  //  }
  @Override
  public boolean nextSibling(String wanted) {

    try {
      nav.push();

      while (nav.toElement(VTDNav.NEXT_SIBLING)) {
        if (localName().equals(wanted)) {
          nav.pop(); // discard saved position
          return true;
        }
      }

      nav.pop(); // restore original position
      return false;

    } catch (NavException ex) {
      throw new XmlException(ex);
    }
  }

  // --------------------------------------------------------------------
  // Element information
  // --------------------------------------------------------------------

  @Override
  public boolean parent() {
    try {
      return nav.toElement(VTDNav.PARENT);
    } catch (NavException ex) {
      throw new XmlException(ex);
    }
  }

  @Override
  public boolean hasChild(String localName) {
    try {
      if (!nav.toElement(VTDNav.FIRST_CHILD, localName)) {
        return false;
      }
      nav.toElement(VTDNav.PARENT);
      return true;
    } catch (NavException ex) {
      throw new XmlException(ex);
    }
  }

  @Override
  public String elementName() {
    try {
      return nav.toString(nav.getCurrentIndex());
    } catch (NavException ex) {
      throw new XmlException(ex);
    }
  }

  @Override
  public String localName() {
    String name = elementName();
    int idx = name.indexOf(':');
    return idx < 0 ? name : name.substring(idx + 1);
  }

  // --------------------------------------------------------------------
  // Namespace
  // --------------------------------------------------------------------

  @Override
  public String prefix() {
    String name = elementName();
    int idx = name.indexOf(':');
    return idx < 0 ? "" : name.substring(0, idx);
  }

  @Override
  public boolean isElement(String localName) {
    return localName().equals(this.localName());
  }

  private void loadNamespaces() {
    try {
      nav.push();
      nav.toElement(VTDNav.ROOT);
      int count = nav.getTokenCount();
      for (int i = 0; i < count; i++) {
        if (nav.getTokenType(i) != VTDNav.TOKEN_ATTR_NS) {
          continue;
        }
        String name = nav.toString(i);
        String prefix;
        if ("xmlns".equals(name)) {
          prefix = "";
        } else if (name.startsWith("xmlns:")) {
          prefix = name.substring(6);
        } else {
          continue;
        }
        String uri = nav.toString(i + 1);
        namespaces.put(prefix, uri);
      }
      nav.pop();
    } catch (Exception ex) {
      throw new XmlException(ex);
    }
  }

  // --------------------------------------------------------------------
  // Attributes
  // --------------------------------------------------------------------

  @Override
  public String namespaceUri() {
    String uri = namespaces.get(prefix());
    if (uri == null) {
      throw new XmlException("Unknown namespace prefix '" + prefix() + "'");
    }
    return uri;
  }

  @Override
  public Optional<String> attribute(String name) {
    try {
      int index = nav.getAttrVal(name);
      if (index == -1) {
        return Optional.empty();
      }
      return Optional.of(nav.toString(index));
    } catch (NavException ex) {
      throw new XmlException(ex);
    }
  }

  @Override
  public String requiredAttribute(String name) {
    return attribute(name).orElseThrow(() -> new XmlException("Missing attribute '" + name + "'"));
  }

  @Override
  public boolean hasAttribute(String name) {
    return attribute(name).isPresent();
  }

  @Override
  public int attributeAsInt(String name) {
    return Integer.parseInt(requiredAttribute(name));
  }

  // --------------------------------------------------------------------
  // Text
  // --------------------------------------------------------------------

  @Override
  public long attributeAsLong(String name) {
    return Long.parseLong(requiredAttribute(name));
  }

  @Override
  public boolean attributeAsBoolean(String name) {
    return Boolean.parseBoolean(requiredAttribute(name));
  }

  @Override
  public String text() {
    return optionalText().orElse("");
  }

  // --------------------------------------------------------------------
  // Diagnostics
  // --------------------------------------------------------------------

  @Override
  public Optional<String> optionalText() {
    try {
      int token = nav.getText();
      if (token == -1) {
        return Optional.empty();
      }
      return Optional.of(nav.toString(token));
    } catch (NavException ex) {
      throw new XmlException(ex);
    }
  }

  @Override
  public boolean hasText() {
    return optionalText().isPresent();
  }

  @Override
  public int depth() {
    return nav.getCurrentDepth();
  }

  @Override
  public int line() {
    return -1;
  }

  // --------------------------------------------------------------------
  // Lifecycle
  // --------------------------------------------------------------------

  @Override
  public int column() {
    return -1;
  }

  // --------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------

  @Override
  public String path() {
    return "/" + elementName();
  }

  @Override
  public void close() {
    // nothing to close
  }
}
