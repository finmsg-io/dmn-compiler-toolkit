package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.TypeReference;
import java.util.Objects;

/** A successful resolution from a model use-site path to its declaration. */
public record DmnSymbolBinding(String referencePath, String declarationPath, String symbolName, String symbolId,
		DmnSymbolKind kind, TypeReference type, String targetNamespace) {

	public DmnSymbolBinding {
		Objects.requireNonNull(referencePath, "referencePath");
		Objects.requireNonNull(declarationPath, "declarationPath");
		Objects.requireNonNull(symbolName, "symbolName");
		Objects.requireNonNull(symbolId, "symbolId");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(targetNamespace, "targetNamespace");
	}

	public DmnSymbolBinding(String referencePath, String declarationPath, String symbolName, String symbolId,
			DmnSymbolKind kind, TypeReference type) {
		this(referencePath, declarationPath, symbolName, symbolId, kind, type, "");
	}
}
