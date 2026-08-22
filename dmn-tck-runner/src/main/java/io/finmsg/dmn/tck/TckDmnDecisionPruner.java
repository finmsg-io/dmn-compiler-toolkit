package io.finmsg.dmn.tck;

import io.finmsg.dmn.compiler.DmnSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Retains selected TCK result decisions and their declared DRG dependencies.
 */
public final class TckDmnDecisionPruner {
	public DmnSource prune(DmnSource source, Set<String> resultNames) {
		if (resultNames.isEmpty())
			return source;
		try {
			DocumentBuilderFactory builders = DocumentBuilderFactory.newInstance();
			builders.setNamespaceAware(true);
			builders.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			builders.setFeature("http://xml.org/sax/features/external-general-entities", false);
			builders.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			builders.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			builders.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			Document document = builders.newDocumentBuilder().parse(new ByteArrayInputStream(source.content()));
			Element definitions = document.getDocumentElement();
			List<Element> drgElements = directDrgElements(definitions);
			Map<String, Element> byId = new HashMap<>();
			Set<String> retainedIds = new HashSet<>();
			for (Element element : drgElements) {
				String id = element.getAttribute("id");
				if (!id.isBlank())
					byId.put(id, element);
				if (element.getLocalName().equals("inputData") || resultNames.contains(element.getAttribute("name")))
					retainedIds.add(id);
			}
			boolean changed;
			do {
				changed = false;
				for (Element element : drgElements) {
					if (!element.getLocalName().equals("decisionService"))
						continue;
					for (Element descendant : descendants(element)) {
						if (!descendant.getLocalName().equals("outputDecision"))
							continue;
						String targetId = referencedId(descendant.getAttribute("href"));
						if (retainedIds.contains(targetId)) {
							changed |= retainedIds.add(element.getAttribute("id"));
							break;
						}
					}
				}
				for (String id : List.copyOf(retainedIds)) {
					Element element = byId.get(id);
					if (element == null)
						continue;
					for (Element descendant : descendants(element)) {
						String href = descendant.getAttribute("href");
						String targetId = referencedId(href);
						if (!targetId.isBlank() && byId.containsKey(targetId))
							changed |= retainedIds.add(targetId);
					}
				}
			} while (changed);
			for (Element element : drgElements) {
				if (!retainedIds.contains(element.getAttribute("id")))
					definitions.removeChild(element);
			}
			TransformerFactory transformers = TransformerFactory.newInstance();
			transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
			var transformer = transformers.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			transformer.transform(new DOMSource(document), new StreamResult(output));
			return new DmnSource(source.id(), output.toByteArray());
		} catch (Exception exception) {
			throw new IllegalArgumentException("Cannot prune TCK model " + source.id(), exception);
		}
	}

	private static String referencedId(String href) {
		int hash = href.lastIndexOf('#');
		return hash >= 0 ? href.substring(hash + 1) : href;
	}

	private static List<Element> directDrgElements(Element definitions) {
		List<Element> result = new ArrayList<>();
		for (Node node = definitions.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element && isDrgElement(element.getLocalName()))
				result.add(element);
		}
		return result;
	}

	private static boolean isDrgElement(String localName) {
		return Set.of("inputData", "decision", "businessKnowledgeModel", "knowledgeSource", "decisionService")
				.contains(localName);
	}

	private static List<Element> descendants(Element root) {
		List<Element> result = new ArrayList<>();
		var nodes = root.getElementsByTagNameNS("*", "*");
		for (int index = 0; index < nodes.getLength(); index++)
			if (nodes.item(index) instanceof Element element)
				result.add(element);
		return result;
	}
}
