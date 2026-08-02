package io.finmsg.dmn.compiler;

/** Stable compiler phase vocabulary used by shared diagnostics. */
public enum DmnCompilerPhase {
  SOURCE_RESOLUTION,
  XML_FRONTEND,
  FEEL_PARSING,
  SEMANTIC_ANALYSIS,
  RUNTIME_IR_LOWERING,
  OPTIMIZATION,
  GENERATION
}
