package io.finmsg.dmn.frontend.xml.dmn;

import io.finmsg.dmn.frontend.xml.XmlCursor;
import io.finmsg.dmn.frontend.xml.dmn.reader.DefinitionsReader;
import io.finmsg.dmn.frontend.xml.exception.XmlException;
import io.finmsg.dmn.frontend.xml.vtd.VtdXmlCursor;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.Diagnostic;
import io.finmsg.dmn.model.DiagnosticSeverity;
import io.finmsg.dmn.model.SourceLocation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class DmnXmlReader {

	public Definitions read(Path xml) {
		Objects.requireNonNull(xml);
		return readResult(xml, DmnReadOptions.defaults().withSystemId(xml.toUri().toString())).requireModel();
	}

	public Definitions read(InputStream in) {
		return readResult(in, DmnReadOptions.defaults()).requireModel();
	}

	public Definitions read(byte[] xml) {
		return readResult(xml, DmnReadOptions.defaults()).requireModel();
	}

	public DmnReadResult readResult(Path path, DmnReadOptions options) {
		Objects.requireNonNull(path);
		Objects.requireNonNull(options);
		DmnReadOptions effective = options.systemId().isEmpty()
				? options.withSystemId(path.toUri().toString())
				: options;
		try {
			if (Files.size(path) > effective.maxInputBytes()) {
				return failure("DMN-XML-001", "DMN XML exceeds the configured input limit.", effective);
			}
			return readResult(Files.readAllBytes(path), effective);
		} catch (IOException exception) {
			return failure("DMN-XML-002", "Could not read DMN XML: " + path, effective);
		}
	}

	public DmnReadResult readResult(InputStream input, DmnReadOptions options) {
		Objects.requireNonNull(input);
		Objects.requireNonNull(options);
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			long total = 0;
			int count;
			while ((count = input.read(buffer)) != -1) {
				total += count;
				if (total > options.maxInputBytes()) {
					return failure("DMN-XML-001", "DMN XML exceeds the configured input limit.", options);
				}
				output.write(buffer, 0, count);
			}
			return readResult(output.toByteArray(), options);
		} catch (IOException exception) {
			return failure("DMN-XML-002", "Could not read DMN XML input.", options);
		}
	}

	public DmnReadResult readResult(byte[] xml, DmnReadOptions options) {
		Objects.requireNonNull(xml);
		Objects.requireNonNull(options);
		if (xml.length > options.maxInputBytes()) {
			return failure("DMN-XML-001", "DMN XML exceeds the configured input limit.", options);
		}
		if (containsAsciiIgnoringNulls(xml, "<!DOCTYPE") || containsAsciiIgnoringNulls(xml, "<!ENTITY")) {
			return failure("DMN-XML-005", "DTD and entity declarations are prohibited in DMN XML.", options);
		}
		try (XmlCursor cursor = new VtdXmlCursor(xml, options.systemId(), options.captureSourceLocations())) {
			if (cursor.maximumDepth() > options.maxElementDepth()) {
				return failure("DMN-XML-006", "DMN XML exceeds the configured element-depth limit.", options);
			}
			return new DmnReadResult(read(cursor), List.of());
		} catch (UnsupportedDmnXmlException exception) {
			return failure("DMN-XML-004", exception.getMessage(), options);
		} catch (RuntimeException exception) {
			String message = exception.getMessage() == null ? "Cannot parse DMN XML." : exception.getMessage();
			return failure("DMN-XML-003", message, options);
		}
	}

	private static boolean containsAsciiIgnoringNulls(byte[] input, String wanted) {
		int matched = 0;
		for (byte value : input) {
			int character = value & 0xff;
			if (character == 0) {
				continue;
			}
			if (character >= 'a' && character <= 'z') {
				character -= 'a' - 'A';
			}
			if (character == wanted.charAt(matched)) {
				matched++;
				if (matched == wanted.length()) {
					return true;
				}
			} else {
				matched = character == wanted.charAt(0) ? 1 : 0;
			}
		}
		return false;
	}

	private Definitions read(XmlCursor cursor) {

		if (!cursor.toRoot()) {
			throw new XmlException("Empty XML document.");
		}

		DmnXmlContext context = new DmnVersionDetector().detect(cursor);

		return new DefinitionsReader(context).read(cursor);
	}

	private static DmnReadResult failure(String code, String message, DmnReadOptions options) {
		Diagnostic diagnostic = Diagnostic.newBuilder().setSeverity(DiagnosticSeverity.DIAGNOSTIC_SEVERITY_FATAL)
				.setCode(code).setMessage(message)
				.setLocation(SourceLocation.newBuilder().setSystemId(options.systemId())).build();
		return new DmnReadResult(null, List.of(diagnostic));
	}
}
