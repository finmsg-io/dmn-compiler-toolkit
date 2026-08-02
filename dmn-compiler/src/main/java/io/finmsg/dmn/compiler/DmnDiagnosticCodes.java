package io.finmsg.dmn.compiler;

/** Known stable compiler diagnostic codes. */
public final class DmnDiagnosticCodes {
  public static final String IMPORT_MISSING = "DMN-IMPORT-MISSING";
  public static final String IMPORT_AMBIGUOUS = "DMN-IMPORT-AMBIGUOUS";
  public static final String IMPORT_DUPLICATE = "DMN-IMPORT-DUPLICATE";
  public static final String IMPORT_CYCLE = "DMN-IMPORT-CYCLE";
  public static final String FEEL_SYNTAX = "DMN-FEEL-SYNTAX";

  private DmnDiagnosticCodes() {
  }
}
