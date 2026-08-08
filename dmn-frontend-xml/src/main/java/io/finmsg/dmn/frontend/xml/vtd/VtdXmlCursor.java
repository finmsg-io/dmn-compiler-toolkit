package io.finmsg.dmn.frontend.xml.vtd;

import com.ximpleware.NavException;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;
import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.frontend.xml.XmlAttribute;
import io.finmsg.dmn.frontend.xml.exception.XmlException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

public final class VtdXmlCursor implements XmlCursor {

	private final VTDNav nav;
	private final byte[] xml;
	private final String systemId;
	private final boolean captureSourceLocations;
	private final Set<String> namespaceUris = new LinkedHashSet<>();
	private final Map<String, String> documentNamespaceDeclarations = new LinkedHashMap<>();
	private final String documentNamespaceUri;

	public VtdXmlCursor(Path path) {
		this(readAllBytes(path));
	}

	public VtdXmlCursor(InputStream in) {
		this(readAllBytes(in));
	}

	public VtdXmlCursor(byte[] xml) {
		this(xml, "", false);
	}

	public VtdXmlCursor(byte[] xml, String systemId, boolean captureSourceLocations) {
		Objects.requireNonNull(xml);
		this.xml = xml.clone();
		this.systemId = Objects.requireNonNullElse(systemId, "");
		this.captureSourceLocations = captureSourceLocations;
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

	// @Override
	// public boolean nextSibling(String localName) {
	// try {
	// return nav.toElement(VTDNav.NEXT_SIBLING, localName);
	// } catch (NavException ex) {
	// throw new XmlException(ex);
	// }
	// }
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
				if (nav.getTokenDepth(i) == 0) {
					String name = nav.toString(i);
					String declaredPrefix = "xmlns".equals(name) ? "" : name.substring("xmlns:".length());
					documentNamespaceDeclarations.put(declaredPrefix, uri);
				}
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
	public Optional<String> namespaceUri(String prefix) {
		String wanted = prefix == null || prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
		try {
			nav.push();
			do {
				int depth = nav.getCurrentDepth();
				for (int i = nav.getCurrentIndex() + 1; i < nav.getTokenCount(); i++) {
					int type = nav.getTokenType(i);
					if (nav.getTokenDepth(i) != depth
							|| (type != VTDNav.TOKEN_ATTR_NAME && type != VTDNav.TOKEN_ATTR_NS)) {
						break;
					}
					if (type == VTDNav.TOKEN_ATTR_NS && wanted.equals(nav.toString(i))) {
						String uri = nav.toString(i + 1);
						nav.pop();
						return Optional.of(uri);
					}
					i++;
				}
			} while (nav.toElement(VTDNav.PARENT));
			nav.pop();
			return Optional.empty();
		} catch (NavException ex) {
			throw new XmlException(ex);
		}
	}

	@Override
	public String documentNamespaceUri() {
		return documentNamespaceUri;
	}

	@Override
	public Map<String, String> documentNamespaceDeclarations() {
		return Collections.unmodifiableMap(documentNamespaceDeclarations);
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
	public List<XmlAttribute> attributes() {
		try {
			List<XmlAttribute> result = new ArrayList<>();
			int elementDepth = nav.getCurrentDepth();
			for (int i = nav.getCurrentIndex() + 1; i < nav.getTokenCount(); i++) {
				int type = nav.getTokenType(i);
				if (nav.getTokenDepth(i) != elementDepth
						|| (type != VTDNav.TOKEN_ATTR_NAME && type != VTDNav.TOKEN_ATTR_NS)) {
					break;
				}
				String qualifiedName = nav.toString(i);
				if ("xmlns".equals(qualifiedName) || qualifiedName.startsWith("xmlns:")) {
					i++;
					continue;
				}
				String localName = qualifiedName.contains(":")
						? qualifiedName.substring(qualifiedName.indexOf(':') + 1)
						: qualifiedName;
				String namespace = "";
				int valueToken = i + 1;
				if (qualifiedName.contains(":")) {
					for (String uri : namespaceUris) {
						if (nav.getAttrValNS(uri, localName) == valueToken) {
							namespace = uri;
							break;
						}
					}
				}
				result.add(new XmlAttribute(namespace, localName, nav.toString(valueToken)));
				i++;
			}
			return List.copyOf(result);
		} catch (NavException ex) {
			throw new XmlException(ex);
		}
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
	public int maximumDepth() {
		return nav.getNestingLevel();
	}

	@Override
	public int line() {
		int offset = offset();
		if (offset < 0)
			return -1;
		int line = 1;
		for (int i = 0; i < offset && i < xml.length; i++) {
			if (xml[i] == '\n')
				line++;
		}
		return line;
	}

	// --------------------------------------------------------------------
	// Lifecycle
	// --------------------------------------------------------------------

	@Override
	public int column() {
		int offset = offset();
		if (offset < 0)
			return -1;
		int column = 1;
		for (int i = offset - 1; i >= 0 && xml[i] != '\n'; i--)
			column++;
		return column;
	}

	@Override
	public int offset() {
		// VTD points at the first name character; source locations point at the opening
		// '<'.
		return Math.max(0, nav.getTokenOffset(nav.getCurrentIndex()) - 1);
	}

	@Override
	public String systemId() {
		return systemId;
	}

	@Override
	public boolean captureSourceLocations() {
		return captureSourceLocations;
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
