package io.finmsg.dmn.feel.parser;

import io.finmsg.dmn.model.SourceLocation;
import java.util.List;

/**
 * FEEL syntax diagnostics enriched with the path and location of the owning DMN
 * element.
 */
public record DmnFeelDiagnostic(String path, String source, SourceLocation sourceLocation,
		List<FeelDiagnostic> diagnostics) {

	public DmnFeelDiagnostic {
		diagnostics = List.copyOf(diagnostics);
	}
}
