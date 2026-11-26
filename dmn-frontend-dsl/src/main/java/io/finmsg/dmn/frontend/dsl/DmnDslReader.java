package io.finmsg.dmn.frontend.dsl;

import io.finmsg.dmn.model.Definitions;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Public entry point for reading, parsing, formatting, and lowering
 * {@code .decision} DSL models into canonical DMN {@link Definitions}
 * protobufs.
 */
public final class DmnDslReader {

	public DslModel parse(String source) {
		return parse(source, DmnDslReadOptions.defaults());
	}

	public DslModel parse(String source, DmnDslReadOptions options) {
		return DmnDslParser.parse(source, options);
	}

	public Definitions read(String source) {
		return read(source, DmnDslReadOptions.defaults());
	}

	public Definitions read(String source, DmnDslReadOptions options) {
		return DmnDslLowerer.lower(parse(source, options));
	}

	public Definitions read(byte[] utf8Bytes, DmnDslReadOptions options) {
		Objects.requireNonNull(utf8Bytes, "utf8Bytes");
		Objects.requireNonNull(options, "options");
		if (utf8Bytes.length > options.maxBytes()) {
			throw new DmnDslParseException(new DmnDslDiagnostic("DSL-LIMIT-BYTES", 1, 1,
					"DSL source exceeds maxBytes=" + options.maxBytes() + " (actual=" + utf8Bytes.length + ")"));
		}
		return read(new String(utf8Bytes, StandardCharsets.UTF_8), options);
	}

	public String format(String source) {
		return DmnDslFormatter.format(parse(source));
	}

	/**
	 * Heuristic to distinguish {@code .decision} DSL sources from DMN XML sources.
	 */
	public static boolean isDslSource(String sourceId, byte[] content) {
		if (sourceId != null && sourceId.toLowerCase(java.util.Locale.ROOT).endsWith(".decision")) {
			return true;
		}
		if (content == null || content.length == 0) {
			return false;
		}
		String prefix = new String(content, 0, Math.min(content.length, 256), StandardCharsets.UTF_8).stripLeading();
		return !prefix.startsWith("<")
				&& (prefix.startsWith("model ") || prefix.startsWith("//") || prefix.startsWith("/*"));
	}
}
