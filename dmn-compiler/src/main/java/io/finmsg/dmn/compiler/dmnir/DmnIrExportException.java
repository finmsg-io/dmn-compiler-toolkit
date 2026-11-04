package io.finmsg.dmn.compiler.dmnir;

import java.util.Optional;

/** Export failed without producing a partial bundle. */
public final class DmnIrExportException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;
	public enum Category {
		UNSUPPORTED_VERSION, UNREPRESENTABLE_ARTIFACT
	}
	private final Category category;
	private final Optional<DmnIrRuntimeAdapter.ElementId> element;

	DmnIrExportException(Category category, DmnIrRuntimeAdapter.ElementId element) {
		super("DMNIR export failed: " + category);
		this.category = category;
		this.element = Optional.ofNullable(element);
	}
	public Category category() {
		return category;
	}
	public Optional<DmnIrRuntimeAdapter.ElementId> element() {
		return element;
	}
}
