package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.SourceLocation;

/** A semantic error associated with a precise location in the DMN/FEEL model. */
public record DmnSemanticDiagnostic(
    String code,
    String path,
    String message,
    SourceLocation sourceLocation) {
}
