package io.finmsg.dmn.compiler.dmnir;

import io.finmsg.dmn.compiler.DmnCompilerDiagnostic;
import java.util.List;
import java.util.Optional;

/** Failure to produce a complete artifact; no partial output is returned. */
public final class DmnIrProductionException extends IllegalArgumentException {
	public enum Category {
		SOURCE_INVALID, UNSUPPORTED_PROFILE, ARTIFACT_INVALID
	}
	private final Category category;
	private final Optional<DmnIrRuntimeAdapter.ElementId> element;
	private final List<DmnCompilerDiagnostic> diagnostics;

	DmnIrProductionException(Category category, DmnIrRuntimeAdapter.ElementId element,
			List<DmnCompilerDiagnostic> diagnostics, Throwable cause) {
		super("DMNIR production failed: " + category, cause);
		this.category = category;
		this.element = Optional.ofNullable(element);
		this.diagnostics = List.copyOf(diagnostics);
	}

	public Category category() {
		return category;
	}
	public Optional<DmnIrRuntimeAdapter.ElementId> element() {
		return element;
	}
	public List<DmnCompilerDiagnostic> diagnostics() {
		return diagnostics;
	}
}
