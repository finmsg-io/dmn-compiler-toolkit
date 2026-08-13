package io.finmsg.dmn.frontend.xml.dmn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DmnXmlConformanceSecurityTest {

	@ParameterizedTest
	@MethodSource("supportedDmnNamespaces")
	void readsAndWritesSupportedDmnVocabularyVersions(String namespace, boolean implicitDefault) {
		String source = "<definitions xmlns=\"" + namespace
				+ "\" id=\"model\" namespace=\"https://example.com/model\"/>";

		var definitions = new DmnXmlReader().read(source.getBytes(StandardCharsets.UTF_8));
		byte[] written = new DmnWriter().write(definitions);
		var readBack = new DmnXmlReader().read(written);

		assertEquals(implicitDefault ? "" : namespace, definitions.getModelNamespaceUri());
		assertEquals(definitions, readBack);
		assertTrue(new String(written, StandardCharsets.UTF_8).contains(namespace));
	}

	static Stream<Arguments> supportedDmnNamespaces() {
		return Stream.of(Arguments.of("https://www.omg.org/spec/DMN/20180521/MODEL/", false),
				Arguments.of("https://www.omg.org/spec/DMN/20191111/MODEL/", false),
				Arguments.of("https://www.omg.org/spec/DMN/20211108/MODEL/", false),
				Arguments.of("https://www.omg.org/spec/DMN/20230324/MODEL/", true),
				Arguments.of("https://www.omg.org/spec/DMN/20240513/MODEL/", false));
	}

	@Test
	void rejectsExternalEntityAndDtdWithoutResolvingThem() {
		String source = """
				<!DOCTYPE definitions [
				  <!ENTITY secret SYSTEM "file:///definitely-not-readable/dmn-secret.txt">
				]>
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/"
				  namespace="https://example.com/model">
				  <description>&secret;</description>
				</definitions>
				""";

		DmnReadResult result = read(source.getBytes(StandardCharsets.UTF_8), "memory:xxe.dmn");

		assertEquals("DMN-XML-005", result.diagnostics().get(0).getCode());
		assertTrue(result.model().isEmpty());
	}

	@Test
	void rejectsUtf16DtdDeclaration() {
		String source = """
				<?xml version="1.0" encoding="UTF-16LE"?>
				<!DOCTYPE definitions [<!ENTITY value "expanded">]>
				<definitions xmlns="https://www.omg.org/spec/DMN/20230324/MODEL/"/>
				""";

		DmnReadResult result = read(source.getBytes(StandardCharsets.UTF_16LE), "memory:utf16-xxe.dmn");

		assertEquals("DMN-XML-005", result.diagnostics().get(0).getCode());
	}

	@Test
	void rejectsDocumentsBeyondConfiguredElementDepth() {
		StringBuilder source = new StringBuilder("<definitions xmlns=\"https://www.omg.org/spec/DMN/20230324/MODEL/\"");
		source.append(" xmlns:ext=\"https://example.com/ext\">");
		for (int index = 0; index < 12; index++)
			source.append("<ext:level>");
		for (int index = 0; index < 12; index++)
			source.append("</ext:level>");
		source.append("</definitions>");
		DmnReadOptions options = new DmnReadOptions(8192, "memory:deep.dmn").withMaxElementDepth(8);

		DmnReadResult result = new DmnXmlReader().readResult(source.toString().getBytes(StandardCharsets.UTF_8),
				options);

		assertEquals("DMN-XML-006", result.diagnostics().get(0).getCode());
		assertTrue(result.model().isEmpty());
	}

	@Test
	void rejectsMalformedUtf8() {
		byte[] prefix = "<definitions xmlns=\"https://www.omg.org/spec/DMN/20230324/MODEL/\" name=\""
				.getBytes(StandardCharsets.UTF_8);
		byte[] suffix = "\"/>".getBytes(StandardCharsets.UTF_8);
		byte[] source = new byte[prefix.length + 2 + suffix.length];
		System.arraycopy(prefix, 0, source, 0, prefix.length);
		source[prefix.length] = (byte) 0xC3;
		source[prefix.length + 1] = 0x28;
		System.arraycopy(suffix, 0, source, prefix.length + 2, suffix.length);

		DmnReadResult result = read(source, "memory:malformed-utf8.dmn");

		assertEquals("DMN-XML-003", result.diagnostics().get(0).getCode());
		assertTrue(result.model().isEmpty());
	}

	@Test
	void acceptsInputExactlyAtConfiguredByteLimit() {
		byte[] source = "<definitions xmlns=\"https://www.omg.org/spec/DMN/20230324/MODEL/\"/>"
				.getBytes(StandardCharsets.UTF_8);
		DmnReadOptions options = new DmnReadOptions(source.length, "memory:exact-limit.dmn");

		DmnReadResult result = new DmnXmlReader().readResult(source, options);

		assertTrue(result.model().isPresent());
		assertFalse(result.hasErrors());
	}

	private static DmnReadResult read(byte[] source, String systemId) {
		return new DmnXmlReader().readResult(source, new DmnReadOptions(8192, systemId));
	}
}
