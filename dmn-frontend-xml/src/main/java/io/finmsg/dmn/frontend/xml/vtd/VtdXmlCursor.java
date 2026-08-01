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
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class VtdXmlCursor implements XmlCursor {

  private final VTDNav nav;
  private final Set<String> namespaceUris = new LinkedHashSet<>();
  private final String documentNamespaceUri;

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
      documentNamespaceUri = namespaceUri();
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
      String namespace = namespaceUri();
      if (!nav.toElement(VTDNav.FIRST_CHILD)) {
        return false;
      }

      do {
        if (localName().equals(wanted) && namespace.equals(namespaceUri())) {
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
      String namespace = namespaceUri();
      nav.push();

      while (nav.toElement(VTDNav.NEXT_SIBLING)) {
        if (localName().equals(wanted) && namespace.equals(namespaceUri())) {
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
      String namespace = namespaceUri();
      if (!firstChild(localName)) {
        return false;
      }
      if (!namespace.equals(namespaceUri())) {
        throw new XmlException("Namespace-aware child navigation failed.");
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
        String uri = nav.toString(i + 1);
        namespaceUris.add(uri);
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
    try {
      for (String uri : namespaceUris) {
        if (nav.matchElementNS(uri, localName())) {
          return uri;
        }
      }
      if (nav.matchElementNS(null, localName())) {
        return "";
      }
    } catch (NavException ex) {
      throw new XmlException(ex);
    }
    throw new XmlException("Cannot resolve namespace for <" + elementName() + ">");
  }

  @Override
  public String documentNamespaceUri() {
    return documentNamespaceUri;
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
