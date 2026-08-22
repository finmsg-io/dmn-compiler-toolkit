package io.finmsg.dmn.tck;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.temporal.TemporalAmount;
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

/**
 * Secure, namespace-aware decoder for official OMG DMN TCK test-case XML
 * formats.
 */
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
				java.util.Set<String> expectedErrors = new java.util.LinkedHashSet<>();
				for (Element resultNode : children(testCase, "resultNode"))
					if ("true".equals(resultNode.getAttribute("errorResult")))
						expectedErrors.add(requiredAttribute(resultNode, "name"));
				String invocableName = testCase.getAttribute("invocableName");
				String type = testCase.getAttribute("type");
				result.add(new TckTestCase(requiredAttribute(testCase, "id"), testCase.getAttribute("name"),
						namedValues(testCase, "inputNode"), namedValues(testCase, "resultNode"), expectedErrors,
						invocableName.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(invocableName),
						type.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(type)));
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
			TckValue val = decodeNode(node);
			if (values.putIfAbsent(name, val) != null) {
				throw new IOException("Duplicate " + elementName + " name '" + name + "'");
			}
		}
		return values;
	}

	private static TckValue decodeNode(Element node) throws IOException {
		List<Element> components = children(node, "component");
		if (!components.isEmpty()) {
			Map<String, Object> map = new LinkedHashMap<>();
			for (Element comp : components) {
				String name = comp.getAttribute("name");
				map.put(name, decodeNode(comp).runtimeValue());
			}
			return TckValue.context(map);
		}

		List<Element> lists = children(node, "list");
		if (!lists.isEmpty()) {
			List<Object> items = new ArrayList<>();
			for (Element item : children(lists.getFirst(), "item")) {
				items.add(decodeNode(item).runtimeValue());
			}
			return TckValue.list(items);
		}

		List<Element> values = children(node, "value");
		if (!values.isEmpty()) {
			return decodeValue(values.getFirst());
		}

		List<Element> expecteds = children(node, "expected");
		if (!expecteds.isEmpty()) {
			return decodeNode(expecteds.getFirst());
		}

		return TckValue.nullValue();
	}

	private static TckValue decodeValue(Element value) throws IOException {
		if ("true".equals(value.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "nil"))) {
			return TckValue.nullValue();
		}
		String type = value.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type");
		String localType = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
		String text = value.getTextContent().trim();
		return switch (localType) {
			case "boolean" -> booleanValue(text);
			case "decimal", "integer", "double" -> TckValue.number(text);
			case "date" -> TckValue.date(LocalDate.parse(text));
			case "time" -> TckValue.time(hasTimeOffset(text) ? OffsetTime.parse(text) : LocalTime.parse(text));
			case "dateTime",
					"dateAndTime" ->
				TckValue.dateTime(hasTimeOffset(text.substring(text.indexOf('T') + 1))
						? OffsetDateTime.parse(text)
						: LocalDateTime.parse(text));
			case "duration", "yearsAndMonthsDuration", "daysAndTimeDuration" ->
				TckValue.duration(parseTckDuration(text, localType));
			case "string", "" -> TckValue.string(value.getTextContent());
			default -> TckValue.string(text);
		};
	}

	private static TemporalAmount parseTckDuration(String text, String type) {
		if ("yearsAndMonthsDuration".equals(type)) {
			return Period.parse(text);
		}
		if ("daysAndTimeDuration".equals(type)) {
			return Duration.parse(text);
		}
		if (text.contains("Y") || (text.contains("M") && !text.contains("T"))) {
			return Period.parse(text);
		}
		return Duration.parse(text);
	}

	private static boolean hasTimeOffset(String text) {
		return text.endsWith("Z") || text.indexOf('+') > 0 || text.indexOf('-', 1) > 0;
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
			if (node instanceof Element element && localName.equals(element.getLocalName()))
				result.add(element);
		}
		return result;
	}

	private static String requiredAttribute(Element element, String name) throws IOException {
		String value = element.getAttribute(name);
		if (value.isBlank())
			throw new IOException(element.getLocalName() + " requires attribute " + name);
		return value;
	}
}
