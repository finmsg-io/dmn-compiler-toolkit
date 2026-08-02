package io.finmsg.dmn.compiler;

/** Policy boundary used by the compiler to locate an imported DMN source. */
@FunctionalInterface
public interface DmnModelResolver {
  DmnResolutionResult resolve(DmnImportRequest request);
}
