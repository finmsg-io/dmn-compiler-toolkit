package io.finmsg.dmn.tck;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Secure, namespace-aware decoder for the scalar subset of the DMN TCK test format. */
public final class TckTestCaseReader {
  public List<TckTestCase> read(Path path) throws IOException {
    try (InputStream input = Files.newInputStream(path)) {
      return read(input);
    }
  }

  public List<TckTestCase> read(InputStream input) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      Element root = factory.newDocumentBuilder().parse(input).getDocumentElement();
      List<TckTestCase> result = new ArrayList<>();
      for (Element testCase : children(root, "testCase")) {
        result.add(new TckTestCase(
            requiredAttribute(testCase, "id"), testCase.getAttribute("name"),
            namedValues(testCase, "inputNode"), namedValues(testCase, "resultNode")));
      }
      return List.copyOf(result);
    } catch (ParserConfigurationException | SAXException exception) {
      throw new IOException("Invalid TCK test-case XML", exception);
    }
  }

  private static Map<String, TckValue> namedValues(Element testCase, String elementName) throws IOException {
    Map<String, TckValue> values = new LinkedHashMap<>();
    for (Element node : children(testCase, elementName)) {
      String name = requiredAttribute(node, "name");
      Element value = descendants(node, "value").stream().findFirst()
          .orElseThrow(() -> new IOException(elementName + " '" + name + "' has no scalar value"));
      if (values.putIfAbsent(name, scalar(value)) != null) {
        throw new IOException("Duplicate " + elementName + " name '" + name + "'");
      }
    }
    return values;
  }

  private static TckValue scalar(Element value) throws IOException {
    if ("true".equals(value.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "nil"))) {
      return TckValue.nullValue();
    }
    String type = value.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type");
    String localType = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
    String text = value.getTextContent();
    return switch (localType) {
      case "boolean" -> booleanValue(text);
      case "decimal", "integer", "double" -> TckValue.number(text.trim());
      case "string", "" -> TckValue.string(text);
      default -> throw new IOException("Unsupported TCK scalar type '" + type + "'");
    };
  }

  private static TckValue booleanValue(String text) throws IOException {
    return switch (text.trim()) {
      case "true", "1" -> TckValue.bool(true);
      case "false", "0" -> TckValue.bool(false);
      default -> throw new IOException("Invalid TCK boolean value '" + text + "'");
    };
  }

  private static List<Element> children(Element parent, String localName) {
    List<Element> result = new ArrayList<>();
    NodeList nodes = parent.getChildNodes();
    for (int index = 0; index < nodes.getLength(); index++) {
      Node node = nodes.item(index);
      if (node instanceof Element element && localName.equals(element.getLocalName())) result.add(element);
    }
    return result;
  }

  private static List<Element> descendants(Element parent, String localName) {
    NodeList nodes = parent.getElementsByTagNameNS("*", localName);
    List<Element> result = new ArrayList<>();
    for (int index = 0; index < nodes.getLength(); index++) result.add((Element) nodes.item(index));
    return result;
  }

  private static String requiredAttribute(Element element, String name) throws IOException {
    String value = element.getAttribute(name);
    if (value.isBlank()) throw new IOException(element.getLocalName() + " requires attribute " + name);
    return value;
  }
}
