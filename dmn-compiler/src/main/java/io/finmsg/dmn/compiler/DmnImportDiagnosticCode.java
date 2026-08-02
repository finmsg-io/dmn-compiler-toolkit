package io.finmsg.dmn.compiler;

/** Stable classifications for invalid DMN import structures. */
public enum DmnImportDiagnosticCode {
  MISSING("DMN-IMPORT-MISSING"),
  AMBIGUOUS("DMN-IMPORT-AMBIGUOUS"),
  DUPLICATE("DMN-IMPORT-DUPLICATE"),
  CYCLE("DMN-IMPORT-CYCLE");

  private final String value;

  DmnImportDiagnosticCode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
