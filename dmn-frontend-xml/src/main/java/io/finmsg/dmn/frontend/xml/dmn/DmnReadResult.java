package io.finmsg.dmn.frontend.xml.dmn;

import io.finmsg.dmn.frontend.xml.exception.XmlReadException;
import io.finmsg.dmn.model.Definitions;
import io.finmsg.dmn.model.Diagnostic;
import io.finmsg.dmn.model.DiagnosticSeverity;
import java.util.List;
import java.util.Optional;

/** Result of reading DMN XML, including structured frontend diagnostics. */
public record DmnReadResult(Definitions definitions, List<Diagnostic> diagnostics) {

  public DmnReadResult {
    diagnostics = List.copyOf(diagnostics);
  }

  public Optional<Definitions> model() {
    return Optional.ofNullable(definitions);
  }

  public boolean hasErrors() {
    return diagnostics.stream()
        .anyMatch(
            diagnostic ->
                diagnostic.getSeverity() == DiagnosticSeverity.DIAGNOSTIC_SEVERITY_ERROR
                    || diagnostic.getSeverity()
                        == DiagnosticSeverity.DIAGNOSTIC_SEVERITY_FATAL);
  }

  public Definitions requireModel() {
    if (definitions != null && !hasErrors()) {
      return definitions;
    }
    String message = diagnostics.isEmpty() ? "DMN XML did not produce a model."
        : diagnostics.get(0).getMessage();
    throw new XmlReadException(message);
  }
}
